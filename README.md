# Backend Balatro Multiplayer

## 📋 Descripción

Backend para el juego multijugador Balatro, implementado con Spring Boot y WebSocket. Este servidor actúa como **intermediario de comunicación en tiempo real** entre jugadores, sin procesar lógica de juego. Toda la lógica de negocio, reglas del juego y persistencia se manejan en el cliente.

### Arquitectura

Este backend implementa una **arquitectura de clientes gruesos** donde:
- El servidor **empareja jugadores** (matchmaking automático o salas privadas)
- El servidor **reenvía mensajes** entre jugadores sin procesarlos
- Los clientes manejan **toda** la lógica de negocio, reglas del juego y persistencia
- El backend **no conoce ni valida** las reglas del juego
- El backend mantiene un **registro mínimo** de partidas activas (gameId y jugadores)

## 🏗️ Arquitectura del Sistema

```
┌────────────────────────────────────────────────────────────┐
│                   Cliente 1                                │
│  (Lógica de juego + Estado + Base de datos local)         │
└────────────────────────┬───────────────────────────────────┘
                         │ WebSocket (STOMP) + JWT Cognito
                         │
┌────────────────────────▼───────────────────────────────────┐
│              Backend (Spring Boot)                         │
│  ┌──────────────────────────────────────────────────┐    │
│  │      WebSocketConfig (STOMP/SockJS)              │    │
│  │      Cognito Authentication                       │    │
│  └──────────────────────────────────────────────────┘    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐       │
│  │ Matchmaking  │  │ Room Service │  │ Game     │       │
│  │   Service    │  │ (Salas)      │  │ Service  │       │
│  └──────────────┘  └──────────────┘  └──────────┘       │
│  ┌──────────────┐  ┌──────────────┐                      │
│  │ Session      │  │ WebRTC       │                      │
│  │ Service      │  │ Signaling    │                      │
│  └──────────────┘  └──────────────┘                      │
│                                                            │
│  Funciones:                                               │
│  ✓ Autenticación con AWS Cognito (JWT)                   │
│  ✓ Emparejar jugadores (FIFO o salas privadas)          │
│  ✓ Reenviar mensajes entre jugadores                     │
│  ✓ Señalización WebRTC para chat de voz                  │
│  ✓ Notificar desconexiones                               │
│  ✗ NO procesa lógica de juego                            │
│  ✗ NO valida acciones                                    │
│  ✗ NO conoce reglas del juego                            │
└────────────────────────┬───────────────────────────────────┘
                         │ WebSocket (STOMP)
                         │
┌────────────────────────▼───────────────────────────────────┐
│                   Cliente 2                                │
│  (Lógica de juego + Estado + Base de datos local)         │
└────────────────────────────────────────────────────────────┘
```

## 🔧 Componentes Principales

### Configuración

- **WebSocketConfig**: Configura STOMP sobre WebSocket con broker en memoria
  - `/topic/*`: Broadcast a todos los suscriptores
  - `/queue/*`: Mensajes privados a un jugador específico
  - Endpoint `/ws` con soporte SockJS

- **CognitoWebSocketConfig**: Integración con AWS Cognito para autenticación JWT
  - Validación de tokens en el handshake WebSocket
  - Extracción del username de Cognito como playerId

- **SecurityConfig**: Configuración de Spring Security
  - Filtro de autenticación Cognito para peticiones HTTP
  - CORS configurado para desarrollo local

- **CorsConfig**: Permite conexiones desde localhost:3000, localhost:5173, etc.

### Controladores

- **GameWebSocketController**: Endpoints WebSocket para el juego
  - `/app/matchmaking/join` - Unirse a cola de matchmaking
  - `/app/matchmaking/leave` - Salir de cola
  - `/app/room/create` - Crear sala privada
  - `/app/room/join` - Unirse a sala con código
  - `/app/game/{gameId}/message` - Enviar mensaje genérico al oponente
  - `/app/game/{gameId}/chat` - Enviar mensaje de chat
  - `/app/game/{gameId}/emote` - Enviar emote
  - `/app/ping` - Keep-alive

