package com.maimai.trade.api;

import java.time.Instant;

/**
 * 交易模块对外操作契约（trade 实现，payment/aftersales 调用）。
 * 所有方法在调用方事务内执行，保证状态推进与库存变化原子一致。
 */
public interface TradeOrderOps {

    /**
     * 支付确认后推进订单（由 payment 模块在确认幂等校验通过后调用）：
     * payStatus→PAID、paidAt、发货时限 +72h、履约状态按交付方式推进
     * （EXPRESS→PAID_PENDING_SHIP；MEETUP→AWAITING_MEETUP）、预留库存转已售、通知卖家。
     * 仅当订单处于 PENDING_PAYMENT/UNPAID 时生效，重复调用幂等。
     *
     * @return true 表示本次实际推进；false 表示已是终态/已处理（调用方按迟付处理）
     */
    boolean markPaid(Long orderId, Instant paidAt);

    /**
     * 待付款阶段关闭订单（买家取消、30分钟超时、支付失败关闭）：
     * 关闭订单并一次性释放预留库存，重复调用不重复释放。
     */
    void closeUnpaid(Long orderId, String reason);

    /** 售后开启或物流异常时暂停自动确认收货（confirmPaused=true）。 */
    void pauseAutoConfirm(Long orderId, String reason);

    /** 售后关闭/解决后恢复自动确认资格（confirmPaused=false；已超期的由调度器下一轮处理）。 */
    void resumeAutoConfirm(Long orderId);
}
