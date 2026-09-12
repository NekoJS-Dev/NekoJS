# global 共享状态与候选写入规格

Status: ready-for-agent
Type: spec

## Problem Statement

当前 `global` 以一个进程级 Map 被所有 ScriptType 直接共享。候选 SERVER 脚本即使随后失败，也可能已经把顶层 key 写进这个 Map，从而绕过只切换 Context 和 listener 的失败保留机制；同名 key 又会让 STARTUP、SERVER、CLIENT、TEST 意外互相覆盖，而显式跨类型共享也没有清晰入口。与此同时，`global` 与语言全局对象 `globalThis` 的名称冲突、Node shim 的安装顺序、并发 writer 的冲突处理、server stop 或切换世界后的保留期限，以及独立测试 root 的状态串扰都缺少可观察契约。脚本作者需要知道：哪些状态按类型共享、哪些必须显式共享、候选写入何时发布、失败和冲突时旧值是否保留、状态能活多久，以及内存共享为何不能替代网络同步。

## Solution

在同一个 `NekoRuntimeRoot` 内，`STARTUP`、`SERVER`、`CLIENT`、`TEST` 各自拥有独立的 `global` backing store；同类型的多个脚本文件和该类型的普通 reload 共享同一 store，不同类型不因顶层 key 同名而互相覆盖。另提供独立的进程内显式共享入口，工作名为 `shared`，正式名称在 W5 Managed Surface 落地时冻结；它沿用同一种窄域 Map Interface 和顶层 key 语义，不增加第二 runtime owner、权限系统或全仓事务框架。类型私有 `global` 与显式共享入口的顶层 `set`、`delete`、`clear` 都进入候选写集，候选必须 read-your-writes，只有成功 commit 才发布；其他 writer 在候选准备期间修改同一受管顶层 key 时必须检测冲突并让候选失败，不能以旧全量快照覆盖。一次候选同时修改两者时，写集联合预检并交接，不能半提交。嵌套对象内部修改、已共享 Java 对象和高级 Java 访问不承诺深回滚。Map 在同一 root 内跨普通 reload、server stop 和切换世界保留，由 root 最终关闭释放；独立 root 与测试 runner 从空状态开始。`global` 是 NekoJS 状态容器，`globalThis` 是当前 Context 的语言全局对象，二者不承诺相等，Node shim 必须使用正确的语言全局对象，进程内共享也不替代客户端/服务端网络同步。

## User Stories

