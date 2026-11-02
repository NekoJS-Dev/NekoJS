# Fabric PData root mismatch

## Scope and diagnosis

The script entry is `EntityExtension.neko$pdata()`, which uses `EntityPDataStore` with the entity reference. Fabric installs `FabricPDataSync.fullAccess()`. Both reference and entity-ID accessors expect a persistence **container** and select `NekoJSPersistentData` once. Before correction, `NekoEntityPDataMixin` exposed the already-selected payload by extracting the key on load, then wrapped that payload again on save. The bridge selected the payload key a second time.

Ranked hypotheses reported before the fix:

1. **Root/container mismatch:** canonical NeoForge NBT with `mana:303` should read as zero through the real Fabric bridge because the payload key is selected twice.
2. **Entity-ID lookup miss:** less likely for this regression, since the real reference accessor bypasses lookup even before the entity is indexed.
3. **NBT codec or copy loss:** less likely; isolated mixin round-trip coverage does not exercise the bridge disagreement.
4. **Packet revision/client mirror staleness:** less likely; direct server-side script reads fail before packet dispatch.

## Compatibility decision

The Team Lead selected one full `NeoForgeData` container invariant, matching the Fabric bridge and NeoForge persistence contract. No format detector, automatic flattening, migration, new configuration, or deletion is authorized. All loaded container fields, including unrelated mod fields and nested compounds, must survive load/save unchanged unless the caller explicitly changes them.

The canonical shape remains:

```text
NeoForgeData.NekoJSPersistentData.<script payload keys>
```

The historical faulty Fabric writer could emit:

```text
NeoForgeData.NekoJSPersistentData.NekoJSPersistentData.<script payload keys>
```

That historical nested representation round-trips unchanged at the NBT value/type level; binary compression and compound-key ordering are not promised. After correction its script-visible payload retains the nested `NekoJSPersistentData` key; previously flattened field access is not preserved. A sole nested compound is indistinguishable from legitimate user data, so the implementation must not guess. Before any intentional manual migration, stop the server, back up the world/entity data, inspect the exact compound and collisions, and make an explicit maintainer decision. This change does not perform such a migration.

## Verification status

The initial regression is `FabricPDataPersistenceTest#neoForgeSaveIsVisibleToScriptsAndSyncWithoutAnExtraPayloadKey` on both Fabric nodes. It installs the real Fabric access implementation, invokes the script-facing extension, executes production mixin persistence bodies, and captures real synchronization-service packets. Entity/world allocation and network delivery are fixture substitutions; this is not proof of applied Mixin injection or live world persistence.

Coordinated command, executed by the Team Lead only:

```text
./gradlew.bat :26.1.2-fabric:test :26.2.0-fabric:test --tests com.tkisor.nekojs.fabric.FabricPDataPersistenceTest --no-daemon --console=plain
```

### Red evidence (Team Lead execution)

- Initial two-node run: the 26.2 Fabric result reported **1 test, 1 failure, 0 skipped**, with `org.opentest4j.AssertionFailedError: the Fabric bridge must read the loaded payload once ==> expected: <303> but was: <0>` at the original regression line 79. The concurrent 26.1 worker instead hit `Java heap space`; this was not behavior evidence.
- Serial 26.1 retry with `--max-workers=1`: **1 test, 1 failure, 0 skipped**, the same expected-303/actual-0 failure at line 79. No heap configuration change was needed. Its compile task was already up-to-date before the source-edit handoff; this is pre-correction behavioral evidence, not green evidence.
- The regression exercises the entity-reference path, falsifying ID-lookup failure as the cause. It fails before synchronization, excluding client revisions as the cause.

### Implemented correction and coverage

The only production behavior change is in the Fabric mixin: load copies the whole `NeoForgeData` compound, and save copies that same whole compound without adding a second NekoJS wrapper. The existing bridge still selects its key once; no wire key, packet ID, config, or NeoForge source changes. Missing-container reload resets in-memory data, and existing null/empty-root omission is retained.

