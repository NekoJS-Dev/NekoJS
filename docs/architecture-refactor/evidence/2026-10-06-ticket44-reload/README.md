# Ticket 44 resource/font smoke (2026-10-06)

Environment: isolated Minecraft 26.2 / NeoForge 26.2.0.75 / Zulu Java 25 / MCP 0.4.2.

## Verified

- NekoJS resource pack loaded from the corrected root `nekojs/assets/<namespace>/...`.
- `round4-font-flow.tsx` opened a real Screen after client startup.
- Native font measurement logged `plainWidth=24` and selected custom font `selectedWidth=68` for `ticket44_round4:wide`.
- A malformed `ticket44_round4:broken` provider was rejected by Minecraft's native font codec.
- Missing font fallback emitted `NEKO-6004` with root/node/resource/generation location.
- The filtered client log is saved as `client.log`.

## Later completion

This historical run proves initial pack/resource loading only; its MCP hotkey injection did not produce a ResourceManager reload record. The later [complete combined session](../2026-10-06-ticket48-e2e/README.md) closes the pack-content gap: same JVM missing fallback width 24 → recovered provider A=4 width 16 → revised provider A=9 width 36, with actual native F3+T reloads. Successful CLIENT reload recreated generations/roots; this is not same-root/no-rerender recovery. Existing bitmap-font smoke covers glyph appearance, while these space-provider revisions cover native selection/advance readback. Ticket 44 is closed on these combined technical results; publication remains separate.
