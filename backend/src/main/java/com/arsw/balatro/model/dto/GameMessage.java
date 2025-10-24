package com.arsw.balatro.model.dto;

import com.arsw.balatro.model.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mensaje genérico para comunicación WebSocket
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {
    
    private MessageType type;
    private String gameId;
    private String playerId;
    private Object payload;
    private LocalDateTime timestamp;
    private String message;
    
    public static GameMessage create(MessageType type, String gameId, String playerId, Object payload) {
        return GameMessage.builder()
                .type(type)
                .gameId(gameId)
                .playerId(playerId)
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static GameMessage error(String gameId, String playerId, String errorMessage) {
        return GameMessage.builder()
                .type(MessageType.ERROR)
                .gameId(gameId)
                .playerId(playerId)
                .message(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

