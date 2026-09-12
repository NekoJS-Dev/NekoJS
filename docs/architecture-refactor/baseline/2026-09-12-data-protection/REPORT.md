# 2026-09-12 持久化与用户编辑数据保护基线报告（工单 03）

工单：[03: 持久化与用户编辑数据保护基线](../../implementation-tickets/03-data-protection.md)。
盘点交付物：[data-inventory.md](data-inventory.md)；运行 harness：[bench/datafix/](../../../../bench/datafix/README.md)；
原始证据：[evidence/](evidence/README.md)。本票为"默认不改"基线：**零运行时源码修改**，全部场景在隔离
worktree `D:/mcmodDemo/NekoJS-datafix` 的 `versions/26.1.2/run` 中以合成 fixture 运行，主仓库用户数据零接触。

## 1. revision 与执行环境

| 项 | 值 |
|---|---|
| 盘点（代码实读）revision | `d0aa6e0d`（master，工单 03 开工时前端） |
| harness 提交 | `39fd5aa9`（fixture+运行器）→ `0b422168`（pdata 调用语法修复）→ `07efb6e6`（pdata 写入移出 join 窗口，**场景 1/2/3 的 fixture 依赖此版**）→ `c0296bf8`（deploy 补根 jsconfig） |
| 运行 worktree | `../NekoJS-datafix`（detached，含 `07efb6e6`/`c0296bf8` 两个运行时依赖修复；不含主仓库 WIP。worktree 已删除，会话 stdout 全文未随证据归档——见 §7 的如实记录与教训） |
| 节点 | `26.1.2`（primary，NeoForge dev runServer）；RCON 25872 / server 25871 / `pause-when-empty-seconds=-1` |
| 命令通道 | 全部经 RCON（`bench/datafix/rcon.py`）；stdin 停服不通（02 号票已证），停服后等待 `world/session.lock` 释放 |
| 判定口径 | 公开观察面：文件 SHA256+size+mtime 快照、日志 marker、RCON 回执；不触私有字段/文件句柄 |

## 2. 场景矩阵结果

受保护数据集快照范围：`nekojs/config/{engine.toml,probe.toml,trusted-servers.json}`、`nekojs/packs/**`、
脚本树（含 `jsconfig.json`）、`nekojs/README.txt`、根 `jsconfig.json`、`world/nekojs_packs/**`。
快照文件见 [evidence/snapshots/](evidence/snapshots/)。

| # | 场景 | 前置 → 操作 → 观察点 | 结果 | 证据 |
|---|---|---|---|---|
| 1 | 普通成功 reload | s1 起服（GLOBAL pack + config + trust fixture 生效）→ RCON `nekojs reload` → 命令回执 no errors；pack 重新发现并加载；**19/19 文件 hash+mtime 不变**；pdata 经 NBT 读回不变 | **pass** | `log-excerpts-session1.txt`（19:48:43）、`snapshots/s1-before-reload.txt` vs `s1-after-reload.txt`（changed=0 added=0 removed=0）、`data-get-pdata-console-captures.txt` |
| 2 | 失败 reload | 塞入 `zz-broken.js`（语法错误）→ reload → 回执 `1 error(s) remain`，日志给出 source location（`zz-broken.js:2:12`）；**19/19 受保护文件不变**（仅意图内的 zz-broken.js ADDED）；失败后 summon 触发**旧监听器**（DATAFIX-PDATA-WRITE @19:49:16，旧环境存活）；pdata NBT 不变；restore + reload 恢复干净 | **pass** | `log-excerpts-session1.txt`（19:49:0x/19:49:2x）、`snapshots/s2-*.txt`（DIFF-SUMMARY changed=0）、`rcon-20260912-194903.log` |
| 3 | server stop → 重启 | snapshot → RCON stop → 客户端退出 + `session.lock` 释放 → **20/20 文件不变**；部署 WORLD pack → 重启 → config/trust/GLOBAL pack 全部原样可读（0 条 corrupt/legacy 告警）；**pdata 跨停服重启持久**（NBT 读回同值，键由 MC 存档保存） | **pass**（附缺陷发现，见 §3-1） | `snapshots/s3-*.txt`、`log-excerpts-session2-worldpack-defect.txt`、`data-get-pdata-console-captures.txt` |
| 4 | 切换世界 / root close | root close（JVM 退出路径）= 场景 3 的 stop 段：shutdown hook 冲刷、lock 释放、落盘完整性 20/20 不变。**切换世界**（单人退出重进/多世界切换）无法在 runServer 下构造 | **pass（root close 合并观察）**；切换世界 **not-verified** | `snapshots/s3-before-stop.txt` vs `s3-after-stop.txt`；not-verified 见 §5 |
| 5 | 用户编辑不被覆盖 | 预置用户编辑：engine.toml（`scriptEvaluationTimeoutSeconds=77`+自定义注释）、probe.toml（`scan.maxDepth=7`）、`server_scripts/jsconfig.json`（`datafixUserEdit` 等自定义键）、根 `jsconfig.json` → reload + `nekojs probe` → **engine/probe.toml 逐字节不变**；脚本目录 jsconfig 被 probe **合并重写**（hash 变化）但**用户键全部保留**（读-改-写合并语义）；根 jsconfig 未被触碰；第二次 probe 收敛（changed=0） | **pass** | `snapshots/s5-*.txt`、运行记录（REPORT §2 场景 5 详单） |
| 6 | 可再生 cache 重建 | `probe-clear`（删 `.neko_probe`）→ `nekojs probe` → **391 文件全部重生成**（`391 written`）；module cache 盘点确认为**纯进程内存**（`NekoModulePipelineCache`，无盘上形态，源=脚本文件），无"误删唯一数据"风险面；`nekojs/node_modules/` 为用户安装物，**不可再生**，不属 cache | **pass** | probe 日志（`391 written, 0 unchanged`）、[data-inventory.md §8](data-inventory.md) |

