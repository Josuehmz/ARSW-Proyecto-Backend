package com.arsw.balatro.controller;

import com.arsw.balatro.model.dto.*;
import com.arsw.balatro.model.enums.MessageType;
import com.arsw.balatro.service.GameService;
import com.arsw.balatro.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GameWebSocketController {

    private final MatchmakingService matchmakingService;
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/matchmaking/join")
    public void joinMatchmaking(@Payload GameMessage message, Principal principal) {
        try {
            String playerId = extractPlayerId(message, principal);
            log.info("Player {} joining matchmaking queue", playerId);
            
            QueueStatusDto queueStatus = matchmakingService.addToQueue(playerId);
            
            GameMessage response = GameMessage.create(
                MessageType.JOIN_QUEUE, 
                null, 
                playerId, 
                queueStatus
            );
            
            messagingTemplate.convertAndSendToUser(
                playerId, 
                "/queue/matchmaking", 
                response
            );
            
        } catch (Exception e) {
            log.error("Error joining matchmaking: {}", e.getMessage());
            sendError(message.getPlayerId(), null, "Error al unirse a la cola: " + e.getMessage());
        }
    }

    @MessageMapping("/matchmaking/leave")
    public void leaveMatchmaking(@Payload GameMessage message, Principal principal) {
        try {
            String playerId = extractPlayerId(message, principal);
            log.info("Player {} leaving matchmaking queue", playerId);
            
            matchmakingService.removeFromQueue(playerId);
            
            GameMessage response = GameMessage.create(
                MessageType.LEAVE_QUEUE, 
                null, 
                playerId, 
                null
            );
            
            messagingTemplate.convertAndSendToUser(
                playerId, 
                "/queue/matchmaking", 
                response
            );
            
        } catch (Exception e) {
            log.error("Error leaving matchmaking: {}", e.getMessage());
        }
    }

    @MessageMapping("/game/{gameId}/action")
    public void handleGameAction(
            @DestinationVariable String gameId,
            @Payload PlayerAction action,
            Principal principal
    ) {
        try {
            String playerId = extractPlayerId(action.getPlayerId(), principal);
            action.setPlayerId(playerId);
            action.setGameId(gameId);
            
            log.info("Player {} performing action {} in game {}", 
                playerId, action.getActionType(), gameId);
            
            GameState updatedState = gameService.processAction(gameId, action);
            
            GameMessage stateUpdate = GameMessage.create(
                MessageType.GAME_STATE_UPDATE,
                gameId,
                playerId,
                updatedState
            );
            
            messagingTemplate.convertAndSend(
                "/topic/game/" + gameId,
                stateUpdate
            );
            
        } catch (Exception e) {
            log.error("Error processing game action: {}", e.getMessage());
            sendError(action.getPlayerId(), gameId, "Error al procesar acción: " + e.getMessage());
        }
    }

    @MessageMapping("/game/{gameId}/ready")
    public void markPlayerReady(
            @DestinationVariable String gameId,
            @Payload GameMessage message,
            Principal principal
    ) {
        try {
            String playerId = extractPlayerId(message, principal);
            log.info("Player {} ready in game {}", playerId, gameId);
            
            gameService.markPlayerReady(gameId, playerId);
            
            GameMessage readyMessage = GameMessage.create(
                MessageType.PLAYER_STATE_UPDATE,
                gameId,
                playerId,
                "Player ready"
            );
            
            messagingTemplate.convertAndSend(
                "/topic/game/" + gameId,
                readyMessage
            );
            
        } catch (Exception e) {
            log.error("Error marking player ready: {}", e.getMessage());
            sendError(message.getPlayerId(), gameId, "Error al marcar listo: " + e.getMessage());
        }
    }

    @MessageMapping("/game/{gameId}/chat")
    @SendTo("/topic/game/{gameId}/chat")
    public GameMessage handleChatMessage(
            @DestinationVariable String gameId,
            @Payload GameMessage message,
            Principal principal
    ) {
        String playerId = extractPlayerId(message, principal);
        message.setPlayerId(playerId);
        message.setGameId(gameId);
        message.setType(MessageType.CHAT_MESSAGE);
        
        log.info("Chat message in game {} from player {}: {}", 
            gameId, playerId, message.getMessage());
        
        return message;
    }

    @MessageMapping("/game/{gameId}/emote")
    @SendTo("/topic/game/{gameId}")
    public GameMessage handleEmote(
            @DestinationVariable String gameId,
            @Payload GameMessage message,
            Principal principal
    ) {
        String playerId = extractPlayerId(message, principal);
        message.setPlayerId(playerId);
        message.setGameId(gameId);
        message.setType(MessageType.PLAYER_EMOTE);
        
        return message;
    }

    @MessageMapping("/ping")
    public void handlePing(@Payload GameMessage message, Principal principal) {
        String playerId = extractPlayerId(message, principal);
        
        GameMessage pong = GameMessage.create(
            MessageType.PONG,
            null,
            playerId,
            System.currentTimeMillis()
        );
        
        messagingTemplate.convertAndSendToUser(
            playerId,
            "/queue/ping",
            pong
        );
    }

    private String extractPlayerId(GameMessage message, Principal principal) {
        if (message != null && message.getPlayerId() != null) {
            return message.getPlayerId();
        }
        if (principal != null) {
            return principal.getName();
        }
        throw new IllegalArgumentException("No se pudo determinar el ID del jugador");
    }

    private String extractPlayerId(String playerId, Principal principal) {
        if (playerId != null) {
            return playerId;
        }
        if (principal != null) {
            return principal.getName();
        }
        throw new IllegalArgumentException("No se pudo determinar el ID del jugador");
    }

    private void sendError(String playerId, String gameId, String errorMessage) {
        GameMessage error = GameMessage.error(gameId, playerId, errorMessage);
        messagingTemplate.convertAndSendToUser(
            playerId,
            "/queue/errors",
            error
        );
    }
}
