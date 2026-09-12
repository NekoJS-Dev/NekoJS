# 测试、文档、CI 与可观测性证据

## 1. 范围与证据口径

- 目标：为大规模重构规划提供测试、开发者文档、CI、本地反馈和诊断证据。
- 范围：common、common-api-processor、共享 src、versions 节点、wiki、docs/adr、CI/build、错误/日志/诊断/堆栈映射。
- 本次实际执行：只读 glob/read/grep；没有执行 Gradle、Node、Minecraft development runtime 或 GitHub Actions。
- “源码存在”：仓库中有测试/任务/实现，但不代表本次运行通过。
- “CI 配置声明”：workflow 定义了任务，但不代表当前快照已有成功运行记录。
- “历史记录/任务配置”：仅表示仓库文档或 workflow 记录的历史或配置事实；不能替代本次运行。
- 不声称当前已有所有 NeoForge runtime smoke；CI 还配置了单个 NeoForge 26.1.2 GameTest server（runGameTestServer），但单节点 GameTest 配置不等于所有节点 runtime smoke 已验证。证据：.github/workflows/ci-build.yml:631-670。
- 决策 07 已确认：本次 breaking 以 `1.2.0` 为唯一 clean cutover；不以兼容 shim 或永久 deprecated 作为默认目标。
- 用户已确认：GraalJS 固定为唯一引擎家族；精确版本不永久冻结。建议每次精确版本升级做专门兼容性评估，当前坐标仅是仓库事实。
- 决策 02 已确认五节点均保留但支持等级不同：NeoForge 26.1.2 primary、26.2.0 secondary、1.21.1 与两个 Fabric node experimental。它们不是同等 parity 承诺，NeoForge/Fabric 能力差异必须显式记录。
- 用户已确认：语言工具链优先自研；新增依赖只考虑小型纯 Java 库，10+MB 或非纯 Java 替代方案不接受，且需单独评估语义可用性。
- 决策 05/07 已确认：普通重排不改格式/path/key；必要 migration 使用备份或原子替换、schema/version、旧 fixture、幂等和失败回滚。具体备份落盘位置与实施窗口仍属于执行记录，不是新的架构票。

### 1.1 本次调查状态

- 本次没有“已跑测试”结果；所有测试数量、覆盖和门禁结论来自源码、Gradle 配置、workflow 或仓库文档。
- CI 中的 NeoForge nbtSmokeTest、Fabric development server smoke 和 JDK21 复跑均是配置证据，不是本次执行证据。
- 现有 Java/Graal 测试是可复用资产；重构前应在新环境中重新执行并记录版本、节点、skip 和耗时。
- 已存在的 golden 是基线资产，不是不可改变的行为圣经；本次 breaking 允许审阅后更新。
- 高风险优先级：loader/runtime parity，其次脚本 surface 与插件合同，再其次迁移保护，最后才是低风险实现细节。
- 每个新增门禁都应说明 owner、输入 fixture、输出 artifact、失败诊断和是否允许平台差异。
- migration policy 与 Fabric 支持等级已由 02/05/07 闭合；性能预算、runner 成本和具体报告格式仍需在 P0 基线中测量，不能被静默假设。
- 证据引用统一使用仓库相对路径和行号，避免把生成目录或历史构建产物误当源码事实。

## 2. 结论摘要

- common 测试密度高，覆盖 API 模型、Probe、编译器、Graal bridge、reload、插件 Point、文件沙盒、事件总线和部分并发。
- 测试资产分散在 common、共享 src/test、processor 和节点任务中，没有一份机器可读的分层/矩阵/skip 清单。
- API manifest、Probe tree、事件 surface、插件 hook pairing 已具备可复用的 contract/golden 形状。
- Fabric 有 jar 内容检查和 development server smoke 配置，但共享测试大多被 NeoForge 守卫排除；不能把 Fabric build/smoke 当作完整 API parity。
- TS/JSX/Python 具备若干 Graal eval 测试，但脚本作者真正使用的 test_scripts/node:test 端到端契约没有仓库 fixture。
- 插件扩展点内部语义测试较好，缺少独立 addon 的真实发现、fat jar 编译依赖、loader 启动、Probe 输出和 reload 验证。
- 数据迁移测试主要停留在 engine config 的废弃键和 legacy read-only fallback；脚本包、日志、世界/持久化数据和缓存没有迁移合同。
- 错误定位核心实现有测试，但 Diagnostics、Java class-load telemetry、错误 packet/dashboard、完整 source-map chain 没有同等级合同测试。
- 文档入口按读者分组，但支持矩阵、测试策略、诊断、迁移和 CI 事实没有单一权威；Fabric 状态存在明显冲突。

