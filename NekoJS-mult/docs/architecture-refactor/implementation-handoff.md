# NekoJS 实施交接单

> 状态：规划已完成并可交接，非源码实施授权。本轮只完成规划文档收口；无 Java/Gradle/Stonecutter/CI 修改，也未运行构建/测试。依据 proposal、已闭合的决策票 00-10 和当前源码/构建证据整理。
> 本文件只说明实施顺序、物理落点、接口责任、验证输入和删除条件；它不批准 Java、Gradle、Stonecutter、数据格式、公开接口或节点集合的修改。
> 规划收口检查清单见 [planning-completion-checklist.md](planning-completion-checklist.md)；清单变绿也不替代维护者对源码实施的单独授权。

实施某一功能域前先读 [对应 spec](specs/README.md) 的用户故事与测试决策；取舍仍以源决策 Resolution 为准，具体迁移路径与删除前提以本交接单及功能覆盖账本为准。获准实施后的正式认领、依赖、验收和关闭入口是 [实现票据索引](implementation-tickets/README.md)；本文件的 W0-W10 只是物理迁移/交接辅助视图，不是第二执行队列。规格已就绪不代表本轮授权实施。

## 1. 已闭合约束

- 决策 01：逻辑深 Module 优先；一个 `NekoRuntimeRoot`；域内事实源；不因目录整洁新增 Gradle project 或 API jar。
- 决策 02：NeoForge `26.1.2` primary；NeoForge `26.2.0` secondary；NeoForge `1.21.1`、`26.1.2-fabric`、`26.2.0-fabric` experimental；五节点暂不 EOL。
- 决策 03：保留并收紧 Stonecutter；`common` 禁止 Minecraft/loader import；根 `src/` 可承载共享 MC-facing 实现；真实节点差异进入 Adapter 或 node。
- 决策 04：managed contract、legacy preview、Point/plugin contract、Graal/raw Java 面分层；`common`（含 `api.*`）允许 GraalJS，但不允许 MC/loader 依赖；不新增 API artifact。
- 决策 05：普通 reload 只切换脚本环境和模块 session；失败保留 NekoJS 自有 active runtime/state；不重启 Plugin Runtime 或重复注册平台资源；不承诺撤销外部副作用。
- 决策 06：保留 JS/ESM/CJS、TS/JSX/TSX、Python；Preparation、Module Resolution/Cache、Execution 是 common 内逻辑 Module；纯 Java 自研优先。
- 决策 07：P0-P4 是内部阶段；唯一 clean cutover 为 `1.2.0`；使用显式 golden 审阅、五节点/能力 gate、数据回滚保护和维护者四类试做。离线 validator/migration report 是可选、默认只读的辅助产物，不是硬 release gate；性能是独立 P0 baseline，阈值在 P4 前依据 baseline 决定，不塞入 W0。
- 决策 08：搬运功能按事件语义分层；只有明确生命周期、多个脚本/插件贡献、注册/reload/事务提交语义的公开面进入 Managed Surface 事件面；factory、query、runtime command、send/action 继续作为 binding/Adapter。不造万能 Event Module、第二 registry path，也不为没有真实生命周期的功能制造 Point。
- 决策 09：candidate/active 按 generation 完全隔离；commit 前真实平台 callback、生产 timer、对外 binding 和 live registry mutation 只属于 active；Plugin Runtime/Handle 进程级，session object 按 generation 失效；按 ScriptType owner thread 串行 reload/close，watchdog 不自动创建第二个 active runtime。
- 决策 10：`global` 的完整语义以 [跨 reload 的 global 共享状态如何参与候选事务？](decisions/10-shared-global-candidate-writes.md) 的 Resolution 为唯一权威来源；本交接单只保留必要 owner、fixture、迁移与删除条件，不重复裁定。`shared` 是工作名，最终符号在 W5 冻结，不是新的必决问题。

### 1.1 搬运功能适配（规划投影，未实施）

以下内容是 W6/W7 的方向投影，不冻结最终事件名、payload、API signature、`remove`/`replace`/`modify` 语义或 Fabric parity；实际 API 需经 W5-W7 的 contract/golden/capability/smoke 证据再定稿。

