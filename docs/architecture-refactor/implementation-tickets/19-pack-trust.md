# 19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状

**What to build:** 以旧 fixture 为事实输入，闭合 GLOBAL/WORLD 脚本包在服务器 gather、配置期哈希/Bundle 传输、客户端验证或信任决策、SERVER_CACHE 激活和断线卸载的路径：未信任服务器不执行远端脚本，hashOnly 只观测不执行，显式信任后按既有原子持久化语义保存；trust-store 跨 reload 保留，损坏降级可诊断；客户端包变更通过 root 触发 CLIENT reload。Fabric WORLD 保留当前行为并显式记录差异，不新增签名政策或权限，也不强迫 parity。

**Blocked by:** [17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md)、[03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md)

**Status:** closed

**Closure record (2026-09-22):** 8/8 验收项均已逐项满足并附可复现证据（各项【evidence】标注；`baseline/2026-09-22-pack-trust/` REPORT + command-output 01–07 + 红绿记录；合并后 mult 上 `:common:check`、`:26.1.2:test`、两 fabric 节点 test 与 `verifyFabricRuntimeArtifact` 全绿）。未跑的 in-game 连接 smoke / `runGameTestServer` / 26.2.0 与 1.21.1 NeoForge 全量沿用票 17 已 closed 的同一约定（fixture 级验收 + 真机 smoke 归票 34），非本票验收缺口。主会话复核已修复 bucket 大小写 locale 一致性与注释语言问题（REPORT §8）。无维护者签收要求的删除项（本票零删除）。

**Assignee:** zed-flash-19（main-session agent；GLM-5.3 subagent worktree）

**Claim record (2026-09-22):** worktree `../NekoJS-mult-t19` on branch `ticket-19-pack-trust`（基于 `124aace6`）。预计改动范围：PackSyncServer/Client 共享管线 gather/hash-list/bundle 顺序、客户端信任决策与 hashOnly 不执行、trusted-servers trust-store 原子持久化与损坏降级、SERVER_CACHE 落盘激活与 root 触发 CLIENT reload、断线卸载、Fabric WORLD 差异证据 fixture、`baseline/2026-09-22-pack-trust/` 证据。不修改 08 插件模型域文件。

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 复用 PackSyncServer/Client 共享管线，固定 enabled+clientSync 的 GLOBAL/WORLD gather 顺序和 hash-list/bundle 次序。
- 验证客户端信任或拒绝结果、hashOnly 不执行、bundle 大小/损坏/验签失败、信任提示和断线卸载的外部输出。
- 保持 trusted-servers.json 路径、JSON key、原子写入、reload 保留和损坏降级语义；签名公钥 pinning 现状不被加强或放松。
- 让接受的 SERVER_CACHE pack 只在 bundle 完整落盘并验证后激活，再经 root reload CLIENT；断线卸载 cache 集合但不删除可再生文件。
- 记录 Fabric WORLD 当前激活/分发差异并给出能力证据；不新增本地 GLOBAL/WORLD 签名政策，不要求 Fabric parity。

## Acceptance criteria

- [x] 服务器按旧顺序只收集启用且 clientSync 允许的 GLOBAL/WORLD 包，配置期先发送 hash list，非 hashOnly 且有包时再发送 bundle。【evidence: `PackSyncServerTest.collectGathersGlobalBeforeWorldPacks`（GLOBAL 字母序→WORLD 字母序、clientSync=false 不收集）+ `bundleSendDecisionMatrix`；两桥推送次序单点化为 `PackSyncServer.shouldSendBundle`（`PackSyncConfigurationTask`/`FabricPackSync.PushTask` 委托，`PackSyncBridgeSourceTraceTest` 全节点钉住）；真机配置期推送顺序未跑＝baseline REPORT §5-G1】
- [x] 客户端对未信任服务器明确断开或拒绝执行并给出 trust hint；hashOnly 模式不落盘执行远端脚本；all 模式仅在验证与信任通过后激活。【evidence: `PackSyncClientTest.untrustedServerDisconnectsWithTrustHint`（hint 含 `/nekojs trust <addr>`）、`hashOnlyClientNeverExecutesAndEmptyListClears`、`trustedServerActivatesAndReloads` 及同文件 staging/回滚套件；:common:check 全量重跑通过】
- [x] 显式 trustServer/trustPublicKey 的路径、JSON key、bucket 计算、原子替换和跨 reload 保留行为不变；损坏文件降级为空 store 并产生可观察警告。【evidence: `PackSyncTrustStoreTest` 4 用例（持久化/bucket 大小写空格不敏感/key pinning/损坏降级，降级 WARN 见 `PackSyncTrustStore.readRoot` 既有行为）；本票对 trust-store 源码零改动，签名公钥 pinning 现状未加强或放松】
- [x] bundle 损坏、hash 不匹配、超限或非法 manifest 的远端包被拒绝，不执行脚本，不覆盖既有本地包，失败原因进入 pack trust 结果。【evidence: 红→绿 `invalidManifestRejectedWithReasonInTrustResult`（新增拒绝分支，原因入 Outcome）+ `oversizedBundleRejectionsCarryTheLimitReason`（too many packs/file too large/manifest too large）+ `rejectedBundleDoesNotOverwriteExistingLocalGlobalPack`；hash 不匹配/未签名=既有 `hashMismatchAfterPersistDisconnects`/`unsignedPackRejectedByDefault`；全部拒绝路径断言 serverCache 空、零 reload】
- [x] 接受的远端包写入现有 SERVER_CACHE bucket，激活后经 root 触发 CLIENT candidate reload；断线或服务器清空时卸载 cache 集合，可再生文件按现约保留。【evidence: `trustedServerActivatesAndReloads`/`successfulActivationAuthorizesRuntimeCacheAndDisconnectRevokesIt`（激活→reload→断线撤销授权）、`replacingBundleRejectsOldAndStaleFilesButAllowsCurrentSource`（旧缓存文件保留）、新增 `emptyHashListUnloadsActiveSetAndRetainsRegenerableCacheFiles`（服务器清空）；两 loader reload 钩子均 `root.reload(ScriptType.CLIENT)`＝`PackSyncBridgeSourceTraceTest.bothClientReloadHooksRouteThroughTheRuntimeRoot`】
- [x] Fabric WORLD 的当前激活、列表和分发现象被 fixture 固定并公开为 partial/unavailable 证据；NeoForge 与 Fabric 不伪造 parity，也不改变本地 pack 默认启用或路径。【evidence: `FabricWorldPackStatusTest`（fabric 零激活/卸载/目录扫描引用；激活调用点仅 NeoForge ServerEventListener×2；空列表文案声称 `<world>/nekojs_packs/` 的现状钉住不改；gather 只读 registry）；partial/unavailable 判定公开于 baseline REPORT §5-G2；能力表最终呈现归 MANAGED_SURFACE/language-surface（票据 Coordination），本地 pack 默认启用与路径零改动】
- [x] trust 决策、拒绝、降级和审计输出在执行或 pack sync 结果中可见，不宣称强恶意隔离。【evidence: 拒绝原因全部进入 `Outcome.disconnect`（玩家可见断连消息）；执行侧审计=既有 `NekoModuleError.OWNER_PACK_TRUST`（`successfulActivationAuthorizesRuntimeCacheAndDisconnectRevokesIt`）；降级=trust-store 损坏 WARN；文档与消息只描述验签/信任关口，无强隔离宣称（baseline REPORT §6）】
- [x] 共享核心管线 fixture 覆盖 NeoForge 与 Fabric 当前配置期桥；loader 重复信任解析/写文件路线在两侧行为等价验证后才删除。【evidence: `PackSyncBridgeSourceTraceTest` 在 26.1.2 与双 fabric 节点全量重跑通过（command-output/06/07）；重复路线=两桥各自复制的 bundle 门（hashOnly+非空判定）与远端地址→bucket 解析（InetSocketAddress），等价验证（共享核单点行为矩阵 + 两桥委托 trace）通过后删除，桥内残留由 trace 断言禁止；文件写入路线本就只在 common（`ServerPackCache`），无 loader 副本】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Delivery record（2026-09-22）

