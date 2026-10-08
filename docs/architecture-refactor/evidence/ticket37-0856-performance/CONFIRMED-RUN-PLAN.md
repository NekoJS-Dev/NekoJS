# Fixed follow-up after missing sampler exit observation

The first group completed all five reload observations:318,357,343,330,331ms, mean335.8ms, FAIL at the unchanged285.3ms threshold. The wrapper's Process.ExitCode was null; its outer exit was1. No sampler exit0 is inferred. The server stopped through RCON, with no forced kill and no remaining owned game/listening port.

Before starting further observations, the fixed sequence is two complete five-reload groups and one startup group with two retained warmups/five formal samples. The only runner correction invokes PowerShell synchronously and captures LASTEXITCODE directly. The production sampler, path guards, fixtures, source085698ab, thresholds and ports remain unchanged. Retain the original failed group and every new sample; do not rerun groups based on measured latency. Report all fifteen reload values and each complete group's verdict, without pooling historical revisions or using a later pass to erase335.8ms.
