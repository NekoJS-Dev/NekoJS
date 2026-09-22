# 插件作者迁移材料（票 08：真实外部 addon 从 discovery 到消费）

本材料面向**外部 Java 插件（addon）作者**，说明 2026-09-22 票 08 收口后的真实外部插件链路。
仓库内已验证的活样例：

- 测试级真实外部 addon fixture：`common/src/addonFixture/`（独立制品，
  只针对 `:common` main 输出编译，jar 内带生产形态 metadata；由
  `Ticket08ExternalAddonChainTest` 与各节点 `Ticket08LoaderDiscoveryTest` 驱动）。
- 最小可运行示例：`examples/external-addon/`（本目录，已用 JDK 21 针对公共面独立编译验证）。

中文插件开发总览仍以 wiki《插件开发》为准；本文件只覆盖票 08 新增/收口的事实。

## 1. 发现（discovery）：两种 loader 输入,一条引擎入口

| Loader | 你需要提供 | 引擎侧行为 |
|---|---|---|
| NeoForge | jar 内 `META-INF/neoforge.mods.toml`（生产格式，modId=你的 mod）+ 类上 `@RegisterNekoJSPlugin` | FML 扫描 mods 目录 jar 的注解 → `NekoJSBasePluginManager.registerClass(clazz)` |
| Fabric | jar 内 `fabric.mod.json` 声明 `"entrypoints": {"nekojs": ["<你的插件类FQN>"]}` | FabricLoader 解析 entrypoint → `NekoJSBasePluginManager.registerClass(...)` |

两种输入最终都汇入同一条生产注册缝 `NekoJSBasePluginManager.registerClass`：

- **owner identity** 来自 protection domain 的 code source——即你的 jar 本身
  （本票测试断言 `identity.codeSource()` 指向被发现的 jar，不是内部类路径）。
- `clientOnly` / `requiredMods`（AND 语义）/ `priority`（数值大者先；同优先级按类 FQN 兜底）
  全部在注册缝内按旧契约过滤,与 loader 无关。
- 同一 (identity, class) 的重复发现（dev classpath 重复列出、扫描数据重复）只登记一次。

**不要依赖内部实例化冒充发现**：插件类不在引擎类路径上时才叫外部发现。本仓的隔离由
`verifyExternalAddonIsolation` 门禁守住（五个生产 jar 均不含 fixture 内容）。

## 2. 贡献与消费：唯一生命周期是 Point

插件生命周期只有一套：**discovery → contribution → dependency ordering → freeze →
initialization/collection → finish/result**（`NekoPluginBootstrap` 的点优先执行序）。
没有第二套 initializer/merger/finisher 框架；自定义扩展点走
`NekoPluginExtensionProvider.registerPluginExtensionPoints(registry)`，与内置点同批收集。

- **Hook 与 Contributor 等价**：覆写 `NekoJSPlugin.registerBinding` 与
  `implements BindingsPoint.Contributor` 由同一个 `nekojs:bindings` 点收集,产物一致
  （本票 `Ticket08ExternalAddonChainTest.addonContributesThroughHookAndProviderAndFreezesOneProduct`
  以外部 addon 同时验证两形态）。
- **扩展点间依赖**：`dependsOnId("nekojs:bindings")` 声明时序；在 initializer/collector 里
  `context.result("nekojs:bindings", type)` 读对方**已冻结**产物（数据依赖免声明,违序读取立即抛错）。
- **Extension Handle**：`registry.register(point)` 返回句柄；finish 前调用 `result()` 抛
  `IllegalStateException`（含点 id 与"bootstrap incomplete or point skipped"），finish 后可读；
  被环境谓词跳过的点句柄永远未发布。
- **同一冻结产物**：Handle `result()`、`NekoPluginRuntime.extensionProduct(pointId, type)` 与
  脚本可见 binding 读到的是同一对象（本票以 `assertSame` 断言三者）。

## 3. 冻结边界（三层,别混）

1. **注册图 freeze**：bootstrap 的扩展点注册窗口在 freeze 后关闭。迟到注册抛
   `IllegalStateException`——**报错带 addon 定位**（票 08 起,见 §5）。
2. **累积器 seal**：实现了 `Sealable` 的累积器在 finisher 后密封,再收集抛
   `IllegalStateException`。你的自定义点可以选择实现 `Sealable`（fixture 的
   `GreetingAccumulator` 是活样例）。
3. **结果发布**：finisher 产出不可变快照。非 Sealable 累积器（如 bindings 的注册表）
   迟到写入只改累积器,**不影响已发布产物**（本票测试验证两臂）。

## 4. reload 语义：插件产物是进程级

普通 `/nekojs reload`（SERVER/CLIENT/TEST 的事务式 reload）**不重新 discovery、不重新
bootstrap、不重新 freeze** Plugin Runtime：

- 你的插件 `init()` 只在装配时触发一次;`beforeScriptsLoaded/afterScriptsLoaded` 每轮
  reload 触发（这是设计语义,不是重复 bootstrap）。
- Handle 与 `extensionProduct` 跨 reload 保持有效且是同一对象;脚本 binding 每一代读同一
  冻结值。
- `Binding.close(scriptType)` 不因普通 reload 被调用——session 清理不关闭进程级插件产物
  （只有 kill 重建路径会走 binding close）。
- **旧 generation 失效**：reload 后旧 generation 的 Context/事件 token/临时计划不能操作
  新 session;域查询面对旧 token 返回显式 stale（如 `generation-not-active`）,错误明确指向
  失效 generation,不会静默作用于新 session。

## 5. 失败输出定位到 addon（票 08 新行为）

扩展点注册/依赖失败现在**点名肇事插件**（owner id；无 identity 时为类名）：

| 场景 | 阶段 | 报错形态（节选） |
|---|---|---|
| 重复扩展点 id | 注册期 | `... 'X' is already registered by plugin '<first>'; duplicate registration by plugin '<yours>' is rejected` |
| freeze 后迟到注册 | freeze 后 | `... late registration of 'X' by plugin '<yours>' is rejected (the registration window closes at freeze)` |
| 未知依赖 id | freeze 早爆 | `... 'X' (registered by plugin '<yours>') dependsOn unregistered point 'Y' (typo?)` |
| 依赖环 | freeze 拓扑排序 | `cycle fail-fast: X (by '<a>') -> Y (by '<b>') -> X (by '<a>')` |

这些是开发者面异常消息（英文,无 NEKO- 码——本票未新增问题上报型日志,故未动
`wiki/en_us/Error-Reference.md`;该页在维护者 stonecutter 分支另行演进）。

## 6. 迁移检查单（从"嵌入式/内部注册"到真实外部 addon）

1. 你的插件 jar 是否带两份 loader metadata（`META-INF/neoforge.mods.toml` +
   `fabric.mod.json` 的 `nekojs` entrypoint）？缺哪份,对应 loader 就发现不了你。
2. 是否只用公开面编译（`api.*` + `core.plugin` 的 provider/builder）？对引擎内部
   测试 seam 的依赖会让你无法作为第三方插件构建。
3. 是否在 bootstrap 后才读 Handle（finish 前 `result()` 会抛错）？
4. 是否假设 reload 会重建插件产物？修正为"进程级冻结产物 + 每代脚本读同一实例"。
5. 旧的 `NekoPluginRuntime.bootstrap(List<NekoJSPlugin>, ...)` 嵌入式入口已在票 08 删除
   （无生产调用方）；嵌入宿主请改走 `bootstrapOwned(List<OwnedPlugin>, ...)`（owner identity
   显式传入,失败输出也能定位到你）。
