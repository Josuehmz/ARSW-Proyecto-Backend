package com.arsw.balatro.config;

import com.arsw.balatro.service.CognitoTokenValidationService;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class CognitoWebSocketHandshakeInterceptor implements HandshakeInterceptor {
    
    private final CognitoTokenValidationService tokenValidationService;
    
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, 
                                  ServerHttpResponse response, 
                                  WebSocketHandler wsHandler, 
                                  Map<String, Object> attributes) throws Exception {
        
        log.info("=== WEBSOCKET HANDSHAKE CON COGNITO ===");
        log.info("Request URI: {}", request.getURI());
        log.info("Request Headers: {}", request.getHeaders().keySet());
        
        // El token se enviará en el mensaje CONNECT de STOMP, no en el handshake HTTP
        // Por ahora, permitimos el handshake y validaremos en el mensaje CONNECT
        // Esto permite que el cliente establezca la conexión WebSocket, pero la autenticación
        // real ocurrirá cuando se reciba el mensaje STOMP CONNECT con el header Authorization
        return true;
    }
    
    @Override
    public void afterHandshake(ServerHttpRequest request, 
                              ServerHttpResponse response, 
                              WebSocketHandler wsHandler, 
                              Exception exception) {
        if (exception != null) {
            log.error("WebSocket handshake failed: {}", exception.getMessage());
        } else {
            log.info("WebSocket handshake completed");
        }
    }
    
    /**
     * Validar token en el mensaje CONNECT de STOMP
     * Este método se llamará desde el ChannelInterceptor
     */
    public Principal validateAndExtractPrincipal(StompHeaderAccessor accessor) {
        try {
            if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
                log.warn("⚠️ No es un mensaje CONNECT o accessor es null");
                return null;
            }
            
            // Log todos los headers disponibles para debugging (siempre)
            log.info("📋 Headers disponibles en CONNECT:");
            accessor.toNativeHeaderMap().forEach((key, value) -> {
                if (key.equalsIgnoreCase("Authorization")) {
                    String authValue = value != null ? value.toString() : "null";
                    if (authValue.length() > 30) {
                        log.info("   {}: Bearer {}...", key, authValue.substring(7, 27));
                    } else {
                        log.info("   {}: {}", key, "***REDACTED***");
                    }
                } else {
                    log.info("   {}: {}", key, value);
                }
            });
            
            // Extraer token del header Authorization
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || authorization.isEmpty()) {
                log.error("❌ No se encontró header Authorization en mensaje CONNECT");
                log.error("💡 Asegúrate de que el frontend envíe el header 'Authorization: Bearer <token>' en el mensaje STOMP CONNECT");
                log.error("💡 Ejemplo en JavaScript: stompClient.connect({'Authorization': 'Bearer ' + token}, ...)");
                throw new SecurityException("Token de autenticación requerido. El header 'Authorization' no fue encontrado en el mensaje CONNECT.");
            }
            
            String token = tokenValidationService.extractTokenFromHeader(authorization);
            if (token == null) {
                log.error("❌ Formato de token inválido en header Authorization. Esperado: 'Bearer <token>'");
                log.error("💡 El header recibido fue: {}", authorization.length() > 50 ? authorization.substring(0, 50) + "..." : authorization);
                throw new SecurityException("Formato de token inválido. Esperado: 'Bearer <token>'");
            }
            
            log.info("🔐 Validando token JWT de Cognito...");
            
            // Validar token
            DecodedJWT decodedJWT = tokenValidationService.validateToken(token);
            
            // Extraer username
            String username = tokenValidationService.extractUsername(decodedJWT);
            
            // Normalizar el username (trim + lowercase) para consistencia con SessionService
            // Esto asegura que Principal.getName() devuelva el mismo formato que se usa en SessionService
            String normalizedUsername = username != null ? username.trim().toLowerCase() : null;
            
            if (normalizedUsername == null || normalizedUsername.isEmpty()) {
                log.error("❌ Username no puede ser null o vacío después de normalización");
                throw new SecurityException("Username inválido: no se pudo extraer o normalizar");
            }
            
            log.info("✅ Token válido para usuario: {} (normalized: {})", username, normalizedUsername);
            
            // Crear Principal con el username normalizado
            // Esto asegura que Principal.getName() devuelva el username normalizado
            // que coincide con cómo SessionService almacena los playerIds
            return new CognitoPrincipal(normalizedUsername, decodedJWT);
            
        } catch (JWTVerificationException e) {
            log.error("❌ Token JWT inválido: {}", e.getMessage());
            log.error("💡 Verifica que el token sea válido y no haya expirado");
            throw new SecurityException("Token inválido: " + e.getMessage(), e);
        } catch (SecurityException e) {
            // Re-lanzar SecurityException sin envolver
            throw e;
        } catch (Exception e) {
            log.error("❌ Error al validar token: {}", e.getMessage(), e);
            throw new SecurityException("Error al validar token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Principal que contiene el username de Cognito
     */
    public static class CognitoPrincipal implements Principal {
        private final String username;
        private final DecodedJWT decodedJWT;
        
        public CognitoPrincipal(String username, DecodedJWT decodedJWT) {
            this.username = username;
            this.decodedJWT = decodedJWT;
        }
        
        @Override
        public String getName() {
            return username;
        }
        
        public DecodedJWT getDecodedJWT() {
            return decodedJWT;
        }
    }
}

