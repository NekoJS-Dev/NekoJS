# Exact-source 4431 performance

Runtime revision: `4431faeb2b404c914e66e85c41c2fd0ba3d14535`. The existing clean detached performance worktree was advanced from b7, preserving its world, caches and previous output. No benchmark code, metric, threshold or path-safety check was weakened. Sampler SHA256 remains `1F9C5D14A1A586DA4B3F1CA8E71B2407CE089EE8BEE185418963E1B5745DF0B4`.

| Complete group | All five formal values (ms) | Mean (ms) | Gate |
|---|---|---:|---|
| 20261008T121637Z reload |491,358,328,344,339|372.0|FAIL at285.3|
| 20261008T122001Z reload |366,386,375,249,324|340.0|FAIL at285.3|
| 20261008T122047Z startup |14002,12639,21303,23766,23921|19126.2|PASS at41173|

Startup includes both original warmups, 25135/24469ms. All ten reload observations average356.0ms as supplementary arithmetic; both individual complete groups remain FAIL. No first sample is discarded and no previous revision or JFR diagnostic is pooled. **Combined performance gate remains NOT GREEN.**

All nine actual game sessions stopped by normal RCON; every sample records forced_kill/killed=false. Every reload committed generations2–6 with no errors. The second reload and startup sampler exit codes were directly observed as0.

The first sampler printed its final done marker and the Gradle game task printed BUILD SUCCESSFUL, but the outer PowerShell capture remained pending after its sampler process had disappeared. The first sampler exit code was therefore not directly observed. After verifying the exact owned outer command, no owned game process and released ports, only that owned capture PID29120 was stopped; its execution session returned-1. This is an orchestration gap, not a game forced-kill, a discarded formal group or a reason to relabel its failed values. The recovery note and original capture are retained. Remaining groups used a hidden process with separate stdout/stderr files and observed process exit, preserving the same inner sampler.

The initial guard checks exact clean source, absolute owned paths, absence of reparse points in run targets, sampler hash, process ownership and free ports. Only the agent's dedicated run fixture is deployed. Process-local JDK25.0.2, existing `.gradle-perf02`, original DEBUG/data/world/warm-cache settings and25871/25872 ports are retained. No installed-server trial or JFR overlapped formal sampling. A short TSC harness correction ran during the first group's game startup; this was not an exclusive-machine trial and no causal or controlled speedup claim is made. User processes and global Java/security/power settings were untouched.

Raw archives were byte-verified against retained source files and include sampler XML-free JSONL/environment/game logs. Credentials, server.properties, worlds and raw JFR are excluded. [performance-summary.json](performance-summary.json) records arithmetic and the first outer-capture limitation. STARTUP declaration, real installed26.2 and type-check evidence are [separate](../ticket15-startup-event-declarations/README.md); neither package closes multiplayer, visuals, full IDE typing or release acceptance.
