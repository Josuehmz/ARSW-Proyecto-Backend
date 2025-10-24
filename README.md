# Backend Balatro Multiplayer - Sistema WebSocket

## 📋 Descripción

Backend ligero para el juego multijugador Balatro, implementado con Spring Boot y WebSocket. Este servidor actúa como **intermediario de comunicación en tiempo real** entre clientes gruesos, sin manejar persistencia de datos. La lógica de negocio y el almacenamiento se gestionan completamente en el cliente.

### Rol del Backend

Este backend implementa una **arquitectura de clientes gruesos** donde:
- El servidor solo gestiona la **comunicación en tiempo real** entre jugadores
- El servidor mantiene el **estado temporal de las partidas** en memoria
- Los clientes gruesos manejan su propia lógica de negocio y persistencia
- El backend garantiza la **sincronización** entre los dos jugadores de una partida

## 🏗️ Arquitectura del Sistema

### Diagrama de Componentes

```
┌─────────────────────────────────────────────────────────┐
│                   Cliente Grueso 1                       │
│  (Lógica de juego + Base de datos local)                │
└──────────────────────┬──────────────────────────────────┘
                       │ WebSocket (STOMP)
                       │
┌──────────────────────▼──────────────────────────────────┐
│              Backend (Spring Boot)                       │
│  ┌────────────────────────────────────────────────┐    │
│  │      WebSocketConfig (STOMP/SockJS)            │    │
│  └────────────────────────────────────────────────┘    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐    │
│  │ Matchmaking  │  │ Game Service │  │WebSocket │    │
│  │   Service    │  │              │  │Listener  │    │
│  └──────────────┘  └──────────────┘  └──────────┘    │
│                                                         │
│  Estado en memoria (ConcurrentHashMap):                │
│  - Partidas activas                                    │
│  - Cola de matchmaking                                 │
│  - Mapeo jugador -> partida                            │
└──────────────────────┬──────────────────────────────────┘
                       │ WebSocket (STOMP)
                       │
┌──────────────────────▼──────────────────────────────────┐
│                   Cliente Grueso 2                       │
│  (Lógica de juego + Base de datos local)                │
└──────────────────────────────────────────────────────────┘
```

### Flujo de Comunicación

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
    |---READY----------------->|<---READY------------------|
    |                          |                           |
    |<--GAME_START------------|----GAME_START------------>|
    |                          |                           |
    |---PLAY_HAND------------->|                           |
    |                          |----GAME_STATE_UPDATE----->|
    |<--GAME_STATE_UPDATE-----|                           |
