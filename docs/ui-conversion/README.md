# Web to NekoJS JSX Conversion

AI-assisted conversion of controlled web input (HTML + CSS intent + interaction spec) into NekoJS
JSX UI code. This directory is a ticket 47 deliverable.

- **Contract first**: read [../architecture-refactor/ai-authoring-contract.md](../architecture-refactor/ai-authoring-contract.md) — input checklist, output structure, self-check, unsupported report format, Inspector correction protocol.
- **Cookbook**: [web-to-jsx-cookbook.md](web-to-jsx-cookbook.md) — mapping tables and downgrade rules.
- **Fixtures**: representative web inputs, their controlled JSX conversions, and machine-readable conversion reports.

## Files

| File | Role |
|---|---|
| `web-to-jsx-cookbook.md` | HTML/CSS/interaction → controlled-props mapping tables |
| `fixtures/login-form.html` | Web input: labeled form, validation, submit/cancel, loading |
| `fixtures/login-form.output.tsx` | Conversion output (signal-driven controlled form) |
| `fixtures/login-form.conversion-report.json` | Conversion report |
| `fixtures/card-grid.html` | Web input: product grid with hover intent and media query |
| `fixtures/card-grid.output.tsx` | Conversion output (store-driven card list) |
| `fixtures/card-grid.conversion-report.json` | Conversion report |

## Verification

Both `.output.tsx` files are executed (mounted against a fake host, driven by event dispatch,
resized across viewport profiles) by `common/src/test/resources/nekojs/language-ts-examples/tsx/ui-authoring-docs-proof.tsx`
under `common/src/test/java/com/tkisor/nekojs/core/module/TypeScriptUiAuthoringDocsTest.java` —
part of `./gradlew :common:check`. Editing an output without keeping its behavior correct fails CI.
