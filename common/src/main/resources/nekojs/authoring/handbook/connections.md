# MCP connections: in-mod or standalone

NekoJS supports two MCP modes with the same read-only authoring tools: use the **in-mod MCP service while the game runs**, or the independent [stdio helper](stdio.md) for the last exported snapshot, including while the game is stopped. Only in-mod MCP can expose separately opted-in [live controls](live-controls.md). Neither mode automatically configures or connects your AI client.

## In-mod MCP: connect while the game runs

1. In `nekojs/config/ai.toml`, set `enabled = true` under `[mcp]` (default: false). Keep `host = "127.0.0.1"` and `port = 8767` unless another local port is needed. `[http].enabled` may remain false: it controls ordinary JSON routes, not the MCP endpoint.
2. Fully restart the game process. Check `.github/neko_ai/connection.json`: `listening` must be true and `mcp` must contain the endpoint, normally `http://127.0.0.1:8767/mcp`. Keep the game running while connected.
3. Add a Streamable HTTP server in the AI client using that URL and an `Authorization: Bearer <token>` header. Replace `<token>` locally with the contents of `nekojs/config/ai.token`; never commit or share it. The connection descriptor contains the token file location, not the credential.

The client needs **URL + bearer credential**, not a Java command, standalone JAR path or `--game-dir` argument. For clients supporting an `mcpServers` object with `url` and `headers`, the configuration shape is:

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

This settings schema is client-dependent; some clients use a connection dialog or explicit HTTP transport setting. Choose Streamable HTTP/JSON response support and protocol **2025-11-25**. A normal MCP client performs initialization, session headers and shutdown itself; the raw JSON-RPC examples below are only for debugging or custom clients.

4. Confirm the tools are listed, then call `nekojs_context`, `nekojs_capabilities` and `nekojs_schema`. API queries require a successful **TypeScript Probe** snapshot; enabling MCP alone does not generate one. Inspect coverage and `isError` before using signatures.

There is no automatic discovery, GUI enable switch or automatic client configuration. A disabled endpoint is null; authentication remains required after explicit enablement. If you need offline access or prefer a separate client-owned process, use [standalone stdio](stdio.md); only that mode needs a JAR and game-directory path.

## Start offline

Documentation export is enabled by default and does not scan classes or open a port. From the game directory, read `.github/neko_ai/current.json`: its `bundle` and `manifest` paths are relative to `.github/neko_ai/`. Read the active handbook, Skill and examples from that bundle. Read `.github/neko_ai/environment.json` for the instance and configured API snapshot directory. Never assume a bundle or snapshot copied from another instance matches this one.

Exports live in `.github/neko_ai/bundled/<bundleId>/` by default. Missing `AGENTS.md` and `README.md` are created under the selected AI root, not under `nekojs/` or the workspace root. Do not edit an exported bundle in place: a modified same-ID export is rejected instead of overwritten. Disabling documentation does not delete old exports.

### Output directory and discovery

`[output].directory` selects a nonblank game-relative directory (default `.github/neko_ai`); absolute paths, traversal and links escaping the selected game directory are rejected. All generated AI material belongs there: `bundled/`, `current.json`, `environment.json`, `connection.json`, `snapshots/`, `agents/`, `AGENTS.md` and `README.md`. Configuration and private credentials remain in `nekojs/config/`.

Read the stable `nekojs/config/ai-location.json` locator first. Its pure data shape is `{"schemaVersion":1,"directory":".github/neko_ai"}`; resolve `directory` against the game directory, then read that root's discovery files. Examples here use the default; substitute your configured root everywhere. The locator is not instructions and contains no token. The offline helper also supports an explicit game-relative `--ai-dir` override; see [stdio discovery](stdio.md).

Changing directories does not blindly move or delete old files. Known unmodified generated role routers can refresh safely; edited routers and other author-owned files are preserved. Declarations, snippets and editor configuration stay in their existing Probe locations; `snapshots/` is the structured AI index, not the declaration tree.

