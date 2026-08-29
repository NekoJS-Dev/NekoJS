# 01 NekoJSPlugin 移回 common + 恢复 18 个收集钩子

spec: `.scratch/specs/plugin-authoring-model.md` §Implementation 1、3

## 改动
- `common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java` 新建（内容自 common-api 移入），恢复 18 个 default 钩子（签名与 V1 树 `D:/mcmodDemo/NekoJS/common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java` 逐字一致）：
  registerEvents / registerClientEvents / registerBinding / registerAdapters / registerScriptCompilers / registerScriptProperty / registerTypeDocs / registerNodeTypeDocs / registerNodeModules / registerProbeBackends / registerRecipeNamespaces / registerRecipeSchemas / registerLifecycleHooks / registerRecipeLifecycleHooks / generateData / generateAssets / generateLang / modifyWorkspaceConfig
- javadoc 按"注册面（收集型）/ 回调面（直调型）"重组，"我想做 X"导向
- 删除 `common-api/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java`；`@RegisterNekoJSPlugin` 留守 common-api

## 验收
- :common-api :common 编译绿，common-api 无 NekoJSPlugin 残留引用
- 18 钩子签名与 V1 对照一致（参数类型 FQCN 全在 common/common-api，已核实）
