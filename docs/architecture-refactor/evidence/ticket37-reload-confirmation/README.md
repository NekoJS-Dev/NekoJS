# Exact-source reload confirmation and streamed diagnosis

Source revision: `1b406626662b7c6334eb05fed0d964563eb10f01`. The main tree's `678012149` adds documentation only; all three formal groups below ran the same clean detached runtime source. The previous [two-hotspot evidence](../ticket37-reload-optimization/README.md) and its raw failed group remain unchanged.

## Complete no-JFR formal groups

| Group | All five marker_ms values | Mean | Verdict at285.3ms |
|---|---|---:|---|
| Previously retained `20261007T145020Z` |469,265,233,329,226|304.4|FAIL; retained|
| Independent repeat `20261007T154016Z` |296,329,217,313,227|276.4|PASS|
| Independent confirmation `20261007T154100Z` |293,207,212,153,357|244.4|PASS|

Both new five-formal groups pass without removing their first or adverse later sample. **All15 valid formal observations of this exact source**, including the original304.4ms group and469ms value, total4126ms and mean275.06666666666666ms. This all-observation arithmetic is supplementary audit information, not a reason to relabel the failed group. The unchanged [policy](../../implementation-tickets/04-perf-release-policy.md) is applied to each complete group; current repeated windows pass, not a guarantee of all future host conditions. The same-source startup group previously passed22825.6ms with2warmups+5formal. No data is pooled across different source revisions or with JFR diagnostics.

Samplers, serial outer invocation and normal RCON shutdown exited0. All10 new responses committed generations2through6 with no errors; no forced kill/killed/timeout. OuterCPU snapshots were33% and15%; these are observed-load windows, not continuously exclusive-host proof. Both used the original DEBUG/data/world/warm-cache/RCON protocol, ports25871/25872, process-localJDK25.0.2 and existing`.gradle-perf02`. No user processes, globalJava/security/power settings or benchmark world data were changed. SamplerSHA256 remains`1F9C5D14A1A586DA4B3F1CA8E71B2407CE089EE8BEE185418963E1B5745DF0B4`.

## Separately declared current-source diagnosis

`20261007T153633Z-reload-26.1.2` ran10 instrumented reloads and is **diagnostic only**, despite generic JSONL`kind=formal`. Marker values173,144,219,220,108,245,113,220,202,217ms average186.1ms; none count toward formal acceptance. Recording attached only to verified owned gamePID33632, not samplerPID33520 or launcherPID26460. These distinct process ids are not interchangeable.

The diagnostic disabled environment/system-property/security-property/process/JVM-information events at recording setup, then retained only ExecutionSample,NativeMethodSample,ThreadPark,JavaMonitorWait,JavaMonitorEnter andGCPhasePause. The filtered recording is737487bytes. A privacy-event extraction independently returned no forbidden events. Original recording stays local/ignored.

The new JDK consumer reads the whitelisted recording as a stream instead of exporting hundreds of megabytes of all-thread JSON. It filters CPU/native **sampledThread** and duration-event **eventThread** by`Server thread`, preserving the distinction that defeated the earlier thread scrub. It found428CPU/native samples,139reload-chain samples,10time clusters separated by gaps>1s matching the retained reload timestamps. There are0observed reload-chain ThreadPark-duration events; this is not proof of zero wait or a complete CPU trace.

Inclusive reload samples:92candidate-script loading,52module-host preparation,33pipeline-cache preparation,21candidate-context construction. Nearest-Neko counts include10canonical-path conversion,10sandbox construction,6entry execution,6canonical spliced resolution,5manifest evaluation,5game-dir verification,3source-map encoding and2mapping parsing. Neither removed`lineOffset` nor repeated builtin erasure is observed in this captured chain. Counts are not milliseconds or exclusive percentages. Remaining path verification is a security boundary and will not be cached/skipped without a proven scoped invariant.

The first diagnostic reload was173ms, while the previous ordinary first reload was469ms: an inevitably slow first reload hypothesis is not established. JFR attachment, warmup/JIT and host conditions differ; the fast instrumented run cannot explain every older delay. The no-JFR repeats above are the actual feedback loop result. No further production optimization is made solely from a plausible stack.

## Reproduction and boundaries

Run from the owned worktree with the stated process-localJava and unchanged script:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\bench\perf\sample.ps1 -Mode reload -Node 26.1.2 -Reloads 5 -GradleUserHome 'D:\mcmodDemo\NekoJS\.gradle-perf02' -ServerPort 25871 -RconPort 25872
```

The archived diagnostic orchestration is a recorded helper, not a drop-in command against these existing paths: it deliberately refuses to overwrite an existing recording directory. Copy it to a new owned build path and choose a fresh diagnostic directory before reproduction. Compile/run`ReloadProfileSummary.java` with the bundledJDK25 against the safe recording; its argument2 is the summary output path. No private reflection or production instrumentation is used.

Raw sample/environment copies and two-logZIPs were byte-hash verified against retained originals. Scoped`.gitattributes` retains machine-capture bytes and all three default whitespace checks while recognizing actualCRLF. No release/version1.2.0, full installed-JAR/first-frame/all-node runtime, actual MobEffect/multiplayer or public-deletion acceptance is asserted. A later declaration/runtime change must receive its own exact-candidate checks; these numbers are not rebound to it.