```

## 📦 Estructura del Proyecto

```
backend/
├── src/main/java/com/arsw/balatro/
│   ├── BalatroBackendApplication.java    # Clase principal
│   │
│   ├── config/                            # Configuraciones
│   │   ├── WebSocketConfig.java          # Config WebSocket/STOMP
│   │   └── CorsConfig.java               # Config CORS
│   │
│   ├── controller/                        # Controladores WebSocket
│   │   └── GameWebSocketController.java  # Endpoints WebSocket
│   │
│   ├── service/                           # Lógica de negocio
│   │   ├── MatchmakingService.java       # Sistema de emparejamiento
│   │   └── GameService.java              # Gestión de partidas
│   │
│   ├── websocket/                         # Eventos WebSocket
│   │   └── WebSocketEventListener.java   # Listener conexión/desconexión
│   │
│   └── model/                             # Modelos de datos
│       ├── dto/                           # Data Transfer Objects
│       │   ├── GameMessage.java          # Mensaje genérico
│       │   ├── PlayerAction.java         # Acción del jugador
│       │   ├── GameState.java            # Estado del juego
│       │   ├── MatchFoundDto.java        # Datos de match encontrado
│       │   └── QueueStatusDto.java       # Estado de cola
│       └── enums/
│           ├── MessageType.java          # Tipos de mensaje
│           └── GamePhase.java            # Fases del juego
│
├── src/main/resources/
│   └── application.properties             # Configuración de la app
│
└── pom.xml                                # Dependencias Maven
```

## 🔧 Componentes Principales

### 1. WebSocketConfig - Configuración de WebSocket

**Ubicación:** `config/WebSocketConfig.java`

**¿Qué hace?**
Configura el sistema de comunicación en tiempo real usando STOMP (Simple Text Oriented Messaging Protocol) sobre WebSocket.

**¿Cómo funciona?**
- Habilita un **broker de mensajes en memoria** con dos tipos de destinos:
  - `/topic/*`: Para mensajes broadcast (muchos suscriptores)
  - `/queue/*`: Para mensajes punto a punto (un solo destinatario)
- Define el prefijo `/app` para mensajes que van hacia los controladores
- Configura `/user` para mensajes dirigidos a usuarios específicos
- Registra el endpoint `/ws` con soporte para SockJS (fallback para navegadores antiguos)

**¿Por qué STOMP?**
- Proporciona una capa de abstracción sobre WebSocket puro
- Maneja automáticamente el routing de mensajes
- Soporta suscripciones y publicaciones de forma nativa
- Compatible con múltiples clientes (JavaScript, Java, etc.)

### 2. CorsConfig - Configuración de CORS

**Ubicación:** `config/CorsConfig.java`

**¿Qué hace?**
Configura CORS (Cross-Origin Resource Sharing) para permitir que clientes desde diferentes orígenes se conecten al backend.

**¿Por qué es necesario?**
Los navegadores bloquean por defecto las conexiones WebSocket desde orígenes diferentes. Esta configuración permite que el frontend (en localhost:3000 o localhost:5173) pueda conectarse al backend (en localhost:8080).

### 3. GameWebSocketController - Controlador de Mensajes

**Ubicación:** `controller/GameWebSocketController.java`

**¿Qué hace?**
Define los endpoints WebSocket que los clientes pueden invocar. Actúa como el punto de entrada para todas las acciones del cliente.

**Endpoints implementados:**

| Endpoint | Tipo | Descripción |
|----------|------|-------------|
| `/app/matchmaking/join` | Enviar | Unirse a la cola de matchmaking |
| `/app/matchmaking/leave` | Enviar | Salir de la cola de matchmaking |
| `/app/game/{gameId}/action` | Enviar | Realizar una acción en el juego |
| `/app/game/{gameId}/ready` | Enviar | Marcar jugador como listo |
| `/app/game/{gameId}/chat` | Enviar | Enviar mensaje de chat |
| `/app/game/{gameId}/emote` | Enviar | Enviar emote |
| `/app/ping` | Enviar | Ping para keep-alive |
| `/user/queue/matchmaking` | Suscribir | Recibir notificaciones de matchmaking |
| `/topic/game/{gameId}` | Suscribir | Recibir actualizaciones del juego |
| `/topic/game/{gameId}/chat` | Suscribir | Recibir mensajes de chat |
| `/queue/errors` | Suscribir | Recibir mensajes de error |

**¿Cómo funciona?**
1. Recibe un mensaje del cliente a través de `@MessageMapping`
2. Extrae el ID del jugador del mensaje o del `Principal`
3. Delega el procesamiento al servicio correspondiente
4. Envía la respuesta usando `SimpMessagingTemplate`:
   - `convertAndSendToUser`: Para mensajes privados a un usuario
   - `convertAndSend`: Para broadcast a todos los suscriptores

### 4. MatchmakingService - Sistema de Emparejamiento

**Ubicación:** `service/MatchmakingService.java`

**¿Qué hace?**
Gestiona la cola de jugadores esperando partida y los empareja automáticamente.

**¿Cómo funciona?**

```java
// Estructuras de datos thread-safe
ConcurrentLinkedQueue<PlayerInQueue> matchmakingQueue
ConcurrentHashMap<String, PlayerInQueue> playersInQueue
```

**Proceso de matchmaking:**

1. **Jugador entra a la cola (`addToQueue`)**
   - Verifica que no esté ya en cola
   - Crea objeto `PlayerInQueue` con timestamp
   - Agrega a la cola FIFO
   - Intenta hacer match inmediatamente

2. **Intento de emparejamiento (`tryMatchmaking`)**
   - Verifica si hay al menos 2 jugadores en cola
   - Extrae los 2 primeros jugadores (FIFO)
   - Llama a `createMatch`

3. **Crear partida (`createMatch`)**
   - Crea un juego a través de `GameService`
   - Prepara `MatchFoundDto` con información de la partida
   - Notifica a ambos jugadores vía WebSocket
   - En caso de error, reintegra jugadores a la cola

**¿Por qué estructuras concurrentes?**
Porque múltiples threads pueden acceder simultáneamente cuando varios jugadores se conectan al mismo tiempo. `ConcurrentLinkedQueue` y `ConcurrentHashMap` garantizan thread-safety sin necesidad de sincronización manual.

### 5. GameService - Gestión de Partidas

**Ubicación:** `service/GameService.java`

**¿Qué hace?**
Maneja el ciclo de vida completo de las partidas: creación, estado, acciones de jugadores y finalización.

**Estructuras de datos:**

```java
ConcurrentHashMap<String, GameState> activeGames          // gameId -> estado
ConcurrentHashMap<String, String> playerToGame            // playerId -> gameId
ScheduledExecutorService scheduler                         // Para timeouts
```

**Métodos principales:**

#### `createGame(player1Id, player2Id)`
- Genera un UUID único para la partida
- Crea `GameState` inicial con ambos jugadores
- Inicializa estado de cada jugador (dinero, cartas, tienda)
- Almacena en mapas thread-safe
- Retorna el gameId

#### `markPlayerReady(gameId, playerId)`
- Marca al jugador como listo
- Si ambos están listos, inicia el juego
- Cambia fase a SHOP
- Crea mazo de 52 cartas y reparte 8 a cada jugador
- Da dinero inicial (4 a cada uno)

#### `processAction(gameId, action)`
- Valida que el jugador pertenezca al juego
- Procesa según el tipo de acción:
  - `PLAY_HAND`: Jugar mano de cartas, calcular score
  - `DISCARD_CARDS`: Descartar cartas
  - `BUY_ITEM`: Comprar en tienda
  - `SELL_ITEM`: Vender joker
  - `REROLL_SHOP`: Actualizar tienda
- Actualiza el estado del juego
- Retorna el nuevo estado

#### `scheduleGameAbandonment(gameId, playerId, seconds)`
- Programa una tarea para ejecutarse después de N segundos
- Si el jugador no se reconecta, declara victoria al oponente
- Cambia fase a GAME_OVER
- Programa limpieza del juego

**¿Por qué ScheduledExecutorService?**
Permite programar tareas asíncronas (como timeouts de reconexión) sin bloquear el thread principal. Usa un pool de 4 threads para manejar múltiples juegos simultáneamente.

**Creación del mazo:**
```java
String[] suits = {"HEARTS", "DIAMONDS", "CLUBS", "SPADES"};
String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
```
- Genera 52 cartas (13 por palo)
- Asigna fichas base según el rango (2-10 valen su número, figuras 10, A vale 11)
- Baraja aleatoriamente
- Reparte primeras 8 a la mano, el resto queda en el mazo

### 6. WebSocketEventListener - Manejo de Eventos

**Ubicación:** `websocket/WebSocketEventListener.java`

**¿Qué hace?**
Escucha eventos de conexión y desconexión de WebSocket para limpiar recursos.

**Eventos manejados:**

#### `SessionConnectedEvent`
- Se dispara cuando un cliente establece conexión WebSocket
- Registra en logs el sessionId
- Útil para debugging y tracking

#### `SessionDisconnectEvent`
- Se dispara cuando un cliente cierra la conexión
- Extrae el playerId de los atributos de sesión
- Remueve al jugador de la cola de matchmaking
- Si estaba en un juego activo:
  - Notifica al oponente
  - Programa abandono del juego (30 segundos)

**¿Por qué es importante?**
Sin este listener, los recursos quedarían "colgados":
- Jugadores fantasma en la cola de matchmaking
- Oponentes esperando eternamente
- Memoria ocupada innecesariamente

### 7. Modelos de Datos (DTOs)

#### GameMessage
```java
{
  MessageType type;          // Tipo de mensaje
  String gameId;             // ID del juego
  String playerId;           // ID del jugador
  Object payload;            // Datos del mensaje
  LocalDateTime timestamp;   // Marca de tiempo
  String message;            // Mensaje opcional
}
```

**¿Por qué un DTO genérico?**
Permite enviar cualquier tipo de mensaje con una estructura consistente. El campo `payload` puede contener diferentes objetos según el `MessageType`.

#### PlayerAction
```java
{
  MessageType actionType;    // Tipo de acción
  String playerId;           // Quién realiza la acción
  String gameId;             // En qué juego
  List<String> cardIds;      // Cartas involucradas
  String handType;           // Tipo de mano (FLUSH, PAIR, etc.)
  String itemId;             // ID del item (para compras)
  Integer jokerSlot;         // Slot del joker (para ventas)
}
```

#### GameState
```java
{
  String gameId;
  GamePhase currentPhase;    // WAITING, SHOP, PLAYING, ROUND_END, GAME_OVER
  Integer currentRound;
  PlayerState player1;       // Estado completo del jugador 1
  PlayerState player2;       // Estado completo del jugador 2
  RoundInfo currentRoundInfo;
  String winnerId;           // null si el juego continúa
}
```

**PlayerState incluye:**
- Dinero actual
- Rondas ganadas
- Score actual y objetivo
- Manos y descartes restantes
- Cartas en mano y en mazo
- Jokers equipados
- Estado de la tienda

## 🔄 Protocolo de Comunicación

### Conexión Inicial

```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Conectado al servidor');
    
    // Suscribirse a notificaciones de matchmaking
    stompClient.subscribe('/user/queue/matchmaking', function(message) {
        const data = JSON.parse(message.body);
        if (data.type === 'MATCH_FOUND') {
            // Unirse al juego
        }
    });
});
```

### Flujo Completo de una Partida

#### 1. Matchmaking
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
        player2Id: "..."
    }
}
```

#### 2. Suscripción al juego
```javascript
stompClient.subscribe(`/topic/game/${gameId}`, function(message) {
    const gameState = JSON.parse(message.body);
    // Actualizar UI con el estado del juego
});
```

#### 3. Marcar como listo
```javascript
stompClient.send(`/app/game/${gameId}/ready`, {}, JSON.stringify({
    type: "PLAYER_STATE_UPDATE",
    playerId: "player-uuid",
    gameId: gameId
}));
```

#### 4. Realizar acciones
```javascript
// Jugar mano
stompClient.send(`/app/game/${gameId}/action`, {}, JSON.stringify({
    actionType: "PLAY_HAND",
    playerId: "player-uuid",
    gameId: gameId,
    cardIds: ["card1", "card2", "card3"],
    handType: "FLUSH"
}));

// El servidor broadcast el nuevo estado a ambos jugadores
{
    type: "GAME_STATE_UPDATE",
    gameId: "game-uuid",
    payload: { /* GameState completo */ }
}
```

## 🚀 Instalación y Uso

### Requisitos
- Java 17 o superior
- Maven 3.8+

### Pasos de Instalación

1. **Clonar el repositorio y cambiar a rama develop**
```bash
git clone <repo-url>
cd ARSW-PROYECTO-BALATRO
git checkout develop
```

2. **Compilar el proyecto**
```bash
cd backend
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

