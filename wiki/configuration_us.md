<!-- wiki-page: configuration; locale: us -->

> **English** · [中文](configuration_cn)

<a id="wiki-section-1"></a>
# Configuration

All NekoJS configuration files live in `nekojs/config/` inside the game directory. Most people never need to change anything; the defaults work for normal scripting.

<a id="wiki-section-2"></a>
## Configuration files

| File | What it controls | Details |
|---|---|---|
| `nekojs/config/engine.toml` | Script permissions, module and JSX options, error logs, script pack sync, experimental features | This page |
| `nekojs/config/probe.toml` | How code completion declarations are generated | [Probe type generation](probe-type-generation_us) |
| `nekojs/config/ai.toml` | AI authoring material export and the local HTTP/MCP service | [AI-assisted scripting](ai-support_us) |

`engine.toml` and `probe.toml` are created on first launch. Missing keys are added later automatically, each with a short comment.

The `.neko_probe/` folder in the game directory and the `jsconfig.json` files in script folders are generated, not configuration. Manual edits are overwritten the next time they are generated.

<a id="wiki-section-3"></a>
## Default engine.toml

```toml
allowThreads = false
allowReflection = false
allowAsm = false
allowFsWriteOutsideNekojs = false
enableEsmAuthoring = true
conciseScriptErrorLogs = true
jsxAutomaticRuntime = false
scriptMemberValidation = true

[packSync]
mode = "off"
allowUnsigned = false

[dynamicRegistry]
enabled = false

[mixinjs]
enabled = false
```

| Key | Default | What it does |
|---|---|---|
| `allowThreads` | `false` | Lets scripts create their own background threads. Can cause lag or resource leaks. |
| `allowReflection` | `false` | Lets scripts use Java reflection to bypass access checks and change private data. |
| `allowAsm` | `false` | Lets scripts manipulate Java bytecode directly. Mistakes can cause severe crashes. |
| `allowFsWriteOutsideNekojs` | `false` | Lets `fs` write and delete anywhere in the game directory instead of only under `nekojs/`. Paths outside the game directory stay blocked, and `nekojs/config/` stays read-only. |
| `enableEsmAuthoring` | `true` | Enables `import` / `export`. Turn it off only if you need pure CommonJS `require` behavior. See [Module system](module-system_us). |
| `conciseScriptErrorLogs` | `true` | Error logs show only the short, source-focused message. Set `false` to log full stack traces when chasing a hard problem. |
| `jsxAutomaticRuntime` | `false` | Compiles JSX with the automatic runtime, importing from the built-in `nekojs/jsx-runtime` module. The default is the classic runtime. See [TypeScript and JSX](typescript-and-jsx_us). |
| `scriptMemberValidation` | `true` | Checks member names on Bindings and event callbacks before scripts load and reports typos to the error panel. It is not full type checking. Turn it off to skip the extra parsing. |
| `packSync.mode` | `"off"` | Whether a multiplayer server sends script packs to clients. `off`: never; `hashOnly`: send hashes only, clients never run server packs; `all`: send packs and run them on the client. See [Script packs](script-packs_us). |
| `packSync.allowUnsigned` | `false` | Whether to accept server script packs without a signature. By default they are rejected and the player is disconnected with a message. |
| `dynamicRegistry.enabled` | `false` | Experimental: lets scripts register new content after the server has started. Not every platform or registry supports it. See [Registering new content](registering-new-content_us). |
| `mixinjs.enabled` | `false` | Enables MixinJS. See [MixinJS](mixinjs_us). |

<a id="wiki-section-4"></a>
## Applying changes

1. Close the game or dedicated server.
2. Edit `nekojs/config/engine.toml`.
3. Start it again.

`engine.toml` is read once at startup. `/nekojs reload` does not re-read it.

After startup, the game log contains `Engine config loaded. Unsafe features enabled: false` (`true` if you turned on a permission switch). That line confirms the file was read.

When editing TOML:

- Write booleans without quotes: `allowThreads = true`, not `"true"`.
- Keys without a table (such as `allowThreads`) must come before the first `[...]` header. Under `[mixinjs]` the key becomes `mixinjs.allowThreads` and has no effect.
- Do not write the same table header twice.
- If the file is malformed, or a top-level key has the wrong type, the log warns `Failed to load nekojs/config/engine.toml, using default sandbox config` and **every setting uses its default**.

After changing `jsxAutomaticRuntime`, run `/nekojs probe` once so the editor's `jsconfig.json` matches.

<a id="wiki-section-5"></a>
## Safety advice

- Only run scripts and script packs you trust.
- `allowThreads`, `allowReflection`, `allowAsm` and `allowFsWriteOutsideNekojs` all loosen restrictions. Turn them on only when you really need them, never just to silence an error.
- On clients that join unknown servers, keep `packSync.allowUnsigned = false`.
- MixinJS does not need `allowAsm` or `allowReflection`.
- Scripts cannot modify files in `nekojs/config/`.
- The AI service access token is stored in `nekojs/config/ai.token`. Do not commit it to a repository or ship it in a modpack.

<a id="wiki-section-6"></a>
## Old configuration

**Old file location.** Early versions kept the engine settings in `config/nekojs-engine.toml`. NekoJS reads that file only when `nekojs/config/engine.toml` does not exist, and logs a reminder to migrate. The old file is read-only and is never moved or changed. Copy its contents to `nekojs/config/engine.toml` yourself, then delete the old file.

**Removed keys.** `scriptEvaluationTimeoutSeconds`, `scriptStatementLimit`, `scriptRunawayTimeoutSeconds`, `prependRequirePatch`, `useNekoScriptLoader` and `useNativeEsmLoader` are no longer used and are deleted from the file at startup. Scripts now have no execution timeout: an endless loop in a script freezes the game, so avoid writing one.

If a script fails, start with the [FAQ](faq_us) and the [Error reference](error-reference_us).

<!-- wiki-nav -->

---

[Previous: Quick start](quick-start_us) · [Contents](Home) · [Next: FAQ](faq_us)
