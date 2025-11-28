package com.arsw.balatro.service;

import com.arsw.balatro.model.dto.GameMessage;
import com.arsw.balatro.model.dto.MatchFoundDto;
import com.arsw.balatro.model.dto.QueueStatusDto;
import com.arsw.balatro.model.enums.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;

@Service
public class MatchmakingService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

    private final GameService gameService;
    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    private final Queue<PlayerInQueue> matchmakingQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, PlayerInQueue> playersInQueue = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public MatchmakingService(GameService gameService, SessionService sessionService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
    }
    
    @PostConstruct
    public void init() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::sendQueueUpdates, 3, 3, SECONDS);
        log.info("Matchmaking service initialized with periodic queue updates");
    }
    
    @PreDestroy
    public void cleanup() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public QueueStatusDto addToQueue(String playerId) {
        // Normalizar playerId para consistencia (trim + lowercase)
        String normalizedPlayerId = playerId != null ? playerId.trim().toLowerCase() : null;
        if (normalizedPlayerId == null) {
            log.error("Cannot add null playerId to queue");
            throw new IllegalArgumentException("PlayerId cannot be null");
        }
        
        log.info("=== ADDING PLAYER TO QUEUE ===");
        log.info("Player ID (original): {}", playerId);
        log.info("Player ID (normalized): {}", normalizedPlayerId);
        log.info("Current queue size BEFORE: {}", matchmakingQueue.size());
        log.info("Players in map BEFORE: {}", playersInQueue.keySet());
        
        // Verificar si ya está en la cola usando el ID normalizado
        if (playersInQueue.containsKey(normalizedPlayerId)) {
            log.warn("Player {} (normalized: {}) already in queue", playerId, normalizedPlayerId);
            return getQueueStatus(normalizedPlayerId);
        }
        
        // Verificar que la sesión esté registrada
        String sessionId = sessionService.getSessionId(normalizedPlayerId);
        if (sessionId == null) {
            log.warn("⚠️ Player {} (normalized: {}) added to queue but no session found. Session may not be registered yet.", 
                playerId, normalizedPlayerId);
            log.warn("💡 Debug info: {}", sessionService.getDebugInfo());
        } else {
            log.info("✅ Session found for player {} (normalized: {}): {}", playerId, normalizedPlayerId, sessionId);
        }
        
        PlayerInQueue player = new PlayerInQueue(
            normalizedPlayerId,  // Usar el ID normalizado en la cola
            Instant.now(),
            null
        );
        
        matchmakingQueue.offer(player);
        playersInQueue.put(normalizedPlayerId, player);  // Usar el ID normalizado como clave
        
        log.info("Player {} (normalized: {}) added to queue successfully", playerId, normalizedPlayerId);
        log.info("Queue size AFTER: {}", matchmakingQueue.size());
        log.info("Players in queue: {}", playersInQueue.keySet());
        log.info("=== ATTEMPTING MATCHMAKING ===");
        
        tryMatchmaking();
        
        return getQueueStatus(normalizedPlayerId);
    }

    public void removeFromQueue(String playerId) {
        // Normalizar playerId para consistencia
        String normalizedPlayerId = playerId != null ? playerId.trim().toLowerCase() : null;
        if (normalizedPlayerId == null) {
            log.warn("Cannot remove null playerId from queue");
            return;
        }
        PlayerInQueue player = playersInQueue.remove(normalizedPlayerId);
        if (player != null) {
            matchmakingQueue.remove(player);
            log.info("Player {} (normalized: {}) removed from queue. Queue size: {}", 
                playerId, normalizedPlayerId, matchmakingQueue.size());
        }
    }

    public QueueStatusDto getQueueStatus(String playerId) {
        // Normalizar playerId para consistencia
        String normalizedPlayerId = playerId != null ? playerId.trim().toLowerCase() : null;
        if (normalizedPlayerId == null) {
            return QueueStatusDto.builder()
                .playerId(playerId)
                .inQueue(false)
                .queuePosition(null)
                .playersInQueue(matchmakingQueue.size())
                .estimatedWaitTime(estimateWaitTime(matchmakingQueue.size()))
                .build();
        }
        boolean inQueue = playersInQueue.containsKey(normalizedPlayerId);
        int queueSize = matchmakingQueue.size();
        
        return QueueStatusDto.builder()
            .playerId(normalizedPlayerId)  // Devolver el ID normalizado
            .inQueue(inQueue)
            .queuePosition(inQueue ? getQueuePosition(normalizedPlayerId) : null)
            .playersInQueue(queueSize)
            .estimatedWaitTime(estimateWaitTime(queueSize))
            .build();
    }

    private void tryMatchmaking() {
        log.info(">>> tryMatchmaking called. Queue size: {}", matchmakingQueue.size());
        
        if (matchmakingQueue.size() < 2) {
            log.info(">>> Not enough players for matchmaking. Need 2, have {}", matchmakingQueue.size());
            return;
        }
        
        while (matchmakingQueue.size() >= 2) {
            log.info(">>> Attempting to match 2 players...");
            
            PlayerInQueue player1 = matchmakingQueue.poll();
            PlayerInQueue player2 = matchmakingQueue.poll();
            
            if (player1 == null || player2 == null) {
                log.warn(">>> One of the players is null! player1={}, player2={}", player1, player2);
                break;
            }
            
            log.info(">>> Matched: {} with {}", player1.playerId, player2.playerId);
            
            playersInQueue.remove(player1.playerId);
            playersInQueue.remove(player2.playerId);
            
            createMatch(player1.playerId, player2.playerId);
        }
        
        log.info(">>> Matchmaking complete. Remaining in queue: {}", matchmakingQueue.size());
    }

    private void createMatch(String player1Id, String player2Id) {
        // Los playerIds ya están normalizados porque vienen de la cola
        log.info("Creating match between {} and {}", player1Id, player2Id);
        log.info("💡 Debug info before creating match: {}", sessionService.getDebugInfo());
        
        try {
            String gameId = gameService.createGame(player1Id, player2Id);
            
            MatchFoundDto matchData = MatchFoundDto.builder()
                .gameId(gameId)
                .player1Id(player1Id)
                .player1Name("Player " + player1Id.substring(0, Math.min(8, player1Id.length())))
                .player2Id(player2Id)
                .player2Name("Player " + player2Id.substring(0, Math.min(8, player2Id.length())))
                .startTime(System.currentTimeMillis())
                .build();
            
            GameMessage matchFoundMsg = GameMessage.create(
                MessageType.MATCH_FOUND,
                gameId,
                null,
                matchData
            );
            
            // Verificar que las sesiones estén registradas
            String session1Id = sessionService.getSessionId(player1Id);
            String session2Id = sessionService.getSessionId(player2Id);
            
            log.info("🔍 Looking for sessions: player1Id={}, session1Id={}, player2Id={}, session2Id={}", 
                player1Id, session1Id, player2Id, session2Id);
            
            // IMPORTANTE: convertAndSendToUser espera el username (Principal.getName()), no el sessionId
            // Spring WebSocket mapea automáticamente el username a las sesiones activas
            // Los playerIds ya están normalizados y coinciden con el Principal.getName()
            
            if (session1Id != null) {
                // Usar el playerId (username) directamente, no el sessionId
                messagingTemplate.convertAndSendToUser(
                    player1Id,  // Usar el username (Principal name), no el sessionId
                    "/queue/matchmaking",
                    matchFoundMsg
                );
                log.info("✅ Match notification sent to player {} (username, sessionId: {})", player1Id, session1Id);
            } else {
                log.error("❌ No session found for player {} (normalized: {})", player1Id, player1Id);
                log.error("💡 Debug info: {}", sessionService.getDebugInfo());
            }
            
            if (session2Id != null) {
                // Usar el playerId (username) directamente, no el sessionId
                messagingTemplate.convertAndSendToUser(
                    player2Id,  // Usar el username (Principal name), no el sessionId
                    "/queue/matchmaking",
                    matchFoundMsg
                );
                log.info("✅ Match notification sent to player {} (username, sessionId: {})", player2Id, session2Id);
            } else {
                log.error("❌ No session found for player {} (normalized: {})", player2Id, player2Id);
                log.error("💡 Debug info: {}", sessionService.getDebugInfo());
            }
            
            if (session1Id != null && session2Id != null) {
                log.info("✅ Match created successfully. Game ID: {}", gameId);
            } else {
                log.error("❌ Match created but notifications may not have been sent. Game ID: {}", gameId);
            }
            
        } catch (Exception e) {
            log.error("❌ Error creating match: {}", e.getMessage(), e);
            
            // Re-agregar a la cola con IDs normalizados
            matchmakingQueue.offer(new PlayerInQueue(player1Id, Instant.now(), null));
            matchmakingQueue.offer(new PlayerInQueue(player2Id, Instant.now(), null));
            playersInQueue.put(player1Id, new PlayerInQueue(player1Id, Instant.now(), null));
            playersInQueue.put(player2Id, new PlayerInQueue(player2Id, Instant.now(), null));
        }
    }

    private int getQueuePosition(String normalizedPlayerId) {
        int position = 1;
        for (PlayerInQueue player : matchmakingQueue) {
            if (player.playerId.equals(normalizedPlayerId)) {
                return position;
            }
            position++;
        }
        return -1;
    }

    private Integer estimateWaitTime(int queueSize) {
        if (queueSize <= 1) {
            return 30;
        } else if (queueSize <= 5) {
            return 10;
        } else {
            return 5;
        }
    }
    
    private void sendQueueUpdates() {
        try {
            if (playersInQueue.isEmpty()) {
                return;
            }
            
            int queueSize = matchmakingQueue.size();
            log.debug("Sending queue updates to {} players", queueSize);
            
            for (String playerId : playersInQueue.keySet()) {
                try {
                    // Verificar que la sesión esté registrada
                    String sessionId = sessionService.getSessionId(playerId);
                    if (sessionId == null) {
                        log.warn("No session found for player {} in queue", playerId);
                        continue;
                    }
                    
                    QueueStatusDto status = getQueueStatus(playerId);
                    
                    GameMessage update = GameMessage.create(
                        MessageType.JOIN_QUEUE,
                        null,
                        playerId,
                        status
                    );
                    
                    // IMPORTANTE: convertAndSendToUser espera el username (Principal.getName()), no el sessionId
                    messagingTemplate.convertAndSendToUser(
                        playerId,  // Usar el username (Principal name), no el sessionId
                        "/queue/matchmaking",
                        update
                    );
                } catch (Exception e) {
                    log.error("Error sending queue update to player {}: {}", playerId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in sendQueueUpdates: {}", e.getMessage());
        }
    }

    private static class PlayerInQueue {
        String playerId;
        Instant joinedAt;
        Integer rating;

        PlayerInQueue(String playerId, Instant joinedAt, Integer rating) {
            this.playerId = playerId;
            this.joinedAt = joinedAt;
            this.rating = rating;
        }
    }
}
