# NekoJS authoring handbook

Use this installed bundle with the matching instance's declarations. The wiki is supplementary; authoring does not require an internet connection.

NekoJS produces one mod jar per Minecraft/loader node for both client and server, with no separate server jar or bundled ICU4J. Both loaders require GraalMC 25.5.0 or later. The pinned NeoForge build (`maven.modrinth:bjcrgPcA:O8qtoV6d`) and Fabric build (CurseForge file 9555615) supply their own relocated ICU for GraalJS; no separate ICU library is needed for that runtime.

1. Read `[output].directory` in `nekojs/config/ai.toml` to discover the configured AI root (default `.github/neko_ai` if the file or setting is absent), then read `environment.json` and `current.json` under that selected root from the game directory, or use the authoring context operation. Resolve the active bundle and index locations from those discovery files.
2. For MCP tool access while the game runs, enable [in-mod MCP](connections.md) and configure the client with its URL and bearer token; no helper JAR or game-directory argument is needed. For offline snapshots or a separate client-owned process, use the [standalone stdio helper](stdio.md).
3. Choose the script type and read [script types](script-types.md).
4. Read the relevant guide below and query exact signatures before using unfamiliar members.
5. Follow [validation](validation.md) and report what was actually checked.

| Task | Reference |
|---|---|
| Start from runnable scenarios | [Scripting examples](scripting-examples.md) |
| Subscribe to events | [Events](events.md) |
| Prepare experimental native hooks and bind loaded callbacks | [MixinJS](mixinjs.md) |
| Import stable/version helpers or plugin module exports | [API modules](api-modules.md) |
| Call helpers or pass item strings | [Bindings and adapters](bindings-and-adapters.md) |
| Change recipes or tags | [Recipes and tags](recipes-and-tags.md) |
| Register content | [Registry builders](registry-builders.md) |
| Work in a script pack | [Script packs](script-packs.md) |
| Draw a HUD, world lines, or declare post effects | [Client rendering](client-rendering.md) |
| Create CLIENT Screens, cooperative regions, one-root workspaces with retained window management, controlled forms, dialogs or read-only item icons | [JSX UI](jsx-ui.md) |
| Investigate an error | [Diagnostics](diagnostics.md) |
| Find classes and members | [Query API](query-api.md) |
| Connect to the running game | [In-mod MCP setup](connections.md) |
| Read offline snapshots through MCP | [Standalone stdio](stdio.md) |
| Explicitly authorize commands or script reloads | [Live controls](live-controls.md) |

This initial bundle documents the common authoring workflow. Structured API coverage is explicitly incomplete; declaration text remains available when member indexing is unavailable. It is not a catalog of every Minecraft or mod API.
