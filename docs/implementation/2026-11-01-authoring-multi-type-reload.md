# Multi-type live authoring reload

Status: implementation and automated verification complete. Live Minecraft and human acceptance remain pending. No commit, push or version bump.

## Requested behavior and contract

- Extend the existing default-off in-mod MCP `nekojs_reload_scripts` tool from full SERVER reload to full SERVER, CLIENT and TEST reload. Retain the same `[control].reload` authorization; standalone stdio and HTTP query/batch routes do not gain mutation access.
- SERVER retains its candidate transaction and checked recipe/script-pack/resource follow-ups. TEST uses the server owner thread and full TEST candidate reload without SERVER resource follow-ups; a committed generation is not an assertion-pass certificate. CLIENT uses the actual client owner thread, can be available without a live server, and is unavailable on a dedicated server.
- STARTUP remains `RESTART_REQUIRED` because irreversible registration is not a safe generation reload. File/package/all-target requests remain unsupported rather than silently approximated.
- Discovery returns currently authorized/available `liveControls.reloadTypes`, not a constant server-only list. Reload replies and bounded audit entries identify the selected type.
- Each queued ticket captures its target-local lifetime identity. Expiry, shutdown and stale identities prevent late start; identities from different owners are not compared. One shared active-control gate still prevents overlapping effects. Running timeouts remain unknown and non-retryable.
- Existing legacy host implementations remain server-only unless explicitly overriding typed availability and reload. A loader-gated client scheduler keeps client classes out of dedicated-server resolution paths.

## Verification

- PASSED: `./gradlew.bat -Dorg.gradle.java.home="C:/Program Files/Zulu/zulu-25" :common:check :common:assemble guardLint --console=plain`; full common tests: 2651 tests, zero failures/errors, four skips, 360 suites. Common isolation and addon dependency-surface gates passed. Java 21 test toolchain remained unchanged; no temporary launcher override or golden update.
- PASSED: all five node `compileJava` tasks (`1.21.1`, `26.1.2`, `26.1.2-fabric`, `26.2.0`, `26.2.0-fabric`), repeated after all production scopes were frozen. Restored the original `26.1.2` active node with `switchVersion -Pnode=26.1.2`. Guard lint: 385 blocks, 582 files, zero warnings/exemptions.
- Regression coverage: 22 core controls, six client owner-executor, nine MCP-control, nine HTTP/runtime and six standalone-process tests passed. Tests cover dynamic availability and server-only legacy defaults, selected owner dispatch/type propagation, client without server, dedicated-client/STARTUP refusal, target-local epoch retirement, queue expiry/close fencing, shared active gate and late old callbacks. Candidate-failure/lifecycle scopes were reviewed; platform scheduler fixtures are not live-game evidence.
- PASSED: both rebuilt JARs contain the same 32 source-identical SHA-256 payloads and manifest (`bundleId: 4631fb2a26f66b85cb6b6bbf210c1bd4f5fd0654c48a0cbdfd542c39d99fc53e`). The standalone JAR remains Java 21-compatible and excludes client executor, host API and live-control/runtime/platform classes.
- PASSED: `npm run check:wiki` (27 bilingual topics, zero problems), `npm run test:wiki` (eight tests), bundled English/default-configuration/link audits and `git diff --check`. Translation review remains draft/pending, not human-approved.
- Independent read-only review reported no concrete production defects. Fixed one new fixture expectation before running Gradle: missing server for TEST is `LIVE_RUNTIME_REQUIRED`, not the CLIENT-specific `RELOAD_UNAVAILABLE`. The first blocking job-output wait hit the tool execution deadline; the original test job was subsequently collected as successful, without restarting it.
- NOT RUN: live client main-menu reload, UI rendering behavior, dedicated-server startup/classloading acceptance on either loader, real TEST assertion workflow, real SERVER recipe/resource acceptance, node production-JAR packaging, maintainer acceptance, or a new generated-TypeScript fixture run. Earlier evidence is historical, not claimed as this follow-up acceptance.
