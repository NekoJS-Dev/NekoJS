# AI authoring implementation

Date: 2026-10-31

Approved designs: [support](../design/2026-10-31-ai-support-plan.md), [queries](../design/2026-10-31-authoring-query-api.md).

## Delivery sequence

1. Bundle and export versioned English handbooks, a scripting Skill, examples, and a create-if-missing AGENTS template. Preserve user edits and publish content hashes.
2. Capture structured TypeScript member records at their actual emission sites. Publish an immutable snapshot only after successful Probe output. Declare incomplete coverage for authored fragments and unsupported projections instead of deriving signatures with a second reflector or a regular-expression parser.
3. Implement one bounded read-only query core: capabilities, context, resolve, members, correlated predicates, selection, stable snapshot cursors, and schema discovery. Reject unsupported effective generic views until they can be proved against declarations.
4. Provide two MCP modes: explicitly enabled in-mod Streamable HTTP while the game runs, and an optional standalone stdio companion for offline snapshots. Both share the query core. Ordinary JSON HTTP remains available; documentation export alone opens no port.
5. Document actual configuration, examples, coverage, and limitations in both wiki languages. Validate the common checks, query/transport regressions, declaration parity, bundled examples, wiki, and affected packaging. Leave the implementation uncommitted; do not stage unrelated working-tree changes or push.

## Acceptance boundaries

The first delivery is a read-only authoring foundation. Runtime evaluation, script writes, reload tools, registry/world inspection, arbitrary generic assignability, and Python structured member projection remain later phases. The capability document must expose the exact implemented subset. No test may claim that an incomplete query proves an API absent.

## Delivered foundation

- The shared read-only core provides context, capabilities, schema, resolve, members, query, and batch. It validates exact scopes and typed fields, matches each overload separately, correlates parameter predicates, preserves unknown versus known-null data, selects fields, and binds signed cursors to immutable snapshots.
- Structured TypeScript records are captured at final declaration emission sites, including emitted property spelling. Binding aliases and delegated members retain their actual declaration provenance. Repeated delegate sources cannot duplicate member IDs.
- Successful Probe output publishes a content-addressed snapshot and atomically switches its pointer. Failed output/publication leaves the prior pointer intact. Loaded snapshots are checked against content digest, environment, and snapshot identity.
- Runtime-root ownership retains the service across ordinary script reloads and closes it on root teardown. HTTP and MCP listeners are opt-in, IPv4-loopback-only, bearer-authenticated, and bounded by request, response, concurrency, and deadline budgets.
- MCP supports the 2025-11-25 protocol through a separate stdio process and optional sessionful Streamable HTTP JSON POST/DELETE. Both reuse the same tool/resource dispatch and query core. SSE streams, prompts, subscriptions, OAuth, CORS, and hard computation cancellation remain unsupported.
- The versioned English bundle contains handbooks, runnable fixture-tested examples, a Skill, and a create-if-missing AGENTS template. Existing user files are preserved; modified immutable payloads or manifests are rejected without switching the current pointer.
- Chinese and English AI-support wiki pages explain discovery, configuration, queries, MCP sessions, security, and unsupported features. Stable error-reference entries cover NEKO-1019 and NEKO-4055.

## Earlier stdio-first follow-up

The user requested a simpler connection than manual HTTP session setup. This follow-up initially presented the primary entry as `java -jar nekojs-1.1.0-preview3-authoring-stdio.jar --game-dir <game-directory>` using Java 21+. A normal MCP client launches this process and performs initialization itself; no port, token, HTTP bridge, or game stdin/stdout is involved.

