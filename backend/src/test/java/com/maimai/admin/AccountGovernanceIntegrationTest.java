package com.maimai.admin;

import com.maimai.admin.dto.AdminDtos.UserStatusRequest;
import com.maimai.admin.dto.GovernanceDtos.*;
import com.maimai.admin.service.*;
import com.maimai.catalog.service.CategoryAvailability;
import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.identity.dto.AccountClosureDtos.ClosureRequest;
import com.maimai.identity.service.AccountClosureService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.net.*;
import java.net.http.*;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Own fixtures only, real MySQL transactions and independent HTTP sessions. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.hikari.maximum-pool-size=4")
@ActiveProfiles("test")
class AccountGovernanceIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired AdminRoleService roles;
    @Autowired AdminUserService users;
    @Autowired AdminCategoryService categories;
    @Autowired AccountClosureService closures;
    @Autowired com.maimai.aftersales.service.AftersaleService aftersales;
    @Autowired CategoryAvailability availability;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PasswordEncoder passwords;
    @Autowired Environment environment;
    final List<Long> ids=new ArrayList<>(),categoryIds=new ArrayList<>(),orderIds=new ArrayList<>(),productIds=new ArrayList<>();
    long admin,owner,other,operator;
    String hash;

    @BeforeEach void setup() {
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("maimai_test");
        hash=passwords.encode("Governance#123");
        admin=user("SUPER_ADMIN");owner=user("USER");other=user("USER");operator=user("OPERATOR");login(admin,"SUPER_ADMIN");
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        for(long id:orderIds) {
            db.update("DELETE FROM refunds WHERE order_id=?",id);
            db.update("DELETE FROM aftersales WHERE order_id=?",id);
            db.update("DELETE FROM orders WHERE id=?",id);
        }
        for(long id:productIds) db.update("DELETE FROM products WHERE id=?",id);
        for(long id:categoryIds) db.update("DELETE FROM categories WHERE id=?",id);
        for(long id:ids) {
            db.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME=(SELECT email FROM users WHERE id=?)",id);
            db.update("DELETE FROM account_closure_requests WHERE user_id=?",id);
            db.update("DELETE FROM admin_audit_logs WHERE admin_id=?",id);
            db.update("DELETE FROM notifications WHERE user_id=?",id);
            db.update("DELETE FROM user_roles WHERE user_id=?",id);
            db.update("DELETE FROM users WHERE id=?",id);
        }
    }

    @Test void managementRolesRequireActiveSuperAdminAndPreserveBuyerSellerRoles() {
        db.update("INSERT INTO user_roles(user_id,role) VALUES (?,'SELLER')",owner);
        login(operator,"OPERATOR");code("FORBIDDEN",()->roles.change(owner,role("SUPPORT",true)));
        login(admin,"SUPER_ADMIN");code("ROLE_INVALID",()->roles.change(owner,role("SELLER",false)));
        assertThat(roles.change(owner,role("SUPPORT",true)).roles()).contains("USER","SELLER","SUPPORT");
        assertThat(roles.change(owner,role("SUPPORT",false)).roles()).containsExactly("SELLER","USER");
        assertThat(count("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND target_id=?",admin,owner)).isEqualTo(2);
        users.updateStatus(owner,new UserStatusRequest("DISABLED"));
        code("USER_INACTIVE",()->roles.change(owner,role("OPERATOR",true)));
    }

    @Test void lastActiveSuperAdminCannotBeRevokedDisabledOrClosed() {
        assertThat(count("SELECT COUNT(*) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.status='ACTIVE' AND r.role='SUPER_ADMIN'")).isEqualTo(1);
        code("LAST_SUPER_ADMIN",()->roles.change(admin,role("SUPER_ADMIN",false)));
        code("LAST_SUPER_ADMIN",()->users.updateStatus(admin,new UserStatusRequest("DISABLED")));
        code("LAST_SUPER_ADMIN",()->closures.request(close()));
    }

    @Test void simultaneousSelfRevocationsKeepOneActiveSuperAdmin() throws Exception {
        long second=user("SUPER_ADMIN");
        assertThat(count("SELECT COUNT(*) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.status='ACTIVE' AND r.role='SUPER_ADMIN'")).isEqualTo(2);
        var results=parallel(List.of(()->as(admin,"SUPER_ADMIN",()->roles.change(admin,role("SUPER_ADMIN",false))),
                ()->as(second,"SUPER_ADMIN",()->roles.change(second,role("SUPER_ADMIN",false)))));
        assertThat(results).containsExactlyInAnyOrder("OK","LAST_SUPER_ADMIN");
        assertThat(count("SELECT COUNT(*) FROM user_roles WHERE user_id IN (?,?) AND role='SUPER_ADMIN'",admin,second)).isEqualTo(1);
    }

    @Test void revokedActorCannotUseStalePrincipalToGrantRoles() {
        long second=user("SUPER_ADMIN");roles.change(second,role("SUPER_ADMIN",false));
        login(second,"SUPER_ADMIN");code("FORBIDDEN",()->roles.change(owner,role("SUPPORT",true)));
    }

    @Test void closureRejectsPasswordAndFiveWrongAttemptsAreLimited() {
        login(owner,"USER");for(int i=0;i<5;i++) code("PASSWORD_INVALID",()->closures.request(new ClosureRequest("Wrong#123","本人注销")));
        code("RATE_LIMITED",()->closures.request(close()));
        assertThat(count("SELECT COUNT(*) FROM account_closure_requests WHERE user_id=?",owner)).isZero();
        assertThat(db.queryForObject("SELECT status FROM users WHERE id=?",String.class,owner)).isEqualTo("ACTIVE");
    }

    @Test void closureWaitsForOpenTradeRecentCompletionAndUnknownCompletionDate() {
        long order=order(owner,other,"SHIPPED",null);login(owner,"USER");
        code("CLOSURE_TRADE_PENDING",()->closures.request(close()));
        db.update("UPDATE orders SET fulfillment_status='COMPLETED',completed_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 14 DAY) WHERE id=?",order);
        code("CLOSURE_TRADE_PENDING",()->closures.request(close()));
        db.update("UPDATE orders SET completed_at=NULL WHERE id=?",order);
        code("CLOSURE_TRADE_PENDING",()->closures.request(close()));
        assertThat(count("SELECT COUNT(*) FROM account_closure_requests WHERE user_id=?",owner)).isZero();
    }

    @Test void closureWaitsForActiveAftersalesAndProcessingRefundEvenAfterFifteenDays() {
        long order=order(owner,other,"COMPLETED",20);login(owner,"USER");
        long aftersale=insert("INSERT INTO aftersales(aftersale_no,order_id,buyer_id,type,reason,status) VALUES (?,?,?,'REFUND_ONLY','测试售后','SELLER_REJECTED')",unique("AS"),order,owner);
        code("CLOSURE_TRADE_PENDING",()->closures.request(close()));
        db.update("UPDATE aftersales SET status='CLOSED' WHERE id=?",aftersale);
        long refund=insert("INSERT INTO refunds(refund_no,order_id,channel,status) VALUES (?,?,'MOCK','PROCESSING')",unique("RF"),order);
        code("CLOSURE_TRADE_PENDING",()->closures.request(close()));
        db.update("UPDATE refunds SET status='SUCCESS' WHERE id=?",refund);
        assertThat(closures.request(close()).status()).isEqualTo("DISABLED");
        assertThat(count("SELECT COUNT(*) FROM orders WHERE id=?",order)).isEqualTo(1);
    }

    @Test void closurePersistsRequestPausesProductsAndCannotBeUndoneByStatusToggle() {
        var category=createCategory(null,"注销测试分类");
        long product=product(owner,category.id());long history=order(other,owner,"COMPLETED",16);
        login(owner,"USER");var response=closures.request(close());
        assertThat(response.notice()).contains("申请已记录","账号已停用","不代表所有数据已删除");
        assertThat(db.queryForObject("SELECT status FROM products WHERE id=?",String.class,product)).isEqualTo("OFF_SHELF");
        assertThat(count("SELECT COUNT(*) FROM orders WHERE id=?",history)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM account_closure_requests WHERE id=? AND user_id=?",response.id(),owner)).isEqualTo(1);
        login(admin,"SUPER_ADMIN");code("ACCOUNT_CLOSED",()->users.updateStatus(owner,new UserStatusRequest("ACTIVE")));
    }

    @Test void concurrentClosureCreatesExactlyOneDurableRequest() throws Exception {
        var results=parallel(List.of(()->as(owner,"USER",()->closures.request(close())),()->as(owner,"USER",()->closures.request(close()))));
        assertThat(results).containsExactlyInAnyOrder("OK","UNAUTHORIZED");
        assertThat(count("SELECT COUNT(*) FROM account_closure_requests WHERE user_id=?",owner)).isEqualTo(1);
    }

    @Test void disabledAccountCannotStartAftersalesWithAnAlreadyAuthenticatedPrincipal() {
        long order=order(owner,other,"COMPLETED",20);
        String orderNo=db.queryForObject("SELECT order_no FROM orders WHERE id=?",String.class,order);
        login(owner,"USER");closures.request(close());
        code("FORBIDDEN",()->aftersales.create(orderNo,new com.maimai.aftersales.dto.AftersaleDtos.CreateAftersaleRequest(
                com.maimai.aftersales.domain.Aftersale.Type.REFUND_ONLY,"旧身份不能发起售后",1L,0L,null)));
        assertThat(count("SELECT COUNT(*) FROM aftersales WHERE order_id=?",order)).isZero();
    }

    @Test void categoriesRequireOperatorAndRejectCyclesWhileRetainingReferencedProducts() {
        login(owner,"USER");code("FORBIDDEN",()->categories.list());
        long support=user("SUPPORT");login(support,"SUPPORT");code("FORBIDDEN",()->categories.list());
        login(operator,"OPERATOR");var parent=createCategory(null,"一级");var child=createCategory(parent.id(),"二级");
        long product=product(owner,child.id());
        code("CATEGORY_CYCLE",()->categories.save(parent.id(),category(child.id(),"循环","ACTIVE")));
        categories.save(parent.id(),category(null,"停用父类","DISABLED"));
        code("CATEGORY_INVALID",()->new TransactionTemplate(transactions).execute(status->{availability.requireActive(child.id());return null;}));
        code("CATEGORY_INVALID",()->categories.save(null,category(parent.id(),"不可发布子类","ACTIVE")));
        assertThat(db.queryForObject("SELECT category_id FROM products WHERE id=?",Long.class,product)).isEqualTo(child.id());
        assertThat(categories.list()).anyMatch(c->c.id()==parent.id()&&c.status().equals("DISABLED"));
        categories.save(parent.id(),category(null,"恢复并排序","ACTIVE"));
        new TransactionTemplate(transactions).execute(status->{availability.requireActive(child.id());return null;});
    }

    @Test void concurrentParentEditsCannotIntroduceCycle() throws Exception {
        var a=createCategory(null,"A");var b=createCategory(null,"B");
        var results=parallel(List.of(()->as(operator,"OPERATOR",()->categories.save(a.id(),category(b.id(),"A","ACTIVE"))),
                ()->as(operator,"OPERATOR",()->categories.save(b.id(),category(a.id(),"B","ACTIVE")))));
        assertThat(results).containsExactlyInAnyOrder("OK","CATEGORY_CYCLE");
    }

    @Test void realHttpClosureInvalidatesBothSessionsAndNeverClosesAnotherUser() throws Exception {
        Browser first=new Browser(),second=new Browser();first.login(owner);second.login(owner);
        var response=first.call("POST","/me/closure","{\"password\":\"Governance#123\",\"reason\":\"本人确认注销\",\"userId\":"+other+"}",first.csrf());
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        assertThat(first.call("GET","/auth/me",null,null).statusCode()).isEqualTo(401);
        assertThat(second.call("GET","/auth/me",null,null).statusCode()).isEqualTo(401);
        assertThat(db.queryForObject("SELECT status FROM users WHERE id=?",String.class,other)).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME=(SELECT email FROM users WHERE id=?)",owner)).isZero();
    }

    @Test void realHttpRejectsStaffCategoryAndRoleEscalation() throws Exception {
        long support=user("SUPPORT");Browser staff=new Browser();staff.login(support);
        assertThat(staff.call("GET","/admin/categories",null,null).statusCode()).isEqualTo(403);
        assertThat(staff.call("POST","/admin/users/"+owner+"/roles","{\"role\":\"SUPER_ADMIN\",\"grant\":true,\"reason\":\"越权尝试\"}",staff.csrf()).statusCode()).isEqualTo(403);
        assertThat(count("SELECT COUNT(*) FROM user_roles WHERE user_id=? AND role='SUPER_ADMIN'",owner)).isZero();
    }

    private RoleChange role(String value,boolean grant) {return new RoleChange(value,grant,"本地权限验收");}
    private ClosureRequest close() {return new ClosureRequest("Governance#123","本人确认注销，不删除交易记录");}
    private CategoryWrite category(Long parent,String name,String status) {return new CategoryWrite(parent,name,3,status,"分类治理验收");}
    private CategoryItem createCategory(Long parent,String name) {var c=categories.save(null,category(parent,name,"ACTIVE"));categoryIds.add(c.id());return c;}
    private long product(long seller,long category) {long id=insert("INSERT INTO products(seller_id,category_id,title,item_condition,price_cents,stock_available,region,delivery_methods,status) VALUES (?,?,'注销测试商品','GOOD',100,1,'上海','MEETUP','ON_SALE')",seller,category);productIds.add(id);return id;}
    private long order(long buyer,long seller,String state,Integer days) {
        long id=insert("INSERT INTO orders(order_no,batch_id,buyer_id,seller_id,delivery_method,goods_amount_cents,total_cents,fulfillment_status,pay_status,expires_at) VALUES (?,1,?,?,'MEETUP',100,100,?,'PAID',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY))",unique("GO"),buyer,seller,state);
        if(days!=null) db.update("UPDATE orders SET completed_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL ? DAY) WHERE id=?",days,id);
        orderIds.add(id);return id;
    }
    private long user(String role) {long id=insert("INSERT INTO users(email,password_hash,nickname,status) VALUES (?,?,'治理测试用户','ACTIVE')","governance-"+UUID.randomUUID()+"@example.invalid",hash);ids.add(id);db.update("INSERT INTO user_roles(user_id,role) VALUES (?,?)",id,role);return id;}
    private String unique(String prefix) {return prefix+UUID.randomUUID().toString().replace("-","").substring(0,28);}
    private long insert(String sql,Object...args) {var key=new GeneratedKeyHolder();db.update(c->{var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},key);return Objects.requireNonNull(key.getKey()).longValue();}
    private long count(String sql,Object...args) {return db.queryForObject(sql,Long.class,args);}
    private void login(long actor,String role) {var principal=new AuthenticatedUser(actor,"test","测试用户",Set.of(role));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    private String as(long actor,String role,Callable<?> job) throws Exception {login(actor,role);try{job.call();return "OK";}catch(BizException e){return e.getCode();}finally{SecurityContextHolder.clearContext();}}
    private List<String> parallel(List<Callable<String>> jobs) throws Exception {var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(jobs.size())){var results=new ArrayList<Future<String>>();for(var job:jobs)results.add(pool.submit(()->{start.await();return job.call();}));start.countDown();var values=new ArrayList<String>();for(var result:results)values.add(result.get(20,TimeUnit.SECONDS));return values;}}
    private void code(String code,Runnable work) {assertThatThrownBy(work::run).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo(code));}
    private class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        String csrf() {return cookies.getCookieStore().getCookies().stream().filter(c->"XSRF-TOKEN".equals(c.getName())).map(HttpCookie::getValue).findFirst().orElse(null);}
        void login(long actor) throws Exception {call("GET","/auth/csrf",null,null);var result=call("POST","/auth/login","{\"email\":\""+db.queryForObject("SELECT email FROM users WHERE id=?",String.class,actor)+"\",\"password\":\"Governance#123\"}",csrf());assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(200);}
        HttpResponse<String> call(String method,String path,String body,String token) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+environment.getProperty("local.server.port")+"/api/v1"+path)).timeout(Duration.ofSeconds(10)).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
            if(body!=null)request.header("Content-Type","application/json");if(token!=null)request.header("X-XSRF-TOKEN",token);return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
