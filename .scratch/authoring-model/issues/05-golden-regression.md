# 05 golden 再生成 + 全量回归

spec: `.scratch/specs/plugin-authoring-model.md` §Implementation 6。依赖票 01-04。

## 改动
- ApiManifestGoldenTest 基线再生成（`-Dnekojs.golden.regenerate=true`）+ diff 人工评审：只允许出现 18 个恢复钩子与 NekoJSPlugin 模块位移，不允许既有签名漂移
- 全量回归：四节点编译 + 全部测试 + guardLint（既有验收门禁）

## 验收
- diff 评审通过；全绿