## 3. 当前分层

### 3.1 引擎与处理器

- common 使用 JUnit BOM 6.0.0，固定 locale、时区和文件编码；测试运行时补齐日志、Graal、night-config 等实现依赖。
- 证据：common/build.gradle:59-100。
- common:check 依赖 checkCommonIsolation，禁止 common 主源码导入 MC/Forge/NeoForge。
- 证据：common/build.gradle:102-141。
- common 注册 nbtSmokeTest，只运行 nbt-smoke tag；另有 regenerateGoldens 显式入口。
- 证据：common/build.gradle:153-174。
- common-api-processor 必须独立，测试通过 JavaCompiler 内存源码和 processorpath 验证 spec 覆盖。
- 证据：common-api-processor/build.gradle:1-3,27-46；SpecCoverageProcessorTest.java:26-37。
- 静态枚举：common 182 个 Java 测试源、共享 src/test 31 个 Java 测试源、processor 1 个 Java 测试源。
- 这些数字是本次只读枚举，不是测试执行计数；实际发现数仍需由 CI 任务输出确认。

### 3.2 版本树与加载器

- settings 注册 NeoForge 1.21.1、26.1.2、26.2.0，以及 Fabric 26.1.2、26.2.0；后者分别使用 fabric.gradle.kts。
- 证据：settings.gradle.kts:34-43。
- NeoForge convention 挂载共享 src/test 与节点测试 classpath；测试任务启用 JUnit Platform；nbtSmokeTest 只筛 nbt-smoke。
- 证据：buildSrc/src/main/kotlin/nekojs.neoforge-node.gradle.kts:83-89,259-277。
- Fabric convention 也启用 JUnit Platform，并将空测试集设为失败；但共享测试的 loader/版本守卫决定实际发现数量。
- 证据：buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts:166-177。
- CI 主 build job 使用 JDK25，跑 common/processor、三节点 NeoForge NBT smoke、当前比较基线五节点 build；节点保留集以后续支持票为准。
- 证据：.github/workflows/ci-build.yml:92-146。
- CI 另有 JDK21 common/processor 复跑，用于捕获 launcher/toolchain 依赖。
- 证据：.github/workflows/ci-build.yml:369-400。
- 本文不把上述配置等同于本次成功运行，也不主张所有 NeoForge 节点已有完整游戏 server runtime smoke。

### 3.3 API、Probe 与编译器合同

- ApiManifestGoldenTest 冻结 core API manifest；普通运行不会写 golden，只有显式 regenerate property 才写。
- 证据：ApiManifestGoldenTest.java:24-55；common/build.gradle:97-100。
- manifest 测试还检查版本、loader、catalog schema、确定性排序、关键 facade symbol 和至少 110 个 symbol。
- 证据：ApiManifestGoldenTest.java:57-118。
- ProbeOutputCompatibilityTest 检查 legacy 文件保留、内容逐字一致和重复生成确定性。
- 证据：ProbeOutputCompatibilityTest.java:24-73,95-119。
- LegacyProbeTreeTest 还禁止 golden 外的非 managed 产物。
- 证据：LegacyProbeTreeTest.java:20-30,43-81。
- TypeScriptNoopIrGoldenTest 检查未编辑 IR 与旧渲染路径相同，并检查 getter override 在 IR 重渲染中保留。
- 证据：TypeScriptNoopIrGoldenTest.java:36-46,74-115。
- package.json 的 probe typecheck 只对仓库内静态 generated/index.d.ts 执行 tsc，不是运行中 Probe 的端到端产物验证。
- 证据：package.json:4-9；common/src/test/probe-ts/tsconfig.json:1-12；generated/index.d.ts:1-10。
- TS/JSX golden 检查 fixpoint、行结构、Graal parse/eval 和 sourceMap 非空；Python golden 执行 transpiled JS。
- 证据：NekoCompilerGoldenTest.java:12-30,49-100；PythonGoldenTest.java:10-20,40-69。
- TypeScriptErasureParseCorpusTest 覆盖 module parse、行数保持、switch/label/enum 行为。
- 证据：TypeScriptErasureParseCorpusTest.java:13-18,87-132。
- Phase3AFacadeIntegrationTest 在 Graal Context 中验证 ID、Platform、Text、JsonIO、NBT、Registry 和 guest error code。
- 证据：Phase3AFacadeIntegrationTest.java:31-45,68-258。

