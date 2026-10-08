# Exact085698ab formal performance: NOT GREEN

Source `085698ab1de7e37438b16823ac538ca15a50f87e`, clean owned checkout, NeoForge26.1.2/JDK25.0.2. Original sampler SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`, fixture hashes and thresholds unchanged. No concurrent build/test or owned installed game ran during sampling. This is a development-run source measurement, not an installed official-JAR performance claim.

| Complete group | All formal samples (ms) | Mean (ms) | Verdict |
|---|---|---|---|
| Initial reload |318,357,343,330,331|335.8|FAIL at285.3; sampler exit not observed|
| Confirmed reload1 |441,241,343,214,202|288.2|FAIL at285.3; sampler exit0|
| Confirmed reload2 |423,342,208,341,296|322.0|FAIL at285.3; sampler exit0|
| Startup |22278,21648,22553,21070,22043|21918.4|PASS at41173; sampler exit0|

Warmups21486/22035ms are retained. All fifteen reload observations average315.3333333333333ms as supplementary arithmetic; this does not replace the individual complete-group failures. First samples441/423 are retained. Historical ec7 PASS and b7/4431 FAIL records remain separate; no causal performance regression or speedup is inferred from this comparison.

The original asynchronous Process wrapper returned a null ExitCode after its sampler wrote all five observations and its done marker. The outer runner directly exited1, and no sampler exit0 is invented. After verifying owned processes settled and ports free, a fixed [follow-up plan](CONFIRMED-RUN-PLAN.md) declared exactly two complete reload groups and one startup group before any new observations. The only correction invoked the same PowerShell sampler synchronously and captured LASTEXITCODE. All three sampler exits0 and the outer runner exit0 were directly observed. There was no latency-driven rerun or threshold change. All ten game sessions stopped through RCON without force; no user process was stopped.

[Summary and per-entry hashes](performance-summary.json) bind every raw archive; entries were read back/hash verified. Raw samples include RCON RTT and marker time separately. Their difference remains part of the original marker-time measurement and is not subtracted to obtain acceptance. Credential checks exclude only the exact public benchmark cache-directory token from value matching; no server properties, worlds or raw JFR are included. The original sampler is bound by source/hash rather than duplicated with its public fixture password.

Complete current-source performance remains **NOT GREEN**. Overall IDE, visual, live configuration/pre-ACK disconnect, fireResistant, tickets and release/rollback acceptance remain open. HTTPS login restoration is deferred by the user; no push/release or scheduled continuation occurred.
