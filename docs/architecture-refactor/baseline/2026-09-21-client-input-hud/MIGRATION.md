# 票 26 迁移材料：CLIENT 输入与 HUD callback 生命周期

日期：2026-09-21　worktree：`D:/mcmodDemo/NekoJS-mult-t26`（分支 `ticket-26-client-input-hud`）

## 1. 组件与旧 route 对照

| 能力 | 旧路径（改造前） | 新路径 | 兼容性 |
|---|---|---|---|
| HUD 常驻渲染器注册 | `ClientEvents.hudRender(id, opts, cb)` → `RenderRegistrationBusJS.execute` → **直接** `ClientRenderRegistry.registerHud`（脚本执行期即上生产表，**候选期可见**） | 同一脚本写法 `ClientEvents.hudRender(id, opts, cb)`；候选期只进 inert 批次，commit 点整批换装 | **脚本写法不变**；语义收紧（候选期不可见、失败保留旧 active） |
| 世界常驻渲染器注册 | 同 `hudRender`（`worldRender`） | 同上 | 脚本写法不变 |
| 本代不再注册渲染器 | 无显式语义：旧表在下次 load 前被 `clearAll()` 清掉（时机错误，见下） | 空批次**也**参与 commit（`ClientRenderDomainOwner` 无条件注册计划），commit 后旧渲染器退役 | 行为收紧：退役发生在 commit 点而不是「加载前」 |
| 脚本加载前清理渲染器 | `ClientRenderPlugin.beforeScriptsLoaded(CLIENT)` → `ClientRenderRegistry.clearAll()`，触发点在 `ScriptManager` **候选脚本执行之前**（`ScriptManager.java:817`） | **已删除**（`ClientRenderPlugin` 整文件删除）。清理只在 commit 点（`publish()`）发生 | **行为变更**：失败候选不再清掉旧 active（这正是 AC4 的修复） |
| 单文件 reload | `clearAll()` 在 load 前执行 → 同代其它渲染器被误清 | 复用当前 active Context → 不换装、不清 | **行为变更（修隐患）**：单文件 reload 只跑该脚本，其注册按 id 覆盖，同代其它渲染器保留 |
| 初始加载 / 击杀重建 | 依赖「load 前 clearAll」清旧表 | `beginActiveGeneration(context)`：首个来自**新 Context** 的注册换装整表 | 行为等价（新 Context ⇒ 新 generation） |
| 按键绑定创建 | `KeyBindEvents.register(id, key[, category])` | 不变（同 id 幂等、跨 reload 存活、返回 `KeyMapping` 句柄） | 保持 |
| 输入事件订阅 | `KeyBindEvents.pressed/released/tick` | 不变（`EventBusJS.PendingListener`，本就 generation-scoped） | 保持 |
| HUD 每帧监听 | `ClientEvents.hud(painter => {})` | 不变 | 保持 |
| key mapping 注册事件 | `ClientEvents.registerKeyMappings(...)` | 不变 | 保持 |

### 1.1 脚本迁移示例

脚本侧**无需迁移**——`hudRender` / `worldRender` / `KeyBindEvents.*` 的调用形态与参数完全不变。变化的只是**生效时机**：渲染器在 CLIENT reload 的 commit 点整批生效，而不再在脚本执行到那一行时立即生效。

```js
// 写法不变（旧、新一致）
ClientEvents.hudRender('demo:badge', { layer: 'foreground', priority: 100 }, (ctx, gui) => {
  ctx.text('DASH READY', 6, 20, 0xFF55FF55)
})
KeyBindEvents.register('demo:dash', 'key.keyboard.r', 'movement')
KeyBindEvents.pressed('demo:dash', e => console.log('pressed ' + e.id))
```

最小可运行示例：同目录 `examples/client-input-hud.js`。

**可见的时机差异（需脚本作者知晓）**：在 `ClientEvents.hudRender` **之后**、同一脚本内立即依赖「该渲染器已生效」的代码不再成立（例如注册后立刻在别处查询）。回调本身不受影响：它在 commit 后的帧里被调用。

## 2. 保留但语义变化的成员

| 成员 | 变化 | 理由 |
|---|---|---|
| `ClientRenderRegistry.clearAll()` | 仍为 public；语义**增加**一条：现在会把 generation 身份（`activeContext`）一并置空，使下一次注册被当作新 generation 整表换装 | 原语义只清表；置空身份才能让「清空后的首次注册」正确建立新代。保留 public 供诊断/测试（`Ticket26ClientInputHudHarness.resetRegistry()` 即用它做用例隔离）。生产调用点已清零（旧调用者 `ClientRenderPlugin` 已删除） |
| `ClientRenderRegistry.hasHud/hasWorld` | 判定口径不变：**「存在非死 Context 的条目」**。注意：`ScriptManager.isContextDead(ctx)` 对**未注册**（已销毁并从 `CONTEXT_TO_MANAGER` 移除）的 Context 返回 `false`（`ScriptManager.java:106-121`），因此这类条目仍被算作「存活」 | **既有共享树语义，本票不改**。1.21.1 同样如此（`ClientRenderRegistry` 为共享树文件）。实际影响有界：commit 换装会清掉旧代条目；只有「条目所属 Context 被销毁但表未换装」的窗口内 `hasHud` 会偏保守（多做一次空 dispatch，`invokeEntry` 记录一次回调错误）。**注意不要**据此把 `hasHud` 当作「渲染器当前一定可执行」的判据 |
| `ClientRenderRegistry.dispatchHud/dispatchWorld` | 行为不变：按 priority 排序、单回调抛错只记录不中断、Context 已死则跳过并移除 | 保持 |
| `KeyBindEvents.REGISTER` 的绑定生命周期 | 不变：`BINDINGS` 进程级表，绑定跨 CLIENT reload 存活、同 id 幂等 | 既有 javadoc 明写的裁定语义（AC2 要求幂等），本票**刻意不改** |