- The companion contains the pure authoring query/protocol stack, versioned English bundle, and existing Gson dependency only. `:common:assemble` builds the executable alongside the common mod JAR. It excludes platform, Minecraft, Graal, runtime listeners, configuration writers, and bundle exporters.
- The offline session reads the exported `nekojs/ai/environment.json` and its game-relative custom snapshot directory. It verifies identities, digests, bounded UTF-8 files, and real-path containment without writing files. Queries reflect the last successful TypeScript Probe snapshot, not live world state. Exported `apiEnabled` is honored; the CLI does not parse `ai.toml`. An environment change requires restarting the companion.
- Standard output is bounded newline-delimited JSON-RPC only; diagnostics use standard error. Stdio has its own initialized lifecycle, no HTTP session headers or expiry, clean EOF, strict UTF-8, and 64-KiB frame/response limits. Processing is sequential; active cancellation is not implemented.
- A read-only review found a pointer/cache race shared by offline and HTTP sessions. Both now load the exact pointer text used as cache identity instead of rereading it. Regressions cover pinned-pointer loading and deterministic A-to-B-to-A publication.
- Bundled handbooks, Skill, AGENTS template, and both wiki languages now present stdio first while retaining optional HTTP guidance. The documented client JSON configuration is parsed and checked against the packaged version and exact CLI argument contract.

## Initial foundation verification recorded on 2026-10-31

| Check | Result |
| --- | --- |
| `./gradlew.bat :common:check :common:jar --console=plain` | Passed: 2,537 tests, zero failures/errors, four skipped; includes isolation and addon fixture dependency checks. |
| Authoring and Probe regressions within common check | Passed: bundle 7, HTTP/MCP 8, query 14, publication 3, emission parity 1. API manifest golden tests: 5 passed, no golden updates. |
| `npm run test:probe-types` | Passed for all generated declaration fixture projects. |
| `npm run test:wiki` | Passed: eight tests. |
| Packaged common JAR audit | Passed: all 17 English bundle payloads match source bytes and manifest SHA-256 values; generated manifest and query classes are included. Version remains 1.1.0-preview3. |
| `git diff --check` | Passed for tracked changes; an explicit scan of 40 new deliverable text files also found no trailing whitespace. |
| `guardLint` | Failed on the existing unrelated `src/main/java/com/tkisor/nekojs/mixin/RecipeManagerMixin.java`: `守卫未配对（if=6, close=4）`. This change does not alter that file or weaken the gate. |
| `npm run check:wiki` | Passed: 27 bilingual topics, zero problems, after canonical topic registration, navigation generation, and selected digest stamps. Translation status remains draft and human review remains pending. |

The first full common run found two new query regressions; both were fixed before the successful final run. The fixes preserve explicit nested JSON null values and propagate incomplete-array uncertainty through negation instead of weakening assertions.

## Stdio follow-up verification recorded on 2026-10-31

| Check | Result |
| --- | --- |
| `./gradlew.bat :common:check :common:assemble --console=plain` | Passed: 2,553 tests, zero failures/errors, four skipped across 350 suites; isolation and addon dependency checks passed. Both companion and common JAR built. |
| Stdio and offline regressions | Passed: stream protocol 8, offline snapshot session 5, actual standalone subprocess 2. Bundle examples/configuration 8, HTTP/MCP 8, query 14 also passed. |
| Standalone process contract | Passed: launches with only the executable JAR, initializes and queries a fixture snapshot, emits JSON-RPC-only stdout, writes no game files, handles EOF and stderr-only CLI failures. Stream tests compare all query fields except independent request IDs. |
| Packaged artifact audit | Passed: both JARs contain the same generated manifest and all 18 source-identical payloads with valid SHA-256 hashes. Companion is 394,527 bytes, 270 classes; maximum class major 65 (Java 21). Only authoring, Gson, and its existing Error Prone annotation dependency namespaces are present; no game/runtime/listener/exporter classes. Version remains 1.1.0-preview3. |
| `npm run check:wiki && npm run test:wiki` | Passed: 27 bilingual topics, zero problems; eight generator tests. AI topic stamps regenerated; human review remains pending. |
| `git diff --check` | Passed; existing unrelated newline normalization warnings remain. |
| `./gradlew.bat guardLint --console=plain` | Still fails only on the previously recorded unrelated `RecipeManagerMixin.java` guard pairing (`if=6, close=4`); no gate or unrelated source was changed. |

The initial focused stdio run exposed two test assertion mistakes: dependency isolation omitted Gson's existing Error Prone annotations, and envelope parity incorrectly compared independently generated request UUIDs. Tests now explicitly permit only that existing dependency namespace and validate UUIDs before comparing every other result field. All focused and full suites passed after correction. Declaration emission was not changed by this follow-up; the earlier TypeScript fixture check remains the recorded result, not a new run.

## Dual-mode clarification

