package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestModulePreparation;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 11 AC6：<strong>缓存失败</strong>必须与准备失败、resolve/link 失败、执行失败可区分，
 * 且失败不得污染缓存。
 *
 * <p>本测试从 {@link NekoModulePreparation#prepare} 这一缓存层 Seam 观察：
 * <ul>
 *   <li>管线抛出的受检异常必须被隧道出 {@code ConcurrentHashMap.compute} 并按原类型浮出
 *       （不变成无来源的 {@code RuntimeException} 包装）；</li>
 *   <li>管线抛出的 {@code RuntimeException} 必须原样浮出；</li>
 *   <li>失败的 prepare <strong>不得</strong>在缓存里留下条目——后续修复后重试必须能成功，
 *       而不是永久返回失败或返回半初始化的模块。</li>
 * </ul>
 */
class NekoModuleCacheFailureTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** 可编程失败的测试语言编译器：仅在第 {@code failOnCall} 次调用按指定异常类型失败。 */
    private static final class FailingCompiler implements IScriptCompiler {
        private final AtomicInteger calls = new AtomicInteger();
        private final int failOnCall;
        private final boolean checked;

        FailingCompiler(int failOnCall, boolean checked) {
            this.failOnCall = failOnCall;
            this.checked = checked;
        }

        @Override
        public boolean canCompile(String extension) {
            return true;
        }

        @Override
        public String compile(Path file, String sourceCode) throws Exception {
            if (calls.incrementAndGet() == failOnCall) {
                if (checked) {
                    throw new IOException("simulated checked pipeline failure");
                }
                throw new IllegalStateException("simulated unchecked pipeline failure");
            }
            return "module.exports = 'compiled-ok';\n";
        }
    }

    private NekoModulePreparation preparationFor(IScriptCompiler compiler) {
        NekoJSPaths paths = NekoJSPaths.get();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.registerLanguage("failinglang", Set.of(".fail"), compiler);
        return TestModulePreparation.create(paths, compilers, SandboxConfig.defaultConfig());
    }

    private final List<Path> written = new ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void cleanUpWrittenFiles() throws Exception {
        // 真删文件 + 空的父目录：deleteOnExit 只在 JVM 退出时生效，会污染共享 gameDir
        TestModulePreparation.deleteFilesAndEmptyParents(written);
        written.clear();
    }

    private Path write(String name, String source) throws IOException {
        Path file = NekoJSPaths.get().testScripts().resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        written.add(file);
        return file;
    }

    /** 受检异常：按原类型（IOException）浮出，不被降级成无来源的 RuntimeException。 */
    @Test
    void checkedPipelineFailureSurfacesAsItsOriginalType() throws Exception {
        Path script = write("cache-failure-checked.fail", "anything\n");
        NekoModulePreparation preparation = preparationFor(new FailingCompiler(1, true));

        IOException failure = assertThrows(IOException.class, () -> preparation.prepare(script),
                "受检管线异常必须按原类型浮出缓存层");
        assertTrue(failure.getMessage().contains("simulated checked pipeline failure"),
                "原始失败原因必须保留：" + failure.getMessage());
    }

    /** 非受检异常：原样浮出，不被包装。 */
    @Test
    void uncheckedPipelineFailureSurfacesUnwrapped() throws Exception {
        Path script = write("cache-failure-unchecked.fail", "anything\n");
        NekoModulePreparation preparation = preparationFor(new FailingCompiler(1, false));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> preparation.prepare(script), "非受检管线异常必须原样浮出");
        assertTrue(failure.getMessage().contains("simulated unchecked pipeline failure"));
    }

    /** 缓存层失败语义：失败的 prepare 不留条目；修复后重试必须成功。 */
    @Test
    void failedPreparationDoesNotPoisonCache() throws Exception {
        Path script = write("cache-failure-retry.fail", "anything\n");
        // 第 1 次调用失败，第 2 次起成功（验证失败不污染缓存）
        NekoModulePreparation preparation = preparationFor(new FailingCompiler(1, true));

        assertThrows(IOException.class, () -> preparation.prepare(script));
        assertFalse(preparation.isCached(script), "失败的 prepare 不得在缓存里留下条目");

        NekoPreparedModule recovered = preparation.prepare(script);
        assertTrue(recovered.code().contains("compiled-ok"), "修复后重试必须能得到正确产物");
        assertTrue(preparation.isCached(script), "成功后才能入缓存");
        assertEquals("failinglang", recovered.languageId(), "language id 必须来自注册表");
    }

    /** 与准备失败的分工：缺失文件是「准备/IO 阶段」，不进入管线。 */
    @Test
    void missingFileFailsBeforePipelineAndIsNotCached() {
        NekoModulePreparation preparation = preparationFor(new FailingCompiler(1, false));
        Path missing = NekoJSPaths.get().testScripts().resolve("cache-failure-missing.fail");

        Exception failure = assertThrows(Exception.class, () -> preparation.prepare(missing));
        assertInstanceOf(IOException.class, failure, "缺失文件必须归为准备/IO 阶段失败");
        assertFalse(preparation.isCached(missing), "未读到源的路径不得进入缓存");
    }
}
