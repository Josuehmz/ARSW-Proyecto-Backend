package com.arsw.balatro.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RoomInfoDto Tests")
class RoomInfoDtoTest {

    @Test
    @DisplayName("Should create RoomInfoDto with builder")
    void shouldCreateRoomInfoDtoWithBuilder() {
        // When
        RoomInfoDto roomInfo = RoomInfoDto.builder()
            .roomCode("ABC123")
            .gameId("game123")
            .hostId("player1")
            .hostName("Host Player")
            .guestId("player2")
            .guestName("Guest Player")
            .isFull(true)
            .createdAt(1234567890L)
            .status(RoomInfoDto.RoomStatus.IN_PROGRESS)
            .build();

        // Then
        assertNotNull(roomInfo);
        assertEquals("ABC123", roomInfo.getRoomCode());
        assertEquals("game123", roomInfo.getGameId());
        assertEquals("player1", roomInfo.getHostId());
        assertEquals("Host Player", roomInfo.getHostName());
        assertEquals("player2", roomInfo.getGuestId());
        assertEquals("Guest Player", roomInfo.getGuestName());
        assertTrue(roomInfo.isFull());
        assertEquals(1234567890L, roomInfo.getCreatedAt());
        assertEquals(RoomInfoDto.RoomStatus.IN_PROGRESS, roomInfo.getStatus());
    }

    @Test
    @DisplayName("Should create RoomInfoDto with all statuses")
    void shouldCreateRoomInfoDtoWithAllStatuses() {
        // When & Then
        for (RoomInfoDto.RoomStatus status : RoomInfoDto.RoomStatus.values()) {
            RoomInfoDto roomInfo = RoomInfoDto.builder()
                .roomCode("ABC123")
                .status(status)
                .build();
            
            assertNotNull(roomInfo);
            assertEquals(status, roomInfo.getStatus());
        }
    }

    @Test
    @DisplayName("Should create RoomInfoDto with default values")
    void shouldCreateRoomInfoDtoWithDefaultValues() {
        // When
        RoomInfoDto roomInfo = new RoomInfoDto();

        // Then
        assertNotNull(roomInfo);
        assertNull(roomInfo.getRoomCode());
        assertNull(roomInfo.getGameId());
        assertNull(roomInfo.getHostId());
        assertNull(roomInfo.getHostName());
        assertNull(roomInfo.getGuestId());
        assertNull(roomInfo.getGuestName());
        assertFalse(roomInfo.isFull());
        assertEquals(0L, roomInfo.getCreatedAt());
        assertNull(roomInfo.getStatus());
    }

    @Test
    @DisplayName("Should set and get all properties")
    void shouldSetAndGetAllProperties() {
        // Given
        RoomInfoDto roomInfo = new RoomInfoDto();

        // When
        roomInfo.setRoomCode("XYZ789");
        roomInfo.setGameId("game456");
        roomInfo.setHostId("player3");
        roomInfo.setHostName("Host 3");
        roomInfo.setGuestId("player4");
        roomInfo.setGuestName("Guest 4");
        roomInfo.setFull(false);
        roomInfo.setCreatedAt(9876543210L);
        roomInfo.setStatus(RoomInfoDto.RoomStatus.WAITING);

        // Then
        assertEquals("XYZ789", roomInfo.getRoomCode());
        assertEquals("game456", roomInfo.getGameId());
        assertEquals("player3", roomInfo.getHostId());
        assertEquals("Host 3", roomInfo.getHostName());
        assertEquals("player4", roomInfo.getGuestId());
        assertEquals("Guest 4", roomInfo.getGuestName());
        assertFalse(roomInfo.isFull());
        assertEquals(9876543210L, roomInfo.getCreatedAt());
        assertEquals(RoomInfoDto.RoomStatus.WAITING, roomInfo.getStatus());
    }

    @Test
    @DisplayName("Should create RoomInfoDto with AllArgsConstructor")
    void shouldCreateRoomInfoDtoWithAllArgsConstructor() {
        // When
        RoomInfoDto roomInfo = new RoomInfoDto(
            "ABC123", "game123", "player1", "Host Player", 
            "player2", "Guest Player", true, 1234567890L, 
            RoomInfoDto.RoomStatus.IN_PROGRESS
        );

        // Then
        assertNotNull(roomInfo);
        assertEquals("ABC123", roomInfo.getRoomCode());
        assertEquals("game123", roomInfo.getGameId());
        assertEquals("player1", roomInfo.getHostId());
        assertEquals("Host Player", roomInfo.getHostName());
        assertEquals("player2", roomInfo.getGuestId());
        assertEquals("Guest Player", roomInfo.getGuestName());
        assertTrue(roomInfo.isFull());
        assertEquals(1234567890L, roomInfo.getCreatedAt());
        assertEquals(RoomInfoDto.RoomStatus.IN_PROGRESS, roomInfo.getStatus());
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        // Given
        RoomInfoDto room1 = RoomInfoDto.builder()
            .roomCode("ABC123")
            .gameId("game123")
            .hostId("player1")
            .guestId("player2")
            .isFull(true)
            .status(RoomInfoDto.RoomStatus.IN_PROGRESS)
            .build();
        
        RoomInfoDto room2 = RoomInfoDto.builder()
            .roomCode("ABC123")
            .gameId("game123")
            .hostId("player1")
            .guestId("player2")
            .isFull(true)
            .status(RoomInfoDto.RoomStatus.IN_PROGRESS)
            .build();
        
        RoomInfoDto room3 = RoomInfoDto.builder()
            .roomCode("XYZ789")
            .gameId("game456")
            .hostId("player3")
            .guestId("player4")
            .isFull(false)
            .status(RoomInfoDto.RoomStatus.WAITING)
            .build();

        // Then
        assertEquals(room1, room2);
        assertNotEquals(room1, room3);
        assertNotEquals(room1, null);
        assertNotEquals(room1, "not a room");
    }

    @Test
    @DisplayName("Should implement hashCode correctly")
    void shouldImplementHashCodeCorrectly() {
        // Given
        RoomInfoDto room1 = RoomInfoDto.builder()
            .roomCode("ABC123")
            .gameId("game123")
            .hostId("player1")
            .guestId("player2")
            .isFull(true)
            .status(RoomInfoDto.RoomStatus.IN_PROGRESS)
            .build();
        
        RoomInfoDto room2 = RoomInfoDto.builder()
            .roomCode("ABC123")
            .gameId("game123")
            .hostId("player1")
            .guestId("player2")
            .isFull(true)
            .status(RoomInfoDto.RoomStatus.IN_PROGRESS)
            .build();

        // Then
        assertEquals(room1.hashCode(), room2.hashCode());
    }

    @Test
    @DisplayName("Should implement toString")
    void shouldImplementToString() {
        // Given
        RoomInfoDto roomInfo = RoomInfoDto.builder()
            .roomCode("ABC123")
            .gameId("game123")
            .hostId("player1")
            .guestId("player2")
            .isFull(true)
            .status(RoomInfoDto.RoomStatus.IN_PROGRESS)
            .build();

        // When
        String toString = roomInfo.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("ABC123"));
        assertTrue(toString.contains("game123"));
    }
}




