# 票 28 迁移材料：PostEffects 声明与运行时分离

日期：2026-09-21　worktree：`D:/mcmodDemo/NekoJS-mult-t28`（分支 `ticket-28-post-effects`）

## 1. 旧静态路径 → 新路径对照

| 能力 | 旧路径（改造前） | 新路径 | 兼容性 |
|---|---|---|---|
| 声明运行期 post chain（JSON/GLSL/blur） | `PostEffects.register(id, options)`（binding 直连 `PostEffectManager.register`，脚本执行期立即写入进程级 `DEFINITIONS`） | `ClientEvents.postEffects(event => event.register(id, options))`（候选期收进 inert 计划，commit 点整体安装） | **breaking**：`PostEffects.register` 已从 binding 删除（见 §2.1） |
| 撤下已声明定义 | `PostEffects.unregister(id)`（立即从 `DEFINITIONS` 移除 + 命中当前效果则 clear） | `ClientEvents.postEffects(event => event.unregister(id))`（本批 commit 时退役） | **breaking**：`PostEffects.unregister` 已从 binding 删除 |
| 声明移除（脚本不再声明） | 无显式语义：reload 时 `Binding#close` → `PostEffectManager.clearRegistered()` 整表清空 | 成功 reload 后「旧 generation 拥有但本批未声明」的 id 自动 RETIRE（`retired` 计数可观察） | 行为收紧：不再是「整表清空 + 旧定义静默消失」，而是显式退役并保留新 generation 的声明 |
| 定义是否存在 | `PostEffects.has(id)`（读进程级 `DEFINITIONS`，不区分 generation） | `PostEffects.hasDefinition(id)`（读 active generation；退役/未声明返回 false） | **breaking**：`has` 改名并收紧语义 |
| 已声明定义列表 | 无 | `PostEffects.installed()`（排序后的 id 列表，只读快照） | 新增 |
| generation / stale 查询 | 无 | `PostEffects.activeGeneration()`（已提交的声明 generation，未安装为 -1） | 新增 |
| 运行时激活 | `PostEffects.set(id)` | 不变（仍在 binding；行为、client-thread 执行、仅资源支持的 id 可激活） | 保持 |
| 运行时清除 / 切换 / 读取 | `clear()` / `toggle(id)` / `current()` / `isActive()` | 不变 | 保持 |
| 资源可用性 | `PostEffects.isAvailable(id)` | 不变（26.x: `assets/<ns>/post_effect/<path>.json`；1.21.1: `assets/<ns>/shaders/post/<path>.json`） | 保持 |
| 预设列表 | `PostEffects.presets()` | 不变 | 保持 |
| 声明入口的时机 | 脚本任意位置（含 tick 回调内）调用即生效 | 只有 `ClientEvents.postEffects` 监听器在候选收集期被调用才收集；tick 内调用 `register` 现在是 no such member（显式失败） | **breaking**：时机从「随时」变为「声明期」 |

### 1.1 脚本迁移示例

```js
// 旧（1.1.x 及之前）
if (PostEffects.isAvailable('minecraft:invert')) {
  PostEffects.register('nekojs:gray', { fragmentShader: shaderSource })
  PostEffects.set('nekojs:gray')
}

// 新
ClientEvents.postEffects(event => {
  event.register('nekojs:gray', { fragmentShader: shaderSource })
  event.unregister('nekojs:old_effect')   // 可选：显式退役
})
ClientEvents.tickPost(() => {
  if (PostEffects.isAvailable('minecraft:invert')) {
    PostEffects.set('minecraft:invert')   // 运行时动作仍是 binding
  }
})
```

最小可运行示例：同目录 `examples/post-effects-declaration.js`。

## 2. 删除条件与 breaking 清单（维护者 sign-off 项，未勾选）

### 2.1 已删除的公开符号

| 符号 | 旧形态 | 替代路径 | 无消费者证据 |
|---|---|---|---|
| `PostEffectsJS#register(String, Map)` | public 脚本成员 | `ClientEvents.postEffects` → `PostEffectEventJS#register(String, Map)` | 全仓无调用点：`grep -rn "PostEffects.register\|\.register(" src/ wiki/` 中 PostEffects 相关命中只在 `wiki/全局绑定.md` 的旧文档示例（文档更新见 §3）；Java 侧生产代码零引用 |
| `PostEffectsJS#unregister(String)` | public 脚本成员 | `PostEffectEventJS#unregister(String)` | 同上 |
| `PostEffectsJS#has(String)` | public 脚本成员（读旧进程级注册表） | `PostEffectsJS#hasDefinition(String)` | 同上（本票改名，非语义倒退） |
| `PostEffectManager#register/unregister/clearRegistered/getRuntimeShaderSource(id,type) 的旧调用点语义` | 进程级静态注册表 `DEFINITIONS` 的写入方 | `PostEffectManager#parseDefinition` + `installGeneration(generation, definitions, retired)`（只有 Adapter 调用）；`getRuntimeShaderSource` 保留但读 active generation | `PostEffectManager` 的写入方只剩 `PostEffectDomainOwner.apply`（唯一生产调用点） |

