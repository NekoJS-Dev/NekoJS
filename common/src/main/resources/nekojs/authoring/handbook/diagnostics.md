# Diagnostics

Start with the actual failure, script path, line, Script Type, environment and stable NEKO diagnostic code. Use `/nekojs view_all_errors` and the corresponding `logs/nekojs` output. Preserve the original cause while investigating.

Distinguish parse/transpile failures, declaration errors, runtime exceptions, conversion failures and world-specific validation. An empty authoring query with incomplete coverage does not prove that a member is missing.

Read-only authoring queries do not grant access to arbitrary logs, files, guest evaluation or live inspection. In-mod MCP can separately expose explicitly opted-in [live controls](live-controls.md) for allowlisted server commands and full SERVER/CLIENT/TEST reload; stdio never can. Request the relevant diagnostic text through the author's normal workflow.
