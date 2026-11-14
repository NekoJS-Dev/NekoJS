package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.NekoCompileOutput;
import com.tkisor.nekojs.core.compiler.NekoIRProgram;
import com.tkisor.nekojs.core.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.NekoScriptLanguage;
import com.tkisor.nekojs.core.compiler.ScriptCompileResult;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.ScriptSourcePreflight;
import com.tkisor.nekojs.core.compiler.NekoCompileException;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoLegacyLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoSourceMapBuilder;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.core.module.cjs.CjsStaticAnalyzer;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;
import com.tkisor.nekojs.core.module.esm.NekoEsmParser;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Instantiated module compilation pipeline (Script Preparation). The constructor takes a
 * {@link NekoCompilationPipeline}, a {@link ScriptCompilerRegistry}, and a {@link SandboxConfig}, and compiles modules
 * according to ESM/CJS mode and the language plugins.
 *
 * <p>All dependencies are passed explicitly to the constructor; the class holds no process-wide static state
 * (the old {@code LEGACY_INSTANCE}, {@code bindLegacyInstance}, {@code legacyInstance}, {@code legacyPrepare}, and
 * {@code SHARED_COMPILATION_PIPELINE} are gone). Callers that need an instance must get one from the
 * {@link NekoModulePipelineCache} owned by {@code NekoRuntimeRoot}; there is no "run without an instance" fallback.
 * Alternative behavior and traces are covered by {@code NekoModulePipelinePrepareTest} and
 * {@code NekoModulePipelineCacheStampTest}; {@code ModulePipelineIsolationTest} asserts that there are no static calls.
 *
 * <p>The produced {@link NekoPreparedModule} carries the language id, module mode, executable code, the source map
 * produced by the compiler (or a conservative fallback, where the sourceURL can still serve the executor as a fallback),
 * the original diagnostic source path, and a stable cache key. It is immutable.
 *
 * <p>This class does not create a Graal Context, does not decide HostAccess, and does not read Minecraft or the loader
 * (see {@code ModulePipelineIsolationTest}).
 */
public final class NekoModulePipeline {
    private final NekoCompilationPipeline compilationPipeline;
    private final ScriptCompilerRegistry compilers;
    private final SandboxConfig config;

