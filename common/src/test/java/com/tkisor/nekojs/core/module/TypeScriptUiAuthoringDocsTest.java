package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 47: every example published in the AI UI authoring contract and the
 * web conversion cookbook must stay executable. This test copies the cookbook's
 * conversion outputs from {@code docs/ui-conversion/fixtures/} (read-only) next
 * to the proof fixture {@code ui-authoring-docs-proof.tsx} and executes both
 * through the fake-host JSX runtime, asserting the exported proof object.
 */
class TypeScriptUiAuthoringDocsTest {

    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private Context context;
    private NekoScriptModuleLoaderHost host;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = pathsFor(gameDir);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        boot();
    }

    private void boot() throws Exception {
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, true, true,
                30, 0, 0, SandboxConfig.PACK_SYNC_OFF, false, false);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.register(NekoJsxLanguagePlugin.INSTANCE);
        compilers.register(NekoTsxLanguagePlugin.INSTANCE);
        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(config, paths), paths, cache))
                .build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build();
        NekoModuleResolver resolver = new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                new ScriptFilePolicy(compilers));
        NekoNodeModuleInstaller.install(context, ScriptType.TEST, resolver, paths,
                new DefaultErrorTracker(paths, config), config, cache);
        host = (NekoScriptModuleLoaderHost) context.getBindings("js")
                .getMember("__nekoScriptModuleLoaderHost").asHostObject();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void authoringContractAndCookbookExamplesExecute() throws Exception {
        write("ui-authoring-docs-proof.tsx", readResource("/nekojs/language-ts-examples/tsx/ui-authoring-docs-proof.tsx"));
        write("login-form.output.tsx", readDocsFixture("login-form.output.tsx"));
        write("card-grid.output.tsx", readDocsFixture("card-grid.output.tsx"));

        Value exports = asValue(host.loadEntry("./server_scripts/src/ui-authoring-docs-proof.tsx"));
        Value proof = exports.getMember("authoringDocsProof");
        assertNotNull(proof, "the proof fixture must export its proof object");
        assertEquals(true, proof.getMember("passed").asBoolean(), "all documented examples must pass");
        assertEquals(11, proof.getMember("primitives").asInt(), "the primitive catalog must stay complete");
        assertEquals("catalog,login-form,card-grid", proof.getMember("fixtures").asString());
        assertEquals(true, proof.getMember("rootsClosed").asBoolean(), "proof roots must close cleanly");
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream input = TypeScriptUiAuthoringDocsTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "missing test resource: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Reads a published cookbook output from the repository docs tree, never mutating it. */
    private static String readDocsFixture(String name) throws IOException {
        Path candidate = Path.of("..", "docs", "ui-conversion", "fixtures", name);
        if (!Files.isRegularFile(candidate)) candidate = Path.of("docs", "ui-conversion", "fixtures", name);
        assertTrue(Files.isRegularFile(candidate), "missing docs fixture: " + name);
        return Files.readString(candidate, StandardCharsets.UTF_8);
    }

    private Path write(String name, String source) throws IOException {
        Path file = paths.serverScripts().resolve("src/" + name);
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return file;
    }

    private static Value asValue(Object exports) {
        assertTrue(exports instanceof Value, "host must return a guest Value, was: " + exports);
        return (Value) exports;
    }

    private static NekoJSPaths pathsFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return constructor.newInstance(gameDir);
    }
}
