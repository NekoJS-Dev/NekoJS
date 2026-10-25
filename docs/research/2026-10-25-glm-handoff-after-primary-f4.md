# GLM 接手任务书：主代理完成 F4 后

日期：2026-10-25。仓库 `D:/mcmodDemo/NekoJS-mult`，分支 `stonecutter`。

工程交付基点：`dfbcdaa0d5f713ae3f0ae3cad09d554c843d7588`；本任务书之后的纯文档提交不改变下面的产品基线。开工先查看实际 HEAD 和 diff，保留后续工作。

## 1. 先读这些，再开始改代码

- `AGENTS.md`、`CONTEXT.md`、`docs/agents/coding.md`。
- [本轮冻结交付记录](../architecture-refactor/evidence/2026-10-25-primary-erased-method-dad806fa/README.md)。这是当前工程验证入口；旧阶段记录保持历史范围。
- [完整严格 TS 根因表](2026-10-25-strict-ts-root-causes.md)。开头表格是旧 1609 基线，当前数值以本轮证据的 `root-cause-summary.json` 为准。
- [本地票 37](../architecture-refactor/implementation-tickets/37-release-1-2-0-handoff.md)。仍是 in-progress，没有维护者整体接受或发布授权。

目标：继续解决完整严格声明问题和公开合同缺口，逐阶段验证、提交；不能把定向类型检查通过或错误减少当作整票完成。

## 2. Codex 已完成的工作，不要重做

| 提交 | 内容 |
|---|---|
| `a8c0f274` | 将启动后进程身份查询放进 owned 子进程回收范围 |
| `555ee037` | 保护启动/状态输出窗口，中断回收后原样传播，清理失败另记且不遮盖原错误 |
| `dad806fa` | 从同擦除且无歧义的接口来源恢复完整方法泛型合同 |
| `dfbcdaa0` | 冻结安装、诊断、矩阵及产物绑定，更新票据与 F1 未完成状态 |

F4 的实现位于 `common/src/main/java/com/tkisor/nekojs/probe/ir/TypeReflector.java`：

- 只处理签名确已擦除的实现，要求接口参数和返回值擦除完全一致。
- 恢复方法类型参数、界限、参数类型及返回类型；等价变量按位置比较，冲突则保留原路径。
- 保留实现 sourceMethod、隐藏/remap 和原有类型信息，不按 Fabric 类名或方法名列补丁。
- 回归在 `TypeReflectorReturnContractTest`，严格消费已接入 `npm run test:probe-erased-method-contract` 和总链。

驱动在 `bench/tsbaseline/run.py`；回归 `bench/tsbaseline/test_run.py` 现有四项：身份超时、中断、启动输出断管、清理失败。回收/摘要/异常链均有检查，全部是假进程控制。

## 3. 当前经过实测的状态

产品候选：`dad806fa`。安装目录：`build/tsbaseline-primary-f4-dad806fa`。

- 五节点新安装全部 Probe 完成、RCON 正常停服、exit 0、没有强杀、锁已释放；文件数 394/448/430/540/519。
- 完整严格 TS：**1503 → 1409，移除 94、新增 0，仍 FAIL**。
- F4 **80→0**；原尾项 **26→12**。额外 14 条来自 fabric_enumCycle、Builder.get/getOrCreate、MinecraftServer.getOrThrow 的同类方法泛型信息丢失。按旧互斥口径保留在尾项差分，不重复计入 F4。
- common/check、处理器、guardLint、五节点 build 通过；冻结矩阵 **5183 tests、271 skipped、0 failures/errors**。
- common/test 为 2466 tests、4 skipped，已包含于总数；`TypeReflectorReturnContractTest` 为 8 项。
- `npm run test:probe-types` 全链和四项 CI gate 通过；没有更改 golden。
- 新五节点 Python stub/Pyright、当前产品的正式性能、最终候选客户端/TCP/分离回退/版本包装 **未重跑**。

| 节点 | 当前 server / startup 严格诊断 |
|---|---:|
| 1.21.1 NeoForge | 102 / 103 |
| 26.1.2 NeoForge | 154 / 155 |
| 26.2.0 NeoForge | 148 / 149 |
| 26.1.2 Fabric | 152 / 153 |
| 26.2 Fabric | 146 / 147 |

复算冻结证据，无需启动游戏：

```powershell
python -X utf8 docs/architecture-refactor/evidence/2026-10-25-primary-erased-method-dad806fa/verify.py
```

该 PASS 只证明证据一致，不能改写为完整严格 TS PASS。ZIP 含前后十入口原文、完整分类、运行日志和矩阵 XML；密码、配置/世界及完整进程命令行没有入包。

## 4. 第一优先级：F1 的公开合同仍未解决

**不要再次交付“排除方法有失败实验，所以已修好”的结论。**

现有 `$OptionalView` / `$StreamView` 已恢复 sorted 双签名、三参 reduce、supplier collect、spliterator，以及上轮指出的 orElseThrow/present/filter/递归 stream。这些进展要保留。

但当前仍省略：

- `Optional.or`。
- `Stream.reduce(BinaryOperator)`、`reduce(identity, BinaryOperator)`。
- `Stream.collect(Collector)`。
- `Stream.toList()`。

代码/测试入口：

- `common/src/main/java/com/tkisor/nekojs/probe/ir/TypeScriptClassRenderer.java`：捕获视图及成员选择/变换规则。
- `common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/IndexFileGenerator.java`：视图声明与导入。
- `common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/FunctionalInterfaceAliasGenerator.java`、`probe/types/TypeAliasRegistry.java`：host/callback 输入方向。
- `common/src/test/java/com/tkisor/nekojs/probe/ProbeWildcardOptionalCaptureTest.java`：有些缺失成员仍被写成负例。

