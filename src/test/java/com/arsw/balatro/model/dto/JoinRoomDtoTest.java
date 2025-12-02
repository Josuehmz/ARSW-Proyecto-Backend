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
}



