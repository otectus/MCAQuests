package dev.otectus.mcaquests.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicServiceReflectionTest {
    @Test
    void invokesPackageHiddenImplementationThroughPublicInterface() throws Exception {
        Object service = java.util.Collections.unmodifiableList(java.util.List.of("ok"));
        assertThrows(IllegalAccessException.class,
                () -> service.getClass().getMethod("size").invoke(service));
        assertEquals(1, KingdomIntegration.invokePublic(service, java.util.List.class.getName(),
                "size", new Class<?>[0]));
    }
}
