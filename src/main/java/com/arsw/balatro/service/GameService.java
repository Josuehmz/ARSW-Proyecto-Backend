package com.arsw.balatro.service;

import com.arsw.balatro.model.dto.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);
    
    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();
    private final Map<String, String> playerToGame = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public String createGame(String player1Id, String player2Id) {
        String gameId = UUID.randomUUID().toString();
        
        log.info("Creating new game {} for players {} and {}", gameId, player1Id, player2Id);
        
        GameState gameState = GameState.builder()
            .gameId(gameId)
            .player1Id(player1Id)
            .player2Id(player2Id)
            .createdAt(System.currentTimeMillis())
            .lastUpdate(System.currentTimeMillis())
            .build();
        
        activeGames.put(gameId, gameState);
        playerToGame.put(player1Id, gameId);
        playerToGame.put(player2Id, gameId);
        
        log.info("Game {} created successfully", gameId);
        
        return gameId;
    }

    public GameState getGameState(String gameId) {
        GameState state = activeGames.get(gameId);
        if (state == null) {
            throw new IllegalArgumentException("Game not found: " + gameId);
        }
        return state;
    }

    public String getActiveGameIdForPlayer(String playerId) {
        return playerToGame.get(playerId);
    }

    public boolean isPlayerInGame(String gameId, String playerId) {
        GameState state = activeGames.get(gameId);
        if (state == null) {
            return false;
        }
        return state.getPlayer1Id().equals(playerId) || state.getPlayer2Id().equals(playerId);
    }

    public String getOpponentId(String gameId, String playerId) {
        GameState state = getGameState(gameId);
        if (state.getPlayer1Id().equals(playerId)) {
            return state.getPlayer2Id();
        } else if (state.getPlayer2Id().equals(playerId)) {
            return state.getPlayer1Id();
        }
        throw new IllegalArgumentException("Player not in this game");
    }

    public void updateGameActivity(String gameId) {
        GameState state = activeGames.get(gameId);
        if (state != null) {
            state.setLastUpdate(System.currentTimeMillis());
        }
    }

    public void scheduleGameCleanup(String gameId, int secondsDelay) {
        scheduler.schedule(() -> {
            try {
                cleanupGame(gameId);
                log.info("Game {} cleaned up after player disconnection", gameId);
            } catch (Exception e) {
                log.error("Error cleaning up game: {}", e.getMessage());
            }
        }, secondsDelay, TimeUnit.SECONDS);
    }

    public void cleanupGame(String gameId) {
        GameState state = activeGames.remove(gameId);
        if (state != null) {
            playerToGame.remove(state.getPlayer1Id());
            playerToGame.remove(state.getPlayer2Id());
            log.info("Game {} cleaned up", gameId);
        }
    }
}
