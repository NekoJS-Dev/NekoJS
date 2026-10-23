# 票 28 / 37 后续工程收尾

2026-10-23 最新本地阶段：`6e7452b7` [完整类泛型约束](../architecture-refactor/evidence/2026-10-23-class-bounds/README.md)通过5146tests完整检查与五节点新安装正常退出；1609默认stub通过，完整严格TSC为5565（移除10/新增0），仍失败。票28工程closed；票37、340.0/292.8ms重载性能、最终候选客户端/TCP/分离回退及维护者结论继续开放。未发PR、push或发布。

2026-10-23 新源码 `88e18d84` 的[五节点新安装与完整 IDE](../architecture-refactor/evidence/2026-10-23-registry-installed/README.md)全部运行通过、五进程正常退出；1609 个默认 Python stub 检查零错误/警告。完整严格 TSC 为 5619 条、十入口 exit2 无崩溃，相对 5857 移除244/新增6，继续 FAIL；新增为 Fabric 擦除/继承关系的进一步诊断，未隐藏或放宽。该批新 JAR 与全部编译类/源码绑定，最终客户端/TCP/回退及正式性能仍按原范围开放。

2026-10-23 注册表目录源码 `88e18d84` 的[完整检查](../architecture-refactor/evidence/2026-10-23-registry-catalog/README.md)已通过：5140 tests / 271 skipped / 零失败错误，含 common isolation、处理器、guard 和五节点 build/platform/artifact；48 项定向回归零失败/跳过，full/server 严格消费、Python Literal 与完整定向 TS 链通过，两轴复审零剩余项。新安装全图 IDE 随后独立完成，5619 条完整严格诊断继续失败，详见上方最新结果。[票 28 工程收口](../architecture-refactor/evidence/2026-10-23-posteffects-closeout/README.md)确认自身十项条件已满足，已改为 closed；精确追认和已验收组件不变，不是新增整票发布签字。票 37 及完整严格 TSC、重载性能、最终整批候选门禁保持开放。

2026-10-23 后续修复 `c0bc2dbe` 的[raw 泛型 TS 输出](../architecture-refactor/evidence/2026-10-23-raw-generic-output/README.md)通过先红后绿回归、三模式严格消费、完整定向 TS 链与 common/check（2420 tests / 4 skipped / 零失败错误，含 isolation）；guard 零警告，两轴审查无剩余项。五节点新安装全图声明与性能尚未据此重测，保持旧完整 TSC 和性能失败结论；下一项为实际适配器与 RegistryTypes 目录的遗漏/名称不一致，见[共享原因与实现范围](2026-10-23-reload-declaration-findings.md)。

2026-10-23 生产修复 `b47734c6` 已完成本项工程复验：[失败注册的 intrusive holder 清理](../architecture-refactor/evidence/2026-10-23-intrusive-holder-fix/README.md)保留旧 pending、清理本次新增未绑定 identity，保留 null/frozen 与异常归因。旧实现真实 refreeze 4 中 3 fail；四现代节点 16 回归全过，guard 零警告，两轴审查无剩余项。[四节点完整 build](../architecture-refactor/evidence/2026-10-23-holder-modern-builds/README.md)通过（2212 tests / 236 skipped / 零失败错误）。确切同批 JAR 的[两节点真实 TCP 七阶段](../architecture-refactor/evidence/2026-10-23-dynamic-commit-recovery/README.md)全过、六进程正常退出，覆盖原故障点、断线、持续故障重连拒绝、移除故障同进程恢复及再次双提交。完整 TSC、重载性能与最终统一候选验收仍开放，票保持 in-progress。

2026-10-23 事件依赖版本 `1f877b0f` 的[五节点新安装与完整 IDE](../architecture-refactor/evidence/2026-10-23-event-installed/README.md)已封存：五服务器全部行为 PASS、正常退出 0，1609 个新 stub 的默认 Pyright 全通过；十入口完整严格 TSC 为 5857（旧版 5867），均 exit2、无崩溃，继续 FAIL。此批制品在 holder 修复前独立冻结，不冒充随后新源码的最终统一候选。

2026-10-23 真实 TCP 的前置失败：`1f877b0f` 的 26.1.2 在 COMMIT 后单客户端物理激活失败、在线 STATE_SYNC 再次失败时保留旧条目；但随后断线因两个未清理的 intrusive holder 崩溃。[失败证据](../architecture-refactor/evidence/2026-10-23-dynamic-commit-red/README.md)保留前三次尝试，原结果仍为 NOT GREEN；随后共享修复及新源码复验见上，不覆盖或改写旧失败。事件依赖的[五节点普通 platform gate 与两个现代 NeoForge 构建](../architecture-refactor/evidence/2026-10-23-event-node-followup/README.md)已通过，派生十文件只增加实际交易事件 import，提交 `a494da3a`；不将构建通过外推为联网或整票通过。

2026-10-23 后续局部修复 `1f877b0f`：[实际事件签名依赖](../architecture-refactor/evidence/2026-10-23-event-dependencies/README.md)交给现有 TS 闭包，保持 exclude/hide，不收集未输出的 dispatch key。两项回归先红后绿；最终 common/check、隔离及 guard 通过（2416 tests / 4 skipped / 零失败），完整定向 `test:probe-types` 通过；两轴复审最终零未解决项。新源码尚未重新绑定五节点完整安装 IDE、正式性能或最终客户端/TCP/回退，票仍 in-progress。[加载细分诊断](../architecture-refactor/evidence/2026-10-23-loader-preparation-diagnostic/README.md)完成两轮十次 COMMIT 与正常停止，未据小占比缓存或改变初始化次序换取通过。

