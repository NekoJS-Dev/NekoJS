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
        assertThrows(IOException.class, () -> paths.verifyInsideGameDir(outsideRootAbsPath()));
        // 软链到外部目录的路径（若宿主允许创建 symlink）
        Path link = gameDir.resolve("nekojs/link.js");
        try {
            Files.createSymbolicLink(link, gameDir.toAbsolutePath().getRoot());
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
        assertThrows(IOException.class, () -> paths.resolveGamePath(outsideRootAbsPath().toString(), cwd));
    }

    @Test
    void resolveNekoWritePathIsConfinedToNekoRoot() throws Exception {
        NekoJSPaths paths = pathsFor(gameDir);
        Path root = gameDir.resolve("nekojs");

        // 相对 nekojs 根（root 本身作为 cwd）解析；缺失路径返回规范形式
        // （real 前缀拼接缺失后缀——CI 的 tmpdir 短名与本地长名统一）
        assertEquals(gameDir.toRealPath().resolve("nekojs/cache/data.json"),
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
        assertThrows(IOException.class, () -> paths.verifyScriptSyncPath(
                outsideRootAbsPath().resolve("nekojs/server_scripts/x.js").toString()));
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

        // 缺失路径以规范形式（real 前缀 + 缺失后缀）返回，跨平台/拼写形式一致
        assertEquals(gameDir.toRealPath().resolve("nekojs/cache/new/dir/file.json"),
                paths.verifyInsideGameDirForCreate(deep));
    }

    /**
     * Windows 8.3 短名/大小写别名回归：CI runner 的 {@code java.io.tmpdir} 是
     * {@code C:\Users\RUNNER~1\...} 短名形式，而 {@code toRealPath()} 解析为长名——两种
     * 形式混用曾让所有已存在文件被误判 "Symlink escape detected"（中文用户名的 Windows
     * 玩家会以同样方式踩中）。用大小写翻转别名在同源文件系统上模拟同一失配；文件系统大小写
     * 敏感（别名不存在）时跳过。
     */
    @Test
    void verifyInsideGameDirToleratesRealFormAliasOfRoot() throws Exception {
        Path alias = aliasByCase(gameDir);
        // 注意 Path.equals 在 Windows 上大小写不敏感，必须用字符串比较确认拼写确实不同
        Assumptions.assumeTrue(!alias.toString().equals(gameDir.toString()) && Files.exists(alias),
                "filesystem is case-sensitive or alias does not resolve — nothing to simulate");

        NekoJSPaths paths = pathsFor(gameDir);
        Path script = alias.resolve("nekojs/server_scripts/alias.js");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "console.log('alias')");

        assertEquals(script.toRealPath(), paths.verifyInsideGameDir(script),
                "existing file addressed via alias form must resolve to its real form, not be rejected");
        assertEquals(script.getParent().toRealPath().resolve("not-yet.js"),
                paths.verifyInsideGameDirForCreate(alias.resolve("nekojs/server_scripts/not-yet.js")),
                "missing path addressed via alias form must pass via its existing ancestor and come back in real form");
        assertTrue(paths.isInsideScriptRoot(script),
                "alias-form script path must still be recognized as inside a script root");
    }

    /** 全路径大小写翻转：大小写不敏感文件系统上解析到同一路径，敏感系统上则不存在。 */
    private static Path aliasByCase(Path path) {
        StringBuilder sb = new StringBuilder(path.toString().length());
        for (int i = 0; i < path.toString().length(); i++) {
            char c = path.toString().charAt(i);
            sb.append(Character.isLowerCase(c) ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return Path.of(sb.toString());
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }

    /**
     * 跨平台的「gameDir 之外的绝对路径」：取 gameDir 所在文件系统根拼接。
     * Windows 上是 {@code C:\nekojs-test-outside\evil.js}，Linux CI 上是
     * {@code /nekojs-test-outside/evil.js}——两者都是绝对路径且必然在 gameDir 外。
     * （写死 {@code C:/...} 在 Linux 上是相对路径，会解析进 gameDir 导致断言失效。）
     */
    private Path outsideRootAbsPath() {
        return gameDir.toAbsolutePath().getRoot().resolve("nekojs-test-outside/evil.js");
    }
}
