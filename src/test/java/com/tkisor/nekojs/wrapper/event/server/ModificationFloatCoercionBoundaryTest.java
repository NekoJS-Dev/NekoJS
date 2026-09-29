package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Float-coercion defect regression (2026-09-29 hotfix, discovered by the ticket 36
 * maintainer trial, author task 04): the trial script wrote {@code block.friction = 0.9}
 * and {@code block.jumpFactor = 1.1}. Script numbers are doubles, and
 * {@code Value.asFloat()} refuses any double that cannot round-trip through
 * {@code float} exactly, so the property write threw a raw engine
 * {@code PolyglotException} ("Invalid or lossy primitive coercion") from
 * {@link ModificationViewSurface} — and at the DOMAIN_PLAN collection point that
 * exception failed the whole server reload for the 'item-block-modification' domain.
 *
 * <p>Same defect class as D6 (int saturation): a JS number meeting a narrow Java
 * primitive at a script-facing seam. Fix policy (D6 precedent): the surface's numeric
 * coercion normalizes explicitly — finite numbers narrow to {@code float} with Java
 * casting semantics, {@code int}/{@code long} require integral in-range values, and
 * every rejection is an English {@link IllegalArgumentException} carrying the member
 * name so the failing call is legible.
 *
 * <p>Registry-free geometry (mirrors {@code ModificationSetterPropertyParityTest}):
 * a real GraalJS {@link Context} drives a stand-in view with the production
 * {@link BlockModificationJS} member shape (float friction/jumpFactor, int lightLevel)
 * through {@link ModificationViewSurface}, in both delivery forms — the wrapped
 * surface and the production {@code Consumer} + bare ProxyObject view form that
 * {@code BlockModificationEventJS#modify} uses.
 */
class ModificationFloatCoercionBoundaryTest {

    /** Executes a script against the surface-wrapped stand-in view. */
    private static FloatPropertyView evalOn(String script) {
        FloatPropertyView view = new FloatPropertyView();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("block", ModificationViewSurface.of(view));
            context.eval("js", script);
        }
        return view;
    }

    /**
     * Production delivery form: JS function -> sandbox HostAccess {@code Consumer} ->
     * bare view (its own ProxyObject implementation is what GraalJS honors), the same
     * shape as {@code BlockModificationEventJS#modify(String, Consumer)}.
     */
    @SuppressWarnings("unchecked")
    private static FloatPropertyView evalProductionDelivery(String functionSource) {
        FloatPropertyView view = new FloatPropertyView();
        Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .build();
        try {
            Value function = context.eval("js", functionSource);
            assertTrue(function.canExecute(), "fixture script must evaluate to a function");
            Consumer<FloatPropertyView> callback = function.as(Consumer.class);
            assertNotNull(callback, "sandbox HostAccess must implement Consumer for a JS function");
            callback.accept(view);
        } finally {
            context.close();
        }
        return view;
    }

    @Test
    void fractionalFloatPropertyWriteNarrowsInsteadOfThrowingLossyCoercion() {
        FloatPropertyView view = evalOn("block.friction = 0.9; block.jumpFactor = 1.1;");
        assertEquals(0.9f, view.friction, 0.0f, "friction 0.9 must narrow to float 0.9");
        assertEquals(1.1f, view.jumpFactor, 0.0f, "jumpFactor 1.1 must narrow to float 1.1");
    }

    @Test
    void explicitSetterFormAcceptsTheSameFractionalFloats() {
        FloatPropertyView view = evalOn("block.setFriction(0.9); block.setJumpFactor(1.1);");
        assertEquals(0.9f, view.friction, 0.0f, "explicit setter must accept 0.9 (same setter seam)");
        assertEquals(1.1f, view.jumpFactor, 0.0f);
    }

    @Test
    void productionDeliveryFormAcceptsFractionalFloats() {
        FloatPropertyView view = evalProductionDelivery(
                "(v) => { v.friction = 0.9; v.jumpFactor = 1.1; }");
        assertEquals(0.9f, view.friction, 0.0f,
                "the trial's exact delivery form (Consumer + bare view) must accept 0.9");
        assertEquals(1.1f, view.jumpFactor, 0.0f);
    }

    @Test
    void integralDoubleAndPlainIntStillNarrowToInt() {
        FloatPropertyView view = evalOn("block.lightLevel = 7;");
        assertEquals(7, view.lightLevel);
        FloatPropertyView doubleShape = evalOn("block.lightLevel = 16.0;");
        assertEquals(16, doubleShape.lightLevel, "integral doubles keep narrowing to int");
    }

    @Test
    void nonFiniteFloatIsRejectedLegiblyWithMemberContext() {
        RuntimeException nan = assertThrows(RuntimeException.class,
                () -> evalOn("block.friction = NaN;"));
        assertTrue(nan.getMessage().contains("friction") && nan.getMessage().contains("finite"),
                "NaN must be rejected legibly with member context: " + nan.getMessage());
        assertTrue(!nan.getMessage().contains("lossy"),
                "rejection must not be the raw engine lossy-coercion error: " + nan.getMessage());

        RuntimeException infinity = assertThrows(RuntimeException.class,
                () -> evalOn("block.friction = Infinity;"));
        assertTrue(infinity.getMessage().contains("friction") && infinity.getMessage().contains("finite"),
                "Infinity must be rejected legibly with member context: " + infinity.getMessage());
    }

    @Test
    void floatBeyondFloatRangeIsRejectedInsteadOfSilentlyCollapsingToInfinity() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn("block.friction = 1e40;"));
        assertTrue(error.getMessage().contains("friction"),
                "over-range double must be rejected legibly with member context: " + error.getMessage());
        assertTrue(!error.getMessage().contains("lossy"),
                "rejection must not be the raw engine lossy-coercion error: " + error.getMessage());
    }

    @Test
    void fractionalIntIsRejectedLegiblyWithMemberContext() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn("block.lightLevel = 2.5;"));
        assertTrue(error.getMessage().contains("lightLevel") && error.getMessage().contains("integer"),
                "fractional int must be rejected legibly with member context: " + error.getMessage());
        assertTrue(!error.getMessage().contains("lossy"),
                "rejection must not be the raw engine lossy-coercion error: " + error.getMessage());
    }

    @Test
    void intBeyondIntRangeIsRejectedLegibly() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn("block.lightLevel = 1e10;"));
        assertTrue(error.getMessage().contains("lightLevel") && error.getMessage().contains("integer"),
                "out-of-int-range value must be rejected legibly with member context: " + error.getMessage());
    }

    @Test
    void nonNumberFloatIsRejectedWithMemberContext() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn("block.friction = 'slippery';"));
        assertTrue(error.getMessage().contains("friction"),
                "type mismatch carries the member name: " + error.getMessage());
        assertTrue(error.getMessage().contains("number"),
                "type mismatch states the number expectation: " + error.getMessage());
    }

    /**
     * Stand-in view with the production {@code BlockModificationJS} member shape
     * (float friction/jumpFactor, int lightLevel) — the surface derives its member
     * catalog by reflection, so no Minecraft classes are needed to exercise the exact
     * coercion seam the trial hit.
     */
    public static final class FloatPropertyView implements graal.graalvm.polyglot.proxy.ProxyObject {

        final ModificationViewSurface surface = ModificationViewSurface.of(this);

        Float friction;
        Float jumpFactor;
        Integer lightLevel;

        @Override
        public Object getMember(String name) {
            return surface.getMember(name);
        }

        @Override
        public boolean hasMember(String name) {
            return surface.hasMember(name);
        }

        @Override
        public Object getMemberKeys() {
            return surface.getMemberKeys();
        }

        @Override
        public void putMember(String name, Value value) {
            surface.putMember(name, value);
        }

        @Override
        public boolean removeMember(String name) {
            return surface.removeMember(name);
        }

        public Float getFriction() {
            return friction;
        }

        public void setFriction(float friction) {
            this.friction = friction;
        }

        public Float getJumpFactor() {
            return jumpFactor;
        }

        public void setJumpFactor(float jumpFactor) {
            this.jumpFactor = jumpFactor;
        }

        public Integer getLightLevel() {
            return lightLevel;
        }

        public void setLightLevel(int lightLevel) {
            this.lightLevel = lightLevel;
        }
    }
}
