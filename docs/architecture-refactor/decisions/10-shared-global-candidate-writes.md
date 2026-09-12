# 跨 reload 的 global 共享状态如何参与候选事务？

Status: closed
Type: grilling
Mode: HITL
Parent: [NekoJS 架构重构路线图](../../architecture-refactor-map.md)
Assignee: 当前规划收口会话（主 agent，与维护者裁决）
Blocked by: [运行时所有权、reload 与数据保护的契约是什么？](05-runtime-lifecycle-and-data.md)、[reload 候选环境、状态所有权与线程边界如何闭合？](09-reload-candidate-state-and-thread-contract.md)

## Question（历史）

候选 SERVER 脚本先执行 `global.count = 2`，随后抛错，旧 active session 保留下来；此时它应读到旧值还是 `2`？

现有 `global` 是 NekoJS 注入、跨 ScriptType 与 reload 共享的进程级 Map，不是 generation 私有绑定。候选直接写入该 Map，会绕过只切换 Context/listener 引用的失败保留机制。本票需要划分“共享 Map 的顶层键操作”与“键中任意 Java/JS 对象的内部修改”，并决定 `global` 是否应按 `STARTUP` / `SERVER` / `CLIENT` / `TEST` 隔离，而不是所有 ScriptType 共用一个进程级 Map。

本票只补齐 `global` 的事务与作用域，不重开单 runtime owner、Plugin Runtime 进程级、候选事件隔离、高级 Java 访问或动态注册保留的决定。

## Evidence（决策时静态核查）

- 决策时 `NekoGlobal` 使用一个进程级静态 `ConcurrentHashMap`：所有 Context 的绑定都直接指向同一对象，注释和 wiki 宣称 server/client/startup/test 可互相读写，并跨 reload 保留。证据：[NekoGlobal](../../../common/src/main/java/com/tkisor/nekojs/bindings/static_access/NekoGlobal.java):10-16,26,30-34；[脚本基础](../../../wiki/脚本基础.md):69-84；[全局绑定](../../../wiki/全局绑定.md):507-518。
- NeoForge 与 Fabric 都把同一个 `NekoGlobal.shared()` 注册为 `global`，所以这不是某一加载器的局部差异。证据：[NekoJSCorePlugin](../../../src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java):182；[FabricCorePlugin](../../../versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricCorePlugin.java):91。
- 每个 ScriptType 有独立 Context 和独立 `ScriptManager`；`ScriptEnvironmentFactory.create(scriptType)` 创建对应 Context，并把该类型的 binding 直接放入 Context，决策时未为 `global` 构造按类型视图或候选视图。证据：[ScriptEnvironmentFactory](../../../common/src/main/java/com/tkisor/nekojs/script/ScriptEnvironmentFactory.java):53-74；[NekoRuntimeRoot](../../../common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java):42,73-82,117-128。
- 四种 ScriptType 的 API 面本来就不同：`STARTUP` 同时接受 server/client API，`SERVER` 接受 server API，`CLIENT` 接受 client API，`TEST` 独立。证据：[ScriptType](../../../common/src/main/java/com/tkisor/nekojs/api/ScriptType.java):7-11,44-56,59-60。
- 生命周期也不同：`STARTUP` 在 composition root 初始化时加载；`CLIENT` 在客户端 setup 时加载并可在资源 reload 时重载；`SERVER` 在服务器资源 reload / 世界包激活时重载；所查服务器停止路径会清理世界包 listener，但不清理 `global`。证据：[NekoJSMod](../../../src/main/java/com/tkisor/nekojs/NekoJSMod.java):173-179；[NekoJSClient](../../../src/main/java/com/tkisor/nekojs/client/NekoJSClient.java):43-48,67-81；[ServerEventListener](../../../src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java):93-104,116-127,131-145。
- 决策时候选失败路径只恢复旧 `RuntimeEnvironment` 并关闭候选资源，不恢复或隔离共享 Map；`close()` 关闭 Context/binding，但没有清理 `NekoGlobal`。证据：[ScriptManager](../../../common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java):378-466,680-740。
- Node bootstrap 在 sandbox 安装阶段定义了 `globalThis.global = globalThis`（仅当未定义），而 `NekoGlobal` 是在之后由 `ScriptEnvironmentFactory` 放入 bindings；两者在同一名字上存在顺序覆盖关系，决策时测试没有覆盖完整环境中的最终 `global` 语义。该缺口由本 Resolution 的 fixture 要求承接。证据：[bootstrap.js](../../../common/src/main/resources/nekojs/node/bootstrap.js):1-3；[NekoNodeModuleInstaller](../../../common/src/main/java/com/tkisor/nekojs/core/node/NekoNodeModuleInstaller.java):30-38；[NekoSandboxFactory](../../../common/src/main/java/com/tkisor/nekojs/core/NekoSandboxFactory.java):146-154。
- 仓库检索未找到可执行的 startup→server/client 共享消费者；这不能外推为用户脚本不使用已写进 wiki 的能力，因此 Resolution 保留显式跨类型共享入口。已有 [ScriptReloadRegressionTest](../../../common/src/test/java/com/tkisor/nekojs/script/ScriptReloadRegressionTest.java):378-465 覆盖旧 Context 保留，但不是 global 写集的回归证据。
- 进程内共享不是网络同步：同一 JVM 内 server/client Context 看到同一 Map，只能说明本地进程内存；它不能替代客户端/服务端协议，也不能证明独立客户端进程或多人环境可见性。

## Resolution

维护者在本轮明确确认：“1.这个没问题 目前看下来似乎没问题了”。以下是本票唯一正式决议。

