# Issue #4 Implementation And Verification

Source: https://github.com/NekoJS-Dev/NekoJS/issues/4 (Painter, automatically synchronized Persistent Data, native entities/goals, and PowerfulJS-style capability integration).

The maintainer authorized implementation and subagents. Existing user changes, including the wiki's canonical-page migration, are preserved. No commit, branch, GitHub comment, or issue-state change was made. Maintainer acceptance remains a human decision, not an automatically supplied conclusion.

## Implemented Contracts

- Painter: chainable native GUI primitives, gradients, text/measurement, transforms, clipping, items, corrected nonzero-origin outline geometry and normalized texture UVs. Existing `texture` overloads keep the legacy 256-pixel crop contract; explicit source dimensions and `textureFull` provide intentional sampling/scaling. Fabric HUD and screen extraction are real native callbacks, deduplicated across screen initialization and replacement events.
- Persistent Data: server-authoritative entity/player data, automatic dirty flush, login/tracking snapshots, stop-tracking/removal tombstones, clone/respawn copying, dimension/world cleanup, owner-thread dispatch and defensive client mirrors. Client writes explicitly fail. ItemStack data uses vanilla `CUSTOM_DATA` component persistence and synchronization. Save keys, payload identifiers, packet field order and codec remain unchanged.
- Entity/Goal: default visible textured humanoid, configurable renderer/texture/shadow Bean properties, public concrete native entity constructors/factories, native attribute baselines with only explicit overrides, required Mob attributes validated before registration, builtin goal configuration and native custom goal factories/classes. Native factories must not retain a guest Context; unsupported guest factories fail explicitly. Goal targets respect namespaces; join deduplication uses weak identity keys and immutable published plans. Fabric drains newly produced registries and puts ITEM after entity/block producers, preserving spawn-egg and BlockItem co-registration.
- Capability: standard and custom native block, block-entity, entity and item providers. Context is preserved; `null` declines a query; incompatible returns, bad targets, duplicates and failed commits remain failures. Startup callbacks use strict collection dispatch. Providers return stable owner-owned storage rather than fresh or globally shared storage. NeoForge 26.x uses native transactional handlers; 1.21.1 retains native simulation semantics; Fabric uses native item/fluid Transfer lookups and a NekoJS typed energy API. Fabric storage rejects invalid/partial saved values atomically and disallows saving tentative transaction contents.
- Integration fixes discovered by real execution: official NeoForge JarJar ICU dependency plus artifact verification closes a dedicated-server startup failure; bounded expression-arrow parsing and real SAM parameter derivation close legitimate capability callback preflight errors. Fabric client-only model fallback supplies the vanilla egg for registered spawn eggs without an item definition, preserving user definitions and other plugins' visible models.

## Build Evidence

