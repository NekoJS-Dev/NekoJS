package com.tkisor.nekojs.bindings.query;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.TypeOutputLayout;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Declaration/Probe parity fixture for the query bindings. It replays the production path on the
 * runtime classes ({@code EntitySelectorsJS}/{@code EntitySelectorBuilderJS}, plus {@code DataMapJS} on
 * neoforge): the seed classes of the ProbeCoordinator BFS are reflected by {@link TypeReflector} into
 * the shared IR and rendered by the real TS and Python probe backends. Output goes only to
 * {@code @TempDir} and never touches a golden file. The assertions are:
 * <ol>
 *   <li>generating the whole tree twice from the same input gives byte-identical output
 *       (determinism);</li>
 *   <li>every runtime public member, with the list derived by reflection rather than written by hand,
 *       appears in both the TS and the Python output (anchored per line, so a one-letter method name
 *       cannot match as a substring); a name that does not exist never appears (phantom negative
 *       sample).</li>
 * </ol>
 *
 * <p>Bare-JVM constraint: classes load with initialize=false, so the MC registry class initialization
 * chain never runs. {@code DataMapJS} is included only on the neoforge class path; fabric nodes lack
 * it.
 */
class QueryToolDeclarationParityTest {

    private static final String PHANTOM = "thisMemberDoesNotExistAtRuntime";

    @TempDir
    Path tempA;

    @TempDir
    Path tempB;

    @TempDir
    Path tempC;

    QueryToolDeclarationParityTest() {
        // Minimal Platform injection for the shared test tree. Platform.init is defensive: it does
        // nothing when Platform is already initialized.
        try {
            Platform.init(new StubPlatform());
        } catch (RuntimeException ignored) {
            // Another test already initialized Platform.
        }
    }

    // ---- TS ----

    @Test
    void tsDeclarationIsDeterministicAndMatchesRuntimeMembers() throws Exception {
        Map<String, String> first = generate("typescript", tempA);
        Map<String, String> second = generate("typescript", tempA);
        assertEquals(first.keySet(), second.keySet(), "TS file set must be deterministic");
        for (String key : first.keySet()) {
            assertEquals(first.get(key), second.get(key), "TS byte drift: " + key);
        }
        assertMembersPresent(first, runtimeMethodNames());
    }

    // ---- Python ----

    @Test
    void pythonDeclarationIsDeterministicAndMatchesRuntimeMembers() throws Exception {
        Map<String, String> first = generate("python", tempB);
        Map<String, String> second = generate("python", tempB);
        assertEquals(first.keySet(), second.keySet(), "Python file set must be deterministic");
        for (String key : first.keySet()) {
            assertEquals(first.get(key), second.get(key), "Python byte drift: " + key);
        }
        assertMembersPresent(first, runtimeMethodNames());
    }

    // ---- Generation (replays the production derivation chain) ----

    private Map<String, String> generate(String language, Path outputDir) throws IOException {
        List<Class<?>> collected = collectedClasses();
        TypeReflector reflector = new TypeReflector();
        List<TypeDecl> ir = new ArrayList<>();
        for (Class<?> cls : collected) {
            ir.add(reflector.reflect(cls));
        }

        NekoScriptCatalogSnapshot snapshot = new NekoScriptCatalogSnapshot(
                List.of(ScriptType.values()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new TypeOutputLayout(Path.of("probe-types"), Path.of("snippets")),
                Map.of(), List.of());
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs"), List.of(), List.of(), List.of(), 5, "SMART"));
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tempC);
        Files.createDirectories(paths.root());
        ProbeContext ctx = new ProbeContext.Of(snapshot, collected, cfg, paths, language, outputDir, ir);

        ProbeBackend backend = language.equals("typescript")
                ? new TypeScriptProbeBackend() : new PythonProbeBackend();
        ProbeBackend.GenerateResult result = backend.generate(ctx);
        assertTrue(result.success(), language + " probe generate failed: " + result.message());

        Map<String, String> tree = new TreeMap<>();
        try (Stream<Path> files = Files.walk(outputDir)) {
            List<Path> sorted = new ArrayList<>(files.filter(Files::isRegularFile).sorted().toList());
            for (Path file : sorted) {
                tree.put(outputDir.relativize(file).toString().replace('\\', '/'),
                        Files.readString(file));
            }
        }
        assertFalse(tree.isEmpty(), language + " declaration tree must not be empty");
        return tree;
    }

