package com.arsw.balatro.service;

import com.arsw.balatro.model.dto.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GameService Tests")
class GameServiceTest {

    private GameService gameService;

    @BeforeEach
    void setUp() {
        gameService = new GameService();
    }

    @Test
    @DisplayName("Should create game successfully")
    void shouldCreateGameSuccessfully() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";

        // When
        String gameId = gameService.createGame(player1Id, player2Id);

        // Then
        assertNotNull(gameId);
        assertFalse(gameId.isEmpty());
        
        GameState gameState = gameService.getGameState(gameId);
        assertNotNull(gameState);
        assertEquals("player1", gameState.getPlayer1Id());
        assertEquals("player2", gameState.getPlayer2Id());
        assertEquals(gameId, gameState.getGameId());
    }

    @Test
    @DisplayName("Should not create game with null player1Id")
    void shouldNotCreateGameWithNullPlayer1Id() {
        // Given
        String player1Id = null;
        String player2Id = "player2";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            gameService.createGame(player1Id, player2Id);
        });
    }

    @Test
    @DisplayName("Should not create game with null player2Id")
    void shouldNotCreateGameWithNullPlayer2Id() {
        // Given
        String player1Id = "player1";
        String player2Id = null;

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            gameService.createGame(player1Id, player2Id);
        });
    }

    @Test
    @DisplayName("Should normalize playerIds when creating game")
    void shouldNormalizePlayerIdsWhenCreatingGame() {
        // Given
        String player1Id = "  Player1  ";
        String player2Id = "PLAYER2";

        // When
        String gameId = gameService.createGame(player1Id, player2Id);

        // Then
        GameState gameState = gameService.getGameState(gameId);
        assertEquals("player1", gameState.getPlayer1Id());
        assertEquals("player2", gameState.getPlayer2Id());
    }

    @Test
    @DisplayName("Should get active game ID for player")
    void shouldGetActiveGameIdForPlayer() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";
        String gameId = gameService.createGame(player1Id, player2Id);

        // When
        String foundGameId1 = gameService.getActiveGameIdForPlayer(player1Id);
        String foundGameId2 = gameService.getActiveGameIdForPlayer(player2Id);

        // Then
        assertEquals(gameId, foundGameId1);
        assertEquals(gameId, foundGameId2);
    }

    @Test
    @DisplayName("Should return null for player not in game")
    void shouldReturnNullForPlayerNotInGame() {
        // Given
        String playerId = "nonexistent";

        // When
        String gameId = gameService.getActiveGameIdForPlayer(playerId);

        // Then
        assertNull(gameId);
    }

    @Test
    @DisplayName("Should normalize playerId when getting active game")
    void shouldNormalizePlayerIdWhenGettingActiveGame() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";
        String gameId = gameService.createGame(player1Id, player2Id);

        // When
        String foundGameId = gameService.getActiveGameIdForPlayer("  PLAYER1  ");

        // Then
        assertEquals(gameId, foundGameId);
    }

    @Test
    @DisplayName("Should check if player is in game")
    void shouldCheckIfPlayerIsInGame() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";
        String gameId = gameService.createGame(player1Id, player2Id);

        // When & Then
        assertTrue(gameService.isPlayerInGame(gameId, player1Id));
        assertTrue(gameService.isPlayerInGame(gameId, player2Id));
        assertFalse(gameService.isPlayerInGame(gameId, "player3"));
    }

    @Test
    @DisplayName("Should return false for non-existent game")
    void shouldReturnFalseForNonExistentGame() {
        // Given
        String gameId = "nonexistent";
        String playerId = "player1";

        // When
        boolean isInGame = gameService.isPlayerInGame(gameId, playerId);

        // Then
        assertFalse(isInGame);
    }

    @Test
    @DisplayName("Should return false for null playerId")
    void shouldReturnFalseForNullPlayerId() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";
        String gameId = gameService.createGame(player1Id, player2Id);

        // When
        boolean isInGame = gameService.isPlayerInGame(gameId, null);

        // Then
        assertFalse(isInGame);
    }

    @Test
    @DisplayName("Should get opponent ID")
    void shouldGetOpponentId() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";
        String gameId = gameService.createGame(player1Id, player2Id);

        // When
        String opponent1 = gameService.getOpponentId(gameId, player1Id);
        String opponent2 = gameService.getOpponentId(gameId, player2Id);

        // Then
        assertEquals("player2", opponent1);
        assertEquals("player1", opponent2);
    }

    @Test
    @DisplayName("Should not get opponent ID for player not in game")
    void shouldNotGetOpponentIdForPlayerNotInGame() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";
        String gameId = gameService.createGame(player1Id, player2Id);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            gameService.getOpponentId(gameId, "player3");
        });
    }

    @Test
    @DisplayName("Should not get opponent ID with null playerId")
    void shouldNotGetOpponentIdWithNullPlayerId() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";
        String gameId = gameService.createGame(player1Id, player2Id);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            gameService.getOpponentId(gameId, null);
        });
    }

    @Test
    @DisplayName("Should throw exception when getting non-existent game state")
    void shouldThrowExceptionWhenGettingNonExistentGameState() {
        // Given
        String gameId = "nonexistent";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            gameService.getGameState(gameId);
        });
    }

    @Test
    @DisplayName("Should update game activity")
    void shouldUpdateGameActivity() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";
        String gameId = gameService.createGame(player1Id, player2Id);
        GameState gameState = gameService.getGameState(gameId);
        long originalUpdate = gameState.getLastUpdate();

        // When
        try {
            Thread.sleep(10); // Small delay to ensure timestamp difference
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        gameService.updateGameActivity(gameId);

        // Then
        GameState updatedState = gameService.getGameState(gameId);
        assertTrue(updatedState.getLastUpdate() >= originalUpdate);
    }

    @Test
    @DisplayName("Should not throw exception when updating non-existent game")
    void shouldNotThrowExceptionWhenUpdatingNonExistentGame() {
        // Given
        String gameId = "nonexistent";

        // When & Then
        assertDoesNotThrow(() -> {
            gameService.updateGameActivity(gameId);
        });
    }

    @Test
    @DisplayName("Should cleanup game")
    void shouldCleanupGame() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";
        String gameId = gameService.createGame(player1Id, player2Id);

        // When
        gameService.cleanupGame(gameId);

        // Then
        assertThrows(IllegalArgumentException.class, () -> {
            gameService.getGameState(gameId);
        });
        assertNull(gameService.getActiveGameIdForPlayer(player1Id));
        assertNull(gameService.getActiveGameIdForPlayer(player2Id));
    }

    @Test
    @DisplayName("Should not throw exception when cleaning up non-existent game")
    void shouldNotThrowExceptionWhenCleaningUpNonExistentGame() {
        // Given
        String gameId = "nonexistent";

        // When & Then
        assertDoesNotThrow(() -> {
            gameService.cleanupGame(gameId);
        });
    }

    @Test
    @DisplayName("Should handle multiple games")
    void shouldHandleMultipleGames() {
        // Given
        String gameId1 = gameService.createGame("player1", "player2");
        String gameId2 = gameService.createGame("player3", "player4");

        // When & Then
        assertNotNull(gameService.getGameState(gameId1));
        assertNotNull(gameService.getGameState(gameId2));
        assertEquals(gameId1, gameService.getActiveGameIdForPlayer("player1"));
        assertEquals(gameId2, gameService.getActiveGameIdForPlayer("player3"));
    }
}




