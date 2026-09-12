# NekoJS 架构重构：获准实施后的执行说明（给下一个执行 AI）

## 0. 你的任务

仓库：`D:\mcmodDemo\NekoJS\NekoJS-mult`（Minecraft 多版本多加载器 mod，GraalJS 脚本引擎，类 KubeJS）。

> **先取得实施授权。** 本说明只定义获准后的执行方式，本身不构成授权。必要规划决策 00-10 已全部闭合，规划已完成并可交接；本轮只完成文档收口，无 Java/Gradle/Stonecutter/CI 修改，也未运行构建/测试。
获得维护者明确实施授权后，先按 [实现票据索引](implementation-tickets/README.md) 的 metadata、`Blocked by` 和验收规则认领执行；W0-W10 只是物理迁移/交接辅助视图，不替代票据依赖图。不要重新规划、重新设计或重问已决选择。未获授权时，不得开始源码实施。

唯一权威来源（先读，再动手）：

- [实现票据索引](implementation-tickets/README.md)：**正式认领、依赖、验收和关闭入口**
- `docs/architecture-refactor-map.md`：路线图索引与全局约束
- `docs/architecture-refactor/implementation-handoff.md`：实施交接单，W0-W10 物理迁移/辅助视图
- [决策票导航](decisions/README.md)：本次全部已闭合决策的 Resolution（不可违反）
- [完整规格索引](specs/README.md)：全部决策的一一对应 spec；先按功能域读取用户故事、实施约束与测试要求
- [跨 reload 的 global 共享状态如何参与候选事务？](decisions/10-shared-global-candidate-writes.md)：已闭合；global 的完整语义以票内 Resolution 为唯一权威来源。`shared` 仅为工作名，最终符号在 W5 冻结。
- `docs/architecture-refactor/proposal.md`：候选方案、2.5 功能覆盖账本、迁移阶段
- `docs/architecture-refactor/evidence/*.md`：源码证据（含行号）
- `docs/architecture-refactor/planning-completion-checklist.md`：规划完成与实施验收清单；清单状态不等于实施授权

## 1. 不可违反的约束（来自已关闭决策票）

1. **优先级**：维护者 > Java 插件作者 > JS/TS 脚本作者。
2. **一个运行时 owner**：只有 `NekoRuntimeRoot`；不得新增 `RuntimeKernel`/`RuntimeGateway`/`RuntimeAssembly`/`ServiceLocator` 叠层。共同装配写成函数或工厂即可。
3. **物理结构保守**：保留 `common`、`common-api-processor` 和五个 Stonecutter node；**不新增 Gradle 子项目、不拆 API jar**，除非有真实发布/依赖/独立测试收益。
4. **依赖方向**：`common`（含 `api.*`）**禁止 Minecraft/loader import**；`common` **允许** GraalJS。允许 MC/loader 的共享实现放根 `src/` 或 node。
5. **GraalJS 与高级 Java 访问不收紧**：`java:`、`Java.type`、`Java.loadClass`、HostAccess 语义保持；不得以“契约纯化”为由偷偷收紧。
6. **事实源分离**：managed contract（`NormativeApiContract`）是规范源；legacy preview / manifest / Probe / declaration 是派生物；Point 是插件扩展事实源。不得造全仓万能 catalog 或第二注册路径。
7. **reload 语义**：普通 reload 只切换脚本环境和模块 session；失败时保留 NekoJS 自有的旧 runtime/state；**不重启 Plugin Runtime、不重复注册平台事件/registry/network**；不承诺撤销脚本对外部对象造成的副作用。
8. **数据保护**：默认不改 config/world/pdata/pack/trust-store 的路径、格式、key、wire id；必要迁移必须备份或原子替换、带版本标记、旧 fixture 可回读、失败可回滚。
9. **语言全保留**：JS/ESM/CJS、TS/JSX/TSX、Python 都保留；纯 Java 自研优先，不引入非纯 Java 转译依赖。
10. **节点集合**：NeoForge `26.1.2` primary、`26.2.0` secondary、`1.21.1` 与两个 Fabric 为 experimental；**五个节点都不退役**。
11. **发布**：完成时 mod 版本从 `1.1.0-preview3` 改为 `1.2.0`，只做一次 clean cutover；不保留长期兼容 shim。
12. **搬运功能适配（决策 08）**：只有明确生命周期、多个脚本/插件贡献、注册/reload/事务提交语义的公开面进入 Managed Surface 事件面；factory/query/runtime command/send 保留 binding/Adapter；不造万能 Event Module、第二 registry path 或无真实生命周期的 Point。Villager Trades、Dynamic Registry、PostEffects 等按决策 08 的现有事件/Registry Runtime 路径规划，未冻结最终 API。
13. **验证口径**：离线 validator/migration report 可选、默认只读，不是硬 release gate；性能是独立 P0 baseline，阈值在 P4 前依据 baseline 决定，不在 W0 冻结。
14. **reload 运行时契约（决策 09）**：candidate/active 按 generation 隔离；Plugin Runtime/Handle 进程级、session object 按 generation 管理；按 ScriptType owner thread 串行 reload/close；watchdog 不自动创建第二个 active runtime。

