# 工单 31：Fabric raw loader 源根显式所有权迁移——实施报告（2026-09-15）

> 分支 `ticket-31-fabric-raw-root`（基于 master `47f4a444`），隔离 worktree `D:\mcmodDemo\NekoJS\.worktrees\t31\NekoJS-mult`。
> 证据目录：本目录 `evidence/`。方法沿用票 01 基线（`2026-09-12-fabric-build-fix/` 的 jar facts + 冷构建序列）。
> 关联 commit：`9a3cf9d1`（迁移前基线）→ `dd752171`（raw root 迁移）→ `295b90be`（fabric McVersionCompat provider）→ `579b58cf`（smoke 兼容面调用）→ 证据回填 commit。

## 0. 结论摘要

1. **迁移**：68 个文件（java 61 / resources 4 / templates 1 / smoke fixture 2）从 `versions/26.1.2-fabric/src/main|test` `git mv` 到交接单批准锚点 `src/fabric/{java,resources,templates,test/resources/fabric-runtime-smoke}`；Fabric convention 改为两节点无条件显式挂载 `src/fabric`。迁移 commit 后两 fabric jar 与迁移前**逐字节一致**（SHA-256 不变）——迁移本身零内容影响。
2. **潜伏崩溃修复**：共享树 `McVersionCompat` 在 fabric 节点编译打包但此前零 ServiceLoader provider，而 `inject.MixinLevel` 已让 Level 实现 `LevelExtension`、d.ts 已宣传 `spawnLightning`——脚本一调用就会 `ExceptionInInitializerError`（静态初始化 ServiceLoader 失败）。本票以 per-node override（`Fabric261/262VersionCompat` + services 条目）把该面转为**可用**。
3. **runtime smoke**：两 fabric 节点本地 runServer 全绿，三标记齐全（`startup bindings ok` / `server started` / **`spawnLightning ok, bolt=true`**），日志零 ERROR/Exception；CI 增加第三标记断言。
4. **五节点无回归**：NeoForge 三节点迁移前后 jar 哈希逐字节一致；1.21.1 的 check 因输入未变全部 up-to-date（Gradle 输入哈希本身即"src/fabric 不在其输入集"的证明）；srcDirs 探针显示 NeoForge 三节点零 `src/fabric` 挂载。
5. **guardLint**：扫描 331→392（+61 恰为迁入文件数），守卫块 248、违规 0、警告 0、豁免 0；规则未动，common 的 MC/loader 隔离与 Graal 许可未放宽。

## 1. AC 逐条判定

