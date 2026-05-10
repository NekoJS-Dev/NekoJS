package com.tkisor.nekojs.api.probe;

import graal.graalvm.polyglot.HostAccess;

import java.util.List;

public record ProbeAdapterDoc(
        Class<?> targetType,
        String alias,
        List<String> inputShapes,
        HostAccess.TargetMappingPrecedence precedence,
        String errorPolicy,
        List<String> examples
) {
    public static ProbeAdapterDoc of(Class<?> targetType, HostAccess.TargetMappingPrecedence precedence) {
        return new ProbeAdapterDoc(targetType, targetType.getSimpleName(), List.of(), precedence, null, List.of());
    }
}
