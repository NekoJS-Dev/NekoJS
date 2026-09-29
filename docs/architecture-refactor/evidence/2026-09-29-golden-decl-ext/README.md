# events-declared golden extension: KeyBindEvents (ticket 26 AC7 leg) — review pack

**Base:** maintainer-approved `events-declared` family (`evidence/2026-09-29-golden-decl-prep/`,
merged 2026-09-29, commit `7985420d3`). This branch extends that family only; merging awaits
maintainer approval.
**Branch:** `golden-decl-ext` (worktree `../NekoJS-mult-tgext`), based on `mult@60e113a3`.
**Date:** 2026-09-29.

---

## 1. Why this extension exists

The prep batch's REPORT §4 excluded `KeyBindEvents` alongside `NetworkEvents`, `RegistryEvents`,
`DynamicRegistryEvents`, `ScriptEvents`, `ProbeEvents` — the stated reason was review scoping:
"the golden scope is the 11 groups of tickets 23/24/27 only". That reason was correct for a
single-topic review batch and is not an architectural impediment: `KeyBindEvents` is an
`EventGroup` registered through the same real registration entry points the family already drives
(`NekoJSCorePlugin.registerClientEvents`, guarded `//? if >=26` on neoforge; the independent fabric
twin via `FabricCorePlugin`), so it enters the family's derivation with no production change.
The 2026-09-29 in-review digest named exactly this as the remaining closure path for ticket 26:
"KeyBindEvents 侧需一次同款 golden 范围扩展" (`evidence/2026-09-29-inreview-digest/README.md`,
ticket-26 section: AC7 "半边解锁", KeyBindEvents 0 hits in all five client goldens).

## 2. What changed

1. **`src/test/java/com/tkisor/nekojs/platform/DeclaredEventSurfaceGoldenTest.java`** — adds the
   ticket-26 domain group `CLIENT_INPUT_HUD_GROUPS = ["KeyBindEvents"]` to the in-scope surface
   list; class javadoc updated (four ticket domains; the direct-call `register` member freezes the
   same way as the already-frozen `ClientEvents.hudRender` — both are `EventBusJS` group members
   and the family freezes what the production renderer declares); report scope string now
   `tickets 23/24/26/27 domains`. No other test logic touched: cross-node presence still reuses
   the ticket-33 read-only baseline (`event-surface-domains.txt` rows L54–58: `present` with
   `buses=pressed,register,released,tick` on the four 26.x nodes, `not-verified` on 1.21.1).
2. **Goldens regenerated through the workflow** (`-Dnekojs.golden.regenerate=true`, all five
   nodes, no hand-patching). Content changes: 8 files / 4 nodes; zero content change on 1.21.1
   (its three files and all five `*-server-events.d.ts` files were rewritten byte-identical
   modulo line endings — verified with `git diff --ignore-cr-at-eol` and reverted, mirroring the
   prep pack's handling of the same re-touch).
3. **`docs/architecture-refactor/baseline/2026-09-12-managed-surface/REGENERATE.md`** — the
   family's registry row now records the extended scope (ticket 26 / KeyBindEvents) and this
   review pack, per that document's role as the single registry of goldens.

No production source changed. No other golden family changed.

## 3. Per-node diff stats

`git diff --numstat` (insertions/deletions; the only deletions outside the test class are zero):

| Node | startup | server | client | KeyBindEvents in golden |
|---|---|---|---|---|
| 1.21.1 | 0 | 0 | 0 | absent (class guarded `//? if neoforge && >=26`; ticket-33 row `not-verified`) |
| 26.1.2 | +11 | 0 | +11 | present |
| 26.2.0 | +11 | 0 | +11 | present |
| 26.1.2-fabric | +13 | 0 | +13 | present (independent twin) |
| 26.2.0-fabric | +13 | 0 | +13 | present (independent twin) |
| test class | +20/−10 | | | |

Frozen member set on every node that has it — exactly the ticket-33 baseline
`buses=pressed,register,released,tick`:

```ts
namespace KeyBindEvents {
    function pressed(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
    function pressed(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
    function released(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
    function released(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
    function tick(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
    function tick(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
    function register(handler: ((event: $Object) => void)): void;
}
```

Declared-member counts (gate report, `versions/<node>/build/nekojs-gates/declared-event-surface-<node>.json`):
1.21.1 156/120/34 (unchanged); 26.1.2 and 26.2.0 164/121/41 (was 157/121/34); fabric nodes
97/77/19 (was 90/77/12). Delta = +7 on each CLIENT-visible document (startup and client): three
trigger buses × two overload forms (the `DispatchKey<String>` on the binding full id renders the
keyed form, same as `ClientEvents.generateAssets`) + the single `register` form.

Honesty notes (details in `03-per-node-honesty.txt`):

- **1.21.1: honestly absent.** The shared-tree `KeyBindEvents` is an entire-file
  `//? if neoforge && >=26` guard and its registration line sits inside `//? if >=26`; the node
  registers no such group and the golden carries none.
- **Server side: honestly empty.** All members are `ScriptType.CLIENT`; the production catalog
  never places them in the server document — identical to how `ClientEvents` behaves in the
  committed baseline (namespace present in startup+client, absent in server).
- **Startup side: present, matching production.** CLIENT-typed buses are declared in the startup
  document too — the committed baseline already renders `ClientEvents` there, so `KeyBindEvents`
  appearing in `26.*.startup-events.d.ts` is the same production semantics, not a regression.
- **Fabric: honest twin.** The fabric `KeyBindEventJS` payload reaches
  `$KeyMapping`/`$KeyMapping$Category`/`$KeyEvent`/`$MouseButtonEvent`/`$MouseButtonInfo`/
  `$InputWithModifiers` through the renderer's reflection BFS (fabric +2 import lines vs neoforge,
  hence +13 vs +11); the member set is identical on both loaders. This pins the ticket-26 fact
  that fabric `KeyBindEvents` is an independent twin with the same four script faces.

