package com.arsw.balatro;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
class BalatroBackendApplicationTest {

    @Test
    void testApplicationContextLoads() {
        // Verificar que la aplicación puede iniciar sin errores
        assertDoesNotThrow(() -> {
            // El contexto de Spring se carga automáticamente con @SpringBootTest
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


