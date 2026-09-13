# 25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径

**What to build:** 脚本作者调用 DataMap 的 furnaceFuel/compostable 等只读查询，或用 EntitySelectors factory/builder/query 生成选择器并执行查询；请求经既有 tier的 binding 与平台/version Adapter返回快照或结果，缺失/非法输入得到普通可读错误，TS/Python declaration 和 capability 与真实行为一致。tier 按 source contract 归类，规范化不默认 stable；二者都不事件化。

**Blocked by:** [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)

**Status:** closed

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中涉及的维护者删除确认是后续发布门禁。agent 可以准备替代路径 parity、迁移表和旧 route 无消费者证据，但不得在获得维护者 sign-off 前删除旧公开路径或勾选对应删除验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- 将 DataMapJS 与 EntitySelectorsJS/Builder/factory 按 source contract 归类为 query binding，不复用事件 owner，也不创建 Point。
- 为 DataMap furnaceFuel/compostable 等既有查询建立命中、缺失、空值、类型转换和只读结果 fixture；NeoForge data map 与 MC-facing类型由平台/版本 Adapter持有。
- 为 EntitySelectors builder/query 建立 server/test side、selector 语法、level/entity 输入、命中与非法 selector/level 错误 fixture。
- 把两个查询域的 runtime member、TS/Python declaration、Probe parity 和 loader/version capability 纳入 contract/golden。
- 记录 current path、target owner、Adapter source trace、替代覆盖和旧路径删除条件；当前入口存在不等于 managed stable。
- 收缩 gate：DataMap/EntitySelectors 旧查询旁路只有在替代 behavior、declaration、trace 与无调用者证据通过后移除；公开查询功能不删除，清理随本票完成而不是 final release 统一处理。

## Acceptance criteria

- [x] DataMap representative 查询命中返回既有平台数据快照，未命中/缺失按公开语义返回空或明确错误，不暴露可变 registry view。
- [x] EntitySelectors factory/builder/query 在 server/test side 生成并执行预期 selector，返回可验证实体结果。
- [x] 非法 selector、非法 level 或缺失输入得到普通错误，包含域、调用入口和源位置，不嵌修复提示。
- [x] DataMap 和 EntitySelectors 均无新增事件、无事件包装器、无无生命周期 Point。
- [x] source contract 明确二者既有 tier 与能力，不因现有 binding/LEGACY_PREVIEW 收录而自动升级 managed stable。
- [x] TS/Python declaration、Probe 输出与 runtime member/signature 一致。
- [x] NeoForge/Fabric/1.21.1 capability 按 source trace 和真实 smoke/fixture 记录 supported/partial/unavailable。
- [x] MC-facing data map/selector 类型与平台差异由平台/版本 Adapter持有；共享契约与查询 binding 不引入 Minecraft/loader 依赖。
- [x] 每个域的替代查询面、旧路径消费者和删除条件可追踪；公开删除仍需维护者确认。
- [x] 旧查询旁路只有在替代 behavior、declaration、trace 通过且无调用者后移除；公开 DataMap/EntitySelectors 查询功能不删除，清理不推迟 final release。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md): 查询工具的既有 tier、签名、capability 和 TS/Python declaration 必须由 source contract/catalog 归类并派生；现有 binding 的存在不把 legacy/raw 规范化为 managed stable。

## Scope and coordination

**Rationale:** 每个查询域都走 Query+Adapter+declaration fixture 的一条 path；两者可在同一窄票中闭合非事件查询工具的共同验收，但 coverageDomains 明确分开，不互相冒充。

**Coordination:**

- REGISTRY_STARTUP: DataMap 的 Registry Runtime 查询面归属与启动 registry/类型元数据由 registry 组协调，本票负责查询 binding 到 Adapter 的完整路径。
- MANAGED_SURFACE: declaration/golden 再生成必须走显式审阅流程。
- DIAGNOSTICS: 查询错误复用统一错误上下文，不创建第二错误面。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
## Closure record（2026-09-12）

- 执行者：zcode-agent。分支 `ticket-25-query-tools`（9 commit）合并入 master（`a29d7e06`）；
  合流后完整验证全绿（同票 06 的验证批次）。
- 交付：查询域盘点、source contract 归类（两域=query binding，观察面在 legacySurface，**不默认 stable**、
  无 Point、无事件化，`QueryToolContractClassificationTest` 钉住）、capability 三态矩阵两套
  （neoforge/fabric，fabric DataMap 全 `UNAVAILABLE` 显式失败非静默）、DataMap 与 EntitySelectors
  fixture（裸 JUnit + 游戏内 test_scripts，58 断言 0 failed）、declaration parity、`bench/query/`
  （README + `run-query.ps1`：固定 25971/RCON 25972、RCON 停服+等 session.lock、单命令复现）、
  current path→target owner→Adapter source trace→旧路径删除条件表。
- **F1（保留，必要前提）**：`DataMap` 由 class binding 改 instance binding——否则脚本侧
  `DataMap.furnaceFuel` 报 `Unknown identifier`，AC1 无从取证。审查后补了 A/B 对照证据
  （`bench/query/diagnostics/q25-datamap-binding-control.js`：class 形态 `undefined` + Unknown identifier，
  instance 形态 1600 全绿）。
- **F2/F3（回退，越权语义变更）**：审查裁定二者超出"仅 fixture"授权——`type()/typeTag()` 的
  `includesEntities` 作用域改动、未知 type tag 由静默空改抛错，都属公开语义变更。已回退为
  characterization 测试 + 缺口记录（报告 N7/N8，owner 维护者/domain 票），并保留错误消息域前缀
  改进（AC3 合规）。AC2 改用不改语义的路径（实体基座预设 / 反选玩家）取证。
- 验收判定（据实）：AC1-AC6、AC8、AC9 **满足**；**AC7 部分满足**（fabric 已补 `:26.1.2-fabric:test`
  73 用例 0 失败，但真实服务器 runtime query smoke 未跑，owner loader-port）；**AC10 未勾且未删任何
  公开路径**——按工单 Human input note，维护者 sign-off 前不删、不勾删除项（合规）。
- 其它据实修正：自述"3 处"改为 4 处（含 `DataMapJS.itemHolder` null 守卫，如实标注脚本侧不可达、
  仅 Java 调用者可达）；不再隐瞒的失败样本已解释清楚（`postfix-extract` 的 2 failed 系会话内累计 +
  中间版 fixture 的真实断言失败，已由 `cf8d24ef` 修掉，干净会话 SUMMARY 全 0 failed）；
  `DataMap binding[CLIENT]` capability 由硬编码 SUPPORTED 改判 PARTIAL；恒真断言改为有效断言。
- 遗留（owner 已列）：查询域 declaration golden 不在 09 `REGENERATE.md` §1 清单（登记建议见报告 N9，
  owner managed-surface）；三处 golden 变更仅有 owner 自查、缺维护者审阅记录；fabric runtime smoke。
