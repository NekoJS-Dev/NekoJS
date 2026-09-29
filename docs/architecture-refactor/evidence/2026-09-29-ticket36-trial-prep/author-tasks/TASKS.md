# 票 36 脚本作者试做协议（TASKS.md）

配属于 [36: P4 维护者与脚本作者真实试做](../../../implementation-tickets/36-release-maintainer-trials.md)
的**脚本作者半边**。维护者四类任务的操作说明在姊妹包
[`../cookbooks/`](../cookbooks/README.md)；本包只覆盖「以脚本作者视角完成代表性脚本任务」。

- **AC3 对应**：每个任务只用对应节点已通过 gate 的 capability；unavailable / not verified
  的能力以 b 变体验证**显式拒绝**，试做者不得被要求寻找隐藏替代路径。
- **AC4 对应**：每个任务保留可复制示例（本目录脚本）、实际诊断/错误输出（试做时填入
  下方记录表）、公开材料是否足够的结论（填入「公开材料是否足够」列）。

判定口径（AC4）：阅读多份公开文档不算失败；**需要读内部实现、重复事实源、猜 owner、
绕过 managed API 或改 Java 才能完成** = 记录为失败或待修复。

## 试做方式

1. 按下方任务表任选节点起步（建议顺序：26.1.2 → 26.2.0 → 1.21.1 → 26.1.2-fabric →
   26.2.0-fabric；同一任务至少覆盖一个 supported 节点和一个 b 变体节点）；
2. 把任务脚本复制到其头部标注的脚本目录（`<gameDir>/nekojs/{startup,server,client}_scripts/`），
   执行头部标注的命令（`/nekojs reload <type>` 或重启游戏）；
3. 触发任务标注的事件源（登录、聊天、按键、爆炸等），采集**逐字**输出进记录表；
4. 每格「公开材料是否足够」按判定口径填：`足够` / `不足:<缺什么>` / `失败:<原因>`。

## 能力事实源（每行任务的能力依据）

- 平台 gate 读侧基线：`src/test/resources/nekojs/platform-gates/event-surface-domains.txt`
  （票 33 门禁；行格式 `事件组 | 节点 = present/not-verified | buses=…`）；
- 各任务所属票据的 REPORT / MIGRATION 能力表（下方「能力依据」列给出精确小节）；
- 已知边界：fabric 侧部分总线经 mod 入口桥接（`Fabric*EventBindings` 直接挂共享组），
  gate 行只记录经核心钩子注册的成员——成员级差异以实跑输出为准（见任务 5 的注）。

## 任务表

