package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code DynamicRegistry.*} builder options: defaults, chaining identity and the
 * actionable errors for unknown enum-ish strings. These are the script-facing
 * option objects, so their defaults are part of the documented API.
 */
class DynamicRegistryBuilderTest {

    @Test
    void itemDefaultsMatchDocumentedValues() {
        DynamicRegistryJS.ItemBuilder builder = new DynamicRegistryJS.ItemBuilder();

        assertEquals(DynamicRegisterMode.WORLD, builder.resolveMode());
        assertEquals(Rarity.COMMON, builder.resolveRarity());
        assertEquals(64, builder.stackSize);
        assertEquals(false, builder.fireResistant);
    }

    @Test
    void itemBuilderChainsOnSameInstance() {
        DynamicRegistryJS.ItemBuilder builder = new DynamicRegistryJS.ItemBuilder();

        DynamicRegistryJS.ItemBuilder chained = builder
                .mode("reloadable")
                .maxStackSize(16)
                .rarity("epic")
                .fireResistant();

        assertSame(builder, chained, "builder calls must stay chainable on one instance");
        assertEquals(DynamicRegisterMode.RELOADABLE, builder.resolveMode());
        assertEquals(Rarity.EPIC, builder.resolveRarity());
        assertEquals(16, builder.stackSize);
        assertEquals(true, builder.fireResistant);
    }

    @Test
    void unknownRarityNamesActionableError() {
        DynamicRegistryJS.ItemBuilder builder = new DynamicRegistryJS.ItemBuilder().rarity("legendary");

        String message = assertThrows(IllegalArgumentException.class, builder::resolveRarity).getMessage();
        assertEquals("Unknown rarity 'legendary': expected one of common, uncommon, rare, epic", message);
    }

    @Test
    void globalModeIsRejectedWithRegistryEventsHint() {
        DynamicRegistryJS.ItemBuilder builder = new DynamicRegistryJS.ItemBuilder().mode("global");

        String message = assertThrows(IllegalArgumentException.class, builder::resolveMode).getMessage();
        assertEquals(true, message.contains("RegistryEvents"), message);
    }

    @Test
    void soundEventDefaultsToNoFixedRange() {
        DynamicRegistryJS.SoundEventBuilder builder = new DynamicRegistryJS.SoundEventBuilder();

        assertNull(builder.fixedRange, "omitted fixedRange must stay null so the sound definition decides");
        assertSame(builder, builder.fixedRange(16f).mode("world"));
        assertEquals(16f, builder.fixedRange);
    }

    @Test
    void mobEffectDefaultsAndCategoryParsing() {
        DynamicRegistryJS.MobEffectBuilder builder = new DynamicRegistryJS.MobEffectBuilder();

        assertEquals(MobEffectCategory.NEUTRAL, builder.resolveCategory());
        assertEquals(0xFFFFFF, builder.color);

        assertSame(builder, builder.category("harmful").color(0x8B0000));
        assertEquals(MobEffectCategory.HARMFUL, builder.resolveCategory());
        assertEquals(0x8B0000, builder.color);

        String message = assertThrows(IllegalArgumentException.class,
                () -> builder.category("scary").resolveCategory()).getMessage();
        assertEquals("Unknown mob effect category 'scary': expected 'beneficial', 'harmful' or 'neutral'", message);
    }
}
