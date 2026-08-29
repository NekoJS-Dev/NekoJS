package com.tkisor.nekojs.core.compiler;

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

    /**
     * 按扩展名取编译器。
     *
     * <p><b>倒序查找、后注册者胜（last-wins）：</b>从最新注册项向前遍历，第一个声明支持
     * 该扩展名的编译器获胜——后注册的插件可以用普通 {@code register} 覆盖内置编译器，
     * 无需 {@link #replaceLanguage}。注册顺序由插件 priority 决定（数值大者先注册），
     * 因此「覆盖内置」的插件通常需要<b>低于</b>内置插件的 priority（后注册），或直接用
     * {@code replaceLanguage} 显式替换以消除顺序依赖。同 priority 时顺序为扫描序（不稳定）。
     */
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

    /** 同 {@link #getCompiler(String)} 的 last-wins 语义，返回整个语言插件条目。 */
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
