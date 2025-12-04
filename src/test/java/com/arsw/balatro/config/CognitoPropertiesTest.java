package com.arsw.balatro.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CognitoPropertiesTest {

    private CognitoProperties cognitoProperties;

    @BeforeEach
    void setUp() {
        cognitoProperties = new CognitoProperties();
    }

    @Test
    void testGetJwkUrl_WhenJwkUrlIsSet_ShouldReturnJwkUrl() {
        // Given
        cognitoProperties.setJwkUrl("https://custom-jwk-url.com/jwks.json");
        cognitoProperties.setRegion("us-east-1");
        cognitoProperties.setUserPoolId("us-east-1_ABC123");

        // When
        String result = cognitoProperties.getJwkUrl();

        // Then
        assertEquals("https://custom-jwk-url.com/jwks.json", result);
    }

    @Test
    void testGetJwkUrl_WhenJwkUrlIsNull_ShouldBuildUrlFromRegionAndUserPoolId() {
        // Given
        cognitoProperties.setJwkUrl(null);
        cognitoProperties.setRegion("us-east-1");
        cognitoProperties.setUserPoolId("us-east-1_ABC123");

        // When
        String result = cognitoProperties.getJwkUrl();

        // Then
        assertEquals("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_ABC123/.well-known/jwks.json", result);
    }

    @Test
    void testGetJwkUrl_WhenJwkUrlIsEmpty_ShouldBuildUrlFromRegionAndUserPoolId() {
        // Given
        cognitoProperties.setJwkUrl("");
        cognitoProperties.setRegion("us-west-2");
        cognitoProperties.setUserPoolId("us-west-2_XYZ789");

        // When
        String result = cognitoProperties.getJwkUrl();

        // Then
        assertEquals("https://cognito-idp.us-west-2.amazonaws.com/us-west-2_XYZ789/.well-known/jwks.json", result);
    }

    @Test
    void testGetIssuer_ShouldBuildIssuerFromRegionAndUserPoolId() {
        // Given
        cognitoProperties.setRegion("eu-west-1");
        cognitoProperties.setUserPoolId("eu-west-1_DEF456");

        // When
        String result = cognitoProperties.getIssuer();

        // Then
        assertEquals("https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_DEF456", result);
    }

    @Test
    void testGettersAndSetters() {
        // Given & When
        cognitoProperties.setUserPoolId("test-pool-id");
        cognitoProperties.setClientId("test-client-id");
        cognitoProperties.setRegion("test-region");
        cognitoProperties.setJwkUrl("test-jwk-url");

        // Then
        assertEquals("test-pool-id", cognitoProperties.getUserPoolId());
        assertEquals("test-client-id", cognitoProperties.getClientId());
        assertEquals("test-region", cognitoProperties.getRegion());
        assertEquals("test-jwk-url", cognitoProperties.getJwkUrl());
    }
}



