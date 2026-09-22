# 票 19 证据报告：脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状

- 执行者：zed-flash-19（GLM-5.3 subagent worktree）。实施区间 7768e02d..（分支
  `ticket-19-pack-trust`，3 个实现 commit + 本证据 commit）。
- 基线：mult@7768e02d。共享核管线（PackSyncServer/Client/TrustStore/ServerPackCache/
  PackSignatureVerifier）与两 loader 配置期桥在本票前已存在；本票按票据口径闭合顺序单点、
  拒绝语义、WORLD 包执行缺陷与 Fabric 现状证据，不重定义已闭合票 17/03 的面。

## 1. 范围

- 服务器 gather 顺序、配置期 hash-list/bundle 次序的共享单点化（AC1、AC8）。
- 客户端 trust 决策链的缺口闭合：非法 manifest 拒绝原因进 trust 结果（AC4）。
- WORLD 包相对路径执行缺陷修复（票 03 §3-1 实码缺陷、票 07 G4 遗留，归票 19）。
- Fabric WORLD 当前激活/列表/分发现状的 fixture 固定与能力证据（AC6）。
- 明确不动：签名公钥 pinning 现状、trusted-servers.json 路径/JSON key/bucket/原子替换/
  reload 保留/损坏降级语义（票 03 已验收，本票仅重跑回归）；本地 pack 默认启用与路径；
  Fabric 不伪造 parity；不新增本地 GLOBAL/WORLD 签名政策；不新增 NEKO- 日志码
  （见 §5-G6 说明）。

## 2. 实现摘要（what changed）

### 2.1 主源码（commit 6905e5c2、9d02ad3e）

| 文件 | 变更 |
|---|---|
| `common/.../core/pack/sync/PackSyncServer.java` | 新增 `shouldSendBundle(int)`：配置期 bundle 门（非 hashOnly 且有包才发）的共享单点。 |
| `common/.../core/pack/sync/PackSyncClient.java` | 新增 `normalizeRemoteAddress(SocketAddress)`（远端地址→bucket 信任输入的共享归一）；`handleBundle` 逐包校验新增「manifest 必须是 JSON 对象」拒绝分支，原因进入 `Outcome.disconnect`。 |
| `common/.../core/pack/ScriptPackRegistry.java` | `activateWorldPacks` 把 world 目录归一为绝对路径再扫描（G4 根因修复：平台可能传入 `.\world\.` 相对形式，nekojs root 恒为绝对，混用 relativize 抛 IAE 使 WORLD 包脚本全部执行失败）。 |
| `common/.../script/ScriptExecutor.java` | `executeEntry` 对 `script.path` 先 `toAbsolutePath().normalize()` 再 relativize（防御性第二面；与 ScriptError:287 既有模式一致）。 |
| `src/main/.../network/PackSyncConfigurationTask.java` | bundle 发送改经 `PackSyncServer.shouldSendBundle(packs.size())`；删除本地 hashOnly+非空判定。 |
| `src/main/.../network/PackSyncMessageHandler.java` | 地址解析改经 `PackSyncClient.normalizeRemoteAddress`；删除本地 `resolveServerAddress`（含 InetSocketAddress 解析的重复信任输入路线）。 |
| `src/fabric/.../FabricPackSync.java` | PushTask 与接收侧同上两项委托；删除本地重复路线。 |

AC8 的删除条件执行方式：loader 侧两条重复判定/解析路线（bundle 门、地址归一）在共享核
单点落地 + 两桥委托的等价 fixture（§3 桥 trace + common 行为矩阵）通过后删除；删除后桥内
不允许再出现 `PackSyncServer.hashOnly()`/`InetSocketAddress`（trace 断言钉住）。

### 2.2 测试/fixture（同上两 commit + ad4ca3ff）

- `common/.../script/WorldPackRelativePathExecutionTest.java`：G4 红→绿主证。相对 world 目录
  激活 → 断言包 root 绝对化 + 真实 ScriptManager reload 执行 WORLD 包脚本（TestRecorder
  收到脚本上报）。测试几何说明：共享测试 gameDir 在系统临时盘与 Gradle CWD 跨盘时无法从
  CWD 构造指向它的相对路径，故临时把 NekoJSPaths 单例换到 CWD 同盘 `build/` 下自建
  gameDir（测试专用反射 seam，先例：根树测试反射 Platform.INSTANCE），用后恢复。
- `common/.../pack/sync/PackSyncClientTest.java` 新增 5 用例：非法 manifest 拒绝（红→绿）、
  超限三面（too many packs / file too large / manifest too large）、被拒 bundle 不覆盖本地
  GLOBAL 包、空哈希清单卸载 active 集合但保留可再生缓存文件、共享地址归一
  （含与 bucketFor 的一致性）。
