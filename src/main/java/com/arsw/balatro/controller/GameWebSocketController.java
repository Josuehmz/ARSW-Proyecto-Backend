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

/**
 * Controlador WebSocket simplificado que actúa como intermediario de mensajes.
 * No procesa lógica de juego, solo reenvía mensajes entre jugadores.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class GameWebSocketController {

    private final MatchmakingService matchmakingService;
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Unirse a la cola de matchmaking
     */
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

    /**
     * Salir de la cola de matchmaking
     */
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

    /**
     * Reenvía mensajes de juego entre jugadores sin procesarlos.
     * El backend solo actúa como intermediario.
     */
    @MessageMapping("/game/{gameId}/message")
    public void relayGameMessage(
            @DestinationVariable String gameId,
            @Payload GameMessage message,
            Principal principal
    ) {
        try {
            String playerId = extractPlayerId(message, principal);
            message.setPlayerId(playerId);
            message.setGameId(gameId);
            
            // Verificar que el jugador pertenece al juego
            if (!gameService.isPlayerInGame(gameId, playerId)) {
                log.warn("Player {} attempted to send message to game {} but is not a participant", 
                    playerId, gameId);
                sendError(playerId, gameId, "No perteneces a esta partida");
                return;
            }
            
            log.debug("Relaying message from player {} in game {}: type={}", 
                playerId, gameId, message.getType());
            
            // Actualizar timestamp de actividad
            gameService.updateGameActivity(gameId);
            
            // Reenviar el mensaje a ambos jugadores en el topic del juego
            messagingTemplate.convertAndSend(
                "/topic/game/" + gameId,
                message
            );
            
        } catch (Exception e) {
            log.error("Error relaying game message: {}", e.getMessage());
            sendError(message.getPlayerId(), gameId, "Error al enviar mensaje: " + e.getMessage());
        }
    }

    /**
     * Manejo de mensajes de chat
     */
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

    /**
     * Manejo de emotes
     */
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

    /**
     * Ping para keep-alive
     */
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

    private void sendError(String playerId, String gameId, String errorMessage) {
        GameMessage error = GameMessage.error(gameId, playerId, errorMessage);
        messagingTemplate.convertAndSendToUser(
            playerId,
            "/queue/errors",
            error
        );
    }
}
