package com.arsw.balatro.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SignalingMessage Tests")
class SignalingMessageTest {

    @Test
    @DisplayName("Should create SignalingMessage with builder")
    void shouldCreateSignalingMessageWithBuilder() {
        // When
        SignalingMessage message = SignalingMessage.builder()
            .type("OFFER")
            .gameId("game123")
            .senderId("player1")
            .targetId("player2")
            .payload("sdp offer")
            .timestamp("2024-01-01T00:00:00Z")
            .build();

        // Then
        assertNotNull(message);
        assertEquals("OFFER", message.getType());
        assertEquals("game123", message.getGameId());
        assertEquals("player1", message.getSenderId());
        assertEquals("player2", message.getTargetId());
        assertEquals("sdp offer", message.getPayload());
        assertEquals("2024-01-01T00:00:00Z", message.getTimestamp());
    }

    @Test
    @DisplayName("Should create SignalingMessage with default constructor")
    void shouldCreateSignalingMessageWithDefaultConstructor() {
        // When
        SignalingMessage message = new SignalingMessage();

        // Then
        assertNotNull(message);
        assertNull(message.getType());
        assertNull(message.getGameId());
        assertNull(message.getSenderId());
        assertNull(message.getTargetId());
        assertNull(message.getPayload());
        assertNull(message.getTimestamp());
    }

    @Test
    @DisplayName("Should set and get all properties")
    void shouldSetAndGetAllProperties() {
        // Given
        SignalingMessage message = new SignalingMessage();

        // When
        message.setType("ANSWER");
        message.setGameId("game456");
        message.setSenderId("player3");
        message.setTargetId("player4");
        message.setPayload("sdp answer");
        message.setTimestamp("2024-01-02T00:00:00Z");

        // Then
        assertEquals("ANSWER", message.getType());
        assertEquals("game456", message.getGameId());
        assertEquals("player3", message.getSenderId());
        assertEquals("player4", message.getTargetId());
        assertEquals("sdp answer", message.getPayload());
        assertEquals("2024-01-02T00:00:00Z", message.getTimestamp());
    }

    @Test
    @DisplayName("Should support all signal types")
    void shouldSupportAllSignalTypes() {
        // When & Then
        String[] types = {"OFFER", "ANSWER", "ICE_CANDIDATE"};
        for (String type : types) {
            SignalingMessage message = SignalingMessage.builder()
                .type(type)
                .gameId("game123")
                .build();
            
            assertNotNull(message);
            assertEquals(type, message.getType());
        }
    }
}


