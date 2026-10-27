# 37: 1.2.0 clean cutover 与发布交接

2026-10-28 获批SectionPos精确迁移（产品 `3f9f1b0a`，[正式证据](../evidence/2026-10-28-section-pos-bean-implementation/README.md)）：五节点仅省略15处TS零参x/y/z方法，保留数字Bean、getter、静态方法、JS/Java/Python及编辑IR权威；现有TypeDocs渠道收集精确投影。完整矩阵5226 tests /271 skipped /零失败错误，完整TS消费链、四项CI及五节点source-roots通过，无golden变化；确切新JAR均正常停服，十入口997→973、完整诊断块−24/+0，F13 36→12，其余族逐入口不变、零UNMAPPED。完整严格TS仍FAIL；Shelf及其余族、最终候选门禁保持开放，未push、PR或代签。

2026-10-27 Stream捕获返回（产品 `11c9a180`，[冻结证据](../evidence/2026-10-27-stream-capture-11c9a180/README.md)）：复用完整StreamView及继承facet，保留窄返回、操作数域和编辑IR权威，异种容器不串用facet。最终完整矩阵5206 tests /271 skipped /零失败错误，完整TS链和四项CI gate通过，无golden变化；五节点确切JAR新安装均正常停服，十入口1029→997，完整诊断块−32/+0，F8清零、F1/F2/F4仍零，其余族逐入口不变、零UNMAPPED。孤儿消费清理及同容器guard均有先红后绿证据。完整严格TS仍FAIL；F6、host结构身份、JS数组桥异常及最终候选门禁仍开放，未push、PR或代签。

2026-10-27 F6 [真实DFU捕获实验](../evidence/2026-10-27-recipe-capture-prototypes/README.md)已取得公开backend继承红证及Java CAP#1/CAP#2正负编译对照；五组冻结TypeScript输入精确复现失败，不透明标记仍合并独立捕获，never方案还泄漏底类型，全部未投产。生产1029不变，F6保持开放，先推进可复用完整StreamView/返回facet的F8；没有全局协变、any或成员删除。

2026-10-27 完整捕获视图恢复（产品 `8f71ca31`，连续 wildcard 祖先合同修复 `15c4d9a6`，[最终证据](../evidence/2026-10-27-transitive-capture-facets/README.md)）：从可见编辑 IR 继承完整原始实例面，恢复 or、全部 reduce、Collector collect、toList 及 List/Collection 递归导航，保留输入域负例、工厂签名和隐藏/排除/手写替代权威。前驱新安装 1037 的 8 条遗漏已保留[失败证据](../evidence/2026-10-27-complete-capture-views/README.md)，补回归修复后，完整矩阵5200 tests /271 skipped /零失败错误、TS全链和四项CI gate通过；新五节点均正常停服，25类与制品匹配，十入口回到1029，对3d2cc1cb完整诊断块−0/+0，各族及逐入口不变且零UNMAPPED。F1/F2/F4诊断为零，F1公开方法缺口已补；host结构身份、JS数组桥异常、其余严格TS族及最终候选门禁继续开放，未push、PR、发布或代签。

2026-10-26 继承捕获返回合同基础（产品 `5280d20b`，[冻结证据](../evidence/2026-10-26-capture-return-contracts/README.md)）：从可见编辑IR给窄Optional返回追加继承facet，保留直接窄读取及较宽fallback；支持泛型参数/数组专化、继承实现、中间重复覆盖和wildcard窄返回，隐藏/改名/覆盖返回不从反射恢复。两轴复查的两项P2各自先红后绿并复查通过；common/check+guardLint（2478 tests /4 skipped /零失败错误）、完整npm链和确切生成消费者通过，移除facet对照严格报5条TS2345。完整视图公开方法恢复仍开放；没有新安装/十入口/性能，1029仍仅绑定3d2cc1cb。JS数组触发ClassCastException的扩展失败保留，未宣称修复，票保持in-progress。

2026-10-26 ConstantDesc标量合同（产品 `3d2cc1cb`，[冻结证据](../evidence/2026-10-26-constable-values-3d2cc1cb/README.md)）：真实Graal证明常量描述包含number/string及host，声明在最终值边界表达联合，显式adapter及覆写仍优先，继承顶部保留host。三变体严格消费、构造器varargs和显式联合数组分组均有正负证据；收口矩阵5193 tests / 271 skipped / 零失败错误，TS全链和四项CI gate通过。新五节点正常停服，十入口1079→1029、原文/同源均−50/+0，F1诊断清零，其他族逐入口不变且零UNMAPPED。F1公开捕获方法仍缺，完整严格TS仍FAIL；启动器短暂Maven超时后原进程完成，原文保留，不作为性能样本。新候选性能、Python/客户端/TCP/回退/包装及维护者结论仍未完成，未push、PR或代签。

2026-10-26 SAM对象输入与转换边界（产品 `1a1660b6`，[冻结证据](../evidence/2026-10-26-sam-proxy-1a1660b6/README.md)）：真实Graal确认Java命名的最小SAM对象可用、默认方法可用、@Remap不改代理分派名；TS别名新增受保护对象分支，globalThis.NoInfer避免Function.apply污染旧reduce推断。sealed接口保留完整host、移除运行时不支持的旧函数输入，不增代理分支；完整结构对象模拟host的旧名义身份限制仍记录开放。四项新回归、完整定向TS链、四项CI gate及独立临时目录矩阵通过（5191 tests / 271 skipped / 零失败错误）。新五节点安装全部正常停服，十入口仍1079，各族不变、原文及同源差分均0/0，零UNMAPPED，完整严格TS继续FAIL。另冻结并重放16组捕获协议实验，失败保留且不视为生产F1完成；F1缺失公开方法及最终候选门禁仍开放，未push、PR或代签。

