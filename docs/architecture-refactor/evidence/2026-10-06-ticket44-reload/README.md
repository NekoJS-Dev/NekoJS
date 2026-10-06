# Ticket 44 resource/font smoke (2026-10-06)

Environment: isolated Minecraft 26.2 / NeoForge 26.2.0.75 / Zulu Java 25 / MCP 0.4.2.

## Verified

- NekoJS resource pack loaded from the corrected root `nekojs/assets/<namespace>/...`.
- `round4-font-flow.tsx` opened a real Screen after client startup.
- Native font measurement logged `plainWidth=24` and selected custom font `selectedWidth=68` for `ticket44_round4:wide`.
- A malformed `ticket44_round4:broken` provider was rejected by Minecraft's native font codec.
- Missing font fallback emitted `NEKO-6004` with root/node/resource/generation location.
- The filtered client log is saved as `client.log`.

## Remaining

This run proves initial pack/resource loading and controlled font/fallback behavior. It does not claim a same-session modified pack content readback after F3+T; MCP hotkey injection did not produce a new ResourceManager reload record. Ticket 44 AC3 remains open for complete pack-content reload/readback and maintainer whole-ticket conclusion.
