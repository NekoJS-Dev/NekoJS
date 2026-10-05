# Minecraft MCP with DSH: launch, status, and tools

## Scope

This note separates the upstream Minecraft MCP guide from the NekoJS project's local runbook and the DSH client contract. The local bridge and launch prerequisites were also checked from this workspace.

## DSH Web: fastest path on this machine

The installed `dsh-mcp-manager-plus` plugin exposes **Settings > MCP 管理 > 添加** in the existing Web GUI. Paste the official guide's `mcpServers` JSON (below); this manager accepts that format and writes the active profile patch with live reload. Do not assume the upstream project `.mcp.json` is loaded automatically by DSH. Source: [installed MCP manager README](C:/Users/11515/.dsh/profiles/web/node_modules/dsh-mcp-manager-plus/README.md).

```json
{"mcpServers":{"minecraft-mod-mcp":{"type":"local","command":["npx","-y","minecraft-mod-mcp"]}}
```

After the server is registered, it can expose its tool list even before a Minecraft client connects; a game-dependent call needs an installed modded client running. The Web manager supports restart and displaying connected tools.

## DSH-compatible configuration

The upstream guide shows generic MCP clients using a project `.mcp.json` entry. For DSH, use the DSH MCP-client plugin configuration instead of assuming DSH reads `.mcp.json` automatically. Add one plugin entry to the active DSH profile/overlay:

- insert:
    - id: mcp-minecraft
      name: '@deepseek-ai/dsh-mcp-client'
      config:
        serverName: minecraft
        transport: stdio
        command: npx
        args: ['-y', 'minecraft-mod-mcp']

`stdio` is required: the npm bridge speaks MCP over stdio. The in-game mod exposes ordinary HTTP endpoints, not MCP; do not configure its port, `/api/events`, or an SSE/URL transport as the MCP server. DSH exposes discovered tools as `mcp__minecraft__<raw-tool-name>` (for example, `mcp__minecraft__ping`).

## Verified locally

- The installed `minecraft-mod-mcp mcp --no-discover` completes MCP initialization and `tools/list` without a game. It returned 46 tools here (the upstream guide describes 45), including `launch_minecraft`, `get_minecraft_status`, `screenshot_to_file`, `click`, `press_key`, and `execute_command`.
- After the user added the DSH MCP server, `ping` returned `pong`. The MCP `launch_minecraft(26.2, neoforge)` startup logged FML `Game directory: %USERPROFILE%/.minecraft`; the CLI `launch 26.2 --loader neoforge --java <Zulu Java 25.0.3>` startup logged `%APPDATA%/.minecraft/mcp_launcher/game`. These launches read **different** `mods/` and `nekojs/client_scripts/` directories. Always confirm FML's current `Game directory:` and `Mod List:` and back up any jar before replacement.
- Oracle GraalVM 25.0 plus GraalMC 25.1.3.7 failed at NekoJS construction (`Got null compiler version`). CLI + Zulu Java 25.0.3 loaded five mods and CLIENT scripts. Its launcher-directory NekoJS jar was stale (hash `33F0DA8B`, no `ClientUiJS.class`), unlike the freshly built jar (`FA2C287A`).
- MCP screenshots and player coordinates worked, but GUI discovery reported `screen: LocalPlayer`, no buttons; `click` reported `no screen`, `overlay_click` `blocked`, and `execute_command` `no command method found`. The maintainer entered a world and ran `/nekojs reload client` manually. After isolating the pre-existing four-second `t41_smoke.jsx` loop and testing the actual ticket-41 fixture, the old jar reported `ClientUI is not defined`. With the fresh jar, early startup failed at `JsxScreen.<init>` (`Minecraft.getInstance()` null), then render-thread reload failed at `UI.createRoot` (`Viewport input must be a plain object`; Java host returns `Map`). The ticket-41 Screen and ticket-42 reload smoke did **not** pass. The old script, entry, jar and user-profile staged extras were restored, and the test clients stopped.

