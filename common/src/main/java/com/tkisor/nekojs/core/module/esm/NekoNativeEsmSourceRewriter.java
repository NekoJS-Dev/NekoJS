package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.api.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.module.cache.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.module.NekoPreparedModule;
import com.tkisor.nekojs.core.module.NekoResolvedModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NekoNativeEsmSourceRewriter {
    private final NekoModuleResolver resolver;

    public NekoNativeEsmSourceRewriter(NekoModuleResolver resolver) {
        this.resolver = resolver;
    }

    public java.net.URI registerModule(Path file, String moduleId, NekoPreparedModule prepared) throws IOException {
        return registerModule(file, moduleId, prepared, new HashSet<>());
    }

    private java.net.URI registerModule(Path file, String moduleId, NekoPreparedModule prepared, Set<String> visiting) throws IOException {
        NekoEsmVirtualModuleRegistry.reserve(moduleId);
        if (visiting.contains(moduleId)) {
            return NekoEsmVirtualModuleRegistry.uri(moduleId);
        }
        visiting.add(moduleId);
        try {
            String source = rewrite(file, moduleId, prepared, visiting);
            return NekoEsmVirtualModuleRegistry.register(moduleId, source);
        } finally {
            visiting.remove(moduleId);
        }
    }

    private String rewrite(Path file, String moduleId, NekoPreparedModule prepared, Set<String> visiting) throws IOException {
        if (prepared.esmAst() == null) {
            return prepared.code();
        }
        RewriteContext context = new RewriteContext(file, moduleId, prepared.code(), prepared.esmAst(), visiting);
        return context.rewrite();
    }

    private final class RewriteContext {
        private final Path file;
        private final String moduleId;
        private final String code;
        private final NekoEsmModuleAst ast;
        private final Set<String> visiting;

        private RewriteContext(Path file, String moduleId, String code, NekoEsmModuleAst ast, Set<String> visiting) {
            this.file = file;
            this.moduleId = moduleId;
            this.code = code == null ? "" : code;
            this.ast = ast;
            this.visiting = visiting;
        }

        private String rewrite() throws IOException {
            List<Replacement> replacements = new ArrayList<>();
            for (NekoEsmStatement statement : ast.statements()) {
                String specifier = specifier(statement);
                if (specifier == null) {
                    continue;
                }
                NekoEsmSpan literalSpan = specifierLiteralSpan(statement);
                replacements.add(new Replacement(literalSpan.start(), literalSpan.end(), jsString(rewrittenSpecifier(statement, specifier))));
            }
            for (NekoEsmRuntimeExpression expression : ast.runtimeExpressions()) {
                NekoEsmSpan span = expression.span();
                switch (expression.kind()) {
                    case IMPORT_META_URL -> replacements.add(new Replacement(span.start(), span.end(), jsString(file.toUri().toString())));
                    case IMPORT_META_FILENAME -> replacements.add(new Replacement(span.start(), span.end(), jsString(file.toAbsolutePath().normalize().toString().replace('\\', '/'))));
                    case IMPORT_META_DIRNAME -> replacements.add(new Replacement(span.start(), span.end(), jsString(file.toAbsolutePath().normalize().getParent().toString().replace('\\', '/'))));
                    case IMPORT_META_RESOLVE -> replacements.add(new Replacement(span.start(), span.end(), "(specifier => globalThis.__nekoScriptModuleLoaderHost.resolveImportMeta(" + jsString(moduleId) + ", String(specifier)))"));
                    case DYNAMIC_IMPORT -> {
                        if (expression.specifier() != null && expression.specifierLiteralSpan() != null) {
                            NekoEsmSpan literalSpan = expression.specifierLiteralSpan();
                            replacements.add(new Replacement(literalSpan.start(), literalSpan.end(), jsString(rewrittenSpecifier(expression.specifier()))));
                        } else {
                            replacements.add(new Replacement(span.start(), span.end(), "(specifier => import(globalThis.__nekoScriptModuleLoaderHost.resolveNativeImport(" + jsString(moduleId) + ", String(specifier))))"));
                        }
                    }
                }
            }
            replacements.sort(Comparator.comparingInt(Replacement::start));
            return applyReplacements(replacements);
        }

        private String rewrittenSpecifier(NekoEsmStatement statement, String specifier) throws IOException {
            return rewrittenSpecifier(specifier, statement);
        }

        private String rewrittenSpecifier(String specifier) throws IOException {
            return rewrittenSpecifier(specifier, null);
        }

        private String rewrittenSpecifier(String specifier, NekoEsmStatement statement) throws IOException {
            NekoResolvedModule resolved = resolver.resolve(moduleId, specifier);
            if (resolved.special()) {
                return syntheticObjectModule(statement, resolved.specifier()).toString();
            }
            if (resolved.json()) {
                return syntheticJsonModule(resolved.path()).toString();
            }
            NekoPreparedModule prepared = prepareResolvedModule(resolved.path());
            if (prepared.mode() == NekoModuleMode.ESM) {
                return registerModule(resolved.path(), resolved.id(), prepared, visiting).toString();
            }
            return syntheticCjsModule(moduleId, specifier, resolved.id(), statement).toString();
        }

        private NekoPreparedModule prepareResolvedModule(Path path) throws IOException {
            return NekoModulePipelineCache.prepare(path);
        }

        private String specifier(NekoEsmStatement statement) {
            if (statement instanceof NekoEsmImportDecl importDecl) {
                return importDecl.specifier();
            }
            if (statement instanceof NekoEsmExportDecl exportDecl) {
                return exportDecl.specifier();
            }
            return null;
        }

        private NekoEsmSpan specifierLiteralSpan(NekoEsmStatement statement) {
            if (statement instanceof NekoEsmImportDecl importDecl && importDecl.specifierSpan() != null) {
                return importDecl.specifierSpan();
            }
            if (statement instanceof NekoEsmExportDecl exportDecl && exportDecl.specifierSpan() != null) {
                return exportDecl.specifierSpan();
            }
            throw new IllegalArgumentException("Missing ESM module specifier span in " + file + ": " + oneLine(code.substring(statement.span().start(), statement.span().end())));
        }

        private java.net.URI syntheticObjectModule(NekoEsmStatement statement, String specifier) {
            Set<String> namedExports = statement == null ? Set.of() : requestedExportNames(statement);
            StringBuilder source = new StringBuilder();
            source.append("const __neko_module = globalThis.__nekoNodeResolve(").append(jsString(specifier)).append(");\n")
                    .append("if (__neko_module === globalThis.__nekoNodeNoModule) throw new Error('Cannot resolve module: ").append(escapeForSingleQuoted(specifier)).append("');\n")
                    .append("export default __neko_module;\n");
            for (String name : namedExports) {
                if (isIdentifier(name)) {
                    source.append("export const ").append(name).append(" = __neko_module[").append(jsString(name)).append("];\n");
                }
            }
            return NekoEsmVirtualModuleRegistry.register(specifier + namedExports, source.toString());
        }

        private java.net.URI syntheticJsonModule(Path path) throws IOException {
            return syntheticJsonModuleUri(path);
        }

        private java.net.URI syntheticCjsModule(String parentModuleId, String specifier, String resolvedModuleId, NekoEsmStatement statement) {
            StringBuilder source = new StringBuilder("const __neko_exports = globalThis.__nekoScriptModuleLoaderHost.nativeImport(" + jsString(parentModuleId) + ", " + jsString(specifier) + ");\n"
                    + "export default __neko_exports;\n"
                    + "export const namespace = __neko_exports;\n");
            for (String name : requestedExportNames(statement)) {
                if (isIdentifier(name) && !"default".equals(name) && !"namespace".equals(name)) {
                    source.append("export const ").append(name).append(" = __neko_exports[").append(jsString(name)).append("];\n");
                }
            }
            return NekoEsmVirtualModuleRegistry.register(resolvedModuleId + "#cjs-interop" + requestedExportNames(statement), source.toString());
        }

        private Set<String> requestedExportNames(NekoEsmStatement statement) {
            Set<String> names = new LinkedHashSet<>();
            if (statement instanceof NekoEsmImportDecl importDecl) {
                for (NekoEsmBinding binding : importDecl.namedBindings()) {
                    names.add(binding.imported());
                }
            } else if (statement instanceof NekoEsmExportDecl exportDecl) {
                if (exportDecl.kind() == NekoEsmExportKind.RE_EXPORT_LIST) {
                    for (NekoEsmBinding binding : exportDecl.bindings()) {
                        names.add(binding.imported());
                    }
                }
            }
            return names;
        }

        private String applyReplacements(List<Replacement> replacements) {
            StringBuilder output = new StringBuilder(code.length());
            int cursor = 0;
            for (Replacement replacement : replacements) {
                if (replacement.start() < cursor) {
                    throw new IllegalArgumentException("Overlapping native ESM rewrite spans in " + file);
                }
                output.append(code, cursor, replacement.start());
                output.append(replacement.text());
                cursor = replacement.end();
            }
            output.append(code, cursor, code.length());
            return output.toString();
        }
    }

    private record Replacement(int start, int end, String text) {}

    public java.net.URI syntheticObjectModuleUri(String specifier) {
        String source = "const __neko_module = globalThis.__nekoNodeResolve(" + jsString(specifier) + ");\n"
                + "if (__neko_module === globalThis.__nekoNodeNoModule) throw new Error('Cannot resolve module: " + escapeForSingleQuoted(specifier) + "');\n"
                + "export default __neko_module;\n"
                + "export const namespace = __neko_module;\n";
        return NekoEsmVirtualModuleRegistry.register(specifier + "#dynamic", source);
    }

    public java.net.URI syntheticCjsModuleUri(String resolvedModuleId, String parentModuleId, String specifier) {
        String source = "const __neko_exports = globalThis.__nekoScriptModuleLoaderHost.nativeImport(" + jsString(parentModuleId) + ", " + jsString(specifier) + ");\n"
                + "export default __neko_exports;\n"
                + "export const namespace = __neko_exports;\n";
        return NekoEsmVirtualModuleRegistry.register(resolvedModuleId + "#cjs-interop-dynamic", source);
    }

    public java.net.URI syntheticJsonModuleUri(Path path) throws IOException {
        String json = Files.readString(path);
        String source = "const __neko_json = JSON.parse(" + jsString(json) + ");\nexport default __neko_json;\n";
        return NekoEsmVirtualModuleRegistry.register(moduleId(path), source);
    }

    private static String moduleId(Path path) {
        Path absolute = path.normalize().toAbsolutePath();
        try {
            return com.tkisor.nekojs.core.fs.NekoJSPaths.get().root().relativize(absolute).toString().replace('\\', '/');
        } catch (IllegalArgumentException ignored) {
            return absolute.toString().replace('\\', '/');
        }
    }

    private static boolean isIdentifier(String value) {
        if (value == null || value.isBlank()) return false;
        char first = value.charAt(0);
        if (first != '_' && first != '$' && !Character.isLetter(first)) return false;
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '_' && c != '$' && !Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

    private static String jsString(String value) {
        return "'" + escapeForSingleQuoted(value) + "'";
    }

    private static String escapeForSingleQuoted(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String oneLine(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
