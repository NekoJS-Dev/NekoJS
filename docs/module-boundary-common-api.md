# common-api 模块边界评估

> 本文评估 `common-api` 作为独立 Gradle 模块的必要性，并给出处置方案。
> 涉及的既有决策：[ADR-0007](adr/0007-module-boundaries.md)（四层归属判据）、
> [ADR-0010](adr/0010-plugin-authoring-model.md) §4（插件入口接口回迁 `common`）。

## Problem Statement

引擎层现在是三个 Gradle 模块：`common-api`（114 个类型）、`common`（392 个）、
`common-api-processor`（2 个）。`common-api` 的设计意图是"对外契约层"——插件作者只依赖它，
硬边界是零 MC / Loader / Graal import。

但这个模块边界现在没有兑现它的收益，同时在三个地方收着成本。

**收益没有兑现。** 两个模块都不发布 Maven 制品，都被整体嵌进各平台 fat jar，所以不存在"只
依赖契约层"的消费者：插件拿到的是 fat jar，里面两个模块的类都在。`common` 用
`api project(':common-api')` 再导出，下游任何依赖 `common` 的东西都自动拿到 `common-api`。
唯一真正只依赖 `common-api` 的消费者是 `common-api-processor`，而它只用到 **1 个**类型
（`api.spec.PlatformAvailability`），且用的是 `compileOnly`——非传递，对任何消费者的编译期
与运行期 classpath 都没有影响。

**契约层的内容分布也不支持这个划分。** 114 个类型里：

| 被谁引用 | 类型数 |
|---|---|
| 只被 `common` 引用（不被版本树、不被处理器） | 67 |
| 被版本树（`src/` 或 `versions/*/src/`）引用 | 43 |
| 只被处理器引用 | 1 |
| 只被 common-api 自己的测试引用 | 1 |
| 谁都不引用 | 2 |

也就是说 **59% 的"对外契约"其实只有引擎自己在用**，集中在 `api.surface`（26）、
`api.facade`（10）、`api.capability`（7）、`api.module`（6）、`api.contract`（5）这些包。
它们放在契约层不是因为对外，而是因为当初按"看起来像 API"归的类。

**成本一：接口与构造方式被拆到两个模块。** `common-api` 里 43 个接口/抽象类型，**没有一个**
声明返回实现的静态工厂——这不是风格选择，而是 Java 语义：契约层在依赖图上位于引擎之下，
它的类型不可能引用住在 `common` 的实现。于是 20 对接口/实现的构造入口全部落在类型之外，
并催生了两个只为绕开这条限制而存在的工厂类：

- `EventBusFactory`（`common/.../eventbus/`）——`EventBus` / `CancellableEventBus` /
  `DispatchEventBus` / `DispatchCancellableEventBus` / `DispatchKey` 五个契约类型的构造入口。
  后果是版本树里的业务代码（`bindings/event/` 下的各事件组）要调用**实现包里的工厂**
  `EventBusFactory.createDispatchKey(...)`，而不是 `DispatchKey.of(...)`。读代码的人拿到接口
  之后，没有任何线索指向"怎么造一个"。
- `EnvironmentKeyFactory`（`common/.../api/surface/`）——`EnvironmentKey` 是 `common-api` 里的
  record，但取当前值需要 `platform.Platform`，只能把 `current()` 留在 `common`。

**成本二：7 个包被拆在两个 jar 里。** `com.tkisor.nekojs.api`、`.api.data`、`.api.event`、
`.api.module`、`.api.plugin`、`.api.recipe`、`.api.surface` 这 7 个包名同时存在于两个模块。
同一个包分布在两个制品里，读代码时"这个类在哪个模块"只能靠查。

**成本三：40 行构建脚本只为跨 jar 的编译正确性。** `common/build.gradle` 里有一段
`$SwitchMap$` 陈旧性守卫：javac 把 `switch` over enum 编译成消费方内部的合成类，而 Gradle 的
ABI 编译回避不会注意到枚举常量**重排**，跨模块时会留下一个错误分派的陈旧 `$SwitchMap$`。
解法是把 `:common-api:jar` 的字节指纹作为 `compileJava` 的输入。这段机制在仓库里没有第二处
同类，它的存在条件就是"契约层是另一个 jar"。作用范围是 common-api 的 9 个枚举 × `common` 里
54 个含 `switch` 的文件。

