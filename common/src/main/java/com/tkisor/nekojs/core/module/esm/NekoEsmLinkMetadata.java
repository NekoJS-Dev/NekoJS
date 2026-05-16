package com.tkisor.nekojs.core.module.esm;

import java.util.List;
import java.util.Set;

public record NekoEsmLinkMetadata(
        List<NekoEsmResolvedDependency> dependencies,
        Set<String> localExports,
        Set<String> indirectExports,
        List<NekoEsmExportDecl> starExports
) {
    public NekoEsmLinkMetadata {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        localExports = localExports == null ? Set.of() : Set.copyOf(localExports);
        indirectExports = indirectExports == null ? Set.of() : Set.copyOf(indirectExports);
        starExports = starExports == null ? List.of() : List.copyOf(starExports);
    }
}