- 执行者：zed-flash-19（GLM-5.3 subagent worktree `../NekoJS-mult-t19`，分支 `ticket-19-pack-trust`，
  基于 `7768e02d`）。实现 3 commits：`6905e5c2` fix(pack) WORLD 包相对路径执行缺陷（票 03 §3-1
  实码缺陷承接、票 07 G4 遗留）、`9d02ad3e` feat(pack-sync) 配置期次序/地址输入共享单点 + 非法
  manifest 拒绝、`ad4ca3ff` test(pack-trust) Fabric WORLD 现状钉住；证据 commit 见 baseline。
- 主源码净变更（最小面）：common 共享方法 2 个（`PackSyncServer.shouldSendBundle`、
  `PackSyncClient.normalizeRemoteAddress`）+ `handleBundle` 非法 manifest 拒绝分支 1 处 +
  WORLD 归一 2 处（registry/executor）；两桥改为委托并删除本地重复判定（bundle 门、
  InetSocketAddress 地址解析）。trust-store/ServerPackCache/验签器/payload 线格式/命令语义
  零改动；无 golden 变更、无迁移、无新依赖、无新 NEKO- 码（baseline REPORT §5-G6）。
- 红→绿证据（baseline `../baseline/2026-09-22-pack-trust/command-output/01-04`）：非法 manifest
  拒绝（trust 决策）、WORLD 包激活→执行（激活次序）、配置期桥共享次序 trace（4 用例红）。
- 验证（真跑，摘录见 command-output/05-07）：`:common:check` BUILD SUCCESSFUL（1771 tests 0 失败，
  含隔离检查）；`:26.1.2-fabric:test`/`:26.2.0-fabric:test` 全量 226/226 0 失败；
  `:26.1.2:test` 全量 351/351 0 失败。本机 Windows 结果，不代表其他平台/CI/release。
- 遗留与 owner（REPORT §5）：G1 真机客户端连接 smoke→主会话 minecraft-mod-mcp；G2 Fabric WORLD
  能力表呈现→MANAGED_SURFACE/language-surface（本票已交 partial/unavailable 行为证据，含
  `nekojs_packs` 文案不一致的现状记录）；G4 runGameTestServer 与 G5 26.2.0/1.21.1 NeoForge
  全量→主会话/合并门；G6 NEKO- 码→stonecutter 分支合并后统一补。

## Dependency rationale

- [17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md): 配置期 payload 传输、wire 兼容、owner 队列和断线处理是远端包验证与激活的真实输入。
- [03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md): trust-store、pack 状态、缓存与用户数据的路径/格式旧 fixture 及保护回滚验收是持久化决策的必备输入。

## Scope and coordination

**Rationale:** pack trust 是独立于 PData/ClientData 的远端脚本执行安全路径；用一次客户端连接夹具可从服务器包集合观察到信任、激活、reload 与断线卸载全链路。

**Coordination:**

- 与 DATA_PROTECTION 共享 trust-store 与 cache 分类；冲突协调而非硬串。
- 能力表最终呈现由 language-surface/MANAGED_SURFACE 组承接，本票提供行为证据。
- RUNTIME_COMMANDS 只复用 trust 命令结果，不改 trust 语义。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