The root's `agents/` files are reference routers, not automatically discovered VS Code agents. `[output].editor_agents = true` optionally writes compatible routers to standard `.github/agents/`; it is false by default. The AI root's `AGENTS.md` is not automatically workspace-wide instructions: explicitly point the client to it or integrate selected guidance into your own instruction setup. No global client settings are changed.

## Configuration reference: generated defaults

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

Set `[http].enabled = true` for ordinary JSON routes, `[mcp].enabled = true` for MCP, or both. They share the port under `[mcp]`. Only the literal IPv4 loopback host is accepted. Ports must be integers from 1 through 65535; booleans must be TOML booleans. `documentation.agents` accepts only `create-if-missing` or `off`. `api.enabled = false` disables resolve/members/query/batch, not context, capabilities, schema or bundled docs.

Restart the game process after configuration changes. Ordinary script reload does not recreate the root-owned listener. Invalid settings or a port conflict reject authoring initialization (NEKO-1019), not the whole game.

## Advanced: ordinary HTTP

Read `.github/neko_ai/connection.json` after restart. Use its `http` or `mcp` endpoint and `listening` state; a disabled endpoint is null. `tokenFile` is relative to the game directory (`tokenFileBase: "game-directory"`), not to the descriptor. The descriptor never contains the token. The following POSIX-shell example assumes the default HTTP endpoint, a current game directory and curl (use an equivalent private token-loading mechanism on other shells):

```sh
TOKEN="$(tr -d '\r\n' < nekojs/config/ai.token)"
BASE='http://127.0.0.1:8767/api/v1/authoring'
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/context"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/capabilities"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/schema"
# Save a request from query-api.md as request.json and replace its discovered IDs.
curl --fail-with-body -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' --data-binary @request.json "$BASE/resolve"
unset TOKEN
```

GET operations: `context`, `capabilities`, `schema`. POST JSON operations: `resolve`, `members`, `query`, `batch`, all below `/api/v1/authoring/`. Every route requires bearer authentication. Query-string parameters are rejected; put scope and filters in the JSON body. See [query requests](query-api.md) before interpreting results.

## Advanced reference: manual MCP over HTTP

Configure an MCP client supporting **Streamable HTTP with JSON responses**, bearer headers and protocol **2025-11-25**. Use the descriptor's `mcp` URL (default `http://127.0.0.1:8767/mcp`), not the ordinary HTTP base. No global AI client configuration is installed or modified.

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

Stdio is provided by the separate [standalone helper](stdio.md), not this in-game endpoint. There is no legacy SSE endpoint, GET event stream (GET /mcp returns 405), prompts, resource subscriptions or server-initiated operations. The Accept header's event-stream token is a transport requirement, not a promise of streaming responses. Use files or ordinary HTTP if your client requires one of these unsupported features.

## HTTP security and budgets

The random bearer token is created only when a listener is enabled, at `nekojs/config/ai.token` with owner-only POSIX permissions or Windows owner ACLs. Keep it out of source control, prompts, screenshots and shared logs. Local bearer auth is not OAuth, TLS or remote access control. Do not forward the port or put it behind a public proxy; browser Origin and Host checks are not a replacement for secrecy. Trusted local processes able to read the file can authenticate.

Only IPv4 loopback peers are accepted. Host must match localhost or 127.0.0.1 and the listener port; an Origin, when present, must match the local HTTP origin. Requests/responses are limited to 64 KiB, with two concurrent exchanges and a 10-second exchange deadline. Select fewer fields or smaller pages when responses exceed budget; read large declarations locally instead. Shutdown closes listeners, sessions and query caches and marks the owning descriptor offline. Ordinary HTTP routes and batch remain read-only. They do not evaluate or write scripts, trigger Probe or expose live controls. Separately opted-in MCP controls can execute allowlisted server commands or reload full SERVER, CLIENT or TEST generations on their available owners; read [live controls](live-controls.md) before enabling them.
