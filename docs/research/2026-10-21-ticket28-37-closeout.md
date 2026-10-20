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
