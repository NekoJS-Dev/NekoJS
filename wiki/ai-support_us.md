<!-- wiki-page: ai-support; locale: us -->

> **English** · [中文](ai-support_cn)

<a id="wiki-section-1"></a>
# AI-assisted authoring

NekoJS exports an English, versioned authoring bundle and supports **in-mod MCP while the game runs** and a **standalone MCP stdio helper** for offline exported snapshots. Both provide read-only authoring queries. In-mod MCP additionally offers separately enabled command and full SERVER/CLIENT/TEST reload tools, disabled by default. It is not an AI model or chat service and never changes global AI client configuration.

Discover the installed bundle → generate a successful TypeScript Probe snapshot → choose in-mod MCP or standalone stdio if tool access is needed → inspect exact signatures → validate scripts in the game. See [Script API conventions](script-api-style_us) for call shapes and [client rendering and post effects](client-rendering_us) for HUD/world interfaces; do not infer new NekoJS overloads from another project's configuration objects. The bundled handbook, Skill, AGENTS template and examples are canonical AI-facing material; this wiki is a user-facing guide.

<a id="wiki-section-2"></a>
## Offline entry points

Paths below start at the game directory and assume the default `[output].directory = ".github/neko_ai"`. Read `[output].directory` from `nekojs/config/ai.toml` for a customized AI root; use the default if the file or setting is absent.

| Path | Purpose |
|---|---|
| `nekojs/config/ai.toml` | Output, documentation, query, connection and control settings |
| `.github/neko_ai/current.json` | Active bundle pointer; bundle/manifest paths are relative to .github/neko_ai/ |
| `.github/neko_ai/bundled/<bundleId>/` | Versioned English documentation; do not edit in place |
| `.github/neko_ai/environment.json` | Environment and apiSnapshotDirectory |
| `.github/neko_ai/connection.json` | Endpoints, listening state, protocol and token-file location; never the token |
| `nekojs/config/ai.token` | Local bearer credential, created only for enabled listeners |
| `.github/neko_ai/snapshots/current.json` | Structured AI snapshot pointer under the selected AI root |

Documentation export is enabled by default and does not scan classes or open a port. From the game directory, read `.github/neko_ai/current.json`: its `bundle` and `manifest` paths are relative to `.github/neko_ai/`. Read the active handbook, Skill and examples from that bundle. Read `.github/neko_ai/environment.json` for the instance and configured API snapshot directory. Never assume a bundle or snapshot copied from another instance matches this one.

Exports live in the selected AI root's `bundled/<bundleId>/`. Missing `AGENTS.md` and `README.md` are created there, not in `nekojs/` or the workspace root. Known unmodified generated role routers under `agents/` can refresh safely; edited routers and author-owned files are preserved. A modified same-ID bundle is rejected instead of overwritten. Changing the directory does not blindly move/delete old files; disabling export does not delete them.

The selected AI root contains `bundled/`, `current.json`, `environment.json`, `connection.json`, `snapshots/`, `agents/`, `AGENTS.md` and `README.md`. Absolute paths, traversal and escaping links are rejected. Configuration and private credentials stay in `nekojs/config/`; declarations, snippets and editor configuration retain their existing Probe locations. `agents/` is not automatically discovered by VS Code: `[output].editor_agents = true` optionally writes the standard `.github/agents/` bridge. It defaults false. The AI root's `AGENTS.md` is not automatically workspace-wide instructions; explicitly configure your client to read it or merge selected guidance into your own instructions.


<a id="wiki-section-3"></a>
## Default configuration

The generated defaults in `nekojs/config/ai.toml` are:

```toml
# Generated AI material is contained in a game-relative directory. Restart after changes.
[output]
directory = ".github/neko_ai"
editor_agents = false

# English authoring documentation is exported without scanning classes or opening a port.
[documentation]
enabled = true
agents = "create-if-missing"

[api]
enabled = true

# Both protocols share one loopback listener and the same read-only query core.
[http]
enabled = false

[mcp]
enabled = false
host = "127.0.0.1"
port = 8767

# Live MCP actions require a matching runtime owner and explicit authorization.
[control]
commands = false
reload = false
command_allowlist = []
```

