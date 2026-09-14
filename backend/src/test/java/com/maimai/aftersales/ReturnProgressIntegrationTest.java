package com.maimai.aftersales;

import com.maimai.admin.dto.AdminDtos.ResolveAftersaleRequest;
import com.maimai.admin.service.AdminAftersaleService;
import com.maimai.aftersales.domain.Aftersale;
import com.maimai.aftersales.dto.AftersaleDtos.*;
import com.maimai.aftersales.service.AftersaleService;
import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReturnProgressIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired AftersaleService cases;
    @Autowired AdminAftersaleService staff;
    long buyer,seller,other,admin,batch,order,caseId;
    String orderNo;

    @BeforeEach void fixtures() {
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("maimai_test");
        buyer=user();seller=user();other=user();admin=user();
        db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'SUPER_ADMIN')",admin);
        orderNo="RT"+UUID.randomUUID().toString().replace("-","").substring(0,24);
        db.update("INSERT INTO checkout_batches(batch_no,user_id,idempotency_key) VALUES(?,?,?)",orderNo,buyer,orderNo);
        batch=db.queryForObject("SELECT id FROM checkout_batches WHERE batch_no=?",Long.class,orderNo);
        db.update("INSERT INTO orders(order_no,batch_id,buyer_id,seller_id,delivery_method,goods_amount_cents,total_cents,platform_fee_cents,pay_status,fulfillment_status,expires_at,auto_confirm_at) "
                +"VALUES(?,?,?,?,'EXPRESS',10000,10000,3,'PAID','SHIPPED',DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 HOUR),DATE_ADD(UTC_TIMESTAMP(),INTERVAL 5 DAY))",orderNo,batch,buyer,seller);
        order=db.queryForObject("SELECT id FROM orders WHERE order_no=?",Long.class,orderNo);
        db.update("INSERT INTO payment_requests(pay_no,order_id,amount_cents,channel,status,simulated) VALUES(?,?,10000,'MOCK_LOCAL','PAID',1)",orderNo,order);
        as(buyer,"USER");
        caseId=cases.create(orderNo,new CreateAftersaleRequest(Aftersale.Type.RETURN_REFUND,"退货流程验证",2500L,0L,"测试说明")).id();
    }

    @AfterEach void cleanupOwnRecords() {
        SecurityContextHolder.clearContext();
        db.update("DELETE FROM admin_audit_logs WHERE admin_id=?",admin);
        for(long id:db.queryForList("SELECT id FROM aftersales WHERE order_id=?",Long.class,order))
            db.update("DELETE FROM aftersale_logs WHERE aftersale_id=?",id);
        for(String table:List.of("finance_allocation_expectations","trade_reminder_events","ledger_entries","refunds","aftersales","payment_requests"))
            db.update("DELETE FROM "+table+" WHERE order_id=?",order);
        db.update("DELETE FROM orders WHERE id=?",order);
        db.update("DELETE FROM checkout_batches WHERE id=?",batch);
        for(long id:new long[]{buyer,seller,other,admin}) {
            db.update("DELETE FROM notifications WHERE user_id=?",id);
            db.update("DELETE FROM user_roles WHERE user_id=?",id);
            db.update("DELETE FROM users WHERE id=?",id);
        }
    }

    @Test void returnApprovalNeedsValidAddressAndProtectsPrivateContact() {
        as(seller,"USER");
        assertThatThrownBy(()->cases.respond(caseId,new RespondRequest(true,"同意退货")))
                .isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo("RETURN_ADDRESS_REQUIRED"));
        assertThat(cases.detail(caseId).status()).isEqualTo("PENDING_SELLER");
        assertThat(cases.detail(caseId).returnDeadline()).isNull();
        var approved=approve();
        assertThat(approved.returnRecipient()).isEqualTo("测试收件人");
        assertThat(approved.returnPhone()).isEqualTo("13800000000");
        assertThat(approved.returnAddress()).contains("黄浦区");
        assertThat(approved.returnDeadline()).isBetween(Instant.now().plus(Duration.ofDays(7)).minusSeconds(10),Instant.now().plus(Duration.ofDays(7)).plusSeconds(1));
        as(buyer,"USER");assertThat(cases.detail(caseId).returnAddress()).isEqualTo(approved.returnAddress());
        as(other,"OPERATOR");assertThatThrownBy(()->cases.detail(caseId)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getStatus().value()).isEqualTo(404));
        as(other,"SUPPORT");assertThat(cases.detail(caseId).returnPhone()).isEqualTo("13800000000");
    }

    @Test void shippedReturnCanEscalateWhenSellerNeverAcknowledgesReceipt() {
        ship();as(buyer,"USER");
        var manual=cases.escalate(caseId);
        assertThat(manual.status()).isEqualTo("PENDING_MANUAL");
        assertThat(manual.returnReceivedAt()).isNull();
        assertThat(count("SELECT confirm_paused FROM orders WHERE id=?",order)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id=?",order)).isZero();
        as(admin,"SUPER_ADMIN");
        staff.resolve(caseId,new ResolveAftersaleRequest("REFUND","人工核查后同意模拟退款",null,null));
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id=? AND status='SUCCESS'",order)).isEqualTo(1);
    }

    @Test void receiveIsIdempotentAndInspectionTimeoutEscalatesOnlyOnce() throws Exception {
        ship();
        List<String> results=parallel(8,()->{as(seller,"USER");return cases.receiveReturn(caseId).returnReceivedAt().toString();});
        assertThat(results.stream().distinct()).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM aftersale_logs WHERE aftersale_id=? AND action='RETURN_RECEIVED'",caseId)).isEqualTo(1);
        as(seller,"USER");var received=cases.detail(caseId);
        assertThat(Duration.between(received.returnReceivedAt(),received.returnInspectionDeadline())).isEqualTo(Duration.ofHours(48));
        db.update("UPDATE aftersales SET return_inspection_deadline=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE id=?",caseId);
        parallel(8,()->{cases.escalateExpired(caseId);return "checked";});
        assertThat(count("SELECT COUNT(*) FROM aftersale_logs WHERE aftersale_id=? AND action='ESCALATE_MANUAL'",caseId)).isEqualTo(1);
        assertThat(cases.detail(caseId).status()).isEqualTo("PENDING_MANUAL");
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id=?",order)).isZero();
    }

    @Test void concurrentConfirmationRefundsOnceAndPreservesSignedReceiptEvidence() throws Exception {
        ship();
        var results=parallel(8,()->{as(seller,"USER");try {cases.confirmReturn(caseId);return "REFUNDED";}catch(BizException error){return error.getCode();}});
        assertThat(results.stream().filter("REFUNDED"::equals).count()).isEqualTo(1);
        as(seller,"USER");var result=cases.detail(caseId);
        assertThat(result.status()).isEqualTo("RESOLVED");assertThat(result.returnReceivedAt()).isNotNull();
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id=?",order)).isEqualTo(1);
        assertThat(count("SELECT SUM(platform_fee_refund_cents) FROM refunds WHERE order_id=?",order)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM aftersale_logs WHERE aftersale_id=? AND action='RETURN_RECEIVED'",caseId)).isEqualTo(1);
    }

    @Test void returnAddressDisputeAndInvalidTrackingDoNotLoseBuyersManualRoute() {
        approve();as(buyer,"USER");
        assertThatThrownBy(()->cases.returnShip(caseId,new ReturnShipRequest("shunfeng","invalid\nnumber"))).isInstanceOf(BizException.class);
        assertThat(cases.detail(caseId).status()).isEqualTo("PENDING_RETURN");
        assertThat(cases.escalate(caseId).status()).isEqualTo("PENDING_MANUAL");
        as(other,"USER");assertThatThrownBy(()->cases.receiveReturn(caseId)).isInstanceOf(BizException.class);
    }

    @Test void resolvedDecisionCanBeAppealedWithoutAutomaticallyPayingAgain() {
        ship();as(seller,"USER");cases.confirmReturn(caseId);
        as(buyer,"USER");assertThat(cases.escalate(caseId).status()).isEqualTo("PENDING_MANUAL");
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id=?",order)).isEqualTo(1);
        assertThatThrownBy(()->cases.escalate(caseId)).isInstanceOf(BizException.class);
    }

    private AftersaleDetail approve() {
        as(seller,"USER");return cases.respond(caseId,new RespondRequest(true,"同意退货","测试收件人","13800000000","上海市黄浦区本地测试地址10号"));
    }

    @Test void expiredSellerResponseMovesToManualBeforeARejectionCanResumeReceipt() {
        db.update("UPDATE aftersales SET seller_deadline=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE id=?",caseId);
        as(seller,"USER");
        assertThat(cases.respond(caseId,new RespondRequest(false,"超时后的拒绝")).status()).isEqualTo("PENDING_MANUAL");
        assertThat(count("SELECT confirm_paused FROM orders WHERE id=?",order)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM aftersale_logs WHERE aftersale_id=? AND action='SELLER_REJECT'",caseId)).isZero();
    }

    @Test void expiredInspectionCannotRaceSchedulerToExecuteSellerRefund() {
        ship();as(seller,"USER");cases.receiveReturn(caseId);
        db.update("UPDATE aftersales SET return_inspection_deadline=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE id=?",caseId);
        assertThat(cases.confirmReturn(caseId).status()).isEqualTo("PENDING_MANUAL");
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id=?",order)).isZero();
    }

    @Test void sellerDisabledAfterReturnWasOpenedIsEscalatedWithoutWaitingFortyEightHours() {
        ship();db.update("UPDATE users SET status='DISABLED' WHERE id=?",seller);
        cases.escalateExpired(caseId);as(buyer,"USER");
        assertThat(cases.detail(caseId).status()).isEqualTo("PENDING_MANUAL");
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id=?",order)).isZero();
    }
    private void ship() {approve();as(buyer,"USER");cases.returnShip(caseId,new ReturnShipRequest("shunfeng","RET123456789"));}
    private long count(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
    private long user(){String email="returns-"+UUID.randomUUID()+"@example.test";db.update("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test-only','退货测试','ACTIVE')",email);return db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);}
    private void as(long id,String role){var c=SecurityContextHolder.createEmptyContext();c.setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id,"test@example.test","测试",Set.of(role)),null,List.of()));SecurityContextHolder.setContext(c);}
    private <T> List<T> parallel(int n,Supplier<T> work) throws Exception {
        try(var pool=Executors.newFixedThreadPool(n)){var futures=new ArrayList<Future<T>>();for(int i=0;i<n;i++)futures.add(pool.submit(()->{try{return work.get();}finally{SecurityContextHolder.clearContext();}}));var results=new ArrayList<T>();for(var f:futures)results.add(f.get(30,TimeUnit.SECONDS));return results;}
    }
}
