# 04 PluginHookPairingTest 架构单测

spec: `.scratch/specs/plugin-authoring-model.md` §Implementation 5。依赖票 02/03。

## 改动
- 新增 `common/src/test/java/com/tkisor/nekojs/core/plugin/PluginHookPairingTest.java`：
  1. 14 个内置 POINT 的 `pluginType()` 全为 `NekoJSPlugin.class`
  2. 基接口收集型钩子集合与 14 点双射（反射取 declared methods，回调族豁免清单：init 族×5、attach×3、generate×3、modifyWorkspaceConfig、beforeRecipeLoading、afterRecipes、registerApiSurface）
  3. 多余的 `register*` 方法（无 Point 配对）→ 红，强制走豁免清单显式决策

## 验收
- 测试绿；人为加一个无配对钩子能变红（本地验证后撤掉）
