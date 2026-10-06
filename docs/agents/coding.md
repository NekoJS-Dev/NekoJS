# NekoJS coding conventions

Read this before modifying code, together with [AGENTS.md](../../AGENTS.md). The entry file defines scope, language, and architecture constraints; this document gives implementation and verification details.

## Scope and collaboration

- Identify the requested behavior, affected consumers, and acceptance criteria. Trace the relevant entry points, callers, state owners, and tests far enough to understand the change; a local fix does not require a repository-wide audit.
- Keep each change focused on one goal. Necessary related edits belong together, including affected node implementations and declarations; unrelated cleanup belongs in a separate task.
- Before parallel work, identify the person responsible for each change and its affected files. Use separate branches/worktrees for independent implementation and agree who edits overlapping files; isolation alone does not resolve semantic conflicts.
- Preserve unrelated working changes. Inspect and understand a conflicting edit before integrating it; do not reset or overwrite another contributor's work to obtain a clean diff.
- When a module, dependency direction, public contract, or lifecycle owner changes, explain the reason and affected consumers in the change description. Routine implementation choices need no separate design document.

## Design and code style

- Look for a suitable existing implementation or pattern first. Then consider the standard library and already-installed dependencies. A new helper should remove actual repetition or own a coherent rule; place it with the behavior it serves.
- Fix the cause at the narrowest shared owner. Check all callers of a changed contract and all affected platform implementations; avoid patching only the reported path when sibling paths share the defect.
- Add an interface, factory, strategy, wrapper, or configuration option only for a current consumer, real variation, or useful test seam. Keep lifecycle and ordering complexity inside the responsible module instead of making every caller repeat it.
- Keep public surface area and visibility as small as the actual consumers require. Test through observable behavior; widening a production interface only to inspect private state needs a better design.
- Use the vocabulary in [CONTEXT.md](../../CONTEXT.md). Name types and operations for their domain purpose; avoid a growing generic `Manager`, `Helper`, or `Utils` container for unrelated responsibilities.
- Follow the configured formatter and nearby style where they respect current rules. Prefer explicit control flow and names that explain state; avoid clever expression chains and broad reformatting. Split by responsibility, not an arbitrary line-count target.
- Use precise types in host internals. Keep dynamic values and conversions at script/platform interoperability boundaries; validate assumptions there rather than spreading unchecked casts and nullable fallbacks through callers.
- New dependencies need a concrete capability the current stack cannot reasonably supply. Record platform/JDK compatibility, packaging or relocation impact, license, and size where relevant. Keep dependency and build changes within the task.
- On tick, render, callback, and reload paths, avoid adding blocking I/O, unbounded work, or unnecessary allocation. Performance changes should have a reproducible measurement; introduce caching only with a clear owner, invalidation rule, and demonstrated need.

## Script API conventions

Use these conventions when designing or changing script-facing APIs, declarations, examples, and scripts. Keep existing public names and overloads unless the requested change includes their migration.

### Events

- Expose events as `EventGroupName.eventName(event => {})`, such as `ServerEvents.tickPre(event => {})`. The group is the namespace, the member selects the event, and `event` is the callback's event object. Keep event names in lower camel case and use the existing domain group.
- Create a group with `EventGroup.of("ServerEvents")` and declare its events on that group, for example `GROUP.server("tickPre", ServerTickEvent.Pre.class)`. Creation alone does not register the group: `registerEvents(EventGroupRegistry registry)` calls `registry.register(ServerEvents.GROUP)`. Use `registerClientEvents` for the corresponding client registration path.
- Finish the group's event definitions before registration and freezing. Preserve each event's Script Type and any required selector/filter arguments; put those arguments before the callback. Treat the group as a subscription namespace, not an object scripts can mutate to define new events.
- Keep the callback's data and event-specific operations on the event object. General helpers belong on a Binding when they do not depend on an event.

Reference: [ServerEvents.java](../../src/main/java/com/tkisor/nekojs/bindings/event/ServerEvents.java).

### Bindings

- Expose named helpers through the existing Binding registration path, so scripts call the published entry directly, for example `Item.of("minecraft:stone")`. Follow the binding's actual Script Type availability and member signatures.
- Register bindings in `registerBinding(BindingRegistry registry)`. Use a class binding for static members and an instance binding for instance methods. For proxies with dynamic members, supply the exposed type through `Binding.of(name, value, valueType)` so declarations and member validation describe the same interface as runtime access.
- Reuse the existing `Item` helper/target delegation pattern when extending that surface; access through a Binding does not imply that the underlying Java class has gained methods.

