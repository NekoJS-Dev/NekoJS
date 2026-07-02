package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.MemberVisibilityQuery;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.utils.event.dispatch.DispatchEventBus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NekoScriptCatalog {
    private static NekoCatalogPlatformProvider platformProvider = NekoCatalogPlatformProvider.EMPTY;

    private NekoScriptCatalog() {}

    public static void setPlatformProvider(NekoCatalogPlatformProvider provider) {
        platformProvider = provider == null ? NekoCatalogPlatformProvider.EMPTY : provider;
    }

    public static NekoScriptCatalogSnapshot snapshot(IPluginRuntime runtime) {
        return new NekoScriptCatalogSnapshot(
                ScriptType.all(),
                bindings(runtime),
                events(runtime),
                adapters(runtime),
                recipeNamespaces(),
                hostExtensions(),
                List.copyOf(platformProvider.snippets()),
                NekoTypeDocs.typeDocs(runtime),
                NekoTypeDocs.manualDeclarations(runtime),
                List.copyOf(platformProvider.registryTypes()),
                platformProvider.outputLayout(),
                JavaModuleImportPolicy.nekoDefault()
        );
    }

    public static NekoScriptCatalogSnapshot snapshot(IPluginRuntime runtime, ScriptType scriptType) {
        return new NekoScriptCatalogSnapshot(
                List.of(scriptType),
                bindings(runtime, scriptType),
                events(runtime, scriptType),
                adapters(runtime),
                recipeNamespaces(),
                hostExtensions(scriptType),
                snippets(scriptType),
                NekoTypeDocs.typeDocs(runtime, scriptType),
                NekoTypeDocs.manualDeclarations(runtime, scriptType),
                List.copyOf(platformProvider.registryTypes()),
                platformProvider.outputLayout(),
                JavaModuleImportPolicy.nekoDefault()
        );
    }

    /** Merged recipe namespace entries: handler methods + schema types. */
    public static List<RecipeNamespaceCatalogEntry> recipeNamespaces() {
        Map<String, RecipeNamespaceCatalogEntry> map = new LinkedHashMap<>();
        for (var entry : platformProvider.recipeNamespaces()) {
            map.put(entry.namespace(), entry);
        }
        RecipeTypeDefinitionRegistry schemas = RecipeTypeDefinitionStorage.current();
        for (String ns : schemas.namespaces()) {
            List<RecipeSchemaTypeEntry> schemaTypes = new ArrayList<>();
            for (String type : schemas.types(ns)) {
                var def = schemas.get(ns, type);
                if (def != null) schemaTypes.add(RecipeSchemaTypeEntry.from(def));
            }
            map.compute(ns, (k, existing) -> {
                if (existing != null) return existing.withSchemaTypes(schemaTypes);
                return new RecipeNamespaceCatalogEntry(ns, null, new ArrayList<>(schemas.types(ns)),
                        true, List.of(), List.of(), schemaTypes);
            });
        }
        return List.copyOf(map.values());
    }

    public static TypeOutputLayout outputLayout() {
        return platformProvider.outputLayout();
    }

    // ... (bindings/events/adapters methods unchanged, omitted for brevity)
    // The rest of the file is the same as before

    public static List<BindingCatalogEntry> bindings(IPluginRuntime runtime) {
        List<BindingCatalogEntry> entries = new ArrayList<>();
        for (ScriptType type : ScriptType.all()) {
            entries.addAll(bindings(runtime, type));
        }
        return List.copyOf(entries);
    }

    public static List<BindingCatalogEntry> bindings(IPluginRuntime runtime, ScriptType scriptType) {
        List<BindingCatalogEntry> entries = new ArrayList<>();
        List<TypeDocCatalogEntry> docs = NekoTypeDocs.typeDocs(runtime, scriptType).stream()
                .filter(doc -> doc.kind().equals("binding"))
                .toList();
        for (var binding : runtime.bindings(scriptType).values()) {
            BindingCatalogEntry entry = BindingCatalogEntry.of(binding.name(), scriptType, binding.valueType(), binding.value() instanceof Class<?>);
            for (TypeDocCatalogEntry doc : docs) {
                if (doc.target().equals(binding.name())) {
                    entry = entry.withDoc(doc);
                }
            }
            entries.add(entry);
        }
        return List.copyOf(entries);
    }

    public static List<EventCatalogEntry> events(IPluginRuntime runtime) {
        List<EventCatalogEntry> entries = new ArrayList<>();
        for (ScriptType type : ScriptType.all()) {
            entries.addAll(events(runtime, type));
        }
        return List.copyOf(entries);
    }

    public static List<EventCatalogEntry> events(IPluginRuntime runtime, ScriptType scriptType) {
        List<EventCatalogEntry> entries = new ArrayList<>();
        for (EventGroup group : runtime.eventGroups().values()) {
            for (var entry : group.viewBuses().entrySet()) {
                EventGroup.BusHolder holder = entry.getValue();
                if (!holder.canApplyOn(scriptType)) continue;
                var bus = holder.getBus(scriptType);
                if (bus == null) continue;
                Class<?> dispatchKeyType = bus.bus() instanceof DispatchEventBus<?, ?> dispatchBus
                        ? dispatchBus.dispatchKey().keyType()
                        : null;
                entries.add(EventCatalogEntry.of(
                        group.name(), entry.getKey(), scriptType,
                        bus.bus().eventType(), dispatchKeyType, bus.canCancel(), bus.canDispatch()));
            }
        }
        return List.copyOf(entries);
    }

    public static List<AdapterCatalogEntry> adapters(IPluginRuntime runtime) {
        List<AdapterCatalogEntry> entries = new ArrayList<>();
        for (var adapter : runtime.adapters()) {
            entries.add(adapterEntry(adapter));
        }
        return List.copyOf(entries);
    }

    private static AdapterCatalogEntry adapterEntry(JSTypeAdapter<?> adapter) {
        Class<?> targetType = adapter.getTargetClass();
        return new AdapterCatalogEntry(targetType, adapter.inputShapes(), adapter.getPrecedence());
    }

    public static List<HostExtensionCatalogEntry> hostExtensions() {
        List<HostExtensionCatalogEntry> entries = new ArrayList<>();
        for (ScriptType type : ScriptType.all()) entries.addAll(hostExtensions(type));
        return List.copyOf(entries);
    }

    public static List<HostExtensionCatalogEntry> hostExtensions(ScriptType scriptType) {
        List<HostExtensionCatalogEntry> entries = new ArrayList<>();
        for (HostExtensionSource source : platformProvider.hostExtensions()) {
            if (!source.canApplyOn(scriptType)) continue;
            for (var binding : MemberVisibilityQuery.getVisibleMethods(source.extensionInterface()).values()) {
                entries.add(new HostExtensionCatalogEntry(source.targetClass(),
                        source.extensionInterface(), binding.member().getName(),
                        binding.jsName(), binding.member(), source.scriptType(), false));
            }
        }
        return List.copyOf(entries);
    }

    public static List<SnippetCatalogEntry> snippets() { return List.copyOf(platformProvider.snippets()); }

    public static List<SnippetCatalogEntry> snippets(ScriptType scriptType) {
        List<SnippetCatalogEntry> entries = new ArrayList<>();
        for (SnippetCatalogEntry snippet : platformProvider.snippets())
            if (snippet.canApplyOn(scriptType)) entries.add(snippet);
        return List.copyOf(entries);
    }
}
