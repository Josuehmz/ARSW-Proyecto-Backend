# Backend Balatro Multiplayer - Sistema WebSocket (Simplificado)

## 📋 Descripción

Backend ultra-ligero para el juego multijugador Balatro, implementado con Spring Boot y WebSocket. Este servidor actúa **exclusivamente como intermediario de comunicación en tiempo real**, sin procesar lógica de juego. Toda la lógica de negocio, reglas del juego y persistencia se manejan completamente en el cliente.

### Rol del Backend

Este backend implementa una **arquitectura de clientes gruesos** donde:
- El servidor **solo empareja jugadores** (matchmaking)
- El servidor **reenvía mensajes** entre los dos jugadores de una partida sin procesarlos
- Los clientes gruesos manejan **toda** la lógica de negocio, reglas del juego y persistencia
- El backend **no conoce ni valida** las reglas del juego
- El backend solo mantiene un **registro mínimo** de partidas activas (gameId y jugadores)

## 🏗️ Arquitectura del Sistema

### Diagrama Simplificado

```
┌────────────────────────────────────────────────────────────┐
│                   Cliente Grueso 1                         │
│  (TODA la lógica de juego + Estado + Base de datos local) │
└────────────────────────┬───────────────────────────────────┘
                         │ WebSocket (STOMP)
                         │ Solo mensajes genéricos
                         │
┌────────────────────────▼───────────────────────────────────┐
│              Backend (Spring Boot)                         │
│  ┌──────────────────────────────────────────────────┐    │
│  │      WebSocketConfig (STOMP/SockJS)              │    │
│  └──────────────────────────────────────────────────┘    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐       │
│  │ Matchmaking  │  │ Game Service │  │WebSocket │       │
│  │   Service    │  │ (Simplificado)│ │Listener  │       │
│  └──────────────┘  └──────────────┘  └──────────┘       │
│                                                            │
│  Funciones:                                               │
│  ✓ Emparejar jugadores (FIFO)                            │
│  ✓ Reenviar mensajes entre jugadores                     │
│  ✓ Notificar desconexiones                               │
│  ✗ NO procesa lógica de juego                            │
│  ✗ NO valida acciones                                    │
│  ✗ NO conoce reglas del juego                            │
│                                                            │
│  Estado mínimo en memoria:                                │
│  - Cola de matchmaking                                    │
│  - gameId + player1Id + player2Id                         │
└────────────────────────┬───────────────────────────────────┘
                         │ WebSocket (STOMP)
                         │ Solo reenvío de mensajes
                         │
┌────────────────────────▼───────────────────────────────────┐
│                   Cliente Grueso 2                         │
│  (TODA la lógica de juego + Estado + Base de datos local) │
└────────────────────────────────────────────────────────────┘
```

### Flujo de Comunicación Simplificado

```
Cliente A                   Backend                    Cliente B
    |                          |                           |
    |---JOIN_QUEUE------------>|                           |
    |                          |<---JOIN_QUEUE-------------|
    |                          |                           |
    |                    [Matchmaking]                     |
    |                          |                           |
    |<--MATCH_FOUND-----------|----MATCH_FOUND----------->|
    |                          |                           |
    |  Suscribirse a          |        Suscribirse a      |
    |  /topic/game/{gameId}   |     /topic/game/{gameId}  |
    |                          |                           |
    |                    [JUEGO INICIA]                    |
    |                          |                           |
    |---GAME_MESSAGE---------->|                           |
    |  (payload: cualquier     |                           |
    |   acción del cliente)    |----RELAY MESSAGE--------->|
    |                          |                           |
    |                          |<---GAME_MESSAGE-----------|
    |<----RELAY MESSAGE--------|  (payload: respuesta      |
    |                          |   del oponente)           |
    |                          |                           |
```

**Nota importante:** El backend NO interpreta el contenido de GAME_MESSAGE. 
Solo verifica que el jugador pertenece a la partida y reenvía el mensaje al oponente.

## 📦 Estructura del Proyecto (Simplificada)

