# NekoJS

## Agent skills

### Issue tracker

Plans, specs and tickets live as local Markdown under `docs/` — do NOT create GitHub issues for them. GitHub Issues are a read-only archive; community reports via `/triage` are the exception. See `docs/agents/issue-tracker.md`.

### Triage labels

Default triage label vocabulary: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root CONTEXT.md + docs/adr/. See `docs/agents/domain.md`.

### Minecraft MCP

`minecraft-mod-mcp` is registered as an MCP server and is the supported way to drive a live Minecraft client. Reach for it when a change needs in-game evidence that tests cannot give — client/server runtime smoke, Mixin and access-transformer load paths, GUI or chat behaviour, `/nekojs reload`. It complements unit tests and `runGameTestServer`; it does not replace them.

The bridge picks the mod's port itself (it scans 9876→9000 on every call) and finds whichever client is running. Never hardcode a port, and never point an MCP/SSE client straight at the mod — that HTTP server does not speak MCP.

See `docs/agents/minecraft-mcp.md` for the launch recipe, the mod set a NekoJS client needs, and the gotchas.