**这个边界已经被现实检验过一次并且被推回了。** ADR-0007 决策 1 要求把插件入口契约
（`NekoJSPlugin` 等）迁进 `common-api`，把它从"数据契约孵化层"升级为真正的对外契约层。
ADR-0010 §4 记录了实施裁决：迁不动——18 个钩子的参数类型全在 `common`，反向搬 14 个类型会
级联拖出内部依赖，最终 `NekoJSPlugin` 留在 `common`。所以今天这个模块不是一个未完成的计划，
而是一次已经失败的搬迁留下的残留：契约层里没有插件入口，插件作者仍然只能编译依赖平台 fat jar。

## 原始设计：这些工厂类不是设计，是拆分的副产物

`v1.0.4` 是单 Gradle 模块，事件总线的写法是这样的（`utils/event/EventBus.java`）：

```java
public interface EventBus<E> {
    static <E> EventBus<E> create(Class<E> eventType) {
        return new EventBusImpl<>(eventType);
    }
    // ...
}
```

接口住 `utils/event/`，实现住 `utils/event/impl/`，接口 import 实现并**自带静态工厂**。
`CancellableEventBus.create(...)`、`DispatchEventBus.create(...)` 同形，`DispatchKey` 更完整：
`of(Class, Function)` / `of(Class)` / `string()` 三个静态工厂全在接口上。**没有 `EventBusFactory`
这个类**——拿到接口就知道怎么造一个。

它是分两步变成今天这样的：

1. **`3b9882df`（2026-07-23，仍是单模块）**：包结构重排为 `api/event/`（契约）+ `eventbus/`
   （实现），静态工厂从接口上剥掉，新增 57 行的 `eventbus/EventBusFactory`。这一步是**包级洁癖
   的选择而不是语言限制**——`EventBusFactory` 自己的 javadoc 写着理由："Lives in `eventbus/`
   package to avoid circular dependencies with impl classes"。而 v1.0.4 证明那个包环是完全可以
   接受的：Java 允许它，原作者也这么写了几个版本。
2. **`d59c97d3`（2026-07-27）建 `common-api` 骨架，`ff55a7b1` 把这批接口迁进去**：至此那个
   *偏好*变成了*不可能*。契约层在依赖图上位于引擎之下，接口再也无法引用实现，静态工厂不是"我们
   选择不写"，而是"写不出来"。

命名上的退化也一并发生了：`DispatchKey.of(keyType, toKey)` 变成
`EventBusFactory.createDispatchKey(keyType, toKey)`，`DispatchKey.string()` 变成
`EventBusFactory.createStringDispatchKey()`。同一件事多了一个类名、一层前缀，还从契约包搬进了
实现包——版本树里的业务代码因此要 import 引擎实现包。

这段历史决定了本文方案第二步的性质：**它不是新设计，是把 v1.0.4 已经验证过的形状恢复回来。**
也说明并入模块之后唯一残留的反对意见是那个包环偏好，而那个偏好在原始设计里就没有被当成硬约束。

## Solution

把 `common-api` 的源码并入 `common`，仍然放在 `com.tkisor.nekojs.api.*` 包下，**所有 FQCN
保持不变**。引擎层从三个模块变成两个：`common` 与 `common-api-processor`。

"契约层零 MC / Loader / Graal import"这条纪律不取消，改由**包前缀规则**承载：`guardLint` 现有
的模块边界规则今天是按源码目录取的（`fileTree("common-api/src/main/java")`），改成按包前缀取
（`com/tkisor/nekojs/api/**`）。这条规则本来就在 CI 上强制，改的是它的取材范围，不是它的地位。

改完之后收回来的是：

- 接口可以自带静态工厂。`EventBus.create(...)`、`DispatchKey.of(...)` 这类写法成为可能，因为契约
  类型和实现终于在同一个编译单元里。`EventBusFactory` 与 `EnvironmentKeyFactory` 这两个绕道
  工厂可以退役，构造入口回到类型本身。
- 7 个拆包合并，一个类只属于一个制品。
- 40 行 `$SwitchMap$` 指纹守卫可以删掉——同模块内 javac 会正常重编译。
- 两份 `build.gradle` 合成一份，fat jar 少装配一个模块输出。

