# Ticket 39 NeoForge 1.21.1 production smoke

Result: **real baseline → active → rejected candidate → empty-plan restore → fresh stack → relog passed** with the unmodified official build output. This completes the legacy item smoke, not independent-client network isolation or Fabric parity.

## Environment and artifact

Minecraft 1.21.1 / NeoForge 21.1.172 / Java 21.0.10 / MCP 0.4.2 / GraalMC 8762962; owned profile `build/ticket39-autonomous`, PID 22420, discovered endpoint 9875. Official NekoJS artifact SHA256: `A4B4557C565364039524DBDF2DC50BCE93599910E8D146CA95C1046FCFC3F876`.

The first exact official artifact reproduced [duplicate ICU module resolution](production-icu-collision.log). The build convention was corrected: legacy Minecraft supplies ICU on its module layer, so the legacy mod must not embed/redeclare it; modern NeoForge retains its intact ICU Jar-in-Jar. The node-specific verification gate checks both ownership rules, no flattened ICU, and complete ICU class/resource/module identity. Clean legacy build plus both modern artifact gates passed. The rebuilt official jar above was copied without ZIP edits, reached the [real title screen](production-title.png), and completed the world trial.

## Fixtures and controls

- Baseline/restore: [canonical fixture](../2026-10-05-ticket39-client/fixtures/1211-server-item-baseline-or-restore.js).
- Active: [canonical fixture](../2026-10-05-ticket39-client/fixtures/1211-server-item-active.js), property diamond and explicit setter stick, both 16/EPIC.
- Native client probe: [client-observer.js](client-observer.js); read-only, emits changes and a bounded positive heartbeat.
- Rejected plan: [invalid-candidate.js](invalid-candidate.js), maxStackSize=0.
- Raw record: [production-smoke.log](production-smoke.log).
- Reproduce command control: [mcp-command.ps1](mcp-command.ps1), with discovered port and owned PID arguments; [native input](../2026-10-06-ticket48-e2e/native-input.ps1).

The maintainer confirmed activating the required MCP Take Over button before game control. The bridge's execute_command route returned `no command method found`; its key acknowledgement did not submit chat. The working route opens native ChatScreen through user-activated MCP, returns to normal input, verifies owned-window foreground identity, and submits native text/physical Enter. A screenshot exposed active Chinese IME converting the command prefix; the helper selects English only for that owned test window. No private controller activation state is changed and no user world/profile is overwritten.

## Actual observations

| Stage | Server/client readback |
|---|---|
| Startup baseline | Diamond and stick 64/COMMON |
| Active reload | Both 16/EPIC; declared items=2; server and client agree |
| Fresh active inventory | `/give` 20 produces selected count=16, heldMax=16, heldRarity=EPIC |
| Invalid candidate | Candidate generation 3 rejected in STATE_PLAN, domain state-plan-preflight:item-block-modification; `Invalid maxStackSize 0`, NEKO-1008; active retained |
| Fresh post-failure stack | After clearing only the disposable inventory, new count=3 remains 16/EPIC |
| Empty declarations | Both fresh Item.of reads restore 64/COMMON; existing held stack stays 16/EPIC |
| Fresh restored inventory | Clear and give 20: selected count=20, heldMax=64, heldRarity=COMMON |
| Real relog | Save/quit, second `joined the game`, then ordinary `/give` 1 yields count=21, heldMax=64, heldRarity=COMMON; no CLIENT reload forced for that reading |

Existing ItemStack instances capture old components and are not retroactively rewritten. The observed old held stack is therefore explicitly separate from fresh defaults and serialized/recreated stacks. This was an integrated server and client sharing one JVM; it is not proof of independent 1.21.1 network replay. Independent server/client 26.2 sync evidence remains in [the client record](../2026-10-05-ticket39-client/README.md). Other nodes retain their actual capability/source-trace/test results; no automatic Fabric parity is asserted.

The narrow real [AC14 maintainer confirmation](AC14-confirmation.md) is now recorded. It does not authorize release or other public deletions.

## Historical attempts

The earlier disposable modified-jar run only reached startup/world preparation and is retained in [run2-filtered.log](run2-filtered.log). It did not provide item/restore acceptance. The MOD-bus fixes for pack and payload registration and the legacy loot registry fallback remain separately covered; this item trial does not claim a successful custom JSON loot replacement. The earlier installer EOF/world-preparation gaps are superseded by this actual official-jar world run.
