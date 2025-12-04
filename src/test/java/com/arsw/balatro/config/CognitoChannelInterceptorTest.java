package com.arsw.balatro.config;

import com.arsw.balatro.config.CognitoWebSocketHandshakeInterceptor.CognitoPrincipal;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CognitoChannelInterceptorTest {

    private CognitoChannelInterceptor interceptor;
    private CognitoWebSocketHandshakeInterceptor handshakeInterceptor;

    @BeforeEach
    void setUp() {
        handshakeInterceptor = mock(CognitoWebSocketHandshakeInterceptor.class);
        interceptor = new CognitoChannelInterceptor(handshakeInterceptor);
    }

    @Test
    void testPreSend_WhenConnectCommand_ShouldValidateAndSetPrincipal() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session123");
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);
        
        CognitoPrincipal principal = new CognitoPrincipal("player1", mock(DecodedJWT.class));
        when(handshakeInterceptor.validateAndExtractPrincipal(any(StompHeaderAccessor.class)))
            .thenReturn(principal);

        // When
        Message<?> result = interceptor.preSend(message, channel);

        // Then
        assertNotNull(result);
        verify(handshakeInterceptor).validateAndExtractPrincipal(any(StompHeaderAccessor.class));
        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(
            result, StompHeaderAccessor.class);
        assertNotNull(resultAccessor);
        assertEquals(principal, resultAccessor.getUser());
    }

    @Test
    void testPreSend_WhenConnectCommandWithNullPrincipal_ShouldThrowException() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session123");
        Message<?> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);
        
        when(handshakeInterceptor.validateAndExtractPrincipal(any(StompHeaderAccessor.class)))
            .thenReturn(null);

        // When & Then
        assertThrows(MessageDeliveryException.class, () -> {
            interceptor.preSend(message, channel);
        });
    }

    @Test
    void testPreSend_WhenConnectCommandWithSecurityException_ShouldThrowMessageDeliveryException() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session123");
        Message<?> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);
        
        when(handshakeInterceptor.validateAndExtractPrincipal(any(StompHeaderAccessor.class)))
            .thenThrow(new SecurityException("Token inválido"));

        // When & Then
        assertThrows(MessageDeliveryException.class, () -> {
            interceptor.preSend(message, channel);
        });
    }

    @Test
    void testPreSend_WhenNonConnectCommand_ShouldReturnMessage() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("session123");
        Message<?> message = MessageBuilder.withPayload("test")
            .setHeaders(accessor)
            .build();
        MessageChannel channel = mock(MessageChannel.class);

        // When
        Message<?> result = interceptor.preSend(message, channel);

        // Then
        assertNotNull(result);
        verify(handshakeInterceptor, never()).validateAndExtractPrincipal(any());
    }

    @Test
    void testPreSend_WhenNullAccessor_ShouldReturnMessage() {
        // Given
        Message<?> message = MessageBuilder.withPayload("test").build();
        MessageChannel channel = mock(MessageChannel.class);

        // When
        Message<?> result = interceptor.preSend(message, channel);

        // Then
        assertNotNull(result);
        verify(handshakeInterceptor, never()).validateAndExtractPrincipal(any());
    }

    @Test
    void testPreSend_WhenConnectCommandWithPrincipal_ShouldLogAndSetPrincipal() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session123");
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);
        
        CognitoPrincipal principal = new CognitoPrincipal("player1", mock(DecodedJWT.class));
        when(handshakeInterceptor.validateAndExtractPrincipal(any(StompHeaderAccessor.class)))
            .thenReturn(principal);

        // When
        Message<?> result = interceptor.preSend(message, channel);

        // Then
        assertNotNull(result);
        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(
            result, StompHeaderAccessor.class);
        assertNotNull(resultAccessor);
        assertEquals(principal, resultAccessor.getUser());
        assertEquals("player1", resultAccessor.getUser().getName());
    }

    @Test
    void testPreSend_WhenConnectCommandWithGeneralException_ShouldThrowMessageDeliveryException() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session123");
        Message<?> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);
        
        when(handshakeInterceptor.validateAndExtractPrincipal(any(StompHeaderAccessor.class)))
            .thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        assertThrows(MessageDeliveryException.class, () -> {
            interceptor.preSend(message, channel);
        });
    }

    @Test
    void testPreSend_WhenNonConnectCommandWithPrincipal_ShouldReturnMessage() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("session123");
        accessor.setUser(() -> "player1");
        Message<?> message = MessageBuilder.withPayload("test")
            .setHeaders(accessor)
            .build();
        MessageChannel channel = mock(MessageChannel.class);

        // When
        Message<?> result = interceptor.preSend(message, channel);

        // Then
        assertNotNull(result);
        verify(handshakeInterceptor, never()).validateAndExtractPrincipal(any());
    }

    @Test
    void testPreSend_WhenNonConnectCommandWithoutPrincipal_ShouldReturnMessage() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session123");
        Message<?> message = MessageBuilder.withPayload("test")
            .setHeaders(accessor)
            .build();
        MessageChannel channel = mock(MessageChannel.class);

        // When
        Message<?> result = interceptor.preSend(message, channel);

        // Then
        assertNotNull(result);
        verify(handshakeInterceptor, never()).validateAndExtractPrincipal(any());
    }
}