- **Villager Trades**：进入现有 `ServerEvents` 的数据/reload 子事件（工作名，非最终 API）；第一版只规划 `add` + stable query，`remove`/`replace`/`modify` 延后；平台 registry mutation 只放版本/平台 Adapter。Fabric 当前记为 `unavailable`，必须显式 capability，不能静默 no-op。
- **Dynamic Registry**：公开服务器运行期动态注册事件 facade（工作名 `ServerEvents.dynamicRegistry`，非最终 API）+ Registry Runtime/platform/version Adapter；Adapter 负责 claim/stale/cleanup/ID/sync/registry surgery；不与启动期 `RegistryEvents.register` 混淆。Fabric/1.21.1 capability 显式，不能静默降级。
- **Item/Block modification**：保留既有 `ItemEvents.modification` 与 `BlockEvents.modification`，把修改声明收口为 inert candidate plan；snapshot/restore 由 `NekoRuntimeRoot` 或授权 domain owner 管理，合法 commit 点才由平台/版本 Adapter 应用。详细 owner、依赖与验收以 [票 39](implementation-tickets/39-item-block-modification.md) 为准。
- **PostEffects**：`register`/`unregister` 进入现有 `ClientEvents` 的客户端资源/reload 子事件；`set`/`clear`/`toggle`/`current` 保留 runtime binding/Adapter。
- **复用与不重复**：Assets 复用已有 `ClientEvents.generateAssets`；EntitySelectors 保持 factory/query binding；已事件化域不重复造事件，普通启动期 registry 仍走 `RegistryEvents.register`。

## 2. 物理落点规则

| 位置 | 放置内容 | 明确禁止 |
|---|---|---|
| `common/src/main/java/com/tkisor/nekojs/api/` | 作者入口、managed contract、Plugin contract、必要 Graal interop facade | Minecraft/loader import、MC 对象实现、第二规范源 |
| `common/src/main/java/com/tkisor/nekojs/core/`、`script/`、`eventbus/`、`probe/` | Plugin Runtime、Preparation、Module Resolution/Cache、Execution Environment、Pack Trust、Probe | Minecraft/loader import、平台 callback、第二 runtime owner |
| `src/main/java/`、`src/main/resources/` | 真正共享的 MC-facing wrapper、binding、registry metadata/builder、可由 guard/facade 表达的共同平台面 | 只属于一个 loader/时代的整文件实现、隐式版本语义 |
| `src/fabric/` | Fabric 两个 26.x 节点共同的 loader source root；只由 Fabric convention 显式挂载 | NeoForge source、common runtime、第二份跨 loader business logic |
| `versions/<node>/src/main/` | 无法用小 Adapter/facade 表达的节点差异、原生接线、高湍流或整文件 override | 完整 Script Runtime、Plugin Runtime、复制 shared business logic |
| `common-api-processor/` | 必须独立编译的 annotation processor | runtime owner、Script API jar |
| Stonecutter/buildSrc | variant evaluation、机械 replacements、资源/metadata/mixin 处理、node 验证 | 业务语义、隐式 capability、第二注册路径 |

`common` 的 MC/loader-free 规则与 `api.*` 的 GraalJS allowance 是两个独立规则，不能用后者绕过前者。

## 3. 垂直工作单