| # | AC | 判定 | 证据 |
|---|---|---|---|
| 1 | 锚点是两 Fabric 节点唯一共享 raw loader 根；旧 root 仅作记录在案过渡 bridge；26.2 身份/坐标/制品命名不变 | **满足** | convention `fabricSourceRoot = rootProject.file("src/fabric")`（dd752171）；srcDirs 探针（`evidence/post-srcdirs-probe.txt`）：两 fabric 节点各挂 `src\fabric\java`/`resources` 恰一次；`deps.fabric_source_node` 键保留在两节点 gradle.properties、convention 不再读取（过渡 bridge，票 32 删）；jar 名仍 `nekojs-fabric-26.2-1.1.0-preview3.jar`、`fabric.mod.json` `minecraft ~26.2`、节点名 `26.2.0-fabric` 不变（`post-jar-facts-final.txt`） |
| 2 | 文件移动只按共享 raw root 所有权执行；节点专属/有意留原位逐文件说明 | **满足** | §3 清单：61 java 全部是两节点共享 loader 源（迁移前 26.2.0-fabric 经 bridge 编同一批文件）、4 资源 + 1 模板同根消费（AW/metadata template）、2 fixture 是共享 smoke 面 → 全部迁移；**有意留原位/新增于节点目录**：`versions/<fabric-node>/src` 仅承载 per-node override（Fabric261/262VersionCompat + services + wiring 测试，交接单 W8"版本差异由节点 override 承担"）；无"目录整洁式"全量搬迁 |
| 3 | provider matrix 只诊断实际输入与测试结果；`not verified` 独立状态不被改判 | **满足** | §4 matrix 全部条目给出"打包/调用/证据"三列；fabric 的 `McClientCompat` 记为 **not sampled**（零调用方，无从采样），未外推 supported/unsupported；无任何既有支持等级被调低 |
| 4 | 每节点列出实际打包/调用 provider；fabric smoke 真调用 `Level.spawnLightning` 或等价面；不允许 ServiceLoader 潜伏初始化崩溃 | **满足** | §4 matrix；smoke fixture（579b58cf）在 `ServerEvents.started` 调 `event.server.overworld().spawnLightning(0.5,100,0.5)`；两节点 `spawnLightning ok, bolt=true`（`evidence/smoke-*-markers.txt`）；失败路径显式化：catch 先打 `spawnLightning rejected: <原因>` 再抛出 |
| 5 | 1.21.1 独立 compat 路径与 26.x provider 路径差异显式记录；`not sampled` 不外推 | **满足** | §4：1.21.1 的 `LevelExtension` 是节点 override（`versions/1.21.1/.../api/inject/LevelExtension.java:49-51` 直接 `EntityType.LIGHTNING_BOLT`，不经 McVersionCompat，节点树零 facade 引用）；1.21.1 jar 无 McVersionCompat services 条目属设计行为（facade javadoc 明示）；`McClientCompat@fabric` 记 not sampled |
| 6 | 两 Fabric 节点只经 convention 显式挂载共享 raw root；不声称 Stonecutter 预处理该根；版本差异由 compat facade/节点 override 承担 | **满足** | convention 注释 + srcDirs 探针；`src/fabric` 在共享树 source set 布局（src/main、src/test）之外，`stonecutterGenerate` 产物不含它（生成副本计数随共享树而非 src/fabric）；版本漂移实例 = `Fabric261/262VersionCompat` 节点 override |
| 7 | Stonecutter、五节点、支持等级不变；不新增 Gradle project/API jar/版本树 | **满足** | settings.gradle.kts 节点图仅注释更新；无新 project；26.2.0-fabric 身份不变；NeoForge 三 jar 哈希不变 |
| 8 | fabric Java/资源/模板/fixture 所有权落位完整，旧路径消费者全部更新或归类过渡引用 | **满足** | §5 消费者清单：live 消费者 4 处全更新（convention、CI fixture 路径、settings 注释、wiki 构建系统页）；`docs/architecture-refactor/**` 历史基线/决策文档与 `docs/fabric-node-expansion.md` 显式归类为历史记录（描述当时状态，非活消费者） |
| 9 | runtime smoke fixture 的测试资源接线与 CI 拷贝用途分别记录；CI 直接复制不误写成 Gradle 测试资源已消费 | **满足** | `src/fabric/test/resources/fabric-runtime-smoke` **未接**任何 test source set（convention 只挂 main）；CI 注释与本文 §6 明示"仅 CI 拷贝"；迁移前 26.1.2-fabric 曾经由节点本地 test 目录把 fixture 带上 test classpath（无测试消费），迁移后两节点一致地不携带——记录为已说明差异 |
| 10 | guard lint 扩大扫描零违规；common 隔离与 Graal 许可不放宽 | **满足** | `guardLint: 守卫块 248，扫描 392 个文件；超限豁免 0 个；警告 0 条`（`evidence/post-build-fabric-migration.log` + 直跑复现）；guardLint 规则代码零改动（diff 仅 dd752171 的文件移动） |
| 11 | 两 fabric 节点编译、检查、制品验证、smoke 通过；失败可回滚 | **满足** | `post-build-fabric-provider.log` BUILD SUCCESSFUL（含 test + verifyFabricRuntimeArtifact）；双节点 runServer 三标记全绿；回滚 = `git revert dd752171 295b90be 579b58cf`（§7） |
| 12 | NeoForge 防御性排除、fabric 禁止资源检查、fabric 制品验证保留 | **满足** | `java.exclude("**/NeoForge*.java")`、`fabricForbiddenResourceEntries`、`verifyFabricRuntimeArtifact` 均未动（dd752171 diff 可核）；jar facts `forbidden : none`；verify 任务在每次 fabric check 中执行 |
| 13 | 迁移前后 artifact/resource/mixin/metadata/smoke 对照无未说明差异 | **满足** | §6：迁移 commit 单独复测 jar **逐字节一致**；provider commit 的 +3 entries（services 目录项/services 文件/Fabric26x class）与 wiring 测试为**已说明的有意差异**；mixin 29+13+1、fabric.mod.json depends/entrypoints/AW 全不变；smoke 前后均绿（前=旧 fixture 两标记，后=新 fixture 三标记，多出的标记即本票新增断言面） |
| 14 | source bridge、bridge 引用、旧排除规则本票可回滚、不提前删除 | **满足** | `deps.fabric_source_node` 键保留（两节点 properties）；convention 注释记录 bridge 历史与票 01 双挂载教训；旧排除规则全部保留；删除归票 32 |

