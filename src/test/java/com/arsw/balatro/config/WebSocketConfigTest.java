package com.arsw.balatro.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketConfigTest {

    private WebSocketConfig webSocketConfig;
    private CognitoWebSocketHandshakeInterceptor handshakeInterceptor;
    private CognitoChannelInterceptor channelInterceptor;

    @BeforeEach
    void setUp() {
        handshakeInterceptor = mock(CognitoWebSocketHandshakeInterceptor.class);
        channelInterceptor = mock(CognitoChannelInterceptor.class);
        webSocketConfig = new WebSocketConfig(handshakeInterceptor, channelInterceptor);
        ReflectionTestUtils.setField(webSocketConfig, "allowedOrigins", new String[]{"http://localhost:3000"});
    }

    @Test
    void testConfigureMessageBroker_ShouldConfigureBroker() {
        // Given
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        SimpleBrokerRegistration brokerRegistration = mock(SimpleBrokerRegistration.class);
        when(registry.enableSimpleBroker(anyString())).thenReturn(brokerRegistration);
        when(registry.setApplicationDestinationPrefixes(anyString())).thenReturn(registry);
        when(registry.setUserDestinationPrefix(anyString())).thenReturn(registry);

        // When
        webSocketConfig.configureMessageBroker(registry);

        // Then
        verify(registry).enableSimpleBroker("/topic", "/queue");
        verify(registry).setApplicationDestinationPrefixes("/app");
        verify(registry).setUserDestinationPrefix("/user");
    }

    @Test
    void testConfigureClientInboundChannel_ShouldAddInterceptor() {
        // Given
        ChannelRegistration registration = mock(ChannelRegistration.class);
        when(registration.interceptors(any())).thenReturn(registration);

        // When
        webSocketConfig.configureClientInboundChannel(registration);

        // Then
        verify(registration).interceptors(channelInterceptor);
    }

    @Test
    void testRegisterStompEndpoints_ShouldRegisterEndpoints() {
        // Given
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        org.springframework.web.socket.config.annotation.SockJsServiceRegistration sockJsRegistration = 
            mock(org.springframework.web.socket.config.annotation.SockJsServiceRegistration.class);
        
        when(registry.addEndpoint(anyString())).thenReturn(registration);
        when(registration.setAllowedOrigins(any(String[].class))).thenReturn(registration);
        when(registration.setHandshakeHandler(any())).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);
        when(registration.withSockJS()).thenReturn(sockJsRegistration);

        // When
        webSocketConfig.registerStompEndpoints(registry);

        // Then
        verify(registry, atLeastOnce()).addEndpoint("/ws");
        verify(registration, atLeastOnce()).setAllowedOrigins(any(String[].class));
        verify(registration, atLeastOnce()).setHandshakeHandler(any());
        verify(registration, atLeastOnce()).addInterceptors(handshakeInterceptor);
    }
}

