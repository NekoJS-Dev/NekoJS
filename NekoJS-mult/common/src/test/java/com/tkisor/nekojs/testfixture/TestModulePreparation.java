package com.tkisor.nekojs.testfixture;

import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePreparation;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 测试辅助：构造<strong>独立实例</strong>的 {@link NekoModulePreparation}（工单 11）。
 *
 * <p>生产路径由 {@code NekoRuntimeRoot} 持有模块 session cache；测试各自构造独立实例，
 * 不共享任何 process-wide 状态，也不再需要用反射去戳 static 缓存。
 */
public final class TestModulePreparation {

    private TestModulePreparation() {}

    public static NekoModulePreparation create(NekoJSPaths paths, ScriptCompilerRegistry compilers, SandboxConfig config) {
        return new NekoModulePreparation(new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config), paths);
    }

    public static NekoModulePreparation create(NekoJSPaths paths, SandboxConfig config) {
        return create(paths, ScriptCompilerRegistry.current(), config);
    }

    public static NekoModulePreparation create() {
        return create(NekoJSPaths.get(), SandboxConfig.defaultConfig());
    }

    /**
     * 测试清理：删除给定文件，并自底向上删除因此变空的父目录。
     *
     * <p>为什么必须真删而不是 {@code File.deleteOnExit()}：全部 common 测试共用固定 gameDir，
     * `deleteOnExit` 只在 JVM 退出时执行，测试之间不生效；残留的<strong>文件或非空目录</strong>会让
     * 既有测试（如 `ScriptReloadGenerationTest` 用 `Files.deleteIfExists` 清空脚本目录）抛
     * `DirectoryNotEmptyException`，造成跨测试污染。
     */
    public static void deleteFilesAndEmptyParents(java.util.List<Path> files) throws java.io.IOException {
        java.util.Set<Path> parents = new java.util.LinkedHashSet<>();
        for (Path file : files) {
            Files.deleteIfExists(file);
            Path parent = file.getParent();
            if (parent != null) {
                parents.add(parent);
            }
        }
        parents.stream()
                .sorted(java.util.Comparator.comparingInt((Path p) -> p.getNameCount()).reversed())
                .forEach(dir -> {
                    try {
                        Files.deleteIfExists(dir);
                    } catch (java.io.IOException ignored) {
                        // 目录非空（仍被其它测试/进程使用）→ 保留，删除本身不是本夹具的验收目标
                    }
                });
    }
}
