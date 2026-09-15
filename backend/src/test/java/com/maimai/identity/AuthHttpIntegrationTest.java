package com.maimai.identity;

import com.maimai.identity.service.EmailCodeVerifier;
import com.maimai.identity.domain.EmailVerification.Purpose;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.hikari.maximum-pool-size=2")
@ActiveProfiles("test")
class AuthHttpIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired Environment environment;
    @Autowired EmailCodeVerifier verifier;
    @Autowired com.maimai.common.SimpleRateLimiter limiter;
    String email;
    long userId;

    @BeforeEach void createUser() {
        assertThat(db.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("maimai_test");
        // Each HTTP scenario gets its own local rate-limit window; production limits remain active.
        synchronized (limiter) {
            ((Map<?,?>) org.springframework.test.util.ReflectionTestUtils.getField(limiter,"windows")).clear();
        }
        email = "auth-" + UUID.randomUUID() + "@example.invalid";
        db.update("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,?,?,'ACTIVE')",
                email, passwords.encode("TestAuth#123"), "认证测试");
        userId = db.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'USER')", userId);
    }

    @AfterEach void removeFixture() {
        db.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", email);
        db.update("DELETE FROM email_verifications WHERE email=?", email);
        db.update("DELETE FROM user_roles WHERE user_id=?", userId);
        db.update("DELETE FROM admin_audit_logs WHERE admin_id=?",userId);
        db.update("DELETE FROM user_policy_acceptances WHERE user_id=?",userId);
        db.update("DELETE FROM users WHERE id=?", userId);
    }

    @Test void rawSpaTokenWorksAndSessionPersistsThenLogoutRevokesIt() throws Exception {
        Browser browser = new Browser();
        assertThat(browser.call("GET", "/auth/csrf", null, null).statusCode()).isEqualTo(200);
        String preLoginToken = browser.csrf();
        String login = "{\"email\":\"" + email + "\",\"password\":\"TestAuth#123\"}";
        assertThat(browser.call("POST", "/auth/login", login, null).statusCode()).isEqualTo(403);
        assertThat(browser.call("POST", "/auth/login", login, "wrong").statusCode()).isEqualTo(403);
        var response = browser.call("POST", "/auth/login", login, browser.csrf());
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        assertThat(response.headers().allValues("set-cookie").toString()).contains("HttpOnly");
        assertThat(browser.csrf()).isNotBlank().isNotEqualTo(preLoginToken);
        assertThat(browser.call("GET", "/auth/me", null, null).body()).contains(email);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", Long.class, email)).isEqualTo(1);
        assertThat(browser.call("POST", "/auth/logout", "{}", null).statusCode()).isEqualTo(403);
        assertThat(browser.call("GET", "/auth/me", null, null).statusCode()).isEqualTo(200);
        assertThat(browser.call("POST", "/auth/logout", "{}", browser.csrf()).statusCode()).isEqualTo(204);
        assertThat(browser.call("GET", "/auth/me", null, null).statusCode()).isEqualTo(401);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", Long.class, email)).isZero();
    }

    @Test void resetInvalidatesAllSessionsAndDisabledUserCannotUseOldCookie() throws Exception {
        Browser first = new Browser(), second = new Browser();
        first.login("TestAuth#123"); second.login("TestAuth#123");
        insertCode("654321");
        var reset = first.call("POST", "/auth/password/reset", "{\"email\":\"" + email
                + "\",\"code\":\"654321\",\"newPassword\":\"Changed#456\"}", first.csrf());
        assertThat(reset.statusCode()).withFailMessage(reset.body()).isEqualTo(204);
        assertThat(first.call("GET", "/auth/me", null, null).statusCode()).isEqualTo(401);
        assertThat(second.call("GET", "/auth/me", null, null).statusCode()).isEqualTo(401);
        second.login("Changed#456");
        db.update("UPDATE users SET status='DISABLED' WHERE id=?", userId);
        assertThat(second.call("GET", "/auth/me", null, null).statusCode()).isEqualTo(401);
    }

    @Test void revokedRoleCannotRetainAdminAccess() throws Exception {
        db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'SUPER_ADMIN')", userId);
        Browser browser = new Browser(); browser.login("TestAuth#123");
        assertThat(browser.call("GET", "/admin/users", null, null).statusCode()).isEqualTo(200);
        db.update("DELETE FROM user_roles WHERE user_id=? AND role='SUPER_ADMIN'", userId);
        assertThat(browser.call("GET", "/admin/users", null, null).statusCode()).isEqualTo(403);
    }

    @Test void failedVerificationAttemptsPersistAndConcurrentConsumptionSucceedsOnce() throws Exception {
        insertCode("123456");
        for (int attempt = 0; attempt < 5; attempt++) assertThat(verifier.consume(email, Purpose.RESET, "000000")).isFalse();
        assertThat(verifier.consume(email, Purpose.RESET, "123456")).isFalse();
        assertThat(db.queryForObject("SELECT attempts FROM email_verifications WHERE email=?", Integer.class, email)).isEqualTo(5);
        db.update("DELETE FROM email_verifications WHERE email=?", email);
        insertCode("123456");
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i=0; i<8; i++) tasks.add(() -> verifier.consume(email, Purpose.RESET, "123456"));
            int successes=0;
            for (var result : pool.invokeAll(tasks)) if (result.get()) successes++;
            assertThat(successes).isEqualTo(1);
        }
    }

    @Test void operationsAndSupportCannotCrossRoleBoundaries() throws Exception {
        db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'SUPPORT')",userId);
        Browser browser=new Browser(); browser.login("TestAuth#123");
        assertThat(browser.call("GET","/admin/seller-applications",null,null).statusCode()).isEqualTo(403);
        assertThat(browser.call("GET","/admin/products",null,null).statusCode()).isEqualTo(403);
        assertThat(browser.call("GET","/admin/aftersales",null,null).statusCode()).isEqualTo(200);
        assertThat(browser.call("GET","/admin/finance/orders",null,null).statusCode()).isEqualTo(403);
        db.update("UPDATE user_roles SET role='OPERATOR' WHERE user_id=? AND role='SUPPORT'",userId);
        assertThat(browser.call("GET","/admin/seller-applications",null,null).statusCode()).isEqualTo(200);
        assertThat(browser.call("GET","/admin/aftersales",null,null).statusCode()).isEqualTo(403);
        assertThat(browser.call("GET","/admin/finance/orders",null,null).statusCode()).isEqualTo(403);
        db.update("UPDATE user_roles SET role='SUPER_ADMIN' WHERE user_id=? AND role='OPERATOR'",userId);
        assertThat(browser.call("GET","/admin/finance/orders",null,null).statusCode()).isEqualTo(200);
        var csv=browser.call("GET","/admin/finance/export.csv",null,null);
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.body()).contains("渠道对账状态");
        assertThat(csv.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    @Test void rejectedHttpResetCommitsAttemptLimitWithoutChangingPassword() throws Exception {
        insertCode("123456");
        Browser browser=new Browser(); browser.call("GET","/auth/csrf",null,null);
        String wrong="{\"email\":\""+email+"\",\"code\":\"000000\",\"newPassword\":\"Changed#456\"}";
        for(int i=0;i<5;i++) assertThat(browser.call("POST","/auth/password/reset",wrong,browser.csrf()).statusCode()).isEqualTo(400);
        assertThat(db.queryForObject("SELECT attempts FROM email_verifications WHERE email=?",Integer.class,email)).isEqualTo(5);
        String correct=wrong.replace("000000","123456");
        assertThat(browser.call("POST","/auth/password/reset",correct,browser.csrf()).statusCode()).isEqualTo(400);
        assertThat(passwords.matches("TestAuth#123",db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,userId))).isTrue();
    }

    @Test void concurrentHttpResetsUseSmallPoolAndConsumeCodeOnce() throws Exception {
        insertCode("123456");
        var browsers=new ArrayList<Browser>();
        for(int i=0;i<6;i++) { Browser browser=new Browser();browser.call("GET","/auth/csrf",null,null);browsers.add(browser); }
        String body="{\"email\":\""+email+"\",\"code\":\"123456\",\"newPassword\":\"Changed#456\"}";
        CountDownLatch ready=new CountDownLatch(6),start=new CountDownLatch(1);
        try(ExecutorService pool=Executors.newFixedThreadPool(6)) {
            var results=new ArrayList<Future<Integer>>();
            for(var browser:browsers)results.add(pool.submit(()->{ready.countDown();assertThat(start.await(5,TimeUnit.SECONDS)).isTrue();return browser.call("POST","/auth/password/reset",body,browser.csrf()).statusCode();}));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            int succeeded=0;
            for(var result:results){int status=result.get(15,TimeUnit.SECONDS);assertThat(status).isIn(204,400);if(status==204)succeeded++;}
            assertThat(succeeded).isEqualTo(1);
        }
        assertThat(passwords.matches("Changed#456",db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,userId))).isTrue();
    }

    @Test void registrationRequiresCurrentExplicitConsentBeforeConsumingCode() throws Exception {
        db.update("DELETE FROM user_roles WHERE user_id=?",userId);
        db.update("DELETE FROM users WHERE id=?",userId);
        db.update("INSERT INTO email_verifications(email,purpose,code_hash,expires_at,last_sent_at) VALUES(?,'REGISTER',?,DATE_ADD(NOW(),INTERVAL 10 MINUTE),NOW())",email,EmailCodeVerifier.hash("112233"));
        Browser browser=new Browser();
        var policy=browser.call("GET","/policies/current",null,null);
        assertThat(policy.statusCode()).isEqualTo(200);
        assertThat(policy.body()).contains(com.maimai.identity.service.PolicyConsentService.CURRENT_VERSION).contains("\"localDraft\":true");
        String fields="\"email\":\""+email+"\",\"code\":\"112233\",\"password\":\"Register#123\",\"nickname\":\"同意条款测试\"";
        assertThat(browser.call("POST","/auth/register","{"+fields+"}",browser.csrf()).statusCode()).isEqualTo(400);
        String wrong="{"+fields+",\"acceptedTerms\":true,\"policyVersion\":\"obsolete\"}";
        assertThat(browser.call("POST","/auth/register",wrong,browser.csrf()).statusCode()).isEqualTo(400);
        String unchecked="{"+fields+",\"acceptedTerms\":false,\"policyVersion\":\""+com.maimai.identity.service.PolicyConsentService.CURRENT_VERSION+"\"}";
        assertThat(browser.call("POST","/auth/register",unchecked,browser.csrf()).statusCode()).isEqualTo(400);
        assertThat(db.queryForObject("SELECT consumed_at FROM email_verifications WHERE email=?",java.sql.Timestamp.class,email)).isNull();
        assertThat(db.queryForObject("SELECT attempts FROM email_verifications WHERE email=?",Integer.class,email)).isZero();
        String accepted="{"+fields+",\"acceptedTerms\":true,\"policyVersion\":\""+com.maimai.identity.service.PolicyConsentService.CURRENT_VERSION+"\"}";
        var registration=browser.call("POST","/auth/register",accepted,browser.csrf());
        assertThat(registration.statusCode()).withFailMessage(registration.body()).isEqualTo(200);
        userId=db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM user_policy_acceptances WHERE user_id=? AND policy_version=?",Long.class,userId,com.maimai.identity.service.PolicyConsentService.CURRENT_VERSION)).isEqualTo(1);
        assertThat(browser.call("GET","/auth/me",null,null).statusCode()).isEqualTo(200);
    }

    @Test void disabledAccountExplainsStatusOnlyAfterCorrectPassword() throws Exception {
        db.update("UPDATE users SET status='DISABLED' WHERE id=?",userId);
        Browser browser = new Browser(); browser.call("GET","/auth/csrf",null,null);
        String wrong="{\"email\":\""+email+"\",\"password\":\"WrongPassword#9\"}";
        var invalid=browser.call("POST","/auth/login",wrong,browser.csrf());
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("BAD_CREDENTIALS").doesNotContain("ACCOUNT_DISABLED");
        String correct="{\"email\":\""+email+"\",\"password\":\"TestAuth#123\"}";
        var disabled=browser.call("POST","/auth/login",correct,browser.csrf());
        assertThat(disabled.statusCode()).isEqualTo(403);
        assertThat(disabled.body()).contains("ACCOUNT_DISABLED","该账号已被停用","平台客服");
        assertThat(browser.call("GET","/auth/me",null,null).statusCode()).isEqualTo(401);
    }

    @Test void switchingAccountReplacesPrincipalAndRotatesSessionAndCsrf() throws Exception {
        String secondEmail="switch-"+UUID.randomUUID()+"@example.invalid";
        db.update("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,?,?,'ACTIVE')",secondEmail,passwords.encode("Switch#123"),"切换账号测试");
        long secondId=db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,secondEmail);
        db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'USER')",secondId);
        try {
            Browser browser=new Browser(); browser.login("TestAuth#123");
            String oldCsrf=browser.csrf();
            String oldSession=browser.cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("SESSION")).map(HttpCookie::getValue).findFirst().orElseThrow();
            var invalid=browser.call("POST","/auth/login","{\"email\":\""+secondEmail+"\",\"password\":\"wrong\"}",oldCsrf);
            assertThat(invalid.statusCode()).isEqualTo(400);
            assertThat(browser.call("GET","/auth/me",null,null).body()).contains(email);
            var switched=browser.call("POST","/auth/login","{\"email\":\""+secondEmail+"\",\"password\":\"Switch#123\"}",oldCsrf);
            assertThat(switched.statusCode()).withFailMessage(switched.body()).isEqualTo(200);
            assertThat(browser.call("GET","/auth/me",null,null).body()).contains(secondEmail).doesNotContain(email);
            assertThat(browser.csrf()).isNotEqualTo(oldCsrf);
            assertThat(browser.cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("SESSION")).map(HttpCookie::getValue)).doesNotContain(oldSession);
            assertThat(browser.call("POST","/auth/logout","{}",oldCsrf).statusCode()).isEqualTo(403);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?",Long.class,email)).isZero();
        } finally {
            db.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?",secondEmail);
            db.update("DELETE FROM user_roles WHERE user_id=?",secondId);
            db.update("DELETE FROM users WHERE id=?",secondId);
        }
    }

    @Test void registeredUserCanApplyBecomeSellerAndBeFollowedWithoutPaymentQualification() throws Exception {
        var json=new tools.jackson.databind.json.JsonMapper();
        String sellerEmail="seller-flow-"+UUID.randomUUID()+"@example.invalid";
        long sellerId=0;
        db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'SUPER_ADMIN')",userId);
        Browser admin=new Browser(),seller=new Browser();admin.login("TestAuth#123");
        seller.call("GET","/auth/csrf",null,null);
        db.update("INSERT INTO email_verifications(email,purpose,code_hash,expires_at,last_sent_at) VALUES(?,'REGISTER',?,DATE_ADD(NOW(),INTERVAL 10 MINUTE),NOW())",sellerEmail,EmailCodeVerifier.hash("123456"));
        try {
            String registration=json.writeValueAsString(Map.of("email",sellerEmail,"password","Natural#1234","nickname","正常入驻测试卖家","code","123456","acceptedTerms",true,"policyVersion",com.maimai.identity.service.PolicyConsentService.CURRENT_VERSION));
            var created=seller.call("POST","/auth/register",registration,seller.csrf());
            assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(200);
            sellerId=json.readTree(created.body()).path("id").asLong();
            assertThat(sellerId).isPositive();
            assertThat(json.readTree(admin.call("GET","/sellers/"+sellerId,null,null).body()).path("sellerApproved").asBoolean()).isFalse();
            var applied=seller.call("POST","/me/seller-application","{\"intro\":\"数码与家居闲置，支持快递与面交\"}",seller.csrf());
            assertThat(applied.statusCode()).withFailMessage(applied.body()).isEqualTo(200);
            long applicationId=json.readTree(applied.body()).path("id").asLong();
            assertThat(admin.call("POST","/community/follows/"+sellerId,"{}",admin.csrf()).statusCode()).isEqualTo(403);
            var reviewed=admin.call("POST","/admin/seller-applications/"+applicationId+"/review","{\"action\":\"APPROVE\",\"reason\":\"平台卖家资料审核通过，收款渠道另行核实\",\"channelQualified\":false}",admin.csrf());
            assertThat(reviewed.statusCode()).withFailMessage(reviewed.body()).isEqualTo(200);
            assertThat(json.readTree(reviewed.body()).path("channelStatus").asString()).isEqualTo("PENDING");
            var me=json.readTree(seller.call("GET","/auth/me",null,null).body());
            assertThat(me.path("sellerStatus").asString()).isEqualTo("APPROVED");
            assertThat(me.path("roles").toString()).contains("SELLER");
            assertThat(json.readTree(admin.call("GET","/sellers/"+sellerId,null,null).body()).path("sellerApproved").asBoolean()).isTrue();
            assertThat(admin.call("POST","/community/follows/"+sellerId,"{}",admin.csrf()).statusCode()).isEqualTo(201);
            assertThat(seller.call("GET","/seller/products",null,null).statusCode()).isEqualTo(200);
            var repeated=seller.call("POST","/me/seller-application","{\"intro\":\"重复申请\"}",seller.csrf());
            assertThat(repeated.statusCode()).isEqualTo(409);
            assertThat(repeated.body()).contains("SELLER_ALREADY_APPROVED");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM seller_applications WHERE user_id=?",Long.class,sellerId)).isEqualTo(1);
            db.update("INSERT INTO seller_applications(user_id,status,channel_status,created_at) SELECT user_id,'SUSPENDED','PENDING',created_at FROM seller_applications WHERE id=?",applicationId);
            assertThat(json.readTree(seller.call("GET","/auth/me",null,null).body()).path("sellerStatus").asString()).isEqualTo("SUSPENDED");
            assertThat(json.readTree(admin.call("GET","/sellers/"+sellerId,null,null).body()).path("sellerApproved").asBoolean()).isFalse();
            // Suspended sellers retain read access to their own inventory and historical orders.
            assertThat(seller.call("GET","/seller/products",null,null).statusCode()).isEqualTo(200);
            assertThat(admin.call("POST","/community/follows/"+sellerId,"{}",admin.csrf()).statusCode()).isEqualTo(403);
        } finally {
            db.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?",sellerEmail);
            db.update("DELETE FROM email_verifications WHERE email=?",sellerEmail);
            if(sellerId>0) {
                db.update("DELETE FROM community_seller_follows WHERE seller_id=?",sellerId);
                db.update("DELETE FROM notifications WHERE user_id=?",sellerId);
                db.update("DELETE FROM seller_applications WHERE user_id=?",sellerId);
                db.update("DELETE FROM user_policy_acceptances WHERE user_id=?",sellerId);
                db.update("DELETE FROM user_roles WHERE user_id=?",sellerId);
                db.update("DELETE FROM users WHERE id=?",sellerId);
            }
        }
    }

    void insertCode(String code) {
        db.update("INSERT INTO email_verifications(email,purpose,code_hash,expires_at,last_sent_at) VALUES(?,'RESET',?,DATE_ADD(NOW(), INTERVAL 10 MINUTE),NOW())", email, EmailCodeVerifier.hash(code));
    }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        String csrf() { return cookies.getCookieStore().getCookies().stream().filter(c -> "XSRF-TOKEN".equals(c.getName())).map(HttpCookie::getValue).findFirst().orElse(null); }
        void login(String password) throws Exception {
            call("GET", "/auth/csrf", null, null);
            var result=call("POST", "/auth/login", "{\"email\":\""+email+"\",\"password\":\""+password+"\"}",csrf());
            assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(200);
        }
        HttpResponse<String> call(String method, String path, String body, String token) throws Exception {
            URI uri=URI.create("http://127.0.0.1:"+environment.getProperty("local.server.port")+"/api/v1"+path);
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .method(method, body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            if (body!=null) request.header("Content-Type","application/json");
            if (token!=null) request.header("X-XSRF-TOKEN",token);
            return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
