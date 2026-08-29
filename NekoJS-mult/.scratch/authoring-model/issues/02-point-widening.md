# 02 十四个内置点收集面放宽

spec: `.scratch/specs/plugin-authoring-model.md` §Implementation 2。依赖票 01（collector 引基接口方法）。

## 改动
14 个 Point（common/.../core/plugin/）：`builder(ID, Contributor.class)` → `builder(ID, NekoJSPlugin.class)`；`Contributor::xxx` → `NekoJSPlugin::xxx`；POINT 泛型首参同步。Contributor 接口全部保留（显式形态，现有 V2 插件零破坏）。

- EventsPoint → registerEvents
- ClientEventsPoint → registerClientEvents（clientOnly + dependsOn 不动）
- BindingsPoint → registerBinding（ScriptType 过滤 lambda 不动）
- AdaptersPoint → registerAdapters
- TypeDocsPoint → registerTypeDocs
- NodeTypeDocsPoint → registerNodeTypeDocs
- NodeModulesPoint → registerNodeModules
- ScriptCompilersPoint → registerScriptCompilers
- ScriptPropertiesPoint → registerScriptProperty
- RecipeNamespacesPoint → registerRecipeNamespaces
- RecipeSchemasPoint → registerRecipeSchemas
- RecipeLifecyclePoint → registerRecipeLifecycleHooks
- LifecyclePoint → registerLifecycleHooks
- ProbeBackendsPoint → registerProbeBackends

## 验收
- 14 文件各 ~2 行 diff；:common 编译绿；既有测试（NekoPluginBootstrapV2Test 等）不红
