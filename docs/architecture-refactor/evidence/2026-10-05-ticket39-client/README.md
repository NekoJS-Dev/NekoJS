# Ticket 39 real-client acceptance readiness

Status: **NeoForge 26.2 visibility verified; cross-version acceptance pending**. The isolated dedicated server and separate client use matching Issue4 startup registry scripts. The server active plan reports diamond `16/EPIC` and lamp `7`; the client receives generation-scoped declarations over the existing payload channel and reports the same values. Empty-plan restore, fresh server-issued stacks, relog catch-up, and a subsequent active reload pass. Existing ItemStack instances are not retroactively rewritten; newly created or relogged stacks match the server. No maintainer sign-off is provided for AC14, and 1.21.1 smoke remains blocked by the MCP installer EOF.

## Inputs and observable values

Authoritative inputs: [ticket 39](../../implementation-tickets/39-item-block-modification.md), [baseline REPORT](../../baseline/2026-09-16-item-block-modification/REPORT.md), [MIGRATION](../../baseline/2026-09-16-item-block-modification/MIGRATION.md) and its examples. Prepared fixtures use `ItemEvents.modification`, `BlockEvents.modification`, `ServerEvents.tickPost`, `ClientEvents.playerTickPost`, `Item.of`, `Block.id` and JavaBean properties on published Minecraft payloads. There is no `Java.type`, internal runtime access or new sync path.

| Reading | Vanilla baseline in an otherwise unmodified test process | Active plan | Restoration expectation |
|---|---|---|---|
| fresh diamond stack maxStackSize | 64 | 16 | captured baseline, normally 64 |
| fresh diamond rarity | COMMON (`String(rarity)` enum representation) | EPIC | captured baseline, normally COMMON |
| fresh stick stack maxStackSize | 64 | 16 (explicit setter parity) | captured baseline, normally 64 |
| fresh stick rarity | COMMON | EPIC | captured baseline, normally COMMON |
| default unlit redstone lamp state lightEmission | 0 | 7 | 0; the lit-state baseline is separately 15 |
| existing held diamond stack | observed separately | not assumed to change | not assumed to restore until recreated |

Capture the actual baseline; other mods or declarations may change it. Do not normalize an unexpected baseline to vanilla. The fixture has no automatic `/give`, `/setblock`, `/clear`, time/weather change or data writes. It prints at most once per distinct reading, sampled once per 20 ticks, and waits for server/player ticks rather than reading item components during client construction.

## Exact deployment

First confirm the live launch log's game directory and jar hash. Current documented CLI directory is `%APPDATA%/.minecraft/mcp_launcher/game`, but the actual current launch log wins. Keep the same final NekoJS jar for the entire run; no development Gradle run is required for these scripts.

1. Confirm that `nekojs/server_scripts/src/t39-server-trial.js` and `nekojs/client_scripts/src/t39-client-observe.js` **do not already exist**. If either exists, stop and choose distinct names; never replace a user file.
2. Inspect existing scripts for declarations targeting diamond, stick or redstone lamp and existing script failures. If conflicts/failures exist, do not disable or rewrite unrelated scripts. Use an authorized isolated game directory/profile or record the blocker.
3. Copy [26x-server-baseline-or-restore.js](fixtures/26x-server-baseline-or-restore.js) to the new server filename and [26x-client-observer.js](fixtures/26x-client-observer.js) to the new client filename. Both contain read-only probes. Record original files/hashes outside the game directory; these newly allocated filenames are the only mutable deployed fixtures.
4. In a disposable or explicitly authorized test world with commands enabled, run `/nekojs reload server`, then `/nekojs reload client`. Wait at least 20 ticks. Preserve the `[ticket39] server phase=baseline-or-restored` and `[ticket39] client` lines.
5. Replace **only the newly created server filename** with [26x-server-active.js](fixtures/26x-server-active.js), run `/nekojs reload server`, and preserve phase/generation result plus server/client readings. The intended complete plan has two item declarations and one block declaration.
6. To inspect network-delivered stacks, manually `/give @s minecraft:diamond 32`, then select a newly received diamond stack; manually `/give @s minecraft:stick 16` and select it. Record the CLIENT held reading separately from CLIENT freshly manufactured stacks and SERVER freshly manufactured stacks. No component-qualified `/give` is used; explicitly patched stacks would mask the default-component problem.
7. For the block's visual/light-data leg, `/give @s minecraft:redstone_lamp 1`, place it manually in an empty, identified test location without power, inspect nearby block light after normal propagation, and record screenshot/location. Do not equate block texture brightness with light emission. Chunk lighting payloads may transfer calculated light without transferring the block's own properties.
8. Replace only the server trial filename with [26x-server-invalid-candidate.js](fixtures/26x-server-invalid-candidate.js), run `/nekojs reload server`. Expect a failed candidate at `STATE_PLAN`, with `state-plan-preflight:item-block-modification`, because maxStackSize=500 exceeds 99. Existing active probes should continue printing 16/EPIC/7 when a reading changes; the candidate's lamp=2 must not publish.
9. Replace only the server trial filename with the baseline/restore fixture and `/nekojs reload server`. Fresh SERVER values should return to the captured baseline. Read the CLIENT probes and newly recreated stacks independently; cached inventory stacks may retain a captured component map.
10. Leave and reconnect **only after saving**. Collect all three readings again. Do not write `relog = supported` merely because same-process block fields are visible. The separate-process procedure below is required for AC10.