- `common/.../pack/sync/PackSyncServerTest.java` 新增 2 用例：gather 顺序 GLOBAL（字母序）→
  WORLD（字母序）且 clientSync=false 不收集；`shouldSendBundle` 决策矩阵。
- `src/test/.../network/PackSyncBridgeSourceTraceTest.java`（全部节点同跑，纯文件读）：
  两桥推送次序同点（gather → 哈希清单先行 → bundle 经共享门）、接收路由同点（handleHashList/
  handleBundle + Outcome 断连 + 共享地址归一、桥内无重复解析残留）、同一 TASK_ID、两 reload
  钩子均 `root.reload(ScriptType.CLIENT)`、共享主线程 latch 协议、shouldSendBundle 调用点
  恰两桥各一。
- `src/test/.../pack/FabricWorldPackStatusTest.java`（全部节点同跑）：fabric raw root 零
  `activateWorldPacks`/`deactivateWorldPacks`/`WORLD_PACKS_DIR` 引用；WORLD 激活调用点仅存在于
  两个 NeoForge ServerEventListener；fabric `/nekojs packs` 空结果文案声称查找
  `<world>/nekojs_packs/`（现状钉住，不改）；共享 gather 只读 registry。

### 2.3 未改动的面（明确）

- `PackSyncTrustStore`、`ServerPackCache`、`PackSignatureVerifier`、payload 线格式、
  trusted-servers.json 语义：零改动（既有 25 用例在 :common:check 全量重跑通过）。
- Fabric WORLD 行为本身：零改动（只钉现状）；`FabricNekoJSCommands` 文案保留原样。
- `/nekojs trust` 命令语义（RUNTIME_COMMANDS 只消费，本票不动）。

## 3. 红→绿证据（red→green）

| # | 断言域 | 红（修复前） | 绿（修复后） |
|---|---|---|---|
| 1 | trust 决策：非法 manifest 被拒且原因进结果（AC4） | `invalidManifestRejectedWithReasonInTrustResult` 失败于 `contains("manifest")`：垃圾 manifest 经 allowUnsigned 放行后只以未信任提示断连（command-output/02） | 同用例通过：断连消息含 `manifest is not a JSON object`，无落盘、无 reload（04） |
| 2 | 激活→执行顺序：WORLD 包激活后脚本必须真的执行（G4，票 03 §3-1 承接） | `WorldPackRelativePathExecutionTest` 失败于第 86 行 isAbsolute：相对 world 目录激活后包 root 仍相对（01） | 同用例通过：包 root 绝对化，真实 reload 执行脚本，TestRecorder 收到 `g4-world-pack-ok`（04） |
| 3 | 配置期次序单点（AC1/AC8） | `PackSyncBridgeSourceTraceTest` 4 用例失败：共享门/共享地址归一不存在，两桥各自复制判定（03） | 11 用例全过：两桥均委托共享单点，重复路线已删（04） |

注：红 1 首轮曾因测试地址 `srv-garbage-manifest.test` 含子串 "manifest" 被未信任提示误判
通过；改地址为 `srv-badpack.test` 后得到真实红（01/02 尾注）。

## 4. 真实验证命令与结果

| 命令 | 结果 | 转录 |
|---|---|---|
| `./gradlew.bat :common:test --tests {WorldPackRelativePathExecutionTest, PackSyncClientTest, PackSyncServerTest}`（修复前/后） | 红 1 失败 → 全绿 | 01/02/04 |
| `./gradlew.bat :26.1.2:test --tests {PackSyncBridgeSourceTraceTest, FabricWorldPackStatusTest}`（修复前/后） | 红 4 失败 → 11/11 | 03/04 |
| `./gradlew.bat :common:check --console=plain` | **通过**（tests=1771 failures=0 errors=0 skipped=4，含隔离检查） | 05 |
| `./gradlew.bat :26.1.2-fabric:test --console=plain` | **通过**（tests=226 failures=0 errors=0 skipped=25） | 06 |
| `./gradlew.bat :26.2.0-fabric:test --console=plain` | **通过**（tests=226 failures=0 errors=0 skipped=25） | 06 |
| `./gradlew.bat :26.1.2:test --console=plain` | **通过**（tests=351 failures=0 errors=0 skipped=54） | 07 |

环境：Windows 10（win32 10.0.26200）、Gradle 9.6.0 wrapper、JDK 见工程工具链。以上为本机
结果，不代表其他平台/CI/release 口径。skip 均为节点既有条件跳过用例（跨节点/平台守卫），
非本票新增。普通测试零 golden 写入。

## 5. 未验证项与 owner（gaps）

