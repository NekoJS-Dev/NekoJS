package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.script.ScriptType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NekoTypeDocs {
    private static final List<TypeDocCatalogEntry> TYPE_DOCS = new ArrayList<>();
    private static final List<ManualDeclarationCatalogEntry> MANUAL_DECLARATIONS = new ArrayList<>();
    private static boolean initialized = false;

    private NekoTypeDocs() {}

    public static synchronized List<TypeDocCatalogEntry> typeDocs() {
        if (!initialized) initialize();
        return List.copyOf(TYPE_DOCS);
    }

    public static synchronized List<TypeDocCatalogEntry> typeDocs(ScriptType scriptType) {
        return typeDocs().stream()
                .filter(entry -> appliesTo(entry.scriptType(), scriptType))
                .toList();
    }

    public static synchronized List<ManualDeclarationCatalogEntry> manualDeclarations() {
        if (!initialized) initialize();
        return List.copyOf(MANUAL_DECLARATIONS);
    }

    public static synchronized List<ManualDeclarationCatalogEntry> manualDeclarations(ScriptType scriptType) {
        return manualDeclarations().stream()
                .filter(entry -> appliesTo(entry.scriptType(), scriptType))
                .toList();
    }

    private static void initialize() {
        TypeDocsRegister register = new TypeDocsRegister() {
            @Override
            public void register(TypeDocCatalogEntry entry) {
                TYPE_DOCS.add(entry);
            }

            @Override
            public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) {
                MANUAL_DECLARATIONS.add(entry);
            }
        };

        NekoJSBasePluginManager.getPlugins().forEach(plugin -> plugin.registerTypeDocs(register));
        TYPE_DOCS.sort(Comparator.comparingInt(TypeDocCatalogEntry::priority));
        MANUAL_DECLARATIONS.sort(Comparator.comparingInt(ManualDeclarationCatalogEntry::priority));
        initialized = true;
    }

    private static boolean appliesTo(ScriptType source, ScriptType target) {
        return source.test(target);
    }
}
