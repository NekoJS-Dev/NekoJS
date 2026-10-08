# Legacy dedicated-server Probe linkage repair candidate

Status: **golden maintainer conclusion ACCEPTED for local submission; complete IDE acceptance FAIL**. [Direct maintainer record](MAINTAINER-ACCEPTANCE.md). Baseline `87e9b863`; production patch SHA256 `9b70e7439799f09ebf2c7fc2f551cd4e39da4118e62b861b27444e6c9715ea6f`. Installed rebuilt server JAR SHA256 `1bd35c6a79e243bf4094633e6fb194fd35d4aa0a8f7d84e5b4a8abcef5b84ba9`. This is not the earlier packaging commit's artifact or a remote release.

## Behavior and contracts

The original installed legacy server aborted both Probe commands while scanning `IClientItemExtensions` at depth1: its method signatures reference absent client classes. A default-catalog profile without custom registrations reproduces the same declaring type; the loader can also reject client dependencies with RuntimeException. This is signature resolution, not static initialization.

Shared collection now keeps other queued types and reports incomplete closure. Shared IR reports omitted declarations in every backend result. Fatal VM/ThreadDeath and interruption propagate; outstanding reflection Futures are cancelled. Event declaration imports use only the event and dispatch-key symbols actually referenced, avoiding a second unbounded member traversal. Missing types remain explicit warnings, not fabricated complete declarations. HostAccess, ClassFilter, scan filters/default includes, supported nodes, public API/data/wire contracts and legacy manual producers are preserved.

See [diagnosis plan](PLAN.md), [independent Standards/Spec review](REVIEW.md), and [exact source/golden diff](source-and-golden-diff.patch).

## Passed verification

Five focused regressions cover missing erased signatures, loader rejection, missing generic signatures, collector VM failure and fatal shared-IR cancellation. Both declaration backends preserve available output and warnings; the fixture host fails if Probe initializes it. Original missing-signature and subsequent owner-cancellation RED logs/XML are retained, followed by GREEN.

```text
gradlew.bat :common:check :common-api-processor:test :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build guardLint --offline --console=plain
npm run test:probe-types
```

Final matrix PASS, observed exit0,106tasks (34 executed,72 up-to-date),1m51s. Ordinary snapshot:790XML,4592tests,271skipped,0failures/errors. Up-to-date reports are not claimed as fresh execution. TypeScript contract fixture PASS. [Matrix log](legacy-probe-final-matrix.txt), [test/artifact summary](legacy-probe-final-test-summary.json).

Twenty event golden files were regenerated using the existing common task and five platform gates; later ordinary runs had no regenerate flag. Independent audits confirm all non-import text is identical. Import lines5162→230; namespaces, event payloads, signatures and dispatch overloads are unchanged. [Per-file audit](legacy-probe-golden-audit.json). The maintainer explicitly accepted this baseline change on2026-10-09 for local submission; see the [record](MAINTAINER-ACCEPTANCE.md) required by the [baseline workflow](../../baseline/2026-09-12-managed-surface/REGENERATE.md).

Exact new legacy server replay with original fixtures/new world passes14 registrations, nested fingerprint equivalence/mutation sensitivity, FluidType identity, block/fluid placement and committed reload. Probe generates334 TypeScript files and319 Python files, including317 stubs with0AST failures. Both backend responses explicitly report8 unavailable types (32 scan/IR warning lines across the two calls). Result: **PARTIAL available output**, not complete reflection closure. Normal RCON stop and process exit0 are directly observed.

The first candidate harness read only one RCON packet, missed the trailing success summary and correctly stayed NOT GREEN. Repeating with a later read-only marker command captured every response packet; the unchanged JAR then confirmed generation. Both trials are retained. No benchmark sampler was changed.

## Failed and remaining checks

Actual default editor includes are preserved; strict TypeScript changes only skipLibCheck to false and disables emit. Server has2785 diagnostics; STARTUP has2786; both exit2. Whole generated Python Pyright1.1.414 analyzes all317 explicitly enumerated stubs and reports129 errors, exit1. The first directory invocation analyzed0 files and is not a pass; an absolute-include attempt was also invalid and retained. Relative explicit enumeration corrected the harness. Generated output was not edited. [IDE summary](legacy-probe-ide-summary.json).

These counts concern this legacy node and must not be compared as a same-node reduction from earlier26.2 results. Other-node replay subsequently passed the supported server checks; see [replay evidence](OTHER-NODE-REPLAY.md). Missing metadata, real in-flight configuration/pre-ACK disconnect, fireResistant lookup, first-frame/visual evidence, current-source formal performance and final ticket/release/rollback acceptance remain open. HTTPS credentials remain unavailable by the user's choice to restore login later. Automation stays paused.

## Retained evidence

[Manifest](manifest.json) and [raw/source/output/XML archive](probe-linkage-evidence.zip),1543 entries, SHA256 `6337ca6a25fac687449e8fda13a0e24ce6b0778a89721af5724b95178ac7d81a`. Entries were read back and hash-verified. Includes initial diagnostic attempts, failed/green checks, baseline diffs, exact generated output, default configs, raw IDE diagnostics, fixture source, artifact hashes and process shutdown records. Credentials were scanned including decompressed logs; server properties, worlds, accounts, binaries and raw JFR are excluded. All owned trial servers have stopped; no test/build job remains running at this checkpoint.
