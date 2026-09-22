package com.maimai.admin.service;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.community.repository.CommunityRepository;
import com.maimai.trade.service.TransactionProvenance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Every figure is a database aggregate with an explicit provenance and time basis. */
@Service
@Transactional(readOnly=true)
public class AdminOperationsService {
    private final CommunityRepository repo;
    public AdminOperationsService(CommunityRepository repo){this.repo=repo;}
    private static final String SOURCE=TransactionProvenance.sql("o");
    private static final String BASE=" (SELECT o.*, "+SOURCE+" source FROM orders o) o ";
    public record SourceTotals(String source,String label,long orders,long paidOrders,long completedOrders,Long paidCents,Long refundedCents,Long calculatedFeeCents){}
    public record Trend(String date,long orders,long simulatedPaid,long livePaid){}
    public record Issue(String key,String label,long count){}
    public record Dashboard(Instant generatedAt,String timezone,String sourceNotice,long users,long activeUsers,long listedProducts,List<SourceTotals> sources,List<Trend> trend,Map<String,Long> queues,List<Issue> issues){}
    public Dashboard dashboard(){
        SecurityUtils.requireAdmin();
        boolean finance=SecurityUtils.current().roles().contains("SUPER_ADMIN");
        List<SourceTotals> sources=new ArrayList<>();
        for(String source:List.of("SIMULATED","LIVE","HISTORICAL","UNPAID","UNVERIFIED")){
            sources.add(repo.queryOne("SELECT COUNT(*) n,COALESCE(SUM(o.pay_status='PAID'),0) paid,COALESCE(SUM(o.fulfillment_status='COMPLETED'),0) completed,COALESCE(SUM(CASE WHEN o.pay_status='PAID' THEN o.total_cents ELSE 0 END),0) amount,COALESCE(SUM(CASE WHEN o.pay_status='PAID' THEN o.platform_fee_cents ELSE 0 END),0) fee,COALESCE(SUM((SELECT COALESCE(SUM(r.goods_refund_cents+r.freight_refund_cents),0) FROM refunds r WHERE r.order_id=o.id AND r.status='SUCCESS')),0) refunded,COALESCE(SUM((SELECT COALESCE(SUM(r.platform_fee_refund_cents),0) FROM refunds r WHERE r.order_id=o.id AND r.status='SUCCESS')),0) fee_refunded FROM "+BASE+" WHERE o.source=?",
                (rs,n)->new SourceTotals(source,TransactionProvenance.label(source),rs.getLong("n"),rs.getLong("paid"),rs.getLong("completed"),finance?rs.getLong("amount"):null,finance?rs.getLong("refunded"):null,finance?rs.getLong("fee")-rs.getLong("fee_refunded"):null),source));
        }
        var zone=ZoneOffset.ofHours(8);LocalDate today=LocalDate.now(zone),start=today.minusDays(13);
        var rows=repo.query("SELECT DATE(DATE_ADD(o.created_at,INTERVAL 8 HOUR)) day,COUNT(*) n,SUM(o.source='SIMULATED' AND o.pay_status='PAID') simulated,SUM(o.source='LIVE' AND o.pay_status='PAID') live FROM "+BASE+" WHERE o.created_at>=? GROUP BY day",(rs,n)->new Trend(rs.getString("day"),rs.getLong("n"),rs.getLong("simulated"),rs.getLong("live")),Timestamp.from(start.atStartOfDay(zone).toInstant()));
        Map<String,Trend> byDay=new HashMap<>();rows.forEach(row->byDay.put(row.date(),row));
        List<Trend> trend=new ArrayList<>();for(int day=0;day<14;day++){String date=start.plusDays(day).toString();trend.add(byDay.getOrDefault(date,new Trend(date,0,0,0)));}
        var queues=new LinkedHashMap<String,Long>();
        queues.put("reports",repo.count("SELECT COUNT(*) FROM community_reports WHERE status='PENDING'"));
        queues.put("products",repo.count("SELECT COUNT(*) FROM products WHERE status='PENDING_REVIEW'"));
        queues.put("tickets",repo.count("SELECT COUNT(*) FROM support_tickets WHERE status IN ('OPEN','IN_PROGRESS')"));
        queues.put("aftersales",repo.count("SELECT COUNT(*) FROM aftersales WHERE status='PENDING_MANUAL'"));
        var issues=List.of(
            issue("order_accounts","订单买卖双方关联异常","SELECT COUNT(*) FROM orders o LEFT JOIN users b ON b.id=o.buyer_id LEFT JOIN users s ON s.id=o.seller_id WHERE b.id IS NULL OR s.id IS NULL OR o.buyer_id=o.seller_id"),
            issue("order_products","订单商品与卖家不一致","SELECT COUNT(*) FROM order_items i JOIN orders o ON o.id=i.order_id LEFT JOIN products p ON p.id=i.product_id WHERE p.id IS NULL OR p.seller_id<>o.seller_id"),
            issue("ratings","评价参与者关联异常","SELECT COUNT(*) FROM community_order_ratings r JOIN orders o ON o.id=r.order_id WHERE NOT ((r.rater_id=o.buyer_id AND r.ratee_id=o.seller_id) OR (r.rater_id=o.seller_id AND r.ratee_id=o.buyer_id))"),
            issue("paid_receipts","已付款但缺少付款凭据","SELECT COUNT(*) FROM "+BASE+" WHERE o.source='UNVERIFIED'"),
            issue("ticket_orders","工单关联订单不属于提交人","SELECT COUNT(*) FROM support_tickets t LEFT JOIN orders o ON o.id=t.order_id WHERE t.order_id IS NOT NULL AND (o.id IS NULL OR (t.owner_id<>o.buyer_id AND t.owner_id<>o.seller_id))")
        );
        return new Dashboard(Instant.now(),"Asia/Shanghai","数据直接汇总自账号、订单、付款、退款、评价、举报和工单表；模拟金额不代表真实收款，服务费为账面计算额而非已结算收入。趋势按订单创建日分组，显示这些订单的当前付款来源。",repo.count("SELECT COUNT(*) FROM users"),repo.count("SELECT COUNT(*) FROM users WHERE status='ACTIVE'"),repo.count("SELECT COUNT(*) FROM products p JOIN users u ON u.id=p.seller_id WHERE p.status='ON_SALE' AND u.status='ACTIVE'"),sources,trend,queues,issues);
    }
    private Issue issue(String key,String label,String sql){return new Issue(key,label,repo.count(sql));}
    public record OrderRow(long id,String orderNo,long buyerId,String buyerNickname,long sellerId,String sellerNickname,String source,String fulfillmentStatus,String payStatus,String refundStatus,long totalCents,Instant createdAt){}
    public record OrderPage(List<OrderRow> items,long total,int page,int size){}
    public record Item(long productId,String title,int quantity,long unitPriceCents){}
    public record Related(long id,String title,String status){}
    public record OrderDetail(OrderRow order,List<Item> items,List<Related> payments,List<Related> refunds,List<Related> tickets,String deliveryMethod,String deliveryStatus,String notice){}
    private static final String ORDERS="SELECT o.*,b.nickname buyer_name,s.nickname seller_name FROM "+BASE+" LEFT JOIN users b ON b.id=o.buyer_id LEFT JOIN users s ON s.id=o.seller_id ";
    private static final org.springframework.jdbc.core.RowMapper<OrderRow> ORDER=(rs,n)->new OrderRow(rs.getLong("id"),rs.getString("order_no"),rs.getLong("buyer_id"),Objects.toString(rs.getString("buyer_name"),"账号资料待核查"),rs.getLong("seller_id"),Objects.toString(rs.getString("seller_name"),"账号资料待核查"),rs.getString("source"),rs.getString("fulfillment_status"),rs.getString("pay_status"),rs.getString("refund_status"),rs.getLong("total_cents"),rs.getTimestamp("created_at").toInstant());
    public OrderPage orders(String source,String orderNo,int page,int size){
        requireOrderStaff();
        if(page<0||page>10000||size<1||size>50)throw BizException.badRequest("PAGE_INVALID","分页参数无效");
        String filter=" WHERE 1=1 ";List<Object> args=new ArrayList<>();
        if(source!=null&&!source.isBlank()){
            if(!Set.of("SIMULATED","LIVE","HISTORICAL","UNPAID","UNVERIFIED").contains(source))throw BizException.badRequest("SOURCE_INVALID","交易来源无效");
            filter+=" AND o.source=?";args.add(source);
        }
        if(orderNo!=null&&!orderNo.isBlank()){if(orderNo.length()>32)throw BizException.badRequest("ORDER_NO_INVALID","订单号过长");filter+=" AND o.order_no=?";args.add(orderNo.strip());}
        long total=repo.count("SELECT COUNT(*) FROM "+BASE+filter,args.toArray());args.add(size);args.add(page*size);
        return new OrderPage(repo.query(ORDERS+filter+" ORDER BY o.created_at DESC,o.id DESC LIMIT ? OFFSET ?",ORDER,args.toArray()),total,page,size);
    }
    public OrderDetail order(String orderNo){
        requireOrderStaff();
        OrderRow order=repo.queryOne(ORDERS+" WHERE o.order_no=?",ORDER,orderNo);
        if(order==null)throw BizException.notFound("订单不存在");
        var items=repo.query("SELECT product_id,title,quantity,price_cents FROM order_items WHERE order_id=? ORDER BY id",(rs,n)->new Item(rs.getLong(1),rs.getString(2),rs.getInt(3),rs.getLong(4)),order.id());
        var payments=repo.query("SELECT id,pay_no,status,simulated FROM payment_requests WHERE order_id=? ORDER BY id",(rs,n)->new Related(rs.getLong(1),rs.getString(2)+(rs.getBoolean(4)?" · 模拟付款":" · 正式渠道"),rs.getString(3)),order.id());
        var refunds=repo.query("SELECT id,refund_no,status,simulated FROM refunds WHERE order_id=? ORDER BY id",(rs,n)->new Related(rs.getLong(1),rs.getString(2)+(rs.getBoolean(4)?" · 模拟退款":" · 正式渠道"),rs.getString(3)),order.id());
        var tickets=repo.query("SELECT id,title,status FROM support_tickets WHERE order_id=? ORDER BY id",(rs,n)->new Related(rs.getLong(1),rs.getString(2),rs.getString(3)),order.id());
        String delivery=repo.queryOne("SELECT delivery_method FROM orders WHERE id=?",(rs,n)->rs.getString(1),order.id());
        String deliveryStatus=repo.queryOne("SELECT status FROM shipments WHERE order_id=?",(rs,n)->rs.getString(1),order.id());
        if(deliveryStatus==null)deliveryStatus=repo.queryOne("SELECT status FROM meetup_appointments WHERE order_id=? ORDER BY id DESC LIMIT 1",(rs,n)->rs.getString(1),order.id());
        return new OrderDetail(order,items,payments,refunds,tickets,delivery,deliveryStatus,"账号名称来自用户表，商品标题为下单时快照；当前页不展示收货电话和详细地址。历史体验记录不补造付款流水，模拟支付只更新站内交易状态。");
    }
    private void requireOrderStaff(){
        if(!SecurityUtils.current().hasRole("SUPER_ADMIN")&&!SecurityUtils.current().hasRole("SUPPORT"))throw BizException.forbidden("订单核查需要客服或超级管理员权限");
    }
}
