package com.arsw.balatro.model.enums;

/**
 * Tipos de mensajes WebSocket para comunicación en tiempo real
 */
public enum MessageType {
   
    JOIN_QUEUE,
    LEAVE_QUEUE,
    MATCH_FOUND,
    
   
    GAME_START,
    GAME_END,
    ROUND_START,
    ROUND_END,
    
    
    PLAY_HAND,
    DISCARD_CARDS,
    BUY_ITEM,
    SELL_ITEM,
    REROLL_SHOP,
    
    
    GAME_STATE_UPDATE,
    PLAYER_STATE_UPDATE,
    OPPONENT_ACTION,
    
  
    SHOP_UPDATE,
    
   
    CHAT_MESSAGE,
    PLAYER_EMOTE,
    
    
    ERROR,
    INVALID_ACTION,
    
  
    PLAYER_CONNECTED,
    PLAYER_DISCONNECTED,
    PING,
    PONG
}