- **G1 真机客户端连接 smoke 未跑**（NeoForge/Fabric 真实客户端连服走配置期桥、断连提示
  UI、`/nekojs trust` 后重连激活、断线卸载的端到端现象）。JVM fixture 覆盖共享核与桥的
  静态接线，不含真实网络栈。owner：主会话 minecraft-mod-mcp（同票 17 R2 口径）。
- **G2 Fabric WORLD 现状为 unavailable/partial，不伪造 parity**（AC6 的能力表呈现归
  MANAGED_SURFACE/language-surface 组，本票只交行为证据）：
  - 激活：unavailable（fabric 从不激活/卸载 WORLD 包）；
  - 列表：partial（列表本身工作，但空结果文案声称查找 `<world>/nekojs_packs/` 而该目录
    从不被扫描——票 03 已记录的文案与行为不一致，按票据口径保留现状不改，见 FabricWorldPackStatusTest）；
  - 分发：partial（gather 只读 registry，fabric 上 WORLD 批恒空 → 只能分发 GLOBAL 包）。
  未来若立 Fabric WORLD parity 票，需同时处理文案一致性。
- **G3 服务器侧 WORLD 包启动期 reload 的 Windows 相对路径现象**：G4 修复覆盖「注册表归一 +
  执行器兜底」两面；票 03 §3-1 的原始现象（DefaultErrorTracker 掩错）已由票 07 修复。本票
  未在真实 Windows 专用服上复跑整链（bench/datafix 场景属票 03 基线）；如需端到端复验，
  owner：主会话 runGameTestServer/manual server smoke（未跑，见 G1）。
- **G4 runGameTestServer 未跑**（would 覆盖 NeoForge 服务器启动期 WORLD 包激活路径）；
  owner：主会话。
- **G5 26.2.0 与 1.21.1 NeoForge 节点全量 test 未跑**（本票改动经共享树编译进各节点；
  26.2.0-fabric 全量已跑，26.1.2 NeoForge 全量已跑）。owner：合并门/CI。
- **G6 未新增 NEKO- 日志码**：本票未新增任何 problem-reporting 日志消息（新增面为共享方法
  与既有 Outcome 断连消息文案的自然延伸；`manifest is not a JSON object` 进入玩家可见断连
  消息而非日志）。NEKO- 码体系（3xxx=script sync limits）当前只存在于未合并的 stonecutter
  分支（2fec8ada），本分支无 Error-Reference.md 可同步——为避免跨分支编号冲突，本轮不加码；
  若 stonecutter 先合并，后续票给 pack sync 消息补码时以此为基准。
- **G7 hashOnly 客户端模式的「观测」输出面**：现状为清空+忽略 bundle（既有用例
  `hashOnlyClientNeverExecutesAndEmptyListClears` 钉住），无额外「观测报告」面；票据未要求
  新观测面，记录为设计现状。

## 6. 双轴审查自查

- 标准轴：改动最小（共享方法 2 个 + 拒绝分支 1 个 + 归一 2 处 + 桥委托）；注释为中文
  （与所在文件既有注释语言一致，工程内 authored comments 现状如此）；无新依赖；无 golden
  变更；无迁移。
- 规格轴：AC 判定见票内更新；不宣称强恶意隔离（断连消息与文档只描述验签/信任关口）；
  不伪造 Fabric parity（G2）。
- 测试断言全部经公开文件内容、registry/Outcome 公开结果与脚本执行观察，无私有字段契约
  （WorldPackRelativePathExecutionTest 对 NekoJSPaths.INSTANCE 的反射仅是测试几何 seam，
  非被测契约）。

## 7. 归档与可追溯

- 实现 commits：`6905e5c2`（fix(pack) G4）、`9d02ad3e`（feat(pack-sync) 共享单点+manifest
  拒绝）、`ad4ca3ff`（test(pack-trust) Fabric 现状钉住）、证据 commit（本目录）。
- 输入基线：`../2026-09-15-network-sync/`（配置期桥与 wire 前置）、
  `../2026-09-12-data-protection/`（trust-store/缓存旧 fixture 与 §3-1 缺陷记录）。

## 8. 主会话复核修正（2026-09-22，合并后）

- bucket 一致性缺陷（复核发现）：`PackSyncClient.normalizeRemoteAddress` 初版用
  `Locale.ROOT` 小写化 host，而 `PackSyncTrustStore.bucketFor`（及旧 resolver）用默认
  locale——在 locale 敏感 JVM（如 Turkish）上会算出与已持久化 trust 条目不同的 bucket，
  造成静默失信，违反 AC3「bucket 计算行为不变」。已改回默认 locale 并加注释说明该
  约束（不得"修复"为 ROOT）。
- 本票新增的中文注释（4 个生产/桥文件 + 4 个测试文件中本次 diff 新增部分）改英文
  （AGENTS.md 语言规则）；既有中文注释未动。
