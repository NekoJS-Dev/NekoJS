# NekoJS authoring handbook

Use this installed bundle with the matching instance's declarations. The wiki is supplementary; authoring does not require an internet connection.

1. Read `nekojs/config/ai-location.json` to discover the configured AI root (default `.github/neko_ai`), then read `environment.json` and `current.json` under that selected root from the game directory, or use the authoring context operation. Resolve the active bundle and index locations from those discovery files.
2. For MCP tool access while the game runs, enable [in-mod MCP](connections.md) and configure the client with its URL and bearer token; no helper JAR or game-directory argument is needed. For offline snapshots or a separate client-owned process, use the [standalone stdio helper](stdio.md).
3. Choose the script type and read [script types](script-types.md).
4. Read the relevant guide below and query exact signatures before using unfamiliar members.
5. Follow [validation](validation.md) and report what was actually checked.

| Task | Reference |
|---|---|
| Start from runnable scenarios | [Scripting examples](scripting-examples.md) |
| Subscribe to events | [Events](events.md) |
| Call helpers or pass item strings | [Bindings and adapters](bindings-and-adapters.md) |
| Change recipes or tags | [Recipes and tags](recipes-and-tags.md) |
| Register content | [Registry builders](registry-builders.md) |
| Work in a script pack | [Script packs](script-packs.md) |
| Draw a HUD, world lines, or declare post effects | [Client rendering](client-rendering.md) |
| Create a CLIENT Screen, clipboard input, themed controls, tabs, validated forms, modal dialogs or read-only item icons | [JSX UI](jsx-ui.md) |
| Investigate an error | [Diagnostics](diagnostics.md) |
| Find classes and members | [Query API](query-api.md) |
| Connect to the running game | [In-mod MCP setup](connections.md) |
| Read offline snapshots through MCP | [Standalone stdio](stdio.md) |
| Explicitly authorize commands or script reloads | [Live controls](live-controls.md) |

This initial bundle documents the common authoring workflow. Structured API coverage is explicitly incomplete; declaration text remains available when member indexing is unavailable. It is not a catalog of every Minecraft or mod API.