pdata 写入路径勘误（harness 演进，非产品缺陷判定变化）：joinLevel 回调窗口内写 pdata 会**静默丢弃**（§3-3），
fixture v3 起改为"join 捕获实体 + tick 延迟写"，写入与读回一致（`ticket03-persist-me`/303）。

## 3. 缺口清单（当前实现 vs 工单口径）

### 3-1【缺陷，已实码复现】WORLD pack 激活触发的 SERVER reload 在 Windows/相对 world 路径下灾难性失败

- **现状**：NeoForge 在 `ServerAboutToStartEvent` 激活 `<world>/nekojs_packs/` 并补一次完整 SERVER reload
  （`ServerEventListener.java:94-108`）。本票 fixture 下该 reload **fatal**：
  `java.lang.IllegalArgumentException: 'other' is different type of Path` 于
  `DefaultErrorTracker.record(DefaultErrorTracker.java:70)`（`paths.root().relativize(script.path)`——
  `nekojs` 根为绝对路径，而 WORLD 包脚本路径来自 `server.getWorldPath(LevelResource.ROOT)`，本环境为
  **相对路径** `.\world\.`；WindowsPath 混合绝对/相对 relativize 抛 IAE）。级联后果：
  1. 事务 reload 失败 → "previous scripts retained but event listeners/bindings were cleared"；
  2. **WORLD 包脚本从未加载**（无 DATAFIX-WORLDPACK marker）；
  3. **原始脚本错误被吞**（record() 在 `ScriptError.create` 之前抛出，外层日志只见 IAE）；
  4. 监听器被清后，实体从存档加载不再触发脚本（pdata 脚本侧 readback marker 因此缺失）；
  5. 文档化恢复契约"再跑一次 /neko reload"**不成立**——world pack 在位时手动 reload 同样 fatal（19:53:26）；
  6. 只有把 world pack 移走后 reload 才恢复；**同一包目录拷到 `nekojs/packs/`（绝对路径）加载无错**
     （19:54:09），证明触发点是 world 相对路径处理而非包内容。
- **工单口径**："普通成功 reload、失败 reload、server stop、切换世界和 root close 后，config、world、pdata、
  pack、trust-store、workspace/declaration 和 logs 原样可读"（AC2）；脚本错误不得破坏当前可用环境（spec 用户故事 5-7）。
- **差距**：WORLD pack 存在时，一次普通脚本错误即可导致整个 SERVER 环境监听器清空且无法按文档恢复；
  错误报告链路自身抛异常吞掉根因。数据本身未损坏（快照 20/20 不变），但"读写语义不被运行时重构改变"的
  前提已破——这是运行中行为缺陷，非数据损毁。
