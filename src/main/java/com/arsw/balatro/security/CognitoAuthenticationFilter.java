package com.arsw.balatro.security;

import com.arsw.balatro.service.CognitoTokenValidationService;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class CognitoAuthenticationFilter extends OncePerRequestFilter {
    
    private final CognitoTokenValidationService tokenValidationService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // Saltar autenticación para WebSocket y endpoints de SockJS (se maneja en el interceptor)
        String path = request.getRequestURI();
        if (path.startsWith("/ws") || path.startsWith("/actuator")) {
            // Permitir /ws/info que es parte del handshake de SockJS
            if (path.startsWith("/ws/info")) {
                filterChain.doFilter(request, response);
                return;
            }
            // Si es una petición HTTP normal a /ws (no WebSocket), dar mensaje informativo
            if (path.startsWith("/ws") && !"websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Este endpoint es solo para conexiones WebSocket. Usa un cliente WebSocket (STOMP) con el token en el header Authorization del mensaje CONNECT.\"}");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extraer token del header Authorization
        String authorizationHeader = request.getHeader("Authorization");
        
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.debug("No se encontró token en request: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Token de autenticación requerido\"}");
            return;
        }
        
        try {
            String token = tokenValidationService.extractTokenFromHeader(authorizationHeader);
            
            // Validar token
            DecodedJWT decodedJWT = tokenValidationService.validateToken(token);
            
            // Extraer username
            String username = tokenValidationService.extractUsername(decodedJWT);
            
            log.debug("✅ Usuario autenticado: {} en path: {}", username, path);
            
            // Crear detalles de autenticación
            WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();
            Object details = detailsSource.buildDetails(request);
            
            // Establecer autenticación en contexto de seguridad
            // UsernamePasswordAuthenticationToken tiene un constructor que acepta detalles
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                username,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );
            authentication.setDetails(details);
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            filterChain.doFilter(request, response);
            
        } catch (JWTVerificationException e) {
            log.error("❌ Token inválido: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Token inválido: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            log.error("❌ Error al procesar token: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Error al validar token\"}");
        }
    }
}

