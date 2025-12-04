package com.arsw.balatro.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MatchFoundDto Tests")
class MatchFoundDtoTest {

    @Test
    @DisplayName("Should create MatchFoundDto with builder")
    void shouldCreateMatchFoundDtoWithBuilder() {
        // When
        MatchFoundDto dto = MatchFoundDto.builder()
            .gameId("game123")
            .player1Id("player1")
            .player1Name("Player 1")
            .player2Id("player2")
            .player2Name("Player 2")
            .startTime(1234567890L)
            .build();

        // Then
        assertNotNull(dto);
        assertEquals("game123", dto.getGameId());
        assertEquals("player1", dto.getPlayer1Id());
        assertEquals("Player 1", dto.getPlayer1Name());
        assertEquals("player2", dto.getPlayer2Id());
        assertEquals("Player 2", dto.getPlayer2Name());
        assertEquals(1234567890L, dto.getStartTime());
    }

    @Test
    @DisplayName("Should create MatchFoundDto with default constructor")
    void shouldCreateMatchFoundDtoWithDefaultConstructor() {
        // When
        MatchFoundDto dto = new MatchFoundDto();

        // Then
        assertNotNull(dto);
        assertNull(dto.getGameId());
        assertNull(dto.getPlayer1Id());
        assertNull(dto.getPlayer1Name());
        assertNull(dto.getPlayer2Id());
        assertNull(dto.getPlayer2Name());
        assertNull(dto.getStartTime());
    }

    @Test
    @DisplayName("Should create MatchFoundDto with AllArgsConstructor")
    void shouldCreateMatchFoundDtoWithAllArgsConstructor() {
        // When
        MatchFoundDto dto = new MatchFoundDto(
            "game123", "player1", "Player 1", "player2", "Player 2", 1234567890L
        );

        // Then
        assertNotNull(dto);
        assertEquals("game123", dto.getGameId());
        assertEquals("player1", dto.getPlayer1Id());
        assertEquals("Player 1", dto.getPlayer1Name());
        assertEquals("player2", dto.getPlayer2Id());
        assertEquals("Player 2", dto.getPlayer2Name());
        assertEquals(1234567890L, dto.getStartTime());
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        // Given
        MatchFoundDto dto1 = MatchFoundDto.builder()
            .gameId("game123")
            .player1Id("player1")
            .player1Name("Player 1")
            .player2Id("player2")
            .player2Name("Player 2")
            .startTime(1234567890L)
            .build();
        
        MatchFoundDto dto2 = MatchFoundDto.builder()
            .gameId("game123")
            .player1Id("player1")
            .player1Name("Player 1")
            .player2Id("player2")
            .player2Name("Player 2")
            .startTime(1234567890L)
            .build();
        
        MatchFoundDto dto3 = MatchFoundDto.builder()
            .gameId("game456")
            .player1Id("player3")
            .player1Name("Player 3")
            .player2Id("player4")
            .player2Name("Player 4")
            .startTime(9876543210L)
            .build();

        // Then
        assertEquals(dto1, dto2);
        assertNotEquals(dto1, dto3);
        assertNotEquals(dto1, null);
        assertNotEquals(dto1, "not a dto");
    }

    @Test
    @DisplayName("Should implement hashCode correctly")
    void shouldImplementHashCodeCorrectly() {
        // Given
        MatchFoundDto dto1 = MatchFoundDto.builder()
            .gameId("game123")
            .player1Id("player1")
            .player2Id("player2")
            .startTime(1234567890L)
            .build();
        
        MatchFoundDto dto2 = MatchFoundDto.builder()
            .gameId("game123")
            .player1Id("player1")
            .player2Id("player2")
            .startTime(1234567890L)
            .build();

        // Then
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    @DisplayName("Should implement toString")
    void shouldImplementToString() {
        // Given
        MatchFoundDto dto = MatchFoundDto.builder()
            .gameId("game123")
            .player1Id("player1")
            .player2Id("player2")
            .build();

        // When
        String toString = dto.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("game123"));
    }

    @Test
    @DisplayName("Should set and get all properties")
    void shouldSetAndGetAllProperties() {
        // Given
        MatchFoundDto dto = new MatchFoundDto();

        // When
        dto.setGameId("game456");
        dto.setPlayer1Id("player3");
        dto.setPlayer1Name("Player 3");
        dto.setPlayer2Id("player4");
        dto.setPlayer2Name("Player 4");
        dto.setStartTime(9876543210L);

        // Then
        assertEquals("game456", dto.getGameId());
        assertEquals("player3", dto.getPlayer1Id());
        assertEquals("Player 3", dto.getPlayer1Name());
        assertEquals("player4", dto.getPlayer2Id());
        assertEquals("Player 4", dto.getPlayer2Name());
        assertEquals(9876543210L, dto.getStartTime());
    }
}