### 3.4 脚本生命周期、并发和资源

- ScriptReloadRegressionTest 覆盖 unreadable preload、候选 Context 事务提交、timer callback、statement-limit kill 和 teardown 清理。
- 证据：ScriptReloadRegressionTest.java:313-353,355-395,398-444,621-699。
- ReloadMemoryStabilityTest 执行 50 次 reload，每 10 次 GC 记录 heap；阈值是末窗口不超过首窗口 32 MiB。
- 证据：ReloadMemoryStabilityTest.java:41-59,109-170。
- EventBusConcurrentStressTest 用 8 线程、每线程 1000 次 mutation/post，最后断言总线为空。
- 证据：EventBusConcurrentStressTest.java:18-70。
- PlatformConcurrencyTest 覆盖路径 singleton 一次初始化和 Platform.init 竞态 200 轮。
- 证据：PlatformConcurrencyTest.java:29-154。
- NekoJSLoggersConcurrencyTest 验证并发创建同名 logger 只生成一组 appender。
- 证据：NekoJSLoggersConcurrencyTest.java:28-39,72-162。
- 这些是并发回归测试，不是 jcstress、可控调度或稳定性重复门禁；CI 没有统一的 flake/skip 报告。

## 4. NeoForge/Fabric 能力差异证据

| 能力面 | NeoForge 当前证据 | Fabric 当前证据/差异 | 规划含义 |
|---|---|---|---|
| 节点 | 1.21.1、26.1.2、26.2.0 | 26.1.2、26.2.0，26.2 复用 26.1.2 source bridge | 26.1.2 primary、26.2.0 secondary、1.21.1 与两个 Fabric node experimental；五节点暂不 EOL；settings.gradle.kts:37-39 |
| 编译/检查 | 节点 build、common check、NBT smoke 配置 | 节点 build、jar isolation gate、JUnit Platform 配置 | build 不是 runtime parity；workflow:134-146；fabric-node.gradle.kts:210-278 |
| runtime smoke | CI 定义 NeoForge nbtSmokeTest；另有仅 26.1.2 的 runGameTestServer；单节点 GameTest 不等于所有节点完整 server runtime smoke 已验证 | 两个 matrix leg 起 development server，要求 startup/server-started marker | 下一阶段需明确当前比较基线各节点 smoke 语义和真实执行结果，保留集按支持票；workflow:215-367；workflow:631-670 |
| 共享测试 | 大量 src/test 以 NeoForge 守卫运行 | 大多数 TODO(fabric)+整文件守卫被排除 | 必须输出 discovered/skip 名单；EventBusForgeBridgeTest.java:1-3 |
| 事件/注入 | Mixin config、target、extension coverage 和 BlockEvents golden | Fabric 事件/mixin 逐批移植，语义有差异 | 不能复用 NeoForge golden 作为 Fabric 断言；MixinTargetResolutionTest.java:25-43 |
| 错误 UI | NeoForge 代码路径传 ErrorSummaryDTO 到 dashboard | Fabric 命令明确降级为文本，无 UI/network panel | 需要选择 parity 或记录正式差异；FabricNekoJSCommands.java:216-249 |
| 能力缺口 | NeoForge 专属能力按平台测试 | CapabilityEvents、datagen、流体体系、JEI 等列为 Fabric 重新设计/未覆盖 | 用 capability matrix 标注 unsupported，而不是伪造 parity；fabric-port-status.md:320-332 |
| 事件语义 | NeoForge 与原生事件时机/取消语义 | Fabric 有 starting/aboutToStart、damagePre、CLIENT 时机等差异 | script contract 必须测试语义差异；fabric-port-status.md:326-332 |

## 5. 主要缺口

