package com.arsw.balatro.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuración de WebSocket para comunicación en tiempo real
 * Utiliza STOMP (Simple Text Oriented Messaging Protocol) sobre WebSocket
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${spring.websocket.servlet.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Configura el broker de mensajes
     * - /topic: para mensajes broadcast (ej: actualizaciones de partida)
     * - /queue: para mensajes punto a punto (ej: notificaciones personales)
     * - /app: prefijo para mensajes destinados a @MessageMapping en controladores
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Registra endpoints STOMP
     * - /ws: endpoint principal para conexión WebSocket
     * - Soporta SockJS como fallback para navegadores sin soporte WebSocket nativo
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
        
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins);
    }
}

