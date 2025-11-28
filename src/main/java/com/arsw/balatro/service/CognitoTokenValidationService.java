package com.arsw.balatro.service;

import com.arsw.balatro.config.CognitoProperties;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CognitoTokenValidationService {
    
    private final CognitoProperties cognitoProperties;
    private final WebClient webClient;
    
    // Cache de JWK por kid
    private final Map<String, Jwk> jwkCache = new ConcurrentHashMap<>();
    private JwkProvider jwkProvider;
    
    /**
     * Validar token JWT de Cognito
     */
    public DecodedJWT validateToken(String token) throws JWTVerificationException {
        try {
            // Decodificar sin verificar primero para obtener el kid
            DecodedJWT decodedJWT = JWT.decode(token);
            
            // Verificar claims básicos
            verifyClaims(decodedJWT);
            
            // Obtener el kid del header
            String kid = decodedJWT.getKeyId();
            if (kid == null) {
                throw new JWTVerificationException("Token no tiene kid en el header");
            }
            
            // Obtener JWK
            Jwk jwk = getJwk(kid);
            
            // Verificar firma
            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
            com.auth0.jwt.interfaces.Verification verification = JWT.require(algorithm)
                    .withIssuer(cognitoProperties.getIssuer());
            
            // Solo validar audience si está presente en el token
            List<String> audienceList = decodedJWT.getAudience();
            if (audienceList != null && !audienceList.isEmpty()) {
                verification.withAudience(cognitoProperties.getClientId());
            } else {
                log.debug("⚠️ Token no tiene audience, se omite validación de audience");
            }
            
            return verification.build().verify(token);
            
        } catch (Exception e) {
            log.error("❌ Error al validar token JWT: {}", e.getMessage());
            if (e instanceof JWTVerificationException) {
                throw (JWTVerificationException) e;
            }
            throw new JWTVerificationException("Error al validar token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Verificar claims básicos del token
     */
    private void verifyClaims(DecodedJWT decodedJWT) throws JWTVerificationException {
        // Verificar issuer
        String issuer = decodedJWT.getIssuer();
        if (!cognitoProperties.getIssuer().equals(issuer)) {
            throw new JWTVerificationException("Issuer inválido: " + issuer);
        }
        
        // Verificar audience (puede ser null en algunos tokens)
        List<String> audienceList = decodedJWT.getAudience();
        if (audienceList != null && !audienceList.isEmpty()) {
            String audience = audienceList.stream()
                    .filter(aud -> cognitoProperties.getClientId().equals(aud))
                    .findFirst()
                    .orElse(null);
            if (audience == null) {
                log.warn("⚠️ Token tiene audience pero no coincide con client-id esperado. Audience: {}", audienceList);
                // No lanzamos excepción aquí, dejamos que el verifier lo valide
            }
        } else {
            log.warn("⚠️ Token no tiene claim 'aud' (audience). Se validará con el verifier.");
            // No lanzamos excepción aquí, dejamos que el verifier lo valide
        }
        
        // Verificar expiración
        if (decodedJWT.getExpiresAt() == null) {
            throw new JWTVerificationException("Token no tiene fecha de expiración");
        }
        
        if (decodedJWT.getExpiresAt().getTime() < System.currentTimeMillis()) {
            throw new JWTVerificationException("Token expirado");
        }
    }
    
    /**
     * Obtener JWK del cache o descargarlo
     */
    private Jwk getJwk(String kid) throws Exception {
        // Verificar cache primero
        if (jwkCache.containsKey(kid)) {
            log.debug("✅ JWK obtenido del cache para kid: {}", kid);
            return jwkCache.get(kid);
        }
        
        // Inicializar provider si no existe
        if (jwkProvider == null) {
            String jwkUrl = cognitoProperties.getJwkUrl();
            log.info("🔗 Inicializando JWK Provider con URL: {}", jwkUrl);
            try {
                URL url = new URL(jwkUrl);
                jwkProvider = new UrlJwkProvider(url);
                log.info("✅ JWK Provider inicializado correctamente");
            } catch (Exception e) {
                log.error("❌ Error al inicializar JWK Provider con URL: {}", jwkUrl, e);
                throw new RuntimeException("Error al inicializar JWK Provider: " + e.getMessage(), e);
            }
        }
        
        // Obtener JWK
        try {
            log.debug("🔍 Obteniendo JWK para kid: {}", kid);
            Jwk jwk = jwkProvider.get(kid);
            
            // Guardar en cache
            jwkCache.put(kid, jwk);
            
            log.info("✅ JWK obtenido correctamente para kid: {}", kid);
            return jwk;
        } catch (Exception e) {
            log.error("❌ Error al obtener JWK para kid: {} desde URL: {}", kid, cognitoProperties.getJwkUrl(), e);
            throw new RuntimeException("Error al obtener JWK: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extraer username del token
     * Prioridad: cognito:username > username > sub
     */
    public String extractUsername(DecodedJWT decodedJWT) {
        log.info("🔍 Extrayendo username del token JWT...");
        
        // Intentar obtener de cognito:username primero (preferido)
        String username = decodedJWT.getClaim("cognito:username").asString();
        if (username != null && !username.isEmpty()) {
            log.info("✅ Username obtenido de claim 'cognito:username': {}", username);
            return username;
        }
        log.debug("   - claim 'cognito:username': null o vacío");
        
        // Intentar obtener de username (claim estándar en tokens de acceso)
        username = decodedJWT.getClaim("username").asString();
        if (username != null && !username.isEmpty()) {
            log.info("✅ Username obtenido de claim 'username': {}", username);
            return username;
        }
        log.debug("   - claim 'username': null o vacío");
        
        // Fallback a sub (UUID) - solo si no hay username disponible
        String sub = decodedJWT.getSubject();
        log.warn("⚠️ No se encontró claim 'cognito:username' ni 'username' en el token. Usando 'sub' como fallback: {}", sub);
        log.warn("⚠️ Esto puede causar problemas con el enrutamiento WebRTC. Verifica que el token incluya el claim 'username'.");
        log.warn("💡 Claims disponibles en el token: {}", decodedJWT.getClaims().keySet());
        return sub;
    }
    
    /**
     * Extraer token del header Authorization
     */
    public String extractTokenFromHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring(7);
    }
}