2026-10-23 当前生产源码 `7bea6d6d` 的[完整新候选检查](../architecture-refactor/evidence/2026-10-23-current-candidate-checks/README.md)已完成：common/check、处理器、guard 与五节点构建通过，5070 tests / 271 skipped / 零失败；五个新安装服务端实际运行全部通过并正常退出 0，包含 26.1.2 实体查询及重载前后效果。测试服显式禁用空服暂停，旧候选首次失败仍未确诊。全部 1609 个新 stub 的默认 Pyright 通过；十入口新生成声明的完整严格 TSC 仍为 5867 条、无崩溃，继续 FAIL。[确切同一 26.1.2 JAR 的正式性能](../architecture-refactor/evidence/2026-10-22-source-facts-performance/README.md)仍为重载 340.0 / 292.8 ms 失败、启动 33486.6 ms 通过。后续最终候选的客户端/TCP/分离回退和真实维护者结论保持各自范围，未因本轮构建及服务器成功而关闭。

2026-10-22 阶段候选 `4405b0b0`：全量 5067 tests / 271 skipped / 0 failures or errors；[统一三节点安装客户端](../architecture-refactor/evidence/2026-10-22-unified-client-native/README.md)完成 30 图及六次正常退出；[制品/世界数据分离回退](../architecture-refactor/evidence/2026-10-22-candidate-rollback/README.md)完成四阶段。五节点安装首轮仍有 26.1.2 实体查询未过，保持 NOT GREEN 并单独运行控制实验；完整 TSC 仍失败，性能和维护者结论未因此通过。

2026-10-22 追加：[真实 TCP 封存](../architecture-refactor/evidence/2026-10-22-dynamic-tcp/README.md)完成 `d91ca974` 的 26.1.2/26.2.0 两节点、各八阶段及八次正常进程退出；含真实 ACK/NACK、配置补同步、超时/迟到 ACK、准备断线和重连后注册表正负结果。历史失败夹具及一次精确强制清理完整保留，成功运行均无强制清理。后续源码的统一候选、COMMIT 后激活失败、完整 TSC、最终性能和分离回退仍需完成。

用户于 2026-10-21 授权尽可能完成剩余项。起点为 stonecutter `495d9cb8`；主代理实现、测试、游戏验证与提交，Luna xhigh 只读调查，不创建 PR、不推送或正式发布。保留工作区既有两份修改声明与其他无关文件。

## 执行顺序与边界

1. 按完整严格 TSC 的诊断归因修复共享原因，保留真实 Java/Graal 行为、公开成员、编辑后 IR 和依赖限制。先处理多父接口继承签名，再核对其他高重复原因。6867 是本轮起点的十入口结果；最新测量见上方逐批记录，不混作当前源码数据。
2. 补 PostEffects 的 26.1.2 / 1.21.1 原生 render-target 首帧、资源重载与失败保留。沿用现有 resources-only / unavailable 能力边界，不扩大任意 inline id 激活承诺。
3. 通过真实独立服务端和客户端核验 Dynamic Registry 的 prepare/ack/commit、状态同步与 registry surgery；脚本包签名同步不替代该链路，默认安全门禁保持。
4. 整合确切本地候选、迁移/支持矩阵与分离的制品/数据回退。已获批准的 A15/A16/A22、五处 Entity、A28 和票 36 试做不重复请求；未取得的最终发布/回滚结论仍须真实维护者确认。

## 验证与交付

沿用已批准的公开验证边界：生产 TypeReflector / TypeScript backend 输出 → 严格 TS 正反例、真实 Graal host 行为、原生事件/资源/网络入口。对共享原因逐项先复现再修复；定向 Java/严格类型检查随实现执行，全量 common/check、处理器、五节点构建和必要 CI gate 在实现收口后集中执行。普通测试不更新 golden；必要派生变化从 canonical source 生成并解释。

审查基点固定为本轮起点 `495d9cb8`。交付前分别进行 Standards 和 Spec 只读审查，由主代理核验和处理发现，再提交。性能只在影响测量路径或最终候选验证确有需要时复验，不因工具与文档变化重复正式 cohort；保留所有失败与未运行范围。

状态以各票 metadata 和实际证据为准，此文仅为执行范围和验证安排，不创建第二状态表。不得将本机工程验收写成维护者整体验收或正式发布授权。

## 实施记录：多父接口声明

共享反射现在对多个直接父接口共有的暴露方法名补入实际继承方法，并按上下文 Java 参数类型保留重载、选择可确定的协变返回。比较键保留 Java 方法身份和真实 Type，避免把 remap 后同名方法或同名但不同约束的方法变量合并。未知泛型返回不按原始类猜测协变。没有删除公开成员、放宽类型或改变运行时转换；已有编辑后 IR 路径继续使用其编辑结果。

最小协变用例先复现 TS2320 / TS2322 / TS2741，再修复。进一步的不同泛型约束重载曾复现一个 Java 断言失败和 TS2345 / TS2430；改用真实参数 Type 后通过。新增三个回归覆盖严格声明正负例、泛型约束及生产 HostAccess 上的返回值与两组重载，并接入 `npm run test:probe-types`。

