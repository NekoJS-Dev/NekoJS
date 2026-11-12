<!-- wiki-page: platform-compatibility; locale: us -->

> **English** · [中文](platform-compatibility_cn)

<a id="wiki-section-1"></a>
# Platforms and compatibility

The current NekoJS codebase contains two groups of nodes: NeoForge and Fabric. This page summarizes platform status; for individual APIs, the platform notes on the relevant reference page still apply.

<a id="wiki-section-2"></a>
## Current nodes

| Platform | Minecraft | Current status | Java | Description |
|---|---|---|---|---|
| NeoForge | 1.21.1 | Supported | 21 | An older version node under stable maintenance |
| NeoForge | 26.1.2 | Supported | 25 | A primary version node |
| NeoForge | 26.2.0 | Supported | 25 | A primary version node |
| Fabric | 26.1.2 | Current repository build node | 25 | Loader-specific API subset; verify the installed artifact/profile |
| Fabric | 26.2 | Current repository build node (`26.2.0-fabric`) | 25 | Loader-specific API subset; verify the installed artifact/profile |

Fabric nodes already cover script loading, recipes, networking, probe, some registry features, common events, and key bindings. This does not mean that they have complete feature parity with NeoForge. Refer to [Releases](https://github.com/NekoJS-Dev/NekoJS/releases) for publication status.

The node graph is source/build evidence, not release or live-game acceptance evidence. Scripts need the matching **GraalMC** dependency (see [Quick start](quick-start_us)), including Python-to-JS execution; installing Python, GraalPy, or a GraalVM JDK is not a substitute. This audit did not run Minecraft.

<a id="wiki-section-3"></a>
## Main feature differences

| Feature | NeoForge 1.21.1 / 26.x | Fabric 26.x |
|---|---|---|
| JS/TS/JSX/Python scripts | Supported | Supported |
| ESM, CommonJS, Node core module adapters | Implemented subset, not a full Node.js process | Implemented subset, not a full Node.js process |
| Recipe scripts and `reload server` | Supported | Supported; the current implementation determines the recipe API |
| `/nekojs probe` and TypeScript/Python declarations | Supported | Supported |
| `Network` / `NetworkEvents` network channels | Supported | Supported |
| Basic registration builders | Supported | Partially supported |
| `Registry` runtime view | Native query service | Binding exists, but queries use an empty fallback; not a functional cross-loader lookup |
| JSX client Screen UI | NeoForge 26.x | Not yet ported |
| `ClientEvents` HUD/Screen Painter | Supported | Supports `hud` and `screenRender`; registered renderers through `hudRender` / `worldRender` have not yet been ported |
| `ServerEvents.tags` | Supported | Not yet ported |
| `ServerEvents.generateData` | Supported | Not yet ported |
| `ClientEvents.generateAssets` / `lang` | Supported | Not yet ported |
| `Fluid`, `FluidIngredient`, `FluidBuilder` | Supported | Not yet ported |
| `Capabilities` / `CapabilityEvents` | Standard and third-party native capabilities for ordinary blocks, block entities, entities, and items; preserves each scope's context | Real Lookup integration is registered; items/fluids use Fabric Transfer, energy uses NekoJS's own interface, and custom typed Lookups are supported |
| `ServerEvents.tradeDeclaration` / `VillagerTrades.query` | Supported | Nonempty declarations are rejected; query binding is unavailable |
| `PostEffects` | Supported | Not yet ported |
| JEI recipe viewer events | Supported (requires JEI) | Not yet ported |
| 26.x enchantment registration builder | Unavailable | Unavailable |

Enchantments in 26.x use data-driven registries. The runtime registration builder from 1.21.1 cannot be reused; use a data pack instead. A builder retained in the source code does not mean that a 26.x registration path is available.

**Optional addons are not cross-platform features:** NekuJS is a separately installed NeoForge 1.21.1 compatibility addon, not part of the core JAR or evidence that KubeJS addons work on Fabric/26.x. MixinJS is default-off and retains development-only/exact-profile restrictions even after opt-in; it is distinct from NekoJS’s own implementation Mixins. See [Recipe system](recipe-system_us) and [MixinJS](mixinjs_us).

**Client rendering is not one feature flag.** Painter `hud` / `screenRender` exists on Fabric too; named `hudRender` / `worldRender` and post effects are NeoForge-only. NeoForge 26.2 `ctx.line` / `ctx.box` now submit through an independently owned staged buffer; 1.21.1/26.1.2 use their corresponding paths. Submission regressions do not establish GPU visual acceptance. JSX `ClientUI` is NeoForge 26.x only. See [Client rendering and post effects](client-rendering_us) and [JSX client UI](jsx-client-ui_us) for activation, lifecycle, and limits.

<a id="wiki-section-3-issue4"></a>
**Issue #4 implemented capabilities**

The following surfaces are implemented in the corresponding NeoForge 1.21.1, NeoForge 26.x, and Fabric 26.x nodes. Fabric still only promises the subset explicitly listed here and on each reference page:

| Capability | Runtime contract | Detailed reference |
|---|---|---|
| Painter | `ClientEvents.hud` and `ClientEvents.screenRender` provide a frame-scoped `PainterJS`; supports text, gradients, clipping, transforms, items, textures, and explicit UV/dimension parameters | [Event reference](event-reference_us#wiki-section-25) |
| Entity / Goal | Registered entities get a visible humanoid renderer by default; native entity classes, attribute baselines, Goal configuration, and native Goal classes/factories are supported | [Registering new content](registering-new-content_us#wiki-section-7), [Event reference](event-reference_us#wiki-section-14) |
| Persistent Data | Server entity/player PData uses automatic dirty flush, login/tracking snapshots, and respawn/clone copying; client mirrors are read-only; ItemStack data uses vanilla `CUSTOM_DATA` | [Global bindings](global-bindings_us#wiki-section-4) |
| Capability | NeoForge uses native capabilities and transactional handlers; Fabric uses real Transfer/Lookup APIs; providers commit once during STARTUP and owners retain saving/synchronization responsibility | [Global bindings](global-bindings_us#wiki-section-26), [Event reference](event-reference_us#wiki-section-15) |

The Fabric entity-PData container root is now aligned with its bridge. Historical double-wrapped saves remain intact and require backup and an intentional migration decision; regression fixtures are not real restart/multiplayer acceptance. See [Data, lifetime, and authority](data-and-state_us). Painter objects and client PData mirrors are valid only during their drawing/connection lifecycle. Capability providers do not automatically persist arbitrary owner storage, and Fabric's energy interface is not a cross-mod standard energy protocol.

Entity type builders use a visible humanoid renderer on the NeoForge/Fabric nodes above, matching the vanilla 64×64 zombie texture by default; `renderer`, `texture`, and `shadowRadius` are configurable. Native entity construction and custom Goal factories must be Java implementations independent of the guest Context; built-in Goal configuration can still use script callbacks.

Identically named Capability script entries do not imply identical native types: NeoForge 26.x factories use native transactional storage, 1.21.1 uses the older interfaces with `simulate`, and Fabric uses Transfer transactions. Fabric has no standard cross-mod energy API, so NekoJS's `FabricEnergyHandler` cannot automatically access other energy mods. The input capacity for `fluidTank` is consistently expressed in mB, but quantities and capacities on Fabric's returned objects use droplets (1000 mB = 81000 droplets). The concrete owner retains, persists, and synchronizes storage; registering a provider does not automatically save data. See [Global bindings](global-bindings_us#wiki-section-26) and [Event reference](event-reference_us#wiki-section-15).

<a id="wiki-section-4"></a>
## Command differences

The following commands exist on Fabric, but some capabilities are text-based or subsets:

- `/nekojs reload <type> <file>`: supported on Fabric.
- `/nekojs view_all_errors`: Fabric outputs chat text; NeoForge can open an error UI.
- `/nekojs probe reset_config`: supported on Fabric.
- `/nekojs editor`: removed; use an external editor for scripts.
- `/nekojs registry`: the command exists on Fabric, but its dynamic registration snapshot is currently empty; dynamic registration is still outside Fabric's supported feature set.
- `/nekojs reload startup`: all current platforms reject reloading STARTUP through an in-game command. Startup script changes require a game restart.

<a id="wiki-section-5"></a>
## Cleanroom legacy

Cleanroom 1.12.2 is maintained in a separate legacy branch and is not part of the current codebase's version graph. Neither this page nor the current repository can verify that branch's implementation. Cleanroom sections retained in the Wiki should be treated as legacy material and should not be read as part of the current NeoForge/Fabric support matrix.

<a id="wiki-section-6"></a>
## How to determine availability

- When writing cross-platform scripts, prefer public APIs marked as supported on this page.
- Follow platform labels on reference pages; do not assume that an unlabeled new API is available on Fabric.
- If a binding or event is missing after your script starts, run `/nekojs probe`, then check `/nekojs error` and the platform-specific notes.
- For code contributions, see the repository's `docs/fabric-port-status.md` for detailed Fabric gaps and batch progress.

<!-- wiki-nav -->

---

[Previous: Home](home_us) · [Contents](Home) · [Next: Quick start](quick-start_us)