Reference: [NekoJSCorePlugin.java](../../src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java).

### Builder callbacks

- For APIs that create a configurable definition, use `event.create(arg1, ..., build => { ... })`: identifying or required arguments first, configuration callback last. Name the callback parameter `build` in examples.
- Expose configuration operations on the builder. A callback should not need an extra `build()` or registration call unless the existing API explicitly requires one. Support fluent chaining only where the methods actually return the builder.
- Keep the event object and builder distinct, including in generated types. The event coordinates creation; the builder configures that definition.

Shape only: `definitionId`, `property`, and `value` below are placeholders for the API being documented, not additional promised members.

```js
event.create(definitionId, build => {
    build.property = value;
});
```

### Bean properties

- Prefer property access for exposed JavaBean accessors: `getXxx()` reads as `event.xxx`, and `setXxx(value)` writes as `event.xxx = value`. The same convention applies to builders and bindings that expose Bean properties.
- A getter alone does not make a property writable; a setter alone does not make it readable. Preserve the accessor's validation and side effects. Keep parameterized operations as methods rather than treating every `get...` or `set...` name as a property.
- When implementing or changing such an API, verify property access on the actual script-visible wrapper/proxy and keep declarations consistent with its readable/writable members. Java accessor names alone do not prove that every wrapper exposes Bean properties.

### Public changes

- Favor convenient script authoring. Within an explicitly authorized migration, a major refactor may make a single breaking Script API change with a wiki migration table; Plugin API changes may also break during active development. Keep unrelated public behavior compatible.
- Keep error messages focused on the failure. Put migration instructions in the migration documentation rather than embedding them in routine runtime errors.

## Errors and input boundaries

- Validate external script, plugin, network, file, and configuration input at the appropriate boundary, following the existing trust model. Keep permission, path, and trust checks intact when refactoring.
- Catch an error where the contract can recover, translate it, or report failure. Preserve its cause and useful operation context. A fallback must be an intentional, observable part of the contract.
- Registration, persistence, and reload failures must remain failures. Do not turn them into success, empty collections, or `null` merely to keep execution going; a data-read failure must not trigger overwriting the original with empty defaults.
- Preserve cancellation and interruption behavior through the existing lifecycle path. Cleanup must also run on partial initialization and failure without masking the original error.

## Resources, threads, and state

Apply these constraints when changing runtime behavior. Check the affected implementation and tests before claiming that it satisfies them.

- Every Context, listener, timer, thread, file handle, and other owned resource needs an identifiable owner, release point, and failure cleanup path. Separate root-owned resources from generation-owned resources; closing a generation must not release shared Plugin Runtime products.
- Prepare and validate candidate resources before the single owner-thread commit point. Production callbacks, timers, bindings, and live registry mutation stay with active until that handoff. Candidate failure releases its resources and preserves active; avoid old/new double callbacks.
- Route cross-thread runtime requests through the owning thread. Preserve serialization of evaluate/reload/close for each ScriptType and close priority; a concurrent collection does not make guest objects or Context access thread-safe.
- Ordinary reload does not bootstrap Plugin Runtime again. Watchdog termination of active records failure and awaits explicit recovery; it must not create an unrequested second active runtime.
- `global` is shared within one ScriptType, with cross-type sharing through the explicit shared-state entry. Preserve the root/generation lifetime distinction and the separate meaning of the language object `globalThis`; read the current contract/declarations for the published entry name.
- Candidate state writes cover managed top-level key operations, with conflict detection and joint publication of private/shared write sets. Preserve the stated limits: nested object mutation, arbitrary Java/world/file effects, and cross-process network behavior are not guaranteed by this mechanism.

## Logging

