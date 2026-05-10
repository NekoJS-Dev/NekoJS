package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.MemberVisibilityQuery;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.NekoBindings;
import com.tkisor.nekojs.api.data.NekoJSTypeAdapters;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.NekoEventGroups;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.utils.event.dispatch.DispatchEventBus;

import java.util.ArrayList;
import java.util.List;

public final class NekoScriptCatalog {
    private static NekoCatalogPlatformProvider platformProvider = NekoCatalogPlatformProvider.EMPTY;

    private NekoScriptCatalog() {}

    public static void setPlatformProvider(NekoCatalogPlatformProvider provider) {
        platformProvider = provider == null ? NekoCatalogPlatformProvider.EMPTY : provider;
    }

    public static NekoScriptCatalogSnapshot snapshot() {
        return new NekoScriptCatalogSnapshot(
                ScriptType.all(),
                bindings(),
                events(),
                adapters(),
                List.copyOf(platformProvider.recipeNamespaces()),
                hostExtensions(),
                List.copyOf(platformProvider.snippets()),
                platformProvider.outputLayout()
        );
    }

    public static NekoScriptCatalogSnapshot snapshot(ScriptType scriptType) {
        return new NekoScriptCatalogSnapshot(
                List.of(scriptType),
                bindings(scriptType),
                events(scriptType),
                adapters(),
                List.copyOf(platformProvider.recipeNamespaces()),
                hostExtensions(scriptType),
                snippets(scriptType),
                platformProvider.outputLayout()
        );
    }

    public static TypeOutputLayout outputLayout() {
        return platformProvider.outputLayout();
    }

    public static List<BindingCatalogEntry> bindings() {
        List<BindingCatalogEntry> entries = new ArrayList<>();
        for (ScriptType type : ScriptType.all()) {
            entries.addAll(bindings(type));
        }
        return List.copyOf(entries);
    }

    public static List<BindingCatalogEntry> bindings(ScriptType scriptType) {
        List<BindingCatalogEntry> entries = new ArrayList<>();
        for (Binding binding : NekoBindings.getFor(scriptType).values()) {
            entries.add(BindingCatalogEntry.of(binding.getName(), scriptType, binding.getType(), binding.isStaticClass()));
        }
        return List.copyOf(entries);
    }

    public static List<EventCatalogEntry> events() {
        List<EventCatalogEntry> entries = new ArrayList<>();
        for (ScriptType type : ScriptType.all()) {
            entries.addAll(events(type));
        }
        return List.copyOf(entries);
    }

    public static List<EventCatalogEntry> events(ScriptType scriptType) {
        List<EventCatalogEntry> entries = new ArrayList<>();
        for (EventGroup group : NekoEventGroups.all().values()) {
            for (var entry : group.viewBuses().entrySet()) {
                EventGroup.BusHolder holder = entry.getValue();
                if (!holder.canApplyOn(scriptType)) continue;

                var bus = holder.getBus(scriptType);
                if (bus == null) continue;

                Class<?> dispatchKeyType = bus.bus() instanceof DispatchEventBus<?, ?> dispatchBus
                        ? dispatchBus.dispatchKey().keyType()
                        : null;

                entries.add(EventCatalogEntry.of(
                        group.name(),
                        entry.getKey(),
                        scriptType,
                        bus.bus().eventType(),
                        dispatchKeyType,
                        bus.canCancel(),
                        bus.canDispatch()
                ));
            }
        }
        return List.copyOf(entries);
    }

    public static List<AdapterCatalogEntry> adapters() {
        List<AdapterCatalogEntry> entries = new ArrayList<>();
        for (var adapter : NekoJSTypeAdapters.all()) {
            entries.add(AdapterCatalogEntry.of(adapter.getTargetClass(), adapter.getPrecedence()));
        }
        return List.copyOf(entries);
    }

    public static List<HostExtensionCatalogEntry> hostExtensions() {
        List<HostExtensionCatalogEntry> entries = new ArrayList<>();
        for (ScriptType type : ScriptType.all()) {
            entries.addAll(hostExtensions(type));
        }
        return List.copyOf(entries);
    }

    public static List<HostExtensionCatalogEntry> hostExtensions(ScriptType scriptType) {
        List<HostExtensionCatalogEntry> entries = new ArrayList<>();
        for (HostExtensionSource source : platformProvider.hostExtensions()) {
            if (source.scriptType() != ScriptType.COMMON && source.scriptType() != scriptType) continue;

            for (var binding : MemberVisibilityQuery.getVisibleMethods(source.extensionInterface()).values()) {
                entries.add(new HostExtensionCatalogEntry(
                        source.targetClass(),
                        source.extensionInterface(),
                        binding.member().getName(),
                        binding.jsName(),
                        binding.member(),
                        scriptType,
                        false
                ));
            }
        }
        return List.copyOf(entries);
    }

    public static List<SnippetCatalogEntry> snippets(ScriptType scriptType) {
        List<SnippetCatalogEntry> entries = new ArrayList<>();
        for (SnippetCatalogEntry snippet : platformProvider.snippets()) {
            if (snippet.scriptType() == ScriptType.COMMON || snippet.scriptType() == scriptType) {
                entries.add(snippet);
            }
        }
        return List.copyOf(entries);
    }
}
