# 工单 32：Fabric 五层源唯一性与 bridge 删除——实施报告（2026-09-15）

> 分支 `ticket-32-fabric-trace-cutover`（基于 master `7fb521eb`），隔离 worktree
> `D:\mcmodDemo\NekoJS\.worktrees\t32\NekoJS-mult`。证据目录：本目录 `evidence/`。
> 关联 commit：`709093aa`（五层 trace + 门禁证据）→ `f984953b`（bridge 删除，单独 revert 粒度）
> → 证据回填 commit。

## 0. 结论摘要

1. **五层 trace 建立并闭合**：两 fabric 节点的 raw 源（`src/fabric`）、processed 源
   （stonecutter 生成副本）、编译 class、Jar 去重前打包输入、最终 ZIP entries 分层记录
   （`evidence/fabric-trace-five-layers.txt`）。L4（去重前，按 jar from 序枚举全部来源）与
   L5（最终 jar 文件 entries）**集合全等**；7 个孪生 class 在 jar 内与节点编译产物
   **字节级一致**。生成副本以"注释空壳剥离检验"定性为生成证据，不是第二事实源。
2. **孪生唯一性证明链成立**：源层 7 对同名文件是设计上的双面接口（共享树
   `//? if neoforge` 守卫面 + src/fabric fabric 面）；stonecutter 生成树把 neoforge 面
   100% 注释空壳化（63/63）；编译层每 FQCN 唯一 class、common 零携带；去重前输入层孪生
   来源数全部 x1（唯一的多来源 entry 只有 `META-INF/MANIFEST.MF`，与孪生无关——
   `DuplicatesStrategy.EXCLUDE` 对孪生不承担任何去重）；最终 jar ×1。跨 loader：
   NeoForge 三节点 jar 孪生与 src/fabric 编译物**零字节重合**。
3. **bridge 已删除**（`f984953b`，单独 commit）：删除条件全部满足后收掉
   `deps.fabric_source_node` 两键、convention bridge 历史注释、`McVersionCompat` 过时措辞。
   删除后复测：双 fabric build/test、smoke 三标记、guardLint（248/392/0/0 不变）、
   NeoForge 三节点 check 全绿；jar facts 除 sha256 外**全字段等价**（entries 7433、
   dup 0、twins ×1、services/mixin/metadata/forbidden 逐项相同）。
4. **与票 31 基线的唯一差异已归因**：五节点 jar 各 +6 entries 逐 entry 精确等于票 07
   合并新增的 `SyncEvalWatchdog`/`ScriptLifecycleGate` 6 个 entry（票 31 测量点分支 base
   `47f4a444` 不含票 07）；除此+6 及其连锁的 sha256 外全部维度等价
   （`evidence/pre-post-trace-parity.md`）。

## 1. AC 逐条判定