Only for the optional in-game transports, set `[http].enabled = true` for ordinary JSON routes, `[mcp].enabled = true` for HTTP MCP, or both. They share the port under `[mcp]`. Only the literal IPv4 loopback host is accepted. Ports must be integers from 1 through 65535; booleans must be TOML booleans. `documentation.agents` accepts only `create-if-missing` or `off`. `api.enabled = false` disables resolve/members/query/batch, not context, capabilities, schema or bundled docs.

Restart the game process after configuration changes. Ordinary script reload does not recreate the root-owned listener. Invalid settings or a port conflict reject authoring initialization (NEKO-1019), not the whole game.


<a id="wiki-section-4"></a>
## Generate and discover a snapshot

Read `context`, `capabilities` and `schema` first. Context reports `environmentId`, `snapshotId` and `indexState`. Index states include `not-ready`, `ready`, `disabled` and `unavailable`. Exporting the handbook does not build the API index. Run a successful **TypeScript Probe** in the game (use the installed `/nekojs probe` help); ordinary script reload does not regenerate it.

The default pointer is `.github/neko_ai/snapshots/current.json` relative to the game directory. `.github/neko_ai/environment.json` records `apiSnapshotDirectory` for a custom Probe base directory. Probe publishes an immutable digest-named JSON file, then atomically switches its pointer. The runtime verifies the hash, snapshot ID and environment before loading it on a context/API request. A failed publication does not replace the previous verified snapshot. Do not edit index files manually.

<a id="wiki-section-5"></a>
## In-mod MCP: connect while the game runs

Both **in-mod MCP** and **standalone stdio** are supported. For a running game, the in-mod service avoids installing or launching a separate helper:

1. In `nekojs/config/ai.toml`, set `enabled = true` under `[mcp]` (default: false). Keep `host = "127.0.0.1"` and `port = 8767`; `[http].enabled` may stay false because it controls ordinary JSON routes, not MCP.
2. Fully restart the game process, then keep it running. Read `.github/neko_ai/connection.json`: `listening` must be true and `mcp` normally equals `http://127.0.0.1:8767/mcp`. Use the actual descriptor URL if you changed the port.
3. In the AI client, add a Streamable HTTP/JSON-response MCP connection using that URL and `Authorization: Bearer <token>`. Replace `<token>` locally with the contents of `nekojs/config/ai.token`. Keep the credential private; the descriptor never includes the token itself.

The client needs **URL + bearer credential**, not a Java command, JAR path or `--game-dir`. For clients accepting an `mcpServers` object with `url` and `headers`, the configuration shape is:

```json
{
  "mcpServers": {
    "nekojs": {
      "url": "http://127.0.0.1:8767/mcp",
      "headers": {"Authorization": "Bearer <token>"}
    }
  }
}
```

The exact settings schema is client-dependent; some clients use a dialog or explicit transport setting. The client handles protocol **2025-11-25** initialization and session headers automatically. The manual JSON-RPC section below is only for custom-client/debugging work, not a mandatory user workflow. Confirm tools appear and call `nekojs_context`, `nekojs_capabilities` and `nekojs_schema`; API queries still need a successful TypeScript Probe snapshot.

This is explicitly opt-in, not automatic discovery or zero-configuration setup. There is no GUI enable switch, automatic client configuration or automatic connection. Game shutdown closes the in-mod service; choose the standalone mode below for offline exports.

<a id="wiki-section-6"></a>
## Alternative: standalone MCP stdio for offline exports

Use Java **21 or newer** and the matching release's standalone `nekojs-1.1.0-preview3-authoring-stdio.jar` (build output: `common/build/libs/nekojs-1.1.0-preview3-authoring-stdio.jar`; build task: `:common:authoringStdioJar`, also included in `assemble`). It is not the mod JAR or a game launcher. Start the game once to export the environment, then run a successful TypeScript Probe for API queries. The helper works with that last export even while the game is stopped.

