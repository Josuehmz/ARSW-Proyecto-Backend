package com.arsw.balatro.controller;

import com.arsw.balatro.model.dto.SignalingMessage;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WebRTCSignalingController Tests")
class WebRTCSignalingControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SessionService sessionService;

    @Mock
    private Principal principal;

    @Mock
    private SimpMessageHeaderAccessor headerAccessor;

    @InjectMocks
    private WebRTCSignalingController controller;

    @BeforeEach
    void setUp() {
        lenient().when(principal.getName()).thenReturn("player1");
        lenient().when(headerAccessor.getSessionId()).thenReturn("session1");
    }

    @Test
    @DisplayName("Should handle OFFER signal successfully")
    void shouldHandleOfferSignalSuccessfully() {
        // Given
        SignalingMessage message = new SignalingMessage();
        message.setType("OFFER");
        message.setGameId("game123");
        message.setTargetId("player2");
        message.setSenderId("player1");
        
        when(sessionService.getSessionId("player2")).thenReturn("session2");

        // When
        controller.handleSignaling(message, principal, headerAccessor);

        // Then
        verify(sessionService).registerSession("player1", "session1");
        verify(sessionService).getSessionId("player2");
        verify(messagingTemplate).convertAndSendToUser(
            eq("player2"),
            eq("/queue/webrtc/game123"),
            any()
        );
        assertEquals("player1", message.getSenderId());
    }

    @Test
    @DisplayName("Should not handle signal with null Principal")
    void shouldNotHandleSignalWithNullPrincipal() {
        // Given
        SignalingMessage message = new SignalingMessage();
        message.setType("OFFER");
        message.setGameId("game123");
        message.setTargetId("player2");

        // When
        controller.handleSignaling(message, null, headerAccessor);

        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should not handle signal with null targetId")
    void shouldNotHandleSignalWithNullTargetId() {
        // Given
        SignalingMessage message = new SignalingMessage();
        message.setType("OFFER");
        message.setGameId("game123");
        message.setTargetId(null);

        // When
        controller.handleSignaling(message, principal, headerAccessor);

        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should not handle signal with null gameId")
    void shouldNotHandleSignalWithNullGameId() {
        // Given
        SignalingMessage message = new SignalingMessage();
        message.setType("OFFER");
        message.setGameId(null);
        message.setTargetId("player2");

        // When
        controller.handleSignaling(message, principal, headerAccessor);

        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should not handle signal with null type")
    void shouldNotHandleSignalWithNullType() {
        // Given
        SignalingMessage message = new SignalingMessage();
        message.setType(null);
        message.setGameId("game123");
        message.setTargetId("player2");

        // When
        controller.handleSignaling(message, principal, headerAccessor);

        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should not handle signal when sending to self")
    void shouldNotHandleSignalWhenSendingToSelf() {
        // Given
        SignalingMessage message = new SignalingMessage();
        message.setType("OFFER");
        message.setGameId("game123");
        message.setTargetId("player1"); // Same as principal

        // When
        controller.handleSignaling(message, principal, headerAccessor);

        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should not handle signal when target session not found")
    void shouldNotHandleSignalWhenTargetSessionNotFound() {
        // Given
        SignalingMessage message = new SignalingMessage();
        message.setType("OFFER");
        message.setGameId("game123");
        message.setTargetId("player2");
        
        when(sessionService.getSessionId("player2")).thenReturn(null);

        // When
        controller.handleSignaling(message, principal, headerAccessor);

        // Then
        verify(sessionService).getSessionId("player2");
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should normalize playerIds when handling signal")
    void shouldNormalizePlayerIdsWhenHandlingSignal() {
        // Given
        SignalingMessage message = new SignalingMessage();
        message.setType("ANSWER");
        message.setGameId("game123");
        message.setTargetId("  Player2  ");
        
        when(sessionService.getSessionId("player2")).thenReturn("session2");

        // When
        controller.handleSignaling(message, principal, headerAccessor);

        // Then
        verify(sessionService).getSessionId("player2");
        verify(messagingTemplate).convertAndSendToUser(
            eq("player2"),
            eq("/queue/webrtc/game123"),
            any()
        );
    }

    @Test
    @DisplayName("Should handle ICE_CANDIDATE signal successfully")
    void shouldHandleIceCandidateSignalSuccessfully() {
        // Given
        SignalingMessage message = new SignalingMessage();
        message.setType("ICE_CANDIDATE");
        message.setGameId("game123");
        message.setTargetId("player2");
        
        when(sessionService.getSessionId("player2")).thenReturn("session2");

        // When
        controller.handleSignaling(message, principal, headerAccessor);

        // Then
        verify(messagingTemplate).convertAndSendToUser(
            eq("player2"),
            eq("/queue/webrtc/game123"),
            any()
        );
    }

    @Test
    @DisplayName("Should use Principal username as senderId")
    void shouldUsePrincipalUsernameAsSenderId() {
        // Given
        SignalingMessage message = new SignalingMessage();
        message.setType("OFFER");
        message.setGameId("game123");
        message.setTargetId("player2");
        message.setSenderId("differentSender"); // Different from principal
        
        when(sessionService.getSessionId("player2")).thenReturn("session2");

        // When
        controller.handleSignaling(message, principal, headerAccessor);

        // Then
        assertEquals("player1", message.getSenderId());
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should register sender session when handling signal")
    void shouldRegisterSenderSessionWhenHandlingSignal() {
        // Given
        SignalingMessage message = new SignalingMessage();
        message.setType("OFFER");
        message.setGameId("game123");
        message.setTargetId("player2");
        
        when(sessionService.getSessionId("player2")).thenReturn("session2");

        // When
        controller.handleSignaling(message, principal, headerAccessor);

        // Then
        verify(sessionService).registerSession("player1", "session1");
    }
}

