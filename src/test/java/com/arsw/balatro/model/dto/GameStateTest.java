package com.arsw.balatro.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GameState Tests")
class GameStateTest {

    @Test
    @DisplayName("Should create GameState with builder")
    void shouldCreateGameStateWithBuilder() {
        // When
        GameState gameState = GameState.builder()
            .gameId("game123")
            .player1Id("player1")
            .player2Id("player2")
            .createdAt(1234567890L)
            .lastUpdate(1234567890L)
            .build();

        // Then
        assertNotNull(gameState);
        assertEquals("game123", gameState.getGameId());
        assertEquals("player1", gameState.getPlayer1Id());
        assertEquals("player2", gameState.getPlayer2Id());
        assertEquals(1234567890L, gameState.getCreatedAt());
        assertEquals(1234567890L, gameState.getLastUpdate());
    }

    @Test
    @DisplayName("Should create GameState with default constructor")
    void shouldCreateGameStateWithDefaultConstructor() {
        // When
        GameState gameState = new GameState();

        // Then
        assertNotNull(gameState);
        assertNull(gameState.getGameId());
        assertNull(gameState.getPlayer1Id());
        assertNull(gameState.getPlayer2Id());
        assertEquals(0L, gameState.getCreatedAt());
        assertEquals(0L, gameState.getLastUpdate());
    }

    @Test
    @DisplayName("Should set and get all properties")
    void shouldSetAndGetAllProperties() {
        // Given
        GameState gameState = new GameState();

        // When
        gameState.setGameId("game456");
        gameState.setPlayer1Id("player3");
        gameState.setPlayer2Id("player4");
        gameState.setCreatedAt(9876543210L);
        gameState.setLastUpdate(9876543210L);

        // Then
        assertEquals("game456", gameState.getGameId());
        assertEquals("player3", gameState.getPlayer1Id());
        assertEquals("player4", gameState.getPlayer2Id());
        assertEquals(9876543210L, gameState.getCreatedAt());
        assertEquals(9876543210L, gameState.getLastUpdate());
    }

    @Test
    @DisplayName("Should create GameState with AllArgsConstructor")
    void shouldCreateGameStateWithAllArgsConstructor() {
        // When
        GameState gameState = new GameState(
            "game123", "player1", "player2", 1234567890L, 1234567890L
        );

        // Then
        assertNotNull(gameState);
        assertEquals("game123", gameState.getGameId());
        assertEquals("player1", gameState.getPlayer1Id());
        assertEquals("player2", gameState.getPlayer2Id());
        assertEquals(1234567890L, gameState.getCreatedAt());
        assertEquals(1234567890L, gameState.getLastUpdate());
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        // Given
        GameState state1 = GameState.builder()
            .gameId("game123")
            .player1Id("player1")
            .player2Id("player2")
            .createdAt(1234567890L)
            .lastUpdate(1234567890L)
            .build();
        
        GameState state2 = GameState.builder()
            .gameId("game123")
            .player1Id("player1")
            .player2Id("player2")
            .createdAt(1234567890L)
            .lastUpdate(1234567890L)
            .build();
        
        GameState state3 = GameState.builder()
            .gameId("game456")
            .player1Id("player3")
            .player2Id("player4")
            .createdAt(9876543210L)
            .lastUpdate(9876543210L)
            .build();

        // Then
        assertEquals(state1, state2);
        assertNotEquals(state1, state3);
        assertNotEquals(state1, null);
        assertNotEquals(state1, "not a state");
    }

    @Test
    @DisplayName("Should implement hashCode correctly")
    void shouldImplementHashCodeCorrectly() {
        // Given
        GameState state1 = GameState.builder()
            .gameId("game123")
            .player1Id("player1")
            .player2Id("player2")
            .createdAt(1234567890L)
            .lastUpdate(1234567890L)
            .build();
        
        GameState state2 = GameState.builder()
            .gameId("game123")
            .player1Id("player1")
            .player2Id("player2")
            .createdAt(1234567890L)
            .lastUpdate(1234567890L)
            .build();

        // Then
        assertEquals(state1.hashCode(), state2.hashCode());
    }

    @Test
    @DisplayName("Should implement toString")
    void shouldImplementToString() {
        // Given
        GameState gameState = GameState.builder()
            .gameId("game123")
            .player1Id("player1")
            .player2Id("player2")
            .build();

        // When
        String toString = gameState.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("game123"));
    }
}




