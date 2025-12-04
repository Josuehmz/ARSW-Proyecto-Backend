package com.arsw.balatro.model.dto;

import com.arsw.balatro.model.enums.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GameMessage DTO Tests")
class GameMessageTest {

    @Test
    @DisplayName("Should create GameMessage with create method")
    void shouldCreateGameMessageWithCreateMethod() {
        // Given
        MessageType type = MessageType.JOIN_QUEUE;
        String gameId = "game123";
        String playerId = "player1";
        Object payload = "test payload";

        // When
        GameMessage message = GameMessage.create(type, gameId, playerId, payload);

        // Then
        assertNotNull(message);
        assertEquals(type, message.getType());
        assertEquals(gameId, message.getGameId());
        assertEquals(playerId, message.getPlayerId());
        assertEquals(payload, message.getPayload());
        assertNotNull(message.getTimestamp());
    }

    @Test
    @DisplayName("Should create error GameMessage")
    void shouldCreateErrorGameMessage() {
        // Given
        String gameId = "game123";
        String playerId = "player1";
        String errorMessage = "Error occurred";

        // When
        GameMessage message = GameMessage.error(gameId, playerId, errorMessage);

        // Then
        assertNotNull(message);
        assertEquals(MessageType.ERROR, message.getType());
        assertEquals(gameId, message.getGameId());
        assertEquals(playerId, message.getPlayerId());
        assertEquals(errorMessage, message.getMessage());
        assertNotNull(message.getTimestamp());
    }

    @Test
    @DisplayName("Should create GameMessage with null values")
    void shouldCreateGameMessageWithNullValues() {
        // When
        GameMessage message = GameMessage.create(null, null, null, null);

        // Then
        assertNotNull(message);
        assertNull(message.getType());
        assertNull(message.getGameId());
        assertNull(message.getPlayerId());
        assertNull(message.getPayload());
        assertNotNull(message.getTimestamp());
    }

    @Test
    @DisplayName("Should create error GameMessage with null values")
    void shouldCreateErrorGameMessageWithNullValues() {
        // When
        GameMessage message = GameMessage.error(null, null, null);

        // Then
        assertNotNull(message);
        assertEquals(MessageType.ERROR, message.getType());
        assertNull(message.getGameId());
        assertNull(message.getPlayerId());
        assertNull(message.getMessage());
        assertNotNull(message.getTimestamp());
    }

    @Test
    @DisplayName("Should use builder pattern")
    void shouldUseBuilderPattern() {
        // When
        GameMessage message = GameMessage.builder()
            .type(MessageType.PING)
            .gameId("game123")
            .playerId("player1")
            .payload("payload")
            .timestamp("2024-01-01T00:00:00Z")
            .message("test message")
            .build();

        // Then
        assertNotNull(message);
        assertEquals(MessageType.PING, message.getType());
        assertEquals("game123", message.getGameId());
        assertEquals("player1", message.getPlayerId());
        assertEquals("payload", message.getPayload());
        assertEquals("2024-01-01T00:00:00Z", message.getTimestamp());
        assertEquals("test message", message.getMessage());
    }

    @Test
    @DisplayName("Should create GameMessage with all MessageTypes")
    void shouldCreateGameMessageWithAllMessageTypes() {
        // When & Then
        for (MessageType type : MessageType.values()) {
            GameMessage message = GameMessage.create(type, "game123", "player1", null);
            assertNotNull(message);
            assertEquals(type, message.getType());
        }
    }

    @Test
    @DisplayName("Should create GameMessage with AllArgsConstructor")
    void shouldCreateGameMessageWithAllArgsConstructor() {
        // When
        GameMessage message = new GameMessage(
            MessageType.PING, "game123", "player1", "payload", 
            "2024-01-01T00:00:00Z", "test message"
        );

        // Then
        assertNotNull(message);
        assertEquals(MessageType.PING, message.getType());
        assertEquals("game123", message.getGameId());
        assertEquals("player1", message.getPlayerId());
        assertEquals("payload", message.getPayload());
        assertEquals("2024-01-01T00:00:00Z", message.getTimestamp());
        assertEquals("test message", message.getMessage());
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        // Given
        GameMessage msg1 = GameMessage.builder()
            .type(MessageType.PING)
            .gameId("game123")
            .playerId("player1")
            .payload("payload")
            .timestamp("2024-01-01T00:00:00Z")
            .message("test")
            .build();
        
        GameMessage msg2 = GameMessage.builder()
            .type(MessageType.PING)
            .gameId("game123")
            .playerId("player1")
            .payload("payload")
            .timestamp("2024-01-01T00:00:00Z")
            .message("test")
            .build();
        
        GameMessage msg3 = GameMessage.builder()
            .type(MessageType.PONG)
            .gameId("game456")
            .playerId("player2")
            .payload("different")
            .timestamp("2024-01-02T00:00:00Z")
            .message("different")
            .build();

        // Then
        assertEquals(msg1, msg2);
        assertNotEquals(msg1, msg3);
        assertNotEquals(msg1, null);
        assertNotEquals(msg1, "not a message");
    }

    @Test
    @DisplayName("Should implement hashCode correctly")
    void shouldImplementHashCodeCorrectly() {
        // Given
        GameMessage msg1 = GameMessage.builder()
            .type(MessageType.PING)
            .gameId("game123")
            .playerId("player1")
            .payload("payload")
            .timestamp("2024-01-01T00:00:00Z")
            .build();
        
        GameMessage msg2 = GameMessage.builder()
            .type(MessageType.PING)
            .gameId("game123")
            .playerId("player1")
            .payload("payload")
            .timestamp("2024-01-01T00:00:00Z")
            .build();

        // Then
        assertEquals(msg1.hashCode(), msg2.hashCode());
    }

    @Test
    @DisplayName("Should implement toString")
    void shouldImplementToString() {
        // Given
        GameMessage message = GameMessage.builder()
            .type(MessageType.PING)
            .gameId("game123")
            .playerId("player1")
            .payload("payload")
            .build();

        // When
        String toString = message.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("game123"));
        assertTrue(toString.contains("player1"));
    }
}




