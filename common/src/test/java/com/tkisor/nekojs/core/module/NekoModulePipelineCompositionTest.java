package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.compiler.FrontendSemantics;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.preflight.ScriptPreflight;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Stage;
import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoScriptLanguage;
import com.tkisor.nekojs.core.compiler.ScriptCompileResult;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler;
import com.tkisor.nekojs.core.config.EngineConfigException;
import com.tkisor.nekojs.core.config.SandboxConfigLoader;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.preflight.BuiltinJavaScriptPreflight;
import com.tkisor.nekojs.core.preflight.ScriptPreflightCatalog;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NekoModulePipelineCompositionTest {
    private static final FrontendSemantics.Input PYTHON = new FrontendSemantics.Input(FrontendSemantics.SOURCE_TEXT, "nekojs:python");
    private static final Set<String> CAPABILITIES = Set.of(FrontendSemantics.PYTHON_AUTHORING);
    private static final FrontendSemantics PYTHON_TO_JS = new FrontendSemantics(PYTHON, FrontendSemantics.JAVASCRIPT_OUTPUT, CAPABILITIES);

    @TempDir Path gameDir;

    @BeforeAll static void bindPlatform() { TestPlatformInit.ensureInitialized(); }

    @Test
    void composesThirdPartySourceChecksAndJavaScriptOutputInDeclaredOrder() throws Exception {
        var calls = new ArrayList<String>();
        var compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        registration(compilers, "python-vendor:python", ".py", python(calls));
        compilers.freeze();
        var checks = catalog(
                check("checker:second", Stage.SOURCE, PYTHON, CAPABILITIES, request -> {
                    calls.add("second:" + request.scope()); return List.of();
                }),
                check("checker:first", Stage.SOURCE, PYTHON, CAPABILITIES, request -> {
                    calls.add("first:" + request.scope());
                    assertEquals(ScriptType.SERVER, request.scriptType());
                    assertEquals("python-vendor:python", request.frontendId());
                    assertTrue(request.text().startsWith("# Python"));
                    return List.of();
                }),
                check("checker:output", Stage.OUTPUT, FrontendSemantics.JAVASCRIPT_OUTPUT, Set.of(), request -> {
                    calls.add("output:" + request.scope());
                    assertTrue(request.text().contains("Fixture.missing()"));
                    assertFalse(request.text().startsWith("# Python"));
                    assertNotNull(request.sourceMap());
                    int offset = request.text().indexOf("Fixture.missing()");
                    int line = (int) request.text().substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
                    return List.of(new ScriptPreflight.Diagnostic("output", ScriptPreflight.Severity.WARNING,
                            "Output fixture finding", new ScriptPreflight.Position(line, 1, offset), "output"));
                }));
        var diagnostics = new ArrayList<ScriptBindingSchema.Diagnostic>();
        try (var owner = cache(compilers, checks, """
                [languagePipelines.".py"]
                frontend = "python-vendor:python"
                sourceChecks = ["checker:first", "checker:second"]
                outputChecks = ["checker:output", "nekojs:javascript"]
                """); var session = owner.openSession(ScriptType.SERVER, view(Set.of("valid"), diagnostics))) {
            Path file = script("check.py", "# Python input\nFixture.missing()\n");
            session.prepareEntry(file);
            assertEquals(List.of("first:FULL", "second:FULL", "compile", "output:FULL",
                    "first:BINDINGS_ONLY", "second:BINDINGS_ONLY", "output:BINDINGS_ONLY"), calls);
            assertEquals(2, diagnostics.size(), "each provider finding is delivered once");
            var authored = ((NekoEsmLinkException) diagnostics.getFirst().throwable()).diagnostic();
            assertEquals(file, authored.file());
            assertEquals(2, authored.line(), "checks must map the actual generated artifact back to Python");
            assertTrue(diagnostics.get(1).callbackKind().startsWith("binding-preflight"));
        }
    }

    @Test
    void explicitEmptyStageDoesNotDisableDefaultOutputOrOrdinaryCompilation() throws Exception {
        var calls = new ArrayList<String>();
        var compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        registration(compilers, "python-vendor:python", ".py", python(calls));
        compilers.freeze();
        var checks = catalog();
        var diagnostics = new ArrayList<ScriptBindingSchema.Diagnostic>();
        try (var owner = cache(compilers, checks, """
                [languagePipelines.".py"]
                frontend = "python-vendor:python"
                sourceChecks = []
                """); var session = owner.openSession(ScriptType.SERVER, view(Set.of("valid"), diagnostics))) {
            session.prepare(script("default.py", "Fixture.missing()\n"));
            assertEquals(1, diagnostics.size());
        }
        diagnostics.clear();
        try (var owner = cache(compilers, checks, """
                [languagePipelines.".py"]
                outputChecks = []
                """); var session = owner.openSession(ScriptType.SERVER, view(Set.of("valid"), diagnostics))) {
            session.prepare(script("silent.py", "Fixture.missing()\n"));
            assertTrue(diagnostics.isEmpty());
            assertThrows(NekoModuleError.class, () -> session.prepare(script("invalid.py", "def bad(\n")));
        }
        assertEquals(List.of("compile", "compile", "compile"), calls);
    }

    @Test
    void rejectsInvalidCompositionEvenWhilePreflightExecutionIsDisabled() throws Exception {
        var compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        registration(compilers, "python-vendor:python", ".py", python(new ArrayList<>()));
        compilers.freeze();
        var failure = assertThrows(EngineConfigException.class, () -> cache(compilers, catalog(), """
                scriptMemberValidation = false
                [languagePipelines.".py"]
                frontend = "python-vendor:python"
                sourceChecks = ["nekojs:javascript"]
                """));
        assertTrue(failure.getMessage().contains("[NEKO-1030]"));
        assertTrue(failure.getMessage().contains("engine.toml"));
        assertTrue(failure.getMessage().contains(".py SOURCE"));
        assertTrue(failure.getMessage().contains("nekojs:javascript"));
        assertTrue(failure.getMessage().contains("compatible checks"));
        var diagnostics = new ArrayList<ScriptBindingSchema.Diagnostic>();
        try (var owner = cache(compilers, catalog(), """
                scriptMemberValidation = false
                [languagePipelines.".py"]
                outputChecks = ["nekojs:javascript"]
                """); var session = owner.openSession(ScriptType.SERVER, view(Set.of("valid"), diagnostics))) {
            assertFalse(session.prepare(script("unchecked.py", "Fixture.missing()\n")).code().isBlank());
            assertTrue(diagnostics.isEmpty());
            assertThrows(NekoModuleError.class, () -> session.prepare(script("still-invalid.py", "def bad(\n")));
        }
    }

    @Test
    void addsOnlyTheConfiguredExtensionWhenTheAutomaticCandidateHasBeenReplaced() throws Exception {
        var invocations = new AtomicInteger();
        IScriptCompiler compiler = new IScriptCompiler() {
            public boolean canCompile(String extension) { return extension.equals(".fixture") || extension.equals(".sibling"); }
            public String compile(Path file, String source) { invocations.incrementAndGet(); return "module.exports = 'explicit';"; }
        };
        var compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.registerFrontend("vendor:preferred", "v1", new NekoScriptLanguage("shared", Set.of(".fixture", ".sibling"), compiler), PYTHON_TO_JS);
        compilers.replaceLanguage(new NekoScriptLanguage("shared", Set.of(".other"), python(new ArrayList<>())));
        compilers.freeze();
        var file = script("preferred.fixture", "authored");
        var adjacent = script("ignored.sibling", "authored");
        try (var owner = cache(compilers, catalog(), """
                [languagePipelines.".fixture"]
                frontend = "vendor:preferred"
                """)) {
            assertTrue(owner.filePolicy().isSupportedScriptFile(file));
            assertFalse(owner.filePolicy().isSupportedScriptFile(adjacent));
            var paths = NekoJSPaths.fromGameDir(gameDir);
            var resolver = new NekoModuleResolver(gameDir, paths.root(), paths.nodeModules(), owner.filePolicy());
            assertEquals(file, resolver.resolveEntry("./server_scripts/preferred").path());
            owner.prepare(file);
            assertEquals(1, invocations.get());
        }
    }

    private NekoModulePipelineCache cache(ScriptCompilerRegistry compilers, ScriptPreflightCatalog catalog,
                                          String text) throws Exception {
        var paths = NekoJSPaths.fromGameDir(gameDir);
        Files.createDirectories(paths.engineConfig().getParent());
        Files.writeString(paths.engineConfig(), text);
        var config = new SandboxConfigLoader().load(paths.engineConfig());
        var effective = LanguagePipelineSelection.resolve(paths.engineConfig(), config, compilers, catalog);
        return new NekoModulePipelineCache(new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config, effective),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()), NekoTrustContext.local());
    }

    private Path script(String name, String text) throws Exception {
        Path file = NekoJSPaths.fromGameDir(gameDir).serverScripts().resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    private static IScriptCompiler python(List<String> trace) {
        return new IScriptCompiler() {
            public boolean canCompile(String extension) { return ".py".equals(extension); }
            public String compile(Path file, String source) throws Exception { return compileDetailed(file, source).code(); }
            public ScriptCompileResult compileDetailed(Path file, String source) throws Exception {
                trace.add("compile");
                return new PythonToJsCompiler().compileDetailed(file, source);
            }
        };
    }

    private static void registration(ScriptCompilerRegistry compilers, String id, String ext, IScriptCompiler compiler) {
        compilers.registerFrontend(id, "v1", new NekoScriptLanguage("python", Set.of(ext), compiler), PYTHON_TO_JS);
    }

    private static ScriptPreflightCatalog catalog(ScriptPreflight... checks) {
        var collector = new ScriptPreflightCatalog.Collector();
        collector.register(BuiltinJavaScriptPreflight.INSTANCE);
        for (var check : checks) collector.register(check);
        return collector.freeze();
    }

    private static ScriptPreflight check(String id, Stage stage, FrontendSemantics.Input input, Set<String> caps,
                                         ScriptPreflight.Worker worker) {
        return new ScriptPreflight() {
            public Descriptor descriptor() {
                return new Descriptor(id, "v1", Map.of(stage, Set.of(input)), Set.of(Scope.FULL, Scope.BINDINGS_ONLY), caps);
            }
            public Worker open() { return worker; }
        };
    }

    private static ScriptBindingSchema.View view(Set<String> methods, List<ScriptBindingSchema.Diagnostic> diagnostics) {
        return new ScriptBindingSchema.View(Map.of("Fixture", new ScriptBindingSchema.BindingMembers(methods)),
                Set.of("Fixture"), diagnostics::add);
    }
}
