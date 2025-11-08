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
    
    public void registerSession(String playerId, String sessionId) {
        log.info("Registering session for player {}: {}", playerId, sessionId);
        String oldSessionId = playerToSession.get(playerId);
        if (oldSessionId != null) {
            sessionToPlayer.remove(oldSessionId);
        }
        playerToSession.put(playerId, sessionId);
        sessionToPlayer.put(sessionId, playerId);
    }
    
    public String getSessionId(String playerId) {
        return playerToSession.get(playerId);
    }
    
    public String getPlayerId(String sessionId) {
        return sessionToPlayer.get(sessionId);
    }
    
    public void removeByPlayerId(String playerId) {
        log.info("Removing session for player: {}", playerId);
        String sessionId = playerToSession.remove(playerId);
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
        return playerToSession.containsKey(playerId);
    }
    
    public int getActiveSessionCount() {
        return playerToSession.size();
    }
}
