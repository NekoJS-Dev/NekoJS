package com.tkisor.nekujs.legacyutil;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.color.KubeColor;
import dev.latvian.mods.kubejs.error.KubeRuntimeException;
import dev.latvian.mods.kubejs.generator.KubeDataGenerator;
import dev.latvian.mods.kubejs.holder.HolderWrapper;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.StringUtilsWrapper;
import dev.latvian.mods.kubejs.script.ConsoleJS;
import dev.latvian.mods.kubejs.script.ConsoleLine;
import dev.latvian.mods.kubejs.script.SourceLine;
import dev.latvian.mods.kubejs.script.data.VirtualDataMapFile;
import dev.latvian.mods.kubejs.util.ID;
import dev.latvian.mods.kubejs.util.OpsContainer;
import dev.latvian.mods.kubejs.util.TickDuration;
import dev.latvian.mods.kubejs.util.TickTemporalUnit;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyUtilityTest {
    @Test
    void consumesOnlyIdentifierCharactersAndPreservesDataResultFailures() {
        StringReader reader = new StringReader("create:andesite,remaining");
        assertEquals(ResourceLocation.parse("create:andesite"), ID.read(reader).getOrThrow());
        assertEquals(',', reader.peek());
        StringReader invalid = new StringReader("minecraft::");
        assertTrue(ID.read(invalid).error().isPresent());
        assertEquals(11, invalid.getCursor());
        assertEquals(ResourceLocation.parse("minecraft:stone"), ID.mc("stone"));
        assertEquals(ResourceLocation.parse("custom:stone"), ID.of("stone", "custom"));
        assertEquals("custom/stone", ID.resourcePath(ResourceLocation.parse("custom:stone")));
        assertThrows(IllegalArgumentException.class, () -> ID.mc(Holder.direct("unregistered")));
        assertThrows(IllegalArgumentException.class, () -> ID.mc(new Object()));
    }

    @Test
    void convertsTicksThroughNativeTemporalArithmeticAndRejectsUnsupportedForms() {
        assertSame(TickDuration.ZERO, TickDuration.wrap(null, null));
        assertEquals(42L, TickDuration.wrap(null, 42).ticks());
        assertEquals(2L, TickDuration.wrap(null, Duration.ofMillis(149L)).ticks());
        assertEquals(7L, TickDuration.wrap(null, new JsonPrimitive(7)).ticks());
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        assertEquals(start.plusMillis(150L), TickDuration.of(3L).addTo(start));
        assertEquals(start.minusMillis(100L), TickDuration.of(2L).subtractFrom(start));
        assertEquals(3L, TickTemporalUnit.INSTANCE.between(start, start.plusMillis(199L)));
        assertEquals(Integer.MAX_VALUE, TickDuration.of(Long.MAX_VALUE).intTicks());
        assertEquals(Integer.MIN_VALUE, TickDuration.of(Long.MIN_VALUE).intTicks());
        assertEquals(List.of(TickTemporalUnit.INSTANCE), TickDuration.of(3L).getUnits());
        assertEquals(0L, TickDuration.of(3L).get(ChronoUnit.SECONDS));
        assertThrows(ArithmeticException.class, () -> TickTemporalUnit.INSTANCE.addTo(start, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> TickDuration.wrap(null, "tomorrow"));
    }

    @Test
    void preservesNativeColorBitsAndNumericFallback() {
        KubeColor color = () -> 0x8A123456;
        assertEquals(0x123456, color.kjs$getRGB());
        assertEquals(0x123456, color.kjs$getFireworkRGB());
        assertEquals("#8A123456", color.kjs$toHexString());
        assertEquals(3.5D, StringUtilsWrapper.parseDouble("3.5", 9D));
        assertEquals(12D, StringUtilsWrapper.parseDouble(12, 9D));
        assertEquals(9D, StringUtilsWrapper.parseDouble(null, 9D));
        assertEquals(9D, StringUtilsWrapper.parseDouble("not numeric", 9D));
    }

    @Test
    void diagnosticMetadataNeverInventsAnActiveContextSourcePosition() {
        assertEquals(SourceLine.UNKNOWN, SourceLine.of(null));
        assertEquals(new SourceLine("", 0), SourceLine.of(null, -1));
        UnsupportedOperationException unsupported = assertThrows(UnsupportedOperationException.class,
                () -> SourceLine.of(new Context()));
        assertTrue(unsupported.getMessage().contains("NEKO-4281"));
        SourceLine first = SourceLine.of("startup_scripts/test.js", 12);
        KubeRuntimeException failure = new KubeRuntimeException("fixture failure").source(first);
        failure.source(SourceLine.of("other.js", 3));
        failure.customData.put("fixture", "value");
        ConsoleLine line = failure.apply(new ConsoleLine("fixture failure", failure));
        assertEquals(first, line.sourceLine);
        assertEquals("value", line.customData.get("fixture"));
        ConsoleLine logged = ConsoleJS.STARTUP.error("expected utility test diagnostic", failure);
        assertSame(failure, logged.throwable);
    }

    @Test
    void holdersRequireMatchingElementTypesAndNeverGuessARegistry() {
        Holder<String> holder = Holder.direct("native");
        assertSame(holder, HolderWrapper.wrap(null, holder, TypeInfo.of(String.class)));
        assertThrows(UnsupportedOperationException.class,
                () -> HolderWrapper.wrap(null, holder, TypeInfo.of(Integer.class)));
        assertThrows(UnsupportedOperationException.class,
                () -> HolderWrapper.wrap(null, "minecraft:stone", TypeInfo.of(String.class)));
    }

    @Test
    void explicitDataMapSinkReceivesEntriesButUnwiredGeneratorDoesNotRunCallback() {
        var entries = new LinkedHashMap<ResourceLocation, String>();
        var file = new VirtualDataMapFile<String, Object>(entries::put);
        ResourceLocation id = ResourceLocation.parse("example:entry");
        file.accept(id, "payload");
        assertEquals("payload", entries.get(id));
        assertThrows(NullPointerException.class, () -> file.accept(null, "payload"));
        AtomicBoolean callbackRan = new AtomicBoolean();
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
                () -> new KubeDataGenerator() {}.dataMap(null, ignored -> callbackRan.set(true)));
        assertTrue(failure.getMessage().contains("NEKO-4287"));
        assertFalse(callbackRan.get());
    }

    @Test
    void codecConversionUsesNativeJsonAndJavaRepresentations() {
        assertEquals(5, OpsContainer.DEFAULT.decode(null, Codec.INT, new JsonPrimitive(5)).intValue());
        assertEquals("native", OpsContainer.DEFAULT.decode(null, Codec.STRING, "native"));
        assertThrows(IllegalStateException.class, () -> OpsContainer.DEFAULT.decode(null, Codec.INT, "invalid"));
    }
}
