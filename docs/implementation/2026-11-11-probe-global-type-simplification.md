# Global Probe TypeScript declaration simplification

## Scope and preserved contracts

This change simplifies the shared TypeScript declaration pipeline, not a recipe name, addon, Minecraft class or mod profile. Existing public overloads, parameter names, optionality, return types, Java host identity and guest adapter inputs remain the contract. No runtime conversion or recipe engine is added.

## Shared implementation

- The [collection registry](<../../common/src/main/java/com/tkisor/nekojs/probe/types/TypeAliasRegistry.java>) distinguishes using an existing input alias from defining that alias. Native arguments reuse canonical List_, Collection_, Iterable_ and Map_ spellings only when host and guest type arguments match and the builtin arity is valid. The definition path stays expanded, avoiding recursive alias bodies. Set remains host-only.
- The [package export catalog](<../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/IndexFileGenerator.java>) includes the builtin fallback collection aliases in symbol allocation and module-local collision handling. A real-backend regression verifies imports and actual definitions; manual module stubs alone did not cover this path.
- Both the [neutral type renderer](<../../common/src/main/java/com/tkisor/nekojs/probe/ir/TypeScriptClassRenderer.java>) and [reflection/SAM input renderer](<../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/FunctionalInterfaceAliasGenerator.java>) use that same preference. A host List of an adapted type is not widened to a host List of its input alias. Known exact string/number/symbol map keys remove redundant PropertyKey/Extract guards; unknown, mixed and generic keys retain them. Partial records remain partial.
- [Array expression grouping](<../../common/src/main/java/com/tkisor/nekojs/probe/types/TypeScriptTypeExpressions.java>) removes parentheses only for recognized primary or fully grouped types. Functions, type operators, unions, intersections and opaque/template expressions stay conservatively grouped. This is not a complete TypeScript parser or a regex rewrite over declarations.
- [Callable formatting](<../../common/src/main/java/com/tkisor/nekojs/probe/types/TypeScriptDeclarationFormatter.java>) consumes already-rendered parameter fragments. Ordinary class/interface methods, constructors, authored overloads, managed modules, events and recipe declarations use the same 120-column soft layout with four-space continuation indentation. A single large parameter/head/result can exceed the target; opaque pre-rendered method fragments are not reparsed.
- Captured collection host carriers use the registry's structural host projection. They must not depend on replacing an expanded host string inside an input alias. During validation, the TypeScript AST comparison caught four missing addAll redeclarations after alias reuse; that regression was not accepted as a golden change and receives a dedicated real-backend test.

## Verification plan and observations

Initial global RED captured four failures out of six projection assertions. Shared projection/formatter/array tests then passed. The strict global fixture covers ordinary methods, callback aliases, unrelated native handlers, scoped import names, old-expanded/new-compact bidirectional assignability and invalid host generic values. A missing callback dependency in the test fixture initially produced any; its real declaration closure was completed and the negative assertion retained.

Canonical goldens are regenerated only with the existing Gradle task. The pre-change tree is snapshotted under build reports. All 58 file paths remain present. The final canonical comparison changes 26 files: 3 have identical non-trivia TypeScript tokens (layout only), and 23 contain the intended alias/parenthesis simplifications or newly required canonical alias imports. The TypeScript parser reports no syntax errors, and callable inventories preserve names, generic names, parameter names/order, optionality and rest markers after the structural carrier correction. The 15 platform event baseline paths also remain unchanged; thirteen event files change only whitespace, with identical non-trivia TypeScript tokens and callable inventories. Full strict assignability/negative-call fixture checks pass. A full-suite run exposed the previously incomplete package export catalog; its shared fix is covered by the real-backend alias-import regression. The complete post-fix normal Gradle matrix passed in 9m 46s (123 actionable tasks). The common suite contains 2952 tests, zero failures and four existing skips. All five platform builds, isolated 1.21.1 native recipe validation, and addon build/fixture/expanded/proof tasks passed. The 1.21.1 native check executes six cases with no skips and validates shaped/inline JSON through the vanilla serializer. The final full TypeScript suite passed sequentially after Java fixture generation, including recipe and Mixin language-service completions, native handler strict calls, and the new global old-expanded/new-compact assignment fixture. A final fixture-only correction emits the registry-generated List_ body in the pre-existing native-handler nominal module; all positive and negative calls remain unchanged. After that correction, :common:check and guardLint passed again in 4m 23s with 2952 tests, zero failures and four existing skips. No production source changed after the successful full platform/addon matrix.

## Commands


Focused checks use the common test task; full acceptance uses:

~~~bash
./gradlew --no-daemon :common:check guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build :addons:nekujs:build :addons:nekujs:fixtureTest :addons:nekujs:expandedFixtureTest :addons:nekujs:compatProofTest --console=plain
pnpm run test:probe-types
~~~

The isolated 1.21.1 native recipe check is a dependency of node check/build and generates fresh declarations. Modern bare-JVM native integration still requires an active FML Loader and is explicitly reported as unavailable, not fabricated as a pass. Live game/world acceptance and edits to existing instance Probe output are not performed. Restart with the rebuilt artifact and export Probe again to refresh live declarations.
