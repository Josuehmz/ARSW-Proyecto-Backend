package com.arsw.balatro.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

class WebClientConfigTest {

    @Test
    void testWebClientBean_ShouldCreateWebClient() {
        // Given
        WebClientConfig config = new WebClientConfig();

        // When
        WebClient webClient = config.webClient();

        // Then
        assertNotNull(webClient);
    }

    @Test
    void testWebClientBean_ShouldReturnNewInstance() {
        // Given
        WebClientConfig config = new WebClientConfig();

        // When
        WebClient webClient1 = config.webClient();
        WebClient webClient2 = config.webClient();

        // Then
        assertNotNull(webClient1);
        assertNotNull(webClient2);
        // Note: WebClient instances are typically different objects
        assertNotSame(webClient1, webClient2);
    }
}






