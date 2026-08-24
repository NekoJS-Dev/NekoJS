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
 * runProbe 的失败路径与生命周期硬化回归：
 * <ol>
 *   <li>并发第二次 runProbe 被拒（fail-fast 互斥，不排队）</li>
 *   <li>同 outputDir 的后续 backend 被跳过（就地同步会把先跑者的产物当陈旧文件删掉）</li>
 *   <li>一个 backend 失败不影响后续 backend</li>
 *   <li>contributeEditorConfig 抛异常不影响生成成功结果（进 warnings）</li>
 *   <li>Python 渲染中途失败 → 旧输出完整保留（渲染阶段不触盘）</li>
 *   <li>目录交换时代遗留的 .staging/.old 在下一次运行被清理，且内容不泄漏</li>
 *   <li>assign_type 先于 modify_type 应用（modify 的显式设置不被 assign 二次覆盖）</li>
 * </ol>
 */
class ProbeCoordinatorHardeningTest {

    /** ScriptType 静态初始化需要 Platform（Python backend / BindingCatalogEntry 均触及）。 */
    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /* ================= fixtures ================= */

    /** 顺序测试宿主：m1/m2 均返回 {@link OrderHelper}（SYMBOL 槽）。 */
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
     * 可编排的假 backend：render 返回构造时给定的文件映射（默认空），由接口默认 generate
     * 统一落盘/提交；contributeEditorConfig 行为由构造传入的动作决定。
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

    /** render 返回 {@code markerName → markerContent} 单文件的假 backend。 */
    private static FakeBackend markerBackend(String lang, String name, String markerName, String content) {
        return new FakeBackend(lang, name, false, ctx -> Map.of(markerName, content), null);
    }

    /* ================= 1. 并发互斥 ================= */

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

    /* ================= 2. outputDir 去重 ================= */

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

    /* ================= 3. backend 失败隔离 ================= */

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

    /* ================= 4. editor-config 失败不拖垮生成结果（进 warnings） ================= */

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

    /* ================= 5/6. Python 渲染失败与残留恢复 ================= */

    @Test
    void pythonRenderFailureKeepsOldOutput(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path out = paths.gameDir().resolve(".neko_probe").resolve("python");
        Files.createDirectories(out);
        Files.writeString(out.resolve("old-marker.txt"), "keep");

        // 毒丸 IR：fqn 含 NUL（JDK 在 Windows/Linux 均拒绝 NUL 路径段）→ render 内包路径计算失败。
        // 渲染阶段不触盘：旧输出天然保留，且不应产生任何中间目录。
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
        // 目录交换时代（或其崩溃）遗留的 .staging/.old：现在只清理，不再恢复成输出目录——
        // 产物完全由本次 render 决定，旧内容没有回填价值
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

    /* ================= 7. assign_type 先于 modify_type ================= */

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

    /* ================= 工具 ================= */

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    private static NekoScriptCatalogSnapshot snapshotWithBinding(Class<?> javaType) {
        return new NekoScriptCatalogSnapshot(
                List.of(),
                List.of(BindingCatalogEntry.of("Host", ScriptType.SERVER, javaType, false)),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, new LinkedHashMap<>(), List.of());
    }
}