1. **按 ScriptType 私有 `global`**：同一个 `NekoRuntimeRoot` 内，`STARTUP` / `SERVER` / `CLIENT` / `TEST` 各有独立 backing store；同类型的脚本文件共享该类型的 `global`，并跨该类型的普通 reload 保留；不同类型不因顶层 key 同名而互相覆盖。不是每个文件一份，也不是在原共享 Map 中仅加 `global.server` 等子键。这里只隔离命名空间与受管写入，不是强安全沙箱；经高级 Java 访问或主动共享引用造成的别名仍受既定副作用边界约束。
2. **保留显式共享能力**：提供独立的进程内共享入口，工作名 `shared`，正式名称在 W5 Managed Surface 落地时冻结。它让跨 ScriptType 读写成为明确选择，沿用同种窄域 Map Interface 和顶层键语义，不增加第二 runtime owner、权限系统或全仓事务框架；只在同一 root 所属进程内共享，不替代客户端/服务端网络协议，也不收紧既有高级 Java 访问。
3. **候选写集与提交**：类型私有 `global` 与显式共享入口的**顶层 key** `set` / `delete` / `clear` 都进入候选写集；候选必须 read-your-writes，只有成功 commit 才发布，失败则丢弃并让旧 active 继续看到旧值。其他 writer 在候选准备期间修改同一受管顶层 key 时，不得用旧全量快照覆盖它；必须检测冲突并让候选失败。一次候选同时修改 `global` 与 `shared` 时，两者写集必须联合预检并交接，不能留下“一个已发布、另一个未发布”的半提交。跨类型同名私有 `global` 写入不冲突，共享入口的竞争写入必须检测。具体版本检测与实现留 W1/W4。
4. **不深回滚的边界**：不承诺对 `global.obj.field = ...`、`global.list.add(...)`、已共享 Java 对象的内部修改或高级 Java 访问做通用深拷贝/深回滚。需要事务保护的脚本应构造新值，再通过顶层 key 替换提交。已销毁 Context 的 guest 函数/`Value` 不因存入 `global` 或 `shared` 而获得永久生命周期；保存的 guest 引用仍受 generation 生命周期约束。普通 Map 的并发安全不得表述为任意 guest 对象可跨线程安全调用。
5. **内存保留期限**：在同一个 runtime root 内，普通 reload、server stop 或切换世界不自动清空这些 Map；由 `NekoRuntimeRoot` 最终关闭时释放，新的独立 root 从空状态开始。独立测试 root/runner 不得沿用前一次运行状态；同一测试运行域内的显式 reload 遵循相同保留规则。generation close 不清理 root 级状态。Map 保留不延长其中 world/server/session/guest 对象的有效生命周期，不提供跨进程重启持久化；世界数据继续由既有数据域负责。
6. **语言全局对象分工**：用户脚本中的 `global` 表示 NekoJS 状态容器，`globalThis` 表示当前 Context 的语言全局对象，不承诺 `global === globalThis`。Node shim/模块的内部安装与解析必须使用正确的语言全局对象，不得把状态容器静默覆盖成 `globalThis`，也不得拿状态 Map 替代模块所需的语言全局对象。安装顺序、typed/global 声明一致性与完整环境行为由 W4/W5 的 fixture 验证。
7. **迁移与实施落点**：按既定 `1.2.0` clean cutover 写脚本迁移表：同类型 `global.foo` 继续使用；旧的跨类型 `global.foo` 改走最终冻结的显式共享入口。同步 core/Fabric binding、contract/declaration、wiki 与示例；不保留旧新双写或隐式回退兼容层。W1/W4 由既定 Runtime Root / Script Execution Environment owner 负责 backing store、候选视图、联合写集与关闭顺序；W5 负责 managed contract、语言全局对象分工与完整环境验证。普通 reload 不重新 bootstrap Plugin Runtime；绑定定义与按类型/generation 创建的视图分开。
8. **实施验收边界**：实施必须至少提供以下 fixture/验证，而不是由本规划票宣称已通过：
   - 候选 read-your-writes、成功发布、失败保留旧值；`set` / `delete` / `clear` 在失败时均不污染 active。
   - 同类型多文件共享及跨 reload 保留；不同 ScriptType 的同名 key 独立；独立 root/测试运行域不串状态。
   - 显式跨类型共享可见；并发写入/删除/clear 的冲突不丢写；候选同时写私有 `global` 与共享入口时联合成功或全部不发布。
   - 在同一 root 内 server stop/切世界不隐式清空键；root close 清理，generation close 不误清；保存对象不延长其世界/session/guest 有效期。
   - 嵌套对象内部修改不深回滚及过期 guest 函数/`Value` 的边界；普通 Map 并发安全不被误写为任意 guest 对象可跨线程安全调用。
   - 完整环境覆盖 NekoJS `global` 与 `globalThis` 的分工、Node shim 的正确语言全局对象、typed/global 声明一致性，以及 core/Fabric 的绑定装配。
   - 旧跨类型示例到显式共享的迁移表可逐项对应；多人网络语义不由进程内共享示例代替。

本 Resolution 不改变已闭合的 [运行时所有权、reload 与数据保护的契约是什么？](05-runtime-lifecycle-and-data.md) 与 [reload 候选环境、状态所有权与线程边界如何闭合？](09-reload-candidate-state-and-thread-contract.md)：进程级 owner 与 generation 级资源仍分离，持久化路径/格式与外部副作用边界不变。本票只完成规划决策，不授权或宣称源码实施、构建、运行测试已经完成。