Both Fabric nodes now have six caller-level regressions: canonical NeoForge read/save/sync, fresh script write/save/reload/dirty flush/client mirror, unrelated-field preservation through write and clear, historical nested round-trip with no heuristic, production player-copy path, and defensive-copy/missing-container lifecycle boundaries. The seven existing isolated save-shape cases retain their canonical wire assertions and now seed/assert the full-container duck root.

Exact focused green command executed by the Team Lead (both node tasks serialized by one worker):

```text
./gradlew.bat :26.1.2-fabric:test --tests com.tkisor.nekojs.fabric.FabricPDataPersistenceTest --tests com.tkisor.nekojs.fabric.mixin.NekoEntityPDataSaveShapeTest :26.2.0-fabric:test --tests com.tkisor.nekojs.fabric.FabricPDataPersistenceTest --tests com.tkisor.nekojs.fabric.mixin.NekoEntityPDataSaveShapeTest -Dorg.gradle.java.home="C:/Program Files/Zulu/zulu-25" --max-workers=1 --console=plain
```

### Green evidence (Team Lead execution)

The Team Lead reported final job `bash-1c56ddf3805640d6` exited **0**, with `BUILD SUCCESSFUL` in **1 minute**, including both nodes' actual compilation and focused suites. The implementer independently read all four resulting XML suite headers:

| Fabric node | Caller persistence suite | Isolated save-shape suite | Failures / errors / skipped |
| --- | --- | --- | --- |
| 26.1.2 | 6 passed | 7 passed | 0 / 0 / 0 |
| 26.2.0 | 6 passed | 7 passed | 0 / 0 / 0 |

**26 tests passed in total; none failed, errored, or skipped.** The 26.1.2 XML suite timestamps are `2026-10-31T18:06:57.194Z` and `2026-10-31T18:07:09.409Z`; the 26.2.0 timestamps are `2026-10-31T18:07:26.126Z` and `2026-10-31T18:07:38.143Z`. These fresh caller suites include the original expected-303 regression, now passing on both nodes.

The implementer did not run Gradle/Stonecutter. Focused source/diff review and `git diff --check` passed. Full node gates, runtime Mixin application, real world save/reload, and client acceptance remain **not verified**; focused green is not a substitute for those gates.

## Source and test evidence

- [Script-facing entity PData](../../src/main/java/com/tkisor/nekojs/api/inject/EntityExtension.java#L67-L76) and [entity-reference store entry](../../src/main/java/com/tkisor/nekojs/api/inject/EntityPDataStore.java#L89-L97).
- [Fabric container access, including both reference and ID paths](../../src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java#L73-L113).
- [Corrected container persistence](../../src/fabric/java/com/tkisor/nekojs/fabric/mixin/NekoEntityPDataMixin.java#L39-L64).
- [Production copy and owner dispatch](../../src/main/java/com/tkisor/nekojs/wrapper/pdata/PDataSyncService.java#L64-L79) and [sync snapshot payload selection](../../src/main/java/com/tkisor/nekojs/wrapper/pdata/PDataSyncService.java#L155-L166).
- [26.1.2 Fabric caller regressions](../../versions/26.1.2-fabric/src/test/java/com/tkisor/nekojs/fabric/FabricPDataPersistenceTest.java#L71-L195) and [26.2 Fabric caller regressions](../../versions/26.2.0-fabric/src/test/java/com/tkisor/nekojs/fabric/FabricPDataPersistenceTest.java#L71-L195).
- [26.1.2 isolated persistence fixtures](../../versions/26.1.2-fabric/src/test/java/com/tkisor/nekojs/fabric/mixin/NekoEntityPDataSaveShapeTest.java) and [26.2 isolated persistence fixtures](../../versions/26.2.0-fabric/src/test/java/com/tkisor/nekojs/fabric/mixin/NekoEntityPDataSaveShapeTest.java).
