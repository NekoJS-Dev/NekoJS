# Web to NekoJS JSX Conversion

AI-assisted conversion of controlled web input (HTML + CSS intent + interaction spec) into NekoJS
JSX UI code. This directory holds the ticket 46 conversion contract deliverables (input contract,
mapping tables, fixtures, conversion reports); the ticket 47 authoring contract in
`../architecture-refactor/ai-authoring-contract.md` consumes them.

- **Input + mapping contract**: [web-to-jsx-cookbook.md](web-to-jsx-cookbook.md) — §1 is the
  normative input contract; the sections below it are the HTML/CSS/interaction → controlled-props
  mapping tables and downgrade rules.
- **Contract first**: read [../architecture-refactor/ai-authoring-contract.md](../architecture-refactor/ai-authoring-contract.md) — output structure, self-check, unsupported report format, Inspector correction protocol.
- **Fixtures**: representative web inputs, their controlled JSX conversions, and machine-readable conversion reports.

## Files

| File | Role |
|---|---|
| `web-to-jsx-cookbook.md` | Ticket 46 input contract (§1) + mapping tables and downgrade rules |
| `fixtures/login-form.html` | Web input: labeled form, validation, submit/cancel, loading |
| `fixtures/login-form.output.tsx` | Conversion output (signal-driven controlled form) |
| `fixtures/login-form.conversion-report.json` | Conversion report (mapping decisions + verification record) |
| `fixtures/card-grid.html` | Web input: product grid with hover intent and media query |
| `fixtures/card-grid.output.tsx` | Conversion output (store-driven card list) |
| `fixtures/card-grid.conversion-report.json` | Conversion report (mapping decisions + verification record) |

## Verification

Both `.output.tsx` files are executed (mounted against a fake host, driven by event dispatch,
resized across viewport profiles) by `common/src/test/resources/nekojs/language-ts-examples/tsx/ui-authoring-docs-proof.tsx`
under `common/src/test/java/com/tkisor/nekojs/core/module/TypeScriptUiAuthoringDocsTest.java` —
part of `./gradlew :common:check`. Editing an output without keeping its behavior correct fails CI.
Both conversion reports are validated against the machine-readable report contract (input-checklist
linkage, severity coverage, verification record) by
`common/src/test/java/com/tkisor/nekojs/core/module/WebConversionReportContractTest.java`.
