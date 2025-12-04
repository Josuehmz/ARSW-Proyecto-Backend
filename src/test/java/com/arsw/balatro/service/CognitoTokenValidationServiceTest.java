package com.arsw.balatro.service;

import com.arsw.balatro.config.CognitoProperties;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CognitoTokenValidationService Tests")
class CognitoTokenValidationServiceTest {

    @Mock
    private CognitoProperties cognitoProperties;

    @Mock
    private WebClient webClient;

    @InjectMocks
    private CognitoTokenValidationService service;

    private KeyPair keyPair;
    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        // Generate RSA key pair for testing
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();

        // Setup default properties
        when(cognitoProperties.getIssuer()).thenReturn("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TEST123");
        when(cognitoProperties.getClientId()).thenReturn("test-client-id");
        when(cognitoProperties.getJwkUrl()).thenReturn("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TEST123/.well-known/jwks.json");
    }

    @Test
    @DisplayName("Should extract token from Bearer header")
    void shouldExtractTokenFromBearerHeader() {
        // Given
        String header = "Bearer test-token-123";

        // When
        String token = service.extractTokenFromHeader(header);

        // Then
        assertEquals("test-token-123", token);
    }

    @Test
    @DisplayName("Should return null for non-Bearer header")
    void shouldReturnNullForNonBearerHeader() {
        // Given
        String header = "Basic test-token-123";

        // When
        String token = service.extractTokenFromHeader(header);

        // Then
        assertNull(token);
    }

    @Test
    @DisplayName("Should return null for null header")
    void shouldReturnNullForNullHeader() {
        // When
        String token = service.extractTokenFromHeader(null);

        // Then
        assertNull(token);
    }

    @Test
    @DisplayName("Should extract username from cognito:username claim")
    void shouldExtractUsernameFromCognitoUsernameClaim() {
        // Given
        String token = createTestToken("test-user", null, null);
        DecodedJWT decodedJWT = JWT.decode(token);

        // When
        String username = service.extractUsername(decodedJWT);

        // Then
        assertEquals("test-user", username);
    }

    @Test
    @DisplayName("Should extract username from username claim when cognito:username is missing")
    void shouldExtractUsernameFromUsernameClaim() {
        // Given
        String token = createTestToken(null, "fallback-user", null);
        DecodedJWT decodedJWT = JWT.decode(token);

        // When
        String username = service.extractUsername(decodedJWT);

        // Then
        assertEquals("fallback-user", username);
    }

    @Test
    @DisplayName("Should extract username from sub claim when other claims are missing")
    void shouldExtractUsernameFromSubClaim() {
        // Given
        String token = createTestToken(null, null, "sub-uuid-123");
        DecodedJWT decodedJWT = JWT.decode(token);

        // When
        String username = service.extractUsername(decodedJWT);

        // Then
        assertEquals("sub-uuid-123", username);
    }

    @Test
    @DisplayName("Should throw exception when token has no kid")
    void shouldThrowExceptionWhenTokenHasNoKid() {
        // Given
        String token = createTestTokenWithoutKid();
        
        // When & Then
        assertThrows(JWTVerificationException.class, () -> {
            service.validateToken(token);
        });
    }

    @Test
    @DisplayName("Should throw exception when token issuer is invalid")
    void shouldThrowExceptionWhenTokenIssuerIsInvalid() {
        // Given
        String token = createTestTokenWithInvalidIssuer();
        
        // When & Then
        assertThrows(JWTVerificationException.class, () -> {
            service.validateToken(token);
        });
    }

    @Test
    @DisplayName("Should throw exception when token is expired")
    void shouldThrowExceptionWhenTokenIsExpired() {
        // Given
        Date pastDate = new Date(System.currentTimeMillis() - 3600000); // 1 hour ago
        String token = createTestTokenWithExpiration(pastDate);
        
        // When & Then
        assertThrows(JWTVerificationException.class, () -> {
            service.validateToken(token);
        });
    }

    @Test
    @DisplayName("Should throw exception when token has no expiration")
    void shouldThrowExceptionWhenTokenHasNoExpiration() {
        // Given
        String token = createTestTokenWithoutExpiration();
        
        // When & Then
        assertThrows(JWTVerificationException.class, () -> {
            service.validateToken(token);
        });
    }

    // Helper methods to create test tokens
    private String createTestToken(String cognitoUsername, String username, String sub) {
        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 3600000); // 1 hour from now

        var builder = JWT.create()
            .withIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TEST123")
            .withSubject(sub != null ? sub : "default-sub")
            .withAudience("test-client-id")
            .withIssuedAt(now)
            .withExpiresAt(expiration)
            .withKeyId("test-kid");

        if (cognitoUsername != null) {
            builder.withClaim("cognito:username", cognitoUsername);
        }
        if (username != null) {
            builder.withClaim("username", username);
        }

        return builder.sign(algorithm);
    }

    private String createTestTokenWithoutKid() {
        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 3600000);

        return JWT.create()
            .withIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TEST123")
            .withSubject("test-sub")
            .withAudience("test-client-id")
            .withIssuedAt(now)
            .withExpiresAt(expiration)
            // No kid
            .sign(algorithm);
    }

    private String createTestTokenWithInvalidIssuer() {
        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 3600000);

        return JWT.create()
            .withIssuer("https://invalid-issuer.com")
            .withSubject("test-sub")
            .withAudience("test-client-id")
            .withIssuedAt(now)
            .withExpiresAt(expiration)
            .withKeyId("test-kid")
            .sign(algorithm);
    }

    private String createTestTokenWithExpiration(Date expiration) {
        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        Date now = new Date();

        return JWT.create()
            .withIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TEST123")
            .withSubject("test-sub")
            .withAudience("test-client-id")
            .withIssuedAt(now)
            .withExpiresAt(expiration)
            .withKeyId("test-kid")
            .sign(algorithm);
    }

    private String createTestTokenWithoutExpiration() {
        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        Date now = new Date();

        return JWT.create()
            .withIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TEST123")
            .withSubject("test-sub")
            .withAudience("test-client-id")
            .withIssuedAt(now)
            // No expiration
            .withKeyId("test-kid")
            .sign(algorithm);
    }
}

