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
    
    
    private List<String> cardIds;
    private String handType;  
    
   
    private String itemId;
    private String itemType; 
    private Integer itemIndex;
    
    
    private Integer jokerSlot;
    
    
    private Boolean rerollShop;
    
  
    private Long timestamp;
}