```
ARSW-Proyecto-Backend/
│
├── src/main/java/com/arsw/balatro/
│   ├── BalatroBackendApplication.java    # Clase principal
│   │
│   ├── config/                            # Configuraciones
│   │   ├── WebSocketConfig.java          # Config WebSocket/STOMP
│   │   └── CorsConfig.java               # Config CORS
│   │
│   ├── controller/                        # Controladores WebSocket
│   │   └── GameWebSocketController.java  # Endpoints WebSocket (simplificados)
│   │
│   ├── service/                           # Servicios minimalistas
│   │   ├── MatchmakingService.java       # Sistema de emparejamiento
│   │   └── GameService.java              # Registro de partidas (sin lógica)
│   │
│   ├── websocket/                         # Eventos WebSocket
│   │   └── WebSocketEventListener.java   # Listener conexión/desconexión
│   │
│   └── model/                             # Modelos de datos
│       ├── dto/                           # Data Transfer Objects
│       │   ├── GameMessage.java          # Mensaje genérico universal
│       │   ├── GameState.java            # Estado mínimo (solo IDs)
│       │   ├── MatchFoundDto.java        # Datos de match encontrado
│       │   └── QueueStatusDto.java       # Estado de cola
│       └── enums/
│           └── MessageType.java          # Tipos de mensaje simplificados
│
├── src/main/resources/
│   └── application.properties             # Configuración de la app
│
├── pom.xml                                # Dependencias Maven
├── target/                                # Archivos compilados (generado)
├── LICENSE                                # Licencia MIT
└── README.md                              # Este archivo
```

**Archivos eliminados en la simplificación:**
- ❌ `PlayerAction.java` - Ya no se procesa lógica de juego
- ❌ `GamePhase.java` - Ya no se manejan fases de juego

## 🔧 Componentes Principales (Simplificados)

### 1. WebSocketConfig - Configuración de WebSocket

**Ubicación:** `config/WebSocketConfig.java`

**¿Qué hace?**
Configura el sistema de comunicación en tiempo real usando STOMP sobre WebSocket.

**Configuración:**
- **Broker en memoria** para enrutamiento de mensajes:
  - `/topic/*`: Broadcast a todos los suscriptores de un juego
  - `/queue/*`: Mensajes privados a un jugador específico
- **Prefijo `/app`**: Para mensajes que van al backend
- **Prefijo `/user`**: Para mensajes dirigidos a usuarios específicos
- **Endpoint `/ws`**: Con soporte para SockJS

**Sin cambios** - Esta configuración se mantiene igual, solo cambia cómo se usan los endpoints.

### 2. CorsConfig - Configuración de CORS

**Ubicación:** `config/CorsConfig.java`

**¿Qué hace?**
Configura CORS para permitir conexiones desde el cliente (frontend).

**Sin cambios** - Permite conexiones desde localhost:3000, localhost:5173, etc.

### 3. GameWebSocketController - Intermediario de Mensajes (Simplificado)

**Ubicación:** `controller/GameWebSocketController.java`

**¿Qué hace?**
Actúa como **intermediario puro** - recibe mensajes y los reenvía sin procesarlos.

**Endpoints simplificados:**

| Endpoint | Tipo | Descripción |
|----------|------|-------------|
| `/app/matchmaking/join` | Enviar | Unirse a la cola de matchmaking |
| `/app/matchmaking/leave` | Enviar | Salir de la cola de matchmaking |
| `/app/game/{gameId}/message` | Enviar | **Enviar mensaje genérico al oponente** |
| `/app/game/{gameId}/chat` | Enviar | Enviar mensaje de chat |
| `/app/game/{gameId}/emote` | Enviar | Enviar emote |
| `/app/ping` | Enviar | Ping para keep-alive |
| `/user/queue/matchmaking` | Suscribir | Recibir notificaciones de matchmaking |
| `/topic/game/{gameId}` | Suscribir | **Recibir todos los mensajes del juego** |
| `/topic/game/{gameId}/chat` | Suscribir | Recibir mensajes de chat |
| `/queue/errors` | Suscribir | Recibir mensajes de error |

**Cambio principal:**
- ❌ **Eliminado:** `/app/game/{gameId}/action` - Ya no procesa acciones específicas
- ❌ **Eliminado:** `/app/game/{gameId}/ready` - Ya no maneja estado de jugadores
- ✅ **Nuevo:** `/app/game/{gameId}/message` - **Solo reenvía** el mensaje completo al oponente

**¿Cómo funciona el reenvío?**
1. Cliente A envía mensaje a `/app/game/{gameId}/message`
2. Backend verifica que el jugador pertenece al juego
3. Backend **reenvía sin procesar** a `/topic/game/{gameId}`
4. Ambos clientes (A y B) reciben el mensaje
5. **Los clientes deciden** qué hacer con el mensaje

