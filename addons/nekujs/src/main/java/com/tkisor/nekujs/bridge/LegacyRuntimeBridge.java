package com.tkisor.nekujs.bridge;

import com.mojang.logging.LogUtils;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekujs.plugin.LegacyPluginDiscovery;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plugin-runtime-owned definitions; no loader, evaluator or guest context is acquired here. */
public final class LegacyRuntimeBridge {
    private final List<LegacyPluginDiscovery.LoadedPlugin<KubeJSPlugin>> plugins;
    private final List<EventGroup> events;
    private final dev.latvian.mods.kubejs.registry.BuilderTypeCatalog builders = new dev.latvian.mods.kubejs.registry.BuilderTypeCatalog();
    private final Map<net.minecraft.core.component.DataComponentType<?>, dev.latvian.mods.rhino.type.TypeInfo> componentDescriptions = new LinkedHashMap<>();
    private dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage components;
    private Map<String, com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition> recipeSchemas;

    public LegacyRuntimeBridge() {
        this(discoverPlugins());
    }

    private static List<LegacyPluginDiscovery.LoadedPlugin<KubeJSPlugin>> discoverPlugins() {
        var modList = ModList.get();
        var sources = new ArrayList<LegacyPluginDiscovery.Source>();
        for (var info : modList.getModFiles()) {
            var descriptor = info.getFile().findResource("kubejs.plugins.txt");
            if (Files.isRegularFile(descriptor)) {
                String owner = String.join(",", info.getMods().stream().map(mod -> mod.getModId()).toList());
                sources.add(new LegacyPluginDiscovery.Source(owner, descriptor.getParent()));
            }
        }
        return LegacyPluginDiscovery.load(sources, KubeJSPlugin.class.getClassLoader(), modList::isLoaded,
                FMLEnvironment.dist.isClient(), KubeJSPlugin.class);
    }

    LegacyRuntimeBridge(List<LegacyPluginDiscovery.LoadedPlugin<KubeJSPlugin>> plugins) {
        this.plugins = List.copyOf(plugins);
        var definitions = new ArrayList<EventGroup>();
        for (var plugin : plugins) invoke(plugin, "registerEvents", () -> plugin.instance().registerEvents(definitions::add));
        events = List.copyOf(definitions);
        for (var plugin : plugins) invoke(plugin, "registerBuilderTypes", () -> plugin.instance().registerBuilderTypes(builders));
        LogUtils.getLogger().info("Loaded {} owned legacy plugins; native event definitions: {}", plugins.size(), events.size());
    }

    public void registerEvents(EventGroupRegistry registry, boolean client) {
        events.forEach(group -> group.register(registry, client));
    }

    public void registerBindings(com.tkisor.nekojs.api.data.BindingRegistry registry) {
        ScriptType type = ScriptType.valueOf(registry.scriptType().name());
        var context = new KubeJSContext(type, registry::register);
        var bindings = new dev.latvian.mods.kubejs.script.BindingRegistry(context, null);
        for (var plugin : plugins) invoke(plugin, "registerBindings", () -> plugin.instance().registerBindings(bindings));
    }

    public void registerAdapters(JSTypeAdapterRegistry registry) {
        LegacyTypeWrappers.register(registry, FMLEnvironment.dist.isClient(), wrappers -> {
            for (var plugin : plugins) {
                invoke(plugin, "registerTypeWrappers", () -> plugin.instance().registerTypeWrappers(wrappers));
            }
        });
    }

    /** Compiles owned source graphs before publishing profiles into NekoJS's canonical schema layer. */
    public void registerRecipeSchemas(com.tkisor.nekojs.core.plugin.RecipeSchemaRegister sink) {
        if (recipeSchemas == null) {
            var registries = new dev.latvian.mods.kubejs.util.RegistryAccessContainer(
                    net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
            var catalog = new dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage(registries);
            for (var plugin : plugins) invoke(plugin, "registerRecipeComponents", () -> plugin.instance().registerRecipeComponents(catalog));
            catalog.freezeComponents();
            var factories = new dev.latvian.mods.kubejs.recipe.schema.RecipeFactoryRegistry(catalog);
            for (var plugin : plugins) invoke(plugin, "registerRecipeFactories", () -> plugin.instance().registerRecipeFactories(factories));
            var registry = new dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry(catalog);
            for (var plugin : plugins) invoke(plugin, "registerRecipeSchemas", () -> plugin.instance().registerRecipeSchemas(registry));
            List<com.tkisor.nekujs.recipe.schema.SchemaDocument> documents;
            try {
                documents = com.tkisor.nekujs.recipe.LegacyRecipeSchemaSources.read(
                        plugins.stream().map(LegacyPluginDiscovery.LoadedPlugin::source).distinct().toList());
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("[NEKO-5279] Legacy recipe schema resources could not be read", failure);
            }
            var compilation = new com.tkisor.nekujs.recipe.schema.RecipeSchemaGraphCompiler().compile(documents,
                    new com.tkisor.nekujs.recipe.value.LegacyRecipeComponents(catalog, registries));
            for (var rejection : compilation.rejections()) {
                if (!rejection.code().equals("NEKO-5251")) LogUtils.getLogger().warn("{}", rejection.reason());
            }
            components = catalog;
            var definitions = new LinkedHashMap<>(compilation.definitions());
            definitions.putAll(com.tkisor.nekujs.recipe.schema.ProgrammaticRecipeSchemas.compile(catalog));
            recipeSchemas = java.util.Collections.unmodifiableMap(definitions);
            long callable = recipeSchemas.values().stream().filter(schema -> schema.unsupportedReason() == null).count();
            LogUtils.getLogger().info("Compiled {} callable legacy recipe profiles from {} owned schema documents using {} component codecs; {} terminal profiles are not callable",
                    callable, documents.size(), components.componentTypes().size(), recipeSchemas.size() - callable);
        }
        recipeSchemas.values().forEach(schema -> sink.register(schema.namespace(), schema.name(), schema));
    }

    public void init() { for (var plugin : plugins) invoke(plugin, "init", plugin.instance()::init); }
    public void initStartup() { for (var plugin : plugins) invoke(plugin, "initStartup", plugin.instance()::initStartup); }
    public void afterInit() {
        // NekoJS dispatches this hook from load-complete work, after NeoForge has bound registry values.
        for (var plugin : plugins) {
            invoke(plugin, "registerDataComponentTypeDescriptions", () -> plugin.instance().registerDataComponentTypeDescriptions((type, description) -> {
                if (componentDescriptions.putIfAbsent(type, description) != null) {
                    throw new IllegalArgumentException("[NEKO-4265] Duplicate legacy data component description: " + type);
                }
            }));
        }
        LogUtils.getLogger().info("Collected {} legacy data component descriptions at load completion", componentDescriptions.size());
        for (var plugin : plugins) invoke(plugin, "afterInit", plugin.instance()::afterInit);
    }

    private static void invoke(LegacyPluginDiscovery.LoadedPlugin<KubeJSPlugin> plugin, String hook, Runnable action) {
        try { action.run(); }
        catch (RuntimeException | LinkageError failure) {
            throw new IllegalStateException("[NEKO-4200] Legacy plugin hook failed: owner=" + plugin.source().owner()
                    + ", class=" + plugin.className() + ", hook=" + hook, failure);
        }
    }
}