阶段检查：`:common:check :common-api-processor:test guardLint` 通过，共 common 2395 tests（4 skipped）和 processor 13 tests，零失败/错误，common isolation 通过，guard 零警告；完整定向声明消费链 `npm run test:probe-types` 通过。首次 Probe 相关测试有两个预期 golden 失败，随后只通过 `:common:regenerateGoldens --tests com.tkisor.nekojs.probe.LegacyProbeTreeTest` 更新 canonical legacy tree；`ByteChannel` 与 `SequencedSet` 两文件共增加 23 行实际继承方法，未手改快照。两份既有修改声明的 SHA256 保持原值。

这些结果不代表完整生产声明图已通过；十入口默认 TSC 尚待新制品重新生成和测量，6867 仍仅是旧归档数字。原始阶段日志和 XML 暂存 `build/priority-closeout/interface-merge-verification` 与同级 `interface-merge-*` 文件，最终交付时再封存与候选绑定。

## 实施记录：动态物品 wire 属性校验

NeoForge 26.x 的实际 Adapter 不再把非法 `fireResistant` 文本通过 `Boolean.parseBoolean` 静默转为 false。只接受 builder 规范化的 `true` / `false`；缺省 false 保持。错误包含稳定 `NEKO-4050`、物品 id 和 owner。正常 payload/schema/id/编码以及默认关闭的 activation gate 不变。开启 gate 的隔离用例经实际 codec 和 client participant 到实际 Adapter 验证 PREPARE 拒绝且不能 COMMIT、STATE_SYNC 拒绝、合法值和缺省值的 inert prepare；不把这组组件检查当作真实 TCP 或 registry surgery 验收。

红测六例中五例失败，保留于 `build/priority-closeout/dynamic-boolean-red*`。修复后两个 NeoForge 26.x 节点的六例与既有 wire golden 检查通过，common 动态注册表相关用例通过，详见 `dynamic-boolean-green.log` 和对应 XML。另修正了初次收集在已绑定生产引擎时仍声称“未接线/inert only”的过时说明与日志：只报告计划已提交及 activation 已交由事务 gate，不在 ACK 前声称完成激活。完整节点构建与多人原生验证仍待后续集中执行。

## 实施记录：显式手写声明依赖

`ManualDeclarationCatalogEntry` 增加显式全局别名 → Java Class / host 或 input 的引用记录，保留原六参数构造和无引用时的输出。record 的组件列表增加 `references`；需要按六组件解构的插件源码应改为访问器或新组件列表，不能据保留构造函数宣称所有 record 元数据兼容。依赖只进入 TS 的声明闭包，不改变主扫描、runtime 权限、wire 或 Python。已有 TypeDocs Point 原样收集，无新增 Point。内置 NeoForge 声明由版本层提供实际类，保持当前三种 NeoForge 的 side 范围，不把 Fluid 或 Ingredient 的 NeoForge API 注入 Fabric。

生成的 `@manual/references.d.ts` 用内联 import 保留全局作用域、真实宿主成员及输入适配器；raw 泛型 Class 引用沿用擦除实参规则，数组补齐元素类，不生成虚假的 Java 数组类。显式依赖被 hide/exclude 或不可生成时以 `NEKO-4033` 失败；同一全局别名的不一致引用以 `NEKO-4051` 失败。保留编辑后 IR 与 canonical Java 导出，未用空类型或全图 any 消除错误。

手写依赖与泛型数组各有先失败后通过记录；另有输入转换、hide/exclude、编辑/重命名和冲突用例。common Probe 相关测试、三个 NeoForge 节点的实际贡献映射，以及 `npm run test:probe-types` 的全部严格消费链通过。中英文插件文档配对更新并只 stamp 该主题，保持 draft/pending；结构检查零问题，整库严格文档检查仍失败 12 项，不代签其他主题。阶段材料位于 `build/priority-closeout/manual-reference-*`；完整 common/check、五节点制品与生产声明图仍待本轮实现集中收口后执行。

## 集中构建与安装候选：dd15186c

完整命令 `:common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build` 在离线缓存、JDK 25 Gradle JVM 和节点既定 toolchain 下通过，8m35s，106 tasks（42 executed）。common 2400 tests / 4 skipped，processor 13 / 0；节点普通测试依次 444 / 31、683 / 81、683 / 81、409 / 37、409 / 37，另五节点 platform gate 各 3 / 0；所列检查均零失败/错误。common isolation、guardLint（380 blocks / 561 files / 0 warnings）、三个 NeoForge runtime artifact、两个 Fabric runtime artifact 与 legacy server artifact 门禁通过。归档 XML 中其他 suite 来自已有构建目录，不计作本次执行结果。首次错误调用不存在的 `verifyModArtifact` 任务在执行前失败，原日志保留；没有修改或绕过门禁。

`build/priority-closeout/dd15186c-full-verification` 冻结 XML，`dd15186c-final-build-v2.log` 为正式构建日志。新 JAR 安装到全新 `build/closeout-dd15186c-installed`，只读复用已缓存 loader libraries，所有 common / 节点生产 class 字节逐项匹配；记录源码摘要、JAR SHA256 和 class 摘要。两份原有修改声明 SHA256 仍与起点一致。

五个独立已安装服务端均完成注册回调、实际物品/方块/流体适用项、重载、声明生成与 Python AST 检查；NeoForge 另验证实际交易池生命周期，两个 26.x 节点验证空动态 generation 保留物理效果。五进程均由 RCON 正常 stop 并 exitCode=0。此项不替代多人 TCP 动态注册表或客户端 PostEffects 证据。完整严格 TSC / Pyright 测量随后单独记录。