| ID | 当前事实入口 | 目标 owner | 主要动作 | 通过条件 | 可删除内容 |
|---|---|---|---|---|---|
| W0 | `settings.gradle.kts:34-43`、`stonecutter.gradle.kts:1-68`、五个 node properties | node/source/artifact manifest | 枚举 source roots、resources、mixin、metadata、processor、capability、test skip 和 generated trace | 五节点 manifest 可复现；等级和 capability matrix 一致 | 仅删除被 manifest/release report 取代的冗余手工记录 |
| W1 | `NekoRuntimeRoot`、两个 loader composition root、`ScriptManager` | 单一 `NekoRuntimeRoot` + shared construction function/factory | 对齐创建顺序；注入 discovery/lifecycle/network/registry 接线；移除 static root 旁路；按决策 09 隔离 candidate/active、切换 generation，并串行化 owner-thread lifecycle | startup、CLIENT、afterInit、reload、close、failure-retain、timer/listener cleanup、generation visibility 和无双重 callback parity | 重复 assembly、static root 读取、第二 manager/container、candidate 提前发布 |
| W2 | `NekoPluginBootstrap`、`NekoPluginRuntime`、`NekoJSBasePluginManager`、`NekoJSPlugin` | Plugin Runtime | 保留 Point、Contributor、Hook、dependsOn、freeze、Handle/result；平台只给 discovery input；区分进程级 Handle 与 generation-scoped session object | pairing、排序、freeze、错误、reload、Handle 失效边界、builtin discovery、external addon fixture | 无调用者 manager facade、loader 私有 Point registry、旧 Handle 静默操作新 session |
| W3 | `NekoCompilationPipeline`、`NekoModulePipeline`、`NekoModulePipelineCache`、language plugins | Script Preparation + Module Resolution/Cache | 显式注入 pipeline/cache；保留所有语言、legacy bridge、source map | corpus、golden、source location、cache invalidation、CJS/ESM link、Graal execution | process-wide static cache、第二语义 pipeline |
| W4 | `NekoSandboxFactory`、`ScriptEnvironmentFactory`、`ScriptExecutor`、`ScriptContextRegistry` | Script Execution Environment | 集中 Context、HostAccess、ClassFilter、Node shim、session、timer/listener；按决策 09 实现 candidate/active generation、owner-thread 串行化和 watchdog policy | candidate failure 保留 NekoJS-owned runtime；资源释放、trust audit、线程/重入、watchdog 恢复可观察 | Context mapping 直接暴露、重复 reload owner、自动创建第二 active runtime |
| W5 | `CoreManagedApiBootstrap`、`ApiManifestGenerator`、`NekoScriptCatalog`、`LegacySurfaceAdapter` | Managed Surface + Probe | Normative contract 为唯一规范源；legacy/manifest/Probe/declaration 为派生或观察面 | contract/golden、legacy characterization、contribution validation、Probe parity | 手写第二规范 JSON、全仓万能 catalog、legacy 升 stable |
| W6 | `RegistryRepository`、`RegistryEventJS`、`RegistryInfosPoint`、`RegistryTypesPoint`、declarations、drain adapters | Registry Runtime | 保留先攒后建、duplicate/additional/default、drain；用窄 descriptor 同步 sugar/declaration；按 §1.1 增加服务器运行期 Dynamic Registry 事件 facade（工作名，非最终 API）+ Registry Runtime/Adapter（claim/stale/cleanup/ID/sync/registry surgery），不与启动期 `RegistryEvents.register` 混淆 | runtime member/declaration parity、delivery timing、type conversion、contract/golden、transaction/reload/delete-cleanup、capability/source-trace/smoke、Fabric/1.21.1 capability 显式 | 不受测的手写 declaration、重复 type catalog |
| W7 | `bindings/event`、`wrapper/event`、recipe、client/render/gui、network、PData、command、pack sync | surface contract + platform/client/network Adapter | 按 coverage ledger 逐域迁移；按 §1.1 将 Villager Trades 迁入现有 `ServerEvents` 数据/reload 子事件（第一版 `add` + stable query；`remove`/`replace`/`modify` 延后；平台 mutation 仅 Adapter；Fabric 显式 unavailable，不静默 no-op）；既有 Item/Block modification 按 §1.1 收口为 candidate plan/snapshot owner（票 39）；PostEffects `register`/`unregister` 进入 `ClientEvents` 资源/reload 子事件，`set`/`clear`/`toggle`/`current` 保留 binding；Assets 复用 `ClientEvents.generateAssets`；EntitySelectors 保持 factory/query；已事件化域不重复；原生 callback/mixin/transport 时机留 Adapter | 每行有 contract/golden fixture、transaction/reload/delete-cleanup、capability/source-trace/smoke；Fabric unsupported/partial 必须显式 | 仅在 parity 和删除条件通过后删重复 binding |
| W8 | Fabric bridge source root 和 node properties | `src/fabric` raw loader root + Fabric convention | 迁移 Fabric Java/resources/templates/fixture；更新 CI fixture 消费者；建立 raw/processed/class/jar origin trace；保留防御性 gates | 两 Fabric node 的 source/class/jar/resource/runtime trace 可审阅；fixture 从新路径运行；NeoForge 不挂载 `src/fabric` | `deps.fabric_source_node`、bridge source reference、旧排除规则（仅在 parity + 单独证明后） |
| W9 | NeoForge/Fabric buildSrc conventions、guardLint、processor path、CI 列表 | 现有 two-project/five-node 图 | NeoForge 接线不变；Fabric processor 1.2.0 暂不接，补非 processor 覆盖 gate；CI 按用途消费 manifest；更新过时 Graal lint | 五 node check/artifact、source trace、Fabric artifact verify；CI 子集与 settings 一致；deferred gate 有 owner/输入/输出/失败诊断 | 不新增 Gradle project/API jar，不删 Stonecutter，不改变支持矩阵 |
| W10 | `gradle.properties`、README/wiki、ADR/迁移材料 | `1.2.0` release handoff | 最后改版本；发布矩阵、迁移表、data rollback、contract/golden diff、维护者记录 | 07 release checklist 全绿；无长期 shim/旧 route | 旧 public route、compat shim、无调用者实现 |

