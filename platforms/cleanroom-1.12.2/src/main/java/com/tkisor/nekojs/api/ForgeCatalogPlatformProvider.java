package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.catalog.NekoCatalogPlatformProvider;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import com.tkisor.nekojs.api.catalog.SnippetCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeOutputLayout;
import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.js.type_adapter.TileEntityAdapter;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ForgeCatalogPlatformProvider implements NekoCatalogPlatformProvider {

    @Override
    public Collection<RegistryTypeCatalogEntry> registryTypes() {
        List<RegistryTypeCatalogEntry> entries = new ArrayList<>();
        entries.add(registry("Item", ForgeRegistries.ITEMS));
        entries.add(registry("Block", ForgeRegistries.BLOCKS));

        // Fluid from FluidRegistry (ForgeRegistries.FLUIDS may not exist in 1.12.2)
        List<String> fluidIds = new ArrayList<>();
        for (String name : FluidRegistry.getRegisteredFluids().keySet()) {
            fluidIds.add(name);
        }
        fluidIds.sort(String::compareTo);
        entries.add(new RegistryTypeCatalogEntry("Fluid", fluidIds, List.of()));

        entries.add(registry("EntityType", ForgeRegistries.ENTITIES));
        entries.add(registry("SoundEvent", ForgeRegistries.SOUND_EVENTS));
        entries.add(registry("Potion", ForgeRegistries.POTIONS));
        entries.add(registry("Enchantment", ForgeRegistries.ENCHANTMENTS));
        entries.add(registry("Biome", ForgeRegistries.BIOMES));

        // Particle types from EnumParticleTypes enum
        List<String> particleIds = new ArrayList<>();
        for (EnumParticleTypes type : EnumParticleTypes.values()) {
            particleIds.add(type.getParticleName());
        }
        particleIds.sort(String::compareTo);
        entries.add(new RegistryTypeCatalogEntry("ParticleType", particleIds, List.of()));

        // TileEntity: 1.12.2 has no IForgeRegistry for tile entities; ids come from
        // TileEntityAdapter's reflection over the internal REGISTRY (RegistryNamespaced).
        entries.add(new RegistryTypeCatalogEntry("TileEntity", TileEntityAdapter.allRegisteredIds(), List.of()));

        return entries;
    }

    private static <T extends net.minecraftforge.registries.IForgeRegistryEntry<T>> RegistryTypeCatalogEntry registry(
            String typeName,
            net.minecraftforge.registries.IForgeRegistry<T> registry
    ) {
        List<String> ids = new ArrayList<>();
        registry.getKeys().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(ids::add);
        return new RegistryTypeCatalogEntry(typeName, ids, List.of());
    }

    @Override
    public Collection<SnippetCatalogEntry> snippets() {
        return List.of(
                new SnippetCatalogEntry("Server started event", ScriptType.SERVER, "server-started",
                        "ServerEvents.started(event => {\n  $0\n})",
                        "Run code after the server has started"),
                new SnippetCatalogEntry("Recipe event", ScriptType.SERVER, "server-recipes",
                        "ServerEvents.recipes(event => {\n  $0\n})",
                        "Modify datapack recipe JSON"),
                new SnippetCatalogEntry("Shapeless recipe", ScriptType.SERVER, "recipe-shapeless",
                        "event.recipes.minecraft.shapeless(ItemJS.of('${1:minecraft:stick}'), [Ingredient.of('${2:#minecraft:planks}')])",
                        "Create a minecraft shapeless recipe"),
                new SnippetCatalogEntry("Item registry", ScriptType.STARTUP, "registry-item",
                        "RegistryEvents.item(event => {\n  event.create('${1:modid:item_id}')\n    .displayName('${2:My Item}')\n  $0\n})",
                        "Register a new item")
        );
    }

    @Override
    public Collection<RecipeNamespaceCatalogEntry> recipeNamespaces() {
        return NekoRecipeNamespaces.getHandlerClasses().entrySet().stream()
                .map(entry -> recipeNamespace(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static RecipeNamespaceCatalogEntry recipeNamespace(String namespace, Class<?> handlerClass) {
        List<String> examples = namespace.equals("minecraft") ? List.of(
                "event.recipes.minecraft.shapeless(ItemJS.of('minecraft:stick'), [Ingredient.of('#minecraft:planks')])",
                "event.recipes.minecraft.smelting(Ingredient.of('minecraft:iron_ore'), ItemJS.of('minecraft:iron_ingot'))"
        ) : List.of();
        return RecipeNamespaceCatalogEntry.withHandlerMethods(namespace, handlerClass,
                List.copyOf(NekoRecipeNamespaces.getRecipeTypes(handlerClass)), true, examples);
    }

    @Override
    public TypeOutputLayout outputLayout() {
        return new TypeOutputLayout(
                NekoJSPaths.get().probeDir(),
                NekoJSPaths.get().gameDir().resolve(".vscode").resolve("nekojs.code-snippets")
        );
    }
}
