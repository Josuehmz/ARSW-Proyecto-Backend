package com.arsw.balatro.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JoinRoomDto Tests")
class JoinRoomDtoTest {

    @Test
    @DisplayName("Should create JoinRoomDto with builder")
    void shouldCreateJoinRoomDtoWithBuilder() {
        // When
        JoinRoomDto dto = JoinRoomDto.builder()
            .playerId("player1")
            .playerName("Player 1")
            .roomCode("ABC123")
            .build();

        // Then
        assertNotNull(dto);
        assertEquals("player1", dto.getPlayerId());
        assertEquals("Player 1", dto.getPlayerName());
        assertEquals("ABC123", dto.getRoomCode());
    }

    @Test
    @DisplayName("Should create JoinRoomDto with default constructor")
    void shouldCreateJoinRoomDtoWithDefaultConstructor() {
        // When
        JoinRoomDto dto = new JoinRoomDto();

        // Then
        assertNotNull(dto);
        assertNull(dto.getPlayerId());
        assertNull(dto.getPlayerName());
        assertNull(dto.getRoomCode());
    }

    @Test
    @DisplayName("Should set and get all properties")
    void shouldSetAndGetAllProperties() {
        // Given
        JoinRoomDto dto = new JoinRoomDto();

        // When
        dto.setPlayerId("player2");
        dto.setPlayerName("Player 2");
        dto.setRoomCode("XYZ789");

        // Then
        assertEquals("player2", dto.getPlayerId());
        assertEquals("Player 2", dto.getPlayerName());
        assertEquals("XYZ789", dto.getRoomCode());
    }

    @Test
    @DisplayName("Should create JoinRoomDto with AllArgsConstructor")
    void shouldCreateJoinRoomDtoWithAllArgsConstructor() {
        // When
        JoinRoomDto dto = new JoinRoomDto("player1", "Player 1", "ABC123");

        // Then
        assertNotNull(dto);
        assertEquals("player1", dto.getPlayerId());
        assertEquals("Player 1", dto.getPlayerName());
        assertEquals("ABC123", dto.getRoomCode());
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        // Given
        JoinRoomDto dto1 = JoinRoomDto.builder()
            .playerId("player1")
            .playerName("Player 1")
            .roomCode("ABC123")
            .build();
        
        JoinRoomDto dto2 = JoinRoomDto.builder()
            .playerId("player1")
            .playerName("Player 1")
            .roomCode("ABC123")
            .build();
        
        JoinRoomDto dto3 = JoinRoomDto.builder()
            .playerId("player2")
            .playerName("Player 2")
            .roomCode("XYZ789")
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
        JoinRoomDto dto1 = JoinRoomDto.builder()
            .playerId("player1")
            .playerName("Player 1")
            .roomCode("ABC123")
            .build();
        
        JoinRoomDto dto2 = JoinRoomDto.builder()
            .playerId("player1")
            .playerName("Player 1")
            .roomCode("ABC123")
            .build();

        // Then
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    @DisplayName("Should implement toString")
    void shouldImplementToString() {
        // Given
        JoinRoomDto dto = JoinRoomDto.builder()
            .playerId("player1")
            .playerName("Player 1")
            .roomCode("ABC123")
            .build();

        // When
        String toString = dto.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("player1"));
        assertTrue(toString.contains("Player 1"));
        assertTrue(toString.contains("ABC123"));
    }
}