The language rule and literal-data exceptions are in [AGENTS.md](../../AGENTS.md#language).

- State what happened to which operation or object, then the useful outcome or known cause. For a failure, say whether work was rejected, skipped, retried, or retained when that result is known. Use short, direct sentences; omit generic failure announcements, progress narration, and implementation history.
- Include enough context to locate and diagnose the event. Select identifiers such as source location, ScriptType, generation, phase, or owner/domain when they distinguish this failure; keep fields required by the runtime contract. Reuse context already supplied by the diagnostic envelope or logger instead of repeating it in prose. Never invent a cause or claim recovery that the code has not established.
- Use the module's existing logging API and parameterized formatting. Production diagnostics belong in that logger, not temporary console output or `printStackTrace`.
- Use DEBUG/TRACE for detailed diagnostics, INFO for meaningful normal lifecycle events, WARN for unexpected recoverable conditions, and ERROR for failed operations that need attention. Routine per-tick, per-frame, or per-item output stays at DEBUG/TRACE.
- Gate expensive diagnostic computation on the enabled log level; parameter placeholders alone do not defer argument evaluation. Keep repeated diagnostics bounded using an existing mechanism when available.
- Keep the message readable and attach the exception/cause when needed for diagnosis. Record a propagated failure at its handling/reporting layer; preserve causes when wrapping and avoid repeated stack traces along the call chain. Concise wording must not discard evidence needed to investigate the failure.
- Include raw input or third-party text only when it adds necessary diagnostic evidence. Preserve the exact fragment needed for that purpose, subject to redaction and output limits; retaining a cause does not require dumping its entire payload. Never log credentials, tokens, or other secrets.

For example, when a failed reload has actually retained the old generation, a useful summary is `[NEKO-1008] Failed to reload SERVER scripts at server_scripts/main.js:42; generation 12 remains active.` Attach the cause at the reporting boundary. Keep extra phase/owner context in the existing diagnostic fields rather than expanding the message into a report.

### Diagnostic codes

A log or exception message that reports a problem carries a stable `NEKO-` code. The code is the lookup key: do not change it when the wording changes, and do not reuse it for a different failure. The same failure uses the same code at every call site, including node copies. Assigned codes are listed in `wiki/error-reference_us.md` with the corresponding Chinese reference in `wiki/error-reference_cn.md`; update both pages in the same change. Review the diff against the page. There is no separate code-number check.

- Prefix the message with `[NEKO-nnnn]`. When the body is not already the short English summary (the existing Chinese messages), append ` — ` and that summary. Put the summary before a trailing runtime value: `[NEKO-4006] 未知方块实体类型 — unknown block entity type: minecraft:chest`. A message that is already that English sentence does not get a second paraphrase.
- Take the next free number in the area: `1xxx` script loading and reload, `2xxx` sandbox and resource limits, `3xxx` script sync limits, `4xxx` registration and bindings, `5xxx` recipes and data, `6xxx` client UI visual and resource contract, `7xxx` client JSX UI lifecycle and diagnostics, `8xxx` UI inspector.
- Routine progress messages get the English summary and no code.
- Do not put a code on player chat or on in-game error-panel labels. Those stay in the existing language files.
- Leave the Chinese body of an existing coded message in place. Do not translate those messages as a drive-by. New developer-facing log and exception text still follows the English language rule, and a new problem report still gets a code.

## Comments and documentation

- Start with what the class or method does. For straightforward behavior, one short sentence describing the purpose or result is enough. Use concrete verbs and the actual domain names.
- For complex behavior, explain the mechanism a maintainer needs to understand: the main stages or dispatch rule, relevant ordering/state changes, and the constraints that make it correct. Include inputs, results, side effects, failure behavior, or a small example when they resolve a real ambiguity; do not mechanically fill every category.
- Put each explanation where it helps: class Javadoc for the role and caller-visible behavior, method Javadoc for that operation's contract, and a local comment beside a non-obvious implementation constraint. For example, explain the active-context requirement beside deferred value conversion, without reproducing the entire investigation in class Javadoc. Avoid repeating the same explanation at all three levels.
- Write plain English as a maintainer explaining the code to a colleague. Keep short comments free of stock section labels, emphatic markup, and line-by-line narration. Important thread affinity, ownership, lifetime, error, and compatibility constraints still belong in the contract; brevity is not a reason to omit them.
- Keep comments focused on current behavior and the reason for a necessary constraint. Omit abandoned approaches, platform-history digressions, investigation timelines, and long error transcripts; include that background in the change description only when it helps review. Existing or quoted prose may be rewritten for clarity; the literal-data exception does not protect its wording.
- Verify comments against the implementation when editing them. Describe current behavior, distinguish proposed behavior explicitly, and update or remove comments invalidated by the change.
- A workaround must state why it is needed and what would allow its removal. Keep TODOs concrete enough for another maintainer to act on.
- Keep API examples, migration notes, and relevant wiki pages consistent with an intentional public change. Update or remove guidance that describes behavior the project no longer supports.

## Compatibility, data, and generated artifacts

- Classify changes to the Script API, Plugin API, persistent formats/paths/keys, and network identifiers separately. Preserve contracts outside the authorized migration scope; accepted breaking changes need the corresponding migration material and consumer checks.
- A new collection channel needs its Point, corresponding default Plugin Hook on `NekoJSPlugin`, entry in `NekoBuiltinPointsPlugin`, and pairing in `PluginHookPairingTest`. Direct callback hooks do not require a Point; keep their dispatch separate from collection channels.
- Persistent migrations need the established backup/atomic replacement and recovery path, including failure validation. Ordinary reload must not overwrite or delete config, world, pdata, pack, trust-store, or user-edited workspace/declaration data.
- Change the canonical contract or generator first, then regenerate affected derived artifacts through the existing workflow and inspect their diff. Keep hand-authored declarations distinct from generated ones; do not patch generated output to conceal a source defect.
- Existing node splits carry real platform differences. When shared behavior changes, inspect the corresponding implementations for every affected supported node; a single-node success is not evidence for the others.
- Remove temporary migration paths when their migration and validation requirements have been met. Avoid speculative compatibility shims and duplicate runtime or state owners.

## Verification

Select the applicable checks below. The current commands, toolchains, node matrix, and CI setup live in [ci-build.yml](../../.github/workflows/ci-build.yml) and [package.json](../../package.json); read those when running the affected checks rather than copying release configuration into this document. On Windows use `gradlew.bat` (or `./gradlew.bat` in PowerShell).

| Change | Relevant existing checks |
|---|---|
| Common engine, ownership, or dependency isolation | `:common:check`; include `guardLint` for boundary, import, guard, or version-source changes. `:common:test` alone omits isolation checks. |
| Annotation processor | `:common-api-processor:test`, plus affected contract/consumer checks. |
| Version facade, guards, or source-root/build changes | `guardLint`, affected node `build` tasks, and the applicable `tools/nekojs-ci-gates.py` checks with the setup used in CI. A platform-wide change covers the full supported matrix. |
| NBT codec | The `nbtSmokeTest` tasks for the NeoForge nodes listed in CI; do not assume Fabric exposes the same task. |
| Plugin API or collection channels | `PluginHookPairingTest` and relevant plugin-consumer tests within `:common:check`. |
| Script API, managed declarations, or Probe | `ApiManifestGoldenTest` and relevant surface tests; `npm run test:probe-types` after the documented dependency setup when TypeScript declarations are affected. Type checking alone does not prove runtime behavior. |
| Runtime reload, state, persistence, or threading | Relevant regression checks for success, failure retention, cleanup, isolation, and ordering; add platform/GameTest evidence when the behavior crosses into Minecraft. |
| Loader startup, Mixin, GUI/chat, or game behavior | Affected runtime smoke and `runGameTestServer` tasks where applicable, plus [Minecraft MCP evidence](minecraft-mcp.md) when a live client is needed. Follow CI's required-test success assertion; process startup alone is not a pass. |
| Prose documentation only | Check changed links/anchors, accuracy, consistency, and `git diff --check`. No game build is needed for prose alone; executable examples need the checks appropriate to their changes. |

- For non-trivial new behavior or a bug fix, add or update the smallest runnable check that distinguishes correct behavior from the failure. Reuse the existing test framework; exercise observable behavior, including the relevant failure or lifecycle boundary, rather than mirroring private implementation.
- Start with focused checks, then run affected integration and required CI gates. Respect the task's explicit acceptance requirements. Local results do not imply that other platforms, JDKs, CI jobs, or release acceptance have passed.
- Run ordinary tests without updating goldens. A deliberate contract change must explain and review each relevant golden/snapshot difference before accepting it. Never relax isolation rules, delete a failing regression, or add a broad exclusion merely to make checks pass.
- If a failure prevents further verification, report its evidence and separate it from checks not run. Resolve newly introduced failures; do not claim a pre-existing failure without evidence.
- Before delivery, inspect the complete diff against these rules and report the four items required by [AGENTS.md](../../AGENTS.md#verification-and-delivery). Include enough command and result details to reproduce verification.

## Maintaining these rules

- Add a rule for a concrete recurring mistake or project invariant. State its scope, meaningful exceptions, and how it is checked. Write the rule directly and keep detailed guidance in one place.
- Use existing formatters, compilers, tests, and CI gates for deterministic checks. Review judgment-dependent rules in the diff. A language checker must distinguish comments/log templates from localized text and exact-input fixtures before it can block changes.
