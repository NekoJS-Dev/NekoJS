package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Position;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Scope;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Stage;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.ScriptPathProvider;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;
import com.tkisor.nekojs.core.preflight.BuiltinJavaScriptPreflight;
import com.tkisor.nekojs.core.preflight.ScriptPreflightRunner;
import com.tkisor.nekojs.core.preflight.ScriptPreflightRunner.Report;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Dispatches preflight and bridges its findings to the current generation's diagnostic sink. */
final class PipelinePreflight {
    private final ScriptPreflightRunner runner;
    private final Path root;

    PipelinePreflight(LanguagePipelineSelection selection, SourceMapRegistry sourceMaps) {
        runner = new ScriptPreflightRunner(selection.checks(), sourceMaps);
        root = sourceMaps.root();
    }

    List<Report> check(Path file, ScriptType type, NekoModulePipeline.LanguageBinding binding,
                       Stage stage, Scope scope, String source, String map, ScriptBindingSchema.View schema)
            throws NekoModuleError {
        var checks = stage == Stage.SOURCE ? binding.checks().source() : binding.checks().output();
        if (checks.isEmpty()) return List.of();
        var frontend = binding.frontend();
        var semantics = frontend == null ? null : frontend.semantics();
        var input = semantics == null ? com.tkisor.nekojs.api.compiler.FrontendSemantics.JAVASCRIPT_OUTPUT
                : stage == Stage.SOURCE ? semantics.source() : semantics.output();
        var request = new com.tkisor.nekojs.api.preflight.ScriptPreflight.Request(file, type,
                frontend == null ? null : frontend.id(), stage, scope, input,
                semantics == null ? Set.of() : semantics.capabilities(), source, map, schema.schemas(), schema.globals());
        var findings = new java.util.ArrayList<Report>();
        for (var check : checks) {
            try {
                findings.addAll(runner.run(check.id(), request));
            } catch (ScriptPreflightRunner.ProviderFailure failure) {
                throw NekoModuleError.prepare(NekoModuleError.displayPath(file), binding.identity().languageId(),
                        binding.identity().requestedMode(), failure.getMessage(), failure);
            }
        }
        return findings;
    }

    void publish(Path file, ScriptType type, ScriptBindingSchema.View schema, List<Report> reports) {
        // The batch contains only this operation's findings. Never suppress a later entry recheck.
        Set<Finding> published = new HashSet<>();
        for (Report report : reports) {
            var diagnostic = report.diagnostic();
            var mapped = report.authoredPosition();
            var position = mapped == null ? diagnostic.position() : mapped.position();
            var path = mapped == null ? file : resolvePath(mapped.path(), file);
            var key = new Finding(report.providerId(), report.providerRevision(), report.stage(), diagnostic.code(),
                    diagnostic.dedupKey(), diagnostic.message(), diagnostic.severity(), path,
                    position == null ? -1 : position.line(), position == null ? -1 : position.column(),
                    diagnostic.position() == null ? -1 : diagnostic.position().offset());
            if (!published.add(key)) continue;
            boolean builtin = BuiltinJavaScriptPreflight.ID.equals(report.providerId());
            String kind = builtin ? diagnostic.dedupKey()
                    : "preflight " + report.providerId() + " " + report.stage() + " " + diagnostic.code();
            String message = builtin ? diagnostic.message() : "[NEKO-1031] " + diagnostic.severity()
                    + " from " + report.providerId() + "@" + report.providerRevision()
                    + " (" + report.stage() + "/" + report.scope() + ", " + diagnostic.code() + "): " + diagnostic.message();
            schema.report(type, kind, asDiagnostic(path, position, message));
        }
    }

    private Path resolvePath(String text, Path fallback) {
        try {
            Path path = Path.of(text);
            return path.isAbsolute() ? path.normalize() : root.resolve(path).normalize();
        } catch (InvalidPathException ignored) {
            return fallback;
        }
    }

    static ScriptType scriptType(Path file, ScriptType sessionType) {
        return sessionType != null ? sessionType : ScriptPathProvider.typeOf(file);
    }

    private static Throwable asDiagnostic(Path file, Position position, String message) {
        if (position == null) return new IllegalArgumentException(message);
        // Source coordinates retain the original UTF-16 offset; a mapped point may only have line/column.
        var span = position.offset() < 0 ? new NekoEsmSpan(0, 0)
                : new NekoEsmSpan(position.offset(), position.offset());
        return new NekoEsmLinkException(new NekoEsmDiagnostic(file, span, position.line(), position.column(), message));
    }

    void close() throws ScriptPreflightRunner.ProviderFailure {
        runner.close();
    }

    private record Finding(String provider, String revision, Stage stage, String code, String dedupKey,
                           String message, com.tkisor.nekojs.api.preflight.ScriptPreflight.Severity severity,
                           Path path, int line, int column, int inputOffset) {}
}
