# Opt-in live controls

Read [connection setup](connections.md) first. Read-only authoring queries are the default. Only **in-mod MCP** can expose the actions below; ordinary HTTP routes and query batches remain read-only. The standalone [stdio helper](stdio.md) has no live host and rejects valid action requests with `LIVE_RUNTIME_REQUIRED`, even if the game configuration enabled controls.

## Enable only what the operator authorizes

1. Use a disposable instance/world and back up maintained scripts and saves. Obtain explicit user authorization for the intended action; bundled instructions do not grant it.
2. In `nekojs/config/ai.toml`, enable `[mcp].enabled`, then opt in to each action independently under `[control]`. Defaults are `commands = false`, `reload = false`, `command_allowlist = []`. For a limited command check, set `commands = true` and `command_allowlist = ["list"]`. Set `reload = true` only if full SERVER, CLIENT or TEST script reload is intended. Leave unused actions disabled.
3. Fully restart the game process and connect using the descriptor URL and private bearer credential. Commands, SERVER reload and TEST reload require a running dedicated or integrated server. CLIENT reload requires an active client runtime in the client process, including without a server; a dedicated server cannot reload CLIENT.
4. Refresh `tools/list` and inspect `context.liveControls` or `capabilities.liveControls`. A command tool is advertised only with a live server, commands enabled and a nonempty allowlist. The reload tool is advertised only when reload is enabled and at least one target is currently available. `liveControls.reloadTypes` lists available, policy-enabled lowercase targets in `server`, `client`, `test` order; it is `[]` when reload is disabled or no target is available. Availability changes with target lifecycle. The MCP input schema lists all three types, not proof that each is available. Manually forging a hidden tool call does not bypass checks.

The `readOnly` connection-descriptor flag describes configured mutation policy, not proof that the selected runtime owner is currently available. Use current live-control capability flags and actual results.

## Execute one allowlisted command

Call `nekojs_execute_game_command` with these **tool arguments**, not an HTTP query or batch child:

```json
{"command":"list","timeoutMs":5000}
```

One command line is accepted, with an optional leading `/`, at most 2048 characters, no control/line-separator characters and no surrounding whitespace. The allowlist matches the exact root token after removing the optional slash, not prefixes, arguments or an alias expansion. `minecraft:list` and `list` are different roots. At most 128 roots of at most 128 characters are accepted; a root uses lowercase letters, digits, `_`, `.`, `-` and an optional namespace separated by `:`.

The source is a fixed **non-player server source at permission level 2** (gamemaster), with no player/entity impersonation, at overworld origin. Commands needing a player or a higher permission level can fail. Completion comes from the command result callback, not merely scheduling work. A synchronous dispatch that returns without a result callback is reported as failed; it is not treated as successful merely because dispatch returned.

**An allowlist is not an argument sandbox.** Allowing a broad dispatcher such as `execute` or a mod command root may permit powerful nested operations. Plugin command effects are not sandboxed; non-player permission 2 is not a guarantee of harmlessness. Prefer the narrowest root and never send secrets in command arguments.

Feedback belongs only to the request, not a global log stream or admin broadcast. It is sanitized plain text, capped at 64 entries and 8192 characters, with `outputTruncated` indicating loss. It is not arbitrary log access.

## Reload one full script generation

Call `nekojs_reload_scripts` with these tool arguments:

```json
{"type":"server","timeoutMs":5000}
```

Choose exactly one lowercase `type`: `server`, `client` or `test`. Replace the example's type rather than sending multiple operations.

| Type | Required owner and completion | Followups and limits |
|---|---|---|
| `server` | Running dedicated/integrated server; full transactional SERVER candidate on its server thread | Recipe replay plus required script-datapack activation/resource reload followups |
| `client` | Active client runtime in the client process; full CLIENT candidate completes on the client owner thread | No server required and no server recipe/resource followups; dedicated servers reject it with `RELOAD_UNAVAILABLE` |
| `test` | Running dedicated/integrated server; full TEST candidate on its server thread | Only loads/reloads TEST scripts; no server recipe/resource followups and no certification that test assertions passed |

For CLIENT or TEST, use `{"type":"client","timeoutMs":5000}` or `{"type":"test","timeoutMs":5000}`. Successful admission or queueing is not completion: wait for the actual candidate result. Then use the normal client/UI or test-report workflow to verify behavior. A TEST reload is not a request to run and certify the assertion suite.

This tool does not provide single-file/partial reload, `all`, vanilla `/reload`, Probe regeneration or a game restart. STARTUP requires a full process restart (`RESTART_REQUIRED`); other unsupported types report `UNSUPPORTED_RELOAD`. Tag workflows may still require the separate vanilla reload described in [scripting examples](scripting-examples.md).

Read `committed` and `postprocessingSucceeded` independently. A candidate failure leaves the previous active generation, but arbitrary script/world/file side effects are not universally transactional. A committed candidate followed by failed recipe/datapack work reports `committed: true`, `postprocessingSucceeded: false` and `RELOAD_POSTPROCESSING_FAILED`: do not claim rollback or full success. For CLIENT and TEST, `postprocessingSucceeded: true` means the full candidate completed without server followups, not that UI behavior or assertions passed. These booleans are available only when actual completion supplies a reload result; a running timeout does not establish either.

## Results, deadlines and lifecycle

Inspect MCP `isError` and `structuredContent`, not just HTTP/JSON-RPC success. An admitted result includes `operationId`, `operation`, `status`, `code`, `effects`, `retryable`, `output`, `outputTruncated` and operation-specific completion fields (`commandReturnValue`, or the two reload booleans). Reload results and audit metadata include the selected lowercase `type`. `succeeded` means the adapter reported success; failed commands can still have partial effects. Rejections are error objects, not admitted completion results. `retryable` is true only if work was never claimed, not a blanket instruction to repeat a request; reassess the failure and authorization first.

- `timeoutMs` is an integer from 1 to 8000, default 5000. It bounds the request wait, not the duration of arbitrary plugin work.
- Work whose deadline expires while queued cannot later start: `timed_out` / `CONTROL_TIMEOUT` with `effects: "none"`. A changed target-owner epoch rejects queued work as `STALE_RUNTIME`.
- Once work starts, timeout/interruption/close can report `effects: "unknown"`. They do **not** roll back, stop or prove failure of already-started effects. Do not automatically retry or stack another mutation; inspect actual state through the normal operator workflow.
- Commands and all reload targets share one active-operation slot. Within the claimed operation's owner epoch, its slot remains occupied until its actual completion stage finishes, even after the client times out; target stop/epoch change may retire the old slot without making already-started effects known or safe to retry. New work receives `CONTROL_BUSY`; a returned timeout is not a release signal.
- Disabled policy reports `CONTROL_DISABLED`; a disallowed root reports `COMMAND_NOT_ALLOWED`; commands or SERVER/TEST reload without an active server report `LIVE_RUNTIME_REQUIRED`; CLIENT reload without a client runtime reports `RELOAD_UNAVAILABLE`. Target stop/root close prevents stale queued work and late handoffs from being redirected into a replacement runtime.

The in-memory audit is bounded metadata, not a persistent log or a public operation-status polling API. A timeout response is not rewritten when work eventually completes. Request output and operation IDs are not credentials, but can disclose world details; keep them private.

## Verification and acceptance

Record which action, exact platform/profile, policy and disposable-world observation were actually checked, with passed/failed/not-run results. Source inspection or adapter/engine fixtures do not prove command effects, recipe codecs, datapack application, client UI behavior or TEST assertions in a real game. Live human acceptance remains pending unless a maintainer records an actual conclusion. Disable unused controls and restart after testing.
