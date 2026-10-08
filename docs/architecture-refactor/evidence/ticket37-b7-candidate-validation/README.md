# Exact b7 candidate: performance and installed domain validation

Runtime source:`b7c788cbbc1c18822b465feb9f0cda0330735570`; main documentation revision before this supplement:`678efcb734926d0c3828870466a70e1bb615adbf`. The same source already passed [common/isolation/five-node4534test validation](../ticket16-default-proxy-declarations/README.md), with271skipped and0failures/errors. No new production code or version bump is made in this supplement.

## Exact-source formal performance

The owned, clean detached tree was advanced from`1b406626`to exact`b7c788cb`, without creating a branch or changing benchmark scripts. SamplerSHA256 stays`1F9C5D14A1A586DA4B3F1CA8E71B2407CE089EE8BEE185418963E1B5745DF0B4`. SameDEBUG/data/world/warm-cache fixtures, process-localJDK25.0.2, existing`.gradle-perf02`, RCON and25871/25872 ports; noJFR and no concurrent installed-server trial during formal sampling.

| Complete group | Warmups | All five formal policy values(ms) | Mean(ms) | Verdict |
|---|---|---|---:|---|
| `20261007T163039Z-startup-26.1.2` |43384,27724|27147,25060,25023,29625,28602|27091.4|PASS at41173|
| `20261007T163426Z-reload-26.1.2` |none|293,357,240,340,373|320.6|FAIL at285.3|
| `20261007T163516Z-reload-26.1.2` |none|404,209,282,386,355|327.2|FAIL at285.3|

**Current exact-source combined gate remains NOT GREEN.** Both complete new reload groups fail; all10formal reload values average323.9ms, over38.6ms. Startup passes. No first/later sample is discarded, faster RCON metric substituted, marker observation lag subtracted, threshold changed or historical same-source/different-source group silently pooled. [Older1b confirmations](../ticket37-reload-confirmation/README.md) remain historical PASS/FAIL evidence, not certification ofb7.

All9server sessions and10reload responses settled normally; stop viaRCON, forced_kill/killed false, no timeout, reloads committed generations2through6 with no errors. Every inner sampler and serial outer invocation exited0. OuterCPU snapshots49%,18%,22% describe observed load, not exclusive-machine conditions or an established cause. Startup archive has15entries(14logs+originalchannel-test); reload archives2each. Text copies and every decompressed archive entry were SHA256-verified against retained originals. [Machine summary](performance-summary.json) records the exact arithmetic.

## Actual installed NeoForge26.2 proof

Separate new agent-owned profile:`build/ticket37-b7-installed-26.2`, loopback25891/25892. Runtime launch uses the already-installed officialNeoForge26.2.0.75/JDK25 loader libraries through an immutable dependency junction and GraalMCmod. This is a real productionJAR boot, **not runServer development-classpath proof**. Deployed NekoJS preview3 SHA256:`15686606D4AB77DBCF5F0F9678A6BD8814D5930B48D8E683FE7DABA46EBA3CBF`, equal to the current [five-artifact manifest](../ticket16-default-proxy-declarations/artifact-manifest.json). No user's profile/world/mods/config was changed; the new disposable world is retained. Only this test profile enables the existing`[dynamicRegistry] enabled=true`gate; noClassFilter/HostAccess relaxation.

Initial bootPID2324 reachedDone, bound the real activation engine, and public`nekojs registry`reported1registered/0stale for Item,SoundEvent,MobEffect. Public commands generated actual defaultTS359files andPython346files. The initial entity fixture summoned a taggedPig but subsequent selector returned`No entity was found`: no MobEffectapplicationPASS is claimed for that trial. Original console/stdout/stderr/helper and RCON failure are preserved. This is an unloaded entity-chunk fixture issue, not a failed custom-id parse.

Retry explicitly force-loads the test chunk and provides a small stone platform; original source/artifact and world are preserved. The selector consistently returns the same taggedPigUUID before/after reload with observedPos`[0.5,1.0,0.5]`; the first trial did not capture aUUID, so which summon produced this selectedPig is not established and it is not claimed to be the newly summoned high-platformPig. Vanilla`effect give`successfully applied`ticket37_b7:proof_effect`; `data get entity`returned actual`active_effects`with that id/duration1199. Identical-definition`nekojs reload server`committedgeneration2/COMMIT/noerrors; publicregistrycounts remained1/0stale. Vanilla`effect clear`removed it, then`effect give`reapplied it withamplifier1; actual entityNBT returnedid/duration1200/amplifier1. This proves real single-server MobEffect registration/application before/after reload, not visual effect behavior or clients.

Both installed-server sessions stopped through ordinaryRCON and exited0. There was no forcedkill on these completed trials. Tests did not login clients or fakeACKs: multiplayer/catch-up/prepare/ack/abort/degraded/clientregistry parity remain notverified. Item/SoundEvent registry health counts are not item use/sound playback proof. No other node's installed-binary or first-frame acceptance is inferred.

## Actual default Probe output: narrow PASS, wholePython FAIL

