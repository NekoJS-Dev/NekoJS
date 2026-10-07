# Current-candidate performance verification

Date: 2026-10-07. Behavior revision: `ac810fa71d061d31983220c0d9b198af5d4253ac`. Primary node: NeoForge `26.1.2`. This evidence does not authorize publication or close the remaining domain tickets.

## Policy and result

The unchanged [ticket04 policy](../../implementation-tickets/04-perf-release-policy.md) requires startup `wall_done_ms` mean <= 41173ms and reload `marker_ms` mean <= 285.3ms, with at least five formal samples per dimension. Warmups are separate. No adverse sample is deleted; forced-kill/timeout samples must be repeated rather than silently accepted.

| Group | Formal values (ms) | Mean (ms) | Verdict |
|---|---|---:|---|
| Initial startup, `20261007T094306Z` | 35858, 51283, 34733, 51046, 40802 | 42744.4 | FAIL; retained |
| Full startup repeat, `20261007T123902Z` | 28044, 23944, 25296, 25093, 26107 | 25696.8 | PASS |
| Independent startup confirmation, `20261007T125114Z` | 23348, 23842, 23751, 24495, 23785 | 23844.2 | PASS |
| Observed-noise reload, `20261007T130505Z` | 473, 322, 390, 287, 473 | 389.0 | FAIL; environmental drift disclosed |

**Combined current gate: NOT GREEN.** Startup repeats pass, but the actual reload group exceeds285.3ms by103.7ms. Environmental noise is a disclosed condition, not a waiver or a reason to delete this group. All five RCON responses report successful commits to generations2through6with no errors; functional success does not imply performance acceptance. RCON roundtrip values are459,322,274,286,365ms(mean341.2ms), but the policy uses marker time, not this faster alternative. The third/fifth marker delays include additional observation lag; do not subtract it from the policy metric.

Every startup group contains two warmups and five formal samples. All 21 sessions record `stop_channel=rcon`, `forced_kill=false`, `killed=false` and no timeout. Both new startup sampler invocations settled with exit0. The serial confirmation's outer job later exited1 because its strict ten-minute quiet preflight rejected reload **before** launching any reload sampler; this does not change the completed startup samples or manufacture reload evidence. The initial sampler produced all seven records and its done marker, but its outer runner was subsequently cancelled; it is not relabeled as a clean command exit.

## Exact protocol and isolation

- Unmodified `bench/perf/sample.ps1`: SHA256 `1F9C5D14A1A586DA4B3F1CA8E71B2407CE089EE8BEE185418963E1B5745DF0B4`. The canonical and owned copies match. All 18 tracked fixture files were hash-compared and match.
- Existing detached, clean worktree: `build/ticket37-perf-owned-ac810fa7`, at the full behavior revision above. No new Git branch or source modification was used for sampling.
- The exact absolute `versions/26.1.2/run` path in that owned worktree was resolved and verified before the harness's fixture replacement. Only this agent-owned run root is used; user worlds/profiles are untouched. Existing benchmark world is reused under the same warm-cache protocol.
- Ports 25871/25872 were checked free. The same Gradle cache `D:/mcmodDemo/NekoJS/.gradle-perf02`, DEBUG capture, fixture data and existing RCON channel are retained.
- New invocations explicitly set process-local `JAVA_HOME=C:/Program Files/Java/jdk-25.0.2` and prepend its bin to PATH. No global Java, power, security or process setting was changed. The prior initial run also used Java25; exact daemon/game vendor and arguments require the retained environment/launcher evidence rather than inference from PATH alone.

