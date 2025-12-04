package com.arsw.balatro.controller;

import com.arsw.balatro.model.dto.*;
import com.arsw.balatro.model.enums.MessageType;
import com.arsw.balatro.service.GameService;
import com.arsw.balatro.service.MatchmakingService;
import com.arsw.balatro.service.RoomService;
import com.arsw.balatro.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GameWebSocketController Tests")
class GameWebSocketControllerTest {

    @Mock
    private MatchmakingService matchmakingService;

    @Mock
    private GameService gameService;

    @Mock
    private RoomService roomService;

    @Mock
    private SessionService sessionService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Principal principal;

    @Mock
    private SimpMessageHeaderAccessor headerAccessor;

    @InjectMocks
    private GameWebSocketController controller;

    @BeforeEach
    void setUp() {
        lenient().when(principal.getName()).thenReturn("player1");
        lenient().when(headerAccessor.getSessionId()).thenReturn("session1");
    }

    @Test
    @DisplayName("Should join matchmaking successfully")
    void shouldJoinMatchmakingSuccessfully() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.JOIN_QUEUE);
        message.setPlayerId("player1");
        
        QueueStatusDto queueStatus = QueueStatusDto.builder()
            .playerId("player1")
            .inQueue(true)
            .queuePosition(1)
            .playersInQueue(1)
            .estimatedWaitTime(30)
            .build();
        
        when(matchmakingService.addToQueue("player1")).thenReturn(queueStatus);

        // When
        controller.joinMatchmaking(message, principal, headerAccessor);

        // Then
        verify(sessionService).registerSession("player1", "session1");
        verify(matchmakingService).addToQueue("player1");
        verify(messagingTemplate).convertAndSendToUser(
            eq("player1"),
            eq("/queue/matchmaking"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should not join matchmaking with null Principal")
    void shouldNotJoinMatchmakingWithNullPrincipal() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.JOIN_QUEUE);
        message.setPlayerId("player1");

        // When
        controller.joinMatchmaking(message, null, headerAccessor);

        // Then
        verify(sessionService, never()).registerSession(anyString(), anyString());
        verify(matchmakingService, never()).addToQueue(anyString());
        verify(messagingTemplate).convertAndSend(eq("/queue/errors"), any(GameMessage.class));
    }

    @Test
    @DisplayName("Should leave matchmaking successfully")
    void shouldLeaveMatchmakingSuccessfully() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.LEAVE_QUEUE);
        message.setPlayerId("player1");

        // When
        controller.leaveMatchmaking(message, principal);

        // Then
        verify(matchmakingService).removeFromQueue("player1");
        verify(messagingTemplate).convertAndSendToUser(
            eq("player1"),
            eq("/queue/matchmaking"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should create room successfully")
    void shouldCreateRoomSuccessfully() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.CREATE_ROOM);
        message.setPlayerId("player1");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("roomCode", "ABC123");
        payload.put("playerName", "Player 1");
        message.setPayload(payload);
        
        RoomInfoDto roomInfo = RoomInfoDto.builder()
            .roomCode("ABC123")
            .hostId("player1")
            .hostName("Player 1")
            .isFull(false)
            .status(RoomInfoDto.RoomStatus.WAITING)
            .build();
        
        when(roomService.createRoom(any(CreateRoomDto.class))).thenReturn(roomInfo);

        // When
        controller.createRoom(message, principal, headerAccessor);

        // Then
        verify(sessionService).registerSession("player1", "session1");
        verify(roomService).createRoom(any(CreateRoomDto.class));
        verify(messagingTemplate).convertAndSendToUser(
            eq("player1"),
            eq("/queue/room"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should not create room with null Principal")
    void shouldNotCreateRoomWithNullPrincipal() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.CREATE_ROOM);
        message.setPlayerId("player1");

        // When
        controller.createRoom(message, null, headerAccessor);

        // Then
        verify(roomService, never()).createRoom(any(CreateRoomDto.class));
        verify(messagingTemplate).convertAndSend(eq("/queue/errors"), any(GameMessage.class));
    }

    @Test
    @DisplayName("Should join room successfully")
    void shouldJoinRoomSuccessfully() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.JOIN_ROOM);
        message.setPlayerId("player1"); // Controller uses Principal.getName(), not message.getPlayerId()
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("roomCode", "ABC123");
        payload.put("playerName", "Player 1");
        message.setPayload(payload);
        
        RoomInfoDto roomInfo = RoomInfoDto.builder()
            .roomCode("ABC123")
            .hostId("player1")
            .guestId("player1") // The principal is player1, so guestId will be player1
            .gameId("game123")
            .isFull(true)
            .status(RoomInfoDto.RoomStatus.IN_PROGRESS)
            .build();
        
        when(roomService.joinRoom(any(JoinRoomDto.class))).thenReturn(roomInfo);
        when(sessionService.getSessionId("player1")).thenReturn("session1");

        // When
        controller.joinRoom(message, principal, headerAccessor);

        // Then
        verify(sessionService).registerSession("player1", "session1");
        verify(roomService).joinRoom(any(JoinRoomDto.class));
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
            anyString(),
            eq("/queue/room"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should not join room with null Principal")
    void shouldNotJoinRoomWithNullPrincipal() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.JOIN_ROOM);
        message.setPlayerId("player2");

        // When
        controller.joinRoom(message, null, headerAccessor);

        // Then
        verify(roomService, never()).joinRoom(any(JoinRoomDto.class));
        verify(messagingTemplate).convertAndSend(eq("/queue/errors"), any(GameMessage.class));
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException when joining room")
    void shouldHandleIllegalArgumentExceptionWhenJoiningRoom() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.JOIN_ROOM);
        message.setPlayerId("player2");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("roomCode", "NONEXISTENT");
        message.setPayload(payload);
        
        when(roomService.joinRoom(any(JoinRoomDto.class)))
            .thenThrow(new IllegalArgumentException("Sala no encontrada"));

        // When
        controller.joinRoom(message, principal, headerAccessor);

        // Then
        verify(messagingTemplate).convertAndSendToUser(
            eq("player1"),
            eq("/queue/errors"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should leave room successfully")
    void shouldLeaveRoomSuccessfully() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.LEAVE_ROOM);
        message.setPlayerId("player1");
        
        when(roomService.getRoomCodeForPlayer("player1")).thenReturn("ABC123");

        // When
        controller.leaveRoom(message, principal);

        // Then
        verify(roomService).leaveRoom("player1");
        verify(messagingTemplate).convertAndSendToUser(
            eq("player1"),
            eq("/queue/room"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should handle ping message")
    void shouldHandlePingMessage() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.PING);
        message.setPlayerId("player1");

        // When
        controller.handlePing(message, principal);

        // Then
        verify(messagingTemplate).convertAndSendToUser(
            eq("player1"),
            eq("/queue/ping"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should use Principal username when message has different playerId")
    void shouldUsePrincipalUsernameWhenMessageHasDifferentPlayerId() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.JOIN_QUEUE);
        message.setPlayerId("differentPlayer"); // Different from principal
        
        QueueStatusDto queueStatus = QueueStatusDto.builder()
            .playerId("player1")
            .inQueue(true)
            .queuePosition(1)
            .playersInQueue(1)
            .estimatedWaitTime(30)
            .build();
        
        when(matchmakingService.addToQueue("player1")).thenReturn(queueStatus);

        // When
        controller.joinMatchmaking(message, principal, headerAccessor);

        // Then
        // Should use principal.getName() ("player1"), not message.getPlayerId() ("differentPlayer")
        verify(matchmakingService).addToQueue("player1");
        verify(matchmakingService, never()).addToQueue("differentPlayer");
    }

    @Test
    @DisplayName("Should handle exception when joining matchmaking")
    void shouldHandleExceptionWhenJoiningMatchmaking() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.JOIN_QUEUE);
        message.setPlayerId("player1");
        
        when(matchmakingService.addToQueue("player1"))
            .thenThrow(new RuntimeException("Service error"));

        // When
        controller.joinMatchmaking(message, principal, headerAccessor);

        // Then
        verify(messagingTemplate).convertAndSendToUser(
            eq("player1"),
            eq("/queue/errors"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should handle exception when creating room")
    void shouldHandleExceptionWhenCreatingRoom() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.CREATE_ROOM);
        message.setPlayerId("player1");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("roomCode", "ABC123");
        message.setPayload(payload);
        
        when(roomService.createRoom(any(CreateRoomDto.class)))
            .thenThrow(new IllegalStateException("Room already exists"));

        // When
        controller.createRoom(message, principal, headerAccessor);

        // Then
        verify(messagingTemplate).convertAndSendToUser(
            eq("player1"),
            eq("/queue/errors"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should handle exception when leaving room")
    void shouldHandleExceptionWhenLeavingRoom() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.LEAVE_ROOM);
        message.setPlayerId("player1");
        
        doThrow(new RuntimeException("Error")).when(roomService).leaveRoom("player1");

        // When
        controller.leaveRoom(message, principal);

        // Then
        // Should not throw exception, just log error
        verify(roomService).leaveRoom("player1");
    }

    @Test
    @DisplayName("Should not create room with missing roomCode")
    void shouldNotCreateRoomWithMissingRoomCode() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.CREATE_ROOM);
        message.setPlayerId("player1");
        message.setPayload(new HashMap<>()); // Empty payload

        // When
        controller.createRoom(message, principal, headerAccessor);

        // Then
        verify(roomService, never()).createRoom(any(CreateRoomDto.class));
        verify(messagingTemplate).convertAndSendToUser(
            eq("player1"),
            eq("/queue/errors"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should not join room with missing roomCode")
    void shouldNotJoinRoomWithMissingRoomCode() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.JOIN_ROOM);
        message.setPlayerId("player1");
        message.setPayload(new HashMap<>()); // Empty payload

        // When
        controller.joinRoom(message, principal, headerAccessor);

        // Then
        verify(roomService, never()).joinRoom(any(JoinRoomDto.class));
        verify(messagingTemplate).convertAndSendToUser(
            eq("player1"),
            eq("/queue/errors"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should not join matchmaking with null sessionId")
    void shouldNotJoinMatchmakingWithNullSessionId() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.JOIN_QUEUE);
        message.setPlayerId("player1");
        
        when(headerAccessor.getSessionId()).thenReturn(null);

        // When
        controller.joinMatchmaking(message, principal, headerAccessor);

        // Then
        verify(sessionService, never()).registerSession(anyString(), anyString());
        verify(messagingTemplate).convertAndSendToUser(
            eq("player1"),
            eq("/queue/errors"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should register session successfully")
    void shouldRegisterSessionSuccessfully() {
        // Given
        Map<String, String> registration = new HashMap<>();
        registration.put("playerId", "player1");
        registration.put("timestamp", "2024-01-01T00:00:00.000Z");

        // When
        controller.registerSession(registration, principal, headerAccessor);

        // Then
        verify(sessionService).registerSession("player1", "session1");
    }

    @Test
    @DisplayName("Should not register session with null sessionId")
    void shouldNotRegisterSessionWithNullSessionId() {
        // Given
        Map<String, String> registration = new HashMap<>();
        registration.put("playerId", "player1");
        
        when(headerAccessor.getSessionId()).thenReturn(null);

        // When
        controller.registerSession(registration, principal, headerAccessor);

        // Then
        verify(sessionService, never()).registerSession(anyString(), anyString());
    }

    @Test
    @DisplayName("Should register game session successfully")
    void shouldRegisterGameSessionSuccessfully() {
        // Given
        GameMessage message = new GameMessage();
        message.setPlayerId("player1");
        String gameId = "game123";
        
        when(gameService.isPlayerInGame(gameId, "player1")).thenReturn(true);

        // When
        controller.registerGameSession(gameId, message, principal, headerAccessor);

        // Then
        verify(sessionService).registerSession("player1", "session1");
    }

    @Test
    @DisplayName("Should not register game session if player not in game")
    void shouldNotRegisterGameSessionIfPlayerNotInGame() {
        // Given
        GameMessage message = new GameMessage();
        message.setPlayerId("player1");
        String gameId = "game123";
        
        when(gameService.isPlayerInGame(gameId, "player1")).thenReturn(false);

        // When
        controller.registerGameSession(gameId, message, principal, headerAccessor);

        // Then
        verify(sessionService, never()).registerSession(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle game message successfully")
    void shouldHandleGameMessageSuccessfully() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.GAME_MESSAGE);
        message.setPlayerId("player1");
        String gameId = "game123";
        
        when(gameService.isPlayerInGame(gameId, "player1")).thenReturn(true);

        // When
        GameMessage result = controller.handleGameMessage(gameId, message, principal);

        // Then
        assertNotNull(result);
        assertEquals("player1", result.getPlayerId());
        assertEquals(gameId, result.getGameId());
        verify(gameService).updateGameActivity(gameId);
    }

    @Test
    @DisplayName("Should return error when player not in game")
    void shouldReturnErrorWhenPlayerNotInGame() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.GAME_MESSAGE);
        message.setPlayerId("player1");
        String gameId = "game123";
        
        when(gameService.isPlayerInGame(gameId, "player1")).thenReturn(false);

        // When
        GameMessage result = controller.handleGameMessage(gameId, message, principal);

        // Then
        assertNotNull(result);
        assertEquals(MessageType.ERROR, result.getType());
    }

    @Test
    @DisplayName("Should relay game message successfully")
    void shouldRelayGameMessageSuccessfully() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.GAME_MESSAGE);
        message.setPlayerId("player1");
        String gameId = "game123";
        
        when(gameService.isPlayerInGame(gameId, "player1")).thenReturn(true);

        // When
        controller.relayGameMessage(gameId, message, principal);

        // Then
        verify(gameService).updateGameActivity(gameId);
        verify(messagingTemplate).convertAndSend(
            eq("/topic/game/" + gameId),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should handle chat message successfully")
    void shouldHandleChatMessageSuccessfully() {
        // Given
        GameMessage message = new GameMessage();
        message.setMessage("Hello");
        String gameId = "game123";

        // When
        GameMessage result = controller.handleChatMessage(gameId, message, principal);

        // Then
        assertNotNull(result);
        assertEquals(MessageType.CHAT_MESSAGE, result.getType());
        assertEquals("player1", result.getPlayerId());
        assertEquals(gameId, result.getGameId());
    }

    @Test
    @DisplayName("Should handle chat message alt successfully")
    void shouldHandleChatMessageAltSuccessfully() {
        // Given
        GameMessage message = new GameMessage();
        message.setMessage("Hello");
        String gameId = "game123";
        
        when(gameService.isPlayerInGame(gameId, "player1")).thenReturn(true);

        // When
        controller.handleChatMessageAlt(gameId, message, principal);

        // Then
        verify(messagingTemplate).convertAndSend(
            eq("/topic/game/" + gameId + "/chat"),
            any(GameMessage.class)
        );
    }

    @Test
    @DisplayName("Should handle emote successfully")
    void shouldHandleEmoteSuccessfully() {
        // Given
        GameMessage message = new GameMessage();
        message.setType(MessageType.PLAYER_EMOTE);
        String gameId = "game123";

        // When
        GameMessage result = controller.handleEmote(gameId, message, principal);

        // Then
        assertNotNull(result);
        assertEquals(MessageType.PLAYER_EMOTE, result.getType());
        assertEquals("player1", result.getPlayerId());
        assertEquals(gameId, result.getGameId());
    }

    @Test
    @DisplayName("Should handle exception in handleGameMessage")
    void shouldHandleExceptionInHandleGameMessage() {
        // Given
        GameMessage message = new GameMessage();
        message.setPlayerId("player1");
        String gameId = "game123";
        
        when(gameService.isPlayerInGame(gameId, "player1")).thenThrow(new RuntimeException("Error"));

        // When
        GameMessage result = controller.handleGameMessage(gameId, message, principal);

        // Then
        assertNotNull(result);
        assertEquals(MessageType.ERROR, result.getType());
    }
}

