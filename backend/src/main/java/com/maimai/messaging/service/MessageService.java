package com.maimai.messaging.service;

import com.maimai.common.BizException;
import com.maimai.messaging.dto.MessageDtos.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class MessageService {
    private final JdbcTemplate db;
    private final ApplicationEventPublisher events;
    public MessageService(JdbcTemplate db, ApplicationEventPublisher events) {
        this.db = db; this.events = events;
    }

    @Transactional
    public long open(long actor, OpenConversation request) {
        long recipient = request.recipientId();
        if (recipient == actor) throw BizException.badRequest("MESSAGE_SELF", "不能给自己发私信");
        long low = Math.min(actor, recipient), high = Math.max(actor, recipient);
        // Lock both users in a stable order before creating foreign-key references.
        lockActiveUser(low);
        lockActiveUser(high);
        requireUnblocked(actor, recipient);
        if (request.productId() != null && count("SELECT COUNT(*) FROM products WHERE id=? AND seller_id=? AND status='ON_SALE'", request.productId(), recipient) != 1)
            throw BizException.notFound("商品暂不可咨询");
        List<Long> existing = db.queryForList("SELECT id FROM message_conversations WHERE user_low=? AND user_high=?", Long.class, low, high);
        if (!existing.isEmpty()) return existing.getFirst();
        if (count("SELECT COUNT(*) FROM message_conversations WHERE created_by=? AND created_at>DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR)", actor) >= 10)
            throw BizException.tooMany("新建会话过于频繁，请稍后重试");
        db.update("INSERT INTO message_conversations(user_low,user_high,product_id,created_by) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE id=id", low, high, request.productId(), actor);
        return db.queryForObject("SELECT id FROM message_conversations WHERE user_low=? AND user_high=?", Long.class, low, high);
    }

    public List<Conversation> conversations(long actor, int page, int size) {
        validatePage(page, size);
        return db.query("""
            SELECT c.id, c.product_id, c.updated_at,
              CASE WHEN c.user_low=? THEN c.user_high ELSE c.user_low END other_id,
              u.nickname,
              (SELECT COALESCE(m.body,'[图片]') FROM direct_messages m WHERE m.conversation_id=c.id ORDER BY m.id DESC LIMIT 1) last_message,
              (SELECT COUNT(*) FROM direct_messages m WHERE m.conversation_id=c.id AND m.sender_id<>?
                 AND m.id>COALESCE((SELECT last_read_id FROM message_read_positions r WHERE r.conversation_id=c.id AND r.user_id=?),0)) unread
            FROM message_conversations c
            JOIN users u ON u.id=CASE WHEN c.user_low=? THEN c.user_high ELSE c.user_low END
            WHERE c.user_low=? OR c.user_high=?
            ORDER BY c.updated_at DESC,c.id DESC LIMIT ? OFFSET ?
            """, (rs,n) -> new Conversation(rs.getLong("id"),rs.getLong("other_id"),rs.getString("nickname"),
                rs.getObject("product_id",Long.class),rs.getString("last_message"),rs.getTimestamp("updated_at").toInstant(),rs.getLong("unread")),
            actor, actor, actor, actor, actor, actor, size, (long)page*size);
    }

    public History history(long actor, long conversation, Long beforeId, int size) {
        requireMember(actor,conversation);
        if (size<1 || size>50 || beforeId!=null && beforeId<1) throw BizException.badRequest("MESSAGE_PAGE", "分页参数不正确");
        List<Message> rows = db.query("SELECT * FROM direct_messages WHERE conversation_id=? AND id<? ORDER BY id DESC LIMIT ?",
            messageMapper(), conversation, beforeId==null?Long.MAX_VALUE:beforeId, size+1);
        boolean more = rows.size()>size;
        List<Message> items = List.copyOf(rows.subList(0,Math.min(rows.size(),size)));
        return new History(items,more, more ? items.getLast().id() : null);
    }

    @Transactional
    public Message send(long actor,long conversation,SendMessage request) {
        // Acquire the per-sender lock before the first consistent read. Otherwise MySQL
        // REPEATABLE READ could preserve a stale quota snapshot while waiting on the lock.
        lockActiveUser(actor);
        List<Long> allowed=db.queryForList("SELECT id FROM message_conversations WHERE id=? AND (user_low=? OR user_high=?) FOR UPDATE",Long.class,conversation,actor,actor);
        if(allowed.isEmpty()) throw BizException.notFound("会话不存在或不可访问");
        requireCanSend(actor, conversation);
        String body = request.body()==null?null:request.body().strip();
        if (body!=null && body.isEmpty()) body=null;
        if (request.clientId()==null || body==null && request.attachmentId()==null || body!=null && body.length()>2000)
            throw BizException.badRequest("MESSAGE_CONTENT", "请填写不超过2000字的消息或上传图片");
        String attachment=request.attachmentId()==null?null:request.attachmentId().toString();
        List<Message> prior=db.query("SELECT * FROM direct_messages WHERE sender_id=? AND client_id=?",messageMapper(),actor,request.clientId().toString());
        if (!prior.isEmpty()) {
            Message old=prior.getFirst();
            if (old.conversationId()!=conversation || !Objects.equals(old.body(),body) || !Objects.equals(old.attachmentUrl(),attachmentUrl(attachment)))
                throw BizException.conflict("MESSAGE_RETRY_CONFLICT","重试编号已用于其他消息");
            return old;
        }
        if (count("SELECT COUNT(*) FROM direct_messages WHERE sender_id=? AND created_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE)",actor)>=30)
            throw BizException.tooMany("消息发送过于频繁，请稍后重试");
        if (attachment!=null && (count("SELECT COUNT(*) FROM message_attachments WHERE id=? AND conversation_id=? AND uploaded_by=?",attachment,conversation,actor)!=1
            || count("SELECT COUNT(*) FROM direct_messages WHERE attachment_id=?",attachment)>0))
            throw BizException.badRequest("MESSAGE_ATTACHMENT", "图片不可用于此消息");
        db.update("INSERT INTO direct_messages(conversation_id,sender_id,client_id,body,attachment_id) VALUES(?,?,?,?,?)",conversation,actor,request.clientId().toString(),body,attachment);
        db.update("UPDATE message_conversations SET updated_at=UTC_TIMESTAMP(6) WHERE id=?",conversation);
        Message result=db.queryForObject("SELECT * FROM direct_messages WHERE sender_id=? AND client_id=?",messageMapper(),actor,request.clientId().toString());
        memberIds(conversation).forEach(id -> events.publishEvent(new Changed(id,conversation)));
        return result;
    }

    @Transactional
    public void read(long actor,long conversation,long throughId) {
        requireMember(actor,conversation);
        if (throughId<0 || throughId>0 && count("SELECT COUNT(*) FROM direct_messages WHERE conversation_id=? AND id=?",conversation,throughId)!=1)
            throw BizException.badRequest("MESSAGE_READ_POSITION","已读位置不属于当前会话");
        db.update("""
            INSERT INTO message_read_positions(conversation_id,user_id,last_read_id) VALUES(?,?,?)
            ON DUPLICATE KEY UPDATE last_read_id=GREATEST(last_read_id,VALUES(last_read_id)),updated_at=UTC_TIMESTAMP(6)
            """,conversation,actor,throughId);
        events.publishEvent(new Changed(actor,conversation));
    }

    public void requireMember(long actor,long conversation) {
        if (count("SELECT COUNT(*) FROM message_conversations WHERE id=? AND (user_low=? OR user_high=?)",conversation,actor,actor)!=1)
            throw BizException.notFound("会话不存在或不可访问");
    }
    public void requireCanSend(long actor, long conversation) {
        requireMember(actor, conversation);
        long other = memberIds(conversation).stream().filter(id -> id != actor).findFirst().orElseThrow();
        requireUnblocked(actor, other);
    }

    public BlockState blockState(long actor, long target) {
        if (actor == target || count("SELECT COUNT(*) FROM users WHERE id=?", target) != 1)
            throw BizException.notFound("用户不存在或不可操作");
        boolean mine = count("SELECT COUNT(*) FROM message_user_blocks WHERE blocker_id=? AND blocked_id=?", actor, target) > 0;
        return new BlockState(mine, mine || count("SELECT COUNT(*) FROM message_user_blocks WHERE blocker_id=? AND blocked_id=?", target, actor) > 0);
    }

    @Transactional
    public BlockState setBlock(long actor, long target, boolean blocked) {
        if (actor == target) throw BizException.badRequest("MESSAGE_SELF", "不能屏蔽自己");
        // Sharing the same ordered user locks as conversation creation serializes sends and block changes.
        for (long id : new long[]{Math.min(actor, target), Math.max(actor, target)}) {
            if (db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, id).isEmpty())
                throw BizException.notFound("用户不存在或不可操作");
        }
        if (blocked) db.update("INSERT INTO message_user_blocks(blocker_id,blocked_id) VALUES(?,?) ON DUPLICATE KEY UPDATE blocker_id=blocker_id", actor, target);
        else db.update("DELETE FROM message_user_blocks WHERE blocker_id=? AND blocked_id=?", actor, target);
        for (long conversation : db.queryForList("SELECT id FROM message_conversations WHERE user_low=? AND user_high=?", Long.class, Math.min(actor,target), Math.max(actor,target))) {
            events.publishEvent(new Changed(actor, conversation));
            events.publishEvent(new Changed(target, conversation));
        }
        return blockState(actor, target);
    }

    private void requireUnblocked(long actor, long other) {
        if (count("SELECT COUNT(*) FROM message_user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)", actor,other,other,actor)>0)
            throw BizException.forbidden("当前不能互发私信；历史记录和订单通知仍可查看");
    }
    public List<Long> memberIds(long conversation) {
        return db.queryForObject("SELECT user_low,user_high FROM message_conversations WHERE id=?",
            (rs,n) -> List.of(rs.getLong(1),rs.getLong(2)),conversation);
    }
    private void lockActiveUser(long actor) {
        List<String> states=db.queryForList("SELECT status FROM users WHERE id=? FOR UPDATE",String.class,actor);
        if (states.isEmpty() || !"ACTIVE".equals(states.getFirst())) throw BizException.forbidden("账号当前不可发送消息");
    }
    private long count(String sql,Object...args) { return db.queryForObject(sql,Long.class,args); }
    private static void validatePage(int page,int size) {
        if(page<0 || page>10000 || size<1 || size>50) throw BizException.badRequest("MESSAGE_PAGE","分页参数不正确");
    }
    public static String attachmentUrl(String id) { return id==null?null:"/api/v1/messages/attachments/"+id; }
    private static RowMapper<Message> messageMapper() {
        return (rs,n) -> new Message(rs.getLong("id"),rs.getLong("conversation_id"),rs.getLong("sender_id"),
            UUID.fromString(rs.getString("client_id")),rs.getString("body"),attachmentUrl(rs.getString("attachment_id")),rs.getTimestamp("created_at").toInstant());
    }
}
