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
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

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
    public void joinMatchmaking(
            @Payload GameMessage message, 
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {
        try {
            log.info("=== MATCHMAKING JOIN REQUEST ===");
            log.info("Principal: {} (null? {})", principal != null ? principal.getName() : "null", principal == null);
            log.info("Message: {}", message != null ? message.toString() : "null");
            log.info("HeaderAccessor: {}", headerAccessor != null ? "not null" : "null");
            
            if (principal == null) {
                log.error("❌ Principal es null! El mensaje no puede ser procesado sin autenticación.");
                if (message != null && message.getPlayerId() != null) {
                    GameMessage errorMsg = GameMessage.error(null, message.getPlayerId(), "Error de autenticación: Principal es null");
                    // Intentar enviar error usando el playerId del mensaje como fallback
                    messagingTemplate.convertAndSend("/queue/errors", errorMsg);
                }
                return;
            }
            
            String playerId = extractPlayerId(message, principal);
            // Obtener el sessionId real de Spring WebSocket
            String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
            
            log.info("Player ID: {} (from Principal)", playerId);
            log.info("Session ID: {} (from Spring WebSocket)", sessionId);
            
            // Registrar la sesión
            if (sessionId != null && playerId != null) {
                // SessionService normaliza automáticamente el playerId (trim + lowercase)
                sessionService.registerSession(playerId, sessionId);
                log.info("Session registered for player {} (normalized) -> session: {}", playerId, sessionId);
            } else {
                log.error("No session ID or player ID available. sessionId: {}, playerId: {}", sessionId, playerId);
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
            
            // IMPORTANTE: convertAndSendToUser espera el username (Principal.getName()), no el sessionId
            messagingTemplate.convertAndSendToUser(
                playerId,  // Usar el username (Principal name), no el sessionId
                "/queue/matchmaking", 
                response
            );
            
            log.info("Join confirmation sent to player {} (username, sessionId: {})", playerId, sessionId);
            
        } catch (Exception e) {
            log.error("Error joining matchmaking: {}", e.getMessage(), e);
            String playerId = extractPlayerId(message, principal);
            if (playerId != null) {
                GameMessage errorMsg = GameMessage.error(null, playerId, "Error al unirse a la cola: " + e.getMessage());
                // IMPORTANTE: convertAndSendToUser espera el username (Principal.getName()), no el sessionId
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
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
    public void createRoom(
            @Payload GameMessage message, 
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {
        try {
            String playerId = extractPlayerId(message, principal);
            // Obtener el sessionId real de Spring WebSocket
            String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
            
            log.info("=== CREATE ROOM REQUEST ===");
            log.info("Player ID: {} (from Principal)", playerId);
            log.info("Session ID: {} (from Spring WebSocket)", sessionId);
            log.info("Message payload: {}", message.getPayload());
            
            // Validar que el Principal esté disponible
            if (principal == null) {
                log.error("❌ Principal es null. El mensaje requiere autenticación con Cognito.");
                if (message != null && message.getPlayerId() != null) {
                    GameMessage errorMsg = GameMessage.error(null, message.getPlayerId(), "Error de autenticación: Principal es null");
                    messagingTemplate.convertAndSend("/queue/errors", errorMsg);
                }
                return;
            }
            
            // Registrar la sesión si no existe
            if (sessionId != null && playerId != null) {
                sessionService.registerSession(playerId, sessionId);
                log.info("Session registered for player {} (normalized) -> session: {}", playerId, sessionId);
            } else {
                log.error("No session ID or player ID available. sessionId: {}, playerId: {}", sessionId, playerId);
                GameMessage errorMsg = GameMessage.error(null, playerId, "Error de sesión");
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
                return;
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
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
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
            
            // Normalizar playerId para convertAndSendToUser
            String normalizedPlayerId = playerId != null ? playerId.trim().toLowerCase() : null;
            
            // IMPORTANTE: convertAndSendToUser espera el username (Principal.getName()), no el sessionId
            messagingTemplate.convertAndSendToUser(
                normalizedPlayerId,  // Usar el username de Cognito normalizado
                "/queue/room",
                response
            );
            
            log.info("✅ CREATE_ROOM response sent to player {} (normalized: {}, sessionId: {}) for room {}", 
                playerId, normalizedPlayerId, sessionId, roomInfo.getRoomCode());
            
        } catch (IllegalStateException e) {
            log.error("Room creation failed: {}", e.getMessage());
            String playerId = extractPlayerId(message, principal);
            if (playerId != null) {
                GameMessage errorMsg = GameMessage.error(null, playerId, e.getMessage());
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
            }
        } catch (Exception e) {
            log.error("Error creating room: {}", e.getMessage(), e);
            String playerId = extractPlayerId(message, principal);
            if (playerId != null) {
                GameMessage errorMsg = GameMessage.error(null, playerId, "Error al crear sala: " + e.getMessage());
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
            }
        }
    }

    /**
     * Unirse a una sala privada con código
     */
    @MessageMapping("/room/join")
    public void joinRoom(
            @Payload GameMessage message, 
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {
        try {
            String playerId = extractPlayerId(message, principal);
            // Obtener el sessionId real de Spring WebSocket
            String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
            
            log.info("=== JOIN ROOM REQUEST ===");
            log.info("Player ID: {} (from Principal)", playerId);
            log.info("Session ID: {} (from Spring WebSocket)", sessionId);
            log.info("Message payload: {}", message.getPayload());
            
            // Validar que el Principal esté disponible
            if (principal == null) {
                log.error("❌ Principal es null. El mensaje requiere autenticación con Cognito.");
                if (message != null && message.getPlayerId() != null) {
                    GameMessage errorMsg = GameMessage.error(null, message.getPlayerId(), "Error de autenticación: Principal es null");
                    messagingTemplate.convertAndSend("/queue/errors", errorMsg);
                }
                return;
            }
            
            // Registrar la sesión si no existe
            if (sessionId != null && playerId != null) {
                sessionService.registerSession(playerId, sessionId);
                log.info("Session registered for player {} (normalized) -> session: {}", playerId, sessionId);
            } else {
                log.error("No session ID or player ID available. sessionId: {}, playerId: {}", sessionId, playerId);
                GameMessage errorMsg = GameMessage.error(null, playerId, "Error de sesión");
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
                return;
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
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
                return;
            }
            
            JoinRoomDto joinDto = JoinRoomDto.builder()
                .playerId(playerId)
                .playerName(playerName)
                .roomCode(roomCode.trim())
                .build();
            
            log.info("Attempting to join room with DTO: {}", joinDto);
            log.info("💡 Debug info before join: {}", roomService.getDebugInfo());
            
            // El roomService.joinRoom crea el juego automáticamente
            RoomInfoDto roomInfo = roomService.joinRoom(joinDto);
            
            log.info("Player {} successfully joined room {}. Game {} created.", 
                playerId, roomCode, roomInfo.getGameId());
            log.info("Room info: {}", roomInfo);
            log.info("💡 RoomInfoDto playerIds - hostId: {}, guestId: {}", 
                roomInfo.getHostId(), roomInfo.getGuestId());
            log.info("💡 Estos playerIds están normalizados y deben usarse para WebRTC");
            
            // Verificar que las sesiones estén registradas
            String guestSessionId = sessionService.getSessionId(playerId);
            String hostSessionId = sessionService.getSessionId(roomInfo.getHostId());
            
            log.info("Guest session: {} (playerId: {}), Host session: {} (playerId: {})", 
                guestSessionId, playerId, hostSessionId, roomInfo.getHostId());
            log.info("💡 Para WebRTC, el frontend debe usar hostId={} y guestId={} del RoomInfoDto", 
                roomInfo.getHostId(), roomInfo.getGuestId());
            
            // Crear mensaje de respuesta con JOIN_ROOM (lo que espera el frontend)
            GameMessage response = GameMessage.create(
                MessageType.JOIN_ROOM,
                roomInfo.getGameId(),
                null,  // será establecido para cada jugador
                roomInfo
            );
            
            // Normalizar playerIds para convertAndSendToUser
            String normalizedGuestId = playerId != null ? playerId.trim().toLowerCase() : null;
            String normalizedHostId = roomInfo.getHostId() != null ? roomInfo.getHostId().trim().toLowerCase() : null;
            
            // Notificar al guest que se unió exitosamente
            response.setPlayerId(playerId);
            if (normalizedGuestId != null && guestSessionId != null) {
                // IMPORTANTE: convertAndSendToUser espera el username (Principal.getName()), no el sessionId
                messagingTemplate.convertAndSendToUser(
                    normalizedGuestId,  // Usar el username de Cognito normalizado
                    "/queue/room",
                    response
                );
                log.info("✅ JOIN_ROOM sent to guest: {} (normalized: {}, sessionId: {})", 
                    playerId, normalizedGuestId, guestSessionId);
            } else {
                log.error("❌ No session found for guest: {} (normalized: {})", playerId, normalizedGuestId);
            }
            
            // Notificar al host que alguien se unió (CRÍTICO: el host debe saber que ya hay match)
            response.setPlayerId(roomInfo.getHostId());
            if (normalizedHostId != null && hostSessionId != null) {
                // IMPORTANTE: convertAndSendToUser espera el username (Principal.getName()), no el sessionId
                messagingTemplate.convertAndSendToUser(
                    normalizedHostId,  // Usar el username de Cognito normalizado
                    "/queue/room",
                    response
                );
                log.info("✅ JOIN_ROOM sent to host: {} (normalized: {}, sessionId: {})", 
                    roomInfo.getHostId(), normalizedHostId, hostSessionId);
            } else {
                log.error("❌ No session found for host: {} (normalized: {})", 
                    roomInfo.getHostId(), normalizedHostId);
            }
            
            log.info("=== JOIN ROOM COMPLETED ===");
            
        } catch (IllegalArgumentException e) {
            log.error("Failed to join room (validation error): {}", e.getMessage());
            String playerId = extractPlayerId(message, principal);
            if (playerId != null) {
                GameMessage errorMsg = GameMessage.error(null, playerId, e.getMessage());
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
            }
        } catch (IllegalStateException e) {
            log.error("Failed to join room (state error): {}", e.getMessage());
            String playerId = extractPlayerId(message, principal);
            if (playerId != null) {
                GameMessage errorMsg = GameMessage.error(null, playerId, e.getMessage());
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
            }
        } catch (Exception e) {
            log.error("Error joining room: {}", e.getMessage(), e);
            String playerId = extractPlayerId(message, principal);
            if (playerId != null) {
                GameMessage errorMsg = GameMessage.error(null, playerId, "Error al unirse a la sala: " + e.getMessage());
                messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", errorMsg);
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
     * Registrar sesión del jugador (endpoint genérico usado por el frontend)
     * Endpoint: /app/session/register
     * El frontend envía este mensaje para registrar la sesión cuando se conecta
     * 
     * Acepta tanto Map<String, String> (formato requerido) como GameMessage (para compatibilidad)
     * Formato esperado del Map:
     * {
     *   "playerId": "username_cognito",
     *   "timestamp": "2024-01-01T00:00:00.000Z"
     * }
     */
    @MessageMapping("/session/register")
    public void registerSession(
            @Payload Map<String, String> registration, 
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {
        try {
            // Obtener el sessionId real de Spring WebSocket
            String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
            
            // Obtener el playerId del Principal (username de Cognito)
            // El playerId del mensaje se ignora para mantener consistencia con la autenticación
            String playerId = principal != null ? principal.getName() : null;
            
            // Si el mensaje trae un playerId diferente, loguear advertencia
            if (registration != null && registration.containsKey("playerId")) {
                String messagePlayerId = registration.get("playerId");
                if (playerId != null && !messagePlayerId.equals(playerId)) {
                    log.warn("⚠️ Session register: Message contains different playerId ({}), but using Cognito username ({}) instead", 
                        messagePlayerId, playerId);
                }
            }
            
            log.info("=== REGISTER SESSION ===");
            log.info("Player ID: {} (from Principal)", playerId);
            log.info("Session ID: {} (from Spring WebSocket)", sessionId);
            if (registration != null && registration.containsKey("timestamp")) {
                log.info("Timestamp: {}", registration.get("timestamp"));
            }
            
            if (sessionId == null) {
                log.error("No session ID available for player {}", playerId);
                return;
            }
            
            if (playerId == null) {
                log.error("No player ID available (Principal is null)");
                return;
            }
            
            // Registrar la sesión (actualiza si ya existe)
            // SessionService normaliza automáticamente el playerId (trim + lowercase)
            sessionService.registerSession(playerId, sessionId);
            log.info("✅ Session registered for player {} (normalized) -> session: {}", 
                playerId, sessionId);
            
        } catch (Exception e) {
            log.error("Error registering session: {}", e.getMessage(), e);
        }
    }

    /**
     * Registrar sesión cuando el jugador entra a una partida
     * Endpoint: /app/game/{gameId}/register
     * Esto asegura que la sesión esté registrada para el enrutamiento de WebRTC
     */
    @MessageMapping("/game/{gameId}/register")
    public void registerGameSession(
            @DestinationVariable String gameId,
            @Payload GameMessage message,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        try {
            String playerId = extractPlayerId(message, principal);
            // Obtener el sessionId real de Spring WebSocket
            String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
            
            log.info("=== REGISTER GAME SESSION ===");
            log.info("Game ID: {}", gameId);
            log.info("Player ID: {} (from Principal)", playerId);
            log.info("Session ID: {} (from Spring WebSocket)", sessionId);
            
            if (sessionId == null) {
                log.error("No session ID available for player {}", playerId);
                return;
            }
            
            // Verificar que el jugador pertenece al juego
            if (!gameService.isPlayerInGame(gameId, playerId)) {
                log.warn("Player {} attempted to register for game {} but is not a participant", 
                    playerId, gameId);
                return;
            }
            
            // Registrar la sesión (actualiza si ya existe)
            // SessionService normaliza automáticamente el playerId (trim + lowercase)
            sessionService.registerSession(playerId, sessionId);
            log.info("✅ Session registered for player {} (normalized) in game {} -> session: {}", 
                playerId, gameId, sessionId);
            
        } catch (Exception e) {
            log.error("Error registering game session: {}", e.getMessage(), e);
        }
    }

    /**
     * ⚠️ IMPORTANTE: Este método recibe mensajes de juego y hace BROADCAST
     * Ruta principal para mensajes de juego: /app/game/{gameId}
     * 
     * Este método maneja todos los tipos de mensajes de juego, incluyendo:
     * - GAME_MESSAGE
     * - ROUND_COMPLETE
     * - TIME_OUT (cuando un jugador se queda sin tiempo)
     * - GAME_WON (cuando un jugador gana, incluyendo por timeout del oponente)
     * - GAME_LOST (cuando un jugador pierde)
     * - GAME_OVER / VICTORY (cuando el juego termina)
     * - TIMER_SYNC, TIMER_START, TIMER_STOP, TIMER_UPDATE (sincronización de cronómetro)
     * - GAME_STATE_SYNC, PLAYER_ACTION_SYNC (sincronización de estado)
     * - Y cualquier otro tipo de mensaje de juego
     * 
     * IMPORTANTE: Todos los mensajes se reenvían a /topic/game/{gameId} para que
     * ambos jugadores los reciban. Esto incluye GAME_WON y GAME_LOST.
     */
    @MessageMapping("/game/{gameId}")
    @SendTo("/topic/game/{gameId}")
    public GameMessage handleGameMessage(
            @DestinationVariable String gameId,
            @Payload GameMessage message,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        try {
            String playerId = extractPlayerId(message, principal);
            // Obtener el sessionId real de Spring WebSocket
            String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
            
            // Normalizar playerId para consistencia (GameService normaliza internamente, pero mejor hacerlo aquí también)
            String normalizedPlayerId = playerId != null ? playerId.trim().toLowerCase() : null;
            
            log.info("=== HANDLE GAME MESSAGE ===");
            log.info("GameId: {}", gameId);
            log.info("PlayerId (original): {}", playerId);
            log.info("PlayerId (normalized): {}", normalizedPlayerId);
            log.info("Message type: {}", message.getType());
            log.info("SessionId: {}", sessionId);
            
            // ✅ CRÍTICO: Registrar/actualizar sesión cuando se envía un mensaje de juego
            // Esto asegura que las sesiones WebRTC se mantengan activas
            if (sessionId != null && normalizedPlayerId != null) {
                sessionService.registerSession(normalizedPlayerId, sessionId);
                log.debug("✅ Sesión actualizada para mantener WebRTC activo: playerId={}, sessionId={}", 
                    normalizedPlayerId, sessionId);
            }
            
            message.setPlayerId(normalizedPlayerId);
            message.setGameId(gameId);
            
            // Verificar que el jugador pertenece al juego
            boolean isInGame = gameService.isPlayerInGame(gameId, normalizedPlayerId);
            log.info("Player validation: isInGame={}, gameId={}, playerId={}", isInGame, gameId, normalizedPlayerId);
            
            if (!isInGame) {
                // Log detallado para diagnóstico
                try {
                    com.arsw.balatro.model.dto.GameState gameState = gameService.getGameState(gameId);
                    log.error("❌ Player {} (normalized: {}) NOT in game {}", normalizedPlayerId, normalizedPlayerId, gameId);
                    log.error("💡 Game state: player1Id={}, player2Id={}", gameState.getPlayer1Id(), gameState.getPlayer2Id());
                    log.error("💡 Comparison: player1Id.equals? {}, player2Id.equals? {}", 
                        gameState.getPlayer1Id().equals(normalizedPlayerId),
                        gameState.getPlayer2Id().equals(normalizedPlayerId));
                    log.error("💡 Debug info:\n{}", gameService.getDebugInfo());
                } catch (Exception e) {
                    log.error("💡 Game {} not found or error getting game state: {}", gameId, e.getMessage());
                    log.error("💡 Debug info:\n{}", gameService.getDebugInfo());
                }
                return GameMessage.error(gameId, normalizedPlayerId, "No perteneces a esta partida");
            }
            
            // Procesar mensajes según su tipo
            if (message.getType() == MessageType.ROUND_COMPLETE) {
                log.info("🔄 ROUND_COMPLETE recibido de player {} (normalized: {}) en game {}: payload={}", 
                    playerId, normalizedPlayerId, gameId, message.getPayload());
                
                // ✅ CRÍTICO: Asegurar que la sesión se mantenga activa después de ROUND_COMPLETE
                // Esto previene que el micrófono se desactive
                if (sessionId != null && normalizedPlayerId != null) {
                    sessionService.registerSession(normalizedPlayerId, sessionId);
                    log.info("✅ Sesión WebRTC mantenida activa después de ROUND_COMPLETE: playerId={}, sessionId={}", 
                        normalizedPlayerId, sessionId);
                }
                
                // Extraer ante y blind del payload
                try {
                    if (message.getPayload() != null) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> payloadMap = (java.util.Map<String, Object>) message.getPayload();
                        if (payloadMap.containsKey("data")) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) payloadMap.get("data");
                            if (dataMap != null && dataMap.containsKey("ante") && dataMap.containsKey("blind")) {
                                Integer ante = dataMap.get("ante") instanceof Number ? 
                                    ((Number) dataMap.get("ante")).intValue() : null;
                                String blind = dataMap.get("blind") != null ? dataMap.get("blind").toString() : null;
                                
                                if (ante != null && blind != null) {
                                    // Actualizar progreso del jugador
                                    gameService.updatePlayerProgress(gameId, normalizedPlayerId, ante, blind);
                                    
                                    // Verificar condiciones de victoria
                                    GameService.VictoryCheckResult victoryResult = 
                                        gameService.checkVictoryConditions(gameId, normalizedPlayerId);
                                    
                                    if (victoryResult.hasVictory()) {
                                        // Enviar GAME_WON al ganador y GAME_LOST al perdedor
                                        String winnerId = victoryResult.getWinnerId();
                                        String loserId = victoryResult.getLoserId();
                                        String reason = victoryResult.getReason();
                                        
                                        log.info("🏆 Victoria detectada: winner={}, loser={}, reason={}", 
                                            winnerId, loserId, reason);
                                        
                                        // Crear mensaje GAME_WON para el ganador
                                        java.util.Map<String, Object> wonPayload = new java.util.HashMap<>();
                                        java.util.Map<String, Object> wonData = new java.util.HashMap<>();
                                        wonData.put("reason", reason);
                                        wonData.put("opponentId", loserId);
                                        wonData.put("winnerId", winnerId);
                                        wonPayload.put("action", "GAME_WON");
                                        wonPayload.put("data", wonData);
                                        
                                        GameMessage wonMessage = GameMessage.create(
                                            MessageType.GAME_WON,
                                            gameId,
                                            winnerId,
                                            wonPayload
                                        );
                                        
                                        // Crear mensaje GAME_LOST para el perdedor
                                        java.util.Map<String, Object> lostPayload = new java.util.HashMap<>();
                                        java.util.Map<String, Object> lostData = new java.util.HashMap<>();
                                        lostData.put("reason", reason.equals("opponent_no_hands") ? "no_hands" : reason);
                                        lostPayload.put("action", "GAME_LOST");
                                        lostPayload.put("data", lostData);
                                        
                                        GameMessage lostMessage = GameMessage.create(
                                            MessageType.GAME_LOST,
                                            gameId,
                                            loserId,
                                            lostPayload
                                        );
                                        
                                        // Enviar mensajes a ambos jugadores
                                        messagingTemplate.convertAndSend("/topic/game/" + gameId, wonMessage);
                                        messagingTemplate.convertAndSend("/topic/game/" + gameId, lostMessage);
                                        
                                        log.info("✅ GAME_WON y GAME_LOST enviados a /topic/game/{}", gameId);
                                        
                                        // Retornar el mensaje original también
                                        return message;
                                    } else if (victoryResult.isTie()) {
                                        // Enviar GAME_LOST con reason 'tie' a ambos jugadores
                                        java.util.Map<String, Object> tiePayload = new java.util.HashMap<>();
                                        java.util.Map<String, Object> tieData = new java.util.HashMap<>();
                                        tieData.put("reason", "tie");
                                        tieData.put("message", "Empate - ambos jugadores se quedaron sin manos en el mismo ante");
                                        tiePayload.put("action", "GAME_LOST");
                                        tiePayload.put("data", tieData);
                                        
                                        GameMessage tieMessage1 = GameMessage.create(
                                            MessageType.GAME_LOST,
                                            gameId,
                                            normalizedPlayerId,
                                            tiePayload
                                        );
                                        
                                        String opponentId = gameService.getOpponentId(gameId, normalizedPlayerId);
                                        GameMessage tieMessage2 = GameMessage.create(
                                            MessageType.GAME_LOST,
                                            gameId,
                                            opponentId,
                                            tiePayload
                                        );
                                        
                                        messagingTemplate.convertAndSend("/topic/game/" + gameId, tieMessage1);
                                        messagingTemplate.convertAndSend("/topic/game/" + gameId, tieMessage2);
                                        
                                        log.info("✅ GAME_LOST (tie) enviado a ambos jugadores en /topic/game/{}", gameId);
                                        
                                        return message;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error procesando ROUND_COMPLETE: {}", e.getMessage(), e);
                }
                
            } else if (message.getType() == MessageType.GAME_LOST) {
                log.info("💀 GAME_LOST recibido de player {} (normalized: {}) en game {}: payload={}", 
                    playerId, normalizedPlayerId, gameId, message.getPayload());
                
                // Procesar GAME_LOST según la razón
                try {
                    if (message.getPayload() != null) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> payloadMap = (java.util.Map<String, Object>) message.getPayload();
                        if (payloadMap.containsKey("data")) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) payloadMap.get("data");
                            if (dataMap != null && dataMap.containsKey("reason")) {
                                String reason = dataMap.get("reason").toString();
                                
                                if ("no_hands".equals(reason)) {
                                    // Registrar que el jugador se quedó sin manos
                                    Integer ante = dataMap.get("ante") instanceof Number ? 
                                        ((Number) dataMap.get("ante")).intValue() : null;
                                    String blind = dataMap.get("blind") != null ? 
                                        dataMap.get("blind").toString() : null;
                                    
                                    if (ante != null && blind != null) {
                                        gameService.registerNoHands(gameId, normalizedPlayerId, ante, blind);
                                        log.info("💡 Registrado: Player {} se quedó sin manos en ante={}, blind={}", 
                                            normalizedPlayerId, ante, blind);
                                        
                                        // Verificar si el oponente ya está más adelante
                                        String opponentId = gameService.getOpponentId(gameId, normalizedPlayerId);
                                        GameService.VictoryCheckResult victoryResult = 
                                            gameService.checkVictoryConditions(gameId, opponentId);
                                        
                                        if (victoryResult.hasVictory() && opponentId.equals(victoryResult.getWinnerId())) {
                                            // El oponente ya ganó
                                            log.info("🏆 El oponente {} ya está más adelante - victoria inmediata", opponentId);
                                            
                                            java.util.Map<String, Object> wonPayload = new java.util.HashMap<>();
                                            java.util.Map<String, Object> wonData = new java.util.HashMap<>();
                                            wonData.put("reason", "opponent_no_hands");
                                            wonData.put("opponentId", normalizedPlayerId);
                                            wonData.put("winnerId", opponentId);
                                            wonPayload.put("action", "GAME_WON");
                                            wonPayload.put("data", wonData);
                                            
                                            GameMessage wonMessage = GameMessage.create(
                                                MessageType.GAME_WON,
                                                gameId,
                                                opponentId,
                                                wonPayload
                                            );
                                            
                                            messagingTemplate.convertAndSend("/topic/game/" + gameId, wonMessage);
                                            log.info("✅ GAME_WON enviado al oponente en /topic/game/{}", gameId);
                                        }
                                    }
                                } else if ("tie".equals(reason)) {
                                    log.info("💡 Empate detectado en game {}", gameId);
                                    // El empate ya se maneja en checkVictoryConditions
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error procesando GAME_LOST: {}", e.getMessage(), e);
                }
                
            } else if (message.getType() == MessageType.TIME_OUT) {
                log.info("⏰ TIME_OUT recibido de player {} (normalized: {}) en game {}: El jugador se quedó sin tiempo", 
                    playerId, normalizedPlayerId, gameId);
            } else if (message.getType() == MessageType.GAME_WON) {
                log.info("🏆 GAME_WON recibido de player {} (normalized: {}) en game {}: payload={}", 
                    playerId, normalizedPlayerId, gameId, message.getPayload());
                // Extraer información del payload para logging adicional
                if (message.getPayload() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> payloadMap = (java.util.Map<String, Object>) message.getPayload();
                        if (payloadMap.containsKey("data")) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) payloadMap.get("data");
                            if (dataMap != null && dataMap.containsKey("reason")) {
                                String reason = dataMap.get("reason").toString();
                                log.info("💡 Razón de victoria: {}", reason);
                                if ("opponent_timeout".equals(reason)) {
                                    log.info("💡 El oponente se quedó sin tiempo - notificando al oponente");
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("No se pudo parsear el payload para logging adicional: {}", e.getMessage());
                    }
                }
            } else if (message.getType() == MessageType.GAME_OVER || message.getType() == MessageType.VICTORY) {
                log.info("🏆 {} recibido de player {} (normalized: {}) en game {}: payload={}", 
                    message.getType(), playerId, normalizedPlayerId, gameId, message.getPayload());
            } else if (message.getType() == MessageType.TIMER_SYNC || 
                       message.getType() == MessageType.TIMER_START || 
                       message.getType() == MessageType.TIMER_STOP || 
                       message.getType() == MessageType.TIMER_UPDATE) {
                log.info("⏱️ Mensaje de cronómetro recibido: type={}, gameId={}, playerId={} (normalized: {})", 
                    message.getType(), gameId, playerId, normalizedPlayerId);
                // Los mensajes de cronómetro se reenvían automáticamente a ambos jugadores
            } else if (message.getType() == MessageType.GAME_STATE_SYNC || 
                       message.getType() == MessageType.PLAYER_ACTION_SYNC) {
                log.info("🔄 Mensaje de sincronización recibido: type={}, gameId={}, playerId={} (normalized: {})", 
                    message.getType(), gameId, playerId, normalizedPlayerId);
                // Los mensajes de sincronización se reenvían automáticamente a ambos jugadores
            } else {
                log.info("📨 Mensaje de juego recibido: gameId={}, playerId={} (normalized: {}), type={}", 
                    gameId, playerId, normalizedPlayerId, message.getType());
            }
            
            // Actualizar timestamp de actividad
            gameService.updateGameActivity(gameId);
            
            // ✅ IMPORTANTE: Retornar el mensaje hace que se envíe a TODOS los suscritos
            // Esto incluye al emisor y al oponente
            if (message.getType() == MessageType.ROUND_COMPLETE) {
                log.info("✅ ROUND_COMPLETE será enviado a /topic/game/{}", gameId);
            } else if (message.getType() == MessageType.TIME_OUT) {
                log.info("✅ TIME_OUT será enviado a /topic/game/{} - El oponente recibirá notificación de victoria", gameId);
            } else if (message.getType() == MessageType.GAME_WON) {
                log.info("✅ GAME_WON será enviado a /topic/game/{} - Ambos jugadores recibirán la notificación", gameId);
            } else if (message.getType() == MessageType.GAME_LOST) {
                log.info("✅ GAME_LOST será enviado a /topic/game/{} - Ambos jugadores recibirán la notificación", gameId);
            } else if (message.getType() == MessageType.GAME_OVER || message.getType() == MessageType.VICTORY) {
                log.info("✅ {} será enviado a /topic/game/{}", message.getType(), gameId);
            }
            return message;
            
        } catch (Exception e) {
            log.error("Error handling game message: {}", e.getMessage(), e);
            return GameMessage.error(gameId, message.getPlayerId(), "Error al enviar mensaje: " + e.getMessage());
        }
    }

    /**
     * Reenvía mensajes de juego entre jugadores sin procesarlos.
     * El backend solo actúa como intermediario.
     * Ruta alternativa: /app/game/{gameId}/message
     * 
     * Este método maneja todos los tipos de mensajes de juego, incluyendo:
     * - GAME_MESSAGE
     * - ROUND_COMPLETE
     * - TIME_OUT (cuando un jugador se queda sin tiempo)
     * - GAME_WON (cuando un jugador gana, incluyendo por timeout del oponente)
     * - GAME_LOST (cuando un jugador pierde)
     * - GAME_OVER / VICTORY (cuando el juego termina)
     * - TIMER_SYNC, TIMER_START, TIMER_STOP, TIMER_UPDATE (sincronización de cronómetro)
     * - GAME_STATE_SYNC, PLAYER_ACTION_SYNC (sincronización de estado)
     * - Y cualquier otro tipo de mensaje de juego
     * 
     * IMPORTANTE: Todos los mensajes se reenvían a /topic/game/{gameId} para que
     * ambos jugadores los reciban. Esto incluye GAME_WON y GAME_LOST.
     */
    @MessageMapping("/game/{gameId}/message")
    public void relayGameMessage(
            @DestinationVariable String gameId,
            @Payload GameMessage message,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        try {
            String playerId = extractPlayerId(message, principal);
            // Obtener el sessionId real de Spring WebSocket
            String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
            // Normalizar playerId para consistencia
            String normalizedPlayerId = playerId != null ? playerId.trim().toLowerCase() : null;
            
            log.info("=== RELAY GAME MESSAGE ===");
            log.info("GameId: {}", gameId);
            log.info("PlayerId (original): {}", playerId);
            log.info("PlayerId (normalized): {}", normalizedPlayerId);
            log.info("Message type: {}", message.getType());
            log.info("SessionId: {}", sessionId);
            
            // ✅ CRÍTICO: Registrar/actualizar sesión cuando se envía un mensaje de juego
            // Esto asegura que las sesiones WebRTC se mantengan activas
            if (sessionId != null && normalizedPlayerId != null) {
                sessionService.registerSession(normalizedPlayerId, sessionId);
                log.debug("✅ Sesión actualizada para mantener WebRTC activo: playerId={}, sessionId={}", 
                    normalizedPlayerId, sessionId);
            }
            
            message.setPlayerId(normalizedPlayerId);
            message.setGameId(gameId);
            
            // Verificar que el jugador pertenece al juego
            boolean isInGame = gameService.isPlayerInGame(gameId, normalizedPlayerId);
            log.info("Player validation: isInGame={}, gameId={}, playerId={}", isInGame, gameId, normalizedPlayerId);
            
            if (!isInGame) {
                // Log detallado para diagnóstico
                try {
                    com.arsw.balatro.model.dto.GameState gameState = gameService.getGameState(gameId);
                    log.error("❌ Player {} (normalized: {}) NOT in game {}", normalizedPlayerId, normalizedPlayerId, gameId);
                    log.error("💡 Game state: player1Id={}, player2Id={}", gameState.getPlayer1Id(), gameState.getPlayer2Id());
                    log.error("💡 Comparison: player1Id.equals? {}, player2Id.equals? {}", 
                        gameState.getPlayer1Id().equals(normalizedPlayerId),
                        gameState.getPlayer2Id().equals(normalizedPlayerId));
                    log.error("💡 Debug info:\n{}", gameService.getDebugInfo());
                } catch (Exception e) {
                    log.error("💡 Game {} not found or error getting game state: {}", gameId, e.getMessage());
                    log.error("💡 Debug info:\n{}", gameService.getDebugInfo());
                }
                sendError(normalizedPlayerId, gameId, "No perteneces a esta partida");
                return;
            }
            
            // Log detallado para mensajes importantes
            if (message.getType() == MessageType.ROUND_COMPLETE) {
                log.info("🔄 Reenviando ROUND_COMPLETE de player {} (normalized: {}) en game {}: payload={}", 
                    playerId, normalizedPlayerId, gameId, message.getPayload());
                
                // ✅ CRÍTICO: Asegurar que la sesión se mantenga activa después de ROUND_COMPLETE
                // Esto previene que el micrófono se desactive
                if (sessionId != null && normalizedPlayerId != null) {
                    sessionService.registerSession(normalizedPlayerId, sessionId);
                    log.info("✅ Sesión WebRTC mantenida activa después de ROUND_COMPLETE: playerId={}, sessionId={}", 
                        normalizedPlayerId, sessionId);
                }
            } else if (message.getType() == MessageType.TIME_OUT) {
                log.info("⏰ Reenviando TIME_OUT de player {} (normalized: {}) en game {}: El jugador se quedó sin tiempo", 
                    playerId, normalizedPlayerId, gameId);
            } else if (message.getType() == MessageType.GAME_WON) {
                log.info("🏆 Reenviando GAME_WON de player {} (normalized: {}) en game {}: payload={}", 
                    playerId, normalizedPlayerId, gameId, message.getPayload());
                // Extraer información del payload para logging adicional
                if (message.getPayload() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> payloadMap = (java.util.Map<String, Object>) message.getPayload();
                        if (payloadMap.containsKey("data")) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) payloadMap.get("data");
                            if (dataMap != null && dataMap.containsKey("reason")) {
                                String reason = dataMap.get("reason").toString();
                                log.info("💡 Razón de victoria: {}", reason);
                                if ("opponent_timeout".equals(reason)) {
                                    log.info("💡 El oponente se quedó sin tiempo - notificando al oponente");
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("No se pudo parsear el payload para logging adicional: {}", e.getMessage());
                    }
                }
            } else if (message.getType() == MessageType.GAME_LOST) {
                log.info("💀 Reenviando GAME_LOST de player {} (normalized: {}) en game {}: payload={}", 
                    playerId, normalizedPlayerId, gameId, message.getPayload());
            } else if (message.getType() == MessageType.GAME_OVER || message.getType() == MessageType.VICTORY) {
                log.info("🏆 Reenviando {} de player {} (normalized: {}) en game {}: payload={}", 
                    message.getType(), playerId, normalizedPlayerId, gameId, message.getPayload());
            } else if (message.getType() == MessageType.TIMER_SYNC || 
                       message.getType() == MessageType.TIMER_START || 
                       message.getType() == MessageType.TIMER_STOP || 
                       message.getType() == MessageType.TIMER_UPDATE) {
                log.info("⏱️ Reenviando mensaje de cronómetro: type={}, gameId={}, playerId={} (normalized: {})", 
                    message.getType(), gameId, playerId, normalizedPlayerId);
            } else if (message.getType() == MessageType.GAME_STATE_SYNC || 
                       message.getType() == MessageType.PLAYER_ACTION_SYNC) {
                log.info("🔄 Reenviando mensaje de sincronización: type={}, gameId={}, playerId={} (normalized: {})", 
                    message.getType(), gameId, playerId, normalizedPlayerId);
            } else {
                log.debug("Relaying message from player {} (normalized: {}) in game {}: type={}", 
                    playerId, normalizedPlayerId, gameId, message.getType());
            }
            
            // Actualizar timestamp de actividad
            gameService.updateGameActivity(gameId);
            
            // Reenviar el mensaje a ambos jugadores en el topic del juego
            // Esto incluye al emisor y al oponente
            messagingTemplate.convertAndSend(
                "/topic/game/" + gameId,
                message
            );
            
            if (message.getType() == MessageType.ROUND_COMPLETE) {
                log.info("✅ ROUND_COMPLETE reenviado exitosamente a /topic/game/{}", gameId);
            } else if (message.getType() == MessageType.TIME_OUT) {
                log.info("✅ TIME_OUT reenviado exitosamente a /topic/game/{} - El oponente recibirá notificación de victoria", gameId);
            } else if (message.getType() == MessageType.GAME_WON) {
                log.info("✅ GAME_WON reenviado exitosamente a /topic/game/{} - Ambos jugadores recibirán la notificación", gameId);
            } else if (message.getType() == MessageType.GAME_LOST) {
                log.info("✅ GAME_LOST reenviado exitosamente a /topic/game/{} - Ambos jugadores recibirán la notificación", gameId);
            } else if (message.getType() == MessageType.GAME_OVER || message.getType() == MessageType.VICTORY) {
                log.info("✅ {} reenviado exitosamente a /topic/game/{}", message.getType(), gameId);
            }
            
        } catch (Exception e) {
            log.error("Error relaying game message: {}", e.getMessage(), e);
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
     * Sincronización de cronómetro entre jugadores
     * Endpoint: /app/game/{gameId}/timer
     * 
     * Este endpoint permite sincronizar el cronómetro entre ambos jugadores.
     * Los mensajes se reenvían a ambos jugadores para mantener sincronización.
     */
    @MessageMapping("/game/{gameId}/timer")
    public void handleTimerSync(
            @DestinationVariable String gameId,
            @Payload GameMessage message,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        try {
            String playerId = extractPlayerId(message, principal);
            String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
            String normalizedPlayerId = playerId != null ? playerId.trim().toLowerCase() : null;
            
            log.info("⏱️ Timer sync recibido: gameId={}, playerId={} (normalized: {}), type={}", 
                gameId, normalizedPlayerId, normalizedPlayerId, message.getType());
            
            // Verificar que el jugador pertenece al juego
            if (!gameService.isPlayerInGame(gameId, normalizedPlayerId)) {
                log.warn("Player {} attempted to sync timer in game {} but is not a participant", 
                    normalizedPlayerId, gameId);
                return;
            }
            
            // ✅ CRÍTICO: Mantener sesión activa para WebRTC
            if (sessionId != null && normalizedPlayerId != null) {
                sessionService.registerSession(normalizedPlayerId, sessionId);
            }
            
            // Establecer gameId y playerId en el mensaje
            message.setGameId(gameId);
            message.setPlayerId(normalizedPlayerId);
            
            // Reenviar a ambos jugadores para sincronización
            messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
            
            log.info("✅ Timer sync reenviado a /topic/game/{}", gameId);
            
        } catch (Exception e) {
            log.error("Error handling timer sync: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sincronización de estado del juego
     * Endpoint: /app/game/{gameId}/sync
     * 
     * Este endpoint permite sincronizar el estado del juego y las acciones del rival.
     * Los mensajes se reenvían a ambos jugadores para mantener sincronización.
     */
    @MessageMapping("/game/{gameId}/sync")
    public void handleGameStateSync(
            @DestinationVariable String gameId,
            @Payload GameMessage message,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        try {
            String playerId = extractPlayerId(message, principal);
            String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
            String normalizedPlayerId = playerId != null ? playerId.trim().toLowerCase() : null;
            
            log.info("🔄 Game state sync recibido: gameId={}, playerId={} (normalized: {}), type={}", 
                gameId, normalizedPlayerId, normalizedPlayerId, message.getType());
            
            // Verificar que el jugador pertenece al juego
            if (!gameService.isPlayerInGame(gameId, normalizedPlayerId)) {
                log.warn("Player {} attempted to sync state in game {} but is not a participant", 
                    normalizedPlayerId, gameId);
                return;
            }
            
            // ✅ CRÍTICO: Mantener sesión activa para WebRTC
            if (sessionId != null && normalizedPlayerId != null) {
                sessionService.registerSession(normalizedPlayerId, sessionId);
            }
            
            // Establecer gameId y playerId en el mensaje
            message.setGameId(gameId);
            message.setPlayerId(normalizedPlayerId);
            
            // Reenviar a ambos jugadores para sincronización
            messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
            
            log.info("✅ Game state sync reenviado a /topic/game/{}", gameId);
            
        } catch (Exception e) {
            log.error("Error handling game state sync: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Solicitar estado actual del juego
     * Endpoint: /app/game/{gameId}/state
     * 
     * Este endpoint permite a un jugador solicitar el estado actual del juego,
     * incluyendo el progreso de ambos jugadores.
     */
    @MessageMapping("/game/{gameId}/state")
    public void handleGameStateRequest(
            @DestinationVariable String gameId,
            @Payload GameMessage message,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        try {
            String playerId = extractPlayerId(message, principal);
            String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
            String normalizedPlayerId = playerId != null ? playerId.trim().toLowerCase() : null;
            
            log.info("📊 Game state request recibido: gameId={}, playerId={} (normalized: {})", 
                gameId, normalizedPlayerId, normalizedPlayerId);
            
            // Verificar que el jugador pertenece al juego
            if (!gameService.isPlayerInGame(gameId, normalizedPlayerId)) {
                log.warn("Player {} attempted to request state for game {} but is not a participant", 
                    normalizedPlayerId, gameId);
                return;
            }
            
            // ✅ CRÍTICO: Mantener sesión activa para WebRTC
            if (sessionId != null && normalizedPlayerId != null) {
                sessionService.registerSession(normalizedPlayerId, sessionId);
            }
            
            // Obtener el estado del juego
            GameState gameState = gameService.getGameState(gameId);
            
            // Crear mensaje de respuesta con el estado
            GameMessage response = GameMessage.create(
                MessageType.GAME_STATE_SYNC,
                gameId,
                normalizedPlayerId,
                gameState
            );
            
            // Enviar solo al jugador que lo solicitó
            messagingTemplate.convertAndSendToUser(
                normalizedPlayerId,
                "/queue/game/" + gameId + "/state",
                response
            );
            
            log.info("✅ Game state enviado a player {} para game {}", normalizedPlayerId, gameId);
            
        } catch (Exception e) {
            log.error("Error handling game state request: {}", e.getMessage(), e);
        }
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

    /**
     * Normaliza un playerId: trim + lowercase
     * Esto asegura consistencia en las comparaciones con GameService
     */
    private String normalizePlayerId(String playerId) {
        if (playerId == null) {
            return null;
        }
        return playerId.trim().toLowerCase();
    }
    
    /**
     * Extrae el playerId del usuario autenticado.
     * Con Cognito, siempre usamos el username de Cognito como playerId para mantener consistencia.
     * Si el mensaje trae un playerId diferente, lo ignoramos y usamos el del Principal.
     * 
     * IMPORTANTE: El playerId retornado NO está normalizado. Debe normalizarse antes de usarse
     * en comparaciones con GameService.
     */
    private String extractPlayerId(GameMessage message, Principal principal) {
        // Con Cognito, siempre usar el username del Principal (viene del token JWT)
        // Esto asegura que el playerId sea consistente con la autenticación
        if (principal != null) {
            String cognitoUsername = principal.getName();
            log.debug("Using Cognito username as playerId: {}", cognitoUsername);
            
            // Si el mensaje trae un playerId diferente, loguear advertencia
            if (message != null && message.getPlayerId() != null) {
                String normalizedMessagePlayerId = normalizePlayerId(message.getPlayerId());
                String normalizedCognitoUsername = normalizePlayerId(cognitoUsername);
                if (!normalizedMessagePlayerId.equals(normalizedCognitoUsername)) {
                    log.warn("Message contains different playerId ({}), but using Cognito username ({}) instead", 
                        message.getPlayerId(), cognitoUsername);
                }
            }
            
            return cognitoUsername;
        }
        
        // Fallback: si no hay Principal (no debería pasar con Cognito), usar el del mensaje
        if (message != null && message.getPlayerId() != null) {
            log.warn("No Principal available, using playerId from message: {}", message.getPlayerId());
            return message.getPlayerId();
        }
        
        throw new IllegalArgumentException("No se pudo determinar el ID del jugador: no hay Principal ni playerId en el mensaje");
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
