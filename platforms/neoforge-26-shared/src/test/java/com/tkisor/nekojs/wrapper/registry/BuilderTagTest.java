package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.wrapper.event.server.TagEventJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BuilderTags} record/flush/clear semantics, the builder-facing
 * {@code .tag(...)} recording, and the {@link TagEventJS} constructor flush that
 * composes pending builder tags with the {@code ServerEvents.tags} machinery.
 * No real tag load is involved: consumers and source maps are fakes.
 */
class BuilderTagTest {

    /** BuilderTags is global static state; keep every case deterministic. */
    @BeforeEach
    @AfterEach
    void resetPendingTags() {
        BuilderTags.clear();
    }

    private static Identifier id(String id) {
        return Identifier.parse(id);
    }

    /** Fake tag-add consumer: collects tagId -> [targetId] in insertion order. */
    private static final class RecordingAdder implements java.util.function.BiConsumer<Identifier, Identifier> {
        final Map<Identifier, List<Identifier>> added = new HashMap<>();

        @Override
        public void accept(Identifier tag, Identifier target) {
            added.computeIfAbsent(tag, k -> new ArrayList<>()).add(target);
        }
    }

    // ------------------------------------------------------------------
    // BuilderTags: record / flushInto / clear
    // ------------------------------------------------------------------

    @Test
    void flushDeliversOnlyEntriesOfTheRequestedRegistry() {
        BuilderTags.record(Registries.ITEM, id("c:tools/pickaxe"), id("mymod:my_pick"));
        BuilderTags.record(Registries.BLOCK, id("minecraft:mineable/pickaxe"), id("mymod:my_ore"));

        RecordingAdder itemAdder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:item"), itemAdder);

        assertEquals(1, itemAdder.added.size());
        assertEquals(List.of(id("mymod:my_pick")), itemAdder.added.get(id("c:tools/pickaxe")));
    }

    @Test
    void flushKeepsPendingEntriesSoEveryTagLoadReappliesThem() {
        BuilderTags.record(Registries.ITEM, id("c:tools/pickaxe"), id("mymod:my_pick"));

        RecordingAdder firstLoad = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:item"), firstLoad);
        RecordingAdder reload = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:item"), reload);

        assertEquals(firstLoad.added, reload.added, "pending entries must survive a flush and re-apply on the next tag load");
    }

    @Test
    void recordDeduplicatesIdenticalTriples() {
        BuilderTags.record(Registries.ITEM, id("c:tools/pickaxe"), id("mymod:my_pick"));
        BuilderTags.record(Registries.ITEM, id("c:tools/pickaxe"), id("mymod:my_pick"));

        assertEquals(1, BuilderTags.size());

        RecordingAdder adder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:item"), adder);
        assertEquals(List.of(id("mymod:my_pick")), adder.added.get(id("c:tools/pickaxe")));
    }

    @Test
    void clearDropsEverything() {
        BuilderTags.record(Registries.ITEM, id("c:tools/pickaxe"), id("mymod:my_pick"));
        BuilderTags.clear();

        assertEquals(0, BuilderTags.size());

        RecordingAdder adder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:item"), adder);
        assertTrue(adder.added.isEmpty(), "cleared pending set must flush nothing");
    }

    @Test
    void recordIgnoresNullArguments() {
        BuilderTags.record(null, id("c:tools/pickaxe"), id("mymod:my_pick"));
        BuilderTags.record(Registries.ITEM, null, id("mymod:my_pick"));
        BuilderTags.record(Registries.ITEM, id("c:tools/pickaxe"), null);

        assertEquals(0, BuilderTags.size());
    }

    // ------------------------------------------------------------------
    // Builders: .tag(...) records pending entries against the right registry
    // ------------------------------------------------------------------

    @Test
    void itemBuilderTagRecordsPendingItemEntryAndStaysChainable() {
        ItemBuilderJS builder = new ItemBuilderJS(id("mymod:my_pick"));

        ItemBuilderJS chained = builder.tag("c:tools/pickaxe").tag("minecraft:mineable/pickaxe");

        assertSame(builder, chained, "tag(...) must return the same builder for chaining");

        RecordingAdder adder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:item"), adder);
        assertEquals(List.of(id("mymod:my_pick")), adder.added.get(id("c:tools/pickaxe")));
        assertEquals(List.of(id("mymod:my_pick")), adder.added.get(id("minecraft:mineable/pickaxe")));
    }

    @Test
    void bareTagNameDefaultsToMinecraftNamespaceAndHashPrefixIsTolerated() {
        new ItemBuilderJS(id("mymod:my_pick"))
                .tag("mineable/pickaxe")
                .tag("#c:tools/pickaxe");

        RecordingAdder adder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:item"), adder);
        assertEquals(List.of(id("mymod:my_pick")), adder.added.get(id("minecraft:mineable/pickaxe")));
        assertEquals(List.of(id("mymod:my_pick")), adder.added.get(id("c:tools/pickaxe")));
    }