1. 作为整合包脚本作者，我希望同一 ScriptType 的多个脚本文件共享同一 `global`，以便模块化脚本可以读写同一类型内状态。
2. 作为整合包脚本作者，我希望同一 ScriptType 的 `global` 跨普通 reload 保留，以便脚本迭代不会无故丢失运行期状态。
3. 作为整合包脚本作者，我希望 STARTUP、SERVER、CLIENT 和 TEST 的 `global` 彼此独立，以便同名 key 不会因类型不同而互相覆盖。
4. 作为脚本作者，我希望候选脚本写入后立即读到自己写入的值，以便同一候选内的逻辑保持一致。
5. 作为脚本作者，我希望候选成功提交后其顶层 `set` 才对 active 可见，以便状态发布与脚本切换同步。
6. 作为脚本作者，我希望候选失败时顶层 `set` 被丢弃，以便错误脚本不会污染旧 active 的状态。
7. 作为脚本作者，我希望候选失败时顶层 `delete` 被丢弃，以便失败脚本不会删除旧 active 仍在使用的值。
8. 作为脚本作者，我希望候选失败时顶层 `clear` 被丢弃，以便失败脚本不会清空旧 active 的命名空间。
9. 作为服务器管理员，我希望其他 writer 在候选准备期间修改同一受管顶层 key 时冲突被检测，以便候选不会用旧快照覆盖新写入。
10. 作为脚本作者，我希望不同 ScriptType 的私有 `global` 写入不互相冲突，以便类型隔离和并发写入可以同时成立。
11. 作为脚本作者，我希望通过明确的 `shared` 入口跨 ScriptType 读写状态，以便跨类型共享成为主动选择而不是隐式副作用。
12. 作为服务器管理员，我希望共享入口的竞争写入被检测并让候选失败，以便并发修改不会静默丢失。
13. 作为脚本作者，我希望一次候选同时修改私有 `global` 与 `shared` 时，两边的写集联合提交或全部不发布，以便不会出现半提交状态。
14. 作为脚本作者，我希望只有顶层 key 的 `set`、`delete`、`clear` 进入事务保护，以便我清楚知道哪些操作可回滚。
15. 作为脚本作者，我希望嵌套对象、列表和已共享 Java 对象的内部修改不承诺深回滚，以便可以通过构造新值再替换顶层 key 获得事务保护。
16. 作为脚本作者，我希望保存 guest 函数或 `Value` 到 `global` 或 `shared` 不会让其获得永久生命周期，以便已销毁 Context 的引用仍按 generation 失效。
17. 作为服务器管理员，我希望普通 Map 的并发安全不被表述为任意 guest 对象可跨线程安全调用，以便共享状态的线程边界真实可信。
18. 作为服务器管理员，我希望同一 root 内 server stop 或切换世界不会自动清空 `global` 与 `shared`，以便运行期状态按 root 生命周期保留。
19. 作为服务器管理员，我希望 `NekoRuntimeRoot` 最终关闭时释放这些 Map，以便关闭后不继续持有状态。
20. 作为维护者，我希望 generation close 不清理 root 级状态，以便一次脚本环境关闭不会误删其他 generation 或类型的共享值。
21. 作为测试维护者，我希望新的独立 root 或测试 runner 从空状态开始，以便测试不会沿用前一次运行的状态。
22. 作为维护者，我希望保存的 world、server、session 或 guest 引用不会延长其有效生命周期，以便 Map 保留不等于对象保活。
23. 作为脚本作者，我希望用户脚本中的 `global` 表示 NekoJS 状态容器，而 `globalThis` 表示当前 Context 的语言全局对象，以便两者语义不会被混为一谈。
24. 作为脚本作者，我希望 Node shim 和模块内部安装、解析使用正确的语言全局对象，以便 `global` 状态容器不会覆盖 `globalThis`。
25. 作为维护者，我希望完整环境、typed 声明和 runtime binding 对 `global` 与 `globalThis` 的分工一致，以便声明不会诱导脚本使用错误对象。
26. 作为整合包作者，我希望进程内共享不被描述为网络同步，以便客户端、服务端和多人环境不会被错误当成共享同一 JVM 内存。
27. 作为脚本作者，我希望旧的跨类型 `global.foo` 用法按迁移表改走显式共享入口，而同类型 `global.foo` 继续可用，以便 1.2.0 clean cutover 有明确迁移路径。
28. 作为维护者，我希望迁移同步 core/Fabric binding、contract、declaration、wiki 与示例，并且不保留旧新双写或隐式回退，以便共享状态只有一套标准实现。

## Implementation Decisions