### 4. MatchmakingService - Sistema de Emparejamiento

**Ubicación:** `service/MatchmakingService.java`

**¿Qué hace?**
Gestiona la cola de jugadores esperando partida y los empareja automáticamente.

**Sin cambios funcionales** - Este servicio se mantiene prácticamente igual:

```java
// Estructuras de datos thread-safe
ConcurrentLinkedQueue<PlayerInQueue> matchmakingQueue
ConcurrentHashMap<String, PlayerInQueue> playersInQueue
```

**Proceso de matchmaking:**

1. **Jugador entra a la cola (`addToQueue`)** - FIFO
2. **Emparejamiento automático (`tryMatchmaking`)** - Toma los 2 primeros
3. **Crear partida (`createMatch`)** - Crea gameId y notifica a ambos jugadores

**Sin cambios** - El matchmaking funciona igual, solo que ahora crea partidas simplificadas.

### 5. GameService - Registro Minimalista de Partidas (Simplificado)

**Ubicación:** `service/GameService.java`

**¿Qué hace?**
**Solo mantiene un registro** de qué jugadores están en qué partida. **No procesa lógica de juego.**

**Estructuras de datos (simplificadas):**

```java
ConcurrentHashMap<String, GameState> activeGames    // gameId -> {gameId, player1Id, player2Id, timestamps}
ConcurrentHashMap<String, String> playerToGame      // playerId -> gameId
ScheduledExecutorService scheduler                   // Solo para limpieza
```

**Métodos simplificados:**

#### `createGame(player1Id, player2Id)`
- Genera un UUID único para la partida
- Crea `GameState` **minimalista** con solo:
  - `gameId`
  - `player1Id`
  - `player2Id`
  - `createdAt`
  - `lastUpdate`
- Almacena en mapas thread-safe
- Retorna el gameId

#### `isPlayerInGame(gameId, playerId)`
- Verifica si un jugador pertenece a una partida
- **Usado solo para validar permisos de envío de mensajes**

#### `getOpponentId(gameId, playerId)`
- Obtiene el ID del oponente
- **Útil para reenvío directo de mensajes**

#### `updateGameActivity(gameId)`
- Actualiza el timestamp de última actividad
- **Usado para evitar limpieza prematura**

#### `scheduleGameCleanup(gameId, seconds)`
- Programa limpieza del juego después de N segundos
- **Solo limpia de memoria, no declara ganador**
- El cliente decide qué hacer con desconexiones

**Eliminado:**
- ❌ `markPlayerReady()` - Ya no se maneja estado de jugadores
- ❌ `processAction()` - Ya no se procesa lógica de juego
- ❌ `createStandardDeck()` - Ya no se crean mazos
- ❌ `dealInitialCards()` - Ya no se reparten cartas
- ❌ `calculateScore()` - Ya no se calcula puntaje
- ❌ Todo lo relacionado con tienda, jokers, cartas, dinero, etc.

### 6. WebSocketEventListener - Manejo de Eventos (Simplificado)

**Ubicación:** `websocket/WebSocketEventListener.java`

**¿Qué hace?**
Escucha eventos de conexión/desconexión para limpiar recursos.

**Eventos manejados:**

#### `SessionConnectedEvent`
- Registra en logs el sessionId
- **Sin cambios**

#### `SessionDisconnectEvent`
- Remueve al jugador de la cola de matchmaking
- Si estaba en un juego activo:
  - Notifica al oponente con `PLAYER_DISCONNECTED`
  - Programa limpieza del juego (60 segundos)

**Cambios:**
- ❌ Ya no declara ganador por abandono
- ❌ Ya no programa timeout de reconexión
- ✅ Solo notifica desconexión y limpia recursos
- **El cliente decide** cómo manejar desconexiones del oponente

### 7. Modelos de Datos (DTOs) - Simplificados

#### GameMessage (Sin cambios - Universal)
```java
{
  MessageType type;          // Tipo de mensaje
  String gameId;             // ID del juego
  String playerId;           // ID del jugador
  Object payload;            // Datos del mensaje (cualquier objeto)
  LocalDateTime timestamp;   // Marca de tiempo
  String message;            // Mensaje opcional
}
```

**¿Por qué un DTO genérico?**
Es perfecto para el rol de intermediario. El campo `payload` puede contener **cualquier cosa** - el backend no lo interpreta, solo lo reenvía.

