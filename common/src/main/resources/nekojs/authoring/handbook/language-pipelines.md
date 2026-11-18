# Script language pipelines

A frontend accepts source for an extension and prepares it for execution. Its frontend ID identifies the implementation, not the language: different plugins can implement the same language and support the same extension.

## Configure a pipeline

Set optional per-extension overrides in `<gamedir>/nekojs/config/engine.toml`. Put the dotted extension in quotes; `.js`, `.mjs` and `.cjs` are separate keys. With no entry, NekoJS keeps automatic frontend selection and the default checks.

```toml
[languagePipelines.".py"]
frontend = "nekojs:python"
sourceChecks = []
outputChecks = ["nekojs:javascript"]
```

This uses the built-in Python frontend, no check on the Python source, and the built-in JavaScript check on the generated JavaScript. Omitting `frontend` keeps automatic selection. Omitting a check list keeps that stage's default: the built-in check reads native JavaScript source, or reads generated JavaScript for other frontends. An empty list disables only that stage. A frontend that declares non-JavaScript output gets no default output check. An explicit list runs in order; every check must support the selected frontend's input and capabilities for that stage, in both `FULL` and `BINDINGS_ONLY` scopes. An unnamed frontend without semantics can use the defaults, but cannot satisfy an explicit check selection.

NekoJS runs `SOURCE` checks on the authored text before the frontend prepares it. `OUTPUT` checks require the frontend to produce an artifact successfully. For native CommonJS, an explicitly selected `OUTPUT` check can run before Graal parses the execution wrapper, so a later syntax error is still possible. NekoJS uses `FULL` when preparing modules and rechecks bindings with the current script generation's schema when preparing the entry script. Mapped output findings point back to the original source where possible. Findings are reported and do not stop scripts; a provider exception is a failure. During reload, a failed candidate is discarded and the active generation is retained.

Restart the game or dedicated server to apply edits; script reload does not reread the file. Invalid or unreadable engine config, an unknown explicit ID, or an incompatible selection prevents NekoJS scripts from initializing (`NEKO-1030`) instead of silently using defaults. `scriptMemberValidation = false` disables preflight execution, not configuration validation, compilation, authorization or sandbox checks.

## Select a Probe backend

Optionally add a `probe` reference to select a declaration backend for a target language. For example, the native JavaScript frontend supports the built-in TypeScript declaration backend:

```toml
[languagePipelines.".js"]
probe = { language = "typescript", backend = "builtin" }
```

The selected frontend and backend must share an exact authoring capability. Plugin backends declare these through `ProbeBackend.authoringCapabilities()`; the built-ins use `nekojs:typescript-authoring/v1` and `nekojs:python-authoring/v1`. Backends without capabilities still work through legacy selection, but cannot be referenced from a pipeline. Backends are identified by the pair `(language, backend)`. Several extensions may select the same pair; conflicting backends for one target language are rejected. Configured output directories must not overlap.

- `/nekojs probe` and regeneration after `reset_config` select the configured TypeScript backend or exactly `typescript builtin`.
- `/nekojs probe <language>` selects the engine reference, then the matching preference in `probe.toml`, then the highest-priority registered backend.
- `/nekojs probe <language> <name>` selects that pair for one run without falling back. `/nekojs probe all` still runs all registered backends in their original order.

NeoForge can generate the same TypeScript target after server startup when Probe is enabled, `runAtStartup` is true and the scan mode is not `NONE`. Fabric requires a manual command. A startup generation failure is logged; invalid startup pipeline configuration instead prevents NekoJS initialization. Restart to change engine references; Probe commands do not change pipeline selections. Each generation uses one Probe config snapshot, and only successful backends contribute editor settings. These commands require operator permission.

## Register a frontend

In a Java plugin's `registerScriptCompilers(ScriptCompilerRegistry)` hook, use `registerFrontend(id, implementationRevision, implementation, semantics)`. Pass a `NekoLanguagePlugin` for a complete language implementation, or a `NekoScriptLanguage` wrapping an existing `IScriptCompiler`. Register that implementation once: named registration already makes it an automatic candidate. Existing `register`, `registerLanguage`, and `replaceLanguage` calls retain their automatic behavior without creating a named candidate.