## 分轴审查：495d9cb8...dd15186c

Standards：Luna xhigh 只读审查最初报告无硬违规、无值得改动的 smell。主代理复核发现 `coding.md` 要求的新诊断码登记遗漏，已补齐中英文 `error-reference` 的 `NEKO-4050` / `NEKO-4051`；只 stamp 该主题，结构检查 26 topics / 0 problems，保留 draft/pending。原审查结论与这一主代理补查分开记录。

Spec：独立 Luna xhigh 只读审查无 findings，认为接口方法合并、显式 host/input 依赖和布尔拒绝符合本阶段规格；明确完整 TSC、PostEffects、多节点 TCP 和最终候选仍开放。这是源码审查，不代表运行验收或维护者接受。

两轴原报告 findings 均 0；主代理额外发现 Standards 文档遗漏 1 项，已修复，Spec 0 项。没有把两个轴合并排序，也没有据此关闭整票。

## 完整生产声明新测量

`dd15186c` 五节点十入口的默认严格 TSC 已完成，全部 exitCode=2，无 checker crash。总数 6867 → 6523；以文件、错误码、首行消息的多重集比较移除 344 / 新增 0，其中 TS2320 的 218 条全部消失，NeoForge 手写引用再移除 126 条。所有 1609 个实际 Python stub 的默认 Pyright 为 0 errors / warnings。完整 TSC 仍失败，不因诊断减少、收集脚本 exit0 或定向消费链通过而关闭门禁。

已封存于 [证据报告](../architecture-refactor/evidence/2026-10-21-probe-closeout/README.md)，包含 4935 项原始证据、完整声明、前后诊断、XML、安装日志、源码/class/JAR 绑定与验证工具。`verify.py` 实际重算 XML、五节点正常退出、Pyright 文件数和诊断差异，通过并保留完整 TSC 的失败结论。


## 实施记录：legacy 原生资源重载与画面所有权

26.1.2 的隔离开发客户端先完成七阶段 API 资源重载和七阶段原生键盘处理链，生产 class 与已安装 `dd15186c` 候选逐项匹配。后者通过测试 mod 暂时提供 F3 状态、刷新缓存键位并调用 Minecraft 的 T 按键处理器，确实进入原生 debug 资源重载；不称为物理键盘或 GLFW 回调输入。首个 RenderFrameEvent.Post 在 set/clear 返回后消费 arm 并读取主 render target，遮罩存在时不截图；七张原图由主代理查看，固定墙区域清晰梯度约 338、模糊约 12.9，候选拒绝后未再次 set 仍保留模糊。客户端和服务器均正常退出。

1.21.1 的首轮原生资源验证发现真实失败：声明 generation 保持，候选拒绝后画面却恢复清晰。`GameRenderer` 的 shader reload apply 会关闭旧 PostChain，随后重新检查原版实体效果；旧链已关闭，不能仅靠脚本 generation 回滚保住画面。读取已保存图片的比较脚本先确认 retained 与 clear 的差异约 0.13、与 invert 约 74.91；该轮机械记录曾为 PASS，但图像复核明确判 FAIL，原记录不覆盖。

节点专用 mixin 现在在原生资源重载前捕获实际 NekoJS 选择，并在完成后用新资源重建链。弱引用只用于识别 renderer 所有的链，不延长其生命或复用已关闭的 GPU 资源；set/clear/retirement 和 world identity 可取消旧恢复，关闭状态保持。`current()` 不再把原版接管的链误报成旧 NekoJS id，`isActive()` 在没有实际链时返回 false。恢复任务包含在返回的异步结果中，仍在 Minecraft owner executor 执行；没有新增 Script member、wire、配置、持久格式或 inline shader 能力。

Standards 的两项边界发现均已处理：完成时若 renderer 已有其他链就不覆盖；返回的重载结果包含恢复完成。Spec 另发现退役使用 stale lastSetId 会关闭原版接管的链，四阶段原生红测通过实际 Adapter 复现 active=false 后，再修复为仅清理仍由 NekoJS 选择的链，否则只取消账本/恢复请求。两轴最后均无剩余源码 findings；早期审查中的 runtime pending 不能代替后续执行结论。

最终 `posteffects-121-f3t-final` 十六阶段实际运行和逐图复核通过，七次原生 T 处理、资源重载前后、候选拒绝保留、恢复、重载期间显式 clear、声明退役、关闭效果的保持、原版 creeper 链接管及接管后的实际 Adapter 退役均有观察。最后一项有意直接验证生产 Adapter，不冒充又一次脚本事务：definition 消失、renderer 仍 active，两张绿画面 ROI 相同。所有帧都在 owner thread 且无加载遮罩，两个进程正常 exit0。中间十一/十五阶段结果、最初图片失败和四阶段原生红测均保留。world identity 取消目前是源码守卫，未另做此效果重载期间切世界的原生用例；此前脚本包 A/B/A 不替代此项。

原始材料暂存 `build/priority-closeout/posteffects-*`。本阶段只修改 legacy 节点；common 和其余节点生产源码未改，集中五节点证据继续绑定上一阶段 `dd15186c`。完成本阶段构建、制品/class 绑定和独立归档后，再更新对应票据；不据目前结果提前关闭票 28 或发布交接。


