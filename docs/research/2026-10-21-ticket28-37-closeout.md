# 票 28 / 37 后续工程收尾

用户于 2026-10-21 授权尽可能完成剩余项。起点为 stonecutter `495d9cb8`；主代理实现、测试、游戏验证与提交，Luna xhigh 只读调查，不创建 PR、不推送或正式发布。保留工作区既有两份修改声明与其他无关文件。

## 执行顺序与边界

1. 按完整严格 TSC 的诊断归因修复共享原因，保留真实 Java/Graal 行为、公开成员、编辑后 IR 和依赖限制。先处理多父接口继承签名，再核对其他高重复原因。6867 是最近归档的十入口结果，不当作当前源码的新测量。
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