#### GameState (Simplificado drásticamente)
```java
{
  String gameId;           // ID único del juego
  String player1Id;        // ID del jugador 1
  String player2Id;        // ID del jugador 2
  Long createdAt;          // Timestamp de creación
  Long lastUpdate;         // Timestamp de última actividad
}
```

**¿Qué se eliminó?**
- ❌ `currentPhase` - Ya no se manejan fases
- ❌ `currentRound` - Ya no se rastrean rondas
- ❌ `player1/player2` (objetos complejos) - Ya no se guarda estado de jugadores
- ❌ `currentRoundInfo` - Ya no hay info de rondas
- ❌ `winnerId` - Ya no se declara ganador

**El cliente maneja su propio estado completo del juego.**

#### PlayerAction - ELIMINADO
Este DTO fue completamente eliminado. Ya no se necesita porque el backend no procesa acciones específicas del juego.

**En su lugar:** Los clientes envían `GameMessage` con cualquier `payload` que necesiten.

## 🔄 Protocolo de Comunicación (Simplificado)

### Conexión Inicial con Autenticación Cognito

**⚠️ IMPORTANTE:** El backend requiere autenticación con token JWT de AWS Cognito. Debes enviar el token en el header `Authorization` del mensaje STOMP CONNECT.

```javascript
// Obtener el token de Cognito (ejemplo con AWS Amplify)
import { getCurrentUser, fetchAuthSession } from 'aws-amplify/auth';

async function connectWebSocket() {
    try {
        // Obtener el token de acceso de Cognito
        const session = await fetchAuthSession();
        const token = session.tokens?.accessToken?.toString();
        
        if (!token) {
            console.error('❌ No se encontró token de autenticación');
            return;
        }
        
        // Crear conexión WebSocket
        const socket = new SockJS('http://localhost:8080/ws');
        const stompClient = Stomp.over(socket);
        
        // Conectar con el token en los headers
        stompClient.connect({
            'Authorization': `Bearer ${token}`
        }, function(frame) {
            console.log('✅ Conectado al servidor');
            
            // Suscribirse a notificaciones de matchmaking
            stompClient.subscribe('/user/queue/matchmaking', function(message) {
                const data = JSON.parse(message.body);
                if (data.type === 'MATCH_FOUND') {
                    // Unirse al juego
                }
            });
        }, function(error) {
            console.error('❌ Error de conexión:', error);
            console.error('💡 Verifica que el token sea válido y no haya expirado');
        });
        
    } catch (error) {
        console.error('❌ Error al obtener token:', error);
    }
}
```

**Ejemplo con @stomp/stompjs (versión moderna):**

```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

async function connectWebSocket() {
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
            console.error('💡 Verifica que el token sea válido');
        },
        onWebSocketError: (event) => {
            console.error('❌ Error WebSocket:', event);
        }
    });
    
    client.activate();
    return client;
}
```

**Errores comunes:**
- ❌ `400 Bad Request` de Cognito: El token puede estar expirado o ser inválido
- ❌ `Failed to send message to ExecutorSubscribableChannel`: El token no se está enviando correctamente en el header `Authorization`
- ❌ `Token de autenticación requerido`: El frontend no está enviando el header `Authorization` en el mensaje CONNECT

### Flujo Completo de una Partida (Simplificado)

#### 1. Matchmaking (Sin cambios)
```javascript
// Cliente A y B envían
stompClient.send("/app/matchmaking/join", {}, JSON.stringify({
    type: "JOIN_QUEUE",
    playerId: "player-uuid"
}));

// Servidor responde a ambos cuando hay match
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

#### 2. Suscripción al juego (Sin cambios)
```javascript
stompClient.subscribe(`/topic/game/${gameId}`, function(message) {
    const data = JSON.parse(message.body);
    // El cliente interpreta el mensaje según el tipo
    handleGameMessage(data);
});
```

#### 3. Enviar mensajes genéricos al oponente (NUEVO)
```javascript
// Cliente A envía cualquier tipo de mensaje
stompClient.send(`/app/game/${gameId}/message`, {}, JSON.stringify({
    type: "GAME_MESSAGE",  // o cualquier tipo que el cliente defina
    playerId: "player-uuid",
    gameId: gameId,
    payload: {
        // CUALQUIER ESTRUCTURA QUE EL CLIENTE NECESITE
        action: "PLAY_HAND",
        cards: ["card1", "card2"],
        score: 500,
        // ... lo que sea necesario
    }
}));