| # | AC | 判定 | 证据 |
|---|---|---|---|
| 1 | 五层分层记录，生成副本不被误认为第二事实源 | **满足** | `fabric-trace-five-layers.txt` 五段分层；L2 用注释剥离检验（`strip_comments` 后为空 = HOLLOW）证明生成树同名文件是守卫空壳；trace 头部与孪生表明示"生成证据，不是第二源码事实源" |
| 2 | 7 个同名 FQCN 五层均可解释 origin 与最终重复计数；去重策略/集合存在性/guard 数/源文件数不作证明 | **满足** | `twin-fqcn-five-layer-table.md` 逐层解释 + `fabric-trace-five-layers.txt` 数据；证明依据是守卫核验（7/7）、空壳剥离（7/7+63 守卫全量）、class 唯一性 + common 零携带、来源唯一性、字节级回链；表内明示 `EXCLUDE` 只消化 MANIFEST.MF 一项、对孪生不承担去重 |
| 3 | 无预处理方案的版本差异如实记为未处理、由 compat facade/节点 override 承担 | **满足** | `pre-post-trace-parity.md` §未处理差异登记：LIGHTNING_BOLT 漂移（26.1 `EntityType`/26.2 `EntityTypes`）无 Stonecutter 方案，由 `Fabric261/262VersionCompat` 节点 override 承担；convention 注释明示 raw root 不经预处理 |
| 4 | 两 fabric 节点编译/检查/制品验证/runtime smoke 全过，重复计数符合预期 | **满足** | `gate-fabric-build-test.log`（build+test BUILD SUCCESSFUL，74 tests 两节点各无失败）；check 含 `verifyFabricRuntimeArtifact`；`jar-facts.txt` entries 7433/dup 0/twins ×1/forbidden none；smoke `smoke-*-markers.txt` 三标记 + latest.log 与 gradle log 零 ERROR/Exception |
| 5 | 三 NeoForge 节点 check 通过且证明不挂载 src/fabric 或生成副本 | **满足** | `gate-neoforge-build.log`（三节点 build 含 check）；`srcdirs-probe.txt` 三 NeoForge 节点 java/res srcDirs 均无 `src\fabric`；trace L5 跨 loader 对照：NeoForge jar 孪生与 src/fabric 编译物零字节重合（fabric 内容从未进入 NeoForge 制品） |
| 6 | sandboxCheck 聚合通过且**结果**覆盖 guardLint 与节点检查 | **满足** | `gate-sandboxcheck.log`：BUILD SUCCESSFUL，输出含 `guardLint: 守卫块 248，扫描 392 个文件；超限豁免 0 个；警告 0 条` 与 `:1.21.1:check`、`:26.1.2:check`、`:26.2.0:check`、`:26.1.2-fabric:check`、`:26.2.0-fabric:check`、`:common:check` 等任务的实际执行记录（不是任务存在性引用） |
| 7 | 迁移前后 source/artifact/resource/mixin/metadata trace 等价；有意差异有说明与 owner | **满足** | `pre-post-trace-parity.md`：source trace 68 文件逐一对应；artifact/mixin/metadata 逐字段等价；唯一差异 = 五节点 +6 entries，逐 entry 归因票 07（owner 票 07 域，已关闭），附 `git diff --name-status` 证据 |
| 8 | 全部条件满足且替代证明齐备才删 bridge；删除单独成 commit | **满足** | §3 决策记录（条件清单 8/8）；commit `f984953b` 仅含 bridge 三件套（deps 两键、convention 注释段、McVersionCompat 措辞），可独立 revert |
| 9 | 任一条件不满足时保留 bridge 并记录回滚 | **满足（未触发保留分支）** | 全部条件满足，未走到保留分支；回滚路径已写入删除 commit 说明：revert `f984953b` 即恢复 bridge 原状 |

## 2. 五层 trace 方法论（可复现）

采集入口：`evidence/fabric-trace-t32.py`（主脚本）+ `trace-probe.init.gradle`（srcDirs
与依赖清单探针，`--init-script` 运行）+ `jar-facts-t32.py`（制品事实，沿用票 31 脚本）。

| 层 | 采集方式 | 关键判定规则 |
|---|---|---|
| L1 raw source | `src/fabric/**` 全清单（61 java + 4 资源 + 1 模板 + 2 fixture，逐文件 SHA-256）；共享树 7 个孪生源首行守卫核验；节点 override 清单 | 每个孪生 FQCN 在仓库恰 2 个源文件（neoforge 守卫面 + fabric 面），这是设计 origin |
| L2 processed source | stonecutter 生成树逐文件对照 | 同名文件 ≠ 第二源：剥离 `/* */` 与 `//` 注释后为空 = 守卫空壳（HOLLOW），javac 不产 class；`src/fabric` 内容以有效代码出现 = 0；63 个整文件守卫 100% 空壳化 |
| L3 编译 class | 节点 `build/classes/java/main` + `common/build/classes/java/main` | 每 FQCN 恰 1 个 class（两节点哈希逐一相同）；common 携带孪生 = 0（:common 编译 `common/src`，srcDirs 探针佐证） |
| L4 去重前打包输入 | 按 jar 任务 from 注册序枚举：节点 main output（classes+resources+icuClasses）→ common output → common runtimeClasspath（过滤 graal 后 **空**，COMMON_RT_COUNT::0）→ bundled night-config ×2 | 聚合 entry→来源映射；多来源 entry 全集 = {`META-INF/MANIFEST.MF`}；7 孪生来源数 x1、唯一来源 = 节点 sourceSets.main.output |
| L5 最终 ZIP | jar 实测 namelist + 逐 entry 哈希 | ZIP 重复 0；孪生 ×1 且 SHA-256 == L3 节点产物（字节回链）；L4 序首胜出集合与 jar 文件 entry **全等**（7268 文件 + 165 目录项 = 7433） |

