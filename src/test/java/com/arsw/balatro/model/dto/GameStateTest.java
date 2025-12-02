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
}