最终 legacy 源码的 `:1.21.1:build guardLint` 通过，1m08s / 37 tasks（9 executed），普通测试 444 / 31 skipped、platform gate 3 / 0，零失败/错误；runtime artifact 与 legacy server artifact 门禁通过，guard 380 blocks / 561 files / 0 warnings。新增 helper test mod 仅在隔离 init 中加载，不进入生产 JAR。前一阶段 common/check 与其他四节点未因这一节点专属改动重复执行；它们仍绑定原 `dd15186c` 证据，不能冒充本阶段新测量。

三节点 PostEffects 证据现已[独立封存](../architecture-refactor/evidence/2026-10-21-posteffects-native/README.md)：528 项、30 张原图，完整最终 legacy patch、XML、fixture/source/class/JAR 绑定及失败尝试保留；只读 verifier 实际通过。26.2 在 vendor 开发警告页面的隔离开关后七阶段通过，两进程正常退出。其页面阻挡是源码/后续结果支持的推断，未取得该页截图。票 28 的 AC6 节点 smoke 已有实证并勾选，AC9/整票仍保留正式候选安装客户端与最终验收边界；迁移记录与中英文 wiki 同步说明 legacy 所有权修复。

## 实施记录：Java Object 的 TS 值边界

TS-only 投影仅在未编辑 Java 来源确定为 `Object` 的位置输出 `unknown`，包含返回/字段、数组、嵌套 host 泛型、类/方法/SAM 泛型边界与 SAM 参数/结果；默认 Object 输入 alias 同步为 unknown，显式适配器仍优先。共享 TypeReflector IR 的 primitive object、Python 和运行时不变，显式编辑/合成的结构化 object 保留。未知值仍须收窄才能访问成员或赋为具体类型，没有全图 any 或删除成员。

三个回归中最初两个 Java 断言失败，严格 TS 复现 17 条诊断；生产 NekoSharedHostAccess 已实际验证 Object 接受/返回数字、字符串、布尔、null、回调和字段。第一版修复后追加泛型边界用例又复现 TS2345 / TS2344，再修复共同投影位置。初次 Probe 检查 400 tests / 2 skipped，仅两个 canonical legacy tree 比对按预期失败；通过现有 `:common:regenerateGoldens --tests com.tkisor.nekojs.probe.LegacyProbeTreeTest` 生成 26 文件、300 行 object→unknown 的预期变化，未手改产物。严格消费链和最终集中构建随后执行，完整生产声明图不因最小用例通过而提前判绿。

Object 修复后的 Probe 定向检查实际通过，400 tests / 2 skipped、零失败/错误；`npm run test:probe-types` 全链（含新增 Object 正负例）通过。两份起点既有修改声明摘要保持原值。common/check、处理器与五节点完整构建留到下一批共享声明修复集中执行；全量正式 TSC 结果仍只绑定 dd15186c，当前不作新总量承诺。

Object 阶段分轴审查：固定 `0a96cb3d...d9cd307d`，Standards 0 hard breach / 0 smell；Spec 1 项——重命名 canonical export 仍使用 neutral object 泛型边界。主代理复查还定位到同一 Adapter alias 边界路径，新增重命名+适配器真实生成用例先复现两条 TS2344，然后统一通过保留 TypeSlot 来源的 bound renderer。Object 与 GenericAdapter 定向 Java 检查通过，Object 严格消费和 plain/renamed/edited 三组 GenericAdapter 严格消费通过。原审查与修复后证据分开，未宣称原审查从未发现问题；后续复审仅检查该发现是否消除。


Object 源码跟进 `d9cd307d...6297761d` 已分轴复审：Standards 0 hard breach / 0 smell，Spec 0 剩余源码 findings。只读审查不替代完整图执行。

## 实施记录：集合输入的宿主身份

默认 List/Collection/Iterable 输入现在保留实际 host 容器，同时允许现有数组转换；修正 Iterable 的 Java 身份为 java.lang.Iterable。host 泛型参数与可转换元素输入分轨，防止元素适配器把 List<Payload> 错写为 List<string>。Map 同样保留 host key/value，record 分支使用可转换输入，非 PropertyKey 的未适配 key 仍只接受 host。显式注册的类适配器继续优先；既有自定义集合 alias 仍按原扩展方法派发。fallback 导出别名从同一 Registry 生成，不再重复默认集合规则。未修改 shared IR、Python、运行时或其他容器别名。

Set 的旧数组输入声明与实际运行不符：生产 NekoSharedHostAccess 实测接收 Java Set，但拒绝 JS 数组。声明据实改为 host Set，避免承诺不存在的转换；这不是新增运行时限制。真实 host List/Collection/Set/Iterable、List/Collection/Iterable 数组、带元素适配器的 host List/Map 和数组/record 输入均通过实际 Graal 运行。初次三个 Java 回归中两个类型断言失败，运行时观察通过；修复后通过。严格类型夹具只提供区分容器与数组所需的 host 操作，API 声明由真实 renderer 生成，不把该夹具称为完整 JDK 声明验收。

新 Map 负例还复现了 TS 5.8.3 的具体边界：将 Partial<Record<...>> 放在具体 key 的 conditional true 分支中，会丢失索引签名并意外接受 boolean 值。原负例的 @ts-expect-error 因此报 TS2578。把 PropertyKey 判定保留为 unknown/never 的交集门禁、record 放在条件外后，合法记录保持可用，错误值及不可转换 host key 的 record 均被严格消费拒绝；未用 any 或空类型压掉门禁。完整生产声明、五节点和最终候选验收仍待集中运行。

