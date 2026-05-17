package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.catalog.HostExtensionSource;
import com.tkisor.nekojs.api.catalog.NekoCatalogPlatformProvider;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.SnippetCatalogEntry;
import com.tkisor.nekojs.api.inject.*;
import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import com.tkisor.nekojs.script.ScriptType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.List;

public class NeoForgeCatalogPlatformProvider implements NekoCatalogPlatformProvider {
    @Override
    public Collection<RecipeNamespaceCatalogEntry> recipeNamespaces() {
        return NekoRecipeNamespaces.getHandlerClasses().entrySet().stream()
                .map(entry -> recipeNamespace(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static RecipeNamespaceCatalogEntry recipeNamespace(String namespace, Class<?> handlerClass) {
        List<String> examples = namespace.equals("minecraft") ? List.of(
                "event.recipes.minecraft.shapeless(ItemJS.of('minecraft:stick'), [Ingredient.of('#minecraft:planks')])",
                "event.recipes.minecraft.smelting(Ingredient.of('minecraft:iron_ore'), ItemJS.of('minecraft:iron_ingot'))",
                "event.builder('minecraft:crafting_shapeless').property('ingredients', [Ingredient.of('minecraft:stone')]).output('result', ItemJS.of('minecraft:stone_button'))"
        ) : List.of("event.recipes." + namespace + ".<recipeType>({ type: '" + namespace + ":<recipeType>' })");
        return new RecipeNamespaceCatalogEntry(namespace, handlerClass, List.copyOf(NekoRecipeNamespaces.getRecipeTypes(namespace)), true, examples);
    }

    @Override
    public Collection<HostExtensionSource> hostExtensions() {
        return List.of(
                HostExtensionSource.common(ItemStack.class, ItemStackExtension.class),
                HostExtensionSource.common(Item.class, ItemExtension.class),
                HostExtensionSource.common(Block.class, BlockExtension.class),
                HostExtensionSource.common(BlockState.class, BlockStateExtension.class),
                HostExtensionSource.common(Entity.class, EntityExtension.class),
                HostExtensionSource.common(LivingEntity.class, LivingEntityExtension.class),
                HostExtensionSource.common(Player.class, PlayerExtension.class),
                HostExtensionSource.common(Level.class, LevelExtension.class)
        );
    }

    @Override
    public Collection<SnippetCatalogEntry> snippets() {
        return List.of(
                new SnippetCatalogEntry("Server started event", ScriptType.SERVER, "server-started", "ServerEvents.started(event => {\n  $0\n})", "Run code after the server has started"),
                new SnippetCatalogEntry("Recipe event", ScriptType.SERVER, "server-recipes", "ServerEvents.recipes(event => {\n  $0\n})", "Modify datapack recipe JSON"),
                new SnippetCatalogEntry("After recipes event", ScriptType.SERVER, "server-after-recipes", "ServerEvents.afterRecipes(event => {\n  ${1:event.print()}\n  $0\n})", "Inspect or finalize recipe JSON after recipe scripts run"),
                new SnippetCatalogEntry("Recipe namespace types", ScriptType.SERVER, "recipe-types", "ServerEvents.recipes(event => {\n  console.info(event.recipes.namespaces())\n  console.info(event.recipes.types('${1:minecraft}'))\n  $0\n})", "Inspect registered recipe namespaces and typed handler methods"),
                new SnippetCatalogEntry("Fallback recipe namespace", ScriptType.SERVER, "recipe-fallback", "event.recipes.${1:mymod}.${2:custom_type}({\n  ${3:key}: ${4:value}\n})", "Create raw JSON for a namespace without a typed handler method"),
                new SnippetCatalogEntry("Shapeless recipe", ScriptType.SERVER, "recipe-shapeless", "event.recipes.minecraft.shapeless(ItemJS.of('${1:minecraft:stick}'), [Ingredient.of('${2:#minecraft:planks}')])", "Create a minecraft shapeless recipe"),
                new SnippetCatalogEntry("Recipe JSON builder", ScriptType.SERVER, "recipe-builder", "event.builder('${1:minecraft:crafting_shapeless}')\n  .id('${2:nekojs:example}')\n  .property('ingredients', [Ingredient.of('${3:minecraft:stone}')])\n  .output('result', ItemJS.of('${4:minecraft:stone_button}'))", "Create a raw JSON-first recipe builder")
        );
    }
}