The final check sequence includes these successful commands (Windows):

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --continue --console=plain
./gradlew.bat :26.1.2-fabric:build :26.2.0-fabric:build --continue --console=plain
./gradlew.bat guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest --console=plain
npm run test:probe-types
python tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json
git diff --check
```

Common isolation and addon dependency checks pass. Each node's build includes ordinary tests and independent platform/spec/event/declaration gates. `verifyNeoForgeRuntimeArtifact` checks nested ICU metadata, version, classes, locale data and module identity; Fabric artifacts reject NeoForge leakage. Compiler regression tests first reproduced the valid three-argument callback being parsed as six arguments, then passed after the parser/SAM fixes; negative unknown-member and wrong-arity assertions remain enabled.

| Node | Ordinary Tests | Passed | Skipped | Failed |
|---|---:|---:|---:|---:|
| NeoForge 1.21.1 | 417 | 386 | 31 | 0 |
| NeoForge 26.1.2 | 637 | 556 | 81 | 0 |
| NeoForge 26.2.0 | 637 | 556 | 81 | 0 |
| Fabric 26.1.2 | 371 | 334 | 37 | 0 |
| Fabric 26.2.0 | 371 | 334 | 37 | 0 |

Skipped cases require loader-owned components, mutable registries or live world fixtures; they are not counted as passes. The vanilla probe now distinguishes usable ordinary item components from registry metadata alone. Native lookup, transaction, strict decoding and resource-manager tests run where their actual prerequisites are available; runtime evidence below covers the unavailable bare-JVM paths.

## Generated Declarations

Production event generation was explicitly run using `-Dnekojs.golden.regenerate=true`, followed by ordinary read-only platform gates and builds. Builder goldens were copied from the actual `RegistryBuilderTsRenderer` report output after reviewing the diff, not hand-authored.

Intentional changes: Fabric CapabilityEvents and Painter payloads; entity constructor/factory, full attribute baseline, renderer, texture and shadow configuration; native/custom provider signatures; ItemStack PData and native custom Goal-class members. Existing unrelated contracts are retained. Transitive imports are generator output rather than an independently promised loader API. Regeneration is not a test pass; the subsequent read-only comparisons are the verification evidence.

## Live Runtime Evidence

Reproducible fixtures and safety/acceptance instructions are in [bench/issue4/README.md](../bench/issue4/README.md). Tests ran only in new private directories under `build/issue4-*`, with localhost-only servers and disposable worlds. The original failures and successful reruns are retained in `build/issue4-evidence/`.

Actual environment: Minecraft 26.2, Zulu Java 25.0.3, GraalMC 25.1.3.7; NeoForge 26.2.0.75 and Fabric Loader 0.19.5 / Fabric API 0.159.0+26.2. These loader patch versions differ from the build nodes' pinned minimum-compatible versions; the exact loaded versions were read from runtime logs.

Both loaders passed real native checks for entity health 42, installed native FloatGoal/look-at Goal, spawn-egg registration, and an explicitly configured native Zombie constructor/type identity with max health 44 while retaining its native speed and armor; block/block-entity/entity/item capability queries, stable same-owner handlers, distinct owners, sided/null rejection; root and nested transaction abort/commit; and energy/item/fluid ValueIO serialization round trips. Both also passed real native ItemStack codec copying/round-trip/clear isolation checks. Fabric tests execute actual ItemVariant/FluidVariant and container-context Mixin paths, not bare-JVM replacements.

Both remote clients observed initial entity PData 41 followed by an automatic update to 42 without `sync()`. Player and entity mirror mutations failed with `NEKO-4013` and left 42 unchanged. Both loaders passed real death/Respawn-button lifecycle checks and fresh-client mirror checks, then real disconnect/reconnect retention. NeoForge also passed clean server-stop/restart persisted-player checks. Fabric's clean server restart passed `pdata.player_login_persisted`; its final run additionally passed native ItemStack codec copying/round-trip/clear isolation and received item data 42 through vanilla inventory component synchronization.

Framebuffer evidence was independently inspected with an image-capable route, not inferred from draw-call logs. Captured size is 854x480, GUI scale approximately 2. NeoForge world and inventory screenshots show a visible six-part textured humanoid, correctly framed HUD/SCREEN rectangles, complete stone texture UVs and readable data. Fabric screenshots initially exposed a missing held spawn-egg model, triggering the client-only fallback fix and dedicated native resource override tests. Final Fabric world and inventory screenshots show a normal held egg and inventory icons with the purple/black missing-model region removed; the humanoid, HUD/SCREEN geometry, texture UVs and data remain visible. Earlier missing-model screenshots are retained only as red regression evidence.

## Boundaries And Acceptance

- Full five-node builds and tests are verified; live remote-client/server smoke covers both 26.2 loaders, not separate live 1.21.1 and 26.1.2 clients. No empty GameTest suite is claimed as evidence.
- Painter callbacks own their native graphics for that frame only. Fabric's identified `hudRender` / `worldRender` registration APIs, broader datagen, and unrelated UI features are outside this Issue #4 implementation.
- Capabilities do not automatically serialize a handler onto arbitrary owners; owners keep, serialize and synchronize their storage using native interfaces. Runtime checks prove actual handler serialization, not automatic capability attachment persistence across restart. Third-party provider priority/interoperability is not exhaustively verified. Fabric's energy interface is not another mod's standard energy API.
- Custom native entity/Goal classes and factories are supported; guest Context-bound factories are explicitly rejected, rather than installed as permanently stale callbacks.
- Malformed user item-model definitions retain native errors; native resource-manager tests cover user priority and add/remove recomputation. An interactive custom-resource-pack reload scenario is not claimed as completed.
- GitHub issues remain open. Human review/merge acceptance, including intentional generated-contract differences, is not inferred from automated evidence.