2026-10-26 枚举实例接口合同恢复（产品 `84ae51c1`，[冻结证据](../evidence/2026-10-25-enum-interface-84ae51c1/README.md)）：生成器从编辑后的 IR 保留枚举 implements，恢复 IsoEra→Era 等默认实例合同，不改运行时或静态工厂。七项枚举回归、六组严格消费、定向 TS 全链、四项 CI gate 及串行隔离矩阵通过（5187 tests、271 skipped、零失败；节点测试有缓存复用）。早期重叠测试的目录/结果文件污染与回收证据保留，不计绿态。新五节点安装全部正常停服；十入口1409→1079，F2 330→0，其余族不变。原码/原文多重集差分为−334/+4；其中4条是同一客户端缺失引用的 TS2304/TS2552 提示变化，实际声明、源码行和缺失名称匹配后差分为−330/+0，两种口径及原文均保存。完整严格TS仍FAIL，F1公开捕获合同保持开放；新候选性能、Python默认安装及最终客户端/TCP/回退等未重测，不外推旧证据。

2026-10-25 主代理接手审核与实现（`a8c0f274`、`555ee037`、`dad806fa`，[冻结证据](../evidence/2026-10-25-primary-erased-method-dad806fa/README.md)）：f1v3 恢复的重载与驱动修正保留，但 or/部分 reduce/Collector collect/toList 仍被省略，F1 公开合同未获完整通过。修复启动后身份查询/输出/中断的 owned 子进程回收与清理失败保因，四项故障回归通过；共享反射层从无歧义、同擦除接口恢复完整方法泛型合同，保留原 Method、隐藏/remap 和既有类型。完整 common/处理器/guard/五节点矩阵 5183 tests、271 skipped、零失败，定向 TS 链与四项 CI gate 通过。新五节点安装全部正常停服，新十入口 1503→1409（移除94/新增0：F4 80→0、尾项26→12，其余不变），全图仍 FAIL。性能与最终候选门禁保留各自范围；未 push、PR 或代签。

2026-10-25 第二轮复核四项修正（`122c8c3e`+`f71b45fb`，[证据](../evidence/2026-10-25-strict-ts-f1v3-overload-retention-f71b45fb/README.md)）：捕获视图重载保留重做——真实树 19 轮实验矩阵（X4–X8/Y1–Y16）证伪了"整组排除"，`sorted` 双签名（无参前置）、三参 `reduce`、supplier 版 `collect`、`spliterator` 全部恢复为正向合同（golden +5 行），排除收敛为实证锚定的 R1a 嵌套捕获容器 / R1b 多重载组内全 B 别名（BinaryOperator reduce、同形状单签名如 orElseGet 不受影响）/ R2 组内零参+首个选择 / R3 不变容器返回（toList，白名单 Iterator/Spliterator）；驱动三项：kill 脚本改微秒创建时刻+完整命令行双全等（PID 复用两形态实测 SKIP）、run 失败原因入摘要且 `SystemExit from` 原异常（注入实测）、分类器去无条件兜底改显式枚举（未知诊断 UNMAPPED 非零退出，闭合仍 1609）。f1v3 确切产物十入口 1503（与 f1v2 逐入口一致、重载保留零诊断代价、族级 F1 156→50 其余 ±0、逐条 −106/+0），:common:check 全量与 test:probe-types 绿。

2026-10-25 Codex复核五项修正与F1成员驱动视图重做（`d075db12`+`fab58a7d`，[证据](../evidence/2026-10-25-strict-ts-f1v2-member-view-fab58a7d/README.md)）：捕获视图改为按 `java.util.Optional`/`Stream`/`BaseStream` 反射实例面生成的命名 `$OptionalView<B>`/`$StreamView<B>`——`orElseThrow`/Bean `present`/`filter`/递归 `stream().findAny().stream()` 等复核点名正例全部恢复为正向合同（夹具+严格 tsc 退出 0），排除集收敛为三条带实测错误链证明的机械规则（嵌套捕获容器参数 `or`；多重载上界 SAM 组 `reduce/sorted/collect`；不变容器返回 `toList/spliterator`，`$Iterator` 白名单），被排除成员在具体实现上完整保留；原型在 914df3c7 真实树副本零净差异后产品化。驱动加固：kill 脚本仅处理本驱动 `server-process.json` 记录且三重身份验证（四情形实测）、run 异常路径统一回收（故障注入实测）、check 三态分类显式失败 invalid 运行（注入实测）。[根因分类](../../../research/2026-10-25-strict-ts-root-causes.md)经新增 `classify_diagnostics.py` 逐条互斥归类精确闭合 1609（映射 JSON 入库，F9 按叶因解散归 F1/F5/F6/F13）。fab58a7d 确切产物十入口重测 1609→1503（F1 156→50、其余族±0、逐条移除 106/新增 0），:common:check 全量与 test:probe-types 绿。（该轮的整组重载排除已被上方 f1v3 重做修正；历史证据保持原样。）