Commands, in the owned worktree, with those process-local Java variables:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\bench\perf\sample.ps1 -Mode startup -Node 26.1.2 -Warmup 2 -Samples 5 -GradleUserHome 'D:\mcmodDemo\NekoJS\.gradle-perf02' -ServerPort 25871 -RconPort 25872
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\bench\perf\sample.ps1 -Mode reload -Node 26.1.2 -Reloads 5 -GradleUserHome 'D:\mcmodDemo\NekoJS\.gradle-perf02' -ServerPort 25871 -RconPort 25872
```

The first command was run twice. Separate orchestration attempted to wait for three CPU-load observations <=10% before sampling, without modifying the harness or excluding any sample after startup. The first repeat incorrectly allowed one null CPU reading through PowerShell numeric comparison, so it is supporting evidence, not the strict quiet-window confirmation. The confirmation corrected that preflight to reject null and observed2%,2%,0%. Its harness environment snapshot shortly afterward nevertheless recorded22%CPU: a point-in-time quiet preflight is not a claim of continuous host isolation. A two-minute preflight before a separate reload attempt remained busy and exited1 **before** any sampler or Minecraft launch. A later serial attempt's ten-minute strict preflight also expired without launching reload. Neither is a failed or discarded reload sample. The separate observed-noise reload group then completed the unchanged harness with exit0. Its outer observation was48%CPU and its harness snapshot36%CPU,37.2GBfree RAM and6Java processes, compared with the initial startup snapshot1%CPU,46.2GBfree and2Java processes. This environmental drift is explicitly recorded, not called exclusive-machine acceptance. All reload values remain in the result table. No user process was stopped.

## Diagnosis and limits

The initial failure is genuine and retained. Its 42744.4ms mean was 1571.4ms above the threshold. Two complete repeats of unchanged runtime code now pass; the original slowdown is therefore not reproducible in these later windows. This resolves the startup gate for the measured repeated windows, not the root cause or all future conditions. There is no code-optimization claim and no threshold change.

A read-only historical comparison against `28283cc4` found NeoForge26.1.2.71, FML11.0.13, GraalMC25.1.3.7, JEI29.5.0.26 and major fixture markers/counts unchanged. Historical startup mean was16512.2ms. Approximate historical/current-initial phase means from second-resolution logs were: FML to first plugin registration7.8/20.2s, first plugin to bootstrap1.4/2.2s, bootstrap to first startup fixture0.6/3.2s, first fixture to Done3.8/10.0s. The first plugin marker is `PythonTranspilerPlugin`, not the later `DynamicRegistryPlugin`. Cross-thread/asynchronous log spans are not CPU profiling or proof that no Neko code ran earlier.

Host/path/cache/I/O/output pressure, revision-dependent initialization and wrapper/daemon drift remain distinguishable hypotheses, not established causes. A contemporaneous host observation during a rejected reload preflight showed SearchIndexer, Pulse.Index and other resident applications consuming CPU; this is not proof that they caused the earlier startup failure. Balanced old/new A/B and JFR were not run. Such attribution is needed before speculative caching, lifecycle changes or security-check removal. None of those changes were made.

Complete evidence is preserved in the four group directories above: `samples.jsonl`, `env-snapshot.txt` and `raw-logs.zip` containing all original stdout/stderr files(14per startup group,2for reload). Each startup archive also contains its original `channel-test.txt`; its trailing whitespace is retained verbatim inside the archive rather than edited or committed as normalized text. Each copied text file and each decompressed archive entry was SHA256-compared with the original; all match. Invocation outputs are retained as `initial-startup-console.txt`, `repeat-startup-console.txt`, `confirmation-startup-console.txt` and `observed-reload-console.txt`. Original isolated run directories remain available under the owned worktree's `bench/perf/out/`; no raw source was deleted. No generic `error.png` DEBUG asset-path match is treated as a runtime ERROR; case-sensitive ERROR/FATAL logger records, uncaught thread exceptions and NEKO diagnostics are checked separately.

## Remaining acceptance

- Investigate reload389.0msFAIL through a controlled stable-window repeat and profiling that separates interpreter/RCON/owner-thread wait, script execution and marker-observation overhead. The current five valid samples stay retained. Do not hide the first reload, substitute RCON roundtrip for marker time, loosen285.3ms or optimize away necessary work.
- Do not reuse current measurements to certify later runtime/code/artifact changes without appropriate revalidation.
- Default typed Proxy event declarations, actual MobEffect/multiplayer windows, legacy static-pool and all-node/first-frame visual proof remain open.
- A22/A28 precise retrospective approvals are recorded in [PUBLIC-MIGRATIONS.md](../ticket37-autonomous-closeout/PUBLIC-MIGRATIONS.md#已取得的维护者结论主会话原文); they are not performance waivers or release authorization.
