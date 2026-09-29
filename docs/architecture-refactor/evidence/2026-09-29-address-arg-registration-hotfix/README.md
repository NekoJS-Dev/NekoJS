# AddressArgument client-sync registration hotfix (2026-09-29, ticket-36 trial follow-up)

CRITICAL regression fix for the merged F1–F4 command pack (commit `150617ba`): the custom
brigadier argument `com.tkisor.nekojs.command.AddressArgument` shipped without argument-type
serialization registration, so **every real client world entry failed** during the
configuration phase while RCON/console dispatch kept working. Found by the 2026-09-29
ticket-36 maintainer trial session. Delivered on branch `hotfix-address-arg`
(worktree `D:/mcmodDemo/NekoJS-mult-taddr`, baseline `mult@ca52f90f`).

## Root cause

`ClientboundCommandsPacket.createEntry` resolves every argument in a server command tree via
`ArgumentTypeInfos.byClass` (throwing `IllegalArgumentException: Unrecognized argument type`
on a miss) and serializes the resolved info as its numeric id from
`BuiltInRegistries.COMMAND_ARGUMENT_TYPE`, which the client looks up in its own registry.
`AddressArgument` (F1, merged in `150617ba`) was used in all three `/nekojs trust` trees but
was in neither map, so `Commands.sendCommands` → `PlayerList.placeNewPlayer` →
`ServerConfigurationPacketListenerImpl` logged "Couldn't place player in world" and the client
was kicked with invalid player data. Full evidence excerpt (main checkout, read-only):
`transcripts/03-regression-evidence-main-checkout.txt`.

Two halves must both hold, with the same info instance:
1. `ArgumentTypeInfos.BY_CLASS` must map the class — the server-side throw in the evidence.
2. The info must be registered in `minecraft:command_argument_type` — the wire id the client
   resolves; `getId` is identity-based, so a second, non-registered instance would serialize
   `-1` and the client would silently drop the node.

## Why the pack's RCON check missed it

RCON dispatch runs commands through `Commands#performPrefixedCommand` — parse and execute
only. `ClientboundCommandsPacket` is built exclusively on the player-sync path
(`sendPlayerPermissionLevel` at join / op-level change), which no console session ever
reaches. The pack's live verification leg was therefore structurally blind to the regression
it introduced: F1's parse fix was confirmed real, but nothing in the session serialized the
tree. (The trial session that found this was a singleplayer client — the same `sendCommands`
path runs for dedicated-server client joins.)

## Fix

| File | Change |
|---|---|
| `src/main/java/com/tkisor/nekojs/command/NekoJSArgumentTypes.java` | **new** (neoforge-guarded, shared by 1.21.1 / 26.1.2 / 26.2.0 — API verified identical in all three patched sources): `DeferredRegister` into `BuiltInRegistries.COMMAND_ARGUMENT_TYPE` under `nekojs:address`; the register supplier routes through `ArgumentTypeInfos.registerByClass(AddressArgument.class, SingletonArgumentInfo.contextFree(AddressArgument::address))` so one instance lands in the registry and the class map (the pairing the NeoForge-patched `registerByClass` javadoc prescribes) |
| `src/main/java/com/tkisor/nekojs/NekoJSMod.java` | mod constructor calls `NekoJSArgumentTypes.register(modEventBus)` (registration moment: mod construction; the supplier executes at `RegisterEvent`, before any server start) |
| `src/fabric/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java` | `onInitialize` calls `ArgumentTypeRegistry.registerArgumentType(nekojs:address, AddressArgument.class, SingletonArgumentInfo.contextFree(...))` — fabric is NOT exempt: the serialization code is vanilla and loader-agnostic (verified against the fabric-command-api-v2 bytecode: `registerArgumentType` puts into the same `ArgumentTypeInfos.BY_CLASS` map via mixin and registers into the same registry). Without it, a fabric server would hit the identical crash on client join |
| `src/test/java/com/tkisor/nekojs/command/AddressArgumentSerializationRegistrationTest.java` | **new** JVM leg, see below |

No behavior/contract changes elsewhere: command trees, parse semantics, trust flow, and the
F1–F4 outputs are untouched — this only makes the existing tree serializable.

## Verification

| Leg | Result |
|---|---|
| `./gradlew.bat :26.1.2:test` | **passed** (new suite ran: 2 tests, 0 skipped, 0 failures) |
| `./gradlew.bat :1.21.1:test` | **passed** (twin node compiles the same shared registration; 2/0/0) |
| `./gradlew.bat :26.2.0:test` | **passed** (second 26.x node fed by the shared files; 2/0/0) |
| `./gradlew.bat :26.1.2-fabric:test` `:26.2.0-fabric:test` | **passed** (fabric entry touched) |
| bounded `:26.1.2:runServer` + RCON | **passed**: `Done (1.906s)`, zero ERROR lines, mod construction + RegisterEvent ran the registration without failure (a mis-wired DeferredRegister aborts loading before `Done`), F1/F3 behavior preserved verbatim through RCON (three address forms + bare root usage), clean `stop`. Transcripts `01`/`02` |

The JVM test suite pins what a bare JVM can see:
- behavioral: `addressArgumentInfo()` runs, then `ArgumentTypeInfos.unpack(AddressArgument.address())`
  resolves — `unpack` is the exact frame the crash stack hit;
- source-trace: the NeoForge entry calls the registration hook; the hook pairs
  `registerByClass` with the `COMMAND_ARGUMENT_TYPE` DeferredRegister on one instance; the
  fabric entrypoint uses fabric's `ArgumentTypeRegistry` (and not the NeoForge hook).

### Verification boundary — handoff to maintainer re-trial (explicit)

The failing path needs a real client to serialize the tree to. No headless route exercises
it: RCON/console never builds `ClientboundCommandsPacket`; the repo has no GameTest classes
and vanilla/NeoForge gametest batches do not run the configuration-phase join path; a real
`runClient` needs an interactive desktop session. The registry-id wire half and the
client-side deserialization are therefore **not machine-verified here**. Handoff: the ticket-36
maintainer trial session re-attempts world entry immediately after landing this hotfix
(singleplayer entry or dedicated-server client join on 26.1.2). Pass criteria: no
"Unrecognized argument type" / "Couldn't place player in world" in the session log, and the
client command tree shows `nekojs trust <address>` with a working address argument. If the
client runs the same NekoJS build, the numeric argument-type id resolves symmetrically on
both sides (standard vanilla constraint; the pack-sync trust flow already requires NekoJS on
the client).

## Not done / gaps

- 1.21.1 needed no `versions/<node>` copy: the shared, preprocessed registration compiles
  verbatim on all three NeoForge nodes (verified by the three green test suites).
- No ticket files were touched; no golden output exists for the command tree (unchanged from
  the F1-F4 pack's assessment).