## 2. 锚点与文件移动清单

锚点（交接单 §4.2 唯一目标）：`src/fabric/java`、`src/fabric/resources`、`src/fabric/templates`、`src/fabric/test/resources/fabric-runtime-smoke`。

| 来源（versions/26.1.2-fabric/src/…） | 目标（src/fabric/…） | 数量 | 所有权理由 |
|---|---|---|---|
| `main/java/com/tkisor/nekojs/bindings/event/**`（CommandEvents、EntityEvents、ItemEvents、LevelEvents、PlayerEvents、ServerEvents、client/KeyBindEvents） | `java/…同路径` | 7 | 共享树同名接口整文件 `//? if neoforge` 守卫的 fabric 孪生；两 fabric 节点共用 |
| `main/java/com/tkisor/nekojs/fabric/**`（入口 ×2、event 桥 ×11、mixin ×30 等） | `java/…同路径` | 51 | fabric loader 专属实现，两节点共享 |
| `main/java/com/tkisor/nekojs/platform/**`（FabricIdCompat、FabricModInfo、FabricPlatform） | `java/…同路径` | 3 | fabric 平台面，两节点共享 |
| `main/resources/**`（nekojs-fabric.mixins.json、-shared、-dynamic、nekojs-fabric.accesswidener） | `resources/` | 4 | AW 与三份 mixin 配置由 convention/AW/`fabric.mod.json` 同根消费 |
| `main/templates/fabric.mod.json` | `templates/` | 1 | `generateModMetadata` 模板展开 |
| `test/resources/fabric-runtime-smoke/**`（startup/server fixture） | `test/resources/fabric-runtime-smoke/` | 2 | 两节点共享 runtime smoke fixture（仅 CI 拷贝消费） |

**留在/新增于节点目录（versions/&lt;fabric-node&gt;/src）**：`Fabric261VersionCompat.java` + services + `FabricVersionCompatWiringTest`（26.1.2-fabric）；`Fabric262VersionCompat.java` + services + 同名 wiring 测试（26.2.0-fabric）。理由：`LIGHTNING_BOLT` 常量 26.1 在 `EntityType`、26.2 在 `EntityTypes`，raw root 不经 stonecutter 预处理、单文件无法同时编两版——版本漂移按交接单落节点 override（与 NeoForge 侧 Nf261/Nf262 同构）。

## 4. provider availability matrix（实际打包 / 实际调用 / 证据）

| 节点 | McVersionCompat.Impl | McPlatformCompat.Impl | McClientCompat.Impl |
|---|---|---|---|
| 1.21.1（NeoForge） | **不提供（设计行为）**：facade 类打包但无 services 条目；1.21.1 `LevelExtension` 为节点 override、直接用 `EntityType.LIGHTNING_BOLT`，不经 facade（节点树零 facade 引用）→ 无潜伏调用方 | Nf1211PlatformCompat：打包+调用（`NekoJSMod.isClientDist`、`NekoJSNetwork`、`ClientReloadExecutor`） | Nf1211ClientCompat：打包+调用（`NekoHostIdentifier`、`NekoSecurityWarningHandler`、error dashboard、`NekoJSNetwork` 客户端分支） |
| 26.1.2（NeoForge） | Nf261VersionCompat：打包+调用（`LevelExtension.neko$spawnLightning`；`McVersionCompatWiringTest`） | Nf261PlatformCompat：打包+调用 | Nf261ClientCompat：打包+调用 |
| 26.2.0（NeoForge） | Nf262VersionCompat：打包+调用 | Nf262PlatformCompat：打包+调用 | Nf262ClientCompat：打包+调用 |
| 26.1.2-fabric | 迁移前：**零 provider（潜伏初始化崩溃）**；本票后：**Fabric261VersionCompat 打包+调用**（smoke `spawnLightning ok, bolt=true`；wiring 测试 1/1） | **facade 类不编译**（`McPlatformCompat` 整文件 `//? if neoforge` 守卫）；fabric 网络/dist 走自有 `FabricPlayNetwork`/`FabricServerEventBindings` 通道 → 不适用 | facade 类打包（无 loader 守卫）；**零 provider、零 fabric 调用方** → **not sampled**（无运行路径可采样，不外推 supported/unavailable） |
| 26.2.0-fabric | 迁移前同上；本票后：**Fabric262VersionCompat 打包+调用**（smoke 同上；wiring 测试 1/1） | 同上 | 同上 |

