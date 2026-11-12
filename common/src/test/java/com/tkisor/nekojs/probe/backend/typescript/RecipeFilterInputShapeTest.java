package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

/** Formatting coverage complements, but never replaces, the native canonical-adapter test. */
class RecipeFilterInputShapeTest {
    public static class FilterTarget {}
    public static class MatchContext { public String getId() { return "fixture:id"; } }

    @Test void typedCallbackRetainsHostParametersBooleanReturnAndDependencyClosureInBothBackends(@TempDir Path temp) throws Exception {
        var callback = callable(List.of(Slot.req("match", host(MatchContext.class))), bool());
        var target = new AdapterCatalogEntry(FilterTarget.class, List.of(self(), callback, arrayOf(self())), ConversionPrecedence.LOW, Optional.empty());
        var contextAdapter = new AdapterCatalogEntry(MatchContext.class, List.of(self(), string()), ConversionPrecedence.LOW, Optional.empty());
        var generator = new AdapterAliasGenerator(new TypeAliasRegistry());
        generator.prepare(List.of(target, contextAdapter), Set.of(FilterTarget.class.getName(), MatchContext.class.getName()));
        var alias = generator.getAlias(FilterTarget.class.getName());
        assertEquals("$RecipeFilterInputShapeTest$FilterTarget | ((match: $RecipeFilterInputShapeTest$MatchContext) => boolean) | $RecipeFilterInputShapeTest$FilterTarget_[]", alias.union());
        assertFalse(alias.union().contains("MatchContext_"), "Callback payloads are actual hosts, not accepted input coercions");
        assertTrue(alias.referenceFqns().contains(MatchContext.class.getName()));
        assertTrue(generator.hostImports().contains(MatchContext.class.getName()));

        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(target, contextAdapter),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".probe", new ProbeConfig.ScanConfig(List.of(), List.of(), List.of(), List.of(), 1, "SMART"));
        var paths = NekoJSPaths.fromGameDir(temp);
        Files.createDirectories(paths.root());
        var output = temp.resolve("python");
        var reflector = new TypeReflector();
        var probe = new ProbeContext.Of(snapshot, List.of(FilterTarget.class, MatchContext.class), config, paths, "python", output,
                List.of(reflector.reflect(FilterTarget.class), reflector.reflect(MatchContext.class)));
        var result = new PythonProbeBackend().generate(probe);
        assertTrue(result.success(), result.message());
        String declaration = Files.readString(output.resolve("nekojs/_java/com/tkisor/nekojs/probe/backend/typescript/__init__.pyi"));
        assertTrue(declaration.contains("Callable[[RecipeFilterInputShapeTest_MatchContext], bool]"), declaration);
        assertTrue(declaration.contains("from typing import Any, Callable"), declaration);
        assertFalse(declaration.contains("Callable[[RecipeFilterInputShapeTest_MatchContext_],"), declaration);
    }

    @Test void callableShapeCopiesItsRequiredParametersAndRejectsUnsupportedSignatures() {
        var parameters = new ArrayList<>(List.of(Slot.req("match", string())));
        var shape = callable(parameters, bool());
        parameters.clear();
        assertEquals(1, shape.parameters().size());
        assertThrows(UnsupportedOperationException.class, () -> shape.parameters().clear());
        assertThrows(IllegalArgumentException.class, () -> callable(List.of(Slot.opt("match", string())), bool()));
        assertThrows(IllegalArgumentException.class, () -> callable(List.of(Slot.req("match", string()), Slot.req("match", string())), bool()));
        assertThrows(NullPointerException.class, () -> callable(List.of(), null));
    }
}
