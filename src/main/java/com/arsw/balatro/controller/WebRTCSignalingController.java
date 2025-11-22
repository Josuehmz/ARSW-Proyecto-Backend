package com.arsw.balatro.controller;

import com.arsw.balatro.model.dto.SignalingMessage;
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

    /**
     * Manejar mensajes de señalización WebRTC (OFFER, ANSWER, ICE_CANDIDATE)
     * El frontend envía a: /app/webrtc/signal
     */
    @MessageMapping("/webrtc/signal")
    public void handleSignaling(@Payload SignalingMessage message, Principal principal) {
        try {
            String sessionId = principal != null ? principal.getName() : null;
            
            log.info("WebRTC Signal received: type={}, gameId={}, from={}, to={}", 
                message.getType(), 
                message.getGameId(), 
                message.getSenderId(), 
                message.getTargetId());

            // Construir el mensaje a enviar
            var signalMessage = new Object() {
                public final String type = "WEBRTC_SIGNAL";
                public final SignalingMessage payload = message;
            };

            // Enviar el mensaje al jugador destinatario en su cola personal
            // El destinatario está escuchando en: /user/queue/webrtc/{gameId}
            messagingTemplate.convertAndSendToUser(
                message.getTargetId(),
                "/queue/webrtc/" + message.getGameId(),
                signalMessage
            );

            log.info("WebRTC Signal forwarded to player: {}", message.getTargetId());

        } catch (Exception e) {
            log.error("Error handling WebRTC signal: {}", e.getMessage(), e);
        }
    }
}

