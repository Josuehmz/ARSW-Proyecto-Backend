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

    public RoomInfoDto createRoom(CreateRoomDto createDto) {
        // Normalizar playerId para consistencia
        String normalizedPlayerId = normalizePlayerId(createDto.getPlayerId());
        if (normalizedPlayerId == null) {
            throw new IllegalArgumentException("PlayerId no puede ser null");
        }
        
        log.info("Creating room for player {} (normalized: {})", createDto.getPlayerId(), normalizedPlayerId);
        
        if (playerToRoom.containsKey(normalizedPlayerId)) {
            String existingRoomCode = playerToRoom.get(normalizedPlayerId);
            log.warn("Player {} (normalized: {}) already in room {}", createDto.getPlayerId(), normalizedPlayerId, existingRoomCode);
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
            .hostId(normalizedPlayerId)  // Guardar el ID normalizado
            .hostName(createDto.getPlayerName())
            .isFull(false)
            .status(RoomInfoDto.RoomStatus.WAITING)
            .createdAt(System.currentTimeMillis())
            .build();
        
        rooms.put(roomCode, roomInfo);
        playerToRoom.put(normalizedPlayerId, roomCode);  // Usar ID normalizado como clave
        
        log.info("Room created with code: {} for host: {} (normalized: {})", roomCode, createDto.getPlayerId(), normalizedPlayerId);
        
        return roomInfo;
    }

    public RoomInfoDto joinRoom(JoinRoomDto joinDto) {
        // Normalizar playerId para consistencia
        String normalizedPlayerId = normalizePlayerId(joinDto.getPlayerId());
        if (normalizedPlayerId == null) {
            throw new IllegalArgumentException("PlayerId no puede ser null");
        }
        
        String roomCode = joinDto.getRoomCode() != null ? joinDto.getRoomCode().toUpperCase().trim() : null;
        log.info("=== JOIN ROOM ATTEMPT ===");
        log.info("Player {} (normalized: {}) attempting to join room {}", joinDto.getPlayerId(), normalizedPlayerId, roomCode);
        log.info("Room code (original): {}", joinDto.getRoomCode());
        log.info("Room code (normalized): {}", roomCode);
        log.info("Total rooms available: {}", rooms.size());
        log.info("Available room codes: {}", rooms.keySet());
        
        if (roomCode == null || roomCode.isEmpty()) {
            log.error("Room code is null or empty after normalization");
            throw new IllegalArgumentException("El código de sala es requerido");
        }
        
        RoomInfoDto roomInfo = rooms.get(roomCode);
        if (roomInfo == null) {
            log.error("❌ Room {} not found in rooms map", roomCode);
            log.error("💡 Available rooms: {}", rooms.keySet());
            log.error("💡 Total rooms: {}", rooms.size());
            throw new IllegalArgumentException("Sala no encontrada. Verifica el código.");
        }
        
        // Usar ID normalizado para verificar si ya está en una sala
        if (playerToRoom.containsKey(normalizedPlayerId)) {
            String existingRoomCode = playerToRoom.get(normalizedPlayerId);
            if (existingRoomCode.equals(roomCode)) {
                log.info("Player {} (normalized: {}) already in this room", joinDto.getPlayerId(), normalizedPlayerId);
                return roomInfo;
            } else {
                log.warn("Player {} (normalized: {}) already in another room {}", joinDto.getPlayerId(), normalizedPlayerId, existingRoomCode);
                throw new IllegalStateException("Ya estás en otra sala. Sal primero.");
            }
        }
        
        if (roomInfo.isFull()) {
            log.warn("Room {} is full", roomCode);
            throw new IllegalStateException("La sala está llena.");
        }
        
        // Comparar con hostId normalizado (que también está normalizado)
        if (roomInfo.getHostId() != null && roomInfo.getHostId().equals(normalizedPlayerId)) {
            log.warn("Host {} (normalized: {}) trying to join own room", joinDto.getPlayerId(), normalizedPlayerId);
            throw new IllegalStateException("No puedes unirte a tu propia sala.");
        }
        
        roomInfo.setGuestId(normalizedPlayerId);  // Guardar ID normalizado
        roomInfo.setGuestName(joinDto.getPlayerName());
        roomInfo.setFull(true);
        roomInfo.setStatus(RoomInfoDto.RoomStatus.READY);
        
        playerToRoom.put(normalizedPlayerId, roomCode);  // Usar ID normalizado como clave
        
        String gameId = gameService.createGame(roomInfo.getHostId(), roomInfo.getGuestId());
        roomInfo.setGameId(gameId);
        roomInfo.setStatus(RoomInfoDto.RoomStatus.IN_PROGRESS);
        
        log.info("Player {} (normalized: {}) joined room {}. Game {} created.", joinDto.getPlayerId(), normalizedPlayerId, roomCode, gameId);
        
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
        String normalizedPlayerId = normalizePlayerId(playerId);
        return normalizedPlayerId != null ? playerToRoom.get(normalizedPlayerId) : null;
    }

    public void leaveRoom(String playerId) {
        String normalizedPlayerId = normalizePlayerId(playerId);
        if (normalizedPlayerId == null) {
            log.warn("Cannot leave room: playerId is null");
            return;
        }
        
        String roomCode = playerToRoom.remove(normalizedPlayerId);
        if (roomCode == null) {
            log.debug("Player {} (normalized: {}) not in any room", playerId, normalizedPlayerId);
            return;
        }
        
        RoomInfoDto roomInfo = rooms.get(roomCode);
        if (roomInfo == null) {
            return;
        }
        
        log.info("Player {} (normalized: {}) leaving room {}", playerId, normalizedPlayerId, roomCode);
        
        // Comparar con IDs normalizados
        if ((roomInfo.getHostId() != null && roomInfo.getHostId().equals(normalizedPlayerId)) || 
            roomInfo.getStatus() == RoomInfoDto.RoomStatus.WAITING) {
            rooms.remove(roomCode);
            if (roomInfo.getHostId() != null) {
                playerToRoom.remove(roomInfo.getHostId());
            }
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
    
    /**
     * Método de debugging para obtener información sobre las salas activas
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Total rooms: ").append(rooms.size()).append("\n");
        sb.append("Room codes: ").append(rooms.keySet()).append("\n");
        rooms.forEach((code, roomInfo) -> {
            sb.append("  Room ").append(code).append(": ")
              .append("host=").append(roomInfo.getHostId())
              .append(", guest=").append(roomInfo.getGuestId() != null ? roomInfo.getGuestId() : "none")
              .append(", status=").append(roomInfo.getStatus())
              .append(", full=").append(roomInfo.isFull())
              .append("\n");
        });
        return sb.toString();
    }
}