放弃的是 javac 级别的强制：并入之后，`api.*` 里的类在编译期可以引用引擎内部而不报错，只有
lint 会拦。这个损失比看起来小——`common` 现在已经有 66 个文件住在 `com/tkisor/nekojs/api/`
包下，`EnvironmentKeyFactory` 就在 `api.surface` 包里 import `platform.Platform`。也就是说
"`api.*` 是纯的"这条性质在包层面**今天就已经不成立**；模块边界保护的只是这 114 个文件，不是
这个包。并入之后 lint 的覆盖范围反而从 114 个文件扩大到全部 `api.*`（实测 `common` 那 66 个
文件当前零 Graal import，改成包前缀规则今天就能直接通过）。

### 为什么不选另一条路

另一个自洽的终态是把边界做实：让 `common-api` 真的成为发布制品，把插件入口类型迁进去，让插件
只依赖它。不推荐，因为这条路已经试过了——ADR-0007 决策 1 就是它，ADR-0010 §4 记录了它撞上
什么：钩子的参数类型全在引擎侧，搬接口就得级联搬实现。要重走这条路，先要解决的是"插件 API 的
参数类型能否与引擎实现解耦"，那是一个远大于模块拆分的题目，且当前没有需求驱动（预发布阶段，
没有外部插件作者在等一个可单独依赖的 API 制品）。

两个终态都自洽，不自洽的是今天：有边界的形式，没有边界的收益，还在付边界的代价。

## User Stories

1. 作为引擎贡献者，我想在拿到一个契约接口时就能看见怎么造出它的实例，这样我不必先去猜实现住在哪个模块、再去找有没有对应的工厂类。
2. 作为引擎贡献者，我想在版本树的业务代码里写 `DispatchKey.of(...)` 而不是 `EventBusFactory.createDispatchKey(...)`，这样调用点不必知道实现包的名字。
3. 作为引擎贡献者，我想让 `EventBus` 这类接口自带静态工厂，这样新增一种总线时构造入口自然长在类型上，不必同步维护一个平行的工厂类。
4. 作为引擎贡献者，我想知道一个类唯一属于哪个制品，这样在 IDE 里跳转和在构建里定位都不产生歧义。
5. 作为引擎贡献者，我想只维护一份引擎层 `build.gradle`，这样编译选项（`-Xlint`、`-parameters`、toolchain）只有一处事实源，不会两边悄悄漂移。
6. 作为引擎贡献者，我想删掉 `$SwitchMap$` 指纹守卫，这样枚举重排的正确性由 javac 的正常增量编译保证，而不是靠一段需要被理解和维护的构建脚本。
7. 作为引擎贡献者，我想让"`api.*` 零 MC / Loader / Graal import"这条纪律覆盖所有 `api.*` 文件而不只是其中 114 个，这样纪律的范围与它的名字一致。
8. 作为引擎贡献者，我想在移动代码时不改任何 FQCN，这样版本树、插件、probe golden、`META-INF/services` 全都不需要跟着改。
9. 作为引擎贡献者，我想让边界违规在 CI 上被拦住，这样并入模块不等于放弃纪律。
10. 作为插件作者，我想让我现有的 import 路径继续可用，这样这次调整对我完全无感。
11. 作为插件作者，我想让 `@PlatformAvailability` 与 spec 覆盖检查继续工作，这样我依赖的编译期校验不因引擎内部重构而消失。
12. 作为发版负责人，我想让 fat jar 的内容在调整前后逐字节等价（除装配来源减少一个模块外），这样这次调整不需要额外的产物验证。
13. 作为发版负责人，我想让 `.d.ts` / `.pyi` 的 probe 产物在调整前后不变，这样脚本作者的类型声明不受影响。
14. 作为新贡献者，我想在读 ADR-0007 的四层判据时看到与仓库实际结构一致的层数，这样我不会按一个已经不存在的模块去归置新代码。
15. 作为新贡献者，我想知道"什么该放 `api.*`"有一条能自查的规则，这样我不必靠模仿现有文件来猜。
16. 作为审阅者，我想在 diff 里看到"纯移动 + 删除机制"而不是"移动 + 顺手改逻辑"，这样我能低成本确认行为未变。
17. 作为审阅者，我想让工厂类退役与模块并入是两个可独立回滚的步骤，这样出问题时能只回退一半。
18. 作为维护者，我想在将来真的需要一个可单独依赖的 API 制品时，知道那件事的前置条件是什么，这样我不会误以为只要重新拆个模块就够了。