- **建议 owner**：票 19（PACK_TRUST/pack 路径域）或票 07（runtime-threads/失败保留）；修复方向（供参考，本票未改码）：
  world 包路径入 ScriptContainer 前统一 `toAbsolutePath().normalize()`，且 `DefaultErrorTracker.record`
  对 relativize 失败兜底（用绝对路径字符串而非抛出），错误记录失败不应放大为 reload 失败。
- **证据**：`evidence/log-excerpts-worldpack-iae-stack.txt`、`log-excerpts-session2-worldpack-defect.txt`。
- **归因口径**：两段日志里 `Caused by` 均止于 `WindowsPath` 帧，**未见 `DefaultErrorTracker` 帧**；`DefaultErrorTracker.record:70` 是代码实读定位（该行恰为对相对 world 路径的 `relativize`），与异常点吻合但**栈内不可直接可见**，属强推断而非栈内实证——修复时以复现 + 断点/补日志确认为准。

### 3-2【机制缺口】除 trust-store 外，盘上持久化数据均无原子写/备份/schema-version

- **现状**：trust-store 用 temp+`ATOMIC_MOVE`（`PackSyncTrustStore.writeRoot:140-153`，唯一原子写）；
  engine/probe.toml 走 NightConfig autosave 直写、`.neko_pack.state.json`/`ScriptPackState.save` 直写、
  probe 输出 commitInPlace（逐文件写+陈旧删除）；全部数据无 schema/version 标记。
- **工单口径**：AC5——"若必须迁移，迁移前生成备份或使用原子替换，写入版本标记……本票必备 gate"。
- **差距**：本票结论是"**当前无迁移发生**，AC5 的前置（若必须迁移）未触发"；但一旦后续票（17/18/19 改 wire、
  改格式）需要迁移，现有写盘设施（除 trust-store 外）没有可复用的原子替换/版本标记先例。工单 AC8
  "数据专用迁移与回滚 fixture 通过前，不删除旧格式读取路径"——当前也没有"旧 fixture 回读 gate"挂在任何
  测试里（本票的 bench/datafix 是手工回读 harness，不是 CI gate）。
- **建议 owner**：票 18（DATA_SYNC）与票 19（PACK_TRUST）实施迁移时，先在本 harness 基础上落"备份/原子替换/
  version 标记/幂等/回滚"fixture gate；无迁移则保持现状（符合"默认不改"）。

### 3-3【行为发现】joinLevel 回调窗口内写 pdata 静默丢弃

- **现状**：`EntityPDataStore` NeoForge 实现按 entity id 反查实体（`NekoJSMod.pdataContainer`，遍历
  `level.getEntity(id)`）；`EntityJoinLevelEvent` 窗口内实体尚未入索引 → `Access.set` 空容器 **no-op**、
  `Access.get` 返回空 tag——put 后立刻 get 也是空（实测 19:46:07 `DATAFIX-PDATA-WRITE key= seq=0`）。
  写失败无日志（静默）。
- **工单口径**：AC1"未知项不能默认当作 cache 删除"的精神 + spec 故事 24（pdata key 语义不变）；静默写丢失
  与"读写语义稳定"相悖，至少应可观察。
- **差距**：脚本作者在 join 类事件里写 pdata 会静默丢数据；错误被吞无诊断。属运行时行为缺口，不是数据格式问题。
- **建议 owner**：票 18（DATA_SYNC，pdata 语义域）；方向：join 窗口写报错/延迟提交，或 `pdataContainer`
  查不到实体时 WARN。
- **证据**：`evidence/log-excerpts-session1.txt`（19:46:07 空值 write）+ 本报告 §2 勘误注。

### 3-4【文档/行为落差】Fabric WORLD pack 从不激活，但命令文案声称查找 `<world>/nekojs_packs/`

- **现状**：两个 fabric 节点无任何 `activateWorldPacks` 调用；`nekojs_packs` 仅出现在
  `FabricNekoJSCommands.java:309` 的 `/nekojs packs` 提示文案里。世界包目录在 Fabric 上是纯静态数据。
- **工单口径**：AC3——"Fabric WORLD 当前行为被记录为现状差异而非被迫 parity"。
- **差距**：行为本身属"现状"（符合"不强求 parity"），但文案与行为不一致会误导用户；差异未在面向用户的
  能力表中显式标注。
