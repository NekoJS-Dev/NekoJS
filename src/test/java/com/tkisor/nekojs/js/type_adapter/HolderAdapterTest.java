//? if >=26 {
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.ValueConversionException;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure logic paths of {@link HolderAdapter} that run without FML or a registry bootstrap: an
 * invalid id reports an error with context, and non-Holder host objects are rejected.
 *
 * <p>{@code AbstractJSTypeAdapter.test()} delegates strings straight to {@code fromString}, so both
 * the shape check for a valid id and cross-registry resolution (single hit, ambiguous, no hit)
 * depend on registry contents. Bare JUnit has no FML loader and cannot bootstrap, so in-game
 * verification covers those.
 */
class HolderAdapterTest {

    private final HolderAdapter adapter = new HolderAdapter();

    @Test
    void invalidIdThrowsWithContext() {
        String message = assertThrows(ValueConversionException.class,
                () -> adapter.apply(Value.asValue("not an id!"))).getMessage();
        assertTrue(message.contains("Holder"), message);
        assertTrue(message.contains("invalid id"), message);
        assertTrue(message.contains("registry entry id"), message);
    }

    @Test
    void nonHolderValuesRejectedByTest() {
        try (var context = graal.graalvm.polyglot.Context.newBuilder().build()) {
            assertEquals(false, adapter.test(context.asValue(new Object())));
            assertEquals(false, adapter.test(Value.asValue(42)));
            assertEquals(false, adapter.test(Value.asValue(true)));
        }
    }
}
//?}
