# Local candidate and test summary

Snapshot: base revision `d5484f9d71fa5fd5b297ffa14d1cad864b594a56` plus dirty WIP, not an inferred commit. Five main jars are staged at `build/ticket37-local-candidate`; original/copy SHA256 and ZIP metadata are in [CANDIDATE.json](CANDIDATE.json). Existing differing candidate bytes are rejected, never overwritten. All metadata reports **1.1.0-preview3**, not final 1.2.0.

## Existing command results

- Build/artifact/isolation command: `gradlew.bat :common:check :common-api-processor:test :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest verifyExternalAddonIsolation guardLint --console=plain`; `build/ticket37-final-build.log` records BUILD SUCCESSFUL in 4m36s, 107 actionable tasks: 28 executed, 79 up-to-date. Platform/artifact gates and NBT tasks execute; common/processor tests are up-to-date. The log also lists ordinary node `:test` task executions; current XML scope is independently enumerated below rather than inferred from the `build` name.
- Ordinary-suite command: `gradlew.bat :common:check :common-api-processor:test :1.21.1:test :26.1.2:test :26.2.0:test :26.1.2-fabric:test :26.2.0-fabric:test --console=plain`; `build/ticket37-full-tests.log` records BUILD SUCCESSFUL in 19s, 78 actionable tasks: 2 executed, 76 up-to-date. **All ordinary test tasks in this repeat are UP-TO-DATE**, not fresh executions. The requested full-suite scope is accepted by Gradle; retained XML contains the suite inventory below.
- This manifest operation runs no new tests, CI gates, typecheck, install or remote CI. Previously requested worker gate/typecheck results are not assumed to have settled. Build success markers and complete log hashes are recorded in the manifest; exit-0 completion was supplied by the parent session.

## Actual retained ordinary JUnit XML

Counts sum `TEST-*.xml` only in each project's `build/test-results/test`, excluding platformGateTest/NBT task directories. `tests` includes skipped cases; failures and errors are zero, not a claim that skips were executed.

| Scope | Suites | Tests | Failures | Errors | Skipped | XML UTC timestamps, earliest → latest |
|---|---:|---:|---:|---:|---:|---|
| common | 276 | 2041 | 0 | 0 | 4 | 2026-10-06 18:47:54.791 → 18:50:16.419 |
| 1.21.1 | 94 | 420 | 0 | 0 | 31 | 2026-10-07 05:01:11.720 → 05:02:33.727 |
| 26.1.2 | 127 | 644 | 0 | 0 | 81 | 2026-10-07 05:01:12.406 → 05:02:45.168 |
| 26.2.0 | 127 | 644 | 0 | 0 | 81 | 2026-10-07 05:01:12.908 → 05:02:50.772 |
| 26.1.2-fabric | 76 | 378 | 0 | 0 | 37 | 2026-10-07 05:01:12.698 → 05:02:48.944 |
| 26.2.0-fabric | 76 | 378 | 0 | 0 | 37 | 2026-10-07 05:01:11.746 → 05:02:43.148 |

`PostEffectDeclarationParityTest`: one test, zero failures/errors/skips on **each of five nodes**. XML timestamps: legacy05:02:02.419Z; NeoForge26.1.2 05:01:58.690Z; NeoForge26.2 05:02:00.869Z; Fabric26.1.2 05:01:55.403Z; Fabric26.2 05:01:53.551Z.

`FabricEventBusBridgeTest`: **five tests**, zero failures/errors/skips in **both Fabric nodes**, timestamps05:02:05.495Z and05:02:02.115Z respectively. Its canonical inactive wrapper correction does not remove discovery on supported nodes.

## Exact-byte verification limits

- NeoForge26.2 candidate hash `600ca6cae32ed1fd7bc89d200aa283865f8e89f4694aeefb60cd55fbbfa8b88b` differs from native renderer/trade-session `32309A66568C6E2EB57CF01CAA5A0D5B4201E01A6555F229EC10EAE107E0CDB8`. Older pictures/Offers cannot certify the new jar without revalidation.
- Fabric26.2 candidate hash **equals** earlier official server smoke `6DAF9141EE25EA69B19BB70E857429439D59EE62030DC986055E2A9F86921A81` (case-insensitive hex equality). Those bytes have the separately recorded negative declaration rejection and empty-domain startup proof; not client rendering, full domain parity or all-node runtime certification.
- No full five-node exact-candidate native/runtime, first-frame or current policy-conformant performance result is claimed. Existing revision-bound performance results do not automatically certify these jars.
- No final1.2.0 version switch, publication, remote upload, release authorization or maintainer public-breaking sign-off is implied by local artifact preparation.
