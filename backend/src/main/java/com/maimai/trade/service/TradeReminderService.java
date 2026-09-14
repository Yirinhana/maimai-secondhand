package com.maimai.trade.service;

import com.maimai.common.BizException;
import com.maimai.notification.NotificationService;
import com.maimai.trade.domain.Order;
import com.maimai.trade.repo.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

/** Reminder events are unique per order/type; retries never repeatedly notify users. */
@Service
public class TradeReminderService {
    private final OrderRepository orders;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    public TradeReminderService(OrderRepository orders,JdbcTemplate jdbc,NotificationService notifications) {
        this.orders=orders;this.jdbc=jdbc;this.notifications=notifications;
    }
    @Transactional
    public void check(long orderId) {
        Order order=orders.lockById(orderId).orElse(null);if(order==null)return;
        Instant now=Instant.now();
        checkMeetup(order);
        boolean overdue=order.getDeliveryMethod()==Order.DeliveryMethod.EXPRESS
                && order.getFulfillmentStatus()==Order.FulfillmentStatus.PAID_PENDING_SHIP
                && order.getPayStatus()==Order.PayStatus.PAID && order.getRefundStatus()!=Order.RefundStatus.FULL
                && order.getShipDeadline()!=null && !order.getShipDeadline().isAfter(now);
        if(overdue && record(orderId,"SHIP_OVERDUE","付款后72小时仍未发货，需要卖家处理和人工跟进")) {
            notifications.notify(order.getSellerId(),"SHIP_OVERDUE","订单发货逾期","订单 "+order.getOrderNo()+" 已超过72小时发货期限，请处理并与买家沟通。");
            notifications.notify(order.getBuyerId(),"SHIP_OVERDUE","卖家尚未发货","订单 "+order.getOrderNo()+" 已超过发货期限，已进入人工待办；可申请退款或联系卖家。");
        } else if(!overdue) {
            jdbc.update("UPDATE trade_reminder_events SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP(6) WHERE order_id=? AND event_type='SHIP_OVERDUE' AND status='OPEN'",orderId);
        }
        if(order.getFulfillmentStatus()!=Order.FulfillmentStatus.SHIPPED || order.getRefundStatus()==Order.RefundStatus.FULL) {
            resolveReceiptReview(orderId);
        }
        boolean soon=order.getDeliveryMethod()==Order.DeliveryMethod.EXPRESS
                && order.getFulfillmentStatus()==Order.FulfillmentStatus.SHIPPED
                && order.getPayStatus()==Order.PayStatus.PAID && order.getRefundStatus()!=Order.RefundStatus.FULL
                && !order.isConfirmPaused() && order.getAutoConfirmAt()!=null && order.getAutoConfirmAt().isAfter(now)
                && !order.getAutoConfirmAt().isAfter(now.plusSeconds(86400));
        if(soon) {
            Long safe=jdbc.queryForObject("SELECT COUNT(*) FROM shipments WHERE order_id=? AND status='DELIVERED'",Long.class,orderId);
            Long active=jdbc.queryForObject("SELECT COUNT(*) FROM aftersales WHERE order_id=? AND status IN ('PENDING_SELLER','PENDING_RETURN','RETURN_SHIPPED','PENDING_MANUAL')",Long.class,orderId);
            if(safe!=null && safe>0 && active!=null && active==0 && record(orderId,"AUTO_RECEIPT_SOON","正常快递订单将在24小时内自动确认收货")) {
                notifications.notify(order.getBuyerId(),"AUTO_RECEIPT_SOON","订单即将自动确认收货","订单 "+order.getOrderNo()+" 将在24小时内自动确认。如未收到或存在问题，请及时申请售后；售后处理中会暂停自动确认。");
            }
        }
    }
    private void checkMeetup(Order order) {
        boolean overdue=order.getDeliveryMethod()==Order.DeliveryMethod.MEETUP
                && order.getFulfillmentStatus()==Order.FulfillmentStatus.AWAITING_MEETUP
                && order.getPayStatus()==Order.PayStatus.PAID && order.getRefundStatus()!=Order.RefundStatus.FULL
                && jdbc.queryForObject("SELECT COUNT(*) FROM meetup_appointments WHERE order_id=? AND status='ARRANGED' AND scheduled_at<=CURRENT_TIMESTAMP(6)",Long.class,order.getId())>0;
        if(overdue) {
            if(record(order.getId(),"MEETUP_OVERDUE","面交约定时间已过仍未完成，需双方重新约定或协商退款")) {
                for(Long userId:List.of(order.getBuyerId(),order.getSellerId()))notifications.notify(userId,"MEETUP_OVERDUE","面交约定已逾时","订单 "+order.getOrderNo()+" 约定的面交时间已过，请联系对方重新约定；需要取消时按售后流程协商退款，已进入人工待办。");
            } else {
                jdbc.update("UPDATE trade_reminder_events SET status='OPEN',resolved_at=NULL WHERE order_id=? AND event_type='MEETUP_OVERDUE' AND status='RESOLVED'",order.getId());
            }
        } else {
            jdbc.update("UPDATE trade_reminder_events SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP(6) WHERE order_id=? AND event_type='MEETUP_OVERDUE' AND status='OPEN'",order.getId());
        }
    }
    /** Called while the order row is locked by autoComplete; one event and two notices per order. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void deferReceipt(Order order) {
        if(record(order.getId(),"RECEIPT_CHECK_REQUIRED","自动收货期限已到，但尚无确认签收记录，已延期并转人工核查")) {
            notifications.notify(order.getBuyerId(),"RECEIPT_CHECK_REQUIRED","订单收货需要核查","订单 "+order.getOrderNo()+" 尚无确认签收记录，暂不自动收货，已进入人工待办；可联系卖家、申请售后或提交客服工单。");
            notifications.notify(order.getSellerId(),"RECEIPT_CHECK_REQUIRED","订单物流需要核查","订单 "+order.getOrderNo()+" 已到自动收货期限但尚无确认签收记录，请核对物流并配合买家和客服处理。");
        }
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void resolveReceiptReview(long orderId) {
        jdbc.update("UPDATE trade_reminder_events SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP(6) WHERE order_id=? AND event_type='RECEIPT_CHECK_REQUIRED' AND status='OPEN'",orderId);
    }
    private boolean record(long orderId,String type,String reason) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM trade_reminder_events WHERE order_id=? AND event_type=?",Long.class,orderId,type)>0)return false;
        jdbc.update("INSERT INTO trade_reminder_events(order_id,event_type,reason) VALUES(?,?,?)",orderId,type,reason);
        return true;
    }
    public List<Long> candidates() {
        return jdbc.queryForList("""
            SELECT o.id FROM orders o WHERE
              (o.fulfillment_status='PAID_PENDING_SHIP' AND o.ship_deadline<=CURRENT_TIMESTAMP(6)
                AND NOT EXISTS(SELECT 1 FROM trade_reminder_events e WHERE e.order_id=o.id AND e.event_type='SHIP_OVERDUE')) OR
              (o.fulfillment_status='SHIPPED' AND o.auto_confirm_at>CURRENT_TIMESTAMP(6)
                 AND o.auto_confirm_at<=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 24 HOUR)
                 AND o.confirm_paused=0 AND o.refund_status<>'FULL'
                 AND NOT EXISTS(SELECT 1 FROM aftersales a WHERE a.order_id=o.id AND a.status IN ('PENDING_SELLER','PENDING_RETURN','RETURN_SHIPPED','PENDING_MANUAL'))
                 AND EXISTS(SELECT 1 FROM shipments s WHERE s.order_id=o.id AND s.status='DELIVERED')
                 AND NOT EXISTS(SELECT 1 FROM trade_reminder_events e WHERE e.order_id=o.id AND e.event_type='AUTO_RECEIPT_SOON')) OR
              ((o.fulfillment_status<>'PAID_PENDING_SHIP' OR o.refund_status='FULL') AND
                EXISTS(SELECT 1 FROM trade_reminder_events e WHERE e.order_id=o.id AND e.event_type='SHIP_OVERDUE' AND e.status='OPEN')) OR
              ((o.fulfillment_status<>'SHIPPED' OR o.refund_status='FULL') AND
                EXISTS(SELECT 1 FROM trade_reminder_events e WHERE e.order_id=o.id AND e.event_type='RECEIPT_CHECK_REQUIRED' AND e.status='OPEN')) OR
              (o.fulfillment_status='AWAITING_MEETUP' AND o.refund_status<>'FULL'
                AND EXISTS(SELECT 1 FROM meetup_appointments m WHERE m.order_id=o.id AND m.status='ARRANGED' AND m.scheduled_at<=CURRENT_TIMESTAMP(6))
                AND NOT EXISTS(SELECT 1 FROM trade_reminder_events e WHERE e.order_id=o.id AND e.event_type='MEETUP_OVERDUE' AND e.status='OPEN')) OR
              (EXISTS(SELECT 1 FROM trade_reminder_events e WHERE e.order_id=o.id AND e.event_type='MEETUP_OVERDUE' AND e.status='OPEN')
                AND (o.fulfillment_status<>'AWAITING_MEETUP' OR o.refund_status='FULL'
                  OR NOT EXISTS(SELECT 1 FROM meetup_appointments m WHERE m.order_id=o.id AND m.status='ARRANGED' AND m.scheduled_at<=CURRENT_TIMESTAMP(6))))
            ORDER BY o.id LIMIT 100
            """,Long.class);
    }
    public List<Todo> todos(int page,int size) {
        if(page<0||page>100_000||size<1||size>100)throw BizException.badRequest("PAGE_INVALID","分页参数不合法");
        return jdbc.query("""
            SELECT e.id,e.order_id,o.order_no,o.buyer_id,o.seller_id,e.reason,e.created_at FROM trade_reminder_events e
            JOIN orders o ON o.id=e.order_id WHERE e.status='OPEN' AND o.refund_status<>'FULL'
              AND ((e.event_type='SHIP_OVERDUE' AND o.fulfillment_status='PAID_PENDING_SHIP')
                OR (e.event_type='RECEIPT_CHECK_REQUIRED' AND o.fulfillment_status='SHIPPED')
                OR (e.event_type='MEETUP_OVERDUE' AND o.fulfillment_status='AWAITING_MEETUP'
                  AND EXISTS(SELECT 1 FROM meetup_appointments m WHERE m.order_id=o.id AND m.status='ARRANGED' AND m.scheduled_at<=CURRENT_TIMESTAMP(6))))
            ORDER BY e.created_at,e.id LIMIT ? OFFSET ?
            """,(rs,row)->new Todo(rs.getLong("id"),rs.getLong("order_id"),rs.getString("order_no"),
                rs.getLong("buyer_id"),rs.getLong("seller_id"),rs.getString("reason"),rs.getTimestamp("created_at").toInstant()),size,(long)page*size);
    }
    public record Todo(long id,long orderId,String orderNo,long buyerId,long sellerId,String reason,Instant createdAt) {}
}