**W1/W4/global 实施引用**：[跨 reload 的 global 共享状态如何参与候选事务？](decisions/10-shared-global-candidate-writes.md)。决策 10 已闭合；完整语义只以票内 Resolution 为准。实施时按票内 owner/fixture/迁移要求验证；旧静态 Map/直接注入旁路的删除前提见 proposal 功能覆盖账本，不以文档定案代替验证通过。`shared` 仅为工作名，最终符号在 W5 冻结。

**W1/W2/W4/W6/W7 共同 gate**：candidate/active/generation 可见性、state ownership、owner-thread/重入、失败保留、资源释放和 watchdog 先通过；W6/W7 再叠加 contract/golden、transaction/reload/delete-cleanup、capability/source-trace/smoke。Fabric Villager Trades 的 `unavailable` 与 Dynamic Registry 的 capability 必须显式记录，不能用 no-op 或静默降级代替。性能是独立 P0 baseline，不在 W0 冻结阈值，P4 前依据基线决定。

## 4. W8/W9 物理构建与 CI 接线工作单

> 状态：规划工作单，非源码实施授权；本轮不改 Java/Gradle/Stonecutter/CI，也不要求先完成构建。W8/W9 的规划产物与 B/C 区域的实际实施证据分开记录。详细决策依据见 [决策 03](decisions/03-platform-build-strategy.md)、[决策 07](decisions/07-validation-and-migration.md) 和 [proposal §9](proposal.md)。

### 4.1 已确认口径（保留，不因 W8/W9 改写）

- Fabric `WORLD` pack 保留现状并显式记录 capability 差异，不要求 `1.2.0` parity；本地 GLOBAL/WORLD pack 不新增签名政策。依据见 [proposal §9](proposal.md) 与 [决策 05](decisions/05-runtime-lifecycle-and-data.md)。
- 离线 validator/migration report 可选、默认只读，不是硬 release gate；不得进入普通 runtime 错误路径或成为 Script API 事实源。
- 性能是独立 P0 baseline，环境、预热、重复次数和统计口径在 P0 记录；阈值在 P4 前依据 baseline 决定。不得把性能工作塞进 W0。
- `26.2.0-fabric` node 名称、`deps.minecraft=26.2` MC 坐标和 `nekojs-fabric-26.2-*` 制品命名保持现状；W8/W9 不擅自归一化为 `26.2.0`。
- 决策 03 已选定 raw root + compat/node 方向；这不是新的产品问题，不在本工作单重开。26.2 身份按上一项保持现状，无需重问。

### 4.2 W8：Fabric raw loader root 迁移

**Owner**：Fabric convention + 对应资源/metadata owner；不新增 Gradle project。

**输入**：`settings.gradle.kts:34-43`、`stonecutter.gradle.kts:11`、当前 `buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts:21-25,88-127,148-177,192-278`、`versions/26.1.2-fabric/src/main`、两个 Fabric properties、`.github/workflows/ci-build.yml` 和 baseline manifest。

**动作**：

1. materialize `src/fabric/java/`、`src/fabric/resources/`、`src/fabric/templates/` 和 `src/fabric/test/resources/fabric-runtime-smoke/`。`src/fabric` 是 Fabric convention 显式挂载的 raw loader root；**不声称 Stonecutter 会自动处理它**。
2. 版本差异优先复用现有 compat facade 或 `versions/<node>` node override；若确实需要预处理，另立生成/源唯一性工作项并先证明处理输入、输出和唯一性 gate 存在，不能预设。
3. `guardLint` 的 `fileTree("src")` 会因新 root 自动扩大输入集合；记录扫描文件数、新增文件范围和零违规结果，不修改规则来规避新输入。
4. 将 runtime smoke fixture 移到 `src/fabric/test/resources/fabric-runtime-smoke/`；更新 CI 消费者，至少核对 `.github/workflows/ci-build.yml:312,315-316`，并搜索全仓旧路径。CI 直接 `cp` fixture **不等于** Gradle test source set 已挂载；若测试要消费它，必须显式接 test resources，否则在文档中标明“仅 CI 拷贝”。
5. 将 `fabricSourceRoot` 固定为 `rootProject.file("src/fabric")`；先保留 `java.srcDir`、`resources.srcDir`、access widener、metadata template、JUnit、fat-jar 和 artifact gate。删除 `deps.fabric_source_node` 及其 bridge 引用前，先完成 4.3。
6. 保留 `NeoForge*.java` 防御性 exclude、`fabricForbiddenResourceEntries` 和 `verifyFabricRuntimeArtifact`；删除任何一项都必须另有迁移证明，不能因 source root 收口顺手删除。

