# Community proposals against stonecutter

- Scope: read-only investigation of issues 60 and 63 and PRs 61, 65, and 66; no implementation, merge, or GitHub mutation.
- Baseline: current stonecutter HEAD; record exact commit in final report.
- Ownership: root owns this plan and aggregate report; compiler agent investigates PR 61; lambda agent investigates issue 60 / PR 65; declaration agent investigates issue 63 / PR 66. Agents return findings without editing tracked files.
- Checks: inspect PR diffs, current entry points/callers/tests, and targeted reproducible checks where useful. Preserve existing worktree changes.
- Baseline commit: ec7fe6848afc1970661ee1f38a7c7c6d4575530c.
- Status: three GPT-6 Luna xhigh investigations and root verification completed; findings in REPORT.md. No production or official test source edits.

## Executed checks

```powershell
./gradlew.bat :common:check --console=plain
./gradlew.bat :common:test --tests '*TypeScriptErasureParseCorpusTest' --tests '*NekoTypeScriptCompilerTest' --tests '*NativeSamCallbackTypesTest' --tests '*TypeReflectorTest' --tests '*TypeScriptClassRendererTest' --tests '*PluginHookPairingTest' --tests '*ProbeBackend*' --tests '*Catalog*Test' --tests '*TypeDocs*Test' --tests '*IndexFileGeneratorTest' --console=plain
npm.cmd run test:probe-types
powershell -File docs/architecture-refactor/evidence/2026-10-08-community-stonecutter/audit-pr61.ps1
java docs/architecture-refactor/evidence/2026-10-08-community-stonecutter/Pr65SamAudit.java
git diff --check
```

The corpus audit reports 44 passes / 19 failures / zero skips; its successful Gradle exit means the investigative runner executed, not that the corpus passed. The current-branch focused test suite reports 168 tests / zero failures / zero skips. The full common check reused up-to-date test output and executed isolation verification. Probe type checking passed. No proposed implementation has been installed or verified.

For the invalid for-of fixture, run local TypeScript 5.8.3 with `--noEmit --pretty false --skipLibCheck` on `declare const items: string[]; for (const item: string of items) {}`. The expected diagnostic is TS2483 and exit code 2.
