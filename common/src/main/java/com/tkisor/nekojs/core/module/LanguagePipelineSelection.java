package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.compiler.FrontendSemantics;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Descriptor;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Scope;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Stage;
import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.EngineConfigException;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.preflight.BuiltinJavaScriptPreflight;
import com.tkisor.nekojs.core.preflight.ScriptPreflightCatalog;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves every configured extension against the frozen plugin products before script discovery. */
public final class LanguagePipelineSelection {
    private final Map<String, Effective> configured;
    private final ScriptPreflightCatalog checks;

    private LanguagePipelineSelection(Map<String, Effective> configured, ScriptPreflightCatalog checks) {
        this.configured = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(configured));
        this.checks = checks;
    }

    public static LanguagePipelineSelection resolve(Path path, SandboxConfig config,
                                                     ScriptCompilerRegistry compilers, ScriptPreflightCatalog checks) {
        if (!compilers.frozen()) {
            throw new IllegalStateException("[NEKO-1030] Cannot resolve pipelines before frontend registration is frozen: " + path);
        }
        Map<String, Effective> effective = new LinkedHashMap<>();
        config.languagePipelines().forEach((extension, setting) -> {
            ScriptCompilerRegistry.Capture capture;
            try {
                capture = compilers.capture(extension, setting.frontend());
            } catch (RuntimeException failure) {
                throw new EngineConfigException(path, "Frontend selection failed for " + extension
                        + ": " + failure.getMessage(), failure);
            }
            if (".json".equals(extension) || (capture.plugin() == null && capture.compiler() == null && capture.frontend() == null)) {
                throw new EngineConfigException(path, "No executable frontend for " + extension
                        + "; available extensions: " + compilers.supportedExtensionsInOrder());
            }
            var semantics = capture.frontend() == null ? null : capture.frontend().semantics();
            var defaults = defaults(capture, checks);
            var source = resolveChecks(path, extension, Stage.SOURCE, setting.sourceChecks(), semantics, defaults.source(), checks);
            var output = resolveChecks(path, extension, Stage.OUTPUT, setting.outputChecks(), semantics, defaults.output(), checks);
            effective.put(extension, new Effective(capture, new Checks(source, output)));
        });
        return new LanguagePipelineSelection(effective, checks);
    }

    static LanguagePipelineSelection defaultsOnly() {
        var collector = new ScriptPreflightCatalog.Collector();
        collector.register(BuiltinJavaScriptPreflight.INSTANCE);
        return new LanguagePipelineSelection(Map.of(), collector.freeze());
    }

    private static List<Descriptor> resolveChecks(Path path, String extension, Stage stage, List<String> requested,
                                                   FrontendSemantics semantics, List<Descriptor> defaults,
                                                   ScriptPreflightCatalog catalog) {
        if (requested == null) {
            // No default checker can consume a declared non-JavaScript artifact.
            if (stage == Stage.OUTPUT && semantics != null && !FrontendSemantics.JAVASCRIPT_OUTPUT.equals(semantics.output())) {
                return List.of();
            }
            return defaults;
        }
        return requested.stream().map(id -> {
            var candidate = catalog.find(id).orElse(null);
            if (candidate == null || !compatible(candidate, stage, semantics)) {
                var available = catalog.descriptors().values().stream()
                        .filter(descriptor -> compatible(descriptor, stage, semantics)).map(Descriptor::id).sorted().toList();
                throw new EngineConfigException(path, "Check selection rejected for " + extension + " " + stage
                        + ": " + id + (candidate == null ? " is not registered" : " is incompatible with " + semantics)
                        + "; compatible checks: " + available);
            }
            return candidate;
        }).toList();
    }

    private static boolean compatible(Descriptor descriptor, Stage stage, FrontendSemantics semantics) {
        if (semantics == null) return false;
        var input = stage == Stage.SOURCE ? semantics.source() : semantics.output();
        return descriptor.supports(stage, Scope.FULL, input, semantics.capabilities())
                && descriptor.supports(stage, Scope.BINDINGS_ONLY, input, semantics.capabilities());
    }

    Effective capture(String extension, ScriptCompilerRegistry compilers) {
        var selection = configured.get(extension);
        if (selection != null) return selection;
        var capture = compilers.capture(extension);
        return new Effective(capture, defaults(capture, checks));
    }

    private static Checks defaults(ScriptCompilerRegistry.Capture capture, ScriptPreflightCatalog catalog) {
        var builtin = catalog.find(BuiltinJavaScriptPreflight.ID).stream().toList();
        boolean nativeJs = capture.plugin() == NekoJavaScriptLanguagePlugin.INSTANCE
                || capture.plugin() == null && capture.compiler() == null && capture.frontend() != null;
        if (nativeJs) return new Checks(builtin, List.of());
        var frontend = capture.frontend();
        return new Checks(List.of(), frontend == null || FrontendSemantics.JAVASCRIPT_OUTPUT.equals(frontend.semantics().output())
                ? builtin : List.of());
    }

    /** Only validated explicit extensions augment the existing automatic discovery policy. */
    public Set<String> configuredExtensions() {
        return configured.keySet();
    }

    /** The frontend descriptor actually frozen for each configured extension, if it has one. */
    public Map<String, ScriptCompilerRegistry.FrontendDescriptor> configuredFrontends() {
        var result = new LinkedHashMap<String, ScriptCompilerRegistry.FrontendDescriptor>();
        configured.forEach((extension, effective) -> {
            var frontend = effective.capture().frontend();
            if (frontend != null) result.put(extension, frontend);
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    ScriptPreflightCatalog checks() {
        return checks;
    }

    record Effective(ScriptCompilerRegistry.Capture capture, Checks checks) {}

    record Checks(List<Descriptor> source, List<Descriptor> output) {
        static final Checks NONE = new Checks(List.of(), List.of());
    }
}
