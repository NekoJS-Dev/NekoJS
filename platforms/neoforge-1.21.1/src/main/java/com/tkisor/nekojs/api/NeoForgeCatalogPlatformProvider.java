package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.catalog.HostExtensionSource;
import com.tkisor.nekojs.api.catalog.NekoCatalogPlatformProvider;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import com.tkisor.nekojs.api.catalog.SnippetCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeOutputLayout;
import com.tkisor.nekojs.api.inject.*;
import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.api.ScriptType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
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
        return RecipeNamespaceCatalogEntry.withHandlerMethods(namespace, handlerClass, List.copyOf(NekoRecipeNamespaces.getRecipeTypes(handlerClass)), true, examples);
    }

    @Override
    public Collection<HostExtensionSource> hostExtensions() {
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
        List<RegistryTypeCatalogEntry> entries = new ArrayList<>();
        entries.add(registry("Item", BuiltInRegistries.ITEM));
        entries.add(registry("Block", BuiltInRegistries.BLOCK));
        entries.add(registry("Fluid", BuiltInRegistries.FLUID));
        entries.add(registry("EntityType", BuiltInRegistries.ENTITY_TYPE));
        entries.add(registry("MobEffect", BuiltInRegistries.MOB_EFFECT));
        entries.add(registry("Potion", BuiltInRegistries.POTION));
        entries.add(registry("SoundEvent", BuiltInRegistries.SOUND_EVENT));
        entries.add(registry("ParticleType", BuiltInRegistries.PARTICLE_TYPE));
        entries.add(registry("BlockEntityType", BuiltInRegistries.BLOCK_ENTITY_TYPE));
        entries.add(registry("CreativeModeTab", BuiltInRegistries.CREATIVE_MODE_TAB));
        return entries;
    }

    private static <T> RegistryTypeCatalogEntry registry(String typeName, net.minecraft.core.Registry<T> registry) {
        List<String> ids = new ArrayList<>();
        registry.keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(ids::add);
        return new RegistryTypeCatalogEntry(typeName, ids, tagIds(registry));
    }

    /**
     * 该注册表当前已绑定的标签 id（不含 {@code #} 前缀）。probe 在服务器运行时执行，数据包标签
     * 此时已绑定；排序保证产物确定性。标签未加载时为空流，probe 侧会回退成 {@code string}。
     */
    private static <T> List<String> tagIds(net.minecraft.core.Registry<T> registry) {
        return registry.getTagNames()
                .map(tag -> tag.location().toString())
                .sorted()
                .toList();
    }

    /**
     * 已加载 mod id：{@code "@create"} 这类命名空间过滤写法的补全来源之一。
     *
     * <p>取加载器的 mod 列表，覆盖「装了但没往某个注册表注册东西」的 mod；probe 再把它与注册表
     * 条目 id 的命名空间取并集（那部分覆盖脚本 {@code event.create('mymod:x')}、数据包等不属于
     * 任何 mod 的命名空间）。排序与去重由 {@code NekoScriptCatalog.modIds()} 统一负责。
     */
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
