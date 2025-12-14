package com.arsw.balatro.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "aws.cognito")
public class CognitoProperties {
    
    private String userPoolId;
    private String clientId;
    private String region;
    private String jwkUrl;
    
    public String getJwkUrl() {
        if (jwkUrl != null && !jwkUrl.isEmpty()) {
            return jwkUrl;
        }
        // Construir URL automáticamente si no está configurada
        return String.format("https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json", 
            region, userPoolId);
    }
    
    public String getIssuer() {
        return String.format("https://cognito-idp.%s.amazonaws.com/%s", region, userPoolId);
    }
}