- 私有命名空间：同一个 `NekoRuntimeRoot` 内，`STARTUP`、`SERVER`、`CLIENT`、`TEST` 各有独立 backing store；同类型脚本文件共享该类型的 `global`，并跨该类型的普通 reload 保留。不是每个文件一份，也不是在原共享 Map 中增加类型子键。
- 显式共享入口：提供独立的进程内共享入口，工作名为 `shared`，正式名称在 W5 Managed Surface 落地时冻结。它沿用同一种窄域 Map Interface 和顶层 key 语义，只在同一 root 所属进程内共享，不替代客户端/服务端网络协议。
- 隔离边界：类型私有 `global` 只隔离命名空间与受管写入，不是强安全沙箱；高级 Java 访问或主动共享引用造成的别名仍受既有副作用边界约束。该隔离不收紧既有高级 Java 访问。
- Candidate 写集：类型私有 `global` 与显式共享入口的顶层 `set`、`delete`、`clear` 都进入候选写集；候选必须 read-your-writes。只有成功 commit 才发布，失败则丢弃并让旧 active 继续看到旧值。
- 冲突处理：其他 writer 在候选准备期间修改同一受管顶层 key 时，不得用旧全量快照覆盖；必须检测冲突并让候选失败。跨类型同名私有 `global` 写入不冲突；共享入口的竞争写入必须检测。具体版本检测与实现留给 W1/W4。
- 联合交接：一次候选同时修改 `global` 与 `shared` 时，两者写集必须联合预检并交接，不能留下“一个已发布、另一个未发布”的半提交。
- 深回滚边界：不承诺对嵌套字段修改、集合内部修改、已共享 Java 对象的内部修改或高级 Java 访问做通用深拷贝或深回滚。需要事务保护的脚本应构造新值，再通过顶层 key 替换提交。
- Guest 生命周期：已销毁 Context 的 guest 函数或 `Value` 不因存入 `global` 或 `shared` 而获得永久生命周期；保存的 guest 引用仍受 generation 生命周期约束。普通 Map 的并发安全不得表述为任意 guest 对象可跨线程安全调用。
- 内存保留期限：在同一个 `NekoRuntimeRoot` 内，普通 reload、server stop 或切换世界不自动清空这些 Map。root 级保留期限不等同于 generation 生命周期：generation close 只释放该 generation 的资源，不清理同 root 的 Map；只有 `NekoRuntimeRoot` 最终关闭时释放这些 Map。新的独立 root 从空状态开始，独立测试 root 或 runner 不得沿用前一次运行状态；同一测试运行域内的显式 reload 遵循相同保留规则。
- 对象保活边界：Map 保留不延长其中 world、server、session 或 guest 对象的有效生命周期，也不提供跨进程重启持久化；世界数据继续由既有数据域负责。
- 语言全局对象：用户脚本中的 `global` 表示 NekoJS 状态容器，`globalThis` 表示当前 Context 的语言全局对象，不承诺 `global` 与 `globalThis` 恒等。Node shim 与模块内部安装、解析必须使用正确的语言全局对象，不得把状态容器静默覆盖成 `globalThis`，也不得拿状态 Map 替代模块所需的语言全局对象。
- 完整环境：安装顺序、typed/global 声明一致性和完整环境行为由 W4/W5 的 fixture 验证。绑定定义与按类型或 generation 创建的视图必须分开；普通 reload 不重新 bootstrap Plugin Runtime。
- 迁移与落点：按既定 1.2.0 clean cutover 编写脚本迁移表：同类型的 `global` 顶层成员访问继续使用；旧的跨类型 `global` 顶层成员访问改走最终冻结的显式共享入口。同步 core/Fabric binding、contract、declaration、wiki 与示例，不保留旧新双写或隐式回退兼容层。
- 所有权：W1/W4 由既定 Runtime Root 与 Script Execution Environment owner 负责 backing store、候选视图、联合写集与关闭顺序；W5 负责 Managed Surface contract、语言全局对象分工与完整环境验证。不得增加第二 runtime owner、权限系统或全仓事务框架。
- 非网络同步：同一 JVM 内不同 ScriptType 看到同一共享入口只说明进程内存共享，不能替代客户端/服务端协议，也不能证明独立客户端进程或多人环境可见性。

## Testing Decisions

测试优先现有最高公开 Seam：`NekoRuntimeRoot` 生命周期 Interface 与 Managed Surface 的 `global`/`shared` binding 是状态保留和候选写集的最高观察点；真实脚本 eval、reload 与 close 行为以及公开事件和声明结果作为当前可达路径与 prior art。断言应通过脚本读回值、候选成功或失败后的 reload 行为、独立 root 的读回结果和完整环境中的标识符解析来验证；不得直接断言私有 Map、锁字段或内部版本对象。

已有 prior art：

- `ScriptReloadRegressionTest` 覆盖 candidate Context、候选失败保留旧 Context、timer、listener 和 teardown 清理，可作为 reload 失败语义的 prior art；它没有覆盖 `global` 写集，不能作为本 spec 已通过的证据。
- `ReloadMemoryStabilityTest.fiftyConsecutiveReloadsDoNotGrowMemoryMonotonically` 提供重复 reload 的状态稳定性 prior art。
- `NodeModulesJsRegressionTest.nodeModuleSurfaceMatchesNodeSemantics` 覆盖 Node 模块表面和 shim 语境中的语言全局行为；该测试中的 `global === globalThis` 只属于 shim 语境，不能证明完整 NekoJS 环境中状态容器与语言全局对象的分工。
- `GlobalBindingMemberValidatorTest` 提供 binding 成员和已知 global 标识符校验的 prior art；它不覆盖完整环境中 typed/global 声明一致性。
- `WorkspaceGeneratorManagedTypesTest` 提供按 ScriptType 生成 managed types 配置的 prior art；它不覆盖 `global` 与 `shared` 的 runtime 值语义。