2026-10-25 严格TS基线重建与第一族修复：新保存[可复用五节点安装/生成/检查驱动](../../../bench/tsbaseline/README.md)，c3ee4097 十入口默认安装基线实测 1609（逐入口/错误码与 v3 修正值逐项一致，声明树与 3ccea55a 逐字节一致）。[根因分类](../../../research/2026-10-25-strict-ts-root-causes.md)列 15 族（真实签名经 patched-jar javap 核验）。F1 Constable→Optional 通配族修复 `914df3c7`（canonical 渲染器输出位捕获视图，先红后绿+正负消费+[证据](../evidence/2026-10-25-strict-ts-f1-optional-capture-914df3c7/README.md)）：十入口 1609→1503（F1 −90、HolderLookup.Provider/RegistryAccess 连带 −16，零新增零回归），:common:check 与 test:probe-types 全过；剩余 50 为 number/string 值投影可表达性边界。完整严格 TS 仍 FAIL（1503）。（该轮内联视图后被 2026-10-25 复核指出公开面缩减，已由上方 fab58a7d 成员驱动视图替换；历史证据保持原样。）

2026-10-25 当前HEAD `d06cd961` 的[原政策正式性能](../evidence/2026-10-25-perf-d06cd961/README.md)：两组重载均值 219.8 / 214.0 ms（门槛 285.3）、启动 20340.4 ms（门槛 41173），**三组 PASS**；17 条有效记录、9 次会话全部正常 RCON 停服，原采样器（SHA 不变）/fixtures/门槛/首值保持，默认系统策略，JAR `10e43cae…`（1648 类）绑定该源码。对 26844b7e..d06cd961 全部 88 提交的只读路径复查未发现任何给重载路径新增可归因重复工作的提交，历史 FAIL 记录不改动不覆盖。同轮完成混合引号导入名分配回归修复（`d38c14dd`，先红后绿，backend 回归/common:check/probe-types 全过）与 v3 push 摘要标注修正（`d06cd961`，两个 helper JAR 摘要实为不同，相同的是内嵌 class 摘要）。完整严格 TS（1609 条 exit 2）、最终候选冻结/客户端/TCP/分离回退、1.2.0 包装与维护者结论仍开放；不代签。

2026-10-24 当前6e43b827的[工程候选验收汇总](../evidence/2026-10-24-owner-stamp-acceptance/README.md)为当前状态入口：确切六制品的TCP、三节点安装渲染及已知版本对的26.1.2制品/数据分离回退均已完成各自范围。完整严格TypeScript、原门槛重载、最终版本包装与维护者结论仍开放；旧975记录保持原二进制范围，未代签或发布。

2026-10-24 显式选择器生成器（7d17de4e、e13cbe82）及[java:/node: Windows路径修复](../evidence/2026-10-24-java-module-windows-path/README.md)（3ccea55a）后的[第三轮隔离安装验收](../evidence/2026-10-24-selector-installed-v3/README.md)：五节点矩阵5177项/271跳过/零失败；新目录新端口安装五节点运行时全PASS，`java:`类导入实际执行；push继承家族运行时身份两加载器采集一致，AST实测迁移991移除/1070选择器/121普通新增、字段零删改。十组完整严格TS修正计数为1609（=1629首轮实测−20条脚本自生诊断；仍FAIL，剩余与旧原型1621同源），Python五节点1609 stub零错误零警告。正式重载、3ccea55a候选的制品/数据分离回退、性能复验与最终版本包装仍开放；无代签。

2026-10-24 初次固定6e43b827时的记录：[owner泛型、事件导入与属性查询修复](../evidence/2026-10-24-owner-stamp/README.md)通过5158项/271跳过/零失败、五节点新安装与五正常停止、1609默认stub及完整定向TS链。完整严格TSC5549→5537，移除12/新增0，仍FAIL。[原政策正式性能](../evidence/2026-10-24-owner-stamp-performance/README.md)重载288.2/299.4ms均超285.3ms，启动26174.8ms通过，17有效记录/9正常停止。[六制品本地交接](../evidence/2026-10-24-owner-stamp-handoff/README.md)已更新为确切6e候选；当时客户端/TCP/分离回退尚未重跑，后续当前TCP已由上方独立证据补齐，975历史证明不冒称当前。维护者结论未代签，整票继续in-progress。

2026-10-24 最新工程源码87baad4b的[继承属性修复](../evidence/2026-10-24-inherited-bean/README.md)通过5152项/271跳过/零失败、五节点新安装与五次正常停止、1609默认stub零错误警告。完整严格TSC5565→5549，删除16条TS2611、没有新增，仍FAIL。[同JAR原政策性能](../evidence/2026-10-24-inherited-bean-performance/README.md)重载287.4/305.6ms继续超285.3ms，启动28014.8ms通过，17有效记录/9正常停止。既有六制品交接与客户端/TCP/回退仍明确绑定975，不冒称87已重跑那些流程。下一项无损共享修复及96个真实字段/方法冲突见[TS2300分类](../../research/2026-10-24-ts2300-followup.md)；未删除成员、放宽门禁或代签维护者结论，整票保持in-progress。

2026-10-24 [实际managed schema微测量](../evidence/2026-10-24-managed-schema-diagnostic/README.md)完成6000条记录：148symbol/7global，六组纯重新提取均值0.0537427–0.0997818ms；保留v1夹具崩溃和v2正常停止，未增加缓存。它不测整个BINDING阶段，也不替代正式性能，重载313.8/308.6ms继续FAIL。

2026-10-24 [本地候选交接](../evidence/2026-10-24-candidate-handoff/README.md)已准备五节点六个确切JAR、SHA256SUMS与独立清单；显示版本仍1.1.0-preview3，未切换版本或重打包，releaseReady=false。当前指定候选的工程复验已补齐，完整严格TSC和重载性能仍失败，未远程上传、push、发PR或正式发布。[下一共享原因调查](../../research/2026-10-24-shared-cause-followup.md)保留未实现/未测量项与已否决原型边界。

