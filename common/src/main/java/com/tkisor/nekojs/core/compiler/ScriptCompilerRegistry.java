package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.FrontendSemantics;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Pattern;

/** Registry mapping file extensions and language IDs to {@link NekoLanguagePlugin} and legacy {@link IScriptCompiler} frontends. */
public final class ScriptCompilerRegistry {
    private static final List<String> NATIVE_EXTENSIONS_IN_ORDER = List.of(".js", ".mjs", ".cjs");
    private static final Set<String> NATIVE_EXTENSIONS = Set.copyOf(NATIVE_EXTENSIONS_IN_ORDER);

    private static final Pattern FRONTEND_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final String NATIVE_FRONTEND_ID = "nekojs:javascript";
    private static final Candidate NATIVE_FALLBACK = new Candidate(
            new NekoScriptLanguage("javascript", NATIVE_EXTENSIONS, NekoJavaScriptLanguagePlugin.INSTANCE),
            new FrontendDescriptor(NATIVE_FRONTEND_ID, "javascript", NATIVE_EXTENSIONS, "1",
                    new FrontendSemantics(FrontendSemantics.JAVASCRIPT_SOURCE, FrontendSemantics.JAVASCRIPT_OUTPUT,
                            Set.of(FrontendSemantics.TYPESCRIPT_AUTHORING))));

    public static final ScriptCompilerRegistry INSTANCE = createRuntimeRegistry();

    private static volatile ScriptCompilerRegistry current = INSTANCE;

    private final List<IScriptCompiler> compilers = new ArrayList<>();
    private final List<Candidate> languages = new ArrayList<>();
    // The native fallback remains outside the automatic registration order.
    private final Map<String, Candidate> frontends = new LinkedHashMap<>(Map.of(NATIVE_FRONTEND_ID, NATIVE_FALLBACK));
    private final Set<String> extraExtensions = new LinkedHashSet<>();
    private boolean frozen;
    private long revision;

    private ScriptCompilerRegistry() {}

    public static ScriptCompilerRegistry createRuntimeRegistry() {
        return new ScriptCompilerRegistry();
    }

    public static ScriptCompilerRegistry current() {
        return current;
    }

    public static void useRuntime(ScriptCompilerRegistry registry) {
        current = registry == null ? INSTANCE : registry;
    }

    public synchronized void register(IScriptCompiler compiler) {
        requireMutable();
        if (compiler != null) {
            compilers.add(compiler);
            revision++;
        }
    }

    public synchronized void register(String extension, IScriptCompiler compiler) {
        requireMutable();
        extraExtensions.add(normalizeExtension(extension));
        register(compiler);
    }

    public synchronized void register(NekoScriptLanguage language) {
        requireMutable();
        if (language == null) return;
        append(new Candidate(normalizedLanguage(language), null));
    }

    /** Registers a named frontend once in both the selectable catalog and automatic lookup. */
    public synchronized void registerFrontend(String id, String implementationRevision,
                                              NekoScriptLanguage language, FrontendSemantics semantics) {
        if (frozen) {
            throw new IllegalStateException("[NEKO-1026] Frontend registration rejected after freeze: " + id);
        }
        if (id == null || !FRONTEND_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("[NEKO-1026] Invalid frontend identifier: " + id);
        }
        if (frontends.containsKey(id)) {
            throw new IllegalArgumentException("[NEKO-1026] Duplicate frontend identifier: " + id);
        }
        if (language == null || (language.compiler() == null && language.plugin() == null)
                || semantics == null || implementationRevision == null || implementationRevision.isBlank()) {
            throw new IllegalArgumentException("[NEKO-1026] Frontend registration requires an implementation, revision and semantics: " + id);
        }
        NekoScriptLanguage normalized = normalizedLanguage(language);
        if (normalized.extensions().isEmpty() || normalized.extensions().contains(".")) {
            throw new IllegalArgumentException("[NEKO-1026] Frontend registration requires supported extensions: " + id);
        }
        Candidate candidate = new Candidate(normalized,
                new FrontendDescriptor(id, normalized.id(), normalized.extensions(), implementationRevision, semantics));
        frontends.put(id, candidate);
        append(candidate);
    }

    /** Registers a complete language plugin with its own language ID and extensions. */
    public void registerFrontend(String id, String implementationRevision,
                                 NekoLanguagePlugin plugin, FrontendSemantics semantics) {
        NekoScriptLanguage language = plugin == null ? null : new NekoScriptLanguage(plugin.id(), plugin.extensions(), plugin);
        registerFrontend(id, implementationRevision, language, semantics);
    }

