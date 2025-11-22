package com.arsw.balatro.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalingMessage {
    private String type;          // "OFFER", "ANSWER", "ICE_CANDIDATE"
    private String gameId;        // ID del juego
    private String senderId;      // ID del jugador que envía
    private String targetId;      // ID del jugador destinatario
    private Object payload;       // SDP offer/answer o ICE candidate
    private String timestamp;     // Timestamp del mensaje
}

