package com.tkisor.nekojs.core.fs;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NekoJSPaths} 路径校验聚焦测试：VFS 安全边界（gameDir / nekojs 根限制、相对解析、
 * 脚本同步白名单、symlink 逃逸拒绝）。
 */
class NekoJSPathsTest {

    @TempDir
    Path gameDir;

    @Test
    void verifyInsideGameDirAllowsRelativeWithinRoot() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);
        Path script = gameDir.resolve("nekojs/server_scripts/a.js");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "console.log('x')");

        assertEquals(script.toRealPath(), paths.verifyInsideGameDir(script));
        assertEquals(script.toRealPath(), paths.verifyInsideGameDir(gameDir.resolve("nekojs/../nekojs/server_scripts/a.js")));
    }

    @Test
    void verifyInsideGameDirRejectsTraversalAndAbsoluteOutside() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);

        assertThrows(IOException.class, () -> paths.verifyInsideGameDir(gameDir.resolve("../outside.js")));
        assertThrows(IOException.class, () -> paths.verifyInsideGameDir(Path.of("C:/Windows/system32/evil.js")));
        // 软链到外部目录的路径（若宿主允许创建 symlink）
        Path link = gameDir.resolve("nekojs/link.js");
        try {
            Files.createSymbolicLink(link, Path.of("C:/Windows/win.ini"));
            Assumptions.assumeTrue(Files.exists(link), "symlink creation unsupported, skipping");
            assertThrows(IOException.class, () -> paths.verifyInsideGameDir(link),
                    "symlink escape must be rejected");
        } catch (UnsupportedOperationException | IOException ignored) {
            // 宿主不允许创建 symlink：跳过逃逸断言
        }
    }

    @Test
    void resolveGamePathResolvesAgainstWorkingDirectory() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);
        Path script = gameDir.resolve("nekojs/server_scripts/b.js");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "console.log('y')");

        Path cwd = gameDir.resolve("nekojs/server_scripts");
        assertEquals(script.toRealPath(), paths.resolveGamePath("b.js", cwd));
        assertEquals(script.toRealPath(), paths.resolveGamePath("./b.js", cwd));
        assertEquals(script.toRealPath(), paths.resolveGamePath("nekojs/server_scripts/b.js", gameDir));
        // 越界：server_scripts 上溯三级越过 gameDir
        assertThrows(IOException.class, () -> paths.resolveGamePath("../../../outside.js", cwd));
        assertThrows(IOException.class, () -> paths.resolveGamePath("C:/Windows/system32/evil.js", cwd));
    }

    @Test
    void resolveNekoWritePathIsConfinedToNekoRoot() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);
        Path root = gameDir.resolve("nekojs");

        // 相对 nekojs 根（root 本身作为 cwd）解析
        assertEquals(root.resolve("cache/data.json").normalize().toAbsolutePath(),
                paths.resolveNekoWritePath("cache/data.json", root));
        // 越出 nekojs 根（到 gameDir 直接写文件）拒绝
        assertThrows(IOException.class, () -> paths.resolveNekoWritePath("../data.json", root));
    }

    @Test
    void verifyScriptSyncPathEnforcesScriptRootWhitelist() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);
        Path script = gameDir.resolve("nekojs/server_scripts/sync.js");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "console.log('sync')");

        assertEquals(script.toRealPath(), paths.verifyScriptSyncPath("server_scripts/sync.js"));
        // 绝对路径 / .. 拒绝
        assertThrows(IOException.class, () -> paths.verifyScriptSyncPath("C:/nekojs/server_scripts/x.js"));
        assertThrows(IOException.class, () -> paths.verifyScriptSyncPath("../server_scripts/x.js"));
        // 非脚本扩展名拒绝
        assertThrows(IOException.class, () -> paths.verifyScriptSyncPath("server_scripts/x.txt"));
        // 脚本根之外（nekojs/config 等）拒绝
        assertThrows(IOException.class, () -> paths.verifyScriptSyncPath("config/nekojs-engine.toml"));
    }

    @Test
    void verifyInsideGameDirForCreateAllowsDeepMissingPaths() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);
        Path deep = gameDir.resolve("nekojs/cache/new/dir/file.json");

        assertEquals(deep.normalize().toAbsolutePath(), paths.verifyInsideGameDirForCreate(deep));
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}
