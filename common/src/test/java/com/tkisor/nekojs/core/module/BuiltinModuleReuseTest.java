package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoLexer;
import com.tkisor.nekojs.core.compiler.NekoParser;
import com.tkisor.nekojs.core.compiler.NekoAstLowering;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinModuleReuseTest {
    @TempDir Path gameDir;
    private NekoJSPaths paths;
    private ScriptCompilerRegistry compilers;

    @BeforeEach
    void initialize() {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = NekoJSPaths.fromGameDir(gameDir);
        compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        compilers.register(NekoTypeScriptLanguagePlugin.INSTANCE);
    }

    @Test
    void unchangedBuiltinTransformsSurviveGenerationCloseButMapsStayLocal() throws Exception {
        for (String extension : new String[]{"js", "mjs", "cjs", "ts"}) {
            Path file = write("probe." + extension, "const value = 1;\n");
            try (NekoModulePipelineCache owner = owner(NekoTrustContext.local())) {
                NekoPreparedModule first = prepare(owner, file);
                try (NekoModulePipelineCache next = owner.openSession()) {
                    AtomicInteger observations = new AtomicInteger();
                    next.registerPreparationObserver((path, revision) -> observations.incrementAndGet());
                    assertSame(first, next.prepare(file), "unchanged built-in transformation should be reused");
                    assertEquals(1, observations.get(), "transform hits must still publish session observations");
                    assertEquals(first.cacheKey(), next.sourceMaps().mappedCacheRevision("server_scripts/probe." + extension));
                    assertEquals(1, owner.preparedEntryCount(), "only the live session owns prepared entries");
                }
                assertEquals(0, owner.preparedEntryCount());
                assertNull(owner.sourceMaps().mappedCacheRevision("server_scripts/probe." + extension));
            }
        }
    }

    @Test
    void customLanguagePluginWithBuiltinIdStillRunsEachGeneration() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        compilers.register(new NekoLanguagePlugin() {
            @Override public String id() { return "javascript"; }
            @Override public Set<String> extensions() { return Set.of(".js"); }
            @Override public NekoLexer lexer() { calls.incrementAndGet(); return NekoJavaScriptLanguagePlugin.INSTANCE.lexer(); }
            @Override public NekoParser parser() { return NekoJavaScriptLanguagePlugin.INSTANCE.parser(); }
            @Override public NekoAstLowering lowering() { return NekoJavaScriptLanguagePlugin.INSTANCE.lowering(); }
        });
        Path file = write("custom-plugin.js", "const value = 1;\n");
        try (NekoModulePipelineCache owner = owner(NekoTrustContext.local())) {
            prepare(owner, file);
            prepare(owner, file);
            assertEquals(2, calls.get(), "delegating to built-in phases does not make a plugin pure");
        }
    }

    @Test
    void freshContentReadDetectsEqualSizeEqualTimestampEdits() throws Exception {
        Path file = write("edit.js", "const value = 'old';\n");
        try (NekoModulePipelineCache owner = owner(NekoTrustContext.local())) {
            NekoPreparedModule first = prepare(owner, file);
            FileTime time = Files.getLastModifiedTime(file);
            Files.writeString(file, "const value = 'new';\n");
            Files.setLastModifiedTime(file, time);
            NekoPreparedModule next = prepare(owner, file);
            assertNotSame(first, next);
            assertTrue(next.code().contains("new"));
        }
    }

    @Test
    void customCompilerRunsForEveryNewGenerationEvenWithBuiltinLanguageId() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        compilers.registerLanguage("javascript", Set.of(".js"), new IScriptCompiler() {
            @Override public boolean canCompile(String extension) { return ".js".equals(extension); }
            @Override public String compile(Path file, String source) {
                return "const revision = " + calls.incrementAndGet() + ";\n";
            }
        });
        Path file = write("custom.js", "const value = 1;\n");
        try (NekoModulePipelineCache owner = owner(NekoTrustContext.local())) {
            assertTrue(prepare(owner, file).code().contains("revision = 1"));
            assertTrue(prepare(owner, file).code().contains("revision = 2"));
            assertEquals(2, calls.get());
        }
    }

    @Test
    void cacheHitStillUsesCurrentCandidateSchema() throws Exception {
        for (String extension : new String[]{"js", "ts"}) {
            Path file = write("schema." + extension, "Probe.allowed();\n");
            AtomicInteger diagnostics = new AtomicInteger();
            ScriptBindingSchema.View valid = new ScriptBindingSchema.View(
                    Map.of("Probe", new ScriptBindingSchema.BindingMembers(Set.of("allowed"))), Set.of(),
                    diagnostic -> diagnostics.incrementAndGet());
            ScriptBindingSchema.View invalid = new ScriptBindingSchema.View(
                    Map.of("Probe", new ScriptBindingSchema.BindingMembers(Set.of("other"))), Set.of(),
                    diagnostic -> diagnostics.incrementAndGet());
            try (NekoModulePipelineCache owner = owner(NekoTrustContext.local())) {
                NekoPreparedModule first;
                try (NekoModulePipelineCache session = owner.openSession(valid)) { first = session.prepare(file); }
                assertEquals(0, diagnostics.get());
                try (NekoModulePipelineCache session = owner.openSession(invalid)) {
                    assertSame(first, session.prepare(file));
                    assertTrue(diagnostics.get() > 0, "fresh schema diagnostics must run on transform hits");
                }
            }
        }
    }

    @Test
    void cacheHitCannotBypassLiveTrustOrPackageContainment() throws Exception {
        AtomicBoolean authorized = new AtomicBoolean(true);
        Path file = write("trust.js", "const value = 1;\n");
        try (NekoModulePipelineCache owner = owner(path -> authorized.get() ? NekoTrustApprovedSource.local(path) : null)) {
            prepare(owner, file);
            authorized.set(false);
            try (NekoModulePipelineCache session = owner.openSession()) { assertThrows(java.io.IOException.class, () -> session.prepare(file)); }
            authorized.set(true);
            try (NekoModulePipelineCache session = owner.openSession()) {
                session.restrictToPackRoot(paths.root().resolve("other-pack"));
                assertThrows(java.io.IOException.class, () -> session.prepare(file));
            }
        }
    }

    @Test
    void explicitInvalidationAndTypeClearEvictReusableTransforms() throws Exception {
        Path file = write("clear.js", "const value = 1;\n");
        try (NekoModulePipelineCache owner = owner(NekoTrustContext.local())) {
            NekoPreparedModule first = prepare(owner, file);
            owner.clear(ScriptType.CLIENT);
            assertSame(first, prepare(owner, file));
            owner.invalidate(file);
            NekoPreparedModule second = prepare(owner, file);
            assertNotSame(first, second);
            owner.clear(ScriptType.SERVER);
            NekoPreparedModule third = prepare(owner, file);
            assertNotSame(second, third);
            owner.clear();
            assertNotSame(third, prepare(owner, file));
        }
    }

    @Test
    void independentOwnersDoNotShareTransforms() throws Exception {
        Path file = write("owner.js", "const value = 1;\n");
        try (NekoModulePipelineCache first = owner(NekoTrustContext.local());
             NekoModulePipelineCache second = owner(NekoTrustContext.local())) {
            assertNotSame(prepare(first, file), prepare(second, file));
        }
    }

    private Path write(String name, String source) throws Exception {
        Path file = paths.serverScripts().resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }

    private NekoModulePipelineCache owner(NekoTrustContext trust) {
        return new NekoModulePipelineCache(new NekoModulePipeline(new NekoCompilationPipeline(), compilers,
                SandboxConfig.defaultConfig()), new SourceMapRegistry(paths.root()),
                new NekoEsmVirtualModuleRegistry(paths.root()), trust);
    }

    private NekoPreparedModule prepare(NekoModulePipelineCache owner, Path file) throws Exception {
        try (NekoModulePipelineCache session = owner.openSession()) { return session.prepare(file); }
    }
}