19. 作为引擎贡献者，我想让恢复后的静态工厂沿用 v1.0.4 的方法名，这样翻旧提交和旧分支时同一个概念只有一个名字。
20. 作为审阅者，我想知道当前形状是从哪个提交开始偏离原始设计的，这样我能判断这次调整是回归而不是又一次重构。

## Implementation Decisions

分两步落地，两步各自可独立回滚。**第一步是纯结构变更，不改任何行为；第二步才动 API 形状。**

### 第一步：模块并入（纯移动）

1. **源码位置**：`common-api/src/main/java/**` 移入 `common/src/main/java/**`，包路径与文件名
   全部不变；`common-api/src/test/java/**`（20 个测试）移入 `common/src/test/java/**`。所有
   FQCN 不变，因此版本树、`META-INF/services`、probe golden、插件 import 均无需改动。
2. **Gradle 拓扑**：`settings.gradle.kts` 去掉 `common-api`；`common/build.gradle` 删除
   `api project(':common-api')`；`common-api/build.gradle` 删除。
3. **编译选项**合并到 `common`：保留 `-parameters`（`ContractReflector` 要反射 facade 方法的
   参数名，这是行为依赖不是风格）。`-Xlint:all` 目前只在 `common-api` 与处理器上开、`common`
   上没开；合并后按 `common` 现状执行（即不对这 114 个文件新增 lint 门槛），是否给整个 `common`
   开 `-Xlint:all` 作为独立事项另议——它会引入一批未知量的新告警，混在本次变更里会淹没 diff。
4. **边界 lint 改取材范围**：`guardLint` 的 L1 规则从"扫 `common-api/src/main/java` 全树"改为
   "扫 `common/src/main/java` 下 `com/tkisor/nekojs/api/**`"，禁止清单不变（MC / Loader / Graal）；
   L2 规则（`common` 零 MC / Loader import）不变。`common/build.gradle` 的 `checkCommonIsolation`
   保留——它与 guardLint 重叠，但它是 `:common:check` 的一部分，能在不跑根项目任务时兜住。
5. **注解处理器**：节点 convention plugin 里的 `annotationProcessor(project(":common-api"))`
   改为 `annotationProcessor(project(":common"))`；`common-api-processor` 的
   `compileOnly project(':common-api')` 同改。代价是 3 个 NeoForge 节点的**处理器路径**上会多出
   `common` 与 Graal——编译期与运行期 classpath 不受影响（`compileOnly` 非传递，且消费者本来就
   通过 `implementation(project(":common"))` 拿到了 `common` 与 Graal）。若处理器路径的体积日后
   成为问题，退路是把 `api/spec/**`（`PlatformAvailability` + 10 个 `*Spec`）单独拆成一个只含
   注解与 spec 接口的小模块；本次不做，因为为 1 个注解保留一个模块正是本文要消除的形状。
6. **删除 `$SwitchMap$` 指纹守卫**（`common/build.gradle` 那 40 行）与 `sha256Of` 辅助闭包。
   同模块内 javac 会因源码变更正常重编译，跨 jar 的 ABI 编译回避不再适用。
7. **修掉 `common/build.gradle` 里指向 `checkApiBoundaries` 的注释**——仓库里没有这个任务，
   真正的检查是 `guardLint`。
8. **文档同步**：ADR-0007 的四层表改为三层（`common` / 版本树 / 节点目录），并记录修订理由；
   `wiki/项目架构.md` 的模块结构、`README.md` 的源码结构、`wiki/构建系统.md` 的模块边界一节
   同步。ADR-0007 决策 1（插件入口迁 `common-api`）连同 ADR-0010 §4 的推回一起归档为"该路线
   已终止"，避免后来者重走。

### 第二步：构造入口回到类型上（恢复 v1.0.4 的形状）