删除条件（已满足的部分 / 待维护者确认的部分）：

- ✅ 替代路径 parity：`PostEffectDeclarationLifecycleTest`（8 tests × 3 节点）覆盖声明→候选→commit→回读→退役；
- ✅ 失败保留：`invalidChainJsonFailsTheWholeBatchAndKeepsTheOldActiveGeneration`、
  `collectionErrorFailsTheWholeBatchInDomainPlanAndALaterCandidateStillCommits`；
- ✅ 迁移表：本文档 §1/§1.1；
- ✅ 旧 route 无消费者：见上表最后一列；
- ⬜ **维护者确认删除**：`PostEffects.register` / `unregister` / `has` 是脚本可见公开面，
  属 breaking；按票据 Human input note，不等同于勾选 AC9。

### 2.2 保留但语义收紧的行为

| 行为 | 变化 | 理由 |
|---|---|---|
| 运行时声明 id 无法激活 | 旧：`set` 打 warn 后返回 false（定义仍留着）；新：`set` 用 `isDeclarationOnly` 判定，同样返回 false 并 warn | 命名与语义对齐（不再是「runtime-only」而是「有声明但无资源」）；不静默渲染空画面 |
| CLIENT reload 后的定义 | 旧：`Binding#close` 整表清空（reload 前发生）；新：成功 commit 时按 generation 退役 + 安装 | 候选期不可见、失败保留旧 active（spec 09）；清空时机从「reload 前」移到「commit 点」 |
| **reload teardown 是否清屏** | 旧：`PostEffectsJS#close` → `PostEffectManager.clearRegistered()`，清声明账本时**顺带清掉当时正在生效的 runtime post effect**（调用者可见的清屏副作用）；新：`PostEffectsJS` **不再覆写 `close`**（回到 `Binding` 默认 no-op），声明由 commit 换装，运行时画面只由显式 `set`/`clear` 改变 | **行为变更（规格轴审查 P4 整改）**：保留清屏需重新裁定「reload 是否应关画面」，且与「候选期不可见 / 失败保留旧 active」方向相反；默认 no-op 是语义最小的动作 |
| 资源 reload 时的 chain 缓存 | 不变：`ShaderManagerMixin` 的 `apply`/`close` 仍调 `invalidatePostChainCache()`；新增：退役单个 id 时关闭其缓存链 | 释放顺序可观察（`retired` 计数 + 缓存清理） |

## 3. 文档与消费者清单

| 消费者 | 位置 | 处理 |
|---|---|---|
| 旧脚本示例（`PostEffects.register`） | `wiki/全局绑定.md:287-298` | **未在本票更新**（wiki 属维护者文档面，且改它会与其它票的文档工作重叠）→ 记为遗留项，见 §4 |
| 运行时 binding 用法 | `wiki/全局绑定.md:289` | 仍然有效 |
| Java 侧调用点 | 全仓零调用（见 §2.1） | — |
| Fabric | `versions/26.1.2-fabric` | PostEffects 整包缺席：fabric 生成源码里四个 posteffect 类整文件被注释（`//? if neoforge` 为假 ⇒ 类不存在），`ClientEvents` 在 fabric 只有 tick/tickPost/tickPre，脚本引用 `PostEffects.*` 或 `ClientEvents.postEffects` 得到「未定义标识符 / No such event bus」的确定失败（非静默 no-op）。**未**新造 fabric capability 矩阵文件（正式 capability 记录归 fabric 面 owner，见 REPORT §5） |

## 4. 遗留（owner）

- `wiki/全局绑定.md` 的 PostEffects 段落仍写旧 `register` 写法 —— owner：维护者文档面（票 37 发布交接）。
- Fabric capability/source-trace 的正式记录 —— owner：票 31/32 fabric 面 owner。
- 真机客户端渲染 smoke（声明链实际出图） —— owner：票 34 P4 真机试做。
