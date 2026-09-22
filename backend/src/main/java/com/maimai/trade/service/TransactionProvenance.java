package com.maimai.trade.service;

/** A paid flag alone is not evidence of a channel receipt. All views share these definitions. */
public final class TransactionProvenance {
    private TransactionProvenance() { }
    public static String sql(String alias) {
        if (!alias.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Invalid SQL alias");
        return "CASE WHEN " + alias + ".experience_source IS NOT NULL AND " + alias + ".experience_source<>'" + com.maimai.trade.domain.Order.INTERACTIVE_EXPERIENCE + "' THEN 'HISTORICAL' "
                + "WHEN EXISTS(SELECT 1 FROM payment_requests source_pay WHERE source_pay.order_id=" + alias + ".id AND source_pay.status='PAID' AND source_pay.simulated=1) THEN 'SIMULATED' "
                + "WHEN EXISTS(SELECT 1 FROM payment_requests source_pay WHERE source_pay.order_id=" + alias + ".id AND source_pay.status='PAID' AND source_pay.simulated=0) THEN 'LIVE' "
                + "WHEN " + alias + ".pay_status IN ('UNPAID','PAYING','CLOSED') THEN 'UNPAID' ELSE 'UNVERIFIED' END";
    }
    public static String label(String source) {
        return switch(source) {
            case "HISTORICAL" -> "历史体验记录";
            case "SIMULATED" -> "模拟支付交易";
            case "LIVE" -> "正式渠道交易";
            case "UNPAID" -> "尚未付款";
            default -> "付款凭据待核查";
        };
    }
}