- **WebRTCSignalingController**: Señalización WebRTC para chat de voz
  - `/app/webrtc/signal` - Maneja OFFER, ANSWER, ICE_CANDIDATE

### Servicios

- **MatchmakingService**: Sistema de emparejamiento automático FIFO
  - Cola thread-safe de jugadores esperando partida
  - Emparejamiento automático cuando hay 2+ jugadores
  - Notificación a ambos jugadores cuando se encuentra match

- **RoomService**: Gestión de salas privadas
  - Creación de salas con código único (6 caracteres)
  - Unión a salas mediante código
  - Máximo 2 jugadores por sala
  - Limpieza automática cuando se abandona

- **GameService**: Registro minimalista de partidas
  - Mantiene solo: gameId, player1Id, player2Id, timestamps
  - Validación de pertenencia a partida para envío de mensajes
  - Limpieza automática de partidas inactivas

- **SessionService**: Gestión de sesiones WebSocket
  - Mapeo playerId ↔ sessionId
  - Registro de sesiones activas
  - Limpieza al desconectar

- **CognitoTokenValidationService**: Validación de tokens JWT de AWS Cognito
  - Validación de firma usando JWK
  - Extracción de claims (username, sub, etc.)

### Eventos

- **WebSocketEventListener**: Manejo de conexiones/desconexiones
  - Registro de nuevas conexiones
  - Limpieza de recursos al desconectar
  - Notificación al oponente sobre desconexiones

## 🔐 Autenticación

El backend requiere autenticación con **AWS Cognito** usando tokens JWT.

### Conexión WebSocket con Cognito

```javascript
import { fetchAuthSession } from 'aws-amplify/auth';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

async function connectWebSocket() {
    // Obtener token de Cognito
    const session = await fetchAuthSession();
    const token = session.tokens?.accessToken?.toString();
    
    if (!token) {
        throw new Error('No se encontró token de autenticación');
    }
    
    const client = new Client({
        webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
        connectHeaders: {
            'Authorization': `Bearer ${token}`
        },
        onConnect: (frame) => {
            console.log('✅ Conectado al servidor');
            
            // Suscribirse a notificaciones
            client.subscribe('/user/queue/matchmaking', (message) => {
                const data = JSON.parse(message.body);
                // Manejar mensaje
            });
        },
        onStompError: (frame) => {
            console.error('❌ Error STOMP:', frame);
        }
    });
    
    client.activate();
    return client;
}
```

**Importante:** El token debe enviarse en el header `Authorization` del mensaje STOMP CONNECT.

## 📦 Estructura del Proyecto

```
ARSW-Proyecto-Backend/
│
├── src/main/java/com/arsw/balatro/
│   ├── BalatroBackendApplication.java    # Clase principal
│   │
│   ├── config/                            # Configuraciones
│   │   ├── WebSocketConfig.java          # Config WebSocket/STOMP
│   │   ├── CognitoWebSocketConfig.java   # Config Cognito para WebSocket
│   │   ├── SecurityConfig.java           # Config Spring Security
│   │   ├── CorsConfig.java                # Config CORS
│   │   └── ...
│   │
│   ├── controller/                        # Controladores WebSocket
│   │   ├── GameWebSocketController.java  # Endpoints del juego
│   │   └── WebRTCSignalingController.java # Señalización WebRTC
│   │
│   ├── service/                           # Servicios
│   │   ├── MatchmakingService.java       # Sistema de emparejamiento
│   │   ├── RoomService.java              # Gestión de salas privadas
│   │   ├── GameService.java              # Registro de partidas
│   │   ├── SessionService.java           # Gestión de sesiones
│   │   └── CognitoTokenValidationService.java # Validación JWT
│   │
│   ├── websocket/                         # Eventos WebSocket
│   │   └── WebSocketEventListener.java   # Listener conexión/desconexión
│   │
│   ├── security/                         # Seguridad
│   │   └── CognitoAuthenticationFilter.java # Filtro de autenticación
│   │
│   └── model/                             # Modelos de datos
│       ├── dto/                           # Data Transfer Objects
│       │   ├── GameMessage.java          # Mensaje genérico universal
│       │   ├── GameState.java            # Estado mínimo (solo IDs)
│       │   ├── MatchFoundDto.java        # Datos de match encontrado
│       │   ├── RoomInfoDto.java         # Información de sala
│       │   ├── SignalingMessage.java    # Mensaje WebRTC
│       │   └── ...
│       └── enums/
│           └── MessageType.java          # Tipos de mensaje
│
├── src/main/resources/
│   └── application.properties             # Configuración de la app
│
├── pom.xml                                # Dependencias Maven
└── README.md                              # Este archivo
```

