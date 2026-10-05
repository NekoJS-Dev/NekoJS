# Cordis v4：一手来源与可确认的边界

本文由 DeepSeek V4.1 Flash 搜集资料，主代理复核论文关键原文及固定提交源码后整理。结论对应下面列出的版本，不推断本机安装版本、npm 最新版本或不同发行线的完整差异。

## 来源与版本

- 论文：[A Programming Paradigm for Spatiotemporal Composability，arXiv:2608.25512v1](https://arxiv.org/abs/2608.25512v1)，Yifan Shi、Wei Zhang、Tianyi Cui，2026-08-26。[固定 v1 PDF](https://arxiv.org/pdf/2608.25512v1)共 92 页；已取得抽取文本，主代理复核了下述决定性段落。
- [作者仓库](https://github.com/cordiverse/paper) main 仅有 README.md 与 .gitattributes，README 声明预印本仍在修订。此次访问 HTML 版本返回 404；理论内容以 PDF 为准。
- 主要实现基准：DSH [c36a83ff6bb95e3f82cf79f9be7c724270a8aa61](https://github.com/deepseek-ai/deepseek-harness/commit/c36a83ff6bb95e3f82cf79f9be7c724270a8aa61)，其 [vendor/cordis/package.json](https://github.com/deepseek-ai/deepseek-harness/blob/c36a83ff6bb95e3f82cf79f9be7c724270a8aa61/vendor/cordis/package.json)声明 `@deepseek-ai/cordis` **4.0.3**。这是该提交的源码版本，不声称已发布到 npm。完整提交 SHA 已通过 GitHub git-commit API 确认。
- 另对照上游 [cordiverse/cordis@f8ea3cd50f1a5724e8e715995bcde131c9c12b2c 的 fiber.ts](https://github.com/cordiverse/cordis/blob/f8ea3cd50f1a5724e8e715995bcde131c9c12b2c/packages/core/src/fiber.ts)。当前上游 core package 声明 **4.0.0-rc.10**。上游与 DSH 的文件行号、effect 加固机制不同，不混用。

## 论文提供的理念与有条件保证

论文以 revertible effects 管理退出时的恢复，以 reactive coeffects 根据上下文变化驱动激活/停用；统一上下文中介把两者组合起来。这里的可恢复性和最终静止态并不等同于撤销全部物理世界状态。

| 已复核的原文位置 | 可以支持的结论 |
|---|---|
| §5.1.1，PDF 印刷页 59：逆是否撤销效果是 “an obligation on the component author rather than a property the runtime verifies” | 运行时记录、调用 disposer，不验证用户写出的逆是否正确；同段明确 coeffect 的交换性见证也未校验 |
| §4.3.2，印刷页 47：“a message already sent stays sent” | Recovery 比较受管状态在观测等价下的结果，不能推出任意文件、网络或游戏效果的深回滚 |
| §4.3.4，印刷页 49：Theorems 73/80 假设依赖关系 acyclic；Theorem 73 还列出有限名字集与迭代长度界 | 进展、终止和合流有前提，不是任何插件依赖图都自动安全 |
| §4.4，印刷页 55–56：同步演算的状态映射整步施加；异步宿主的 in-flight map 会完成，再处理停用 | 可以分析异步生命周期，但不能据此声称实现了 JVM 多线程同步、MC owner-thread 调度或可抢占取消 |
| §6.1，印刷页 70–71：系统能独占修改并恢复的位置在内；否则效果既不被追踪，也不被恢复 | 可撤销与否按具体位置和操作决定，不按“内存/文件/外部总线”一刀切 |
| §6.1，印刷页 71：区分 acquisition 与 emission，并讨论 withholding / compensation | 获取句柄可以配释放；已发出的效果需要延迟发布或补偿。补偿的交换性需重新建立，不能直接继承原定理 |
| 案例研究的 Threats to validity，印刷页 70 | 作者承认证据来自单一生态和宿主语言，属于观察性采用证据；没有对替代架构的受控性能或开发效率比较 |

这些限制并不否定范式的价值；它们限定了可以拿论文支持什么判断。尤其不能从“有形式化模型”推出“NekoJS 改成它必然更简单、更快或更可靠”。

## 主要实现基准的实际行为

以下行号均对应 DSH 的完整提交 c36a83ff6bb95e3f82cf79f9be7c724270a8aa61：

| 机制 | 证据 | 可确认行为 |
|---|---|---|
| 单个 effect 的清理 | [fiber.ts L418](https://github.com/deepseek-ai/deepseek-harness/blob/c36a83ff6bb95e3f82cf79f9be7c724270a8aa61/vendor/cordis/src/fiber.ts#L418)，尤其 L431–439 | 已登记 disposer 反序，并通过 Promise 链串行衔接；这不等于保证 disposer 出错后链内其余项仍执行 |
| Fiber 卸载 | [fiber.ts L675](https://github.com/deepseek-ai/deepseek-harness/blob/c36a83ff6bb95e3f82cf79f9be7c724270a8aa61/vendor/cordis/src/fiber.ts#L675) | 顶层清理项经 Promise.all 并发等待，各项异常分别记录。不能把 Fiber 整体当作严格串行的逆序关闭栈 |
| 父子归属 | [fiber.ts L265](https://github.com/deepseek-ai/deepseek-harness/blob/c36a83ff6bb95e3f82cf79f9be7c724270a8aa61/vendor/cordis/src/fiber.ts#L265) | 子 Fiber 的 dispose 由 parent.fiber.effect 持有，父退出带走子实例 |
| 依赖重激活 | [fiber.ts L611](https://github.com/deepseek-ai/deepseek-harness/blob/c36a83ff6bb95e3f82cf79f9be7c724270a8aa61/vendor/cordis/src/fiber.ts#L611) | 依赖缺席或提供者身份变化改变 epoch，驱动 load/unload；重载不是默认保留旧实例的候选事务 |
| 服务可见与撤销 | [reflect.ts L237](https://github.com/deepseek-ai/deepseek-harness/blob/c36a83ff6bb95e3f82cf79f9be7c724270a8aa61/vendor/cordis/src/reflect.ts#L237)、[L277](https://github.com/deepseek-ai/deepseek-harness/blob/c36a83ff6bb95e3f82cf79f9be7c724270a8aa61/vendor/cordis/src/reflect.ts#L277) | strict 查询要求提供者 ACTIVE；provide 的 disposer 删除解析槽、notify 依赖者并 await 它们，再删除自身缓存项。不能扩大为“提供者所有兄弟资源都必然最后释放” |
| isolate | [context.ts L109](https://github.com/deepseek-ai/deepseek-harness/blob/c36a83ff6bb95e3f82cf79f9be7c724270a8aa61/vendor/cordis/src/context.ts#L109) | 针对服务名改变解析标签，允许不同作用域绑定不同实现；不提供内存、线程、Graal 或安全沙盒隔离 |

上游对照提交的 `effect()` 位于 L275 附近、`_unload()` 位于 L439；它同样有单 effect 反序 Promise 链与 Fiber 级 Promise.all，但没有主要基准中的 setupBarrier / effectInertia。因此只援引上游行号不能说明 DSH 版本的所有行为。

## 对 NekoJS 判断的直接输入

1. 资源随 owner 退出、依赖随可用性变化，是可移植的理念，不依赖采用 TS 包或 `ctx.xxx` 语法。
2. NekoJS 的候选代验证及失败保留旧代属于另一项契约；Fiber core 的卸载后重激活不能直接替代它。
3. 服务解析作用域可以辅助多实例组合，但不替代 MC 版本树、平台线程约束或 Graal 执行隔离。
4. 外部订阅若有 unregister，可以纳入受管效果；订阅期间已经执行的世界修改仍需领域自己的事务/补偿判断。

## 未验证项

- 本次未运行 Cordis 测试、HMR 或性能基准；不对可选 Loader/HMR 所有恢复策略作结论。
- 没有审核全部定理的数学证明；核对的是上述定义、假设、边界与关键源码，不宣称形式验证了实现。
- 不保留无法可靠配对的本机包与发布版本比较；它们不是 NekoJS 适配结论的必要前提。
- 完整项目适配与本地源码证据见 [独立复核](cordis-v4-reassessment.md)。
