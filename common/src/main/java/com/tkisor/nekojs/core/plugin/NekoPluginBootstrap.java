package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSBasePlugin;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocsRegister;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.plugin.NekoPluginExtensionContext;
import com.tkisor.nekojs.api.plugin.NekoPluginExtensionPoint;
import com.tkisor.nekojs.api.plugin.NekoPluginExtensionProvider;
import com.tkisor.nekojs.api.plugin.NekoPluginExtensionRegistry;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleRegister;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import com.tkisor.nekojs.api.recipe.RecipeSchemaRegister;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.ScriptTypePredicate;
import com.tkisor.nekojs.script.ScriptTypedValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class NekoPluginBootstrap {

    private NekoPluginBootstrap() {}

    private static List<NekoPluginExtensionPoint<?>> builtInExtensionPoints(com.tkisor.nekojs.script.prop.ScriptPropertyRegistry scriptProperties) {
        return List.of(
            base("nekojs:script_compilers", (plugin, context) -> plugin.registerScriptCompilers(context.scriptCompilers())),
            base("nekojs:script_properties", (plugin, context) -> plugin.registerScriptProperty(scriptProperties)),
            base("nekojs:bindings", (plugin, context) -> {
                    var predicate = context.client()
                        ? ScriptTypePredicate.exact(ScriptType.CLIENT).negate()
                        : ScriptTypePredicate.any();
                    predicate.streamMatched().map(context.bindings()::at).forEach(plugin::registerBinding);
                }
            ),
            base("nekojs:adapters", (plugin, context) -> plugin.registerAdapters(context.adapters())),
            base("nekojs:type_docs", (plugin, context) -> plugin.registerTypeDocs(context.typeDocs())),
            platform("nekojs:events", (plugin, context) -> plugin.registerEvents(context.events())),
            clientPlatform("nekojs:client_events", (plugin, context) -> plugin.registerClientEvents(context.events())),
            platform("nekojs:recipe_namespaces", (plugin, context) -> plugin.registerRecipeNamespaces(context.recipeNamespaces())),
            platform("nekojs:recipe_schemas", (plugin, context) -> plugin.registerRecipeSchemas(context.recipeSchemas())),
            platform("nekojs:recipe_lifecycle", (plugin, context) -> plugin.registerRecipeLifecycleHooks(context.recipeLifecycle()))
        );
    }

    public static NekoPluginRuntime bootstrap(List<NekoJSBasePlugin> plugins, com.tkisor.nekojs.script.prop.ScriptPropertyRegistry scriptProperties) {
        BootstrapState state = new BootstrapState(Platform.isClient());
        ExtensionRegistry registry = new ExtensionRegistry();
        builtInExtensionPoints(scriptProperties).forEach(registry::register);
        for (NekoJSBasePlugin plugin : plugins) {
            if (plugin instanceof NekoPluginExtensionProvider provider) {
                provider.registerPluginExtensionPoints(registry);
            }
        }
        List<NekoPluginExtensionPoint<?>> extensionPoints = registry.freeze();
        for (NekoJSBasePlugin plugin : plugins) {
            for (NekoPluginExtensionPoint<?> extensionPoint : extensionPoints) {
                extensionPoint.collect(plugin, state);
            }
        }

        // 初始化内置 probe generator
        com.tkisor.nekojs.api.probe.ProbeRegistry.setGenerator(new com.tkisor.nekojs.probe.BuiltinProbeGenerator(), "NekoJS (built-in)");

        // 允许插件替换 probe generator
        for (NekoJSBasePlugin plugin : plugins) {
            plugin.registerProbeGenerator();
        }

        // 锁定注册表，检测冲突
        com.tkisor.nekojs.api.probe.ProbeRegistry.lock();

        state.freeze();
        return state.createRuntime();
    }

    private static NekoPluginExtensionPoint<NekoJSBasePlugin> base(String id, BiConsumer<NekoJSBasePlugin, NekoPluginExtensionContext> collector) {
        return NekoPluginExtensionPoint.of(id, NekoJSBasePlugin.class, collector);
    }

    private static NekoPluginExtensionPoint<NekoJSPlugin> platform(String id, BiConsumer<NekoJSPlugin, NekoPluginExtensionContext> collector) {
        return NekoPluginExtensionPoint.of(id, NekoJSPlugin.class, collector);
    }

    private static NekoPluginExtensionPoint<NekoJSPlugin> clientPlatform(String id, BiConsumer<NekoJSPlugin, NekoPluginExtensionContext> collector) {
        return NekoPluginExtensionPoint.clientOnly(id, NekoJSPlugin.class, collector);
    }

    private static final class ExtensionRegistry implements NekoPluginExtensionRegistry {
        private final Map<String, NekoPluginExtensionPoint<?>> extensionPoints = new LinkedHashMap<>();
        private boolean frozen;

        @Override
        public <P extends NekoJSBasePlugin> void register(NekoPluginExtensionPoint<P> extensionPoint) {
            if (frozen) {
                throw new IllegalStateException("Plugin extension registry is frozen after bootstrap collection");
            }
            if (extensionPoints.containsKey(extensionPoint.id())) {
                throw new IllegalArgumentException("Plugin extension point '" + extensionPoint.id() + "' is already registered");
            }
            extensionPoints.put(extensionPoint.id(), extensionPoint);
        }

        List<NekoPluginExtensionPoint<?>> freeze() {
            frozen = true;
            return List.copyOf(extensionPoints.values());
        }
    }

    private static final class BootstrapState implements NekoPluginExtensionContext, TypeDocsRegister {
        private final ScriptCompilerRegistry scriptCompilers = ScriptCompilerRegistry.createRuntimeRegistry();
        private final ScriptTypedValue<BindingRegistry> bindingRegistries = ScriptTypedValue.of(BindingRegistry.BindingRegistryImpl::new);
        private final boolean client;
        private final JSTypeAdapterRegistry adapters = new JSTypeAdapterRegistry.Impl();
        private final EventGroupRegistry eventGroups = new EventGroupRegistry.Impl();
        private final List<TypeDocCatalogEntry> typeDocs = new ArrayList<>();
        private final List<ManualDeclarationCatalogEntry> manualDeclarations = new ArrayList<>();
        private final Map<String, RecipeNamespaceEntry> recipeNamespaces = new LinkedHashMap<>();
        private final Map<String, Map<String, RecipeTypeDefinition>> recipeSchemaOverrides = new LinkedHashMap<>();
        private final List<Consumer<RecipeLifecycleContext>> beforeRecipeLoadingHooks = new ArrayList<>();
        private final List<Consumer<RecipeLifecycleContext>> afterRecipesHooks = new ArrayList<>();
        private boolean frozen;

        BootstrapState(boolean client) {
            this.client = client;
        }

        @Override
        public boolean client() {
            return client;
        }

        @Override
        public ScriptCompilerRegistry scriptCompilers() {
            return scriptCompilers;
        }

        @Override
        public ScriptTypedValue<BindingRegistry> bindings() {
            return bindingRegistries;
        }

        @Override
        public JSTypeAdapterRegistry adapters() {
            return adapters;
        }

        @Override
        public EventGroupRegistry events() {
            return eventGroups;
        }

        @Override
        public TypeDocsRegister typeDocs() {
            return this;
        }

        @Override
        public RecipeNamespaceRegister recipeNamespaces() {
            return this::registerRecipeNamespace;
        }

        @Override
        public RecipeSchemaRegister recipeSchemas() {
            return (namespace, type, schema) -> {
                requireMutable("recipe schema overrides");
                recipeSchemaOverrides.computeIfAbsent(namespace, ignored -> new LinkedHashMap<>()).put(type, schema);
            };
        }

        @Override
        public RecipeLifecycleRegister recipeLifecycle() {
            return new RecipeLifecycleRegister() {
                @Override
                public void beforeRecipeLoading(Consumer<RecipeLifecycleContext> hook) {
                    requireMutable("recipe lifecycle hooks");
                    beforeRecipeLoadingHooks.add(Objects.requireNonNull(hook, "hook"));
                }

                @Override
                public void afterRecipes(Consumer<RecipeLifecycleContext> hook) {
                    requireMutable("recipe lifecycle hooks");
                    afterRecipesHooks.add(Objects.requireNonNull(hook, "hook"));
                }
            };
        }

        void freeze() {
            frozen = true;
            scriptCompilers.freeze();
        }

        NekoPluginRuntime createRuntime() {
            return new NekoPluginRuntime(
                    scriptCompilers,
                    bindingsByScriptType(),
                    List.copyOf(adapters().view()),
                    Map.copyOf(eventGroups.view()),
                    typeDocsSnapshot(),
                    manualDeclarationsSnapshot(),
                    recipeNamespacesSnapshot(),
                    recipeSchemaOverridesSnapshot(),
                    beforeRecipeLoadingHooksSnapshot(),
                    afterRecipesHooksSnapshot()
            );
        }

        Map<String, Map<String, RecipeTypeDefinition>> recipeSchemaOverridesSnapshot() {
            Map<String, Map<String, RecipeTypeDefinition>> copy = new LinkedHashMap<>();
            for (var entry : recipeSchemaOverrides.entrySet()) {
                copy.put(entry.getKey(), Map.copyOf(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }

        @Override
        public void register(TypeDocCatalogEntry entry) {
            requireMutable("type docs");
            typeDocs.add(Objects.requireNonNull(entry, "entry"));
        }

        @Override
        public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) {
            requireMutable("manual declarations");
            manualDeclarations.add(Objects.requireNonNull(entry, "entry"));
        }

        void registerRecipeNamespace(RecipeNamespaceEntry entry) {
            requireMutable("recipe namespaces");
            Objects.requireNonNull(entry, "entry");
            if (recipeNamespaces.containsKey(entry.namespace())) {
                throw new IllegalArgumentException("Recipe namespace '" + entry.namespace() + "' is already registered. Possible plugin conflict.");
            }
            recipeNamespaces.put(entry.namespace(), entry);
        }

        Map<ScriptType, Map<String, Binding>> bindingsByScriptType() {
            return this.bindingRegistries.stream()
                .collect(Collectors.toMap(BindingRegistry::scriptType, BindingRegistry::viewRegistered));
        }

        List<TypeDocCatalogEntry> typeDocsSnapshot() {
            return typeDocs.stream()
                    .sorted(Comparator.comparingInt(TypeDocCatalogEntry::priority))
                    .toList();
        }

        List<ManualDeclarationCatalogEntry> manualDeclarationsSnapshot() {
            return manualDeclarations.stream()
                    .sorted(Comparator.comparingInt(ManualDeclarationCatalogEntry::priority))
                    .toList();
        }

        Map<String, RecipeNamespaceEntry> recipeNamespacesSnapshot() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(recipeNamespaces));
        }

        List<Consumer<RecipeLifecycleContext>> beforeRecipeLoadingHooksSnapshot() {
            return List.copyOf(beforeRecipeLoadingHooks);
        }

        List<Consumer<RecipeLifecycleContext>> afterRecipesHooksSnapshot() {
            return List.copyOf(afterRecipesHooks);
        }

        private void requireMutable(String registryName) {
            if (frozen) {
                throw new IllegalStateException("Plugin " + registryName + " registry is frozen after bootstrap");
            }
        }
    }
}
