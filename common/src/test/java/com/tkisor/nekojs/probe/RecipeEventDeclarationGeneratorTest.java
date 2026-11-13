package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
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
 * Regression: imports in the recipes declaration must follow the platform package paths and must not
 * repeat. A schema field kind once hardcoded NeoForge packages ({@code net.minecraft.world.item*})
 * while the handler path used the real FQN ({@code net.minecraft.item*} on 1.12.2). The same alias was
 * then imported from two modules, TS reported Duplicate identifier, the whole recipes declaration file
 * became invalid and {@code event.recipes} completion disappeared.
 */
class RecipeEventDeclarationGeneratorTest {

    /**
     * Stub platform: behaves like {@code TestPlatformInit.TestIPlatform}, but
     * {@code recipeFieldKindPackage} switches on legacy12.
     */
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

    /**
     * A namespace mixing a schema kind field (mapped by the platform) with a handler method (real FQN),
     * shaped like real output.
     */
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
        setPlatform(true); // 1.12.2: Ingredient and ItemStack live in net.minecraft.item[.crafting]
        String out = new RecipeEventDeclarationGenerator(new TypeAliasRegistry())
                .generate(List.of(mixedNamespace("testmod", "net.minecraft.item.ItemStack")), ScriptType.SERVER);

        // The handler FQN (1.12.2) and the schema kind mapped by the platform now resolve to the same source,
        // so each is imported once and from the right package.
        assertEquals(1, out.lines().filter(l -> l.contains("from \"java:net/minecraft/item\"")).count(),
                "ItemStack 只应 import 一次且来自 net.minecraft.item\n" + out);
        assertEquals(1, out.lines().filter(l -> l.contains("from \"java:net/minecraft/item/crafting\"")).count(),
                "Ingredient 只应 import 一次且来自 net.minecraft.item.crafting\n" + out);
        assertFalse(out.contains("world/item"), "1.12.2 平台不得出现 NeoForge 包路径\n" + out);
    }

    @Test
    void defaultPlatformUsesNeoForgePackagePaths() {
        setPlatform(false); // Default NeoForge paths
        String out = new RecipeEventDeclarationGenerator(new TypeAliasRegistry())
                .generate(List.of(mixedNamespace("testmod", "net.minecraft.world.item.ItemStack")), ScriptType.SERVER);

        assertEquals(1, out.lines().filter(l -> l.contains("from \"java:net/minecraft/world/item\"")).count(),
                "默认平台（NeoForge）应为 world.item 包且只 import 一次\n" + out);
        assertEquals(1, out.lines().filter(l -> l.contains("from \"java:net/minecraft/world/item/crafting\"")).count());
        assertTrue(out.contains("export class Testmod$Machine extends $RecipeJsonBuilder"));
        assertTrue(out.contains("input(input: $Ingredient_): this;"));
    }
}
