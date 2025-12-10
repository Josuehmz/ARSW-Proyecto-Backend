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
    
    // Progreso actual de cada jugador
    private Integer player1Ante;
    private String player1Blind;
    private Integer player2Ante;
    private String player2Blind;
    
    // Información cuando un jugador se queda sin manos
    private Integer player1NoHandsAnte;
    private String player1NoHandsBlind;
    private Integer player2NoHandsAnte;
    private String player2NoHandsBlind;
    
    // Estado del juego
    private Boolean isTie;
    private String winnerId;
    private Boolean gameEnded;
}
