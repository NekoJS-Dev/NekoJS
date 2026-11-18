package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Scope;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Stage;
import com.tkisor.nekojs.core.compiler.NekoCompileOutput;
import com.tkisor.nekojs.core.compiler.NekoIRProgram;
import com.tkisor.nekojs.core.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.NekoScriptLanguage;
import com.tkisor.nekojs.core.compiler.ScriptCompileResult;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.NekoCompileException;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoLegacyLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoSourceMapBuilder;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.preflight.ScriptPreflightRunner.Report;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.core.module.cjs.CjsStaticAnalyzer;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;
import com.tkisor.nekojs.core.module.esm.NekoEsmParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private final LanguagePipelineSelection selection;
    private final ScriptFilePolicy filePolicy;

    public NekoModulePipeline(NekoCompilationPipeline compilationPipeline, ScriptCompilerRegistry compilers, SandboxConfig config) {
        this(compilationPipeline, compilers, config, LanguagePipelineSelection.defaultsOnly());
    }

    public NekoModulePipeline(NekoCompilationPipeline compilationPipeline, ScriptCompilerRegistry compilers,
                              SandboxConfig config, LanguagePipelineSelection selection) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.compilationPipeline = Objects.requireNonNull(compilationPipeline, "compilationPipeline");
        this.compilers = Objects.requireNonNull(compilers, "compilers");
        this.config = Objects.requireNonNull(config, "config");
        this.filePolicy = new ScriptFilePolicy(compilers, selection.configuredExtensions());
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
        return prepareDirect(file, rawSource, binding, ScriptBindingSchema.emptyView());
    }

    NekoPreparedModule prepareCaptured(Path file, String rawSource, NekoTrustApprovedSource approval,
                                       LanguageBinding binding, ScriptBindingSchema.View schemaView) throws Exception {
        requireApproval(file, approval, binding);
        return prepareDirect(file, rawSource, binding, schemaView);
    }

    private NekoPreparedModule prepareDirect(Path file, String rawSource, LanguageBinding binding,
                                             ScriptBindingSchema.View view) throws Exception {
        var preflight = new PipelinePreflight(selection, new SourceMapRegistry(file.toAbsolutePath().getParent()));
        try {
            var type = PipelinePreflight.scriptType(file, null);
            var result = prepareSource(file, rawSource == null ? "" : rawSource, binding, view, null, preflight, type);
            preflight.publish(file, type, view, result.findings());
            return result.module();
        } finally {
            preflight.close();
        }
    }

    /** Preparation always uses the same frontend capture as its trust-approved snapshot. */
    NekoPreparedModule prepare(Path file, String rawSource, NekoTrustApprovedSource approval) throws Exception {
        return prepareCaptured(file, rawSource, approval, captureBindingChecked(file), ScriptBindingSchema.emptyView());
    }

    PreparationResult prepareCaptured(Path file, String source, NekoTrustApprovedSource approval, LanguageBinding binding,
                                      ScriptBindingSchema.View schema, BuiltinModuleTransformCache transforms,
                                      PipelinePreflight preflight, ScriptType type) throws Exception {
        requireApproval(file, approval, binding);
        return prepareSource(file, source, binding, schema, transforms, preflight, type);
    }

    private static void requireApproval(Path file, NekoTrustApprovedSource approval, LanguageBinding binding) throws NekoModuleError {
        if (approval == null || !approval.covers(file)) {
            throw NekoModuleError.denied(NekoModuleError.displayPath(file), binding.identity().languageId(),
                    binding.identity().requestedMode(), approval, "preparation requires a trust-approved source");
        }
    }

    private PreparationResult prepareSource(Path file, String source, LanguageBinding binding,
                                             ScriptBindingSchema.View schema, BuiltinModuleTransformCache transforms,
                                             PipelinePreflight preflight, ScriptType type) throws Exception {
        NekoModuleIdentity identity = binding.identity();
        try {
            ensureBindingCurrent(file, binding);
            var findings = new ArrayList<>(preflight.check(file, type, binding, Stage.SOURCE, Scope.FULL, source, null, schema));
            // Authorization and schema-dependent checks run even when the pure transform is reused.
            String extension = extension(file);
            NekoPreparedModule prepared = transforms == null
                    ? prepareModule(file, source, extension, binding)
                    : transforms.prepare(file, source, binding, () -> prepareModule(file, source, extension, binding));
            findings.addAll(preflight.check(file, type, binding, Stage.OUTPUT, Scope.FULL,
                    prepared.code(), prepared.diagnosticSourceMap(), schema));
            ensureBindingCurrent(file, binding);
            return new PreparationResult(prepared, findings);
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

    record PreparationResult(NekoPreparedModule module, List<Report> findings) {}

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

    ScriptFilePolicy filePolicy() {
        return filePolicy;
    }

    public LanguagePipelineSelection selection() {
        return selection;
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
                        CjsStaticAnalyzer.analyze(compiled.code())).withPreparationIdentity(binding.cacheIdentity(),
                        diagnosticMap(rawSource, compiled.code(), compiled.sourceMap(), file));
            }
            if (language == NekoJavaScriptLanguagePlugin.INSTANCE) {
                return NekoPreparedModule.commonJs(languageId, sourcePath, rawSource,
                        NekoSourceMapBuilder.identity(file, rawSource, rawSource),
                        CjsStaticAnalyzer.analyze(rawSource)).withPreparationIdentity(binding.cacheIdentity(),
                        NekoSourceMapBuilder.identity(file, rawSource, rawSource));
            }
            NekoCompileOutput compiled = compilationPipeline.compile(
                file, rawSource, extension, language, config.jsxAutomaticRuntime());
            return NekoPreparedModule.commonJs(languageId, sourcePath, compiled.code(),
                    usableMap(file, rawSource, compiled.code(), compiled.program().sourceMap()),
                    CjsStaticAnalyzer.analyze(compiled.code())).withPreparationIdentity(binding.cacheIdentity(),
                    diagnosticMap(rawSource, compiled.code(), compiled.program().sourceMap(), file));
        }

        NekoCompileOutput compiled = compilationPipeline.compile(
            file, rawSource, extension, language, config.jsxAutomaticRuntime());
        return prepareModule(file, rawSource, compiled, languageId, sourcePath)
                .withPreparationIdentity(binding.cacheIdentity(),
                        diagnosticMap(rawSource, compiled.code(), compiled.program().sourceMap(), file));
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

    private static String diagnosticMap(String rawSource, String code, String map, Path file) {
        if (map != null && !map.isBlank()) return map;
        return rawSource.equals(code) ? NekoSourceMapBuilder.identity(file, rawSource, code) : null;
    }

    LanguageBinding captureBinding(Path file) {
        String extension = extension(file);
        NekoModuleMode requestedMode = NekoModuleMode.fromExtension(extension);
        var effective = selection.capture(extension, compilers);
        var capture = effective.capture();
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
        return new LanguageBinding(new NekoModuleIdentity(plugin.id(), requestedMode), plugin, capture.revision(),
                capture.frontend(), memberValidationEnabled() ? effective.checks() : LanguagePipelineSelection.Checks.NONE);
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
        if (compilers.revision() != binding.registryRevision()) {
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

    record LanguageBinding(NekoModuleIdentity identity, NekoLanguagePlugin plugin, long registryRevision,
                           ScriptCompilerRegistry.FrontendDescriptor frontend, LanguagePipelineSelection.Checks checks) {
        String cacheIdentity() {
            var material = new StringBuilder();
            append(material, frontend == null ? "" : frontend.id());
            append(material, frontend == null ? "" : frontend.implementationRevision());
            append(material, Long.toString(registryRevision));
            append(material, identity.requestedMode().name());
            for (var stage : Stage.values()) {
                append(material, stage.name());
                for (var check : stage == Stage.SOURCE ? checks.source() : checks.output()) {
                    append(material, check.id());
                    append(material, check.revision());
                }
            }
            return material.toString();
        }

        private static void append(StringBuilder target, String text) {
            target.append(text.length()).append(':').append(text);
        }

        LanguageBinding(NekoModuleIdentity identity, NekoLanguagePlugin plugin, long registryRevision) {
            this(identity, plugin, registryRevision, null, LanguagePipelineSelection.Checks.NONE);
        }
    }

}
