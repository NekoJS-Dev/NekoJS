package com.tkisor.nekojs.api.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ScriptCompilerRegistry {
    private static final Set<String> BUILTIN_EXTENSIONS = Set.of(".js", ".mjs", ".cjs", ".ts", ".jsx", ".tsx");
    private static final List<IScriptCompiler> COMPILERS = new ArrayList<>();
    private static final Set<String> REGISTERED_EXTENSIONS = new LinkedHashSet<>(BUILTIN_EXTENSIONS);

    public static final ScriptCompilerRegistry INSTANCE = new ScriptCompilerRegistry();

    private ScriptCompilerRegistry() {}

    public static void register(IScriptCompiler compiler) {
        if (compiler != null) {
            COMPILERS.add(compiler);
        }
    }

    public static void register(String extension, IScriptCompiler compiler) {
        String normalized = normalizeExtension(extension);
        REGISTERED_EXTENSIONS.add(normalized);
        register(compiler);
    }

    public static void registerExtension(String extension) {
        REGISTERED_EXTENSIONS.add(normalizeExtension(extension));
    }

    public static IScriptCompiler getCompiler(String extension) {
        String dotted = normalizeExtension(extension);
        String bare = dotted.substring(1);
        for (IScriptCompiler compiler : COMPILERS) {
            if (compiler.canCompile(dotted) || compiler.canCompile(bare)) {
                return compiler;
            }
        }
        return null;
    }

    public static Set<String> supportedExtensions() {
        return Set.copyOf(REGISTERED_EXTENSIONS);
    }

    public static List<String> supportedExtensionsInOrder() {
        return List.copyOf(REGISTERED_EXTENSIONS);
    }

    public static boolean isSupportedScriptExtension(String extension) {
        return REGISTERED_EXTENSIONS.contains(normalizeExtension(extension));
    }

    public static boolean isSupportedScriptFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 && isSupportedScriptExtension(fileName.substring(dotIndex));
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("Script extension must not be blank");
        }
        String normalized = extension.toLowerCase(Locale.ROOT).trim();
        return normalized.startsWith(".") ? normalized : "." + normalized;
    }
}