## 2. 目标架构（逻辑模块与物理落点）

逻辑模块（common 内，不自动拆成 Gradle 项目）：

- Plugin Runtime：Point/Hook/Contributor/Handle、dependsOn、freeze、拓扑与产物
- Script Preparation：源码 -> prepared module（语言、mode、IR、source map、cache key）
- Module Resolution/Cache：CJS/ESM identity、require/import/link、失效
- Script Execution Environment：Graal Context、sandbox、bindings、session、timer/listener
- Managed Surface：Normative contract -> frozen surface -> manifest/Probe
- Registry Runtime：metadata、type、repository、builder、drain
- Pack Trust / Data Protection：本地发现、远端验签、缓存、持久化保护

物理落点：

| 位置 | 放什么 |
|---|---|
| `common/src/main/java/.../api/` | 作者入口、managed/plugin 契约、必要 Graal facade（零 MC/loader） |
| `common/.../core/`、`script/`、`eventbus/`、`probe/` | 跨 loader 引擎实现 |
| 根 `src/main/java` + `resources` | 共享 MC-facing wrapper/binding/registry/builder |
| `src/fabric/` | 两个 Fabric 26.x 节点共用的 loader source root（只由 Fabric convention 挂载） |
| `versions/<node>/src/main/` | 无法用 Adapter/facade 表达的节点差异 |

## 3. 实施顺序（P0-P4 与 W0-W10）

**P0 基线** → **P1 单一 runtime owner** → **P2 runtime 域迁移** → **P3 功能域与 loader parity** → **P4 清理与 1.2.0 发布**。
P0-P4 不各自产生 public breaking。

| ID | 内容 | 关键通过条件 |
|---|---|---|
| W0 | 生成五节点 source/artifact manifest（source roots、resources、mixins、metadata、processor、能力、test skip） | manifest 可复现，能力矩阵一致 |
| W1 | 统一两 loader 装配，单一 `NekoRuntimeRoot`，移除 static root 旁路 | startup/CLIENT/afterInit/reload/close/失败保留/清理 parity；global 按已闭合决策的 fixture/迁移要求验证；删除前提见功能覆盖账本 |
| W2 | Plugin Runtime 收拢，保留 Point/Handle/dependsOn/freeze | pairing、排序、freeze、错误、reload、外部 addon fixture |
| W3 | Preparation + Module 显式注入 pipeline/cache | corpus、golden、source location、缓存失效、Graal 执行 |
| W4 | Execution Environment 集中 Context/sandbox/session | 失败保留、资源释放、trust 审计；global 按已闭合决策的 fixture/迁移要求验证；删除前提见功能覆盖账本 |
| W5 | Managed Surface：规范源唯一、其余派生 | contract/golden、legacy characterization、Probe parity |
| W6 | Registry Runtime + 窄 descriptor 同步 sugar/declaration；含运行期 Dynamic Registry 事件 facade（工作名，非最终 API）+ Registry Runtime/Adapter | runtime/declaration parity、drain 时机、contract/golden、transaction/reload/delete-cleanup、capability/source-trace/smoke、loader 能力 |
| W7 | 事件、recipe、client、network/PData、command、诊断、pack 逐域迁移；含搬运功能适配（Villager Trades、PostEffects、Assets、EntitySelectors）与既有 Item/Block modification（[票 39](implementation-tickets/39-item-block-modification.md)） | 每域 contract/golden fixture、transaction/reload/delete-cleanup、capability/source-trace/smoke |
| W8 | Fabric raw loader root 去 source bridge（详见下节与 implementation-handoff §4-§5） | 两 Fabric node source/class/jar/资源/运行时 trace；fixture 新路径；NeoForge 不挂载 |
| W9 | 构建、CI 子集、guard 与 Fabric processor 延期 gate | 五 node check/artifact；CI 子集一致；非 processor 覆盖 gate 有 owner/输入/输出/失败诊断 |
| W10 | 版本改 `1.2.0`、文档与迁移表 | 07 release checklist 全绿、无长期 shim |

