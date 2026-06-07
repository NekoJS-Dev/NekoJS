package com.tkisor.nekojs.api.plugin;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.script.ScriptType;

import java.util.List;
import java.util.Map;

/**
 * Minimal read-only contract that the api layer needs from the plugin runtime.
 * Implemented by {@code NekoPluginRuntime} in core.
 */
public interface IPluginRuntime {

    Map<String, Binding> bindings(ScriptType type);

    Map<String, EventGroup> eventGroups();

    List<JSTypeAdapter<?>> adapters();

    List<TypeDocCatalogEntry> typeDocs();

    List<ManualDeclarationCatalogEntry> manualDeclarations();
}