- **建议 owner**：票 19（Fabric WORLD 现状与其能力表条目）；可在文案或能力表中显式 `unavailable/partial`。
- 详证：[data-inventory.md §9](data-inventory.md)。

### 3-5【记录】启动期自动物化示例脚本（写脚本根）

- **现状**：首启 `NekoJSMod.initializeWorkspace()` → `ScriptBootstrap.generateDefaultScripts()` 会在脚本根
  写入 `*_scripts/src/main.js` 示例（本票部署后出现，与 fixture 并存、未覆盖 fixture 文件）。
- **工单口径**：AC1/AC2（workspace/declaration 原样可读）。
- **差距**：无——物化仅在缺失时发生（未覆盖既有文件，快照对比中 fixture 文件 hash 全部稳定）。记录进盘点，
  供后续"workspace 组"（票 09/30）参考。无 owner 动作。

## 4. Fabric WORLD 现状差异（工单 AC3 要求的记录）

见 [data-inventory.md §9](data-inventory.md) 与缺口 §3-4。一句话结论：**Fabric 节点当前不激活 WORLD pack**
（生命周期仅维护 currentServer 引用；`nekojs_packs` 只在列表命令文案中出现），本票未在 Fabric 节点做运行时
复测（not-verified，§5），该现状以代码实读为准记录，且不作为 parity 目标。

## 5. not-verified 清单（每项独立状态 + owner）

| # | 项 | 状态 | 说明 | owner |
|---|---|---|---|---|
| 1 | 切换世界（单人退出重进 / 多世界切换 / root close 的客户端分支） | **not-verified**（专用服务器无法构造） | root close 的服务端退出路径已由场景 3 覆盖；客户端/单人侧 world pack 卸载路径（`onServerStopped` CLIENT 清理分支）未运行 | 维护者（建议经 minecraft-mcp 客户端会话或单人集成验证；工单已注明此类场景可用 mcp 补证） |
| 2 | Fabric 节点运行时回读（config/pack/pdata 在 26.1.2-fabric/26.2.0-fabric 上的实际读写） | **not-verified**（本票仅采 primary 节点；Fabric WORLD 现状为代码实读结论） | fabric run 目录为 `run-server`，harness 未适配（02 号票同样未采） | 票 19/34（能力矩阵收口时补） |
| 3 | PData/ClientData wire 的多端实际收发（packSync/pdata_sync 在真实客户端连接下） | **not-verified**（无客户端；wire id 与 codec 以代码实读 + 既有单测为据：`PDataSyncAcceptTest` 等） | 本票固定的是 wire 格式事实（§盘点），行为级多端验证需客户端 | 票 17/18 |
| 4 | `data get entity <sel> NeoForgeData.NekoJSPersistentData` 深路径查询 | 观察通道限制（vanilla 命令在 26.1.2 对无容器实体报 "Expected double"）；用 `NeoForgeData` 整读 + tag 选择器替代 | 不影响结论（双通道之一即可） | — |
| 5 | 1.21.1 / 26.2.0 节点回读 | **not-verified**（同 #2 口径，未采样非 primary 节点） | 数据面代码三节点同源（`src/main` + versions override），路径/格式一致性由盘点代码引用支撑 | 票 34（P4 整体验证） |

## 6. 逐条对照工单验收（建议勾选状态；工单文件未改动）