## 🧪 Prueba del Sistema

### Cliente de Prueba en JavaScript

```html
<!DOCTYPE html>
<html>
<head>
    <title>Test Balatro Backend</title>
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
</head>
<body>
    <h1>Balatro Backend Test</h1>
    <button onclick="connect()">Conectar</button>
    <button onclick="joinQueue()">Unirse a Cola</button>
    <pre id="log"></pre>

    <script>
        let stompClient = null;
        const playerId = 'test-' + Math.random().toString(36).substr(2, 9);

        function connect() {
            const socket = new SockJS('http://localhost:8080/ws');
            stompClient = Stomp.over(socket);

            stompClient.connect({}, function(frame) {
                log('Conectado: ' + frame);

                stompClient.subscribe('/user/queue/matchmaking', function(message) {
                    log('Matchmaking: ' + message.body);
                    const data = JSON.parse(message.body);
                    if (data.type === 'MATCH_FOUND') {
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
            log('Enviado JOIN_QUEUE con ID: ' + playerId);
        }

        function subscribeToGame(gameId) {
            log('Suscrito al juego: ' + gameId);
            stompClient.subscribe(`/topic/game/${gameId}`, function(message) {
                log('Game update: ' + message.body);
            });
        }

        function log(msg) {
            document.getElementById('log').textContent += new Date().toISOString() + ': ' + msg + '\n';
        }
    </script>
</body>
</html>
```