NeoForge 侧补充：三节点 jar 孪生 ×1（1.21.1 的 KeyBindEvents ×0 = 该版本无客户端
KeyBind 面，票 01 起已知），与两 fabric 节点编译物零字节重合。

## 3. bridge 删除决策记录（AC8 条件清单）

| # | 条件（交接单 §4.3 删除条件 + 票面 AC8） | 结果 |
|---|---|---|
| 1 | 五层证据（raw/processed/class/pre-dedup/final jar，含重复计数） | ✔ `fabric-trace-five-layers.txt`（L4↔L5 闭合、孪生字节回链） |
| 2 | 两 fabric 节点 compileJava/check/verifyFabricRuntimeArtifact 通过 | ✔ `gate-fabric-build-test.log` + `gate-sandboxcheck.log` |
| 3 | origin trace 无未说明差异 | ✔ 孪生 7/7 逐层解释；与票 31 基线差异全部归因（票 07 +6） |
| 4 | 最终 jar 7 FQCN 重复计数符合预期 | ✔ ×1（两节点） |
| 5 | sandboxCheck 通过 | ✔ `gate-sandboxcheck.log`（guardLint 统计 + 五节点 check） |
| 6 | NeoForge 三节点 check 证明不挂载 src/fabric | ✔ srcDirs 探针 + check 通过 + jar 零字节重合 |
| 7 | smoke 与 fixture 消费者 | ✔ 两节点三标记 + 零 ERROR；CI fixture 路径 `src/fabric/test/resources/fabric-runtime-smoke` 与三标记断言在 `ci-build.yml:307,327,351,355`；全仓无旧路径 live 引用 |
| 8 | bridge 依赖/引用/旧排除规则有替代证明 | ✔ `deps.fabric_source_node` 全仓 live 引用 = 键本身 + convention 注释（零代码读取）；挂载替代 = `fabricSourceRoot` 显式注入（srcDirs 探针）；`NeoForge*.java` 排除、`fabricForbiddenResourceEntries`、`verifyFabricRuntimeArtifact` 为防御 gate，**不属 bridge，全部保留**（交接单 §4.2.6） |

**决策：删除**（commit `f984953b`）。回滚 = `git revert f984953b`（bridge 原状恢复，无行为
耦合）；如需退回迁移前，继续 revert 票 31 的 `dd752171 295b90be 579b58cf`。

## 4. 删除后复测（无回归）

| 项 | 删除前 | 删除后 | 结论 |
|---|---|---|---|
| 双 fabric build+test | BUILD SUCCESSFUL | BUILD SUCCESSFUL（`post-delete-fabric-build-test.log`） | 无回归 |
| jar facts | 7433/0 dup/twins ×1/forbidden none | 同（除 sha256 外**全字段 diff 为空**） | 行为等价 |
| sha256 | `31f6ecda…`/`b70e1085…` | `aed6cc5d…`/`d8f3615c…` | 预期差异：McVersionCompat javadoc 行数/字符串变化 → 该类 LineNumberTable 与常量池变化（其余删除项不进字节码）。旧制品未留存做逐 entry 字节 diff，等价性以 facts 全等 + git diff 边界（f984953b 仅 4 文件）归因 |
| guardLint | 248/392/0 豁免/0 警告 | 同（`post-delete-guardlint-neoforge-check.log`） | 无回归 |
| NeoForge 三节点 check | 通过 | 通过（同 log） | 无回归（McVersionCompat 为共享树类，三节点重编） |
| runtime smoke | 两节点三标记 + 零 ERROR | 两节点三标记 + 零 ERROR（`smoke-post-delete-*`） | 无回归 |
| NeoForge 三节点 jar | `d1597d2a…/5ba0e0cc…/c40b5e89…`（1401/1454/1454） | `ce892297…/1188ede…/faed6c89…`，entries 1401/1454/1454 不变（`post-delete-jar-facts.txt`） | 同一归因（McVersionCompat 重编） |

