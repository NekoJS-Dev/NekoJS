# Ticket 36 trial prep — script-author task pack (DRAFT)

Prepared on branch `ticket-36-trial-prep-b` at base commit `c8173622` (2026-09-29), for the
script-author half of ticket
[36: P4 维护者与脚本作者真实试做](../../../implementation-tickets/36-release-maintainer-trials.md)
(status `ready-for-human`). The maintainer four-task cookbooks are the sibling pack in
[`../cookbooks/`](../cookbooks/README.md) (branch `ticket-36-trial-prep-a`, merged to `mult`).

**This pack is UNVALIDATED until the maintainer trials run.** The scripts are copyable examples
prepared by reading the tree at the base commit; none of them was executed on a live node during
preparation. Ticket 36 AC4 requires each task to come back with the actual diagnostics/error output
and a "did public docs suffice" verdict filled into the record tables in
[TASKS.md](TASKS.md); until then every "expected output" here is a hypothesis, not evidence.

## What is prepared

Eleven representative script-author tasks (the ticket's list) plus explicit-rejection variants
for capabilities that are unavailable / not verified on specific nodes (AC3: unavailable
capabilities are tested as explicit rejections, never as hidden-alternative-path hunts):

| # | Task | Files | Example provenance |
|---|------|-------|--------------------|
| 1 / 1b | 启动期注册 (+ fabric fluid rejection) | `01-startup-registration.js`, `01b-…` | verbatim from ticket 15 baseline (`2026-09-15-registry-startup/examples/registry-startup.js`) |
| 2 / 2b | Dynamic Registry (+ unavailable-node rejection) | `02-dynamic-registry.js`, `02b-…` | verbatim from ticket 21 baseline (`2026-09-22-registry-dynamic-sync/examples/dynamic-registry-transaction.js`) |
| 3 / 3b | Villager Trades add/query (+ fabric rejection) | `03-villager-trades-add-and-query.js`, `03b-…` | verbatim from ticket 22 baseline |
| 4 / 4b | Item/Block modification (+ 1.21.1 block rejection) | `04-item-block-modification.js`, `04b-…` | verbatim merge of the two ticket 39 baseline examples |
| 5 | server/client gameplay events | `05-gameplay-server-events.js` | verbatim from ticket 24 baseline (`gameplay-events.js`) |
| 6 / 6b | client input & HUD (+ fabric HUD rejection) | `06-client-input-hud.js`, `06b-…` | verbatim from ticket 26 baseline (`client-input-hud.js`) |
| 7 | ESM/CJS/TS imports | `07-module-imports/{js,cjs,esm,ts}/` | byte-identical to the canonical engine test resources (see [`07-module-imports/README.md`](07-module-imports/README.md)) |
| 8 | 诊断定位 (deliberate error → `/nekojs error` → locate) | `08-diagnostics-error-locating.js` | trial-specific; flow grounded in the ticket 30 diagnostics baseline + `wiki/命令.md` |
| 9 | declaration 使用 (probe + jsconfig) | `09-declaration-usage.ts` | trial-specific; grounded in `wiki/Probe-类型生成.md` + ticket 30 `WorkspaceGenerator` |
| 10 | 旧 global 迁移 | `10-legacy-global-migration-{server,client}.js` | ticket 10 baseline pair (client side +1 deliberate extra read) |
| 11 | capability 识别 | `11-capability-inventory.js` | trial-specific; read-only `globalThis` presence probe |

[TASKS.md](TASKS.md) holds the trial protocol: per task — goal, script placement, capability basis
(cited per owning ticket's baseline REPORT/MIGRATION and the
`src/test/resources/nekojs/platform-gates/event-surface-domains.txt` gate rows), expected
diagnostics/output, and the maintainer fill-in record tables (AC3/AC4).

## How each file was checked at preparation time (not a substitute for the trial)

- Every script's code body is either verbatim from a gate-tested baseline example or byte-identical
  to a canonical test resource; deviations are called out in the file header.
- API symbols, rejection messages, and gate rows cited in headers were verified against this
  worktree's sources, in particular: `RegistryEventJS.getMember` / `EventGroupJS.getMember` /
  `GlobalBindingMemberValidator` message templates, `NekoRegistryDeclarations` fabric sugar
  directory, `DynamicRegistryJS.requireRunningServer` gate messages, `VillagerTradeUnavailableDomainOwner`
  rejection text, `VillagerTradesPlugin`'s `//? if neoforge` guard, `FabricClientEventBindings`'s
  tick-only bus set, `KeyBindEvents`'s `>=26` guard, the `nekojs.error.tracker.warning` translation,
  `FabricNekoJSCommands`'s text fallback for `error`/`view_all_errors`, `WorkspaceGenerator`'s
  README.txt hint, and `ItemJS.of/empty` + the `getId()` ItemStack extension documented in
  `wiki/全局绑定.md`.

## What the real trials must still validate

- **All of it, on live nodes, by the maintainer personally** — the trial record tables in TASKS.md
  must come back filled with actual outputs; a task that needed internal implementation reading,
  duplicated facts sources, owner guessing, or Java edits is recorded as a failure per AC4.
- **The five known open items** listed at the bottom of TASKS.md, in particular the fabric
  `VillagerTrades.query()` binding question (03b) and the gate-row vs catalog discrepancy for
  fabric event members (task 5 note).
- Tasks 2 and 6 carry capability areas the owning tickets recorded as `not verified`
  (platform-side dynamic-registry activation; real key-press and HUD render end-to-end): the
  trial is expected to produce the first real-machine evidence for those paths, not to re-assert
  the baselines.

Ticket 36's file itself is untouched by this pack (no status or AC changes); preparation and
verdicts stay separate.