Actual installed generation resolves the dynamic callback to script-only`DynamicRegistryEvent`TSinterface/PythonProtocol with all3concrete optionalcallback/bool operations, explicitPythonbuilder imports and TSrelativebuilder reference. DefaultcoreJava modules are absent. This supplements the earlier defaultcatalog integration seam with a real Minecraft installed-productionJAR command.

Full generatedPythonAST audit is **FAIL**:344`.pyi`files,340parse and4fail. Exact inventory in`installed-26.2/ticket37-live-probe-all-inventory.txt`:

- `nekojs/_java/com/tkisor/nekojs/api/recipe/__init__.pyi`: illegal`nekojs$applyScripts`method identifier.
- `nekojs/_java/net/minecraft/world/item/crafting/__init__.pyi`: illegalJava`$`method identifier.
- `nekojs/_java/net/neoforged/neoforge/mixins/__init__.pyi`: illegalJava`$`method identifier.
- `nekojs/_java/java/text/__init__.pyi`: Beanproperty`2DigitYearStart`starts witha digit.

The checker first failed at the firstSyntaxError, then was strengthened to inventory all failures while retainingexit1. Dynamic event and registry-builder stub files parse, their3imports resolve and their exacttypedoptional/bool methods pass independently; this does not change the overallFAIL. Both initialfailtrace and completeinventory are retained. The actual706-entry generated-outputZIP is byte-verified against originals. Pyright was NOTRUN; earlier strictminimalTS caller checks remain their own smaller scope.

A subsequent independent **complete live strictTS check is alsoFAIL**: bundledexisting`tsc`with`extends`ofactual installed`server_scripts/jsconfig.json`, preserving all real default editor includes/paths/plugins, changing only`noEmit=true`and`skipLibCheck=false`for verification, exits2with3116diagnostics. Original product/default editor`skipLibCheck=true`is unchanged. Full output/config and groupedinventory are retained; no old/newcontrolledstrictcomparison was run, so all3116are not claimed newlyintroduced byb7. Concrete registry-related diagnostics include globalmanual/structured merging conflicts forBlock.item/renderType,Enchantment.supportedItems,Entity.category,MobEffect.displayName,Item.groupTab andSoundEvent.fixedRange, plus manualduplicateitem. The b7conditional reference brings globalstructuredbuilders into the full editor combination; the isolatedcommonfixture did not include realSTARTUPmanualplugins. Future correction must test this complete consumer combination without deletingmanualpublicproducer or suppressingerrors. Other Java/package/generic/reflection diagnostics have separate scope and need triage, not automatic attribution tothisnarrowpatch.

Newidentifier/rendering and declaration-combination failures are concrete next repair targets, not hidden by previous25stub/minimalcallerPASS.

## Diagnostic and acceptance limits

Initialworldcreation used`minecraft:flat`without explicit layers and logged`No key layers in MapLike[{}]`; a later boot ofthe retained generatedworld did not repeat that configurationdiagnostic. Both trials logged DebugFileappender failures involvingNettykqueue/epoll native initializers unsupported onWindows. These exactstderr errors remain retained: installeddomain functionalPASS is **not** a claim of error-free logs/allstartupacceptance. No speculative platform/security suppression was made.

Tickets15/16publicdeletion, other26.xtrade approvals, actualmultiplayer, legacy/allnode/firstframe, finalrelease/cutoverpolicy remain open. Startup's actual scriptProxy surface is sugar2/3argument forms, custom3 andregister3—not a script`create`member; staticJava`RegistryEventJS.create(repository,node)`is an adapter factory. Earlier shorthand “startup3createoverloads” in generatedhandoff prose is corrected here; no phantom scriptmember is implemented. The independent [source-only STARTUP trace](STARTUP-TRACE.md)records actualhost/defaultcollection/platformsubsets/manualproducer/namespace/overload/nullability limits and a proposed realdefaultregression seam. It ran no sourcechanges/tests/builds/MC; its original ignored report is retained, and only links were rebased in this archival copy. Lead's later live strictTSFAIL above is separate executed evidence, not retroactively attributed to the source reviewer. [Next repair sequence](NEXT.md)records remaining work, not futurePASS.

All local rawlogs/world/privateRCONproperties remain in the owned profile. NoRCONcredential/configserver.properties or worldbinary is committed. Original initial/retry2 RCON command transcripts are retained inside`installed-26.2/command-proofs.zip`, with both entries SHA256-verified against originals; their real trailing blank lines are not edited, and default whitespace checks are not disabled. Rawserverlogs contain mixed native encoding and are stored as forensic binary bytes; other textcaptures preserve original bytes/CRLF while keeping defaultwhitespace checks. Benchmarkfailures, fixturefailures, syntaxfailures and diagnosticlimitations are not releasewaivers. Noversion1.2.0 switch, artifactupload, formalrelease or complete48-ticket claim. Executionnote: calls onthisroute continuedusingexplicitcurrentdanger-full-access/nonemptyjustification fields; this is not compliance with the user's no-field instruction. No approvalprompt/receipt/escalation or permissionpolicychange occurred.
