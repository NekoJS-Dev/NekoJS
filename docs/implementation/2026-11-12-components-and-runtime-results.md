# Object item components and queryable runtime results — 2026-11-12

## Authorized scope

Complete the existing item-object conversion path first, then expose existing errors/operation audit/TEST counters. No replacement component system, diagnostic engine, persistent operation journal, extra execution budget or arbitrary script-evaluation endpoint is introduced. All preceding uncommitted Wiki and bugfix work remains intact. Verification was completed before staging or committing; the user subsequently authorized committing this delivery together with the preceding Wiki audit and bugfixes. This is not a release/version bump, push or installation into a user world.

## Item objects: existing native codecs

- NeoForge 1.21.1 now declares and consumes object `components`, applying a native `DataComponentPatch` to a copy before count. Existing 26.x callers retain their interface and gain the shared repair.
- `DataComponentsAdapter.toPatch(Value)` uses `ItemStackStringRegistries.current().createSerializationContext(JsonOps.INSTANCE)` on each call. The existing server/client/built-in lookup policy remains unchanged; no world lookup is cached. The obsolete whole-file 26-only guard is removed because the same codec interface exists on all supported nodes.
- Actual native codec errors fail conversion with their original cause and `NEKO-4054`. The built-in-only fallback cannot invent unloaded world/datapack registries. Removals, nested holder lookup and patch normalization remain Minecraft semantics.
- No speculative ItemStack extension getter/setter was added. The remaining generic `CodecAdapter` is not the object-component path; its only current built-in registration is Fireworks. Startup item-definition lifecycle and unrelated serializers are outside this patch.

Five native 1.21.1 cases executed with zero skips/failures: primitive components/count/copy isolation; nested `can_break` registry holders and full ItemStack codec roundtrip; component removal with native normalized patch equivalence; genuine native-error inputs retaining typed causes; native accepted optional-field fallback equivalence.

The original four tests demonstrated actual RED before production changes: max stack size remained 64 instead of 16, complex components were absent, removal did not happen, and invalid components were ignored. Two initial GREEN failures exposed incorrect new-test assumptions, not more production defects: Minecraft normalizes removal of a non-prototype component away, and its AdventureModePredicate fallback accepts some malformed optional nested selectors. Primary native source and direct codec results justified calibrating those tests, while retaining genuine strict-error and copy-isolation assertions. No historical gate was weakened or manual component validator invented.

## Read-only live result queries

The live root injects the existing `errors().records()` frozen view (including pack errors) and its latest finished TEST report into root-owned `AuthoringRuntime`. Queries do not enter a guest Context or dispatch owner-thread work.

| Entry | Request / response contract |
|---|---|
| MCP `nekojs_errors` / HTTP POST `errors` | Optional uppercase `scriptType`; limit default 25/max 100. Bounded diagnostic ID/type/phase/owner/generation/candidate flag, relative source path, line/column and untrusted message. |
| MCP `nekojs_operation_status` / HTTP POST `operation_status` | Complete admitted `operationId` UUID; returns found/state/terminal/claimed/retryable plus bounded initial/latest metadata. Never re-enqueues or retries. |
| Existing MCP `nekojs_context` / HTTP GET `context` | Nullable `testRunReport` from the latest finished attempt, not live progress for an in-flight operation. |

These reads need the normal existing bearer/loopback transport authentication, but not mutation flags or a Probe snapshot. Live MCP has seven snapshot/documentation tools plus two live read tools and zero to two separately enabled mutation tools. Legacy query-only MCP constructors retain seven tools unless given explicit live-query support. Offline stdio retains seven, strips live reports, marks live queries unavailable, and rejects forged live calls before dispatch with `LIVE_STATE_UNAVAILABLE`. Snapshot `query.from`/batch resources do not gain mutation or arbitrary live-query children.

Errors reuse the current public error set, not a history/log stream. Projection is capped to 100 rows, 10,000 scanned frozen records and roughly 20 KiB; message cap is 1024 characters. Inspect totalIsExact/truncated; inexact totals are null. No raw Throwable, host stack, source snippet, private module/cache identity or absolute external path is exposed. An absent provider is explicitly unavailable, not fabricated empty state. Candidate-private diagnostics discarded by the existing transaction are not reconstructed as history; an empty current error set does not prove an earlier action succeeded.

Operation lookup reuses the existing pending set and 64-transition audit. A claimed timeout remains running/timed_out_pending until completion or owner retirement; the original timeout result never changes, while retained latest metadata can report eventual success/failure. Commands/arguments/feedback are excluded from retained lookup metadata. Missing and evicted IDs are intentionally indistinguishable (`unknown_or_expired`); that is not permission to repeat work. Tracking can become terminal after epoch retirement while external effects remain unknown. No ID-recovery or replay guarantee is fabricated after a lost response.

## TEST: execution versus assertions