2026-10-24 当前975496d3的[三节点安装客户端渲染](../evidence/2026-10-24-candidate-render/README.md)完成30原图逐张复核、13次原生F3+T、六个正常进程退出；legacy16阶段、现代各7阶段，实际JAR、1100源码、全部生产类与客户端加载来源均核对。结合本轮当前TCP与分离回退，指定候选的这些工程复验已补齐；不扩展为官方安装器、物理显示器present、任意旧版恢复政策或维护者发布验收。完整严格TSC5565与重载313.8/308.6ms继续FAIL，整票仍in-progress。

2026-10-24 当前975496d3的[双节点真实TCP](../evidence/2026-10-24-candidate-tcp/README.md)完成每节点八阶段协议与七阶段COMMIT故障恢复，共30阶段和14正常进程退出。1100源码、每节点1641生产类、五个实际加载类与PID逐项核对；所有证明均来自本轮当前JAR。未扩大为其他平台或外部安装器，完整TSC、重载性能及维护者结论仍失败/开放。

2026-10-24 当前975496d3的[制品与数据分离回退](../evidence/2026-10-24-candidate-rollback/README.md)完成四阶段、四次正常退出：旧JAR读取推进后的pdata/世界，恢复停服备份后当前JAR才回到旧值。实际当前JAR与1641类绑定同存；完整备份与推进后原世界保持原摘要。范围仅已知d91ca974↔975496d3和26.1.2完整世界夹具，不代签任意旧版、世界外副作用或最终回滚政策。该阶段尚缺的当前客户端/TCP已由上方本轮独立证据补齐。

2026-10-24 当前候选 `975496d3` 的[方法上界赋值与分组](../evidence/2026-10-24-method-bounds/README.md)通过完整5149tests/271skipped、五节点新安装实际行为及全部正常退出0；1609默认stub通过。完整严格TSC仍5565，移除0/新增0。首次旧精确文本断言失败及其一行预期更新分别保留；无成员删除、泛型猜测或门禁放宽。该源码[正式性能](../evidence/2026-10-24-method-bounds-performance/README.md)为重载313.8/308.6ms均超285.3ms、启动14132.6ms通过；17有效样本/9正常停止。

2026-10-23 [五个双语主题实际复核](../evidence/2026-10-23-wiki-review/README.md)已修正文案与示例后更新digest；严格wiki门禁26主题零问题，8项工具测试及三个NeoForge原生桥接示例编译通过。旧10条摘要告警清零，所有人工审核/翻译状态保持原值。此项不替代完整TSC、性能或维护者验收。

2026-10-23 最新源码 `6e7452b7` 的[完整类泛型约束](../evidence/2026-10-23-class-bounds/README.md)通过5146 tests/271 skipped、完整五节点检查及五个新安装服务端，全部正常退出0。1609默认stub通过；完整严格TSC为5565条，移除10/新增0，仍FAIL。显式约束编辑及别名、依赖闭包保持一致，union逐项分组。正式性能仍失败，最终候选与维护者结论未代验收。

2026-10-23 最新源码 `459c0cd7` 的[合成接口实现声明修复](../evidence/2026-10-23-synthetic-contract/README.md)通过完整五节点检查（5141 tests / 271 skipped / 零失败）与五个新安装服务端运行，全部正常退出0。1609个默认Python stub通过；完整严格TSC降至5575条，移除44/新增0，仍FAIL。原始Method的Hide/remap保留，普通synthetic/bridge仍过滤；没有猜测恢复Mixin擦除的泛型方法签名。正式重载仍以340.0 / 292.8 ms超标为准，最终客户端/TCP/分离回退与维护者结论继续开放。

2026-10-23 新源码 `88e18d84` 的[五节点新安装与完整 IDE](../evidence/2026-10-23-registry-installed/README.md)全部运行通过、五进程正常退出；1609 个默认 Python stub 检查零错误/警告。完整严格 TSC 为 5619 条、十入口 exit2 无崩溃，相对 5857 移除244/新增6，继续 FAIL；新增为 Fabric 擦除/继承关系的进一步诊断，未隐藏或放宽。该批新 JAR 与全部编译类/源码绑定，最终客户端/TCP/回退及正式性能仍按原范围开放。

2026-10-23 [票 28 本域工程收口](../evidence/2026-10-23-posteffects-closeout/README.md)已完成：精确删除条件、安装渲染、声明 parity 和旧消费者核对满足，本域 14 个源码与已验收组件一致、相关 93 项当前测试通过。票 28 的 closed 不替代本票最终候选、完整 TSC、性能、分离回退或维护者发布结论；本票仍 in-progress。

2026-10-23 共享修复后续：`c0bc2dbe` 的[raw 泛型 TS 输出](../evidence/2026-10-23-raw-generic-output/README.md)通过先红后绿、三模式严格消费和 common/check（2420 tests / 4 skipped / 零失败，含 isolation）。`b47734c6` 的[两节点 COMMIT 后失败恢复](../evidence/2026-10-23-dynamic-commit-recovery/README.md)完成各七阶段及六进程正常退出。后续 `88e18d84` 注册表目录修复已完成五节点 48 项定向回归、完整定向 TS 链和两轴零剩余项复审；[完整五节点检查](../evidence/2026-10-23-registry-catalog/README.md)已通过（5140 tests / 271 skipped / 零失败错误，含 isolation/platform/artifact）；该新源码安装后的全图 IDE 已单独核验为 5619 条，完整 TSC 继续失败；性能仍以 `7bea6d6d` 的 340.0 / 292.8 ms 失败为准，不据局部通过关闭整票。

