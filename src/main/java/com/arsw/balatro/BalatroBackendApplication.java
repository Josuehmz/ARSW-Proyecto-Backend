package com.arsw.balatro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicación principal del backend de Balatro Multijugador
 * 
 * Esta aplicación proporciona:
 * - Comunicación WebSocket en tiempo real con STOMP
 * - Sistema de matchmaking automático
 * - Gestión de partidas multijugador 1v1
 * - API REST para autenticación y perfiles
 * 
 * @author ARSW Team - Proyecto Balatro
 * @version 1.0.0
 */
@SpringBootApplication
public class BalatroBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BalatroBackendApplication.class, args);
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║   🃏  Balatro Multiplayer Backend - STARTED  🃏           ║");
        System.out.println("║                                                            ║");
        System.out.println("║   Server: http://localhost:8081                            ║");
        System.out.println("║   WebSocket: ws://localhost:8081/ws                        ║");
        System.out.println("║   Health: http://localhost:8081/actuator/health            ║");
        System.out.println("║                                                            ║");
        System.out.println("║   Ready for connections! 🚀                                ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }
}

