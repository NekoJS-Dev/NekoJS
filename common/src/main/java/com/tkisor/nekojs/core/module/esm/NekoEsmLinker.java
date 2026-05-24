package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.core.module.NekoModulePreparationCache;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.module.NekoModuleMode;
import com.tkisor.nekojs.core.module.NekoPreparedModule;
import com.tkisor.nekojs.core.module.NekoResolvedModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NekoEsmLinker {
    private final NekoModuleResolver resolver;

    public NekoEsmLinker(NekoModuleResolver resolver) {
        this.resolver = resolver;
    }

    public NekoEsmLinkMetadata link(String moduleId, Path path, NekoPreparedModule prepared) throws IOException {
        NekoEsmModuleAst ast = prepared.esmAst();
        if (ast == null) {
            return new NekoEsmLinkMetadata(List.of(), Set.of(), Set.of(), List.of(), NekoEsmExportShape.unresolved());
        }

        validateDuplicateLocalBindings(path, prepared.code(), ast);
        validateLocalExports(path, prepared.code(), ast);
        validateDuplicateExports(path, prepared.code(), ast);
        List<NekoEsmResolvedDependency> dependencies = resolveDependencies(moduleId, ast);
        validateDependencyExports(path, prepared.code(), dependencies);
        return new NekoEsmLinkMetadata(dependencies, localExports(ast), indirectExports(ast), starExports(ast), exportShape(moduleId, ast, new LinkedHashSet<>()));
    }

    private void validateDuplicateLocalBindings(Path path, String source, NekoEsmModuleAst ast) throws NekoEsmLinkException {
        Map<Integer, NekoEsmScope> scopes = scopesById(ast);
        Map<Integer, Map<String, NekoEsmLocalBinding>> lexicalScopes = new LinkedHashMap<>();
        for (NekoEsmLocalBinding binding : ast.localBindings()) {
            if (binding == null || !duplicateChecked(binding)) {
                continue;
            }
            int scopeId = normalizedDuplicateScopeId(source, binding, scopes);
            Map<String, NekoEsmLocalBinding> seen = lexicalScopes.computeIfAbsent(scopeId, ignored -> new LinkedHashMap<>());
            NekoEsmLocalBinding previous = seen.putIfAbsent(binding.name(), binding);
            if (previous != null) {
                throw diagnostic(path, source, binding.span(), "Duplicate local ESM binding '" + binding.name() + "'");
            }
        }
    }

    private int normalizedDuplicateScopeId(String source, NekoEsmLocalBinding binding, Map<Integer, NekoEsmScope> scopes) {
        if ("var".equals(binding.kind())) {
            int scopeId = binding.scopeId();
            while (true) {
                NekoEsmScope scope = scopes.get(scopeId);
                if (scope == null || scope.kind() == NekoEsmScopeKind.MODULE || scope.kind() == NekoEsmScopeKind.FUNCTION || staticBlockScope(source, scope)) {
                    return scopeId;
                }
                scopeId = scope.parentId();
            }
        }
        return binding.scopeId();
    }

    private boolean staticBlockScope(String source, NekoEsmScope scope) {
        if (source == null || scope.kind() != NekoEsmScopeKind.BLOCK) {
            return false;
        }
        int previous = previousNonWhitespace(source, scope.span().start() - 1);
        int end = previous + 1;
        while (previous >= 0 && isIdentifierPart(source.charAt(previous))) previous--;
        return "static".equals(source.substring(previous + 1, end));
    }

    private int previousNonWhitespace(String source, int index) {
        int i = index;
        while (i >= 0 && Character.isWhitespace(source.charAt(i))) i--;
        return i;
    }

    private Map<Integer, NekoEsmScope> scopesById(NekoEsmModuleAst ast) {
        Map<Integer, NekoEsmScope> scopes = new LinkedHashMap<>();
        for (NekoEsmScope scope : ast.scopes()) {
            scopes.put(scope.id(), scope);
        }
        return scopes;
    }

    private boolean duplicateChecked(NekoEsmLocalBinding binding) {
        String kind = binding.kind();
        return "import".equals(kind) || "const".equals(kind) || "let".equals(kind) || "var".equals(kind) || "class".equals(kind) || "param".equals(kind) || "catch".equals(kind) || kind != null && kind.contains("function");
    }

    private void validateLocalExports(Path path, String source, NekoEsmModuleAst ast) throws NekoEsmLinkException {
        for (NekoEsmExportDecl exportDecl : ast.exports()) {
            if (exportDecl.kind() != NekoEsmExportKind.LIST) {
                continue;
            }
            for (NekoEsmBinding binding : exportDecl.bindings()) {
                if (!ast.hasLocalBinding(binding.imported())) {
                    throw diagnostic(path, source, bindingSpan(source, exportDecl.span(), binding.imported()), "Missing local ESM binding '" + binding.imported() + "'");
                }
            }
        }
    }

    private void validateDuplicateExports(Path path, String source, NekoEsmModuleAst ast) throws NekoEsmLinkException {
        Map<String, NekoEsmSpan> seen = new LinkedHashMap<>();
        for (NekoEsmExportDecl exportDecl : ast.exports()) {
            for (String exportName : explicitExportNames(exportDecl)) {
                NekoEsmSpan span = bindingSpan(source, exportDecl.span(), exportName);
                NekoEsmSpan previous = seen.putIfAbsent(exportName, span);
                if (previous != null) {
                    throw diagnostic(path, source, span, "Duplicate ESM export '" + exportName + "'");
                }
            }
        }
    }

    private Set<String> explicitExportNames(NekoEsmExportDecl exportDecl) {
        Set<String> names = new LinkedHashSet<>();
        switch (exportDecl.kind()) {
            case DEFAULT_EXPRESSION, DEFAULT_NAMED_DECLARATION, DEFAULT_ANONYMOUS_DECLARATION -> names.add("default");
            case DECLARATION -> addDeclarationExportNames(names, exportDecl);
            case LIST, RE_EXPORT_LIST -> {
                for (NekoEsmBinding binding : exportDecl.bindings()) {
                    names.add(binding.local());
                }
            }
            case RE_EXPORT_NAMESPACE -> names.add(exportDecl.namespaceName());
            case RE_EXPORT_ALL -> {
            }
        }
        names.remove(null);
        return names;
    }

    private void addDeclarationExportNames(Set<String> names, NekoEsmExportDecl exportDecl) {
        if (!exportDecl.bindings().isEmpty()) {
            for (NekoEsmBinding binding : exportDecl.bindings()) {
                names.add(binding.local());
            }
            return;
        }
        names.add(exportDecl.localName());
    }

    private List<NekoEsmResolvedDependency> resolveDependencies(String moduleId, NekoEsmModuleAst ast) throws IOException {
        var dependencies = new java.util.ArrayList<NekoEsmResolvedDependency>();
        for (NekoEsmStatement statement : ast.staticDependencies()) {
            String specifier = specifier(statement);
            dependencies.add(new NekoEsmResolvedDependency(statement, specifier, resolver.resolve(moduleId, specifier)));
        }
        return List.copyOf(dependencies);
    }

    private void validateDependencyExports(Path path, String source, List<NekoEsmResolvedDependency> dependencies) throws IOException {
        for (NekoEsmResolvedDependency dependency : dependencies) {
            NekoEsmExportShape exports = exportShape(dependency.resolved(), new LinkedHashSet<>());
            NekoEsmStatement statement = dependency.statement();
            if (statement instanceof NekoEsmImportDecl importDecl) {
                validateImport(path, source, importDecl, exports);
            } else if (statement instanceof NekoEsmExportDecl exportDecl) {
                validateReExport(path, source, exportDecl, exports);
            }
        }
    }

    private void validateImport(Path path, String source, NekoEsmImportDecl importDecl, NekoEsmExportShape exports) throws NekoEsmLinkException {
        if (importDecl.defaultName() != null) {
            requireExport(path, source, bindingSpan(source, importDecl.span(), importDecl.defaultName()), exports, "default");
        }
        for (NekoEsmBinding binding : importDecl.namedBindings()) {
            requireExport(path, source, bindingSpan(source, importDecl.span(), binding.imported()), exports, binding.imported());
        }
    }

    private void validateReExport(Path path, String source, NekoEsmExportDecl exportDecl, NekoEsmExportShape exports) throws NekoEsmLinkException {
        if (exportDecl.kind() != NekoEsmExportKind.RE_EXPORT_LIST) {
            return;
        }
        for (NekoEsmBinding binding : exportDecl.bindings()) {
            requireExport(path, source, bindingSpan(source, exportDecl.span(), binding.imported()), exports, binding.imported());
        }
    }

    private void requireExport(Path path, String source, NekoEsmSpan span, NekoEsmExportShape exports, String exportName) throws NekoEsmLinkException {
        if (exports.ambiguous(exportName)) {
            throw diagnostic(path, source, span, "Ambiguous ESM star export '" + exportName + "'");
        }
        if (!exports.has(exportName)) {
            throw diagnostic(path, source, span, "Missing ESM export '" + exportName + "'");
        }
    }

    private NekoEsmExportShape exportShape(NekoResolvedModule resolved, Set<String> visiting) throws IOException {
        if (resolved.special()) {
            return NekoEsmExportShape.unresolved();
        }
        if (resolved.json()) {
            return NekoEsmExportShape.of(Set.of("default"));
        }
        if (!visiting.add(resolved.id())) {
            return NekoEsmExportShape.unresolved();
        }
        try {
            NekoPreparedModule prepared = prepare(resolved.path());
            if (prepared.mode() != NekoModuleMode.ESM || prepared.esmAst() == null) {
                return NekoEsmExportShape.unresolved();
            }
            return exportShape(resolved.id(), prepared.esmAst(), visiting);
        } finally {
            visiting.remove(resolved.id());
        }
    }

    private NekoEsmExportShape exportShape(String moduleId, NekoEsmModuleAst ast, Set<String> visiting) throws IOException {
        Set<String> explicitExports = new LinkedHashSet<>(localExports(ast));
        explicitExports.addAll(indirectExports(ast));
        Set<String> exports = new LinkedHashSet<>(explicitExports);
        Set<String> ambiguous = new LinkedHashSet<>();
        Set<String> starProvided = new LinkedHashSet<>();
        for (NekoEsmExportDecl exportDecl : starExports(ast)) {
            NekoEsmExportShape dependencyExports = exportShape(resolver.resolve(moduleId, exportDecl.specifier()), visiting);
            if (dependencyExports.unknown()) {
                return NekoEsmExportShape.unresolved();
            }
            for (String name : dependencyExports.names()) {
                if ("default".equals(name) || explicitExports.contains(name)) {
                    continue;
                }
                if (!starProvided.add(name)) {
                    ambiguous.add(name);
                    exports.remove(name);
                } else if (!ambiguous.contains(name)) {
                    exports.add(name);
                }
            }
        }
        return NekoEsmExportShape.of(exports, ambiguous);
    }

    private NekoPreparedModule prepare(Path path) throws IOException {
        return NekoModulePreparationCache.prepare(path);
    }

    private Set<String> localExports(NekoEsmModuleAst ast) {
        Set<String> exports = new LinkedHashSet<>();
        for (NekoEsmExportDecl exportDecl : ast.exports()) {
            switch (exportDecl.kind()) {
                case DEFAULT_EXPRESSION, DEFAULT_NAMED_DECLARATION, DEFAULT_ANONYMOUS_DECLARATION -> exports.add("default");
                case DECLARATION -> addDeclarationExportNames(exports, exportDecl);
                case LIST -> {
                    for (NekoEsmBinding binding : exportDecl.bindings()) {
                        exports.add(binding.local());
                    }
                }
                case RE_EXPORT_NAMESPACE -> exports.add(exportDecl.namespaceName());
                case RE_EXPORT_LIST, RE_EXPORT_ALL -> {
                }
            }
        }
        return Set.copyOf(exports);
    }

    private Set<String> indirectExports(NekoEsmModuleAst ast) {
        Set<String> exports = new LinkedHashSet<>();
        for (NekoEsmExportDecl exportDecl : ast.exports()) {
            if (exportDecl.kind() != NekoEsmExportKind.RE_EXPORT_LIST) {
                continue;
            }
            for (NekoEsmBinding binding : exportDecl.bindings()) {
                exports.add(binding.local());
            }
        }
        return Set.copyOf(exports);
    }

    private List<NekoEsmExportDecl> starExports(NekoEsmModuleAst ast) {
        var exports = new java.util.ArrayList<NekoEsmExportDecl>();
        for (NekoEsmExportDecl exportDecl : ast.exports()) {
            if (exportDecl.kind() == NekoEsmExportKind.RE_EXPORT_ALL) {
                exports.add(exportDecl);
            }
        }
        return List.copyOf(exports);
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

    private NekoEsmSpan bindingSpan(String source, NekoEsmSpan fallback, String name) {
        if (source == null || fallback == null || name == null || name.isBlank()) {
            return fallback;
        }
        int start = Math.max(0, fallback.start());
        int end = Math.min(source.length(), fallback.end());
        int index = source.indexOf(name, start);
        while (index >= 0 && index < end) {
            int after = index + name.length();
            if ((index == 0 || !isIdentifierPart(source.charAt(index - 1))) && (after >= source.length() || !isIdentifierPart(source.charAt(after)))) {
                return new NekoEsmSpan(index, after);
            }
            index = source.indexOf(name, after);
        }
        return fallback;
    }

    private boolean isIdentifierPart(char c) {
        return c == '_' || c == '$' || Character.isLetterOrDigit(c);
    }

    private NekoEsmLinkException diagnostic(Path path, String source, NekoEsmSpan span, String message) {
        return new NekoEsmLinkException(NekoEsmDiagnostic.fromSource(path, source, span, message));
    }

}