    private void append(Candidate candidate) {
        languages.add(candidate);
        IScriptCompiler compiler = candidate.language().compiler();
        if (compiler != null) compilers.add(compiler);
        revision++;
    }

    public synchronized void register(NekoLanguagePlugin plugin) {
        if (plugin == null) return;
        register(new NekoScriptLanguage(plugin.id(), plugin.extensions(), plugin));
    }

    public synchronized void registerLanguage(String id, Set<String> extensions, IScriptCompiler compiler) {
        register(new NekoScriptLanguage(id, extensions, compiler));
    }

    public synchronized void registerLanguage(String id, Set<String> extensions, NekoLanguagePlugin plugin) {
        register(new NekoScriptLanguage(id, extensions, plugin));
    }

    public synchronized void replaceLanguage(String id, Set<String> extensions, IScriptCompiler compiler) {
        replaceLanguage(new NekoScriptLanguage(id, extensions, compiler));
    }

    public synchronized void replaceLanguage(String id, Set<String> extensions, NekoLanguagePlugin plugin) {
        replaceLanguage(new NekoScriptLanguage(id, extensions, plugin));
    }

    public synchronized void replaceLanguage(NekoScriptLanguage replacement) {
        requireMutable();
        NekoScriptLanguage normalizedReplacement = normalizedLanguage(replacement);
        List<NekoScriptLanguage> removed = new ArrayList<>();
        languages.removeIf(candidate -> {
            NekoScriptLanguage language = candidate.language();
            boolean matches = language.id().equals(normalizedReplacement.id());
            if (matches) {
                removed.add(language);
            }
            return matches;
        });
        for (NekoScriptLanguage language : removed) {
            if (language.compiler() != null) {
                compilers.remove(language.compiler());
            }
        }
        register(normalizedReplacement);
    }

    public synchronized void registerExtension(String extension) {
        requireMutable();
        extraExtensions.add(normalizeExtension(extension));
        revision++;
    }

    /**
     * Returns the compiler for an extension.
     *
     * <p><b>The search runs backwards and the last registration wins:</b> the list is walked from the
     * newest registration down, and the first compiler that claims the extension wins. A plugin
     * registered later can therefore override a built-in compiler with a plain {@code register} call,
     * without {@link #replaceLanguage}. Registration order follows plugin priority, where a higher
     * number registers first, so a plugin that overrides a built-in usually needs a <b>lower</b>
     * priority than the built-in, or should call {@code replaceLanguage} to replace it explicitly and
     * drop the ordering dependency. At equal priority, entries are ordered by implementation class FQN and then
     * by owner id (see {@code NekoJSBasePluginManager.ORDER}); that order is fixed on one classpath
     * but depends on your class names, so do not rely on it.
     */
    public synchronized IScriptCompiler getCompiler(String extension) {
        String dotted = normalizeExtension(extension);
        String bare = dotted.substring(1);
        for (int i = compilers.size() - 1; i >= 0; i--) {
            IScriptCompiler compiler = compilers.get(i);
            if (compiler.canCompile(dotted) || compiler.canCompile(bare)) {
                return compiler;
            }
        }
        return null;
    }

    /**
     * Capture the language/plugin choice and registry revision under one registry lock.
     * Preparation must never pair a compiler read from one registry state with a revision
     * from a later replacement of that compiler.
     */
    public synchronized Capture capture(String extension) {
        return capture(extension, null);
    }

    /** Captures an exact named frontend when requested; a rejected selection never falls back. */
    public synchronized Capture capture(String extension, String frontendId) {
        String dotted = normalizeExtension(extension);
        if (frontendId != null) {
            Candidate selected = frontends.get(frontendId);
            if (selected == null || !selected.frontend().extensions().contains(dotted)) {
                List<String> available = frontends.values().stream().map(Candidate::frontend)
                        .filter(frontend -> frontend.extensions().contains(dotted))
                        .map(FrontendDescriptor::id).sorted().toList();
                String reason = selected == null ? "is not registered" : "does not support the extension";
                throw new IllegalArgumentException("[NEKO-1027] Frontend selection rejected for " + dotted
                        + ": " + frontendId + " " + reason + "; available frontends: " + available);
            }
            return captureCandidate(selected);
        }
        String bare = dotted.substring(1);
        for (int i = languages.size() - 1; i >= 0; i--) {
            Candidate candidate = languages.get(i);
            NekoScriptLanguage language = candidate.language();
            IScriptCompiler compiler = language.compiler();
            if (language.extensions().contains(dotted)
                    || compiler != null && (compiler.canCompile(dotted) || compiler.canCompile(bare))) {
                return captureCandidate(candidate);
            }
        }
        for (int i = compilers.size() - 1; i >= 0; i--) {
            IScriptCompiler compiler = compilers.get(i);
            if (compiler.canCompile(dotted) || compiler.canCompile(bare)) {
                return new Capture(null, null, compiler, revision);
            }
        }
        return new Capture(null, null, null, revision, NATIVE_EXTENSIONS.contains(dotted)
                ? NATIVE_FALLBACK.frontend() : null);
    }

