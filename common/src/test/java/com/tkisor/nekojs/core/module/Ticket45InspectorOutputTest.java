package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.ui.InspectorNode;
import com.tkisor.nekojs.api.ui.InspectorScreenshot;
import com.tkisor.nekojs.api.ui.InspectorSnapshot;
import com.tkisor.nekojs.api.ui.InspectorSnapshots;
import com.tkisor.nekojs.api.ui.InspectorViewport;
import com.tkisor.nekojs.api.ui.ResourceStatus;
import com.tkisor.nekojs.api.ui.UiErrorCodes;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 45 fake-host inspector output, executed against the real common JSX runtime:
 * the {@code ui-inspector.tsx} fixture renders one representative UI at all six viewport
 * profiles, and this suite collects the guest layout snapshots through the shared
 * {@link InspectorSnapshots} path — the same code the NeoForge host runs — then pins the
 * decorated records against a read-only golden file. Real-client capture on NeoForge
 * 26.2 is NOT RUN here (no Minecraft client); the host-side glue has its own guarded
 * smoke suite in {@code client.ui.Ticket45InspectorSmokeTest}.
 */
class Ticket45InspectorOutputTest {

    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private NekoModulePipelineCache cache;
    private Context context;
    private NekoScriptModuleLoaderHost host;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = pathsFor(gameDir);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        // Same sandbox shape as the ui-core proof: the jsx-automatic flags decide the
        // automatic JSX runtime the fixture relies on.
        boot(new SandboxConfig(false, false, false, false, true, true, true, true, 30, 0, 0,
                SandboxConfig.PACK_SYNC_OFF, false, false));
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    private void boot(SandboxConfig sandboxConfig) throws Exception {
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
        compilers.register(NekoTsxLanguagePlugin.INSTANCE);
        cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, sandboxConfig),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(sandboxConfig, paths), paths, cache))
                .build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build();
        NekoModuleResolver resolver = new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                new ScriptFilePolicy(compilers));
        NekoNodeModuleInstaller.install(context, ScriptType.TEST, resolver, paths,
                new DefaultErrorTracker(paths, sandboxConfig), sandboxConfig, cache);
        host = (NekoScriptModuleLoaderHost) context.getBindings("js")
                .getMember("__nekoScriptModuleLoaderHost").asHostObject();
    }

    private Value loadFixture() throws IOException {
        try (var input = getClass().getResourceAsStream("/nekojs/language-ts-examples/tsx/ui-inspector.tsx")) {
            assertNotNull(input, "the inspector fixture must exist");
            Files.write(paths.serverScripts().resolve("src/ui-inspector.tsx"),
                    input.readAllBytes());
        }
        Value exports = (Value) host.loadEntry("./server_scripts/src/ui-inspector.tsx");
        Value proof = exports.getMember("inspectorProof");
        assertEquals(true, proof.getMember("passed").asBoolean(), "the fixture self-checks must pass");
        return proof;
    }

    private static final Function<String, ResourceStatus> RESOURCES = id ->
            ResourceStatus.resolved(id, "pack/assets/" + id.replace(':', '/') + ".png");

    private InspectorSnapshot decorated(Value proof, int index) {
        Value snapshot = proof.getMember("snapshots").getArrayElement(index);
        InspectorSnapshot base = InspectorSnapshots.read(
                proof.getMember("rootId").asString(), proof.getMember("source").asString(), snapshot);
        List<InspectorSnapshot.PhaseError> errors = List.of(new InspectorSnapshot.PhaseError(
                proof.getMember("error").getMember("phase").asString(),
                proof.getMember("error").getMember("rootId").asString(),
                proof.getMember("error").getMember("message").asString()));
        return InspectorSnapshots.decorate(base,
                Set.of(proof.getMember("focusedId").asString()), RESOURCES, errors,
                new InspectorScreenshot("fake-frame",
                        base.viewport().width(), base.viewport().height()));
    }

    @Test
    void sixProfileDecoratedOutputMatchesTheGoldenFile() throws Exception {
        Value proof = loadFixture();
        StringBuilder collected = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            InspectorSnapshot decorated = decorated(proof, i);
            assertEquals(i + 1, decorated.profile(), "profile " + (i + 1) + " is measured");
            collected.append("--- profile ").append(i + 1).append(" ---").append((char) 10)
                    .append(InspectorSnapshots.canonical(decorated));
        }
        String golden;
        try (var input = getClass().getResourceAsStream("/nekojs/inspector/six-profiles-golden.txt")) {
            assertNotNull(input, "the golden file must exist");
            golden = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace(String.valueOf((char) 13) + (char) 10, String.valueOf((char) 10));
        }
        assertEquals(golden, collected.toString(),
                "golden output is read-only: an intentional change must review the old/new diff");
    }

    @Test
    void decoratedRecordsCarryBindingsFocusResourcesAndErrorPhase() throws Exception {
        Value proof = loadFixture();
        InspectorSnapshot decorated = decorated(proof, 5);
        InspectorNode act = find(decorated.nodes(), "act");
        assertNotNull(act, "the act button is locatable by id");
        assertEquals(List.of("blur", "click"), act.bindings(), "bindings summarize bound event names");
        assertEquals(true, act.focused(), "host focus decorates the focused node");
        InspectorNode hero = find(decorated.nodes(), "hero");
        assertEquals(1, hero.resources().size(), "the image id resolves to one status");
        assertEquals(ResourceStatus.State.RESOLVED, hero.resources().getFirst().state());
        assertEquals("mymod:gui/hero", hero.resources().getFirst().id());
        InspectorNode scroller = find(decorated.nodes(), "scroller");
        assertEquals(2.0, scroller.scrollOffset(), "the scroll offset is a resolved style fact");
        assertEquals("layout", decorated.errors().getFirst().phase(), "the error phase is retained");
        assertEquals("insp-error-root", decorated.errors().getFirst().rootId());
        InspectorSnapshot first = decorated(proof, 0);
        assertTrue(first.diagnostics().contains("wide:overflow-right"),
                "the 200px row overflows the 100px viewport: " + first.diagnostics());
        InspectorNode wide = find(first.nodes(), "wide");
        assertEquals(100.0, wide.overflow().right());
    }

    @Test
    void hostObjectPathProducesTheIsomorphicRecord() throws Exception {
        Value proof = loadFixture();
        Value guest = proof.getMember("snapshots").getArrayElement(5);
        InspectorSnapshot guestPath = InspectorSnapshots.read(
                proof.getMember("rootId").asString(), proof.getMember("source").asString(), guest);
        // A host that receives the same snapshot shape as plain host data (never crossing
        // the guest boundary) must read the identical record through the shared path.
        InspectorSnapshot hostPath = InspectorSnapshots.read(guestPath.rootId(), guestPath.source(),
                toPlainSnapshot(guestPath));
        assertEquals(guestPath, hostPath,
                "the shared collection path is structure-identical for guest and host objects");
    }

    @Test
    void malformedSnapshotIsRejectedWithTheInspectorCode() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> InspectorSnapshots.read("r", "fake-host", Value.asValue(Map.of("nodes", List.of()))));
        assertTrue(failure.getMessage().contains(UiErrorCodes.INSPECTOR_SNAPSHOT_MALFORMED),
                "stable code in: " + failure.getMessage());
    }

    @Test
    void malformedHostScalarIsRejectedWithTheInspectorCode() {
        // Same shape as the runtime snapshot but with a non-boolean `visible`: the strict
        // host-data path must fail with NEKO-8001 just like the guest path would.
        Map<String, Object> rect = new LinkedHashMap<>(Map.of("x", 0, "y", 0, "width", 1, "height", 1));
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "panel");
        node.put("key", null);
        node.put("visible", "yes");
        node.put("rect", rect);
        node.put("clip", rect);
        node.put("overflow", Map.of("left", 0, "top", 0, "right", 0, "bottom", 0));
        node.put("style", Map.of());
        node.put("children", List.of());
        Map<String, Object> viewport = new LinkedHashMap<>();
        viewport.put("width", 100);
        viewport.put("height", 100);
        viewport.put("safeArea", Map.of("top", 0, "right", 0, "bottom", 0, "left", 0));
        viewport.put("contentWidth", 100);
        viewport.put("contentHeight", 100);
        viewport.put("profile", 1);
        viewport.put("guiScale", 2);
        viewport.put("designScale", 1.0);
        Map<String, Object> snapshot = Map.of("viewport", viewport, "nodes", List.of(node));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> InspectorSnapshots.read("r", "fake-host", snapshot));
        assertTrue(failure.getMessage().contains(UiErrorCodes.INSPECTOR_SNAPSHOT_MALFORMED),
                "host-data coercion failures carry the stable code: " + failure.getMessage());
    }

    private static InspectorNode find(List<InspectorNode> nodes, String id) {
        for (InspectorNode node : nodes) {
            if (id.equals(node.id())) return node;
            InspectorNode nested = find(node.children(), id);
            if (nested != null) return nested;
        }
        return null;
    }

    /** Mirrors the runtime layout snapshot shape as plain host data. */
    private static Map<String, Object> toPlainSnapshot(InspectorSnapshot snapshot) {
        InspectorViewport viewport = snapshot.viewport();
        Map<String, Object> plain = new LinkedHashMap<>();
        plain.put("profile", viewport.profile());
        plain.put("viewport", new LinkedHashMap<>(Map.of(
                "width", viewport.width(), "height", viewport.height(),
                "safeArea", new LinkedHashMap<>(Map.of(
                        "top", viewport.safeArea().top(), "right", viewport.safeArea().right(),
                        "bottom", viewport.safeArea().bottom(), "left", viewport.safeArea().left())),
                "contentWidth", viewport.contentWidth(), "contentHeight", viewport.contentHeight(),
                "profile", viewport.profile(),
                "guiScale", viewport.guiScale(), "designScale", viewport.designScale())));
        plain.put("nodes", snapshot.nodes().stream().map(Ticket45InspectorOutputTest::toPlainNode).toList());
        plain.put("diagnostics", snapshot.diagnostics());
        return plain;
    }

    private static Map<String, Object> toPlainNode(InspectorNode node) {
        Map<String, Object> plain = new LinkedHashMap<>();
        plain.put("type", node.type());
        plain.put("key", node.key());
        if (node.id() != null) plain.put("id", node.id());
        plain.put("visible", node.visible());
        plain.put("rect", new LinkedHashMap<>(Map.of(
                "x", node.rect().x(), "y", node.rect().y(),
                "width", node.rect().width(), "height", node.rect().height())));
        plain.put("clip", new LinkedHashMap<>(Map.of(
                "x", node.clip().x(), "y", node.clip().y(),
                "width", node.clip().width(), "height", node.clip().height())));
        plain.put("overflow", new LinkedHashMap<>(Map.of(
                "left", node.overflow().left(), "top", node.overflow().top(),
                "right", node.overflow().right(), "bottom", node.overflow().bottom())));
        plain.put("style", new LinkedHashMap<>(node.style()));
        plain.put("bindings", node.bindings());
        plain.put("children", node.children().stream().map(Ticket45InspectorOutputTest::toPlainNode).toList());
        return plain;
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}