**Cómo probar:**
1. Abre el archivo en DOS navegadores diferentes
2. Haz clic en "Conectar" en ambos
3. Haz clic en "Unirse a Cola" en ambos
4. Deberías ver el mensaje "MATCH_FOUND" en ambos

## 💡 Decisiones de Diseño

### ¿Por qué sin base de datos?

El backend está diseñado como **intermediario ligero** porque:

1. **Arquitectura de Clientes Gruesos:**
   - Los clientes tienen su propia lógica de negocio
   - Cada cliente maneja su base de datos local
   - El servidor solo sincroniza estado entre clientes

2. **Simplicidad:**
   - No requiere configuración de base de datos
   - Más fácil de desplegar y escalar
   - Menos latencia (todo en memoria)

3. **Enfoque del Proyecto:**
   - Demostrar comunicación en tiempo real con WebSocket
   - No requiere persistencia entre sesiones
   - Las partidas son temporales

### ¿Por qué en memoria?

El estado se mantiene en `ConcurrentHashMap` por:

1. **Velocidad:** Acceso instantáneo sin I/O
2. **Simplicidad:** No requiere ORM ni queries
3. **Suficiente:** Para partidas temporales de 10-30 minutos
4. **Thread-safe:** Soporta concurrencia nativa

**Limitación:** Si el servidor se reinicia, se pierden las partidas activas. Para producción se podría agregar Redis.

