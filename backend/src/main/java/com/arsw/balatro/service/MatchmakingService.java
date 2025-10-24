package com.arsw.balatro.service;

import com.arsw.balatro.model.dto.GameMessage;
import com.arsw.balatro.model.dto.MatchFoundDto;
import com.arsw.balatro.model.dto.QueueStatusDto;
import com.arsw.balatro.model.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchmakingService {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    private final Queue<PlayerInQueue> matchmakingQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, PlayerInQueue> playersInQueue = new ConcurrentHashMap<>();

    public QueueStatusDto addToQueue(String playerId) {
        log.info("Adding player {} to matchmaking queue", playerId);
        
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
        
        log.info("Player {} added to queue. Queue size: {}", playerId, matchmakingQueue.size());
        
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
        while (matchmakingQueue.size() >= 2) {
            PlayerInQueue player1 = matchmakingQueue.poll();
            PlayerInQueue player2 = matchmakingQueue.poll();
            
            if (player1 == null || player2 == null) {
                break;
            }
            
            playersInQueue.remove(player1.playerId);
            playersInQueue.remove(player2.playerId);
            
            createMatch(player1.playerId, player2.playerId);
        }
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
            
            messagingTemplate.convertAndSendToUser(
                player1Id,
                "/queue/matchmaking",
                matchFoundMsg
            );
            
            messagingTemplate.convertAndSendToUser(
                player2Id,
                "/queue/matchmaking",
                matchFoundMsg
            );
            
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