// El servidor REENVÍA sin procesar a ambos jugadores
// Ambos clientes (A y B) reciben:
{
    type: "GAME_MESSAGE",
    gameId: "game-uuid",
    playerId: "player-uuid",  // quien lo envió
    timestamp: "2024-...",
    payload: {
        action: "PLAY_HAND",
        cards: ["card1", "card2"],
        score: 500
    }
}
```

**Clave:** El backend NO valida ni interpreta el `payload`. Solo verifica que el jugador pertenece al juego y reenvía el mensaje.

## 🚀 Instalación y Uso

### Requisitos
- Java 17 o superior
- Maven 3.8+

### Pasos de Instalación

1. **Clonar el repositorio y cambiar a rama develop**
```bash
git clone <repo-url>
cd ARSW-Proyecto-Backend
git checkout develop
```

2. **Compilar el proyecto**
```bash
mvn clean install
```

3. **Ejecutar el servidor**
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

**Archivo:** `application.properties`

```properties
# Puerto del servidor
server.port=8080

# Orígenes permitidos para WebSocket y CORS
spring.websocket.servlet.allowed-origins=http://localhost:3000,http://localhost:5173
cors.allowed-origins=http://localhost:3000,http://localhost:5173

# Tamaño máximo de mensajes WebSocket (64KB)
spring.websocket.message-size-limit=65536

# Nivel de logging
logging.level.com.arsw.balatro=DEBUG
```

**Modificar orígenes permitidos:**
Agregar los puertos donde correrá tu cliente al campo `allowed-origins`.

## 🧪 Prueba del Sistema Simplificado

### Cliente de Prueba en JavaScript

```html
<!DOCTYPE html>
<html>
<head>
    <title>Test Balatro Backend (Simplificado)</title>
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
</head>
<body>
    <h1>Balatro Backend Test - Intermediario Puro</h1>
    <button onclick="connect()">Conectar</button>
    <button onclick="joinQueue()">Unirse a Cola</button>
    <button onclick="sendTestMessage()">Enviar Mensaje de Prueba</button>
    <pre id="log"></pre>

    <script>
        let stompClient = null;
        let currentGameId = null;
        const playerId = 'test-' + Math.random().toString(36).substr(2, 9);

        function connect() {
            const socket = new SockJS('http://localhost:8080/ws');
            stompClient = Stomp.over(socket);

            stompClient.connect({}, function(frame) {
                log('✅ Conectado al servidor');

                // Suscribirse a notificaciones de matchmaking
                stompClient.subscribe('/user/queue/matchmaking', function(message) {
                    log('📬 Matchmaking: ' + message.body);
                    const data = JSON.parse(message.body);
                    if (data.type === 'MATCH_FOUND') {
                        currentGameId = data.gameId;
                        subscribeToGame(data.gameId);
                    }
                });
            });
        }

        function joinQueue() {
            stompClient.send('/app/matchmaking/join', {}, JSON.stringify({
                type: 'JOIN_QUEUE',
                playerId: playerId
            }));
            log('📤 Enviado JOIN_QUEUE con ID: ' + playerId);
        }

        function subscribeToGame(gameId) {
            log('🎮 Suscrito al juego: ' + gameId);
            stompClient.subscribe(`/topic/game/${gameId}`, function(message) {
                const data = JSON.parse(message.body);
                log('📥 Mensaje recibido: ' + JSON.stringify(data, null, 2));
            });
        }

        function sendTestMessage() {
            if (!currentGameId) {
                log('❌ No estás en un juego. Une a la cola primero.');
                return;
            }
            
            // Enviar mensaje genérico al oponente
            stompClient.send(`/app/game/${currentGameId}/message`, {}, JSON.stringify({
                type: 'GAME_MESSAGE',
                playerId: playerId,
                gameId: currentGameId,
                payload: {
                    action: 'TEST_ACTION',
                    data: 'Mensaje de prueba desde ' + playerId,
                    timestamp: Date.now()
                }
            }));
            log('📤 Mensaje de prueba enviado');
        }

        function log(msg) {
            const logEl = document.getElementById('log');
            logEl.textContent += new Date().toISOString() + ': ' + msg + '\n';
            logEl.scrollTop = logEl.scrollHeight;
        }
    </script>
