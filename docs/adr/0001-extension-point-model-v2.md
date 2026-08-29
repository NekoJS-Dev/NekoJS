# 扩展点模型 V2：Collector 式基线 + 一个扩展点一个自包含文件

插件扩展点系统是 PR #37 记录的最大痛点：新增一个内置扩展点需连锁修改 5 处（`NekoJSPlugin` → `BuiltinPluginExtensionPoints` → Register 接口 → `NekoPluginRuntime` → 下游工厂），`NekoJSPlugin` 膨胀至 33 个 default 方法，EP 间依赖靠"注册顺序=依赖方向"的隐式约定。RE2 调研（`research/re2-precedents`）证实 master 已落地的 `NekoPluginExtensionPoint<P,A,R>` 是 `java.util.stream.Collector` 语义的忠实移植、无先例违背，问题全在外围形态。故决策：**保留 Collector 三段式内核**（initializer/collector/finisher + pluginType 过滤 + enabled 谓词 + reload 可重入），外围按"**一个扩展点 = 一个自包含文件**"重组：

1. 每个扩展点一个 Point 文件：**Contributor 接口**（插件贡献面，实现即被收集）+ builder 定义 + 累积器策略同处一文件
2. **merge 策略每点必填**（builder 不给默认值）：`append`（列表式无冲突）/ `firstWin` / `overrideWarn` / `failFast` 四档引擎标准件（E4 原型发现：append 是列表式点的常态，补为第四档）
3. **`NekoJSPlugin` 冻结**为纯生命周期接口；14 个 EP 钩子外迁为各 Point 的 Contributor 接口
4. **产物访问收敛到 `NekoPluginExtensionHandle`**（bootstrap 后 `get()`）；`NekoPluginRuntime` 冻结，不再新增 per-EP 访问器
5. 7 个静态工厂收敛为 **1 个 builder**（clientOnly 等变开关）
6. finisher 执行后引擎**置空累积器引用**——"finish 后不再收集"从约定变机制
7. `result()`（可选依赖，null）/ `resultOrThrow()`（必需依赖，未完成或被环境跳过抛 `IllegalStateException`）**两档访问**
8. **不提供插队 API**（无 first/last）；顺序 = 注册序 + 显式依赖声明
9. **"内置扩展点先行"保留为引擎保证的固定相位**

## Considered Options

- 推翻重设计：弃——RE2 证实基线正确，推翻无收益且连锁推翻 E2/E3/R1 前置。
- EP 钩子在 `NekoJSPlugin` 上保留 deprecated 默认实现做兼容：弃——上帝接口继续膨胀，违背"冻结"目标；插件 API 已授权 breaking。

## Consequences

- 插件 API breaking：现有插件的 EP 钩子迁移为 Contributor 接口（机械迁移，方法签名不变）。
- 成功判据②"新增扩展点只动 1 个文件"从口号变成结构保证。
- 定义注册路径统一（内置与第三方同走 provider）由 E3 落地；14 个内置 EP 的迁移编排与 runtime 委托保留期随 G1 路线图收束。
- E4 原型票以本模型为准做骨架验证。
