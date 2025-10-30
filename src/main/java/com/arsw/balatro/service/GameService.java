package com.arsw.balatro.service;

import com.arsw.balatro.model.dto.GameState;
import com.arsw.balatro.model.dto.PlayerAction;
import com.arsw.balatro.model.enums.GamePhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();
    private final Map<String, String> playerToGame = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public String createGame(String player1Id, String player2Id) {
        String gameId = UUID.randomUUID().toString();
        
        log.info("Creating new game {} for players {} and {}", gameId, player1Id, player2Id);
        
        GameState gameState = GameState.builder()
            .gameId(gameId)
            .currentPhase(GamePhase.WAITING)
            .currentRound(0)
            .currentAnte(1)
            .player1(createInitialPlayerState(player1Id))
            .player2(createInitialPlayerState(player2Id))
            .currentRoundInfo(null)
            .winnerId(null)
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

    public void markPlayerReady(String gameId, String playerId) {
        GameState state = getGameState(gameId);
        
        if (state.getPlayer1().getPlayerId().equals(playerId)) {
            state.getPlayer1().setReady(true);
        } else if (state.getPlayer2().getPlayerId().equals(playerId)) {
            state.getPlayer2().setReady(true);
        }
        
        if (state.getPlayer1().isReady() && state.getPlayer2().isReady()) {
            startGame(gameId);
        }
        
        state.setLastUpdate(System.currentTimeMillis());
    }

    private void startGame(String gameId) {
        GameState state = getGameState(gameId);
        
        log.info("Starting game {}", gameId);
        
        state.setCurrentPhase(GamePhase.SHOP);
        state.setCurrentRound(1);
        
        state.setCurrentRoundInfo(GameState.RoundInfo.builder()
            .roundNumber(1)
            .blindType("SMALL")
            .targetScore(300)
            .bossEffect(null)
            .build());
        
        dealInitialCards(state.getPlayer1());
        dealInitialCards(state.getPlayer2());
        
        state.getPlayer1().setMoney(4);
        state.getPlayer2().setMoney(4);
        
        state.setLastUpdate(System.currentTimeMillis());
        
        log.info("Game {} started. Current phase: SHOP", gameId);
    }

    public GameState processAction(String gameId, PlayerAction action) {
        GameState state = getGameState(gameId);
        
        log.info("Processing action {} for player {} in game {}", 
            action.getActionType(), action.getPlayerId(), gameId);
        
        validateAction(state, action);
        
        switch (action.getActionType()) {
            case PLAY_HAND:
                processPlayHand(state, action);
                break;
            case DISCARD_CARDS:
                processDiscard(state, action);
                break;
            case BUY_ITEM:
                processBuyItem(state, action);
                break;
            case SELL_ITEM:
                processSellItem(state, action);
                break;
            case REROLL_SHOP:
                processRerollShop(state, action);
                break;
            default:
                log.warn("Unknown action type: {}", action.getActionType());
        }
        
        state.setLastUpdate(System.currentTimeMillis());
        
        checkRoundEnd(state);
        checkGameEnd(state);
        
        return state;
    }

    public void scheduleGameAbandonment(String gameId, String disconnectedPlayerId, int secondsDelay) {
        scheduler.schedule(() -> {
            try {
                GameState state = activeGames.get(gameId);
                if (state != null) {
                    log.info("Player {} abandoned game {}. Declaring opponent winner.", 
                        disconnectedPlayerId, gameId);
                    
                    String winnerId = state.getPlayer1().getPlayerId().equals(disconnectedPlayerId) 
                        ? state.getPlayer2().getPlayerId() 
                        : state.getPlayer1().getPlayerId();
                    
                    state.setWinnerId(winnerId);
                    state.setCurrentPhase(GamePhase.GAME_OVER);
                    state.setLastUpdate(System.currentTimeMillis());
                    
                    scheduler.schedule(() -> cleanupGame(gameId), 60, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                log.error("Error processing game abandonment: {}", e.getMessage());
            }
        }, secondsDelay, TimeUnit.SECONDS);
    }

    private void cleanupGame(String gameId) {
        GameState state = activeGames.remove(gameId);
        if (state != null) {
            playerToGame.remove(state.getPlayer1().getPlayerId());
            playerToGame.remove(state.getPlayer2().getPlayerId());
            log.info("Game {} cleaned up", gameId);
        }
    }

    private GameState.PlayerState createInitialPlayerState(String playerId) {
        return GameState.PlayerState.builder()
            .playerId(playerId)
            .playerName("Player " + playerId.substring(0, Math.min(8, playerId.length())))
            .money(4)
            .roundsWon(0)
            .currentScore(0)
            .targetScore(300)
            .handsRemaining(4)
            .discardsRemaining(3)
            .hand(new ArrayList<>())
            .deck(new ArrayList<>())
            .jokers(new ArrayList<>())
            .shop(createInitialShop())
            .isReady(false)
            .hasPlayed(false)
            .build();
    }

    private GameState.ShopState createInitialShop() {
        return GameState.ShopState.builder()
            .availableJokers(new ArrayList<>())
            .availablePlanets(new ArrayList<>())
            .rerollCost(5)
            .canReroll(true)
            .build();
    }

    private void dealInitialCards(GameState.PlayerState player) {
        List<GameState.CardDto> deck = createStandardDeck();
        Collections.shuffle(deck);
        
        List<GameState.CardDto> hand = new ArrayList<>(deck.subList(0, 8));
        List<GameState.CardDto> remainingDeck = new ArrayList<>(deck.subList(8, deck.size()));
        
        player.setHand(hand);
        player.setDeck(remainingDeck);
    }

    private List<GameState.CardDto> createStandardDeck() {
        List<GameState.CardDto> deck = new ArrayList<>();
        String[] suits = {"HEARTS", "DIAMONDS", "CLUBS", "SPADES"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        
        for (String suit : suits) {
            for (String rank : ranks) {
                deck.add(GameState.CardDto.builder()
                    .id(UUID.randomUUID().toString())
                    .suit(suit)
                    .rank(rank)
                    .chips(getBaseChips(rank))
                    .isSelected(false)
                    .build());
            }
        }
        
        return deck;
    }

    private Integer getBaseChips(String rank) {
        return switch (rank) {
            case "2" -> 2;
            case "3" -> 3;
            case "4" -> 4;
            case "5" -> 5;
            case "6" -> 6;
            case "7" -> 7;
            case "8" -> 8;
            case "9" -> 9;
            case "10" -> 10;
            case "J", "Q", "K" -> 10;
            case "A" -> 11;
            default -> 0;
        };
    }

    private void validateAction(GameState state, PlayerAction action) {
        if (!state.getPlayer1().getPlayerId().equals(action.getPlayerId()) &&
            !state.getPlayer2().getPlayerId().equals(action.getPlayerId())) {
            throw new IllegalArgumentException("Player not in this game");
        }
    }

    private void processPlayHand(GameState state, PlayerAction action) {
        GameState.PlayerState player = getPlayerState(state, action.getPlayerId());
        
        if (player.getHandsRemaining() <= 0) {
            throw new IllegalStateException("No hands remaining");
        }
        
        int score = calculateScore(action.getCardIds(), action.getHandType());
        player.setCurrentScore(player.getCurrentScore() + score);
        player.setHandsRemaining(player.getHandsRemaining() - 1);
        player.setHasPlayed(true);
        
        log.info("Player {} played hand. Score: {}, Hands remaining: {}", 
            action.getPlayerId(), score, player.getHandsRemaining());
    }

    private void processDiscard(GameState state, PlayerAction action) {
        GameState.PlayerState player = getPlayerState(state, action.getPlayerId());
        
        if (player.getDiscardsRemaining() <= 0) {
            throw new IllegalStateException("No discards remaining");
        }
        
        player.setDiscardsRemaining(player.getDiscardsRemaining() - 1);
        
        log.info("Player {} discarded cards. Discards remaining: {}", 
            action.getPlayerId(), player.getDiscardsRemaining());
    }

    private void processBuyItem(GameState state, PlayerAction action) {
        GameState.PlayerState player = getPlayerState(state, action.getPlayerId());
        
        log.info("Player {} bought item {}", action.getPlayerId(), action.getItemId());
    }

    private void processSellItem(GameState state, PlayerAction action) {
        GameState.PlayerState player = getPlayerState(state, action.getPlayerId());
        
        log.info("Player {} sold item at slot {}", action.getPlayerId(), action.getJokerSlot());
    }

    private void processRerollShop(GameState state, PlayerAction action) {
        GameState.PlayerState player = getPlayerState(state, action.getPlayerId());
        
        if (player.getMoney() < player.getShop().getRerollCost()) {
            throw new IllegalStateException("Not enough money for reroll");
        }
        
        player.setMoney(player.getMoney() - player.getShop().getRerollCost());
        
        log.info("Player {} rerolled shop", action.getPlayerId());
    }

    private GameState.PlayerState getPlayerState(GameState state, String playerId) {
        if (state.getPlayer1().getPlayerId().equals(playerId)) {
            return state.getPlayer1();
        } else if (state.getPlayer2().getPlayerId().equals(playerId)) {
            return state.getPlayer2();
        }
        throw new IllegalArgumentException("Player not found in game");
    }

    private int calculateScore(List<String> cardIds, String handType) {
        return switch (handType) {
            case "HIGH_CARD" -> 50;
            case "PAIR" -> 100;
            case "TWO_PAIR" -> 200;
            case "THREE_OF_A_KIND" -> 300;
            case "STRAIGHT" -> 400;
            case "FLUSH" -> 500;
            case "FULL_HOUSE" -> 600;
            case "FOUR_OF_A_KIND" -> 800;
            case "STRAIGHT_FLUSH" -> 1000;
            default -> 50;
        };
    }

    private void checkRoundEnd(GameState state) {
    }

    private void checkGameEnd(GameState state) {
    }
}
