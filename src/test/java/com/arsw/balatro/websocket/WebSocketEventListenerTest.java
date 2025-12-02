package com.arsw.balatro.websocket;

import com.arsw.balatro.service.GameService;
import com.arsw.balatro.service.MatchmakingService;
import com.arsw.balatro.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @Mock
    private MatchmakingService matchmakingService;

    @Mock
    private GameService gameService;

    @Mock
    private SessionService sessionService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketEventListener webSocketEventListener;

    private SessionConnectedEvent connectedEvent;
    private SessionDisconnectEvent disconnectEvent;
    private Message<byte[]> message;
    private StompHeaderAccessor headerAccessor;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        Message<byte[]> msg = mock(Message.class);
        message = msg;
        headerAccessor = mock(StompHeaderAccessor.class);
        when(headerAccessor.getSessionId()).thenReturn("session123");
        
        connectedEvent = new SessionConnectedEvent(this, message);
        disconnectEvent = new SessionDisconnectEvent(this, message, "session123", CloseStatus.NORMAL);
    }

    @Test
    void testHandleWebSocketConnectListener_ShouldLogConnection() {
        // Given
        try (MockedStatic<StompHeaderAccessor> mockedStatic = mockStatic(StompHeaderAccessor.class)) {
            mockedStatic.when(() -> StompHeaderAccessor.wrap(any(Message.class))).thenReturn(headerAccessor);

            // When
            webSocketEventListener.handleWebSocketConnectListener(connectedEvent);

            // Then - just verify it doesn't throw exception
            verify(sessionService, never()).getPlayerId(anyString());
        }
    }

    @Test
    void testHandleWebSocketDisconnectListener_WhenPlayerIdExists_ShouldCleanup() {
        // Given
        String playerId = "player1";
        String gameId = "game123";
        
        try (MockedStatic<StompHeaderAccessor> mockedStatic = mockStatic(StompHeaderAccessor.class)) {
            mockedStatic.when(() -> StompHeaderAccessor.wrap(any(Message.class))).thenReturn(headerAccessor);
            when(sessionService.getPlayerId("session123")).thenReturn(playerId);
            when(gameService.getActiveGameIdForPlayer(playerId)).thenReturn(gameId);

            // When
            webSocketEventListener.handleWebSocketDisconnectListener(disconnectEvent);

            // Then
            verify(sessionService).removeBySessionId("session123");
            verify(matchmakingService).removeFromQueue(playerId);
            verify(gameService).getActiveGameIdForPlayer(playerId);
            verify(messagingTemplate).convertAndSend(eq("/topic/game/" + gameId), any(Object.class));
            verify(gameService).scheduleGameCleanup(gameId, 60);
        }
    }

    @Test
    void testHandleWebSocketDisconnectListener_WhenPlayerIdIsNullButPrincipalExists_ShouldCleanupByPrincipal() {
        // Given
        String principalName = "player1";
        Principal principal = mock(Principal.class);
        
        try (MockedStatic<StompHeaderAccessor> mockedStatic = mockStatic(StompHeaderAccessor.class)) {
            mockedStatic.when(() -> StompHeaderAccessor.wrap(any(Message.class))).thenReturn(headerAccessor);
            when(sessionService.getPlayerId("session123")).thenReturn(null);
            when(headerAccessor.getUser()).thenReturn(principal);
            when(principal.getName()).thenReturn(principalName);

            // When
            webSocketEventListener.handleWebSocketDisconnectListener(disconnectEvent);

            // Then
            verify(sessionService).removeBySessionId("session123");
            verify(sessionService).removeByPlayerId(principalName);
            verify(matchmakingService).removeFromQueue(principalName);
            verify(gameService, never()).getActiveGameIdForPlayer(anyString());
        }
    }

    @Test
    void testHandleWebSocketDisconnectListener_WhenNoPlayerIdAndNoPrincipal_ShouldOnlyRemoveSession() {
        // Given
        try (MockedStatic<StompHeaderAccessor> mockedStatic = mockStatic(StompHeaderAccessor.class)) {
            mockedStatic.when(() -> StompHeaderAccessor.wrap(any(Message.class))).thenReturn(headerAccessor);
            when(sessionService.getPlayerId("session123")).thenReturn(null);
            when(headerAccessor.getUser()).thenReturn(null);

            // When
            webSocketEventListener.handleWebSocketDisconnectListener(disconnectEvent);

            // Then
            verify(sessionService).removeBySessionId("session123");
            verify(sessionService, never()).removeByPlayerId(anyString());
            verify(matchmakingService, never()).removeFromQueue(anyString());
            verify(gameService, never()).getActiveGameIdForPlayer(anyString());
        }
    }

    @Test
    void testHandleWebSocketDisconnectListener_WhenNoActiveGame_ShouldNotSendDisconnectMessage() {
        // Given
        String playerId = "player1";
        
        try (MockedStatic<StompHeaderAccessor> mockedStatic = mockStatic(StompHeaderAccessor.class)) {
            mockedStatic.when(() -> StompHeaderAccessor.wrap(any(Message.class))).thenReturn(headerAccessor);
            when(sessionService.getPlayerId("session123")).thenReturn(playerId);
            when(gameService.getActiveGameIdForPlayer(playerId)).thenReturn(null);

            // When
            webSocketEventListener.handleWebSocketDisconnectListener(disconnectEvent);

            // Then
            verify(sessionService).removeBySessionId("session123");
            verify(matchmakingService).removeFromQueue(playerId);
            verify(gameService).getActiveGameIdForPlayer(playerId);
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
            verify(gameService, never()).scheduleGameCleanup(anyString(), anyInt());
        }
    }

    @Test
    void testHandleWebSocketDisconnectListener_WhenExceptionOccurs_ShouldHandleGracefully() {
        // Given
        String playerId = "player1";
        
        try (MockedStatic<StompHeaderAccessor> mockedStatic = mockStatic(StompHeaderAccessor.class)) {
            mockedStatic.when(() -> StompHeaderAccessor.wrap(any(Message.class))).thenReturn(headerAccessor);
            when(sessionService.getPlayerId("session123")).thenReturn(playerId);
            when(gameService.getActiveGameIdForPlayer(playerId)).thenThrow(new RuntimeException("Test exception"));

            // When
            webSocketEventListener.handleWebSocketDisconnectListener(disconnectEvent);

            // Then
            verify(sessionService).removeBySessionId("session123");
            verify(matchmakingService).removeFromQueue(playerId);
            verify(gameService).getActiveGameIdForPlayer(playerId);
            // Should not throw exception
        }
    }
}

