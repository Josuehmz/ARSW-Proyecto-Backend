package com.arsw.balatro.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreateRoomDto Tests")
class CreateRoomDtoTest {

    @Test
    @DisplayName("Should create CreateRoomDto with builder")
    void shouldCreateCreateRoomDtoWithBuilder() {
        // When
        CreateRoomDto dto = CreateRoomDto.builder()
            .playerId("player1")
            .playerName("Player 1")
            .roomCode("ABC123")
            .isPrivate(true)
            .build();

        // Then
        assertNotNull(dto);
        assertEquals("player1", dto.getPlayerId());
        assertEquals("Player 1", dto.getPlayerName());
        assertEquals("ABC123", dto.getRoomCode());
        assertTrue(dto.isPrivate());
    }

    @Test
    @DisplayName("Should create CreateRoomDto with default constructor")
    void shouldCreateCreateRoomDtoWithDefaultConstructor() {
        // When
        CreateRoomDto dto = new CreateRoomDto();

        // Then
        assertNotNull(dto);
        assertNull(dto.getPlayerId());
        assertNull(dto.getPlayerName());
        assertNull(dto.getRoomCode());
        assertFalse(dto.isPrivate());
    }

    @Test
    @DisplayName("Should set and get all properties")
    void shouldSetAndGetAllProperties() {
        // Given
        CreateRoomDto dto = new CreateRoomDto();

        // When
        dto.setPlayerId("player2");
        dto.setPlayerName("Player 2");
        dto.setRoomCode("XYZ789");
        dto.setPrivate(false);

        // Then
        assertEquals("player2", dto.getPlayerId());
        assertEquals("Player 2", dto.getPlayerName());
        assertEquals("XYZ789", dto.getRoomCode());
        assertFalse(dto.isPrivate());
    }
}


