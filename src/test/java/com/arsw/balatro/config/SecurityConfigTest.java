package com.arsw.balatro.config;

import com.arsw.balatro.security.CognitoAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.RETURNS_SELF;

class SecurityConfigTest {

    private SecurityConfig securityConfig;
    private CognitoAuthenticationFilter cognitoAuthenticationFilter;
    private CorsConfigurationSource corsConfigurationSource;

    @BeforeEach
    void setUp() {
        cognitoAuthenticationFilter = mock(CognitoAuthenticationFilter.class);
        corsConfigurationSource = mock(CorsConfigurationSource.class);
        securityConfig = new SecurityConfig(cognitoAuthenticationFilter, corsConfigurationSource);
    }

    @Test
    void testSecurityFilterChain_ShouldCreateFilterChain() throws Exception {
        // Given
        HttpSecurity http = mock(HttpSecurity.class, RETURNS_SELF);
        DefaultSecurityFilterChain filterChain = mock(DefaultSecurityFilterChain.class);
        
        when(http.csrf(any())).thenReturn(http);
        when(http.cors(any())).thenReturn(http);
        when(http.sessionManagement(any())).thenReturn(http);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.addFilterBefore(any(), eq(UsernamePasswordAuthenticationFilter.class))).thenReturn(http);
        when(http.build()).thenReturn(filterChain);

        // When
        SecurityFilterChain chain = securityConfig.securityFilterChain(http);

        // Then
        assertNotNull(chain);
        assertEquals(filterChain, chain);
        verify(http).csrf(any());
        verify(http).cors(any());
        verify(http).sessionManagement(any());
        verify(http).authorizeHttpRequests(any());
        verify(http).addFilterBefore(eq(cognitoAuthenticationFilter), eq(UsernamePasswordAuthenticationFilter.class));
    }
}

