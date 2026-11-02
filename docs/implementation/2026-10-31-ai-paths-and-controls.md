# Configurable AI outputs and opt-in live controls

Status: implementation and automated verification complete; live-game and maintainer acceptance remain unperformed. The user confirmed `.github/neko_ai` as the default. No commit, push, dependency addition or version bump.

## Delivered behavior

- `[output].directory` in `nekojs/config/ai.toml` selects one contained game-relative AI root (default `.github/neko_ai`). Bundle/current/environment/connection, API snapshots, agent routers, README and optional instructions use that root. Declarations, snippets, editor configuration and actual scripts retain their existing locations.
- Configuration, the bounded offline locator `nekojs/config/ai-location.json`, and private bearer token `nekojs/config/ai.token` intentionally stay outside the GitHub-oriented output tree. Connection descriptors explicitly mark token paths as relative to the game directory.
- Runtime resolves the path once and binds Probe to it for the root lifetime. Containment, portable path validation and symlink checks cover runtime publication, export, snapshot loads and offline discovery. Offline precedence is explicit `--ai-dir`, validated locator, new default descriptor, then legacy descriptor only when modern discovery is absent. Offline startup does not write config or read/relay credentials.
- AI-root `agents/` contains short English role routers pointing at the canonical handbook instead of duplicated API facts. `[output].editor_agents = true` additionally creates standard `.github/agents` bridges; default false. Nonstandard agent and instruction directories are not automatically discovered by clients.
- Existing user files are preserved. Exact recognized legacy generated roles are backed up before replacement; unchanged router roots can refresh with backups. User-modified, oversized and nonregular files are not overwritten. Legacy trees are not silently moved or deleted.
- `[control].commands = false`, `reload = false`, `command_allowlist = []` are independent defaults. Mutation tools are available only through in-mod MCP with a running live host and the relevant explicit policy. HTTP query routes/batch remain read-only; standalone stdio always rejects live operations with `LIVE_RUNTIME_REQUIRED`.
- Command requests use exact root-token allowlists, a non-player permission-level-2 source, request-local bounded text feedback and owner-thread dispatch. Nested Minecraft command contexts are rejected before dispatch. Top-level dispatch aggregates branch callbacks and reports missing callbacks as failure only after synchronous dispatch returns. The allowlist is not a sandbox for plugin side effects.
- Script reload initially supports full SERVER reload only. It retains candidate transactions and reports `committed` separately from recipe/pack/resource postprocessing. CLIENT, STARTUP, file and package reload are not silently approximated.
- Queued expiry/cancellation/shutdown/epoch changes prevent later execution. Running timeouts remain unknown and non-retryable; the same live epoch keeps the active gate until actual completion. Retiring a server releases stale accounting without making old effects safely retryable; late callbacks cannot clear a replacement gate. Root teardown stops controls before closing managers.

## Review fixes and outcome reporting

- Resource signatures now distinguish desired selection from actually loaded content. Failed reloads remain dirty; late old-repository completion cannot clear a new server; removal of the last pack requires real successful reload. Reset retires pending acknowledgements.
- `EventBusJS.postChecked` records scoped listener failures without changing normal continue-on-error/cancellation semantics. Both recipe-manager mixins report callback/condition/decode/after-recipe failures and expose fail-closed replay observations.
- Actual Minecraft sources/jars confirm resource reload constructs and installs a new RecipeManager. Resource completion therefore inspects the current manager: retained identity uses the operation baseline; a newly installed identity must have completed at least one replay with no failures since creation. There is no global stale ErrorTracker shortcut or third replay.
- New platform diagnostics use documented numeric NEKO codes. No failed gate, path validation or golden assertion was weakened.

## Verification results

- Final `:common:check :common:assemble` passed (3m42s): 2637 tests, zero failures/errors, four skips, 359 suites; common isolation and addon dependency gates passed. The original Java-21 test toolchain was retained, and no goldens were updated.
- Five real platform compilations passed twice, including the final diagnostic changes: `:1.21.1:compileJava :26.1.2:compileJava :26.1.2-fabric:compileJava :26.2.0:compileJava :26.2.0-fabric:compileJava`.
- Restored active node `26.1.2` through canonical `switchVersion`, not by hand-patching generated sources. Final `guardLint` passed: 385 guard blocks across 581 files, zero exemptions/warnings. The earlier malformed-guard failure no longer reproduces after recipe-hook restructuring and canonical switching; the gate itself was not weakened.
- Focused authoring/Probe/path/agent/resource run: 125 tests, one stale HTTP fixture still targeting the old snapshot root; updated that fixture to the configured root.
- First full run under a temporary Java-25 test launcher: 2637 tests, two legacy declaration golden failures from new JDK-25 Reader methods. Goldens were not updated. Final rerun uses the original Java-21 test toolchain.
- Windows Gradle-JVM-21 child launch failed with CreateProcess error=740. Using `-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25` for the Gradle daemon fixes process startup without changing production compile targets. The temporary launcher override is not a production build change.
- Final strict bilingual wiki check passed: 27 topics, zero problems; eight wiki tests passed. Translation metadata remains draft/pending rather than claiming human approval.
- Both common and standalone JARs contain the same 32 source-identical, SHA-256-validated payloads and manifest (bundleId `ee20ec160c82e3c06f9a361e28dccc1ba070b8dbd34025ba1a420f2e9c8762f7`). The offline JAR excludes live-control contracts/core/platform classes. The audit command initially failed from shell quoting; the corrected ignored-build audit script passed.
- Key regressions passed: controls16, paths12, MCP mutation policy8, agent router refresh5, Probe paths6, resource application state5, EventBus outcomes6. `git diff --check` passed; explicit scan of 67 affected authoring/resource/test files found no trailing whitespace, control bytes or Chinese bundled prose.
- `npm run test:probe-types` was not rerun in this follow-up; its previous success is historical. No production platform JAR packaging, live client/server playthrough or permission/owner-thread Mixin runtime smoke test is claimed.

## Remaining acceptance limits

- No live Minecraft/client acceptance or maintainer approval was performed. Compilation does not prove runtime Mixin application, command permission/output behavior or real owner-thread reload/world-stop effects.
- Vanilla 1.21.1 recipe JSON rejected upstream before the injected apply hook cannot be attributed by the scoped replay observer. Checked script callback and observed decode failures are reported, but this is not universal raw-resource validation.
- Concurrent malicious filesystem replacement is outside ordinary path revalidation guarantees. External commands/plugin callbacks may have effects outside the candidate transaction; running actions are not interrupted or rolled back.
- Existing query limits (declared emitted TypeScript only, conservative completeness, no inherited generic view/Python assignability) remain unchanged.