### ¿Por qué STOMP sobre WebSocket puro?

STOMP proporciona:

1. **Routing automático:** No necesitamos parsear y routear manualmente
2. **Suscripciones:** Los clientes se suscriben a topics específicos
3. **Broadcasts:** Enviar a múltiples clientes es trivial
4. **Compatibilidad:** Librerías disponibles en todos los lenguajes
5. **Fallback:** SockJS proporciona compatibilidad con navegadores antiguos

### ¿Por qué thread-safe collections?

Múltiples jugadores se conectan simultáneamente, generando:

- Múltiples threads procesando mensajes WebSocket
- Accesos concurrentes a la cola de matchmaking
- Actualizaciones simultáneas de estado de juego

`ConcurrentHashMap` y `ConcurrentLinkedQueue` garantizan operaciones atómicas sin locks explícitos, mejorando el rendimiento.

## 📊 Flujo de Mensajes Detallado

### Tipos de Mensajes (MessageType)

```java
// Matchmaking
JOIN_QUEUE          // Unirse a cola
LEAVE_QUEUE         // Salir de cola
MATCH_FOUND         // Match encontrado

// Lifecycle del juego
GAME_START          // Juego iniciado
GAME_END            // Juego terminado
ROUND_START         // Ronda iniciada
ROUND_END           // Ronda terminada

// Acciones de jugador
PLAY_HAND           // Jugar mano
DISCARD_CARDS       // Descartar cartas
BUY_ITEM            // Comprar item
SELL_ITEM           // Vender item
REROLL_SHOP         // Reroll tienda

// Actualizaciones
GAME_STATE_UPDATE   // Estado del juego actualizado
PLAYER_STATE_UPDATE // Estado de jugador actualizado

// Comunicación
CHAT_MESSAGE        // Mensaje de chat
PLAYER_EMOTE        // Emote

// Eventos
PLAYER_CONNECTED    // Jugador conectado
PLAYER_DISCONNECTED // Jugador desconectado
PING / PONG         // Keep-alive

// Errores
ERROR               // Error genérico
INVALID_ACTION      // Acción inválida
```