The immutable public `TestRunReport` contains executionCompleted, executionSucceeded, passed and failed, with derived nullable assertionsPassed and overallPassed. Root run boundaries sample the existing host TestJS counters before/after, including legacy plus package scripts and the existing timer-drain window. A synchronized root run serializes report counter windows; the report is a detached volatile latest-result value, not another execution engine. No manager/executor/core-context or assertion-binding implementation was rewritten. Existing cumulative TestJS getter/summary behavior remains unchanged; report counts are attempt deltas.

- A caught failed assertion still yields assertionsPassed=false even when execution returns normally.
- Zero observed assertions yield assertionsPassed=null and overallPassed=false, not a vacuous green. Completing such an execution is not itself an execution failure.
- Uncaught failures retain the existing reload exception/candidate-retention behavior, publishing the failed attempt before the exception escapes. Completion means the attempt ended; execution success is separate.
- Completion does not certify callbacks scheduled beyond the existing run/drain window or future event handlers. Context may still show the previous finished attempt while a new operation is running.
- Minecraft authoring TEST now uses the actual root TEST run flow and attaches its report. Failed observed assertions/incomplete execution make the operation fail with TEST_ASSERTIONS_FAILED / TEST_EXECUTION_INCOMPLETE / TEST_EXECUTION_FAILED, without falsifying committed/postprocessing/effects. A caught assertion failure can have committed=true and effects=committed.
- NeoForge and Fabric command feedback uses localized, separate execution/assertion summaries. Failed observed assertions return command failure; zero assertions are explicitly unobserved. Existing error-panel feedback remains. Old two-argument TestRunResult and AuthoringControlHost.ReloadResult constructors/accessors remain available, without fabricating assertion evidence.

## Verification and diagnostic chronology

- First combined RED: two authenticated live routes returned 404; four actual native component cases failed as described above. The prototype TEST report check failed at a missing manager report interface; final implementation was deliberately moved to the single runtime root, so that prototype is not claimed as proof of a root-specific RED.
- Focused common run: 183 cases, with only two new TEST fixture failures. One was a Windows temporary-directory cleanup lock from a process-owned logger; one was a stale global pack-discovery root. Test-only fixes initialize the logger under the existing stable fixture and refresh pack discovery through its public path. The pack case now asserts actual committed pack execution before checking both assertion deltas. All 10 real-root TEST cases then passed with zero skips.
- Final focused result classes: structured diagnostics 5, live MCP 2, operation control 26, HTTP 12, stdio 9 and root TEST reports 10; zero failures/skips in these classes. Bundle verification remains 13 cases and executes all four original snapshot documentation requests plus both new live requests. The documented operation lookup reads an actually admitted command ID and verifies dispatch count stays one.
- First full run found one documentation fixture assumption (four JSON examples versus six after adding live requests). The test was extended to execute both additional examples through matching live runtime seams; its original four snapshot assertions were preserved.

Final Gradle command passed in **2m 7s**:

```bash
./gradlew --no-daemon :common:check guardLint \
  :1.21.1:test --tests "*Adapter*" :1.21.1:nativeRecipeProbeTest \
  :26.1.2:test --tests "*Adapter*" \
  :1.21.1:compileTestJava :26.1.2:compileTestJava \
  :26.2.0:compileJava :26.2.0:compileTestJava \
  :26.1.2-fabric:compileJava :26.1.2-fabric:compileTestJava \
  :26.2.0-fabric:compileJava :26.2.0-fabric:compileTestJava --continue --console=plain
```

This includes full common regression/isolation/dependency gates, ordinary adapter contracts, the isolated 1.21.1 native recipe/component gate, and production/test compilation for all five nodes. The common suite reports 2989 cases with four assumption/disabled skips; those are not counted as executed acceptance. No golden/snapshot was blindly updated. The full existing `pnpm run test:probe-types` chain passed with the bundled Node/pnpm runner after Gradle generated its fixtures.

Canonical English handbooks, connection/offline/control instructions, Skill and AGENTS template, plus the paired Wiki adapter/AI/command pages were updated. The existing build regenerates content-hashed bundled manifests from canonical resources; no generated manifest was hand-patched. Wiki sync/stamp/strict validation passed at 32 bilingual topics, all eight Wiki tool tests passed and the changed-code syntax harness passed 36 JS/TS fences. Final closeout verified all 62 original page anchor identities are unchanged, canonical generation is content-idempotent and `git diff --check` passes. Lead diff/source review and bounded independent cross-reviews of the component/TEST/result-query methods found no additional concrete issues; these are not exhaustive branch reviews.

## Not run / remaining boundaries

No live Minecraft client/server, dynamic world registry lifecycle, modern-loader runtime, multiplayer, GUI/GPU or maintainer human acceptance was performed. Native metadata/codec fixtures are not those acceptances. Unrelated particle/general-codec/startup-definition JsonOps sites are not advertised as fixed; the object ItemStack patch route is. Retention is root-lifetime bounded memory, not persistent recovery. No unrelated Python/authoring gaps were claimed resolved.
