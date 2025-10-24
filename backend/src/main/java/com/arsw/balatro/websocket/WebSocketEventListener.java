package com.arsw.balatro.websocket;

import com.arsw.balatro.model.dto.GameMessage;
import com.arsw.balatro.model.enums.MessageType;
import com.arsw.balatro.service.GameService;
import com.arsw.balatro.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final MatchmakingService matchmakingService;
    private final GameService gameService;
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
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");
        
        log.info("WebSocket connection closed. Session ID: {}, Player ID: {}", sessionId, playerId);
        
        if (playerId != null) {
            try {
                matchmakingService.removeFromQueue(playerId);
                
                String gameId = gameService.getActiveGameIdForPlayer(playerId);
                if (gameId != null) {
                    handlePlayerDisconnection(gameId, playerId);
                }
                
            } catch (Exception e) {
                log.error("Error handling player disconnection: {}", e.getMessage());
            }
        }
    }

    private void handlePlayerDisconnection(String gameId, String playerId) {
        log.info("Player {} disconnected from game {}", playerId, gameId);
        
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
        
        try {
            gameService.scheduleGameAbandonment(gameId, playerId, 30);
        } catch (Exception e) {
            log.error("Error scheduling game abandonment: {}", e.getMessage());
        }
    }
}
