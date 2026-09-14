package com.maimai.messaging.socket;

import com.maimai.common.security.AuthenticatedUser;
import com.maimai.config.MaimaiProperties;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

@Configuration
@EnableWebSocket
@EnableScheduling
public class MessageSocketConfig implements WebSocketConfigurer {
    private final MessageSocket socket;
    private final MaimaiProperties properties;
    public MessageSocketConfig(MessageSocket socket,MaimaiProperties properties) {this.socket=socket;this.properties=properties;}
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socket,"/api/v1/messages/socket").setAllowedOrigins(properties.getFrontendOrigin())
            .addInterceptors(new HandshakeInterceptor() {
                @Override
                public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes) {
                    if(!(request instanceof ServletServerHttpRequest servlet)
                        || !(request.getPrincipal() instanceof Authentication authentication)
                        || !authentication.isAuthenticated()
                        || !(authentication.getPrincipal() instanceof AuthenticatedUser actor)) {
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);return false;
                    }
                    HttpSession session=servlet.getServletRequest().getSession(false);
                    if(session==null) {response.setStatusCode(HttpStatus.UNAUTHORIZED);return false;}
                    attributes.put("messageUserId",actor.id());
                    attributes.put("messageHttpSessionId",session.getId());
                    return true;
                }
                @Override
                public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception exception) {}
            });
    }
}
