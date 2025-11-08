package com.arsw.balatro.model.enums;

/**
 * Tipos de mensajes WebSocket simplificados para comunicación en tiempo real.
 * El backend solo actúa como intermediario, por lo que la mayoría de mensajes
 * específicos del juego se eliminan y se manejan como mensajes genéricos en el cliente.
 */
public enum MessageType {
   
    // Matchmaking
    JOIN_QUEUE,
    LEAVE_QUEUE,
    MATCH_FOUND,
    
    // Mensajes genéricos de juego (reenvío sin procesar)
    GAME_MESSAGE,
    
    // Comunicación
    CHAT_MESSAGE,
    PLAYER_EMOTE,
    
    // Errores
    ERROR,
    
    // Eventos de conexión
    PLAYER_CONNECTED,
    PLAYER_DISCONNECTED,
    
    // Keep-alive
    PING,
    PONG
}

