# Early dynamic registry synchronization

Source: `ec7fe6848afc1970661ee1f38a7c7c6d4575530c`. This closes the reproduced initial-join ordering defect on the installed NeoForge26.2 node; it is not overall ticket15/16/37 acceptance or a release authorization.

## Change and contracts

Previously, the server activated three dynamic IDs before login, but sent catch-up only from PlayerLoggedIn through a play-only payload. NeoForge's earlier frozen-registry synchronization disconnected the client with those unknown IDs. The same existing payload now supports CONFIGURATION and PLAY. An early task is inserted before NeoForge SyncRegistries, using the existing coordinator for catch-up and live transactions. Configuration participants remain enrolled until play handoff. Accepted STATE_SYNC ACK follows client activation; PREPARE ACK alone cannot release the task, which waits for a successful COMMIT activation report. Rejection, timeout, disconnect and owner close have explicit cleanup.

No payload ID, JSON schema, golden wire bytes, legacy CODEC field, script-facing method or registry type was removed. COMMON_CODEC is additive and needs no registry access. Configuration phase support requires a matching client/server build; old play-only registration does not provide that phase capability. Modern NeoForge is the affected platform; existing1.21.1 and Fabric boundaries are retained. The transport belongs to the existing engine, released on server stop, rebind and Runtime Root domain close. Ordinary script reload does not close it or rebuild Plugin Runtime. No HostAccess/ClassFilter/path permission was relaxed.

## Exact installed feedback

Installed official26.2 JAR SHA256: `EC3263CC5A4AB568D4CCD5E2D6F71544F3C766CAC8AF439378270F5B4931B7F7`. Server and all three clients used these bytes, NeoForge26.2.0.75 and JDK25.0.2, with identical static STARTUP fixtures. Clients had no dynamic-ID pre-seeding. Profiles, worlds and credentials remained under the owned build directory; no user's game/account profile was copied or changed.

| Window | Observed result |
|---|---|
| Original ClientA join, resumed log inspection | Configuration catch-up completed at dynamic generation2; actual login succeeded. The user then quit that client. |
| A+B simultaneous sessions | Both initially synchronized generation2. Identical reload generations3/4 each received two real ACKs and successful activation reports. |
| Three newly added IDs | Dynamic generation5 committed Item/SoundEvent/MobEffect additions, with two ACKs and two successful activation reports. Actual give to A and effect application to B succeeded. |
| Fresh ClientC late join | Configuration catch-up generation5 completed; the client read back all six dynamic IDs before any rejection setup. |
| Real client rejection | After host editing only C's owned dynamicRegistry gate, its existing workspace setup reloaded that config. Generation6 received a real negative ACK and aborted. Activated generation stayed5, the new abort_item was absent on the server, and the previous MobEffect still applied. |
| Recovery | C was kicked; generation7 committed with two ACKs/two successful activation reports. abort_item was now present and actually given to B. |
| Cleanup | Server RCON stop saved all dimensions and closed the loader; process/ports disappeared. Its exit code was not directly observed. GUI failure required exact PID/path-checked force-stop of the owned clients; do not describe those as normal client exits. |

The owned observer uses existing host facade observations from a SERVER tick; it adds no production binding or second runtime owner. `runtime-verdict.json` is checked against complete logs, actual phase/outcome records and registry readbacks. Script generation in RCON responses and dynamic transaction generation are distinct and must not be conflated.

## Verification and retained failures

- Phase registration RED→GREEN on both modern NeoForge nodes, real node facade and both protocol phases.
- Legacy/configuration codec golden bytes match, including decoding without RegistryAccess.
- Owner release RED→GREEN for server stop/rebind and the root closeable-domain contract.
- Configuration participant coverage includes empty state, real-reply release, transaction joins, old replies/queued batches, failure/abort, disconnect, timeout and close. A failing negative activation-report case was corrected before the full build.
- First main-workspace `:common:check`/five-node build/guardLint passed in1m48s,101tasks. CI gates and all five source-root checks passed. Its later report snapshot was overwritten by unrelated focused work; the partial525XML/2697test snapshot is explicitly not a complete matrix.
- First isolated repeat failed before execution because the benchmark Gradle home lacked offline26.2 dependencies. Retrying with the normal cache exposed an existing source-trace path bug: Ticket24GameplayEventPhaseTraceTest excludes every absolute path containing `build`, including that checkout. All five nodes failed that one test. The failures are retained; no exclusion/assertion was weakened. A clean managed worktree outside `build` is used for the final repeat.
- The initial `/nekojs test` attempt reported TEST scripts not configured. It proves no TEST execution; acceptance then used actual SERVER/CLIENT events and ordinary RCON commands.
- The observer's array-literal `.includes` produced a separate existing binding-preflight diagnostic. Its fixture uses scalar comparisons now. The client's attempted engine.toml write was correctly rejected by node:fs; the corrected test uses a host-owned config edit and a normal signal file. Both failed harness attempts remain in raw logs.

Final managed matrix: PASS in1m36s,103tasks (81executed,22from cache),789ordinaryXML/4587tests/271skipped/0failures/errors. The independently rebuilt26.2 artifact is byte-identical to the installed JAR. [manifest.json](manifest.json) binds all81 archived entries and the [complete evidence ZIP](configuration-sync-evidence.zip), SHA256 `28eb92fd0d0d3ba5d7dbd04ad2b7fed79f4ffe52dc2b3d968e68c1a7820e27d4`. The two-axis review is in [REVIEW.md](REVIEW.md).

## Remaining gaps

Real configuration joining during an already in-flight transaction and a disconnect specifically before its ACK still have test-double coverage only. The successful real rejection above happened after play handoff. fireResistant datapack-tag lookup, other installed node JARs, visual/first-frame acceptance and final maintainer conclusions remain open. Computer Use returned an unrelated image for the Minecraft window and failed activation; a fresh-object retry did not recover, and no further GUI input was issued. No screenshot from that attempt is presented as game evidence.

Full strict TS/Pyright retain their earlier failures. Current-source formal startup/reload performance is a separate pending measurement;4431's startup PASS and reload FAIL are historical evidence. Scheduled automatic continuation remains paused. Public deletion, final release and rollback/cutover authorization retain their existing exact boundaries.