| # | 任务 | 脚本（放置目录） | 能力依据（票据基线） | 节点门状态 | 预期结果 / 诊断 |
|---|------|------------------|----------------------|-----------|-----------------|
| 1 | 启动期注册 | [01](01-startup-registration.js)（startup） | 票 15 REPORT §3 能力表；`NekoRegistryDeclarations` 糖方法目录 | 五节点 supported（fabric 为单批直注形状） | 重启后注册生效；五个糖方法段全部注册成功，`custom`/`register` 高级入口同批可用 |
| 1b | 同上：fabric 上 fluid 拒绝 | [01b](01b-startup-registration-fabric-fluid-reject.js)（startup） | 同上（fabric 目录只保留 5 个平台无关糖方法） | fluid 类型在 fabric unavailable | 收集期整批失败：`RegistryEvent has no member 'fluid'; known: […]`（`RegistryEventJS.getMember`） |
| 2 | Dynamic Registry | [02](02-dynamic-registry.js)（server） | 票 21 REPORT §5 能力表 + 基线示例头注 | 声明面仅 26.x NeoForge present；平台激活三类候选 not verified | reload 成功、声明进 inert 候选计划与账本，**不发生热更新**（激活门禁的预期状态，非缺陷）；改 fingerprint 后 reload 以 `dynamic-registry-conflict` 结构化失败 |
| 2b | 同上：无能力节点拒绝 | [02b](02b-dynamic-registry-unavailable-node.js)（server） | gate 行 `DynamicRegistryEvents = not-verified`（1.21.1 / fabric 两节点）；`DynamicRegistryJS.requireRunningServer` 消息 | 1.21.1 / 26.x fabric 不可用 | 「未知标识符 `DynamicRegistryEvents`」定位诊断 + ReferenceError；旧直注 `DynamicRegistry` 默认 `enabled = false` 时得 disabled 提示（26.x NeoForge 上可选验证） |
| 3 | Villager Trades add/query | [03](03-villager-trades-add-and-query.js)（server） | 票 22 REPORT §3（AC9/AC11）+ MIGRATION §1 | 26.x NeoForge / 1.21.1 supported | reload 成功；查询段输出 ACTIVE generation、tradeSetIds、countOf；`tradeReload` 段打印 total |
| 3b | 同上：fabric 拒绝 | [03b](03b-villager-trades-unavailable-fabric.js)（server） | 票 22 AC9 + MIGRATION §1/§2（fabric 装配 unavailable owner） | fabric 两节点不可用 | 声明段：reload 结构化失败（phase STATE_PLAN，`villager trade registry mutation has no Fabric implementation in this port`）；查询段见下方公开 TODO |
| 4 | Item / Block modification | [04](04-item-block-modification.js)（server） | 票 39 REPORT §8 capability/source-trace 表 | modification.item 五节点 supported；modification.block 26.x（含 fabric 26.x）supported | reload 成功后属性生效；复合组件（food/tool）仅 26.x；客户端纯视觉不同步（relog/resync，票 39 AC10 边界） |
| 4b | 同上：1.21.1 block 拒绝 | [04b](04b-block-modification-unavailable-1.21.1.js)（server） | gate 行 BlockEvents 1.21.1 无 modification 总线 | modification.block 在 1.21.1 unavailable | `No such event bus: BlockEvents.modification`（`EventGroupJS.getMember`）+ 加载侧「无此成员」定位诊断；Item 半边仍可用（复合组件段除外） |
| 5 | server 事件面（gameplay） | [05](05-gameplay-server-events.js)（server） | 票 24 REPORT + catalog-snapshot.md 对照表 | 五节点 present；成员面按节点有差异 | 26.x NeoForge / 1.21.1 全成员可跑；fabric 上 drops 载荷恒空、chat 不可取消（示例内注释），其余成员以实跑为准（见任务 5 注） |
| 6 | client 输入与 HUD | [06](06-client-input-hud.js)（client） | 票 26 MIGRATION §4 按节点差异表 | 26.x NeoForge 全量；1.21.1 无 KeyBindEvents；fabric 26.x 无 hud/hudRender | 按键注册/轮询/事件三段工作，HUD 两段出图；真实按键端到端与 HUD 实际出图票 26 记 not verified——本试做正好覆盖 |
| 6b | 同上：fabric HUD 拒绝 | [06b](06b-client-hud-unavailable-fabric.js)（client） | gate 行 `ClientEvents node=fabric buses=tick,tickPost,tickPre` | HUD 渲染面在 fabric 不可用 | KeyBindEvents 段照常工作；`No such event bus: ClientEvents.hudRender` |
| 7 | ESM/CJS/TS 导入 | [07-module-imports/](07-module-imports/README.md)（server，四组分子目录） | 票 11 MIGRATION §1.1（`ModuleExamplesSmokeTest` 正本）+ 票 12 MIGRATION（类型擦除） | 模块管线为 common 面，五节点同语义 | 四组全部加载成功；缓存身份一致；ESM 缺失导出 link 期报错带文件行列；TS 类型擦除后输出与期望一致 |
| 8 | 诊断定位 | [08](08-diagnostics-error-locating.js)（server） | 票 30 基线（2026-09-22-diagnostics）REPORT §1/§3；wiki/命令.md | 全节点（fabric 为文本降级） | 场景 A：reload 成功、触发后 `/nekojs error` 报 active error(s) + Dashboard 入口；场景 B：reload 事务失败旧 active 保留；两者都能定位到 authored 路径与源文件行号 |
| 9 | declaration 使用 | [09](09-declaration-usage.ts)（server） | wiki/Probe-类型生成.md；票 30 WorkspaceGenerator；README.txt 提示行 | 全节点（`probe reset_config` 仅 NeoForge） | `/nekojs probe` 生成 `.neko_probe` 与 jsconfig 合并条目；编辑器补全可用；错误成员行同时得到编辑器红线与 binding-preflight 定位诊断；用户自加 jsconfig 条目在重跑后保留 |
| 10 | 旧 global 迁移 | [10-server](10-legacy-global-migration-server.js)（server）+ [10-client](10-legacy-global-migration-client.js)（client） | 票 10 MIGRATION §1/§2 迁移表（1.2.0 clean cutover，五节点 build 通过） | 全节点（common 面） | client 侧三行输出：`global.handoff = undefined`、`shared.handoff = explicit`、`global.serverOnly = undefined` |
| 11 | capability 识别 | [11](11-capability-inventory.js)（server） | probe 声明目录 + gate 基线 + 运行时显式拒绝（本包 b 变体） | 全节点可运行（只读盘点，不抛错） | 逐名字输出 present/absent，与该节点 `.neko_probe/typescript/@side-only/server/` 声明目录交叉一致 |

**任务 5 注（成员级差异的记录口径）**：gate 基线的 fabric 行只覆盖经核心钩子注册的总线
（如 `CommandEvents node=fabric buses=register`、LevelEvents fabric 行无 explosionStart），
而票 24 catalog-snapshot 记录了 rightClicked / explosionStart 等成员的 fabric 孪生
（`FabricBlockEventBindings` v1/v2、`FabricLevelEventBindingsV2` 直挂共享组）。fabric 节点上
跑 05 时逐成员记录实际结果；gate 行与 catalog 不一致处本身就是「公开材料是否足够」的
试做发现，按 AC4 记录，不要绕过。