The user clarified that both entry points should be offered, with the in-mod service explicitly enabled rather than forcing an external JAR workflow. No transport was removed or newly invented: the root-owned in-mod MCP listener already existed and remains disabled by default.

- While the game runs, enable `[mcp].enabled = true` in `nekojs/config/ai.toml`, then restart the game process. A compatible Streamable HTTP MCP client needs only the endpoint URL and bearer credential, not a Java command, companion JAR path, or game-directory argument. It handles initialization and sessions automatically; low-level JSON-RPC steps are protocol reference rather than manual user setup.
- The in-mod service uses its own instance paths and latest successful Probe snapshot. No zero-configuration discovery or automatic client reconfiguration is claimed. URL and authentication still need client setup, and credentials must stay private.
- The standalone stdio companion remains available for offline snapshot access and stdio-only clients. That separate process requires explicit JAR and game-directory paths. The game process itself is not a stdio MCP subprocess.
- Bundled English references, Skill and AGENTS template plus both wiki languages distinguish these choices. Runtime behavior, listener defaults, authentication, snapshot publication and query contracts are unchanged.

### Dual-mode clarification verification

- `./gradlew.bat :common:test --tests "com.tkisor.nekojs.authoring.*" :common:assemble --console=plain` passed: 46 tests, zero failures/errors/skips. Bundle tests now verify both the URL-plus-bearer in-mod example and standalone process arguments, with explicit opt-in leaving ordinary HTTP disabled. Both artifacts were rebuilt.
- Both rebuilt JARs contain the same generated manifest and 18 source-identical SHA-256-verified payloads; bundle ID is `76e43b6474b926e45e0f83ab5257089e6222bccfad57166ac115df5bf437021d`. An initial audit script incorrectly treated the manifest file array as an object; it was corrected to the existing manifest schema and the read-only audit passed.
- Strict wiki validation passed with 27 bilingual topics and zero problems; all eight wiki tests passed. The 15 bundled Markdown documents passed English/link checks, and documented JSON examples parsed successfully. `git diff --check` passed with only existing unrelated newline-normalization warnings. Wiki translation status remains draft and human review pending.
- This clarification changes documentation and documentation-contract tests only. The earlier full common check and guardLint results remain historical; they were not rerun. Live game/client integration and human acceptance remain unperformed.

## Scripting-example follow-up

The user found that the handbook mostly explained constraints and queries rather than showing how to write scripts. The bundle now contains a complete [scenario cookbook](<../../common/src/main/resources/nekojs/authoring/handbook/scripting-examples.md>) and [scenario metadata](<../../common/src/main/resources/nekojs/authoring/examples/metadata.json>): 12 scenarios, 14 actual source files, ten substantive scenarios plus the two earlier warmups. Installation is opt-in; authoring transports still cannot install or evaluate scripts.

- Added login messages; exact recipe removal plus shaped, shapeless and smelting recipes; distinct item/block tags; startup item/block registration; detached component-bearing stacks and explicit counts; primitive shared configuration, SERVER reload counters and a one-shot timer; a two-file CommonJS module; real TestJS assertions; and a CLIENT TSX signal counter.
- Every scenario has exact game-relative install destinations, required APIs/profile, execution trigger, expected observations, cleanup and evidence limits. Tags are gated to the NeoForge implementation; ClientUI is gated to the supported NeoForge 26.x CLIENT profile. Registry definitions require a full process restart, matching client/server content and disposable-world cleanup. Recipe replay uses whole SERVER reload; tags additionally need vanilla datapack reload.
- Source audits checked actual result-first recipe helpers, filter semantics, explicit recipe IDs, plain tag IDs and removal ordering, RegistryEvents.register with configuration-last builders, implicit BlockItem creation, Item.of component parsing, event/player APIs, state lifetimes and timer ownership. Ordinary stack Bean writes are deliberately not used in the detached-stack example. No production API or runtime behavior was changed by this follow-up.
- New inventory regressions require one scenario per shipped source, complete install/result/evidence metadata and exact source-code parity with the cookbook. Isolated callback/platform fixtures check the Minecraft-facing examples without pretending to execute Minecraft. Separate real-engine tests load the shipped CommonJS modules through the real module cache, run actual TestJS (three passed, zero failed), and compile/execute TSX with the actual UI runtime while replacing only the platform screen host; the latter asserts count 0 to 1 on click and cleanup on root.close.
- Added the full [Chinese practical review](<../reviews/2026-10-31-scripting-examples-zh-cn.md>) and linked it from the earlier [Chinese basic-handbook review](<../reviews/2026-10-31-ai-handbook-zh-cn.md>). All 14 source blocks and install paths match the English payloads; 29 links/anchors validate. Official bundled prose remains English. Both wiki languages now include a scenario table and concrete login/recipe walkthroughs; Skill/template and thematic handbook pointers lead to the cookbook.
- The initial focused run completed 56 tests with two fixture failures: real TestJS left a scoped log appender open during Windows temporary-directory cleanup, and the fake UI host searched child text even though the real JSX runtime normalizes label text into node.props.text. These require fixture cleanup/host-contract fixes, not changes to the shipped examples or weaker behavior assertions.