### Fases del Juego (GamePhase)

```java
WAITING      // Esperando que ambos jugadores estén listos
SHOP         // Fase de compra en la tienda
PLAYING      // Fase de jugada (jugar cartas)
ROUND_END    // Fin de ronda
GAME_OVER    // Juego terminado
```

## 🔐 Manejo de Errores

### Validaciones Implementadas

1. **Jugador no en el juego:**
   - Al procesar una acción, se verifica que el playerId pertenezca a la partida
   - Exception: `IllegalArgumentException("Player not in this game")`

2. **Sin manos restantes:**
   - No se puede jugar si `handsRemaining <= 0`
   - Exception: `IllegalStateException("No hands remaining")`

3. **Sin descartes restantes:**
   - No se puede descartar si `discardsRemaining <= 0`
   - Exception: `IllegalStateException("No discards remaining")`

4. **Dinero insuficiente:**
   - No se puede hacer reroll si no hay suficiente dinero
   - Exception: `IllegalStateException("Not enough money for reroll")`

### Manejo de Desconexiones

1. **Desconexión detectada:**
   - `WebSocketEventListener` captura el evento
   - Remueve jugador de cola de matchmaking
   - Notifica al oponente

2. **Tiempo de reconexión:**
   - Se dan 30 segundos para reconectar
   - Si no reconecta, se declara victoria al oponente
   - Después de 60 segundos más, se limpia el juego de memoria

## 🎯 Extensiones Futuras

Aunque el backend actual es funcional, se podrían agregar:

### 1. Persistencia con Redis
- Mantener estado de partidas en Redis
- Permite reiniciar el servidor sin perder partidas
- Habilita escalado horizontal (múltiples instancias)

### 2. Autenticación
- Integrar Spring Security con JWT
- Validar tokens en la conexión WebSocket
- Asociar sesiones a usuarios autenticados

### 3. Rankings y Estadísticas
- API REST para consultar estadísticas
- Historial de partidas
- Sistema de ELO o MMR

### 4. Matchmaking Avanzado
- Matchmaking basado en rating
- Salas privadas con código
- Torneos y eventos

### 5. Observadores
- Permitir espectadores en partidas
- Stream del estado del juego
- Chat de espectadores

## 📝 Conclusión

Este backend implementa un **sistema robusto y eficiente** para comunicación en tiempo real entre clientes gruesos. 

**Características clave:**
- ✅ WebSocket con STOMP para comunicación bidireccional
- ✅ Matchmaking automático FIFO
- ✅ Gestión de estado temporal en memoria
- ✅ Thread-safe para múltiples conexiones simultáneas
- ✅ Manejo de desconexiones y reconexiones
- ✅ Sin dependencias de base de datos (ligero y rápido)

El diseño es **modular**, **escalable** y **fácil de extender**, perfecto para una arquitectura de clientes gruesos donde la lógica de negocio reside en el cliente.

---

**Proyecto ARSW - Arquitecturas de Software**  
Universidad Escuela Colombiana de Ingeniería Julio Garavito  
2024

**Equipo:**
- Samuel Alejandro Prieto Reyes
- Josué David Hernández Martínez
- Juan José Díaz Gómez
