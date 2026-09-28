# Ticket 36 trial prep — maintainer cookbook pack (DRAFT)

Prepared on branch `ticket-36-trial-prep-a` at base commit `c8173622` (2026-09-29), for ticket
[36: P4 维护者与脚本作者真实试做](../../../implementation-tickets/36-release-maintainer-trials.md)
(status `ready-for-human`; multiple blocking tickets are not yet closed).

**These drafts are UNVALIDATED until the maintainer trials run.** They were written by reading the
tree at the base commit, not by performing the tasks. Ticket 36 AC12 requires each cookbook to be
corrected by the corresponding real trial performed by the maintainer; until then every step here is
a hypothesis about the easiest legal path, not evidence.

## What is drafted

| File | Task class | Real mechanisms it documents |
|---|---|---|
| [new-event.md](new-event.md) | 新增事件 | `EventGroup.of` / `registerEvents` / `registerClientEvents` (`ServerEvents.java`, `EventGroup.java`, `EventGroupRegistry.java`, `NekoJSCorePlugin`, `FabricCorePlugin`), `EventBusForgeBridge` wiring, catalog-derived declarations, the ticket 33 event-surface gate (`EventSurfaceDomainGateTest` + `event-surface-domains.txt`), ticket 14 baseline |
| [new-adapter.md](new-adapter.md) | 新增 Adapter | `AdaptersPoint` / `JSTypeAdapter` / `JSTypeAdapterRegistry`, the handoff placement table (`src/main` vs `src/fabric` vs `versions/<node>`), `EventBusForgeBridge` + `Fabric*EventBindings` wiring examples, `McVersionCompat` facade, `ClientRenderEvents` node twins, guardLint, ticket 26/27 baselines |
| [new-extension-point.md](new-extension-point.md) | 新增扩展点 | ADR-0001/0002/0003/0010 Point / Contributor / Hook / Handle model, `NekoPluginExtensionPoint` builder, `MergePolicy`, `Sealable`, the four-part builtin channel change (Point file + `NekoJSPlugin` hook + `NekoBuiltinPointsPlugin` line + `PluginHookPairingTest` row), ticket 08 external-addon path |
| [new-version.md](new-version.md) | 新增版本 | `settings.gradle.kts` stonecutter DSL as the single node-graph facts source, `versions/<node>/gradle.properties` `deps.*` keys, `buildSrc` convention plugins, `stonecutter.gradle.kts` (`switchVersion`, replacements, `guardLint`, `sandboxCheck`, `verifyExternalAddonIsolation`), `gradle/libs.versions.toml` scope, `.github/workflows/ci-build.yml` node lists, `tools/nekojs-ci-gates.py` (ticket 33), throwaway-branch requirement (AC13) |

Each cookbook contains: purpose, verified public entry points, owners/facts sources, concrete steps,
contract/golden regeneration, required tests (real task names), the affected-nodes matrix note, the
"no runtime/bootstrap change needed" case, and a trial checklist table (Task / Entry / Result /
Problems) for the maintainer to fill during the real trial (ticket 36 AC1/AC12).

## What the real trials must still validate

- **Every step, in order, by the maintainer personally** — ticket 36's note states the four trials
  and their conclusions cannot be delegated to an agent. Any step that turns out wrong, redundant,
  or insufficient gets corrected in these documents, not worked around.
- **The checklist tables must come back filled** with task, entry, result, and problems; a trial
  that needed hidden paths, duplicated facts sources, owner guessing, or Java edits for script tasks
  is recorded as a failure per AC1/AC3/AC9.
- **Falsifiable expectations called out in the drafts**, in particular:
  - `new-event.md`: that a new builtin member needs only the group file + platform wiring + gate
    fixture row, and that `api-manifest-core.json` does not move.
  - `new-adapter.md`: that placement can be decided from the handoff table alone, and that probe
    aliases follow adapter registration with no hand-authoring.
  - `new-extension-point.md`: that the four-part change is complete for a builtin channel, and that
    a custom point needs zero engine edits.
  - `new-version.md`: that a trial node is build-graph-only, and how the by-design node-count
    sensitive gates (`verifyExternalAddonIsolation` 5-jar assertion, event-surface fixture coverage)
    behave on the trial branch.
- **Verification commands on the maintainer's machine** (Windows `gradlew.bat`, `npm ci` +
  `npm run test:probe-types`, `python tools/nekojs-ci-gates.py ...`) with real outputs.

## Known open items (TODO list carried into the drafts)

1. **`docs/agents/coding.md` is not committed at this base.** The task brief and the newer working
   copy of `AGENTS.md` reference it as the Script API conventions document, but it has no commit on
   `mult` at `c8173622` (it exists only as an uncommitted file in the main worktree). The cookbooks
   therefore cite the concrete committed sources (`ServerEvents.java`, ADRs, handoff, baselines)
   instead. TODO: once the conventions doc is committed, link it from `new-event.md` and
   `new-adapter.md` as the authoring-style reference.
2. **`new-extension-point.md`**: whether adding a default hook on `NekoJSPlugin` actually drifts
   `common/src/test/resources/nekojs/golden/api-manifest-core.json` (expected, but the manifest
   input set must be confirmed by running `ApiManifestGoldenTest` during the trial).
3. **`new-version.md`**: whether a node directory with no `src/` builds cleanly, or whether
   `versions/<node>/` needs a stub; not verifiable without performing the trial.
4. Drafts were written at `c8173622`; the trials will run on a later tree. Re-verify cited line-level
   facts (e.g. CI node lists, gate messages) against the trial base and update citations that moved.
