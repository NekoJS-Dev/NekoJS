package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.probe.testfixture.ClassDeclarationFixtures;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeDeclarationCollectionTest {
    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void genericHeritageArgumentsEnterTheSharedIrCollection() {
        var snapshot = snapshot(List.of(BindingCatalogEntry.of("Derived", ScriptType.SERVER, Derived.class, false)),
                List.of());
        Set<Class<?>> classes = ProbeCoordinator.collectClasses(snapshot, config(List.of()));
        assertTrue(classes.contains(ClassDeclarationFixtures.Imported.class), classes.toString());
    }

    @Test
    void functionalWildcardBoundsEnterCollectionWithoutAnExplicitSeed() {
        var snapshot = snapshot(List.of(BindingCatalogEntry.of("Api", ScriptType.SERVER,
                com.tkisor.nekojs.probe.testfixture.CommunityProbeTypes.Api.class, false)), List.of());
        Set<Class<?>> classes = ProbeCoordinator.collectClasses(snapshot, config(List.of()));
        assertTrue(classes.contains(com.tkisor.nekojs.probe.testfixture.callback.CallbackValue.class),
                classes.toString());
    }

    @Test
    void authoredTypesAndExplicitDependenciesAreSeedsSubjectToExclusions() {
        var entry = ClassDeclarationCatalogEntry.of(Derived.class, "export class $Derived {}",
                Set.of(ClassDeclarationFixtures.Adapted.class));
        var snapshot = snapshot(List.of(), List.of(entry));
        Set<Class<?>> classes = ProbeCoordinator.collectClasses(snapshot, config(List.of()));
        assertTrue(classes.contains(Derived.class), classes.toString());
        assertTrue(classes.contains(ClassDeclarationFixtures.Adapted.class), classes.toString());
        Set<Class<?>> filtered = ProbeCoordinator.collectClasses(snapshot,
                config(List.of("com.tkisor.nekojs.probe.testfixture")));
        assertTrue(filtered.contains(Derived.class), filtered.toString());
        assertFalse(filtered.contains(ClassDeclarationFixtures.Adapted.class), filtered.toString());
    }

    private static ProbeConfig config(List<String> excluded) {
        return new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), excluded, List.of(), 3, "SMART"));
    }

    private static NekoScriptCatalogSnapshot snapshot(List<BindingCatalogEntry> bindings,
                                                      List<ClassDeclarationCatalogEntry> declarations) {
        return new NekoScriptCatalogSnapshot(List.of(), bindings, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), declarations, List.of(), List.of(),
                List.of(), null, Map.of(), List.of());
    }

    public static class Base<T> {}
    public static final class Derived extends Base<ClassDeclarationFixtures.Imported> {}
}