    public NekoModulePipeline(NekoCompilationPipeline compilationPipeline, ScriptCompilerRegistry compilers, SandboxConfig config) {
        this.compilationPipeline = Objects.requireNonNull(compilationPipeline, "compilationPipeline");
        this.compilers = Objects.requireNonNull(compilers, "compilers");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Resolves identity: path/extension to source path, requested mode, and language ID.
     * Does not compile, read from disk, or touch global state.
     */
    NekoModuleIdentity identify(Path file) {
        return captureBinding(file).identity();
    }

    NekoPreparedModule prepare(Path file, String rawSource) throws Exception {
        LanguageBinding binding = captureBindingChecked(file);
        return prepareWithBinding(file, rawSource == null ? "" : rawSource, binding,
                ScriptBindingSchema.emptyView(), null);
    }

    /**
     * Cache-owned preparation seam: the binding captured for the source stamp drives the preparation,
     * and the registry revision is checked before and after compilation.
     */
    NekoPreparedModule prepareCaptured(Path file, String rawSource, NekoTrustApprovedSource approval,
                                       LanguageBinding binding) throws Exception {
        return prepareCaptured(file, rawSource, approval, binding, ScriptBindingSchema.emptyView());
    }

    NekoPreparedModule prepareCaptured(Path file, String rawSource, NekoTrustApprovedSource approval,
                                       LanguageBinding binding, ScriptBindingSchema.View schemaView) throws Exception {
        return prepareCaptured(file, rawSource, approval, binding, schemaView, null);
    }

    NekoPreparedModule prepareCaptured(Path file, String rawSource, NekoTrustApprovedSource approval,
                                       LanguageBinding binding, ScriptBindingSchema.View schemaView,
                                       BuiltinModuleTransformCache transforms) throws Exception {
        NekoModuleIdentity identity = binding.identity();
        if (approval == null || !approval.covers(file)) {
            throw NekoModuleError.denied(NekoModuleError.displayPath(file), identity.languageId(),
                    identity.requestedMode(), approval, "preparation requires a trust-approved source");
        }
        return prepareWithBinding(file, rawSource == null ? "" : rawSource, binding, schemaView, transforms);
    }

    private NekoPreparedModule prepareWithBinding(Path file, String rawSource, LanguageBinding binding,
                                                  ScriptBindingSchema.View schemaView,
                                                  BuiltinModuleTransformCache transforms) throws Exception {
        NekoModuleIdentity identity = binding.identity();
        try {
            return prepareStages(file, rawSource, binding, schemaView, transforms);
        } catch (NekoModuleError staged) {
            throw staged;
        } catch (Exception failure) {
            NekoCompileException compileFailure = findCompileFailure(failure);
            if (compileFailure != null) {
                throw NekoModuleError.prepare(NekoModuleError.displayPath(file), identity.languageId(),
                        identity.requestedMode(), compileFailure.line(), compileFailure.column(),
                        NekoModuleError.rootMessage(failure), failure);
            }
            throw NekoModuleError.prepare(NekoModuleError.displayPath(file), identity.languageId(),
                    identity.requestedMode(), NekoModuleError.rootMessage(failure), failure);
        }
    }

    /**
     * Preparation gate with trust credentials: local and remote-authorized sources follow the same
     * preparation phase; trust only determines the authorization result.
     * Rejects missing or non-matching credentials with an authorization error (stage remains
     * PREPARE, owner is Pack Trust).
     */
    NekoPreparedModule prepare(Path file, String rawSource, NekoTrustApprovedSource approval) throws Exception {
        LanguageBinding binding = captureBindingChecked(file);
        return prepareCaptured(file, rawSource, approval, binding);
    }

    private NekoPreparedModule prepareStages(Path file, String rawSource, LanguageBinding binding,
                                             ScriptBindingSchema.View schemaView,
                                             BuiltinModuleTransformCache transforms) throws Exception {
        ensureBindingCurrent(file, binding);
        String extension = extension(file);
        // Run static checks at load time: scans for member access on global bindings (e.g., Utils, Platform, Items)
        // and reports errors to the in-game dashboard if members are missing. Does not abort compilation or execution.
        // Only the native JS frontend guarantees authored JS. Other frontends, including JS extension
        // overrides, are checked after compilation so foreign syntax cannot become bogus global calls.
        if (config.scriptMemberValidation() && rawPreflightApplies(binding)) {
            validateSource(file, rawSource, schemaView, transforms);
        }
        // Only transformation is reusable. Authorization and current-schema checks remain live.
        NekoPreparedModule prepared = transforms == null
                ? prepareModule(file, rawSource, extension, binding)
                : transforms.prepare(file, rawSource, binding,
                        () -> prepareModule(file, rawSource, extension, binding));
        if (config.scriptMemberValidation() && !rawPreflightApplies(binding)) {
            validateSource(file, prepared.code(), schemaView, transforms);
        }
        ensureBindingCurrent(file, binding);
        return prepared;
    }

    private static void validateSource(Path file, String source, ScriptBindingSchema.View view,
                                       BuiltinModuleTransformCache transforms) {
        if (transforms == null) ScriptSourcePreflight.validate(file, source, view);
        else transforms.validate(file, source, view);
    }

    /** Language-frontend diagnostics that carry their authored position. */
    private static NekoCompileException findCompileFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof NekoCompileException compileFailure) {
                return compileFailure;
            }
        }
        return null;
    }

    boolean memberValidationEnabled() {
        return config.scriptMemberValidation();
    }

    /** Only this captured frontend guarantees that the authored source is JavaScript. */
    static boolean rawPreflightApplies(LanguageBinding binding) {
        return binding.plugin() == NekoJavaScriptLanguagePlugin.INSTANCE;
    }

    private NekoPreparedModule prepareModule(Path file, String rawSource, String extension,
                                              LanguageBinding binding) throws Exception {
        NekoModuleMode requestedMode = binding.identity().requestedMode();
        NekoLanguagePlugin language = binding.plugin();
        String languageId = binding.identity().languageId();
        String sourcePath = NekoModuleError.displayPath(file);

        if (!config.enableEsmAuthoring() || requestedMode == NekoModuleMode.COMMONJS) {
            if (language instanceof NekoLegacyLanguagePlugin legacyLanguage) {
                ScriptCompileResult compiled = legacyLanguage.compiler().compileDetailed(file, rawSource);
                // CJS static analysis must run on compiled output: require and module.exports are generated by transpilation, not present in raw source.
                return NekoPreparedModule.commonJs(languageId, sourcePath, compiled.code(),
                        usableMap(file, rawSource, compiled.code(), compiled.sourceMap()),
                        CjsStaticAnalyzer.analyze(compiled.code()));
            }
            if (language == NekoJavaScriptLanguagePlugin.INSTANCE) {
                return NekoPreparedModule.commonJs(languageId, sourcePath, rawSource,
                        NekoSourceMapBuilder.identity(file, rawSource, rawSource),
                        CjsStaticAnalyzer.analyze(rawSource));
            }
            NekoCompileOutput compiled = compilationPipeline.compile(
                file, rawSource, extension, language, config.jsxAutomaticRuntime());
            return NekoPreparedModule.commonJs(languageId, sourcePath, compiled.code(),
                    usableMap(file, rawSource, compiled.code(), compiled.program().sourceMap()),
                    CjsStaticAnalyzer.analyze(compiled.code()));
        }

        NekoCompileOutput compiled = compilationPipeline.compile(
            file, rawSource, extension, language, config.jsxAutomaticRuntime());
        return prepareModule(file, rawSource, compiled, languageId, sourcePath);
    }

    private NekoPreparedModule prepareModule(Path file, String rawSource, NekoCompileOutput compiled,
                                             String languageId, String sourcePath) {
        NekoIRProgram ir = compiled.program();
        String map = usableMap(file, rawSource, compiled.code(), compiled.program().sourceMap());
        if (ir.requestedMode() == NekoModuleMode.AUTO && !ir.module()) {
            return NekoPreparedModule.commonJs(languageId, sourcePath, compiled.code(), map,
                    CjsStaticAnalyzer.analyze(compiled.code()));
        }
        NekoEsmModuleAst ast = compiled.esmAst();
        if (ast == null) {
            ast = new NekoEsmParser(null, compiled.code()).parse();
        }
        return NekoPreparedModule.esm(languageId, sourcePath, compiled.code(), map, ast);
    }

    /** Use the compiler map when present; otherwise publish a non-null conservative fallback map. */
    private static String usableMap(Path file, String authoredSource, String generatedSource, String sourceMap) {
        if (sourceMap != null && !sourceMap.isBlank()) {
            return sourceMap;
        }
        return NekoSourceMapBuilder.identity(file, authoredSource, generatedSource);
    }

    LanguageBinding captureBinding(Path file) {
        String extension = extension(file);
        NekoModuleMode requestedMode = NekoModuleMode.fromExtension(extension);
        ScriptCompilerRegistry.Capture capture = compilers.capture(extension);
        NekoLanguagePlugin plugin = capture.plugin();
        if (plugin == null && capture.compiler() != null) {
            if (capture.language() != null) {
                plugin = new NekoLegacyLanguagePlugin(capture.language().id(),
                        capture.language().extensions(), capture.compiler());
            } else {
                plugin = new NekoLegacyLanguagePlugin("legacy:" + extension.substring(1),
                        Set.of(extension), capture.compiler());
            }
        }
        if (plugin == null && !ScriptCompilerRegistry.isNativeScriptExtension(extension)) {
            throw new IllegalArgumentException("No script compiler registered for " + extension + " module: " + file);
        }
        if (plugin == null) plugin = NekoJavaScriptLanguagePlugin.INSTANCE;
        return new LanguageBinding(new NekoModuleIdentity(plugin.id(), requestedMode), plugin, capture.revision());
    }

    private LanguageBinding captureBindingChecked(Path file) throws NekoModuleError {
        try {
            return captureBinding(file);
        } catch (Exception failure) {
            throw NekoModuleError.prepare(NekoModuleError.displayPath(file), "unknown",
                    NekoModuleMode.AUTO, NekoModuleError.rootMessage(failure), failure);
        }
    }

    void ensureBindingCurrent(Path file, LanguageBinding binding) throws NekoModuleError {
        if (compilers.capture(extension(file)).revision() != binding.registryRevision()) {
            throw NekoModuleError.prepare(NekoModuleError.displayPath(file), binding.identity().languageId(),
                    binding.identity().requestedMode(), "compiler registry changed during preparation", null);
        }
    }

    private String extension(Path file) {
        if (file == null || file.getFileName() == null) {
            return "";
        }
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    record LanguageBinding(NekoModuleIdentity identity, NekoLanguagePlugin plugin, long registryRevision) {}

}
