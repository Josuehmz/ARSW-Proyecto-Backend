package com.arsw.balatro.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para notificar que se encontró una partida
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchFoundDto {
    private String gameId;
    private String player1Id;
    private String player1Name;
    private String player2Id;
    private String player2Name;
    private Long startTime;
}

