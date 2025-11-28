package com.arsw.balatro.controller;

import com.arsw.balatro.model.dto.SignalingMessage;
import com.arsw.balatro.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controlador para manejar la señalización WebRTC del chat de voz.
 * Usa Cognito como identificador principal (username de Cognito = playerId).
 * 
 * Endpoint: /app/webrtc/signal
 * Suscripción: /user/queue/webrtc/{gameId}
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebRTCSignalingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionService sessionService;

    /**
     * Maneja mensajes de señalización WebRTC (OFFER, ANSWER, ICE_CANDIDATE).
     * 
     * El senderId se obtiene del Principal (username de Cognito).
     * El targetId debe venir en el mensaje (username de Cognito del destinatario).
     * 
     * @param message Mensaje de señalización WebRTC
     * @param principal Principal autenticado (contiene el username de Cognito)
     * @param headerAccessor Acceso a los headers del mensaje WebSocket
     */
    @MessageMapping("/webrtc/signal")
    public void handleSignaling(
            @Payload SignalingMessage message, 
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {
        
        log.info("🔔 ========== WEBRTC SIGNAL RECIBIDO ==========");
        log.info("🔔 Principal: {} (null? {})", principal != null ? principal.getName() : "null", principal == null);
        log.info("🔔 Message: type={}, gameId={}, targetId={}, senderId={}", 
            message != null ? message.getType() : "null",
            message != null ? message.getGameId() : "null",
            message != null ? message.getTargetId() : "null",
            message != null ? message.getSenderId() : "null");
        log.info("🔔 HeaderAccessor: {}", headerAccessor != null ? "not null" : "null");
        
        try {
            // Validar que el Principal esté disponible
            if (principal == null) {
                log.error("❌ WebRTC Signal: Principal es null. El mensaje requiere autenticación con Cognito.");
                return;
            }

            // Obtener el username de Cognito como senderId (identificador principal)
            String cognitoUsername = principal.getName();
            if (cognitoUsername == null || cognitoUsername.trim().isEmpty()) {
                log.error("❌ WebRTC Signal: No se pudo obtener el username de Cognito del Principal.");
                return;
            }

            // Normalizar el senderId (username de Cognito)
            String normalizedSenderId = cognitoUsername.trim().toLowerCase();
            
            // Normalizar el targetId (debe ser el username de Cognito del destinatario)
            String normalizedTargetId = message.getTargetId() != null 
                ? message.getTargetId().trim().toLowerCase() 
                : null;

            // Validaciones básicas
            if (normalizedTargetId == null || normalizedTargetId.isEmpty()) {
                log.error("❌ WebRTC Signal: targetId es null o vacío. Se requiere el username de Cognito del destinatario.");
                return;
            }

            if (message.getGameId() == null || message.getGameId().isEmpty()) {
                log.error("❌ WebRTC Signal: gameId es null o vacío.");
                return;
            }

            if (message.getType() == null || message.getType().isEmpty()) {
                log.error("❌ WebRTC Signal: type es null o vacío. Tipos válidos: OFFER, ANSWER, ICE_CANDIDATE.");
                return;
            }

            // Validar que no se envíe a uno mismo
            if (normalizedSenderId.equals(normalizedTargetId)) {
                log.error("❌ WebRTC Signal: Intento de enviar mensaje a uno mismo. senderId: {}, targetId: {}", 
                    cognitoUsername, message.getTargetId());
                return;
            }

            // Advertencia si el mensaje trae un senderId diferente al de Cognito
            if (message.getSenderId() != null && !message.getSenderId().equals(cognitoUsername)) {
                log.warn("⚠️ WebRTC Signal: El mensaje contiene senderId diferente ({}), pero se usará el username de Cognito ({})", 
                    message.getSenderId(), cognitoUsername);
            }

            // Actualizar el senderId en el mensaje con el username de Cognito
            message.setSenderId(cognitoUsername);

            // Registrar/actualizar la sesión del remitente
            String springSessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
            if (springSessionId != null && normalizedSenderId != null) {
                sessionService.registerSession(normalizedSenderId, springSessionId);
                log.debug("✅ Sesión del remitente actualizada: playerId={} (normalized: {}), sessionId={}", 
                    cognitoUsername, normalizedSenderId, springSessionId);
            } else {
                log.warn("⚠️ No se pudo registrar la sesión del remitente: senderId={}, sessionId={}", 
                    cognitoUsername, springSessionId);
            }

            log.info("📨 WebRTC Signal recibido: type={}, gameId={}, from={} (normalized: {}), to={} (normalized: {})", 
                message.getType(), 
                message.getGameId(), 
                cognitoUsername, normalizedSenderId,
                message.getTargetId(), normalizedTargetId);

            // Verificar que la sesión del destinatario esté registrada (usar ID normalizado para buscar)
            String targetSessionId = sessionService.getSessionId(normalizedTargetId);
            if (targetSessionId == null) {
                log.error("❌ WebRTC Signal: No se encontró sesión para el destinatario: {} (normalized: {})", 
                    message.getTargetId(), normalizedTargetId);
                log.error("💡 El destinatario debe estar conectado y haber registrado su sesión.");
                log.error("💡 Debug info: {}", sessionService.getDebugInfo());
                log.error("💡 Sender session: {}", sessionService.getSessionId(normalizedSenderId));
                log.error("💡 TargetId original del mensaje: {}", message.getTargetId());
                log.error("💡 TargetId normalizado: {}", normalizedTargetId);
                log.error("💡 SenderId (Principal): {} (normalized: {})", cognitoUsername, normalizedSenderId);
                
                // Intentar buscar con el targetId original también (por si acaso)
                if (message.getTargetId() != null && !message.getTargetId().equals(normalizedTargetId)) {
                    String altSessionId = sessionService.getSessionId(message.getTargetId());
                    if (altSessionId != null) {
                        log.warn("⚠️ Sesión encontrada con targetId original (sin normalizar): {}", altSessionId);
                        log.warn("⚠️ Esto sugiere un problema de normalización. Usando sesión encontrada.");
                        targetSessionId = altSessionId;
                    }
                }
                
                if (targetSessionId == null) {
                    return;
                }
            }

            // Construir el mensaje de respuesta (formato esperado por el frontend)
            var signalResponse = new Object() {
                public final String type = "WEBRTC_SIGNAL";
                public final SignalingMessage payload = message;
            };

            // Destino: /user/{cognitoUsername}/queue/webrtc/{gameId}
            // IMPORTANTE: convertAndSendToUser espera el username del Principal.getName()
            // Ahora el Principal.getName() devuelve el username normalizado (trim + lowercase)
            // porque lo normalizamos en CognitoWebSocketHandshakeInterceptor
            // Esto coincide con cómo SessionService almacena los playerIds (normalizados)
            String destination = "/queue/webrtc/" + message.getGameId();
            
            log.info("📤 Reenviando WebRTC Signal a: {} (normalized: {}, sessionId: {}), destino: /user{}{}", 
                message.getTargetId(), 
                normalizedTargetId,
                targetSessionId,
                normalizedTargetId,
                destination);

            // IMPORTANTE: convertAndSendToUser usa el username del Principal
            // El Principal.getName() ahora devuelve el username normalizado
            // que coincide con cómo SessionService almacena los playerIds
            // Por lo tanto, usamos normalizedTargetId que coincide con Principal.getName() del destinatario
            messagingTemplate.convertAndSendToUser(
                normalizedTargetId,  // Usar el username de Cognito normalizado (coincide con Principal.getName())
                destination,
                signalResponse
            );

            log.info("✅ WebRTC Signal reenviado exitosamente: {} → {} (type: {})", 
                cognitoUsername, 
                message.getTargetId(),
                message.getType());

        } catch (Exception e) {
            log.error("❌ Error al procesar WebRTC Signal: {}", e.getMessage(), e);
        }
    }
}