### 搬运功能适配（W6/W7，Q2-Q5 已确认口径，尚未实施）

以下内容只同步方向，不冻结最终事件名、payload、API signature 或 Fabric parity。

- **事件化边界**：只有明确生命周期、多个脚本/插件贡献、注册/reload/事务提交语义的公开面进入 Managed Surface 事件面；factory、query、runtime command、send/action 保留 binding/Adapter；不造万能 Event Module、第二 registry path，也不为没有真实生命周期的功能制造 Point。
- **W6 / Dynamic Registry**：公开服务器运行期动态注册事件 facade（工作名 `ServerEvents.dynamicRegistry`，非最终 API）+ Registry Runtime/Adapter；Adapter 负责 claim/stale/cleanup/ID/sync/registry surgery；不与启动期 `RegistryEvents.register` 混淆；Fabric/1.21.1 capability 显式。
- **W7 / Villager Trades**：迁入现有 `ServerEvents` 数据/reload 子事件；第一版只规划 `add` + stable query，`remove`/`replace`/`modify` 延后；平台 mutation 仅 Adapter；Fabric 显式 `unavailable`，不能静默 no-op。
- **W6/W7 / Item/Block modification**：保留既有 `ItemEvents.modification` 与 `BlockEvents.modification`，把修改声明收口为 inert candidate plan，snapshot/restore 由单一 runtime/domain owner 管理；详细验收以 [票 39](implementation-tickets/39-item-block-modification.md) 为准。
- **W7 / PostEffects 与复用**：`register`/`unregister` 进入 `ClientEvents` 资源/reload 子事件，`set`/`clear`/`toggle`/`current` 保留 binding；Assets 复用 `ClientEvents.generateAssets`；EntitySelectors 保持 factory/query；已事件化域不重复。
- **共同 gate**：contract/golden、transaction/reload/delete-cleanup、capability/source-trace/smoke 缺一不可；Fabric unsupported/partial 必须显式记录。

## 4. W8/W9 迁移工作单（获准后按基线与 gate 执行）

> 完整 owner、输入、输出、失败诊断和删除条件以 [implementation-handoff §4-§5](implementation-handoff.md) 为准；决策依据见 [决策 03](decisions/03-platform-build-strategy.md)、[决策 07](decisions/07-validation-and-migration.md)，global 的完整语义见 [跨 reload 的 global 共享状态如何参与候选事务？](decisions/10-shared-global-candidate-writes.md)；W1/W4 按票内 owner/fixture/迁移要求与功能覆盖账本的删除前提验证。本节只保留执行顺序和不可违反口径。

### W0：只读基线（不改代码）

产出五节点清单：每个 node 的 source roots、resources、mixins、metadata、processor path、声明的能力、测试 skip/assume、生成物 source trace。参考事实：`settings.gradle.kts:34-43`、`stonecutter.gradle.kts:1-68`、五个 `versions/*/gradle.properties`。

### W8：Fabric raw loader root 收口

1. **raw root**：`src/fabric` 只由 Fabric convention 显式挂载；不声称 Stonecutter 自动处理。版本差异优先走现有 compat/node override；若需预处理，另立生成/源唯一性 gate，不能预设存在。
2. **输入扩展**：`guardLint` 的 `fileTree("src")` 会纳入新 root；记录扫描文件数、新增范围和零违规，不靠改规则规避。
3. **fixture**：迁入 `src/fabric/test/resources/fabric-runtime-smoke/`，更新 CI 消费者（`.github/workflows/ci-build.yml:312,315-316` 及全仓搜索结果）。CI `cp` 不等于 Gradle test source set 已挂载；测试消费需显式接线，否则标明“仅 CI 拷贝”。
4. **origin trace**：raw source、processed source、编译 class、去重前打包输入、最终 jar entries 分开记录；7 个同 FQCN 单独做 origin 比对和重复计数。`DuplicatesStrategy.EXCLUDE`、`Set` 存在性、guard/source count 都不是唯一性或 parity 证据。
5. **防御性 gate**：保留 `NeoForge*.java` exclude、`fabricForbiddenResourceEntries`、`verifyFabricRuntimeArtifact`；删除必须单独证明。
6. **删除条件**：两 Fabric `compileJava/check/verifyFabricRuntimeArtifact`、`sandboxCheck`、三 NeoForge `check`、新 fixture smoke 和迁移前后 trace 全通过后，才可删除 `deps.fabric_source_node` 与 bridge；否则回滚。

### W9：构建、CI 与 Fabric processor 延期 gate

