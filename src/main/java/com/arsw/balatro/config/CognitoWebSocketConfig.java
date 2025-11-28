package com.arsw.balatro.config;

import com.arsw.balatro.service.CognitoTokenValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CognitoWebSocketConfig {
    
    private final CognitoTokenValidationService tokenValidationService;
    
    @Bean
    public CognitoWebSocketHandshakeInterceptor cognitoWebSocketHandshakeInterceptor() {
        return new CognitoWebSocketHandshakeInterceptor(tokenValidationService);
    }
    
    @Bean
    public CognitoChannelInterceptor cognitoChannelInterceptor(
            CognitoWebSocketHandshakeInterceptor handshakeInterceptor) {
        return new CognitoChannelInterceptor(handshakeInterceptor);
    }
}


