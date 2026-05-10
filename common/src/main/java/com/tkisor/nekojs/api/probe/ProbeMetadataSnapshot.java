package com.tkisor.nekojs.api.probe;

import java.util.List;

public record ProbeMetadataSnapshot(
        List<ProbeBindingDoc> bindings,
        List<ProbeEventDoc> events,
        List<ProbeAdapterDoc> adapters,
        List<ProbeExtensionDoc> extensions,
        List<ProbeRecipeNamespaceDoc> recipeNamespaces,
        List<ProbeSnippetDoc> snippets
) {
}
