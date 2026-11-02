# GLM §1/§2 复核与下一阶段任务

审查日期：2026-10-25。仓库 `D:/mcmodDemo/NekoJS-mult`，分支 `stonecutter`。固定范围 `370e8105...ff6e0558`，涉及 `d38c14dd`、`d06cd961`、`ff6e0558` 三提交。Codex 派出三名 GPT-6-luna xhigh 子代理，只读分别核对代码、性能证据与严格 TS 下一步；主代理独立重算性能样本并核对制品。没有修改产品代码或重跑构建、测试、游戏。

## Standards

没有发现新的确定性产品缺陷。混合引号修复记录具体开引号，仅同类引号闭合，保留原有转义处理；新增回归经过公开 getter override 和生成器路径，验证 opaque import 原文保留及私有导入别名避让。

一项小验证缺口：新用例生成声明并断言文本，但没有相应 tsconfig，`npm run test:probe-types` 也未消费该目录，因此不能声称这份混合引号产物已经通过严格 TS 编译。请补齐一个窄范围消费用例，提供合法的 `"a'b"` 导出 fixture，验证生成声明与调用能够严格编译；避免把原 fixture 中未提供的导出直接当作合法模块。此项不是发现代码修复失效。

主代理读取现存 `TypeScriptSameNameHeritageTest` XML：9 tests、零失败/错误，包含新测试。这是对保留结果的检查，不是本次重新执行测试或独立重现历史 red。

## Spec

§1 的代码修复和两处摘要标注修正已落地。§2 对所测候选支持 PASS，可以结束当前的性能排查阶段，继续 §3。不能将一次当前 PASS 推导为历史波动根因已经查明，也不能预先覆盖未来最终候选。

主代理从提交内原始 JSONL 重算：

| 组 | 正式样本 | 均值 ms | 原门槛 ms | 结果 |
|---|---|---:|---:|---|
| reload-1 | 301、137、163、250、248 | 219.8 | 285.3 | PASS |
| reload-2 | 335、122、246、121、246 | 214.0 | 285.3 | PASS |
| startup | 20180、20989、21110、20475、18948 | 20340.4 | 41173 | PASS |

17 行记录包含 startup 的两次预热，共 9 个 PID，未标记强杀或超时。子代理核对九份 stdout 均有正常停服/RCON Listener 停止及 BUILD SUCCESSFUL，冻结 JSONL 与工作树原件逐字节一致。保留性能工作树中的实际 JAR SHA256 与记录一致，含 1648 个 class，采样器 SHA256 亦一致。主代理另比较节点 474 个、common 1174 个编译 class，与 JAR 内全部对应项逐字节相同。测试源码绑定为 `d06cd961`；后续 `ff6e0558` 只修改文档/证据，没有产品源码变更。

绑定精度需如实说明：原采样器经 Gradle `runServer` 启动，并非安装该 JAR 启动。当前编译输出与 JAR 一致支持制品关联，但不能仅凭 JAR 摘要把开发运行改写为安装制品实测；最终候选仍需实际加载来源与 class/JAR 绑定。另外 reload JSONL 没有 `timeout` 字段，准确措辞为“未标记超时”，不是所有行都显式 `timeout=null`。

请同步票 37 的当前 `Status` 段：目前它仍列“原门槛重载仍 FAIL”，与最新条目不一致。保留所有旧日期的 FAIL 记录，当前状态改为“d06cd961 性能 PASS；完整严格 TS、最终候选和人工结论开放”。归因措辞采用“本轮未作额外性能源码修改而通过”，不要把源码检查写成对环境唯一根因的证明。

## 直接交给 GLM 的下一阶段任务

目标：推进 §3 完整严格 TS；第一批交付必须包含当前基线、根因分类和至少一个有真实签名依据的共享修复。不要停在“1609 条，建议另开阶段”，也不要先做最终客户端全矩阵或可选清理。

