package com.arsw.balatro.model.dto;

import com.arsw.balatro.model.enums.GamePhase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Estado completo del juego en un momento dado
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameState {
    
    private String gameId;
    private GamePhase currentPhase;
    private Integer currentRound;
    private Integer currentAnte;
    
    // Estado de jugadores
    private PlayerState player1;
    private PlayerState player2;
    
    // Información de la ronda actual
    private RoundInfo currentRoundInfo;
    
    // Ganador (null si el juego continúa)
    private String winnerId;
    
    private Long lastUpdate;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerState {
        private String playerId;
        private String playerName;
        private Integer money;
        private Integer roundsWon;
        private Integer currentScore;
        private Integer targetScore;
        private Integer handsRemaining;
        private Integer discardsRemaining;
        private List<CardDto> hand;
        private List<CardDto> deck;
        private List<JokerDto> jokers;
        private ShopState shop;
        private boolean isReady;
        private boolean hasPlayed;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoundInfo {
        private Integer roundNumber;
        private String blindType;  // "SMALL", "BIG", "BOSS"
        private Integer targetScore;
        private String bossEffect;  // Efecto especial del boss blind
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardDto {
        private String id;
        private String suit;  // "HEARTS", "DIAMONDS", "CLUBS", "SPADES"
        private String rank;  // "2" - "10", "J", "Q", "K", "A"
        private Integer chips;
        private boolean isSelected;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JokerDto {
        private String id;
        private String name;
        private String description;
        private String effect;
        private String rarity;  // "COMMON", "UNCOMMON", "RARE", "LEGENDARY"
        private Integer cost;
        private Map<String, Object> effectParams;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShopState {
        private List<JokerDto> availableJokers;
        private List<PlanetCardDto> availablePlanets;
        private Integer rerollCost;
        private boolean canReroll;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanetCardDto {
        private String id;
        private String name;
        private String handType;  // Qué combinación mejora
        private Integer chipsBonus;
        private Integer multBonus;
        private Integer cost;
    }
}