If the command bridge is incompatible with this game version, use the options tool to pause for manual in-game commands. Do not report a successful command injection without the reload log.

## Options-tool pauses

[actions.json](actions.json) contains directly usable `ask_user_question` question payloads for baseline, active observations, failed candidate, restoration and safe exit. Every outcome includes failure/unknown choices. Collect logs/screenshots after each reply; user choice alone does not prove remote synchronization.

## Restore and preservation

Before cleanup, install the baseline/restore fixture in the owned server filename and successfully reload SERVER. Confirm fresh values match captured baseline before removing the owned server/client files. Then reload SERVER and CLIENT again to discard probe listeners. This step can execute unrelated user scripts, so do it only in the same previously authorized test profile. Save and exit normally before replacing any jar. Compare pre/post hashes of all pre-existing script/config/pack/trust-store files; changes from ordinary Probe/workspace generation must be recorded, not silently rewritten. Do not delete worlds, clear inventory or remove placed blocks except at the explicit disposable test location. Root close restores its owned baselines; server stop merely clears the server binding, so leaving a world alone is not the restoration check.

## AC10 evidence boundary

An integrated server and local client share `BuiltInRegistries.ITEM/BLOCK` objects in one JVM. The owner writes the item holder's component map and existing `Block`/`BlockState` instances; seeing these values in CLIENT does not prove a packet, resync or a separate-client apply path. CLIENT `Item.of` probes manufacture a local stack; the held stack is another independently sampled object. Existing stacks capture their base components at construction and can remain stale after holder rebinding, even in an integrated process.

On a dedicated server, leave server fixtures only on the server, observer only on the independent client. Record both PIDs, node/loader/jar hashes and separate logs. Do not mirror the server modification listener onto CLIENT or inject per-stack components as a workaround. Compare active commit while connected, a fresh ordinary `/give`, relog, and optional genuine platform chunk resend. A remote mismatch must be recorded as `unsupported`/failure or corrected by an explicitly accepted policy; it cannot be converted into a pass by documenting a guessed repair.

The existing policy deliberately declares automatic modification sync unsupported and forbids adding network references without updating the capability record. It does **not** preflight-block valid but client-sensitive properties such as block hardness/friction/lightEmission or item rarity/stack size based on connection topology. Its current documentation claim that relog is supported is not backed by a production modification replay path.

## Production trace and actionable findings

| Owner/path | Verified current behavior | Consequence |
|---|---|---|
| `NekoJSMod.java:179` | registers one `ModificationDomainOwner` in the runtime root | root owns baselines |
| `ModificationDomainOwner.java:101–112` | SERVER candidate collector, inert empty plan before server bind, then candidate item/block dispatch | no CLIENT collector |
| `ServerEventListener.java:76–79` | calls applyInitialPlan about-to-start | initial apply before player handshake |
| `ModificationDomainOwner.java:243–277` | restore owned baseline, apply complete declarations | legal commit application |
| `ItemModificationEventJS.java:102–104` | `Holder.Reference.bindComponents` | changes local holder defaults only |
| `BlockModificationJS.java:247–272` | writes properties and all state copies | static local block graph, no remote mirror |
| `ServerEventListener.java:164–168` | clearServer on stop without baseline restoration | must empty-plan reload or close root for restore |
| `ModificationDomainOwner.java:430–445` | close restores and clears snapshots | process shutdown/root close restoration |
| `common/.../EventBusJS.java:720–727` | normal active listener exceptions are caught/reported | startup collector cannot see normal callback failure |

**F39-startup-partial (code defect, not merely unverified):** `applyInitialPlan` posts to active buses (`ModificationDomainOwner.java:127–128`, 1.21.1 twin:90), whose normal script wrappers catch callback exceptions. A listener can add a valid declaration then throw; the startup owner still preflights/applies the accumulated partial plan. Its `try/catch` around post does not recover swallowed guest failures. Candidate DOMAIN_PLAN collection propagates failures through `executeForCollection`; startup lacks that behavior. This conflicts with AC4's all-or-nothing collection. Optional negative fixture [startup-partial-negative.js](fixtures/startup-partial-negative.js) is for an isolated startup regression only; it is not part of the ordinary happy-path client trial.