9. **`EventBusFactory` 退役**：5 个创建方法改回契约类型自身的静态工厂。命名取 v1.0.4 的原名而
   不是造新的——`EventBus.create` / `CancellableEventBus.create` / `DispatchEventBus.create` /
   `DispatchCancellableEventBus.create`，以及 `DispatchKey.of(keyType, toKey)` /
   `DispatchKey.of(keyType)` / `DispatchKey.string()`。调用点从 `EventBusFactory.createXxx` 改为
   对应静态工厂。
10. **接受 `api.event` ↔ `eventbus` 的包环**。这是第二步的全部代价，也是 v1.0.4 的原状：契约包
    import 实现包。它不影响编译、不影响运行，只违反"包依赖应当是 DAG"这条洁癖。换来的是构造
    入口长在类型上。若将来确实想恢复 DAG，正确做法是把实现移进契约包（同包内 package-private
    实现类），而不是再立一个工厂类。
11. **`EnvironmentKeyFactory` 退役**：`current()` 并入 `EnvironmentKey` 作为静态工厂。注意它与
    上面几个不同——它的阻碍不是包环而是 `platform.Platform` 的位置，并入模块后才成立。
12. **不做**的事：不改任何接口的实例方法签名，不改实现类的行为，不动 `precedence` / 冲突语义。
    第二步只搬构造入口的位置。
13. **迁移期兼容**：不保留 `EventBusFactory` 的委托壳。它是引擎内部类型，21 个调用点全在本仓
    （`common` 主源 4 处、`common` 测试 1 处、版本树 16 处——共享树 `bindings/event` 与
    `client/render` 共 10 处、`1.21.1` 节点 1 处、`26.1.2-fabric` 节点 5 处），一次改完；保留一个
    空壳只会让"构造入口在哪"重新变成两个答案。

## Testing Decisions

好的测试只钉外部可观察的行为。本变更的性质决定了它的验收方式与功能特性不同：**第一步的正确性
判据是"什么都没变"**，所以主要手段不是新写测试，而是让既有的冻结类门禁充当见证——如果它们在
纯移动之后仍然逐字节通过，就说明对外表面确实没动。

**不新增测试接缝。** 全部复用现有的四个：

1. **`guardLint` 的模块边界规则**（`stonecutter.gradle.kts`，CI 强制）——唯一需要改动的接缝。
   改的是取材范围（源码目录 → 包前缀），断言不变。改完要验证两件事：现有 `api.*` 全部通过
   （已实测 `common` 那 66 个 `api.*` 文件零 Graal import）；以及规则仍然会失败——故意在
   `api.*` 下加一行 Graal import，确认 lint 报错，再撤掉。**没有这一步就等于把纪律悄悄关掉了。**
2. **`ApiManifestGoldenTest`**（`common/src/test/.../core/api/`）——冻结引擎的 API 表面。第一步
   之后这个 golden **必须一字不改地通过**；它一旦要改基线，说明"纯移动"的前提破了。这是第一步
   最有力的单一见证。
3. **probe golden**（`ProbeOutputCompatibilityTest` / `LegacyProbeTreeTest` /
   `TypeScriptNoopIrGoldenTest`）——`.d.ts` / `.pyi` 产物逐字比对。类型声明由反射生成，FQCN 不变
   则产物不变；这组 golden 通过即说明脚本作者侧无感。
4. **`SpecCoverageProcessorTest`**（`common-api-processor`）——处理器依赖改指向后必须仍然通过。
   它内嵌编译源码字符串，不依赖真实 jar 布局，所以它验证的正是"处理器仍能加载到注解类型"。

**第二步需要的新测试**：只有一处。`EventBusFactory` 退役后，新的静态工厂应当有一个测试断言
四种总线的**可取消性与分发语义未变**——即 `EventBus.create` 造出的总线 `canCancel()` 为假、
`CancellableEventBus.create` 为真、`DispatchEventBus.create` 按 key 分发。现有
`EventBusJSPriorityKeyTest` 已经覆盖了 dispatch key 的行为，扩一个用例即可，不新建测试类。
**不要**为静态工厂本身写"调用它返回非 null"这种测试——那是在测实现细节。

另外第二步有一个便宜的等价性检查值得做一次：改调用点之前先跑一遍 `:common:test` 与四节点
`build` 留底，改完再跑，两次结果应当完全一致。因为静态工厂与旧工厂方法返回的是同一批实现类，
任何行为差异都意味着改写时手滑了。