    private Capture captureCandidate(Candidate candidate) {
        NekoScriptLanguage language = candidate.language();
        return new Capture(language, language.plugin(), language.compiler(), revision, candidate.frontend());
    }

    /** Returns an immutable snapshot of the named frontend catalog. */
    public synchronized List<FrontendDescriptor> frontends() {
        return frontends.values().stream().map(Candidate::frontend).toList();
    }

    /** Uses the same last-wins rule as {@link #getCompiler(String)} and returns the whole language plugin entry. */
    public synchronized NekoScriptLanguage getLanguage(String extension) {
        String dotted = normalizeExtension(extension);
        String bare = dotted.substring(1);
        for (int i = languages.size() - 1; i >= 0; i--) {
            NekoScriptLanguage language = languages.get(i).language();
            if (language.extensions().contains(dotted)) {
                return language;
            }
            IScriptCompiler compiler = language.compiler();
            if (compiler != null && (compiler.canCompile(dotted) || compiler.canCompile(bare))) {
                return language;
            }
        }
        return null;
    }

    public synchronized NekoLanguagePlugin getLanguagePlugin(String extension) {
        NekoScriptLanguage language = getLanguage(extension);
        return language == null ? null : language.plugin();
    }

    public synchronized Set<String> supportedExtensions() {
        return Set.copyOf(registeredExtensionsInOrder());
    }

    public synchronized List<String> supportedExtensionsInOrder() {
        return List.copyOf(registeredExtensionsInOrder());
    }

    public synchronized List<NekoScriptLanguage> languages() {
        return languages.stream().map(Candidate::language).toList();
    }

    public synchronized boolean isSupportedScriptExtension(String extension) {
        String normalized = normalizeExtension(extension);
        return NATIVE_EXTENSIONS.contains(normalized) || getLanguage(normalized) != null || getCompiler(normalized) != null || registeredExtensionsInOrder().contains(normalized);
    }

    public boolean isSupportedScriptFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 && isSupportedScriptExtension(fileName.substring(dotIndex));
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public synchronized boolean frozen() {
        return frozen;
    }

    /** Monotonic registration identity used to validate captured preparation bindings. */
    public synchronized long revision() {
        return revision;
    }

    public static boolean isNativeScriptExtension(String extension) {
        return NATIVE_EXTENSIONS.contains(normalizeExtension(extension));
    }

    public static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("Script extension must not be blank");
        }
        String normalized = extension.toLowerCase(Locale.ROOT).trim();
        return normalized.startsWith(".") ? normalized : "." + normalized;
    }

    private NekoScriptLanguage normalizedLanguage(NekoScriptLanguage language) {
        Set<String> extensions = new LinkedHashSet<>();
        for (String extension : language.extensions()) {
            extensions.add(normalizeExtension(extension));
        }
        return new NekoScriptLanguage(language.id(), extensions, language.compiler(), language.plugin());
    }

    private List<String> registeredExtensionsInOrder() {
        LinkedHashSet<String> extensions = new LinkedHashSet<>(NATIVE_EXTENSIONS_IN_ORDER);
        extensions.addAll(extraExtensions);
        for (Candidate candidate : languages) {
            for (String extension : candidate.language().extensions()) {
                extensions.add(normalizeExtension(extension));
            }
        }
        return List.copyOf(extensions);
    }

    private void requireMutable() {
        if (frozen) {
            throw new IllegalStateException("Script compiler registry is frozen after plugin bootstrap");
        }
    }

    private record Candidate(NekoScriptLanguage language, FrontendDescriptor frontend) {}

    /** Stable identity and semantic capabilities independent of automatic registration order. */
    public record FrontendDescriptor(String id, String languageId, Set<String> extensions,
                                     String implementationRevision, FrontendSemantics semantics) {
        public FrontendDescriptor {
            extensions = Set.copyOf(extensions);
        }
    }

    /** Atomic compiler binding and revision; the descriptor is absent for unnamed implementations. */
    public record Capture(NekoScriptLanguage language, NekoLanguagePlugin plugin,
                          IScriptCompiler compiler, long revision, FrontendDescriptor frontend) {
        public Capture(NekoScriptLanguage language, NekoLanguagePlugin plugin, IScriptCompiler compiler, long revision) {
            this(language, plugin, compiler, revision, null);
        }
    }
}
