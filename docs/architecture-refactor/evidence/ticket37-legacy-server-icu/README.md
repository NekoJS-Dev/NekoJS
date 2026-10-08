# Legacy dedicated-server ICU packaging and installed-node checks

Packaging source: `0d06a6fb35a52a2c0d334e3d8f31f97c922ee55c`. Runtime source remains `ec7fe6848afc1970661ee1f38a7c7c6d4575530c`; all eight installed NekoJS JAR copies match an independent clean rebuild of the packaging commit. These commits and this evidence are local pending restored HTTPS credentials. No release was published.

## Change and contract

The ordinary 1.21.1 client JAR remains byte-identical (SHA256 `10379fbe2e643ea6839d26620a984d75ce2f18734ca99d3a93ebcb384fbb948d`). The new `nekojs-neoforge-1.21.1-1.1.0-preview3-server.jar` includes ICU through ModDevGradle's JarJar task (SHA256 `9bd9bfbfe225adf2548fe210f4a1d84517d955f7f8824dba669ca2f3a0333a16`). Every original ZIP entry is preserved; only JarJar directory, metadata and ICU entries are added. Install one environment-appropriate NekoJS variant and the existing Graal dependency. The server classifier must not be installed on a client, where the historical duplicate ICU module failure remains relevant. Other node artifacts are unchanged.

This changes artifact selection for a legacy dedicated server. Script API, Plugin API, data, wire identifiers, HostAccess, ClassFilter and path permissions are unchanged. See [plan](PLAN.md) and [two-axis review](REVIEW.md). Publication workflows still use legacy wildcard selection; primary-file/environment behavior must be verified before any future publication.

## Verification

The clean managed checkout ran:

```text
gradlew.bat :common:check :common-api-processor:test :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build guardLint --offline --console=plain
```

PASS, directly observed exit0, 1m36s, 106 tasks (36 executed, 1 cached, 69 up-to-date). The ordinary XML snapshot contains789 XML,4587 tests,271 skipped,0 failures/errors. Cached/up-to-date reports are not described as freshly executed tests. [Build log](legacy-server-full-matrix.txt), [summary](test-summary.json), [XML](full-matrix-ordinary-xml.zip).

Installed trials use separate owned profiles, new worlds, official loaders, actual RCON commands and unchanged sandbox permissions:

| Trial | Result and limits |
|---|---|
| Original 1.21.1 client artifact on dedicated NeoForge21.1.227/JDK21 | FAIL before readiness: missing `com.ibm.icu.text.DateFormat`. Process exit0 does not override the crash. |
| Original NeoForge26.1.2 attempt | Partial:14 registrations, nested fingerprint sensitivity, FluidType identity, placements, reload,377 Python AST; pre-reload entity insertion race prevented complete proof. Failed command retained. |
| Original Fabric26.1.2 and26.2.0 attempts | FAIL in the observer, which incorrectly treated Fabric's empty query-service fallback as a missing registration. The observer was corrected to read BuiltInRegistries; no platform capability was added. |
| NeoForge26.1.2 repeat | PASS for14 registrations, nested property/setter fingerprint equivalence and mutation sensitivity, shared source/flowing/registered FluidType identity, block/fluid placement, actual MobEffect before/after committed reload,377 Python AST. |
| Fabric26.1.2 repeat | PASS for7 supported registrations, nested fingerprint checks, actual block placement, committed reload and293 Python AST. |
| Fabric26.2.0 repeat | PASS for the same supported subset and259 Python AST. |
| New 1.21.1 dedicated-server variant | Boot,14 registrations, nested fingerprint checks, FluidType identity, block/fluid placement and committed reload PASS. Both Probe commands FAIL with `NoClassDefFoundError: net/minecraft/client/model/Model`;0 Python stubs. Overall NOT GREEN. The unsupported legacy registry-health command is retained without inferring a dynamic-registry capability. |

All final server processes exited0 after normal RCON stop; original crash processes also exited0 and remain failures. The first PowerShell installer wrapper did not observe exit codes; the Python repeat directly observed both NeoForge installer exits0. No client/visual acceptance is inferred from these dedicated servers.

## Evidence and remaining gaps

[Manifest](manifest.json) binds source revisions, eight artifact copies, checks, per-entry hashes and2938 archive entries. [Raw evidence](installed-node-evidence.zip) SHA256: `6c276d83ebdd88ea1f573aa21aa2b277c7c4bf8522549d43d7140d8cfa4f5ff8`. It includes failed initial attempts, corrected fixtures, exact generated output/default editor configs, commands, logs, process exit records and the archive helper. Archive entries were read back and hash-verified. Credential values were checked, including decompressed log files; server properties, worlds, account files, binaries and raw JFR are excluded.

Legacy Probe needs a separate source repair and exact installed replay. Full strict TypeScript and whole-project Pyright were not run on these new node outputs; earlier actual whole-project failures remain unresolved. Real in-flight configuration/pre-ACK disconnect, fireResistant lookup, first-frame/visual evidence, final maintainer acceptance and release/rollback decisions remain open. Exact ec7 performance evidence is linked separately in [the performance package](../ticket37-ec7-performance/README.md); it is not a measurement of a future Probe repair. Automation remains paused.