- 没有由 settings 版本图生成或校验的 CI matrix；新增节点需手改 workflow。
- 没有每节点 discovered test、assumption skip、guard 排除的机器可读预算。
- Fabric smoke 当前只检查两个脚本 marker，不是完整脚本 API、Probe、reload、事件或错误合同。
- 没有仓库级 test_scripts fixture 覆盖 node:test；node:test 只有资源实现和类型抽取测试。
- NekoTypeScriptCompilerTest 明确把运行合法性委托给游戏内 /nekojs test，形成 CI 盲区。
- 没有真实外部 addon fixture 验证 loader discovery、fat jar 编译依赖、hook、Point、Probe 和 reload。
- API manifest 只冻结 core surface；平台 bindings/events/adapter 的全量脚本表面没有跨 NeoForge/Fabric golden。
- 数据迁移只验证 engine config 废弃键和旧位置只读 fallback。
- 旧 logs/nekojs/*.txt 移动到 .log 的实现没有测试；ScriptTypeEnv.java:41-64。
- ScriptPack manifest/state 有解析、启用、排序、去重测试，但没有 schema/version migration 或回滚测试。
- 没有 world/persistent data、server cache/trust store、generated declaration 的版本升级/降级 fixture。
- PerformanceFacade/PerfTimer 只有计时 API 语义，没有启动、probe、reload、tick、adapter 的预算。
- 没有 Diagnostics、JavaClassLoadTelemetry、ErrorSummaryDTO/ShowErrorListPacket 的 contract 测试。
- StackTraceMapper 没有直接单测；TS/JSX/Python 多段 source-map chain 没有错误输出 golden。
- 没有 NeoForge/Fabric dashboard/command/network runtime integration test。

## 6. 重构前测试资产

### Characterization

- 记录当前五节点比较基线的实际 test class/count/skip/assumption、Graal 坐标、jar class/resource 清单和 runtime 日志；后续按支持票批准的保留节点更新。
- 为 startup/server/client/test 建最小真实脚本 fixture，覆盖 JS、TS、JSX、ESM、CJS、Python、TLA、dynamic import 和 node:test。
- 记录绑定、事件、取消、dispatch、side visibility、reload、context kill、error snapshot、Probe output 和 editor config。
- 记录旧 engine config、旧 .txt 日志、pack manifest/state、world/cache 输入；按 07 的备份/原子替换、旧 fixture 和回滚要求验证，不覆盖原始输入。
- 所有记录进入只读 artifact；不要把 characterization 运行自动写回源码 golden。

### Contract

- core API manifest：symbol、signature、capability、module、version metadata。
- script API：每个 ScriptType 的可见/不可见 globals、events、bindings、adapter 输入、错误码和取消语义。
- Java plugin：NekoJSPlugin hook 签名、Point id、dependsOn、merge、freeze、handle/result 类型。
- ProbeBackend：catalog snapshot、IR、render path、outputDir、editor config、失败保留旧产物。
- loader contract：NeoForge/Fabric capability 和正式 unsupported 语义；不把差异隐藏在同一 golden。
- packet/diagnostic：DTO 字段、fullDetails 上限、权限、空列表、错误映射和客户端降级。

### Golden

- 保留现有 API manifest、Probe legacy tree、IR no-op tree、事件 surface golden 作为资产。
- 本次 `1.2.0` breaking 允许审阅后的 golden 更新；更新必须显示旧/新 diff、原因、迁移影响和版本号。
- 新增每节点 .d.ts/.pyi、完整事件目录、capability matrix、error detail/stack map、jar isolation 清单。
- Golden 生成命令必须显式开启；CI 普通测试禁止写基线。

### Smoke

- NeoForge：每个节点至少编译、测试、NBT smoke；`1.2.0` release gate 还要求记录声明的 runtime smoke，当前 evidence 不把配置当作已执行结果。
- Fabric：每个节点运行 startup/server fixture，并逐步加入 binding、event、reload、Probe、error 文本路径。
- 每个节点检查 Graal Context 创建、脚本错误可见性、loader-specific artifact 禁止清单。
- smoke marker 应包含版本、loader、Graal build、脚本阶段和断言摘要，失败上传完整日志。

## 7. 文档现状与信息架构

- README 同时承担产品定位、语言/runtime、安全、插件、事件、目录和源码结构，入口过宽。
- Wiki sidebar 已按脚本作者、整合包作者、插件开发者、项目贡献者分组，但没有测试/CI/迁移/可观测性页。
- ADR-0009 只有高层发布和门禁策略，不能替代测试矩阵和验收表。
- README 写 Fabric 已有 CI runtime smoke；wiki/Home 仍写 Fabric 正在移植；fabric-node-expansion 仍写测试树不启用；fabric-port-status 写 smoke/test 已收口。
- wiki/插件开发同时存在“注解式文档规划中”和“注解式/编程式两条路可用”的矛盾。
- 维护者入口建议：system map、test strategy、version/loader matrix、local loop、CI/release、observability、data migration、ADR index。
- Java 插件作者入口建议：getting started、fat jar dependency reality、hook/Point matrix、Probe/type docs、addon fixture、loader portability、breaking policy。
- 脚本作者入口建议：support matrix、lifecycle/reload、JS/TS/JSX/ESM/CJS/Python、generated API reference、events/bindings/recipes、node:test、errors、migration、安全。
- README 只保留定位和入口；脚本 API 参考应由 Probe/manifest 生成，避免手抄漂移。
- 支持矩阵必须唯一且可从 settings/Gradle 事实源校验；当前五节点的 primary/secondary/experimental 等级以 02 Resolution 为准，能力细项由 07 的 capability matrix 承载。

## 8. CI 与本地反馈

- PR fast loop：guardLint、common unit/contract、processor:test、静态 Probe tsc。
- 受影响节点 loop：对应 NeoForge/Fabric test、build、jar isolation；合并门禁按 02 的五节点等级和 07 的 declared capability gate 执行。
- full loop：Stonecutter 重新求值、全节点 check、NBT smoke、脚本 smoke、Probe artifact、migration fixture。
- workflow 应从 settings 版本图生成或校验节点清单；当前是手写列表，workflow:142-146。
- CI 应在成功和失败都上传 JUnit XML/HTML、Probe diff、jar manifest、runtime log、skip summary；当前测试报告只在 failure 上传，workflow:201-213。
- 每个节点输出 discovered/skip/count/time；关键 loader 测试被 guard 排除时必须显式显示。
- sandboxCheck 已定义为 guardLint + 所有节点 check，可作为统一门禁；stonecutter.gradle.kts:249-260。
- local docs 应给出单测选择器、节点 test、runtime smoke、golden review 和 migration fixture 命令，不只列 build。
- Gradle.properties 固定本机 Windows org.gradle.java.home，CI 用 sed 删除；维护者体验应改为 documented JAVA_HOME/toolchain bootstrap，gradle.properties:14-18；workflow:116-119。
- GraalJS 版本策略固定引擎家族；精确版本坐标可演进，升级前建议专门评估兼容性，不把当前坐标写成永久用户确认；libs.versions.toml:21-29；fabric-node.gradle.kts:57-63。
- 语言工具链继续优先自研；小型纯 Java 依赖要单独评估语义、包体和维护成本，禁止用非纯 Java 或 10+MB 工具替换核心语义。
- 并发/性能用独立 soak/benchmark 任务，记录启动、probe、reload、tick、adapter、heap；不要把不稳定阈值混入普通单测。

## 9. 分阶段验收门槛

- P0 基线：静态矩阵、coverage ledger、golden、jar、Graal、错误和迁移输入可复现；普通运行不写基线。
- P1 runtime owner：共同装配、单一 `NekoRuntimeRoot`、startup/CLIENT/afterInit、reload/close、错误阶段和资源释放通过。
- P2 runtime domains：Plugin Runtime、Preparation/Resolution/Execution、Managed Surface、Registry Runtime 的 contract、corpus、source-map 和 parity gate 通过。
- P3 feature/platform parity：事件、recipe、client/UI/render、network/PData、command、diagnostics、pack trust 按 coverage ledger 逐域完成；unsupported/partial 显式记录。
- P4 cutover/release：兼容桥删除条件、迁移表、data rollback、全节点 artifact/runtime evidence、维护者四类试做和 `1.2.0` 文档一致性全部通过。

## 10. 实施输入与未来重评

- 五节点等级已由 02 确定；每项 capability 的 `supported`/`partial`/`unavailable` 由 07 的 matrix 和 P3 证据填写。
- GraalJS 坐标可演进但引擎家族固定；精确升级必须有兼容性评估。
- Stonecutter 当前保留并收紧；退出只在五节点等价 PoC、IDE/build/jar/runtime 和真实新版本接入证据齐全后重评。
- 公开 contract、Plugin Point/Hook/Handle、Probe、诊断字段和外部 addon fixture 由 P2/P3 作为实施输入冻结；不是本证据报告另立规范。
- 性能预算、runner 成本、telemetry 展示和具体备份窗口在 P0 基线与 P4 release report 中记录，不能改变已关闭的架构票。
- 本文件仅承载证据与验收建议；实施结果应回写对应的 release report、migration table 和维护者记录。
