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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * runProbe 的失败路径与生命周期硬化回归（此前 10 项行为缺口中的 6 项）：
 * <ol>
 *   <li>并发第二次 runProbe 被拒（fail-fast 互斥，不排队）</li>
 *   <li>同 outputDir 的后续 backend 被跳过（staging/swap 不会互相吞产物）</li>
 *   <li>一个 backend 失败不影响后续 backend</li>
 *   <li>contributeEditorConfig 抛异常不影响生成成功结果</li>
 *   <li>Python 生成中途失败 → 旧输出完整保留、staging 清除</li>
 *   <li>崩溃残留（staging/backup）在下一次运行被恢复/清理</li>
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

    /** 可编排的假 backend：generate / contributeEditorConfig 行为由构造传入的动作决定。 */
    private static final class FakeBackend implements ProbeBackend {
        interface GenerateAction {
            void run(ProbeContext ctx) throws Exception;
        }

        private final String languageId;
        private final String name;
        private final boolean needIr;
        private final GenerateAction generateAction;
        private final Runnable editorConfigAction;

        FakeBackend(String languageId, String name, boolean needIr,
                    GenerateAction generateAction, Runnable editorConfigAction) {
            this.languageId = languageId;
            this.name = name;
            this.needIr = needIr;
            this.generateAction = generateAction;
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
        public ProbeGenerator.GenerateResult generate(ProbeContext ctx) {
            if (generateAction != null) {
                try {
                    generateAction.run(ctx);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return ProbeGenerator.GenerateResult.success(0, 0);
        }

        @Override
        public void contributeEditorConfig(EditorConfigContributor contributor, ProbeContext ctx) {
            if (editorConfigAction != null) {
                editorConfigAction.run();
            }
        }
    }

    private static FakeBackend ok(String lang, String name, FakeBackend.GenerateAction action) {
        return new FakeBackend(lang, name, false, action, null);
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
            public ProbeGenerator.GenerateResult generate(ProbeContext ctx) {
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ProbeGenerator.GenerateResult.success(0, 0);
            }
        };

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        Thread first = new Thread(() -> firstSucceeded.set(c.runProbe(emptySnapshot(), List.of(blocker)).get(0).success()));
        first.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS), "首个 run 应已进入 backend generate");

        List<ProbeGenerator.GenerateResult> rejected = c.runProbe(emptySnapshot(), List.of(blocker));
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
        FakeBackend first = ok("dup", "a", ctx -> {
            try {
                Files.createDirectories(ctx.languageDir());
                Files.writeString(ctx.languageDir().resolve("marker.txt"), "from-first");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        AtomicBoolean secondRan = new AtomicBoolean(false);
        FakeBackend second = ok("dup", "b", ctx -> secondRan.set(true));

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        List<ProbeGenerator.GenerateResult> results = c.runProbe(emptySnapshot(), List.of(first, second));

        assertEquals(2, results.size(), "结果顺序与输入一致（跳过者也占位）");
        assertTrue(results.get(0).success());
        assertFalse(results.get(1).success());
        assertTrue(results.get(1).message().contains("duplicate output directory"), results.get(1).message());
        assertFalse(secondRan.get(), "同目录的后续 backend 不得执行（staging/swap 会吞掉先跑者产物）");
        assertTrue(Files.exists(paths.gameDir().resolve(".neko_probe").resolve("dup").resolve("marker.txt")),
                "先跑者产物必须保留");
    }

    /* ================= 3. backend 失败隔离 ================= */

    @Test
    void failingBackendDoesNotAffectLaterBackends(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        FakeBackend bad = ok("bad", "x", ctx -> {
            throw new IllegalStateException("boom");
        });
        FakeBackend good = ok("good", "x", ctx -> {
            try {
                Files.createDirectories(ctx.languageDir());
                Files.writeString(ctx.languageDir().resolve("ok.txt"), "ok");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        List<ProbeGenerator.GenerateResult> results = c.runProbe(emptySnapshot(), List.of(bad, good));

        assertFalse(results.get(0).success());
        assertTrue(results.get(0).message().contains("boom"), results.get(0).message());
        assertTrue(results.get(1).success());
        assertTrue(Files.exists(paths.gameDir().resolve(".neko_probe").resolve("good").resolve("ok.txt")));
    }

    /* ================= 4. editor-config 失败不拖垮生成结果（进 warnings） ================= */

    @Test
    void editorConfigFailureDoesNotFailGeneration(@TempDir Path tmp) {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        FakeBackend backend = new FakeBackend("ec", "x", false, null, () -> {
            throw new IllegalStateException("editor-config boom");
        });

        ProbeCoordinator c = new ProbeCoordinator(paths, ProbeExternalArtifacts.NONE);
        List<ProbeGenerator.GenerateResult> results = c.runProbe(emptySnapshot(), List.of(backend));

        assertEquals(1, results.size());
        assertTrue(results.get(0).success(), "editor-config 失败只降级，生成结果仍为成功");
        assertEquals(1, results.get(0).warnings().size());
        assertTrue(results.get(0).warnings().get(0).contains("editor-config contribution failed"),
                results.get(0).warnings().get(0));
        assertTrue(results.get(0).warnings().get(0).contains("editor-config boom"));
    }

    /* ================= 5/6. Python staging 回滚与残留恢复 ================= */

    @Test
    void pythonMidGenerationFailureKeepsOldOutput(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path out = paths.gameDir().resolve(".neko_probe").resolve("python");
        Files.createDirectories(out);
        Files.writeString(out.resolve("old-marker.txt"), "keep");

        // 毒丸 IR：fqn 含 NUL（JDK 在 Windows/Linux 均拒绝 NUL 路径段）→ 包目录创建中途失败
        TypeDecl good = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.a.Foo");
        TypeDecl poison = new TypeDecl(TypeDecl.Kind.CLASS, null, "bad\u0000pkg.Cls");
        ProbeContext ctx = new ProbeContext.Of(emptySnapshot(), List.of(),
                ProbeConfig.defaultConfig(), paths, "python", out, List.of(good, poison), null);

        ProbeGenerator.GenerateResult res = new PythonProbeBackend().generate(ctx);

        assertFalse(res.success());
        assertTrue(Files.exists(out.resolve("old-marker.txt")), "生成中途失败必须完整保留旧输出");
        assertFalse(Files.exists(out.resolveSibling("python.staging")), "staging 半成品必须被清除");
    }

    @Test
    void stagingLeftoversRecoveredAndCleanedAfterSuccessfulRun(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path out = paths.gameDir().resolve(".neko_probe").resolve("python");
        // 模拟上次进程崩溃的残留：半成品 staging + 停留在 backup 的旧产物（outputDir 缺失）
        Files.createDirectories(out.resolveSibling("python.staging"));
        Files.writeString(out.resolveSibling("python.staging").resolve("junk.txt"), "half-written");
        Files.createDirectories(out.resolveSibling("python.old"));
        Files.writeString(out.resolveSibling("python.old").resolve("stale.txt"), "old-output");

        TypeDecl good = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.a.Foo");
        ProbeContext ctx = new ProbeContext.Of(emptySnapshot(), List.of(),
                ProbeConfig.defaultConfig(), paths, "python", out, List.of(good), null);

        ProbeGenerator.GenerateResult res = new PythonProbeBackend().generate(ctx);

        assertTrue(res.success(), res.message());
        assertFalse(Files.exists(out.resolveSibling("python.staging")), "成功运行后 staging 必须已提交/清除");
        assertFalse(Files.exists(out.resolveSibling("python.old")), "成功运行后 backup 必须删除");
        assertTrue(Files.exists(out.resolve("nekojs").resolve("_java").resolve("pkg").resolve("a").resolve("__init__.pyi")),
                "新产物应正常生成");
        assertFalse(Files.exists(out.resolve("stale.txt")), "崩溃残留的 backup 内容不得泄漏进新产物");
    }

    /* ================= 7. assign_type 先于 modify_type ================= */

    @Test
    void assignTypeAppliedBeforeModifyTypeExplicitEdit(@TempDir Path tmp) {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        String hostFqn = OrderHost.class.getName();
        String helperFqn = OrderHelper.class.getName();
        AtomicReference<List<TypeDecl>> capturedIr = new AtomicReference<>();
        FakeBackend capture = new FakeBackend("capture", "ir", true, ctx -> capturedIr.set(ctx.ir()), null);

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
            List<ProbeGenerator.GenerateResult> results =
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
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    private static NekoScriptCatalogSnapshot snapshotWithBinding(Class<?> javaType) {
        return new NekoScriptCatalogSnapshot(
                List.of(),
                List.of(BindingCatalogEntry.of("Host", ScriptType.SERVER, javaType, false)),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }
}
