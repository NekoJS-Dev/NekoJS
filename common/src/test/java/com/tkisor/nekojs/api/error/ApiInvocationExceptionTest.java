package com.tkisor.nekojs.api.error;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiInvocationExceptionTest {
    @Test
    void preservesCodeDetailsAndCauseAsImmutableData() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("symbolId", "member:ID.of");
        RuntimeException cause = new RuntimeException("cause");

        ApiInvocationException error = new ApiInvocationException(
                ApiErrorCodes.TYPE_MISMATCH, "Invalid argument", source, cause);
        source.put("platform", "changed");

        assertEquals(ApiErrorCodes.TYPE_MISMATCH, error.code());
        assertEquals(Map.of("symbolId", "member:ID.of"), error.details());
        assertSame(cause, error.getCause());
        assertThrows(UnsupportedOperationException.class,
                () -> error.details().put("platform", "test"));
    }

    @Test
    void rejectsBlankCodesAndInvalidDetails() {
        assertThrows(IllegalArgumentException.class,
                () -> new ApiInvocationException(" ", "message"));
        assertThrows(IllegalArgumentException.class,
                () -> new ApiInvocationException("CODE", "message", Map.of(" ", "value")));
    }
}
