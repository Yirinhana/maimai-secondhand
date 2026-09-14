package com.maimai.trade.service;

import com.maimai.common.BizException;
import com.maimai.trade.domain.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;

/** Called inside the refund transaction with the order row already locked. */
@Service
public class RefundFulfillmentService {
    private final JdbcTemplate jdbc;
    public RefundFulfillmentService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void closeUnshippedFullRefund(Order order) {
        if(order.getRefundStatus()!=Order.RefundStatus.FULL
                || (order.getFulfillmentStatus()!=Order.FulfillmentStatus.PAID_PENDING_SHIP
                && order.getFulfillmentStatus()!=Order.FulfillmentStatus.AWAITING_MEETUP)) return;
        var items=jdbc.queryForList("SELECT product_id,quantity FROM order_items WHERE order_id=? ORDER BY product_id,id",order.getId());
        for(var item:items) {
            long productId=((Number)item.get("product_id")).longValue();int quantity=((Number)item.get("quantity")).intValue();
            int changed=jdbc.update("UPDATE products SET stock_sold=stock_sold-?,stock_available=stock_available+?,version=version+1 WHERE id=? AND stock_sold>=? AND stock_available<=?",
                    quantity,quantity,productId,quantity,Integer.MAX_VALUE-quantity);
            if(changed!=1)throw BizException.conflict("REFUND_STOCK_INVALID","未交付退款库存异常，需要人工核查");
            jdbc.update("INSERT INTO stock_logs(product_id,delta_available,delta_sold,reason,ref_type,ref_id) VALUES(?,?,?,'REFUND_RESTOCK','ORDER',?)",
                    productId,quantity,-quantity,order.getId());
        }
        order.setFulfillmentStatus(Order.FulfillmentStatus.CLOSED);
        order.setClosedAt(Instant.now());order.setCloseReason("未交付订单已全额退款");
        order.setConfirmPaused(false);
        jdbc.update("UPDATE delivery_codes SET invalidated=1 WHERE order_id=?",order.getId());
        jdbc.update("UPDATE trade_reminder_events SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP(6) WHERE order_id=? AND status='OPEN'",order.getId());
    }
}