1. **processor**：1.2.0 暂不接 Fabric `common-api-processor`；当前 processor 只接受 `nf26/nf121/cr`，没有 Fabric option/scope。不得把“挂上 processor”当作延期替代品，也不改变支持矩阵。
2. **非 processor 覆盖 gate**：build convention owner 负责接线；Managed Surface/Probe owner 负责 contract 与 declaration fixture。为 contract/spec、event/surface、declaration 各列 owner、输入、输出和失败诊断；这是拟实施清单，不等于已运行验证。没有测试或证据只能记 `not verified`（未验证）并阻塞该域验收，不能直接写成 `unavailable/partial`；`supported`/`partial`/`unavailable` 按实际语义和证据裁定，规范 `ALL` 与实际不符须显式记录差异并审阅。此清单是验收表，不造 catalog/第二 registry path。
3. **CI 子集**：NeoForge-only NBT、Fabric-only artifact/smoke、全节点 build、release/publish 分别按用途与 `settings.gradle.kts` 节点图一致性校验；manifest 是派生快照，不是第二 node 事实源。
4. **保持原样**：26.2.0 node、`deps.minecraft=26.2` MC 坐标和 `nekojs-fabric-26.2-*` 制品命名不改，无需重问；raw root + compat/node 是决策 03 已选方向，不是新产品问题。不新增 Gradle project/API jar，不删 Stonecutter。

**已确认口径**：Fabric `WORLD` pack 保留现状并显式记录 capability 差异，不要求 1.2.0 parity；离线 validator/migration report 可选、默认只读、非硬 gate；性能是独立 P0 baseline，绝不在 W0 冻结。详见 [proposal §9](proposal.md)。

## 5. 验证要求

已有可用任务/命令：

- `./gradlew projects --console=plain`
- `./gradlew guardLint --console=plain`
- `./gradlew :common:check --console=plain`
- `./gradlew :26.2.0-fabric:check --console=plain`
- `sandboxCheck`（guardLint + 各节点 check）

验证口径：离线 validator/migration report 可选、默认只读，不是硬 release gate；性能是独立 P0 baseline，阈值在 P4 前依据 baseline 决定，不在 W0 冻结。W6/W7 证据仍为 contract/golden、transaction/reload/delete-cleanup、capability/source-trace/smoke。

W8 gate：记录 raw/processed/class/去重前打包输入/最终 jar（含重复计数）→ `guardLint`（记录 `fileTree("src")` 输入扩展）→ 两 Fabric `compileJava/check/verifyFabricRuntimeArtifact` → 7 个同 FQCN origin 比对 + 重复计数 → 三 NeoForge `check` → `sandboxCheck` → 从新 fixture 路径跑 Fabric smoke。`DuplicatesStrategy.EXCLUDE`、`Set` 存在性、guard/source count 不构成唯一性或 parity 证据；checkout dev run 不等于下载 remap jar 放入真实 mods，后者属于 P4。

W9 gate：五 node check/artifact/source trace；CI 的 NeoForge-only、Fabric-only、all-node、release/publish 子集逐类对照 `settings.gradle.kts`；manifest 只作派生快照。Fabric processor 1.2.0 保持不接入；build convention owner 负责接线，Managed Surface/Probe owner 负责 contract 与 declaration fixture，非 processor contract/event/declaration 覆盖 gate 必须列明输入、输出、失败诊断。无证据的 `ALL` spec 记 `not verified` 并阻塞该域验收，不直接改判 `unavailable/partial`，也不改变支持矩阵。

## 6. 禁止的捷径

- 不用 Graal 允许规则绕过 `common` 的 MC/loader 隔离。
- 不为目录整齐把 MC 类型搬进 `common`。
- 不用 catalog/capability registry/万能 declaration DSL 收拢所有域。
- 不在普通 reload 里重新 bootstrap Plugin Runtime。
- 不把单节点 build、startup marker 或 Fabric source bridge 宣称为 parity。
- 不先改版本号、数据格式、wire id、公开 FQCN 或节点集合再补测试。

## 7. 完成定义

每个 W 项完成需同时具备：owner、当前路径、目标路径、验收 gate、删除条件、证据产物。
缺少任一项时，不得声称该域迁移完成，也不得删除旧实现。

最终 `1.2.0` 发布前必须存在：五节点 build/check/artifact 报告、capability matrix、contract/golden diff、语言 corpus 与 source-map 报告、runtime smoke、数据迁移与回滚 fixture、维护者四类试做记录、脚本/插件迁移文档。这些是实施/发布证据，不是规划阶段已经生成或通过的证据；获准实施后必须产出。