要求：

1. 从真实 Java 捕获关系和合法调用建立最小正例，再决定表示；不能仅把被点名方法补回来，继续任意缩减其它成员。
2. 同时证明具体实现到接口/捕获视图的赋值、窄返回和上界读取，以及合法 host/SAM/callback 输入；错误载荷、错误结果、无依据的显式泛型窄返回必须继续拒绝。
3. 考虑 Optional→Stream→List 等递归关系和方法局部变量，保留 Bean、隐藏/排除、编辑后的 IR 权威及普通成员顺序。
4. 全局协变、any/unknown/never 兜底、空声明、skipLibCheck 或删成员不是解法。已被反例否决的 raw-first 重载不要重新引入。
5. 若没有安全方案，交付可复现反例和明确未解决关系，保持 F1 开放；不要用更多例外规则宣称完整收口。可以推进其它有独立来源证明的族，但不得掩盖 F1 的公共合同缺陷。

F1 剩余的 **50 条诊断**（Double/Float/Integer/Long/String ×十入口）还涉及 number/string 值投影与 ConstantDesc host 合同。它和“视图漏方法”是两个验收维度；诊断减少不能替代 API 保持。不要未经新证明就写成 TypeScript 永远不可表达。

## 5. 后续共享根因与当前计数

以下来自本轮分类；F4 已是 0。先找真实签名和最小叶因，禁止逐类硬编码。

| 族 | 当前数量 |
|---|---:|
| F1 Constable/Optional | 50，另有上述公开合同缺口 |
| F2 java.time chrono 互环 | 330 |
| F6 Recipe 约束 | 488 |
| F7 ConsumeEffect 约束 | 160 |
| F5 codec/getType | 160 |
| F3 entity 容器/重载 | 52 |
| F8 entity/stream | 32 |
| F13 Bean/方法形状 | 36 |
| F10/F11/F12 缺失类型/模块 | 46 / 31 / 8 |
| F14 Fabric pdata 成员 | 4 |
| 尾项 | 12 |

除 F1 外，可以优先调查 F5/F7 的共同叶因，再处理 F6/F2 或证据最充分的独立小族。不能仅据 TS2416 的元数提示就认定缺重载；要追到真实不兼容叶子。缺失模块类不得通过解除 hide/exclude、放开 core/运行时权限或空 stub 消音。

共享 IR 恢复也影响 Python 声明，所以后续应补当前确切产物的默认 Python 检查，而不是沿用旧 1609 stub 的成功记录。

## 6. 实施与验收流程

1. 先看 diff、受影响入口/调用者及相关测试，再改 canonical 源码；测试沿用反射/公开 backend/严格消费和驱动命令的既有接缝。
2. 小范围先红后绿，保存有效红证；编译错误、fixture 导入错误不冒充产品行为红证。
3. 实现收敛前只运行对应的集中检查，避免每个小改动都重跑完整游戏。收敛后运行 common/check、完整定向链、相关节点/CI gate。
4. 新候选固定本地 commit、源/编译 class/JAR 摘要，再建立新目录重采五节点十入口；不要覆盖本轮已冻结目录或改标签套用旧结果。
5. 按入口、包路径、错误码和诊断首行的多重集比较，记录 removed/added/remaining；分类需互斥且能重算，未知项显式失败。
6. 最后复审完整 diff，分别报告 Standards/Spec 与已通过、失败、未运行项，再阶段性本地 commit。

可用命令与工具：

```powershell
python -X utf8 -m unittest discover -s bench/tsbaseline -p test_run.py
.\gradlew.bat :common:check :common-api-processor:test guardLint --console=plain
npm.cmd run test:probe-types
python -X utf8 tools/nekojs-ci-gates.py all --out build/<new-tag>-gates.json
python -X utf8 bench/tsbaseline/run.py prepare --target build/<new-tag> --revision <commit> --base-port <free-base-port>
python -X utf8 -u bench/tsbaseline/run.py run --target build/<new-tag>
python -X utf8 -u bench/tsbaseline/run.py check --target build/<new-tag>
python -X utf8 bench/tsbaseline/classify_diagnostics.py --ide-dir build/<new-tag>/ide --json build/<new-tag>/mapping.json
```

prepare 的前置是对应五节点 JAR 已构建；它按产物时间选 JAR，revision 参数不是自动源码绑定证明。新端口先检查可用，记录实际制品和加载来源。完整构建命令见冻结交付记录。

## 7. 不能外推的门禁与操作限制

- 原政策性能 PASS 仍只绑定 `d06cd961`：重载 219.8/214.0 ms，门槛 285.3；启动 20340.4 ms，门槛 41173。未来最终候选需按原政策复测，不能择优或改条件覆盖历史 FAIL。
- 实现收敛后才进入最终候选的客户端/PostEffects/TCP/制品与数据分离回退、1.2.0 本地包装。更换旧 JAR 不等于恢复世界数据。
- 维护者真实接受、发布/回滚政策与发布授权不能由代理代签。
- 不 push、不创建 PR、不上传或正式发布、不代发评论或关闭远端 Issue。内部计划和证据写仓库。
- 主代理自己实现、验证和 commit；子代理仅只读调查/审查，不改任何文件。
- 新源码注释、开发日志、异常说明用英文；文档按现有中文风格。保留隔离、版本边界、生命周期与用户数据。
- 交接前 tracked 工作区干净；既有未跟踪的 `.memsearch/`、工具目录、旧审查文档和证据图片均保留。不要全量 add、reset、clean 或删资料来取得干净状态。

最后交付：本地提交列表、确切候选绑定、实际验证命令和原始结果、诊断逐条增减、公开合同变更说明、剩余问题。完整严格 TS 或公开合同任一未满足，票 37 都保持开放。
