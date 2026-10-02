package com.maimai.catalog.controller;

import com.maimai.common.security.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/seller/tasks")
public class SellerTasksController {
    private final JdbcTemplate jdbc;
    public SellerTasksController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Task(String kind,String title,String targetPath,Instant deadline) {}
    public record Tasks(Map<String,Long> counts,List<Task> items) {}
    @GetMapping
    public Tasks mine(){
        long user=SecurityUtils.currentUserId();
        var counts=new LinkedHashMap<String,Long>();var tasks=new ArrayList<Task>();
        String orders="FROM orders WHERE seller_id=? AND pay_status='PAID' AND fulfillment_status='PAID_PENDING_SHIP' AND refund_status<>'FULL'";
        add(counts,tasks,"SHIP","待发货订单 ","'/orders/'", "order_no","ship_deadline",orders,user);
        String bargains="FROM bargain_offers b JOIN products p ON p.id=b.product_id WHERE p.seller_id=? AND b.status='PENDING' AND b.expires_at>UTC_TIMESTAMP(6)";
        counts.put("BARGAIN",jdbc.queryForObject("SELECT COUNT(*) "+bargains,Long.class,user));
        tasks.addAll(jdbc.query("SELECT p.title,b.expires_at "+bargains+" ORDER BY b.expires_at LIMIT 5",(r,n)->new Task("BARGAIN","待回复议价 · "+r.getString(1),"/seller/bargains",r.getTimestamp(2).toInstant()),user));
        String aftersales="FROM aftersales a JOIN orders o ON o.id=a.order_id WHERE o.seller_id=? AND a.status IN ('PENDING_SELLER','RETURN_SHIPPED')";
        add(counts,tasks,"AFTERSALE","待处理售后 ","'/aftersales/'","a.id","CASE WHEN a.status='RETURN_SHIPPED' THEN a.return_inspection_deadline ELSE a.seller_deadline END",aftersales,user);
        long unread=jdbc.queryForObject("SELECT COUNT(*) FROM direct_messages m JOIN message_conversations c ON c.id=m.conversation_id WHERE (c.user_low=? OR c.user_high=?) AND m.sender_id<>? AND m.id>COALESCE((SELECT r.last_read_id FROM message_read_positions r WHERE r.conversation_id=c.id AND r.user_id=?),0)",Long.class,user,user,user,user);
        counts.put("MESSAGE",unread);
        if(unread>0)tasks.add(new Task("MESSAGE","有 "+unread+" 条未读私信","/messages?unread=1",null));
        tasks.sort(Comparator.comparing(Task::deadline,Comparator.nullsLast(Comparator.naturalOrder())));
        return new Tasks(counts,tasks);
    }
    private void add(Map<String,Long> counts,List<Task> tasks,String kind,String title,String prefix,String id,String deadline,String from,long user){
        counts.put(kind,jdbc.queryForObject("SELECT COUNT(*) "+from,Long.class,user));
        tasks.addAll(jdbc.query("SELECT "+id+",CONCAT("+prefix+","+id+"),"+deadline+" "+from+" ORDER BY "+deadline+" LIMIT 5",(r,n)->new Task(kind,title+r.getString(1),r.getString(2),r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant()),user));
    }
}
