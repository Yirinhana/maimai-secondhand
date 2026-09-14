package com.maimai.support.chat;

import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.support.ai.SupportAiGateway.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class TinaChatStore {
    public record Turn(long id, String requestId, String question, String answer, String status, String errorCode, Instant createdAt) { }
    public record Attempt(Turn turn, String attemptId, boolean invoke) { }
    private static final RowMapper<Turn> ROW=(r,n)->new Turn(r.getLong("id"),r.getString("request_id"),r.getString("question"),
            r.getString("answer"),r.getString("status"),r.getString("error_code"),r.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;
    public TinaChatStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional
    public List<Turn> history() {
        long owner=lockOwner();
        expire(owner);
        var rows=jdbc.query("SELECT * FROM support_ai_turns WHERE owner_id=? ORDER BY id DESC LIMIT 20",ROW,owner);
        Collections.reverse(rows);
        return rows;
    }

    @Transactional
    public Attempt begin(String requestId,String question) {
        long owner=lockOwner();
        if(requestId==null||!requestId.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")
                ||question==null||question.isBlank()||question.strip().length()>1000||question.indexOf('\0')>=0)
            throw BizException.badRequest("AI_CHAT_INVALID","问题不能为空，最多1000字");
        question=question.strip();
        expire(owner);
        var old=jdbc.query("SELECT * FROM support_ai_turns WHERE owner_id=? AND request_id=?",ROW,owner,requestId);
        if(!old.isEmpty()) {
            var turn=old.getFirst();
            if(!turn.question().equals(question)) throw BizException.conflict("AI_REQUEST_REUSED","请为新的问题创建新的请求");
            if(!"FAILED".equals(turn.status())) return new Attempt(turn,"",false);
        }
        if(jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_turns WHERE owner_id=? AND status='PENDING'",Long.class,owner)>0)
            throw BizException.conflict("AI_CHAT_BUSY","上一条问题还在回复，请稍候");
        String attempt=UUID.randomUUID().toString();
        if(old.isEmpty()) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM support_ai_turns WHERE owner_id=?",Long.class,owner)>=200)
                throw BizException.conflict("AI_HISTORY_FULL","对话已达到200条，请先清空历史或联系人工");
            jdbc.update("INSERT INTO support_ai_turns(owner_id,request_id,attempt_id,question,status) VALUES(?,?,?,?,'PENDING')",owner,requestId,attempt,question);
        } else {
            jdbc.update("UPDATE support_ai_turns SET status='PENDING',error_code=NULL,attempt_id=?,updated_at=NOW(6) WHERE owner_id=? AND request_id=?",attempt,owner,requestId);
        }
        return new Attempt(jdbc.queryForObject("SELECT * FROM support_ai_turns WHERE owner_id=? AND request_id=?",ROW,owner,requestId),attempt,true);
    }

    @Transactional(readOnly=true)
    public List<ChatMessage> context(long turnId) {
        long owner=SecurityUtils.currentUserId();
        var current=owned(turnId,owner);
        var rows=jdbc.query("SELECT * FROM support_ai_turns WHERE owner_id=? AND id<? AND status='COMPLETE' ORDER BY id DESC LIMIT 6",ROW,owner,turnId);
        Collections.reverse(rows);
        var result=new ArrayList<ChatMessage>();
        for(var row:rows) {
            result.add(new ChatMessage("user",row.question()));
            result.add(new ChatMessage("assistant",row.answer()));
        }
        result.add(new ChatMessage("user",current.question()));
        return result;
    }

    @Transactional
    public Turn finish(Attempt attempt,String answer,String errorCode) {
        long owner=lockOwner();
        if(answer!=null&&(answer.isBlank()||answer.length()>1800)) throw BizException.badRequest("AI_RESPONSE_INVALID","回复内容无效");
        if(jdbc.update("UPDATE support_ai_turns SET answer=?,status=?,error_code=?,updated_at=NOW(6) WHERE id=? AND owner_id=? AND status='PENDING' AND attempt_id=?",
                answer,answer==null?"FAILED":"COMPLETE",errorCode,attempt.turn().id(),owner,attempt.attemptId())!=1)
            throw BizException.conflict("AI_CHAT_CHANGED","对话已发生变化，请刷新查看");
        return owned(attempt.turn().id(),owner);
    }

    @Transactional
    public void clear() {
        long owner=lockOwner();
        jdbc.update("DELETE FROM support_ai_turns WHERE owner_id=?",owner);
    }
    private Turn owned(long id,long owner) {
        var rows=jdbc.query("SELECT * FROM support_ai_turns WHERE id=? AND owner_id=?",ROW,id,owner);
        if(rows.isEmpty()) throw BizException.notFound("对话不存在");
        return rows.getFirst();
    }
    private long lockOwner() {
        long owner=SecurityUtils.currentUserId();
        if(jdbc.queryForList("SELECT id FROM users WHERE id=? AND status='ACTIVE' FOR UPDATE",Long.class,owner).isEmpty())
            throw BizException.unauthorized("请重新登录");
        return owner;
    }
    private void expire(long owner) {
        jdbc.update("UPDATE support_ai_turns SET status='FAILED',error_code='AI_TIMEOUT',updated_at=NOW(6) WHERE owner_id=? AND status='PENDING' AND updated_at<DATE_SUB(NOW(6),INTERVAL 2 MINUTE)",owner);
    }
}
