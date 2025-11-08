package com.arsw.balatro.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomDto {
    private String playerId;
    private String playerName;
    private String roomCode;
    private boolean isPrivate;
}
