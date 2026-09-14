package com.maimai.messaging.socket;

import com.maimai.common.security.AuthenticatedUser;
import com.maimai.messaging.dto.MessageDtos.Changed;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Socket only sends invalidation events. Mutations and history use authenticated, CSRF-protected HTTP. */
@Component
public class MessageSocket extends TextWebSocketHandler {
    private final ConcurrentHashMap<String,Connection> connections=new ConcurrentHashMap<>();
    private final SessionRepository<? extends Session> sessions;
    private final JdbcTemplate db;
    public MessageSocket(SessionRepository<? extends Session> sessions,JdbcTemplate db) {this.sessions=sessions;this.db=db;}

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws IOException {
        Long user=(Long)raw.getAttributes().get("messageUserId");
        String sessionId=(String)raw.getAttributes().get("messageHttpSessionId");
        Connection connection=new Connection(user==null?0:user,sessionId,new ConcurrentWebSocketSessionDecorator(raw,5000,32*1024));
        synchronized(connections) {
            if(!valid(connection) || connections.values().stream().filter(c->c.user()==connection.user()).count()>=5) {
                raw.close(CloseStatus.POLICY_VIOLATION);return;
            }
            connections.put(raw.getId(),connection);
        }
        raw.setTextMessageSizeLimit(128);
        connection.socket().sendMessage(new TextMessage("{\"type\":\"ready\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw,TextMessage message) throws IOException {
        Connection connection=connections.get(raw.getId());
        if(connection==null || !valid(connection)) {raw.close(CloseStatus.POLICY_VIOLATION);return;}
        if("ping".equals(message.getPayload())) connection.socket().sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        else raw.close(CloseStatus.NOT_ACCEPTABLE);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,CloseStatus status) {connections.remove(session.getId());}

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void changed(Changed event) {
        for(Connection connection:connections.values()) {
            if(connection.user()!=event.userId()) continue;
            if(!valid(connection)) {close(connection);continue;}
            try {
                connection.socket().sendMessage(new TextMessage("{\"type\":\"messages.changed\",\"conversationId\":"+event.conversationId()+"}"));
            } catch(Exception ex) {close(connection);}
        }
    }

    @Scheduled(fixedDelay=30000)
    public void expireSessions() {
        for(Connection connection:connections.values()) if(!valid(connection)) close(connection);
    }

    private boolean valid(Connection connection) {
        if(connection.sessionId()==null || !connection.socket().isOpen()) return false;
        try {
            Session session=sessions.findById(connection.sessionId());
            if(session==null || session.isExpired()) return false;
            SecurityContext context=session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if(context==null || context.getAuthentication()==null || !context.getAuthentication().isAuthenticated()
                || !(context.getAuthentication().getPrincipal() instanceof AuthenticatedUser actor) || actor.id()!=connection.user()) return false;
            return db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND status='ACTIVE'",Long.class,connection.user())==1;
        } catch(RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(MessageSocket.class).warn("Message session validation unavailable: {}",ex.getClass().getSimpleName());
            return false;
        }
    }

    private void close(Connection connection) {
        connections.remove(connection.socket().getId());
        try {connection.socket().close(CloseStatus.POLICY_VIOLATION);} catch(IOException ignored) { /* Transport is already unavailable. */ }
    }
    private record Connection(long user,String sessionId,WebSocketSession socket) {}
}