2026-10-23 后续局部修复 `1f877b0f`：[实际事件签名依赖](../evidence/2026-10-23-event-dependencies/README.md)交给现有 TS 闭包，保持 exclude/hide，不收集未输出的 dispatch key。两项回归先红后绿；最终 common/check、隔离及 guard 通过（2416 tests / 4 skipped / 零失败），完整定向 `test:probe-types` 通过；两轴复审最终零未解决项。新源码尚未重新绑定五节点完整安装 IDE、正式性能或最终客户端/TCP/回退，票仍 in-progress。[加载细分诊断](../evidence/2026-10-23-loader-preparation-diagnostic/README.md)完成两轮十次 COMMIT 与正常停止，未据小占比缓存或改变初始化次序换取通过。

2026-10-23 当前生产源码 `7bea6d6d` 的[完整新候选检查](../evidence/2026-10-23-current-candidate-checks/README.md)已完成：common/check、处理器、guard 与五节点构建通过，5070 tests / 271 skipped / 零失败；五个新安装服务端实际运行全部通过并正常退出 0，包含 26.1.2 实体查询及重载前后效果。测试服显式禁用空服暂停，旧候选首次失败仍未确诊。全部 1609 个新 stub 的默认 Pyright 通过；十入口新生成声明的完整严格 TSC 仍为 5867 条、无崩溃，继续 FAIL。[确切同一 26.1.2 JAR 的正式性能](../evidence/2026-10-22-source-facts-performance/README.md)仍为重载 340.0 / 292.8 ms 失败、启动 33486.6 ms 通过。后续最终候选的客户端/TCP/分离回退和真实维护者结论保持各自范围，未因本轮构建及服务器成功而关闭。

2026-10-22 后续：[`4405b0b0` 三节点安装客户端](../evidence/2026-10-22-unified-client-native/README.md)完成 30 图和六次正常退出；[制品/数据分离回退](../evidence/2026-10-22-candidate-rollback/README.md)完成已知 `d91ca974 → 4405b0b0 → d91ca974` 与停服备份恢复四阶段、四次正常退出。换旧 JAR 保留推进数据，恢复备份才回到旧值，范围仅该已知候选对和完整世界夹具。五节点首轮安装运行四过一未过（26.1.2 召唤后实体查询未成功），该失败与后续控制实验分别保留；不能用正常退出或构建通过覆盖验收失败。最新默认完整 TSC 仍失败 5867 条；Python 默认检查仍通过。性能与最终维护者结论继续开放。

2026-10-22 阶段候选 `4405b0b0` 全量矩阵通过：11m41s，106 tasks / 40 executed，5067 tests / 271 skipped / 0 failures or errors，含 common 隔离、处理器、guard、五节点 build/platform gate；矩阵 XML 与生产 class/JAR 绑定在本地冻结。另有 [legacy 安装客户端 16 阶段](../evidence/2026-10-22-posteffects-legacy-installed/README.md)通过，实际使用普通 client JAR 与 server 分类 JAR。五节点安装运行、完整 IDE、原门槛性能及分离回退仍按各自结果记录，不能用构建通过代替。

2026-10-22 补充：[双节点真实 TCP 验收](../evidence/2026-10-22-dynamic-tcp/README.md)以冻结安装候选 `d91ca974` 在 26.1.2/26.2.0 完成各八阶段、八个进程正常退出，覆盖真实双 ACK/NACK、配置补同步、超时与迟到 ACK、准备中断线和重连后的实际注册表。它不替代后续源码的统一候选、COMMIT 后激活失败、完整 TSC、最终性能、分离回退或维护者结论；失败尝试及外部清理记录已封存。

**What to build:** 在全部 P4 gate 通过后汇总公开迁移材料，核对各域旧路径已按删除条件收缩，完成最终版本切换与本地候选制品准备；仅处理已证明无调用者的少量残余过渡项，不在验收后启动大范围清理。任何影响候选制品的清理或版本包装后，重新验证确切产物；不执行远程上传。

**Blocked by:** [34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md)、[35: P4 性能复测与政策对照](35-release-perf-compare.md)、[36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md)

**Status:** in-progress（2026-10-28：3f9f1b0a新安装十入口严格TS 973，对11c9a180完整诊断块−24/+0，F13降至12，F1/F2/F4/F8仍为零；获批15处SectionPos迁移已完成。完整严格TS仍FAIL，host结构身份、JS数组桥异常及其余族开放。d06cd961原政策性能PASS不外推新候选；最终客户端/TCP/分离回退、Python默认复验、内存预算、包装与维护者/发布门禁未完成。）

**Assignee:** main-session agent（stonecutter；写集：必要源码/测试修复、迁移与数据保护交接、票据和证据记录）

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示本地候选制品、迁移材料、验证证据和交接清单可由 agent 准备；每个 public breaking symbol 的维护者确认、最终发布/回滚策略确认和正式发布授权是不可代答的后续门禁。agent 不得替维护者 sign-off、执行远程上传或渠道公告，也不得勾选依赖维护者确认的验收项，且不因此把本票改判为 `ready-for-human`。

**Work items:**

- P4
- W10

## Acceptance criteria

