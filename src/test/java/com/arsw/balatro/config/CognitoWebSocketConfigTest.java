package com.arsw.balatro.config;

import com.arsw.balatro.service.CognitoTokenValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CognitoWebSocketConfigTest {

    private CognitoWebSocketConfig config;
    private CognitoTokenValidationService tokenValidationService;

    @BeforeEach
    void setUp() {
        tokenValidationService = mock(CognitoTokenValidationService.class);
        config = new CognitoWebSocketConfig(tokenValidationService);
    }

    @Test
    void testCognitoWebSocketHandshakeInterceptorBean_ShouldCreateInterceptor() {
        // When
        CognitoWebSocketHandshakeInterceptor interceptor = 
            config.cognitoWebSocketHandshakeInterceptor();

        // Then
        assertNotNull(interceptor);
    }

    @Test
    void testCognitoChannelInterceptorBean_ShouldCreateInterceptor() {
        // Given
        CognitoWebSocketHandshakeInterceptor handshakeInterceptor = 
            config.cognitoWebSocketHandshakeInterceptor();

        // When
        CognitoChannelInterceptor channelInterceptor = 
            config.cognitoChannelInterceptor(handshakeInterceptor);

        // Then
        assertNotNull(channelInterceptor);
    }

    @Test
    void testCognitoChannelInterceptorBean_ShouldUseHandshakeInterceptor() {
        // Given
        CognitoWebSocketHandshakeInterceptor handshakeInterceptor = 
            config.cognitoWebSocketHandshakeInterceptor();

        // When
        CognitoChannelInterceptor channelInterceptor = 
            config.cognitoChannelInterceptor(handshakeInterceptor);

        // Then
        assertNotNull(channelInterceptor);
        // Verificar que el interceptor está correctamente configurado
        assertSame(handshakeInterceptor, 
            org.springframework.test.util.ReflectionTestUtils.getField(
                channelInterceptor, "handshakeInterceptor"));
    }
}






