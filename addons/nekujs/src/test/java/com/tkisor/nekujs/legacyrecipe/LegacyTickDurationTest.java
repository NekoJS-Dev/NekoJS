package com.tkisor.nekujs.legacyrecipe;

import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekujs.bridge.LegacyTickDurationAdapter;
import dev.latvian.mods.kubejs.util.TickDuration;
import graal.graalvm.polyglot.Context;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyTickDurationTest {
    @Test
    void sharedWrapUsesOriginalTimeJsUnitsAtTwentyTicksPerSecond() {
        var cases = Map.ofEntries(
                Map.entry("1t", 1L), Map.entry("50000000ns", 1L), Map.entry("50ms", 1L),
                Map.entry("1s", 20L), Map.entry("1m", 1200L), Map.entry("1h", 72000L),
                Map.entry("1d", 1728000L), Map.entry("1w", 12096000L),
                Map.entry("1M", 52594920L), Map.entry("1y", 631139040L));
        cases.forEach((input, expected) -> assertEquals(expected.longValue(), TickDuration.wrap(null, input).ticks(), input));
    }

    @Test
    void sharedWrapCombinesWhitespaceAdjacentAndDecimalAmountsBeforeTruncating() {
        assertEquals(1812L, TickDuration.wrap(null, "  1 m 30.5s 2t  ").ticks());
        assertEquals(1230L, TickDuration.wrap(null, "1m1.5s").ticks());
        assertEquals(1L, TickDuration.wrap(null, "0.5t 25ms").ticks());
        assertEquals(1L, TickDuration.wrap(null, "25ms 25000000ns").ticks());
        assertEquals(30L, TickDuration.wrap(null, ".5s 1s").ticks());
        assertEquals(-30L, TickDuration.wrap(null, "-1.5s").ticks());
        assertEquals(0L, TickDuration.wrap(null, "0.9t").ticks());
        assertEquals(1L, TickDuration.wrap(null, "0.9t .1t").ticks());
    }

    @Test
    void sharedWrapPreservesLegacyNullNumberNativeAndDurationBranches() {
        var nativeTicks = TickDuration.of(43L);
        assertSame(TickDuration.ZERO, TickDuration.wrap(null, null));
        assertSame(nativeTicks, TickDuration.wrap(null, nativeTicks));
        assertEquals(42L, TickDuration.wrap(null, 42.9D).ticks());
        assertEquals(-42L, TickDuration.wrap(null, -42.9D).ticks());
        assertEquals(42L, TickDuration.wrap(null, new JsonPrimitive(42)).ticks());
        assertEquals(30L, TickDuration.wrap(null, Duration.ofMillis(1500)).ticks());
        assertEquals(1L, TickDuration.wrap(null, Duration.ofMillis(99)).ticks());
    }

    @Test
    void invalidStringsRetainTheExistingConversionDiagnostic() {
        for (String input : List.of("", " ", "20", "s", "1q", "1S", "1seconds", "1 s junk",
                "1e3s", "NaNs", "Infinitys", "1s + 2s", "(1s)", "1s;", "1..2s")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> TickDuration.wrap(null, input), input);
            assertTrue(failure.getMessage().contains("NEKO-4284"), input);
            assertNotNull(failure.getCause(), input);
        }
    }

    @Test
    void managedHostAccessConvertsStringsNumbersAndNativeDurationArguments() {
        var adapter = new LegacyTickDurationAdapter();
        try (Context context = newContext(adapter)) {
            context.getBindings("js").putMember("durations", new DurationTarget());
            context.getBindings("js").putMember("nativeTicks", TickDuration.of(43L));
            context.getBindings("js").putMember("nativeDuration", Duration.ofMillis(1500));
            assertEquals(1812L, context.eval("js", "durations.ticks('1m 30.5s 2t')").asLong());
            assertEquals(30L, context.eval("js", "durations.ticks('1.5s')").asLong());
            assertEquals(42L, context.eval("js", "durations.ticks(42)").asLong());
            assertEquals(42L, context.eval("js", "durations.ticks(42.9)").asLong());
            assertEquals(43L, context.eval("js", "durations.ticks(nativeTicks)").asLong());
            assertEquals(30L, context.eval("js", "durations.ticks(nativeDuration)").asLong());
            assertEquals(20L, context.eval("js", "'1s'").as(TickDuration.class).ticks());
            assertSame(TickDuration.ZERO, adapter.apply(null));
            assertSame(TickDuration.ZERO, adapter.apply(context.eval("js", "null")));
            var nativeTicks = TickDuration.of(17L);
            assertSame(nativeTicks, adapter.apply(context.asValue(nativeTicks)));
            assertFalse(adapter.test(context.eval("js", "({ticks: 20})")));
            assertFalse(adapter.test(context.eval("js", "true")));
        }
    }

    @Test
    void managedHostAccessRejectsMalformedTimeStringsWithoutEvaluatingExpressions() {
        var adapter = new LegacyTickDurationAdapter();
        try (Context context = newContext(adapter)) {
            context.getBindings("js").putMember("durations", new DurationTarget());
            context.eval("js", "globalThis.executed = false");
            for (String input : List.of("", "20", "1fortnight", "1s + 2s", "executed = true")) {
                assertTrue(adapter.test(context.asValue(input)));
                var failure = assertThrows(IllegalArgumentException.class, () -> adapter.apply(context.asValue(input)), input);
                assertTrue(failure.getMessage().contains("NEKO-4284"), input);
            }
            var failure = assertThrows(graal.graalvm.polyglot.PolyglotException.class,
                    () -> context.eval("js", "durations.ticks('executed = true')"));
            assertTrue(failure.getMessage().contains("NEKO-4284"));
            assertFalse(context.eval("js", "executed").asBoolean());
        }
    }

    @Test
    void adapterInputShapesDescribeTheRuntimeConversionBranches() {
        var adapter = new LegacyTickDurationAdapter();
        assertEquals(TickDuration.class, adapter.getTargetClass());
        assertEquals(List.of(AdapterInputShape.self(), AdapterInputShape.number(), AdapterInputShape.string(),
                AdapterInputShape.host(Duration.class), AdapterInputShape.raw("null")), adapter.inputShapes());
        assertTrue(adapter.syntaxDoc().orElseThrow().contains("20 per second"));
    }

    private static Context newContext(LegacyTickDurationAdapter adapter) {
        return Context.newBuilder("js").allowHostAccess(new NekoSharedHostAccess(List.of(adapter)).get())
                .option("engine.WarnInterpreterOnly", "false").build();
    }

    public static final class DurationTarget {
        public long ticks(TickDuration duration) { return duration.ticks(); }
    }
}
