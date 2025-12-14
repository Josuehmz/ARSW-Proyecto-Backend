package com.arsw.balatro.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

class UserHandshakeHandlerTest {

    private UserHandshakeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserHandshakeHandler();
    }

    @Test
    void testDetermineUser_ShouldCreatePrincipal() {
        // Given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();
        
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/ws"));

        // When
        Principal principal = handler.determineUser(request, wsHandler, attributes);

        // Then
        assertNotNull(principal);
        assertNotNull(principal.getName());
        assertFalse(principal.getName().isEmpty());
    }

    @Test
    void testDetermineUser_ShouldGenerateUniqueIds() {
        // Given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();
        
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/ws"));

        // When
        Principal principal1 = handler.determineUser(request, wsHandler, attributes);
        Principal principal2 = handler.determineUser(request, wsHandler, attributes);

        // Then
        assertNotNull(principal1);
        assertNotNull(principal2);
        // Los IDs deberían ser diferentes (UUID aleatorios)
        assertNotEquals(principal1.getName(), principal2.getName());
    }

    @Test
    void testDetermineUser_ShouldReturnValidUUID() {
        // Given
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();
        
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8080/ws"));

        // When
        Principal principal = handler.determineUser(request, wsHandler, attributes);

        // Then
        assertNotNull(principal);
        String name = principal.getName();
        // Verificar que es un UUID válido (formato: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
        assertTrue(name.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
    }
}






