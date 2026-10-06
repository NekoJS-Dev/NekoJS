# Ticket 48 NeoForge 26.2 combined-session attempt (2026-10-06)

Result: **blocked before client connection**. This is a failed attempt record, not acceptance evidence.

## Environment

- Loader: NeoForge `26.2.0.75`
- Minecraft: `26.2`
- Java: `25`
- Game directory reported by FML: `C:\Users\11515\.minecraft`
- NekoJS jar: `nekojs-neoforge-26.2.0-1.1.0-preview3.jar`, built from the current worktree with `:26.2.0:build`
- GraalMC: `25.1.3.7`, NeoForge artifact `graal-1504336-8762962.jar`
- MCP mod: `minecraft-mcp-26.2-neoforge.jar`

## Fixture

A temporary fixture copied the controlled conversion output `docs/ui-conversion/fixtures/login-form.output.tsx` and opened it through a `ClientEvents.tickPost` wrapper. The fixture was intended to record the login form, viewport/profile changes, state, reload and cleanup observations. Existing `nekojs/client_scripts/src/main.js` was not modified.

## Startup trace

The first launch selected the wrong MCP jar because 1.21.1, 26.2, 26.3 and Fabric jars with the same mod id were present. The non-target jars were renamed to `.before-ticket48` backups; no file was deleted. A second launch selected the correct 26.2 NeoForge MCP jar and reported the expected mod list containing GraalMC, NekoJS, NeoForge and MCP.

Both launches then failed during NeoForge mod construction:

```text
NekoJS (nekojs) has failed to load correctly
java.lang.ExceptionInInitializerError: null
Failed to wait for future Mod Construction, 1 errors found
```

Crash UUIDs:

- `94d01116-9034-4437-a069-8375919f64b9` (wrong-MCP launch)
- `70d129ef-f836-4df5-9b95-74a0f10564e3` (correct 26.2 MCP launch)

The MCP endpoint never became connected, so no Screen, input, reload, resource, performance, or cleanup observation was collected. The nested initializer cause was not emitted in the available crash report/log output; this remains a separate startup diagnosis task.

## Cleanup

The temporary fixture, copied NekoJS jar and copied GraalMC jar were removed. The `.before-ticket48` MCP backups were restored to their original names. The user's existing `main.js` and other game files were left unchanged.

## Consequence

Ticket 48 remains open. This attempt does not satisfy any of AC2, AC4, AC5, AC6 or AC7. The next run needs a launch path that exposes the nested `ExceptionInInitializerError` cause, likely the documented Zulu Java 25 CLI path or an isolated game profile with one target MCP jar.
