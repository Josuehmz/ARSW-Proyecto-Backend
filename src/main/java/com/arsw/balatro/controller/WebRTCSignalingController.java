package com.arsw.balatro.controller;

import com.arsw.balatro.model.dto.SignalingMessage;
import com.arsw.balatro.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebRTCSignalingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionService sessionService;

    /**
     * Manejar mensajes de señalización WebRTC (OFFER, ANSWER, ICE_CANDIDATE)
     * El frontend envía a: /app/webrtc/signal
     */
    @MessageMapping("/webrtc/signal")
    public void handleSignaling(@Payload SignalingMessage message, Principal principal) {
        try {
            String sessionId = principal != null ? principal.getName() : null;
            
            // Validaciones básicas
            if (message.getTargetId() == null || message.getTargetId().isEmpty()) {
                log.error("❌ WebRTC Signal: targetId es null o vacío");
                return;
            }
            
            if (message.getGameId() == null || message.getGameId().isEmpty()) {
                log.error("❌ WebRTC Signal: gameId es null o vacío");
                return;
            }
            
            if (message.getType() == null || message.getType().isEmpty()) {
                log.error("❌ WebRTC Signal: type es null o vacío");
                return;
            }
            
            // Asegurar que la sesión del remitente esté actualizada (maneja reconexiones)
            if (sessionId != null && message.getSenderId() != null) {
                sessionService.registerSession(message.getSenderId(), sessionId);
                log.debug("✅ Sender session refreshed: player={}, session={}", message.getSenderId(), sessionId);
            }
            
            log.info("📨 WebRTC Signal received: type={}, gameId={}, from={}, to={}", 
                message.getType(), 
                message.getGameId(), 
                message.getSenderId(), 
                message.getTargetId());
            
            // Log detallado del payload para debugging de audio
            if (message.getPayload() != null) {
                String payloadStr = message.getPayload().toString();
                // Limitar el log para no saturar (SDP puede ser muy largo)
                if (payloadStr.length() > 200) {
                    payloadStr = payloadStr.substring(0, 200) + "... (truncated)";
                }
                log.debug("📦 Payload preview: {}", payloadStr);
                
                // Verificar si es un SDP (offer/answer) o ICE candidate
                if (message.getType().equals("OFFER") || message.getType().equals("ANSWER")) {
                    log.info("🎯 SDP {} recibido, verificando estructura...", message.getType());
                    
                    // Verificar que el payload contenga SDP
                    if (message.getPayload() instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> payloadMap = (java.util.Map<String, Object>) message.getPayload();
                        Object sdpObj = payloadMap.get("sdp");
                        Object typeObj = payloadMap.get("type");
                        
                        if (sdpObj instanceof String) {
                            String sdp = (String) sdpObj;
                            boolean hasAudio = sdp.contains("m=audio");
                            boolean hasOpus = sdp.contains("opus");
                            boolean hasPCMU = sdp.contains("PCMU");
                            boolean hasPCMA = sdp.contains("PCMA");
                            
                            log.info("🎯 Análisis del SDP {}:", message.getType());
                            log.info("   - Tiene audio (m=audio): {}", hasAudio);
                            log.info("   - Tiene codec Opus: {}", hasOpus);
                            log.info("   - Tiene codec PCMU: {}", hasPCMU);
                            log.info("   - Tiene codec PCMA: {}", hasPCMA);
                            log.info("   - Longitud del SDP: {} caracteres", sdp.length());
                            
                            if (!hasAudio) {
                                log.error("❌ PROBLEMA: El SDP no contiene línea de audio (m=audio)!");
                                log.error("❌ Esto significa que el stream local no tiene tracks de audio configurados");
                            } else if (!hasOpus && !hasPCMU && !hasPCMA) {
                                log.warn("⚠️ ADVERTENCIA: El SDP tiene audio pero no tiene codecs comunes (Opus/PCMU/PCMA)");
                            } else {
                                log.info("✅ El SDP parece correcto con audio y codecs");
                            }
                            
                            // Contar líneas de audio en el SDP
                            String[] lines = sdp.split("\r?\n");
                            long audioLines = java.util.Arrays.stream(lines)
                                .filter(line -> 
                                    line.contains("m=audio") || 
                                    line.contains("a=rtpmap") || 
                                    line.contains("a=sendrecv") || 
                                    line.contains("a=sendonly") ||
                                    line.contains("a=recvonly")
                                )
                                .count();
                            log.info("   - Líneas relacionadas con audio: {}", audioLines);
                        } else {
                            log.warn("⚠️ El payload del SDP no tiene formato esperado (sdp no es String)");
                        }
                    } else {
                        log.warn("⚠️ El payload del SDP no es un Map, tipo: {}", 
                            message.getPayload() != null ? message.getPayload().getClass().getName() : "null");
                    }
                } else if (message.getType().equals("ICE_CANDIDATE")) {
                    log.debug("🧊 ICE Candidate recibido");
                }
            } else {
                log.warn("⚠️ Payload es null en mensaje WebRTC de tipo: {}", message.getType());
            }

            // Obtener el sessionId del jugador destinatario con retry mechanism
            String targetSessionId = getTargetSessionIdWithRetry(message.getTargetId(), 3, 200);
            
            if (targetSessionId == null) {
                log.error("❌ No session found for target player: {} after retries. Available sessions: {}", 
                    message.getTargetId(), 
                    sessionService.getActiveSessionCount());
                log.error("💡 Debug info: {}", sessionService.getDebugInfo());
                log.error("💡 Tip: El jugador debe enviar un mensaje a /app/session/register o /app/game/{}/register antes de iniciar WebRTC", 
                    message.getGameId());
                log.warn("⚠️ Señal WebRTC descartada. El receptor ({}) debe registrarse primero.", message.getTargetId());
                return;
            }

            // Construir el mensaje a enviar (formato esperado por el frontend)
            // El frontend espera recibir: { type: "WEBRTC_SIGNAL", payload: SignalingMessage }
            var signalMessage = new Object() {
                public final String type = "WEBRTC_SIGNAL";
                public final SignalingMessage payload = message;
            };

            // Destino: /user/{targetSessionId}/queue/webrtc/{gameId}
            String destination = "/queue/webrtc/" + message.getGameId();
            
            log.info("📤 Reenviando mensaje a usuario: {} (session: {}), destino: /user{}{}", 
                message.getTargetId(), 
                targetSessionId,
                targetSessionId,
                destination);
            
            // Log del payload para debugging
            if (log.isDebugEnabled()) {
                log.debug("📦 Payload del mensaje: type={}, payloadType={}", 
                    message.getType(), 
                    message.getPayload() != null ? message.getPayload().getClass().getSimpleName() : "null");
            }

            // Enviar el mensaje al jugador destinatario en su cola personal
            // El destinatario está escuchando en: /user/queue/webrtc/{gameId}
            messagingTemplate.convertAndSendToUser(
                targetSessionId,  // Spring busca la sesión por este ID
                destination,
                signalMessage  // Enviar el mensaje envuelto como espera el frontend
            );

            log.info("✅ WebRTC Signal forwarded successfully: {} → {} (type: {})", 
                message.getSenderId(), 
                message.getTargetId(), 
                message.getType());

        } catch (Exception e) {
            log.error("❌ Error handling WebRTC signal: {}", e.getMessage(), e);
        }
    }

    /**
     * Intenta obtener el sessionId del jugador destinatario con retry mechanism
     * para manejar race conditions cuando el receptor aún no se ha registrado
     */
    private String getTargetSessionIdWithRetry(String targetPlayerId, int maxRetries, long delayMs) {
        String targetSessionId = sessionService.getSessionId(targetPlayerId);
        
        if (targetSessionId != null) {
            return targetSessionId;
        }
        
        // Si no se encuentra, intentar con retry
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.debug("🔄 Retry {}: Esperando sesión del jugador {}...", attempt, targetPlayerId);
            
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⚠️ Retry interrumpido mientras esperaba sesión del jugador {}", targetPlayerId);
                break;
            }
            
            targetSessionId = sessionService.getSessionId(targetPlayerId);
            if (targetSessionId != null) {
                log.info("✅ Sesión encontrada en retry {} para jugador {}", attempt, targetPlayerId);
                return targetSessionId;
            }
        }
        
        return null;
    }
}

