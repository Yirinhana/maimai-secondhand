package com.maimai.support.service;

import com.maimai.common.BizException;
import com.maimai.common.SimpleRateLimiter;
import com.maimai.common.security.SecurityUtils;
import com.maimai.support.dto.SupportDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class SupportTicketService {
    private static final String JOIN = " FROM support_tickets t JOIN users u ON u.id=t.owner_id LEFT JOIN orders o ON o.id=t.order_id ";
    private static final RowMapper<Ticket> TICKET = (r,n) -> new Ticket(r.getLong("id"), r.getLong("owner_id"), r.getString("nickname"),
            r.getString("title"), r.getString("order_no"), r.getString("status"), nullable(r,"assigned_to"), instant(r,"created_at"), instant(r,"updated_at"));
    private static final RowMapper<Message> MESSAGE = (r,n) -> new Message(r.getLong("id"), r.getLong("ticket_id"), nullable(r,"author_id"),
            r.getString("author_kind"), r.getString("author_name"), r.getString("body"), instant(r,"created_at"));
    private final JdbcTemplate jdbc;
    private final SimpleRateLimiter limiter;
    private final com.maimai.notification.NotificationService notifications;
    public SupportTicketService(JdbcTemplate jdbc, SimpleRateLimiter limiter,com.maimai.notification.NotificationService notifications) { this.jdbc=jdbc; this.limiter=limiter; this.notifications=notifications; }

    @Transactional
    public Ticket create(CreateTicket input) {
        long actor = actor();
        limiter.require("support:create:"+actor,5,300,"工单创建频繁，请稍后重试");
        String title = text(input.title(),100), body = text(input.body(),2000);
        Long orderId = null;
        if (input.orderNo()!=null && !input.orderNo().isBlank()) {
            var rows = jdbc.query("SELECT id FROM orders WHERE order_no=? AND (buyer_id=? OR seller_id=?)",
                    (r,n)->r.getLong(1),text(input.orderNo(),32),actor,actor);
            if (rows.isEmpty()) throw BizException.notFound("订单不存在或不属于本人");
            orderId=rows.getFirst();
        }
        var transcripts=new ArrayList<String[]>();
        var requests=input.aiRequestIds()==null?java.util.List.<String>of():input.aiRequestIds().stream().distinct().toList();
        if(requests.size()>5)throw BizException.badRequest("HANDOFF_LIMIT","最多附上5段问答");
        for(String request:requests){
            var turns=jdbc.query("SELECT question,answer FROM support_ai_turns WHERE owner_id=? AND request_id=? AND status='COMPLETE'",(r,n)->new String[]{r.getString(1),r.getString(2)},actor,request);
            if(turns.isEmpty())throw BizException.notFound("所选问答已不可用，请重新选择");
            transcripts.add(turns.getFirst());
        }
        long id=insert("INSERT INTO support_tickets(owner_id,title,order_id) VALUES (?,?,?)",actor,title,orderId);
        addMessage(id,actor,"USER",body);
        for(var turn:transcripts){
            addMessage(id,actor,"USER","用户选择转交的提问：\n"+turn[0]);
            addMessage(id,null,"AI","麦仔当时的回复（仅供客服参考）：\n"+turn[1]);
        }
        notifications.notify(actor,"SUPPORT","人工工单已提交",title+"，可在工单内跟进回复。","/support/tickets/"+id);
        audit(id,actor,"CREATE",null,"OPEN");
        return ticket(id,false);
    }

    public Page<Ticket> mine(Integer page,Integer size) { return tickets("t.owner_id=?",page,size,actor()); }
    public Page<Ticket> queue(String status,Integer page,Integer size) {
        staff();
        String normalized=status==null||status.isBlank()?"OPEN":status.strip().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("OPEN","IN_PROGRESS","CLOSED").contains(normalized)) throw BizException.badRequest("TICKET_STATUS_INVALID","工单状态不支持");
        return tickets("t.status=?",page,size,normalized);
    }
    public Ticket detail(long id) { var t=ticket(id,false); access(t); return t; }
    public Page<Message> messages(long id,Integer page,Integer size) {
        access(ticket(id,false));
        var p=paging(page,size);
        var rows=jdbc.query("SELECT m.*,u.nickname author_name FROM support_messages m LEFT JOIN users u ON u.id=m.author_id WHERE m.ticket_id=? ORDER BY m.id DESC LIMIT ? OFFSET ?",MESSAGE,id,p[1],(long)p[0]*p[1]);
        return page(rows,jdbc.queryForObject("SELECT COUNT(*) FROM support_messages WHERE ticket_id=?",Long.class,id),p);
    }

    @Transactional
    public Message reply(long id,WriteMessage input) {
        var t=ticket(id,true); access(t); open(t);
        long actor=actor();
        if (t.ownerId()!=actor) assigned(t);
        limiter.require("support:reply:"+actor,30,60,"工单回复频繁，请稍后重试");
        var result=addMessage(id,actor,t.ownerId()==actor?"USER":"STAFF",text(input.body(),2000));
        audit(id,actor,"MESSAGE",t.status(),t.status());
        if(t.ownerId()!=actor)notifications.notify(t.ownerId(),"SUPPORT","客服回复了你的工单",t.title(),"/support/tickets/"+id);
        return result;
    }

    @Transactional
    public Ticket transition(long id,String action) {
        staff();
        limiter.require("support:state:"+actor(),30,60,"工单操作频繁，请稍后重试");
        var t=ticket(id,true);
        long actor=actor();
        String state;
        switch (action) {
            case "CLAIM" -> {
                open(t);
                if (t.assignedTo()!=null && t.assignedTo()!=actor && !superAdmin()) throw BizException.conflict("TICKET_ASSIGNED","工单已由其他客服接管");
                if (Objects.equals(t.assignedTo(),actor) && "IN_PROGRESS".equals(t.status())) return t;
                jdbc.update("UPDATE support_tickets SET assigned_to=? WHERE id=?",actor,id); state="IN_PROGRESS";
            }
            case "CLOSE" -> { open(t); assigned(t); state="CLOSED"; }
            case "REOPEN" -> {
                if (!"CLOSED".equals(t.status())) throw BizException.conflict("TICKET_NOT_CLOSED","只有已关闭工单可重新打开");
                assigned(t); jdbc.update("UPDATE support_tickets SET assigned_to=NULL WHERE id=?",id); state="OPEN";
            }
            default -> throw BizException.badRequest("TICKET_ACTION_INVALID","工单动作不支持");
        }
        jdbc.update("UPDATE support_tickets SET status=?,updated_at=NOW(6) WHERE id=?",state,id);
        String assigneeAfter="CLAIM".equals(action)?Long.toString(actor):"REOPEN".equals(action)?"null":String.valueOf(t.assignedTo());
        audit(id,actor,action,t.status()+":"+t.assignedTo(),state+":"+assigneeAfter);
        notifications.notify(t.ownerId(),"SUPPORT","工单进度已更新",t.title()+"："+switch(state){case "CLOSED"->"已关闭";case "IN_PROGRESS"->"客服处理中";default->"等待接待";},"/support/tickets/"+id);
        return ticket(id,false);
    }

    public void authorizeAi(long id) {
        var t=ticket(id,false); owner(t); open(t);
        limiter.require("support:ai:"+actor(),5,300,"AI问答频繁，请转人工或稍后重试");
    }
    @Transactional
    public Message saveAi(long id,String explanation) {
        var t=ticket(id,true); owner(t); open(t);
        Message result=addMessage(id,null,"AI",text(SupportFaq.NOTICE+explanation,2000));
        audit(id,actor(),"AI_FAQ",t.status(),t.status()); return result;
    }

    private Page<Ticket> tickets(String filter,Integer page,Integer size,Object...args) {
        var p=paging(page,size); var parameters=new ArrayList<>(Arrays.asList(args)); parameters.add(p[1]); parameters.add((long)p[0]*p[1]);
        var rows=jdbc.query("SELECT t.*,u.nickname,o.order_no"+JOIN+"WHERE "+filter+" ORDER BY t.updated_at DESC,t.id DESC LIMIT ? OFFSET ?",TICKET,parameters.toArray());
        return page(rows,jdbc.queryForObject("SELECT COUNT(*)"+JOIN+"WHERE "+filter,Long.class,args),p);
    }
    private Ticket ticket(long id,boolean lock) {
        if (lock && jdbc.query("SELECT id FROM support_tickets WHERE id=? FOR UPDATE",(r,n)->r.getLong(1),id).isEmpty()) throw BizException.notFound("工单不存在");
        var rows=jdbc.query("SELECT t.*,u.nickname,o.order_no"+JOIN+"WHERE t.id=?",TICKET,id);
        if (rows.isEmpty()) throw BizException.notFound("工单不存在"); return rows.getFirst();
    }
    private Message addMessage(long id,Long author,String kind,String body) {
        long message=insert("INSERT INTO support_messages(ticket_id,author_id,author_kind,body) VALUES (?,?,?,?)",id,author,kind,body);
        jdbc.update("UPDATE support_tickets SET updated_at=NOW(6) WHERE id=?",id);
        return jdbc.queryForObject("SELECT m.*,u.nickname author_name FROM support_messages m LEFT JOIN users u ON u.id=m.author_id WHERE m.id=?",MESSAGE,message);
    }
    private void audit(long ticket,long actor,String action,String before,String after) {
        jdbc.update("INSERT INTO support_action_logs(ticket_id,actor_id,action,before_state,after_state) VALUES (?,?,?,?,?)",ticket,actor,action,before,after);
    }
    private long insert(String sql,Object...args) {
        var key=new GeneratedKeyHolder(); jdbc.update(c->{var ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) ps.setObject(i+1,args[i]); return ps;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private static long actor() { return SecurityUtils.currentUserId(); }
    private static boolean superAdmin() { return SecurityUtils.current().hasRole("SUPER_ADMIN"); }
    private static boolean isStaff() { return superAdmin()||SecurityUtils.current().hasRole("SUPPORT"); }
    private static void staff() { if(!isStaff()) throw BizException.forbidden("需要人工客服或超级管理员权限"); }
    private static void owner(Ticket t) { if(t.ownerId()!=actor()) throw BizException.forbidden("仅工单本人可请求AI解释"); }
    private static void access(Ticket t) { if(t.ownerId()!=actor()&&!isStaff()) throw BizException.notFound("工单不存在"); }
    private static void assigned(Ticket t) { staff(); if(!superAdmin()&&!Objects.equals(t.assignedTo(),actor())) throw BizException.conflict("TICKET_NOT_ASSIGNED","请先接管工单或由已接管客服处理"); }
    private static void open(Ticket t) { if("CLOSED".equals(t.status())) throw BizException.conflict("TICKET_CLOSED","工单已关闭，请联系人工重新打开"); }
    private static String text(String s,int max) { if(s==null||s.isBlank()||s.strip().length()>max||s.indexOf('\0')>=0) throw BizException.badRequest("INVALID_CONTENT","内容不能为空且不能超过"+max+"字"); return s.strip(); }
    private static int[] paging(Integer page,Integer size) { int p=page==null?0:page,s=size==null?20:Math.min(size,50); if(p<0||p>10000||s<1) throw BizException.badRequest("PAGE_INVALID","分页参数无效"); return new int[]{p,s}; }
    private static <T> Page<T> page(java.util.List<T> rows,long count,int[] p) { return new Page<>(rows,count,p[0],p[1],(int)Math.min(Integer.MAX_VALUE,(count+p[1]-1)/p[1])); }
    private static Long nullable(ResultSet r,String field)throws SQLException { long n=r.getLong(field); return r.wasNull()?null:n; }
    private static Instant instant(ResultSet r,String field)throws SQLException { var t=r.getTimestamp(field); return t==null?null:t.toInstant(); }
}
