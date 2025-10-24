package com.arsw.balatro.model.dto;

import com.arsw.balatro.model.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Representa una acción realizada por un jugador
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerAction {
    
    private MessageType actionType;
    private String playerId;
    private String gameId;
    
    // Para jugadas de cartas
    private List<String> cardIds;
    private String handType;  // "PAIR", "FLUSH", etc.
    
    // Para compras en tienda
    private String itemId;
    private String itemType;  // "JOKER", "PLANET", etc.
    private Integer itemIndex;
    
    // Para venta de items
    private Integer jokerSlot;
    
    // Para reroll de tienda
    private Boolean rerollShop;
    
    // Metadatos
    private Long timestamp;
}