## 5. 验证命令与结果（全部在 worktree 内）

| 命令 | 结果 |
|---|---|
| `./gradlew :26.1.2-fabric:build :26.2.0-fabric:build :26.1.2-fabric:test :26.2.0-fabric:test --console=plain` | BUILD SUCCESSFUL（22s；删除前后各一轮） |
| `./gradlew :1.21.1:build :26.1.2:build :26.2.0:build --console=plain` | BUILD SUCCESSFUL（19s） |
| `./gradlew sandboxCheck --console=plain` | BUILD SUCCESSFUL（32s；guardLint 统计 + 五节点 check 全执行） |
| `./gradlew guardLint :1.21.1:check :26.1.2:check :26.2.0:check --console=plain`（删除后） | BUILD SUCCESSFUL（28s） |
| `python evidence/fabric-trace-t32.py . <out>` | 五层 trace 输出（L4↔L5 全等、孪生字节回链） |
| `./gradlew --init-script evidence/trace-probe.init.gradle :26.1.2-fabric:traceFabricBundled :26.2.0-fabric:traceFabricBundled :common:traceCommonRuntime` | SRCDIRS 16 行 + COMMON_RT_COUNT::0 + BUNDLED ×2/节点 |
| `python evidence/smoke-fabric-node.py . <node> <prefix>` | 删除前后各两轮，全 PASS（RCON stop 优雅退出，gradle exit=0） |
| `python evidence/jar-facts-t32.py . <out>` | 五节点 facts；删除前后非哈希字段全等 |

## 6. 遗留缺口与建议 owner（不阻塞本票 AC）

| 缺口 | 说明 | 建议 owner |
|---|---|---|
| fabric `McClientCompat` provider（承票 31） | facade 打包、零 provider 零调用（not sampled）。fabric 客户端面移植时必须补 per-node provider 或显式 unavailable 通道 | W7 客户端域票 |
| 删除前制品未留存 | bridge 删除导致的 jar sha 变化以 facts 全等 + git diff 边界归因，未做逐 entry 字节 diff（旧 jar 被 rebuild 覆盖）。如需字节级审计，可 revert f984953b 后同机重建对照 | 构建证据（按需） |
| CI 全链路（ubuntu）复跑 | 本票验证为本地 Windows 等价（含 RCON stop 与 CI 的 timeout 包装差异）；CI 合并后自然覆盖 | 主会话合并时观察 |
| guardLint 对 src/fabric 的 wrapper 规则 | 承票 31 观察项，现状零违规、无 wrapper 目录，无需动作 | 无 |

## 7. 审查重点建议

1. `fabric-trace-t32.py` 的 L2 空壳判定（`strip_comments` 后为空）是否足以防止"文件名
   存在"被误读为双源——这是本票把 AC1/AC2 从"计数"升级为"有效性检验"的核心。
2. L4 来源序（jar 任务 from 注册序 = EXCLUDE 胜者序）的建模是否与 convention 实际一致
   （节点 output → common output → runtime deps（空）→ bundled）。
3. `f984953b` 的边界：只删 bridge 三件套，防御 gate（NeoForge 排除/forbidden/verify）
   一字未动；`McVersionCompat` 措辞变化是唯一源码语义外改动。
4. 票 07 +6 归因链（`git diff --name-status 47f4a444 772ef466` ↔ 五节点 jar 各 +6 逐
   entry 全等）是否接受为"迁移前后等价"的基线组合差解释。
