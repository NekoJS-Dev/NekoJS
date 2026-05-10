package com.tkisor.nekojs.api.probe;

import java.util.List;

public record ProbeRecipeNamespaceDoc(
        String namespace,
        Class<?> handlerClass,
        boolean fallbackSupported,
        List<String> examples
) {
    public static ProbeRecipeNamespaceDoc of(String namespace, Class<?> handlerClass) {
        return new ProbeRecipeNamespaceDoc(namespace, handlerClass, true, List.of());
    }
}
