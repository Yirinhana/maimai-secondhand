package com.maimai.community;

import com.maimai.admin.service.AdminOperationsService;
import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.community.dto.CommunityDtos.*;
import com.maimai.community.service.*;
import com.maimai.support.ai.SupportAiGateway;
import com.maimai.support.workflow.WorkflowAiService;
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
import java.sql.Statement;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MySQL joins and constraints; each test rolls back only its own fixtures. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkedGovernanceIT {
    @Autowired JdbcTemplate db;
    @Autowired WorkflowAiService workflow;
    @Autowired ReputationService reputation;
    @Autowired RatingService ratings;
    @Autowired ReportService reports;
    @Autowired ReportContextService contexts;
    @Autowired ProductDiscussionService discussions;
    @Autowired AdminOperationsService operations;
    @MockitoBean SupportAiGateway ai;
    long buyer,seller,other,admin,category,product,order;

    @BeforeEach void setup(){
        buyer=user("核查买家","USER");seller=user("核查卖家","SELLER");other=user("无关账号","USER");admin=user("核查管理员","SUPER_ADMIN");
        category=insert("INSERT INTO categories(name,sort,status) VALUES(?,1,'ACTIVE')","联动-"+UUID.randomUUID());
        product=insert("INSERT INTO products(seller_id,category_id,title,description,item_condition,price_cents,stock_available,region,delivery_methods,freight_cents,status) VALUES(?,?,'核查商品','描述无个人信息','GOOD',10000,5,'上海','MEETUP',0,'ON_SALE')",seller,category);
        db.update("INSERT INTO seller_applications(user_id,status,channel_status,intro) VALUES(?,'APPROVED','QUALIFIED','测试资料')",seller);
        order=order("SIMULATED");
        when(ai.configured()).thenReturn(true);when(ai.modelName()).thenReturn("hermes-test");when(ai.advise(anyString(),anyString())).thenReturn("请核实交付资料，模拟付款不发生真实扣款。");
        login(buyer,"USER");
    }
    @AfterEach void logout(){SecurityContextHolder.clearContext();}

    @Test void workflowEnforcesOwnershipAndMasksUserDataAndDeduplicates(){
        var key=UUID.randomUUID().toString();
        var result=workflow.ask("ORDER",order,"邮箱 somebody@example.test 电话13800138000",key);
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(workflow.ask("ORDER",order,"邮箱 somebody@example.test 电话13800138000",key).id()).isEqualTo(result.id());
        verify(ai,times(1)).advise(anyString(),argThat(text->text.contains("模拟")&&!text.contains("somebody@example.test")&&!text.contains("13800138000")));
        assertCode("AI_REQUEST_REUSED",()->workflow.ask("ORDER",order,"另一条问题",key));
        login(other,"USER");assertCode("NOT_FOUND",()->workflow.ask("ORDER",order,"读取他人订单",UUID.randomUUID().toString()));
        assertCode("NOT_FOUND",()->workflow.result(result.id(),other));
        assertCode("NOT_FOUND",()->workflow.ask("LISTING",product,"修改他人商品",UUID.randomUUID().toString()));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM ai_workflow_runs WHERE actor_id=?",Long.class,other)).isZero();
    }
    @Test void allWorkflowStagesUseAuthorizedRecordsAndFailuresStayVisible(){
        assertThat(workflow.ask("GUIDE",null,"下单步骤",UUID.randomUUID().toString()).status()).isEqualTo("SUCCEEDED");
        assertThat(workflow.ask("LISTING",null,"准备发布一台相机",UUID.randomUUID().toString()).status()).isEqualTo("SUCCEEDED");
        assertThat(workflow.ask("PRODUCT",product,"核查商品",UUID.randomUUID().toString()).status()).isEqualTo("SUCCEEDED");
        assertThat(workflow.ask("REVIEW",order,"如何客观评价",UUID.randomUUID().toString()).status()).isEqualTo("SUCCEEDED");
        when(ai.advise(anyString(),anyString())).thenThrow(BizException.tooMany("忙碌"));
        var result=workflow.ask("ORDER",order,"请求帮助",UUID.randomUUID().toString());
        assertThat(result.status()).isEqualTo("FAILED");assertThat(result.answer()).isNull();
        assertThat(db.queryForObject("SELECT fulfillment_status FROM orders WHERE id=?",String.class,order)).isEqualTo("COMPLETED");
    }
    @Test void sourceSeparatedReputationExcludesHistoricalHiddenRefundedAndInvalidRatings(){
        var rating=ratings.create(buyer,order,new RatingRequest(5,"如实说明交付过程"));
        long historical=order("HISTORICAL");
        db.update("INSERT INTO community_order_ratings(order_id,rater_id,ratee_id,rating,comment) VALUES(?,?,?,1,'历史体验')",historical,buyer,seller);
        var profile=reputation.profile(seller);
        assertThat(scope(profile,"SIMULATED").ratings()).isEqualTo(1);
        assertThat(scope(profile,"SIMULATED").averageRating()).isEqualTo(5.0);
        assertThat(scope(profile,"HISTORICAL").ratings()).isEqualTo(1);
        assertThat(profile.level()).isEqualTo("评价积累中");
        assertThat(scope(profile,"LIVE").averageRating()).isNull();
        db.update("UPDATE community_order_ratings SET ratee_id=? WHERE id=?",other,rating.id());
        assertThat(scope(reputation.profile(other),"SIMULATED").ratings()).isZero();
        db.update("UPDATE community_order_ratings SET ratee_id=?,is_hidden=1 WHERE id=?",seller,rating.id());
        assertThat(scope(reputation.profile(seller),"SIMULATED").ratings()).isZero();
        db.update("UPDATE community_order_ratings SET is_hidden=0 WHERE id=?",rating.id());
        db.update("UPDATE orders SET refund_status='FULL' WHERE id=?",order);
        assertThat(scope(reputation.profile(seller),"SIMULATED").ratings()).isZero();
        login(seller,"SELLER");assertCode("RATING_ORDER_INVALID",()->ratings.create(seller,order,new RatingRequest(5,"全退后评价")));
    }
    @Test void remindersAndRatingNotificationsReachBothPartiesOnlyOnce(){
        reputation.remindCompleted();reputation.remindCompleted();
        assertThat(db.queryForList("SELECT user_id FROM reputation_reminders WHERE order_id=?",Long.class,order)).containsExactlyInAnyOrder(buyer,seller);
        for(long user:List.of(buyer,seller))assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='RATING_REMINDER'",Long.class,user)).isEqualTo(1);
        ratings.create(buyer,order,new RatingRequest(4,"沟通顺畅"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='REPUTATION_UPDATED'",Long.class,seller)).isEqualTo(1);
        assertCode("RATING_DUPLICATE",()->ratings.create(buyer,order,new RatingRequest(5,"重复评价")));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='REPUTATION_UPDATED'",Long.class,seller)).isEqualTo(1);
    }
    @Test void reportsKeepOriginalEvidenceAndAiCannotPunishOrChangeReputation(){
        login(seller,"SELLER");var comment=discussions.create(seller,product,new ProductDiscussionService.CreateComment("要求转账后线下联系的待核查留言",null));
        login(buyer,"USER");var report=reports.create(buyer,new ReportCreateRequest("PRODUCT_COMMENT",comment.id(),"涉嫌诱导离开平台，请核查"));
        assertCode("REPORT_DUPLICATE",()->reports.create(buyer,new ReportCreateRequest("PRODUCT_COMMENT",comment.id(),"再次报告同一条留言")));
        assertCode("FORBIDDEN",()->contexts.detail(report.id()));
        db.update("UPDATE product_comments SET content='修改后的内容' WHERE id=?",comment.id());
        login(admin,"SUPER_ADMIN");var context=contexts.assess(report.id());
        assertThat(context.sourceText()).contains("待核查留言");assertThat(context.currentText()).isEqualTo("修改后的内容");assertThat(context.targetOwnerId()).isEqualTo(seller);
        assertThat(context.targetUrl()).isEqualTo("/products/"+product+"#discussion-"+comment.id());
        assertThat(context.assessments().getFirst().status()).isEqualTo("SUCCEEDED");
        assertThat(db.queryForObject("SELECT status FROM community_reports WHERE id=?",String.class,report.id())).isEqualTo("PENDING");
        assertThat(reputation.profile(seller).confirmedContentActions()).isZero();
        reports.process(admin,report.id(),ReportAction.HIDE,"核对原文后确认违规，隐藏处理");
        assertThat(reputation.profile(seller).confirmedContentActions()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='CONTENT_MODERATED'",Long.class,seller)).isEqualTo(1);
        assertCode("REPORT_PROCESSED",()->contexts.assess(report.id()));
    }
    @Test void failedAiStillLeavesReportForHumanAndRejectionDoesNotPunishReporter(){
        var report=reports.create(buyer,new ReportCreateRequest("PRODUCT",product,"对商品描述存在疑问，请核对"));
        login(admin,"SUPER_ADMIN");when(ai.advise(anyString(),anyString())).thenThrow(BizException.tooMany("忙碌"));
        assertThat(contexts.assess(report.id()).assessments().getFirst().status()).isEqualTo("FAILED");
        reports.process(admin,report.id(),ReportAction.REJECT,"暂缺证明材料，可补充凭据后再核查");
        assertThat(reputation.profile(buyer).confirmedContentActions()).isZero();
        assertThat(reputation.profile(seller).confirmedContentActions()).isZero();
    }
    @Test void dashboardUsesActualPaymentSourcesAndLinkedOrderAccounts(){
        long historical=order("HISTORICAL"),unverified=order("UNVERIFIED");
        login(admin,"SUPER_ADMIN");var dashboard=operations.dashboard();
        assertThat(dashboard.sources()).extracting(AdminOperationsService.SourceTotals::source).containsExactlyInAnyOrder("LIVE","SIMULATED","HISTORICAL","UNPAID","UNVERIFIED");
        assertThat(dashboard.trend()).hasSize(14);
        var no=db.queryForObject("SELECT order_no FROM orders WHERE id=?",String.class,order);
        var detail=operations.order(no);
        assertThat(detail.order().buyerNickname()).isEqualTo("核查买家");assertThat(detail.order().sellerNickname()).isEqualTo("核查卖家");
        assertThat(detail.order().source()).isEqualTo("SIMULATED");assertThat(detail.payments()).hasSize(1);
        assertThat(detail.items()).hasSize(1);assertThat(detail.items().getFirst().productId()).isEqualTo(product);
        assertThat(operations.orders("UNVERIFIED",null,0,50).items()).extracting(AdminOperationsService.OrderRow::id).contains(unverified);
        login(admin,"OPERATOR");assertThat(operations.dashboard().sources()).allMatch(s->s.paidCents()==null&&s.calculatedFeeCents()==null);
        assertCode("FORBIDDEN",()->operations.order(no));assertCode("FORBIDDEN",()->operations.orders(null,null,0,20));
        login(buyer,"USER");assertCode("FORBIDDEN",()->operations.dashboard());assertCode("FORBIDDEN",()->operations.order(no));
    }
    @Test void missingPaymentReceiptCannotBecomeAValidReview(){
        long unverified=order("UNVERIFIED");
        assertCode("RATING_ORDER_INVALID",()->ratings.create(buyer,unverified,new RatingRequest(5,"缺少付款凭据")));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM community_order_ratings WHERE order_id=?",Long.class,unverified)).isZero();
    }
    @Test void interactiveExperienceIsSimulatedAndClosedUnpaidIsNotAMissingReceipt(){
        db.update("UPDATE orders SET experience_source='maimai-experience-checkout-v1' WHERE id=?",order);
        var rating=ratings.create(buyer,order,new RatingRequest(4,"站内模拟付款后完成交付流程"));
        assertThat(rating.paymentSource()).isEqualTo("SIMULATED");
        assertThat(scope(reputation.profile(seller),"SIMULATED").ratings()).isEqualTo(1);
        reputation.remindCompleted();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM reputation_reminders WHERE order_id=?",Long.class,order)).isEqualTo(2);
        long closed=order("UNVERIFIED");db.update("UPDATE orders SET pay_status='CLOSED',fulfillment_status='CLOSED' WHERE id=?",closed);
        login(admin,"SUPER_ADMIN");
        assertThat(operations.orders("UNPAID",null,0,50).items()).extracting(AdminOperationsService.OrderRow::id).contains(closed);
    }
    private ReputationService.Scope scope(ReputationService.Profile p,String source){return p.scopes().stream().filter(s->s.source().equals(source)).findFirst().orElseThrow();}
    private long user(String name,String role){long id=insert("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test-hash',?,'ACTIVE')",UUID.randomUUID()+"@governance.test",name);db.update("INSERT INTO user_roles(user_id,role) VALUES(?,?)",id,role);return id;}
    private long order(String source){
        long id=insert("INSERT INTO orders(order_no,batch_id,buyer_id,seller_id,delivery_method,goods_amount_cents,freight_cents,total_cents,platform_fee_cents,fulfillment_status,pay_status,refund_status,expires_at,completed_at,experience_source) VALUES(?,1,?,?,'MEETUP',10000,0,10000,3,'COMPLETED','PAID','NONE',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),UTC_TIMESTAMP(6),?)","LG"+UUID.randomUUID().toString().replace("-","").substring(0,28),buyer,seller,"HISTORICAL".equals(source)?"linked-test":null);
        db.update("INSERT INTO order_items(order_id,product_id,title,item_condition,price_cents,quantity) VALUES(?,?,'核查商品快照','GOOD',10000,1)",id,product);
        if("SIMULATED".equals(source)||"LIVE".equals(source))db.update("INSERT INTO payment_requests(pay_no,order_id,amount_cents,channel,status,simulated) VALUES(?,?,10000,'MOCK','PAID',?)","LP"+UUID.randomUUID().toString().replace("-","").substring(0,28),id,"SIMULATED".equals(source));
        return id;
    }
    private long insert(String sql,Object...args){var key=new GeneratedKeyHolder();db.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();}
    private void login(long id,String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id,"local-test","核查主体",Set.of(role)),null,List.of()));}
    private void assertCode(String code,org.assertj.core.api.ThrowableAssert.ThrowingCallable action){assertThatThrownBy(action).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo(code));}
}
