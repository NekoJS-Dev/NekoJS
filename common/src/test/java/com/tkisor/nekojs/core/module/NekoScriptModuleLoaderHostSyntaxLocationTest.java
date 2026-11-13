package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
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
 * Recovers the location of CJS syntax errors. When {@code new Function} in script-loader.js fails to
 * compile, the exception's SourceSection points at internal/script-loader.js, an internal frame, so
 * the real line and column are lost. {@link NekoScriptModuleLoaderHost} re-parses with
 * {@code context.parse} to recover the position and wraps it in a {@link NekoEsmLinkException}
 * diagnostic whose file, line and column point at the user script.
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
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, SandboxConfig.defaultConfig()),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        host = new NekoScriptModuleLoaderHost(context,
                 new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                         ScriptFilePolicy.legacyRuntime()), cache);
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

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                error, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
        NekoEsmLinkException link = assertInstanceOf(NekoEsmLinkException.class, staged.getCause());
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

        // The inner preparation diagnostic keeps its PREPARE stage after crossing into the Graal guest, and
        // the full diagnostic text (including at <file>:<line>:<column>) stays in the exception message.
        IOException error = assertThrows(IOException.class,
                () -> host.loadEntry("./server_scripts/src/outer.js"));

        NekoModuleError staged = NekoModulePipelinePrepareTest.assertStaged(
                error, NekoModuleError.Stage.PREPARE, NekoModuleError.OWNER_PREPARATION);
        String message = String.valueOf(staged.getMessage());
        assertTrue(message.contains("SyntaxError"), "message was: " + message);
        assertTrue(message.contains("inner-broken.js:2:7"), "message was: " + message);
    }

    @Test
    void authoredIdentityKeepsSameNamedPackPrefixesAndUnknownLayouts() {
        Path first = Path.of("packs", "one", "server_scripts", "same.js");
        Path second = Path.of("packs", "two", "server_scripts", "same.js");
        Path unknown = Path.of("vendor", "one", "server_scripts", "same.js");

        assertEquals("packs/one/server_scripts/same.js", NekoModuleError.displayPath(first));
        assertEquals("packs/two/server_scripts/same.js", NekoModuleError.displayPath(second));
        assertTrue(!NekoModuleError.displayPath(first).equals(NekoModuleError.displayPath(second)));
        assertEquals("vendor/one/server_scripts/same.js", NekoModuleError.displayPath(unknown),
                "unknown layouts must retain the complete path instead of truncating at a type segment");
    }
}
