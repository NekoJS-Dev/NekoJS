# Default TypeScript event dependency repair

Source: `f370c6af2e87975747d2507bd58294e72813bb69`. Baseline: `2af1bf71dc6a83fc5f17afd1d35c2a43d77af1b0`. This is a declaration dependency correction. It does not change runtime access, Plugin API constructors, persistent data, wire contracts, or release policy.

The old event reference included every global structured builder, including unassociated STARTUP builders. Those globals merged with the retained manual declarations and caused seven TS2717 conflicts. Events now reference `@event-builders/index.d.ts`, rendered from the same catalog entries with explicit association to a registered event. The complete legacy `@registry-builders` remains available to explicit consumers. The manual producer and editor includes remain intact. Metadata-free entries with the same builder name do not enter the event companion.

## Verification

- Real focused RED and GREEN logs/XML are preserved in `validation/`. The new regression drives actual bootstrap, default collector, both production backends, manual coexistence and same-name spoof exclusion. Unassociated catalogs do not emit a companion/reference. Existing HostAccess/ClassFilter negatives remain enabled.
- Full common check/isolation, processor tests, five node builds, external-addon isolation and guardLint passed: 104 tasks, 39 executed, 65 up-to-date, 2m13s. The 779 ordinary test XML files report 4536 tests, 271 skipped, zero failures/errors. This count excludes older regeneration, NBT and platformGate report directories; those are not described as newly executed tests.
- `npm run test:probe-types`: PASS. The focused strict caller passes with `skipLibCheck=false`, manual declaration included, concrete callbacks/boolean returns, nullable sound and expected invalid calls. Its first attempt used the wrong `ServerEvents` group and failed; the corrected caller uses actual `DynamicRegistryEvents`. Both logs are retained.
- Installed the exact new official NeoForge 26.2 production JAR into a new owned profile `build/ticket37-ts-installed-26.2`, using only previously installed immutable libraries via a junction. Public Probe commands generated fresh output. Public commands also verified actual entity MobEffect application before and after identical-definition reload. Server PID12748 stopped through normal RCON and exited0. No clients participated.
- Entire actual installed Python output: 344 stubs, zero AST failures. The three dynamic callback/builder imports and default core exclusions pass again. Four Python files differ in bytes from the preceding run because declaration order changed; file sets are equal. No Python static typing acceptance is claimed.
- Complete strict TSC extends the actual server editor config, preserving all default includes, with only `noEmit=true` and `skipLibCheck=false`: **FAIL, exit2, 3109 diagnostics**. The prior 3116-diagnostic evidence remains unchanged. After normalizing owned profile prefixes and line/column locations, exactly seven builder TS2717 diagnostics disappear, with no added file/code/message diagnostics. Six existing diagnostics moved with declaration order. All other failures are retained in raw output.
- `--listFilesOnly` confirms the default closure contains the manual file and new three-builder companion, and excludes the legacy full builder index. The complete old builder and manual output files are byte-identical to the prior installed b7 output. No generated output was hand-patched.

Exact artifact hashes, report scope and evidence hashes are in [artifact-manifest.json](artifact-manifest.json) and [evidence-manifest.json](evidence-manifest.json). ZIP contents were byte-verified against their source files. Credentials, server.properties, worlds and raw JFR are excluded. Raw logs include existing Windows Netty appender diagnostics; this is not an error-free boot claim.

A delivery check found that Git had normalized one derived JSON (`live-diagnostic-delta.json`) before the raw-capture attributes took effect. Re-adding it with the established attributes restores its original CRLF bytes; all28 evidence entries now match their committed/index bytes and manifest SHA256. Diagnostic contents and verdicts are unchanged.

The archived harnesses retain their actual execution paths; the corrected audit loads the previous build audit with the companion-reference expectation updated. That previous audit is also archived for provenance. Restoring these scripts elsewhere requires restoring or adjusting paths, not editing generated declarations.

[Two-axis source review](REVIEW.md): zero demonstrated standards breaches, one nonblocking duplication suggestion, zero spec findings. Source-only review does not independently certify runtime evidence.

## Remaining acceptance

Full TS remains FAIL; Pyright and performance for this source are NOT RUN. Latest formal b7 reload320.6/327.2ms FAIL remains binding to b7, not reassigned to this source. STARTUP payload/sugar/custom/register typing, overloads and type isolation are separate work. Multiplayer prepare/ACK/catch-up/abort, all installed nodes, first-frame/visual and remaining domain windows remain open. Tickets15/16/22/28/34/37 are not collectively accepted; ticket38's accepted optional status is unchanged. No maintainer acceptance is fabricated, no additional public deletion or release is authorized.
