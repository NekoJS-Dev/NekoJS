package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.ScriptTypePredicate;

import java.util.List;

public final class NekoTypeDocs {
    private NekoTypeDocs() {}

    public static List<TypeDocCatalogEntry> typeDocs(IPluginRuntime runtime) {
        return runtime.typeDocs();
    }

    public static List<TypeDocCatalogEntry> typeDocs(IPluginRuntime runtime, ScriptType scriptType) {
        return typeDocs(runtime).stream()
                .filter(entry -> appliesTo(entry.scriptType(), scriptType))
                .toList();
    }

    public static List<ManualDeclarationCatalogEntry> manualDeclarations(IPluginRuntime runtime) {
        return runtime.manualDeclarations();
    }

    public static List<ManualDeclarationCatalogEntry> manualDeclarations(IPluginRuntime runtime, ScriptType scriptType) {
        return manualDeclarations(runtime).stream()
                .filter(entry -> appliesTo(entry.scriptType(), scriptType))
                .toList();
    }

    private static boolean appliesTo(ScriptTypePredicate source, ScriptType target) {
        return source.test(target);
    }
}
