# P4 physical-architecture audit (AC7)

> Ticket 34 evidence pack, 2026-09-29. Automated production source/resource inventory per the
> implementation handoff §2 placement rules, covering **all** production files. Generator:
> `architecture-audit.py` (this directory, read-only); raw output: `command-output/08-architecture-audit.txt`
> (full per-file listing, 1084 files). Per the AC, this audit does **not** require physical
> migration of conforming sources and does not demand a hand-written repo-wide matrix.

## 1. Inventory coverage (all production files)

| area | files | handoff role |
|---|---:|---|
| `common/src/main` | 627 | engine/api/core/script/eventbus/probe — MC/loader-free rule |
| `src/main` (shared MC-facing) | 319 | shared wrappers/bindings/registry metadata |
| `src/fabric` | 71 | Fabric raw loader root (both 26.x fabric nodes) |
| `versions/1.21.1/src/main` | 58 | legacy-node whole-file overrides + node resources |
| `versions/26.1.2/src/main` | 3 | node-local evaluated code |
| `versions/26.2.0/src/main` | 3 | node-local evaluated code |
| `versions/26.1.2-fabric/src/main` | 1 | `Fabric261VersionCompat` + service registration |
| `versions/26.2.0-fabric/src/main` | 1 | `Fabric262VersionCompat` + service registration |
| `common-api-processor/src/main` | 1 | independent annotation processor |
| **total** | **1084** | every file listed in `command-output/08` (`FILE` lines) |

Shared resource splits (`resources-legacy` / `resources-modern` / `templates`) retain the roles
recorded in the W0 manifest (`baseline/node-source-artifact-manifest.md`).

## 2. Rule verdicts (automated)

| rule (handoff §2) | check | verdict |
|---|---|---|
| R1 `common/src/main` no Minecraft/loader imports (Graal allowed) | import scan `net.minecraft|com.mojang|net.neoforged|net.fabricmc|net.minecraftforge|org.spongepowered` over 627 files | **PASS — 0 violations** (graal-allowed imports listed where present) |
| R2 `common-api-processor` independent + MC/loader-free | same scan over processor main | **PASS — 0 violations** (backed by CI `:common-api-processor:test` 13/0, run 03) |
| R3 no duplicated business logic root ↔ nodes / node ↔ node | sha256 content identity between `src/main` and `versions/*/src/main`, and across different nodes | **PASS — 0 identical root-node copies; 0 cross-node identical groups** (the two fabric nodes share `src/fabric` by design, not by copy) |
| R4 `src/fabric` carries no NeoForge sources | NeoForge loader import scan over 71 files | **PASS — 0 violations** (textual NeoForge mentions in comments/guards are references, not sources) |

Cross-checks executed by the build itself: `guardLint` 341 blocks / 487 files / 0 warnings (run 01),
`checkCommonIsolation` green (run 03), per-node source-roots probe matches settings DSL for all five
nodes (run 04), ci-gates node-report green (run 05).

## 3. Per-file exception table (owner + retention reason)

No file violates a placement rule, so there are no nonconformance exceptions. The following
*allowed-by-rule* patterns are inventoried per file so nothing is silently excepted:

| pattern | files | owner | retention reason |
|---|---|---|---|
| whole-file overrides in `versions/1.21.1/src/main` duplicating a `src/main` relative path | 58 (full list: `OVERRIDE` lines in run 08) | ticket 01/24/28 domains (legacy node owners) | handoff §2 explicitly allows whole-file overrides in `versions/<node>` for high-turbulence/legacy-era code; R3 confirms none is a byte-identical copy of the shared file (all 58 differ in content) |
| per-node compat adapters as each fabric node's only local source | 2 (`Fabric261VersionCompat`, `Fabric262VersionCompat`) | tickets 31/32 (fabric convention) | node-specific evaluated code; version differences cannot be expressed as a small facade |
| `26.2.0-fabric` node identity (`deps.minecraft=26.2`, jar `nekojs-fabric-26.2-*`) | n/a (config) | handoff §4.1 confirmed stance | kept as-is by confirmed ruling; not renamed by W8/W9 |
| `src/fabric/test/resources/fabric-runtime-smoke/` fixture consumed by CI `cp` only | fixture set | ticket 31 record | documented as "CI copy ≠ test source-set mount" in the handoff §4.2 item 4 |

## 4. Deletion-ledger consistency (old paths / bridges / duplicate managers)

Checked against the handoff W-sheets and the owning tickets' closure records:

- **Fabric bridge**: `deps.fabric_source_node` bridge source reference and old exclusion rules deleted
  after ticket 31/32 parity proof (closure records); `src/fabric` is the mounted raw root
  (source-roots probe, run 04). No stale bridge files remain in the inventory.
- **Duplicate managers / static bypasses**: ticket 05 deleted static root bypasses; 06/07 deleted the
  legacy prepare and Context private-lock routes; 11 deleted implicit cache constructors (W3 cutover).
  The audit's zero identical-copy result plus guardLint's 0 exclusions-over-limit corroborate that no
  second runtime/manager copy is parked in root `src` or `versions/<node>`.
- **Executed deletions with sign-off**: `ClientRenderPlugin` (ticket 26, maintainer sign-off
  2026-09-25) — file absent from the inventory.
- **Zero-deletion rulings (2026-09-29)**: tickets 23/24/27/29 closed with zero deletions; deletion
  actions intentionally left to the maintainer where evidence was ready. These are open *actions*,
  not inconsistencies: the ledger (handoff + ticket records) matches the tree.

**AC7 verdict: PASS.** Automated inventory covers all 1084 production files; all placement rules
hold with zero violations; every allowed deviation is inventoried per file with owner and reason;
the deletion ledger is consistent with the handoff and the closed tickets' records.
