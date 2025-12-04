package com.arsw.balatro.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QueueStatusDto Tests")
class QueueStatusDtoTest {

    @Test
    @DisplayName("Should create QueueStatusDto with builder")
    void shouldCreateQueueStatusDtoWithBuilder() {
        // When
        QueueStatusDto dto = QueueStatusDto.builder()
            .playerId("player1")
            .inQueue(true)
            .queuePosition(5)
            .playersInQueue(10)
            .estimatedWaitTime(30)
            .build();

        // Then
        assertNotNull(dto);
        assertEquals("player1", dto.getPlayerId());
        assertTrue(dto.isInQueue());
        assertEquals(5, dto.getQueuePosition());
        assertEquals(10, dto.getPlayersInQueue());
        assertEquals(30, dto.getEstimatedWaitTime());
    }

    @Test
    @DisplayName("Should create QueueStatusDto with default constructor")
    void shouldCreateQueueStatusDtoWithDefaultConstructor() {
        // When
        QueueStatusDto dto = new QueueStatusDto();

        // Then
        assertNotNull(dto);
        assertNull(dto.getPlayerId());
        assertFalse(dto.isInQueue());
        assertNull(dto.getQueuePosition());
        assertNull(dto.getPlayersInQueue());
        assertNull(dto.getEstimatedWaitTime());
    }

    @Test
    @DisplayName("Should set and get all properties")
    void shouldSetAndGetAllProperties() {
        // Given
        QueueStatusDto dto = new QueueStatusDto();

        // When
        dto.setPlayerId("player2");
        dto.setInQueue(false);
        dto.setQueuePosition(null);
        dto.setPlayersInQueue(0);
        dto.setEstimatedWaitTime(0);

        // Then
        assertEquals("player2", dto.getPlayerId());
        assertFalse(dto.isInQueue());
        assertNull(dto.getQueuePosition());
        assertEquals(0, dto.getPlayersInQueue());
        assertEquals(0, dto.getEstimatedWaitTime());
    }

    @Test
    @DisplayName("Should create QueueStatusDto with AllArgsConstructor")
    void shouldCreateQueueStatusDtoWithAllArgsConstructor() {
        // When
        QueueStatusDto dto = new QueueStatusDto(
            "player1", true, 5, 30, 10
        );

        // Then
        assertNotNull(dto);
        assertEquals("player1", dto.getPlayerId());
        assertTrue(dto.isInQueue());
        assertEquals(5, dto.getQueuePosition());
        assertEquals(30, dto.getEstimatedWaitTime());
        assertEquals(10, dto.getPlayersInQueue());
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        // Given
        QueueStatusDto dto1 = QueueStatusDto.builder()
            .playerId("player1")
            .inQueue(true)
            .queuePosition(5)
            .playersInQueue(10)
            .estimatedWaitTime(30)
            .build();
        
        QueueStatusDto dto2 = QueueStatusDto.builder()
            .playerId("player1")
            .inQueue(true)
            .queuePosition(5)
            .playersInQueue(10)
            .estimatedWaitTime(30)
            .build();
        
        QueueStatusDto dto3 = QueueStatusDto.builder()
            .playerId("player2")
            .inQueue(false)
            .queuePosition(1)
            .playersInQueue(5)
            .estimatedWaitTime(15)
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
        QueueStatusDto dto1 = QueueStatusDto.builder()
            .playerId("player1")
            .inQueue(true)
            .queuePosition(5)
            .build();
        
        QueueStatusDto dto2 = QueueStatusDto.builder()
            .playerId("player1")
            .inQueue(true)
            .queuePosition(5)
            .build();

        // Then
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    @DisplayName("Should implement toString")
    void shouldImplementToString() {
        // Given
        QueueStatusDto dto = QueueStatusDto.builder()
            .playerId("player1")
            .inQueue(true)
            .queuePosition(5)
            .build();

        // When
        String toString = dto.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("player1"));
    }
}

