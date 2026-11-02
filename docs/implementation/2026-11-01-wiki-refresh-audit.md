# Whole-wiki source audit — 2026-11-01

## Scope and status

Completed a bounded source audit of all 27 existing bilingual topics and added three focused pairs, for 30 topics / 60 canonical pages. This is not a certification that every example executes on every node. The user specifically requested stale/missing material, hot reload and object-shaped APIs influenced by Katton. This is a documentation refresh, not an authorized breaking Script API migration. No production API, version, golden, commit or push is changed by this audit. Preserve pre-existing worktree changes.

## Ownership and evidence

- Reload/state/modules/packs/architecture: [reload audit](<2026-11-01-wiki-reload-audit.md>).
- Events/Bindings/adapters/recipes/registration/plugins: [API audit](<2026-11-01-wiki-api-audit.md>).
- Platform/languages/UI/Probe/build/entry pages and client drawing: [platform audit](<2026-11-01-wiki-platform-audit.md>).
- Lead: AI support, error reference, new script-interface conventions, canonical navigation and final validation.
- New focused guides: [Script API style](<../../wiki/script-api-style_us.md>), [client rendering](<../../wiki/client-rendering_us.md>) and [data/state](<../../wiki/data-and-state_us.md>), each with a Chinese pair. The data/state audit also checked local source line ranges, entity authority, component ownership and transport limits.

## Confirmed design distinctions

1. Event namespaces, callback-last builders and Bean properties are NekoJS conventions. An options Map used to define behavior is a review candidate, not grounds to silently teach an unimplemented overload.
2. Recipe filters, recipe/resource JSON, JSX props and MCP request bodies are legitimate structured data, not automatically a convention violation.
3. ClientEvents.hudRender/worldRender currently support an optional options object; ClientEvents.postEffects exposes event.register(id, Map). These are callable current interfaces; their possible callback-builder migration must cover runtime, Probe, validation, failure retention and supported nodes together.
4. Katton provenance is asserted only where source comments identify it. That evidence does not establish the origin of every object-shaped parameter.
5. Filesystem watching must not be inferred from manual reload or dependency invalidation. Source/fixture evidence is not live-world acceptance.

## Lead review

AI support was checked against AuthoringConfig, AuthoringMcp and live host source: the .github/neko_ai locator/config paths, opt-in MCP mutation policy, SERVER/CLIENT/TEST owner availability and offline read-only behavior remain current. Added pointers to the conventions and client-rendering topics without changing the bundled English material.

Error reference review: observed production common error codes are represented; searches for NEKO-1007, NEKO-1011/1012 and NEKO-3001–3004 found no main common/shared call sites. This is a lookup for published diagnostics, not a promise that every platform/mod exception receives a NekoJS code.

## Documentation gaps addressed and source work not implied

- Reload documentation distinguishes full candidate replacement from legacy file/dependency reruns in the active Context, manual reload from filesystem watching, and script activation from resource/data-pack reload. No auto-watch setting was found in the inspected runtime; this is bounded evidence for this checkout, not a statement about external plugins.
- Filesystem documentation now separates reads, default writes under `nekojs/`, the opt-in game-directory write expansion and always-protected configuration. Watchdog budgets and signed packs are not a general untrusted-code guarantee.
- A dedicated data/state topic separates process memory, save persistence, client mirrors, JSON files and explicit network messages. Structured payloads remain data, not proposed configuration-builder migrations.
- The renderer/post-effect API still accepts configuration objects. A future builder migration is source work: define the actual callback contract and compatibility policy, then update runtime, Probe, validation/failure retention, node implementations and examples together. This audit deliberately does not fabricate such an overload.
- NeoForge 26.2 world-render callbacks are present, but the existing `line`/`box` helpers skip drawing and warn once. Documentation exposes the limitation; source implementation and visual validation remain separate work.
- Fabric entity PData has an unresolved payload-root mismatch between the [bridge](<../../src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java#L98-L109>) and the [save mixin](<../../src/fabric/java/com/tkisor/nekojs/fabric/mixin/NekoEntityPDataMixin.java#L53-L67>). Direct-mixin fixture evidence does not prove script-visible persistence or cross-loader migration. Both the focused guide and platform/global-binding summaries now qualify this. No runtime fix is claimed.
- Fabric registered renderers/PostEffects/retained JSX and Registry query backends still have documented gaps. Python remains a JS-backed subset; Probe file output can be partial after an I/O failure even though publication retains the last successful AI snapshot.
- Dynamic registration already has event/builder entry points. `world` and `reloadable` currently share stale-retention semantics; do not infer automatic world-leave unregistration from the mode name.
- Katton attribution remains mechanism-specific where source comments identify it; no blanket provenance claim is made for every Map, JSON, JSX or network argument.

## Verification and remaining work

| Check | Result |
|---|---|
| `node scripts/wiki-docs.mjs sync` and `stamp --all` | Passed; canonical navigation/anchors/digests regenerated, all 30 topics remain `draft` / human review `pending` |
| `node scripts/wiki-docs.mjs check --strict` | Passed: 30 bilingual topics, 0 problems |
| `node --test scripts/wiki-docs.test.mjs` | Passed: 8 tests, 0 failures |
| Temporary TypeScript-parser audit under `common/build/` | Passed: 60 canonical pages, 280 JS/TS/JSX/TSX code fences, 0 syntax failures; no execution or type-checking claim |
| Temporary integrated audit under `common/build/` | Passed: repeated sync/stamp byte-idempotent; numeric anchor sequences retained in all 52 tracked baseline pages; local evidence targets and source line ranges resolve |
| `git diff --check` | Passed across the shared worktree; unrelated line-ending normalization warnings remain informational |

The initial baseline-anchor comparison exposed two new command subsections and an already non-numbered Issue #4 section shifting later anchors after canonical generation. Those labels are now bold rather than new numbered headings, preserving the existing numeric destinations without hand-editing generated anchors. The named Issue #4 anchor remains available. The local-client browse exception was also made consistent with the permissions section.

Not run in this documentation round: Gradle/common gates, node compiles, Minecraft, shader/visual acceptance, multiplayer or persistence integration, release-artifact installation, external URL fetching and maintainer acceptance. Earlier implementation-round builds are not presented as fresh wiki-round results. No outstanding documentation gate failures remain.

The API design debt and platform/source gaps above remain implementation work, not completed fixes. Existing production contracts, unrelated worktree edits, generated runtime sources and bundled AI resources were preserved. No version bump, commit or push. Human acceptance remains pending.