集合阶段初次 Probe 检查 403 tests / 2 skipped，两个 canonical golden 比对和一个旧回调结果断言按预期失败。canonical 任务再生成 16 个文件、87 行配对变化，保留 host 分支、Set 真实输入、Map key 门禁与转换元素；回调断言改为同时保留 host List 和数组的实际结果类型。修复后 403 tests / 2 skipped 零失败，完整 `npm run test:probe-types` 通过。中英文类型指南同步，仅 stamp typescript-and-jsx 并保留 draft/pending；结构检查 26 topics / 0 problems，整库严格文档检查仍失败其他主题的 10 项。两份起点既有修改声明未变。

## 集合别名复审与后续候选

固定 `6297761d...99677b0f` 的 Standards 为 0 hard breach / 0 smell；Spec 指出 fallback 的 SequencedSet / SortedMap 等仍错误承诺数组或 record 输入。主代理核对同一生成分支后，把既有具体集合类及 Collection/Set/Map 子接口的 fallback 输入别名改为各自 host 类型；普通参数原本已使用这些 host 类型。List/Collection/Iterable/Map 本身的原生转换规则、显式插件适配器优先级、其他 Iterator/Stream 别名和运行时均未改动。

生产 HostAccess 实测 ArrayList、SequencedCollection、SequencedSet、SortedMap、SequencedMap 的真实容器可用，相应数组/record 在实际执行集合操作时失败。新增严格消费覆盖十二种既有 fallback 别名，先复现宿主容器被拒绝和非法数组/record 被接受，再修复；夹具仍只补入区分容器所需的 host 操作，不冒充完整 JDK 声明验收。

这次还明确了 Set 的延迟代理边界：Graal 可以先构造 Set 接口代理，`Collections.unmodifiableSet(array)` 单独返回不证明转换可用，调用 `size()` 才失败。新增真实 HostAccess 回归同时验证代理可构造和操作被拒绝。安装测试的第一轮错误地只检查包装调用，导致脚本候选失败；完整失败日志和五进程正常退出保留于 `build/closeout-99677b0f-installed`，不计为通过。第二轮使用同一批冻结 JAR、独立新目录与修正后的操作断言，测试结果单独记录；输出标记只匹配真实日志事件，不能把异常堆栈中的脚本源码行计为通过。

前一源码 `99677b0f` 的完整 common/check、处理器、guard 与五节点 build 已通过：8m16s，106 tasks / 45 executed，5062 tests / 271 skipped / 0 failures or errors，含五节点 platform gate。XML 与完整日志在 `build/priority-closeout/99677b0f-matrix` 冻结；安装 JAR 的全部 common/node class 与对应构建逐字节绑定。该结果不覆盖本节 fallback 后续源码，也不代表完整严格 TSC 已通过。

fallback 阶段初次 Probe 检查仅两项 canonical 比对失败；既有再生成任务只改变 java.util golden 的四行输入别名。最终 Probe 检查 404 tests / 2 skipped / 0 failures or errors（其中集合四例），完整 `npm run test:probe-types` 通过。中英文指南已配对更新，结构检查 26 topics / 0 problems。两份起点既有声明摘要保持原值。修正夹具后的 `99677b0f` 五节点安装运行全部通过，五进程均正常退出 0；严格 TSC 仍按实际结果保留失败状态，完整计数和差异后续封存。

`99677b0f...149faa8a` 的两轴只读复审均无新增源码 findings，原 fallback finding 已关闭。它们不替代实机或整票验收。

## 实施记录：Java 静态字段隐藏

完整安装候选 `99677b0f` 的十个默认严格 TSC 入口仍失败，现为 6453 条；相对 `dd15186c` 按文件、代码和首行消息比较移除 70 条、新增 0 条。所有 1609 个 Python stub 的默认 Pyright 仍零错误/警告，Java host stub 字节均不变；全局与事件入口若干文件存在内容/顺序差异，不声称整套 Python 逐字节一致。原始材料在 `build/closeout-99677b0f-installed-v2/ide`，首次失败夹具另存原目录。

下一项共因为 Java 子类合法的同名静态字段与 TypeScript 静态侧继承不兼容，例如 BlockPos/Vec3i 的 CODEC。TS-only 投影只处理完成声明闭包后确认为静态字段隐藏的普通类：class 保留构造器、自身静态字段及可调用静态方法；同名合并 interface 保留编辑后的实例父类型、接口、字段、Bean 读写和方法。其余类与 enum 路径不变，未修改 runtime、Python、公开 Java API、wire、主扫描或 exclude/hide。

实际生产 HostAccess 配合 NekoSandboxFactory 的 Nashorn/foreign-object 选项，验证子类能调用父静态方法和不同参数重载，却不能通过子类 class value 读取父静态字段。早期夹具按纯 Java 继承假设读取该字段和缺少兼容选项读取 Bean 而失败，均保留并修正测试假设；不把它们说成生产 bug。拆分后的声明也不再承诺子类值可读父静态字段，应通过父类读取。实例继承与实际静态调用保持。

继承只使用同轮完成的编辑后 IR；不反射补回被隐藏、排除或手写替换的父类。全部父签名已参与闭包，仅重渲染受影响的缓存，模块局部 import/rename 走现有引用观察器；每轮结束清理投影。新增内部 MethodDecl.sourceMethod 保留 Java 原始方法身份与擦除参数，避免编辑参数后复活已隐藏的父签名；不改变其他后端输出。无公开构造器的拆分类不会获得虚假的公共默认构造器。

