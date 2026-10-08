# Configuration synchronization investigation

Active work note, not implemented or accepted. The real initial join failed on source4431; no production source has changed since that capture.

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
