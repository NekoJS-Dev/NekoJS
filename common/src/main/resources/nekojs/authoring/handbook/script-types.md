# Script types

| Directory under nekojs | Script Type | Loading and validation |
|---|---|---|
| startup_scripts | STARTUP | Runs at process startup; restart the game for registration changes. |
| server_scripts | SERVER | Loads with the server/world; use `/nekojs reload server`. |
| client_scripts | CLIENT | Client-only APIs; use `/nekojs reload client` on an integrated client. |
| test_scripts | TEST | Explicit tests; use `/nekojs test`. |

JavaScript, erasable TypeScript, JSX/TSX, CommonJS and a Python subset are supported by the corresponding compiler. TypeScript type checking is a separate step from runtime loading. Python is not CPython; consult the generated Python stubs and verify supported syntax.

For complete scenarios using these directories, CommonJS modules, state, timers and TEST assertions, see [scripting examples](scripting-examples.md).

`global` shares memory within one Script Type across ordinary reloads. `shared` shares memory across Script Types in the same runtime. Neither provides network synchronization or save persistence.

A failed transactional candidate preserves the active script generation. Arbitrary game, Java, network, or file effects are outside the managed rollback guarantee. Startup scripts cannot be rerun with `/nekojs reload startup`.

Opening `//` header directives (`priority`, `modloaded`, `after`, `disable`) are read only before the first code or non-`//` comment line. An invalid known value emits one `NEKO-1025` warning with its file, physical line and cause, retaining the last valid value or the default; a later valid directive can still replace it. Unknown property names are ignored. Put integer-priority explanations on separate comment lines. `disable` is presence-based, even when its text says `false`.

TEST reports separate `executionCompleted`, `executionSucceeded`, `passed`, `failed`, nullable `assertionsPassed`, and `overallPassed`. Counts belong to one attempt, not the shared binding’s lifetime. A caught failed assertion still makes `assertionsPassed: false`; zero observed assertions give `null`, not a vacuous pass. Completion covers the existing run/drain window, not future timers/events. Query the latest finished report through live `context.testRunReport`; see [runtime results](query-api.md#live-runtime-results-in-mod-only).

Script reload commands report results through chat and logs. NekoJS does not draw an on-screen script reload progress bar.
