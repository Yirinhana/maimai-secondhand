package com.maimai.payment.finance;

import com.maimai.common.BizException;
import com.maimai.common.FeeCalculator;
import com.maimai.payment.finance.FinanceDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Internal expected money flows. No call here marks provider reconciliation or settlement successful. */
@Service
public class FinanceService {
    private static final String SELECT = """
        SELECT o.*, COALESCE(r.goods,0) refunded_goods, COALESCE(r.freight,0) refunded_freight,
          COALESCE(r.fee,0) refunded_fee, p.channel payment_channel, COALESCE(p.simulated,0) simulated,
          COALESCE(a.status,'NOT_RECORDED') allocation_status
        FROM orders o
        LEFT JOIN (SELECT order_id,SUM(goods_refund_cents) goods,SUM(freight_refund_cents) freight,
          SUM(platform_fee_refund_cents) fee FROM refunds WHERE status='SUCCESS' GROUP BY order_id) r ON r.order_id=o.id
        LEFT JOIN payment_requests p ON p.id=(SELECT MAX(p2.id) FROM payment_requests p2 WHERE p2.order_id=o.id)
        LEFT JOIN finance_allocation_expectations a ON a.order_id=o.id
        """;
    private final JdbcTemplate jdbc;
    public FinanceService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Money money(long orderId) {
        Summary value = summary(orderId);
        return new Money(value.retainedPlatformFeeCents(), value.channelFeeCents(), value.channelFeeConfirmed(),
                value.expectedSellerNetCents(), value.simulated(), value.allocationStatus());
    }
    public Summary summary(long orderId) {
        var rows=jdbc.query(SELECT+" WHERE o.id=?",this::map,orderId);
        if(rows.isEmpty()) throw BizException.notFound("订单不存在");
        return rows.getFirst();
    }
    public Detail detail(long orderId) {
        var value=summary(orderId);
        var ledger=jdbc.query("SELECT * FROM ledger_entries WHERE order_id=? ORDER BY id",(rs,row)->
                new Ledger(rs.getLong("id"),rs.getString("entry_type"),rs.getLong("amount_cents"),
                        rs.getString("ref_type"),rs.getObject("ref_id",Long.class),rs.getTimestamp("created_at").toInstant()),orderId);
        return new Detail(value,ledger,"PROVIDER_NOT_CONNECTED",
                "本站内部流水及预期分配；未核对渠道账单，未执行真实分账。渠道费用未确认时卖家预计净额为空。");
    }
    public Page list(LocalDate from,LocalDate to,int page,int size) {
        Range range=range(from,to);
        if(page<0 || page>100_000 || size<1 || size>100) throw BizException.badRequest("PAGE_INVALID","分页参数不合法");
        String where=" WHERE o.created_at>=? AND o.created_at<?";
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM orders o"+where,Long.class,range.start(),range.end());
        var rows=jdbc.query(SELECT+where+" ORDER BY o.created_at DESC,o.id DESC LIMIT ? OFFSET ?",
                this::map,range.start(),range.end(),size,(long)page*size);
        return new Page(rows,total,page,size);
    }
    public String csv(LocalDate from,LocalDate to) {
        Range range=range(from,to);
        var rows=jdbc.query(SELECT+" WHERE o.created_at>=? AND o.created_at<? ORDER BY o.id LIMIT 5001",
                this::map,range.start(),range.end());
        if(rows.size()>5000) throw BizException.badRequest("EXPORT_TOO_LARGE","一次最多导出5000笔，请缩小日期范围");
        StringBuilder csv=new StringBuilder("\uFEFF订单号,商品金额分,运费分,商品退款分,运费退款分,原平台费分,保留平台费分,已退平台费分,应补退平台费分,渠道费分,渠道费已确认,预计卖家净额分,模拟资金,支付渠道,支付状态,退款状态,预期分配状态,渠道对账状态\r\n");
        for(Summary row:rows) {
            Object[] columns={row.orderNo(),row.goodsAmountCents(),row.freightCents(),row.goodsRefundedCents(),row.freightRefundedCents(),
                row.originalPlatformFeeCents(),row.retainedPlatformFeeCents(),row.platformFeeRefundedCents(),row.platformFeeRefundDueCents(),
                row.channelFeeCents(),row.channelFeeConfirmed(),row.expectedSellerNetCents(),row.simulated(),row.channel(),row.payStatus(),
                row.refundStatus(),row.allocationStatus(),"PROVIDER_NOT_CONNECTED"};
            for(int i=0;i<columns.length;i++){ if(i>0)csv.append(',');csv.append(csvCell(columns[i])); }
            csv.append("\r\n");
        }
        return csv.toString();
    }
    public static String csvCell(Object value) {
        if(value==null)return "\"\"";
        String raw=value.toString(), trimmed=raw.stripLeading();
        if((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0) || raw.startsWith("\t") || raw.startsWith("\r")) raw="'"+raw;
        return "\""+raw.replace("\"","\"\"")+"\"";
    }

