package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoCompileException;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.DiagnosticPhase;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.module.NekoModuleError;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.NekoModuleTestFixtures;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises entry preflight through the executor, real module loader and diagnostic tracker. */
class ScriptExecutorPreflightTest {
    // Diagnostic log appenders remain open on Windows, so this directory is not a JUnit TempDir.
    private static final Path GAME_DIR = Path.of(System.getProperty("java.io.tmpdir"),
            "nekojs-executor-preflight-gamedir");

    private NekoJSPaths paths;
    private SandboxConfig config;
    private ScriptCompilerRegistry compilers;
    private DefaultErrorTracker tracker;
    private NekoModulePipelineCache cache;
    private Context context;
    private NekoNodeRuntime nodeRuntime;

    @BeforeEach
    void setUp() throws Exception {
        createEnvironment(SandboxConfig.defaultConfig());
    }

    private void createEnvironment(SandboxConfig config) throws Exception {
        this.config = config;
        TestPlatformInit.ensureInitialized(GAME_DIR);
        paths = NekoJSPaths.fromGameDir(GAME_DIR);
        Files.createDirectories(paths.serverScripts());
        compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        compilers.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
        tracker = new DefaultErrorTracker(paths, config);
        cache = NekoModuleTestFixtures.newCache(paths, compilers, config);
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(config, paths), paths, cache))
                .build();
        context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build();
        ScriptContextRegistry.bind(context, ScriptType.SERVER);
        NekoModuleResolver resolver = new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                new ScriptFilePolicy(compilers));
        nodeRuntime = NekoNodeModuleInstaller.install(context, ScriptType.SERVER, resolver, paths, tracker, config, cache);
        tracker.activateModuleViews(ScriptType.SERVER, context, cache, 1L);
        context.eval("js", "globalThis.PreflightFixture = { capture(value) { globalThis.__entryResult = value; } };");
        Set<String> globals = new HashSet<>(context.getBindings("js").getMemberKeys());
        var globalNames = context.eval("js", "Object.getOwnPropertyNames(globalThis)");
        for (long i = 0; i < globalNames.getArraySize(); i++) {
            globals.add(globalNames.getArrayElement(i).asString());
        }
        cache.installBindingSchemaView(new ScriptBindingSchema.View(
                Map.of("PreflightFixture", new ScriptBindingSchema.BindingMembers(Set.of("capture"))),
                globals, diagnostic -> tracker.recordCallbackError(context, diagnostic.type(),
                        diagnostic.callbackKind(), diagnostic.throwable())));
    }

    @AfterEach
    void tearDown() {
        try {
            if (nodeRuntime != null) nodeRuntime.close();
        } finally {
            try {
                if (context != null) {
                    ScriptContextRegistry.unbind(context);
                    context.close();
                }
            } finally {
                if (cache != null) cache.close();
            }
        }
    }

    @Test
    void pythonEntryUsesPreparedJavaScriptWithoutFalseUnknownFunctionDiagnostics() throws Exception {
        Path entry = paths.serverScripts().resolve("python-entry.py");
        Files.writeString(entry, "# Python definitions are not JavaScript declarations\n"
                + "def greet():\n"
                + "    return 7\n"
                + "PreflightFixture.capture(greet())\n");
        ScriptContainer script = new ScriptContainer(ScriptId.of("test", "python-entry"), ScriptType.SERVER,
                entry, new ScriptPropertyRegistry.Impl());
        ScriptExecutor executor = new ScriptExecutor(tracker, paths, config, ignored -> {});

        executor.executeEntry(context, script, nodeRuntime, cache);

        assertFalse(script.disabled, () -> "Python entry failed: " + script.lastError);
        assertNull(script.lastError);
        assertEquals(7, context.getBindings("js").getMember("__entryResult").asInt());
        assertTrue(tracker.getAllErrors().isEmpty(),
                () -> "Valid Python must not produce raw-source preflight diagnostics: " + tracker.getAllErrors());
    }

    @Test
    void typescriptEntryChecksErasedJavaScriptInsteadOfInterfaceMembers() throws Exception {
        ScriptContainer script = execute("typescript-entry.ts", "interface Shape { compute(): number; }\n"
                + "const value: number = 9;\n"
                + "PreflightFixture.capture(value);\n", nodeRuntime);

        assertFalse(script.disabled, () -> "TypeScript entry failed: " + script.lastError);
        assertNull(script.lastError);
        assertEquals(9, context.getBindings("js").getMember("__entryResult").asInt());
        assertTrue(tracker.getAllErrors().isEmpty());
    }

    @Test
    void legacyCompilerOverridingJsChecksGeneratedMembersAndCompilesOnlyOnce() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        context.eval("js", "PreflightFixture.missing = () => {};");
        compilers.registerLanguage("entry-override", Set.of(".js"), new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return ".js".equals(extension) || "js".equals(extension);
            }

            @Override
            public String compile(Path file, String source) {
                compilations.incrementAndGet();
                return "PreflightFixture.missing(); PreflightFixture.capture(11);";
            }
        });

        ScriptContainer script = execute("overridden-entry.js", "authoredOnly();", nodeRuntime);

        assertFalse(script.disabled, () -> "Overridden entry failed: " + script.lastError);
        assertNull(script.lastError);
        assertEquals(11, context.getBindings("js").getMember("__entryResult").asInt());
        assertEquals(1, compilations.get(), "Entry validation and loading must reuse preparation");
        assertEquals(1, tracker.getErrorCount());
        assertTrue(tracker.getAllErrors().iterator().next().getErrorMessage().contains("missing"));
        assertTrue(tracker.getAllErrors().stream().noneMatch(error -> error.getErrorMessage().contains("authoredOnly")));
    }

    @Test
    void preparationFailureUsesOrdinaryEntryErrorAndNeverFallsBackToLoadingRawSource() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        NekoCompileException failure = new NekoCompileException("Frontend rejected entry", 2, 3);
        registerFailingCompiler(compilations, failure);
        context.eval("js", "globalThis.__loaderCalls = 0;"
                + " globalThis.__nekoScriptLoader.loadEntry = () => { globalThis.__loaderCalls++; };");

        ScriptContainer script = execute("rejected-entry.js", "PreflightFixture.capture(99);", null);

        assertEquals(1, compilations.get());
        assertEquals(0, context.getBindings("js").getMember("__loaderCalls").asInt(),
                "A preparation failure must stop before the executor's fallback loader");
        assertFalse(context.getBindings("js").hasMember("__entryResult"));
        assertPreparationFailure(script, failure);
    }

    @Test
    void disabledValidationSuppressesBothChecksButRetainsNormalCompilationFailure() throws Exception {
        tearDown();
        createEnvironment(new SandboxConfig(false, false, false, false, true, true, false, false));
        AtomicInteger compilations = new AtomicInteger();
        context.eval("js", "PreflightFixture.missing = () => {};");
        compilers.registerLanguage("disabled-entry", Set.of(".js"), new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return ".js".equals(extension) || "js".equals(extension);
            }

            @Override
            public String compile(Path file, String source) {
                compilations.incrementAndGet();
                return "PreflightFixture.missing(); PreflightFixture.capture(13);";
            }
        });

        ScriptContainer successful = execute("disabled-validation.js", "authoredOnly();", nodeRuntime);

        assertFalse(successful.disabled, () -> "Disabled-validation entry failed: " + successful.lastError);
        assertEquals(13, context.getBindings("js").getMember("__entryResult").asInt());
        assertEquals(1, compilations.get(), "Normal compilation must still run exactly once");
        assertTrue(tracker.getAllErrors().isEmpty(), "Both preparation and entry preflight must be disabled");

        NekoCompileException failure = new NekoCompileException("Disabled preflight does not disable compilation", 2, 3);
        registerFailingCompiler(compilations, failure);
        ScriptContainer rejected = execute("disabled-rejected.js", "PreflightFixture.capture(99);", nodeRuntime);

        assertEquals(2, compilations.get());
        assertEquals(13, context.getBindings("js").getMember("__entryResult").asInt());
        assertPreparationFailure(rejected, failure);
    }

    @Test
    void compilerErrorStopsEntryWithoutRetryingOrInvokingFallbackLoader() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        AssertionError failure = new AssertionError("Frontend crashed before producing JavaScript");
        compilers.registerLanguage("stateful-error-entry", Set.of(".js"), new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return ".js".equals(extension) || "js".equals(extension);
            }

            @Override
            public String compile(Path file, String source) {
                if (compilations.incrementAndGet() == 1) throw failure;
                return "PreflightFixture.capture(17);";
            }
        });
        context.eval("js", "globalThis.__loaderCalls = 0;"
                + " const loader = globalThis.__nekoScriptLoader;"
                + " const originalLoadEntry = loader.loadEntry;"
                + " loader.loadEntry = function(path) { globalThis.__loaderCalls++;"
                + " return originalLoadEntry.call(this, path); };");

        ScriptContainer script = execute("compiler-error-entry.js", "PreflightFixture.capture(99);", null);

        assertTrue(script.disabled, "A frontend Error must fail the entry rather than retry compilation");
        assertSame(failure, script.lastError);
        assertEquals(1, compilations.get(), "The loader must not retry a failed frontend");
        assertEquals(0, context.getBindings("js").getMember("__loaderCalls").asInt());
        assertFalse(context.getBindings("js").hasMember("__entryResult"));
        assertEquals(1, tracker.getErrorCount());
        var error = tracker.getAllErrors().iterator().next();
        assertEquals(script.id, error.getErrorId(), "The failure belongs to the script, not preflight-validator");
        assertSame(failure, error.getRawException());
    }

    private ScriptContainer execute(String fileName, String source, NekoNodeRuntime runtime) throws Exception {
        Path entry = paths.serverScripts().resolve(fileName);
        Files.writeString(entry, source);
        ScriptContainer script = new ScriptContainer(ScriptId.of("test", fileName), ScriptType.SERVER,
                entry, new ScriptPropertyRegistry.Impl());
        new ScriptExecutor(tracker, paths, config, ignored -> {}).executeEntry(context, script, runtime, cache);
        return script;
    }

    private void registerFailingCompiler(AtomicInteger compilations, NekoCompileException failure) {
        compilers.registerLanguage("rejected-entry", Set.of(".js"), new IScriptCompiler() {
            @Override
            public boolean canCompile(String extension) {
                return ".js".equals(extension) || "js".equals(extension);
            }

            @Override
            public String compile(Path file, String source) {
                compilations.incrementAndGet();
                throw failure;
            }
        });
    }

    private void assertPreparationFailure(ScriptContainer script, NekoCompileException failure) {
        assertTrue(script.disabled);
        assertEquals(1, tracker.getErrorCount(), "Preparation failure must not become a validator callback error");
        var error = tracker.getAllErrors().iterator().next();
        assertEquals(script.id, error.getErrorId());
        assertSame(script.lastError, error.getRawException());
        assertEquals(DiagnosticPhase.PREPARE, error.diagnostic().phase());
        assertEquals(NekoModuleError.OWNER_PREPARATION, error.diagnostic().owner());
        assertEquals(2, error.diagnostic().line());
        assertEquals(3, error.diagnostic().column());
        Throwable cause = script.lastError;
        while (cause != null && cause != failure) cause = cause.getCause();
        assertSame(failure, cause, "The original frontend failure must remain in the cause chain");
    }
}
