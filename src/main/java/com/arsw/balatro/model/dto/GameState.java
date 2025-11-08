package com.arsw.balatro.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameState {
    private String gameId;
    private String player1Id;
    private String player2Id;
    private long createdAt;
    private long lastUpdate;
}
