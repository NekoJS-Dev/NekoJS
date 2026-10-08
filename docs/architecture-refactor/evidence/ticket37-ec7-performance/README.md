# Exact-source ec7 performance

Runtime source: `ec7fe6848afc1970661ee1f38a7c7c6d4575530c`. The clean detached performance worktree, existing world/caches, DEBUG settings, JDK25.0.2, original fixture, Gradle home and25871/25872 ports were retained. The original sampler is unchanged: SHA256 `1F9C5D14A1A586DA4B3F1CA8E71B2407CE089EE8BEE185418963E1B5745DF0B4`. Source and fixture hashes were checked before execution and again during archival.

| Complete group | All five formal observations (ms) | Mean (ms) | Original gate |
|---|---|---:|---|
| 20261008T170421Z reload |274,156,252,247,218|229.4|PASS at285.3|
| 20261008T170503Z reload |338,188,288,253,268|267.0|PASS at285.3|
| 20261008T170539Z startup |26168,24017,24559,24521,25754|25003.8|PASS at41173|

Both startup warmups,26495/26650ms, are retained. All ten reload observations average248.2ms as supplementary arithmetic. The338ms first observation in the second group remains included. Each complete group passes its unchanged mean threshold. **The exact ec7 combined performance measurement passes.** Earlier b7 and4431 failed groups remain valid historical records; they are neither discarded nor pooled into this measurement.

All three sampler exit codes and the outer runner exit code were directly observed as0. All nine game sessions stopped by normal RCON, with forced_kill/killed=false. Each of the ten reloads committed without errors. No installed-client trial, heavy test matrix or JFR overlapped these groups. Lightweight evidence/documentation operations overlapped part of sampling, and user processes remained uncontrolled. This was not an exclusive-host experiment and proves no causal explanation for the change in measured performance.

`prepare-current-performance.ps1` preserves the existing absolute-path, ancestor/reparse-point, process-ownership, source-cleanliness, sampler-hash and free-port guards. `run-current-performance.ps1` rechecks source/hash/ports before each group, preserves existing output and directly observes each hidden sampler's exit. The inner sampler and its warmup/sample counts are unchanged.

The three raw ZIPs contain all original sampler output files, including complete game stdout/stderr, environment snapshots and JSONL. Every archived entry was byte-verified against the retained local source; per-entry hashes are in [performance-summary.json](performance-summary.json), and package-file hashes are in [evidence-manifest.json](evidence-manifest.json). Credentials, server.properties, worlds and raw JFR are excluded. The original sampler and fixture sources are bound by revision/hashes rather than copied, avoiding duplication of their public benchmark credential default. Credential scanning excludes only the exact public Gradle-cache directory token, whose suffix happens to equal that default; it found no credential configuration or other credential-value occurrence in included bytes. The initial scanner failures remain under the ignored build directory.

This result closes this revision's formal startup/reload measurement gap. [Configuration synchronization evidence](../ticket37-configuration-sync/README.md) separately records the actual26.2 multiplayer protocol and full matrix. Full IDE typing, other installed nodes, remaining configuration concurrency windows, visual/first-frame checks and maintainer conclusions remain open. Scheduled continuation remains paused; this package grants no public deletion or release authorization.
