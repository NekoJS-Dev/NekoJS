package com.tkisor.nekojs.api.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ScriptCompilerRegistry {
    private static final List<String> NATIVE_EXTENSIONS_IN_ORDER = List.of(".js", ".mjs", ".cjs");
    private static final Set<String> NATIVE_EXTENSIONS = Set.copyOf(NATIVE_EXTENSIONS_IN_ORDER);

    public static final ScriptCompilerRegistry INSTANCE = createRuntimeRegistry();

    private static volatile ScriptCompilerRegistry current = INSTANCE;

    private final List<IScriptCompiler> compilers = new ArrayList<>();
    private final List<NekoScriptLanguage> languages = new ArrayList<>();
    private final Set<String> extraExtensions = new LinkedHashSet<>();
    private boolean frozen;

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

    public void register(IScriptCompiler compiler) {
        requireMutable();
        if (compiler != null) {
            compilers.add(compiler);
        }
    }

    public void register(String extension, IScriptCompiler compiler) {
        requireMutable();
        extraExtensions.add(normalizeExtension(extension));
        register(compiler);
    }

    public void register(NekoScriptLanguage language) {
        requireMutable();
        if (language == null) return;
        NekoScriptLanguage normalized = normalizedLanguage(language);
        languages.add(normalized);
        if (normalized.compiler() != null) {
            compilers.add(normalized.compiler());
        }
    }

    public void register(NekoLanguagePlugin plugin) {
        if (plugin == null) return;
        register(new NekoScriptLanguage(plugin.id(), plugin.extensions(), plugin));
    }

    public void registerLanguage(String id, Set<String> extensions, IScriptCompiler compiler) {
        register(new NekoScriptLanguage(id, extensions, compiler));
    }

    public void registerLanguage(String id, Set<String> extensions, NekoLanguagePlugin plugin) {
        register(new NekoScriptLanguage(id, extensions, plugin));
    }

    public void replaceLanguage(String id, Set<String> extensions, IScriptCompiler compiler) {
        replaceLanguage(new NekoScriptLanguage(id, extensions, compiler));
    }

    public void replaceLanguage(String id, Set<String> extensions, NekoLanguagePlugin plugin) {
        replaceLanguage(new NekoScriptLanguage(id, extensions, plugin));
    }

    public void replaceLanguage(NekoScriptLanguage replacement) {
        requireMutable();
        NekoScriptLanguage normalizedReplacement = normalizedLanguage(replacement);
        List<NekoScriptLanguage> removed = new ArrayList<>();
        languages.removeIf(language -> {
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

    public void registerExtension(String extension) {
        requireMutable();
        extraExtensions.add(normalizeExtension(extension));
    }

    public IScriptCompiler getCompiler(String extension) {
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

    public NekoScriptLanguage getLanguage(String extension) {
        String dotted = normalizeExtension(extension);
        String bare = dotted.substring(1);
        for (int i = languages.size() - 1; i >= 0; i--) {
            NekoScriptLanguage language = languages.get(i);
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

    public NekoLanguagePlugin getLanguagePlugin(String extension) {
        NekoScriptLanguage language = getLanguage(extension);
        return language == null ? null : language.plugin();
    }

    public Set<String> supportedExtensions() {
        return Set.copyOf(registeredExtensionsInOrder());
    }

    public List<String> supportedExtensionsInOrder() {
        return List.copyOf(registeredExtensionsInOrder());
    }

    public List<NekoScriptLanguage> languages() {
        return List.copyOf(languages);
    }

    public boolean isSupportedScriptExtension(String extension) {
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

    public void freeze() {
        frozen = true;
    }

    public boolean frozen() {
        return frozen;
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
        for (NekoScriptLanguage language : languages) {
            for (String extension : language.extensions()) {
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
}
