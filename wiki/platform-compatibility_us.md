<!-- wiki-page: platform-compatibility; locale: us -->

> **English** · [中文](platform-compatibility_cn)

<a id="wiki-section-1"></a>
# Platforms and compatibility

NekoJS supports two loaders: NeoForge and Fabric. NeoForge has the most complete feature set. Fabric handles most common work, but some features have not been ported yet.

<a id="wiki-section-2"></a>
## Supported versions

| Loader | Minecraft | Java | Notes |
|---|---|---|---|
| NeoForge | 1.21.1 | 21 | Full support |
| NeoForge | 26.1.2 | 25 | Full support; main version |
| NeoForge | 26.2.0 | 25 | Full support; main version |
| Fabric | 26.1.2 | 25 | Some features missing; see the table below |
| Fabric | 26.2 | 25 | Some features missing; see the table below |

Every version needs the matching [Graal](https://www.curseforge.com/minecraft/mc-mods/graal) dependency; see [Quick start](quick-start_us). Downloadable versions are listed on [Releases](https://github.com/NekoJS-Dev/NekoJS/releases).

<a id="wiki-section-3"></a>
## Feature comparison

This table only lists features that differ between platforms or need a note. Common features not listed here — script loading, reload, modules, Node.js built-in modules, code completion, networking and so on — work on every version.

| Feature | NeoForge | Fabric |
|---|---|---|
| Changing recipes (`ServerEvents.recipes`) | ✅ | ✅ |
| Registering items, blocks and entities | ✅ | ⚠️ Partial |
| Changing tags (`ServerEvents.tags`) | ✅ | ❌ |
| Changing existing item and block properties (`ItemEvents.modification`, `BlockEvents.modification`) | ✅ | ✅ |
| Generating data packs and resource packs from scripts (`generateData`, `generateAssets`, `lang`) | ✅ | ❌ |
| Fluids (`Fluid`, `FluidIngredient`) | ✅ | ❌ |
| HUD drawing (`ClientEvents.hud`, `screenRender`) | ✅ | ✅ |
| In-world drawing (`hudRender`, `worldRender`) | ✅ | ❌ |
| Post-processing shaders (`PostEffects`) | ✅ | ❌ |
| JSX screens (`ClientUI`) | ✅ 26.x only | ❌ |
| Villager trades (`tradeDeclaration`, `VillagerTrades`) | ✅ | ❌ |
| JEI integration events | ✅ (requires JEI) | ❌ |
| Registry queries (`Registry`) | ✅ | ❌ Queries return nothing |
| Capabilities (`Capabilities`) | ✅ | ✅ Works differently; see below |
| Error panel (`/nekojs view_all_errors`) | ✅ Graphical | ⚠️ Chat text |
| Dynamic registration (`/nekojs registry`) | ✅ 26.x only, experimental | ❌ |

<a id="wiki-section-4"></a>
## Differences to watch for

<a id="wiki-section-5"></a>
### Enchantments

In Minecraft 26.x, enchantments are defined by data packs and can no longer be registered from scripts. Add enchantments with a data pack (put the files under `nekojs/data/`). NeoForge 1.21.1 can still register enchantments from scripts.

<a id="wiki-section-6"></a>
### Capabilities

Script code looks the same on both platforms, but the underlying systems differ:

- NeoForge uses its native capability system, which works with item, fluid and energy storage from other mods.
- On Fabric, items and fluids use the Fabric Transfer API and work with other Fabric mods. **Energy** uses a NekoJS-specific interface, because Fabric has no common energy standard, so it does not connect directly to other mods' energy systems.
- On Fabric, fluid amounts are measured in droplets (1000 mB = 81000 droplets). The capacity you pass when creating a tank is still in mB.

<a id="wiki-section-7"></a>
### Item pickup event

`ItemEvents.canPickUp` is blocked differently on each platform. On Fabric, `return true` from the callback blocks the pickup. On NeoForge, `return true` has no effect; call `event.setCanPickup(TriState.FALSE)` instead. See [Event reference](event-reference_us#wiki-item-events).

<a id="wiki-section-8"></a>
### Vanilla /reload and F3+T

On NeoForge, vanilla `/reload` also reloads server scripts, and F3+T also reloads client scripts. On Fabric they do not; use `/nekojs reload` and `/nekojs reload client` directly.

<a id="wiki-section-9"></a>
### Other

- NekuJS (the KubeJS compatibility add-on) is installed separately and only supports NeoForge 1.21.1.
- [MixinJS](mixinjs_us) is experimental and off by default. Read its page before using it.

<a id="wiki-section-10"></a>
## How to tell whether a feature is available

1. Check the platform notes in the [Event reference](event-reference_us) and [Global bindings](global-bindings_us).
2. Run `/nekojs probe` in your current game. Only what appears in completion is actually available in that environment.
3. For cross-platform scripts, rely only on features this page marks as supported on both.

> About Cleanroom 1.12.2: it is maintained on a separate branch, and this Wiki does not apply to it.

<a id="wiki-section-11"></a>
## For contributors

Detailed Fabric porting progress and gaps are tracked in `docs/fabric-port-status.md` in the repository.

<!-- wiki-nav -->

---

[Previous: Home](home_us) · [Contents](Home) · [Next: Quick start](quick-start_us)
