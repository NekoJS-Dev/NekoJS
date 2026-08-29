# 插件作者面双形态模型：基接口门面 + Point 事实源

ADR-0001/0002 把插件注册面摊平成 per-channel Contributor 后，作者注册插件要翻十几个 Point 文件、implements 一串接口；wiki《插件开发》的钩子一览（单接口形态）与实现脱节。V1 的病根是胖接口无事实源膨胀（25 个方法堆在接口上）+ 万能 `NekoPluginExtensionContext` 耦合；V2 的新问题是作者上手面。裁决（grilling 2026-08-29）：**作者体验 V1 优先，同时保留 NekoJS 开发体验，两者共存**。本 ADR 定实现模型：

1. **双形态**：每条通道的**事实源**是自包含 Point 文件（MergePolicy / initializer / finisher / dependsOn 全在点内，ADR-0001 语义不变）；`NekoJSPlugin` 基接口钩子是各点的**门面投影**。覆写基接口钩子（推荐、最简）与 implements `XxxPoint.Contributor`（显式形态）由同一个点收集，效果完全等价。
2. **收集面放宽**：14 个收集型内置点的 `pluginType()` = `NekoJSPlugin.class`——所有插件被收集、未覆写即 default no-op（V1 收集宽度回归，成本可忽略）。`Contributor` 接口全部保留，存量 V2 插件零改动。
3. **配对协议（机械化）**：加收集型通道 = 三件套（Point 文件 + 基接口 default 钩子 + `NekoBuiltinPointsPlugin` 清单一行）+ `PluginHookPairingTest` 配对表登记；基接口出现无 Point 配对的 `register*` 方法即 CI 红。回调族直调钩子（`init` 族 / `attachXxxData` / `generateXxx` / `modifyWorkspaceConfig` / `beforeRecipeLoading` / `afterRecipes`）不以 `register` 开头，天然在守卫外；`registerApiSurface` 为直调豁免的唯一 `register*`。
4. **模块归位**：`NekoJSPlugin` 从 common-api 移回 common——18 个恢复钩子的参数类型全在 common，反向搬 14 个类型会级联拖出内部依赖；单文件移动、FQCN 不变、common-api 无反向引用（已核实）。`@RegisterNekoJSPlugin` 留守 common-api。
5. **GenerationPoint 撤销**：`POINT` 从未注册（死定义）；`generateData`/`generateAssets`/`generateLang`/`modifyWorkspaceConfig` 在 `PluginGenerationHooks` / `WorkspaceGenerator` 直调（去 `instanceof Contributor` 过滤，全员调用，异常隔离保留）。分类知识并入基接口 javadoc。
6. **V1 shim 层不做**：`of(...)`/`clientOnly(...)` 工厂、Context 废弃存根、`NekoPluginExtensionRegistry.register` 的 void 回退全部取消——插件作者存量为零（预发布期），无服务对象；第三方自定义扩展点一律 V2 builder（文档给示例）。`register` 维持 handle 返回：产物访问（`isFinished()` 守卫 + 类型安全读取 + reload 可重入）是 PR37 作者本人要的形态（MINECRAFT_REGISTRY.md §2.2 原话）。
7. **作者面签名守卫**：`PluginHookPairingTest` 冻结 15 个 `register` 钩子的参数类型表。注意分工：`ApiManifestGoldenTest` 冻结的是**脚本面**绑定/member（不含 Java 插件 SPI）；插件作者面的签名漂移由本测试拦截。

## Considered Options

- `NekoPluginFeatures` 聚合门面（本轮早先提案）：被配对协议取代——基接口本身就是单接口门面，聚合层无增量价值，反而多一个形态。弃。
- 永久冻结基接口方法集合：用户否决（功能会增多，通道要长）；配对协议同样防膨胀（方法必须有 Point 配对），但保留增长自由。弃。
- 注解驱动注册（`@OnEvents` 等）：丢编译期契约、签名漂移静默失效（KubeJS 生态老坑）、引擎加反射层。弃。
- V1 shim 层（`of`/存根/void 回退）：唯一受益者是"定义过自定义扩展点的存量 V1 插件"，该集合为空。弃。
- 14 个参数类型上移 common-api：会级联拖出 common 内部依赖（各 registry 引用引擎内部类型）。弃，改移 `NekoJSPlugin` 单文件。

## Consequences

- 存量 V2 插件（`NekoRegistryPointsPlugin` 等）零改动：implements Contributor 在放宽后自动仍被收集。
- "谁参与哪个点"从 implements 关系退到"是否覆写"（IDE goTo-super 兜底）；Contributor 保留为文档化的显式形态。
- 基接口收集型钩子签名受 CI 冻结——这是对插件作者的稳定承诺，改动即破坏性变更评审。
- 双形态的 javadoc/wiki 一致性靠评审维护（配对测试管代码不管注释）。
- 集合增长规则：新收集型通道走三件套 + 配对表登记；回调族新钩子直接加在基接口回调面（不需要 Point）。
