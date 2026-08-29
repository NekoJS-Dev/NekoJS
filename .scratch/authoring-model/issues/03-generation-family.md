# 03 GenerationPoint 族修正

spec: `.scratch/specs/plugin-authoring-model.md` §Implementation 4。依赖票 01（钩子在基接口）。

## 改动
- `PluginGenerationHooks.java`：去 `instanceof GenerationPoint.Contributor` 过滤 → 全员调用（try/catch 隔离已有）
- `WorkspaceGenerator.java:93`：同样去过滤
- 删除 `GenerationPoint.java`（POINT 从未注册，属死定义；钩子已上基接口；分类知识"与 registerApiSurface 同类，不做收集式扩展点"并入基接口 javadoc）
- 更新受影响测试：`PluginGenerationHooksTest`、`WorkspaceGeneratorModifyConfigHookTest`（fake Contributor → 基接口覆写）

## 验收
- 全仓无 GenerationPoint 引用残留；两个测试绿
