package com.tkisor.nekojs.api.recipe.definition;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyRecipeSchemaScannerTest {

    /** 测试基类：补齐 IRecipe + IForgeRegistryEntry 的抽象方法，子类只声明业务字段。 */
    public abstract static class AbstractTestRecipe implements IRecipe {
        private ResourceLocation registryName;

        @Override public boolean matches(InventoryCrafting inv, World worldIn) { return false; }
        @Override public ItemStack getCraftingResult(InventoryCrafting inv) { return ItemStack.EMPTY; }
        @Override public boolean canFit(int width, int height) { return true; }
        @Override public ItemStack getRecipeOutput() { return ItemStack.EMPTY; }
        @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.create(); }
        @Override public IRecipe setRegistryName(ResourceLocation name) { this.registryName = name; return this; }
        @Override public ResourceLocation getRegistryName() { return registryName; }
        @Override public Class<IRecipe> getRegistryType() { return IRecipe.class; }
    }

    /** 可扫描的配方类：标准字段类型齐全。 */
    public static class MachineRecipe extends AbstractTestRecipe {
        public Ingredient input;
        public ItemStack output;
        public int energy;
        public float chance;
        public String group;
        public NonNullList<Ingredient> catalysts;
        private static final int IGNORED_STATIC = 1; // static 应被跳过
        public transient java.util.Map<String, Object> cache; // 不可转换类型：剔除（运行时注入不了）
        public com.google.gson.JsonObject payload; // JsonElement 族：JSON kind 保留
        public final ItemStack frozen = null; // final：注入路径赋不了值，剔除
        public java.util.regex.Pattern pattern; // 未知类型：剔除
        public int[] slots; // 基元数组：剔除
    }

    public static final class MinimalRecipe extends AbstractTestRecipe {
        public ItemStack result;
    }

    @Test
    void infersFieldsWithKindsAndArrayFlags() {
        List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> keys =
                LegacyRecipeSchemaScanner.inferFields(MachineRecipe.class);
        assertTrue(keys.stream().anyMatch(k -> k.name().equals("input") && k.kind() == RecipeFieldKind.INGREDIENT && !k.isList()));
        assertTrue(keys.stream().anyMatch(k -> k.name().equals("output") && k.kind() == RecipeFieldKind.ITEM_STACK));
        assertTrue(keys.stream().anyMatch(k -> k.name().equals("energy") && k.kind() == RecipeFieldKind.INT));
        assertTrue(keys.stream().anyMatch(k -> k.name().equals("chance") && k.kind() == RecipeFieldKind.NUMBER));
        assertTrue(keys.stream().anyMatch(k -> k.name().equals("group") && k.kind() == RecipeFieldKind.STRING));
        assertTrue(keys.stream().anyMatch(k -> k.name().equals("catalysts") && k.kind() == RecipeFieldKind.INGREDIENT && k.isList()));
        assertTrue(keys.stream().anyMatch(k -> k.name().equals("payload") && k.kind() == RecipeFieldKind.JSON),
                "JsonElement 族字段应保留为 JSON kind");
        assertFalse(keys.stream().anyMatch(k -> k.name().equals("cache")), "Map 等不可转换类型必须剔除");
        assertFalse(keys.stream().anyMatch(k -> k.name().equals("frozen")), "final 字段必须剔除");
        assertFalse(keys.stream().anyMatch(k -> k.name().equals("pattern")), "未知类型必须剔除");
        assertFalse(keys.stream().anyMatch(k -> k.name().equals("slots")), "基元数组必须剔除");
        assertFalse(keys.stream().anyMatch(k -> k.name().equals("IGNORED_STATIC")), "static 字段必须排除");
    }

    /**
     * 复刻 IE 的形态：直接继承 {@code IForgeRegistryEntry.Impl} 的特殊配方
     * （RecipeJerrycan/RecipeEarmuffs 等）。基类的 token/delegate/registryName
     * 不得进入 schema。
     */
    public abstract static class ImplBasedRecipe
            extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe>
            implements IRecipe {
        @Override public boolean matches(InventoryCrafting inv, World worldIn) { return false; }
        @Override public ItemStack getCraftingResult(InventoryCrafting inv) { return ItemStack.EMPTY; }
        @Override public boolean canFit(int width, int height) { return true; }
        @Override public ItemStack getRecipeOutput() { return ItemStack.EMPTY; }
    }

    public static class RegistryToolRecipe extends ImplBasedRecipe {
        public Ingredient tool;
    }

    public static class RegistryJunkOnlyRecipe extends ImplBasedRecipe {}

    @Test
    void registryEntryBaseFieldsAreExcluded() {
        List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> keys =
                LegacyRecipeSchemaScanner.inferFields(RegistryToolRecipe.class);
        assertTrue(keys.stream().anyMatch(k -> k.name().equals("tool") && k.kind() == RecipeFieldKind.INGREDIENT),
                "自身声明的字段应保留");
        assertFalse(keys.stream().anyMatch(k -> k.name().equals("token")),
                "IForgeRegistryEntry.Impl 的 TypeToken token 是内部字段，必须剔除");
        assertFalse(keys.stream().anyMatch(k -> k.name().equals("delegate")),
                "IForgeRegistryEntry.Impl 的 delegate 是内部字段，必须剔除");
        assertFalse(keys.stream().anyMatch(k -> k.name().equals("registryName")),
                "registryName 由 builder 自动生成，不得进入 schema");
    }

    @Test
    void implBasedRecipeWithNoOwnFieldsYieldsNoType() {
        RegistryJunkOnlyRecipe recipe = new RegistryJunkOnlyRecipe();
        recipe.setRegistryName(new ResourceLocation("testmod", "junk"));
        RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes discovered =
                LegacyRecipeSchemaScanner.collectTypes(List.of(recipe));
        assertTrue(discovered.types().isEmpty(),
                "只剩注册表基类字段的配方（如 IE jerrycan）不得产出 schema 类型");
    }

    @Test
    void typeNameIsSnakeCaseOfSimpleName() {
        assertEquals("machine_recipe", LegacyRecipeSchemaScanner.typeNameFor(MachineRecipe.class));
        assertEquals("minimal_recipe", LegacyRecipeSchemaScanner.typeNameFor(MinimalRecipe.class));
        assertEquals("thermal_expansion_recipe", LegacyRecipeSchemaScanner.typeNameFor(ThermalExpansionRecipe.class));
        assertEquals("x2_thing", LegacyRecipeSchemaScanner.typeNameFor(X2Thing.class));
    }

    public static class ThermalExpansionRecipe extends AbstractTestRecipe {
        public ItemStack output;
    }

    public static class X2Thing extends AbstractTestRecipe {
        public ItemStack output;
    }

    @Test
    void scanCollectsTypesFromRecipeIterableAndSkipsVanilla() {
        ResourceLocation modId = new ResourceLocation("testmod", "a");
        ResourceLocation vanillaId = new ResourceLocation("minecraft", "b");
        MachineRecipe machine = new MachineRecipe();
        machine.setRegistryName(modId);
        MinimalRecipe minimal = new MinimalRecipe();
        minimal.setRegistryName(vanillaId);

        RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes discovered =
                LegacyRecipeSchemaScanner.collectTypes(List.of(machine, minimal, machine));

        assertTrue(discovered.types().containsKey("testmod:machine_recipe"),
                "testmod 命名空间应收集到 machine_recipe");
        assertFalse(discovered.types().containsKey("minecraft:minimal_recipe"),
                "minecraft 命名空间必须跳过");
        assertEquals(1, discovered.types().size(),
                "同一类多个配方实例应去重为一个类型（types map 每个 key 是一个类型）");
        assertTrue(discovered.types().get("testmod:machine_recipe").stream()
                        .anyMatch(k -> k.name().equals("catalysts") && k.isList()),
                "列表字段应保留 list 标记");
    }

    @Test
    void classWithoutInferableFieldsIsSkipped() {
        RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes discovered =
                LegacyRecipeSchemaScanner.collectTypes(List.of(new NoFieldsRecipe()));
        assertTrue(discovered.types().isEmpty(), "无可注入字段的类不得产出类型");
    }

    public static class NoFieldsRecipe extends AbstractTestRecipe {
        public void nothing() {}
    }
}
