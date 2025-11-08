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
        log.info("=== ADDING PLAYER TO QUEUE ===");
        log.info("Player ID: {}", playerId);
        log.info("Current queue size BEFORE: {}", matchmakingQueue.size());
        log.info("Players in map BEFORE: {}", playersInQueue.keySet());
        
        if (playersInQueue.containsKey(playerId)) {
            log.warn("Player {} already in queue", playerId);
            return getQueueStatus(playerId);
        }
        
        PlayerInQueue player = new PlayerInQueue(
            playerId,
            Instant.now(),
            null
        );
        
        matchmakingQueue.offer(player);
        playersInQueue.put(playerId, player);
        
        log.info("Player {} added to queue successfully", playerId);
        log.info("Queue size AFTER: {}", matchmakingQueue.size());
        log.info("Players in queue: {}", playersInQueue.keySet());
        log.info("=== ATTEMPTING MATCHMAKING ===");
        
        tryMatchmaking();
        
        return getQueueStatus(playerId);
    }

    public void removeFromQueue(String playerId) {
        PlayerInQueue player = playersInQueue.remove(playerId);
        if (player != null) {
            matchmakingQueue.remove(player);
            log.info("Player {} removed from queue. Queue size: {}", playerId, matchmakingQueue.size());
        }
    }

    public QueueStatusDto getQueueStatus(String playerId) {
        boolean inQueue = playersInQueue.containsKey(playerId);
        int queueSize = matchmakingQueue.size();
        
        return QueueStatusDto.builder()
            .playerId(playerId)
            .inQueue(inQueue)
            .queuePosition(inQueue ? getQueuePosition(playerId) : null)
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
        log.info("Creating match between {} and {}", player1Id, player2Id);
        
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
            
            String session1 = sessionService.getSessionId(player1Id);
            String session2 = sessionService.getSessionId(player2Id);
            
            if (session1 != null) {
                messagingTemplate.convertAndSendToUser(
                    session1,
                    "/queue/matchmaking",
                    matchFoundMsg
                );
                log.info("Match notification sent to player {} (session: {})", player1Id, session1);
            } else {
                log.warn("No session found for player {}", player1Id);
            }
            
            if (session2 != null) {
                messagingTemplate.convertAndSendToUser(
                    session2,
                    "/queue/matchmaking",
                    matchFoundMsg
                );
                log.info("Match notification sent to player {} (session: {})", player2Id, session2);
            } else {
                log.warn("No session found for player {}", player2Id);
            }
            
            log.info("Match created successfully. Game ID: {}", gameId);
            
        } catch (Exception e) {
            log.error("Error creating match: {}", e.getMessage());
            
            matchmakingQueue.offer(new PlayerInQueue(player1Id, Instant.now(), null));
            matchmakingQueue.offer(new PlayerInQueue(player2Id, Instant.now(), null));
            playersInQueue.put(player1Id, new PlayerInQueue(player1Id, Instant.now(), null));
            playersInQueue.put(player2Id, new PlayerInQueue(player2Id, Instant.now(), null));
        }
    }

    private int getQueuePosition(String playerId) {
        int position = 1;
        for (PlayerInQueue player : matchmakingQueue) {
            if (player.playerId.equals(playerId)) {
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
                    
                    messagingTemplate.convertAndSendToUser(
                        sessionId,
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
