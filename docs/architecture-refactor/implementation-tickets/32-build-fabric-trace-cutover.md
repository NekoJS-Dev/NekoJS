# 32: Fabric 五层源唯一性与 bridge 删除条件

**What to build:** 用 raw、processed、class、去重前打包输入和最终 jar entries 的五层 trace 证明 Fabric 源唯一性和跨 loader 不挂载，并在全部删除条件满足后收掉 bridge；任一缺口则保留 bridge 并回滚。

**Blocked by:** [31: Fabric raw loader 源根显式所有权迁移](31-build-fabric-raw-root.md)

**Status:** closed

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- W8

## Acceptance criteria

- [x] raw 源、processed 源、编译 class、Jar 去重前输入和最终 ZIP entries 分层记录，生成副本不被误认为第二事实源。【L2 空壳判定 = 注释剥离后有效性检验（审查复核其字符串局限被 stonecutter 壳内转义 + L3/L4/L5 独立证据兜底）】
- [x] 七个同名 FQCN 在五层 trace 中均能解释 origin 与最终重复计数；去重策略、集合存在性、guard 数量或源文件数量不作为唯一性证明。【证明主体 = 守卫核验→空壳剥离→class 唯一+common 零携带→来源唯一→字节回链；EXCLUDE 仅作反证引用（只消化 MANIFEST.MF）】
- [x] 若某版本差异没有预处理方案，报告明确为未处理并由 compat facade 或节点 override 承担，不写成已由 Stonecutter 处理。
- [x] 两个 Fabric 节点分别通过编译、检查、制品验证和 runtime smoke，最终 jar 的重复计数与预期一致。【删除 bridge 前后各一轮，共 4 轮 smoke 三标记全绿；本地 Windows 等价，Ubuntu CI 复跑为合并后观察项】
- [x] 三个 NeoForge 节点检查通过，并证明不挂载 Fabric raw 根或其生成副本。【srcDirs 探针 + check + 孪生 FQCN 逐 entry SHA-256 与 fabric 编译物零重合】
- [x] sandbox 聚合检查通过，且结果覆盖 guard lint 与节点检查，而不是只引用任务存在。
- [x] 迁移前后 source、artifact、resource、mixin 和 metadata trace 等价；每个有意差异都有能力或迁移说明及 owner。【唯一差异 = 票 07 合并的 +6 entries（逐 entry 归因）+ sha（A 文件与 5 个 M 文件双源，审查 S1 补全归因链）；删除前制品未留存逐 entry 字节 diff 已登记（可 revert 同机重建）】
- [x] 只有五层证据、两个 Fabric 门禁、三个 NeoForge 不挂载检查、聚合检查、smoke 与 fixture 消费者全部满足，且 bridge 依赖、引用与旧排除规则均有替代证明时，才删除 bridge。【8/8 条件满足；bridge 删除单独 commit f984953b，防御 gate（NeoForge 排除/禁止资源/制品验证）非 bridge、原样保留】
- [x] 任一条件不满足时 bridge 保留，迁移回滚或缺口修复路径明确，不用删除证据来源来换取收口。【未触发；回滚 = git revert f984953b，链至票 31 三 commit】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [平台构建与 Stonecutter 策略规格](../specs/03-platform-build-strategy.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [31: Fabric raw loader 源根显式所有权迁移](31-build-fabric-raw-root.md): 五层 trace、节点门禁和 bridge 删除条件必须基于已显式挂载且可构建的新 raw 根评估。

## Scope and coordination

**Rationale:** 它单独形成可判定的 bridge 删除/保留决策，避免把大规模源迁移和五层唯一性证明压进同一上下文。

**Coordination:**

- 与 CI 和 runtime smoke owner 同步 fixture 路径与证据格式；与功能域 owner 复核有意 artifact 差异。


## Closure record（2026-09-15）

- 执行者：zcode-agent。实施区间 7fb521eb..292e9f31（分支 3 commit + 审查补强 1 commit + merge）。
- 交付物：五层源唯一性 trace（fabric-trace-t32.py：raw 61 java 逐文件 SHA + 7 孪生 GUARDED 核验 /
  processed 生成树空壳判定（63 守卫文件 100% 空壳化、src/fabric 有效代码出现 = 0）/ class 每 FQCN
  唯一 + common 零携带 / 去重前来源序（EXCLUDE 仅消化 MANIFEST.MF）/ 最终 jar L4↔L5 集合全等 +
  字节回链）、孪生 FQCN 逐层 origin 表、NeoForge 不挂载探针、sandboxCheck 聚合、双 fabric 删除
  前后各一轮 smoke、**bridge 收口删除（f984953b，单独 revert 粒度：deps 两键 + convention 历史
  注释 + McVersionCompat 过时措辞（javadoc + 异常消息字符串，行为等价））**。
- 双轴审查：零必修，判定可合并；审查员独立重跑 trace 脚本（HOLLOW 7/7 复现）与 guardLint 复核
  一致；票 07 +6 归因经独立实测闭合。S1（parity 补列 5 个 M 文件）已随 35e039cf 落地。
- 测试：双 fabric build+test 删除前后均绿；三 NeoForge check + 删除后 build 绿；sandboxCheck 86
  tasks；guardLint 248/392/0/0。一次 smoke 首轮 FAIL 为脚本误报（Mixin DEBUG 日志宽松正则），
  修正扫描逻辑后全绿，与 CI 口径差异已在脚本注释记录。
- 遗留（owner 已列）：fabric McClientCompat provider（承票 31 not sampled）→ W7 客户端域；删除前
  制品逐 entry 字节审计（可 revert 同机重建）→ 按需；Ubuntu CI 全链路复跑 → 合并后观察。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
