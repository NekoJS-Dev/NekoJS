# Probe linkage source and derived-baseline review

Baseline `87e9b8633bbfcce04eb538dcd8e6dce12c08e2e6`; the maintainer subsequently accepted local golden submission on2026-10-09. The production patch SHA256 is `9b70e7439799f09ebf2c7fc2f551cd4e39da4118e62b861b27444e6c9715ea6f`. Standards and Spec reviews used the code-review skill in independent read-only agents. Reviewers did not edit, build or start games.

## Standards

0 confirmed violations,0 actionable smells. Shared collection/IR/event declaration changes preserve common isolation, default scan filters and host access. NEKO-4029 carries failed-type/cause context and reaches every backend result. Both bilingual error references are updated. Fatal VM/ThreadDeath propagation and restored interruption are preserved.

The reviewer identified an untested cleanup boundary: fatal shared reflection previously left other submitted Futures queued. A controlled single-thread regression actually failed before the correction. The owner now cancels submitted Futures before propagating fatal errors or interruption; all five focused tests pass. Follow-up review confirms this boundary is covered. Cancellation requests interruption; it does not forcibly terminate arbitrary running reflection code.

## Spec

0 confirmed implementation findings. Available output continues after per-type reflection failures, with explicit partial-generation warnings. Missing declarations are not represented as complete success. The event file imports only its direct event/key symbols; the shared bounded scan and package declarations own member dependencies. No manual producer, public entry, supported node, sandbox, path check or gate was removed.

## Generated differences and acceptance

Explicit existing workflows regenerated20 event golden files: the common regeneration task and all five node platformGateTest tasks with the existing regenerate switch. Ordinary gate runs then used no regeneration flag. The regeneration occurred with the owned bug-fix WIP present, rather than the workflow's generic clean-tree precondition, so that canonical source changes could drive the output; unrelated user files were never staged or modified.

Both the responsible agent and the Spec/Standards reviewers independently compared every changed file after removing import lines. All remaining text is identical: event namespaces, signatures, payloads and dispatch overloads are unchanged. Import lines decrease5162→230; only unused transitive imports are removed. The exact old/new patch and per-file hashes are retained in the evidence package. There is no public Script/Plugin/data/wire migration in these baseline changes.

Maintainer golden conclusion: **ACCEPTED for local submission**. The direct user reply, date and exact scope are recorded in [MAINTAINER-ACCEPTANCE.md](MAINTAINER-ACCEPTANCE.md), fulfilling [REGENERATE.md §3](../../baseline/2026-09-12-managed-surface/REGENERATE.md#3-旧新-diff原因影响与审阅记录必填). Agent review and passing tests remain distinct from that human conclusion. This does not reopen previously authorized A22/A28 removals or imply overall release acceptance.

Final verification by the responsible agent: complete five-node matrix PASS106tasks/1m51s,790ordinaryXML/4592tests/271skipped/0failures/errors; npm test:probe-types PASS. Actual rebuilt legacy server produces334TS files and319Python files/317AST-valid stubs, with unavailable type warnings and normal RCON shutdown. Whole strict TS/Pyright remain separate unresolved gates; no full IDE PASS is inferred.
