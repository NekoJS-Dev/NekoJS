<!-- wiki-page: script-packs; locale: us -->

> **English** · [中文](script-packs_cn)

<a id="wiki-section-1"></a>
# Versioned script packs

Flat scripts and manifests without `formatVersion` retain recursive discovery, the existing type Context and shared `global`. The following behavior is opt-in through `formatVersion: 1`.

<a id="wiki-section-2"></a>
## Authoring

Use `/nekojs packs create server economy` in game, or select several types with the repository tool:

```powershell
./tools/nekojs-pack.ps1 -Action create -InputPath ./build/author/packs -Id economy -Types server,client
./tools/nekojs-pack.ps1 -Action validate -InputPath ./build/author/packs/economy
```

Templates contain `manifest.json`, each selected type's `main.js` and `pack-api.d.ts`. Existing targets are rejected. Put the directory in `nekojs/packs/`, or in a world's `nekojs_packs/` when that platform mounts WORLD sources.

```json
{
  "formatVersion": 1,
  "id": "shop",
  "version": "1.0.0",
  "entries": { "server": ["main.js"] },
  "dependencies": { "economy": ">=1.0.0 <2.0.0" },
  "capabilities": { "requires": { "economy": ["wallet"] } }
}
```

The machine schema is in the repository at `docs/schema/script-pack-v1.schema.json`. Unknown fields, wrong field types and invalid entries are rejected before activation. Entry keys are `startup/server/client/test`; paths are relative to that type's directory. Only declared entries run independently. Other supported files remain import-only libraries. Physical imports stay inside the pack root; cross-pack calls use declared capabilities.

Pack versions have three numeric parts, optional SemVer prerelease/build identifiers, no numeric leading zeros and at most nine digits per version part. Ranges accept `*`, exact versions, `>= <= > < =` comparators and space-separated conjunctions. Comparators allow one to three numeric parts. There is no npm/Maven solver, `^`, `~`, OR or dependency download. `environment.mods`, `environment.loaders` and `environment.minecraft` add environment conditions. Pack dependencies precede consumers; existing file `priority/after` rules cannot override them. Missing, incompatible, disabled, duplicate or cyclic dependencies block their consumers while unrelated packs continue.

Bare dependencies prefer the same source. WORLD can fall back to GLOBAL/MOD, GLOBAL to MOD, MOD to GLOBAL; GLOBAL/MOD cannot borrow WORLD. Remote dependencies stay in the authorized bucket. Transmitted `GLOBAL:id/WORLD:id` refer to the corresponding remote origin, never local trust. Remote MOD dependencies cannot resolve.

<a id="wiki-section-3"></a>
## Capabilities and ownership

Declare `"capabilities":{"provides":["wallet"]}` on the provider and publish during initialization:

```js
Pack.provide('wallet', request => ({ balance: 42 }));
```

Declare `requires` on the consumer, then call:

```js
const wallet = Pack.require('economy', 'wallet');
console.info(wallet({ player: 'example' }).balance);
```

Calls synchronously accept one JSON value and return JSON data. Functions and host objects cannot cross Contexts. Limits are depth 32, 10000 nodes and 1 Mi characters across names/strings. Sandbox/resource budgets remain in force. `Pack.selector` and `Pack.activation` are read-only properties.

Each versioned pack owns a Context for its type, source and world/connection session. Its private `global` survives ordinary replacement but ends with its source session; `shared` belongs to the runtime root. Handles bind both actual activations and expire after replacement, shutdown, provider failure or session change. Missing providers cause waiting. Failed providers stop consumers and require explicit reload to recover; recovery never overrides administrator disable intent.

<a id="wiki-section-4"></a>
## Operations

- `packs list` shows discovery and saved intent; `packs status <type>` shows actual activations and entry outcomes.
- `packs plan <type>` reads current ordering and blocking reasons without executing scripts.
- `packs enable/disable <selector>` saves local intent and reports persistence failures.
- `packs reload server GLOBAL:economy` replaces the provider and necessary consumers. Unrelated packs retain their activation.
- `nekojs reload <type>` replaces the entire type, including its legacy partition.

