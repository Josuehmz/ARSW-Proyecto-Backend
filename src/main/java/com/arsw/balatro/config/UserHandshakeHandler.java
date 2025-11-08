package com.arsw.balatro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

public class UserHandshakeHandler extends DefaultHandshakeHandler {

    private static final Logger log = LoggerFactory.getLogger(UserHandshakeHandler.class);

    @Override
    protected Principal determineUser(ServerHttpRequest request, 
                                     WebSocketHandler wsHandler, 
                                     Map<String, Object> attributes) {
        
        String userId = UUID.randomUUID().toString();
        
        log.info("=== CREATING WEBSOCKET SESSION ===");
        log.info("Generated user ID: {}", userId);
        log.info("Request URI: {}", request.getURI());
        
        return new StompPrincipal(userId);
    }
    
    private static class StompPrincipal implements Principal {
        private final String name;
        
        public StompPrincipal(String name) {
            this.name = name;
        }
        
        @Override
        public String getName() {
            return name;
        }
    }
}
