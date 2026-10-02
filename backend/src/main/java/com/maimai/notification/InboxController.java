package com.maimai.notification;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

/** Actionable notifications; private chat keeps its own read positions and conversation history. */
@RestController
@RequestMapping("/api/v1/me/inbox")
public class InboxController {
    private static final String CATEGORY = "CASE WHEN type IN ('AFTERSALE','REFUND','SUPPORT') THEN 'SERVICE' WHEN type IN ('ORDER','PAYMENT','SHIP_OVERDUE','AUTO_RECEIPT_SOON','MEETUP_OVERDUE','RECEIPT_CHECK_REQUIRED','RATING_REMINDER','RATING_SUBMITTED','REPUTATION_UPDATED','BARGAIN') THEN 'TRADE' WHEN type='SEARCH_MATCH' THEN 'DISCOVERY' ELSE 'SYSTEM' END";
    private final JdbcTemplate jdbc;
    public InboxController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Entry(long id,String title,String content,String category,boolean read,Instant createdAt,String targetPath) {}
    public record Page(List<Entry> items,long total,int page,int totalPages,long unread) {}

    @GetMapping
    public Page list(@RequestParam(defaultValue="ALL") String category,@RequestParam(defaultValue="false") boolean unreadOnly,@RequestParam(defaultValue="0") int page) {
        if(!Set.of("ALL","TRADE","SERVICE","DISCOVERY","SYSTEM").contains(category)||page<0||page>10000)throw BizException.badRequest("INBOX_FILTER","通知筛选条件无效");
        long user=SecurityUtils.currentUserId();
        var args=new ArrayList<Object>();args.add(user);
        String filter="user_id=?"+(unreadOnly?" AND is_read=0":"");
        if(!category.equals("ALL")){filter+=" AND ("+CATEGORY+")=?";args.add(category);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE "+filter,Long.class,args.toArray());
        args.add((long)page*20);
        var rows=jdbc.query("SELECT *,"+CATEGORY+" category FROM notifications WHERE "+filter+" ORDER BY id DESC LIMIT 20 OFFSET ?",(r,n)->new Entry(r.getLong("id"),r.getString("title"),r.getString("content"),r.getString("category"),r.getBoolean("is_read"),r.getTimestamp("created_at").toInstant(),destination(user,r.getString("type"),r.getString("content"),r.getString("target_path"))),args.toArray());
        long unread=jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND is_read=0",Long.class,user);
        return new Page(rows,total,page,(int)((total+19)/20),unread);
    }

    // Historical notices have no target column. Resolve only known, owned business identifiers.
    private String destination(long user,String type,String content,String target) {
        if(target!=null&&(target.matches("/(?:orders|aftersales|support/tickets|products|publish|messages)/[A-Za-z0-9-]+")||target.matches("/(?:seller|me)/bargains#[0-9]+")))return target;
        var aftersale=java.util.regex.Pattern.compile("售后单[：: ]*([A-Za-z0-9-]{4,32})").matcher(content);
        if(aftersale.find()){
            var cases=jdbc.query("SELECT a.id FROM aftersales a JOIN orders o ON o.id=a.order_id WHERE a.aftersale_no=? AND (o.buyer_id=? OR o.seller_id=?)",(r,n)->r.getLong(1),aftersale.group(1),user,user);
            if(!cases.isEmpty())return "/aftersales/"+cases.getFirst();
        }
        var match=java.util.regex.Pattern.compile("订单[：: ]*([A-Za-z0-9-]{4,32})").matcher(content);
        if(match.find()) {
            var orders=jdbc.query("SELECT order_no FROM orders WHERE order_no=? AND (buyer_id=? OR seller_id=?)",(r,n)->r.getString(1),match.group(1),user,user);
            if(!orders.isEmpty())return "/orders/"+orders.getFirst();
        }
        return switch(type) {
            case "PRODUCT_REVIEW" -> "/seller/products";
            case "SELLER_APPLICATION","USER_STATUS" -> "/me";
            case "AFTERSALE","REFUND" -> "/me/aftersales";
            case "SUPPORT" -> "/support";
            case "REPORT_RECEIVED","REPORT_PROCESSED","CONTENT_MODERATED","REPUTATION_UPDATED" -> "/me/community";
            default -> null;
        };
    }
}
