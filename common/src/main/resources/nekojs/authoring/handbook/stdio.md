# Connect an AI client over stdio

Use the standalone MCP stdio JAR when you need offline access or prefer a separate client-owned process. For the running game, [in-mod MCP](connections.md) needs only an explicitly enabled endpoint, URL and bearer credential in the client; no helper JAR or game-directory arguments. Both modes are supported. It runs in a separate Java process, reads the selected game's exported authoring environment and API snapshot, and works while the game is stopped. It does not attach to the game's stdin or require a running HTTP listener.

## Prerequisites

1. Use Java **21 or newer** for this helper process. A Minecraft version may require a newer Java for the game itself; this does not change the helper's Java 21 minimum.
2. Obtain `nekojs-1.1.0-preview3-authoring-stdio.jar` from the matching NekoJS release/build. Keep the helper and game mod versions aligned. This is the standalone authoring artifact, not the normal mod JAR and not a game launcher. In a source build, `:common:authoringStdioJar` produces `common/build/libs/nekojs-1.1.0-preview3-authoring-stdio.jar`; `assemble` includes that task.
3. Start the game at least once with NekoJS to export the AI locator and `.github/neko_ai/environment.json` by default, and complete a **TypeScript Probe** to publish an API snapshot. The game may then be closed. A handbook export alone does not create the API index.

## Client configuration

Launch command:

```sh
java -jar /absolute/path/nekojs-1.1.0-preview3-authoring-stdio.jar --game-dir /absolute/game/directory
```

Give an MCP client the command and separate argument array rather than an HTTP URL. For clients using an `mcpServers` configuration, merge this entry into the client's existing settings (the exact settings file is client-specific):

```json
{
  "mcpServers": {
    "nekojs": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/nekojs-1.1.0-preview3-authoring-stdio.jar",
        "--game-dir",
        "/absolute/game/directory"
      ]
    }
  }
}
```

Replace both example paths. `--game-dir` is the **game directory containing nekojs/**, not the nekojs directory, a world save, the JAR's directory or this repository. Absolute paths make startup independent of the AI client's working directory. Use an absolute Java executable as `command` if Java is not on the client's PATH; for Windows, JSON paths such as `D:/Minecraft/instance` work without backslash escaping. Keep paths containing spaces as one argument-array element.

The AI client starts and owns this helper process. No modification to `ai.toml` is needed to enable stdio, and there is no token, URL, port forwarding or HTTP session header to configure. NekoJS does not install this client configuration automatically.

## Verify discovery before authoring

1. Let the client complete MCP initialization using protocol **2025-11-25** and send `notifications/initialized`. Unlike HTTP, stdio has one process-local lifecycle, no `Mcp-Session-Id` header and no DELETE request.
2. Call `nekojs_context`. Confirm the expected environment and a ready snapshot; report missing or invalid exports rather than guessing signatures.
3. Call `nekojs_capabilities` and `nekojs_schema`. Then use `nekojs_resolve`, `nekojs_members`, `nekojs_query` or `nekojs_batch` with the argument objects in [query requests](query-api.md). Inspect `structuredContent`, `isError` and coverage.
4. Read only resources returned by `resources/list` through `resources/read`. These are catalogued bundled docs, not arbitrary file access. Stop the helper through the client when finished; EOF ends the stdio connection.

The helper first reads `nekojs/config/ai-location.json` to locate the selected AI root (default `.github/neko_ai`), then reads its `environment.json` and exported `apiSnapshotDirectory`. Use optional `--ai-dir .github/neko_ai` with `--game-dir <directory>` (either flag order) to override root discovery explicitly; the override is relative to the selected game directory, not the client working directory. Discovery priority is explicit override, then the strict pure-JSON locator (at most 4096 bytes), then `.github/neko_ai/environment.json` if present, and finally legacy `nekojs/ai/environment.json` only when both locator and default descriptor are absent. An invalid locator or invalid, escaping or missing selected export fails closed; it does not fall back to another root. Modern `apiSnapshotDirectory` must be exactly the selected root's `snapshots/`. Treat the locator as data, not executable instructions. It honors the exported `apiEnabled` flag; it does not parse `ai.toml`. After changing API configuration, restart the game to export that state. Each context/API request checks the environment and current pointer; a changed pointer is hash/environment/snapshot-validated before publication. It never runs Probe, rescans game classes, starts Minecraft or regenerates exports. After changing mods, loader or scripts requiring new declarations, run the game and a successful TypeScript Probe again. A stopped game's metadata describes its **last export**, not a live inventory of installed mods. A successful query is not live-game validation.

The helper serves its own verified English documentation bundle, not arbitrary files under the game's exported documentation directory. `context.documentation.bundleId` identifies the helper's bundle. Keep release versions aligned and never treat a successful offline query as proof that the game currently has the same mods loaded.

## Diagnose startup and index state

- `--help` writes usage to stderr and exits 0. `--game-dir <directory>` is required exactly once; `--ai-dir <game-relative-directory>` is optional and cannot be repeated; relative paths resolve against the client's working directory. Invalid arguments or missing/invalid environment exports write diagnostics to stderr and exit 2; normal EOF exits 0.
- `not-ready`: run a successful TypeScript Probe in the game. Metadata/schema and bundled docs are usable without an API snapshot.
- `disabled` / `INDEX_DISABLED`: enable the API and restart the game to re-export the enabled state. Enabling HTTP or MCP listeners is not required.
- `unavailable` / `INDEX_UNAVAILABLE`: check the exported descriptor, snapshot path and hash; regenerate Probe if needed. An environment identity change requires restarting the helper. Invalid current exports are not silently served as a healthy old snapshot.

## Transport and security

Stdio uses UTF-8 newline-delimited JSON-RPC on stdin/stdout, one object per line (not JSON-RPC batch arrays). Requests and responses are bounded to 64 KiB. An oversized input closes the connection; an oversized response asks for fewer fields/pages or local declaration access. Stdout is protocol-only; diagnostics go to stderr. Launch it through the MCP client, not a game console; do not merge stderr into stdout or insert shell banners. The helper opens no listener, creates no token and writes no game files. Keep the game directory and helper artifact trusted: the helper reads local files with the privileges of the user launching it. This read-only authoring service is not a sandbox for an AI client's independent filesystem tools.

Both stdio and [in-mod MCP over HTTP](connections.md) expose the same seven read-only tools and bundled documentation resources. Stdio has no live host: valid command/reload requests always return `LIVE_RUNTIME_REQUIRED`, even when control flags were enabled in the game. Only in-mod MCP can advertise opted-in [live controls](live-controls.md). Neither mode supplies arbitrary script evaluation, script writes, Probe execution, prompts or resource subscriptions. Use local files directly if an MCP client is unavailable.