## 3. 已删除的公开/装配路径（维护者 sign-off 项，AC9 未勾选）

| 符号 | 旧形态 | 替代路径 | 无消费者证据 |
|---|---|---|---|
| `ClientRenderPlugin`（整类，`src/main/java/com/tkisor/nekojs/client/render/ClientRenderPlugin.java`） | `@RegisterNekoJSPlugin(clientOnly = true)` 的 `NekoJSPlugin` + `LifecyclePoint.Contributor`；唯一方法 `beforeScriptsLoaded(ScriptType)` 在 CLIENT 时调 `ClientRenderRegistry.clearAll()` | 清理改由 commit 点 `ClientRenderRegistry.Candidate#publish()` 承担（整批换装即退役旧代） | 全仓 `java/kts/gradle/json/txt/properties/toml` 引用**仅该文件自身**；其余命中全在 `docs/architecture-refactor/baseline/**/raw/*.log` 历史运行日志（证据归档，非源码引用）；无 golden/gate 引用它（`EventSurfaceDomainGateTest` 只枚举 `registerEvents/registerClientEvents` 贡献者，该类不贡献任何成员）。见 `command-output/09-old-route-consumers.txt` |
| 新增装配点 `ClientRenderDomainOwner` | （新增，非删除） | `NekoJSMod.registerClient` 的 client dist 分支 `root.registerDomainCollector(...)` | 与票 28 `PostEffectDomainOwner`、票 22 `VillagerTradeDomainOwner` 同款接缝；fabric 侧未注册（见 §4） |

删除条件（已满足的部分 / 待维护者确认的部分）：

- ✅ **替代路径 parity**：`Ticket26ClientInputHudLifecycleTest`（4 tests × 26.1.2）覆盖候选→commit→换装→退役；空批次退役有专门用例。
- ✅ **失败保留**：`aRejectedCandidateKeepsServingThePreviousActiveRenderer`。
- ✅ **迁移表**：本文档 §1/§1.1。
- ✅ **旧 route 无消费者**：见上表最后一行与 `command-output/09`。
- ⬜ **维护者确认删除**：删除 `ClientRenderPlugin` 改变 CLIENT reload 的清理时机（调用者可见），按票据 Human input note，不等同于勾选 AC9。
  - 备选落地方案（若维护者倾向保留类）：保留 `ClientRenderPlugin` 但删除 `beforeScriptsLoaded` 覆写——代价是留下一个空壳 `@RegisterNekoJSPlugin` 组件（无职责）；本票选择整类删除。

## 4. 按节点差异（如实记录，不假装成对）

| 节点/侧 | 本域状态 | 说明 |
|---|---|---|
| 26.1.2 / 26.2.0（NeoForge） | 共享树实现 | `ClientRenderRegistry`/`RenderRegistrationBusJS`/`ClientEvents` 无版本守卫；本修复直接生效。`KeyBindEvents` 为 `//? if neoforge` + `//? if >=26` 守卫的 26.x 实现 |
| 1.21.1（NeoForge） | 共享树实现 + 三个 override | `ClientRenderRegistry`/`RenderRegistrationBusJS` 走共享树（同一份代码，修复生效）；`versions/1.21.1` 只有 `ClientRenderEvents`（阶段枚举分发的旧 API 形态）、`HudRenderContextJS`、`WorldRenderContextJS` 三个 override。**该节点没有 `ClientRenderPlugin` 孪生文件**（原本就是共享树唯一副本，删除后同样只剩共享树语义）。`KeyBindEvents` 在该节点**不存在**（`KeyBindIds` 为 `>=26`） |
| 26.1.2-fabric / 26.2.0-fabric | `KeyBindEvents` 独立孪生；渲染注册面缺席 | `src/fabric/.../bindings/event/client/KeyBindEvents.java` 是独立实现（`KeyMappingHelper` 时机差异、无 `RegisterKeyMappingsEvent` 窗口、无 pending 重试）。`ClientEvents.hudRender/worldRender`、`ClientRenderRegistry`、`ClientRenderDomainOwner` **不在 fabric 生成源码中**（neoforge 守卫为假）⇒ 脚本引用 `ClientEvents.hudRender` 得到「未定义标识符 / No such member」的**确定失败**，而不是静默 no-op。**本票未新造 fabric capability 矩阵条目**（正式记录归 fabric 面 owner，票 31/32） |

## 5. 遗留（owner）

- 上述 fabric capability/source-trace 的正式记录 —— owner：票 31/32 fabric 面 owner。
- 真机客户端渲染 smoke（HUD 实际出图、按键真实触发、`consumeClick()` 端到端） —— owner：票 34 P4 真机试做。
- TS/Python declaration 面覆盖 —— owner：Managed Surface/Probe owner（与票 28 AC7 同批缺口）。
- `hasHud` 对已销毁 Context 口径的收紧（若维护者认为需要） —— 需同时改 1.21.1 共享树语义，建议单独立票。
