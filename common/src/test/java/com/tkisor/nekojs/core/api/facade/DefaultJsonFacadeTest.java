package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.JsonValue;
import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultJsonFacadeTest {
    @TempDir
    Path tempDir;
    private DefaultJsonFacade json;

    @BeforeEach
    void createFacade() {
        json = new DefaultJsonFacade(tempDir.resolve("game").resolve("nekojs").resolve("data"));
    }

    @Test
    void parsesAndSerializesDeterministically() {
        JsonValue.ObjectValue value = assertInstanceOf(
                JsonValue.ObjectValue.class,
                json.parse("{\"b\":[true,null],\"a\":1.20e+3}"));

        assertEquals("{\"b\":[true,null],\"a\":1.20e+3}", json.toString(value));
        assertEquals("""
                {
                  "b": [
                    true,
                    null
                  ],
                  "a": 1.20e+3
                }""", json.toPrettyString(value));
    }

    @Test
    void rejectsMalformedAndDuplicateJson() {
        ApiInvocationException trailing = assertThrows(ApiInvocationException.class,
                () -> json.parse("{} trailing"));
        assertEquals(ApiErrorCodes.INVALID_JSON, trailing.code());

        ApiInvocationException duplicate = assertThrows(ApiInvocationException.class,
                () -> json.parse("{\"a\":1,\"a\":2}"));
        assertEquals(ApiErrorCodes.INVALID_JSON, duplicate.code());

        ApiInvocationException unicodeHex = assertThrows(ApiInvocationException.class,
                () -> json.parse("\"\\u００４１\""));
        assertEquals(ApiErrorCodes.INVALID_JSON, unicodeHex.code());
    }

    @Test
    void enforcesInputLimit() {
        ApiInvocationException error = assertThrows(ApiInvocationException.class,
                () -> json.parse(" ".repeat(JsonValue.MAX_INPUT_CHARS + 1)));
        assertEquals(ApiErrorCodes.JSON_LIMIT_EXCEEDED, error.code());
    }
}
