package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.ScriptTypePredicate;
import com.tkisor.nekojs.api.catalog.*;
import com.tkisor.nekojs.api.AdapterInputShape;
import graal.graalvm.polyglot.HostAccess;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Builds a {@link NekoScriptCatalogSnapshot} covering every probe output category,
 * used by {@link LegacyProbeTreeTest} to generate a deterministic golden tree.
 */
final class LegacyProbeFixture {

    private LegacyProbeFixture() {}

    /** Sample class used for static binding. */
    public static final class SampleHelper {
        public static String of(String id) { return id; }
        public static String of(String id, int count) { return id + ":" + count; }
        public String getName() { return "sample"; }
    }

    /** Sample class used for instance binding. */
    public static final class SampleContext {
        public String getWorld() { return "overworld"; }
    }

    /** Sample event type (cancellable). */
    public static final class SampleCancellableEvent {
        public String getMessage() { return "cancelled"; }
    }

    /** Sample event type with dispatch key. */
    public static final class SampleDispatchEvent {
        public String getResult() { return "ok"; }
    }

    /** Sample dispatch key type. */
    public static final class SampleDispatchKey {
        public String getValue() { return "key"; }
    }

    /** Sample adapter target type. */
    public static final class SampleWidget {
        public String getLabel() { return "widget"; }
    }

    static NekoScriptCatalogSnapshot snapshot() {
        // Static binding (SERVER)
        var staticBinding = BindingCatalogEntry.of("SampleHelper", ScriptType.SERVER, SampleHelper.class, true);

        // Instance binding (SERVER)
        var instanceBinding = new BindingCatalogEntry(
                "sampleContext", ScriptType.SERVER, SampleContext.class,
                false, true, true, null, "Sample context binding", List.of(), List.of());

        // Cancellable event (SERVER)
        var cancellableEvent = EventCatalogEntry.of(
                "SampleEvents", "cancellable", ScriptType.SERVER,
                SampleCancellableEvent.class, null, true, false);

        // Dispatch-key event (SERVER)
        var dispatchEvent = new EventCatalogEntry(
                "SampleEvents", "dispatch", ScriptType.SERVER,
                SampleDispatchEvent.class, SampleDispatchKey.class, false, true,
                "SampleEvents.dispatch(key, event => {\n  $0\n})");

        // Adapter with input alias
        var adapter = new AdapterCatalogEntry(
                SampleWidget.class,
                List.of(AdapterInputShape.self(), AdapterInputShape.string()),
                HostAccess.TargetMappingPrecedence.LOWEST,
                Optional.of("SampleWidget adapter: accepts a string label"));

        // Recipe namespace
        var recipeNamespace = RecipeNamespaceCatalogEntry.of("testcraft", null);

        // Registry literal type
        var registryType = new RegistryTypeCatalogEntry(
                "SampleBlock",
                List.of("testcraft:alpha", "testcraft:beta"),
                List.of("testcraft:all_blocks"));

        // Manual declaration
        var manualDecl = ManualDeclarationCatalogEntry.of(
                "sample-helper",
                "declare module \"@manual/sample-helper\" {\n    export function helperFn(input: string): string;\n}",
                "Sample helper module",
                List.of("helperFn('hello')"));

        // Snippet
        var snippet = new SnippetCatalogEntry(
                "sample-snippet",
                ScriptType.SERVER,
                "sampleSnippet",
                "SampleEvents.cancellable(event => {\n  $0\n})",
                "Sample cancellable event snippet");

        return new NekoScriptCatalogSnapshot(
                ScriptType.all(),
                List.of(staticBinding, instanceBinding),
                List.of(cancellableEvent, dispatchEvent),
                List.of(adapter),
                List.of(recipeNamespace),
                List.of(),
                List.of(snippet),
                List.of(),
                List.of(manualDecl),
                List.of(registryType),
                new TypeOutputLayout(Path.of("probe-types"), Path.of("snippets")),
                java.util.Map.of(),
                java.util.List.of()
        );
    }
}