## Follow-up: isolated JSX Screen smoke

After a focused red/green Map viewport regression and successful `:common:check` / `:26.2.0:build`, the updated NekoJS jar and a `ClientEvents.tickPost` Screen fixture reached a real `Ticket 41` Screen. `screenshots/ticket41-fixed-startup.png` under the launcher game directory shows its `OK` button. A human tried clicking and closing it, but the click marker never reached the log; close reported NEKO-7001/7007 (`ui-host-update` during generation CLOSING). Treat first draw as proven, interaction and cleanup as unverified/failing. The test world was saved; user scripts and the original launcher jar were restored; all test clients were stopped.

## What the official guide states

- Run the bridge with `npx -y minecraft-mod-mcp`; Node.js >=20 is required.
- The bridge scans ports 9876 through 9000 and selects the first `/api/status` response with `type:"minecraft-mod"`; do not hard-code a port. Discovery reports `version`, `loader`, `pid`, and `port`.
- A running modded client may be supplied, or the bridge can launch one through `launch_minecraft` / `serve`.
- CLI equivalents: `npx -y minecraft-mod-mcp list`; `install <version> --loader <loader>`; `launch <version> --loader <loader>`; `server <version>`; `serve <version>`; `auth offline <Player>`; and `status`.
- Connection checks are MCP `ping` and `get_minecraft_status`; the latter should report `connected: true` plus version, loader, and port. The bridge provides 45 tools according to the guide. Common actions include `screenshot`, `screenshot_to_file`, `execute_command`, `click`, `press_key`, `scroll`, `open_chat`, `paste_text`, `get_player_info`, `debug_fields`, `get_world_info`, `get_screen_buttons`, and `enumerate_widgets`.
- The bridge itself is headless. A graphical Minecraft client needs a display; on headless Linux the guide uses `xvfb-run ... launch ...`. A standalone server does not need a display.
- The guide explicitly replaces old `just daemon` / `scripts/mc_vtty.py` instructions with the bridge.

Primary source: [official AI tool integration guide](https://raw.githubusercontent.com/langyo/minecraft-mod-mcp/master/docs/guides/zhs/AI-TOOLS.md) (also [repository view](https://github.com/langyo/minecraft-mod-mcp/blob/master/docs/guides/zhs/AI-TOOLS.md)).

## What DSH states

- `@deepseek-ai/dsh-mcp-client` accepts one server entry with `serverName`, `transport`, and stdio `command`/`args`/`env`/`cwd` fields; no server is enabled by default.
- After startup, tools are registered under `mcp__<serverName>__<rawName>`. A failed optional initial connection leaves DSH running but contributes no tools; `failOnStartupError: true` changes that to activation failure.
- The default per-call timeout is 60 seconds and reconnect is enabled by default (up to ten consecutive failed attempts, with exponential delay capped at 30 seconds).

Primary source: [DSH MCP client README](https://raw.githubusercontent.com/deepseek-ai/deepseek-harness/master/packages/mcp/mcp-client/README.md).

## Local NekoJS guidance (not upstream MCP contract)

The repository's [Minecraft MCP runbook](../agents/minecraft-mcp.md) says the bridge is already on PATH, the client must be launched separately when not using bridge launch, and NekoJS verification uses a built node jar plus its GraalMC dependency in `%APPDATA%\.minecraft\mcp_launcher\game\mods`. For the documented NekoJS 1.21.1 NeoForge path it uses `minecraft-mod-mcp launch 1.21.1 --loader neoforge`, checks `minecraft-mod-mcp status`, and exercises `/nekojs reload` through `execute_command`. It also warns that same-name jars are not overwritten and that `--mod-jar` replaces the bridge mod rather than adding an extra mod.

Those build paths, dependency versions, jar-refresh rules, and NekoJS commands are project-specific guidance; they are not claims made by the upstream AI-TOOLS guide. DSH configuration above is the integration seam; the local runbook remains the source for preparing this project's client.
