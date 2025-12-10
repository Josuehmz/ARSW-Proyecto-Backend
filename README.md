# Backend Balatro Multiplayer

Backend para el juego multijugador Balatro, implementado con Spring Boot y WebSocket. Actúa como **intermediario de comunicación en tiempo real** entre jugadores, con lógica de progreso y detección de victoria.

## 🎯 Funcionalidades Principales

### Comunicación en Tiempo Real
- **WebSocket con STOMP**: Comunicación bidireccional entre jugadores
- **Reenvío de mensajes**: Todos los mensajes se reenvían automáticamente a ambos jugadores
- **Señalización WebRTC**: Soporte para chat de voz entre jugadores

### Emparejamiento
- **Matchmaking automático**: Cola FIFO que empareja jugadores automáticamente
- **Salas privadas**: Creación de salas con código único (6 caracteres) para jugar con amigos

### Lógica de Progreso y Victoria ⭐
- **Seguimiento de progreso**: El backend mantiene el progreso de cada jugador (ante y blind)
- **Detección de victoria automática**: 
  - Detecta cuando un jugador supera al oponente que se quedó sin manos
  - Envía `GAME_WON` y `GAME_LOST` automáticamente
- **Manejo de empates**: Detecta cuando ambos jugadores se quedan sin manos en el mismo ante/blind
- **Comparación de progresos**: Compara ante y blind para determinar quién está más adelante
- **Timeout**: Maneja cuando un jugador se queda sin tiempo (15 segundos)

### Autenticación
- **AWS Cognito**: Autenticación JWT integrada
- **Validación de tokens**: Verificación automática en cada conexión WebSocket

## 🏗️ Arquitectura

```
Cliente 1 ←→ [Backend Spring Boot] ←→ Cliente 2
              ├─ Matchmaking
              ├─ Room Service
              ├─ Game Service (progreso + victoria)
              ├─ WebRTC Signaling
              └─ Session Management
```

## 📡 Endpoints WebSocket

### Matchmaking
- `/app/matchmaking/join` - Unirse a cola
- `/app/matchmaking/leave` - Salir de cola
- Suscripción: `/user/queue/matchmaking`

### Salas Privadas
- `/app/room/create` - Crear sala
- `/app/room/join` - Unirse a sala con código
- Suscripción: `/user/queue/room`

### Mensajes de Juego
- `/app/game/{gameId}` o `/app/game/{gameId}/message` - Enviar mensaje
- `/app/game/{gameId}/chat` - Enviar chat
- `/app/game/{gameId}/emote` - Enviar emote
- Suscripción: `/topic/game/{gameId}`

### WebRTC
- `/app/webrtc/signal` - Señalización WebRTC
- Suscripción: `/user/queue/webrtc/{gameId}`

## 🔄 Tipos de Mensajes Importantes

### ROUND_COMPLETE
Cuando un jugador completa una ronda, el backend:
1. Actualiza el progreso del jugador (ante, blind)
2. Verifica condiciones de victoria
3. Si hay victoria, envía `GAME_WON` y `GAME_LOST` automáticamente
4. Reenvía el mensaje a ambos jugadores

**Formato:**
```json
{
  "type": "ROUND_COMPLETE",
  "gameId": "...",
  "playerId": "...",
  "payload": {
    "action": "ROUND_COMPLETE",
    "data": {
      "ante": 2,
      "blind": "small",
      "score": 500
    }
  }
}
```

### GAME_LOST (no_hands)
Cuando un jugador se queda sin manos:
1. El backend registra el ante/blind donde se quedó sin manos
2. Verifica si el oponente ya está más adelante (victoria inmediata)
3. Si no, espera a que el oponente avance para verificar victoria
4. Reenvía el mensaje a ambos jugadores

**Formato:**
```json
{
  "type": "GAME_LOST",
  "gameId": "...",
  "playerId": "...",
  "payload": {
    "action": "GAME_LOST",
    "data": {
      "reason": "no_hands",
      "ante": 1,
      "blind": "big"
    }
  }
}
```

### GAME_WON / GAME_LOST
El backend envía estos mensajes automáticamente cuando detecta victoria:
- `GAME_WON` al ganador con `reason: "opponent_no_hands"` o `"opponent_timeout"`
- `GAME_LOST` al perdedor con `reason: "no_hands"` o `"timeout"`

### TIME_OUT
Cuando un jugador se queda sin tiempo (cronómetro de 15s):
- El backend reenvía el mensaje a ambos jugadores
- El oponente recibe la notificación de victoria

### Empate
Cuando ambos jugadores se quedan sin manos en el mismo ante/blind:
- El backend detecta el empate automáticamente
- Envía `GAME_LOST` con `reason: "tie"` a ambos jugadores

## 🚀 Instalación Rápida

### Requisitos
- Java 17+
- Maven 3.8+
- AWS Cognito configurado

### Pasos

1. **Clonar y configurar**
```bash
git clone <repo-url>
cd ARSW-Proyecto-Backend
```

2. **Configurar AWS Cognito** en `src/main/resources/application.properties`:
```properties
aws.cognito.userPoolId=tu-user-pool-id
aws.cognito.region=us-east-1
aws.cognito.jwkUrl=https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json
```

3. **Ejecutar**
```bash
mvn spring-boot:run
```

Servidor disponible en `http://localhost:8080`, WebSocket en `ws://localhost:8080/ws`

## 🔐 Autenticación

El backend requiere token JWT de AWS Cognito en el header `Authorization` del mensaje STOMP CONNECT:

```javascript
const client = new Client({
    webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
    connectHeaders: {
        'Authorization': `Bearer ${token}` // Token de Cognito
    }
});
```

## 🧪 Pruebas

```bash
# Ejecutar pruebas
mvn test

# Generar reporte de cobertura
mvn clean verify
# Reporte en: target/site/jacoco/index.html
```

## 📊 Características Técnicas

- ✅ Thread-safe para múltiples conexiones simultáneas
- ✅ Sin base de datos (todo en memoria)
- ✅ Limpieza automática de partidas inactivas
- ✅ Manejo robusto de desconexiones
- ✅ Logging detallado para diagnóstico

## 💡 Lógica de Progreso y Victoria

El backend mantiene estado mínimo pero crítico:

### Estado por Partida
- Progreso actual de cada jugador (ante, blind)
- Punto donde cada jugador se quedó sin manos (si aplica)
- Estado del juego (terminado, empate, ganador)

### Comparación de Progresos
- Compara ante primero, luego blind si el ante es igual
- Orden de blinds: `small` < `big` < `boss`

### Detección de Victoria
1. **Victoria inmediata**: Si el oponente ya está más adelante cuando un jugador se queda sin manos
2. **Victoria por progreso**: Si un jugador supera el ante/blind donde el oponente se quedó sin manos
3. **Empate**: Si ambos se quedan sin manos en el mismo ante/blind

---

**Proyecto ARSW - Arquitecturas de Software**  
Universidad Escuela Colombiana de Ingeniería Julio Garavito

**Equipo:**
- Samuel Alejandro Prieto Reyes
- Josué David Hernández Martínez
- Juan José Díaz Gómez
