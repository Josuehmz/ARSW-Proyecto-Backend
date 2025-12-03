package com.arsw.balatro;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "aws.cognito.user-pool-id=us-east-1_TEST123",
        "aws.cognito.client-id=test-client-id",
        "aws.cognito.region=us-east-1",
        "aws.cognito.jwk-url=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TEST123/.well-known/jwks.json"
    }
)
@ActiveProfiles("test")
class BalatroBackendApplicationTest {

    @Test
    void testApplicationContextLoads() {
        // Verificar que la aplicación puede iniciar sin errores
        // El contexto de Spring se carga automáticamente con @SpringBootTest
        assertDoesNotThrow(() -> {
            // Si llegamos aquí, el contexto se cargó correctamente
        });
    }

    @Test
    void testMainMethodExists() {
        // Verificar que el método main existe y es accesible
        assertDoesNotThrow(() -> {
            BalatroBackendApplication.class.getMethod("main", String[].class);
        });
    }
}


