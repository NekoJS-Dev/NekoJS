# 31: Fabric raw loader 源根显式所有权迁移

**What to build:** 把两个 Fabric 节点共享的 raw loader 源、资源、模板和 runtime smoke fixture 迁到实施交接单已批准锚点指定的唯一 raw 根，同时建立 compat provider availability matrix，并保持迁移前后构建、制品与 smoke 可用。

**Blocked by:** [01: P0 五节点构建与契约基线](01-build-baseline.md)

**Status:** closed

**Assignee:** zcode-agent

**Takeover note（2026-09-15）:** 本认领由另一 zcode 并行会话于 2026-09-13 写入后 36 小时零产出（无分支、无 worktree、无文件改动）；经维护者（用户）批准由当前 zcode 会话接管继续。

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- W8
- 以实施交接单的已批准 raw-root 锚点为唯一目标，更新 Fabric convention、NeoForge 防御性排除、guard lint 与 CI fixture 输入；旧 raw root 仅作为可回滚 bridge，不把所有单 loader 文件强行移动。
- 建立各节点 McVersionCompat、McPlatformCompat、McClientCompat provider availability matrix，并为 Fabric smoke 增加真实兼容面调用。

## Acceptance criteria

- [x] 实施交接单锚点指定的路径是两个 Fabric 节点唯一共享 raw loader 根；旧 raw root 只允许作为本票记录在案的过渡 bridge，且 26.2 仍不改变节点身份、坐标语义或制品命名。
- [x] 文件移动只按共享 Fabric raw root 所有权执行；节点专属源、资源或有意留在原位置的文件按交接单规则逐文件说明，不用“目录看起来整洁”要求全量搬迁。
- [x] provider availability matrix 只诊断实际输入和测试结果；`not verified` 保持独立证据状态并阻塞对应域，不得改判 unavailable/partial，也不得因诊断结果降低既有已支持功能。【fabric McClientCompat 记为 not sampled（类打包、零 provider、零调用方，grep 实证），不外推】
- [x] 每个节点列出实际打包和实际调用的 McVersionCompat、McPlatformCompat 与 McClientCompat provider；Fabric smoke 至少调用 `Level.spawnLightning` 或等价兼容面，证明 provider 可用或得到明确 unavailable/rejection，不允许 ServiceLoader 潜伏初始化崩溃。【双节点 smoke 实调 spawnLightning（bolt=true 标记）+ per-node provider 落位消除既有潜伏崩溃；McPlatformCompat 对 fabric 不适用（facade 整文件 neoforge 守卫，fabric 不编译）】
- [x] 1.21.1 的独立 compat 路径与 26.x provider 路径差异、未提供 provider 的行为和测试证据显式记录；`not sampled` 不得外推为 supported。
- [x] 两个 Fabric 节点只通过 Fabric convention 显式挂载共享 raw loader 根；不声称或依赖 Stonecutter 自动预处理该根，未处理的版本差异由既有 compat facade 或节点 override 承担。
- [x] Stonecutter、五个节点和支持等级保持不变；不新增 Gradle project、API jar或替代版本树。
- [x] Fabric Java、资源、模板与 runtime smoke fixture 的所有权落位完整，旧路径消费者全部更新或显式归类为过渡引用。
- [x] runtime smoke fixture 的测试资源接线与 CI 拷贝用途分别记录；CI 直接复制不被误写成 Gradle 测试资源已消费。
- [x] guard lint 因新增 raw 根扩大的扫描范围被记录并零违规，common 的 Minecraft/loader 隔离和 Graal 许可没有被放宽。【扫描 331→392（+61 恰为迁入文件），守卫块 248、违规 0；审查员独立复跑一致】
- [x] 两个 Fabric 节点的编译、检查、制品验证和现有 smoke 通过；失败时能回滚到迁移前源根与 CI 接线。【本地 Windows 等价验证；Ubuntu CI 全链路复跑为合并后观察项】
- [x] NeoForge 防御性排除、Fabric 禁止资源检查和 Fabric 制品验证仍保留，除非另有独立证明，不因目录收口顺手删除。
- [x] 迁移前后两个 Fabric 节点的 artifact、resource、mixin、metadata 和 smoke 结果对照无未说明差异；26.2 节点身份、坐标语义和制品命名不变。【迁移 commit 本身 jar SHA-256 逐字节不变；provider commit +3 entries（services 目录项/services 文件/compat class）为已说明的有意差异；迁移前 smoke 引用票 01 既有基线（由逐字节一致 jar 支撑）】
- [x] source bridge、bridge 引用和旧排除规则在本票保持可回滚，不提前删除。【旧 raw root 仅剩 deps.fabric_source_node 两键 + convention 历史注释，删除归票 32】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [平台构建与 Stonecutter 策略规格](../specs/03-platform-build-strategy.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [01: P0 五节点构建与契约基线](01-build-baseline.md): 迁移前后 artifact、resource、mixin、metadata、source 与测试对照必须以 P0 基线为输入。

## Scope and coordination

**Rationale:** 它以两个 Fabric 节点的真实构建和 smoke 证明新的显式源根可用，而不是只完成目录搬迁。

**Coordination:**

- 与 runtime smoke 和 CI owner 对齐 fixture 消费者；与功能域 owner 解释有意 artifact 差异，但这些沟通不是源根迁移的技术阻塞。

## Closure record（2026-09-15）

- 执行者：zcode-agent（自停滞的并行 zcode 会话认领接管，维护者批准；原认领 36h 零产出）。
  实施区间 47f4a444..13da6fd5（分支 6 commit + merge）。
- 交付物：共享 raw loader 源/资源/模板/smoke fixture 迁至交接单批准锚点 src/fabric（68 文件
  100% rename；迁移 commit 本身两 fabric jar SHA-256 逐字节不变）、per-node Fabric261/262
  VersionCompat provider（消除「d.ts 已宣传 spawnLightning 但 fabric 零 provider、调用即
  ServiceLoader 潜伏崩溃」的既有缺陷）、fabric smoke 真调 Level.spawnLightning（CI 第三 grep
  断言 ok 标记）、guardLint 扫描范围 +61 零违规、provider availability matrix（McVersionCompat
  per-node、McPlatformCompat fabric 不适用、McClientCompat not sampled 不外推）、1.21.1 独立
  compat 路径显式记录。
- 双轴审查：零必修项，判定可合并；两条建议（McVersionCompat 过时错误消息、CI TERM 时机竞态）
  均已登记归属。审查员独立复核：srcDirs 探针、三测量点制品哈希链、guardLint 复跑、smoke 原始
  日志三标记（grep -a）、CI 旧路径零残留。
- 遗留（owner 已列）：deps.fabric_source_node 两键/convention bridge 注释/旧排除规则删除 →
  票 32（本票 parity 证据为其输入）；fabric McClientCompat provider 或显式 unavailable 通道 →
  W7 客户端域（fabric 客户端面移植时不补会复现同类崩溃）；McVersionCompat javadoc 过时措辞 →
  票 32；Ubuntu CI 全链路复跑 → 合并后观察（本地为 Windows 等价验证）。
- 测试：两 fabric 节点 build 三轮 SUCCESSFUL、test 16 suites/74 tests/0 failed（含新 wiring
  测试）；NeoForge 三节点 + 1.21.1 哈希一致/check up-to-date；本地 CI 等价 smoke 两节点三标记
  全绿、日志零 ERROR；终验 sandboxCheck 86 tasks BUILD SUCCESSFUL。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
