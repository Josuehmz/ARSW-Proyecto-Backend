package com.arsw.balatro.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el estado de la cola de matchmaking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatusDto {
    private String playerId;
    private boolean inQueue;
    private Integer queuePosition;
    private Integer estimatedWaitTime;  // en segundos
    private Integer playersInQueue;
}

