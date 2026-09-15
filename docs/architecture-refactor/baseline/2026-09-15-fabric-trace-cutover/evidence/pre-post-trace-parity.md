# 工单 32：迁移前后 trace 等价性对照（2026-09-15）

对照点：
- **迁移前基线** = 票 31 终态测量（`../2026-09-15-fabric-raw-root/evidence/post-jar-facts-final.txt`，
  采自票 31 分支 295b90be 后，分支 base `47f4a444` **不含票 07**）。
- **迁移后（本票）** = master `7fb521eb`（= 票 07 merge `772ef466` + 票 31 merge `13da6fd5`）
  全新 worktree 冷构建（`jar-facts.txt`、`fabric-trace-five-layers.txt`）。

## source trace

| 项 | 票 31 迁移清单（REPORT §2） | 本票 L1（trace L1 段） | 判定 |
|---|---|---|---|
| java | 61（7 bindings/event 孪生 + 51 fabric/** + 3 platform/**） | 61，包路径逐一相同（trace 列出全部 61 个路径+SHA） | 等价 |
| resources | 4（AW + 3 mixin json） | 4 | 等价 |
| templates | 1（fabric.mod.json） | 1 | 等价 |
| fixture | 2（startup/server fabric_ci_smoke.js） | 2 | 等价 |
| 节点 override | Fabric261/262VersionCompat + services | 同（每节点 1 个 .java，trace L1） | 等价 |

master 7fb521eb 与票 31 分支对 `src/fabric/**` 的 git diff 为零（票 07 不触该目录）。

## artifact / resource / mixin / metadata trace（两 fabric 节点）

| 项 | 票 31 终态 | 本票 | 判定 |
|---|---|---|---|
| entries | 7427 / 7427 | 7433 / 7433 | **+6，见下方归因** |
| ZIP 重复 | 0 / 0 | 0 / 0 | 等价 |
| 7 孪生计数 | ×1（7/7） | ×1（7/7，字节==节点编译产物） | 等价（本票补齐了字节级回链） |
| services | McVersionCompat$Impl = Fabric261/262VersionCompat 各 1 | 同 | 等价 |
| compat classes | McVersionCompat/McClientCompat/LevelExtension/MixinLevel PRESENT；McPlatformCompat absent | 同 | 等价 |
| mixin refs | 29 + 13 + 1 | 29 + 13 + 1 | 等价 |
| fabric.mod.json | depends fabricloader>=0.19.3 / fabric-api>=0.155.2+26.1.2（26.2：>=0.159.0+26.2）/ minecraft ~26.1.2（26.2：~26.2）/ java>=25；entrypoints main+client；AW | 同 | 等价 |
| forbidden | none | none | 等价 |
| sha256 | b65d35f5… / 9b66fe32… | 31f6ecda… / b70e1085… | 变化=+6 entries 及行号/时间戳（见归因） |

NeoForge 对照：票 31 `2b04f132…/2667289b…/6950910b…`（1395/1448/1448 entries）→ 本票
`d1597d2a…/5ba0e0cc…/c40b5e89…`（1401/1454/1454 entries），每节点 **+6**。

## 有意差异归因（唯一差异源：票 07 合并）

五节点 jar 的 +6 entries **逐 entry 精确等于**票 07 在 `:common` 新增的两个源文件
（`git diff --name-status 47f4a444 772ef466 -- common/src/main/java` 确认仅新增
`SyncEvalWatchdog.java` 与 `ScriptLifecycleGate.java`）：

```
com/tkisor/nekojs/core/SyncEvalWatchdog$Guard.class
com/tkisor/nekojs/core/SyncEvalWatchdog.class
com/tkisor/nekojs/core/lifecycle/            （目录项）
com/tkisor/nekojs/core/lifecycle/ScriptLifecycleGate$Decision.class
com/tkisor/nekojs/core/lifecycle/ScriptLifecycleGate$Operation.class
com/tkisor/nekojs/core/lifecycle/ScriptLifecycleGate.class
```

- **能力说明**：票 07 = same-type serialization、close-priority 与 watchdog isolation
  recovery（reload 域增强）；fabric fat jar 内嵌 common output、NeoForge jar 编译共享树
  并内嵌 common，故五节点同步 +6。
- **owner**：票 07 域（已验收关闭，见 `a8aaf764`）；本票只做归因登记，不改动其内容。
- 票 31 的 7427 测量点不含票 07（分支 base 47f4a444 早于票 07 merge），因此该差异是
  **测量基线组合差**，不是 src/fabric 迁移或本票 trace 机制的回归。除此 +6 外，
  entries、services、mixin、metadata、forbidden、孪生计数全部逐字段等价。
- sha256 随之变化（class 内容/数量与 jar 时间戳）；本票在相同 worktree 内的
  删除前/删除后复测（见 REPORT §bridge）以**同机连续构建**口径消除环境漂移。
- **sha 变化的第二贡献源（审查 S1 补列）**：`47f4a444..772ef466` 除上列 2 个 A 文件外
  还有 5 个 M 文件（`EventBusJS`、`DefaultErrorTracker`、`NekoNodeTimers`、`ScriptExecutor`、
  `ScriptManager`）——它们改变 common class 内容但不新增 entry（+6 实测已排除 inner-class
  增量），与上面 +6 清单合并构成 sha 归因的完整链。

## 未处理差异登记（AC3 口径）

- `src/fabric` raw root **无任何 Stonecutter 预处理方案**（设计如此，交接单 W8）。
  已知版本差异 = `LIGHTNING_BOLT` 常量漂移（26.1 `EntityType` / 26.2 `EntityTypes`），
  **未处理、由节点 override 承担**：`Fabric261VersionCompat` / `Fabric262VersionCompat`
  （versions/&lt;node&gt;/src，services 条目注入），不经 Stonecutter。
- raw root 内 61 个文件必须同时编过 26.1.2 与 26.2（convention 注释明示）；当前无
  其它已知漂移。未来新增漂移的承担路径 = compat facade 或节点 override，不得在
  raw root 内写版本分支（会被 guardLint 的恒假常量规则 8 拦截单节点分支）。
