package com.arsw.balatro.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketHandshakeInterceptorTest {

    private WebSocketHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketHandshakeInterceptor();
    }

    @Test
    void testBeforeHandshake_ShouldReturnTrue() throws Exception {
        // Given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();
        
        when(request.getRemoteAddress()).thenReturn(null);
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/ws"));

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
}


