package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.MemberVisibilityQuery;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchEventBus;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.ScriptEventDefinition;
import com.tkisor.nekojs.api.event.ScriptEventRegistry;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.EnvironmentKeyFactory;

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
        List<BindingCatalogEntry> allBindings = bindings(runtime);
        List<EventCatalogEntry> allEvents = events(runtime);
        List<AdapterCatalogEntry> allAdapters = adapters(runtime);
        List<HostExtensionCatalogEntry> allHostExtensions = hostExtensions();

        NekoScriptCatalogSnapshot temp = new NekoScriptCatalogSnapshot(
                ScriptType.all(),
                allBindings,
                allEvents,
                allAdapters,
                recipeNamespaces(),
                allHostExtensions,
                List.copyOf(platformProvider.snippets()),
                runtime.typeDocs(),
                runtime.manualDeclarations(),
                List.copyOf(platformProvider.registryTypes()),
                platformProvider.outputLayout(),
                Map.of(),
                List.of()
        );

        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = buildManagedApis(runtime, ScriptType.all());
        List<ApiSymbol> legacySurface = LegacySurfaceAdapter.convert(temp);

        return new NekoScriptCatalogSnapshot(
                ScriptType.all(),
                allBindings,
                allEvents,
                allAdapters,
                recipeNamespaces(),
                allHostExtensions,
                List.copyOf(platformProvider.snippets()),
                runtime.typeDocs(),
                runtime.manualDeclarations(),
                List.copyOf(platformProvider.registryTypes()),
                platformProvider.outputLayout(),
                managedApis,
                legacySurface
        );
    }

    public static NekoScriptCatalogSnapshot snapshot(IPluginRuntime runtime, ScriptType scriptType) {
        List<BindingCatalogEntry> typeBindings = bindings(runtime, scriptType);
        List<EventCatalogEntry> typeEvents = events(runtime, scriptType);
        List<HostExtensionCatalogEntry> typeHostExtensions = hostExtensions(scriptType);

        NekoScriptCatalogSnapshot temp = new NekoScriptCatalogSnapshot(
                List.of(scriptType),
                typeBindings,
                typeEvents,
                adapters(runtime),
                recipeNamespaces(),
                typeHostExtensions,
                snippets(scriptType),
                runtime.typeDocs().stream().filter(entry -> entry.scriptType().test(scriptType)).toList(),
                runtime.manualDeclarations().stream().filter(entry -> entry.scriptType().test(scriptType)).toList(),
                List.copyOf(platformProvider.registryTypes()),
                platformProvider.outputLayout(),
                Map.of(),
                List.of()
        );

        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = buildManagedApis(runtime, List.of(scriptType));
        List<ApiSymbol> legacySurface = LegacySurfaceAdapter.convert(temp);

        return new NekoScriptCatalogSnapshot(
                List.of(scriptType),
                typeBindings,
                typeEvents,
                adapters(runtime),
                recipeNamespaces(),
                typeHostExtensions,
                snippets(scriptType),
                runtime.typeDocs().stream().filter(entry -> entry.scriptType().test(scriptType)).toList(),
                runtime.manualDeclarations().stream().filter(entry -> entry.scriptType().test(scriptType)).toList(),
                List.copyOf(platformProvider.registryTypes()),
                platformProvider.outputLayout(),
                managedApis,
                legacySurface
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
        List<TypeDocCatalogEntry> docs = runtime.typeDocs().stream()
                .filter(doc -> doc.scriptType().test(scriptType))
                .filter(doc -> doc.kind().equals("binding"))
                .toList();
        for (var binding : runtime.bindings(scriptType).values()) {
            BindingCatalogEntry entry = BindingCatalogEntry.of(binding.name(), scriptType, binding.valueType(), binding.value() instanceof Class<?>);
            // DelegatingBinding 代理：valueType 是 helper，但运行时还委托 targetClass 的静态成员，
            // 让 probe 把 targetClass 的静态成员也合并进 .d.ts。
            if (binding.value() instanceof com.tkisor.nekojs.js.DelegatingBinding del) {
                entry = entry.withExtraDocTypes(List.of(del.targetClass()));
            }
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
        for (EventGroup group : runtime.eventGroups().values()) {
            for (var entry : group.viewBuses().entrySet()) {
                EventGroup.BusHolder holder = entry.getValue();
                // 每个 bus 只产出一条目录条目，并用它的规范 ScriptType 打标签。不能按
                // ScriptType 逐类型各收集一遍：同一条 SERVER bus 会被打上 STARTUP/SERVER/TEST
                // 多个标签重复入目录，而 side 过滤（scriptType().test(side)）会把这些同总线
                // 条目全部放进同一个 side 文件（如 startup 同时命中 STARTUP 与 SERVER 两个标签），
                // 导致 Python .pyi / TS .d.ts 里同一事件重复声明（Pylance 报「方法声明被同名
                // 声明遮盖」）。
                var bus = firstApplicableBus(holder);
                if (bus == null) continue;
                Class<?> dispatchKeyType = bus.bus() instanceof DispatchEventBus<?, ?> dispatchBus
                        ? dispatchBus.dispatchKey().keyType()
                        : null;
                entries.add(EventCatalogEntry.of(
                        group.name(), entry.getKey(), holder.scriptType(),
                        bus.bus().eventType(), dispatchKeyType, bus.canCancel(), bus.canDispatch()));
            }
        }
        // ScriptEvents 动态注册的事件组并列进目录：probe 才能为它们生成声明，payload
        // 事件类也随 catalog 种子进入反射 BFS。validateAvailable 已保证不与内置组/绑定
        // 重名；注册顺序 = 脚本加载顺序（满足确定性）；脚本事件为 PLAIN 总线，无 dispatch 键。
        for (ScriptEventDefinition definition : ScriptEventRegistry.definitions()) {
            EventBusJS<?, ?> scriptBus = definition.bus();
            entries.add(EventCatalogEntry.of(
                    definition.groupName(), definition.eventName(), definition.targetType(),
                    scriptBus.eventType(), null, scriptBus.canCancel(), scriptBus.canDispatch()));
        }
        return List.copyOf(entries);
    }

    /** 取 holder 在任一适用环境下暴露的 bus（同一 EventBusJS 实例，与目标环境无关）。 */
    private static EventBusJS<?, ?> firstApplicableBus(EventGroup.BusHolder holder) {
        for (ScriptType type : ScriptType.all()) {
            EventBusJS<?, ?> bus = holder.getBus(type);
            if (bus != null) return bus;
        }
        return null;
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
        // 动态脚本事件：与静态组一致的 side 过滤语义（canApplyOn），标签用查询侧 ScriptType。
        for (ScriptEventDefinition definition : ScriptEventRegistry.definitions()) {
            if (!definition.canApplyOn(scriptType)) continue;
            EventBusJS<?, ?> scriptBus = definition.bus();
            entries.add(EventCatalogEntry.of(
                    definition.groupName(), definition.eventName(), scriptType,
                    scriptBus.eventType(), null, scriptBus.canCancel(), scriptBus.canDispatch()));
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
        return new AdapterCatalogEntry(targetType, adapter.inputShapes(), adapter.getPrecedence(), adapter.syntaxDoc());
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

    private static Map<ScriptType, ApiEnvironmentSnapshot> buildManagedApis(IPluginRuntime runtime, List<ScriptType> scriptTypes) {
        Map<ScriptType, ApiEnvironmentSnapshot> map = new LinkedHashMap<>();
        for (ScriptType type : scriptTypes) {
            EnvironmentKey key = EnvironmentKeyFactory.current(type);
            ApiRuntimeView view = runtime.apiRuntime(key);
            if (view != null) {
                map.put(type, view.environmentSnapshot());
            }
        }
        return Map.copyOf(map);
    }
}
