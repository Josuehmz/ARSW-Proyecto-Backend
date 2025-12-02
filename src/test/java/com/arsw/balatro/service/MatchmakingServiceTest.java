package com.arsw.balatro.service;

import com.arsw.balatro.model.dto.QueueStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchmakingService Tests")
class MatchmakingServiceTest {

    private MatchmakingService matchmakingService;
    private GameService gameService;
    private SessionService sessionService;
    
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        gameService = new GameService();
        sessionService = new SessionService();
        matchmakingService = new MatchmakingService(gameService, sessionService, messagingTemplate);
    }

    @Test
    @DisplayName("Should add player to queue successfully")
    void shouldAddPlayerToQueueSuccessfully() {
        // Given
        String playerId = "player1";
        String sessionId = "session1";
        sessionService.registerSession(playerId, sessionId);

        // When
        QueueStatusDto status = matchmakingService.addToQueue(playerId);

        // Then
        assertNotNull(status);
        assertTrue(status.isInQueue());
        assertEquals(1, status.getPlayersInQueue());
        assertNotNull(status.getQueuePosition());
    }

    @Test
    @DisplayName("Should not add null playerId to queue")
    void shouldNotAddNullPlayerIdToQueue() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            matchmakingService.addToQueue(null);
        });
    }

    @Test
    @DisplayName("Should return same status if player already in queue")
    void shouldReturnSameStatusIfPlayerAlreadyInQueue() {
        // Given
        String playerId = "player1";
        String sessionId = "session1";
        sessionService.registerSession(playerId, sessionId);
        matchmakingService.addToQueue(playerId);

        // When
        QueueStatusDto status = matchmakingService.addToQueue(playerId);

        // Then
        assertNotNull(status);
        assertTrue(status.isInQueue());
    }

    @Test
    @DisplayName("Should normalize playerId when adding to queue")
    void shouldNormalizePlayerIdWhenAddingToQueue() {
        // Given
        String playerId = "  Player1  ";
        String normalizedPlayerId = "player1";
        String sessionId = "session1";
        sessionService.registerSession(normalizedPlayerId, sessionId);

        // When
        QueueStatusDto status = matchmakingService.addToQueue(playerId);

        // Then
        assertNotNull(status);
        assertTrue(status.isInQueue());
        assertEquals(normalizedPlayerId, status.getPlayerId());
    }

    @Test
    @DisplayName("Should remove player from queue")
    void shouldRemovePlayerFromQueue() {
        // Given
        String playerId = "player1";
        String sessionId = "session1";
        sessionService.registerSession(playerId, sessionId);
        matchmakingService.addToQueue(playerId);

        // When
        matchmakingService.removeFromQueue(playerId);

        // Then
        QueueStatusDto status = matchmakingService.getQueueStatus(playerId);
        assertFalse(status.isInQueue());
    }

    @Test
    @DisplayName("Should not throw exception when removing non-existent player")
    void shouldNotThrowExceptionWhenRemovingNonExistentPlayer() {
        // When & Then
        assertDoesNotThrow(() -> {
            matchmakingService.removeFromQueue("nonexistent");
        });
    }

    @Test
    @DisplayName("Should normalize playerId when removing from queue")
    void shouldNormalizePlayerIdWhenRemovingFromQueue() {
        // Given
        String playerId = "player1";
        String sessionId = "session1";
        sessionService.registerSession(playerId, sessionId);
        matchmakingService.addToQueue(playerId);

        // When
        matchmakingService.removeFromQueue("  PLAYER1  ");

        // Then
        QueueStatusDto status = matchmakingService.getQueueStatus(playerId);
        assertFalse(status.isInQueue());
    }

    @Test
    @DisplayName("Should get queue status for player in queue")
    void shouldGetQueueStatusForPlayerInQueue() {
        // Given
        String playerId = "player1";
        String sessionId = "session1";
        sessionService.registerSession(playerId, sessionId);
        matchmakingService.addToQueue(playerId);

        // When
        QueueStatusDto status = matchmakingService.getQueueStatus(playerId);

        // Then
        assertNotNull(status);
        assertTrue(status.isInQueue());
        assertNotNull(status.getQueuePosition());
        assertTrue(status.getPlayersInQueue() > 0);
        assertNotNull(status.getEstimatedWaitTime());
    }

    @Test
    @DisplayName("Should get queue status for player not in queue")
    void shouldGetQueueStatusForPlayerNotInQueue() {
        // Given
        String playerId = "player1";

        // When
        QueueStatusDto status = matchmakingService.getQueueStatus(playerId);

        // Then
        assertNotNull(status);
        assertFalse(status.isInQueue());
        assertNull(status.getQueuePosition());
    }

    @Test
    @DisplayName("Should create match when two players in queue")
    void shouldCreateMatchWhenTwoPlayersInQueue() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";
        String session1Id = "session1";
        String session2Id = "session2";
        
        sessionService.registerSession(player1Id, session1Id);
        sessionService.registerSession(player2Id, session2Id);

        // When
        matchmakingService.addToQueue(player1Id);
        matchmakingService.addToQueue(player2Id);

        // Then
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
            anyString(),
            eq("/queue/matchmaking"),
            any()
        );
        
        // Verify game was created
        String gameId1 = gameService.getActiveGameIdForPlayer(player1Id);
        String gameId2 = gameService.getActiveGameIdForPlayer(player2Id);
        assertNotNull(gameId1);
        assertNotNull(gameId2);
        assertEquals(gameId1, gameId2);
    }

    @Test
    @DisplayName("Should not create match with only one player")
    void shouldNotCreateMatchWithOnlyOnePlayer() {
        // Given
        String playerId = "player1";
        String sessionId = "session1";
        sessionService.registerSession(playerId, sessionId);

        // When
        matchmakingService.addToQueue(playerId);

        // Then
        verify(messagingTemplate, never()).convertAndSendToUser(
            eq(playerId),
            eq("/queue/matchmaking"),
            any()
        );
        
        String gameId = gameService.getActiveGameIdForPlayer(playerId);
        assertNull(gameId);
    }

    @Test
    @DisplayName("Should handle multiple matches")
    void shouldHandleMultipleMatches() {
        // Given
        String player1Id = "player1";
        String player2Id = "player2";
        String player3Id = "player3";
        String player4Id = "player4";
        
        sessionService.registerSession(player1Id, "session1");
        sessionService.registerSession(player2Id, "session2");
        sessionService.registerSession(player3Id, "session3");
        sessionService.registerSession(player4Id, "session4");

        // When
        matchmakingService.addToQueue(player1Id);
        matchmakingService.addToQueue(player2Id);
        matchmakingService.addToQueue(player3Id);
        matchmakingService.addToQueue(player4Id);

        // Then
        verify(messagingTemplate, atLeast(4)).convertAndSendToUser(
            anyString(),
            eq("/queue/matchmaking"),
            any()
        );
        
        // Verify both games were created
        String gameId1 = gameService.getActiveGameIdForPlayer(player1Id);
        String gameId2 = gameService.getActiveGameIdForPlayer(player3Id);
        assertNotNull(gameId1);
        assertNotNull(gameId2);
        assertNotEquals(gameId1, gameId2);
    }

    @Test
    @DisplayName("Should estimate wait time correctly")
    void shouldEstimateWaitTimeCorrectly() {
        // Given
        String playerId = "player1";
        String sessionId = "session1";
        sessionService.registerSession(playerId, sessionId);

        // When
        QueueStatusDto status1 = matchmakingService.getQueueStatus(playerId);
        matchmakingService.addToQueue(playerId);
        QueueStatusDto status2 = matchmakingService.getQueueStatus(playerId);

        // Then
        assertNotNull(status1.getEstimatedWaitTime());
        assertNotNull(status2.getEstimatedWaitTime());
        assertTrue(status2.getEstimatedWaitTime() > 0);
    }

    @Test
    @DisplayName("Should handle null playerId in getQueueStatus")
    void shouldHandleNullPlayerIdInGetQueueStatus() {
        // When
        QueueStatusDto status = matchmakingService.getQueueStatus(null);

        // Then
        assertNotNull(status);
        assertFalse(status.isInQueue());
        assertNull(status.getQueuePosition());
    }

    @Test
    @DisplayName("Should handle null playerId in removeFromQueue")
    void shouldHandleNullPlayerIdInRemoveFromQueue() {
        // When & Then
        assertDoesNotThrow(() -> {
            matchmakingService.removeFromQueue(null);
        });
    }
}


