# 绑定安装的 guest 全局名枚举范围

## 改动与原因

当前正式基线为 `24d35a5d`，两组重载 289.4 / 338.0 ms，仍未通过 285.3 ms 原门槛。已有[命令阶段诊断](../architecture-refactor/evidence/2026-10-20-current-reload-stages/README.md)覆盖绑定安装和逐个 Graal Value 操作，但采样条数不能换算为该操作的独占耗时；本阶段不承诺收益。

`ScriptEnvironmentFactory.installEnvironmentBindings` 为每个 generation 枚举 Context 的绑定键和 `Object.getOwnPropertyNames(globalThis)`，再逐个读取数组元素、转换为 Java 字符串。各个 Value 操作原先分别自动进入/离开目标 Context。现在仅在这段 guest 检查外围显式 `enter`，并以 `finally` 配对 `leave`，让连续操作复用同一次显式进入范围。

该范围不包含 event bridge、`IPluginRuntime` 的绑定/事件组/API 查询、`Binding.value`/`valueType`、managed API implementation 查询、schema 事务及 telemetry 安装。将整个安装方法包进 Context 会改变这些 Java 回调观察到的 `Context.getCurrent()`，因此没有采用。guest 检查自身触发的 guest/代理回调原本就在 Graal 的自动进入范围内执行。

全局名集合仍每代重新取得，保留绑定键与 guest 属性的并集和 this/arguments/super。guest 表达式、数组检查、逐轮长度读取及读取顺序均保持；不预缓存内置全局或假定插件没有改写 Object.getOwnPropertyNames。Context、guest Value、源码/路径权限、候选 schema 与错误路由仍由原 owner 持有；没有共享 Context、跨代缓存、新公共接口或系统性能策略。

## 失败与回归范围

成功与失败均离开本段显式范围，恢复调用方此前进入的 Context。进入目标 Context 失败时不执行未配对 leave，由原外层 binding 失败处理丢弃候选 schema；枚举失败仍由同一路径报告，旧 active schema 保持，后续候选可重试。

主代理只读检查了锁定 GraalMC `curse.maven:graal-1504336:8762962` 的实际字节码：Context.enter/leave 分别委托 explicitEnter/explicitLeave，PolyglotContextImpl 将 engine.enter 返回的先前状态压入每线程 explicitContextStack，leave 以 removeLast 取回并传给 engine.leave。此机制支持配对恢复，但字节码检查不能代替本改动的运行回归。Luna 的限定只读调查没有给出已确认的问题，也没有确认当前 diff 或该嵌套行为；这里不将其描述为审核通过。

主代理新增 `ScriptEnvironmentBindingScopeTest`，通过真实安装入口检查：无已进入 Context、另一个 Context 已进入、目标 Context 本身已进入，以及 guest 枚举抛错后的旧 schema 保留/重试/发布。插件查询、绑定取值和事件 bridge 的探针要求观察到原调用方；显式范围不会扩到这些回调中。另检查非枚举 guest 名、纯绑定名和语言关键字均进入候选 schema。

## 当前验证边界

按用户“不要着急测试，先完成任务”，本阶段只完成源码、回归与完整 diff/调用方检查，并运行 `git diff --check`；没有运行新增测试、编译、游戏或性能采样。新增四项回归尚未执行，不能标为通过。

集中验证时应先执行新回归、已有 binding/schema/generation 失败保留回归与 `:common:check`，再在固定组件中比较原实现与本实现，保留完整 Context 安装输出和异常语义。只有测得有用收益且没有语义回归，才继续原正式 cohort；若收益不足，应移除本候选，不能靠改变门槛、跳过检查或重复取优来保留它。Fabric 实机资源验收和社区外部收尾仍独立未完成。

集中检查补记：`26844b7e` 的四项新增回归均通过，定向合计 121 项、完整 common 为 2392 项（0 failures/errors、4 skipped），五节点运行产物 gate 通过。[完整证据与组件对照](../architecture-refactor/evidence/2026-10-20-binding-preflight-checks/README.md)保留了一百次独立 Context 安装等价与全部三十对样本。绑定安装配对中位省约 0.197 ms、19/30 对下降，均值受长样本影响；这是小幅收益，保留局部改动，不宣称解决整体重载。没有新增生命周期缓存或改变原正式门槛。本轮未启动游戏或正式 cohort。
