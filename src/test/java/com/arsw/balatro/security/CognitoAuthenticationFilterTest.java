package com.arsw.balatro.security;

import com.arsw.balatro.service.CognitoTokenValidationService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CognitoAuthenticationFilterTest {

    @Mock
    private CognitoTokenValidationService tokenValidationService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private CognitoAuthenticationFilter filter;

    private StringWriter stringWriter;
    private PrintWriter printWriter;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);
    }

    @Test
    void testDoFilterInternal_WhenPathStartsWithWs_ShouldSkipAuthentication() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/ws/info");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(tokenValidationService, never()).extractTokenFromHeader(anyString());
        verify(tokenValidationService, never()).validateToken(anyString());
    }

    @Test
    void testDoFilterInternal_WhenPathStartsWithActuator_ShouldSkipAuthentication() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/actuator/health");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(tokenValidationService, never()).extractTokenFromHeader(anyString());
    }

    @Test
    void testDoFilterInternal_WhenWsPathWithoutWebSocketUpgrade_ShouldReturnBadRequest() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/ws");
        when(request.getHeader("Upgrade")).thenReturn(null);
        when(response.getWriter()).thenReturn(printWriter);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(stringWriter.toString().contains("WebSocket"));
    }

    @Test
    void testDoFilterInternal_WhenNoAuthorizationHeader_ShouldReturnUnauthorized() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(response.getWriter()).thenReturn(printWriter);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(stringWriter.toString().contains("Token de autenticación requerido"));
    }

    @Test
    void testDoFilterInternal_WhenAuthorizationHeaderDoesNotStartWithBearer_ShouldReturnUnauthorized() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("Invalid token");
        when(response.getWriter()).thenReturn(printWriter);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testDoFilterInternal_WhenValidToken_ShouldSetAuthentication() throws Exception {
        // Given
        String token = "Bearer valid-token";
        String username = "testuser";
        DecodedJWT decodedJWT = createMockJWT(username);

        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn(token);
        when(tokenValidationService.extractTokenFromHeader(token)).thenReturn("valid-token");
        when(tokenValidationService.validateToken("valid-token")).thenReturn(decodedJWT);
        when(tokenValidationService.extractUsername(decodedJWT)).thenReturn(username);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(tokenValidationService).validateToken("valid-token");
        verify(tokenValidationService).extractUsername(decodedJWT);
        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(username, SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void testDoFilterInternal_WhenInvalidToken_ShouldReturnUnauthorized() throws Exception {
        // Given
        String token = "Bearer invalid-token";
        JWTVerificationException exception = new JWTVerificationException("Token expired");

        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn(token);
        when(tokenValidationService.extractTokenFromHeader(token)).thenReturn("invalid-token");
        when(tokenValidationService.validateToken("invalid-token")).thenThrow(exception);
        when(response.getWriter()).thenReturn(printWriter);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(stringWriter.toString().contains("Token inválido"));
    }

    @Test
    void testDoFilterInternal_WhenExceptionOccurs_ShouldReturnUnauthorized() throws Exception {
        // Given
        String token = "Bearer token";
        RuntimeException exception = new RuntimeException("Unexpected error");

        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn(token);
        when(tokenValidationService.extractTokenFromHeader(token)).thenThrow(exception);
        when(response.getWriter()).thenReturn(printWriter);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(stringWriter.toString().contains("Error al validar token"));
    }

    private DecodedJWT createMockJWT(String username) {
        Algorithm algorithm = Algorithm.HMAC256("secret");
        String token = JWT.create()
                .withSubject(username)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm);
        return JWT.decode(token);
    }
}