打包证据：`evidence/pre-jar-facts*.txt`（迁移前 services/类存在性）与 `post-jar-facts-final.txt`（迁移+provider 后）。调用证据：代码引用 grep（报告 §4 所列类）+ 测试 + smoke。**潜在外推禁止**：fabric 的 `McClientCompat` 保持 `not sampled`；若未来 fabric 客户端面（错误面板/主机码）移植，需先补 per-node provider 或显式 unavailable 通道，否则会复现同类 ServiceLoader 崩溃——已列为票 32/后续域 owner 注意项。

## 5. 旧路径消费者清单（更新/归类）

| 消费者 | 类型 | 处置 |
|---|---|---|
| `buildSrc/.../nekojs.fabric-node.gradle.kts` | live（构建接线） | 更新：挂载/注释/bridge 历史全部改锚点（dd752171） |
| `.github/workflows/ci-build.yml:304`（fixture_dir） | live（CI） | 更新为 `src/fabric/test/resources/fabric-runtime-smoke`（dd752171） |
| `settings.gradle.kts` 头注释 | live（文档性接线注释） | 更新（dd752171） |
| `wiki/构建系统.md` ×2 处 | live（共享 wiki） | 更新（dd752171） |
| `versions/{26.1.2,26.2.0}-fabric/gradle.properties` 的 `deps.fabric_source_node` | live（属性键） | **保留不动**：过渡 bridge 元数据，票 32 删除 |
| `docs/architecture-refactor/**`（基线/决策/evidence/proposal/next-steps）与 `docs/fabric-node-expansion.md` | 历史记录 | **不改**：描述各自时点的状态，非活消费者 |

## 6. 迁移前后制品对照（两 fabric 节点）

三个测量点（同一 worktree、同 revision 链）：

| 节点 | 迁移前（9a3cf9d1 采） | 迁移后·仅迁移（dd752171 后） | 迁移后·+provider（295b90be 后） |
|---|---|---|---|
| 26.1.2-fabric sha256 | `777739128b5e7eba…` | **同一哈希（逐字节一致）** | `b65d35f5ab2d4221…`（+3 entries） |
| 26.2.0-fabric sha256 | `905180a54b6b668d…` | **同一哈希（逐字节一致）** | `9b66fe3232c6b3ad…`（+3 entries） |
| entries | 7424 / 0 dup / 7 twins ×1 | 同左 | 7427（+`META-INF/services/` 目录项、+services 文件、+`Fabric26xVersionCompat.class`） |
| mixin refs | 29+13+1 | 同左 | 同左 |
| fabric.mod.json | depends fabricloader>=0.19.3 / fabric-api>=0.155.2+26.1.2（26.2：>=0.159.0+26.2）/ minecraft ~26.1.2（26.2：~26.2）/ java>=25；entrypoints main+client；AW | 同左 | 同左 |
| forbidden（AT/mods.toml/NF mixin/NF 类） | none | none | none |
| smoke | 旧 fixture：startup bindings ok + server started | （未单独跑；jar 未变） | 三标记全绿（+`spawnLightning ok, bolt=true`） |

NeoForge 三节点（对照）：`1.21.1 = 2b04f132…`、`26.1.2 = 2667289b…`、`26.2.0 = 6950910b…` 迁移前后**逐字节一致**；`post-build-neoforge.log` 中三节点 check 全部 up-to-date（Gradle 输入哈希未变 = `src/fabric` 不在 NeoForge 输入集）。

已说明差异汇总（AC13 口径）：
1. fabric jar +3 entries：本票 provider 交付（有意差异，见 §4）。
2. 26.1.2-fabric test classpath 不再携带 fixture 两文件（迁移前节点本地 test 目录自动挂载、无测试消费；迁移后与 26.2.0-fabric 行为一致）。
3. smoke 断言面 +1 标记：新调用面（有意增强）。

## 7. bridge 与回滚

- **保留物**：`deps.fabric_source_node` 键（两节点 properties）、convention 内 bridge 历史注释、`NeoForge*.java` 排除、`fabricForbiddenResourceEntries`、`verifyFabricRuntimeArtifact`。删除条件（交接单 §4.3）未满足前一律不动。
- **回滚**：`git revert 579b58cf 295b90be dd752171` —— 文件回到 `versions/26.1.2-fabric/src`、convention 恢复 bridge 读取、CI 恢复旧 fixture 路径；`9a3cf9d1`（基线证据）可留。迁移后如需再验证回滚态构建，注意票 01 教训（自源节点双挂载）在回滚后的 convention 版本里已由 `14de611f` 守卫覆盖。

