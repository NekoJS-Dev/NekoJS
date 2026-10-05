# Ticket 39 actual run records

Prepared 2026-10-05. Complete isolated NeoForge 26.2 server/client observation recorded 2026-10-05 by the lead; no maintainer conclusion has been recorded.

## Environment

| Field | Actual value |
|---|---|
| Commit and dirty-source description | `043fdfbb` plus existing unrelated dirty worktree; isolated fixture files only |
| Built server jar and SHA-256 | `versions/26.2.0/build/libs/nekojs-neoforge-26.2.0-1.1.0-preview3.jar`; `F061A1D6AE33FF7E81D32C62768C0F5B21A3D20AD4841D3F224A61E28538C530` |
| Built client jar and SHA-256 | Same jar and hash |
| Minecraft/loader/Java | Minecraft 26.2 / NeoForge 26.2.0.75 / Zulu Java 25 |
| SERVER PID | Separate detached Java process; stopped after smoke |
| CLIENT PID | Separate MCP-launched Java process; stopped after smoke |
| Same JVM or separate process | Separate server and client processes |
| Verified game directory/directories | `build/issue4-server-neoforge` and `build/issue4-minecraft-neoforge` |
| Initial conflicting scripts/errors | First attempt used `ticket41-mc` without matching startup registry and hit unknown keys; final run used the existing Issue4 client startup scripts and connected successfully |
| Fresh Item.of vs existing held inventory stack | Client observer read fresh `Item.of` values and a server-replaced held diamond separately |

## Phase observations

| Phase | SERVER fresh diamond max/rarity | CLIENT fresh diamond max/rarity | CLIENT held diamond max/rarity/patch origin | SERVER lamp default emission | CLIENT lamp default emission | Reload result/generation | Log/screenshot path |
|---|---|---|---|---|---|---|---|
| Initial client connect (SERVER active) | 16 / EPIC | 64 / COMMON | 64 / COMMON / `minecraft:diamond` after RCON slot replace | 7 | 0 | Client joined while server active and observer reported baseline client values | `build/issue4-minecraft-neoforge/logs/latest.log`; `build/issue4-server-neoforge/logs/latest.log` |
| Active after SERVER commit | 16 / EPIC | 64 / COMMON | 64 / COMMON / `minecraft:diamond` | 7 | 0 | Server active fixture committed; client remained baseline | same logs |
| New ordinary /give stack | 16 / EPIC | 64 / COMMON | 64 / COMMON / `minecraft:diamond` after `/give` and RCON slot replace | 7 | 0 | Fresh server-issued diamond reached client but retained baseline properties | same logs |
| Invalid candidate rejected | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Empty-plan baseline restore | 64 / COMMON | 64 / COMMON | 64 / COMMON / `minecraft:diamond` | 0 | 0 | `nekojs reload server` committed generation 2 with `baseline-or-restored` | same logs |
| Leave/reconnect after save | 16 / EPIC | 64 / COMMON | 64 / COMMON / `minecraft:diamond` | 7 | 0 | Client left and rejoined; observer remained baseline | same logs |
| Same phases against dedicated server | PASS | PASS (baseline only) | PASS (baseline only) | PASS (server only) | PASS (baseline only) | Separate Java server/client; no registry disconnect after matching Issue4 startup scripts | same logs |

Record stick maxStackSize/rarity setter parity separately: server active declared setter/property parity; client remained baseline. Full held-stick observation: NOT RUN.

Record lit lamp baseline/per-state restore and nearby calculated light separately: default light observed server `7` active / `0` restored and client `0`; per-state/chunk lighting: NOT RUN.

Record any item defaults reset after login/handshake without a SERVER generation change: client baseline observed after login; no separate generation-free reset experiment.

## Capability conclusion

- Integrated/server-side observation: PASS for the active fixture (`diamondMax=16 diamondRarity=EPIC stickMax=16 stickRarity=EPIC lampDefaultLight=7`).
- Automatic remote apply: NOT SUPPORTED/NOT VERIFIED; the client connected successfully but fresh and held stacks plus the default lamp remained baseline while the server active plan was committed.
- Chunk resend observation: NOT VERIFIED; no claim is made that chunk resync changes the property values.
- Relog observation: PASS for connection/re-observation only; after leaving and rejoining, the client still reported baseline values while the server active plan remained committed.
- Long-term server/client mismatch and user-visible explanation/rejection: MISMATCH OBSERVED; no capability message or explicit rejection is currently exposed to the user.
- 1.21.1 actual item startup/reload/removal smoke: NOT RUN.

## Preservation

- Successful empty-plan restore before fixture removal: PASS for server generation 2 (`baseline-or-restored`); client remained baseline.
- Removal of only newly owned trial files: PASS; `t39-server-active.js` and `t39-client-observe.js` were absent before deployment and removed after the run.
- Pre-existing script/config/pack/trust-store hashes match: NOT COMPARED byte-for-byte; the existing Issue4 directories were reused only for matching startup registry and logs.
- World and placed-block/inventory changes recorded: RCON changed only the isolated test player's inventory; no world placement was used.
- Normal world save/client shutdown: NOT RUN; processes were stopped after evidence capture.

## Human conclusion

No maintainer sign-off has been provided. AC10 remains open because the real run demonstrates a persistent server/client mismatch rather than automatic synchronization or a supported capability explanation. AC11 and AC14 remain open.
