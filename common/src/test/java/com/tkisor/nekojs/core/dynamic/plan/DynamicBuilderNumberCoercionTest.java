package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Float-coercion defect regression for the dynamic definition builder domain
 * (2026-09-29 hotfix, same class as the ticket 36 trial defect in the modification
 * domain): {@link DynamicBuilderSurface} feeds builders such as
 * {@link DynamicSoundEventBuilder} whose {@code fixedRange} is a {@code Float}
 * property. A script writing {@code s.fixedRange = 0.7} hit the same raw engine
 * lossy-coercion failure ({@code Value.asFloat()} refuses doubles that cannot
 * round-trip through {@code float}) because the surface's numeric coercion used the
 * narrow {@code asInt/asLong/asFloat} readers directly.
 *
 * <p>The fix normalizes explicitly (D6 precedent): finite numbers narrow with Java
 * casting semantics, {@code int} members require integral in-range values, and
 * rejections are English {@link IllegalArgumentException}s with member context.
 * The {@code fixedRange} setter's own finite-or-null validation is unchanged and now
 * cannot even be reached with a non-finite value.
 *
 * <p>Real GraalJS geometry (mirrors {@code DynamicBuilderSurfaceParityTest}).
 */
class DynamicBuilderNumberCoercionTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** Executes a script against the surface-wrapped sound event builder. */
    private static DynamicSoundEventBuilder evalOn(String script) {
        DynamicSoundEventBuilder builder = new DynamicSoundEventBuilder();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("s", DynamicBuilderSurface.of(builder));
                context.eval("js", script);
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
        return builder;
    }

    @Test
    void fractionalFloatPropertyWriteNarrowsInsteadOfThrowingLossyCoercion() {
        DynamicSoundEventBuilder builder = evalOn("s.fixedRange = 0.7;");
        assertEquals(0.7f, builder.getFixedRange(), 0.0f, "fixedRange 0.7 must narrow to float 0.7");
    }

    @Test
    void explicitSetterFormAcceptsTheSameFractionalFloats() {
        DynamicSoundEventBuilder builder = evalOn("s.setFixedRange(0.7);");
        assertEquals(0.7f, builder.getFixedRange(), 0.0f, "explicit setter shares the fixed coercion seam");
    }

    @Test
    void nullFloatPropertyStillMeansUnset() {
        DynamicSoundEventBuilder builder = evalOn("s.fixedRange = null;");
        assertNull(builder.getFixedRange(), "null property write keeps the null-means-unset semantics");
    }

    @Test
    void nonFiniteFloatIsRejectedLegiblyWithMemberContext() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn("s.fixedRange = NaN;"));
        assertTrue(error.getMessage().contains("fixedRange"),
                "NaN must be rejected legibly with member context: " + error.getMessage());
        assertTrue(!error.getMessage().contains("lossy"),
                "rejection must not be the raw engine lossy-coercion error: " + error.getMessage());
    }
}