**输出**：W8 origin trace、迁移前后 artifact/resource/mixin/metadata 对照、两 Fabric smoke 日志、NeoForge 不挂载 `src/fabric` 的检查结果、fixture 消费者清单。

### 4.3 W8：origin trace、唯一性与 parity

**Owner**：构建证据 owner；输入是上述 raw root、Stonecutter 生成目录、`compileJava` 输入和最终 jar。

**必须分开记录**：

- raw source origin：`src/fabric/java/**`（或迁移前的 bridge root）中的有效源。
- processed source：`build/generated/stonecutter/main/**` 等生成副本；这是生成证据，不是第二源码事实源。
- 编译 class：`build/classes/java/main/**`。
- 打包输入：`Jar` 去重前的 copy/source output 清单。
- 最终 jar：实际 ZIP entries，包含重复计数。

对 7 个同 FQCN（`CommandEvents`、`EntityEvents`、`ItemEvents`、`LevelEvents`、`PlayerEvents`、`ServerEvents`、`client/KeyBindEvents`）分别记录 raw/processed/class/pre-dedup/final-jar 结果。`DuplicatesStrategy.EXCLUDE`、`Set` 存在性、guard 数量或 source count 都不能作为唯一性/parity 证据；必须另做 origin 比对和重复计数。若预处理方案未落实，只能记录“未处理/由 node override 承担”，不能写成已由 Stonecutter 处理。

**通过条件**：两个 Fabric node `compileJava`、`check`、`verifyFabricRuntimeArtifact` 通过；origin trace 无未说明差异；最终 jar 对 7 个 FQCN 的重复计数符合预期；`sandboxCheck` 通过；NeoForge 三节点 `check` 证明不挂载 `src/fabric`。

**删除条件**：只有上述证据齐全，且 `deps.fabric_source_node`、bridge reference 和旧排除规则均有对应替代/证明时，才允许删除；否则回滚 W8，不删除 bridge。

### 4.4 W9：构建、CI 与 Fabric processor 延期 gate

**Owner**：build convention owner 负责接线；Managed Surface/Probe owner 负责契约与声明 fixture；不新增 project、API jar 或第二 node 事实源。

**动作**：

1. 保留 NeoForge `mainSources + :common` 接线；更新过时 Graal lint，保留 MC/loader lint。
2. 确认 1.2.0 **暂不接 Fabric `common-api-processor`**。当前 processor 只接受 `nf26/nf121/cr`，没有 Fabric platform option/scope；不得把“挂上 processor”当成延期替代品。补一份非 processor 的 contract/event/declaration 覆盖 gate 拟实施清单：
   - **contract/spec**：Managed Surface/Probe owner 负责契约与 fixture，build convention owner 负责接线；输入（Normative contract + 声明为 ALL 的 spec）、输出（逐 contract/spec 的 Fabric 覆盖报告）、失败诊断（spec/method/platform 缺口）。
   - **event/surface**：Managed Surface/Probe owner 负责 coverage ledger/contract fixture，build convention owner 负责接线；输入（coverage ledger + contract/golden fixture）、输出（逐域 capability/source trace）、失败诊断（域/事件/缺失 binding）。
   - **declaration**：Managed Surface/Probe owner 负责 declaration fixture，build convention owner 负责接线；输入（runtime member/declaration parity fixture）、输出（Fabric declaration parity 报告）、失败诊断（缺失/多余 member/type）。
   这些只是拟实施 gate 清单，**不等同于已运行验证**；实施前只能记为待办，不能计入 coverage passed。没有测试或证据只能记 `not verified`（未验证）并阻塞该域验收，**不能据此直接写成 `unavailable/partial`**；`supported`/`partial`/`unavailable` 必须按实际语义和证据裁定。若规范 `ALL` 与实际能力不符，显式记录差异并审阅，不能用改表掩盖。也不造全仓 catalog、第二 registry path 或新框架。
3. CI 手写列表按用途校验，而不是做单一等值：NeoForge-only NBT、Fabric-only artifact/smoke、全节点 build、release/publish 子集分别对照 `settings.gradle.kts` 节点图。manifest 是派生快照，不是第二 node 事实源；允许有意子集，但每个子集必须有用途说明和一致性检查。保留 26.2.0 node / 26.2 MC 坐标 / 制品命名原样。