## 试做记录表（维护者填写）

每个任务一张；`结论` ∈ `通过` / `失败` / `待修复`。b 变体与主任务分开记。

### 任务 1 / 1b 启动期注册

| 节点 | 变体 | 实际输出 / 诊断 | 公开材料是否足够 | 结论 | 备注 |
|------|------|----------------|------------------|------|------|
|      |      |                |                  |      |      |

### 任务 2 / 2b Dynamic Registry

| 节点 | 变体 | 实际输出 / 诊断 | 公开材料是否足够 | 结论 | 备注 |
|------|------|----------------|------------------|------|------|
|      |      |                |                  |      |      |

### 任务 3 / 3b Villager Trades

| 节点 | 变体 | 实际输出 / 诊断 | 公开材料是否足够 | 结论 | 备注 |
|------|------|----------------|------------------|------|------|
|      |      |                |                  |      |      |

### 任务 4 / 4b Item / Block modification

| 节点 | 变体 | 实际输出 / 诊断 | 公开材料是否足够 | 结论 | 备注 |
|------|------|----------------|------------------|------|------|
|      |      |                |                  |      |      |

### 任务 5 server 事件面

| 节点 | 成员（逐成员记，fabric 必填） | 实际输出 / 诊断 | 公开材料是否足够 | 结论 | 备注 |
|------|------------------------------|----------------|------------------|------|------|
|      |                              |                |                  |      |      |

### 任务 6 / 6b client 输入与 HUD

| 节点 | 变体 | 实际输出 / 诊断（含真实按键、HUD 出图） | 公开材料是否足够 | 结论 | 备注 |
|------|------|----------------------------------------|------------------|------|------|
|      |      |                                        |                  |      |      |

### 任务 7 ESM/CJS/TS 导入

| 节点 | 组（js/cjs/esm/ts） | 实际输出 / 诊断 | 公开材料是否足够 | 结论 | 备注 |
|------|--------------------|------------------|------------------|------|------|
|      |                    |                  |                  |      |      |

### 任务 8 诊断定位

| 节点 | 场景（A 回调内 / B 顶层） | `/nekojs error` 与 view_all_errors 实际输出（含定位路径与行号） | 公开材料是否足够 | 结论 | 备注 |
|------|--------------------------|--------------------------------------------------------------|------------------|------|------|
|      |                          |                                                              |                  |      |      |

### 任务 9 declaration 使用

| 节点 | 步骤（probe 生成 / 编辑器补全 / 错误成员 / jsconfig 保留） | 实际输出 / 诊断 | 公开材料是否足够 | 结论 | 备注 |
|------|-------------------------------------------------------------|------------------|------------------|------|------|
|      |                                                             |                  |                  |      |      |

### 任务 10 旧 global 迁移

| 节点 | 实际输出（三行） | 公开材料是否足够 | 结论 | 备注 |
|------|------------------|------------------|------|------|
|      |                  |                  |      |      |

### 任务 11 capability 识别

| 节点 | 盘点输出与声明目录是否一致 | 不一致项 | 公开材料是否足够 | 结论 | 备注 |
|------|---------------------------|----------|------------------|------|------|
|      |                           |          |                  |      |      |

## 已知未决项（HEAD 复核发现，随试做验证）

1. **03b 查询段**：票 22 基线断言 fabric 上 `VillagerTrades.query()` 返回 STALE +
   `unavailable:…`，但 HEAD 源码中 `VillagerTradesPlugin`（注册 `VillagerTrades` binding）
   整文件 `//? if neoforge` 守卫，fabric 侧 `NekoJSFabricMod` 只装配 unavailable owner 的
   查询状态、未注册同名 binding。若实跑得到「未知标识符」/ReferenceError 而非 STALE，
   该差异按 AC4 记为公开材料与实现不一致的待修复项（详见 03b 头注 TODO）。
2. **02 平台激活**：三类候选类型（Item/SoundEvent/MobEffect）平台侧激活均 not verified，
   公开激活阻塞——02 的预期可见效果是 inert 候选计划，不是热更新。试做验证的是公开材料
   是否让作者动手前就知道这一点。
3. **06 真机端到端**：真实按键（consumeClick 的真实点击源）与 HUD 实际出图票 26 记
   not verified（归票 34 真机试做），本试做正好补上这段证据。
4. **05 fabric 成员面**：gate 行与 catalog-snapshot 的覆盖差异（任务 5 注），逐成员实跑裁定。
5. **24 D2（已修复，2026-09-29 更正）**：本包基于 mult@c8173622 起草时 `BlockEvents.broken`
   的取消仍是静默 no-op；D2 修复已于 2026-09-29 合入 mult（显式可取消 keyed 总线 + bridge
   回写，2026-09-28 真机会话验证方块不被破坏）——试做时应以修复后行为为准，
   05 内「不要依赖取消」的旧注释按修复后语义解读。
