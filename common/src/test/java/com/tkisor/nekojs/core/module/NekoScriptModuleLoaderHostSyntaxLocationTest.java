package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CJS 语法错误的定位回填：script-loader.js 的 new Function 编译失败时，
 * 异常的 SourceSection 指向 internal/script-loader.js（内部帧），真实行列丢失。
 * {@link NekoScriptModuleLoaderHost} 应通过 context.parse 重解析取回位置，
 * 包成 {@link NekoEsmLinkException} 诊断（file/line/column 指向用户脚本）。
 */
class NekoScriptModuleLoaderHostSyntaxLocationTest {
    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private Context context;
    private NekoScriptModuleLoaderHost host;

    @BeforeEach
    void setUp() throws IOException {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = NekoJSPaths.fromGameDir(gameDir);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        context = Context.newBuilder("js").allowAllAccess(true).build();
        host = new NekoScriptModuleLoaderHost(context, new NekoModuleResolver(paths, ScriptFilePolicy.legacyRuntime()), paths);
        context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", host);
        try (var in = getClass().getResourceAsStream("/nekojs/node/internal/script-loader.js")) {
            assertNotNull(in, "script-loader.js must be on the test classpath");
            String loader = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            context.eval(Source.newBuilder("js", loader, "nekojs/node/internal/script-loader.js").build());
        }
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void syntaxErrorCarriesUserFileLineAndColumn() throws IOException {
        Path broken = paths.serverScripts().resolve("src/broken.js");
        Files.writeString(broken, """
                const ok = 1
                const list = [1, 2, 3]
                const } = broken
                """);

        IOException error = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/broken.js"));

        NekoEsmLinkException link = assertInstanceOf(NekoEsmLinkException.class, error);
        NekoEsmDiagnostic diagnostic = link.diagnostic();
        assertEquals(broken.toAbsolutePath().normalize(), diagnostic.file().toAbsolutePath().normalize());
        assertEquals(3, diagnostic.line());
        assertEquals(7, diagnostic.column());
        assertTrue(diagnostic.message().contains("SyntaxError"), "message was: " + diagnostic.message());
    }

    @Test
    void validModuleStillLoads() throws IOException {
        Path fine = paths.serverScripts().resolve("src/fine.js");
        Files.writeString(fine, "module.exports = 41 + 1\n");

        Object exports = host.loadEntry("./server_scripts/src/fine.js");

        assertNotNull(exports);
    }

    @Test
    void nestedRequireSyntaxErrorKeepsInnerLocationInMessage() throws IOException {
        Path inner = paths.serverScripts().resolve("src/inner-broken.js");
        Files.writeString(inner, """
                const ok = 1
                const } = inner
                """);
        Path outer = paths.serverScripts().resolve("src/outer.js");
        Files.writeString(outer, "require('./inner-broken.js')\n");

        // 内层诊断经 Graal guest 边界传播后异常类型丢失（转为 guest error），
        // 但完整诊断文本（含 at <file>:<line>:<column>）保留在外层异常消息里
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> host.loadEntry("./server_scripts/src/outer.js"));

        String message = String.valueOf(error.getMessage());
        assertTrue(message.contains("SyntaxError"), "message was: " + message);
        assertTrue(message.contains("inner-broken.js:2:7"), "message was: " + message);
    }
}
