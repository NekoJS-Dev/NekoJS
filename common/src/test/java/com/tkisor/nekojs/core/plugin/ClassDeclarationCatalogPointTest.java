package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassDeclarationCatalogPointTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void rejectsUnstableOrInvalidClassDeclarationTargets() {
        class LocalType {}
        for (Class<?> invalid : List.of(int.class, String[].class, LocalType.class)) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> ClassDeclarationCatalogEntry.of(invalid, "export class $Invalid {}"));
            assertTrue(failure.getMessage().contains("[NEKO-4031]"), failure.getMessage());
        }
        assertThrows(IllegalArgumentException.class,
                () -> ClassDeclarationCatalogEntry.of(String.class, " "));
    }

    @Test
    void commonAndNodeTypeDocsContributionsReachGlobalImmutableSnapshots() {
        ClassDeclarationCatalogEntry commonReplacement = new ClassDeclarationCatalogEntry(
                String.class, "export class $String { common(): void; }", Set.of(Integer.class), 1);
        ClassDeclarationCatalogEntry commonOnly = ClassDeclarationCatalogEntry.of(
                Integer.class, "export class $Integer { commonOnly(): void; }");
        ClassDeclarationCatalogEntry nodeReplacement = new ClassDeclarationCatalogEntry(
                String.class, "export class $String { node(): void; }", Set.of(Long.class), 5);
        ClassDeclarationCatalogEntry nodeOnly = ClassDeclarationCatalogEntry.of(
                Long.class, "export class $Long { nodeOnly(): void; }");

        CommonContributor common = new CommonContributor(commonReplacement, commonOnly);
        NodeContributor node = new NodeContributor(nodeReplacement, nodeOnly);
        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(common, node), new ScriptPropertyRegistry.Impl());

        List<ClassDeclarationCatalogEntry> expected = ClassDeclarationCatalogEntry.resolve(List.of(
                commonReplacement, commonOnly, nodeReplacement, nodeOnly));
        assertEquals(expected, runtime.classDeclarations());
        assertEquals(nodeReplacement, runtime.classDeclarations().stream()
                .filter(entry -> entry.targetType() == String.class).findFirst().orElseThrow());
        assertEquals(expected, NekoScriptCatalog.snapshot(runtime).classDeclarations());
        for (ScriptType type : ScriptType.values()) {
            assertEquals(expected, NekoScriptCatalog.snapshot(runtime, type).classDeclarations(),
                    "class declarations are global because Java package modules are shared");
        }

        assertThrows(UnsupportedOperationException.class,
                () -> runtime.classDeclarations().add(commonOnly));
        assertThrows(UnsupportedOperationException.class,
                () -> commonReplacement.imports().add(Long.class));
        assertThrows(IllegalStateException.class, () -> common.registry.registerClassDeclaration(commonOnly),
                "the registration bucket must reject writes after bootstrap freezes it");
    }

    @Test
    void identicalEqualPriorityEntriesAreIdempotentAndHigherPriorityWins() {
        ClassDeclarationCatalogEntry low = new ClassDeclarationCatalogEntry(
                String.class, "export class $String { low(): void; }", Set.of(), 1);
        ClassDeclarationCatalogEntry high = new ClassDeclarationCatalogEntry(
                String.class, "export class $String { high(): void; }", Set.of(Integer.class), 9);
        ClassDeclarationCatalogEntry identicalHigh = new ClassDeclarationCatalogEntry(
                String.class, "export class $String { high(): void; }", Set.of(Integer.class), 9);

        assertEquals(List.of(high), ClassDeclarationCatalogEntry.resolve(List.of(low, high, identicalHigh)));
    }

    @Test
    void conflictingEqualPriorityContributionsFailWithStableCode() {
        ClassDeclarationCatalogEntry first = ClassDeclarationCatalogEntry.of(
                String.class, "export class $String { first(): void; }");
        ClassDeclarationCatalogEntry conflictingText = ClassDeclarationCatalogEntry.of(
                String.class, "export class $String { second(): void; }");
        ClassDeclarationCatalogEntry conflictingImports = ClassDeclarationCatalogEntry.of(
                String.class, "export class $String { first(): void; }", Set.of(Integer.class));

        IllegalArgumentException textFailure = assertThrows(IllegalArgumentException.class,
                () -> ClassDeclarationCatalogEntry.resolve(List.of(first, conflictingText)));
        assertTrue(textFailure.getMessage().contains("[NEKO-4032]"), textFailure.getMessage());
        assertTrue(textFailure.getMessage().contains(String.class.getName()), textFailure.getMessage());

        IllegalArgumentException importFailure = assertThrows(IllegalArgumentException.class,
                () -> ClassDeclarationCatalogEntry.resolve(List.of(first, conflictingImports)));
        assertTrue(importFailure.getMessage().contains("[NEKO-4032]"), importFailure.getMessage());
    }

    @Test
    void conflictingCommonAndNodeContributionsFailDuringBootstrap() {
        CommonContributor common = new CommonContributor(ClassDeclarationCatalogEntry.of(
                String.class, "export class $String { common(): void; }"));
        NodeContributor node = new NodeContributor(ClassDeclarationCatalogEntry.of(
                String.class, "export class $String { node(): void; }"));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> NekoPluginBootstrap.bootstrap(List.of(common, node), new ScriptPropertyRegistry.Impl()));
        assertTrue(failure.getMessage().contains("[NEKO-4032]"), failure.getMessage());
    }

    private static final class CommonContributor implements TypeDocsPoint.Contributor {
        private final List<ClassDeclarationCatalogEntry> entries;
        private TypeDocsRegister registry;

        private CommonContributor(ClassDeclarationCatalogEntry... entries) {
            this.entries = List.of(entries);
        }

        @Override
        public void registerTypeDocs(TypeDocsRegister registry) {
            this.registry = registry;
            entries.forEach(registry::registerClassDeclaration);
        }
    }

    private static final class NodeContributor implements NodeTypeDocsPoint.Contributor {
        private final List<ClassDeclarationCatalogEntry> entries;

        private NodeContributor(ClassDeclarationCatalogEntry... entries) {
            this.entries = List.of(entries);
        }

        @Override
        public void registerNodeTypeDocs(TypeDocsRegister registry) {
            entries.forEach(registry::registerClassDeclaration);
        }
    }
}
