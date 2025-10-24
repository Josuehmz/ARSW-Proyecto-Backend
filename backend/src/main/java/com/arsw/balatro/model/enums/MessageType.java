package com.arsw.balatro.model.enums;

/**
 * Tipos de mensajes WebSocket para comunicación en tiempo real
 */
public enum MessageType {
    // Matchmaking
    JOIN_QUEUE,
    LEAVE_QUEUE,
    MATCH_FOUND,
    
    // Game Lifecycle
    GAME_START,
    GAME_END,
    ROUND_START,
    ROUND_END,
    
    // Player Actions
    PLAY_HAND,
    DISCARD_CARDS,
    BUY_ITEM,
    SELL_ITEM,
    REROLL_SHOP,
    
    // Game Updates
    GAME_STATE_UPDATE,
    PLAYER_STATE_UPDATE,
    OPPONENT_ACTION,
    
    // Shop
    SHOP_UPDATE,
    
    // Chat/Communication
    CHAT_MESSAGE,
    PLAYER_EMOTE,
    
    // Errors
    ERROR,
    INVALID_ACTION,
    
    // Connection
    PLAYER_CONNECTED,
    PLAYER_DISCONNECTED,
    PING,
    PONG
}