For example, this hook body registers an existing JavaScript-to-JavaScript compiler. `compiler` is the plugin's `IScriptCompiler` instance; import `FrontendSemantics` from `com.tkisor.nekojs.api.compiler` and `NekoScriptLanguage` from `com.tkisor.nekojs.core.compiler`.

```java
var semantics = new FrontendSemantics(
        FrontendSemantics.JAVASCRIPT_SOURCE,
        FrontendSemantics.JAVASCRIPT_OUTPUT,
        Set.of());
registry.registerFrontend("example:js", "1",
        new NekoScriptLanguage("javascript", Set.of(".js"), compiler), semantics);
```

Use a unique lowercase namespaced ID and a nonempty implementation revision. Update the revision when behavior used by the pipeline changes. Declare the source and output representations, their dialects, and any additional capabilities the implementation actually provides. Registration closes when plugin bootstrap freezes the registry. `NEKO-1026` identifies a rejected named registration or invalid frontend metadata.

| Built-in frontend ID | Extensions |
|---|---|
| `nekojs:javascript` | `.js`, `.mjs`, `.cjs` |
| `nekojs:typescript` | `.ts` |
| `nekojs:jsx` | `.jsx` |
| `nekojs:tsx` | `.tsx` |
| `nekojs:python` | `.py` |

The Java registry exposes named descriptors through `frontends()` and captures the binding revision together with the implementation through `capture(extension, frontendId)`. An unknown ID or unsupported extension is rejected with `NEKO-1027`, without falling back to another implementation. Replacing automatic selection does not remove a registered name; the built-in JavaScript ID is selectable without raising its automatic fallback priority.

## Register a preflight check

A Java plugin contributes a check independently of the frontend. Override `registerScriptPreflights(ScriptPreflightRegistry)` on `NekoJSPlugin` and call `registry.register(provider)`. The `nekojs:script_preflights` extension point freezes descriptors at plugin bootstrap. It rejects duplicate IDs and registration after collection; registration by itself does not schedule a check.

This example registers an output check. Import `ScriptPreflight`, its nested types and `ScriptPreflightRegistry` from `com.tkisor.nekojs.api.preflight`, `FrontendSemantics` from `com.tkisor.nekojs.api.compiler`, and `List`, `Map` and `Set` from `java.util`.

```java
@Override
public void registerScriptPreflights(ScriptPreflightRegistry registry) {
    registry.register(new ScriptPreflight() {
        @Override
        public Descriptor descriptor() {
            return new Descriptor("example:js-output", "1",
                    Map.of(Stage.OUTPUT, Set.of(FrontendSemantics.JAVASCRIPT_OUTPUT)),
                    Set.of(Scope.FULL, Scope.BINDINGS_ONLY), Set.of());
        }

        @Override
        public Worker open() {
            return request -> List.of(); // Replace with findings from the checked input.
        }
    });
}
```

Declare supported input representations and dialects per stage, required frontend capabilities, and only the scopes the check implements. A `Worker` is runtime-root-owned and may receive concurrent calls from different Script Type owner threads. Its `Request` carries the current immutable text, binding schema and globals, not a Graal Context or a writable registry. Keep only thread-safe, pure analysis data across requests and release owned resources in `close()`.

Return ordinary findings as `Diagnostic` values. Positions are one-based, with an optional zero-based UTF-16 offset (`-1` when unknown); use coordinates in the checked text. Throwing from a worker reports a provider failure, not an ordinary finding. `NEKO-1028` identifies rejected preflight registration or an incompatible request; `NEKO-1029` identifies provider setup, execution, or cleanup failures. The bundled `nekojs:javascript` check is separate from the frontend with the same ID and retains the `binding-preflight` and `event-cb-preflight` rule codes.