| # | 验收项 | 建议 | 依据 |
|---|---|---|---|
| 1 | 每类数据有 owner/路径/格式/key/wire/可再生性/备份策略/旧 fixture 结论；未知项不默认当 cache | **可满足** | [data-inventory.md](data-inventory.md) §0 总览表 + §1-§8 逐类（含 file:line）；fixture 均按实读格式构造 |
| 2 | 普通 reload/失败 reload/stop/切世界/root close 后各类数据原样可读 | **部分满足** | 场景 1/2/3/root close pass（§2）；切世界 not-verified（§5-1）；另：WORLD pack 激活 reload 缺陷（§3-1）属"读写语义"缺口，需在 AC2 判定时如实权衡——数据原样可读成立，但 WORLD pack 场景的脚本加载语义被破坏 |
| 3 | GLOBAL/WORLD/SERVER_CACHE 路径、启用状态文件、manifest key、默认启用规则不变；Fabric WORLD 记为现状差异 | **可满足** | 盘点 §3/§9 + 场景 1（reload 后 pack 重发现）+ 场景 3（WORLD pack 发现）+ 状态文件优先级实测（manifest enabled=false + state=true 生效） |
| 4 | probe 输出和 module cache 只有源可重建且有证据才重建 | **可满足** | 场景 6：probe 清除后重建 391 文件（源=运行中 catalog）；module cache 无盘上形态；node_modules 归为不可再生 |
| 5 | 迁移备份/原子替换/版本标记/幂等/回滚为必备 gate | **未触发（无迁移）** | 本票未发生格式迁移，AC5 的"若必须迁移"前置不成立；现状缺口与后续 gate 要求记录为 §3-2 |
| 6 | 离线 validator/report 不实现、不依赖 | **满足** | 本票零工具实现；未触碰任何 runtime 错误路径 |
| 7 | （票 38 边界，本票无动作） | **满足**（无动作） | 未引入任何报告工具 |
| 8 | 迁移 fixture 通过前不删旧格式读取路径；不引入通用 migration framework | **满足** | 零源码修改；旧读路径全部保留 |
| 9 | 断言只用公开观察面 | **满足** | 全部断言=文件内容 hash、日志 marker、RCON 回执（含 `data get`）；无私有字段/句柄 |

> 注：以上仅为执行 agent 的建议判定，工单勾选与关闭仍按 README 规则由认领者/维护者确认。

## 7. 复现命令

```bash
# 0) 隔离 worktree（含 bench/datafix 的 revision；三个 harness commit 见 git log）
cd D:/mcmodDemo/NekoJS/NekoJS-mult
git worktree add --detach ../NekoJS-datafix c0296bf8
cd ../NekoJS-datafix/NekoJS-mult

# 1) 部署 fixture + 首启
powershell -NoProfile -ExecutionPolicy Bypass -File bench/datafix/run-datafix.ps1 deploy
powershell -NoProfile -ExecutionPolicy Bypass -File bench/datafix/run-datafix.ps1 start -Session s1
powershell -NoProfile -ExecutionPolicy Bypass -File bench/datafix/run-datafix.ps1 wait-done -Session s1 -TimeoutSec 900

# 2) 场景序列（参数与判定详见 bench/datafix/README.md 与本报告 §2）
# 场景1: snapshot s1-before-reload → rcon 'nekojs reload' → snapshot s1-after-reload → compare-snapshot
# 场景2: snapshot s2-before → break → rcon 'nekojs reload' → snapshot/compare → summon-target → restore → rcon reload
# 场景5/6: snapshot s5-before-probe → rcon 'nekojs probe' → snapshot/compare；probe-clear → rcon 'nekojs probe'
# 场景3: snapshot s3-before-stop → stop → snapshot/compare → deploy-world → start -Session s2 → wait-done → 观察日志
#   （预期复现 §3-1 缺陷：WORLD pack 在位时 reload fatal，IAE 栈同 evidence/log-excerpts-worldpack-iae-stack.txt）
# 收尾: snapshot s5-final-after-stop → compare → stop

# 3) pdata 观察通道
# 写入: summon-target（带 nekojs_pdata_target 标签的盔甲架，fixture 脚本延迟 tick 写 pdata）
# 读取: query-pdata（RCON data get entity ... NeoForgeData）
powershell -NoProfile -ExecutionPolicy Bypass -File bench/datafix/run-datafix.ps1 summon-target
powershell -NoProfile -ExecutionPolicy Bypass -File bench/datafix/run-datafix.ps1 query-pdata
```

## 8. 结论

本票完成：8 类数据实码盘点（含平台差异与既有保护机制）、合成旧格式 fixture 数据集 + 步骤化回读 harness
（`bench/datafix/`，随仓库提交）、六类回读场景的运行时取证（受保护数据 0 损毁）。发现 1 个已实码复现的
运行缺陷（WORLD pack 激活 reload 在相对 world 路径下 fatal 且吞错，§3-1）、2 个行为缺口（迁移设施缺位 §3-2、
join 窗口 pdata 静默丢弃 §3-3）、1 个 Fabric 文案/行为落差（§3-4）。全部缺口如实保留失败证据，未放宽任何口径，
未修改任何运行时源码。