## 🚀 Instalación y Uso

### Requisitos

- Java 17 o superior
- Maven 3.8+
- AWS Cognito configurado (para autenticación)

### Pasos de Instalación

1. **Clonar el repositorio**
```bash
git clone <repo-url>
cd ARSW-Proyecto-Backend
```

2. **Configurar AWS Cognito**

Editar `src/main/resources/application.properties`:
```properties
# AWS Cognito
aws.cognito.userPoolId=tu-user-pool-id
aws.cognito.region=us-east-1
aws.cognito.jwkUrl=https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json
```

3. **Compilar el proyecto**
```bash
mvn clean install
```

4. **Ejecutar el servidor**
```bash
mvn spring-boot:run
```

El servidor estará disponible en `http://localhost:8080` y el endpoint WebSocket en `ws://localhost:8080/ws`.

### Verificar que Funciona

**Endpoint de salud:**
```bash
curl http://localhost:8080/actuator/health
```

**Logs esperados:**
```
╔════════════════════════════════════════════════════════════╗
║   🃏  Balatro Multiplayer Backend - STARTED  🃏           ║
║                                                            ║
║   Server: http://localhost:8080                            ║
║   WebSocket: ws://localhost:8080/ws                        ║
║   Health: http://localhost:8080/actuator/health            ║
║                                                            ║
║   Ready for connections! 🚀                                ║
╚════════════════════════════════════════════════════════════╝
```

### Configuración

**Archivo:** `src/main/resources/application.properties`

```properties
# Puerto del servidor
server.port=8080

# AWS Cognito
aws.cognito.userPoolId=tu-user-pool-id
aws.cognito.region=us-east-1
aws.cognito.jwkUrl=https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json

# Orígenes permitidos para WebSocket y CORS
spring.websocket.servlet.allowed-origins=http://localhost:3000,http://localhost:5173
cors.allowed-origins=http://localhost:3000,http://localhost:5173

# Tamaño máximo de mensajes WebSocket (64KB)
spring.websocket.message-size-limit=65536

# Nivel de logging
logging.level.com.arsw.balatro=INFO
```

## 🔄 Flujo de Comunicación

### 1. Matchmaking Automático

```javascript
// Cliente A y B envían
stompClient.send("/app/matchmaking/join", {}, JSON.stringify({
    type: "JOIN_QUEUE",
    playerId: "player-uuid"
}));

// Servidor responde a ambos cuando hay match
// Suscripción: /user/queue/matchmaking
{
    type: "MATCH_FOUND",
    gameId: "game-uuid",
    payload: {
        player1Id: "...",
        player2Id: "...",
        startTime: 1234567890
    }
}
```

### 2. Salas Privadas

```javascript
// Crear sala
stompClient.send("/app/room/create", {}, JSON.stringify({
    type: "CREATE_ROOM",
    playerId: "player-uuid"
}));

// Unirse a sala
stompClient.send("/app/room/join", {}, JSON.stringify({
    type: "JOIN_ROOM",
    playerId: "player-uuid",
    payload: {
        roomCode: "ABC123"
    }
}));
```

### 3. Envío de Mensajes

