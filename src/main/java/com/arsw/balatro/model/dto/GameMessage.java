package com.arsw.balatro.model.dto;

import com.arsw.balatro.model.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameMessage {
    
    private MessageType type;
    private String gameId;
    private String playerId;
    private Object payload;
    private String timestamp;
    private String message;
    
    public static GameMessage create(MessageType type, String gameId, String playerId, Object payload) {
        return GameMessage.builder()
                .type(type)
                .gameId(gameId)
                .playerId(playerId)
                .payload(payload)
                .timestamp(java.time.Instant.now().toString())
                .build();
    }
    
    public static GameMessage error(String gameId, String playerId, String errorMessage) {
        return GameMessage.builder()
                .type(MessageType.ERROR)
                .gameId(gameId)
                .playerId(playerId)
                .message(errorMessage)
                .timestamp(java.time.Instant.now().toString())
                .build();
    }
}
