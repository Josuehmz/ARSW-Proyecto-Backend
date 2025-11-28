package com.arsw.balatro.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;

@RequiredArgsConstructor
@Slf4j
public class CognitoChannelInterceptor implements ChannelInterceptor {
    
    private final CognitoWebSocketHandshakeInterceptor handshakeInterceptor;
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && accessor.getCommand() == StompCommand.CONNECT) {
            log.info("🔐 Interceptando mensaje CONNECT para validar token...");
            
            // Log todos los headers para debugging (siempre, no solo en debug)
            log.info("📋 Headers del mensaje CONNECT:");
            accessor.toNativeHeaderMap().forEach((key, value) -> {
                if (key.equalsIgnoreCase("Authorization")) {
                    log.info("   {}: {}", key, value != null && value.toString().length() > 20 
                        ? "Bearer " + value.toString().substring(0, 20) + "..." 
                        : value);
                } else {
                    log.info("   {}: {}", key, value);
                }
            });
            
            try {
                // Validar token y establecer Principal
                var principal = handshakeInterceptor.validateAndExtractPrincipal(accessor);
                
                if (principal != null) {
                    accessor.setUser(principal);
                    log.info("✅ Usuario autenticado exitosamente: {}", principal.getName());
                    log.info("✅ Principal establecido en el accessor. SessionId: {}", accessor.getSessionId());
                } else {
                    log.error("❌ No se pudo extraer Principal del token");
                    throw new MessageDeliveryException(message, "Autenticación fallida: No se pudo extraer Principal");
                }
                
            } catch (SecurityException e) {
                log.error("❌ Error de autenticación: {}", e.getMessage());
                log.error("💡 Asegúrate de que el frontend envíe el header 'Authorization: Bearer <token>' en el mensaje STOMP CONNECT");
                // Usar MessageDeliveryException para un rechazo más limpio
                throw new MessageDeliveryException(message, "Autenticación fallida: " + e.getMessage(), e);
            } catch (Exception e) {
                log.error("❌ Error inesperado durante autenticación: {}", e.getMessage(), e);
                throw new MessageDeliveryException(message, "Error inesperado durante autenticación: " + e.getMessage(), e);
            }
        } else if (accessor != null) {
            // Log para otros mensajes para verificar si el Principal está disponible
            Principal principal = accessor.getUser();
            if (principal != null) {
                log.debug("📨 Mensaje {} - Principal disponible: {}", accessor.getCommand(), principal.getName());
            } else {
                log.warn("⚠️ Mensaje {} - Principal es null! SessionId: {}", accessor.getCommand(), accessor.getSessionId());
            }
        }
        
        return message;
    }
}

