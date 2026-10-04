# Ticket 39 actual run records

Prepared 2026-10-05. No live test or maintainer conclusion has been recorded by the preparer.

## Environment

| Field | Actual value |
|---|---|
| Commit and dirty-source description | UNFILLED |
| Built server jar and SHA-256 | UNFILLED |
| Built client jar and SHA-256 | UNFILLED |
| Minecraft/loader/Java | UNFILLED |
| SERVER PID | UNFILLED |
| CLIENT PID | UNFILLED |
| Same JVM or separate process | UNFILLED |
| Verified game directory/directories | UNFILLED |
| Initial conflicting scripts/errors | UNFILLED |
| Fresh Item.of vs existing held inventory stack | Record separately below |

## Phase observations

| Phase | SERVER fresh diamond max/rarity | CLIENT fresh diamond max/rarity | CLIENT held diamond max/rarity/patch origin | SERVER lamp default emission | CLIENT lamp default emission | Reload result/generation | Log/screenshot path |
|---|---|---|---|---|---|---|---|
| Baseline | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Active after SERVER commit | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| New ordinary /give stack | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Invalid candidate rejected | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Empty-plan baseline restore | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Leave/reconnect after save | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |
| Same phases against dedicated server | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | — |

Record stick maxStackSize/rarity setter parity separately: NOT RUN.

Record lit lamp baseline/per-state restore and nearby calculated light separately: NOT RUN.

Record any item defaults reset after login/handshake without a SERVER generation change: NOT RUN.

## Capability conclusion

- Integrated local observation: UNFILLED; never a substitute for the remote-client gate.
- Automatic remote apply: UNFILLED; source currently declares unsupported.
- Chunk resend observation: UNFILLED; changing light section data is not proof of property synchronization.
- Relog observation: UNFILLED; record default holder values and received stacks independently.
- Long-term server/client mismatch and user-visible explanation/rejection: UNFILLED.
- 1.21.1 actual item startup/reload/removal smoke: NOT RUN.

## Preservation

- Successful empty-plan restore before fixture removal: NOT RUN.
- Removal of only newly owned trial files: NOT RUN.
- Pre-existing script/config/pack/trust-store hashes match: NOT RUN.
- World and placed-block/inventory changes recorded: NOT RUN.
- Normal world save/client shutdown: NOT RUN.

## Human conclusion

No maintainer sign-off has been provided. AC10/AC11/AC14 remain open unless the lead records actual evidence and an appropriately scoped conclusion.
