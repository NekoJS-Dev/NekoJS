package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ModuleEventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.data.BindingExposure;

/** Adds explicit version publications to hand-built native event declaration fixtures. */
public final class ProbeEventFixtures {
    private ProbeEventFixtures() {}

    public static NekoScriptCatalogSnapshot publishVersioned(NekoScriptCatalogSnapshot snapshot) {
        var publications = snapshot.events().stream().filter(event -> !event.scriptDefined())
                .flatMap(event -> ScriptType.all().stream().filter(event.scriptType()::test)
                        .map(type -> new ModuleEventCatalogEntry("nekojs:event/versioned", "nekojs",
                                BindingExposure.Category.VERSION, type, event))).toList();
        return new NekoScriptCatalogSnapshot(snapshot.scriptTypes(), snapshot.bindings(), snapshot.events(),
                snapshot.adapters(), snapshot.recipeNamespaces(), snapshot.hostExtensions(), snapshot.snippets(),
                snapshot.typeDocs(), snapshot.manualDeclarations(), snapshot.classDeclarations(), snapshot.registryBuilderSurfaces(),
                snapshot.registryTypes(), snapshot.modIds(), snapshot.outputLayout(), snapshot.managedApis(), snapshot.legacySurface(),
                snapshot.beanAliasExclusions(), snapshot.beanMethodProjections(), snapshot.moduleBindings(), publications);
    }
}
