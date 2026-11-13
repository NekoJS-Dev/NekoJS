// Catalog platform provider for the Fabric node. It mirrors the shared NeoForgeCatalogPlatformProvider
// (guarded neoforge for the whole file) with three differences: hostExtensions omits the ItemStack and
// BlockState sources because both extension interfaces are still guarded whole-file on Fabric and the
// classes do not exist; snippets, registryTypes and modIds are reused verbatim. Once ItemStackExtension
// and BlockStateExtension are ported, this file merges back into the shared copy.
package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.catalog.HostExtensionSource;
import com.tkisor.nekojs.api.catalog.NekoCatalogPlatformProvider;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import com.tkisor.nekojs.api.catalog.SnippetCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeOutputLayout;
import com.tkisor.nekojs.api.inject.BlockExtension;
import com.tkisor.nekojs.api.inject.BlockStateExtension;
import com.tkisor.nekojs.api.inject.EntityExtension;
import com.tkisor.nekojs.api.inject.ItemExtension;
import com.tkisor.nekojs.api.inject.ItemStackExtension;
import com.tkisor.nekojs.api.inject.LevelExtension;
import com.tkisor.nekojs.api.inject.LivingEntityExtension;
import com.tkisor.nekojs.api.inject.MutableComponentExtension;
import com.tkisor.nekojs.api.inject.PlayerExtension;
import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.js.type_adapter.RegistryTypeCatalogs;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.List;

/** Probe and workspace catalog source for Fabric: registry types, host extensions, recipe namespaces
 * and snippets. */
public class FabricCatalogPlatformProvider implements NekoCatalogPlatformProvider {
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
        return RecipeNamespaceCatalogEntry.withHandlerMethods(namespace, handlerClass, List.copyOf(NekoRecipeNamespaces.getRecipeTypes(handlerClass)), true, examples);
    }

    @Override
    public Collection<HostExtensionSource> hostExtensions() {
        // Nine sources, same as the NeoForge version: the ItemStack and BlockState extensions join once the
        // inject chain is active and MixinItemStack/MixinBlockState are listed in
        // nekojs-fabric-shared.mixins.json
        return List.of(
                HostExtensionSource.any(ItemStack.class, ItemStackExtension.class),
                HostExtensionSource.any(Item.class, ItemExtension.class),
                HostExtensionSource.any(Block.class, BlockExtension.class),
                HostExtensionSource.any(BlockState.class, BlockStateExtension.class),
                HostExtensionSource.any(Entity.class, EntityExtension.class),
                HostExtensionSource.any(LivingEntity.class, LivingEntityExtension.class),
                HostExtensionSource.any(Player.class, PlayerExtension.class),
                HostExtensionSource.any(Level.class, LevelExtension.class),
                HostExtensionSource.any(MutableComponent.class, MutableComponentExtension.class)
        );
    }

    @Override
    public Collection<RegistryTypeCatalogEntry> registryTypes() {
        return RegistryTypeCatalogs.snapshot(RegistryTypeCatalogs.builtInSources(), List.of(), FabricCatalogPlatformProvider::tagIds);
    }

    @Override
    public Collection<RegistryTypeCatalogEntry> registryTypes(List<JSTypeAdapter<?>> adapters) {
        Collection<RegistryTypeCatalogEntry> provided = registryTypes();
        return RegistryTypeCatalogs.append(provided, RegistryTypeCatalogs.builtInSources(), adapters,
                FabricCatalogPlatformProvider::tagIds);
    }

    /** Same meaning as the NeoForge version: 26.x uses getTags(); the stream is empty while tags are not
     * loaded, so the probe side falls back to a string. */
    private static <T> List<String> tagIds(net.minecraft.core.Registry<T> registry) {
        return registry.getTags()
                .map(named -> named.key().location().toString())
                .sorted()
                .toList();
    }

    @Override
    public Collection<String> modIds() {
        return Platform.getMods().keySet();
    }

    @Override
    public Collection<SnippetCatalogEntry> snippets() {
        return List.of(
                new SnippetCatalogEntry("Server started event", ScriptType.SERVER, "server-started", "ServerEvents.started(event => {\n  $0\n})", "Run code after the server has started"),
                new SnippetCatalogEntry("Recipe event", ScriptType.SERVER, "server-recipes", "ServerEvents.recipes(event => {\n  $0\n})", "Modify datapack recipe JSON"),
                new SnippetCatalogEntry("After recipes event", ScriptType.SERVER, "server-after-recipes", "ServerEvents.afterRecipes(event => {\n  ${1:event.print()}\n  $0\n})", "Inspect or finalize recipe JSON after recipe scripts run"),
                new SnippetCatalogEntry("Recipe namespace types", ScriptType.SERVER, "recipe-types", "ServerEvents.recipes(event => {\n  console.info(event.recipes.namespaces())\n  console.info(event.recipes.types('${1:minecraft}'))\n  $0\n})", "Inspect registered recipe namespaces and typed handler methods"),
                new SnippetCatalogEntry("Fallback recipe namespace", ScriptType.SERVER, "recipe-fallback", "ServerEvents.recipes(event => {\n  event.recipes.${1:mymod}.${2:custom_type}({\n    ${3:key}: ${4:value}\n  })\n  $0\n})", "Create raw JSON for a namespace without a typed handler method"),
                new SnippetCatalogEntry("Shapeless recipe", ScriptType.SERVER, "recipe-shapeless", "event.recipes.minecraft.shapeless(ItemJS.of('${1:minecraft:stick}'), [Ingredient.of('${2:#minecraft:planks}')])", "Create a minecraft shapeless recipe"),
                new SnippetCatalogEntry("Recipe JSON builder", ScriptType.SERVER, "recipe-builder", "event.builder('${1:minecraft:crafting_shapeless}')\n  .id('${2:nekojs:example}')\n  .property('ingredients', [Ingredient.of('${3:minecraft:stone}')])\n  .output('result', ItemJS.of('${4:minecraft:stone_button}'))", "Create a raw JSON-first recipe builder")
        );
    }

    @Override
    public TypeOutputLayout outputLayout() {
        return new TypeOutputLayout(
                NekoJSPaths.get().probeDir(),
                NekoJSPaths.get().gameDir().resolve(".vscode").resolve("nekojs.code-snippets")
        );
    }
}
