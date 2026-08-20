package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProbeBackend#render} / 默认 {@code generate} 的契约回归（预发布接口重构）：
 * <ul>
 *   <li>render 纯内存——不创建/触碰输出目录</li>
 *   <li>默认 generate：staging 落盘 + 原子提交 + 结果携带 outputDir</li>
 *   <li>路径越界防护：绝对路径 / {@code ..} 段被拒绝，目录外零写盘</li>
 *   <li>异常消息兜底：null message 的异常不再产出 "backend failed: null"</li>
 *   <li>probe.toml {@code runAtStartup} 解析（默认 false）</li>
 * </ul>
 */
class ProbeBackendRenderTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /* ================= render 纯内存 ================= */

    @Test
    void pythonRenderProducesMapWithoutTouchingDisk(@TempDir Path tmp) {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path out = paths.gameDir().resolve(".neko_probe").resolve("python");

        TypeDecl foo = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.a.Foo");
        ProbeContext ctx = new ProbeContext.Of(emptySnapshot(), List.of(),
                ProbeConfig.defaultConfig(), paths, "python", out, List.of(foo), null);

        Map<String, String> files = new PythonProbeBackend().render(ctx);

        assertFalse(files.isEmpty(), "应产出 stub 文件");
        assertTrue(files.containsKey("nekojs/_java/pkg/a/__init__.pyi"));
        assertTrue(files.containsKey("nekojs/__init__.pyi"));
        assertTrue(files.containsKey("nekojs/py.typed"));
        assertFalse(Files.exists(out), "render 不得触碰输出目录");
        assertFalse(Files.exists(out.resolveSibling("python.staging")), "render 不得创建 staging");
    }

    @Test
    void defaultGenerateCommitsRenderedFilesAtomically(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path out = paths.gameDir().resolve(".neko_probe").resolve("custom");
        ProbeBackend backend = new FakeRenderBackend(Map.of(
                "a/b/file.txt", "hello",
                "top.txt", "world"));

        ProbeBackend.GenerateResult res = backend.generate(new ProbeContext.Of(
                emptySnapshot(), List.of(), ProbeConfig.defaultConfig(), paths, "custom", out));

        assertTrue(res.success(), res.message());
        assertEquals(2, res.filesGenerated());
        assertEquals(out, res.outputDir());
        assertEquals("hello", Files.readString(out.resolve("a").resolve("b").resolve("file.txt"), StandardCharsets.UTF_8));
        assertEquals("world", Files.readString(out.resolve("top.txt"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(out.resolveSibling("custom.staging")));
        assertFalse(Files.exists(out.resolveSibling("custom.old")));
    }

    /* ================= 路径越界防护 ================= */

    @Test
    void traversalPathIsRejectedWithoutWritingOutside(@TempDir Path tmp) {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path out = paths.gameDir().resolve(".neko_probe").resolve("custom");

        ProbeBackend escape = new FakeRenderBackend(Map.of(
                "../escaped.txt", "evil",
                "ok.txt", "fine"));
        ProbeBackend.GenerateResult res = escape.generate(new ProbeContext.Of(
                emptySnapshot(), List.of(), ProbeConfig.defaultConfig(), paths, "custom", out));

        assertFalse(res.success());
        assertTrue(res.message().contains("illegal output path"), res.message());
        assertFalse(Files.exists(paths.gameDir().resolve(".neko_probe").resolve("escaped.txt")),
                "越界文件绝不能落盘");
        assertFalse(Files.exists(out), "路径非法时不应提交任何输出");
    }

    @Test
    void absolutePathIsRejected(@TempDir Path tmp) {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path out = paths.gameDir().resolve(".neko_probe").resolve("custom");
        Map<String, String> evil = new LinkedHashMap<>();
        evil.put(tmp.resolve("elsewhere.txt").toAbsolutePath().toString(), "evil");

        ProbeBackend backend = new FakeRenderBackend(evil);
        ProbeBackend.GenerateResult res = backend.generate(new ProbeContext.Of(
                emptySnapshot(), List.of(), ProbeConfig.defaultConfig(), paths, "custom", out));

        assertFalse(res.success());
        assertTrue(res.message().contains("illegal output path"), res.message());
        assertFalse(Files.exists(tmp.resolve("elsewhere.txt")));
    }

    /* ================= 异常消息兜底 ================= */

    @Test
    void nullMessageExceptionFallsBackToString(@TempDir Path tmp) {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        ProbeBackend npe = new ProbeBackend() {
            @Override
            public String languageId() {
                return "npe";
            }

            @Override
            public String name() {
                return "test";
            }

            @Override
            public Map<String, String> render(ProbeContext ctx) {
                throw new NullPointerException(); // message == null
            }
        };

        ProbeBackend.GenerateResult res = npe.generate(new ProbeContext.Of(
                emptySnapshot(), List.of(), ProbeConfig.defaultConfig(), paths,
                "npe", paths.gameDir().resolve(".neko_probe").resolve("npe")));

        assertFalse(res.success());
        assertNotNull(res.message());
        assertTrue(res.message().contains(NullPointerException.class.getSimpleName()),
                "null message 应回退 toString()：" + res.message());
    }

    /* ================= runAtStartup 配置 ================= */

    @Test
    void runAtStartupDefaultsFalseAndParsesTrue(@TempDir Path tmp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tmp);
        Path cfg = paths.probeConfig();
        Files.createDirectories(cfg.getParent());

        ProbeConfig def = new ProbeConfigLoader().load(cfg);
        assertFalse(def.runAtStartup(), "runAtStartup 默认关闭（opt-in）");
        assertTrue(Files.exists(cfg), "loader 首次加载应自动创建 probe.toml");

        String content = Files.readString(cfg, StandardCharsets.UTF_8);
        assertTrue(content.contains("runAtStartup"), "默认文件应写入 runAtStartup 键与注释");
        Files.writeString(cfg, content.replace("runAtStartup = false", "runAtStartup = true"),
                StandardCharsets.UTF_8);

        ProbeConfig enabled = new ProbeConfigLoader().load(cfg);
        assertTrue(enabled.runAtStartup(), "显式 true 应被解析");
        assertTrue(enabled.enabled(), "其它键不受影响");
    }

    /* ================= 工具 ================= */

    private static final class FakeRenderBackend implements ProbeBackend {
        private final Map<String, String> files;

        FakeRenderBackend(Map<String, String> files) {
            this.files = files;
        }

        @Override
        public String languageId() {
            return "custom";
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public Map<String, String> render(ProbeContext ctx) {
            return files;
        }
    }

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }
}
