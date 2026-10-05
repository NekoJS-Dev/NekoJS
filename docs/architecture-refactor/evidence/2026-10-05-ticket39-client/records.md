# Ticket 39 actual run records

Prepared 2026-10-05. Partial live attempt recorded 2026-10-05 by the lead; no maintainer conclusion has been recorded.

## Environment

| Field | Actual value |
|---|---|
| Commit and dirty-source description | `043fdfbb` plus existing unrelated dirty worktree; isolated fixture files only |
| Built server jar and SHA-256 | `versions/26.2.0/build/libs/nekojs-neoforge-26.2.0-1.1.0-preview3.jar`; `F061A1D6AE33FF7E81D32C62768C0F5B21A3D20AD4841D3F224A61E28538C530` |
| Built client jar and SHA-256 | Same jar and hash |
| Minecraft/loader/Java | Minecraft 26.2 / NeoForge 26.2.0.75 / Zulu Java 25 |
| SERVER PID | Separate direct Java process; stopped after smoke |
| CLIENT PID | `5652`; stopped after smoke |
| Same JVM or separate process | Separate server and client processes |
| Verified game directory/directories | `build/issue4-server-neoforge` and `build/ticket41-mc` |
| Initial conflicting scripts/errors | No t39 filenames existed; offline auth errors are unrelated. First client attempt used the wrong resolved host; second reached `127.0.0.1:25883` but disconnected during registry sync because server-only Issue4 startup registrations (`nekojs:issue4_mob_spawn_egg`, `nekojs:issue4_mob`, `nekojs:issue4_native_zombie`) were unknown to the client |
| Fresh Item.of vs existing held inventory stack | Server fixture only; client not connected |

## Phase observations

| Phase | SERVER fresh diamond max/rarity | CLIENT fresh diamond max/rarity | CLIENT held diamond max/rarity/patch origin | SERVER lamp default emission | CLIENT lamp default emission | Reload result/generation | Log/screenshot path |
|---|---|---|---|---|---|---|---|
| Baseline | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Active after SERVER commit | 16 / EPIC | NOT OBSERVED (client did not connect) | NOT OBSERVED | 7 | NOT OBSERVED | SERVER active fixture loaded and emitted values; client connect failed | `build/issue4-server-neoforge/logs/latest.log`; `build/ticket41-mc/logs/latest.log` | |
| New ordinary /give stack | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Invalid candidate rejected | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Empty-plan baseline restore | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Leave/reconnect after save | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Same phases against dedicated server | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |

Record stick maxStackSize/rarity setter parity separately: NOT RUN.

Record lit lamp baseline/per-state restore and nearby calculated light separately: NOT RUN.

Record any item defaults reset after login/handshake without a SERVER generation change: NOT RUN.

## Capability conclusion

- Integrated/server-side observation: PASS for the active fixture (`diamondMax=16 diamondRarity=EPIC stickMax=16 stickRarity=EPIC lampDefaultLight=7`). This is not remote-client evidence.
- Automatic remote apply: NOT VERIFIED; the client reached configuration/registry sync but was disconnected on unknown server registry keys before player tick.
- Chunk resend observation: NOT VERIFIED.
- Relog observation: NOT VERIFIED.
- Long-term server/client mismatch and user-visible explanation/rejection: NOT VERIFIED; the observed disconnect is a concrete registry mismatch, not a supported capability message.
- 1.21.1 actual item startup/reload/removal smoke: NOT RUN.

## Preservation

- Successful empty-plan restore before fixture removal: NOT RUN.
- Removal of only newly owned trial files: NOT RUN.
- Pre-existing script/config/pack/trust-store hashes match: NOT RUN.
- World and placed-block/inventory changes recorded: NOT RUN.
- Normal world save/client shutdown: NOT RUN.

## Human conclusion

No maintainer sign-off has been provided. AC10/AC11/AC14 remain open unless the lead records actual evidence and an appropriately scoped conclusion.
