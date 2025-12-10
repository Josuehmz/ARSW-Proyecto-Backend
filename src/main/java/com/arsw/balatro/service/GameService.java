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
    
    /**
     * Normaliza un playerId: trim + lowercase
     * Esto asegura consistencia en las comparaciones
     */
    private String normalizePlayerId(String playerId) {
        if (playerId == null) {
            return null;
        }
        return playerId.trim().toLowerCase();
    }

    public String createGame(String player1Id, String player2Id) {
        // Normalizar playerIds para consistencia
        String normalizedPlayer1Id = normalizePlayerId(player1Id);
        String normalizedPlayer2Id = normalizePlayerId(player2Id);
        
        if (normalizedPlayer1Id == null || normalizedPlayer2Id == null) {
            throw new IllegalArgumentException("PlayerIds no pueden ser null");
        }
        
        String gameId = UUID.randomUUID().toString();
        
        log.info("Creating new game {} for players {} (normalized: {}) and {} (normalized: {})", 
            gameId, player1Id, normalizedPlayer1Id, player2Id, normalizedPlayer2Id);
        
        GameState gameState = GameState.builder()
            .gameId(gameId)
            .player1Id(normalizedPlayer1Id)  // Guardar IDs normalizados
            .player2Id(normalizedPlayer2Id)
            .createdAt(System.currentTimeMillis())
            .lastUpdate(System.currentTimeMillis())
            .build();
        
        activeGames.put(gameId, gameState);
        playerToGame.put(normalizedPlayer1Id, gameId);  // Usar IDs normalizados como clave
        playerToGame.put(normalizedPlayer2Id, gameId);
        
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
        String normalizedPlayerId = normalizePlayerId(playerId);
        return normalizedPlayerId != null ? playerToGame.get(normalizedPlayerId) : null;
    }

    public boolean isPlayerInGame(String gameId, String playerId) {
        String normalizedPlayerId = normalizePlayerId(playerId);
        if (normalizedPlayerId == null) {
            log.warn("isPlayerInGame: normalizedPlayerId is null for playerId: {}", playerId);
            return false;
        }
        GameState state = activeGames.get(gameId);
        if (state == null) {
            log.warn("isPlayerInGame: Game {} not found. Active games: {}", gameId, activeGames.keySet());
            return false;
        }
        boolean isPlayer1 = state.getPlayer1Id().equals(normalizedPlayerId);
        boolean isPlayer2 = state.getPlayer2Id().equals(normalizedPlayerId);
        boolean result = isPlayer1 || isPlayer2;
        
        if (!result) {
            log.warn("isPlayerInGame: Player {} (normalized: {}) not in game {}. Game players: player1={}, player2={}", 
                playerId, normalizedPlayerId, gameId, state.getPlayer1Id(), state.getPlayer2Id());
        }
        
        return result;
    }
    
    /**
     * Método de debug para obtener información sobre los juegos activos
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Active games: ").append(activeGames.size()).append("\n");
        for (Map.Entry<String, GameState> entry : activeGames.entrySet()) {
            GameState state = entry.getValue();
            sb.append(String.format("  Game %s: player1=%s, player2=%s\n", 
                entry.getKey(), state.getPlayer1Id(), state.getPlayer2Id()));
        }
        sb.append("Player to game mappings: ").append(playerToGame.size()).append("\n");
        for (Map.Entry<String, String> entry : playerToGame.entrySet()) {
            sb.append(String.format("  Player %s -> Game %s\n", entry.getKey(), entry.getValue()));
        }
        return sb.toString();
    }

    public String getOpponentId(String gameId, String playerId) {
        String normalizedPlayerId = normalizePlayerId(playerId);
        if (normalizedPlayerId == null) {
            throw new IllegalArgumentException("PlayerId no puede ser null");
        }
        GameState state = getGameState(gameId);
        if (state.getPlayer1Id().equals(normalizedPlayerId)) {
            return state.getPlayer2Id();
        } else if (state.getPlayer2Id().equals(normalizedPlayerId)) {
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
    
    /**
     * Orden de los blinds para comparación
     */
    private static final Map<String, Integer> BLIND_ORDER = Map.of(
        "small", 1,
        "big", 2,
        "boss", 3
    );
    
    /**
     * Compara si un progreso (ante, blind) está más adelante que otro
     */
    public boolean isAhead(int ante1, String blind1, int ante2, String blind2) {
        if (ante1 > ante2) return true;
        if (ante1 < ante2) return false;
        // Mismo ante, comparar blind
        int order1 = BLIND_ORDER.getOrDefault(blind1 != null ? blind1.toLowerCase() : "small", 0);
        int order2 = BLIND_ORDER.getOrDefault(blind2 != null ? blind2.toLowerCase() : "small", 0);
        return order1 > order2;
    }
    
    /**
     * Verifica si un progreso ha superado a otro
     */
    public boolean hasSurpassed(int currentAnte, String currentBlind, int targetAnte, String targetBlind) {
        if (currentAnte > targetAnte) return true;
        if (currentAnte == targetAnte) {
            int currentOrder = BLIND_ORDER.getOrDefault(currentBlind != null ? currentBlind.toLowerCase() : "small", 0);
            int targetOrder = BLIND_ORDER.getOrDefault(targetBlind != null ? targetBlind.toLowerCase() : "small", 0);
            return currentOrder > targetOrder;
        }
        return false;
    }
    
    /**
     * Actualiza el progreso de un jugador (ante y blind)
     */
    public void updatePlayerProgress(String gameId, String playerId, int ante, String blind) {
        String normalizedPlayerId = normalizePlayerId(playerId);
        GameState state = activeGames.get(gameId);
        if (state == null) {
            log.warn("Cannot update progress: Game {} not found", gameId);
            return;
        }
        
        if (state.getPlayer1Id().equals(normalizedPlayerId)) {
            state.setPlayer1Ante(ante);
            state.setPlayer1Blind(blind);
            log.info("Updated progress for player1 {} in game {}: ante={}, blind={}", 
                normalizedPlayerId, gameId, ante, blind);
        } else if (state.getPlayer2Id().equals(normalizedPlayerId)) {
            state.setPlayer2Ante(ante);
            state.setPlayer2Blind(blind);
            log.info("Updated progress for player2 {} in game {}: ante={}, blind={}", 
                normalizedPlayerId, gameId, ante, blind);
        } else {
            log.warn("Cannot update progress: Player {} not in game {}", normalizedPlayerId, gameId);
        }
        
        state.setLastUpdate(System.currentTimeMillis());
    }
    
    /**
     * Registra cuando un jugador se queda sin manos
     */
    public void registerNoHands(String gameId, String playerId, int ante, String blind) {
        String normalizedPlayerId = normalizePlayerId(playerId);
        GameState state = activeGames.get(gameId);
        if (state == null) {
            log.warn("Cannot register no hands: Game {} not found", gameId);
            return;
        }
        
        if (state.getPlayer1Id().equals(normalizedPlayerId)) {
            state.setPlayer1NoHandsAnte(ante);
            state.setPlayer1NoHandsBlind(blind);
            log.info("Registered no hands for player1 {} in game {}: ante={}, blind={}", 
                normalizedPlayerId, gameId, ante, blind);
        } else if (state.getPlayer2Id().equals(normalizedPlayerId)) {
            state.setPlayer2NoHandsAnte(ante);
            state.setPlayer2NoHandsBlind(blind);
            log.info("Registered no hands for player2 {} in game {}: ante={}, blind={}", 
                normalizedPlayerId, gameId, ante, blind);
        } else {
            log.warn("Cannot register no hands: Player {} not in game {}", normalizedPlayerId, gameId);
        }
        
        state.setLastUpdate(System.currentTimeMillis());
    }
    
    /**
     * Verifica condiciones de victoria después de actualizar progreso
     * Retorna información sobre si hay victoria y quién ganó
     */
    public VictoryCheckResult checkVictoryConditions(String gameId, String playerId) {
        String normalizedPlayerId = normalizePlayerId(playerId);
        GameState state = activeGames.get(gameId);
        if (state == null) {
            return VictoryCheckResult.noVictory();
        }
        
        // Si el juego ya terminó, no verificar de nuevo
        if (Boolean.TRUE.equals(state.getGameEnded())) {
            return VictoryCheckResult.noVictory();
        }
        
        String opponentId = getOpponentId(gameId, normalizedPlayerId);
        boolean isPlayer1 = state.getPlayer1Id().equals(normalizedPlayerId);
        
        // Obtener progresos actuales
        Integer playerAnte = isPlayer1 ? state.getPlayer1Ante() : state.getPlayer2Ante();
        String playerBlind = isPlayer1 ? state.getPlayer1Blind() : state.getPlayer2Blind();
        Integer opponentAnte = isPlayer1 ? state.getPlayer2Ante() : state.getPlayer1Ante();
        String opponentBlind = isPlayer1 ? state.getPlayer2Blind() : state.getPlayer1Blind();
        
        // Obtener información de quién se quedó sin manos
        Integer opponentNoHandsAnte = isPlayer1 ? state.getPlayer2NoHandsAnte() : state.getPlayer1NoHandsAnte();
        String opponentNoHandsBlind = isPlayer1 ? state.getPlayer2NoHandsBlind() : state.getPlayer1NoHandsBlind();
        Integer playerNoHandsAnte = isPlayer1 ? state.getPlayer1NoHandsAnte() : state.getPlayer2NoHandsAnte();
        String playerNoHandsBlind = isPlayer1 ? state.getPlayer1NoHandsBlind() : state.getPlayer2NoHandsBlind();
        
        // Verificar si el jugador actual superó al oponente que se quedó sin manos
        if (opponentNoHandsAnte != null && opponentNoHandsBlind != null && 
            playerAnte != null && playerBlind != null) {
            if (hasSurpassed(playerAnte, playerBlind, opponentNoHandsAnte, opponentNoHandsBlind)) {
                log.info("Player {} has surpassed opponent {} who ran out of hands at ante={}, blind={}", 
                    normalizedPlayerId, opponentId, opponentNoHandsAnte, opponentNoHandsBlind);
                state.setGameEnded(true);
                state.setWinnerId(normalizedPlayerId);
                return VictoryCheckResult.victory(normalizedPlayerId, opponentId, "opponent_no_hands");
            }
        }
        
        // Verificar si el oponente superó al jugador actual que se quedó sin manos
        if (playerNoHandsAnte != null && playerNoHandsBlind != null && 
            opponentAnte != null && opponentBlind != null) {
            if (hasSurpassed(opponentAnte, opponentBlind, playerNoHandsAnte, playerNoHandsBlind)) {
                log.info("Opponent {} has surpassed player {} who ran out of hands at ante={}, blind={}", 
                    opponentId, normalizedPlayerId, playerNoHandsAnte, playerNoHandsBlind);
                state.setGameEnded(true);
                state.setWinnerId(opponentId);
                return VictoryCheckResult.victory(opponentId, normalizedPlayerId, "opponent_no_hands");
            }
        }
        
        // Verificar empate: ambos se quedaron sin manos en el mismo ante
        if (playerNoHandsAnte != null && opponentNoHandsAnte != null) {
            if (playerNoHandsAnte.equals(opponentNoHandsAnte)) {
                String playerBlindAtNoHands = playerNoHandsBlind != null ? playerNoHandsBlind.toLowerCase() : "small";
                String opponentBlindAtNoHands = opponentNoHandsBlind != null ? opponentNoHandsBlind.toLowerCase() : "small";
                if (playerBlindAtNoHands.equals(opponentBlindAtNoHands)) {
                    log.info("Tie detected: Both players ran out of hands at ante={}, blind={}", 
                        playerNoHandsAnte, playerBlindAtNoHands);
                    state.setGameEnded(true);
                    state.setIsTie(true);
                    return VictoryCheckResult.tie();
                }
            }
        }
        
        return VictoryCheckResult.noVictory();
    }
    
    /**
     * Resultado de la verificación de condiciones de victoria
     */
    public static class VictoryCheckResult {
        private final boolean hasVictory;
        private final boolean isTie;
        private final String winnerId;
        private final String loserId;
        private final String reason;
        
        private VictoryCheckResult(boolean hasVictory, boolean isTie, String winnerId, String loserId, String reason) {
            this.hasVictory = hasVictory;
            this.isTie = isTie;
            this.winnerId = winnerId;
            this.loserId = loserId;
            this.reason = reason;
        }
        
        public static VictoryCheckResult noVictory() {
            return new VictoryCheckResult(false, false, null, null, null);
        }
        
        public static VictoryCheckResult victory(String winnerId, String loserId, String reason) {
            return new VictoryCheckResult(true, false, winnerId, loserId, reason);
        }
        
        public static VictoryCheckResult tie() {
            return new VictoryCheckResult(false, true, null, null, "tie");
        }
        
        public boolean hasVictory() { return hasVictory; }
        public boolean isTie() { return isTie; }
        public String getWinnerId() { return winnerId; }
        public String getLoserId() { return loserId; }
        public String getReason() { return reason; }
    }
}
