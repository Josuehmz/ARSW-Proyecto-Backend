package com.arsw.balatro.websocket;

import com.arsw.balatro.model.dto.GameMessage;
import com.arsw.balatro.model.enums.MessageType;
import com.arsw.balatro.service.GameService;
import com.arsw.balatro.service.MatchmakingService;
import com.arsw.balatro.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Listener simplificado para eventos de conexión y desconexión WebSocket.
 * Maneja limpieza básica de recursos sin lógica de juego compleja.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final MatchmakingService matchmakingService;
    private final GameService gameService;
    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        log.info("New WebSocket connection established. Session ID: {}", sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        // Obtener el playerId desde el registro de sesiones usando el sessionId
        // SessionService.getPlayerId() devuelve el playerId normalizado almacenado
        String playerId = sessionId != null ? sessionService.getPlayerId(sessionId) : null;
        
        // También obtener el principalName para logging (username de Cognito)
        String principalName = headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : null;
        
        log.info("WebSocket connection closed. Session ID: {}, Principal: {}, Player ID: {}", 
            sessionId, principalName, playerId);
        
        // Limpiar la sesión del registro (esto elimina ambas direcciones del mapa)
        if (sessionId != null) {
            sessionService.removeBySessionId(sessionId);
        }
        
        // Si tenemos el playerId (normalizado), hacer limpieza adicional
        if (playerId != null) {
            try {
                // Remover de la cola de matchmaking si estaba esperando
                matchmakingService.removeFromQueue(playerId);
                
                // Notificar desconexión en partida activa
                String gameId = gameService.getActiveGameIdForPlayer(playerId);
                if (gameId != null) {
                    handlePlayerDisconnection(gameId, playerId);
                }
                
            } catch (Exception e) {
                log.error("Error handling player disconnection: {}", e.getMessage(), e);
            }
        } else if (principalName != null) {
            // Si no encontramos playerId por sessionId, intentar limpiar usando el principalName
            // (esto puede pasar si la sesión no estaba registrada correctamente)
            log.warn("No playerId found for sessionId {}, attempting cleanup with principalName: {}", 
                sessionId, principalName);
            // SessionService.removeByPlayerId() normaliza automáticamente
            sessionService.removeByPlayerId(principalName);
            matchmakingService.removeFromQueue(principalName);
        }
    }

    /**
     * Maneja la desconexión de un jugador de una partida activa.
     * Notifica al oponente y programa la limpieza del juego.
     */
    private void handlePlayerDisconnection(String gameId, String playerId) {
        log.info("Player {} disconnected from game {}", playerId, gameId);
        
        // Notificar al oponente sobre la desconexión
        GameMessage disconnectMessage = GameMessage.create(
            MessageType.PLAYER_DISCONNECTED,
            gameId,
            playerId,
            "Opponent disconnected"
        );
        
        messagingTemplate.convertAndSend(
            "/topic/game/" + gameId,
            disconnectMessage
        );
        
        // Programar limpieza del juego después de 60 segundos
        // Esto da tiempo para reconexión si es temporal
        try {
            gameService.scheduleGameCleanup(gameId, 60);
        } catch (Exception e) {
            log.error("Error scheduling game cleanup: {}", e.getMessage());
        }
    }
}
