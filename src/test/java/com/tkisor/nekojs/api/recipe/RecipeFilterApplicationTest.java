// Shared test tree for 26.x and 1.21.1: id construction goes through version guards, because
// RecipeHolder takes a ResourceKey on 26.x and an Identifier on 1.21.1. Filters only read the
// holder id and never the recipe value, which is passed as null; neither record validates it.
package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.tkisor.nekojs.js.type_adapter.RecipeFilterAdapter;
import graal.graalvm.polyglot.Context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filter application fixture: the {@link RecipeFilter} combinators applied to a recipe holder
 * decide which recipes a modify or remove call sees. Id-based filters and their and/or/not
 * combinators only read the holder id, so through the version-neutral
 * {@link RecipeEventJS#recipeHolderId} accessor they run wherever {@code RecipeHolder} can
 * initialize (see {@link com.tkisor.nekojs.testfixture.VanillaRegistryProbe}: bare 26.x JVMs skip,
 * 1.21.1 bare JVMs and ModDev unitTest environments run). Output, input and type filters need live
 * registries and are covered by the runtime smoke tests.
 */
class RecipeFilterApplicationTest {

    @BeforeAll
    static void requireRecipeHolderInitialization() {
        // RecipeHolder.<clinit> reaches Recipe.CODEC -> registry bootstrap; bare 26.x JVMs skip.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "RecipeHolder initialization needs vanilla registries (no FML loader in bare JUnit)");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RecipeHolder<?> holder(String id) {
        int colon = id.indexOf(':');
//? if >=26 {
        return new RecipeHolder<>(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(id.substring(0, colon), id.substring(colon + 1))),
                null);
//?} else {
        /*return new RecipeHolder<>(
                Identifier.fromNamespaceAndPath(id.substring(0, colon), id.substring(colon + 1)), null);
*///?}
    }

    @Test
    void idBasedFiltersMatchTheirOwnSemantics() {
        RecipeHolder<?> chest = holder("minecraft:chest");

        assertTrue(new RecipeFilter.ByMod("minecraft").test(chest, null));
        assertFalse(new RecipeFilter.ByMod("mymod").test(chest, null));

        assertTrue(new RecipeFilter.ById("minecraft:chest").test(chest, null));
        assertTrue(new RecipeFilter.ByIdStartsWith("minecraft:ch").test(chest, null));
        assertTrue(new RecipeFilter.ByIdEndsWith("est").test(chest, null));
        assertTrue(new RecipeFilter.ByIdContains(":che").test(chest, null));
        assertFalse(new RecipeFilter.ByIdStartsWith("nekojs:").test(chest, null));
    }

    @Test
    void combinatorsComposeIdFilters() {
        RecipeHolder<?> chest = holder("minecraft:chest");
        RecipeHolder<?> machine = holder("nekojs:machine");

        RecipeFilter both = new RecipeFilter.And(java.util.List.of(
                new RecipeFilter.ByMod("minecraft"), new RecipeFilter.ByIdStartsWith("minecraft:")));
        assertTrue(both.test(chest, null));
        assertFalse(both.test(machine, null));

        RecipeFilter either = new RecipeFilter.Or(java.util.List.of(
                new RecipeFilter.ByMod("minecraft"), new RecipeFilter.ByMod("nekojs")));
        assertTrue(either.test(chest, null));
        assertTrue(either.test(machine, null));
        assertFalse(either.test(holder("create:crushing"), null));

        RecipeFilter notMinecraft = new RecipeFilter.Not(new RecipeFilter.ByMod("minecraft"));
        assertFalse(notMinecraft.test(chest, null));
        assertTrue(notMinecraft.test(machine, null));
    }

    @Test
    void byIdBareNameResolvesIntoTheMinecraftNamespace() {
        // ById("chest") parses to minecraft:chest (constructor behavior) — same target.
        RecipeHolder<?> chest = holder("minecraft:chest");
        assertTrue(new RecipeFilter.ById("chest").test(chest, null));
        assertFalse(new RecipeFilter.ById("chest").test(holder("nekojs:chest"), null));
    }

    @Test
    void parsedAndArraySelectsOnlyRecipesMatchingEveryCondition() {
        try (Context context = Context.newBuilder("js").build()) {
            RecipeFilter filter = new RecipeFilterAdapter().apply(context.eval("js",
                    "({and: [{mod: 'minecraft'}, {idContains: 'chest'}]})"));
            assertTrue(filter.test(holder("minecraft:chest"), null), "Both conditions match");
            assertFalse(filter.test(holder("minecraft:stone"), null), "Only the mod condition matches");
            assertFalse(filter.test(holder("create:chest"), null), "Only the id condition matches");
            assertFalse(filter.test(holder("create:stone"), null), "Neither condition matches");
        }
    }

    @Test
    void parsedOrArraysAndFlatObjectsKeepTheirExistingSelectionRules() {
        try (Context context = Context.newBuilder("js").build()) {
            var adapter = new RecipeFilterAdapter();
            for (String expression : java.util.List.of(
                    "[{mod:'minecraft'},{idContains:'chest'}]",
                    "({or:[{mod:'minecraft'},{idContains:'chest'}]})")) {
                RecipeFilter filter = adapter.apply(context.eval("js", expression));
                assertTrue(filter.test(holder("minecraft:chest"), null));
                assertTrue(filter.test(holder("minecraft:stone"), null));
                assertTrue(filter.test(holder("create:chest"), null));
                assertFalse(filter.test(holder("create:stone"), null));
            }
            RecipeFilter flat = adapter.apply(context.eval("js", "({mod:'minecraft',idContains:'chest'})"));
            assertTrue(flat.test(holder("minecraft:chest"), null));
            assertFalse(flat.test(holder("minecraft:stone"), null));
            assertFalse(flat.test(holder("create:chest"), null));
        }
    }

    @Test
    void parsedEmptyAndNegatedGroupsKeepBooleanIdentities() {
        try (Context context = Context.newBuilder("js").build()) {
            var adapter = new RecipeFilterAdapter();
            for (String expression : java.util.List.of("({and:[]})", "({not:{or:[]}})",
                    "({and:[{not:{or:[]}}, {and:[]}]})")) {
                assertTrue(adapter.apply(context.eval("js", expression)).test(holder("minecraft:chest"), null), expression);
            }
            for (String expression : java.util.List.of("[]", "({or:[]})", "({not:{and:[]}})",
                    "({or:[{not:{and:[]}}, {or:[]}]})")) {
                assertFalse(adapter.apply(context.eval("js", expression)).test(holder("minecraft:chest"), null), expression);
            }
            RecipeFilter mixed = adapter.apply(context.eval("js",
                    "({and:[{or:[{mod:'minecraft'},{mod:'nekojs'}]},{not:{idContains:'blocked'}}]})"));
            assertTrue(mixed.test(holder("minecraft:chest"), null));
            assertFalse(mixed.test(holder("minecraft:blocked_chest"), null));
            assertFalse(mixed.test(holder("create:chest"), null));
        }
    }

    @Test
    void versionNeutralHolderIdAccessorKeepsBothErasOnOneShape() {
        // recipeHolderId is what the filters use across versions; pin its toString form.
        assertEquals("nekojs:machine", RecipeEventJS.recipeHolderId(holder("nekojs:machine")).toString());
    }
}
//?}