Configure the AI client to launch a **separate process**, not connect to the game's stdin. Merge this example into clients using an `mcpServers` settings object; other clients may use different settings files.

```sh
java -jar /absolute/path/nekojs-1.1.0-preview3-authoring-stdio.jar --game-dir /absolute/game/directory
```

```json
{
  "mcpServers": {
    "nekojs": {
      "command": "java",
      "args": ["-jar", "/absolute/path/nekojs-1.1.0-preview3-authoring-stdio.jar", "--game-dir", "/absolute/game/directory"]
    }
  }
}
```

Replace both paths. `--game-dir` points to the **game directory containing nekojs/**, not nekojs itself, a save, the JAR directory or this repository. Prefer absolute paths and an argument array; keep paths with spaces as one element. Windows paths such as `D:/Games/Instance` avoid JSON backslash escaping. If the client cannot find Java on PATH, use an absolute Java 21+ executable. NekoJS does not install or change client settings automatically.

No listener, token or `ai.toml` connection change is needed. The client initializes protocol **2025-11-25**, sends `notifications/initialized`, then calls `nekojs_context`, `nekojs_capabilities` and `nekojs_schema` before queries. The same seven tools and catalogued bundled resources are available over stdio; use `resources/list` then `resources/read`, and inspect `structuredContent`, `isError` and coverage. There are no HTTP session headers or DELETE requests; stop the client-owned helper when finished (EOF exits 0).

The helper accepts required `--game-dir` exactly once and optional `--ai-dir <game-relative-directory>` once, in either order. Discovery priority is explicit override → read-only `[output].directory` from `nekojs/config/ai.toml` (at most 64 KiB; missing setting uses the default) → default `.github/neko_ai/environment.json` if configuration is absent → legacy `nekojs/ai/environment.json` only when both configuration/default descriptor are absent. Invalid configuration fails closed. The obsolete ai-location.json is no longer read or generated. Invalid, escaping or missing selected exports fail closed. Modern `apiSnapshotDirectory` is exactly the selected root's `snapshots/`. It reads exported `apiEnabled` and uses ai.toml only for directory selection; it does not read tokens, create config/listeners or write game data. A changed output selection requires helper restart. Restart the game after API configuration changes to re-export them. Each context/API request validates changed environment/pointer/snapshots; identity changes require helper restart. Docs come from the helper's own verified English bundle (`context.documentation.bundleId`). Offline results describe the **last export**, not live mods or game state.

<a id="wiki-section-7"></a>
### Startup and offline limits

`--help` writes usage to stderr and exits 0; missing/invalid arguments, directory or environment exports exit 2. `--game-dir <directory>` is required exactly once; relative paths resolve against the client's working directory. `not-ready` needs a successful Probe; metadata/schema and docs need no snapshot. `disabled` / `INDEX_DISABLED` needs an enabled state re-exported by a game restart. `unavailable` / `INDEX_UNAVAILABLE` needs descriptor/snapshot diagnosis or a helper restart after environment changes, not silent fallback to stale data.

Stdio uses UTF-8 newline-delimited JSON-RPC, one object per line, no batch arrays. Input/output frames are bounded to 64 KiB; oversized input closes the connection, oversized output requires smaller selections/pages. Stdout is protocol-only; diagnostics use stderr. Never merge stderr into stdout or insert shell banners. The helper opens no port, creates no token and writes no game data; it cannot sandbox an AI client's independent file tools.

<a id="wiki-connections"></a>
<a id="wiki-section-8"></a>
## Advanced: optional HTTP and curl

Read `.github/neko_ai/connection.json` after restart. Use its `http` or `mcp` endpoint and `listening` state; a disabled endpoint is null. `tokenFile` is relative to the game directory (`tokenFileBase: "game-directory"`), not to the descriptor. The descriptor never contains the token. The following POSIX-shell example assumes the default HTTP endpoint, a current game directory and curl (use an equivalent private token-loading mechanism on other shells):

```sh
TOKEN="$(tr -d '\r\n' < nekojs/config/ai.token)"
BASE='http://127.0.0.1:8767/api/v1/authoring'
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/context"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/capabilities"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/schema"
# Save the resolve request below as request.json and replace the discovered IDs.
curl --fail-with-body -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' --data-binary @request.json "$BASE/resolve"
unset TOKEN
```

GET operations: `context`, `capabilities`, `schema`. POST JSON operations: `resolve`, `members`, `query`, `batch`, all below `/api/v1/authoring/`. Every route requires bearer authentication. Query-string parameters are rejected; put scope and filters in the JSON body. See [query requests](#wiki-query) before interpreting results.


| Method | Operations |
|---|---|
| GET | context, capabilities, schema |
| POST JSON | resolve, members, query, batch |

<a id="wiki-section-9"></a>
## Advanced reference: manual HTTP MCP requests

For debugging or custom clients (ordinary clients handle these exchanges automatically), use **Streamable HTTP with JSON responses**, bearer headers and protocol **2025-11-25**. Use the descriptor's `mcp` URL (default `http://127.0.0.1:8767/mcp`), not the ordinary HTTP base. No global AI client configuration is installed or modified.

1. POST a JSON-RPC `initialize` request with `Content-Type: application/json`, `Accept: application/json, text/event-stream` and `Authorization: Bearer <token>`. For example:

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"local-authoring-client","version":"1"}}}
```

2. Read the returned `protocolVersion` and `Mcp-Session-Id` response header. Use that session plus `MCP-Protocol-Version: 2025-11-25` and the same bearer/Accept/Content-Type headers on subsequent POSTs.
3. POST `{"jsonrpc":"2.0","method":"notifications/initialized"}` without an ID; success is HTTP 202 with no body. Then use `tools/list`, `resources/list` and `tools/call`. Start with:

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"nekojs_context","arguments":{}}}
```

4. Read `nekojs_capabilities` and `nekojs_schema`, then call `nekojs_resolve`, `nekojs_members`, `nekojs_query` or `nekojs_batch` with the same argument objects as HTTP. Inspect `structuredContent` and `isError`, not just HTTP success. `resources/read` accepts only URIs returned by `resources/list` (`nekojs://docs/<bundleId>/<path>`), never arbitrary file paths.
5. DELETE the MCP URL with bearer, session and protocol headers when finished. Sessions expire after 30 minutes of inactivity; at most 32 exist. Reinitialize after expiry or restart.

Stdio is provided by the separate helper above, not by the game listener. There is no legacy SSE endpoint, GET event stream (GET /mcp returns 405), prompts, resource subscriptions or server-initiated operations. The Accept header's event-stream token is a transport requirement, not a promise of streaming responses. Use files or ordinary HTTP if your client requires one of these unsupported features.


<a id="wiki-query"></a>
<a id="wiki-section-10"></a>
## Query examples

Every request scope needs the discovered `environmentId`, an uppercase `scriptType` (`STARTUP`, `SERVER`, `CLIENT` or `TEST`) and `language: "typescript"`. Only `memberView: "declared"` is supported. Pin `snapshotId` explicitly once discovered; it may be omitted only to select the current snapshot. Replace angle-bracket placeholders below before sending requests. The names shown are examples, not promises that every instance exports them.


<a id="wiki-section-11"></a>
### Resolve, then inspect members

POST to `resolve` (or call `nekojs_resolve`):

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared"},
  "name":"Item", "kind":"binding", "match":"exact",
  "select":["symbolId","name","kind","coverage","source"], "page":{"size":20}
}
```

Inspect all candidates: class and Binding names can be ambiguous. Copy the chosen result's `symbolId` into `scope.ownerSymbolId` for `members`. Do not substitute a name for an ID. Exact matching is the default; `prefix` is an explicit option. POST to `members`:

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared","ownerSymbolId":"<ownerSymbolId>"},
  "name":"of", "kind":"method", "arity":{"min":1,"max":1},
  "select":["symbolId","name","parameters","returnType","callArity","coverage"], "page":{"size":20}
}
```

Arity filters the overlap of accepted argument-count intervals, including optional/varargs behavior; it is not a raw parameter-count filter. Every callable row is one emitted overload, never a merged method group.


<a id="wiki-section-12"></a>
### Correlate parameters in one overload

POST to `query`. This asks for an `of` overload whose **same first parameter** has display type `string`. It never combines facts from different parameters or overloads:

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared","ownerSymbolId":"<ownerSymbolId>"},
  "from":"members",
  "where":{"all":[
    {"field":"name","op":"eq","value":"of"},
    {"some":{"path":"parameters","where":{"all":[
      {"field":"position","op":"eq","value":0},
      {"field":"type.display","op":"eq","value":"string"}
    ]}}}
  ]},
  "select":["symbolId","name","parameters","returnType","coverage"], "page":{"size":20}
}
```

Resources are `symbols`, `members`, `events`, `inputAdapters` and `declarations`. Boolean groups are `all`, `any` and `not`; correlated arrays use `some`. Discover resource-specific fields, operators, selectable fields and structural request schemas instead of guessing. For declarations, select `path`, `sha256` and `text`; read the generated local declaration when text exceeds the transport budget. Type IDs/display strings are conservative, not an assignability oracle across modules or aliases.


<a id="wiki-section-13"></a>
### Batch

POST to `batch`. Children are query objects, not arbitrary operations; they share the outer scope and pinned snapshot and cannot override scope:

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared"},
  "queries":[
    {"from":"symbols","where":{"field":"name","op":"eq","value":"Item"},"select":["symbolId","name","coverage"]},
    {"from":"events","where":{"field":"group","op":"eq","value":"ServerEvents"},"select":["symbolId","name","group","coverage"]}
  ]
}
```

Inspect each results entry's status and its result or error independently.

<a id="wiki-section-14"></a>
## Coverage, pagination and limits

Inspect `items`, `coverage.state`, `coverage.scope`, `coverage.unresolvedRecords`, `warnings` and `page`. Missing metadata remains unknown even under `not`. Exact filtering never silently becomes fuzzy. Incomplete coverage and an empty last page do **not** prove an API absent.

Declared records include materialized emission projections, not a flattened inherited generic view. Authored declarations, literal overloads and capture views may exist only as declaration text. Binding inheritance/collision resolution, event callback signatures and adapter aliases are not fully indexed: follow their declarations. Python members, `effective` views and `assignableTo` are unsupported.

For another page, set `page.cursor` to the returned `page.nextCursor`, preserving operation, scope, filter, selection and ordering. Page size may change. Stop only after inspecting `page.endOfResults` and `page.stopReason`. The runtime retains the last three loaded snapshots; cursors expire after 15 minutes or shutdown. After `SNAPSHOT_EXPIRED`, refresh context and restart the query, rather than appending results from a different snapshot.


Limits: batch size 1–10, page size default 20/max 100, 32 selected fields, three ordering keys, 32 predicate nodes, depth 4, 100 values per in/notIn and 64 KiB HTTP bodies/responses. Unknown fields, wrong types and unsupported operators are errors, not ignored options. HTTP allows two concurrent exchanges and a 10-second exchange deadline; reduce fields/pages or read large declarations locally.

| Query error | Next step |
|---|---|
| INDEX_NOT_READY | Run a successful TypeScript Probe |
| INDEX_DISABLED | Check api.enabled and restart |
| INDEX_UNAVAILABLE | Check snapshots, configuration and logs |
| SCOPE_CONFLICT | Rediscover this instance's environment |
| SNAPSHOT_EXPIRED | Refresh context and restart the query |
| RESPONSE_TOO_LARGE | Reduce fields/page size or read local files |

<a id="wiki-section-15"></a>
## Practical scripts: choose one complete scenario

Start from `handbook/scripting-examples.md` in the bundle selected by `.github/neko_ai/current.json`, not from connection examples alone. It contains all 14 shipped source files across 12 scenarios, exact destinations, triggers, expected observations, cleanup and profile gates. `examples/metadata.json` records install sets and verification levels. The table below uses paths relative to that bundle's `examples/` directory.

Use a disposable instance/world, back up existing scripts and install only the selected scenario once. Copy ordinary SERVER files to `nekojs/server_scripts/authoring-examples/`, STARTUP to `nekojs/startup_scripts/authoring-examples/`, CLIENT to `nekojs/client_scripts/authoring-examples/`, and TEST to `nekojs/test_scripts/authoring-examples/`. The CommonJS pair goes under SERVER's `authoring-examples/modules/`, including its `lib/` child. The two state files go to different Script Types. Neither MCP mode installs files or executes commands.

| Scenario | Bundled files | Trigger / gate | Expected observation |
|---|---|---|---|
| Player welcome | `server/player-login.js` | SERVER reload, then log in again | Joining player receives a message |
| Recipes | `server/recipes.js` | Whole SERVER reload; inspect recipe postprocessing | Replacement sticks, gravel-to-flint, sand-to-glass |
| Item tag | `server/item-tags.js` | NeoForge: SERVER reload, then vanilla `/reload` | Item stones tag contains stone, excludes granite |
| Block tag | `server/block-tags.js` | NeoForge: SERVER reload, then vanilla `/reload` | Separate block stones tag contains stone |
| Startup registry | `startup/registry.js` | Full process restart; matching client/server definitions | Ruby item and ruby block; block item is automatic |
| Item components | `server/item-components.js` | SERVER reload; inspect console | In-memory stack changes; no inventory grant |
| State and timer | `startup/shared-config.js` + `server/state-and-timer.js` | Restart once, then SERVER reload | Shared greeting, increasing type-local counter, delayed log |
| Client UI | `client/counter.tsx` | NeoForge 26.x CLIENT reload only | Counter screen opens; button increments label |
| CommonJS | `modules/hello.cjs` + `modules/lib/message.cjs` | SERVER reload; preserve relative paths | Logs `Hello, NekoJS!` |
| Assertions | `test/assertions.js` | `/nekojs test` | Three passing assertions |
| Console warmup | `greeting.js` | SERVER reload | One authoring-loaded console line |
| Event warmup | `event-subscription.js` | SERVER reload | Recipe event logs without mutating recipes |

<a id="wiki-section-16"></a>
### First gameplay script: login welcome

Copy this complete file to `nekojs/server_scripts/authoring-examples/player-login.js`, run `/nekojs reload server`, then reconnect:

```js
// Place in server_scripts. The callback runs for future logins, not existing players.
PlayerEvents.loggedIn(event => {
    event.player.sendMessage("Welcome to NekoJS!");
});
```

The next joining player sees `Welcome to NekoJS!`; existing players do not receive it retroactively. Remove the copied file and reload SERVER to stop future greetings. The group subscription and `event.player` Bean property are intentional; keep the player handle inside the callback.

<a id="wiki-section-17"></a>
### Recipe walkthrough and acceptance

Copy to `nekojs/server_scripts/authoring-examples/recipes.js` only in the disposable world:

```js
// Use a disposable world: this replaces the vanilla stick recipe.
ServerEvents.recipes(event => {
    event.remove({ id: "minecraft:stick" });
    event.shaped(Item.of("minecraft:stick", 4), ["P", "P"], {
        P: Ingredient.of("minecraft:oak_planks")
    }).id("nekojs_example:sticks");
    event.shapeless(Item.of("minecraft:flint"), [
        Ingredient.of("minecraft:gravel"),
        Ingredient.of("minecraft:gravel")
    ]).id("nekojs_example:flint");
    event.smelting(Item.of("minecraft:glass"), Ingredient.of("minecraft:sand"), 0.1, 200)
        .id("nekojs_example:glass");
});
```

Run the whole `/nekojs reload server` command and wait for recipe postprocessing, not merely script commit. Expect four sticks from two vertical oak planks under `nekojs_example:sticks`, one flint from two gravel under `nekojs_example:flint`, and sand-to-glass smelting with 0.1 experience/200 configured ticks under `nekojs_example:glass`. The old `minecraft:stick` recipe is removed; the vanilla glass recipe may remain. Verify actual crafting/furnace behavior. Remove the file and fully reload SERVER to restore the base recipes; inspect resource reload/viewer state if stale.

Tags need both SERVER reload and vanilla `/reload` on the implemented NeoForge path; Fabric hooks remain incomplete. Registry content needs process restart and a disposable save; the sample supplies no assets or creative-tab entry. UI needs the NeoForge 26.x `ClientUI` binding; skip it on 1.21.1/Fabric. State is process-local, not disk persistence or client/server synchronization. The component example constructs detached stacks, not inventory items. Use the cookbook's branch-specific cleanup before switching scenarios.

Verification labels distinguish isolated contract checks, real engine execution and real engine execution with a platform fixture. None certifies a live Minecraft world: game acceptance for recipes, tags, registration, player callbacks and UI must be recorded separately. Consult the installed declarations/profile before copying any unfamiliar call.

<a id="wiki-live-controls"></a>
<a id="wiki-section-18"></a>
## Opt-in live MCP actions

Read the active bundle's `handbook/live-controls.md` before authorizing actions. Ordinary HTTP queries and query batches remain read-only; only **in-mod MCP** can advertise these two tools. Standalone stdio has no live host and rejects valid action calls with `LIVE_RUNTIME_REQUIRED` (409), even if the game enabled controls.

1. Back up scripts/saves and use a disposable world. Obtain explicit user authorization for the intended action.
2. Enable `[mcp].enabled` and opt in independently under `[control]`: `commands = true` with `command_allowlist = ["list"]` for a narrow command check, and/or `reload = true` for a full SERVER, CLIENT or TEST reload. Defaults are false, false and an empty allowlist; leave unused actions off.
3. Fully restart the game process and connect using the private URL/bearer workflow above. Commands, SERVER and TEST require a running dedicated/integrated server. CLIENT requires an active client runtime in the client process and works without a server; a dedicated server cannot reload CLIENT. Refresh `tools/list`; inspect `context.liveControls` or `capabilities.liveControls`. `liveControls.reloadTypes` lists currently available, policy-enabled targets in `server`, `client`, `test` order, or `[]` when disabled/unavailable. The reload tool is advertised when at least one target is available; its three-type input enum is not proof of runtime availability. Forged hidden calls still pass the same checks. The descriptor's `readOnly` flag describes policy, not current owner availability.

| Tool | Tool arguments | Effect and limit |
|---|---|---|
| `nekojs_execute_game_command` | `{"command":"list","timeoutMs":5000}` | One exact-root-allowlisted server command, optional leading slash, at most 2048 characters, no control/line separators or surrounding whitespace |
| `nekojs_reload_scripts` | `{"type":"server","timeoutMs":5000}` | One full `server`, `client` or `test` generation on its actual owner; SERVER alone includes recipe/script-datapack followups; not partial/`all`, vanilla reload or Probe |

The command source is fixed non-player, permission level 2 (gamemaster), no entity impersonation, at overworld origin. A player-only or higher-permission command can fail. Allowlisting is exact root matching, not prefixes or argument filtering: `list` and `minecraft:list` differ. Up to 128 roots of at most 128 characters use lowercase letters, digits, `_`, `.`, `-` and optional `namespace:`. **The allowlist is not a sandbox**: broad roots such as `execute` or plugin dispatchers can allow powerful nested effects. Feedback is request-only sanitized plain text, capped at 64 entries/8192 characters, not global logs/admin broadcasts. Completion uses the real command result; synchronous dispatch without a result callback is failure, not success.

Use lowercase `server`, `client` or `test`. For example, replace the arguments with `{"type":"client","timeoutMs":5000}` or `{"type":"test","timeoutMs":5000}`. CLIENT completion is the actual client-owner candidate result, not queueing; it has no server recipe/resource followups and dedicated servers reject it with `RELOAD_UNAVAILABLE`. TEST needs a live server owner and only reloads TEST scripts: it does not run and certify an assertion suite. SERVER retains recipe/script-datapack followups. STARTUP needs process restart (`RESTART_REQUIRED`); partial/single-file, `all` and other unsupported types report `UNSUPPORTED_RELOAD`. Inspect `committed` and `postprocessingSucceeded` independently: `committed: true` with `RELOAD_POSTPROCESSING_FAILED` means the candidate remains committed, not rollback. For CLIENT/TEST, `postprocessingSucceeded: true` means candidate completion without server followups, not proven UI or assertion success. Even candidate rollback cannot undo arbitrary external effects. Follow the cookbook's separate vanilla reload requirements for tag workflows.

Inspect MCP `isError` and `structuredContent`. Admitted results include `operationId`, `operation`, `status`, `code`, `effects`, `retryable`, `output` and `outputTruncated`, plus completion-specific fields. Reload results and audit metadata include the selected lowercase `type`. Rejections are error objects. `timeoutMs` is an integer 1–8000 (default 5000). Expired queued work cannot later start (`effects: "none"`); once running, timeout/interruption may mean `effects: "unknown"`: no rollback, cancellation guarantee or blind retry. Commands and all reload targets share one active-operation slot. Within the claimed operation's owner epoch, it holds the slot until actual completion, even after client timeout; target stop/epoch change may retire the old slot without making started effects known or safe to retry. Further work gets `CONTROL_BUSY`. `retryable` only means work was never claimed, not automatic permission to repeat it. `CONTROL_DISABLED`, `COMMAND_NOT_ALLOWED` and `STALE_RUNTIME` identify policy/allowlist/epoch rejection. CLIENT without a client runtime reports `RELOAD_UNAVAILABLE`; commands or SERVER/TEST without a server report `LIVE_RUNTIME_REQUIRED`. There is no public status-polling API or rewritten timeout response.

Record exact platform/policy, action, observed result and cleanup. Source/fixture tests do not establish live world effects; **human acceptance remains pending** until a maintainer records a real conclusion. Disable unused controls and restart after testing.

<a id="wiki-section-19"></a>
## Safety and validation

The following network/token restrictions apply only to optional in-game HTTP, not the standalone stdio helper. The random bearer token is created only when a listener is enabled, at `nekojs/config/ai.token` with owner-only POSIX permissions or Windows owner ACLs. Keep it out of source control, prompts, screenshots and shared logs. Local bearer auth is not OAuth, TLS or remote access control. Do not forward the port or put it behind a public proxy; browser Origin and Host checks are not a replacement for secrecy. Trusted local processes able to read the file can authenticate.

Only IPv4 loopback peers are accepted. Host must match localhost or 127.0.0.1 and the listener port; an Origin, when present, must match the local HTTP origin. Requests/responses are limited to 64 KiB, with two concurrent exchanges and a 10-second exchange deadline. Select fewer fields or smaller pages when responses exceed budget; read large declarations locally instead. Shutdown closes listeners, sessions and query caches and marks the owning descriptor offline. Ordinary query routes do not evaluate scripts, write scripts, trigger Probe or reload a generation. Only the separate opted-in in-mod MCP tools above can execute allowlisted server commands or reload available SERVER/CLIENT/TEST targets.

Queries assist authoring; they do not verify game behavior. Inspect emitted declarations, use the normal load/reload/restart workflow for the Script Type and report checks actually performed. See [Probe type generation](probe-type-generation_us), [Script basics](script-basics_us), [TypeScript and JSX](typescript-and-jsx_us), [Commands](commands_us) and [Errors and logs](error-reference_us) (NEKO-1019, NEKO-4055).

<!-- wiki-nav -->

---

[Previous: Event reference](event-reference_us) · [Contents](Home) · [Next: Commands](commands_us)
