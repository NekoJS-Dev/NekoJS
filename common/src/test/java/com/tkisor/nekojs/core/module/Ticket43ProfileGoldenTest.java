package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.ui.InspectorNode;
import com.tkisor.nekojs.api.ui.InspectorSnapshots;
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

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ticket43ProfileGoldenTest {
    @TempDir
    Path gameDir;
    private Context context;
    private Value fixture;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        NekoJSPaths paths = constructor.newInstance(gameDir);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        SandboxConfig sandbox = new SandboxConfig(false, false, false, false, true, true, true, true,
                30, 0, 0, SandboxConfig.PACK_SYNC_OFF, false, false);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
        compilers.register(NekoTsxLanguagePlugin.INSTANCE);
        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, sandbox),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        IOAccess io = IOAccess.newBuilder().fileSystem(new NekoJSFileSystem(paths.root(),
                new SandboxPolicy(sandbox, paths), paths, cache)).build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(io).build();
        NekoModuleResolver resolver = new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                new ScriptFilePolicy(compilers));
        NekoNodeModuleInstaller.install(context, ScriptType.TEST, resolver, paths,
                new DefaultErrorTracker(paths, sandbox), sandbox, cache);
        try (var input = getClass().getResourceAsStream("/nekojs/language-ts-examples/tsx/ui-profile-golden.tsx")) {
            assertNotNull(input, "the profile fixture must exist");
            Files.write(paths.serverScripts().resolve("src/ui-profile-golden.tsx"), input.readAllBytes());
        }
        NekoScriptModuleLoaderHost host = (NekoScriptModuleLoaderHost) context.getBindings("js")
                .getMember("__nekoScriptModuleLoaderHost").asHostObject();
        fixture = (Value) host.loadEntry("./server_scripts/src/ui-profile-golden.tsx");
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void exactOverridesNearestLowerThenBaseWhileFutureOverridesUseTheDefault() {
        Value proof = fixture.getMember("precedenceProof").execute();
        assertEquals(List.of(11.0, 22.0, 22.0, 44.0, 44.0, 44.0), widths(proof, "base-exact-lower"));
        assertEquals(List.of(0.0, 0.0, 0.0, 64.0, 64.0, 64.0), widths(proof, "future-fallback"));
        assertEquals(List.of(13.0, 13.0, 13.0, 13.0, 13.0, 13.0), widths(proof, "base-only"));
        assertEquals(List.of(17.0, 23.0, 31.0, 31.0, 53.0, 53.0), widths(proof, "nearest-lower"));
    }

    @Test
    void resizingAllProfilesPublishesLayoutsWithoutRerenderingOrRebuildingHostNodes() {
        Value proof = fixture.getMember("precedenceProof").execute();
        Value before = proof.getMember("before");
        Value after = proof.getMember("after");
        assertEquals(1, before.getMember("renders").asInt());
        assertEquals(1, after.getMember("renders").asInt());
        assertEquals(1, after.getMember("counts").getMember("commits").asInt());
        assertEquals(5, after.getMember("counts").getMember("creates").asInt());
        assertEquals(6, after.getMember("counts").getMember("layouts").asInt());
        assertEquals(before.getMember("roots").toString(), after.getMember("roots").toString());
        for (int index = 0; index < 6; index++) {
            Value offered = proof.getMember("offered").getArrayElement(index);
            assertEquals(index + 1, offered.getMember("snapshot").getMember("profile").asInt());
            assertEquals(index > 0, offered.getMember("publish").asBoolean());
            if (index > 0) assertTrue(proof.getMember("resizeResults").getArrayElement(index - 1).asBoolean());
        }
    }

    @Test
    void percentageDimensionsAndResponsiveLimitsClampAgainstAvailableLogicalSpace() {
        Value proof = fixture.getMember("geometryProof").execute();
        assertEquals(List.of(80.0, 160.0, 200.0, 160.0, 213.5, 300.0), widths(proof, "percent-clamp"));
        assertEquals(List.of(25.0, 80.0, 120.0, 160.0, 213.5, 320.0), widths(proof, "percent-limits"));
        assertEquals(List.of(20.0, 20.0, 20.0, 20.0, 20.0, 20.0), widths(proof, "logical-length"));
        var first = InspectorSnapshots.read("ticket43-geometry", "fake-host", proof.getMember("snapshots").getArrayElement(0));
        InspectorNode clamped = find(first.nodes(), "percent-clamp");
        assertEquals(50.0, clamped.rect().height());
        InspectorNode overflowing = find(first.nodes(), "percent-overflow");
        assertEquals(120.0, overflowing.rect().width());
        assertEquals(100.0, overflowing.clip().width());
        assertEquals(20.0, overflowing.overflow().right());
        assertEquals(List.of("percent-overflow:overflow-right"), first.diagnostics());
        var last = InspectorSnapshots.read("ticket43-geometry", "fake-host", proof.getMember("snapshots").getArrayElement(5));
        assertEquals(120.0, find(last.nodes(), "percent-clamp").rect().height());
    }

    @Test
    void percentageMinimumAndMaximumHeightsUseParentLogicalHeight() {
        Value proof = fixture.getMember("geometryProof").execute();
        List<Double> heights = new ArrayList<>();
        Value snapshots = proof.getMember("snapshots");
        for (long index = 0; index < snapshots.getArraySize(); index++) {
            var snapshot = InspectorSnapshots.read("ticket43-geometry", "fake-host", snapshots.getArrayElement(index));
            heights.add(find(snapshot.nodes(), "percent-limits").rect().height());
        }
        assertEquals(List.of(25.0, 45.0, 60.0, 90.0, 120.0, 180.0), heights);
    }

    @Test
    void publicProfileSelectionUsesSafeContentLowerAxisAndCapabilityCap() {
        Value records = fixture.getMember("profileProof").execute();
        List<Integer> profiles = new ArrayList<>();
        for (long index = 0; index < records.getArraySize(); index++) {
            profiles.add(records.getArrayElement(index).getMember("profile").asInt());
        }
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 1, 1, 3, 2, 3, 2, 2), profiles);
    }

    @Test
    void invalidProfilesRatiosDimensionsAndLimitsFailBeforeAnyHostCommit() {
        Value records = fixture.getMember("invalidProof").execute();
        assertEquals(26, records.getArraySize());
        for (long index = 0; index < records.getArraySize(); index++) {
            Value record = records.getArrayElement(index);
            String name = record.getMember("name").asString();
            assertTrue(!record.getMember("message").isNull(), name + " must be rejected");
            if (record.getMember("boundary").asString().equals("createRoot")) {
                assertEquals(0, record.getMember("counts").getMember("commits").asInt(), name);
                assertEquals(0, record.getMember("counts").getMember("creates").asInt(), name);
                Value diagnostic = record.getMember("diagnostics").getArrayElement(0);
                assertEquals("layout", diagnostic.getMember("phase").asString(), name);
                assertEquals("invalid-" + name, diagnostic.getMember("rootId").asString(), name);
                assertEquals(record.getMember("message").asString(), diagnostic.getMember("message").asString(), name);
            }
        }
    }

    @Test
    void rejectedResizeKeepsPriorLayoutAndHostIdentityThenAcceptsAValidProfile() {
        Value proof = fixture.getMember("rejectedResizeProof").execute();
        assertEquals(false, proof.getMember("rejected").asBoolean());
        assertEquals(true, proof.getMember("recovered").asBoolean());
        assertEquals(json(proof.getMember("before")), json(proof.getMember("afterRejection")));
        assertEquals(json(proof.getMember("before")), json(proof.getMember("afterInvalid")));
        assertEquals(proof.getMember("beforeRoots").toString(), proof.getMember("afterRoots").toString());
        assertEquals("viewport.width must be a finite positive number", proof.getMember("invalidMessage").asString());
        assertEquals(1, proof.getMember("renders").asInt());
        assertEquals(1, proof.getMember("counts").getMember("commits").asInt());
        assertEquals(1, proof.getMember("counts").getMember("creates").asInt());
        assertEquals(2, proof.getMember("counts").getMember("layouts").asInt());
        assertEquals(2, proof.getMember("recoveredSnapshot").getMember("profile").asInt());
        Value diagnostic = proof.getMember("diagnostics").getArrayElement(0);
        assertEquals("layout", diagnostic.getMember("phase").asString());
        assertEquals("ticket43-rejected-resize", diagnostic.getMember("rootId").asString());
        assertEquals("UI resize layout failed: width min must not exceed max", diagnostic.getMember("message").asString());
    }

    @Test
    void percentageConstraintsApplyBeforeSiblingPlacementAlignmentAndAnchoring() {
        Value snapshots = fixture.getMember("arrangementProof").execute();
        var row = InspectorSnapshots.read("ticket43-arrangement", "fake-host", snapshots.getArrayElement(0));
        assertRect(find(row.nodes(), "constrained-row"), 115, 70, 45, 20);
        assertRect(find(row.nodes(), "next-row"), 170, 80, 20, 10);
        var column = InspectorSnapshots.read("ticket43-arrangement", "fake-host", snapshots.getArrayElement(1));
        assertRect(find(column.nodes(), "constrained-column"), 145, 50, 45, 20);
        assertRect(find(column.nodes(), "next-column"), 170, 80, 20, 10);
        var stack = InspectorSnapshots.read("ticket43-arrangement", "fake-host", snapshots.getArrayElement(2));
        assertRect(find(stack.nodes(), "constrained-stack"), 145, 70, 45, 20);
    }

    private static void assertRect(InspectorNode node, double x, double y, double width, double height) {
        assertEquals(x, node.rect().x());
        assertEquals(y, node.rect().y());
        assertEquals(width, node.rect().width());
        assertEquals(height, node.rect().height());
    }

    @Test
    void publicProfileLayoutsAndDiagnosticsMatchReadOnlyGolden() throws Exception {
        String actual = canonicalProof();
        Path actualPath = Path.of("build/reports/ui/profile-layout-actual.txt");
        Files.createDirectories(actualPath.getParent());
        Files.writeString(actualPath, actual, StandardCharsets.UTF_8);
        if (Boolean.getBoolean("nekojs.ui.profile.golden.regenerate")) {
            Path goldenPath = Path.of("src/test/resources/nekojs/ui/profile-layout-golden.txt");
            Files.createDirectories(goldenPath.getParent());
            Files.writeString(goldenPath, actual, StandardCharsets.UTF_8);
            return;
        }
        try (var input = getClass().getResourceAsStream("/nekojs/ui/profile-layout-golden.txt")) {
            assertNotNull(input, "Run :common:regenerateUiProfileGolden explicitly, then review the new baseline");
            String golden = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertEquals(golden, actual, "Profile output drift: inspect build/reports/ui/profile-layout-actual.txt; ordinary tests never update the golden");
        }
    }

    private String canonicalProof() {
        StringBuilder actual = new StringBuilder("--- public profile selection ---\n");
        appendRecords(actual, fixture.getMember("profileProof").execute());
        Value precedence = fixture.getMember("precedenceProof").execute();
        actual.append("--- precedence host state ---\n")
                .append("before=").append(json(precedence.getMember("before"))).append('\n')
                .append("after=").append(json(precedence.getMember("after"))).append('\n')
                .append("resizeResults=").append(json(precedence.getMember("resizeResults"))).append('\n');
        Value offered = precedence.getMember("offered");
        for (long index = 0; index < offered.getArraySize(); index++) {
            Value layout = offered.getArrayElement(index);
            actual.append("offer profile=").append(layout.getMember("snapshot").getMember("profile").asInt())
                    .append(" publish=").append(layout.getMember("publish").asBoolean()).append('\n');
        }
        appendSnapshots(actual, "precedence", "ticket43-precedence", precedence.getMember("snapshots"));
        Value geometry = fixture.getMember("geometryProof").execute();
        appendSnapshots(actual, "percentages and limits", "ticket43-geometry", geometry.getMember("snapshots"));
        actual.append("--- invalid inputs ---\n");
        appendRecords(actual, fixture.getMember("invalidProof").execute());
        Value rejected = fixture.getMember("rejectedResizeProof").execute();
        actual.append("--- rejected resize ---\n")
                .append("rejected=").append(rejected.getMember("rejected").asBoolean())
                .append(" recovered=").append(rejected.getMember("recovered").asBoolean())
                .append(" renders=").append(rejected.getMember("renders").asInt())
                .append(" counts=").append(json(rejected.getMember("counts"))).append('\n')
                .append("beforeRoots=").append(json(rejected.getMember("beforeRoots")))
                .append(" afterRoots=").append(json(rejected.getMember("afterRoots"))).append('\n')
                .append("invalidViewport=").append(rejected.getMember("invalidMessage").asString()).append('\n');
        appendRecords(actual, rejected.getMember("diagnostics"));
        for (String state : List.of("before", "afterRejection", "afterInvalid", "recoveredSnapshot")) {
            actual.append("state=").append(state).append('\n').append(InspectorSnapshots.canonical(
                    InspectorSnapshots.read("ticket43-rejected-resize", "fake-host", rejected.getMember(state))));
        }
        return actual.toString();
    }

    private void appendRecords(StringBuilder actual, Value records) {
        for (long index = 0; index < records.getArraySize(); index++) {
            actual.append(json(records.getArrayElement(index))).append('\n');
        }
    }

    private static void appendSnapshots(StringBuilder actual, String scene, String rootId, Value snapshots) {
        for (long index = 0; index < snapshots.getArraySize(); index++) {
            actual.append("--- ").append(scene).append(" profile ").append(index + 1).append(" ---\n")
                    .append(InspectorSnapshots.canonical(InspectorSnapshots.read(rootId, "fake-host", snapshots.getArrayElement(index))));
        }
    }

    private String json(Value value) {
        return context.eval("js", "JSON.stringify").execute(value).asString();
    }

    private static List<Double> widths(Value proof, String nodeId) {
        List<Double> widths = new ArrayList<>();
        Value snapshots = proof.getMember("snapshots");
        for (long index = 0; index < snapshots.getArraySize(); index++) {
            var snapshot = InspectorSnapshots.read("ticket43-precedence", "fake-host", snapshots.getArrayElement(index));
            widths.add(find(snapshot.nodes(), nodeId).rect().width());
        }
        return widths;
    }

    private static InspectorNode find(List<InspectorNode> nodes, String id) {
        for (InspectorNode node : nodes) {
            if (id.equals(node.id())) return node;
            InspectorNode nested = find(node.children(), id);
            if (nested != null) return nested;
        }
        return null;
    }
}
