//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsxItemVisualTest {
    @Test
    void defaultsDoNotMutateTheAuthoringProps() {
        Map<String, Object> props = new LinkedHashMap<>(Map.of("item", "minecraft:stone"));
        Map<String, Object> before = new LinkedHashMap<>(props);
        var descriptor = JsxItemVisual.parse(props);
        assertEquals(new JsxItemVisual.Descriptor("minecraft:stone", 1, true), descriptor);
        assertEquals(before, props);
        props.put("item", "minecraft:dirt");
        assertEquals(new JsxItemVisual.Descriptor("minecraft:stone", 1, true), descriptor);
    }

    @Test
    void explicitValuesAndCustomStringTooltipAreAccepted() {
        var descriptor = JsxItemVisual.parse(Map.of("item", "demo_mod:item/path-1.test", "count", 99,
                "itemTooltip", false, "tooltip", "Custom tooltip"));
        assertEquals(new JsxItemVisual.Descriptor("demo_mod:item/path-1.test", 99, false), descriptor);
        assertEquals(1, JsxItemVisual.parse(Map.of("item", "minecraft:stone", "tooltip", "")).count());
        for (Number count : List.of(1, 99L, 2.0, 3.0f, BigInteger.valueOf(4), new BigDecimal("5.0"))) {
            assertEquals(count.intValue(), JsxItemVisual.parse(Map.of("item", "minecraft:stone", "count", count)).count());
        }
    }

    @Test
    void itemRequiresAStringAndAnExplicitLowercaseQualifiedId() {
        assertInvalid(null);
        assertInvalid(Map.of());
        assertInvalid(with("item", null));
        for (Object item : List.of(1, true, Map.of("id", "minecraft:stone"), new Object(),
                "stone", "", ":stone", "minecraft:", "Minecraft:stone", "minecraft:Stone",
                "minecraft:stone:extra", " minecraft:stone", "minecraft:stone ",
                "minecraft:stone\n", "minecraft:item#tag", "minecraft:item?query", "minecraft:item\\path")) {
            assertInvalid(with("item", item));
        }
    }

    @Test
    void countRejectsCoercionFractionsNonFiniteValuesAndOutOfRangeValues() {
        assertInvalid(with("count", null));
        for (Object count : List.of("2", true, new Object(), 0, -1, 100, 1.5, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Long.MAX_VALUE,
                new BigInteger("18446744073709551617"), new BigDecimal("1.00000000000000000001"),
                new BigDecimal("0"), new BigDecimal("100"))) {
            assertInvalid(with("count", count));
        }
    }

    @Test
    void tooltipPropertiesRejectNonBooleanAndNonStringValues() {
        assertInvalid(with("itemTooltip", null));
        for (Object value : List.of("true", "false", 1, 0, new Object())) {
            assertInvalid(with("itemTooltip", value));
        }
        assertInvalid(with("tooltip", null));
        for (Object value : List.of(true, 1, List.of("text"), Map.of("text", "text"))) {
            assertInvalid(with("tooltip", value));
        }
    }

    @Test
    void invalidParsingAlsoLeavesThePropsUntouched() {
        Map<String, Object> props = with("count", "2");
        Map<String, Object> before = new HashMap<>(props);
        assertInvalid(props);
        assertEquals(before, props);
    }

    @Test
    void directDescriptorsCannotBypassIdAndCountValidation() {
        assertDiagnostic(assertThrows(IllegalArgumentException.class,
                () -> new JsxItemVisual.Descriptor("stone", 1, true)));
        assertDiagnostic(assertThrows(IllegalArgumentException.class,
                () -> new JsxItemVisual.Descriptor("minecraft:stone", 0, true)));
        assertDiagnostic(assertThrows(IllegalArgumentException.class,
                () -> new JsxItemVisual.Descriptor("minecraft:stone", 100, true)));
    }

    @Test
    void nativeResolutionRejectsUnknownAndAirRatherThanUsingRegistryDefaults() {
        Assumptions.assumeTrue(VanillaRegistryProbe.registryMetadataAvailable(),
                "Vanilla registry metadata is unavailable in this JVM");
        for (String item : List.of("nekojs:task40_missing_item", "minecraft:air")) {
            assertDiagnostic(assertThrows(IllegalArgumentException.class,
                    () -> JsxItemVisual.minecraft().resolve(JsxItemVisual.parse(Map.of("item", item)))));
        }
    }

    @Test
    void nativeResolutionReusesOneDetachedStackForPaintingDecorationsAndTooltip() throws Exception {
        Assumptions.assumeTrue(VanillaRegistryProbe.available(),
                "Vanilla item components are unavailable in this JVM");
        var descriptor = JsxItemVisual.parse(Map.of("item", "minecraft:stone", "count", 99));
        var resolved = JsxItemVisual.minecraft().resolve(descriptor);
        var graphics = graphics();
        resolved.paint(graphics, null, 12, 34);
        resolved.paint(graphics, null, 56, 78);
        resolved.tooltip(graphics, null, 90, 91);
        assertEquals(List.of("item:12:34", "decorations:12:34", "item:56:78",
                "decorations:56:78", "tooltip:90:91"), graphics.calls);
        ItemStack cached = graphics.stacks.getFirst();
        assertSame(Items.STONE, cached.getItem());
        assertEquals(99, cached.getCount());
        for (ItemStack stack : graphics.stacks) assertSame(cached, stack);

        var otherGraphics = graphics();
        JsxItemVisual.minecraft().resolve(descriptor).paint(otherGraphics, null, 0, 0);
        assertNotSame(cached, otherGraphics.stacks.getFirst());
        cached.setCount(2);
        assertEquals(99, otherGraphics.stacks.getFirst().getCount());
        assertEquals(1, Items.STONE.getDefaultInstance().getCount());
    }

    private static Map<String, Object> with(String key, Object value) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("item", "minecraft:stone");
        props.put(key, value);
        return props;
    }

    private static void assertInvalid(Map<String, Object> props) {
        assertDiagnostic(assertThrows(IllegalArgumentException.class, () -> JsxItemVisual.parse(props)));
    }

    private static void assertDiagnostic(IllegalArgumentException failure) {
        assertTrue(failure.getMessage().startsWith("[NEKO-6012]"), failure.getMessage());
    }

    private static RecordingGraphics graphics() throws Exception {
        // Record native calls without constructing a client, render backend, or window.
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var graphics = (RecordingGraphics) ((Unsafe) field.get(null)).allocateInstance(RecordingGraphics.class);
        graphics.calls = new ArrayList<>();
        graphics.stacks = new ArrayList<>();
        return graphics;
    }

    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private List<String> calls;
        private List<ItemStack> stacks;

        private RecordingGraphics() { super(null, null, 0, 0); }

        @Override
        public void item(ItemStack stack, int x, int y) {
            calls.add("item:" + x + ":" + y);
            stacks.add(stack);
        }

        @Override
        public void itemDecorations(Font font, ItemStack stack, int x, int y) {
            calls.add("decorations:" + x + ":" + y);
            stacks.add(stack);
        }

        @Override
        public void setTooltipForNextFrame(Font font, ItemStack stack, int x, int y) {
            calls.add("tooltip:" + x + ":" + y);
            stacks.add(stack);
        }
    }
}
//?}
