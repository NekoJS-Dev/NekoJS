# 内置扩展点自包含注册：内置点定义插件

现状是两条注册入口：bootstrap 硬编码 `BuiltinPluginExtensionPoints.builtIn(...)`（`NekoPluginBootstrap.java:113`），第三方走 `NekoPluginExtensionProvider` 回调——加一个内置扩展点连锁修改 5 处。E1（ADR-0001）已锁"内置与第三方同一条 provider 路径 + 内置先行固定相位"。本 ADR 定**机制**：

1. **内置点定义插件**：引擎自有的 `NekoBuiltinPointsPlugin implements NekoPluginExtensionProvider`，其 `registerPluginExtensionPoints` 就是一列 `registry.register(XxxPoint.POINT)`——这份清单同时是内置扩展点的**显式总索引**。各 Point 定义按 ADR-0001 散在各自文件（id 常量留在 Point 文件，observable 契约不破坏）；插件发现走现有 `@RegisterNekoJSPlugin` 扫描（`NekoCommonBuiltinPlugin` 已验证此路）。
2. **内置先行 = bootstrap 显式提升**：引擎识别该插件后保证其 provider 回调第一个执行。这是引擎对依赖图根部的承诺，由引擎代码明说，不靠 priority 魔法数字（第三方理论上可撞进保留档）。
3. **新增内置扩展点 = 1 个自包含 Point 文件 + 清单 1 行**（append-only）。成功判据②按此语义微调。

## Considered Options

- 注解处理器编译期生成索引（`@NekoExtensionPoint` → 生成 ServiceLoader 文件）：字面达成"只动 1 个文件"，但需给 common + 版本树 + 各节点全部 source set 配处理器，漏配 = **静默不注册**；构建复杂度正是本项目要减掉的东西。弃。
- 手写 META-INF/services + ServiceLoader：引入第二注册通道，违背"同一条路"。弃。

## Consequences

- `BuiltinPluginExtensionPoints`（394 行大类）消失，定义散入 Point 文件。
- 注册路径与第三方逐字相同（IntelliJ bundled plugins / Eclipse / ServiceLoader / pf4j / KubeJS `plugins.txt` 五框架规范做法，见 RE2 §2）。
- bootstrap 自身对内置产物的消费（`NekoPluginBootstrap.java:151/159` 直引 id 常量）改走 handle 的迁移归 E4 原型验证 + G1 编排，不在本 ADR 范围。
- 漏写清单行的故障模式：该点永不注册——`dependsOn` 未注册 id 在 freeze 报错（ADR-0002 使其响亮），或 Contributor 无收集（测试即红）。
