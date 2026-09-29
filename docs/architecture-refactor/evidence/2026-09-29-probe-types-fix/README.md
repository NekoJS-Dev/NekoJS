# Probe declaration typecheck fix (ticket 34 / F1)

Branch `probe-types-fix` (base `mult@ca52f90f`), 2026-09-29. Closes P4 failures-ledger entry F1:
`npm run test:probe-types` (CI step "Typecheck Probe declarations") failed with 14 errors.

## Diagnosis

The 14 errors (1x TS2307, 1x TS2875, 9x TS7026, 3x TS2578) are all in
`common/src/test/probe-ts/jsx-primitive-props.tsx`. They are **not** a defect in the engine's JSX
declaration — `NodeModuleTypeDocs.extractTS(jsx-runtime.ts)` produces a correct ambient
`declare module 'nekojs/jsx-runtime'` containing `export namespace JSX { interface IntrinsicElements … }`,
and with that declaration in the program the gate is fully green (including every `@ts-expect-error`
negative assertion).

The defect is **where the declaration comes from**: the probe tsconfig included
`../../../build/probe-ts/generated/**/*.d.ts`, i.e. an ignored build-directory artifact whose only
producer is the JUnit side effect in `NodeModuleTypeDocsTest#extractsJsxNamespaceForProbeTypeCheck`
(ticket 40). `npm run test:probe-types` cannot produce that artifact, so the documented gate fails
on any tree where `:common:test` has not run first. The P4 pack reproduced exactly this state (its
run 01 ran five-node build gates, which never execute `:common:test`, so run 02 saw no artifact).
CI's step order (`:common:check` before the typecheck) happens to mask it, leaving the gate
order-dependent rather than correct; the failures-ledger reproduction and the golden-decl-prep §6
note both record the standalone failure. (Note: the task brief cited
`language-ts-examples/tsx/ui-core.tsx` as the failing file; that fixture is not part of the probe
program — the ledger's file, the probe fixture, is the real location. No tsx fixture is touched.)

## Fix (seam: committed golden + tsconfig include + drift-guard test)

1. **`common/src/test/probe-ts/generated/jsx-runtime.d.ts` (new, committed golden)** — the
   extraction output, generated through the existing regeneration workflow
   (`-Dnekojs.golden.regenerate=true`), verified byte-identical to both extraction actuals.
   The already-present `generated/**/*.d.ts` tsconfig include picks it up, so the ambient module
   resolves on a fresh checkout with no gradle preamble.
2. **`common/src/test/probe-ts/tsconfig.json`** — dropped the `../../../build/probe-ts/generated`
   include. This removes the order dependence (and the risk of the same ambient module entering the
   program twice). The build artifact itself is still produced by `NodeModuleTypeDocsTest`,
   unchanged, as the inspection output referenced by `ai-authoring-contract.md`.
3. **`common/src/test/java/com/tkisor/nekojs/probe/JsxRuntimeProbeDeclarationGoldenTest.java` (new)**
   — keeps the committed golden byte-identical to `extractTS(jsx-runtime.ts)`, mirroring
   `ProbeTypeScriptFixtureWriterTest` (same CRLF normalization, same regenerate switch, actuals to
   `build/probe-ts-actual/`). It lives in `com.tkisor.nekojs.probe.*` so the documented
   `:common:regenerateGoldens` entry covers it.
4. **`docs/architecture-refactor/ai-authoring-contract.md`** — fact-source rows updated: the
   committed jsx-runtime golden is now the surface the probe gate typechecks against.
5. **`.gitignore`** — `node_modules/` (the gate's locked dependency tree; `npm ci` output was
   previously untracked noise).

### Why not `jsx-runtime.ts`

No declaration is missing from the engine module — its extraction typechecks the probe fixture
perfectly. The break is purely the artifact-delivery seam, so the engine module is untouched. This
also keeps the sibling session's uncommitted edits to `jsx-runtime.ts` / `JsxHostAdapter.java`
trivial to merge: if their change alters the extracted declaration, the new golden test fails by
design and the golden is regenerated through the documented workflow as part of that change.

## Verification

| Check | Result |
|---|---|
| `npm ci` (fresh) | pass |
| `npm run test:probe-types` baseline reproduction | 14 errors, exit 2 (matches F1 / pack run 02) |
| `npm run test:probe-types` after fix, artifact present | pass, exit 0 |
| `npm run test:probe-types` after `rm -rf common/build/probe-ts` | pass, exit 0 (order-independent) |
| `JsxRuntimeProbeDeclarationGoldenTest` assertion mode (`--rerun`) | pass |
| Negative control (perturbed golden, `--rerun`) | FAIL as designed; restored → pass |
| Golden generated via regen switch; identical to both actuals | verified (`diff` clean) |
| `:common:regenerateGoldens` | pass; probe goldens rewrite is line-ending-only churn on CRLF checkout (pre-existing), reverted |
| `./gradlew :common:check :common-api-processor:test` | pass (BUILD SUCCESSFUL) |
| `./gradlew guardLint` | pass (BUILD SUCCESSFUL) |

Not run: the five-node builds and real-machine legs — out of scope for this gate fix; no engine
runtime behavior changed (the tsx fixtures and all engine resources are byte-identical).

## Files

- `README.md` — this document
- `transcript.txt` — captured commands and outcomes

## Maintainer ask

Approve merging `probe-types-fix`: it turns the F1 probe gate into a fresh-checkout-green check
committed golden + drift guard), with `jsx-runtime.ts` untouched.
