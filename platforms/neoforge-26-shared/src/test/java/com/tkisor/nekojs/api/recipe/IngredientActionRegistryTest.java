package com.tkisor.nekojs.api.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6 原料动作：{@link IngredientActionRegistry} 的匹配/变换语义。
 *
 * <p>除水桶余量用例外，全部用自定义 Item 实例（{@code new Item(new Item.Properties())}
 * 不触碰注册表），裸 JUnit 可跑；涉及 vanilla 注册表类初始化的用例经
 * {@code VanillaRegistryProbe} 守卫，在无 FML Loader 的 JVM 中跳过（ModDev unitTest 接入后真跑）。
 * mixin 的接线由 MixinTargetResolutionTest 做字节级目标校验。
 */
class IngredientActionRegistryTest {

    @BeforeAll
    static void requireVanillaRegistries() {
        // 26.x Item 构造/ItemStack 组件都会触碰 DataComponents→BuiltInRegistries；
        // 裸 JUnit 跳过，见 VanillaRegistryProbe
        org.junit.jupiter.api.Assumptions.assumeTrue(
                com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "item stack components need vanilla registries (no FML loader in bare JUnit)");
    }

    private Identifier recipeId;

    /** 无耐久物品（stone 代用品）。 */
    private static Item plainItem() {
        return new Item(new Item.Properties());
    }

    /** 耐久 100 的可损耗物品（pickaxe 代用品）。 */
    private static Item durableItem() {
        return new Item(new Item.Properties().durability(100));
    }

    @BeforeEach
    void setUp() {
        IngredientActionRegistry.clear();
        recipeId = Identifier.fromNamespaceAndPath("nekojs", "test_recipe");
    }

    @AfterEach
    void tearDown() {
        IngredientActionRegistry.clear();
    }

    private static CraftingInput inputOf(ItemStack... stacks) {
        return CraftingInput.of(1, stacks.length, List.of(stacks));
    }

    @Test
    void damageActionWearsItemAndConsumesAtBreakPoint() {
        Item tool = durableItem();
        IngredientActionRegistry.record(recipeId, new IngredientActionRegistry.Action(
                IngredientActionRegistry.Kind.DAMAGE, Ingredient.of(tool), 10, ItemStack.EMPTY));

        ItemStack fresh = new ItemStack(tool);
        NonNullList<ItemStack> remainders = CraftingRecipe.defaultCraftingReminder(inputOf(fresh.copy()));
        IngredientActionRegistry.transform(recipeId, inputOf(fresh.copy()), remainders);
        assertEquals(10, remainders.get(0).getDamageValue(), "matched slot must come back with +amount damage");

        ItemStack almostBroken = new ItemStack(tool);
        almostBroken.setDamageValue(95);
        NonNullList<ItemStack> broken = CraftingRecipe.defaultCraftingReminder(inputOf(almostBroken.copy()));
        IngredientActionRegistry.transform(recipeId, inputOf(almostBroken.copy()), broken);
        assertTrue(broken.get(0).isEmpty(), "damage past max durability must consume the item");
    }

    @Test
    void unbreakableMatchedItemIsKeptIntactByDamageAction() {
        Item plain = plainItem();
        IngredientActionRegistry.record(recipeId, new IngredientActionRegistry.Action(
                IngredientActionRegistry.Kind.DAMAGE, Ingredient.of(plain), 5, ItemStack.EMPTY));

        ItemStack plainStack = new ItemStack(plain);
        NonNullList<ItemStack> remainders = CraftingRecipe.defaultCraftingReminder(inputOf(plainStack.copy()));
        IngredientActionRegistry.transform(recipeId, inputOf(plainStack.copy()), remainders);

        assertEquals(plain, remainders.get(0).getItem(), "unbreakable item under a damage action must be kept");
        assertEquals(0, remainders.get(0).getDamageValue());
    }

    @Test
    void keepActionReturnsFreshCopyAndUnmatchedSlotsKeepVanillaRemainder() {
        Assumptions.assumeTrue(com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "water bucket remainder needs vanilla registries (no FML loader in bare JUnit)");
        // 水桶有 vanilla 余量（空桶）：不匹配的槽位必须保留该语义
        IngredientActionRegistry.record(recipeId, new IngredientActionRegistry.Action(
                IngredientActionRegistry.Kind.KEEP, Ingredient.of(Items.DIAMOND), 1, ItemStack.EMPTY));

        ItemStack diamond = new ItemStack(Items.DIAMOND);
        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
        CraftingInput input = inputOf(diamond.copy(), waterBucket.copy());
        NonNullList<ItemStack> remainders = CraftingRecipe.defaultCraftingReminder(inputOf(diamond.copy(), waterBucket.copy()));
        IngredientActionRegistry.transform(recipeId, input, remainders);

        assertEquals(Items.DIAMOND, remainders.get(0).getItem(), "keep action must keep the matched ingredient");
        assertTrue(!remainders.get(0).isEmpty() && remainders.get(0).getCount() == 1, "kept copy must be a single item");
        assertEquals(Items.BUCKET, remainders.get(1).getItem(), "unmatched slot must keep vanilla crafting remainder");
    }

    @Test
    void unmatchedPlainSlotStaysEmpty() {
        Item target = plainItem();
        Item other = new Item(new Item.Properties());
        IngredientActionRegistry.record(recipeId, new IngredientActionRegistry.Action(
                IngredientActionRegistry.Kind.KEEP, Ingredient.of(target), 1, ItemStack.EMPTY));

        CraftingInput input = inputOf(new ItemStack(target), new ItemStack(other));
        NonNullList<ItemStack> remainders = CraftingRecipe.defaultCraftingReminder(inputOf(new ItemStack(target), new ItemStack(other)));
        IngredientActionRegistry.transform(recipeId, input, remainders);

        assertEquals(target, remainders.get(0).getItem(), "matched slot must be kept");
        assertTrue(remainders.get(1).isEmpty(), "unmatched plain item has no remainder and no action match");
    }

    @Test
    void replaceActionSwapsMatchedSlotOnly() {
        Item target = plainItem();
        Item replacement = new Item(new Item.Properties());
        Item bystander = new Item(new Item.Properties());
        IngredientActionRegistry.record(recipeId, new IngredientActionRegistry.Action(
                IngredientActionRegistry.Kind.REPLACE, Ingredient.of(target), 1, new ItemStack(replacement, 2)));

        CraftingInput input = inputOf(new ItemStack(target), new ItemStack(bystander));
        NonNullList<ItemStack> remainders = CraftingRecipe.defaultCraftingReminder(inputOf(new ItemStack(target), new ItemStack(bystander)));
        IngredientActionRegistry.transform(recipeId, input, remainders);

        assertEquals(replacement, remainders.get(0).getItem(), "matched slot must be replaced");
        assertEquals(2, remainders.get(0).getCount(), "replacement must carry its own count");
        assertTrue(remainders.get(1).isEmpty(), "unmatched bystander stays empty");
    }

    @Test
    void clearDropsAllActionsForNextScriptRun() {
        Item target = plainItem();
        IngredientActionRegistry.record(recipeId, new IngredientActionRegistry.Action(
                IngredientActionRegistry.Kind.KEEP, Ingredient.of(target), 1, ItemStack.EMPTY));
        IngredientActionRegistry.clear();

        NonNullList<ItemStack> remainders = CraftingRecipe.defaultCraftingReminder(inputOf(new ItemStack(target)));
        IngredientActionRegistry.transform(recipeId, inputOf(new ItemStack(target)), remainders);
        assertTrue(remainders.get(0).isEmpty(), "after clear() no action may apply");
    }
}
