# Issue #4 Isolated Runtime Smoke

These are hand-authored fixtures for **NeoForge 26.1.2 / 26.2 and Fabric 26.1.2 / 26.2**. They exercise the actual loader and Minecraft APIs. They are not a test report: **this authoring task did not start Gradle, a server, or a client, and has no runtime PASS evidence**. The parent session owns execution and evidence collection.

## Safety And Deployment

Run only in a **new, private game directory and a newly created disposable world**. The game-directory path must include a path component containing `issue4`, for example `D:\mcmodDemo\issue4-neoforge-game`. The scripts reject other paths and non-26.x versions. The path check cannot prove the world is new; the operator must verify that separately.

Do not deploy these fixtures to an existing user world. The server script force-loads the origin, overwrites blocks in `-8..8, y=63..70, -8..8`, summons entities, teleports players, sets their gamemode to creative, and gives a spawn egg. Plain-block caches are intentionally fixture-only and do not invalidate upon arbitrary block replacement.

1. Build/package the current NekoJS code outside this fixture task. Install only the matching loader, NekoJS, its matching GraalMC hard dependency, and Fabric API on Fabric; MCP is optional for command/control/screenshot evidence. Avoid additional mods that already provide the standard fixture capabilities.
2. Create separate new server and client game directories whose paths each contain `issue4`. Do not reuse a launcher default directory, existing `run` directory, world, or mods folder.
3. Copy this `fixtures/nekojs` directory into the verified `<game-directory>/nekojs` location for both processes before startup. The server needs STARTUP and SERVER scripts. The client needs STARTUP and CLIENT scripts; matching entity registries must exist on both sides. Integrated singleplayer can use one new directory.
4. Use a new disposable world, with cheats or server administration available. Use a normal difficulty that permits the fixture entities and keep the server ticking when empty (`pause-when-empty-seconds=-1` on a dedicated 26.x server). Prefer a flat world for clean screenshots.
5. Start with at least a 640x480 framebuffer and a GUI scale that leaves at least 172x154 GUI pixels. Verify the startup log's actual game directory, loader, version and mod list before connecting.
6. Connect a command-safe offline username such as `Issue4Dev`. The login callback uses the name in parsed native commands and permits only `[A-Za-z0-9_]`. The client must have the same new fixtures and current NekoJS jar.

Do not reload STARTUP while testing: the object/capability registration is permanent. Restart a fresh process for startup changes. Do not use SERVER reload as a runtime acceptance step for these fixtures: `ServerEvents.started` will not fire again, and the temporary world/cache state is intentionally not reload-managed.

## Files And API Checks

- [issue4-startup.js](fixtures/nekojs/startup_scripts/issue4-startup.js): `RegistryEvents.register` creates `nekojs:issue4_mob` with default visible humanoid rendering, `maxHealth(42)` as its only attribute override, native `customClass(0, FloatGoal)`, `lookAt(2, 'minecraft:player', 12)` and spawn egg co-registration. `CapabilityEvents.register` installs standard and custom energy providers for stick, pig and furnace, and a custom grass-block provider. Pig also exposes queried item and fluid handlers.
- [issue4-server.js](fixtures/nekojs/server_scripts/issue4-server.js): native queries, per-owner stability/isolation, context rejection, root/nested transactions, energy/item/fluid committed serialization round trips, world preparation, authoritative entity/player PData and deferred respawn-copy assertions.
- [issue4-client.js](fixtures/nekojs/client_scripts/issue4-client.js): HUD/screen Painter operations, client player/entity mirror reads, read-only mutation rejection, one-time first-frame markers and bounded mirror timeout.

The fixture reads the loader from `Platform.getLoaderId()`, not class-lookup exceptions. The private-directory check lowercases the path and checks its split components; it does not use regular-expression flags. Static Direction constants and the context class are accessed through a local `NativeDirection = Java.type('net.minecraft.core.Direction')` alias. Loader-specific Capability registrations live in ordinary `registerNative(payload)` / `registerFabric(payload)` helpers; the event callback selects the active loader's helper. This is a portable fixture layout, not a fix to production preflight or SAM callback recognition. It preserves all registration calls, actual queries, context rejection and failure checks.

