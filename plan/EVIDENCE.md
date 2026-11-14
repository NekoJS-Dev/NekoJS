# 历史规划验收依据

状态：已被[拆分索引](README.md)和[当前 #69 修复票](issue-69-fix.md)替代。以下 E1–E8 保留原文，只描述旧合并文档的验收依据／当时事实，不是当前实现验收。历史文档命令结果见 [P0-EXEC](P0-EXEC.yml)，不能沿用其中的 pass。当前代码修复、回归和完整检查结果以[实施验证记录](issue-69-verification.md)为准，人工验收仍未取得。

Scope: extend the Chinese plan with built-in JS preflight extraction, third-party preflight registration, and compatible frontend/checker/probe composition. This delivery changes documentation only.

| ID | Kind / tag | Evidence or acceptance rule |
| --- | --- | --- |
| E1 | CONVENTION ✅可命令 | Keep seven Chinese sections and twenty-four required plugin/composition contract markers. Check C1. |
| E2 | FACT ✅可命令 | All twenty-nine local references in the revised plan must resolve. Check C2. |
| E3 | EDGE ✅可命令 | No tracked production changes; restrict additions to planning artifacts and preserve the pre-existing untracked diagnostic file. Check C3. |
| E4 | CONVENTION ✅可命令 | Parse the proposed TOML into two extension pipelines, each with frontend, sourceChecks, outputChecks and an existing-identity Probe reference. Do not emit a languageFrontends alias. Check C4. |
| E5 | FACT ✅可命令 | ProbeConfig already supports per-language backend selection. ProbeBackendRegistry identifies backends by (languageId, name), and both installed built-in targets use backend name builtin. Source inspection, not a new implementation result. |
| E6 | FACT ✅可命令 | ProbeBackendSelector currently fixes the no-argument target to typescript:builtin, falls back for an unavailable old language preference, and selects every backend for all. The proposed precedence is an explicit contract change, not current behavior. |
| E7 | FACT ✅可命令 | Historical current-source JShell probes reproduced comment splitting and raw Python misparsing. Implementation tests and game acceptance remain planned/not run. |
| E8 | CONVENTION ✅可命令 | The new preflight Point/Hook/Contributor follows existing pairing and runtime ownership conventions. Public contracts remain in api.*, while parsers, validators and caches stay internal. |

No visual criteria are required. The old configuration example and targeted test command change intentionally: the new plan replaces a frontend-only draft with pipeline composition and adds plugin/Probe regression coverage. All production features remain proposed.
