package com.arsw.balatro.service;

import com.arsw.balatro.model.dto.CreateRoomDto;
import com.arsw.balatro.model.dto.JoinRoomDto;
import com.arsw.balatro.model.dto.RoomInfoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final GameService gameService;
    
    private final Map<String, RoomInfoDto> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> playerToRoom = new ConcurrentHashMap<>();

    public RoomService(GameService gameService) {
        this.gameService = gameService;
    }

    public RoomInfoDto createRoom(CreateRoomDto createDto) {
        log.info("Creating room for player {}", createDto.getPlayerId());
        
        if (playerToRoom.containsKey(createDto.getPlayerId())) {
            String existingRoomCode = playerToRoom.get(createDto.getPlayerId());
            log.warn("Player {} already in room {}", createDto.getPlayerId(), existingRoomCode);
            throw new IllegalStateException("Ya estás en una sala. Sal primero antes de crear una nueva.");
        }
        
        String roomCode = createDto.getRoomCode();
        if (roomCode == null || roomCode.trim().isEmpty()) {
            throw new IllegalArgumentException("El código de sala es requerido");
        }
        roomCode = roomCode.toUpperCase().trim();
        
        if (rooms.containsKey(roomCode)) {
            throw new IllegalStateException("El código de sala ya existe. Intenta con otro.");
        }
        
        RoomInfoDto roomInfo = RoomInfoDto.builder()
            .roomCode(roomCode)
            .hostId(createDto.getPlayerId())
            .hostName(createDto.getPlayerName())
            .isFull(false)
            .status(RoomInfoDto.RoomStatus.WAITING)
            .createdAt(System.currentTimeMillis())
            .build();
        
        rooms.put(roomCode, roomInfo);
        playerToRoom.put(createDto.getPlayerId(), roomCode);
        
        log.info("Room created with code: {}", roomCode);
        
        return roomInfo;
    }

    public RoomInfoDto joinRoom(JoinRoomDto joinDto) {
        String roomCode = joinDto.getRoomCode().toUpperCase();
        log.info("Player {} attempting to join room {}", joinDto.getPlayerId(), roomCode);
        
        RoomInfoDto roomInfo = rooms.get(roomCode);
        if (roomInfo == null) {
            log.warn("Room {} not found", roomCode);
            throw new IllegalArgumentException("Sala no encontrada. Verifica el código.");
        }
        
        if (playerToRoom.containsKey(joinDto.getPlayerId())) {
            String existingRoomCode = playerToRoom.get(joinDto.getPlayerId());
            if (existingRoomCode.equals(roomCode)) {
                log.info("Player {} already in this room", joinDto.getPlayerId());
                return roomInfo;
            } else {
                log.warn("Player {} already in another room {}", joinDto.getPlayerId(), existingRoomCode);
                throw new IllegalStateException("Ya estás en otra sala. Sal primero.");
            }
        }
        
        if (roomInfo.isFull()) {
            log.warn("Room {} is full", roomCode);
            throw new IllegalStateException("La sala está llena.");
        }
        
        if (roomInfo.getHostId().equals(joinDto.getPlayerId())) {
            log.warn("Host {} trying to join own room", joinDto.getPlayerId());
            throw new IllegalStateException("No puedes unirte a tu propia sala.");
        }
        
        roomInfo.setGuestId(joinDto.getPlayerId());
        roomInfo.setGuestName(joinDto.getPlayerName());
        roomInfo.setFull(true);
        roomInfo.setStatus(RoomInfoDto.RoomStatus.READY);
        
        playerToRoom.put(joinDto.getPlayerId(), roomCode);
        
        String gameId = gameService.createGame(roomInfo.getHostId(), roomInfo.getGuestId());
        roomInfo.setGameId(gameId);
        roomInfo.setStatus(RoomInfoDto.RoomStatus.IN_PROGRESS);
        
        log.info("Player {} joined room {}. Game {} created.", joinDto.getPlayerId(), roomCode, gameId);
        
        return roomInfo;
    }

    public RoomInfoDto getRoomInfo(String roomCode) {
        RoomInfoDto roomInfo = rooms.get(roomCode.toUpperCase());
        if (roomInfo == null) {
            throw new IllegalArgumentException("Sala no encontrada");
        }
        return roomInfo;
    }

    public String getRoomCodeForPlayer(String playerId) {
        return playerToRoom.get(playerId);
    }

    public void leaveRoom(String playerId) {
        String roomCode = playerToRoom.remove(playerId);
        if (roomCode == null) {
            log.debug("Player {} not in any room", playerId);
            return;
        }
        
        RoomInfoDto roomInfo = rooms.get(roomCode);
        if (roomInfo == null) {
            return;
        }
        
        log.info("Player {} leaving room {}", playerId, roomCode);
        
        if (roomInfo.getHostId().equals(playerId) || roomInfo.getStatus() == RoomInfoDto.RoomStatus.WAITING) {
            rooms.remove(roomCode);
            playerToRoom.remove(roomInfo.getHostId());
            if (roomInfo.getGuestId() != null) {
                playerToRoom.remove(roomInfo.getGuestId());
            }
            log.info("Room {} deleted", roomCode);
        } else {
            roomInfo.setGuestId(null);
            roomInfo.setGuestName(null);
            roomInfo.setFull(false);
            roomInfo.setStatus(RoomInfoDto.RoomStatus.WAITING);
        }
    }

    public void cleanupRoom(String roomCode) {
        RoomInfoDto roomInfo = rooms.remove(roomCode);
        if (roomInfo != null) {
            playerToRoom.remove(roomInfo.getHostId());
            if (roomInfo.getGuestId() != null) {
                playerToRoom.remove(roomInfo.getGuestId());
            }
            log.info("Room {} cleaned up", roomCode);
        }
    }

    public Map<String, RoomInfoDto> getAllRooms() {
        return new ConcurrentHashMap<>(rooms);
    }
}