**回归风险最高的两点，各有对应的既有门禁**：枚举重排导致的 `$SwitchMap$` 错误分派（删掉指纹
守卫后由 javac 正常增量编译保证，`:common:test` 全量跑即可暴露）；fat jar 装配来源变化
（四节点 `build` 覆盖，CI 已有各平台 jar 内容检查）。

验收命令：`guardLint` + `:common:check` + `:common-api-processor:test` + 四节点 `build`
（即 `sandboxCheck` 的范围）。

## Out of Scope

- **不发布 Maven 制品**，不为插件作者提供可单独依赖的 API jar。那需要先解决插件钩子参数类型
  与引擎实现的解耦（见 Solution 里"为什么不选另一条路"），是独立议题。
- **不动 `NekoJSPlugin` 的位置**。它按 ADR-0010 §4 留在 `common`，本变更让它周围的类型与它同
  模块，不改它本身。
- **不给整个 `common` 开 `-Xlint:all`**。合并后有条件这么做，但它会引入未知量的新告警，与本
  变更混在一起会让 diff 无法审阅。
- **不重划 `api.*` 的内容**。那 67 个"只被 `common` 引用"的类型是否还该留在 `api.*` 包下是另一
  个问题——本变更不搬任何类型出 `api.*`，因为一旦开始搬就会改 FQCN，"纯移动"的前提就没了。
- **不合并 `common-api-processor`**。它必须是独立制品才能作为 `annotationProcessor` 使用。
- **不动版本树与节点目录的划分**（ADR-0007 的下两层不受影响）。
- **不改脚本 API**。脚本作者侧零变化，不需要迁移表。

## Further Notes

- **两步的顺序不可交换。** 第二步依赖第一步：只有契约类型与实现同模块之后，静态工厂才可能编译
  通过。反过来先退役工厂类是做不到的。
- **第一步的 diff 会很大但很平。** 114 个文件移动 + 20 个测试移动 + 4 处构建脚本改动 + 若干文档。
  审阅时应确认 diff 里只有"路径变了"和"删除了指定的机制"两类，任何一处内容变更都值得追问。
  建议移动与删除机制分成两个提交，让 `git log --follow` 能干净地跟过去。
- **第二步会顺手改善版本树的可读性。** 版本树里 16 处 `EventBusFactory.*` 调用是它唯一直接引用
  引擎实现包的地方（共享树的 `bindings/event` 与 `client/render`，加上 `1.21.1` 与
  `26.1.2-fabric` 两个节点目录）；换成 `DispatchKey.of` / `EventBus.create` 之后，那一层对引擎的
  依赖就只剩契约类型。
- **"什么该放 `api.*`"这条自查规则**在文档同步时要写清，否则并入之后这个包会变成默认堆放地。
  可用的判据：被版本树或插件消费的类型放 `api.*`（当前 43 个符合），只有引擎内部消费的不放。
  这条判据现在不成立（67 个只被 `common` 引用的类型住在里面），本变更不清理它们，但把判据写下来，
  让新代码至少不再加剧。
- **枚举与 `switch` 的那条坑值得留一句注释**在 `common` 里：删掉指纹守卫之后，如果将来又把
  `api.*` 的枚举拆到另一个制品，同样的陷阱会原样回来。
- **顺手可清的三个类型**：`api.contract.ApiContractViolation`、`api.data.NullJsValueView` 谁都不
  引用，`api.data.ConversionContext` 只被 common-api 自己的测试引用。它们不属于本变更（会改
  FQCN 集合），但并入之后再删会更容易——建议作为后续小改动单独处理。
- **这次调整的性质是回归而非重构。** 从 `v1.0.4` 到今天，事件总线的构造入口经历了
  "接口自带静态工厂" → "剥离到同模块的工厂类"（`3b9882df`）→ "被模块边界固化成唯一可能"
  （`ff55a7b1`）三步。方案的第二步把它退回第一步的形状，第一步则是拆掉让它无法退回的那道墙。
  审阅时可以直接对照 `git show v1.0.4:src/main/java/com/tkisor/nekojs/utils/event/EventBus.java`。
