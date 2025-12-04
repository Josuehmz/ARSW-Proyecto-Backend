package com.arsw.balatro.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionService Tests")
class SessionServiceTest {

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService();
    }

    @Test
    @DisplayName("Should register session successfully")
    void shouldRegisterSessionSuccessfully() {
        // Given
        String playerId = "player1";
        String sessionId = "session123";

        // When
        sessionService.registerSession(playerId, sessionId);

        // Then
        assertTrue(sessionService.hasSession(playerId));
        assertEquals(sessionId, sessionService.getSessionId(playerId));
        assertEquals(playerId.toLowerCase(), sessionService.getPlayerId(sessionId));
    }

    @Test
    @DisplayName("Should not register session with null playerId")
    void shouldNotRegisterSessionWithNullPlayerId() {
        // Given
        String playerId = null;
        String sessionId = "session123";

        // When
        sessionService.registerSession(playerId, sessionId);

        // Then
        assertFalse(sessionService.hasSession(playerId));
        assertNull(sessionService.getSessionId(playerId));
    }

    @Test
    @DisplayName("Should normalize playerId when registering")
    void shouldNormalizePlayerIdWhenRegistering() {
        // Given
        String playerId = "  Player1  ";
        String normalizedPlayerId = "player1";
        String sessionId = "session123";

        // When
        sessionService.registerSession(playerId, sessionId);

        // Then
        assertTrue(sessionService.hasSession(normalizedPlayerId));
        assertTrue(sessionService.hasSession("PLAYER1"));
        assertTrue(sessionService.hasSession("  player1  "));
        assertEquals(sessionId, sessionService.getSessionId(normalizedPlayerId));
    }

    @Test
    @DisplayName("Should update session when player reconnects")
    void shouldUpdateSessionWhenPlayerReconnects() {
        // Given
        String playerId = "player1";
        String oldSessionId = "session123";
        String newSessionId = "session456";

        // When
        sessionService.registerSession(playerId, oldSessionId);
        sessionService.registerSession(playerId, newSessionId);

        // Then
        assertEquals(newSessionId, sessionService.getSessionId(playerId));
        assertNull(sessionService.getPlayerId(oldSessionId));
        assertEquals(playerId.toLowerCase(), sessionService.getPlayerId(newSessionId));
    }

    @Test
    @DisplayName("Should return null for non-existent player")
    void shouldReturnNullForNonExistentPlayer() {
        // Given
        String playerId = "nonexistent";

        // When
        String sessionId = sessionService.getSessionId(playerId);

        // Then
        assertNull(sessionId);
        assertFalse(sessionService.hasSession(playerId));
    }

    @Test
    @DisplayName("Should remove session by playerId")
    void shouldRemoveSessionByPlayerId() {
        // Given
        String playerId = "player1";
        String sessionId = "session123";
        sessionService.registerSession(playerId, sessionId);

        // When
        sessionService.removeByPlayerId(playerId);

        // Then
        assertFalse(sessionService.hasSession(playerId));
        assertNull(sessionService.getSessionId(playerId));
        assertNull(sessionService.getPlayerId(sessionId));
    }

    @Test
    @DisplayName("Should not throw exception when removing non-existent player")
    void shouldNotThrowExceptionWhenRemovingNonExistentPlayer() {
        // Given
        String playerId = "nonexistent";

        // When & Then
        assertDoesNotThrow(() -> sessionService.removeByPlayerId(playerId));
    }

    @Test
    @DisplayName("Should remove session by sessionId")
    void shouldRemoveSessionBySessionId() {
        // Given
        String playerId = "player1";
        String sessionId = "session123";
        sessionService.registerSession(playerId, sessionId);

        // When
        sessionService.removeBySessionId(sessionId);

        // Then
        assertFalse(sessionService.hasSession(playerId));
        assertNull(sessionService.getSessionId(playerId));
        assertNull(sessionService.getPlayerId(sessionId));
    }

    @Test
    @DisplayName("Should return correct active session count")
    void shouldReturnCorrectActiveSessionCount() {
        // Given
        sessionService.registerSession("player1", "session1");
        sessionService.registerSession("player2", "session2");
        sessionService.registerSession("player3", "session3");

        // When
        int count = sessionService.getActiveSessionCount();

        // Then
        assertEquals(3, count);
    }

    @Test
    @DisplayName("Should return zero for empty session count")
    void shouldReturnZeroForEmptySessionCount() {
        // When
        int count = sessionService.getActiveSessionCount();

        // Then
        assertEquals(0, count);
    }

    @Test
    @DisplayName("Should handle case-insensitive playerIds")
    void shouldHandleCaseInsensitivePlayerIds() {
        // Given
        String playerId1 = "Player1";
        String playerId2 = "PLAYER1";
        String playerId3 = "player1";
        String sessionId = "session123";

        // When
        sessionService.registerSession(playerId1, sessionId);

        // Then
        assertTrue(sessionService.hasSession(playerId2));
        assertTrue(sessionService.hasSession(playerId3));
        assertEquals(sessionId, sessionService.getSessionId(playerId2));
        assertEquals(sessionId, sessionService.getSessionId(playerId3));
    }

    @Test
    @DisplayName("Should return debug info")
    void shouldReturnDebugInfo() {
        // Given
        sessionService.registerSession("player1", "session1");
        sessionService.registerSession("player2", "session2");

        // When
        String debugInfo = sessionService.getDebugInfo();

        // Then
        assertNotNull(debugInfo);
        assertTrue(debugInfo.contains("Active sessions"));
        assertTrue(debugInfo.contains("player1"));
        assertTrue(debugInfo.contains("player2"));
    }
}





