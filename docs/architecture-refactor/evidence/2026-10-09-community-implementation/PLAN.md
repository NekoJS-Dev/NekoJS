# Community implementation

Baseline: 3783fadb4b1701971c3d443c0c13c43ee1c46aa5 (stonecutter).

Scope: implement the reviewed TS corpus fixes, functional-interface input declarations, and plugin-authored class declaration replacement; validate and commit focused changes. No GitHub mutation.

Pre-existing changes: Probe declaration resources initially appeared modified, but both textual and binary diffs were empty (line-ending/status noise). The saved patch contained zero bytes. Only the deliberate generated content changes belong to this task. Existing untracked memory/tool directories, the preceding investigation, and ticket-37 screenshots are retained outside this commit.

Ownership: compiler agent owns NekoTypeScriptCompiler and compiler corpus tests; lambda agent owns SAM shared analysis / callback validator refactor and Probe lambda alias pipeline / reflector / renderer / aliases / focused lambda tests. Root owns authored declaration API / Point snapshots / runtime and catalog / backend authored integration / package generation integration / docs, verification, golden review, staging and commits. Backend and Index generator overlap is sequential: lambda agent finishes first, root then adds authored integration. Agents do not run Gradle or modify goldens.

Contracts: functional aliases accept Java instances and functions; callback arguments retain exact host types, returns accept input values. Authored class replacements are global because package declarations are shared; no ScriptType predicate is exposed. Common/node type docs use the existing Point channel; equal-priority FQN collisions fail; explicit imports join class collection; hidden classes remain hidden.

Status: implementation and verification complete. Focused compiler/Probe/native callback checks and actual TypeScript positive/negative checks passed. Golden content reviewed and regenerated through the existing task. Final common/processor/isolation/guard checks and all five node compilations passed; REPORT.md records the results and remaining acceptance gaps. Changes are ready for the authorized local commit.