最小生成用例先复现单一 TS2417，再修复；真实 Graal 回归验证两个静态字段、继承调用、两种重载、Bean 与私有构造器拒绝。追加构造器和编辑参数负例分别先出现 TS2578，再修复。普通、重命名/跨包碰撞、隐藏父方法和重用 backend 四组严格消费通过；407 项 Probe 检查（2 skipped）通过，既有 golden 无变化。全量 common/check、五节点制品与新生产声明图仍须本阶段集中验证，不外推 `99677b0f` 的测量结果。

## 静态投影复审修复与候选实测

`149faa8a...d91ca974` 的 Standards 指出方法签名键混用 `List<Object>` 和测试 setup 重复；Spec 指出 TypeReflector 放进子类的未编辑父重载副本会先占签名键，绕过父类重命名、隐藏或改型。主代理补齐实际 backend 的重载正负例，先复现 TS2339 和 TS2578，再修复：成员记录显式编辑，未编辑继承副本服从声明祖先的编辑后 IR；子类显式编辑的同名继承重载仍优先，隐藏项继续占用原始 Java 签名。键改为明确的 Java / synthetic 两种参数键，测试共用接受 roots 的生成入口。该元数据不改变其他后端输出、Graal 运行时、主扫描、wire 或持久格式。

补充了泛型中间类与祖父类重载，验证祖父改名、最近显式编辑、子类独立重载、子类隐藏与 backend 重用。真实 HostAccess 另验证祖父/父/子三种参数重载；七组严格消费和完整 `npm run test:probe-types` 通过，408 项 Probe 检查（2 skipped）零失败，golden 无变化。完整 `:common:check` 在后续集中执行，不能把仅 Probe 测试当作 isolation 通过。

已冻结的 `d91ca974` 中间候选完整构建通过：8m37s、106 tasks / 41 executed、5066 tests / 271 skipped / 0 failures or errors，含 common isolation、处理器、guard、五节点及各 platform gate。五个新安装服务器全部实际通过、正常退出 0，全部 common/node class 与 JAR 逐字节绑定。1609 个 Python stub 的默认 Pyright 零错误/警告。十入口完整严格 TSC 为 515/516、578/579、569/570、646/647、623/624，共 5867 条；相对 `99677b0f` 移除 594、新增 8。新增均是现代节点 SectionPos 由 class 改为合并 interface 后报告 TS2430；原候选已有 x/y/z Bean 与同名方法不兼容的 TS2416/TS2426，不能称为无新增诊断或完整 IDE 通过。该候选尚不包含本节复审修复，最终候选须另绑定，不沿用旧制品冒充新源码验收。

26.1.2 的独立 ModDev 工程只注册验收 helper 为 source mod，NekoJS 从安装的 `d91ca974` 生产 JAR 加载；ModList 文件路径、实际 CodeSource、class resource URL 和字节摘要均相符。原生 debug 资源重载、拒绝候选后未再次 set 的画面保留、恢复共七张世界目标图由主代理逐张查看，客户端/服务器正常退出 0。该工程仍用开发启动器，不称为外部正式安装器或物理键盘/present/人工签字。首轮因复用的历史 `.js.rejected` 文件名与本轮 rename 冲突，在五帧后中止；两进程正常退出且判 NOT GREEN。第二轮在新目录/新世界、同一冻结 JAR 上用独立退役文件名完成七阶段，旧失败材料保留。原始记录在 `build/priority-closeout/posteffects-261-installed-d91ca974[-v2]`，后续封存后再更新票据交接链接。

`d91ca974...dae6ef6d` 的 Standards 复审为 0 hard breach / 0 smell；Spec 发现参数编辑目标不存在时仍标记继承副本，可能遮蔽父类有效编辑。主代理补入越界/缺失参数选择器，实际严格消费再次复现 TS2339 / TS2578，再将标记限定为匹配到真实目标，保留原验证调用和类级 touched 行为。新增独立子类按参数名改型，证明合法的继承参数编辑仍优先，未改型的自有 overload 保持。408 项 Probe 和完整严格消费链通过，golden 未变化。`dae6ef6d` 自身完整 common/check 已通过（3m07s）；后续 no-op 修复须另记录完整 common/check，不冒充旧检查已经覆盖。

最终源码 `ad4d3750` 的 common/check 已通过（3m23s，2411 tests / 4 skipped / 0 failures or errors，含 isolation）；两轴最终复审均无剩余源码 findings。上述构建、三批安装尝试、完整声明诊断和安装客户端原图现已[独立封存](../architecture-refactor/evidence/2026-10-22-static-inheritance/README.md)，6738 项 / 14,920,619 bytes，逐项摘要只读 verifier 通过。已测中间 JAR 与后续源码仍分别绑定，最终五节点制品、完整严格 IDE 和发布相关验收不因归档而判绿。


## 当前候选正式性能结果


2026-10-22 阶段统一候选 `4405b0b0` 的[正式性能复验](../architecture-refactor/evidence/2026-10-22-candidate-performance/README.md)已完成：两组重载均值 348.2 / 332.2 ms，均超过原 285.3 ms 门槛；启动均值 28026.2 ms，通过 41173 ms 门槛。17 条样本有效，9 次会话均正常退出；原采样器、fixture 和门槛不变。归档复核通过只表示原始记录及实际失败可复算，不表示性能验收通过。该结果取代旧候选在本轮交接中的性能状态；仍须定位并修复当前重载超标后按同一标准复验。


