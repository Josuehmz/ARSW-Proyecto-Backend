package com.arsw.balatro.controller;

import com.arsw.balatro.model.dto.*;
import com.arsw.balatro.model.enums.MessageType;
import com.arsw.balatro.service.GameService;
import com.arsw.balatro.service.MatchmakingService;
import com.arsw.balatro.service.RoomService;
import com.arsw.balatro.service.SessionService;
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
    private final RoomService roomService;
    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Unirse a la cola de matchmaking
     */
    @MessageMapping("/matchmaking/join")
    public void joinMatchmaking(@Payload GameMessage message, Principal principal) {
        try {
            String playerId = extractPlayerId(message, principal);
            String sessionId = principal != null ? principal.getName() : null;
            
            log.info("=== MATCHMAKING JOIN REQUEST ===");
            log.info("Player ID: {}", playerId);
            log.info("Session ID: {}", sessionId);
            
            // Registrar la sesión
            if (sessionId != null) {
                sessionService.registerSession(playerId, sessionId);
                log.info("Session registered for player {}", playerId);
            } else {
                log.error("No session ID available for player {}", playerId);
                sendError(playerId, null, "Error de sesión");
                return;
            }
            
            // Agregar a la cola (esto disparará tryMatchmaking automáticamente)
            QueueStatusDto queueStatus = matchmakingService.addToQueue(playerId);
            
            log.info("Player {} added to queue. Status: {}", playerId, queueStatus);
            
            // Enviar confirmación inmediata
            GameMessage response = GameMessage.create(
                MessageType.JOIN_QUEUE, 
                null, 
                playerId, 
                queueStatus
            );
            
            messagingTemplate.convertAndSendToUser(
                sessionId, 
                "/queue/matchmaking", 
                response
            );
            
            log.info("Join confirmation sent to session {}", sessionId);
            
        } catch (Exception e) {
            log.error("Error joining matchmaking: {}", e.getMessage(), e);
            String sessionId = principal != null ? principal.getName() : null;
            if (sessionId != null) {
                GameMessage errorMsg = GameMessage.error(null, message.getPlayerId(), "Error al unirse a la cola: " + e.getMessage());
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", errorMsg);
            }
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
     * Crear una sala privada
     */
    @MessageMapping("/room/create")
    public void createRoom(@Payload GameMessage message, Principal principal) {
        try {
            String playerId = extractPlayerId(message, principal);
            String sessionId = principal != null ? principal.getName() : null;
            
            log.info("=== CREATE ROOM REQUEST ===");
            log.info("Player ID: {}", playerId);
            log.info("Session ID: {}", sessionId);
            log.info("Message payload: {}", message.getPayload());
            
            // Validar sesión
            if (sessionId == null) {
                log.error("No session ID available for player {}", playerId);
                GameMessage errorMsg = GameMessage.error(null, playerId, "Error de sesión");
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
                return;
            }
            
            // Registrar la sesión si no existe
            if (!sessionService.hasSession(playerId)) {
                sessionService.registerSession(playerId, sessionId);
                log.info("Session registered for player {}", playerId);
            }
            
            // Extraer datos del payload
            String playerName = "Player " + playerId.substring(0, Math.min(8, playerId.length()));
            String roomCode = null;
            
            if (message.getPayload() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> payloadMap = (java.util.Map<String, Object>) message.getPayload();
                    if (payloadMap.containsKey("playerName")) {
                        playerName = payloadMap.get("playerName").toString();
                    }
                    if (payloadMap.containsKey("roomCode")) {
                        roomCode = payloadMap.get("roomCode").toString();
                    }
                    log.info("Parsed - playerName: {}, roomCode: {}", playerName, roomCode);
                } catch (Exception e) {
                    log.error("Could not parse payload", e);
                }
            }
            
            if (roomCode == null || roomCode.trim().isEmpty()) {
                log.error("Room code is missing or empty");
                GameMessage errorMsg = GameMessage.error(null, playerId, "El código de sala es requerido");
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", errorMsg);
                return;
            }
            
            CreateRoomDto createDto = CreateRoomDto.builder()
                .playerId(playerId)
                .playerName(playerName)
                .roomCode(roomCode)
                .isPrivate(true)
                .build();
            
            log.info("Creating room with DTO: {}", createDto);
            RoomInfoDto roomInfo = roomService.createRoom(createDto);
            log.info("Room created successfully: {}", roomInfo);
            
            // Responder con CREATE_ROOM (lo que espera el frontend)
            GameMessage response = GameMessage.create(
                MessageType.CREATE_ROOM,
                null,  // gameId es null porque aún no hay partida
                playerId,
                roomInfo
            );
            
            messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/room",
                response
            );
            
            log.info("CREATE_ROOM response sent to session {} for room {}", sessionId, roomInfo.getRoomCode());
            
        } catch (IllegalStateException e) {
            log.error("Room creation failed: {}", e.getMessage());
            String sessionId = principal != null ? principal.getName() : null;
            if (sessionId != null) {
                GameMessage errorMsg = GameMessage.error(null, message.getPlayerId(), e.getMessage());
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", errorMsg);
            }
        } catch (Exception e) {
            log.error("Error creating room: {}", e.getMessage(), e);
            String sessionId = principal != null ? principal.getName() : null;
            if (sessionId != null) {
                GameMessage errorMsg = GameMessage.error(null, message.getPlayerId(), "Error al crear sala: " + e.getMessage());
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", errorMsg);
            }
        }
    }

    /**
     * Unirse a una sala privada con código
     */
    @MessageMapping("/room/join")
    public void joinRoom(@Payload GameMessage message, Principal principal) {
        try {
            String playerId = extractPlayerId(message, principal);
            String sessionId = principal != null ? principal.getName() : null;
            
            log.info("=== JOIN ROOM REQUEST ===");
            log.info("Player ID: {}", playerId);
            log.info("Session ID: {}", sessionId);
            log.info("Message payload: {}", message.getPayload());
            
            // Validar sesión
            if (sessionId == null) {
                log.error("No session ID available for player {}", playerId);
                GameMessage errorMsg = GameMessage.error(null, playerId, "Error de sesión");
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
                return;
            }
            
            // Registrar la sesión si no existe
            if (!sessionService.hasSession(playerId)) {
                sessionService.registerSession(playerId, sessionId);
                log.info("Session registered for player {}", playerId);
            }
            
            // Extraer roomCode del payload
            String roomCode = null;
            String playerName = "Player " + playerId.substring(0, Math.min(8, playerId.length()));
            
            if (message.getPayload() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> payloadMap = (java.util.Map<String, Object>) message.getPayload();
                    if (payloadMap.containsKey("roomCode")) {
                        roomCode = payloadMap.get("roomCode").toString();
                    }
                    if (payloadMap.containsKey("playerName")) {
                        playerName = payloadMap.get("playerName").toString();
                    }
                    log.info("Parsed - playerName: {}, roomCode: {}", playerName, roomCode);
                } catch (Exception e) {
                    log.error("Error parsing payload", e);
                }
            }
            
            if (roomCode == null || roomCode.trim().isEmpty()) {
                log.error("Room code is missing or empty");
                GameMessage errorMsg = GameMessage.error(null, playerId, "Debes proporcionar un código de sala");
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", errorMsg);
                return;
            }
            
            JoinRoomDto joinDto = JoinRoomDto.builder()
                .playerId(playerId)
                .playerName(playerName)
                .roomCode(roomCode.trim())
                .build();
            
            log.info("Attempting to join room with DTO: {}", joinDto);
            
            // El roomService.joinRoom crea el juego automáticamente
            RoomInfoDto roomInfo = roomService.joinRoom(joinDto);
            
            log.info("Player {} successfully joined room {}. Game {} created.", 
                playerId, roomCode, roomInfo.getGameId());
            log.info("Room info: {}", roomInfo);
            
            // Obtener sessionIds de ambos jugadores
            String guestSession = sessionService.getSessionId(playerId);
            String hostSession = sessionService.getSessionId(roomInfo.getHostId());
            
            log.info("Guest session: {}, Host session: {}", guestSession, hostSession);
            
            // Crear mensaje de respuesta con JOIN_ROOM (lo que espera el frontend)
            GameMessage response = GameMessage.create(
                MessageType.JOIN_ROOM,
                roomInfo.getGameId(),
                null,  // será establecido para cada jugador
                roomInfo
            );
            
            // Notificar al guest que se unió exitosamente
            response.setPlayerId(playerId);
            if (guestSession != null) {
                messagingTemplate.convertAndSendToUser(
                    guestSession,
                    "/queue/room",
                    response
                );
                log.info("JOIN_ROOM sent to guest session: {}", guestSession);
            } else {
                log.error("No session found for guest: {}", playerId);
            }
            
            // Notificar al host que alguien se unió (CRÍTICO: el host debe saber que ya hay match)
            response.setPlayerId(roomInfo.getHostId());
            if (hostSession != null) {
                messagingTemplate.convertAndSendToUser(
                    hostSession,
                    "/queue/room",
                    response
                );
                log.info("JOIN_ROOM sent to host session: {}", hostSession);
            } else {
                log.error("No session found for host: {}", roomInfo.getHostId());
            }
            
            log.info("=== JOIN ROOM COMPLETED ===");
            
        } catch (IllegalArgumentException e) {
            log.error("Failed to join room (validation error): {}", e.getMessage());
            String sessionId = principal != null ? principal.getName() : null;
            if (sessionId != null) {
                GameMessage errorMsg = GameMessage.error(null, message.getPlayerId(), e.getMessage());
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", errorMsg);
            }
        } catch (IllegalStateException e) {
            log.error("Failed to join room (state error): {}", e.getMessage());
            String sessionId = principal != null ? principal.getName() : null;
            if (sessionId != null) {
                GameMessage errorMsg = GameMessage.error(null, message.getPlayerId(), e.getMessage());
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", errorMsg);
            }
        } catch (Exception e) {
            log.error("Error joining room: {}", e.getMessage(), e);
            String sessionId = principal != null ? principal.getName() : null;
            if (sessionId != null) {
                GameMessage errorMsg = GameMessage.error(null, message.getPlayerId(), "Error al unirse a la sala: " + e.getMessage());
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", errorMsg);
            }
        }
    }

    /**
     * Salir de una sala privada
     */
    @MessageMapping("/room/leave")
    public void leaveRoom(@Payload GameMessage message, Principal principal) {
        try {
            String playerId = extractPlayerId(message, principal);
            log.info("Player {} leaving room", playerId);
            
            String roomCode = roomService.getRoomCodeForPlayer(playerId);
            roomService.leaveRoom(playerId);
            
            GameMessage response = GameMessage.create(
                MessageType.LEAVE_ROOM,
                null,
                playerId,
                null
            );
            
            messagingTemplate.convertAndSendToUser(
                playerId,
                "/queue/room",
                response
            );
            
            log.info("Player {} left room {}", playerId, roomCode);
            
        } catch (Exception e) {
            log.error("Error leaving room: {}", e.getMessage());
        }
    }

    /**
     * ⚠️ IMPORTANTE: Este método recibe mensajes de juego y hace BROADCAST
     * Ruta principal para mensajes de juego: /app/game/{gameId}
     */
    @MessageMapping("/game/{gameId}")
    @SendTo("/topic/game/{gameId}")
    public GameMessage handleGameMessage(
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
                return GameMessage.error(gameId, playerId, "No perteneces a esta partida");
            }
            
            log.info("📨 Mensaje de juego recibido: gameId={}, playerId={}, type={}", 
                gameId, playerId, message.getType());
            
            // Actualizar timestamp de actividad
            gameService.updateGameActivity(gameId);
            
            // ✅ IMPORTANTE: Retornar el mensaje hace que se envíe a TODOS los suscritos
            return message;
            
        } catch (Exception e) {
            log.error("Error handling game message: {}", e.getMessage());
            return GameMessage.error(gameId, message.getPlayerId(), "Error al enviar mensaje: " + e.getMessage());
        }
    }

    /**
     * Reenvía mensajes de juego entre jugadores sin procesarlos.
     * El backend solo actúa como intermediario.
     * Ruta alternativa: /app/game/{gameId}/message
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
     * ⚠️ IMPORTANTE: Este método recibe mensajes de CHAT y hace BROADCAST
     * Ruta alternativa: /app/chat/{gameId} (para compatibilidad con frontend)
     */
    @MessageMapping("/chat/{gameId}")
    public void handleChatMessageAlt(
            @DestinationVariable String gameId,
            @Payload GameMessage message,
            Principal principal
    ) {
        try {
            String playerId = extractPlayerId(message, principal);
            message.setPlayerId(playerId);
            message.setGameId(gameId);
            message.setType(MessageType.CHAT_MESSAGE);
            
            log.info("💬 Chat recibido (alt): gameId={}, playerId={}, mensaje={}", 
                gameId, playerId, message.getMessage());
            
            // Verificar que el jugador pertenece al juego
            if (!gameService.isPlayerInGame(gameId, playerId)) {
                log.warn("Player {} attempted to send chat to game {} but is not a participant", 
                    playerId, gameId);
                return;
            }
            
            // ✅ IMPORTANTE: Broadcast a TODOS en el topic (incluye al emisor)
            // El frontend debe filtrar duplicados si los añade localmente
            messagingTemplate.convertAndSend(
                "/topic/game/" + gameId + "/chat",
                message
            );
            
        } catch (Exception e) {
            log.error("Error handling chat message: {}", e.getMessage());
        }
    }

    /**
     * ⚠️ IMPORTANTE: Este método recibe mensajes de CHAT y hace BROADCAST
     * Ruta estándar: /app/game/{gameId}/chat
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
        
        log.info("💬 Chat recibido: gameId={}, playerId={}, mensaje={}", 
            gameId, playerId, message.getMessage());
        
        // ✅ IMPORTANTE: Retornar el mensaje hace BROADCAST a todos
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