- [ ] 每个 Script/Plugin public breaking 符号都有旧写法、1.2.0 新写法、替代路径、数据影响和维护者确认；没有迁移项被可选工具替代。
- [x] 数据保护清单覆盖 config、world、实体/玩家 pdata、脚本与 pack、trust-store、workspace/declaration、日志与 cache 的可再生性、备份、保留和回滚。【2026-10-07：`evidence/ticket37-autonomous-closeout/DATA-PROTECTION.md`；当前路径/owner核对及bounded rehearsal，未冒充实际用户数据恢复。】
- [x] 当前已知候选对的release rollback与data rollback分开验证；旧制品回退不宣称恢复脚本对外部世界、网络或Java对象造成的副作用。【[6e确切候选四阶段](../evidence/2026-10-24-owner-stamp-rollback/README.md)：d91ca974旧JAR仍读到推进后的pdata/金块，恢复停服备份后当前JAR才回到旧值；四正常退出。仅26.1.2完整世界夹具与该已知版本对，不代签任意旧版、世界外副作用或维护者最终回滚政策。】
- [ ] 必要数据迁移具备备份或原子替换、版本/schema 标记、旧 fixture 回读、幂等验证、失败恢复和原始数据保留证据；无必要变化的默认路径、key、wire id、格式和启用规则未被暗中改变。
- [ ] 各功能域迁移票已按自身删除条件移除旧 public route、compat shim、重复 runtime path 或第二语义 pipeline，并完成对应域验证；本票只复核无残留，并只删除已被调用者证据证明为无调用者的小量过渡项，不在验收后临时扩大代码清理。
- [ ] 若最终清理、版本切换或发布包装改变确切候选制品，则对该候选制品重新执行必要 build、artifact、metadata、runtime smoke 和性能相关验证；旧制品证据不能冒充最终证据。
- [ ] README、wiki、ADR 与支持/能力矩阵的发布说明一致，公开说明 Fabric processor 延期、WORLD pack 差异和其他 partial/unavailable 能力。
- [ ] 各 API/功能域的最小可运行示例、迁移材料和 cookbook 已随对应实现票交付并通过验收；本票只汇总、核对链接和修正发布索引，不首次补写域内材料。
- [x] 可选离线 validator/migration report 保持显式运行、默认只读且不是 release blocker，也不进入普通 runtime 错误路径或第二套 Script API。【票38技术验收，现有CLI3tests再次通过；无runtime接线。】
- [x] 当前工程候选在本地完成准备、校验和交接清单核对。【2026-10-24：[6e43b827五节点六制品](../evidence/2026-10-24-owner-stamp-handoff/README.md)，完整SHA与生产类核对通过；显示版本仍1.1.0-preview3，releaseReady=false，不代替下项最终发布产物。】
- [ ] 全部前置门禁通过后，最终1.2.0发布产物在本地完成准备、校验和交接清单核对；版本切换或包装后重验确切制品。远程上传、渠道公告或正式发布动作未在本票执行，需维护者另行明确授权。

## Current handoff record

2026-10-16 [泛型适配器实际复测](../evidence/2026-10-16-followup-acceptance/GENERIC.md)：`f3f32a69` 保留泛型/bound 和编辑后 IR；完整构建 4913 tests（271 skipped，0 failures/errors）、五节点新 JAR 实机、默认全部 Pyright 与定向严格 TSC 通过。完整默认 TSC 移除 40 条裸泛型错误但新增 930 条约束错误，净增 890，未按完整声明或无新增诊断验收。原始失败和 `$Bat`/`$Entity` Bean/方法投影链保留，继续开放。

当前交接沿用[本地清单](../evidence/2026-10-06-ticket37-handoff/README.md)和[技术收尾计划](../evidence/ticket37-autonomous-closeout/WORKPLAN.md)。票 36 维护者试做与票 48 JSX proof 是已完成输入。

2026-10-15 A15/A16/A22 的精确范围已获维护者明确批准；替代、消费者迁移和五节点官方 JAR 实机通过后删除，见[领域切换报告](../evidence/2026-10-15-domain-closeout/REPORT.md)。A28 既有精确追认继续有效，不重复申请。该批准不包含正式发布和最终回滚政策。

[当前性能索引](../evidence/ticket37-current-perf/README.md)中的 e473 源码组 startup 17718.2 ms、reload 214.4/228.4 ms 均通过；b7 的 320.6/327.2 ms 失败组保留为历史记录。e473 结果不证明本轮改动后的候选性能。本轮五节点安装包的 Python stub AST 全通过；完整默认严格 TSC/Pyright 的结果与诊断另列于领域报告，不以定向类型测试通过替代。历史 Python 语法失败已由后续修复取代，但原始失败证据保留。

2026-10-16 [复核修复与完整声明验收](../evidence/2026-10-16-followup-acceptance/REPORT.md)：重复动态物品声明保留既有 holder 组件，新旧 JAR 实机对照成立；插件同名 Builder 嵌套属性改用唯一导出别名。修复 Python 同类实例字段/方法重名后，五节点实际全部 stub 的默认 Pyright 均为 0 error / 0 warning，完整构建和五节点新 JAR 实机均通过。严格 TSC 仍有上轮同量诊断，完整 IDE gate 未通过。

[分离回退的四阶段重启](../evidence/2026-10-16-followup-acceptance/ROLLBACK.md)通过：旧 JAR 启动推进后的世界仍读取新 pdata/方块状态，恢复完整停机备份后实际读取回到旧状态。该证据限定领域切换时的指定 JAR、26.1.2 专属完整世界；不证明其他节点、未知旧制品来源或最终发布候选的恢复政策，因此不据此代签最终 release rollback gate。