</body>
</html>
```

**Cómo probar:**
1. Abre el archivo en **DOS navegadores diferentes**
2. Haz clic en "Conectar" en ambos
3. Haz clic en "Unirse a Cola" en ambos
4. Deberías ver "MATCH_FOUND" en ambos navegadores
5. Haz clic en "Enviar Mensaje de Prueba" en uno
6. **Ambos navegadores** deberían recibir el mensaje (el que envía y el que recibe)
7. Verifica que el backend **solo reenvía** el mensaje sin modificarlo

## 💡 Decisiones de Diseño Simplificado

### ¿Por qué un intermediario puro?

Este backend fue **drásticamente simplificado** para eliminar toda lógica de juego:

1. **Separación de responsabilidades:**
   - Backend: Solo comunicación y matchmaking
   - Cliente: TODA la lógica del juego, validaciones, estado
   - Ventaja: Los clientes pueden implementar variaciones del juego sin cambiar el backend

2. **Escalabilidad:**
   - El backend no procesa nada complejo, solo reenvía bytes
   - Puede manejar miles de partidas simultáneas
   - El cuello de botella está en el cliente, no en el servidor

3. **Desarrollo desacoplado:**
   - Frontend y backend pueden desarrollarse independientemente
   - Cambios en reglas del juego no requieren deploy del backend
   - Facilita pruebas y debugging (cada cliente es autónomo)

### ¿Por qué sin base de datos?

1. **No hay nada que persistir:**
   - El backend solo guarda `gameId + player1Id + player2Id`
   - No hay cartas, puntajes, ni estado de juego
   - Las partidas son sesiones temporales de comunicación

2. **Simplicidad extrema:**
   - No requiere configuración de base de datos
   - Despliegue trivial (un solo JAR)
   - Menos latencia (todo en memoria)

**Limitación:** Si el servidor se reinicia, las partidas activas se pierden. Para producción, agregar Redis solo para el registro de partidas.

### ¿Por qué STOMP sobre WebSocket puro?

STOMP simplifica enormemente el desarrollo:

1. **Routing automático:** `/topic/game/{gameId}` enruta mensajes automáticamente
2. **Suscripciones:** Los clientes se suscriben y reciben solo mensajes relevantes
3. **Broadcasts:** Un mensaje a `/topic/game/{gameId}` llega a ambos jugadores
4. **Compatibilidad:** Librerías en todos los lenguajes (JS, Java, Python, etc.)

### ¿Por qué thread-safe collections?

Múltiples jugadores conectándose simultáneamente generan:

- Múltiples threads procesando matchmaking
- Accesos concurrentes al registro de partidas
- Mensajes simultáneos de diferentes partidas

`ConcurrentHashMap` y `ConcurrentLinkedQueue` garantizan thread-safety sin locks manuales.

## 📊 Flujo de Mensajes Simplificado

### Tipos de Mensajes (MessageType) - Simplificados

```java
// Matchmaking
JOIN_QUEUE          // Unirse a cola
LEAVE_QUEUE         // Salir de cola
MATCH_FOUND         // Match encontrado

// Mensajes de juego (genéricos)
GAME_MESSAGE        // Mensaje genérico que el backend reenvía sin procesar

// Comunicación
CHAT_MESSAGE        // Mensaje de chat
PLAYER_EMOTE        // Emote

// Eventos
PLAYER_CONNECTED    // Jugador conectado
PLAYER_DISCONNECTED // Jugador desconectado

// Keep-alive
PING                // Ping de cliente
PONG                // Respuesta del servidor

