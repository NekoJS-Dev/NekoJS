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
| Initial client connect (SERVER active) | 16 / EPIC | 16 / EPIC | 64 / COMMON / pre-sync inventory stack | 7 | 7 | Client received generation 2 catch-up on login and applied active plan | `build/issue4-minecraft-neoforge/logs/latest.log`; `build/issue4-server-neoforge/logs/latest.log` |
| Active after SERVER commit | 16 / EPIC | 16 / EPIC | 64 / COMMON / old stack remains unchanged until replaced | 7 | 7 | Server active fixture committed; client sync applied generation 2 | same logs |
| New ordinary /give stack | 16 / EPIC | 16 / EPIC | 16 / EPIC / fresh server-issued diamond after `/give` and slot replace | 7 | 7 | New stack used synchronized item defaults; old stack was intentionally not retroactively rewritten | same logs |
| Invalid candidate rejected | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Empty-plan baseline restore | 64 / COMMON | 64 / COMMON | 16 / EPIC / old active stack remains until relog | 0 | 0 | `nekojs reload server` broadcast generation 3 with 0 declarations; client applied restore | same logs |
| Leave/reconnect after save | 64 / COMMON | 64 / COMMON | 64 / COMMON / server inventory stack after relog | 0 | 0 | Client left and rejoined; login catch-up and held stack both baseline | same logs |
| Same phases against dedicated server | PASS | PASS | PASS with fresh/relogged stacks | PASS | PASS | Separate Java server/client; active, restore, and login catch-up synchronized | same logs |

Record stick maxStackSize/rarity setter parity separately: server and client active declarations both observed `16 / EPIC`; fresh held-stick stack: NOT RUN.

Record lit lamp baseline/per-state restore and nearby calculated light separately: default light observed server/client `7` active / `0` restored; per-state/chunk lighting: NOT RUN.

Record any item defaults reset after login/handshake without a SERVER generation change: login catch-up applies the current committed generation; no generation-free reset experiment.

## Capability conclusion

- Integrated/server-side observation: PASS for the active fixture (`diamondMax=16 diamondRarity=EPIC stickMax=16 stickRarity=EPIC lampDefaultLight=7`).
- Automatic remote apply: PASS for NeoForge 26.2; the existing `NekoScriptPayload` channel carries generation-scoped declarations, and the client applies them through the same modification Adapter.
- Fresh/held behavior: PASS for newly created or relogged stacks. Existing ItemStack instances are not retroactively rewritten by an item-default sync; this is visible and bounded rather than a hidden fresh-stack mismatch.
- Chunk resend observation: NOT VERIFIED; the fixture validates default item/block reads and new stacks, not placed-block chunk re-send.
- Relog observation: PASS; login catch-up restored active or baseline values and the held stack matched the server after relog.
- Long-term server/client mismatch and user-visible explanation/rejection: PASS for the tested NeoForge 26.2 path; no mismatch remained after active/restore/relog sync. Unsupported versions still require their own node evidence.
- 1.21.1 actual item startup/reload/removal smoke: NOT RUN; MCP NeoForge 21.1.172 server setup failed during installer headless processor replay with `unexpected end of file`. Existing client version files were not treated as server evidence.

## Preservation

- Successful empty-plan restore before fixture removal: PASS for server generation 3 and client sync generation 3; fresh values returned to baseline.
- Removal of only newly owned trial files: PASS; `t39-server-active.js` and `t39-client-observe.js` were absent before deployment and removed after the run.
- Pre-existing script/config/pack/trust-store hashes match: NOT COMPARED byte-for-byte; the existing Issue4 directories were reused only for matching startup registry and logs.
- World and placed-block/inventory changes recorded: RCON changed only the isolated test player's inventory; no world placement was used.
- Normal world save/client shutdown: NOT RUN; processes were stopped after evidence capture.

## Human conclusion

AC10 real visibility is now demonstrated for NeoForge 26.2: active plans, empty-plan restore, fresh stacks, and relog catch-up synchronize through the existing network channel. AC11 remains open because 1.21.1 server setup was blocked by the MCP NeoForge installer EOF. AC14 remains open pending maintainer deletion confirmation.
