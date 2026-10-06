# Zulu CLI follow-up (2026-10-06)

Result: **blocked by stale MCP runtime; no acceptance evidence**.

The documented Zulu Java 25 CLI path reached NekoJS client reload, unlike the MCP launch path that failed during mod construction. The CLI game directory was `%APPDATA%/.minecraft/mcp_launcher/game`. Its existing MCP runtime answered as `mcpmod@0.3.0` and the HTTP bridge failed with:

```text
NoClassDefFoundError: xyz/langyo/minecraft/mcp/common/ControlModeHelper
ClassNotFoundException: xyz.langyo.minecraft.mcp.common.ControlModeHelper
```

The client log did reach NekoJS CLIENT reload and loaded three scripts, but the temporary combined fixture then reported:

```text
[NEKO-7007] JSX UI root ticket48-login-form-root failed in phase render:
Render values must be VNodes, arrays, text, or null
```

That fixture error is not promoted to a product regression yet: the CLI was running the stale MCP runtime and the converted output was imported through a second `.tsx` module. A follow-up should first replace the CLI MCP jar with the current 26.2 `0.4.2` artifact, then reproduce with a minimal same-module render before classifying the VNode identity issue.

No Screen interaction, performance counter, reload, resource recovery, or cleanup result was accepted from this run. Temporary `ticket48` scripts were removed after the attempt; the launcher jar was preserved as a local test artifact and was not treated as repository evidence.