2026-10-22 统一候选 `4405b0b0` 的[完整检查归档](../architecture-refactor/evidence/2026-10-22-candidate-checks/README.md)：common/check、处理器、guard 和五节点构建通过，5067 tests / 271 skipped / 零失败。安装首轮四节点通过，26.1.2 实体查询未完成；同一 JAR 的独立不暂停控制组严格全链通过，但首次原因未确诊，不能改称首轮五节点全绿。六个服务端均正常退出。1609 个 stub 的默认 Pyright 全通过；十入口默认严格 TSC 仍有 5867 条诊断、无崩溃，完整 IDE gate 继续失败。


## 重载后续：复用纯源码事实

阶段基线 `911f3b5d`，主代理实现与执行；Luna 只读核对调用链。此前正式性能失败及两轮阶段诊断分别封存，不能用较快的诊断结果替代正式标准。

当前实现范围：在既有 root-owned 转换缓存的生命周期中复用 ValParser 树、局部声明名和别名关系，入口预检与模块预检使用同一份纯源码事实。缓存不保存 schema、宿主类型流、Context/Value、监听器、诊断或接收方；入口每次仍读取真实文件并按当前 schema 校验；模块准备仍每次读取并授权，每次实际模块预检仍使用当前 schema。同 stamp 的 prepared entry 命中沿用原有跳过模块预检的行为，本批不扩大其校验频率。最多 128 个路径、512 Ki 个源码字符，超限不保留；内容变化即替换，显式 invalidate/clear/close 释放。自定义语言前端仍逐次转换，只有对实际预检 JS 文本的纯分析可复用。

回归 seam 沿用 ScriptSourcePreflight 的实际诊断结果与 NekoModulePipelineCache generation session：相同源码更换 schema/接收方、不同路径归因、源码修改、容量淘汰/超大文本、清理关闭及候选释放。首个新增 API 用例先因能力不存在编译失败，再修复通过；后续集成校验已通过一批 compiler/module/script 检查。最终 common/check、平台构建、分轴审查与同门槛正式性能复验尚在推进，当前不宣称性能已修复。


本批两轴只读审查：Standards 0 硬性违反 / 1 低优先级命名建议（私有缓存查找方法 `source`）；Spec 0 明确实现错误 / 0 范围蔓延，报告一处“每次校验”的字面边界及解析失败重解析缺少直接覆盖。主代理澄清：本批保留原有 prepared-cache 命中语义，要求每次实际预检使用当前 View，并非新增命中时的模块预检。解析失败驱逐有源码路径，但 ValParser 对畸形文本宽松，不为覆盖而增加解析器注入或不稳定的栈溢出夹具；这项直接执行覆盖仍缺少。缓存名称建议不影响契约，未为此重复改变已验证源码。


2026-10-22 后续源码 `7bea6d6d` 的[纯源码事实复用检查](../architecture-refactor/evidence/2026-10-22-source-facts/README.md)通过 common/check、处理器、guard 及 26.1.2 build（3113 tests / 85 skipped / 零失败）；其余四节点尚未重建，不沿用旧候选结果。相同采样政策的[正式性能复验](../architecture-refactor/evidence/2026-10-22-source-facts-performance/README.md)：两组重载 340.0 / 292.8 ms，均仍超过 285.3 ms；启动 33486.6 ms，通过 41173 ms。17 条有效样本、9 次正常 RCON 退出，1640 个类及 JAR 与主检出一致。源码复用已实现，但重载性能门禁仍失败；不以第二组接近门槛或历史通过代替本轮结论。完整严格 TSC 的最近冻结结果仍为 `4405b0b0` 的 5867 条诊断，当前源码未重跑完整 IDE 与五节点运行，不声称已经完成最终统一候选。


2026-10-23 [重载节奏诊断](../architecture-refactor/evidence/2026-10-23-reload-cadence-diagnostic/README.md)已封存：启动即录制 JFR，Done 后按 3 秒间隔执行 5 次真实重载；只统计命令窗口，入口预检合计 2.92 ms、模块求值 53.21 ms、配方条件过滤 1.69 ms、配方解码 59.86 ms，root 103.21 ms、命令 172.23 ms。此为有诊断开销的阶段测量，不能替代正式 340.0 / 292.8 ms 的失败。15 个 root / 17 个配方执行样本不足以锁定单一叶函数；未跳过当前授权、schema、插件事件或配方解码。纯编译 Source 实验的中位收益仅约 3 ms，未据此改变 CommonJS 语义。两次诊断启动失败保留，成功运行正常 RCON 退出，五处临时源码恢复后 1640 个类和 JAR 与正式候选逐字节一致。完整五节点构建后续另记，不外推旧制品。


## 同名成员投影的运行时反例

2026-10-23 [实际 Minecraft 类诊断](../architecture-refactor/evidence/2026-10-23-bean-member-semantics/README.md)确认：Fabric 26.1.2 的未变换 Vec3 / SectionPos 类，配合生产 HostAccess 与相同相关 JS 选项，读取 x 为数字、直接 x() 成功，但读取到局部变量再调用失败。严格 TS 的 number 与可调用类型交叉会接受最后一种无效调用，不能直接作为修复。夹具、宿主实现与实际 Minecraft/Graal JAR 摘要及严格编译反例均封存；独立 JVM 未应用 Mixin，不称为五节点安装验收。没有删除或隐藏其他公开成员，没有扩大此前五处 Entity 迁移的批准范围。完整声明投影仍须解决宿主读/调用差异、继承和 Position 接口视图，不能以单一交叉类型能编译判绿。
