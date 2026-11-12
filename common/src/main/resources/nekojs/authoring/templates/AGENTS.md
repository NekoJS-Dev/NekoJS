# NekoJS scripting workspace

- Before writing or repairing a script, read output.directory from nekojs/config/ai.toml as data (default .github/neko_ai if the file or setting is absent). Resolve that directory against the game directory, then read environment.json and current.json under that AI root. Resolve the active bundle from current.json and confirm Minecraft, loader, Script Type and language.
- Read the active bundle's skills/nekojs-scripting/SKILL.md for script changes, KubeJS migrations and JSX UI work. If the client cannot install Skills, read that Markdown file directly.
- For MCP access while the game runs, read handbook/connections.md: explicitly enable in-mod MCP, then give the client its URL and bearer credential; no helper JAR/game-directory arguments are needed. For offline snapshots or a separate process, use the Java 21+ helper described in handbook/stdio.md.
- For unfamiliar calls, read handbook/query-api.md in the active bundle and use the current instance's API snapshot or final declarations. Treat incomplete coverage as uncertainty.
- Use Event Groups for subscriptions, named Bindings for helpers, configuration-last callbacks for Builders, and readable/writable Bean properties as declared.
- For runnable starting points, read handbook/scripting-examples.md; copy only the chosen scenario to its documented Script Type directory and follow its trigger and expected-result checks in a disposable instance.
- Use the relevant branch handbook for events, adapters, recipes, startup registration, packs or client UI. Match the current environment rather than borrowing unverified APIs from another engine.
- For structured errors, TEST outcomes or a timed-out action, read handbook/query-api.md#live-runtime-results-in-mod-only. Query the admitted operation ID before considering another action; completion is not an assertion pass.
- Follow handbook/validation.md after changes. Report the changed behavior, verification results and remaining gaps. Startup registration requires a process restart; runtime effects need actual game evidence.
- Preserve unrelated scripts and author-owned files. Maintain signed packs through their source and normal packaging/signing workflow.

- Before any command/reload tool use, read handbook/live-controls.md in the active bundle. Require explicit user authorization and operator opt-in. Never interpret a running timeout as rollback or blindly retry. Offline stdio cannot mutate the live game.

NekoJS creates this entry under the selected AI root only when missing. It is not automatically workspace-wide instructions; explicitly configure the client to read it. Role routers under agents/ are not automatically discovered VS Code agents; the standard .github/agents bridge is opt-in via output.editor_agents. The versioned bundle always contains the current templates/AGENTS.md for manual comparison. No AI client global settings are changed.