1. **补小验证并校正文档。** 为混合引号生成结果补严格消费检查，更新当前 Status。主代理实现验证，阶段性本地 commit。
2. **重建当前严格 TS 基线。** 用当前确切源码的五节点产物做隔离安装，实际生成十入口默认声明，保持 `strict=true`、`skipLibCheck=false`，保留完整诊断、命令、退出码、源/制品摘要。1609 是旧 v3 的诊断总数，先重采再报告当前数值；不要与 1609 份 Python stub 混淆。
3. **交付根因表。** 按错误码、来源 Java 类型/方法、继承/泛型关系和节点聚类，列各族数量、代表诊断、真实签名、拟改动的共享生成机制及正负消费要求。TS2416 的顶层 override 错误可能是深层类型不兼容的连带结果，要追到叶子，不能凭 arity 提示就补重载。
4. **选一族先修。** 选择证据最充分、能在小 fixture 中重现的共享根因。先红后绿，证明合法调用可用，错误输入、错误返回保证和显式泛型滥用仍被拒绝，再改 canonical 生成器并再生。未经证明的嵌套 wildcard/Optional 全局方案不要直接进入生产。
5. **验证并报告实际增减。** 跑相关 backend/严格正负消费、`:common:check` 含隔离及 `npm run test:probe-types`；按影响范围运行节点检查。实现收敛后，用新确切产物检查十入口全图，逐族记录 removed/added/remaining。若没有安全的修复，交付精确反例和可表达性边界，不能降低门禁或声称全票完成。

### 第一族建议：Constable → Optional 通配符与 SAM

只读调查在旧 v3 原始诊断中确认：十入口各有 14 条 `Constable.describeConstable()` 同族错误，共 140/1609 条。这是旧候选分布，必须用新基线重新确认。接口返回 `$Optional<$ConstantDesc>`，实现返回如 `$Optional<$DynamicConstantDesc<boolean>>`，诊断继续进入 `Optional.or` 的 Supplier 和 `filter` 的 Predicate。真实 JDK 签名是 `Optional<? extends ConstantDesc>`；`TypeReflector.toRef` 的 wildcard 上界压平是需要追踪的具体位置，不代表已经证明改这里即可解决。

第一批研究/实现范围限定为该族的真实来源签名与最小 TS 消费：验证 Boolean 作为 Constable、接口返回上界与具体返回视图的合法使用；错误 Supplier、Predicate 和无关类型仍应拒绝。检查 SAM 宿主输入方向、Optional 到 Stream 的递归关系及手工编辑后的 IR 权威。使用位置的捕获投影仅是待验证方向，不能未经正负证明就全局宣布 Optional 协变或引入 never/any。

既有来源调查见 `docs/research/2026-10-23-reload-declaration-findings.md`；应先阅读其中的限制，再决定 canonical IR/TS 后端的最窄共享改动。

### 可复用检查与安装流程缺口

现有定向入口为 `npm run test:probe-wildcard-collection`，Java fixture 为 `common/src/test/java/com/tkisor/nekojs/probe/ProbeWildcardCollectionTest.java`，完整定向链为 `npm run test:probe-types`。新安装生成配置后，逐一使用 `tsc -p <新安装 ide 目录>/<node>-server-tsconfig.json --noEmit` 和 startup 对应配置，五节点共十次。

本次调查未找到保存的 v3 五节点 fresh-install prepare/run 驱动；现有 event-dependencies 的 26.2 单节点脚本有自己的候选和证明范围，不能直接冒称通用五节点工具。GLM 应复用其实际可用流程并把这次准确的安装/生成/检查命令或驱动保留下来，让后续能重做；旧 `build/closeout-selector-backend-installed-v3/ide/` 仅作为历史诊断来源。

约束：不删除公开成员或泛型约束，不用全局 any/unknown、空 stub、hide/exclude 扩大或 skipLibCheck 掩盖诊断，不重新引入已否决的 raw-first 重载。既有原型已证明 raw-first 可能给显式泛型调用制造错误返回保证。沿用批准的真实字段/方法显式选择器规则，不再按旧 979+25+1 数量回退实现。

主要实现收敛后再执行 §4：冻结确切最终候选，完整五节点/客户端/TCP/制品与数据分离回退/1.2.0 本地包装，并确认原性能门禁对最终候选仍适用。保持无 PR、无 push、无正式发布、无代签维护者结论。

参考：[上轮任务书](2026-10-25-glm-next-tasks.md)、[v3 严格诊断证据](../architecture-refactor/evidence/2026-10-24-selector-installed-v3/README.md)、[既有共享根因与否决边界](2026-10-24-shared-cause-followup.md)、[本轮性能证据](../architecture-refactor/evidence/2026-10-25-perf-d06cd961/README.md)。