NeoForge queries use native `Capabilities$Energy`, `Capabilities$Item`, `Capabilities$Fluid`, `BlockCapability`, `EntityCapability` and `ItemCapability` objects through `level/entity/stack.getCapability`. Fabric standard queries use `FabricCapabilities.getBlock/getEntity/getItem`, the same facade delegated to by the `Capabilities` Binding; this local native alias keeps a cross-loader fixture from requesting Fabric-only Binding members on NeoForge. Custom queries use actual `BlockApiLookup/EntityApiLookup/ItemApiLookup` objects. Player login and respawn payloads are read through one helper: NeoForge's native payload exposes `entity`, while Fabric's payload exposes `player`. The existing `pdata()` operation remains a method on the entity.

Standard item energy requires a native non-null location context (`ItemAccess.forStack` on NeoForge or `ContainerItemContext.withConstant` on Fabric). A null context is intentionally declined. Sided energy accepts NORTH and rejects SOUTH **and null**. Custom item energy uses a Direction context on both loaders. The fixture's `nekojs:issue4_energy` ID names separate native scope objects with the same compatible energy API.

A Java `WeakHashMap` holds each owner handler. Values are native storage instances constructed **without callbacks capturing the owner**; different stacks/entities/block entities get different instances, repeated queries get the same instance. Plain blocks use a weak level key and a per-position map whose values do not reference the level. This is temporary fixture storage, not automatic world persistence or a recommended general block attachment implementation.

NeoForge opens `Transaction.openRoot()` / `Transaction.open(parent)` and calls native `serialize/deserialize`. Fabric opens `Transaction.openOuter()` / `parent.openNested()` and calls `writeValue/readValue`. Serialization uses `TagValueOutput.createWithContext` and `TagValueInput.create` with the real server registry provider and a checked `ProblemReporter.Collector`. Fabric item operations use actual `ItemVariant.of` and `ContainerItemContext.withConstant`, deliberately exercising Fabric's loaded Mixin path that a bare JVM cannot supply. Fluid factory capacities are mB; operations use mB on NeoForge and native Fabric droplets (`BUCKET/1000`) on Fabric.

`Commands.performPrefixedCommand` returns void. The script validates Brigadier parse results, issues the command, then checks entity/block outcomes on a later tick; issuing a command alone is not counted as world-state success. The command registrations and native method signatures were inspected from the current compiled 26.2 artifact. Preparation uses `forceload`, `fill`, `setblock`, `summon`, `time`, `gamemode`, `tp` and `give`, not guessed command helpers.

## Required Runtime Evidence

Record each loader/version separately, including current jar identity, game directory, world name, script logs and screenshots. NekoJS `console` output normally lands in `logs/nekojs/startup.log`, `server.log` and `client.log`; also retain the ordinary latest log and any NekoJS diagnostics. Search all logs for both `ISSUE4 FAIL` and script-loading/runtime errors. Missing script execution is not a pass.

### Startup And Native Baseline

`ISSUE4 READY startup.*` proves only that declarations were collected, not that queries worked. After roughly 60 server ticks the baseline requires all of these observed markers:

- `ISSUE4 PASS server.safety_and_started`, `server.prepare_world`, `server.discover_world_entities`.
- `ISSUE4 PASS entity.spawn_attributes_goals_egg` (real entity health 42, exactly one native FloatGoal and one look-at goal, registered egg).
- `ISSUE4 PASS capability.standard_block_scope`, `capability.standard_entity_scope`, `capability.custom_block_entity_scope`, `capability.custom_plain_block_scope`, `capability.custom_entity_scope`, `capability.standard_and_custom_item_scope`.
- `ISSUE4 PASS capability.energy_transactions_and_serialization`, `capability.items_transactions_and_serialization`, `capability.fluids_transactions_and_serialization`.
- `ISSUE4 READY server.native_baseline waiting_for_player=true` and server summary `status=READY`, with **no FAIL/error**.

