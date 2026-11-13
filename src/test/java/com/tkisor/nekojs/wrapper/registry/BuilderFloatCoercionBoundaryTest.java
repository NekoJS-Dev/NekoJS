package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.wrapper.registry.gen.BlockBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.BuilderSurface;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for float coercion in the startup registry-generation path.
 * {@link BuilderSurface} feeds real builders such as {@link BlockBuilder}, whose
 * {@code hardness}/{@code resistance} are {@code float} properties. A script writing
 * {@code b.hardness = 0.9} hit the engine's lossy-coercion failure ({@code Value.asFloat()}
 * refuses doubles that cannot round-trip through {@code float}) because numeric coercion used
 * the narrow {@code asInt/asLong/asFloat} readers directly.
 *
 * <p>The fix normalizes explicitly: finite numbers narrow with Java casting semantics,
 * {@code int} members require integral in-range values, and rejections are English
 * {@link IllegalArgumentException}s that name the member in context.
 *
 * <p>No registry is involved, as in {@code BuilderSetterPropertyParityTest}: a real GraalJS
 * {@link Context} drives the production {@link BlockBuilder} through {@link BuilderSurface}.
 */
class BuilderFloatCoercionBoundaryTest {

    private static BlockBuilder blockBuilder(String id) {
        return new BlockBuilder(net.minecraft.resources.Identifier.parse(id));
    }

    /** Executes a script against the surface-wrapped builder. */
    private static void evalOn(BlockBuilder builder, String script) {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("b", BuilderSurface.of(builder));
            context.eval("js", script);
        }
    }

    @Test
    void fractionalFloatPropertyWriteNarrowsInsteadOfThrowingLossyCoercion() {
        BlockBuilder builder = blockBuilder("mymod:float_boundary");
        evalOn(builder, "b.hardness = 0.9; b.resistance = 1.1;");
        assertEquals(0.9f, builder.getHardness(), 0.0f, "hardness 0.9 must narrow to float 0.9");
        assertEquals(1.1f, builder.getResistance(), 0.0f, "resistance 1.1 must narrow to float 1.1");
    }

    @Test
    void explicitSetterFormAcceptsTheSameFractionalFloats() {
        BlockBuilder builder = blockBuilder("mymod:float_boundary_setter");
        evalOn(builder, "b.setHardness(0.9);");
        assertEquals(0.9f, builder.getHardness(), 0.0f, "explicit setter shares the fixed coercion seam");
    }

    @Test
    void integralDoubleStillNarrowsToInt() {
        BlockBuilder builder = blockBuilder("mymod:int_boundary");
        evalOn(builder, "b.lightLevel = 7.0;");
        assertEquals(7, builder.getLightLevel(), "integral doubles keep narrowing to int");
    }

    @Test
    void fractionalIntIsRejectedLegiblyWithMemberContext() {
        BlockBuilder builder = blockBuilder("mymod:int_boundary_bad");
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn(builder, "b.lightLevel = 2.5;"));
        assertTrue(error.getMessage().contains("lightLevel") && error.getMessage().contains("integer"),
                "fractional int must be rejected legibly with member context: " + error.getMessage());
        assertTrue(!error.getMessage().contains("lossy"),
                "rejection must not be the raw engine lossy-coercion error: " + error.getMessage());
    }

    @Test
    void nonFiniteFloatIsRejectedLegiblyWithMemberContext() {
        BlockBuilder builder = blockBuilder("mymod:float_boundary_bad");
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn(builder, "b.hardness = NaN;"));
        assertTrue(error.getMessage().contains("hardness") && error.getMessage().contains("finite"),
                "NaN must be rejected legibly with member context: " + error.getMessage());
    }
}
