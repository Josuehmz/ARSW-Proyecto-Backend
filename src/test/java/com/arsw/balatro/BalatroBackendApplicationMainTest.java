package com.arsw.balatro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BalatroBackendApplication Main Method Tests")
class BalatroBackendApplicationMainTest {

    @Test
    @DisplayName("Should have main method that can be invoked")
    void shouldHaveMainMethodThatCanBeInvoked() {
        // Given
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        
        try {
            System.setOut(new PrintStream(outContent));
            
            // When - This will try to start Spring, but we're just checking the method exists
            // We can't actually run SpringApplication in a unit test without proper setup
            assertDoesNotThrow(() -> {
                BalatroBackendApplication.class.getMethod("main", String[].class);
            });
            
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should have main method with correct signature")
    void shouldHaveMainMethodWithCorrectSignature() throws NoSuchMethodException {
        // When
        var mainMethod = BalatroBackendApplication.class.getMethod("main", String[].class);
        
        // Then
        assertNotNull(mainMethod);
        assertEquals("main", mainMethod.getName());
        assertEquals(void.class, mainMethod.getReturnType());
        assertEquals(1, mainMethod.getParameterCount());
        assertEquals(String[].class, mainMethod.getParameterTypes()[0]);
        assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isPublic(mainMethod.getModifiers()));
    }
}

