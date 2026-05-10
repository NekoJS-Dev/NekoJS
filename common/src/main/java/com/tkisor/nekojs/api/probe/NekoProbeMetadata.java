package com.tkisor.nekojs.api.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;

import java.util.List;

@Deprecated(forRemoval = false)
public final class NekoProbeMetadata {
    private NekoProbeMetadata() {}

    public static ProbeMetadataSnapshot snapshot() {
        return new ProbeMetadataSnapshot(
                bindings(),
                events(),
                adapters(),
                extensions(),
                recipeNamespaces(),
                snippets()
        );
    }

    public static List<ProbeBindingDoc> bindings() {
        return NekoScriptCatalog.snapshot().bindings().stream()
                .map(entry -> new ProbeBindingDoc(
                        entry.name(),
                        entry.scriptType(),
                        entry.javaType(),
                        entry.staticClass(),
                        entry.hostClass(),
                        entry.emit(),
                        entry.typeOverride(),
                        entry.description()
                ))
                .toList();
    }

    public static List<ProbeEventDoc> events() {
        return NekoScriptCatalog.snapshot().events().stream()
                .map(entry -> new ProbeEventDoc(
                        entry.group(),
                        entry.name(),
                        entry.scriptType(),
                        entry.eventType(),
                        entry.dispatchKeyType(),
                        entry.cancellable(),
                        entry.dispatchable(),
                        entry.snippet()
                ))
                .toList();
    }

    public static List<ProbeAdapterDoc> adapters() {
        return NekoScriptCatalog.snapshot().adapters().stream()
                .map(entry -> new ProbeAdapterDoc(
                        entry.targetType(),
                        entry.alias(),
                        entry.inputShapes(),
                        entry.precedence(),
                        entry.errorPolicy(),
                        entry.examples()
                ))
                .toList();
    }

    public static List<ProbeExtensionDoc> extensions() {
        return NekoScriptCatalog.snapshot().hostExtensions().stream()
                .map(entry -> new ProbeExtensionDoc(
                        entry.targetClass(),
                        entry.extensionInterface(),
                        entry.javaName(),
                        entry.jsName(),
                        entry.scriptType(),
                        entry.hidden()
                ))
                .toList();
    }

    public static List<ProbeRecipeNamespaceDoc> recipeNamespaces() {
        return NekoScriptCatalog.snapshot().recipeNamespaces().stream()
                .map(entry -> new ProbeRecipeNamespaceDoc(
                        entry.namespace(),
                        entry.handlerClass(),
                        entry.fallbackSupported(),
                        entry.examples()
                ))
                .toList();
    }

    public static List<ProbeSnippetDoc> snippets() {
        return NekoScriptCatalog.snapshot().snippets().stream()
                .map(entry -> new ProbeSnippetDoc(
                        entry.name(),
                        entry.scriptType(),
                        entry.prefix(),
                        entry.body(),
                        entry.description()
                ))
                .toList();
    }
}
