package com.tkisor.nekojs.api.probe;

import java.util.Collection;
import java.util.List;

public interface NekoProbeMetadataProvider {
    default Collection<ProbeBindingDoc> bindings() {
        return List.of();
    }

    default Collection<ProbeEventDoc> events() {
        return List.of();
    }

    default Collection<ProbeAdapterDoc> adapters() {
        return List.of();
    }

    default Collection<ProbeExtensionDoc> extensions() {
        return List.of();
    }

    default Collection<ProbeRecipeNamespaceDoc> recipeNamespaces() {
        return List.of();
    }

    default Collection<ProbeSnippetDoc> snippets() {
        return List.of();
    }
}
