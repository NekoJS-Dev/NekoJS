package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.ScriptTypePredicate;

import java.util.List;

public final class NekoTypeDocs {
    private NekoTypeDocs() {}

    public static List<TypeDocCatalogEntry> typeDocs() {
        return NekoRuntimeAccess.get().typeDocs();
    }

    public static List<TypeDocCatalogEntry> typeDocs(ScriptType scriptType) {
        return typeDocs().stream()
                .filter(entry -> appliesTo(entry.scriptType(), scriptType))
                .toList();
    }

    public static List<ManualDeclarationCatalogEntry> manualDeclarations() {
        return NekoRuntimeAccess.get().manualDeclarations();
    }

    public static List<ManualDeclarationCatalogEntry> manualDeclarations(ScriptType scriptType) {
        return manualDeclarations().stream()
                .filter(entry -> appliesTo(entry.scriptType(), scriptType))
                .toList();
    }

    private static boolean appliesTo(ScriptTypePredicate source, ScriptType target) {
        return source.test(target);
    }
}
