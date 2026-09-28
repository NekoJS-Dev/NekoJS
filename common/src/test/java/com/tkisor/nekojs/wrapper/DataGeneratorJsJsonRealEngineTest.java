package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defect D-B regression (ticket 23): {@code DataGeneratorJS.json(path, value)} must serialize
 * JS object arguments to real JSON when called from script code on the real GraalJS engine.
 *
 * <p>Root cause: values proxied from GraalJS into the {@code Object} parameter arrive as host
 * {@code Map}/{@code List} proxies ({@code com.oracle.truffle.polyglot.PolyglotMap/List}), never
 * as {@code graal.graalvm.polyglot.Value}, so the old {@code instanceof Value} branch was
 * unreachable from scripts and the {@code String.valueOf} fallback wrote literal
 * {@code [object Object]} / {@code [object Array]} (proven by the 2026-09-28 runServer smoke,
 * ticket 23 REPORT "D-B 分诊").
 *
 * <p>Test geometry: a real GraalJS {@link Context} wired like {@code NekoSandboxFactory.build}
 * (same {@link NekoSharedHostAccess} + {@link ClassFilter} + interop options, precedent
 * {@code AdvancedJavaInteropSmokeTest}); the generator is exposed as a binding and called from
 * JS — the same host-method conversion boundary production scripts go through.
 */
class DataGeneratorJsJsonRealEngineTest {

    private static Path base;
    private Path root;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
        base = Platform.getGameDir().resolve("nekojs").resolve("test-datagen-json-" + System.nanoTime());
    }

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(base.resolve("case-" + System.nanoTime()));
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(base);
    }

    private Context newScriptContext(DataGeneratorJS generator) {
        NekoSharedHostAccess hostAccess = new NekoSharedHostAccess(List.of());
        ClassFilter classFilter = new ClassFilter(SandboxConfig.defaultConfig());
        Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(hostAccess.get())
                .allowHostClassLookup(classFilter)
                .allowCreateProcess(false)
                .allowValueSharing(true)
                .option("js.foreign-object-prototype", "true")
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .option("js.strict", "true")
                .build();
        context.getBindings("js").putMember("gen", generator);
        return context;
    }

    /** The headline case from the defect: a JS object literal must land as proper JSON. */
    @Test
    void jsObjectSerializesToProperJsonThroughTheRealEngine() throws IOException {
        try (Context context = newScriptContext(new DataGeneratorJS(root))) {
            context.eval("js", "gen.json('x/y.json', {a: 1, b: {c: [1, 2]}})");
        }
        Path file = root.resolve("x/y.json");
        assertTrue(Files.isRegularFile(file), "expected written file at " + file);
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(1, json.get("a").getAsInt());
        assertEquals(2, json.getAsJsonObject("b").getAsJsonArray("c").size());
        assertEquals(2, json.getAsJsonObject("b").getAsJsonArray("c").get(1).getAsInt());
    }

    @Test
    void jsNestedObjectWithNullMembersAndMixedArraysSerializesFully() throws IOException {
        try (Context context = newScriptContext(new DataGeneratorJS(root))) {
            context.eval("js", "gen.json('deep.json', {s: 'text', t: true, f: false,"
                    + " nil: null, arr: [1, 'two', false, null, {deep: [3]}]})");
        }
        var json = JsonParser.parseString(Files.readString(root.resolve("deep.json"))).getAsJsonObject();
        assertEquals("text", json.get("s").getAsString());
        assertTrue(json.get("t").getAsBoolean());
        assertFalse(json.get("f").getAsBoolean());
        assertTrue(json.get("nil").isJsonNull(), "JS null member must serialize as JSON null");
        var arr = json.getAsJsonArray("arr");
        assertEquals(1, arr.get(0).getAsInt());
        assertEquals("two", arr.get(1).getAsString());
        assertFalse(arr.get(2).getAsBoolean());
        assertTrue(arr.get(3).isJsonNull());
        assertEquals(3, arr.get(4).getAsJsonObject().getAsJsonArray("deep").get(0).getAsInt());
    }

    @Test
    void jsTopLevelArrayNumberAndBooleanSerializeAsJson() throws IOException {
        try (Context context = newScriptContext(new DataGeneratorJS(root))) {
            context.eval("js", "gen.json('arr.json', [1, 2, 3])");
            context.eval("js", "gen.json('num.json', 42)");
            context.eval("js", "gen.json('frac.json', 3.5)");
            context.eval("js", "gen.json('bool.json', true)");
        }
        var arr = JsonParser.parseString(Files.readString(root.resolve("arr.json"))).getAsJsonArray();
        assertEquals(3, arr.size());
        assertEquals(2, arr.get(1).getAsInt());
        assertEquals(42, JsonParser.parseString(Files.readString(root.resolve("num.json"))).getAsInt());
        assertEquals(3.5, JsonParser.parseString(Files.readString(root.resolve("frac.json"))).getAsDouble(),
                0.0);
        assertTrue(JsonParser.parseString(Files.readString(root.resolve("bool.json"))).getAsBoolean());
    }

    /** The pre-existing JSON-string form (and plain-string passthrough) must keep working. */
    @Test
    void jsonStringFormStillPassesThroughVerbatim() throws IOException {
        try (Context context = newScriptContext(new DataGeneratorJS(root))) {
            context.eval("js", "gen.json('s.json', '{\"type\":\"minecraft:block\"}')");
            context.eval("js", "gen.json('t.json', 'plain text')");
        }
        assertEquals("{\"type\":\"minecraft:block\"}", Files.readString(root.resolve("s.json")));
        assertEquals("plain text", Files.readString(root.resolve("t.json")),
                "a String argument is written verbatim (JSON-string form); structural validation stays downstream");
    }

    /** null / undefined must fail loudly in English instead of writing a literal \"null\" file. */
    @Test
    void nullAndUndefinedAreExplicitlyRejected() {
        try (Context context = newScriptContext(new DataGeneratorJS(root))) {
            PolyglotException fromNull = assertThrows(PolyglotException.class,
                    () -> context.eval("js", "gen.json('z1.json', null)"));
            assertTrue(fromNull.getMessage().contains("value must not be null"),
                    "expected explicit null rejection, got: " + fromNull.getMessage());
            PolyglotException fromUndefined = assertThrows(PolyglotException.class,
                    () -> context.eval("js", "gen.json('z2.json', undefined)"));
            assertTrue(fromUndefined.getMessage().contains("value must not be null"),
                    "expected explicit undefined rejection, got: " + fromUndefined.getMessage());
        }
        assertFalse(Files.exists(root.resolve("z1.json")), "no file may be written for a rejected value");
        assertFalse(Files.exists(root.resolve("z2.json")), "no file may be written for a rejected value");
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }
}