    @Test
    void blankTagArgumentsAreSkippedWithoutRecording() {
        new ItemBuilderJS(id("mymod:my_pick")).tag("  ", "c:tools/pickaxe", "");

        assertEquals(1, BuilderTags.size(), "only the well-formed tag must be recorded");
    }

    @Test
    void varargsTagCallRecordsEveryArgument() {
        new ItemBuilderJS(id("mymod:my_pick")).tag("c:tools/pickaxe", "c:tools");

        RecordingAdder adder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:item"), adder);
        assertEquals(2, adder.added.size());
    }

    @Test
    void blockBuilderTagRecordsBlockRegistryEntry() {
        // BlockBuilderJS 构造触碰 SoundType→SoundEvents→注册表；裸 JUnit 跳过本例
        org.junit.jupiter.api.Assumptions.assumeTrue(
                com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "block properties need vanilla registries (no FML loader in bare JUnit)");
        new BlockBuilderJS(id("mymod:my_ore")).tag("minecraft:mineable/pickaxe");

        RecordingAdder blockAdder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:block"), blockAdder);
        assertEquals(List.of(id("mymod:my_ore")), blockAdder.added.get(id("minecraft:mineable/pickaxe")));

        RecordingAdder itemAdder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:item"), itemAdder);
        assertTrue(itemAdder.added.isEmpty(), "block tags must not leak into the item registry");
    }

    @Test
    void entityTypeBuilderTagRecordsEntityTypeRegistryEntry() {
        new EntityTypeBuilderJS(id("mymod:test_mob")).tag("minecraft:raiders");

        RecordingAdder adder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:entity_type"), adder);
        assertEquals(List.of(id("mymod:test_mob")), adder.added.get(id("minecraft:raiders")));
    }

    @Test
    void enchantmentAndPaintingVariantBuildersTagTheirRegistries() {
        new EnchantmentBuilderJS(id("mymod:ice_bane")).tag("minecraft:treasure");
        new PaintingVariantBuilderJS(id("mymod:epic_painting")).tag("minecraft:placeable");

        RecordingAdder enchantmentAdder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:enchantment"), enchantmentAdder);
        assertEquals(List.of(id("mymod:ice_bane")), enchantmentAdder.added.get(id("minecraft:treasure")));

        RecordingAdder paintingAdder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:painting_variant"), paintingAdder);
        assertEquals(List.of(id("mymod:epic_painting")), paintingAdder.added.get(id("minecraft:placeable")));
    }

    @Test
    void fluidBuilderTagCoversSourceAndFlowingFluids() {
        FluidBuilderJS builder = new FluidBuilderJS(id("mymod:molten_iron"));

        assertSame(builder, builder.tag("c:molten_iron"));

        RecordingAdder adder = new RecordingAdder();
        BuilderTags.flushInto(id("minecraft:fluid"), adder);
        assertEquals(
                List.of(id("mymod:molten_iron"), id("mymod:flowing_molten_iron")),
                adder.added.get(id("c:molten_iron")));
    }

    // ------------------------------------------------------------------
    // Composition with the ServerEvents.tags machinery (TagEventJS)
    // ------------------------------------------------------------------

    @Test
    void tagEventConstructorFlushesPendingEntriesAndApplyWritesSourceMap() {
        BuilderTags.record(Registries.ITEM, id("c:tools/pickaxe"), id("mymod:my_pick"));

        Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap = new HashMap<>();
        TagEventJS event = new TagEventJS(id("minecraft:item"), sourceMap);
        event.apply();

        List<TagLoader.EntryWithSource> entries = sourceMap.get(id("c:tools/pickaxe"));
        assertEquals(1, entries.size());
        // TagEntry is identity-equal, so compare by fields
        assertEquals(id("mymod:my_pick"), entries.getFirst().entry().getId());
        assertEquals("NekoJS", entries.getFirst().source());
    }

    @Test
    void tagEventForAnotherRegistryDoesNotConsumeForeignEntries() {
        BuilderTags.record(Registries.ITEM, id("c:tools/pickaxe"), id("mymod:my_pick"));

        Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap = new HashMap<>();
        new TagEventJS(id("minecraft:block"), sourceMap).apply();

        assertTrue(sourceMap.isEmpty(), "block tag event must not receive item pending entries");
    }

    @Test
    void scriptMutationsOnTopOfFlushedEntriesStillApply() {
        BuilderTags.record(Registries.ITEM, id("c:tools/pickaxe"), id("mymod:my_pick"));

        Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap = new HashMap<>();
        TagEventJS event = new TagEventJS(id("minecraft:item"), sourceMap);
        // script listener runs after the constructor flush: it can add on top ...
        event.add("c:tools/pickaxe", "minecraft:iron_pickaxe");
        // ... or explicitly remove a pending builder entry
        event.remove("c:tools/pickaxe", "mymod:my_pick");
        event.apply();

        List<TagLoader.EntryWithSource> entries = sourceMap.get(id("c:tools/pickaxe"));
        assertEquals(1, entries.size());
        assertEquals(id("minecraft:iron_pickaxe"), entries.getFirst().entry().getId());
    }
}
