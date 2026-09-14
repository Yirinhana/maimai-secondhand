package com.maimai.official;

import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.identity.service.AuthService;
import com.maimai.identity.service.AvatarService;
import com.maimai.official.OfficialDtos.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.CRC32;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OfficialAvatarIntegrationTest {
    static final Path DIRECTORY=Path.of(".local","avatar-test-"+UUID.randomUUID()).toAbsolutePath().normalize();
    @DynamicPropertySource static void config(DynamicPropertyRegistry properties) {properties.add("MAIMAI_AVATAR_DIR",DIRECTORY::toString);}
    @Autowired JdbcTemplate db;
    @Autowired AvatarService avatars;
    @Autowired AuthService auth;
    @Autowired OfficialArticleService articles;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Environment environment;
    final JsonMapper json=JsonMapper.builder().build();
    String email,prefix;
    long user,other;

    @BeforeEach void fixture() {
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("maimai_test");
        prefix="official-test-"+UUID.randomUUID();email=prefix+"@example.invalid";
        user=createUser(email);other=createUser("other-"+email);as(user,"USER");
    }

    @AfterEach void cleanup() throws IOException {
        SecurityContextHolder.clearContext();
        db.update("DELETE a FROM admin_audit_logs a JOIN official_articles o ON o.id=a.target_id WHERE a.target_type='OFFICIAL_ARTICLE' AND a.admin_id IN (?,?) AND o.slug LIKE ?",user,other,prefix+"%");
        db.update("DELETE FROM official_articles WHERE slug LIKE ?",prefix+"%");
        for(long id:new long[]{user,other}) {
            db.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=(SELECT email FROM users WHERE id=?)",id);
            db.update("DELETE FROM user_roles WHERE user_id=?",id);
            db.update("DELETE FROM user_avatars WHERE user_id=?",id);
            db.update("DELETE FROM users WHERE id=?",id);
        }
        if(Files.isDirectory(DIRECTORY)) {
            try(var files=Files.list(DIRECTORY)) {for(Path path:files.toList()) {assertThat(path.getParent()).isEqualTo(DIRECTORY);Files.deleteIfExists(path);}}
            Files.deleteIfExists(DIRECTORY);
        }
    }

    @Test void avatarIsPersistedReencodedAndOnlyCurrentImageRemainsPublic() throws Exception {
        assertThat(auth.summaryOf(user).avatarUrl()).isNull();
        var first=avatars.upload(png());
        String filename=filename(first.avatarUrl());
        byte[] bytes=Files.readAllBytes(avatars.readable(filename));
        assertThat(bytes[0]&255).isEqualTo(255);assertThat(bytes[1]&255).isEqualTo(216);
        assertThat(ImageIO.read(new ByteArrayInputStream(bytes)).getWidth()).isEqualTo(8);
        assertThat(auth.summaryOf(user).avatarUrl()).isEqualTo(first.avatarUrl());
        assertThat(auth.summaryOf(other).avatarUrl()).isNull();
        assertThat(db.queryForObject("SELECT filename FROM user_avatars WHERE user_id=?",String.class,user)).isEqualTo(filename);
        var replacement=avatars.upload(png());
        assertThat(auth.summaryOf(user).avatarUrl()).isEqualTo(replacement.avatarUrl());
        assertThatThrownBy(()->avatars.readable(filename)).isInstanceOf(BizException.class);
        assertThat(DIRECTORY.resolve(filename)).exists(); // No deletion of previously stored user files.
        SecurityContextHolder.clearContext();assertThat(avatars.readable(filename(replacement.avatarUrl()))).exists();
    }

    @Test void rejectsSpoofedImageOversizeAndDecompressionBombBeforePersisting() throws Exception {
        var svg=new MockMultipartFile("file","photo.jpg","image/jpeg","<svg onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8));
        assertCode(()->avatars.upload(svg),"AVATAR_IMAGE_FORMAT");
        assertCode(()->avatars.upload(new MockMultipartFile("file","truncated.jpg","image/jpeg",new byte[]{(byte)255,(byte)216,(byte)255})),"AVATAR_IMAGE_FORMAT");
        assertCode(()->avatars.upload(new MockMultipartFile("file","huge.png","image/png",new byte[5*1024*1024+1])),"AVATAR_IMAGE_SIZE");
        byte[] bomb=png().getBytes();ByteBuffer.wrap(bomb,16,4).putInt(20001);
        CRC32 crc=new CRC32();crc.update(bomb,12,17);ByteBuffer.wrap(bomb,29,4).putInt((int)crc.getValue());
        assertCode(()->avatars.upload(new MockMultipartFile("file","bomb.png","image/png",bomb)),"AVATAR_IMAGE_DIMENSION");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM user_avatars WHERE user_id=?",Long.class,user)).isZero();
        assertThat(Files.exists(DIRECTORY)).isFalse();
    }

    @Test void avatarDatabaseRollbackRemovesOnlyItsNewFile() throws Exception {
        String initial=avatars.upload(png()).avatarUrl();
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            try {avatars.upload(png());} catch(IOException failure) {throw new UncheckedIOException(failure);}
            status.setRollbackOnly();
        });
        assertThat(auth.summaryOf(user).avatarUrl()).isEqualTo(initial);
        try(var files=Files.list(DIRECTORY)) {assertThat(files.map(p->p.getFileName().toString()).toList()).containsExactly(filename(initial));}
    }

    @Test void rejectsAvatarPathsAndDisabledAccountsEvenWithExistingPrincipal() throws Exception {
        String stored=filename(avatars.upload(png()).avatarUrl());
        for(String path:List.of("../"+stored,"..\\"+stored,"C:\\secret.jpg","%2e%2e%2fsecret.jpg",UUID.randomUUID()+".jpg",stored.toUpperCase(Locale.ROOT)))
            assertThatThrownBy(()->avatars.readable(path)).isInstanceOf(BizException.class);
        db.update("UPDATE users SET status='DISABLED' WHERE id=?",user);
        assertCode(()->avatars.upload(png()),"UNAUTHORIZED");
        assertThatThrownBy(()->avatars.readable(stored)).isInstanceOf(BizException.class);
        assertThat(db.queryForObject("SELECT filename FROM user_avatars WHERE user_id=?",String.class,user)).isEqualTo(stored);
    }

    @Test void draftPublicationWithdrawalAndEditingPersistWithoutExposingDraft() {
        role("OPERATOR");
        var draft=articles.create(request("one",Status.DRAFT));
        assertThat(draft.publishedAt()).isNull();assertThat(articles.adminList(null,Status.DRAFT,0,50).getContent()).extracting(AdminArticle::id).contains(draft.id());
        assertThatThrownBy(()->articles.detail(draft.slug())).isInstanceOf(BizException.class);
        var published=articles.update(draft.id(),request("one",Status.PUBLISHED));
        assertThat(published.publishedAt()).isNotNull();
        SecurityContextHolder.clearContext();
        assertThat(articles.detail(draft.slug()).body()).isEqualTo("第一段\n\n第二段 <b>纯文本</b>");
        assertThat(articles.detail(draft.slug()).publisher()).isEqualTo("麦麦官方");
        assertThat(articles.list(Category.GUIDE,0,50).getContent()).extracting(ArticleSummary::id).contains(draft.id());
        as(user,"OPERATOR");articles.update(draft.id(),request("one",Status.WITHDRAWN));
        assertThatThrownBy(()->articles.detail(draft.slug())).isInstanceOf(BizException.class);
        assertThat(articles.list(Category.GUIDE,0,50).getContent()).extracting(ArticleSummary::id).doesNotContain(draft.id());
        var restored=articles.update(draft.id(),request("renamed",Status.PUBLISHED));
        assertThat(restored.publishedAt()).isEqualTo(published.publishedAt());
        assertThatThrownBy(()->articles.detail(draft.slug())).isInstanceOf(BizException.class);
        assertThat(articles.detail(restored.slug()).title()).isEqualTo("本地内容测试");
    }

    @Test void editorialRolesAreCheckedAgainstDatabaseNotStaleClaimedRoles() {
        for(String denied:List.of("USER","SUPPORT")) {
            role(denied);as(user,"SUPER_ADMIN");
            assertCode(()->articles.create(request("denied",Status.PUBLISHED)),"FORBIDDEN");
            assertCode(()->articles.adminList(null,null,0,10),"FORBIDDEN");
        }
        role("OPERATOR");var saved=articles.create(request("allowed",Status.PUBLISHED));
        role("SUPER_ADMIN");assertThat(articles.update(saved.id(),request("allowed",Status.DRAFT)).status()).isEqualTo(Status.DRAFT);
        db.update("UPDATE users SET status='DISABLED' WHERE id=?",user);
        assertCode(()->articles.update(saved.id(),request("allowed",Status.PUBLISHED)),"FORBIDDEN");
        assertCode(()->articles.adminList(null,null,0,10),"FORBIDDEN");
        assertThat(db.queryForObject("SELECT status FROM official_articles WHERE id=?",String.class,saved.id())).isEqualTo("DRAFT");
    }

    @Test void invalidContentsAndDuplicateSlugsRollBackWithoutCorruptingExistingArticle() {
        role("OPERATOR");var original=articles.create(request("unique",Status.PUBLISHED));
        assertCode(()->articles.create(request("unique",Status.DRAFT)),"OFFICIAL_SLUG_TAKEN");
        var second=articles.create(request("second",Status.DRAFT));
        assertCode(()->articles.update(second.id(),request("unique",Status.PUBLISHED)),"OFFICIAL_SLUG_TAKEN");
        assertCode(()->articles.create(new ArticleRequest("../escape","标题","摘要","正文",Category.NOTICE,Status.DRAFT)),"OFFICIAL_CONTENT_INVALID");
        assertCode(()->articles.create(new ArticleRequest(prefix+"-control","标题","摘要","正文"+(char)0+"\n",Category.NOTICE,Status.DRAFT)),"OFFICIAL_CONTENT_INVALID");
        assertThat(articles.detail(original.slug()).title()).isEqualTo("本地内容测试");
        assertThat(db.queryForObject("SELECT status FROM official_articles WHERE id=?",String.class,second.id())).isEqualTo("DRAFT");
        assertCode(()->articles.list(null,-1,10),"PAGE_INVALID");assertCode(()->articles.list(null,0,51),"PAGE_INVALID");
    }

    @Test void publicPaginationFiltersCategoryAndNeverCountsDrafts() {
        role("OPERATOR");long baseline=articles.list(Category.GUIDE,0,50).getTotalElements();
        articles.create(request("draft",Status.DRAFT));
        var first=articles.create(request("first",Status.PUBLISHED));var second=articles.create(request("second",Status.PUBLISHED));
        var a=articles.list(Category.GUIDE,0,1);var b=articles.list(Category.GUIDE,1,1);
        assertThat(a.getTotalElements()).isEqualTo(baseline+2);assertThat(a.getTotalPages()).isEqualTo(baseline+2);
        assertThat(a.getContent().getFirst().id()).isEqualTo(second.id());assertThat(b.getContent().getFirst().id()).isEqualTo(first.id());
        assertThat(articles.list(Category.ABOUT,0,50).getContent()).extracting(ArticleSummary::id).doesNotContain(first.id(),second.id());
    }

    @Test void editorialAuditRecordsOnlyMetadataAndRollsBackWithFailedWrites() {
        role("OPERATOR");
        var draft=articles.create(request("audit",Status.DRAFT));
        articles.update(draft.id(),request("audit",Status.PUBLISHED));
        articles.update(draft.id(),new ArticleRequest(draft.slug(),"私有标题","私有摘要","私有正文",Category.NOTICE,Status.WITHDRAWN));
        var events=db.queryForList("SELECT admin_id,action,target_id,reason,before_state,after_state FROM admin_audit_logs WHERE admin_id=? AND target_type='OFFICIAL_ARTICLE' AND target_id=? ORDER BY id",user,draft.id());
        assertThat(events).hasSize(3);
        assertThat(events).extracting(event->event.get("action")).containsExactly("OFFICIAL_CREATE","OFFICIAL_UPDATE","OFFICIAL_UPDATE");
        assertThat(events.getFirst().get("before_state")).isNull();
        assertThat(events.getFirst().get("after_state")).isEqualTo("status=DRAFT;category=GUIDE;slug="+draft.slug());
        assertThat(events.get(1).get("before_state")).isEqualTo(events.getFirst().get("after_state"));
        assertThat(events.get(1).get("after_state")).isEqualTo("status=PUBLISHED;category=GUIDE;slug="+draft.slug());
        assertThat(events.getLast().get("before_state")).isEqualTo(events.get(1).get("after_state"));
        assertThat(events.getLast().get("after_state")).isEqualTo("status=WITHDRAWN;category=NOTICE;slug="+draft.slug());
        for(var event:events) {
            assertThat(((Number)event.get("admin_id")).longValue()).isEqualTo(user);
            assertThat(((Number)event.get("target_id")).longValue()).isEqualTo(draft.id());
            assertThat(event.get("reason")).isIn("创建官方内容","编辑官方内容");
            assertThat(event.toString()).doesNotContain("私有标题","私有摘要","私有正文","第一段","用于验证的简介");
        }
        assertCode(()->articles.create(request("audit",Status.PUBLISHED)),"OFFICIAL_SLUG_TAKEN");
        var otherDraft=articles.create(request("audit-second",Status.DRAFT));
        assertCode(()->articles.update(otherDraft.id(),request("audit",Status.PUBLISHED)),"OFFICIAL_SLUG_TAKEN");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND target_type='OFFICIAL_ARTICLE'",Long.class,user)).isEqualTo(4);
        new TransactionTemplate(transactions).executeWithoutResult(status->{articles.create(request("audit-rollback",Status.DRAFT));status.setRollbackOnly();});
        assertThat(db.queryForObject("SELECT COUNT(*) FROM official_articles WHERE slug=?",Long.class,prefix+"-audit-rollback")).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND target_type='OFFICIAL_ARTICLE'",Long.class,user)).isEqualTo(4);
    }

    @Test void fourPublicSeedArticlesClearlyDescribeLocalAndExternalConditions() {
        SecurityContextHolder.clearContext();
        for(String slug:List.of("about-maimai","buying-and-selling-guide","trade-safety","local-development-notice"))
            assertThat(articles.detail(slug).publisher()).isEqualTo("麦麦官方");
        assertThat(articles.detail("about-maimai").body()).contains("本地开发版本","不代表真实资金转移");
        assertThat(articles.detail("local-development-notice").body()).contains("不扣取真实资金","仍需后续确认");
    }

    @Test void publicHttpReadsWorkAndEditorialHttpEnforcesRolesCsrfAndRevocation() throws Exception {
        Browser visitor=new Browser();
        var list=visitor.call("GET","/official/articles?category=NOTICE&page=0&size=1",null,false);
        assertThat(list.statusCode()).withFailMessage(list.body()).isEqualTo(200);
        assertThat(json.readTree(list.body()).get("content").size()).isEqualTo(1);
        assertThat(json.readTree(list.body()).get("number").asInt()).isZero();
        assertThat(visitor.call("GET","/official/articles/local-development-notice",null,false).statusCode()).isEqualTo(200);
        assertThat(visitor.call("GET","/admin/official/articles",null,false).statusCode()).isEqualTo(401);
        role("SUPPORT");Browser staff=new Browser();staff.login();
        assertThat(staff.call("GET","/admin/official/articles",null,false).statusCode()).isEqualTo(403);
        role("OPERATOR");
        String payload=json.writeValueAsString(request("http",Status.DRAFT));
        assertThat(staff.call("POST","/admin/official/articles",payload,false).statusCode()).isEqualTo(403);
        var created=staff.call("POST","/admin/official/articles",payload,true);
        assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(200);
        assertThat(visitor.call("GET","/official/articles/"+prefix+"-http",null,false).statusCode()).isEqualTo(404);
        role("USER");assertThat(staff.call("GET","/admin/official/articles",null,false).statusCode()).isEqualTo(403);
    }

    @Test void realMultipartUploadRequiresLoginAndCsrfThenAvatarIsPublicJpeg() throws Exception {
        Browser browser=new Browser();browser.call("GET","/auth/csrf",null,false);
        assertThat(browser.upload(png().getBytes(),true).statusCode()).isEqualTo(401);
        browser.login();assertThat(browser.upload(png().getBytes(),false).statusCode()).isEqualTo(403);
        var upload=browser.upload(png().getBytes(),true);
        assertThat(upload.statusCode()).withFailMessage(upload.body()).isEqualTo(200);
        String url=json.readTree(upload.body()).get("avatarUrl").asText();
        assertThat(browser.call("GET","/auth/me",null,false).body()).contains(url);
        var image=new Browser().client.send(HttpRequest.newBuilder(uri(url.substring("/api/v1".length()))).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);assertThat(image.headers().firstValue("content-type")).contains("image/jpeg");
        assertThat(image.headers().firstValue("x-content-type-options")).contains("nosniff");
        assertThat(ImageIO.read(new ByteArrayInputStream(image.body()))).isNotNull();
        assertThat(browser.upload("<script>bad</script>".getBytes(StandardCharsets.UTF_8),true).statusCode()).isEqualTo(400);
        assertThat(auth.summaryOf(user).avatarUrl()).isEqualTo(url);
    }

    private long createUser(String address) {
        db.update("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,?,?,'ACTIVE')",address,passwords.encode("Official#123"),"界面测试");
        long id=db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,address);
        db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'USER')",id);return id;
    }
    private void role(String role) {db.update("DELETE FROM user_roles WHERE user_id=?",user);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,?)",user,role);as(user,role);}
    private void as(long id,String role) {
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(id,email,"测试",Set.of(role)),null,List.of()));SecurityContextHolder.setContext(context);
    }
    private ArticleRequest request(String suffix,Status status) {return new ArticleRequest(prefix+"-"+suffix,"本地内容测试","用于验证的简介","第一段\n\n第二段 <b>纯文本</b>",Category.GUIDE,status);}
    private static String filename(String url) {return url.substring(url.lastIndexOf('/')+1);}
    private MockMultipartFile png() throws IOException {
        var image=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);var output=new ByteArrayOutputStream();
        ImageIO.write(image,"png",output);image.flush();return new MockMultipartFile("file","../../avatar.png","image/png",output.toByteArray());
    }
    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BizException.class,error->assertThat(error.getCode()).isEqualTo(code));
    }
    private URI uri(String path) {return URI.create("http://127.0.0.1:"+environment.getProperty("local.server.port")+"/api/v1"+path);}
    private class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        String csrf() {return cookies.getCookieStore().getCookies().stream().filter(c->"XSRF-TOKEN".equals(c.getName())).map(HttpCookie::getValue).findFirst().orElse(null);}
        void login() throws Exception {
            call("GET","/auth/csrf",null,false);
            var result=call("POST","/auth/login","{\"email\":\""+email+"\",\"password\":\"Official#123\"}",true);
            assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(200);
        }
        HttpResponse<String> call(String method,String path,String body,boolean token) throws Exception {
            var request=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
            if(body!=null)request.header("Content-Type","application/json");if(token)request.header("X-XSRF-TOKEN",csrf());
            return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> upload(byte[] bytes,boolean token) throws Exception {
            String boundary="AvatarBoundary035";var body=new ByteArrayOutputStream();
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(bytes);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            var request=HttpRequest.newBuilder(uri("/me/avatar")).timeout(Duration.ofSeconds(10)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
            if(token)request.header("X-XSRF-TOKEN",csrf());return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
