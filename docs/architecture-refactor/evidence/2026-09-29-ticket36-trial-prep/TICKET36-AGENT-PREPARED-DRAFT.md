# Ticket 36 agent-prepared trial draft

> Status: **AGENT-PREPARED — MAINTAINER CONFIRMATION REQUIRED**
>
> This draft consolidates repository evidence, automated checks, and agent-run Minecraft MCP smoke results. It does **not** claim that the maintainer personally performed the four maintenance trials or script-author trials. Ticket 36 AC1/AC2/AC12/AC13 still require the maintainer's real confirmation.

## Evidence basis

- Maintainer cookbook drafts: `cookbooks/new-event.md`, `new-adapter.md`, `new-extension-point.md`, `new-version.md`.
- Script-author protocol and records: `author-tasks/TASKS.md`.
- Fabric event bridge implementation: `src/fabric/java/com/tkisor/nekojs/fabric/event/FabricEventBusBridge.java`.
- Fabric bridge tests: `src/test/java/com/tkisor/nekojs/fabric/event/FabricEventBusBridgeTest.java`.
- Recent Fabric callback migration: command, level, block, server callback bindings.
- Recent 26.2 real-client smoke: `../2026-10-06-ticket48-e2e/mcp-smoke-2026-10-06.md`.

## Maintainer task draft

| Trial | Agent-prepared result | Evidence | Maintainer action |
|---|---|---|---|
| New event | Public path is documented: EventGroup member, platform wiring, event-surface fixture, gate, catalog/declaration checks. Fabric callback wiring is now expressed through `FabricEventBusBridge` for ordinary/cancellable/dispatch callbacks. | `cookbooks/new-event.md`; bridge tests; `:26.2.0:test`; Fabric platform gates | Perform one throwaway event addition and fill the cookbook checklist with actual path, output and problems. |
| New Adapter | Type conversion and platform wiring are explicitly separated. `AdaptersPoint`, `JSTypeAdapterRegistry`, Fabric/NeoForge bridge placement, probe alias derivation and guard checks are documented. | `cookbooks/new-adapter.md`; `:common:check`; `guardLint`; adapter tests | Implement one temporary adapter on the maintainer machine and record whether public materials were sufficient. |
| New Extension Point | Builtin four-part change and external addon custom point are documented: Point, hook, builtin manifest, pairing row, freeze/handle semantics. | `cookbooks/new-extension-point.md`; Point pairing tests; addon fixture tests | Perform one temporary builtin or addon point trial and record whether engine edits were avoided where required. |
| New version | Throwaway node procedure, node graph, properties, CI list, gate fixture and designed five-jar gate failure are documented. | `cookbooks/new-version.md` | Perform on a throwaway branch/fixture only; delete branch/fixture afterward and preserve command output. |

## Script-author draft

The task matrix and scripts already exist in `author-tasks/`. Agent evidence can confirm script locations, declarations, gates, source paths and expected unsupported capabilities, but cannot replace the author's real trial output. The following must remain blank until the maintainer runs them:

- actual per-node output/diagnostic text;
- public-material sufficiency (`足够` / `不足:<reason>` / `失败:<reason>`);
- final task conclusion (`通过` / `失败` / `待修复`);
- whether the author had to inspect internals, guess owner, duplicate facts, bypass managed APIs or edit Java.

## Current objective evidence

- Fabric and NeoForge builds/gates are green for the recent event bridge migration.
- The current 26.2 real client can load NekoJS and a converted JSX fixture. A real Screen opened and emitted live counters; the VNode shape mismatch was fixed in `common/src/main/resources/nekojs/node/internal/define.js` and protected by `NekoJsxClassicRuntimeTest`.
- This evidence validates implementation behavior, not the ticket 36 maintainer-experience conclusion.

## Required maintainer confirmation

The maintainer should review the four filled cookbook checklists and script-author records, then add a dated statement:

> I personally performed the recorded ticket 36 maintenance and script-author trials on the stated nodes. The outputs, problems, public-material sufficiency and conclusions are accurate. Unresolved items have an owner and release impact.

Until that statement exists, keep ticket 36 `ready-for-human` and leave its acceptance boxes unchecked.
