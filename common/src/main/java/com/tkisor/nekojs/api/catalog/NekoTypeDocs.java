package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.script.ScriptType;

import java.util.List;

public final class NekoTypeDocs {
    private NekoTypeDocs() {}

    public static List<TypeDocCatalogEntry> typeDocs() {
        return NekoPluginRuntime.current().typeDocs();
    }

    public static List<TypeDocCatalogEntry> typeDocs(ScriptType scriptType) {
        return typeDocs().stream()
                .filter(entry -> appliesTo(entry.scriptType(), scriptType))
                .toList();
    }

    public static List<ManualDeclarationCatalogEntry> manualDeclarations() {
        return NekoPluginRuntime.current().manualDeclarations();
    }

    public static List<ManualDeclarationCatalogEntry> manualDeclarations(ScriptType scriptType) {
        return manualDeclarations().stream()
                .filter(entry -> appliesTo(entry.scriptType(), scriptType))
                .toList();
    }

    private static boolean appliesTo(ScriptType source, ScriptType target) {
        return source.test(target);
    }
}
