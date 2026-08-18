package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.RecipeHandlerMethodEntry;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.recipe.RecipeFieldRoles;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归：recipes 声明的 import 必须按平台包路径生成且不重复。
 * 曾出现 schema field kind 硬编码 NeoForge 包（net.minecraft.world.item*）而 handler 反射
 * 路径用真实 FQN（1.12.2 为 net.minecraft.item*）——同一别名从两个模块重复 import，
 * TS 报 Duplicate identifier，整个 recipes 声明文件失效（event.recipes 补全消失）。
 */
class RecipeEventDeclarationGeneratorTest {

    /** 桩平台：与 TestPlatformInit.TestIPlatform 行为一致，仅 recipeFieldKindPackage 按 legacy12 切换。 */
    private static final class StubPlatform implements IPlatform {
        private final boolean legacy12;
        StubPlatform(boolean legacy12) { this.legacy12 = legacy12; }

        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "0.0.0"; }
        @Override public Path getGameDir() { return Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir"); }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0.0.0"; }

        @Override
        public String recipeFieldKindPackage(RecipeFieldKind kind) {
            if (!legacy12) return IPlatform.super.recipeFieldKindPackage(kind);
            return switch (kind) {
                case INGREDIENT -> "net.minecraft.item.crafting";
                case ITEM_STACK -> "net.minecraft.item";
                default -> IPlatform.super.recipeFieldKindPackage(kind);
            };
        }
    }

    private static void setPlatform(boolean legacy12) {
        try {
            Field f = Platform.class.getDeclaredField("INSTANCE");
            f.setAccessible(true);
            f.set(null, new StubPlatform(legacy12));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set test platform", e);
        }
    }

    /** schema kind 字段（走平台映射）+ handler 方法（真实 FQN）混合的命名空间，复刻真实输出形态。 */
    private static RecipeNamespaceCatalogEntry mixedNamespace(String ns, String itemStackFqn) {
        RecipeFieldDefinition input = new RecipeFieldDefinition("input", "input", RecipeFieldKind.INGREDIENT,
                false, false, null, RecipeFieldRoles.roleOfName("input"));
        RecipeFieldDefinition result = new RecipeFieldDefinition("result", "result", RecipeFieldKind.ITEM_STACK,
                false, false, null, RecipeFieldRoles.roleOfName("result"));
        RecipeTypeDefinition def = new RecipeTypeDefinition(ns, "machine", ns + ":machine", ns + "_machine",
                List.of(List.of("input", "result")), Map.of("input", input, "result", result), List.of("result"));
        RecipeHandlerMethodEntry craft = new RecipeHandlerMethodEntry("craft",
                List.of(new RecipeHandlerMethodEntry.HandlerParam("output", "ItemStack", itemStackFqn,
                        itemStackFqn, false)), 1);
        return new RecipeNamespaceCatalogEntry(ns, RecipeEventDeclarationGeneratorTest.class,
                List.of("machine", "craft"), true, List.of(), List.of(craft),
                List.of(RecipeSchemaTypeEntry.from(def)));
    }

    @Test
    void legacy12PlatformMapsSchemaKindsToItemPackages() {
        setPlatform(true); // 1.12.2：Ingredient/ItemStack 在 net.minecraft.item[.crafting]
        String out = new RecipeEventDeclarationGenerator(new TypeAliasRegistry())
                .generate(List.of(mixedNamespace("testmod", "net.minecraft.item.ItemStack")), ScriptType.SERVER);

        // handler 真实 FQN（1.12.2）与 schema kind 走平台映射后同源：只 import 一次且来自正确包
        assertEquals(1, out.lines().filter(l -> l.contains("from \"java:net/minecraft/item\"")).count(),
                "ItemStack 只应 import 一次且来自 net.minecraft.item\n" + out);
        assertEquals(1, out.lines().filter(l -> l.contains("from \"java:net/minecraft/item/crafting\"")).count(),
                "Ingredient 只应 import 一次且来自 net.minecraft.item.crafting\n" + out);
        assertFalse(out.contains("world/item"), "1.12.2 平台不得出现 NeoForge 包路径\n" + out);
    }

    @Test
    void defaultPlatformUsesNeoForgePackagePaths() {
        setPlatform(false); // NeoForge 默认路径
        String out = new RecipeEventDeclarationGenerator(new TypeAliasRegistry())
                .generate(List.of(mixedNamespace("testmod", "net.minecraft.world.item.ItemStack")), ScriptType.SERVER);

        assertEquals(1, out.lines().filter(l -> l.contains("from \"java:net/minecraft/world/item\"")).count(),
                "默认平台（NeoForge）应为 world.item 包且只 import 一次\n" + out);
        assertEquals(1, out.lines().filter(l -> l.contains("from \"java:net/minecraft/world/item/crafting\"")).count());
        assertTrue(out.contains("export class Testmod$Machine extends $RecipeJsonBuilder"));
        assertTrue(out.contains("input(input: $Ingredient_): this;"));
    }
}
