# Ticket 39 NeoForge 1.21.1 MCP attempt (2026-10-06)

Result: **blocked before gameplay fixture execution; no acceptance claim**.

## Environment

- Minecraft 1.21.1
- NeoForge 21.1.172
- Java 21.0.10
- MCP 0.4.2
- Isolated game directory: `D:\mcmodDemo\mcp-1211-ticket39-run2`
- NekoJS 1.1.0-preview3 built from current source
- GraalMC `8762962` (NeoForge build), with the duplicate embedded ICU module removed only from the disposable test jar to let module resolution proceed

## Startup findings

The first 1.21.1 attempt found two production startup defects, both fixed in the current source:

1. `NekoJSPackLoader` subscribed to `AddPackFindersEvent` on the common bus; 1.21.1 requires the MOD bus. Fixed by declaring `EventBusSubscriber.Bus.MOD`.
2. `NekoJSNetwork` subscribed to `RegisterPayloadHandlersEvent` on the common bus; 1.21.1 requires the MOD bus. Fixed by declaring `EventBusSubscriber.Bus.MOD` and updating `NetworkRegistrationSourceTraceTest`.

Both fixes compile and the focused source-trace test passes.

## Latest rerun

After the MOD-bus fixes and the version-guarded loot registry fallback, the clean `run2` profile reached NekoJS startup, client script reload, workspace creation and the MCP endpoint without `NoSuchMethodError`. The exact filtered run log is in `D:\mcmodDemo\mcp-1211-ticket39-run2\logs\latest.log`; the MCP connection used port `9876` in the final run.

The run did not complete the ticket39 baseline/active fixture because the MCP GUI/world creation path remained on the world-preparation screen. No item modification, client visibility or restoration result is claimed yet.

## Outcome

- MCP connection and mod list succeeded in a clean isolated game directory.
- NekoJS startup/client scripts reached discovery and reload.
- `NekoJSPackLoader` and `NekoJSNetwork` MOD-bus fixes were exercised by real startup.
- The 1.21.1 loot-table API mismatch no longer crashes startup; legacy registry context is now guarded and unavailable JSON replacement is reported instead of invoking `getRegistries()`.
- Ticket39 baseline/active/restore fixture execution remains pending; do not close AC11 or AC14.