// Errores
ERROR               // Error genérico
```

**Eliminados:**
- ❌ `GAME_START`, `GAME_END`, `ROUND_START`, `ROUND_END` - Ya no se manejan lifecycle events
- ❌ `PLAY_HAND`, `DISCARD_CARDS`, `BUY_ITEM`, `SELL_ITEM`, `REROLL_SHOP` - Ya no se procesan acciones específicas
- ❌ `GAME_STATE_UPDATE`, `PLAYER_STATE_UPDATE`, `OPPONENT_ACTION`, `SHOP_UPDATE` - Ya no se gestiona estado
- ❌ `INVALID_ACTION` - Ya no se validan acciones

**Nuevo:**
- ✅ `GAME_MESSAGE` - Mensaje universal que los clientes usan para comunicarse

### Fases del Juego (GamePhase) - ELIMINADO

El enum `GamePhase` fue completamente eliminado. El backend ya no rastrea fases del juego.

**Los clientes manejan sus propias fases/estados localmente.**

## 🔐 Manejo de Errores (Simplificado)

### Validaciones Implementadas (Mínimas)

El backend solo valida lo mínimo necesario para funcionar como intermediario:

1. **Jugador no en el juego:**
   - Al intentar enviar un mensaje, se verifica que el playerId pertenezca a la partida
   - Si no pertenece, se envía error y no se reenvía el mensaje

2. **Partida no encontrada:**
   - Si el gameId no existe, se envía error al cliente

**Eliminadas todas las validaciones de lógica de juego:**
- ❌ Ya no se valida manos restantes
- ❌ Ya no se valida descartes restantes
- ❌ Ya no se valida dinero suficiente
- ❌ Ya no se valida fase del juego
- ❌ Ya no se valida turno del jugador

**Los clientes son responsables de validar sus propias reglas de juego.**

### Manejo de Desconexiones (Simplificado)

1. **Desconexión detectada:**
   - `WebSocketEventListener` captura el evento
   - Remueve jugador de cola de matchmaking
   - Notifica al oponente con `PLAYER_DISCONNECTED`

2. **Limpieza de memoria:**
   - Después de 60 segundos, se limpia el juego de memoria
   - **No se declara ganador** - el cliente decide qué hacer

**El cliente decide:**
- Si espera reconexión del oponente
- Si declara victoria por abandono
- Cuánto tiempo esperar
- Cómo notificar al usuario

## 🎯 Extensiones Futuras (Para el Backend Simplificado)

Aunque el backend actual es ultra-ligero, se podrían agregar:

### 1. Persistencia con Redis
- Mantener registro de partidas en Redis
- Permite reiniciar el servidor sin perder partidas activas
- Habilita escalado horizontal (múltiples instancias del backend)

### 2. Autenticación
- Integrar Spring Security con JWT
- Validar tokens en la conexión WebSocket
- Asociar sesiones a usuarios autenticados

### 3. Salas Privadas
- Matchmaking con código de sala
- Permitir que amigos se conecten directamente
- No requiere lógica de juego adicional

### 4. Métricas y Monitoreo
- Tiempo promedio de matchmaking
- Número de partidas activas
- Duración promedio de partidas
- **Sin procesar datos de juego**, solo métricas de infraestructura

### 5. Reconexión Inteligente
- Permitir reconexión a partida activa
- Mantener gameId en sesión
- Notificar al oponente sobre reconexión

**NO se recomienda agregar:**
- ❌ Validación de reglas de juego (rompe el propósito de cliente grueso)
- ❌ Procesamiento de lógica de juego (debe estar en el cliente)
- ❌ Almacenamiento de estado detallado del juego (solo en cliente)

## 📝 Conclusión

Este backend implementa un **sistema ultra-ligero y eficiente** como **intermediario puro** de comunicación en tiempo real.

**Características clave:**
- ✅ WebSocket con STOMP para comunicación bidireccional
- ✅ Matchmaking automático FIFO
- ✅ **Solo reenvío de mensajes** sin procesamiento de lógica
- ✅ Thread-safe para múltiples conexiones simultáneas
- ✅ Manejo básico de desconexiones
- ✅ Sin dependencias de base de datos (ultra-ligero y rápido)
- ✅ **No valida ni conoce reglas del juego**

**Lo que hace:**
- ✅ Emparejar jugadores
- ✅ Reenviar mensajes entre jugadores
- ✅ Notificar desconexiones

**Lo que NO hace:**
- ❌ Procesar lógica de juego
- ❌ Validar acciones de jugadores
- ❌ Mantener estado detallado del juego
- ❌ Declarar ganadores
- ❌ Calcular puntajes
- ❌ Manejar turnos

El diseño es **minimalista**, **escalable** y **perfecto para una arquitectura de clientes gruesos** donde **TODA la lógica de negocio reside en el cliente**. El backend es solo un "cartero" que entrega mensajes entre jugadores.

---

**Proyecto ARSW - Arquitecturas de Software**  
Universidad Escuela Colombiana de Ingeniería Julio Garavito  


**Equipo:**
- Samuel Alejandro Prieto Reyes
- Josué David Hernández Martínez
- Juan José Díaz Gómez