    // ---- Member parity (derived from runtime reflection, anchored per line) ----

    /** The runtime public member names of the seed classes, derived from the same collectedClasses as
     * generate. */
    private static List<String> runtimeMethodNames() {
        List<String> names = new ArrayList<>();
        for (Class<?> cls : collectedClasses()) {
            for (Method method : cls.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())
                        && !method.isSynthetic() && method.getDeclaringClass() == cls) {
                    names.add(method.getName());
                }
            }
        }
        assertFalse(names.isEmpty(), "runtime member list must not be empty");
        return names;
    }

    private static void assertMembersPresent(Map<String, String> tree, List<String> memberNames) {
        // Only declaration files (.d.ts / .pyi) are scanned, anchored per line: a TS member line starts
        // with name( and a Python one with def name(.
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : tree.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(".d.ts") || key.endsWith(".pyi")) {
                for (String line : entry.getValue().split("\n")) {
                    lines.add(line.trim());
                }
            }
        }
        assertFalse(lines.isEmpty(), "declaration lines must not be empty");
        for (String name : memberNames) {
            boolean present = lines.stream().anyMatch(line ->
                    line.startsWith(name + "(")
                            || line.startsWith("def " + name + "(")
                            // Python getter semantics: isXxx/getXxx render as @property def xxx
                            || line.startsWith("def " + dePrefixed(name) + "("));
            assertTrue(present, "runtime member missing from declaration: " + name);
        }
        assertFalse(lines.stream().anyMatch(line -> line.startsWith(PHANTOM)),
                "phantom member leaked into declaration");
    }

    private static String dePrefixed(String name) {
        if (name.startsWith("is") && name.length() > 2) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        if (name.startsWith("get") && name.length() > 3) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }
        return name;
    }

    // ---- helpers ----

    /** The seed classes of the production probe, the script-facing classes of this domain; DataMapJS
     * exists only on the neoforge class path. */
    private static List<Class<?>> collectedClasses() {
        List<Class<?>> collected = new ArrayList<>();
        for (String name : new String[]{
                "com.tkisor.nekojs.util.selector.EntitySelectorsJS",
                "com.tkisor.nekojs.util.selector.EntitySelectorBuilderJS",
                "com.tkisor.nekojs.bindings.static_access.DataMapJS"}) {
            Class<?> cls = load(name);
            if (cls != null) {
                collected.add(cls);
            }
        }
        assertFalse(collected.isEmpty(), "seed classes must not be empty");
        return collected;
    }

    /** Loads a class safely on a bare JVM (initialize=false), so reflection does not trigger MC registry
     * class initialization. */
    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, QueryToolDeclarationParityTest.class.getClassLoader());
        } catch (ClassNotFoundException error) {
            return null;
        }
    }

    private static final class StubPlatform implements IPlatform {
        @Override
        public boolean isClient() {
            return false;
        }

        @Override
        public boolean isDevelopment() {
            return true;
        }

        @Override
        public String getMcVersion() {
            return "0.0.0";
        }

        @Override
        public Path getGameDir() {
            return com.tkisor.nekojs.TestGameDirs.unique("nekojs-query-tools-test");
        }

        @Override
        public Map<String, IModInfo> getMods() {
            return Map.of();
        }

        @Override
        public IModInfo getInfo(String modID) {
            return null;
        }

        @Override
        public String getLoaderId() {
            return "test";
        }

        @Override
        public String getLoaderVersion() {
            return "0.0.0";
        }
    }
}