Selectors are `GLOBAL:id`, `WORLD:id`, `MOD:id`; versioned remote selectors are `SERVER_CACHE:packs:id` and `SERVER_CACHE:worldpacks:id`. Bare IDs must be unique. MOD and SERVER_CACHE are read-only sources.

Managed state, listeners, timers and domain plans join one preflight/commit. Failed candidates release their resources and preserve healthy active units. Whole-table domains explicitly expand the affected set; client render contributions publish together. Conflicting top-level state writes reject the batch. Nested mutation and arbitrary Java/world/file effects are outside rollback guarantees. Domain publication must not throw after preflight; violations report the responsible domain, whose partial effects remain its responsibility. Post-commit cleanup failures are reported separately from rollback.

World/session teardown and provider failure stop the affected activations, then rebuild whole-table contributions from the surviving domain subscriptions without re-running their entry scripts. Renderers withdraw entries owned by the closing Context directly. If recollection fails, the runtime attempts an empty plan to withdraw that domain and logs `NEKO-1017`; explicit reload is required for recovery. A failed withdrawal logs the outstanding resource recovery separately. Collection callbacks should declare domain content only; arbitrary host effects remain the author's responsibility.

STARTUP changes require restart. Legacy single-file reruns retain their active-Context semantics; versioned files require package reload. Client operations retain existing client/owner-thread restrictions.

A root close requested inside a managed callback immediately blocks new pack callbacks and defers release until the outermost callback returns. Deferred failures log `NEKO-1017`. Closed timer runtimes reject registration and clear their queue, then wait up to 2 seconds for the scheduler to exit. Timeout or interruption reports incomplete cleanup while other owned resources continue to release.

<a id="wiki-section-5"></a>
## Distribution

```powershell
./tools/nekojs-pack.ps1 -Action archive -InputPath ./build/author/packs/economy -OutputPath ./build/economy.nekopack
./tools/nekojs-pack.ps1 -Action install -InputPath ./build/economy.nekopack -OutputPath ./build/installed/packs
./tools/nekojs-pack.ps1 -Action install -InputPath ./build/economy.nekopack -OutputPath ./build/installed/packs -Replace
./tools/nekojs-pack.ps1 -Action jar -InputPath ./build/author/packs/economy -OutputPath ./build/economy.jar -Loader neoforge -Minecraft 26.1.2
```

Archives preserve original manifest/signature bytes, the four script directories, `assets/data`, a sorted inventory and the existing content hash. Local enabled intent is excluded. Fixed timestamps/STORED ZIP entries make builds reproducible. Install uses staging/restoration, replaces the complete content set and preserves existing local enabled intent. Existing targets require `-Replace`; an older selected archive performs rollback. Unsafe paths, links/reparse points, portable path collisions and quantity/size violations are rejected.

JARs are content mods depending on NekoJS. The mod ID equals the pack ID and must be 2–48 lowercase letters/digits/underscores, starting with a letter, without a runtime ID collision. Targets are NeoForge 1.21.1/26.1.2/26.2 and Fabric 26.1.2/26.2; use `26.2` as the Minecraft version. Install a NekoJS build containing this feature and its dependencies. There is no Forge target.

Place the JAR in the required side's `mods/`. Loader-discovered content becomes a MOD source; JS is not compiled into a standalone Java mod. Root `assets/data` uses native loader resource handling. MOD content does not extend the existing GLOBAL/WORLD sync wire and must be deployed on each required side. Automatic directory-pack mounting on Fabric is outside this feature.

In-game author commands are `packs validate <selector>`, `packs archive <selector>` and `packs jar <loader> <minecraft> <selector>`. Exports go to `nekojs/exports/` without overwriting existing outputs. Archive install is available through the repository CLI.

Signing/key pinning and the existing sync wire remain. Unsigned remote content is rejected by default. Explicit `allowUnsigned=true` still requires server trust and produces a distinct unsigned approval without invented key evidence or local fallback. Failed same-bucket candidates restore old content, credentials and healthy activations. Disconnect, server switch and revocation expire old handles.

<!-- wiki-nav -->

---

[Previous: Error and log reference](error-reference_us) · [Contents](Home) · [Next: Recipe system](recipe-system_us)
