package com.arsw.balatro.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${spring.websocket.servlet.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Crear el handshake handler inline
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(org.springframework.http.server.ServerHttpRequest request,
                                            org.springframework.web.socket.WebSocketHandler wsHandler,
                                            java.util.Map<String, Object> attributes) {
                String sessionId = java.util.UUID.randomUUID().toString();
                System.out.println("=== HANDSHAKE: Creating session " + sessionId + " ===");
                return () -> sessionId;
            }
        };
        
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .setHandshakeHandler(handshakeHandler)
                .withSockJS();
        
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .setHandshakeHandler(handshakeHandler);
    }
}