[26.2 首帧像素验收](../evidence/2026-10-16-followup-acceptance/FRAME.md)已补：资源重载前后和恢复后的首个完成渲染的世界帧，以及失败后未再次 set 的保留画面均有实际 PNG、帧边界和 ROI 证据。仅覆盖 .75 / 0965 指定制品的 render target，不代替其他客户端节点或屏幕 present。

[0965 当前性能复测](../evidence/2026-10-16-followup-acceptance/PERFORMANCE.md)：startup 28118.4 ms 通过，reload 408.6/431.8 ms 超过原 285.3 ms 门槛，候选仍阻塞。旧通过 e473 在当前环境的两组对照也为 386.4/404.4 ms；全部样本、正常退出与定向 JFR 已归档，尚未建立单一源码原因，不以环境推测或历史通过结果消除当前失败。

跨进程动态同步、票 28 多节点客户端首帧、完整 TSC、确切候选性能、最终候选的 release rollback/data rollback 和最终发布/回滚策略仍按各自 gate 核验；领域服务器验收不外推为发布通过。

[枚举继承修复与实际声明复测](../evidence/2026-10-16-followup-acceptance/ENUM.md)：`489b4a6b` 的五节点新 JAR 实机、完整构建和全部默认 Pyright 通过。完整严格 TSC 的 server/startup 诊断分别为 572/573、725/726、619/620、1148/1149、903/904，十入口共减少 160 条且无新增诊断，仍未通过。所有 Java 类 Python stub 字节一致；入口/事件的顺序和本地导入别名差异保留，不能据此宣称完整输出确定性已通过。草稿 PR 为 [#67](https://github.com/NekoJS-Dev/NekoJS/pull/67)，保持草稿且不执行正式发布。

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [PR 37 维护体验回归约束规格](../specs/00-pr37-maintainer-research.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [平台构建与 Stonecutter 策略规格](../specs/03-platform-build-strategy.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md): 五节点、能力、契约、smoke 和 coverage ledger 的最终 release 判定必须已通过。
- [35: P4 性能复测与政策对照](35-release-perf-compare.md): 最终发布必须具备按已确认政策完成的 P4 复测、基线对照和失败处置结论。
- [36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md): 维护者四类任务、脚本作者代表性任务和维护 cookbook 是 release gate。

## Scope and coordination

**Rationale:** 它是所有技术 gate 之后的单一发布交接边界，能完整审计 clean cutover，同时明确不自动获得远程发布授权。

**Coordination:**

- 主整合 owner 汇总本票 Blocked by 已列出的真实先决票并提供各域完成证据；维护者审阅迁移、阈值、试做和最终发布决定。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

后续入口路径修复 `6faed0e9` 及正式性能复测见 [报告](../evidence/2026-10-16-followup-acceptance/RESOLVER.md)。4916 tests、五节点实机及 Pyright 通过，完整 TSC 保持失败；重载 335.4/376.6ms 超过原阈值 285.3ms，启动通过。票据继续开放，没有代签维护者或发布验收。

`569376c6` 恢复擦除返回值的接口契约：[五节点报告](../evidence/2026-10-16-followup-acceptance/RETURN-CONTRACTS.md)。4921 tests、实际运行及 Pyright 通过，完整 TSC 无新增并减少 118 条 TS2314，仍未通过整体验收；Python 普通 host 泛型实参投影仍有限制。

最终 `04550ae2` 的 [TS raw 输入修复](../evidence/2026-10-16-followup-acceptance/RAW-INPUT.md)完成 4923 tests、五节点实际运行、完整 Pyright 和严格正负例；完整 TSC 再减少 160 条 TS2314、无新增，仍失败。Java 类 Python stub 与上批逐字节一致。本批未执行 Entity Bean 删除；后续批准与独立结果见下。


用户明确批准五处 Entity 公开声明精确迁移后，最终 `e551e559` 完成 [实际验证](../evidence/2026-10-16-followup-acceptance/ENTITY-BEAN.md)：4933 tests、五节点新 JAR、完整 Pyright、严格正负例及 CI 通过，恰好删除五处派生属性、保留其他 24 处、真实方法与运行时/Python。完整 TSC 减少 1560 条且无新增，仍失败；最近正式性能及其他 release gate 继续开放。本次精确批准不代表整票维护者或发布验收。

2026-10-17 用户批准扩大 TS 声明依赖输出后，[依赖补齐报告](../evidence/2026-10-17-tsc-closeout/REPORT.md)记录：主扫描仍受原配置限制，TS 单独补齐真实引用并保留 exclude/hide、编辑后 IR 与运行时权限；4949 tests（271 skipped）、五节点实际运行、全部默认 Pyright 和定向严格类型正负例通过。完整默认 TSC 的缺失模块 TS2307 从 3433 减到 29，但诊断总数从 6991 增至 7071，完整 IDE gate 仍失败。默认依赖报告如实保留排除类型与专服不可装载类型；没有删除其他公开成员或代签维护者结论。本批不创建 PR，性能、客户端和最终候选回滚门禁继续开放。

同日继续完成 [Character 值类型投影](../evidence/2026-10-17-tsc-closeout/CHARACTER.md)：4952 tests（271 skipped）、五节点新 JAR、全部默认 Pyright 和严格正负例通过，完整 TSC 移除 204 条且没有新增，仍有 6867 条，整体验收未通过。保留原始 IR、Python 和运行时转换，记录泛型擦除与 1.21.1 旧 Guava 谓词限制；首次实机失败、旧 JAR 复现及最终各节点正负能力检查均封存，没有把原有拒绝行为宣称为已修复。不创建 PR，其他发布门禁继续开放。

2026-10-21 [Probe 共享原因与新制品复测](../evidence/2026-10-21-probe-closeout/README.md)：`dd15186c` 完成多父接口继承签名合并、显式手写 host/input 依赖和动态物品布尔输入校验，5056 tests（271 skipped）、五节点新 JAR 实机、1609 个 stub 的默认 Pyright 与定向严格消费链通过。十入口完整严格 TSC 从 6867 降至 6523，按首行诊断多重集移除 344 / 新增 0，所有 TS2320 消失；完整 IDE gate 仍失败。`ManualDeclarationCatalogEntry` 保留旧构造但增加 record 组件，六组件 record pattern 源码需要迁移，已在报告和插件指南记录。本阶段不替代剩余客户端、多客户端动态同步、最终候选性能与分离回退，不代签发布决定。

2026-10-21 [PostEffects 三节点原生补验](../evidence/2026-10-21-posteffects-native/README.md)：legacy 源码 `8798257f` 修复原生资源重载丢失效果及 stale 退役误清原版链，最终节点构建 447 tests / 31 skipped 零失败，三节点 30 张世界目标图与原生 debug 重载通过并逐张复核。legacy 两个实际红测、依赖缺失及开发警告阻挡尝试均保留；现代节点仍绑定 `dd15186c`，开发客户端与安装候选 class 匹配，不冒充正式安装客户端启动。票 28 的节点 smoke 条件已补齐；正式统一候选、多人动态 TCP、完整 TSC、性能和分离回退仍开放，不代签整票或发布验收。

2026-10-22 [静态继承与安装客户端补验](../evidence/2026-10-22-static-inheritance/README.md)：中间 JAR `d91ca974` 的完整 5066 tests / 271 skipped、五节点新安装运行与全部默认 Pyright 通过；完整 TSC 为 5867 条，移除 594 / 新增 8（SectionPos 既有 Bean/方法冲突改报 TS2430），仍失败。26.1.2 独立开发启动器已确认 NekoJS 从安装生产 JAR 加载，七阶段原图与正常退出通过。后续源码 `ad4d3750` 修复父重载编辑权威及缺失参数选择器的无操作边界，common/check 2411 tests / 4 skipped 与严格定向消费、两轴复审通过；尚未以该源码重建最终五节点候选。其他节点安装客户端、完整 IDE、真实多人动态 TCP、最终性能与分离回退继续开放。


2026-10-22 阶段统一候选 `4405b0b0` 的[正式性能复验](../evidence/2026-10-22-candidate-performance/README.md)已完成：两组重载均值 348.2 / 332.2 ms，均超过原 285.3 ms 门槛；启动均值 28026.2 ms，通过 41173 ms 门槛。17 条样本有效，9 次会话均正常退出；原采样器、fixture 和门槛不变。归档复核通过只表示原始记录及实际失败可复算，不表示性能验收通过。该结果取代旧候选在本轮交接中的性能状态；仍须定位并修复当前重载超标后按同一标准复验。


2026-10-22 统一候选 `4405b0b0` 的[完整检查归档](../evidence/2026-10-22-candidate-checks/README.md)：common/check、处理器、guard 和五节点构建通过，5067 tests / 271 skipped / 零失败。安装首轮四节点通过，26.1.2 实体查询未完成；同一 JAR 的独立不暂停控制组严格全链通过，但首次原因未确诊，不能改称首轮五节点全绿。六个服务端均正常退出。1609 个 stub 的默认 Pyright 全通过；十入口默认严格 TSC 仍有 5867 条诊断、无崩溃，完整 IDE gate 继续失败。


2026-10-22 后续源码 `7bea6d6d` 的[纯源码事实复用检查](../evidence/2026-10-22-source-facts/README.md)通过 common/check、处理器、guard 及 26.1.2 build（3113 tests / 85 skipped / 零失败）；其余四节点尚未重建，不沿用旧候选结果。相同采样政策的[正式性能复验](../evidence/2026-10-22-source-facts-performance/README.md)：两组重载 340.0 / 292.8 ms，均仍超过 285.3 ms；启动 33486.6 ms，通过 41173 ms。17 条有效样本、9 次正常 RCON 退出，1640 个类及 JAR 与主检出一致。源码复用已实现，但重载性能门禁仍失败；不以第二组接近门槛或历史通过代替本轮结论。完整严格 TSC 的最近冻结结果仍为 `4405b0b0` 的 5867 条诊断，当前源码未重跑完整 IDE 与五节点运行，不声称已经完成最终统一候选。


2026-10-23 [重载节奏诊断](../evidence/2026-10-23-reload-cadence-diagnostic/README.md)已封存：启动即录制 JFR，Done 后按 3 秒间隔执行 5 次真实重载；只统计命令窗口，入口预检合计 2.92 ms、模块求值 53.21 ms、配方条件过滤 1.69 ms、配方解码 59.86 ms，root 103.21 ms、命令 172.23 ms。此为有诊断开销的阶段测量，不能替代正式 340.0 / 292.8 ms 的失败。15 个 root / 17 个配方执行样本不足以锁定单一叶函数；未跳过当前授权、schema、插件事件或配方解码。纯编译 Source 实验的中位收益仅约 3 ms，未据此改变 CommonJS 语义。两次诊断启动失败保留，成功运行正常 RCON 退出，五处临时源码恢复后 1640 个类和 JAR 与正式候选逐字节一致。完整五节点构建后续另记，不外推旧制品。
