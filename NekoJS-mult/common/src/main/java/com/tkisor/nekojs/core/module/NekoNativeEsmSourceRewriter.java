package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinker;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;
import com.tkisor.nekojs.core.module.esm.NekoEsmParser;
import com.tkisor.nekojs.core.module.esm.NekoEsmStatement;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;
import com.tkisor.nekojs.core.module.esm.NekoEsmRuntimeExpression;
import com.tkisor.nekojs.core.module.esm.NekoEsmImportDecl;
import com.tkisor.nekojs.core.module.esm.NekoEsmExportDecl;
import com.tkisor.nekojs.core.module.esm.NekoEsmBinding;
import com.tkisor.nekojs.core.module.esm.NekoEsmExportKind;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NekoNativeEsmSourceRewriter {
    private final NekoModuleResolver resolver;
    private final NekoEsmVirtualModuleRegistry virtualModules;
    /** 传递依赖准备缓存（W3 显式注入，语义同 {@link NekoEsmLinker}）。 */
    private final NekoModulePipelineCache preparationCache;
    private final SourceMapRegistry sourceMaps;

    public NekoNativeEsmSourceRewriter(NekoModuleResolver resolver, NekoModulePipelineCache preparationCache,
                                       NekoEsmVirtualModuleRegistry virtualModules,
                                       SourceMapRegistry sourceMaps) {
        this.resolver = resolver;
        this.preparationCache = preparationCache;
        this.virtualModules = virtualModules;
        this.sourceMaps = sourceMaps;
    }

    public java.net.URI registerModule(Path file, String moduleId, NekoPreparedModule prepared) throws IOException {
        return registerModule(file, moduleId, prepared, new HashSet<>());
    }

    private java.net.URI registerModule(Path file, String moduleId, NekoPreparedModule prepared, Set<String> visiting) throws IOException {
        virtualModules.reserve(moduleId);
        if (visiting.contains(moduleId)) {
            return virtualModules.uri(moduleId);
        }
        visiting.add(moduleId);
        try {
            RewriteResult rewritten = rewrite(file, moduleId, prepared, visiting);
            String source = rewritten.code();
            java.net.URI uri = virtualModules.register(moduleId, source);
            String sourceMap = preparationCache.composeRewrittenSourceMap(prepared, source, rewritten.spans());
            if (sourceMap != null && !sourceMap.isBlank()) {
                Path virtualPath = Path.of(uri);
                sourceMaps.register(virtualPath.toString(), withGeneratedFile(sourceMap, virtualPath),
                        source.equals(prepared.code()) ? prepared.prependedLineCount() : 0);
            }
            return uri;
        } finally {
            visiting.remove(moduleId);
        }
    }

    private RewriteResult rewrite(Path file, String moduleId, NekoPreparedModule prepared, Set<String> visiting) throws IOException {
        if (prepared.esmAst() == null) {
            return new RewriteResult(prepared.code(), List.of());
        }
        RewriteContext context = new RewriteContext(file, moduleId, prepared.code(), prepared.esmAst(), visiting);
        return context.rewrite();
    }

    private static String withGeneratedFile(String sourceMap, Path generatedPath) {
        try {
            var root = JsonParser.parseString(sourceMap).getAsJsonObject();
            root.addProperty("file", generatedPath.toString().replace('\\', '/'));
            return root.toString();
        } catch (RuntimeException invalidMap) {
            return sourceMap;
        }
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

        private RewriteResult rewrite() throws IOException {
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
                            // Keep literal dynamic imports on the runtime host path. Resolution,
                            // preparation, dependency recording, and virtual ESM linking must happen
                            // when import() executes, not while the parent source is rewritten.
                            replacements.add(new Replacement(literalSpan.start(), literalSpan.end(),
                                    "globalThis.__nekoScriptModuleLoaderHost.resolveNativeImport("
                                            + jsString(moduleId) + ", " + jsString(expression.specifier()) + ")"));
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
            NekoResolvedModule resolved;
            try {
                resolved = resolver.resolve(moduleId, specifier);
            } catch (NekoModuleError staged) {
                throw staged;
            } catch (IOException failure) {
                throw NekoModuleError.resolve(moduleId, specifier, failure);
            }
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
            return preparationCache.prepare(path);
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
            return virtualModules.register(specifier + namedExports, source.toString());
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
            return virtualModules.register(resolvedModuleId + "#cjs-interop" + requestedExportNames(statement), source.toString());
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

        private RewriteResult applyReplacements(List<Replacement> replacements) {
            StringBuilder output = new StringBuilder(code.length());
            List<NekoModulePipelineCache.RewriteSpan> spans = new ArrayList<>();
            int cursor = 0;
            for (Replacement replacement : replacements) {
                if (replacement.start() < cursor) {
                    throw new IllegalArgumentException("Overlapping native ESM rewrite spans in " + file);
                }
                output.append(code, cursor, replacement.start());
                int generatedStart = output.length();
                output.append(replacement.text());
                spans.add(new NekoModulePipelineCache.RewriteSpan(replacement.start(), replacement.end(),
                        generatedStart, output.length()));
                cursor = replacement.end();
            }
            output.append(code, cursor, code.length());
            return new RewriteResult(output.toString(), List.copyOf(spans));
        }
    }

    private record Replacement(int start, int end, String text) {}

    private record RewriteResult(String code, List<NekoModulePipelineCache.RewriteSpan> spans) {}

    public java.net.URI syntheticObjectModuleUri(String specifier) {
        String source = "const __neko_module = globalThis.__nekoNodeResolve(" + jsString(specifier) + ");\n"
                + "if (__neko_module === globalThis.__nekoNodeNoModule) throw new Error('Cannot resolve module: " + escapeForSingleQuoted(specifier) + "');\n"
                + "export default __neko_module;\n"
                + "export const namespace = __neko_module;\n";
        return virtualModules.register(specifier + "#dynamic", source);
    }

    /**
     * 带命名导出的特殊模块合成源：{@code import { jsx, Fragment } from 'nekojs/jsx-runtime'}
     * 这类编译器注入的命名导入需要静态 export 名（GraalJS 的 ESM 在 parse 期解析导出名），
     * 默认的 syntheticObjectModuleUri 只提供 default/namespace。
     */
    public java.net.URI syntheticNamedModuleUri(String specifier, String... exportNames) {
        StringBuilder source = new StringBuilder();
        source.append("const __neko_module = globalThis.__nekoNodeResolve(").append(jsString(specifier)).append(");\n");
        source.append("if (__neko_module === globalThis.__nekoNodeNoModule) throw new Error('Cannot resolve module: ")
                .append(escapeForSingleQuoted(specifier)).append("');\n");
        source.append("export default __neko_module;\n");
        source.append("export const namespace = __neko_module;\n");
        for (String name : exportNames) {
            source.append("export const ").append(name).append(" = __neko_module[").append(jsString(name)).append("];\n");
        }
        return virtualModules.register(specifier + "#dynamic", source.toString());
    }

    public java.net.URI syntheticCjsModuleUri(String resolvedModuleId, String parentModuleId, String specifier) {
        String source = "const __neko_exports = globalThis.__nekoScriptModuleLoaderHost.nativeImport(" + jsString(parentModuleId) + ", " + jsString(specifier) + ");\n"
                + "export default __neko_exports;\n"
                + "export const namespace = __neko_exports;\n";
        return virtualModules.register(resolvedModuleId + "#cjs-interop-dynamic", source);
    }

    public java.net.URI syntheticJsonModuleUri(Path path) throws IOException {
        String json = preparationCache.prepareJson(path);
        String source = "const __neko_json = JSON.parse(" + jsString(json) + ");\nexport default __neko_json;\n";
        return virtualModules.register(moduleId(path), source);
    }

    private String moduleId(Path path) {
        Path absolute = path.normalize().toAbsolutePath();
        try {
            return virtualModules.root().getParent().relativize(absolute).toString().replace('\\', '/');
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
