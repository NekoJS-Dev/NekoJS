# Configuration synchronization investigation

Active work note, not accepted. The real initial join failed on source4431. A configuration/play channel, early-task hook, engine-owned configuration participants and owner release are now being implemented in the working tree; no successful real join has been recorded for this repair yet.

## Verified ordering

Exact installed NeoForge26.2.0.75 bytecode is retained locally in `build/ticket37-4431-multiplayer/configuration-order-bytecode.txt` and `SyncRegistries-bytecode.txt`. `ConfigurationInitialization.configureEarlyTasks` queues SyncRegistries before the ordinary RegisterConfigurationTasksEvent path. SyncRegistries sends FrozenRegistrySyncStart, generated frozen registry packets and completion. Therefore adding only another ordinary configuration task, even with high event priority, would be too late. Inspect the26.1.2 implementation and supported build versions before selecting a hook; do not infer ordering from the existing PackSyncConfigurationTask prose.

Current dynamic channel registration is playBidirectional in Nf261/Nf262PlatformCompat. DynamicRegistrySyncWire.onPlayerLoggedIn asks the coordinator for STATE_SYNC after the client has already failed frozen-registry validation. DynamicRegistryClientParticipant.onStateSync validates and immediately activates the already-committed server state; its existing implementation can be reused. The client handler currently replies through the play-only ClientPacketDistributor; a configuration receive path needs an explicit connection/context reply function instead.

## Proposed bounded correction to validate

- Queue an early dynamic synchronization task before NeoForge's frozen-registry synchronization. A small evaluated node hook may delegate to shared Minecraft-facing logic; verify actual supported signatures. Preserve the canonical resource/generator workflow and extend mixin integrity/target tests if a mixin is needed.
- Extend the existing single payload type to both phases with the same codec and channel family; no duplicate registration or fabricated ACK. Verify the exact PayloadRegistrar/IPayloadContext API in installed/build libraries before implementing.
- Configuration connections must participate in the existing coordinator's prepare/ACK/commit/abort protocol while they wait. A one-off snapshot alone can race a concurrent live commit before frozen-registry synchronization. Keep them in the participant set until real play handoff, send STATE_SYNC or in-flight PREPARE through the actual connection, and release the configuration task only after accepted catch-up or committed activation. Rejection/disconnect/timeout must have an explicit cleanup outcome.
- Own pending configuration connection/task records inside the existing engine's transport, with clear server-stop/root-close release and tick-driven disconnection cleanup. Do not introduce a process-wide connection map or second runtime owner. Inspect DynamicRegistryFacadeRuntime.clearActivationEngine/abortInFlight and root domain-collector close first; current facade runtime has a pre-existing process-level singleton, so new resources require a real release boundary rather than copying that pattern.
- Keep owner-thread discipline for server coordinator and client activation. A context-backed reply function can serve both actual phases without changing the common participant's wire semantics. Disabled gates and clients without configuration support must fail explicitly through localization and stable diagnostic codes.

## Required feedback loop

Pin RED for early-task/channel availability and lifecycle/ACK handoff, then GREEN with common isolation and relevant node tests. Cover an empty server, existing activated IDs on initial connection, two real clients, live PREPARE/ACK/COMMIT, a late join/catch-up, rejection/abort/disconnect and configuration concurrent with an in-flight transaction. Rebuild/hash/install exact new JARs and re-run the initially failing real connection; do not pre-seed dynamic IDs on the client. Static STARTUP fixtures remain identical on both ends.

Current five-node and installed/type/performance results certify4431 only. After any runtime/network change, perform the relevant full matrix and exact-source formal performance again. Preserve all previous failed captures and actual subtype/gate boundaries. Final maintenance/public deletion/release authorization remains separate.

## Working-tree feedback

- Phase registration RED: the actual node facade registered the dynamic payload only for PLAY. GREEN on both modern NeoForge nodes now registers the same channel for CONFIGURATION and PLAY. Legacy CODEC and wire golden bytes are retained; a FriendlyByteBuf codec works without registry access.
- Connection owner release RED: server stop and rebind did not close the previous transport, and the facade was not closeable for root domain release. GREEN covers release on clear/rebind and the root close contract.
- Configuration participant tests cover empty state, catch-up, mid-transaction joins, superseding replies/queued batches, reject/abort, disconnect, timeout and owner close. One real failing assertion exposed a negative activation report hidden by the coordinator's catch-up retry; the repair now disconnects that configuration client explicitly. Full matrix is running.
- Exact NeoForge26.1.2.71 and26.2.0.57 build sources share configureEarlyTasks(listener, tasks); the installed26.2.0.75 bytecode has the same ordering. Minecraft exposes the configuration identity through public ServerCommonPacketListenerImpl.getOwner(), so no profile accessor mixin is needed.
- Standards and Spec review agents are read-only on the same diff; neither owns source edits. The main agent owns all source, tests, evidence and installation changes. Initial4431 failures and unrelated files remain preserved.

## 2026-10-09 active acceptance

Source ec7fe684 is committed locally. Official installed26.2 JAR SHA256 is EC3263CC5A4AB568D4CCD5E2D6F71544F3C766CAC8AF439378270F5B4931B7F7. The previously launched ClientA actually completed configuration and joined before the user quit it; raw initial logs were copied before relaunch. A and B now both completed initial configuration, real PREPARE/ACK/COMMIT and successful activation reports for identical reloads and three new Item/SoundEvent/MobEffect IDs. Fresh ClientC caught up all six IDs and read them back locally without client pre-seeding.

The test harness's first protected-engine-file write was correctly denied by node:fs. It is retained as a failed harness attempt. The corrected fixture reads an ordinary signal file and invokes existing workspace setup after the host edits only the owned dynamicRegistry feature gate; all sandbox flags remain false. A script Array.includes literal also produced a separate binding-preflight diagnostic; the observer now uses simple scalar comparisons. Neither production gate was weakened.

Computer Use returned an unrelated image for the Minecraft window and failed activation; one fresh-object retry still returned the wrong image. No GUI input was issued after that failure. Visual/first-frame acceptance remains open. ClientC was kicked, then its exact launched PID/path was checked and force-stopped for fixture correction; do not describe this as normal client shutdown.

The main checkout's ordinary XML was overwritten by other focused work after the earlier full matrix passed. That partial snapshot (525XML/2697tests) is not the full repair matrix. A fresh full matrix is running in the already-owned, clean detached worktree pinned to ec7fe684, with no edits to other contributors' files. Further real rejection/abort, fresh performance, artifact/evidence archival and push remain in progress. Scheduled continuation stays paused.

Final synchronization evidence is now archived in [ticket37-configuration-sync](../ticket37-configuration-sync/README.md):789XML/4587tests/271skipped/0failures in a clean managed worktree, byte-identical independently rebuilt official JAR, real generation6 rejection/abort retaining generation5, and generation7 two-client recovery. The intermediate owned-under-build repeat hit an existing source-trace absolute-path bug; its five failures and the earlier missing offline dependencies are retained. Real server shutdown completed through RCON; exact owned clients were force-stopped after GUI failure. Current-source formal performance is running separately. No overall/human acceptance is inferred.
