package com.arsw.balatro.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estado simplificado del juego - solo mantiene info básica de la partida.
 * La lógica de juego y estado detallado se maneja en el cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameState {
    
    private String gameId;
    private String player1Id;
    private String player2Id;
    private Long createdAt;
    private Long lastUpdate;
}