These are executable requirements, not counters copied from an expected report. `passed=` is derived from the set of checks actually completed. An exception changes the summary to FAILED and suppresses later success checks. If a loader or mapping differs from the inspected API, retain the exact error and fix the fixture or production contract; do not add assumptions/skips or synthesize markers.

### PData Tracking, Login, Reconnect And Respawn

The fixture writes entity PData 41 after summoning. At least 120 server ticks after the first login it writes 42 with `putInt`, without `sync()`, so the dirty queue and automatic flush must deliver it. Required client markers are `pdata.player_mirror_42`, `pdata.entity_mirror_42`, `pdata.player_readonly` and `pdata.entity_readonly`. Mutation rejection must contain `NEKO-4013` and leave 42 unchanged. The optional `pdata.entity_initial_tracking_41` marker followed by `pdata.entity_mirror_42` provides stronger evidence that an already-tracking client received the later automatic update. A late connection seeing only 42 proves an initial snapshot, not that transition.

Disconnect and reconnect the same player to the same isolated server. Require server `pdata.player_reconnect_retained`, then client mirror/read-only evidence from the connected client. For world-save/restart evidence, stop cleanly and restart **only this disposable fixture world**; `pdata.player_login_persisted` asserts that persisted 42 existed before the fixture rewrote it. Baseline preparation removes only the four dedicated fixture entity tags, overwrites the private platform and summons fresh fixture entities; player save data is preserved. This permits clean stop/restart tests of player persistence without mistaking old fixture entities for new ones. It does not test entity PData restoration across restart, because those tagged entities are deliberately recreated.

For a real death/respawn acceptance step, after login value 42 is observed, issue the native `kill <username>` command as an administrator and let the client choose Respawn (MCP can click the real button). The fixture does **not** kill players or pretend that a direct data copy equals a respawn. On the actual `PlayerEvents.respawned`, it queues the replacement player and reads PData on the next server tick without writing that value itself. Require `ISSUE4 PASS pdata.player_respawn_copy` and the new client's 42 mirror. Client `pdata.replacement_player_mirror_42` is supplementary evidence only: it also fires after a reconnect/replacement and is not proof of a server respawn by itself.

### Painter And Entity Screenshots

The login callback positions the player at `0.5 64 5.5`, looking towards the fixture humanoid at the origin, and gives the spawn egg. Capture a world screenshot after the chunks/entities render. Require a visible textured humanoid with normal geometry, not merely the absence of a missing-renderer crash. Observe look-at behavior as supplementary evidence; the automatic assertion proves goal installation, not probabilistic activation.

Capture HUD and an open inventory screen separately. Require `ISSUE4 PASS painter.hud.first_frame` and `painter.screen.first_frame`, then inspect pixels:

- HUD rectangle begins at GUI `(16,16)` with width 156 and height 66; the nonzero-origin outline must end at that rectangle's right/bottom edges, not treat width/height as endpoints.
- Screen rectangle begins at `(16,88)` with the same width/height. Reopen/resize the screen and ensure only one overlay is drawn.
- A **complete** stone texture appears in each 24x24 icon, not a wrong crop/UV smear; text and green outline remain visible with no unintended overlap.
- Record actual screenshots and GUI scale. First-frame markers mean native draw calls were issued, not framebuffer pixels validated. `READY_FOR_SCREENSHOT` is not a visual PASS.

## Scope And Limits

This fixture intentionally has no JavaScript-engine test mocks, static-source PASS shortcuts, unconditional success summary, fake test counters, broad skips, production edits or build configuration changes. It covers current 26.x APIs only, not NeoForge 1.21.1's legacy non-transactional interfaces. Each logical side needs its own installed startup declarations; server Java objects are not shared with remote CLIENT scripts.

Serialization checks prove actual in-memory ValueIO round trips of queried capability storage. They do **not** claim automatic owner save attachment, world restart capability restoration, interoperability with every mod's provider ordering, plain-block cache invalidation, garbage-collection timing, cross-generation callback safety, or loader parity beyond the APIs explicitly tested. Full acceptance requires a maintainer's recorded conclusion and the parent session's runtime artifacts. GitHub issue state is unchanged by these fixtures.
