package com.tkisor.nekojs.probe;

import static org.junit.jupiter.api.Assertions.*;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProbeGlobalCollectionAliasImportsTest {
    public static final class Api {
        public String list(List<String> value) { return value.getFirst(); }
        public int collection(Collection<String> value) { return value.size(); }
        public String iterable(Iterable<String> value) { return value.iterator().next(); }
        public long map(Map<String, Long> value) { return value.get("key"); }
    }

    @BeforeAll static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test void ordinaryBackendModulesImportTheCanonicalCollectionAliasesTheyActuallyUse(@TempDir Path temp) {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(Api.class.getName()), List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, List.of(Api.class), config, NekoJSPaths.fromGameDir(temp),
                "typescript", temp.resolve("typescript"), List.of(new TypeReflector().reflect(Api.class)));
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        String module = files.get("@package/com/tkisor/nekojs/probe/index.d.ts");
        assertNotNull(module, "The actual backend must emit the ordinary API package");
        assertTrue(module.contains("$List_<string>"), module);
        assertTrue(module.contains("$Collection_<string>"), module);
        assertTrue(module.contains("$Iterable_<string>"), module);
        assertTrue(module.contains("$Map_<string, number>"), module);
        Set<String> utilImports = importedSymbols(module, "java:java/util");
        assertTrue(utilImports.containsAll(Set.of("$List_", "$Collection_", "$Map_")), module);
        assertTrue(importedSymbols(module, "java:java/lang").contains("$Iterable_"), module);
        for (String name : List.of("$List_", "$Collection_", "$Map_")) {
            assertDefinition(files, "@package/java/util/index.d.ts", name);
        }
        assertDefinition(files, "@package/java/lang/index.d.ts", "$Iterable_");
    }

    private static Set<String> importedSymbols(String declaration, String target) {
        int module = declaration.indexOf("from \"" + target + "\"");
        assertTrue(module >= 0, "Missing actual module import for " + target + "\n" + declaration);
        int start = declaration.lastIndexOf("import ", module);
        assertTrue(start >= 0, declaration);
        String statement = declaration.substring(start, declaration.indexOf(';', module));
        return Arrays.stream(statement.replace('{', ' ').replace('}', ' ').replace(',', ' ').split("\\s+"))
                .collect(Collectors.toSet());
    }
    private static void assertDefinition(Map<String, String> files, String module, String symbol) {
        String declaration = files.get(module);
        assertNotNull(declaration, "Missing actual backend target package: " + module);
        assertTrue(declaration.contains("export type " + symbol + "<"), symbol + " must be an emitted canonical alias\n" + declaration);
    }
}
