# Diagnostics

Start with the actual failure, script path, line, Script Type, environment and stable NEKO diagnostic code. Use `/nekojs view_all_errors` and the corresponding `logs/nekojs` output. Preserve the original cause while investigating.

Distinguish parse/transpile failures, declaration errors, runtime exceptions, conversion failures and world-specific validation. An empty authoring query with incomplete coverage does not prove that a member is missing.

Normal script execution has no module-evaluation deadline, statement budget or runaway watchdog. The engine settings `scriptEvaluationTimeoutSeconds`, `scriptStatementLimit` and `scriptRunawayTimeoutSeconds` were removed, not replaced by a default timeout. An unresolved top-level await can remain pending; infinite guest/host loops may block the owner thread or process. Investigate the actual promise, code path and host call rather than recommending retired budget settings.

`NEKO-2001`, `NEKO-2002`, `NEKO-2003` and `NEKO-2005` are historical diagnostics whose automatic budget call sites were removed. `NEKO-2004` still limits log output, not execution. The structured phase `WATCHDOG` and owner `Runaway Watchdog` are retained compatibility labels for cancelled, interrupted or resource-exhausted Polyglot contexts; they do not prove an automatic watchdog exists.

Request deadlines, lifecycle cancellation/shutdown and the private offline Mixin declaration sandbox's fixed 100,000-statement limit remain separate. Not every resource limit or request wait was removed.

Read-only authoring queries do not grant access to arbitrary logs, files, guest evaluation or live inspection. In-mod MCP can separately expose explicitly opted-in [live controls](live-controls.md) for allowlisted server commands and full SERVER/CLIENT/TEST reload; stdio never can. Request the relevant diagnostic text through the author's normal workflow.
