package com.tkisor.nekojs.api.catalog;

import graal.graalvm.polyglot.HostAccess;

import java.util.List;

public record AdapterCatalogEntry(
        Class<?> targetType,
        String alias,
        List<String> inputShapes,
        HostAccess.TargetMappingPrecedence precedence,
        String errorPolicy,
        List<String> examples
) {
    public static AdapterCatalogEntry of(Class<?> targetType, HostAccess.TargetMappingPrecedence precedence) {
        return new AdapterCatalogEntry(targetType, targetType.getSimpleName(), List.of(), precedence, null, List.of());
    }
}
