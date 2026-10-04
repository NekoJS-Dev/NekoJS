# Ticket 44 texture/resource integration and real client proof

## Implementation

The NeoForge host prepares visuals and immutable image blit plans during measured-layout publication. Painting consumes those plans; it does not read files, decode images or rerun guest render.

`MinecraftUiResourceResolver` consumes the existing Minecraft resource stack, including NekoJS's asset pack and vanilla assets. Static PNG input is bounded and decoded before upload. Unique texture slots belong to the root; revision changes retire old slots, successful host publication releases them, and close drains current/retired slots while aggregating cleanup failures. Underlying IO/decode/upload causes remain attached to the existing diagnostic seam.

GPU upload API differences reside in the node source trees: 26.1.2 uses `TextureFormat.RGBA8`; 26.2 uses `GpuFormat.RGBA8_UNORM`. No shared inline version branch or second resource root was added.

Images/icons support contain, cover, stretch, source crop and opacity. Text painting now uses measured font-size scaling and cached line layout, including design-space scale. Missing/load/decode/size failures use NEKO-6004/6005/6007/6006.

## Actual client proof

Environment: Minecraft 26.2, NeoForge 26.2.0.75, Zulu Java 25.0.3, MCP mod 0.2.1, launcher game directory `%APPDATA%/.minecraft/mcp_launcher/game`.

Live jar SHA-256: `CC021AB9E826B959D5428B52906DEC816162132A49F296D7EBC464C037FE0F02`.

Fixture: [`ticket44-visual-flow.tsx`](../../../../src/test/resources/nekojs/client/ui/ticket44-visual-flow.tsx).

The maintainer selected “图片、字号、按钮与关闭全部正常” after verifying:

- Actual vanilla stone texture, cropped/translucent stone and paper icon.
- A visibly larger 18-pixel title, 9-pixel text and two explicit text lines.
- Missing and corrupt resources remain placeholders without breaking the healthy button.
- The healthy button callback logged `[ticket44] healthy click after resource failures`.
- Escape returned to the main menu.

`live-screen.png` captures the actual textures and font hierarchy. `client-observations.log` captures missing-resource diagnostics, preserved PNG decode causes and the healthy callback. The corrupt image was a uniquely owned `ticket44_trial:gui/corrupt` resource containing deliberately invalid bytes, added only for this test and removed afterward. Original launcher jar and fixture were restored; test client PID 2320 stopped.

The live jar reported an initial `root=unknown` before bindRoot. A subsequent envelope change supplies the existing root id before initial layout preparation; final regression/build verification covers that change, but the recorded checksum and screenshot belong to the preceding live build.

## Verification and acceptance limits

Texture loader tests cover resource lookup/cache, missing/read/decode/upload codes and causes, invalid input size, retry/invalidation, retirement after successful publication, failed-preparation preservation, inspect lookup, cleanup aggregation and closed-owner rejection. Geometry tests cover crop, contain/cover/stretch and opacity. Native decode/GPU upload/blit were executed in the real client above.

The five-node test matrix and guardLint passed after correcting node upload source ownership and node-specific test placement. Regenerate mode changed only the optional `publish` parameter in the host layout declaration; ordinary golden drift and Probe TypeScript checks are required afterward.

This proof does not claim animated PNG/mcmeta playback or custom font resource selection. Rounded-corner painting remains a declared implementation gap. Read/upload failure NEKO-6005 is exercised in the resource-backend regression; the live session exercises missing and decode failures. Real post-reload repaired-image smoke and final maintainer whole-ticket conclusion remain separate acceptance work.
