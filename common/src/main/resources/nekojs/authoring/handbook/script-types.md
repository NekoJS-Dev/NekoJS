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