## 4. Ticket verdicts

**Ticket 26 AC7 (KeyBindEvents declaration leg): closes under the TS-first ruling.** The AC's
recorded gap was "declaration face 0 hits" for this domain; the digest's own criterion was that
the ClientEvents side became covered by the 2026-09-29 TS goldens and "KeyBindEvents 侧需一次
同款 golden 范围扩展". That extension is this pack: `KeyBindEvents.pressed/released/tick/register`
are now frozen in per-node TS declaration goldens guarded by the read-only gate on all five nodes.
Not done here (unchanged from the 2026-09-29 maintainer rulings): Python `.pyi` per-node goldens
(registered follow-up "Python .pyi 后补记 09/34" — applies to this domain like the other three),
AC5 real-machine `consumeClick()` end-to-end, and node runtime smoke (ticket 34). AC7 also bundles
non-declaration legs (Adapter contract traceability, runtime smoke) which this pack does not
address. Whether AC7 can now be ticked remains the maintainer's call.

**Ticket 29 AC9 (Assets declaration gap): boundary recorded, not carried — by charter.** The
digest already split this AC: the *event* leg is unlocked (`ClientEvents.generateAssets`/`lang`
frozen via `ClientEvents` since the prep batch); the remaining faces are the `Assets` typed
binding and plugin-only `generatedLangs()`. Those are **not event-group members** and cannot enter
this family's derivation: `NekoScriptCatalog.events` (both overloads) iterates
`IPluginRuntime.eventGroups()` only — `Assets` is registered on the `BindingRegistry` path
(`NekoJSCorePlugin#registerBinding`, `//? if >=26`) and `generatedLangs()` is a default method on
the common `NekoJSPlugin` interface (plugin face, not a script event). Carrying them would require
a different golden family (bindings/plugin declarations), which is a scope decision for the
maintainer/managed-surface owner, not a silent extension of this one. Net for ticket 29: this pack
changes nothing about AC9's remaining gap and adds no new coverage for it; the boundary statement
is now recorded here and in REGENERATE.md so the gap is documented rather than implicit.

## 5. Verification results

| Check | Result |
|---|---|
| Regeneration, all 5 nodes (`:<node>:platformGateTest -Dnekojs.golden.regenerate=true`) | **pass** ×5, BUILD SUCCESSFUL (`01-regenerate-transcript.txt`) |
| Read-only re-run after regeneration, all 5 nodes (roundtrip proof) | **pass** ×5, BUILD SUCCESSFUL (`02-readonly-gate-transcript.txt`) |
| Per-node honesty greps (1.21.1/server absence, member set, fabric twin imports) | all as expected (`03-per-node-honesty.txt`) |
| `:common:check` | **not run** — `common/` untouched by this change |

## 6. What the maintainer is asked to approve

One line: accept the `events-declared` family scope extension to `KeyBindEvents` (test surface +
regenerated goldens + registry row on `golden-decl-ext`) as the TS-first closure of ticket 26
AC7's KeyBindEvents declaration leg, with the ticket-29 `Assets`-binding/`generatedLangs()` faces
recorded as outside this family's charter.

## Files in this pack

- `README.md` — this document
- `events-declared-goldens-ext.patch` — the 8 golden content changes (4 nodes × startup/client)
- `declared-golden-test-ext.patch` — the `DeclaredEventSurfaceGoldenTest` scope extension plus
  the `REGENERATE.md` registry-row update
- `01-regenerate-transcript.txt` — regeneration commands and results, per-node numstat
- `02-readonly-gate-transcript.txt` — read-only gate re-run and per-node gate reports
- `03-per-node-honesty.txt` — per-node presence/absence greps and the frozen member set