**输出**：五 node check/artifact/source trace、CI 子集一致性报告、Fabric processor 延期说明、非 processor 覆盖 gate 清单（含 owner/输入/输出/失败诊断）。

**不做**：不新增 Gradle project/API jar，不删 Stonecutter，不改变支持矩阵，不在 W8 偷接 Fabric processor。

## 5. 验证顺序

### 已有命令/任务（历史证据，不代表本轮执行）

- `./gradlew projects --console=plain`
- `./gradlew guardLint --console=plain`
- `./gradlew :common:check --console=plain`
- `./gradlew :26.2.0-fabric:check --console=plain`
- `./gradlew help --warning-mode all --console=plain`
- `sandboxCheck` 聚合 `guardLint` 和节点 check；Fabric `check` 已包含 artifact verification。

### W6/W7 搬运域验证口径

- `contract/golden`：验证事件/surface 的有意承诺；普通测试不得更新 golden，重生成需旧/新 diff 和审阅记录。
- `transaction/reload/delete-cleanup`：覆盖注册事务、reload 清理、删除/失败回滚与 stale 状态；失败保留不等于撤销外部副作用。
- `capability/source-trace/smoke`：逐域记录节点 capability、source trace 和 runtime smoke；Fabric Villager Trades 的 `unavailable`、Dynamic Registry 的 Fabric/1.21.1 capability 必须显式。
- 离线 validator/migration report 可选、默认只读，不是硬 release gate。
- 性能是独立 P0 baseline，阈值在 P4 前依据 baseline 决定；不塞入 W0。

### W8/W9 最小验证顺序

1. 记录 bridge 移动前两个 Fabric node 的 raw source、processed source、编译 class、去重前打包输入、最终 jar entries（含重复计数）、metadata、mixin 和 smoke fixture 清单；同时列出旧/新 fixture 路径及全部 CI 消费者。
2. 执行 `guardLint`，确认 `common` MC/loader isolation 未放宽，并记录 `fileTree("src")` 因 `src/fabric` 扩大的扫描输入；新增文件不得靠修改规则规避。
3. 执行两个 Fabric `compileJava`、`check` 和 `verifyFabricRuntimeArtifact`；对 7 个同 FQCN 做 origin trace + 重复计数，不能用 `DuplicatesStrategy.EXCLUDE`、`Set` 存在性或 source count 代替。
4. 执行三个 NeoForge node `check`，证明 NeoForge convention 不挂载 `src/fabric`。
5. 执行 `sandboxCheck`，从 `src/fabric/test/resources/fabric-runtime-smoke/` 执行现有 Fabric server smoke，并确认 CI 旧路径已同步；若该 fixture 供 Gradle test 消费，先完成显式 test resources 接线。下载 remap jar 放入真实 mods 的运行属于 P4，不把 checkout dev run 当作该证据。
6. 对比迁移前后两 Fabric jar/resource/source trace；任一差异没有 capability/迁移说明则回滚 W8，而不是删除 bridge。
7. W9 另核验 CI 子集（NeoForge-only NBT、Fabric-only artifact/smoke、全节点 build、release/publish）与 `settings.gradle.kts` 节点图一致；manifest 仅作派生快照。确认 Fabric processor 未接入，且非 processor contract/event/declaration 覆盖 gate 已列明 owner、输入、输出和失败诊断。

## 6. Release handoff 必备产物

> 下列产物是实施/发布交接证据，不是规划阶段已经生成或通过的证据；获准实施后必须产出，规划阶段只冻结 owner、输入、输出和失败诊断。

- `node/source/artifact manifest`：五节点 source roots、resources、mixins、metadata、processor、能力和 skip。
- `coverage ledger status`：proposal 2.5 每行的 current path、target owner/path、gate 和删除条件。
- `migration table`：每个 Script/Plugin breaking symbol、旧写法、`1.2.0` 新写法、是否有数据影响。
- `data protection inventory`：config、world、entity/player pdata、packs、trust-store、workspace/declaration、logs、cache 的可再生性、备份和 rollback。
- `contract/golden report`：managed、legacy、plugin、registry、packet、diagnostic 的 old/new diff 和审阅记录。
- `release report`：五节点 build/check/artifact、capability matrix、runtime smoke、source trace、维护者四类试做。

任何产物缺 owner、输入、输出、失败诊断或删除条件，都不能标记对应 coverage ledger 行完成。
