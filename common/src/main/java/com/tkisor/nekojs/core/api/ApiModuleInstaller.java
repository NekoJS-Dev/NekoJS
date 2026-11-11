package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ModuleBinding;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.module.NekoScriptModuleLoaderHost;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds guest namespaces only in their owning Context, never in Plugin Runtime products. */
public final class ApiModuleInstaller {
    private ApiModuleInstaller() {}

    public static void install(Context context, IPluginRuntime runtime, ScriptType scriptType) {
        Map<String, Map<String, Object>> modules = new LinkedHashMap<>();
        modules.put("nekojs/api/stable", new LinkedHashMap<>());
        modules.put("nekojs/api/version", new LinkedHashMap<>());
        for (ModuleBinding export : runtime.moduleBindings(scriptType)) {
            var binding = export.binding();
            Object value = binding.value();
            Value delegate = value instanceof Class<?> type
                    ? context.getBindings("js").getMember("Java").invokeMember("type", type.getName())
                    : context.asValue(value);
            Object facade = value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                    ? value : new BindingValueProxy(delegate, binding.valueType(), value instanceof Class<?>);
            modules.computeIfAbsent(export.moduleId(), ignored -> new LinkedHashMap<>()).put(binding.name(), facade);
        }
        for (var publication : runtime.moduleEventGroups()) {
            if (!publication.available(scriptType)) continue;
            var exports = modules.computeIfAbsent(publication.moduleId(), ignored -> new LinkedHashMap<>());
            if (exports.putIfAbsent(publication.group().name(), new ModuleEventGroupProxy(context, publication, scriptType)) != null) {
                throw new IllegalArgumentException("[NEKO-4057] Duplicate API export: " + publication.moduleId() + ":" + publication.group().name());
            }
        }
        var bindings = context.getBindings("js");
        Value hostValue = bindings.getMember("__nekoScriptModuleLoaderHost");
        Value define = bindings.getMember("__nekoNodeDefineApi");
        // Minimal embedding contexts may intentionally omit the Node module host.
        if (hostValue == null || define == null) {
            if (modules.values().stream().allMatch(Map::isEmpty)) return;
            throw new IllegalStateException("[NEKO-4057] API modules require the script module host");
        }
        NekoScriptModuleLoaderHost host = hostValue.asHostObject();
        // ProxyObject supplies own keys without exposing Java Map methods to the guest namespace.
        Value freeze = context.eval("js", "entries => Object.freeze(Object.assign(Object.create(null), entries))");
        modules.forEach((id, exports) -> {
            Value namespace = freeze.execute(graal.graalvm.polyglot.proxy.ProxyObject.fromMap(exports));
            define.execute(id, namespace);
            host.registerApiModule(id, exports.keySet().toArray(String[]::new));
        });
    }
}
