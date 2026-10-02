package com.maimai.support;

import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.support.ai.HermesAiGateway;
import com.maimai.support.dto.SupportDtos.*;
import com.maimai.support.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"MAIMAI_HERMES_BASE_URL=", "MAIMAI_HERMES_TOKEN=", "MAIMAI_HERMES_MODEL=", "MAIMAI_HERMES_READONLY_VERIFIED=false"})
@ActiveProfiles("test")
class SupportTicketServiceTest {
    @Autowired SupportTicketService tickets;
    @Autowired SupportAiService defaultAi;
    @Autowired JdbcTemplate jdbc;
    long owner,other,seller,staff,secondStaff,operator,admin,order,foreignOrder;
    String orderNo,foreignOrderNo;
    final List<Long> users=new ArrayList<>();
    @BeforeEach void setup() {
        owner=user("USER");other=user("USER");seller=user("SELLER");staff=user("SUPPORT");secondStaff=user("SUPPORT");operator=user("OPERATOR");admin=user("SUPER_ADMIN");
        orderNo="ST"+UUID.randomUUID().toString().replace("-","").substring(0,28);
        foreignOrderNo="ST"+UUID.randomUUID().toString().replace("-","").substring(0,28);
        order=order(orderNo,owner,seller);foreignOrder=order(foreignOrderNo,other,seller); login(owner,"USER");
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        if(users.isEmpty()) return;
        var ids=String.join(",",Collections.nCopies(users.size(),"?"));Object[] args=users.toArray();
        jdbc.update("DELETE FROM support_action_logs WHERE actor_id IN ("+ids+")",args);
        jdbc.update("DELETE FROM support_messages WHERE ticket_id IN (SELECT id FROM support_tickets WHERE owner_id IN ("+ids+"))",args);
        jdbc.update("DELETE FROM support_tickets WHERE owner_id IN ("+ids+")",args);
        jdbc.update("DELETE FROM orders WHERE id IN (?,?)",order,foreignOrder);
        jdbc.update("DELETE FROM notifications WHERE user_id IN ("+ids+")",args);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN ("+ids+")",args);
        jdbc.update("DELETE FROM users WHERE id IN ("+ids+")",args); users.clear();
    }

