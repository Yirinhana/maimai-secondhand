package com.maimai.governance;

import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.community.dto.CommunityDtos.ReportCreateRequest;
import com.maimai.community.service.ProductDiscussionService;
import com.maimai.community.service.ReportService;
import com.maimai.support.service.SupportTicketService;
import com.maimai.support.dto.SupportDtos.CreateTicket;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.sql.Statement;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real MySQL; all fixture mutations roll back. Model responses are controlled, never billed. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GovernanceIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired GovernanceWorker worker;
    @Autowired GovernanceSignals signals;
    @Autowired GovernanceSnapshot sources;
    @Autowired GovernanceService service;
    @Autowired ProductDiscussionService comments;
    @Autowired ReportService reports;
    @Autowired SupportTicketService tickets;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean GovernanceModel model;
    long buyer,seller,admin,operator,support,product,category;
    @BeforeEach void setup(){
        buyer=user("核查买家","USER");seller=user("核查卖家","SELLER");admin=user("核查管理员","SUPER_ADMIN");operator=user("核查运营","OPERATOR");support=user("核查客服","SUPPORT");
        category=insert("INSERT INTO categories(name,sort,status) VALUES(?,1,'ACTIVE')","预审-"+UUID.randomUUID());
        product=insert("INSERT INTO products(seller_id,category_id,title,description,item_condition,price_cents,stock_available,region,delivery_methods,freight_cents,status) VALUES(?,?,'核查相机','维修记录已披露','GOOD',10000,5,'上海','MEETUP',0,'ON_SALE')",seller,category);
        db.update("INSERT INTO seller_applications(user_id,status,channel_status,intro) VALUES(?,'APPROVED','QUALIFIED','测试资料')",seller);
        // Isolate the queue within this rollback transaction, not the user's application database.
        db.update("UPDATE ai_governance_signals SET captured_revision=revision");
        db.update("UPDATE ai_governance_jobs SET status='SUPERSEDED' WHERE status IN ('QUEUED','ANALYSING')");
        db.update("UPDATE ai_governance_control SET paused=0,next_call_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND)");
        db.update("INSERT INTO ai_governance_daily_usage(usage_date,calls) VALUES(UTC_DATE(),0) ON DUPLICATE KEY UPDATE calls=0");
        when(model.configured()).thenReturn(true);when(model.model()).thenReturn("local-controlled-test");
        doAnswer(i->assessment(i.getArgument(0),"COMPLAINT")).when(model).assess(any());
        login(buyer,"USER");
    }
    @AfterEach void logout(){SecurityContextHolder.clearContext();}
    GovernanceModel.Assessment assessment(GovernanceSnapshot.Snapshot source,String category){return new GovernanceModel.Assessment(category,"NORMAL","用户提出诉求，尚待核实",category.equals("NONE")?"":source.text().substring(0,Math.min(80,source.text().length())),"需要交易凭据","请按来源核对业务记录");}
    long comment(String text){return comments.create(buyer,product,new ProductDiscussionService.CreateComment(text,null)).id();}
    void capture(){db.update("UPDATE ai_governance_signals SET updated_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 31 SECOND) WHERE captured_revision<revision");while(worker.captureOne()){} }
    void ready(){db.update("UPDATE ai_governance_control SET next_call_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND)");}
    long count(String sql,Object...args){return Objects.requireNonNull(db.queryForObject(sql,Long.class,args));}
    long job(String type,long id){return count("SELECT id FROM ai_governance_jobs WHERE source_type=? AND source_id=? ORDER BY id DESC LIMIT 1",type,id);}
    String state(long id){return db.queryForObject("SELECT status FROM ai_governance_jobs WHERE id=?",String.class,id);}
    long caseId(long job){return count("SELECT case_id FROM ai_governance_case_jobs WHERE job_id=?",job);}

    @Test void ordinaryContentCostsNothingAndSuspiciousCommentsBecomeLinkedEvidence(){
        long normal=comment("你好，请问尺寸是多少？"),risk=comment("付款后没收到商品，希望协助退款");
        verifyNoInteractions(model);assertThat(worker.captureOne()).isFalse();capture();
        assertThat(state(job("PRODUCT_COMMENT",normal))).isEqualTo("FILTERED");
        assertThat(worker.processOne()).isTrue();login(admin,"SUPER_ADMIN");
        var detail=service.detail(caseId(job("PRODUCT_COMMENT",risk)));
        assertThat(detail.evidence()).hasSize(1);assertThat(detail.evidence().getFirst().actorId()).isEqualTo(buyer);
        assertThat(detail.evidence().getFirst().subjectId()).isEqualTo(seller);
        assertThat(detail.evidence().getFirst().sourceUrl()).isEqualTo("/products/"+product+"#discussion-"+risk);
        assertThat(count("SELECT COUNT(*) FROM community_reports WHERE resource_id=? AND resource_type='PRODUCT_COMMENT'",risk)).isZero();
        assertThat(db.queryForObject("SELECT status FROM product_comments WHERE id=?",String.class,risk)).isEqualTo("PUBLISHED");
        assertThat(count("SELECT calls FROM ai_governance_daily_usage WHERE usage_date=UTC_DATE()")).isEqualTo(1);
    }
    @Test void repeatedContentIsDeduplicatedAndRelatedCluesAreGrouped(){
        long a=comment("收到物品破损，请处理"),b=comment("希望协助退款，请处理");capture();worker.processOne();ready();worker.processOne();
        assertThat(caseId(job("PRODUCT_COMMENT",a))).isEqualTo(caseId(job("PRODUCT_COMMENT",b)));
        signals.touch("PRODUCT_COMMENT",a);capture();ready();assertThat(worker.processOne()).isFalse();verify(model,times(2)).assess(any());
        login(admin,"SUPER_ADMIN");assertThat(service.detail(caseId(job("PRODUCT_COMMENT",a))).item().evidenceCount()).isEqualTo(2);
    }
    @Test void failedRequestIsNotAutomaticallyBilledAgainAndCanBeRetriedManually(){
        long a=comment("商品与描述不符，需要售后");capture();doThrow(new IllegalStateException("provider secret text")).when(model).assess(any());
        worker.processOne();long j=job("PRODUCT_COMMENT",a);assertThat(state(j)).isEqualTo("FAILED");ready();assertThat(worker.processOne()).isFalse();
        login(admin,"SUPER_ADMIN");assertThat(service.detail(caseId(j)).evidence().getFirst().failureCode()).isEqualTo("GOV_RESPONSE_INVALID");
        service.retry(j);doAnswer(i->assessment(i.getArgument(0),"COMPLAINT")).when(model).assess(any());ready();worker.processOne();assertThat(state(j)).isEqualTo("COMPLETED");
    }
    @Test void ordinaryNegativeRatingAndReportsNeverAutomaticallyPunishOrDismiss(){
        long id=reports.create(buyer,new ReportCreateRequest("PRODUCT",product,"对商品情况存在疑问，请核实")).id();capture();
        doAnswer(i->assessment(i.getArgument(0),"NONE")).when(model).assess(any());worker.processOne();
        assertThat(db.queryForObject("SELECT status FROM community_reports WHERE id=?",String.class,id)).isEqualTo("PENDING");
        login(admin,"SUPER_ADMIN");assertThat(service.detail(caseId(job("REPORT",id))).item().category()).isEqualTo("NEEDS_REVIEW");
        assertThat(reports.adminDetail(id).id()).isEqualTo(id);
        login(buyer,"USER");denied(()->reports.adminDetail(id));
        assertThat(db.queryForObject("SELECT status FROM users WHERE id=?",String.class,seller)).isEqualTo("ACTIVE");
    }
    @Test void rolesCannotReadOrExportOtherLaneOrMutateBusinessByResolvingCase(){
        long c=comment("商品损坏，协助退款");
        long t=tickets.create(new CreateTicket("申请售后核查","投诉交付情况不符，请协助",null)).id();capture();worker.processOne();ready();worker.processOne();
        long publicCase=caseId(job("PRODUCT_COMMENT",c)),privateCase=caseId(job("SUPPORT_TICKET",t));
        login(operator,"OPERATOR");assertThat(service.detail(publicCase).item().lane()).isEqualTo("PUBLIC");denied(()->service.export(privateCase));denied(()->service.action(privateCase,"CLAIM","核对这条业务记录"));denied(()->service.pause(true));
        login(support,"SUPPORT");denied(()->service.detail(publicCase));assertThat(service.export(privateCase)).contains("核查材料","投诉交付情况不符");
        login(buyer,"USER");denied(service::overview);
        login(admin,"SUPER_ADMIN");service.action(publicCase,"CLAIM","正在核对原始留言");service.action(publicCase,"RESOLVE","完成核对且已记录依据");
        assertThat(service.detail(publicCase).actions()).hasSize(2);
        assertThat(db.queryForObject("SELECT status FROM product_comments WHERE id=?",String.class,c)).isEqualTo("PUBLISHED");
        assertThat(db.queryForObject("SELECT status FROM support_tickets WHERE id=?",String.class,t)).isEqualTo("OPEN");
    }
    @Test void configurationPauseBudgetAndIntervalPreventCalls(){
        comment("未收到物品，卖家失联");capture();when(model.configured()).thenReturn(false);assertThat(worker.processOne()).isFalse();
        when(model.configured()).thenReturn(true);db.update("UPDATE ai_governance_control SET paused=1");assertThat(worker.processOne()).isFalse();
        db.update("UPDATE ai_governance_control SET paused=0");db.update("UPDATE ai_governance_daily_usage SET calls=? WHERE usage_date=UTC_DATE()",worker.dailyLimit());assertThat(worker.processOne()).isFalse();
        db.update("UPDATE ai_governance_daily_usage SET calls=0 WHERE usage_date=UTC_DATE()");db.update("UPDATE ai_governance_control SET next_call_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR)");assertThat(worker.processOne()).isFalse();verify(model,never()).assess(any());
    }
    @Test void deletedAndEditedSourcesDoNotProduceOutdatedConclusions(){
        long c=comment("商品损坏要求退款");capture();doAnswer(i->{comments.delete(buyer,product,c);return assessment(i.getArgument(0),"COMPLAINT");}).when(model).assess(any());
        worker.processOne();assertThat(state(job("PRODUCT_COMMENT",c))).isEqualTo("SUPERSEDED");assertThat(count("SELECT COUNT(*) FROM ai_governance_case_jobs WHERE job_id=?",job("PRODUCT_COMMENT",c))).isZero();
    }
    @Test void restoredUnprocessedContentIsQueuedAgain(){
        long c=comment("商品破损第一条");capture();long first=job("PRODUCT_COMMENT",c);
        db.update("UPDATE product_comments SET content='商品破损第二条' WHERE id=?",c);signals.touch("PRODUCT_COMMENT",c);capture();assertThat(state(first)).isEqualTo("SUPERSEDED");
        db.update("UPDATE product_comments SET content='商品破损第一条' WHERE id=?",c);signals.touch("PRODUCT_COMMENT",c);capture();assertThat(state(first)).isEqualTo("QUEUED");worker.processOne();assertThat(state(first)).isEqualTo("COMPLETED");
    }
    @Test void leaseRecoveryDoesNotRetryAnUnknownBilledRequest(){
        long c=comment("商品破损，申请退款");capture();long j=job("PRODUCT_COMMENT",c);
        db.update("UPDATE ai_governance_jobs SET status='ANALYSING',attempts=1,started_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 4 MINUTE) WHERE id=?",j);worker.recoverExpired();
        assertThat(state(j)).isEqualTo("FAILED");assertThat(caseId(j)).isPositive();assertThat(worker.processOne()).isFalse();
    }
    @Test void signalsParticipateInBusinessRollback(){
        // JDBC savepoint tests the intake statement's participation without opening a separate connection.
        var status=org.springframework.test.context.transaction.TestTransaction.isActive();assertThat(status).isTrue();
        var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(Objects.requireNonNull(db.getDataSource()));
        try {var save=connection.setSavepoint();signals.touch("PRODUCT",product);connection.rollback(save);connection.releaseSavepoint(save);}
        catch(java.sql.SQLException e){throw new RuntimeException(e);}
        assertThat(count("SELECT COUNT(*) FROM ai_governance_signals WHERE source_type='PRODUCT' AND source_id=?",product)).isZero();
    }
    @Test void threeConsecutiveFailuresPauseTheQueueAndKeepEvidence(){
        for(int i=0;i<4;i++)comment("售后核查请求"+i);capture();
        doThrow(new IllegalStateException("unavailable")).when(model).assess(any());
        for(int i=0;i<3;i++){ready();assertThat(worker.processOne()).isTrue();}
        ready();assertThat(worker.processOne()).isFalse();
        login(admin,"SUPER_ADMIN");assertThat(service.overview().paused()).isTrue();assertThat(service.overview().pauseReason()).contains("连续三次");
        assertThat(service.overview().callsToday()).isEqualTo(3);
        service.pause(false);assertThat(service.overview().pauseReason()).isNull();
    }
    @Test void snapshotsCoverAllBusinessSourcesAndKeepPrivateChatOut(){
        long o=insert("INSERT INTO orders(order_no,batch_id,buyer_id,seller_id,delivery_method,goods_amount_cents,freight_cents,total_cents,platform_fee_cents,fulfillment_status,pay_status,refund_status,expires_at) VALUES(?,1,?,?,'MEETUP',100,0,100,0,'COMPLETED','PAID','NONE',UTC_TIMESTAMP(6))","GV"+UUID.randomUUID().toString().replace("-","").substring(0,20),buyer,seller);
        long r=insert("INSERT INTO community_order_ratings(order_id,rater_id,ratee_id,rating,comment) VALUES(?,?,?,2,'商品损坏')",o,buyer,seller);
        long d=insert("INSERT INTO community_demand_posts(author_id,title,description,budget_min_cents,budget_max_cents) VALUES(?,'求购相机','请勿私下转账',100,300)",buyer);
        long reply=insert("INSERT INTO community_demand_replies(demand_id,author_id,content) VALUES(?,?,'需要维修，有意联系')",d,seller);
        long a=insert("INSERT INTO aftersales(aftersale_no,order_id,buyer_id,type,reason) VALUES(?,?,?,'REFUND_ONLY','商品有故障')","GA"+UUID.randomUUID().toString().replace("-","").substring(0,20),o,buyer);
        assertThat(sources.load("ORDER_REVIEW",r).facts()).contains("评分=2");assertThat(sources.load("DEMAND_POST",d).actorId()).isEqualTo(buyer);
        assertThat(sources.load("DEMAND_REPLY",reply).actorId()).isEqualTo(seller);assertThat(sources.load("AFTERSALE",a).lane()).isEqualTo("SERVICE");
        assertThat(sources.load("PRODUCT",product).subjectId()).isEqualTo(seller);assertThat(sources.load("PRIVATE_MESSAGE",1)).isNull();
        db.update("UPDATE community_order_ratings SET ratee_id=? WHERE id=?",admin,r);assertThat(sources.load("ORDER_REVIEW",r)).isNull();
    }
    private long user(String name,String role){long id=insert("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test-hash',?,'ACTIVE')",UUID.randomUUID()+"@governance.test",name);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,?)",id,role);return id;}
    private long insert(String sql,Object...args){var key=new GeneratedKeyHolder();db.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();}
    private void login(long id,String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id,"local-test","核查主体",Set.of(role)),null,List.of()));}
    private void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action){assertThatThrownBy(action).isInstanceOf(BizException.class);}
}
