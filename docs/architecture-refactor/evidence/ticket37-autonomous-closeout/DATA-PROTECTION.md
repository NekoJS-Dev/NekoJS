# Data protection and local rollback handoff

This is an operator handoff, not final release policy or a general rollback guarantee. No data migration, default location, schema, key, wire id or enablement-rule change is introduced by this patch. The existing factual inventory is [ticket 03](../../baseline/2026-09-12-data-protection/data-inventory.md); current path ownership was checked against `NekoJSPaths` and the trust-store implementation.

## Classification and handling

Paths are relative to the owned game/profile directory; the world name is installation-specific.

| Data | Classification / backup | Preserve / restore boundary |
|---|---|---|
| `nekojs/config/engine.toml`, `probe.toml`, old `config/nekojs-engine.toml` | User-edited, not regenerable; stopped-process byte backup, even if malformed | Do not replace with defaults as a migration. Malformed engine config can fall back in memory. Explicit trust/config writes need their own reviewed policy. |
| World / entity / player pdata | Not regenerable; backup **entire stopped world**, including level/playerdata/entities/region/datapacks, not one NBT key | `NeoForgeData.NekoJSPersistentData` compatibility belongs to existing loader readers. Jar rollback cannot undo gameplay saves; stop first, retain failed/current world, restore separately. |
| Startup/server/client/test scripts | User-authored, not regenerable; backup full sources, dependencies and pack copies | Never "repair" by deleting/re-scaffolding. Captured diagnostic logs are not source backups. |
| GLOBAL `nekojs/packs`, WORLD `<world>/nekojs_packs` | User content, not regenerable; preserve manifests, files and `.neko_pack.state.json` enablement | World packs are not interchangeable with global or synchronized server-cache packs. Do not silently alter defaults or unknown keys. |
| `nekojs/config/trusted-servers.json` | Trust decisions/keys, not regenerable; secure byte backup | Trust-store supports temp+atomic replace (fallback where unsupported); corrupt reads can return empty state. Explicit subsequent writes can replace that state: preserve original backup rather than claiming corruption self-recovers. |
| Workspace `jsconfig.json`, `pyrightconfig.json`, `.vscode/settings.json`, README/user declarations | User edits not regenerable; back up before merges | Existing scaffold is only-if-missing; editor merge preserves unrelated keys. Production merge behavior is covered by `FileEditorConfigContributorTest`. |
| Probe `.neko_probe/{typescript,python}`, generated snippets | Regenerable from catalog + matching runtime; save current copy if needed for diff | Never classify arbitrary user files in generated directories as disposable without checking ownership. Generated outputs are not backup substitutes. |
| `logs/nekojs/*`, `logs/nekojs/old/*`, game logs | Historical diagnostics, not regenerable | Retain both current and rotated log before boot; existing one-generation rotation can replace old history. Do not commit private user/server information as evidence. |
| `nekojs/server_packs/<address hash>/<syncId>` | Conditionally regenerable only from reachable trusted server + matching sync id | Cache deletion is not trust migration; offline source may be unavailable. Preserve unknown/offline content until source revalidation. |
| `nekojs/node_modules`, process module cache, resource caches | Dependencies regenerable only with pinned sources; runtime caches memory/recreatable | Never treat package-local edits as disposable. Module/GPU cache close does not undo external effects. |
| Script-generated `nekojs/data`, `assets`, pack data | May contain generated and user-edited data | Classification follows producer ownership, not directory name; keep unknown/user-authored material. |

## Two separate rehearsals

Run from the repository root:

```text
python docs/architecture-refactor/evidence/ticket37-autonomous-closeout/rollback-rehearsal.py
```

The script creates a **new** `build/ticket37-rollback-rehearsal/owned-*` directory; it cannot accept a user profile. It swaps two real but byte-distinct NeoForge26.2 jars in that offline fixture and independently restores backed-up synthetic files. [Observed result](rollback-result.json): 12 protected file hashes match before/after candidate deployment and artifact rollback. After deliberately damaging one source fixture, jar rollback leaves it damaged; a separately staged hash-checked data restore restores exact bytes. Repeating that restore is idempotent. Failure-before-publish leaves live/damaged originals untouched; both backup and damaged original remain saved.

The older jar hash identifies the available rollback bytes, not an inferred source revision. This is **not** a game restart/playability test, production TOML/NBT reader test, directory-wide atomic restore, power-loss test or arbitrary world/network/Java side-effect undo. The old pdata literal/loader parser compatibility and workspace merge tests are independently covered in the five-node build; see [existing save-format fixtures](../../baseline/2026-09-16-data-sync/evidence/save-format-fixtures.md). Synthetic byte copying does not substitute for those production tests.

## Cutover / abort procedure

1. Stop the actual server/client and verify ownership and termination. Preserve complete profile/world/script/config/trust/editable workspace/log backups outside the install directory; record relative paths and hashes. No live-world copy is a consistency guarantee.
2. Record old and candidate jar/node/loader/dependency hashes. Only after confirming matching node compatibility, replace the owned mod jar; leave protected data untouched. Boot candidate and verify its exact artifact, metadata, runtime and required performance gates.
3. On release failure, stop again, retain new logs/current data, restore only the exact old jar/dependencies. Test old-code boot independently; do not claim this step reverses saved gameplay or script side effects.
4. If **data** restoration is separately chosen, retain the failed/current data, verify the original backup hashes, restore into a separate profile/world copy, re-read via matching production readers and test the world there. Do not overwrite the sole original. Changes made since backup will be lost if that restored copy becomes the active world.
5. Do not introduce schema migration without a reviewed version marker, old reader fixture, idempotence/failure tests, backup retention and exact node restore proof. No such migration is needed or implemented here.

Maintainer final rollback/cutover policy is still required; this technical preparation does not approve a downgrade on an arbitrary user installation.