```javascript
// Suscribirse al juego
stompClient.subscribe(`/topic/game/${gameId}`, function(message) {
    const data = JSON.parse(message.body);
    // El cliente interpreta el mensaje según el tipo
    handleGameMessage(data);
});

// Enviar mensaje genérico al oponente
stompClient.send(`/app/game/${gameId}/message`, {}, JSON.stringify({
    type: "GAME_MESSAGE",
    playerId: "player-uuid",
    gameId: gameId,
    payload: {
        // CUALQUIER ESTRUCTURA QUE EL CLIENTE NECESITE
        action: "PLAY_HAND",
        cards: ["card1", "card2"],
        score: 500
    }
}));
```

**Importante:** El backend NO valida ni interpreta el `payload`. Solo verifica que el jugador pertenece al juego y reenvía el mensaje.

### 4. WebRTC Signaling (Chat de Voz)

```javascript
// Suscribirse a señalización WebRTC
stompClient.subscribe(`/user/queue/webrtc/${gameId}`, function(message) {
    const signaling = JSON.parse(message.body);
    // Manejar OFFER, ANSWER, ICE_CANDIDATE
    handleWebRTCSignal(signaling);
});

// Enviar señalización
stompClient.send("/app/webrtc/signal", {}, JSON.stringify({
    type: "OFFER", // o "ANSWER", "ICE_CANDIDATE"
    gameId: gameId,
    targetId: "opponent-id",
    payload: {
        sdp: "...", // o candidate, etc.
    }
}));
```

## 🧪 Pruebas

### Ejecutar Pruebas

```bash
mvn test
```

### Generar Reporte de Cobertura (JaCoCo)

```bash
mvn clean verify
```

El reporte HTML estará disponible en `target/site/jacoco/index.html`

## 📊 Características

**Lo que hace:**
- ✅ Autenticación con AWS Cognito (JWT)
- ✅ Matchmaking automático FIFO
- ✅ Salas privadas con códigos
- ✅ Reenvío de mensajes entre jugadores
- ✅ Señalización WebRTC para chat de voz
- ✅ Thread-safe para múltiples conexiones simultáneas
- ✅ Manejo de desconexiones
- ✅ Sin dependencias de base de datos (ultra-ligero)

**Lo que NO hace:**
- ❌ Procesar lógica de juego
- ❌ Validar acciones de jugadores
- ❌ Mantener estado detallado del juego
- ❌ Declarar ganadores
- ❌ Calcular puntajes
- ❌ Manejar turnos

## 💡 Decisiones de Diseño

### Arquitectura de Clientes Gruesos

Este backend fue diseñado como **intermediario puro** de comunicación:

1. **Separación de responsabilidades:**
   - Backend: Solo comunicación y matchmaking
   - Cliente: TODA la lógica del juego, validaciones, estado

2. **Escalabilidad:**
   - El backend no procesa lógica compleja, solo reenvía mensajes
   - Puede manejar miles de partidas simultáneas
   - El cuello de botella está en el cliente, no en el servidor

3. **Desarrollo desacoplado:**
   - Frontend y backend pueden desarrollarse independientemente
   - Cambios en reglas del juego no requieren deploy del backend

### Sin Base de Datos

- No hay nada que persistir: solo `gameId + player1Id + player2Id`
- No hay cartas, puntajes, ni estado de juego
- Las partidas son sesiones temporales de comunicación
- Despliegue trivial (un solo JAR)

**Limitación:** Si el servidor se reinicia, las partidas activas se pierden. Para producción, considerar Redis solo para el registro de partidas.

### STOMP sobre WebSocket

STOMP simplifica el desarrollo:
- Routing automático: `/topic/game/{gameId}` enruta mensajes automáticamente
- Suscripciones: Los clientes se suscriben y reciben solo mensajes relevantes
- Broadcasts: Un mensaje a `/topic/game/{gameId}` llega a ambos jugadores
- Compatibilidad: Librerías en todos los lenguajes

---

**Proyecto ARSW - Arquitecturas de Software**  
Universidad Escuela Colombiana de Ingeniería Julio Garavito

**Equipo:**
- Samuel Alejandro Prieto Reyes
- Josué David Hernández Martínez
- Juan José Díaz Gómez