需新增 fixture，以下场景尚未通过这些测试，不能写成已通过：

- 同类型多文件共享同一 `global`，该类型普通 reload 后值保留；STARTUP、SERVER、CLIENT、TEST 对同名 key 的读回值彼此独立。
- 显式共享入口在同一 root 内跨 ScriptType 可见；独立 root 或测试 runner 从空状态开始，不串状态。
- 候选 read-your-writes；候选成功发布；候选失败时 `set`、`delete`、`clear` 都不污染 active。
- 其他 writer 与候选修改同一受管顶层 key 时检测冲突且不丢写；不同 ScriptType 的私有同名 key 不冲突；共享入口竞争写入失败。
- 候选同时写私有 `global` 与共享入口时联合成功或全部不发布，不出现半提交。
- 嵌套对象内部修改不深回滚；保存 guest 函数或 `Value` 不延长其 generation 生命周期；普通 Map 并发安全不被误写为任意 guest 对象可跨线程安全调用。
- 同一 root 内 server stop 或切换世界不隐式清空；root close 释放；generation close 不误清；独立测试运行域不串状态。
- 完整环境覆盖 NekoJS `global` 与 `globalThis` 的分工、Node shim 使用正确语言全局对象、typed/global 声明一致性，以及 core/Fabric 的 binding 装配。
- 迁移表中旧跨类型示例到显式共享入口逐项对应；同类型用法保持不变；不保留旧新双写或隐式回退；多人网络语义不由进程内共享示例代替。

本轮未运行上述构建、测试或迁移；这些 fixture 是实施验收输入，不是已通过证据。

## Out of Scope

本轮仅做规格生成；不修改 Java、Gradle、Stonecutter、CI 或源码，也不执行实际迁移、生成 wiki/示例或运行构建、测试。这是本轮生成边界，不是本规格的目标排除项。以下才是目标重构明确不包含的事项：

- 不承诺对嵌套对象、列表、已共享 Java 对象或高级 Java 访问做通用深拷贝或深回滚。
- 不把进程内共享当作网络同步，不提供跨进程、多人或重启持久化状态。
- 不增加第二 runtime owner、权限系统、全仓事务框架或新的网络协议。
- 不冻结 `shared` 的最终公开名称；最终名称由 W5 Managed Surface 落地时确定。
- 不收紧既有高级 Java 访问；不保证任意 guest 对象可跨线程安全调用，也不改变 generation 对 guest 引用的生命周期约束。
- 不改变既有世界数据、pdata 持久化路径或跨 loader 数据语义；`global` 与 `shared` 只是运行期内存状态。

## Further Notes

源决策是 [跨 reload 的 global 共享状态如何参与候选事务？](../decisions/10-shared-global-candidate-writes.md)。本 spec 是该 Resolution 的派生实施输入；若本 spec 与 Resolution 冲突，以 Resolution 为唯一裁决权威。生命周期与候选隔离引用 [运行时所有权、reload 与数据保护的契约是什么？](../decisions/05-runtime-lifecycle-and-data.md) 与 [reload 候选环境、状态所有权与线程边界如何闭合？](../decisions/09-reload-candidate-state-and-thread-contract.md)，迁移与发布门禁引用 [怎样以可验证的阶段完成本次重构并作为新标准？](../decisions/07-validation-and-migration.md)。

相关 spec 为 [05 运行时生命周期与数据保护](05-runtime-lifecycle-and-data.md) 和 [09 reload 候选状态与线程契约](09-reload-candidate-state-and-thread-contract.md)。现有测试 prior art 与运行时证据见 [testing evidence](../evidence/testing-and-docs.md) 和 [runtime evidence](../evidence/runtime-and-modules.md)。

Status 为 ready-for-agent 只表示本 spec 可交给实施代理，不表示本轮已经授权修改源码、构建、运行测试或执行迁移；实施仍须取得维护者的单独授权并满足 07 的 release gate。