    @Test void ownerCanCreateAssociateOrderAndReadPagedMessages() {
        var t=create("我的订单问题");
        assertThat(t.ownerId()).isEqualTo(owner);assertThat(t.orderNo()).isEqualTo(orderNo);assertThat(t.status()).isEqualTo("OPEN");
        tickets.reply(t.id(),new WriteMessage("补充说明内容"));
        var page=tickets.messages(t.id(),0,1);
        assertThat(page.total()).isEqualTo(2);assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items().getFirst().body()).isEqualTo("补充说明内容");
        assertThat(tickets.mine(0,20).items()).extracting(Ticket::id).containsExactly(t.id());
    }

    @Test void otherUserCannotSeeOrReplyAndForeignOrderCannotBeAttached() {
        var t=create("私密求助正文");login(other,"USER");
        code("NOT_FOUND",()->tickets.detail(t.id())); code("NOT_FOUND",()->tickets.messages(t.id(),0,20));
        code("NOT_FOUND",()->tickets.reply(t.id(),new WriteMessage("越权回复")));
        assertThat(tickets.mine(0,20).items()).isEmpty();
        login(owner,"USER");
        code("NOT_FOUND",()->tickets.create(new CreateTicket("关联其他人的订单","不能接入",foreignOrderNo)));
        assertThat(count("SELECT COUNT(*) FROM support_tickets WHERE owner_id=?",owner)).isEqualTo(1);
    }

    @Test void operatorCannotPerformHumanSupportAndStaffMustClaim() {
        var t=create("需要人工客服");login(operator,"OPERATOR");
        code("FORBIDDEN",()->tickets.queue(null,0,20));code("NOT_FOUND",()->tickets.detail(t.id()));
        code("FORBIDDEN",()->tickets.transition(t.id(),"CLAIM"));
        login(staff,"SUPPORT");
        assertThat(tickets.queue(null,0,50).items()).anyMatch(x->x.id()==t.id());
        code("TICKET_NOT_ASSIGNED",()->tickets.reply(t.id(),new WriteMessage("未接管不能回复")));
        var claimed=tickets.transition(t.id(),"CLAIM");assertThat(claimed.assignedTo()).isEqualTo(staff);
        assertThat(tickets.reply(t.id(),new WriteMessage("人工客服已回复")).authorKind()).isEqualTo("STAFF");
        login(secondStaff,"SUPPORT");code("TICKET_ASSIGNED",()->tickets.transition(t.id(),"CLAIM"));
        code("TICKET_NOT_ASSIGNED",()->tickets.reply(t.id(),new WriteMessage("不能冒名处理")));
    }

    @Test void assignedStaffCanCloseReopenAndSuperAdminCanTakeOver() {
        var t=create("人工处理状态");login(staff,"SUPPORT");tickets.transition(t.id(),"CLAIM");
        assertThat(tickets.transition(t.id(),"CLOSE").status()).isEqualTo("CLOSED");
        login(owner,"USER");code("TICKET_CLOSED",()->tickets.reply(t.id(),new WriteMessage("关闭后不能追加")));
        code("FORBIDDEN",()->tickets.transition(t.id(),"REOPEN"));
        login(staff,"SUPPORT");var reopened=tickets.transition(t.id(),"REOPEN");
        assertThat(reopened.status()).isEqualTo("OPEN");assertThat(reopened.assignedTo()).isNull();
        tickets.transition(t.id(),"CLAIM"); login(admin,"SUPER_ADMIN");
        assertThat(tickets.transition(t.id(),"CLAIM").assignedTo()).isEqualTo(admin);
        assertThat(tickets.transition(t.id(),"CLOSE").status()).isEqualTo("CLOSED");
        assertThat(count("SELECT COUNT(*) FROM support_action_logs WHERE ticket_id=?",t.id())).isEqualTo(7);
    }

    @Test void competingStaffClaimHasOneWinnerAndConcurrentRepliesAreAllPersisted() throws Exception {
        var t=create("并发人工回复");
        var claims=parallel(List.of(()->as(staff,"SUPPORT",()->claim(t.id())),()->as(secondStaff,"SUPPORT",()->claim(t.id()))));
        assertThat(claims).containsExactlyInAnyOrder("CLAIMED","TICKET_ASSIGNED");
        long winner=tickets.detail(t.id()).assignedTo();
        var replies=parallel(Collections.nCopies(12,()->as(winner,"SUPPORT",()->{tickets.reply(t.id(),new WriteMessage("并发追加人工答复"));return "REPLIED";})));
        assertThat(replies).allMatch("REPLIED"::equals);
        assertThat(tickets.messages(t.id(),0,50).total()).isEqualTo(13);
        assertThat(count("SELECT COUNT(*) FROM support_action_logs WHERE ticket_id=? AND action='CLAIM'",t.id())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM support_action_logs WHERE ticket_id=? AND action='MESSAGE'",t.id())).isEqualTo(12);
    }

    @Test void closeAndReplyAreSerializedWithoutWritingAfterClosure() throws Exception {
        var t=create("关闭与回复同时发生"); login(staff,"SUPPORT"); tickets.transition(t.id(),"CLAIM");
        var results=parallel(List.of(()->as(staff,"SUPPORT",()->{tickets.transition(t.id(),"CLOSE");return "CLOSED";}),
                ()->as(staff,"SUPPORT",()->{try{tickets.reply(t.id(),new WriteMessage("并发结束前回复"));return "REPLIED";}catch(BizException ex){return ex.getCode();}})));
        assertThat(results).contains("CLOSED"); assertThat(results).allMatch(x->Set.of("CLOSED","REPLIED","TICKET_CLOSED").contains(x));
        assertThat(tickets.detail(t.id()).status()).isEqualTo("CLOSED");
        code("TICKET_CLOSED",()->tickets.reply(t.id(),new WriteMessage("关闭后不能落库")));
        assertThat(tickets.messages(t.id(),0,20).total()).isEqualTo(results.contains("REPLIED")?2:1);
    }

    @Test void defaultAiUnavailableDoesNotFakeMessageOrPreventHumanSupport() {
        var t=create("AI未开通也能求助");
        code("AI_NOT_CONFIGURED",()->defaultAi.explain(t.id(),SupportFaq.Topic.AFTERSALES));
        assertThat(tickets.messages(t.id(),0,20).total()).isEqualTo(1);
        tickets.reply(t.id(),new WriteMessage("改由人工处理即可"));assertThat(tickets.messages(t.id(),0,20).total()).isEqualTo(2);
    }

    @Test void aiReceivesOnlyFixedFaqAndCannotRefundEvenIfItClaimsOtherwise() {
        String privateBody="私人邮箱 owner@example.test，电话13812345678，地址某街12号，付款凭据凭证ABC";
        var t=create(privateBody);var sent=new AtomicReference<String>();
        var gateway=new HermesAiGateway("http://127.0.0.1:8642","test","hermes-agent",true,(u,k,b)->{
            sent.set(b);return "{\"choices\":[{\"message\":{\"content\":\"已退款并执行终端命令（这是测试模型虚构回答）\"}}]}";
        });
        var result=new SupportAiService(tickets,gateway).explain(t.id(),SupportFaq.Topic.PAYMENT);
        assertThat(result.authorKind()).isEqualTo("AI");assertThat(result.authorId()).isNull();
        assertThat(result.body()).startsWith(SupportFaq.NOTICE);
        assertThat(sent.get()).doesNotContain(privateBody,"owner@example.test","13812345678",orderNo,"凭证ABC");
        assertThat(jdbc.queryForObject("SELECT refund_status FROM orders WHERE id=?",String.class,order)).isEqualTo("NONE");
        assertThat(count("SELECT COUNT(*) FROM support_action_logs WHERE ticket_id=? AND action='AI_FAQ'",t.id())).isEqualTo(1);
        login(other,"USER");code("FORBIDDEN",()->new SupportAiService(tickets,gateway).explain(t.id(),SupportFaq.Topic.PAYMENT));
    }

    @Test void inputAndRateLimitsRejectWithoutCreatingPartialTickets() {
        code("INVALID_CONTENT",()->tickets.create(new CreateTicket(" ","正文",null)));
        code("PAGE_INVALID",()->tickets.mine(Integer.MAX_VALUE,50));
        for(int i=0;i<4;i++) tickets.create(new CreateTicket("限流测试"+i,"必要的说明",null));
        // The invalid attempt also consumes capacity, preventing abusive validation retries.
        code("RATE_LIMITED",()->tickets.create(new CreateTicket("超过频率","说明",null)));
        assertThat(count("SELECT COUNT(*) FROM support_tickets WHERE owner_id=?",owner)).isEqualTo(4);
    }

    private Ticket create(String body){return tickets.create(new CreateTicket("测试人工工单",body,orderNo));}
    private String claim(long id){try{tickets.transition(id,"CLAIM");return "CLAIMED";}catch(BizException e){return e.getCode();}}
    private long user(String role){long id=insert("INSERT INTO users(email,password_hash,nickname,status) VALUES (?,'test-hash','工单测试用户','ACTIVE')","support-"+UUID.randomUUID()+"@local.test");users.add(id);jdbc.update("INSERT INTO user_roles(user_id,role) VALUES (?,?)",id,role);return id;}
    private long order(String no,long buyer,long seller){return insert("""
            INSERT INTO orders(order_no,batch_id,buyer_id,seller_id,delivery_method,goods_amount_cents,total_cents,fulfillment_status,pay_status,refund_status,expires_at,completed_at)
            VALUES (?,1,?,?,'MEET',10000,10000,'COMPLETED','PAID','NONE',DATE_ADD(NOW(6),INTERVAL 1 DAY),NOW(6))""",no,buyer,seller);}
    private long insert(String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},key);return key.getKey().longValue();}
    private long count(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    private void login(long actor,String role){var principal=new AuthenticatedUser(actor,"private-test","测试用户",Set.of(role));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    private String as(long actor,String role,Callable<String> work)throws Exception{login(actor,role);try{return work.call();}finally{SecurityContextHolder.clearContext();}}
    private List<String> parallel(List<Callable<String>> jobs)throws Exception{var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(4)){List<Future<String>> futures=new ArrayList<>();for(var job:jobs)futures.add(pool.submit(()->{start.await();return job.call();}));start.countDown();List<String> result=new ArrayList<>();for(var future:futures)result.add(future.get(20,TimeUnit.SECONDS));return result;}}
    private void code(String code,Runnable work){assertThatThrownBy(work::run).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo(code));}
}