The first full run passed all 56 authoring checks but exposed 42 later data-generation failures: the new real TestJS fixture had initialized the process-wide path cache from a disposable game root, conflicting with the stable shared platform fixture. The test now initializes the stable shared platform while retaining temporary module/cache/filesystem paths. This is test isolation repair; no production path gate was loosened. Final verification for this follow-up:

- PASS: `./gradlew.bat :common:check :common:assemble --console=plain` (2m 25s). JUnit reports 2,564 tests across 352 suites, zero failures/errors and four skipped tests; common isolation and addon dependency-surface checks also passed.
- PASS: all 56 authoring checks, including seven isolated scripting-contract checks and three actual-engine checks for the CommonJS loader, TestJS assertions and TSX runtime with a fixture platform UI host. The unchanged full suite confirms the platform-path isolation repair no longer breaks data generation.
- PASS: both common and standalone stdio JARs contain the same generated manifest and all 31 source-identical, SHA-256-verified payloads, including 14 script files. Bundle ID: `8b9ea591960b5fc1ba5ec2c0ef25a44bc18c9a25bdb3822802733823509fbcbd`.
- PASS: `npm run check:wiki` (27 bilingual topics, zero problems) and `npm run test:wiki` (eight tests).
- PASS: Chinese practical-guide/source parity (14 exact script blocks and every metadata installation path), local links/anchors, English-only bundled prose and explicit new-file whitespace/control-character scans; independent read-only content review found no concrete defects. `git diff --check` passed with only pre-existing unrelated newline warnings.
- FAIL, unchanged and outside this follow-up: `./gradlew.bat guardLint --console=plain` reports `RecipeManagerMixin.java: 守卫未配对（if=6, close=4）` in [RecipeManagerMixin.java](<../../src/main/java/com/tkisor/nekojs/mixin/RecipeManagerMixin.java>). No guard was removed or weakened.
- NOT RUN: live Minecraft/player/datapack/registry/UI acceptance, five-node platform builds and a new generated-TypeScript fixture pass. These examples do not change declaration emission; actual common-engine TSX execution is covered separately above. Human acceptance remains pending; no maintainer conclusion is invented.

Both transports, explicit listener opt-in and all existing script contracts are unchanged by this examples-only follow-up. No version bump, commit or push was performed; unrelated worktree changes were preserved.

## Remaining gaps and acceptance

- This is declared, emitted TypeScript coverage, not full implementation of every design projection. Effective inherited/generic-instantiated member views, generic assignability, Python structured queries, runtime evaluation, script writes/reloads, and registry/world inspection remain unsupported and must not be inferred from zero results.
- Schema discovery exposes structural request schemas and typed field/operator catalogs, not complete response or resource-discriminated schemas. Context, resolve, and explain are intentionally reduced. Completeness is conservative at record level rather than tracked independently for every array.
- Cursors bind explicit request structure rather than normalizing semantically equivalent defaults. Batch pagination callers must retain the returned shared snapshotId.
- Automated fixtures are not real-instance accuracy acceptance. Five-node production packaging, real Minecraft/AI-client integration, cross-platform security behavior, and maintainer human acceptance have not been run. The common JAR audit does not establish those release gates.
- No version bump, commit, PR, or push was made. Unrelated working-tree changes are preserved.
