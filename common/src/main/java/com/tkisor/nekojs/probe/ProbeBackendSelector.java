package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry.FrontendDescriptor;
import com.tkisor.nekojs.core.config.EngineConfigException;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves generation requests against the registry and the engine selection frozen at startup. */
public final class ProbeBackendSelector {
    private static final String TYPESCRIPT = "typescript";
    private static final String BUILTIN = "builtin";

    private final ProbeBackendRegistry registry;
    private final Map<String, Explicit> configured;

    private ProbeBackendSelector(ProbeBackendRegistry registry, Map<String, Explicit> configured) {
        this.registry = registry;
        this.configured = Collections.unmodifiableMap(new LinkedHashMap<>(configured));
    }

    /** Validates explicit pipeline references after frontend and backend collection have frozen. */
    public static ProbeBackendSelector resolve(Path engineConfig, SandboxConfig config,
                                               Map<String, FrontendDescriptor> frontends,
                                               ProbeBackendRegistry registry) {
        if (!registry.isLocked()) {
            throw new EngineConfigException(engineConfig, "Cannot resolve Probe selection before backend registration is frozen");
        }
        Map<String, Explicit> selected = new LinkedHashMap<>();
        config.languagePipelines().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String extension = entry.getKey();
            var reference = entry.getValue().probe();
            if (reference == null) return;
            String target = reference.language();
            String key = target + ":" + reference.backend();
            ProbeBackend backend = registry.backend(target, reference.backend()).orElseThrow(() ->
                    new EngineConfigException(engineConfig, "Probe backend " + key + " for " + extension
                            + " is not registered; available backends: " + registry.registrars()));
            var frontend = frontends.get(extension);
            Set<String> offered = frontend == null ? Set.of() : frontend.semantics().capabilities();
            Set<String> supported;
            try {
                supported = Set.copyOf(backend.authoringCapabilities());
            } catch (RuntimeException failure) {
                throw new EngineConfigException(engineConfig, "Could not read Probe capabilities of " + key
                        + " for " + extension, failure);
            }
            if (Collections.disjoint(offered, supported)) {
                throw new EngineConfigException(engineConfig, "Probe backend " + key + " for " + extension
                        + " has no compatible authoring capability (frontend: " + offered + ", backend: " + supported + ")");
            }
            var previous = selected.putIfAbsent(target, new Explicit(extension, backend));
            if (previous != null && previous.backend() != backend) {
                throw new EngineConfigException(engineConfig, "Conflicting Probe backends for " + target
                        + ": " + previous.extension() + " selects " + backendKey(previous.backend())
                        + ", but " + extension + " selects " + key);
            }
        });
        return new ProbeBackendSelector(registry, selected);
    }

    /** Legacy entry points without a runtime root do not apply engine overrides. */
    public static ProbeBackendSelector legacy() {
        return new ProbeBackendSelector(ProbeBackendRegistry.get(), Map.of());
    }

    public boolean hasEngineReferences() {
        return !configured.isEmpty();
    }

    /** Checks actual directories before writes; ancestor directories would also remove each other's files. */
    public void validateConfiguredOutputs(Path engineConfig, NekoJSPaths paths, ProbeConfig config) {
        try {
            configuredOutputs(paths, config);
        } catch (RuntimeException failure) {
            throw new EngineConfigException(engineConfig, "Invalid configured Probe outputs: " + failure.getMessage(), failure);
        }
    }

    Map<String, Path> configuredOutputs(NekoJSPaths paths, ProbeConfig config) {
        Map<String, Path> resolved = new LinkedHashMap<>();
        for (var entry : configured.values()) {
            var backend = entry.backend();
            String key = backendKey(backend);
            Path output;
            try {
                output = backend.outputDir(paths, config).toAbsolutePath().normalize();
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException("Cannot resolve output for " + key + " (" + entry.extension() + ")", failure);
            }
            for (var previous : resolved.entrySet()) {
                Path prior = previous.getValue();
                if (overlaps(output, prior)
                        || ProbeOutputCommitter.legacyCleanupDirs(prior).stream().anyMatch(dir -> overlaps(output, dir))
                        || ProbeOutputCommitter.legacyCleanupDirs(output).stream().anyMatch(dir -> overlaps(prior, dir))) {
                    throw new IllegalArgumentException("Probe output for " + key + " (" + output
                            + ") overlaps " + backendKey(configured.get(previous.getKey()).backend())
                            + " (" + previous.getValue() + ")");
                }
            }
            resolved.put(backend.languageId(), output);
        }
        return Map.copyOf(resolved);
    }

    private static boolean overlaps(Path left, Path right) {
        return left.startsWith(right) || right.startsWith(left);
    }

    /** With an explicit name only the exact registered tuple is eligible. */
    List<ProbeBackend> select(String language, String name, ProbeConfig config) {
        if (name != null) return registry.backend(language, name).stream().toList();
        String target = language == null ? TYPESCRIPT : language;
        Explicit explicit = configured.get(target);
        if (explicit != null) return List.of(explicit.backend());
        if (language == null) return registry.backend(target, BUILTIN).stream().toList();
        var preferred = config.language(target).map(ProbeConfig.LanguageConfig::backend)
                .filter(value -> !value.isBlank()).flatMap(value -> registry.backend(target, value));
        return preferred.or(() -> registry.defaultBackend(target)).stream().toList();
    }

    boolean selectsEngineReference(String language, String name) {
        return name == null && configured.containsKey(language == null ? TYPESCRIPT : language);
    }

    /** All registered backends, in the original language and backend order, even with engine overrides. */
    List<ProbeBackend> selectAll() {
        List<ProbeBackend> result = new ArrayList<>();
        for (String language : registry.languages()) result.addAll(registry.backendsFor(language));
        return List.copyOf(result);
    }

    static String backendKey(ProbeBackend backend) {
        return backend.languageId() + ":" + backend.name();
    }

    public static List<ProbeBackend> defaultTypescript() {
        return legacy().select(null, null, ProbeConfig.defaultConfig());
    }

    public static List<ProbeBackend> all() {
        return legacy().selectAll();
    }

    public static List<ProbeBackend> forLanguage(String languageId) {
        return legacy().select(languageId, null, ProbeCoordinator.config());
    }

    public static List<ProbeBackend> named(String languageId, String name) {
        return legacy().select(languageId, name, ProbeConfig.defaultConfig());
    }

    public static List<String> languageSuggestions() {
        return List.copyOf(ProbeBackendRegistry.get().languages());
    }

    public static List<ProbeBackend> nameSuggestions(String languageId) {
        return ProbeBackendRegistry.get().backendsFor(languageId);
    }

    private record Explicit(String extension, ProbeBackend backend) {}
}
