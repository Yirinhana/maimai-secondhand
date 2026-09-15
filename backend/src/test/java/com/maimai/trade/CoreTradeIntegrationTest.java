package com.maimai.trade;

import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.trade.dto.TradeDtos.*;
import com.maimai.trade.service.*;
import com.maimai.trade.repo.OrderRepository;
import com.maimai.trade.api.TradeOrderOps;
import com.maimai.payment.service.*;
import com.maimai.payment.dto.PaymentDtos.*;
import com.maimai.aftersales.service.AftersaleService;
import com.maimai.aftersales.domain.Aftersale;
import com.maimai.aftersales.dto.AftersaleDtos.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CoreTradeIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired CheckoutService checkout;
    @Autowired PaymentService payments;
    @Autowired MockPaymentService mockPayments;
    @Autowired com.maimai.payment.service.ExperiencePaymentService experiencePayments;
    @Autowired RefundService refunds;
    @Autowired DeliveryCodeService delivery;
    @Autowired FulfillmentService fulfillment;
    @Autowired TradeOrderOps orders;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderMaintenanceService maintenance;
    @Autowired AftersaleService aftersales;
    @Autowired BargainService bargains;
    @Autowired com.maimai.catalog.service.SellerProductService sellerProducts;
    @Autowired com.maimai.payment.finance.FinanceService finance;
    @Autowired TradeReminderService reminders;
    @Autowired com.maimai.catalog.service.ProductQueryService catalog;
    @Autowired com.maimai.catalog.service.ProductRevisionService revisions;
    @Autowired com.maimai.catalog.service.ProductImageService productImages;
    @Autowired com.maimai.catalog.admin.AdminProductService productReview;
    @Autowired com.maimai.catalog.repo.ProductRepository products;
    @Autowired com.maimai.config.MaimaiProperties properties;
    long seller, buyer, otherBuyer, product, address;
    final List<Long> fixtureProducts=new ArrayList<>();

    @BeforeEach void fixtures() {
        assertThat(db.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("maimai_test");
        assertThat(db.queryForObject("SELECT TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(),NOW())", Long.class)).isZero();
        seller=user(); buyer=user(); otherBuyer=user();
        db.update("INSERT INTO seller_applications(user_id,status,channel_status,intro) VALUES(?,'APPROVED','QUALIFIED','本地测试')",seller);
        db.update("INSERT INTO products(seller_id,category_id,title,item_condition,price_cents,stock_available,region,delivery_methods,freight_cents,status) VALUES(?,1,'集成测试商品','GOOD',10000,3,'测试城市','EXPRESS,MEETUP',1200,'ON_SALE')",seller);
        product=db.queryForObject("SELECT id FROM products WHERE seller_id=?",Long.class,seller);
        fixtureProducts.add(product);
        db.update("INSERT INTO addresses(user_id,receiver,phone,region,detail,is_default) VALUES(?,'测试买家','13800000000','测试城市','测试地址',1)",buyer);
        address=db.queryForObject("SELECT id FROM addresses WHERE user_id=?",Long.class,buyer);
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        for (Long id : db.queryForList("SELECT id FROM orders WHERE buyer_id IN (?,?)",Long.class,buyer,otherBuyer)) {
            for (String payNo : db.queryForList("SELECT pay_no FROM payment_requests WHERE order_id=?",String.class,id))
                db.update("DELETE FROM payment_notifications WHERE event_id LIKE ?",payNo+"%");
            for (Long aftersaleId : db.queryForList("SELECT id FROM aftersales WHERE order_id=?",Long.class,id))
                db.update("DELETE FROM aftersale_logs WHERE aftersale_id=?",aftersaleId);
            for(String table:List.of("finance_allocation_expectations","trade_reminder_events","ledger_entries","refunds","aftersales","payment_requests","shipments","delivery_codes","meetup_appointments","order_items"))
                db.update("DELETE FROM "+table+" WHERE order_id=?",id);
            db.update("DELETE FROM orders WHERE id=?",id);
        }
        db.update("DELETE FROM checkout_batches WHERE user_id IN (?,?)",buyer,otherBuyer);
        for(Long productId:fixtureProducts) {
            db.update("DELETE FROM product_revisions WHERE product_id=?",productId);
            db.update("DELETE FROM product_review_logs WHERE product_id=?",productId);
            db.update("DELETE FROM product_images WHERE product_id=?",productId);
            db.update("DELETE FROM admin_audit_logs WHERE target_type='product' AND target_id=?",productId);
            db.update("DELETE FROM stock_logs WHERE product_id=?",productId);
            db.update("DELETE FROM bargain_offers WHERE product_id=?",productId);
            db.update("DELETE FROM products WHERE id=?",productId);
        }
        db.update("DELETE FROM seller_applications WHERE user_id IN (?,?)",seller,otherBuyer);
        db.update("DELETE FROM addresses WHERE user_id=?",buyer);
        for(long id:List.of(seller,buyer,otherBuyer)) {
            db.update("DELETE FROM notifications WHERE user_id=?",id);
            db.update("DELETE FROM users WHERE id=?",id);
        }
    }

    @Test void historicalExperienceShipmentReturnsStoredVirtualTraceWithoutProviderLookup() {
        var order=create("EXPRESS");
        db.update("UPDATE orders SET experience_source='maimai-experience-045',fulfillment_status='COMPLETED',pay_status='PAID' WHERE id=?",order.id());
        db.update("INSERT INTO shipments(order_id,carrier,tracking_no,status,traces,last_trace_at) VALUES(?,'shunfeng','MXEXP04700001','DELIVERED','服务站已收寄\n收件人已确认签收',NOW())",order.id());
        var shipment=as(buyer,()->fulfillment.shipment(buyer,order.orderNo()));
        assertThat(shipment.status()).isEqualTo("DELIVERED");
        assertThat(shipment.queryErrorCode()).isEqualTo("EXPERIENCE_NO_TRACKING");
        assertThat(shipment.lastQueryAttemptAt()).isNull();
        assertThat(shipment.traces()).contains("服务站已收寄","收件人已确认签收");
        assertThat(shipment.lastTraceAt()).isNotNull();
        assertThatThrownBy(()->as(otherBuyer,()->fulfillment.shipment(otherBuyer,order.orderNo()))).isInstanceOf(BizException.class);
    }

    @Test void sameCheckoutKeyNeverReturnsEmptyBatchAndFailedCheckoutRollsBackBatch() throws Exception {
        String key=UUID.randomUUID().toString();
        var request=request(key,"MEETUP",1);
        List<OrderDto> results=parallel(8,()->checkout.checkout(buyer,request).orders().getFirst());
        assertThat(results.stream().map(OrderDto::id).distinct()).hasSize(1);
        assertThat(results).allSatisfy(order -> assertThat(order.freightCents()).isZero());
        assertThat(count("SELECT COUNT(*) FROM orders WHERE buyer_id=?",buyer)).isEqualTo(1);
        assertThat(count("SELECT stock_reserved FROM products WHERE id=?",product)).isEqualTo(1);
        String failedKey=UUID.randomUUID().toString();
        assertThatThrownBy(()->checkout.checkout(buyer,request(failedKey,"MEETUP",99))).isInstanceOf(BizException.class);
        assertThat(count("SELECT COUNT(*) FROM checkout_batches WHERE user_id=? AND idempotency_key=?",buyer,failedKey)).isZero();
    }

    @Test void concurrentBuyersCannotOversellAndCancelReleasesOnlyOnce() throws Exception {
        var sequence=new java.util.concurrent.atomic.AtomicInteger();
        List<OrderDto> purchased=parallel(12,()-> {
            long account=sequence.getAndIncrement()%2==0 ? buyer : otherBuyer;
            try { return checkout.checkout(account,request(UUID.randomUUID().toString(),"MEETUP",1)).orders().getFirst(); }
            catch (BizException rejected) { assertThat(rejected.getCode()).isEqualTo("INSUFFICIENT_STOCK"); return null; }
        });
        List<OrderDto> success=purchased.stream().filter(Objects::nonNull).toList();
        assertThat(success).hasSize(3);
        assertThat(count("SELECT stock_available FROM products WHERE id=?",product)).isZero();
        assertThat(count("SELECT stock_reserved FROM products WHERE id=?",product)).isEqualTo(3);
        orders.closeUnpaid(success.getFirst().id(),"测试取消");
        orders.closeUnpaid(success.getFirst().id(),"重复取消");
        assertThat(count("SELECT stock_available FROM products WHERE id=?",product)).isEqualTo(1);
        assertThat(count("SELECT stock_reserved FROM products WHERE id=?",product)).isEqualTo(2);
    }

    @Test void paymentOwnershipAndConcurrentCallbackAreIdempotent() throws Exception {
        OrderDto order=create("EXPRESS");
        assertThatThrownBy(()->as(otherBuyer,()->payments.pay(order.orderNo()))).isInstanceOf(BizException.class);
        PayResponse pay=as(buyer,()->payments.pay(order.orderNo()));
        assertThat(as(buyer,()->payments.pay(order.orderNo())).payNo()).isEqualTo(pay.payNo());
        assertThatThrownBy(()->mockPayments.confirm(new MockPayConfirmRequest(pay.payNo(),1L))).isInstanceOf(BizException.class);
        parallel(8,()->mockPayments.confirm(new MockPayConfirmRequest(pay.payNo(),pay.amountCents())));
        assertThat(count("SELECT COUNT(*) FROM ledger_entries WHERE order_id=? AND entry_type='PAYMENT'",order.id())).isEqualTo(1);
        assertThat(count("SELECT stock_sold FROM products WHERE id=?",product)).isEqualTo(1);
        assertThat(count("SELECT stock_reserved FROM products WHERE id=?",product)).isZero();
        assertThat(orderRepository.findById(order.id()).orElseThrow().getPayStatus().name()).isEqualTo("PAID");
    }

    @Test void repeatedPartialRefundsUseRemainingGoodsAndRejectOverRefund() {
        OrderDto order=paid("EXPRESS");
        var entity=orderRepository.findById(order.id()).orElseThrow();
        assertThat(refunds.createRefund(entity,null,4000,0).getPlatformFeeRefundCents()).isEqualTo(1);
        assertThat(refunds.createRefund(entity,null,3000,0).getPlatformFeeRefundCents()).isEqualTo(1);
        assertThat(refunds.createRefund(entity,null,0,1200).getPlatformFeeRefundCents()).isZero();
        assertThatThrownBy(()->refunds.createRefund(entity,null,3001,0)).isInstanceOf(BizException.class);
        assertThat(refunds.createRefund(entity,null,3000,0).getPlatformFeeRefundCents()).isEqualTo(1);
        assertThat(count("SELECT SUM(goods_refund_cents) FROM refunds WHERE order_id=?",order.id())).isEqualTo(10000);
        assertThat(count("SELECT SUM(platform_fee_refund_cents) FROM refunds WHERE order_id=?",order.id())).isEqualTo(3);
        assertThat(orderRepository.findById(order.id()).orElseThrow().getRefundStatus().name()).isEqualTo("FULL");
    }

    @Test void deliveryCodeLocksAfterFiveMistakesAndConcurrentVerificationCompletesOnce() throws Exception {
        OrderDto order=paid("MEETUP");
        var initial=as(buyer,()->delivery.generate(buyer,order.orderNo()));
        String wrong=initial.code().equals("000000") ? "111111" : "000000";
        for(int i=0;i<5;i++) assertThatThrownBy(()->as(seller,()-> {delivery.verify(seller,order.orderNo(),wrong);return null;})).isInstanceOf(BizException.class);
        assertThat(count("SELECT attempts FROM delivery_codes WHERE order_id=? AND invalidated=0",order.id())).isEqualTo(5);
        assertThatThrownBy(()->as(seller,()-> {delivery.verify(seller,order.orderNo(),initial.code());return null;})).isInstanceOf(BizException.class);
        var fresh=as(buyer,()->delivery.generate(buyer,order.orderNo()));
        List<Boolean> successes=parallel(8,()->as(seller,()-> {
            try { delivery.verify(seller,order.orderNo(),fresh.code()); return true; }
            catch(BizException ex) { assertThat(ex.getCode()).isEqualTo("ORDER_STATE");return false; }
        }));
        assertThat(successes.stream().filter(Boolean::booleanValue)).hasSize(1);
        assertThat(orderRepository.findById(order.id()).orElseThrow().getFulfillmentStatus().name()).isEqualTo("COMPLETED");
    }

    @Test void aftersaleOwnershipPausesCompletionAndOnlyOneConcurrentApprovalRefunds() throws Exception {
        OrderDto order=paid("EXPRESS");
        var request=new CreateAftersaleRequest(Aftersale.Type.REFUND_ONLY,"商品不符",4000L,0L,"测试证据");
        assertThatThrownBy(()->as(otherBuyer,()->aftersales.create(order.orderNo(),request))).isInstanceOf(BizException.class);
        var aftersale=as(buyer,()->aftersales.create(order.orderNo(),request));
        assertThat(orderRepository.findById(order.id()).orElseThrow().isConfirmPaused()).isTrue();
        assertThatThrownBy(()->as(otherBuyer,()->aftersales.detail(aftersale.id()))).isInstanceOf(BizException.class);
        List<Boolean> successes=parallel(6,()->as(seller,()-> {
            try { aftersales.respond(aftersale.id(),new RespondRequest(true,"同意"));return true; }
            catch(BizException ex) { assertThat(ex.getCode()).isEqualTo("AFTERSALE_STATUS_INVALID");return false; }
        }));
        assertThat(successes.stream().filter(Boolean::booleanValue)).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id=?",order.id())).isEqualTo(1);
    }

    @Test void autoReceiptRequiresExpressNormalLogisticsAndNoAftersale() {
        OrderDto order=paid("EXPRESS");
        db.update("UPDATE orders SET fulfillment_status='SHIPPED',auto_confirm_at=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?",order.id());
        db.update("INSERT INTO shipments(order_id,carrier,tracking_no,status) VALUES(?,'test','TEST123','UNKNOWN')",order.id());
        maintenance.autoComplete(order.id());
        assertThat(orderRepository.findById(order.id()).orElseThrow().getFulfillmentStatus().name()).isEqualTo("SHIPPED");
        db.update("UPDATE shipments SET status='EXCEPTION' WHERE order_id=?",order.id());
        maintenance.autoComplete(order.id());
        assertThat(orderRepository.findById(order.id()).orElseThrow().getFulfillmentStatus().name()).isEqualTo("SHIPPED");
        db.update("UPDATE shipments SET status='DELIVERED' WHERE order_id=?",order.id());
        var aftersale=as(buyer,()->aftersales.create(order.orderNo(),new CreateAftersaleRequest(Aftersale.Type.REFUND_ONLY,"测试售后",1000L,0L,null)));
        db.update("UPDATE orders SET confirm_paused=0 WHERE id=?",order.id());
        maintenance.autoComplete(order.id());
        assertThat(orderRepository.findById(order.id()).orElseThrow().getFulfillmentStatus().name()).isEqualTo("SHIPPED");
        db.update("UPDATE aftersales SET seller_deadline=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE id=?",aftersale.id());
        aftersales.escalateExpired(aftersale.id());
        assertThat(as(buyer,()->aftersales.detail(aftersale.id())).status()).isEqualTo("PENDING_MANUAL");
    }

    @Test void sellerSplitUsesHighestFreightAndEachOrdersOwnFee() {
        long second=createProduct(seller,5000,600);
        db.update("INSERT INTO seller_applications(user_id,status,channel_status) VALUES(?,'APPROVED','QUALIFIED')",otherBuyer);
        long third=createProduct(otherBuyer,1667,300);
        var request=new CheckoutRequest(UUID.randomUUID().toString(),List.of(
                new CheckoutItem(product,1,"EXPRESS",null), new CheckoutItem(second,2,"EXPRESS",null),
                new CheckoutItem(third,1,"EXPRESS",null)),address,null,null,null);
        var result=checkout.checkout(buyer,request);
        assertThat(result.orders()).hasSize(2);
        var first=result.orders().stream().filter(order->order.sellerId()==seller).findFirst().orElseThrow();
        assertThat(first.goodsAmountCents()).isEqualTo(20000);
        assertThat(first.freightCents()).isEqualTo(1200);
        assertThat(first.platformFeeCents()).isEqualTo(6);
        var other=result.orders().stream().filter(order->order.sellerId()==otherBuyer).findFirst().orElseThrow();
        assertThat(other.freightCents()).isEqualTo(300);
        assertThat(other.platformFeeCents()).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"IN_TRANSIT","UNKNOWN","EXCEPTION"})
    void overdueUnconfirmedShipmentDefersOnceAndCompletesOnlyAfterDelivered(String status) throws Exception {
        OrderDto order=paid("EXPRESS");
        db.update("UPDATE orders SET fulfillment_status='SHIPPED',auto_confirm_at=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?",order.id());
        db.update("INSERT INTO shipments(order_id,carrier,tracking_no,status) VALUES(?,'test','UNCHECKED123',?)",order.id(),status);
        parallel(8,()->{maintenance.autoComplete(order.id());return true;});
        assertThat(orderRepository.findById(order.id()).orElseThrow().getFulfillmentStatus().name()).isEqualTo("SHIPPED");
        assertThat(count("SELECT COUNT(*) FROM trade_reminder_events WHERE order_id=? AND event_type='RECEIPT_CHECK_REQUIRED'",order.id())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id IN (?,?) AND type='RECEIPT_CHECK_REQUIRED'",buyer,seller)).isEqualTo(2);
        assertThat(reminders.todos(0,100).stream().anyMatch(todo->todo.orderId()==order.id())).isTrue();
        db.update("UPDATE shipments SET status='DELIVERED' WHERE order_id=?",order.id());
        parallel(8,()->{maintenance.autoComplete(order.id());return true;});
        assertThat(orderRepository.findById(order.id()).orElseThrow().getFulfillmentStatus().name()).isEqualTo("COMPLETED");
        assertThat(db.queryForObject("SELECT status FROM trade_reminder_events WHERE order_id=? AND event_type='RECEIPT_CHECK_REQUIRED'",String.class,order.id())).isEqualTo("RESOLVED");
        assertThat(reminders.todos(0,100).stream().anyMatch(todo->todo.orderId()==order.id())).isFalse();
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id IN (?,?) AND type='RECEIPT_CHECK_REQUIRED'",buyer,seller)).isEqualTo(2);
    }

    @Test void missingShipmentAlsoDefersAndRefundClearsItsReviewTodo() {
        OrderDto order=paid("EXPRESS");
        db.update("UPDATE orders SET fulfillment_status='SHIPPED',auto_confirm_at=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?",order.id());
        maintenance.autoComplete(order.id());
        assertThat(orderRepository.findById(order.id()).orElseThrow().getFulfillmentStatus().name()).isEqualTo("SHIPPED");
        assertThat(reminders.todos(0,100).stream().anyMatch(todo->todo.orderId()==order.id())).isTrue();
        refunds.createRefund(orderRepository.findById(order.id()).orElseThrow(),null,10000,1200);
        assertThat(reminders.candidates()).contains(order.id());
        reminders.check(order.id());
        assertThat(reminders.todos(0,100).stream().anyMatch(todo->todo.orderId()==order.id())).isFalse();
        assertThat(db.queryForObject("SELECT status FROM trade_reminder_events WHERE order_id=? AND event_type='RECEIPT_CHECK_REQUIRED'",String.class,order.id())).isEqualTo("RESOLVED");
    }

    @Test void bargainIsBoundToBuyerQuantityAndExpiryAndDraftDetailsArePrivate() {
        assertThat(sellerProducts.detailMine(seller,product).categoryId()).isEqualTo(1);
        assertThatThrownBy(()->sellerProducts.detailMine(buyer,product)).isInstanceOf(BizException.class);
        var offer=bargains.create(buyer,product,1,5000);
        assertThat(count("SELECT stock_reserved FROM products WHERE id=?",product)).isZero();
        bargains.accept(seller,offer.id());
        var request=new CheckoutRequest(UUID.randomUUID().toString(),List.of(new CheckoutItem(product,1,"MEETUP",offer.id())),null,"测试公共地点",Instant.now().plusSeconds(3600),null);
        assertThatThrownBy(()->checkout.checkout(otherBuyer,request)).isInstanceOf(BizException.class);
        assertThat(checkout.checkout(buyer,request).orders().getFirst().goodsAmountCents()).isEqualTo(5000);
        var usedRequest=new CheckoutRequest(UUID.randomUUID().toString(),request.items(),null,request.meetupLocation(),request.meetupTime(),null);
        assertThatThrownBy(()->checkout.checkout(buyer,usedRequest)).isInstanceOf(BizException.class);
        var expired=bargains.create(buyer,product,1,4500);
        db.update("UPDATE bargain_offers SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",expired.id());
        assertThatThrownBy(()->bargains.accept(seller,expired.id())).isInstanceOf(BizException.class);
        assertThat(bargains.listBuyer(buyer).stream().filter(item->item.id().equals(expired.id())).findFirst().orElseThrow().status()).isEqualTo("EXPIRED");
    }

    @Test void financeDistinguishesUnknownFeeAndMockExpectedAllocationFromRealSettlement() {
        OrderDto pending=create("EXPRESS");
        var before=finance.summary(pending.id());
        assertThat(before.channelFeeCents()).isNull();assertThat(before.channelFeeConfirmed()).isFalse();
        assertThat(before.expectedSellerNetCents()).isNull();
        var pay=as(buyer,()->payments.pay(pending.orderNo()));
        mockPayments.confirm(new MockPayConfirmRequest(pay.payNo(),pay.amountCents()));
        var paid=finance.summary(pending.id());
        assertThat(paid.channelFeeCents()).isZero();assertThat(paid.channelFeeConfirmed()).isTrue();
        assertThat(paid.simulated()).isTrue();assertThat(paid.expectedSellerNetCents()).isEqualTo(11197);
        assertThat(paid.allocationStatus()).isEqualTo("WAITING_CHANNEL");
        assertThat(finance.detail(pending.id()).reconciliationStatus()).isEqualTo("PROVIDER_NOT_CONNECTED");
        var order=orderRepository.findById(pending.id()).orElseThrow();
        refunds.createRefund(order,null,4000,0);
        var after=finance.summary(pending.id());
        assertThat(after.retainedPlatformFeeCents()).isEqualTo(2);
        assertThat(after.platformFeeRefundedCents()).isEqualTo(1);
        assertThat(after.platformFeeRefundDueCents()).isZero();
        assertThat(after.expectedSellerNetCents()).isEqualTo(7198);
        assertThat(count("SELECT platform_expected_cents FROM finance_allocation_expectations WHERE order_id=?",pending.id())).isEqualTo(2);
        assertThat(orderRepository.findById(pending.id()).orElseThrow().getSettleStatus().name()).isEqualTo("NONE");
    }

    @Test void concurrentUnshippedFullRefundRestocksAndClosesExactlyOnce() throws Exception {
        OrderDto order=paid("EXPRESS");
        var entity=orderRepository.findById(order.id()).orElseThrow();
        var success=parallel(8,()-> {
            try {refunds.createRefund(entity,null,10000,1200);return true;}
            catch(BizException error) {assertThat(error.getCode()).isEqualTo("REFUND_EXCEEDED");return false;}
        });
        assertThat(success.stream().filter(Boolean::booleanValue)).hasSize(1);
        assertThat(count("SELECT stock_sold FROM products WHERE id=?",product)).isZero();
        assertThat(count("SELECT stock_available FROM products WHERE id=?",product)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM stock_logs WHERE ref_id=? AND reason='REFUND_RESTOCK'",order.id())).isEqualTo(1);
        assertThat(orderRepository.findById(order.id()).orElseThrow().getFulfillmentStatus().name()).isEqualTo("CLOSED");
        assertThat(orderRepository.findById(order.id()).orElseThrow().getPayStatus().name()).isEqualTo("PAID");
        assertThat(finance.summary(order.id()).expectedSellerNetCents()).isZero();
    }

    @Test void refundOfAlreadyShippedOrderDoesNotInventReturnedStock() {
        OrderDto order=paid("EXPRESS");
        db.update("UPDATE orders SET fulfillment_status='SHIPPED',shipped_at=NOW() WHERE id=?",order.id());
        refunds.createRefund(orderRepository.findById(order.id()).orElseThrow(),null,10000,1200);
        assertThat(count("SELECT stock_sold FROM products WHERE id=?",product)).isEqualTo(1);
        assertThat(count("SELECT stock_available FROM products WHERE id=?",product)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM stock_logs WHERE ref_id=? AND reason='REFUND_RESTOCK'",order.id())).isZero();
    }

    @Test void tradeRemindersAreOnceOnlyAndOverdueTodoResolvesAfterRefund() throws Exception {
        OrderDto order=paid("EXPRESS");
        db.update("UPDATE orders SET ship_deadline=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE id=?",order.id());
        parallel(8,()-> {reminders.check(order.id());return true;});
        assertThat(count("SELECT COUNT(*) FROM trade_reminder_events WHERE order_id=? AND event_type='SHIP_OVERDUE'",order.id())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id IN (?,?) AND type='SHIP_OVERDUE'",buyer,seller)).isEqualTo(2);
        assertThat(reminders.todos(0,100).stream().anyMatch(todo->todo.orderId()==order.id())).isTrue();
        refunds.createRefund(orderRepository.findById(order.id()).orElseThrow(),null,10000,1200);
        assertThat(reminders.todos(0,100).stream().anyMatch(todo->todo.orderId()==order.id())).isFalse();
        assertThat(db.queryForObject("SELECT status FROM trade_reminder_events WHERE order_id=? AND event_type='SHIP_OVERDUE'",String.class,order.id())).isEqualTo("RESOLVED");
    }

    @Test void automaticReceiptReminderNeedsNormalExpressAndIsNotRepeated() throws Exception {
        OrderDto order=paid("EXPRESS");
        db.update("UPDATE orders SET fulfillment_status='SHIPPED',auto_confirm_at=DATE_ADD(NOW(),INTERVAL 12 HOUR) WHERE id=?",order.id());
        db.update("INSERT INTO shipments(order_id,carrier,tracking_no,status) VALUES(?,'test','TRACE123','EXCEPTION')",order.id());
        reminders.check(order.id());
        assertThat(count("SELECT COUNT(*) FROM trade_reminder_events WHERE order_id=?",order.id())).isZero();
        for(String status:List.of("IN_TRANSIT","UNKNOWN")) {
            db.update("UPDATE shipments SET status=? WHERE order_id=?",status,order.id());
            reminders.check(order.id());
            assertThat(count("SELECT COUNT(*) FROM trade_reminder_events WHERE order_id=?",order.id())).isZero();
            assertThat(reminders.candidates()).doesNotContain(order.id());
        }
        db.update("UPDATE shipments SET status='DELIVERED' WHERE order_id=?",order.id());
        parallel(8,()-> {reminders.check(order.id());return true;});
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='AUTO_RECEIPT_SOON'",buyer)).isEqualTo(1);
    }

    @Test void financialDateFiltersAndCsvNeutralizeFormulaInputs() {
        OrderDto order=paid("EXPRESS");
        String injected=" =1+1";
        db.update("UPDATE orders SET order_no=?,created_at='2024-05-31 16:30:00' WHERE id=?",injected,order.id());
        var june1=java.time.LocalDate.of(2024,6,1);
        assertThat(finance.list(june1,june1,0,20).content().stream().map(com.maimai.payment.finance.FinanceDtos.Summary::orderId)).contains(order.id());
        assertThat(finance.list(june1.minusDays(1),june1.minusDays(1),0,20).content().stream().map(com.maimai.payment.finance.FinanceDtos.Summary::orderId)).doesNotContain(order.id());
        assertThat(finance.csv(june1,june1)).contains("\"' =1+1\"").contains("WAITING_CHANNEL").contains("PROVIDER_NOT_CONNECTED");
        assertThatThrownBy(()->finance.list(june1,june1.minusDays(1),0,20)).isInstanceOf(BizException.class);
    }

    @Test void restrictedProvinceIsEnforcedByCheckoutAndFailureDoesNotReserve() {
        db.update("UPDATE products SET shipping_provinces='上海市' WHERE id=?",product);
        assertThatThrownBy(()->create("EXPRESS")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo("ADDRESS_PROVINCE_REQUIRED"));
        db.update("UPDATE addresses SET region='北京市 海淀区' WHERE id=?",address);
        assertThatThrownBy(()->create("EXPRESS")).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo("DELIVERY_REGION_UNSUPPORTED"));
        assertThat(count("SELECT COUNT(*) FROM checkout_batches WHERE user_id=?",buyer)).isZero();
        assertThat(count("SELECT stock_reserved FROM products WHERE id=?",product)).isZero();
        db.update("UPDATE addresses SET region='上海市 浦东新区' WHERE id=?",address);
        assertThat(create("EXPRESS").freightCents()).isEqualTo(1200);
        // A meetup is not constrained by an express shipping whitelist.
        assertThat(create("MEETUP").freightCents()).isZero();
    }

    @Test void nearbySearchHasStableDistancePagesAndUnknownPointsLast() {
        String prefix="nearby-"+UUID.randomUUID();
        long close=createProduct(seller,5000,0),far=createProduct(seller,5000,0),unknown=createProduct(seller,5000,0);
        for(long id:List.of(product,close,far,unknown))db.update("UPDATE products SET title=? WHERE id=?",prefix,id);
        db.update("UPDATE products SET latitude=31.230000,longitude=121.470000 WHERE id=?",product);
        db.update("UPDATE products SET latitude=31.240000,longitude=121.470000 WHERE id=?",close);
        db.update("UPDATE products SET latitude=39.900000,longitude=116.400000 WHERE id=?",far);
        var first=catalog.search(prefix,null,null,null,null,null,null,"distance_asc",0,2,31.23,121.47);
        var second=catalog.search(prefix,null,null,null,null,null,null,"distance_asc",1,2,31.23,121.47);
        assertThat(first.content().stream().map(com.maimai.catalog.dto.CatalogDtos.ProductSummary::id)).containsExactly(product,close);
        assertThat(second.content().stream().map(com.maimai.catalog.dto.CatalogDtos.ProductSummary::id)).containsExactly(far,unknown);
        assertThat(first.content().getFirst().distanceMeters()).isLessThan(1);
        assertThat(first.content().getLast().distanceMeters()).isBetween(1000.0,1200.0);
        assertThat(second.content().getLast().distanceMeters()).isNull();
        assertThat(catalog.search(prefix,null,null,null,null,null,null,"price_asc",0,20,null,null).content()).allSatisfy(p->assertThat(p.distanceMeters()).isNull());
        assertThatThrownBy(()->catalog.search(prefix,null,null,null,null,null,null,"distance_asc",0,2,null,null)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->catalog.search(prefix,null,null,null,null,null,null,"distance_asc",0,2,100.0,121.47)).isInstanceOf(BizException.class);
    }

    @Test void contentHistoryKeepsOriginalSnapshotAndSerialVersionsWithoutStockNoise() throws Exception {
        OrderDto oldOrder=create("EXPRESS");
        parallel(8,()->{sellerProducts.update(seller,product,edit("修订-"+UUID.randomUUID(),List.of("上海市")));return true;});
        var history=as(seller,()->revisions.list(product,0,50,false));
        assertThat(history.totalElements()).isEqualTo(9);
        String displayJson=new tools.jackson.databind.json.JsonMapper().writeValueAsString(history);
        assertThat(displayJson).doesNotContain("actorId","categoryId","experienceSource","latitude","longitude","BASELINE","\"action\"","\"id\"");
        assertThat(displayJson).contains("首次记录","categoryName","actionLabel");
        // Raw immutable audit content is still retained internally.
        assertThat(db.queryForObject("SELECT content FROM product_revisions WHERE product_id=? AND version=1",String.class,product)).contains("categoryId");
        assertThat(history.content().stream().map(com.maimai.catalog.service.ProductRevisionService.Revision::version)).containsExactly(9,8,7,6,5,4,3,2,1);
        assertThat(history.content().getLast().content().title()).isEqualTo("集成测试商品");
        assertThat(history.content().getFirst().content().shippingProvinces()).containsExactly("上海市");
        assertThat(products.findById(product).orElseThrow().getStatus().name()).isEqualTo("CHANGES_REVIEW");
        sellerProducts.adjustStock(seller,product,new com.maimai.catalog.dto.CatalogDtos.StockAdjustRequest(1));
        assertThat(as(seller,()->revisions.list(product,0,50,false)).totalElements()).isEqualTo(9);
        assertThat(db.queryForObject("SELECT title FROM order_items WHERE order_id=?",String.class,oldOrder.id())).isEqualTo("集成测试商品");
        assertThatThrownBy(()->as(buyer,()->revisions.list(product,0,50,false))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->as(buyer,()->revisions.list(product,0,50,true))).isInstanceOf(BizException.class);
    }

    @Test void productImagesAreReencodedAndHistoricalFileSurvivesRemoval() throws Exception {
        var source=new java.awt.image.BufferedImage(2000,20,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(source,"PNG",bytes);source.flush();
        var upload=new org.springframework.mock.web.MockMultipartFile("files","wide.png","image/png",bytes.toByteArray());
        long imageId=productImages.upload(seller,product,List.of(upload)).getFirst();
        String url=sellerProducts.detailMine(seller,product).images().getFirst().path();
        assertThat(url).startsWith("/uploads/products/").doesNotContain("/uploads//uploads/");
        var file=java.nio.file.Path.of(properties.getUploadDir()).resolve(url.substring("/uploads/".length()));
        var result=javax.imageio.ImageIO.read(file.toFile());
        assertThat(result.getWidth()).isEqualTo(1600);assertThat(result.getHeight()).isEqualTo(16);result.flush();
        productReview.review(otherBuyer,product,true,"审核通过");
        assertThat(products.findById(product).orElseThrow().getStatus().name()).isEqualTo("ON_SALE");
        productImages.delete(seller,product,imageId);
        assertThat(sellerProducts.detailMine(seller,product).images()).isEmpty();
        assertThat(java.nio.file.Files.exists(file)).isTrue();
        assertThat(as(seller,()->revisions.list(product,0,50,false)).content()).anySatisfy(r->assertThat(r.content().images()).anySatisfy(i->assertThat(i.path()).isEqualTo(url)));
        var invalid=new org.springframework.mock.web.MockMultipartFile("files","fake.jpg","image/jpeg","<svg/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long before=count("SELECT COUNT(*) FROM product_revisions WHERE product_id=?",product);
        assertThatThrownBy(()->productImages.upload(seller,product,List.of(invalid))).isInstanceOf(BizException.class);
        assertThat(count("SELECT COUNT(*) FROM product_revisions WHERE product_id=?",product)).isEqualTo(before);
    }

    @Test void meetupOverdueOnlyNotifiesOnceAndReopensTodoAfterAnotherMissedAppointment() throws Exception {
        OrderDto order=paid("MEETUP");
        db.update("UPDATE meetup_appointments SET scheduled_at=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE order_id=?",order.id());
        assertThat(reminders.candidates()).contains(order.id());
        parallel(8,()->{reminders.check(order.id());return true;});
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id IN (?,?) AND type='MEETUP_OVERDUE'",buyer,seller)).isEqualTo(2);
        assertThat(reminders.todos(0,100).stream().anyMatch(t->t.orderId()==order.id())).isTrue();
        assertThat(orderRepository.findById(order.id()).orElseThrow().getFulfillmentStatus().name()).isEqualTo("AWAITING_MEETUP");
        db.update("UPDATE meetup_appointments SET scheduled_at=DATE_ADD(NOW(),INTERVAL 1 DAY) WHERE order_id=?",order.id());
        reminders.check(order.id());assertThat(reminders.todos(0,100).stream().anyMatch(t->t.orderId()==order.id())).isFalse();
        db.update("UPDATE meetup_appointments SET scheduled_at=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE order_id=?",order.id());
        assertThat(reminders.candidates()).contains(order.id());reminders.check(order.id());
        assertThat(reminders.todos(0,100).stream().anyMatch(t->t.orderId()==order.id())).isTrue();
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id IN (?,?) AND type='MEETUP_OVERDUE'",buyer,seller)).isEqualTo(2);
        refunds.createRefund(orderRepository.findById(order.id()).orElseThrow(),null,10000,0);reminders.check(order.id());
        assertThat(reminders.todos(0,100).stream().anyMatch(t->t.orderId()==order.id())).isFalse();
    }

    @Test void markedCatalogCannotBecomeARealOrderOrReserveStock() {
        db.update("UPDATE products SET experience_source='experience-test' WHERE id=?",product);
        assertThatThrownBy(()->create("MEETUP")).isInstanceOf(BizException.class).hasMessageContaining("体验库存");
        assertThat(count("SELECT stock_available FROM products WHERE id=?",product)).isEqualTo(3);
        assertThat(count("SELECT stock_reserved FROM products WHERE id=?",product)).isZero();
    }

    @Test void importedExperienceOrderIsVisibleButCannotStartFinancialOperations() {
        var order=paid("MEETUP");
        db.update("UPDATE orders SET experience_source='experience-test',fulfillment_status='COMPLETED',completed_at=UTC_TIMESTAMP() WHERE id=?",order.id());
        db.update("DELETE FROM finance_allocation_expectations WHERE order_id=?",order.id());
        finance.captureExpectedAllocation(order.id());
        assertThat(count("SELECT COUNT(*) FROM finance_allocation_expectations WHERE order_id=?",order.id())).isZero();
        assertThat(finance.money(order.id()).simulated()).isTrue();
        assertThatThrownBy(()->as(buyer,()->payments.pay(order.orderNo()))).isInstanceOf(BizException.class).hasMessageContaining("体验成交记录");
        assertThatThrownBy(()->as(buyer,()->aftersales.create(order.orderNo(),new CreateAftersaleRequest(Aftersale.Type.REFUND_ONLY,"申请",100L,0L,null)))).isInstanceOf(BizException.class).hasMessageContaining("体验成交记录");
        assertThatThrownBy(()->refunds.createRefund(orderRepository.findById(order.id()).orElseThrow(),null,100,0)).isInstanceOf(BizException.class).hasMessageContaining("体验成交记录");
    }

    @Test void experienceCheckoutAndQrRequireBuyerAndNeverUseRealPaymentChannel() throws Exception {
        db.update("UPDATE products SET experience_source='maimai-experience-045' WHERE id=?",product);
        db.update("UPDATE seller_applications SET channel_status='PENDING' WHERE user_id=?",seller);
        String key=UUID.randomUUID().toString();
        var order=checkout.checkoutExperience(buyer,request(key,"MEETUP",1)).orders().getFirst();
        assertThat(order.experienceSource()).isEqualTo(com.maimai.trade.domain.Order.INTERACTIVE_EXPERIENCE);
        assertThat(order.simulated()).isTrue();
        assertThat(checkout.checkoutExperience(buyer,request(key,"MEETUP",1)).orders().getFirst().id()).isEqualTo(order.id());
        assertThatThrownBy(()->checkout.checkout(buyer,request(key,"MEETUP",1))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->as(otherBuyer,()->experiencePayments.create(order.orderNo()))).isInstanceOf(BizException.class);
        var session=as(buyer,()->experiencePayments.create(order.orderNo()));
        assertThat(as(buyer,()->experiencePayments.create(order.orderNo())).token()).isEqualTo(session.token());
        assertThat(session.amountCents()).isEqualTo(order.totalCents());
        assertThatThrownBy(()->as(otherBuyer,()->experiencePayments.get(session.token()))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->as(seller,()->experiencePayments.qr(session.token()))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->as(otherBuyer,()->experiencePayments.finish(session.token(),com.maimai.payment.dto.ExperiencePaymentDtos.Result.SUCCESS))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->as(buyer,()->payments.pay(order.orderNo()))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->mockPayments.confirm(new MockPayConfirmRequest(session.payNo(),session.amountCents()))).isInstanceOf(BizException.class);
        var picture=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(as(buyer,()->experiencePayments.qr(session.token()))));
        int[] pixels=picture.getRGB(0,0,picture.getWidth(),picture.getHeight(),null,0,picture.getWidth());
        var source=new com.google.zxing.RGBLuminanceSource(picture.getWidth(),picture.getHeight(),pixels);
        String decoded=new com.google.zxing.qrcode.QRCodeReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(source))).getText();
        assertThat(decoded).isEqualTo(java.net.URI.create(properties.getFrontendOrigin()).resolve(session.checkoutPath()).toString());
        assertThat(decoded).doesNotContain("weixin:","wxp://");
        parallel(8,()->as(buyer,()->experiencePayments.finish(session.token(),com.maimai.payment.dto.ExperiencePaymentDtos.Result.SUCCESS)));
        assertThat(count("SELECT stock_sold FROM products WHERE id=?",product)).isEqualTo(1);
        assertThat(count("SELECT stock_reserved FROM products WHERE id=?",product)).isZero();
        assertThat(count("SELECT COUNT(*) FROM ledger_entries WHERE order_id=?",order.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM finance_allocation_expectations WHERE order_id=?",order.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM payment_requests WHERE order_id=? AND simulated=0",order.id())).isZero();
    }

    @Test void experienceCancelFailureRetryAndExpiredQrDoNotAdvanceInventory() {
        db.update("UPDATE products SET experience_source='maimai-experience-045' WHERE id=?",product);
        var order=checkout.checkoutExperience(buyer,request(UUID.randomUUID().toString(),"MEETUP",1)).orders().getFirst();
        var first=as(buyer,()->experiencePayments.create(order.orderNo()));
        assertThat(as(buyer,()->experiencePayments.finish(first.token(),com.maimai.payment.dto.ExperiencePaymentDtos.Result.CANCEL)).status()).isEqualTo("CANCELLED");
        as(buyer,()->experiencePayments.finish(first.token(),com.maimai.payment.dto.ExperiencePaymentDtos.Result.CANCEL));
        var second=as(buyer,()->experiencePayments.create(order.orderNo()));
        assertThat(second.token()).isNotEqualTo(first.token());
        as(buyer,()->experiencePayments.finish(second.token(),com.maimai.payment.dto.ExperiencePaymentDtos.Result.FAIL));
        assertThatThrownBy(()->as(buyer,()->experiencePayments.finish(first.token(),com.maimai.payment.dto.ExperiencePaymentDtos.Result.SUCCESS))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->as(buyer,()->experiencePayments.finish(second.token(),com.maimai.payment.dto.ExperiencePaymentDtos.Result.SUCCESS))).isInstanceOf(BizException.class);
        var third=as(buyer,()->experiencePayments.create(order.orderNo()));
        db.update("UPDATE orders SET expires_at=? WHERE id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(2)),order.id());
        assertThat(as(buyer,()->experiencePayments.get(third.token())).status()).isEqualTo("EXPIRED");
        assertThatThrownBy(()->as(buyer,()->experiencePayments.finish(third.token(),com.maimai.payment.dto.ExperiencePaymentDtos.Result.SUCCESS))).isInstanceOf(BizException.class);
        orders.closeUnpaid(order.id(),"体验超时"); orders.closeUnpaid(order.id(),"重复超时");
        assertThat(count("SELECT stock_available FROM products WHERE id=?",product)).isEqualTo(3);
        assertThat(count("SELECT stock_reserved FROM products WHERE id=?",product)).isZero();
    }

    @Test void experienceRefundRunsThroughSellerAftersaleAndNeverWritesRealFunds() {
        db.update("UPDATE products SET experience_source='maimai-experience-045' WHERE id=?",product);
        var order=checkout.checkoutExperience(buyer,request(UUID.randomUUID().toString(),"EXPRESS",1)).orders().getFirst();
        var session=as(buyer,()->experiencePayments.create(order.orderNo()));
        as(buyer,()->experiencePayments.finish(session.token(),com.maimai.payment.dto.ExperiencePaymentDtos.Result.SUCCESS));
        var partial=as(buyer,()->aftersales.create(order.orderNo(),new CreateAftersaleRequest(Aftersale.Type.REFUND_ONLY,"体验部分退款",4000L,0L,null)));
        assertThat(partial.experience()).isTrue();
        as(seller,()->aftersales.respond(partial.id(),new RespondRequest(true,"同意体验退款")));
        assertThat(as(buyer,()->experiencePayments.get(session.token())).refundStatus()).isEqualTo("PARTIAL");
        var rest=as(buyer,()->aftersales.create(order.orderNo(),new CreateAftersaleRequest(Aftersale.Type.REFUND_ONLY,"体验剩余退款",6000L,1200L,null)));
        as(seller,()->aftersales.respond(rest.id(),new RespondRequest(true,"同意剩余退款")));
        assertThat(as(buyer,()->experiencePayments.get(session.token())).status()).isEqualTo("REFUNDED");
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id=? AND simulated=1 AND channel='EXPERIENCE_QR' AND status='SUCCESS'",order.id())).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM ledger_entries WHERE order_id=?",order.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM finance_allocation_expectations WHERE order_id=?",order.id())).isZero();
        assertThat(count("SELECT stock_available FROM products WHERE id=?",product)).isEqualTo(3);
        assertThatThrownBy(()->refunds.createRefund(orderRepository.findById(order.id()).orElseThrow(),null,1,0)).isInstanceOf(BizException.class);
    }

    @Test void realAndImportedOrdersCannotBecomeExperiencePaymentsAndMixedCheckoutRollsBack() {
        var normal=create("MEETUP");
        assertThatThrownBy(()->as(buyer,()->experiencePayments.create(normal.orderNo()))).isInstanceOf(BizException.class);
        db.update("UPDATE orders SET experience_source='maimai-experience-045' WHERE id=?",normal.id());
        assertThatThrownBy(()->as(buyer,()->experiencePayments.create(normal.orderNo()))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->checkout.checkoutExperience(buyer,request(UUID.randomUUID().toString(),"MEETUP",1))).isInstanceOf(BizException.class);
        db.update("UPDATE products SET experience_source='maimai-experience-045' WHERE id=?",product);
        long other=createProduct(seller,5000,0);
        String key=UUID.randomUUID().toString();
        var mixed=new CheckoutRequest(key,List.of(new CheckoutItem(product,1,"MEETUP",null),new CheckoutItem(other,1,"MEETUP",null)),null,"公共地点",Instant.now().plusSeconds(3600),null);
        assertThatThrownBy(()->checkout.checkoutExperience(buyer,mixed)).isInstanceOf(BizException.class);
        assertThat(count("SELECT COUNT(*) FROM checkout_batches WHERE idempotency_key=?",key)).isZero();
        assertThat(count("SELECT stock_reserved FROM products WHERE id=?",other)).isZero();
    }

    private com.maimai.catalog.dto.CatalogDtos.ProductUpdateRequest edit(String title,List<String> provinces) {
        return new com.maimai.catalog.dto.CatalogDtos.ProductUpdateRequest(title,1L,"修订商品描述","GOOD","已披露缺陷",10000L,null,"上海市",List.of("EXPRESS","MEETUP"),1200L,"按约售后",provinces,new java.math.BigDecimal("31.23"),new java.math.BigDecimal("121.47"));
    }
    private OrderDto create(String method) {return checkout.checkout(buyer,request(UUID.randomUUID().toString(),method,1)).orders().getFirst();}
    private OrderDto paid(String method) {
        OrderDto order=create(method);
        var pay=as(buyer,()->payments.pay(order.orderNo()));
        mockPayments.confirm(new MockPayConfirmRequest(pay.payNo(),pay.amountCents()));return order;
    }
    private CheckoutRequest request(String key,String method,int quantity) {
        return new CheckoutRequest(key,List.of(new CheckoutItem(product,quantity,method,null)),
                "EXPRESS".equals(method)?address:null,"测试公共地点",Instant.now().plusSeconds(3600),null);
    }
    private long user() {
        String email="trade-"+UUID.randomUUID()+"@example.invalid";
        db.update("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'unused','交易测试','ACTIVE')",email);
        return db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
    }
    private long createProduct(long owner,long price,long freight) {
        String title="测试-"+UUID.randomUUID();
        db.update("INSERT INTO products(seller_id,category_id,title,item_condition,price_cents,stock_available,region,delivery_methods,freight_cents,status) VALUES(?,1,?,'GOOD',?,3,'测试城市','EXPRESS,MEETUP',?,'ON_SALE')",owner,title,price,freight);
        long id=db.queryForObject("SELECT id FROM products WHERE title=?",Long.class,title);
        fixtureProducts.add(id); return id;
    }
    private long count(String sql,Object...args) {return db.queryForObject(sql,Long.class,args);}
    private <T> T as(long userId,Supplier<T> action) {
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId,"test@example.invalid","测试",Set.of("USER")),null,List.of()));
        SecurityContextHolder.setContext(context);
        try{return action.get();}finally{SecurityContextHolder.clearContext();}
    }
    private <T> List<T> parallel(int count,Callable<T> action) throws Exception {
        try(var executor=Executors.newFixedThreadPool(4)) {
            var tasks=new ArrayList<Callable<T>>();for(int i=0;i<count;i++)tasks.add(action);
            List<T> result=new ArrayList<>();for(var future:executor.invokeAll(tasks))result.add(future.get(30,TimeUnit.SECONDS));return result;
        }
    }
}