**F39-block-whole-replace (code defect):** `applyBlock` (`ModificationDomainOwner.java:367–376`) captures baseline but applies new properties to the current live block; baseline restore occurs once before the entire declaration list. Two declarations for the same block can therefore merge properties. A first stone hardness=2/resistance=8 followed by a second stone lightLevel=7 retains 2/8, whereas REPORT characterization and MIGRATION §2.2.5 require the last declaration to rebuild from baseline (normally hardness1.5/resistance6/light7). Item apply rebuilds from baseline for every declaration (`:309–312`). This is outside this preparation's write scope; lead should add a real regression before source repair. [26x-block-whole-replace-negative.js](fixtures/26x-block-whole-replace-negative.js) provides the isolated public Script API trigger, not an ordinary client trial.

**F39-visibility/relog (unsupported claim):** the modification owner has no client replay or transport. The actual launcher NeoForge 26.2.0.75 bytecode proves that `RegistryDataCollector.collectGameRegistries` calls `updateComponents`; this runs `BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build` and binds reconstructed maps. NekoJS's `dynamic/mixin/RegistryDataCollectorMixin.java:31–38` reapplies **dynamic item specs only**, not modification declarations. A handshake can overwrite item modifications in a shared JVM; a remote client reconstructs its own defaults. The item stack codec transmits count, item holder and **component patch**, not the full modified prototype. An empty-patch stack does not transmit server default changes. Relog and chunk resend do not generally repair block fields or item defaults. See the bytecode traces below. Do not treat this as a permission to introduce a new synchronization feature.

## Automated evidence audit

Existing XML reports were read, not rerun. At the inspected 2026-10-04 timestamps, 26.2.0 has 6/6 `Ticket39ModificationScriptE2ETest`, 4/4 block E2E, 4/4 examples, 15/15 component tests and 2/4 legacy tests **skipped** due to vanilla registry gating. Parity 10, ownership 5, surface 2 and float coercion 9 ran with zero failures. 1.21.1 item E2E 6/6 and legacy 2/4 are skipped; parity8, ownership5, surface2, coercion9 ran. A green aggregate build does not turn these skipped runtime legs into passes.

1.21.1 still needs its real item component reflection/startup/reload/removal smoke. Use [1211-server-item-active.js](fixtures/1211-server-item-active.js) and [1211-server-item-baseline-or-restore.js](fixtures/1211-server-item-baseline-or-restore.js), Java21 and the corresponding built jar/GraalMC pairing; do not deploy 26x block listeners or newer food/tool surfaces. The 26.2 session cannot close this node's leg. The relevant 1.21.1 modification mechanism is reflective `Item.components` replacement; static villager trade pools belong to ticket22 and are not this ticket's missing component test.

## AC14 breaking-symbol checklist (no sign-off)

The migration already lists: public static `ItemModificationEventJS.fire(MinecraftServer)`, public static `BlockModificationEventJS.fire()`, public `ItemModificationEventJS(MinecraftServer)`, and implicit `BlockModificationEventJS()` constructor. Private static SNAPSHOTS and package-private ItemModificationJS.applyTo are structural/internal deletions, not public Script API members. Replacement is owner initial-plan application and SERVER DOMAIN_PLAN; `modify(String,Consumer)`, getters and script event names remain. Maintainer informed confirmation is still required; this preparation does not check AC14 or close ticket39.

## Local bytecode evidence

The [trace directory](trace/) was generated with Zulu25 `javap -p -c` against the installed `minecraft-client-patched-26.2.0.75.jar` that the lead's launcher uses. It records the local binary, not another node's behavior. Read [RegistryDataCollector](trace/registry-data-collector.javap.txt), [initializers](trace/component-initializers.javap.txt), [pending component apply](trace/pending-components.javap.txt), [holder component bind](trace/baked-components.javap.txt), [stack constructors](trace/item-stack.javap.txt) and [optional stack codec](trace/optional-item-stack-codec.javap.txt). The text is generated diagnostic output. No game execution is inferred from these traces.

## Results

All eight fixture files passed `node --check`; `actions.json` parsed successfully. These checks validate syntax only, not GraalJS preflight, Bean Property exposure or Minecraft execution. Existing diff whitespace check returned no errors; the directory remains new/untracked and has not been staged.

All live phases, dedicated-client evidence, final-node smoke and maintainer conclusion: **NOT RUN / UNFILLED**. Fill [records.md](records.md) only from actual results. Fixtures are prepared source-checked scripts; their host property access is grounded in current declarations/source and must still be confirmed by the live run.