    /** Caller flushes its order/payment/refund writes before capturing the same transaction's expectation. */
    @Transactional
    public void captureExpectedAllocation(long orderId) {
        jdbc.queryForList("SELECT id FROM orders WHERE id=? FOR UPDATE",orderId);
        Summary value=summary(orderId);
        if(!"PAID".equals(value.payStatus()))return;
        jdbc.update("""
            INSERT INTO finance_allocation_expectations(order_id,goods_remaining_cents,freight_remaining_cents,
              platform_expected_cents,seller_expected_cents,channel_fee_cents,channel_fee_confirmed,simulated,status)
            VALUES(?,?,?,?,?,?,?,?,'WAITING_CHANNEL') ON DUPLICATE KEY UPDATE
              goods_remaining_cents=VALUES(goods_remaining_cents),freight_remaining_cents=VALUES(freight_remaining_cents),
              platform_expected_cents=VALUES(platform_expected_cents),seller_expected_cents=VALUES(seller_expected_cents),
              channel_fee_cents=VALUES(channel_fee_cents),channel_fee_confirmed=VALUES(channel_fee_confirmed),
              simulated=VALUES(simulated),updated_at=CURRENT_TIMESTAMP(6)
            """,orderId,value.goodsAmountCents()-value.goodsRefundedCents(),value.freightCents()-value.freightRefundedCents(),
                value.retainedPlatformFeeCents(),value.expectedSellerNetCents(),value.channelFeeCents(),value.channelFeeConfirmed(),value.simulated());
    }
    private Summary map(ResultSet rs,int row) throws SQLException {
        long goods=rs.getLong("goods_amount_cents"),freight=rs.getLong("freight_cents");
        long refundedGoods=rs.getLong("refunded_goods"),refundedFreight=rs.getLong("refunded_freight"),refundedFee=rs.getLong("refunded_fee");
        long originalFee=rs.getLong("platform_fee_cents"),retained=FeeCalculator.platformFee(Math.max(0,goods-refundedGoods));
        Long channelFee=rs.getObject("channel_fee_cents",Long.class);
        boolean confirmed=rs.getBoolean("channel_fee_confirmed") && channelFee!=null;
        Long sellerNet=confirmed ? goods-refundedGoods+freight-refundedFreight-retained-channelFee : null;
        Timestamp paidAt=rs.getTimestamp("paid_at");
        return new Summary(rs.getLong("id"),rs.getString("order_no"),rs.getLong("buyer_id"),rs.getLong("seller_id"),
                goods,freight,refundedGoods,refundedFreight,originalFee,retained,refundedFee,Math.max(0,originalFee-retained-refundedFee),
                confirmed?channelFee:null,confirmed,sellerNet,rs.getBoolean("simulated"),rs.getString("payment_channel"),
                rs.getString("pay_status"),rs.getString("refund_status"),rs.getString("fulfillment_status"),rs.getString("allocation_status"),
                rs.getTimestamp("created_at").toInstant(),paidAt==null?null:paidAt.toInstant());
    }
    private Range range(LocalDate from,LocalDate to) {
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        if(to==null)to=LocalDate.now(zone);if(from==null)from=to.minusDays(30);
        if(from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>366)throw BizException.badRequest("DATE_RANGE_INVALID","日期范围需在366天内且起始不晚于结束");
        return new Range(Timestamp.from(from.atStartOfDay(zone).toInstant()),Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()));
    }
    private record Range(Timestamp start,Timestamp end) {}
}
