<!-- wiki-page: configuration; locale: us -->

> **English** · [中文](configuration_cn)

<a id="wiki-section-1"></a>
# Configuration

NekoJS keeps its editable configuration under the game directory's `nekojs/config/`. Engine permissions, declaration generation and AI authoring are separate settings: enabling one does not enable the others.

<a id="wiki-section-2"></a>
## Which file to edit

| File | Purpose | Main guide |
|---|---|---|
| `nekojs/config/engine.toml` | Script permissions, module/JSX options, pack synchronization, experimental dynamic registration and MixinJS opt-in | This page |
| `nekojs/config/probe.toml` | Probe enablement, scan filters and declaration backends/output | [Probe type generation](probe-type-generation_us) |
| `nekojs/config/ai.toml` | Exported authoring material, local HTTP/MCP listener and separately authorized live controls | [AI-assisted authoring](ai-support_us) |

Generated `.neko_probe/` declarations and script-directory `jsconfig.json` files are not engine configuration. Change their canonical settings instead of repeatedly hand-editing generated output. AI output defaults to `.github/neko_ai`; private tokens stay under `nekojs/config/` and must not be committed.

<a id="wiki-section-3"></a>
## Engine defaults

This is a current default-settings reference, not an instruction to enable experimental features. TOML names and types are significant: use booleans, not quoted strings. Root keys must appear before the first table header.

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

| Key | Default | Meaning and limits |
|---|---|---|
| `allowThreads` | `false` | Permits unmanaged guest-created threads; this does not make concurrent access to one Graal Context safe. |
| `allowReflection` | `false` | Relaxes reflection-related class restrictions for trusted scripts; unrelated host/class restrictions still apply. |
| `allowAsm` | `false` | Relaxes ASM/bytecode class restrictions; it is not the MixinJS activation switch. |
| `allowFsWriteOutsideNekojs` | `false` | Extends Node filesystem writes/deletes from `nekojs/` to the game directory, not arbitrary paths outside it; containment checks remain and protected engine configuration is still not script-writable. |
| `enableEsmAuthoring` | `true` | Enables ESM authoring support; see the ESM/CJS rules in [Module system](module-system_us). |
| `conciseScriptErrorLogs` | `true` | Uses source-focused error logs; `false` requests verbose details. |
| `jsxAutomaticRuntime` | `false` | Selects automatic JSX lowering instead of the classic factory; this alone does not add a React runtime or change supported UI platforms. See [TypeScript and JSX](typescript-and-jsx_us). |
| `scriptMemberValidation` | `true` | Reports supported binding/callback member mistakes; this is not full TypeScript checking. |
| `packSync.mode` | `"off"` | `off`, `hashOnly` or `all`; selecting synchronization does not bypass pack trust, signatures or platform support. See [Script packs](script-packs_us). |
| `packSync.allowUnsigned` | `false` | Rejects unsigned server packs by default. Do not relax it for an untrusted server. |
| `dynamicRegistry.enabled` | `false` | Opts into experimental post-start registration on implemented platforms; does not make every registry or Fabric dynamic registration available. See [Registering new content](registering-new-content_us). |
| `mixinjs.enabled` | `false` | Startup opt-in only. It does not relax the existing development-only/profile/trust/thread restrictions. See [MixinJS](mixinjs_us). |

<a id="wiki-section-4"></a>
## Apply changes and migrate old files

1. Stop the game or dedicated server and back up the current file.
2. Edit the canonical file, keeping each key under its correct TOML table. Do not append root keys beneath `[mixinjs]` or duplicate an existing table.
3. Restart the process. An ordinary script reload does not rebuild the root configuration or undo bytecode transformations. MixinJS captures an immutable early-startup setting, so both enabling and disabling it require a full restart.
4. Check startup logs. A malformed engine file may fall back to default settings; a missing, unreadable, malformed or non-boolean early MixinJS setting never opts in. Fix the file rather than assuming that every requested setting applied.
5. After editor-related changes, run `/nekojs probe` to refresh the TypeScript workspace; use `/nekojs probe python` for Python. These commands do not replace the engine restart.

If the canonical engine file is absent and `config/nekojs-engine.toml` exists at the game root, it is honored as a **read-only legacy fallback**. It is not automatically moved or rewritten. Back it up and migrate it manually to `nekojs/config/engine.toml`; an existing canonical file takes precedence. Do not maintain conflicting copies.

**Removed execution controls:** `scriptEvaluationTimeoutSeconds`, `scriptStatementLimit` and `scriptRunawayTimeoutSeconds` no longer configure normal scripts. The writable loader removes these old keys; the read-only legacy file is left unchanged. There is no replacement hidden execution deadline or watchdog. Infinite synchronous guest code may block its owner thread, and top-level `await` that never settles may leave loading pending. HTTP/authoring request waits, lifecycle cancellation and the private Mixin declaration evaluator's fixed budget are separate mechanisms and remain in place.

<a id="wiki-section-5"></a>
## Security and troubleshooting

Only run scripts and packs you trust. Permission switches are not a proof that arbitrary Java object access or host calls are safe, and they should not be enabled merely to silence an unrelated error. MixinJS does not require enabling `allowAsm` or `allowReflection`. Keep the AI listener and live controls default-off unless deliberately needed; never distribute its bearer token with a modpack.

For script failures see [FAQ](faq_us) and [Error reference](error-reference_us). For commands that reload scripts, data or declarations, see [Commands](commands_us); those operations have different scopes.

<!-- wiki-nav -->

---

[Previous: Quick start](quick-start_us) · [Contents](Home) · [Next: FAQ](faq_us)
