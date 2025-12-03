package com.arsw.balatro.config;

import com.arsw.balatro.config.CognitoWebSocketHandshakeInterceptor.CognitoPrincipal;
import com.arsw.balatro.service.CognitoTokenValidationService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;

import java.security.Principal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CognitoWebSocketHandshakeInterceptorTest {

    private CognitoWebSocketHandshakeInterceptor interceptor;
    private CognitoTokenValidationService tokenValidationService;

    @BeforeEach
    void setUp() {
        tokenValidationService = mock(CognitoTokenValidationService.class);
        interceptor = new CognitoWebSocketHandshakeInterceptor(tokenValidationService);
    }

    @Test
    void testBeforeHandshake_ShouldReturnTrue() throws Exception {
        // Given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(java.net.URI.create("ws://localhost:8080/ws"));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertTrue(result);
    }

    @Test
    void testAfterHandshake_WhenNoException_ShouldComplete() {
        // Given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);

        // When & Then
        assertDoesNotThrow(() -> {
            interceptor.afterHandshake(request, response, wsHandler, null);
        });
    }

    @Test
    void testAfterHandshake_WhenException_ShouldHandleGracefully() {
        // Given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Exception exception = new Exception("Test exception");

        // When & Then
        assertDoesNotThrow(() -> {
            interceptor.afterHandshake(request, response, wsHandler, exception);
        });
    }

    @Test
    void testValidateAndExtractPrincipal_WhenValidToken_ShouldReturnPrincipal() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-token");
        
        DecodedJWT decodedJWT = createMockJWT("player1");
        when(tokenValidationService.extractTokenFromHeader("Bearer valid-token"))
            .thenReturn("valid-token");
        when(tokenValidationService.validateToken("valid-token"))
            .thenReturn(decodedJWT);
        when(tokenValidationService.extractUsername(decodedJWT))
            .thenReturn("player1");

        // When
        Principal principal = interceptor.validateAndExtractPrincipal(accessor);

        // Then
        assertNotNull(principal);
        assertEquals("player1", principal.getName());
        assertTrue(principal instanceof CognitoPrincipal);
    }

    @Test
    void testValidateAndExtractPrincipal_WhenNullAccessor_ShouldReturnNull() {
        // When
        Principal principal = interceptor.validateAndExtractPrincipal(null);

        // Then
        assertNull(principal);
    }

    @Test
    void testValidateAndExtractPrincipal_WhenNotConnectCommand_ShouldReturnNull() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);

        // When
        Principal principal = interceptor.validateAndExtractPrincipal(accessor);

        // Then
        assertNull(principal);
    }

    @Test
    void testValidateAndExtractPrincipal_WhenNoAuthorizationHeader_ShouldThrowSecurityException() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        // When & Then
        assertThrows(SecurityException.class, () -> {
            interceptor.validateAndExtractPrincipal(accessor);
        });
    }

    @Test
    void testValidateAndExtractPrincipal_WhenInvalidTokenFormat_ShouldThrowSecurityException() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "InvalidFormat");
        
        when(tokenValidationService.extractTokenFromHeader("InvalidFormat"))
            .thenReturn(null);

        // When & Then
        assertThrows(SecurityException.class, () -> {
            interceptor.validateAndExtractPrincipal(accessor);
        });
    }

    @Test
    void testValidateAndExtractPrincipal_WhenJWTVerificationException_ShouldThrowSecurityException() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer invalid-token");
        
        when(tokenValidationService.extractTokenFromHeader("Bearer invalid-token"))
            .thenReturn("invalid-token");
        when(tokenValidationService.validateToken("invalid-token"))
            .thenThrow(new JWTVerificationException("Token expired"));

        // When & Then
        assertThrows(SecurityException.class, () -> {
            interceptor.validateAndExtractPrincipal(accessor);
        });
    }

    @Test
    void testValidateAndExtractPrincipal_ShouldNormalizeUsername() {
        // Given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-token");
        
        DecodedJWT decodedJWT = createMockJWT("Player1");
        when(tokenValidationService.extractTokenFromHeader("Bearer valid-token"))
            .thenReturn("valid-token");
        when(tokenValidationService.validateToken("valid-token"))
            .thenReturn(decodedJWT);
        when(tokenValidationService.extractUsername(decodedJWT))
            .thenReturn("  Player1  "); // Con espacios

        // When
        Principal principal = interceptor.validateAndExtractPrincipal(accessor);

        // Then
        assertNotNull(principal);
        assertEquals("player1", principal.getName()); // Debe estar normalizado (lowercase, trimmed)
    }

    @Test
    void testCognitoPrincipal_ShouldReturnName() {
        // Given
        DecodedJWT decodedJWT = createMockJWT("player1");
        CognitoPrincipal principal = new CognitoPrincipal("player1", decodedJWT);

        // When
        String name = principal.getName();
        DecodedJWT jwt = principal.getDecodedJWT();

        // Then
        assertEquals("player1", name);
        assertSame(decodedJWT, jwt);
    }

    private DecodedJWT createMockJWT(String username) {
        Algorithm algorithm = Algorithm.HMAC256("secret");
        String token = JWT.create()
                .withSubject(username)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm);
        return JWT.decode(token);
    }
}