## 8. 验证命令与结果（全部在 worktree 内）

| 命令 | 结果 |
|---|---|
| `./gradlew :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`（迁移前/仅迁移/+provider 三轮） | 三轮 BUILD SUCCESSFUL（44s / 46s / 23s；`evidence/pre-build-fabric.log`、`post-build-fabric-migration.log`、`post-build-fabric-provider.log`） |
| `./gradlew :1.21.1:build :26.1.2:build :26.2.0:build --console=plain`（迁移前后） | 两轮 BUILD SUCCESSFUL（20s / 6s；jar 哈希逐字节一致） |
| `./gradlew guardLint --console=plain --rerun-tasks` | BUILD SUCCESSFUL；`守卫块 248，扫描 392 个文件；豁免 0；警告 0` |
| `:26.x-fabric:test`（经 build） | 16 suites / 74 tests / 6 skipped / 0 failed（两节点同；含 `FabricVersionCompatWiringTest` 各 1） |
| fabric runtime smoke（本地 CI 等价：fixture 从 `src/fabric/test/resources/fabric-runtime-smoke` 铺设 + `runServer` 分离启动 + 轮询标记 + RCON stop，端口 25881/RCON 25882） | 两节点三标记全绿、日志零 ERROR/Exception、gradle BUILD SUCCESSFUL（1m36s / 1m23s；`evidence/smoke-*.log`、`smoke-*-markers.txt`）。与 CI 的对应：同 fixture、同 gradle 任务、同标记断言（CI 另有 180s timeout 包装与 packaged-artifact 校验，后者由本地 `verifyFabricRuntimeArtifact` + jar facts 覆盖） |
| srcDirs 探针（init 脚本） | `evidence/post-srcdirs-probe.txt`：NeoForge 三节点无 `src/fabric`；两 fabric 节点各挂载一次 |

## 9. 遗留缺口与建议 owner（不阻塞本票 AC）

| 缺口 | 说明 | 建议 owner |
|---|---|---|
| `deps.fabric_source_node` 键、convention bridge 历史注释删除 | 交接单 §4.3 删除条件已基本齐（本票 parity 证据可作输入），剩 NeoForge 三节点 check 与 sandboxCheck 汇总复跑确认 | **票 32**（W8 收口） |
| fabric `McClientCompat` provider | facade 类打包但零 provider零调用（not sampled）。若 fabric 客户端面（错误面板/主机码/dashboard GUI）移植，必须同时补 per-node provider（26.1 `Minecraft#screen` vs 26.2 `Gui#screen()` 等漂移）或显式 unavailable 通道 | W7 客户端域票 / 票 32 排查清单 |
| `McVersionCompat` javadoc 更新 | 现文档写"expected … in neoforge-26.1/neoforge-26.2"；fabric 节点现已提供 provider，措辞待同步 | 票 32 或下一次 facade 触碰（纯文档） |
| guardLint 对 `src/fabric` 的 wrapper 规则适配 | 当前零违规；`src/main/java/.../wrapper/` 前缀规则不覆盖 `src/fabric`（fabric 无 wrapper 目录），无需动作，仅记录 | 无（观察项） |
| `docs/fabric-node-expansion.md` 等历史文档的旧路径 | 历史记录，不改 | 无 |
| CI 全链路（ubuntu）复跑 | 本票验证为本地 Windows 等价；CI 合并后自然覆盖 | 主会话合并时观察 |

## 10. 审查重点建议

1. `dd752171` 的 convention 改动（唯一行为接线）：无条件注入是否引入双挂载风险的边界论证（srcDirs 探针 + 逐字节一致的 jar 是主要证据）。
2. `295b90be` 的 provider 落位选择（节点 override vs raw root 内 registry 查询单文件）：本票选节点 override，理由是遵守"raw root 不做版本分支"与 Nf261/262 既有模式。
3. `579b58cf` 的 fixture 失败语义（rejection 标记 + rethrow + CI 第三 grep）是否满足"明确 unavailable/rejection"口径。
4. matrix 中 not sampled / 不适用 的判定边界（AC3/AC5 的证据状态词汇）。
