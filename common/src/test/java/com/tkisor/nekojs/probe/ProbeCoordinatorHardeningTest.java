package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.event.EventListenerToken;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.events.ProbeAssignTypeEventJS;
import com.tkisor.nekojs.probe.events.ProbeEvents;
import com.tkisor.nekojs.probe.events.ProbeModifyTypeEventJS;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Failure paths and lifecycle regressions for runProbe:
 * <ol>
 *   <li>a second concurrent runProbe is rejected (fail-fast mutex, no queueing)</li>
 *   <li>a later backend with the same outputDir is skipped (an in-place sync would delete the
 *       earlier backend's output as stale files)</li>
 *   <li>one backend failing does not affect later backends</li>
 *   <li>a throwing contributeEditorConfig does not fail the successful result (it lands in warnings)</li>
 *   <li>a Python render that fails midway keeps the old output intact (rendering does not touch disk)</li>
 *   <li>.staging/.old left over from the directory-swap era are cleaned up on the next run and leak no content</li>
 *   <li>assign_type is applied before modify_type (an explicit modify setting is not overwritten by assign)</li>
 * </ol>
 */
class ProbeCoordinatorHardeningTest {

    /** Static initialization of ScriptType needs Platform (both the Python backend and BindingCatalogEntry touch it). */
    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /* ================= fixtures ================= */

    /** Ordering test host: m1 and m2 both return {@link OrderHelper} (SYMBOL slot). */
    public static class OrderHelper {}

    public static class OrderHost {
        public OrderHelper m1() {
            return null;
        }

        public OrderHelper m2() {
            return null;
        }
    }

    /**
     * Scriptable fake backend: render returns the file map given at construction (empty by default)
     * and the default generate method writes and commits it. What contributeEditorConfig does is
     * decided by the action passed to the constructor.
     */
    private static final class FakeBackend implements ProbeBackend {
        private final String languageId;
        private final String name;
        private final boolean needIr;
        private final Function<ProbeContext, Map<String, String>> renderAction;
        private final Runnable editorConfigAction;

        FakeBackend(String languageId, String name, boolean needIr,
                    Function<ProbeContext, Map<String, String>> renderAction, Runnable editorConfigAction) {
            this.languageId = languageId;
            this.name = name;
            this.needIr = needIr;
            this.renderAction = renderAction;
            this.editorConfigAction = editorConfigAction;
        }

        @Override
        public String languageId() {
            return languageId;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean requiresIr() {
            return needIr;
        }

        @Override
        public Map<String, String> render(ProbeContext ctx) {
            return renderAction == null ? Map.of() : renderAction.apply(ctx);
        }

        @Override
        public void contributeEditorConfig(EditorConfigContributor contributor, ProbeContext ctx) {
            if (editorConfigAction != null) {
                editorConfigAction.run();
            }
        }
    }

    /** Fake backend whose render returns a single file {@code markerName → markerContent}. */
    private static FakeBackend markerBackend(String lang, String name, String markerName, String content) {
        return new FakeBackend(lang, name, false, ctx -> Map.of(markerName, content), null);
    }

    /* ================= 1. Concurrency mutex ================= */

    @Test
    void concurrentSecondRunIsRejectedWhileFirstInProgress(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstSucceeded = new AtomicBoolean(false);
        ProbeBackend blocker = new ProbeBackend() {
            @Override
            public String languageId() {
                return "blocking";
            }

            @Override
            public String name() {
                return "test";
            }

            @Override
            public Map<String, String> render(ProbeContext ctx) {
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Map.of();
            }
        };

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        Thread first = new Thread(() -> firstSucceeded.set(c.runProbe(emptySnapshot(), List.of(blocker)).get(0).success()));
        first.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS), "首个 run 应已进入 backend render");

        List<ProbeBackend.GenerateResult> rejected = c.runProbe(emptySnapshot(), List.of(blocker));
        assertEquals(1, rejected.size());
        assertFalse(rejected.get(0).success());
        assertEquals("probe already running", rejected.get(0).message());

        release.countDown();
        first.join(5_000);
        assertFalse(first.isAlive());
        assertTrue(firstSucceeded.get(), "首个 run 应正常完成且成功");
    }

    /* ================= 2. outputDir de-duplication ================= */

    @Test
    void duplicateOutputDirSkipsLaterBackend(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        FakeBackend first = markerBackend("dup", "a", "marker.txt", "from-first");
        AtomicBoolean secondRan = new AtomicBoolean(false);
        FakeBackend second = new FakeBackend("dup", "b", false, ctx -> {
            secondRan.set(true);
            return Map.of("marker.txt", "from-second");
        }, null);

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        List<ProbeBackend.GenerateResult> results = c.runProbe(emptySnapshot(), List.of(first, second));

        assertEquals(2, results.size(), "结果顺序与输入一致（跳过者也占位）");
        assertTrue(results.get(0).success());
        assertFalse(results.get(1).success());
        assertTrue(results.get(1).message().contains("duplicate output directory"), results.get(1).message());
        assertFalse(secondRan.get(), "同目录的后续 backend 不得执行（staging/swap 会吞掉先跑者产物）");
        assertEquals("from-first", Files.readString(paths.gameDir().resolve(".neko_probe").resolve("dup").resolve("marker.txt")),
                "先跑者产物必须保留");
    }

    /* ================= 3. Backend failure isolation ================= */

    @Test
    void failingBackendDoesNotAffectLaterBackends(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        FakeBackend bad = new FakeBackend("bad", "x", false, ctx -> {
            throw new IllegalStateException("boom");
        }, null);
        FakeBackend good = markerBackend("good", "x", "ok.txt", "ok");

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        List<ProbeBackend.GenerateResult> results = c.runProbe(emptySnapshot(), List.of(bad, good));

        assertFalse(results.get(0).success());
        assertTrue(results.get(0).message().contains("boom"), results.get(0).message());
        assertTrue(results.get(1).success());
        assertEquals("ok", Files.readString(paths.gameDir().resolve(".neko_probe").resolve("good").resolve("ok.txt")));
        assertEquals(paths.gameDir().resolve(".neko_probe").resolve("good"), results.get(1).outputDir(),
                "成功结果应携带输出目录");
    }

    /* ================= 4. editor-config failure does not sink generation (it goes into warnings) ================= */

    @Test
    void editorConfigFailureDoesNotFailGeneration(@TempDir Path tmp) {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        FakeBackend backend = new FakeBackend("ec", "x", false, null, () -> {
            throw new IllegalStateException("editor-config boom");
        });

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        List<ProbeBackend.GenerateResult> results = c.runProbe(emptySnapshot(), List.of(backend));

        assertEquals(1, results.size());
        assertTrue(results.get(0).success(), "editor-config 失败只降级，生成结果仍为成功");
        assertEquals(1, results.get(0).warnings().size());
        assertTrue(results.get(0).warnings().get(0).contains("editor-config contribution failed"),
                results.get(0).warnings().get(0));
        assertTrue(results.get(0).warnings().get(0).contains("editor-config boom"));
    }

    /* ================= 5/6. Python render failure and leftover recovery ================= */

    @Test
    void pythonRenderFailureKeepsOldOutput(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path out = paths.gameDir().resolve(".neko_probe").resolve("python");
        Files.createDirectories(out);
        Files.writeString(out.resolve("old-marker.txt"), "keep");

        // Poison IR: the fqn contains NUL (the JDK rejects NUL path segments on both Windows and Linux),
        // so the package path computation inside render fails. Rendering never touches disk, so the old
        // output survives and no intermediate directory is created.
        TypeDecl good = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.a.Foo");
        TypeDecl poison = new TypeDecl(TypeDecl.Kind.CLASS, null, "bad\u0000pkg.Cls");
        ProbeContext ctx = new ProbeContext.Of(emptySnapshot(), List.of(),
                ProbeConfig.defaultConfig(), paths, "python", out, List.of(good, poison), null);

        ProbeBackend.GenerateResult res = new PythonProbeBackend().generate(ctx);

        assertFalse(res.success());
        assertTrue(Files.exists(out.resolve("old-marker.txt")), "渲染失败必须完整保留旧输出");
        assertFalse(Files.exists(out.resolveSibling("python.staging")), "渲染失败不应留下中间目录");
    }

    @Test
    void legacySwapLeftoversAreCleanedUpAndNeverLeakIntoOutput(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path out = paths.gameDir().resolve(".neko_probe").resolve("python");
        // .staging/.old left behind by the directory-swap era (or by a crash during it): they are only
        // cleaned up now, never restored as the output directory. This run's render alone decides the
        // output; the old content has nothing worth restoring.
        Files.createDirectories(out.resolveSibling("python.staging"));
        Files.writeString(out.resolveSibling("python.staging").resolve("junk.txt"), "half-written");
        Files.createDirectories(out.resolveSibling("python.old"));
        Files.writeString(out.resolveSibling("python.old").resolve("stale.txt"), "old-output");

        TypeDecl good = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.a.Foo");
        ProbeContext ctx = new ProbeContext.Of(emptySnapshot(), List.of(),
                ProbeConfig.defaultConfig(), paths, "python", out, List.of(good), null);

        ProbeBackend.GenerateResult res = new PythonProbeBackend().generate(ctx);

        assertTrue(res.success(), res.message());
        assertFalse(Files.exists(out.resolveSibling("python.staging")), "遗留 staging 必须清除");
        assertFalse(Files.exists(out.resolveSibling("python.old")), "遗留 backup 必须清除");
        assertTrue(Files.exists(out.resolve("nekojs").resolve("_java").resolve("pkg").resolve("a").resolve("__init__.pyi")),
                "新产物应正常生成");
        assertFalse(Files.exists(out.resolve("stale.txt")), "遗留 backup 内容不得泄漏进新产物");
        assertEquals(out, res.outputDir());
    }

    /* ================= 7. assign_type before modify_type ================= */

    @Test
    void assignTypeAppliedBeforeModifyTypeExplicitEdit(@TempDir Path tmp) {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        String hostFqn = OrderHost.class.getName();
        String helperFqn = OrderHelper.class.getName();
        AtomicReference<List<TypeDecl>> capturedIr = new AtomicReference<>();
        FakeBackend capture = new FakeBackend("capture", "ir", true, ctx -> {
            capturedIr.set(ctx.ir());
            return Map.of();
        }, null);

        EventListenerToken<ProbeAssignTypeEventJS> assignTok = ProbeEvents.ASSIGN_TYPE.bus()
                .listen((Consumer<ProbeAssignTypeEventJS>) ev -> ev.assign(helperFqn, "number"));
        EventListenerToken<ProbeModifyTypeEventJS> modifyTok = ProbeEvents.MODIFY_TYPE.bus()
                .listen((Consumer<ProbeModifyTypeEventJS>) ev -> {
                    var editor = ev.forClass(hostFqn);
                    if (editor != null) {
                        editor.changeReturnType("m2", "boolean");
                    }
                });
        try {
            ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
            List<ProbeBackend.GenerateResult> results =
                    c.runProbe(snapshotWithBinding(OrderHost.class), List.of(capture));
            assertTrue(results.get(0).success(), results.get(0).message());
        } finally {
            ProbeEvents.ASSIGN_TYPE.bus().unregister(assignTok);
            ProbeEvents.MODIFY_TYPE.bus().unregister(modifyTok);
        }

        TypeDecl host = capturedIr.get().stream()
                .filter(d -> d.fqn.equals(hostFqn))
                .findFirst()
                .orElseThrow(() -> new AssertionError("OrderHost 不在 IR 中：" + capturedIr.get()));
        MethodDecl m1 = method(host, "m1");
        MethodDecl m2 = method(host, "m2");
        assertEquals(ApiTypeRef.primitive("number"), m1.returnType.ref,
                "未被 modify 触及的槽：assign_type 的重定向应生效");
        assertEquals(ApiTypeRef.primitive("boolean"), m2.returnType.ref,
                "modify_type 的显式设置须在 assign_type 之后应用（不被 assign 二次覆盖）");
        assertTrue(m2.returnType.overridden, "modify 的显式编辑应置 overridden");
        assertTrue(host.mutated);
    }

    private static MethodDecl method(TypeDecl host, String name) {
        return host.methods.stream()
                .filter(m -> m.name.equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("方法 " + name + " 不在 " + host.fqn + " 的 IR 中"));
    }

    /* ================= Helpers ================= */

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    private static NekoScriptCatalogSnapshot snapshotWithBinding(Class<?> javaType) {
        return new NekoScriptCatalogSnapshot(
                List.of(),
                List.of(BindingCatalogEntry.of("Host", ScriptType.SERVER, javaType, false)),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, new LinkedHashMap<>(), List.of());
    }
}
