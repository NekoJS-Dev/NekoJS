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
            return new NekoEsmLinkMetadata(List.of(), Set.of(), Set.of(), List.of());
        }

        validateLocalExports(path, prepared.code(), ast);
        validateDuplicateExports(path, prepared.code(), ast);
        List<NekoEsmResolvedDependency> dependencies = resolveDependencies(moduleId, ast);
        validateDependencyExports(path, prepared.code(), dependencies);
        return new NekoEsmLinkMetadata(dependencies, localExports(ast), indirectExports(ast), starExports(ast));
    }

    private void validateLocalExports(Path path, String source, NekoEsmModuleAst ast) throws NekoEsmLinkException {
        for (NekoEsmExportDecl exportDecl : ast.exports()) {
            if (exportDecl.kind() != NekoEsmExportKind.LIST) {
                continue;
            }
            for (NekoEsmBinding binding : exportDecl.bindings()) {
                if (!ast.hasLocalBinding(binding.local())) {
                    throw diagnostic(path, source, exportDecl.span(), "Missing local ESM binding '" + binding.local() + "'");
                }
            }
        }
    }

    private void validateDuplicateExports(Path path, String source, NekoEsmModuleAst ast) throws NekoEsmLinkException {
        Map<String, NekoEsmSpan> seen = new LinkedHashMap<>();
        for (NekoEsmExportDecl exportDecl : ast.exports()) {
            for (String exportName : explicitExportNames(exportDecl)) {
                NekoEsmSpan previous = seen.putIfAbsent(exportName, exportDecl.span());
                if (previous != null) {
                    throw diagnostic(path, source, exportDecl.span(), "Duplicate ESM export '" + exportName + "'");
                }
            }
        }
    }

    private Set<String> explicitExportNames(NekoEsmExportDecl exportDecl) {
        Set<String> names = new LinkedHashSet<>();
        switch (exportDecl.kind()) {
            case DEFAULT_EXPRESSION, DEFAULT_NAMED_DECLARATION, DEFAULT_ANONYMOUS_DECLARATION -> names.add("default");
            case DECLARATION -> names.add(exportDecl.localName());
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
            ExportShape exports = exportShape(dependency.resolved(), new LinkedHashSet<>());
            NekoEsmStatement statement = dependency.statement();
            if (statement instanceof NekoEsmImportDecl importDecl) {
                validateImport(path, source, importDecl, exports);
            } else if (statement instanceof NekoEsmExportDecl exportDecl) {
                validateReExport(path, source, exportDecl, exports);
            }
        }
    }

    private void validateImport(Path path, String source, NekoEsmImportDecl importDecl, ExportShape exports) throws NekoEsmLinkException {
        if (importDecl.defaultName() != null) {
            requireExport(path, source, importDecl.span(), exports, "default");
        }
        for (NekoEsmBinding binding : importDecl.namedBindings()) {
            requireExport(path, source, importDecl.span(), exports, binding.imported());
        }
    }

    private void validateReExport(Path path, String source, NekoEsmExportDecl exportDecl, ExportShape exports) throws NekoEsmLinkException {
        if (exportDecl.kind() != NekoEsmExportKind.RE_EXPORT_LIST) {
            return;
        }
        for (NekoEsmBinding binding : exportDecl.bindings()) {
            requireExport(path, source, exportDecl.span(), exports, binding.imported());
        }
    }

    private void requireExport(Path path, String source, NekoEsmSpan span, ExportShape exports, String exportName) throws NekoEsmLinkException {
        if (!exports.has(exportName)) {
            throw diagnostic(path, source, span, "Missing ESM export '" + exportName + "'");
        }
    }

    private ExportShape exportShape(NekoResolvedModule resolved, Set<String> visiting) throws IOException {
        if (resolved.special()) {
            return ExportShape.unresolved();
        }
        if (resolved.json()) {
            return ExportShape.of(Set.of("default"));
        }
        if (!visiting.add(resolved.id())) {
            return ExportShape.unresolved();
        }
        try {
            NekoPreparedModule prepared = prepare(resolved.path());
            if (prepared.mode() != NekoModuleMode.ESM || prepared.esmAst() == null) {
                return ExportShape.unresolved();
            }
            return exportShape(resolved.id(), prepared.esmAst(), visiting);
        } finally {
            visiting.remove(resolved.id());
        }
    }

    private ExportShape exportShape(String moduleId, NekoEsmModuleAst ast, Set<String> visiting) throws IOException {
        Set<String> exports = new LinkedHashSet<>(localExports(ast));
        exports.addAll(indirectExports(ast));
        for (NekoEsmExportDecl exportDecl : starExports(ast)) {
            ExportShape dependencyExports = exportShape(resolver.resolve(moduleId, exportDecl.specifier()), visiting);
            if (dependencyExports.unknown()) {
                return ExportShape.unresolved();
            }
            for (String name : dependencyExports.names()) {
                if (!"default".equals(name)) {
                    exports.add(name);
                }
            }
        }
        return ExportShape.of(exports);
    }

    private NekoPreparedModule prepare(Path path) throws IOException {
        return NekoModulePreparationCache.prepare(path);
    }

    private Set<String> localExports(NekoEsmModuleAst ast) {
        Set<String> exports = new LinkedHashSet<>();
        for (NekoEsmExportDecl exportDecl : ast.exports()) {
            switch (exportDecl.kind()) {
                case DEFAULT_EXPRESSION, DEFAULT_NAMED_DECLARATION, DEFAULT_ANONYMOUS_DECLARATION -> exports.add("default");
                case DECLARATION -> exports.add(exportDecl.localName());
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

    private NekoEsmLinkException diagnostic(Path path, String source, NekoEsmSpan span, String message) {
        return new NekoEsmLinkException(NekoEsmDiagnostic.fromSource(path, source, span, message));
    }

    private record ExportShape(Set<String> names, boolean unknown) {
        private static ExportShape of(Set<String> names) {
            return new ExportShape(names == null ? Set.of() : Set.copyOf(names), false);
        }

        private static ExportShape unresolved() {
            return new ExportShape(Set.of(), true);
        }

        private boolean has(String name) {
            return unknown || names.contains(name);
        }
    }
}
