package com.arsw.balatro.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {
    
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    
    private final Map<String, String> playerToSession = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();
    
    /**
     * Normaliza un playerId: trim + lowercase
     * Esto permite que el enrutamiento funcione incluso si hay diferencias de mayúsculas/minúsculas o espacios
     */
    private String normalizePlayerId(String playerId) {
        if (playerId == null) {
            return null;
        }
        return playerId.trim().toLowerCase();
    }
    
    public void registerSession(String playerId, String sessionId) {
        String normalizedPlayerId = normalizePlayerId(playerId);
        if (normalizedPlayerId == null) {
            log.error("Cannot register session: playerId is null");
            return;
        }
        log.info("Registering session for player {} (normalized: {}): {}", playerId, normalizedPlayerId, sessionId);
        String oldSessionId = playerToSession.get(normalizedPlayerId);
        if (oldSessionId != null) {
            sessionToPlayer.remove(oldSessionId);
        }
        playerToSession.put(normalizedPlayerId, sessionId);
        sessionToPlayer.put(sessionId, normalizedPlayerId);
    }
    
    public String getSessionId(String playerId) {
        String normalizedPlayerId = normalizePlayerId(playerId);
        if (normalizedPlayerId == null) {
            return null;
        }
        return playerToSession.get(normalizedPlayerId);
    }
    
    public String getPlayerId(String sessionId) {
        return sessionToPlayer.get(sessionId);
    }
    
    public void removeByPlayerId(String playerId) {
        String normalizedPlayerId = normalizePlayerId(playerId);
        if (normalizedPlayerId == null) {
            log.warn("Cannot remove session: playerId is null");
            return;
        }
        log.info("Removing session for player: {} (normalized: {})", playerId, normalizedPlayerId);
        String sessionId = playerToSession.remove(normalizedPlayerId);
        if (sessionId != null) {
            sessionToPlayer.remove(sessionId);
        }
    }
    
    public void removeBySessionId(String sessionId) {
        log.info("Removing session: {}", sessionId);
        String playerId = sessionToPlayer.remove(sessionId);
        if (playerId != null) {
            playerToSession.remove(playerId);
        }
    }
    
    public boolean hasSession(String playerId) {
        String normalizedPlayerId = normalizePlayerId(playerId);
        if (normalizedPlayerId == null) {
            return false;
        }
        return playerToSession.containsKey(normalizedPlayerId);
    }
    
    public int getActiveSessionCount() {
        return playerToSession.size();
    }
    
    /**
     * Obtiene información de debugging sobre las sesiones activas
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Active sessions: ").append(playerToSession.size()).append("\n");
        playerToSession.forEach((playerId, sessionId) -> {
            sb.append("  Player: ").append(playerId).append(" -> Session: ").append(sessionId).append("\n");
        });
        return sb.toString();
    }
}
