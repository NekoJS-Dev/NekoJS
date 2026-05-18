package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.NekoJSBasePlugin;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocsRegister;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingsRegister;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegister;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.plugin.NekoPluginExtensionContext;
import com.tkisor.nekojs.api.plugin.NekoPluginExtensionPoint;
import com.tkisor.nekojs.api.plugin.NekoPluginExtensionProvider;
import com.tkisor.nekojs.api.plugin.NekoPluginExtensionRegistry;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleRegister;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class NekoPluginBootstrap {
    private static final List<NekoPluginExtensionPoint<?>> BUILT_IN_EXTENSION_POINTS = List.of(
            base("nekojs:script_compilers", (plugin, context) -> plugin.registerScriptCompilers(context.scriptCompilers())),
            base("nekojs:script_properties", (plugin, context) -> plugin.registerScriptProperty(context.scriptProperties())),
            base("nekojs:bindings", (plugin, context) -> plugin.registerBindings(context.bindings())),
            clientBase("nekojs:client_bindings", (plugin, context) -> plugin.registerClientBindings(context.bindings())),
            base("nekojs:adapters", (plugin, context) -> plugin.registerAdapters(context.adapters())),
            base("nekojs:type_docs", (plugin, context) -> plugin.registerTypeDocs(context.typeDocs())),
            platform("nekojs:events", (plugin, context) -> plugin.registerEvents(context.events())),
            clientPlatform("nekojs:client_events", (plugin, context) -> plugin.registerClientEvents(context.events())),
            platform("nekojs:recipe_namespaces", (plugin, context) -> plugin.registerRecipeNamespaces(context.recipeNamespaces())),
            platform("nekojs:recipe_lifecycle", (plugin, context) -> plugin.registerRecipeLifecycleHooks(context.recipeLifecycle()))
    );

    private NekoPluginBootstrap() {}

    public static NekoPluginRuntime bootstrap(List<NekoJSBasePlugin> plugins) {
        BootstrapState state = new BootstrapState(Platform.isClient());
        ExtensionRegistry registry = new ExtensionRegistry();
        BUILT_IN_EXTENSION_POINTS.forEach(registry::register);
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
        state.freeze();
        return state.createRuntime();
    }

    private static NekoPluginExtensionPoint<NekoJSBasePlugin> base(String id, BiConsumer<NekoJSBasePlugin, NekoPluginExtensionContext> collector) {
        return NekoPluginExtensionPoint.of(id, NekoJSBasePlugin.class, collector);
    }

    private static NekoPluginExtensionPoint<NekoJSBasePlugin> clientBase(String id, BiConsumer<NekoJSBasePlugin, NekoPluginExtensionContext> collector) {
        return NekoPluginExtensionPoint.clientOnly(id, NekoJSBasePlugin.class, collector);
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
        private final ScriptPropertyRegistry.Impl scriptProperties = new ScriptPropertyRegistry.Impl();
        private final boolean client;
        private final List<Binding> bindings = new ArrayList<>();
        private final List<JSTypeAdapter<?>> adapters = new ArrayList<>();
        private final Map<String, EventGroup> eventGroups = new LinkedHashMap<>();
        private final List<TypeDocCatalogEntry> typeDocs = new ArrayList<>();
        private final List<ManualDeclarationCatalogEntry> manualDeclarations = new ArrayList<>();
        private final Map<String, RecipeNamespaceEntry<?>> recipeNamespaces = new LinkedHashMap<>();
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
        public ScriptPropertyRegistry scriptProperties() {
            return scriptProperties;
        }

        @Override
        public BindingsRegister bindings() {
            return this::registerBinding;
        }

        @Override
        public JSTypeAdapterRegister adapters() {
            return this::registerAdapter;
        }

        @Override
        public EventGroupRegistry events() {
            return this::registerEvent;
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
            scriptProperties.freeze();
        }

        NekoPluginRuntime createRuntime() {
            return NekoPluginRuntime.create(
                    scriptCompilers,
                    scriptProperties,
                    bindingsByScriptType(),
                    adaptersSnapshot(),
                    eventGroupsSnapshot(),
                    typeDocsSnapshot(),
                    manualDeclarationsSnapshot(),
                    recipeNamespacesSnapshot(),
                    beforeRecipeLoadingHooksSnapshot(),
                    afterRecipesHooksSnapshot()
            );
        }

        void registerBinding(Binding binding) {
            requireMutable("bindings");
            Objects.requireNonNull(binding, "binding");
            String name = binding.getName();
            ScriptType type = binding.scriptType();
            for (Binding existing : bindings) {
                if (!existing.getName().equals(name)) {
                    continue;
                }
                ScriptType existingType = existing.scriptType();
                if (binding.canApplyOn(existingType) || existing.canApplyOn(type)) {
                    String newClassPath = binding.getType().getName();
                    String existingClassPath = existing.getType().getName();
                    throw new IllegalArgumentException(
                            "Duplicate binding name: '" + name + "'\n" +
                                    " -> New: [" + type.name() + "] (" + newClassPath + ")\n" +
                                    " -> Existing: [" + existingType.name() + "] (" + existingClassPath + ")\n" +
                                    "Possible plugin conflict or duplicate registration."
                    );
                }
            }
            bindings.add(binding);
        }

        <T> void registerAdapter(JSTypeAdapter<T> adapter) {
            requireMutable("adapters");
            adapters.add(Objects.requireNonNull(adapter, "adapter"));
        }

        void registerEvent(EventGroup group) {
            requireMutable("events");
            Objects.requireNonNull(group, "group");
            EventGroup existing = eventGroups.get(group.name());
            if (existing != null) {
                existing.merge(group);
            } else {
                eventGroups.put(group.name(), group);
            }
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

        <C> void registerRecipeNamespace(RecipeNamespaceEntry<C> entry) {
            requireMutable("recipe namespaces");
            Objects.requireNonNull(entry, "entry");
            if (recipeNamespaces.containsKey(entry.namespace())) {
                throw new IllegalArgumentException("Recipe namespace '" + entry.namespace() + "' is already registered. Possible plugin conflict.");
            }
            recipeNamespaces.put(entry.namespace(), entry);
        }

        Map<ScriptType, Map<String, Binding>> bindingsByScriptType() {
            EnumMap<ScriptType, Map<String, Binding>> result = new EnumMap<>(ScriptType.class);
            for (ScriptType type : ScriptType.values()) {
                result.put(type, new LinkedHashMap<>());
            }
            for (Binding binding : bindings) {
                for (ScriptType envType : ScriptType.all()) {
                    if (binding.canApplyOn(envType)) {
                        result.get(envType).putIfAbsent(binding.getName(), binding);
                    }
                }
            }
            EnumMap<ScriptType, Map<String, Binding>> frozen = new EnumMap<>(ScriptType.class);
            result.forEach((type, values) -> frozen.put(type, Collections.unmodifiableMap(new LinkedHashMap<>(values))));
            return Collections.unmodifiableMap(frozen);
        }

        List<JSTypeAdapter<?>> adaptersSnapshot() {
            return List.copyOf(adapters);
        }

        Map<String, EventGroup> eventGroupsSnapshot() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(eventGroups));
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

        Map<String, RecipeNamespaceEntry<?>> recipeNamespacesSnapshot() {
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
