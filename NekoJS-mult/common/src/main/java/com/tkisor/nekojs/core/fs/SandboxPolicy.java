package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.core.config.SandboxConfig;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;

/**
 * 脚本可触达文件系统写策略的唯一真相（W7/A6，审计 §3-23/24）。
 *
 * <p>此前两层各判各的：Graal {@link NekoJSFileSystem} 写路径无条件
 * {@code verifyInsideGameDirForCreate}（{@code allowFsWriteOutsideNekojs=false} 也能写
 * {@code mods/}），Node shim（{@code NekoNodeFS}）才按配置收敛；且两层都允许写
 * {@code nekojs/config/}——也就是定义沙箱自身的 {@code engine.toml}（脚本写
 * {@code allowReflection = true}，下次启动沙箱即失效）。
 *
 * <p>两层现在都经本类裁决：
 * <ol>
 *   <li>{@code nekojs/config/} 整目录与旧位置 {@code <gameDir>/config/nekojs-engine.toml}
 *       永久拒写拒删——沙箱定义文件不能被沙箱内代码改写。宿主侧配置物化/probe 编辑器文件
 *       由宿主直连 {@link java.nio.file.Files}，不经过本类。</li>
 *   <li>{@code allowFsWriteOutsideNekojs=false}：写/删只允许 neko 根内；{@code true}：
 *       允许 gameDir 内（受保护配置仍然拒绝）。</li>
 * </ol>
 */
public final class SandboxPolicy {

    private final NekoJSPaths paths;
    private final boolean allowWriteOutsideNekojs;
    private final Path protectedConfigDir;
    private final Path protectedLegacyEngineConfig;

    public SandboxPolicy(SandboxConfig config, NekoJSPaths paths) {
        this.paths = paths;
        this.allowWriteOutsideNekojs = config.allowFsWriteOutsideNekojs();
        this.protectedConfigDir = NekoJSPaths.canonicalForm(paths.config());
        this.protectedLegacyEngineConfig = NekoJSPaths.canonicalForm(paths.legacyEngineConfig());
    }

    /**
     * 写入目标裁决（新建或覆盖）。Graal FileSystem 层用：路径已是解析后的绝对形式。
     *
     * @throws AccessDeniedException 路径越出写作用域，或命中受保护的引擎配置
     */
    public Path resolveWrite(Path path) throws IOException {
        Path target = allowWriteOutsideNekojs
                ? paths.verifyInsideGameDirForCreate(path)
                : paths.verifyInsideNekoRootForCreate(path);
        return rejectProtected(target);
    }

    /** 删除/改名目标裁决（存在路径变体）。 */
    public Path resolveWriteExisting(Path path) throws IOException {
        Path target = allowWriteOutsideNekojs
                ? paths.verifyInsideGameDir(path)
                : paths.verifyInsideNekoRoot(path);
        return rejectProtected(target);
    }

    /** 写入目标裁决（Node shim 层用：原始字符串 + 相对当前工作目录解析）。 */
    public Path resolveWrite(String path, Path currentWorkingDirectory) throws IOException {
        Path target = allowWriteOutsideNekojs
                ? paths.resolveGamePathForCreate(path, currentWorkingDirectory)
                : paths.resolveNekoWritePathForCreate(path, currentWorkingDirectory);
        return rejectProtected(target);
    }

    /** 删除/改名目标裁决（Node shim 层，存在路径变体）。 */
    public Path resolveWriteExisting(String path, Path currentWorkingDirectory) throws IOException {
        Path target = allowWriteOutsideNekojs
                ? paths.resolveGamePath(path, currentWorkingDirectory)
                : paths.resolveNekoWritePath(path, currentWorkingDirectory);
        return rejectProtected(target);
    }

    /** 是否命中「定义沙箱自身的文件」：nekojs/config/ 目录或旧版引擎配置单文件。 */
    public boolean isProtectedConfigPath(Path path) {
        Path canonical = NekoJSPaths.canonicalForm(path);
        return canonical.startsWith(protectedConfigDir) || canonical.equals(protectedLegacyEngineConfig);
    }

    private Path rejectProtected(Path target) throws AccessDeniedException {
        if (isProtectedConfigPath(target)) {
            throw new AccessDeniedException(target.toString(), null,
                    "engine configuration files cannot be modified from scripts (they define the sandbox itself)");
        }
        return target;
    }
}
