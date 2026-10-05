# Cordis 的理念，以及 NekoJS 能参考什么

不换框架。Cordis 是一套动态组合的理念，TypeScript 实现只是落地。NekoJS 用不上那个包，但论文里的两根轴对得上现有的 generation / 扩展点。

## 理念

出处：Yifan Shi, Wei Zhang, Tianyi Cui. *A Programming Paradigm for Spatiotemporal Composability*. arXiv:2608.25512（[论文](https://arxiv.org/abs/2608.25512)，仓库 [cordiverse/paper](https://github.com/cordiverse/paper)，联系邮箱 shigma@cordis.io）。实现是 [cordiverse/cordis](https://github.com/cordiverse/cordis)。DSH 把同一套状态机写成工程说明：[Plugins and lifecycle](https://raw.githubusercontent.com/deepseek-ai/DeepSeek-Harness/cd5ef8148158c3a752a658978873241fdf8e2bbc/docs/user/develop/framework/index.md)。下面 §6 的边界来自对预印本的逐节精读（[deep-dive 第 22 章](https://github.com/xiaonancs/deepseek-harness-deep-dive/blob/main/Part%20IV%20Foundational%20Paper/22-A-Programming-Paradigm-for-Spatiotemporal-Composability.md)），不是摘要里的原句。

动态组合有两根正交的轴：

- **时间（effect）**：组件改了共享环境，卸掉时必须能撤干净。每个改动带一个左逆，运行时记下来，卸载时 LIFO 执行。复合操作的逆由结构自动得到，不靠记得去 `close`。
- **空间（coeffect）**：组件声明「我要这些服务」。上下文每变一次，对这份声明分成三态：activating（齐了，开工）、deactivating（缺了，下工）、neutral（与我无关，不动）。

两根轴进同一个 context：它既是撤销栈，也是当前有哪些服务。两个组件改的键不同，交错装卸互相看不出来（observational equivalence）。键相同且操作不可交换，才需要显式合并。一次运行中的组件实例叫 fiber；子 fiber 随父 fiber 一起卸。

论文不保证的事：逆元是否真的撤销，运行时不检查，作者自己写对；依赖有环则相关组件一直不激活；系统边界外的效果（已经发出的 IO、别人的事件总线）没有逆元，只能补偿或推迟到能原子提交的相位。DSH 文档还补了一条实现限制：多个异步 disposer 并发跑，不保证串行完成；有顺序的清理必须放进同一个 `ctx.effect()`。

下面是「整仓换成 Cordis 包」为什么不成立。理念怎么落到现有 generation 上，见文末。

## Cordis v4 是什么

DeepSeek Harness 用的是 vendored 的 `@deepseek-ai/cordis` **4.0.2**（作者 Shigma，仓库 `deepseek-ai/deepseek-harness` 的 `vendor/cordis`）。本机副本在 dsh 安装树的 `node_modules/@deepseek-ai/cordis`，公开类型在 `lib/types/*.d.ts`。说明与上游 README 一致：

https://raw.githubusercontent.com/deepseek-ai/deepseek-harness/master/vendor/cordis/README.md

它是给 **TypeScript / ESM** 应用的插件框架，不是 Minecraft 模组框架。核心对象：

| 对象 | 行为 | 出处 |
| --- | --- | --- |
| `Context` | 代理容器。普通属性读取走服务解析。`extend` / `isolate` / `intercept` 造子作用域，不改父级。 | `lib/types/context.d.ts` |
| `Service` | `super(ctx, name)` 立刻挂到 `ctx.<name>`，随所属 fiber 卸下。 | `lib/types/service.d.ts` |
| `plugin()` / `inject` | 函数、类或 `{ apply }`。`inject` 里的服务齐了才进入 `LOADING`；依赖实现变化会卸掉重跑。 | `lib/types/registry.d.ts`, `fiber.d.ts` |
| `Fiber` | `PENDING → LOADING → ACTIVE → UNLOADING → DISPOSED`（另有 `FAILED`）。`effect()` 注册的 disposer **逆序**释放。 | `lib/types/fiber.d.ts` |
| 事件 | `on` 挂在当前 fiber 上，卸 fiber 即退订。分发：`emit` / `parallel` / `serial` / `bail` / `waterfall`。 | `lib/types/events.d.ts` |

Loader、HMR、配置 include 是可选包，不是内核。DSH 的用法就是 `new Context()` 然后 `ctx.plugin(...)`，服务类 `extends Service`（例如 `dsh-app-boot`、`dsh-agent`）。

## 本仓已经有的对应物

不是空地。对应关系是「问题同类、机制不同」：

- **组合根不是服务定位器。** `NekoRuntimeRoot` 只由平台入口持有，注释写明不提供 `get()` / `current()`，不要把 root 传给普通业务类（`common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java`）。`NekoCoreContext` 是 4 字段不可变 record，同样禁止服务查找（`common/src/main/java/com/tkisor/nekojs/core/NekoCoreContext.java`）。Cordis 的 `ctx.xxx` 正好是这种被禁止的查找。
- **作用域已经切开，释放是手写的。** root 活过普通 reload（Plugin Runtime、模块缓存、global store、domain collector）；一个 generation 是 `ScriptManager.RuntimeEnvironment`（Graal `Context`、`NekoNodeRuntime`、流、`GenerationGlobals`、模块 session）。SERVER/CLIENT/TEST reload 是候选代事务，单一 `commitGeneration`，失败 `discardCandidate`，active 不动；STARTUP 是不可回滚的 reset+load。`ResourceTracker` 能逆序释放，但生产代码没有 `track` / `cleanup` 调用，root 上那份是空壳。真正的销毁顺序写在 `ScriptManager.closeRuntimeResources`：globals → node runtime → 解绑 → `synchronized (context) { context.close() }` → 再关流 → 关 session。
- **插件依赖已经是显式图，不是响应式 inject。** 扩展点 `dependsOn` + freeze 时 Kahn 排序，环 fail-fast（ADR-0002）。收集是 Collector 三段式，一个点一个文件（ADR-0001）。作者面是 `NekoJSPlugin` 门面 + Contributor，注解驱动注册被明确否掉（ADR-0010）。注册表是一次 bootstrap 里的两张 `LinkedHashMap`，没有父子 Context，也没有插件 disable/unload。`close()` 不碰 `pluginRuntime`。Cordis 的 fiber 卸载在插件面上没有对应物。
- **脚本面不是插件面。** 脚本作者用 Event Group / Binding / Builder（`CONTEXT.md`）。Cordis 的 `declare module` 增广 `Context` 对不上这套 API。

仍在的 Cordis 味分两层，不要混成一件事：

- **能删的静态。** `NekoPluginRuntime.current()`、`ScriptCompilerRegistry.INSTANCE`、`RecipeTypeDefinitionStorage`、`NekoRegistryPointsPlugin` 的静态 handle。调用方已经拿得到 runtime 产物。`NekoRuntimeAssembly` 注释写「重复 bootstrap 会被 publish 拒绝」，`NekoPluginRuntime.publish` 实际只是覆盖。
- **删不掉的静态。** `NekoRuntimeAccess.get()`、各 listener 的 `runtimeRoot`、`PlayPacketDispatchers.get()`。`RecipeManagerMixin` 和 `@SubscribeEvent` 没有构造参数，平台回调进不了 `NekoRuntimeRoot`。Cordis 的 `ctx.xxx` 到这层还是得留一个进程级桥。DSH 没有 mixin，所以它用不上这道桥。

## 为什么整仓不适合

1. **语言和线程。** Cordis fiber 的加载/卸载可以是 async，默认一条 JS 事件循环。这里每个 ScriptType 一个 owner 线程（SERVER=服务端线程，CLIENT=Render），Graal `Context` 单线程；reload 在调用线程上拿 manager 实例锁同步跑完。后台 watchdog 只能 `interrupt`，清理必须回到 owner。Cordis 的异步 disposer 表达不了「先关 Context、再关流」和 commit 中途 rollback。
2. **阶段是一次冻结，不是热插拔服务。** Cordis 的价值是 `inject` 等待 + 依赖变更重入。MC 注册、配方、资源重载是平台回调相位。ADR-0002 把依赖粒度定在「整点先完成」，并拒绝运行期改序。再做一个会睡着、会重跑的插件 fiber，是第二套排序。
3. **`isolate()` 救不了 ScriptType。** Cordis 隔离的是「同一个服务名的另一份实现」。`STARTUP` / `SERVER` / `CLIENT` 不是同一服务的两个实现：启动脚本注册进游戏注册表，普通 reload 换不掉。client/server 还被 loader 源码树切开（ADR-0007）。
4. **作者契约刚冻过。** 新增收集通道 = Point + 基接口钩子 + 清单 + `PluginHookPairingTest`（ADR-0010）。改成 `ctx.plugin` + `inject` 会把这条 CI 契约和 wiki 通道索引一起作废，换来的能力和现有 Point 重叠。
5. **事件总线会分裂。** 脚本事件、插件回调钩子、扩展点收集是三套，词汇上禁止混用（`CONTEXT.md`）。再加 emit/bail/waterfall 是第四套。

## 值得留、不值得建

不值得建：`Context` / `Service` / `Fiber` / `inject`。generation 事务、owner 线程、Graal 单线程这三样 Cordis 都没有。

值得单独看的债，仍然不是换范式：

- 能删的那层静态，加上平台侧 `runtimeRoot` handle（有 bind、没有 unbind）。平台边界那层留着。
- 销毁序列是手写的。漏一条就会漏：`CONTEXT_TO_MANAGER` 强引用、`ScriptContextRegistry.unbind`、按脚本清 listener/timer。脚本事件其实已有 owner：`EventBusJS` 按 ScriptType + scriptId 记账，换代时 `ListenerBatch` 提交或回滚。缺的是原生总线（`NativeEventsJS` 直挂 NeoForge `EVENT_BUS`）在候选代上撤不掉。那是 MC 总线的问题。Cordis 的 `effect()` 清单只能做成 **同步、owner 线程、挂在现有 generation 上的 disposer 列表**。`ResourceTracker` 已经是这个形状，只是没接上。不要为了接上它去引入 Cordis。
- `ScriptManager`（约 1639 行）和 `EventBusJS`（约 854 行）是大类型，因为它们是 reload 事务和脚本句柄，不是因为缺一个服务容器。拆它们是另一件事。

## 理念落在哪

对照的是上面两根轴，不是 `ctx.plugin` 这套 API。

- **generation 就是 fiber。** `EventBusJS` 的 token 加 `ListenerBatch` 提交/回滚，已经是「改动带逆元，换代时撤」。漏的是没登记的那些：`ResourceTracker` 空着、`CONTEXT_TO_MANAGER` 靠人工不变式、`ScriptContextRegistry.unbind` 必须被记得调用。
- **系统边界不要假装可撤销。** STARTUP 写进游戏注册表、`NativeEventsJS` 挂上 NeoForge 总线、已经发出的包，都在 NekoJS 的 context 外面。论文的处理是补偿或推迟。现有的 non-transactional STARTUP、死 Context 短路，就是这个判断，不是缺一个框架。
- **coeffect 三态不要并成一张图。** 插件 bootstrap 只有一次 activating，然后 freeze，没有 deactivating。脚本 reload 才是旧代 deactivating、新代 activating。依赖消失就重入，只适合后者内部，不适合 Java 插件。
- **同层顺序 = 键不同则独立。** ADR-0002 写「不要依赖同层顺序」。同名冲突才用 `MergePolicy`，因为键相同，效果不独立。
- **reload 结束应只取决于最终脚本集。** 漏 unbind、静态 handle 不摘，就是没回到静止态。
