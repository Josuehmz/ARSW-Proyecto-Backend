package com.arsw.balatro.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${spring.websocket.servlet.allowed-origins}")
    private String[] allowedOrigins;
    
    private final CognitoWebSocketHandshakeInterceptor handshakeInterceptor;
    private final CognitoChannelInterceptor channelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Agregar interceptor para validar tokens en mensajes CONNECT
        registration.interceptors(channelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Crear el handshake handler que usará el Principal del interceptor
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(org.springframework.http.server.ServerHttpRequest request,
                                            org.springframework.web.socket.WebSocketHandler wsHandler,
                                            java.util.Map<String, Object> attributes) {
                // El Principal se establecerá en el ChannelInterceptor cuando llegue el mensaje CONNECT
                // Por ahora, generamos un ID temporal que se reemplazará después de la validación
                String sessionId = java.util.UUID.randomUUID().toString();
                System.out.println("=== HANDSHAKE: Creating session " + sessionId + " ===");
                return () -> sessionId;
            }
        };
        
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(handshakeInterceptor)
                .withSockJS();
        
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(handshakeInterceptor);
    }
}
