package com.tkisor.nekojs.probe;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves backends for the {@code /nekojs probe} subcommands, shared by the command layer of every
 * platform. {@link ProbeBackendRegistry} only registers, looks up and checks conflicts; this class
 * owns the per-language default, the per-language config override, the cross-language select-all and
 * the completion candidates.
 *
 * <p>Selection rules:
 * <ul>
 *   <li>No argument: only {@code typescript:builtin} runs ({@code "builtin"} is the string every
 *       platform command layer passes)</li>
 *   <li>{@code <language>}: the backend name from {@code probe.toml [languages.<lang>].backend} wins,
 *       matched on exactly (language, name) and falling back to the registry default when it is not
 *       registered; with no config, the highest-priority backend of that language</li>
 *   <li>{@code <language> <name>}: exactly that (language, name)</li>
 * </ul>
 */
public final class ProbeBackendSelector {

    private ProbeBackendSelector() {
    }

    /** {@code /nekojs probe} with no argument: runs only the TypeScript builtin backend. */
    public static List<ProbeBackend> defaultTypescript() {
        return ProbeBackendRegistry.get().backend("typescript", "builtin")
                .map(List::of).orElse(List.of());
    }

    /** {@code /nekojs probe all}: every registered backend across all languages. */
    public static List<ProbeBackend> all() {
        ProbeBackendRegistry registry = ProbeBackendRegistry.get();
        List<ProbeBackend> result = new ArrayList<>();
        for (String lang : registry.languages()) {
            result.addAll(registry.backendsFor(lang));
        }
        return result;
    }

    /** {@code /nekojs probe <language>}: the per-language config wins, otherwise the highest-priority
     *  backend of that language. */
    public static List<ProbeBackend> forLanguage(String languageId) {
        ProbeBackendRegistry registry = ProbeBackendRegistry.get();
        // The per-language config (probe.toml [languages.<lang>].backend) wins: a configured backend name
        // selects that (language, name) exactly, and falls back to the language default (highest
        // priority) when it is not registered. With no config the registry default is used.
        var langCfg = ProbeCoordinator.config().language(languageId);
        if (langCfg.isPresent()) {
            String configuredName = langCfg.get().backend();
            if (configuredName != null && !configuredName.isBlank()) {
                var configured = registry.backend(languageId, configuredName);
                if (configured.isPresent()) return List.of(configured.get());
            }
        }
        return registry.defaultBackend(languageId)
                .map(List::of).orElse(List.of());
    }

    /** {@code /nekojs probe <language> <name>}: exactly that (language, name). */
    public static List<ProbeBackend> named(String languageId, String name) {
        return ProbeBackendRegistry.get().backend(languageId, name)
                .map(List::of).orElse(List.of());
    }

    /** Returns {@code <language>} completion candidates: the registered languages, which the registry
     *  already sorts. */
    public static List<String> languageSuggestions() {
        return List.copyOf(ProbeBackendRegistry.get().languages());
    }

    /** Name completion candidates for one language, highest priority first, the same order they are shown
     *  in. */
    public static List<ProbeBackend> nameSuggestions(String languageId) {
        return ProbeBackendRegistry.get().backendsFor(languageId);
    }
}
