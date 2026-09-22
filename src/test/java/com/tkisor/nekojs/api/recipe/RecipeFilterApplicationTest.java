// 26.x/1.21.1 共享测试树：id 构造经版本守卫（26.x RecipeHolder 吃 ResourceKey，1.21.1 吃
// ResourceLocation）；filter 只读 holder id，recipe 值不参与（传 null，两侧 record 均不校验）。
//? if neoforge {
package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 23 AC2 filter-application fixture: the {@link RecipeFilter} combinators applied to a
 * recipe holder decide which recipes a modify/remove call sees. Id-based filters and their
 * and/or/not combinators only read the holder id, so they run wherever {@code RecipeHolder}
 * can initialize (see {@link com.tkisor.nekojs.testfixture.VanillaRegistryProbe}: bare 26.x
 * JVMs skip, 1.21.1 bare JVMs and ModDev unitTest environments run) via the version-neutral
 * {@link RecipeEventJS#recipeHolderId} seam; output/input/type filters need live registries
 * and stay with the runtime smoke evidence (ticket 34).
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
/*        return new RecipeHolder<>(
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
    void versionNeutralHolderIdAccessorKeepsBothErasOnOneShape() {
        // recipeHolderId is the seam the filters use across eras; pin its toString form.
        assertEquals("nekojs:machine", RecipeEventJS.recipeHolderId(holder("nekojs:machine")).toString());
    }
}
//?}
