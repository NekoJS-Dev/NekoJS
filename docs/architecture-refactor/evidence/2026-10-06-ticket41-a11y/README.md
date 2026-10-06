# Ticket 41 accessibility smoke (2026-10-06)

Environment: isolated Minecraft 26.2 / NeoForge 26.2.0.75 / Zulu Java 25 / MCP 0.4.2.

## Observed

- Client entered a real single-player world in an isolated profile.
- Delayed accessibility fixture opened after 100 client ticks.
- Screen screenshot shows healthy button, disabled button, input value, hide/show control and help text.
- CLIENT reload and fixture-open log lines are preserved in `client.log`.
- Tab key events were injected and a post-tab screenshot was captured.

## Limits

- MCP widget discovery reports the active screen as `LocalPlayer` and no native widgets because JSX nodes are retained host nodes, not vanilla widgets.
- MCP coordinate clicks did not produce a callback log in this run; no successful/disabled callback result is claimed.
- Native narrator audio/output was not directly observable through this MCP session. Ticket 41 AC4 remains open.
