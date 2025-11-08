package com.arsw.balatro.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomInfoDto {
    private String roomCode;
    private String gameId;
    private String hostId;
    private String hostName;
    private String guestId;
    private String guestName;
    private boolean isFull;
    private long createdAt;
    private RoomStatus status;
    
    public enum RoomStatus {
        WAITING,
        READY,
        IN_PROGRESS
    }
}
