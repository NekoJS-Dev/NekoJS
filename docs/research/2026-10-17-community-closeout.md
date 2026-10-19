# 社区五项提案核对：保留开放，区分分支实现与原 PR

核对日期：2026-10-17。实现基线为 stonecutter `6e5bb8f00c3747b828056589853cb0471477b18a`。Luna xhigh 仅作只读调查，主代理核对代码、既有验证和最新 GitHub 元数据。此文没有修改外部标签、评论、关闭状态或分支。

2026-10-19 再次通过 GitHub 连接器只读获取[五项当前状态](2026-10-19-community-snapshot.json)：#60/#63 仍 open、无标签；#61/#65/#66 仍 open、merged=false，目标仍为 master，三个 head SHA 与原核对一致。本次没有发送评论、设置标签、关闭报告或创建 PR。下表的合同差异继续保留；外部 RPC 集成及维护者结论依旧不能由元数据查询代替。

## 当前状态与处理结论

GitHub 连接器于 `2026-10-17T12:16:44.313Z` 读取的[状态快照](2026-10-17-community-snapshot.json)：两项 Issue 均 open，三个 PR 均 open、`merged=false`，目标均为 master。PR 的 `merge_commit_sha` 是预览合并提交，不能据此称已合并。下面标签是本地建议，尚未应用。

| 事项 | stonecutter 已有内容 | 剩余差异与建议 |
| --- | --- | --- |
| [#60 函数类型导出](https://github.com/NekoJS-Dev/NekoJS/issues/60) | 结构化识别 SAM、生成可接受 lambda 的输入别名；覆盖继承泛型、协变返回和 Comparator 的 Object 方法。 | 保留，建议 `ready-for-human`。能力已有实现及回归，但 master 同步及维护者结论尚缺；不自动关闭。 |
| [#63 自定义声明](https://github.com/NekoJS-Dev/NekoJS/issues/63) | 通过 Plugin Hook / Point 注册 Class 目标的声明替换，真实 backend 输出含 RPC 泛型推导的严格 TypeScript fixture。 | 保留，建议 `needs-info`。提议中的注解没有实现；需确认注册 API 是否满足作者诉求，外部 RPC 插件的实际注册/运行集成亦未验收。 |
| [PR #61 TS 擦除语料](https://github.com/NekoJS-Dev/NekoJS/pull/61) | 10-18 补齐精确 interface 箭头函数类型用例后为 59 项 parse corpus，加相关运行语义回归；没有沿用原提案中 19 项 assumption 中止。 | 保留，建议 `ready-for-human`。本地精确独立覆盖缺口已补；原提案 61 项没有逐字照搬，无效 typed for-of 改为合法 TS。master 验证和维护者结论仍缺，不能宣称原 PR 已完整合并。 |
| [PR #65 lambda 输入别名](https://github.com/NekoJS-Dev/NekoJS/pull/65) | 已有能力，并进一步处理 Object 方法、继承、重复方法和协变返回。 | 保留，建议 `ready-for-human`。不应直接套回原始较弱的 singleAbstractMethod 判定；master 仍需另行适配和验收。 |
| [PR #66 插件声明替换](https://github.com/NekoJS-Dev/NekoJS/pull/66) | Class 目标、全 Script Type 统一替换、优先级冲突拒绝、真实依赖闭包及 hide/exclude 约束，附 Point 和 backend 回归。 | 保留，建议 `ready-for-human`。原提案的 FQN 字符串和按 Script Type 替换与当前合同不同；共享 Java package 输出要求全类型一致，不能把它描述成原 PR 合并。 |

这五项当前都不满足由代理直接宣告关闭的证据条件。它们没有被“另发 PR”替代，本轮也没有新建 Issue 或 PR。

## 对照位置

- SAM：`common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/FunctionalInterfaceAliasGenerator.java`、共享 `FunctionalInterfaceResolver`，以及 `FunctionalInterfaceAliasGeneratorTest`、`common/src/test/probe-ts/community-probe-usage.ts`。
- 声明替换：`TypeDocsRegister`、`ClassDeclarationCatalogEntry`、对应 Point；`ClassDeclarationCatalogPointTest`、`ClassDeclarationTypeScriptBackendTest`、`CommunityProbeTypeScriptFixtureTest`。
- TS：`common/src/test/java/com/tkisor/nekojs/core/compiler/TypeScriptErasureParseCorpusTest.java`。包含合法 `for (const x of xs as T[])`，10-18 增补原提案精确 `arrow-fn-type-in-interface`，保留 parse 和行数选项。
- 严格消费：`common/src/test/probe-ts/tsconfig.community.json` 使用 strict、`skipLibCheck: false`；RPC 正反例见同目录 `community-probe-usage.ts`。

## 验证范围

[2026-10-12 社区复核记录](../architecture-refactor/evidence/2026-10-12-community-followup/REPORT.md) 保存了真实 backend 输出、严格 community tsc、525 项编译器/Node/诊断回归和实际红绿失败记录。那是历史结果，不当作当前全量声明无错误的证明。

本轮 `6e5bb8f0` 的完整 common 检查及五节点 build 已通过，冻结 XML 总计 4959 tests、0 failures/errors、271 skipped，见[本轮证据](../architecture-refactor/evidence/2026-10-17-reload-performance/trust-6e5bb8f0/README.md)。它包括相关 Java 回归，但没有重跑严格 TypeScript 消费，也不能替代真实外部 RPC 插件集成、master 验证或维护者结论。后续依赖声明扩展曾记录全量 TypeScript 错误；本轮没有证据将其宣布全部解决。

10-18 增补：[精确语料与完整 common 证据](../architecture-refactor/evidence/2026-10-18-community61-interface/README.md)已通过，测试类 73 tests、0 failures/errors/skipped，完整 common 2376 tests、0 failures/errors、4 skipped。原文“已有 59 项”计数不准确：新增前实际 58 项，新增后 59 项。只改测试，未改生产编译器或声明，未刷新上述 GitHub 状态快照。

后续处理：重新生成并严格消费相关声明；记录 #63 的合同取舍和外部插件集成结论。外部回复、关闭或标签更改需有明确授权和对应结论，不以 stonecutter 已适配作为 master 已合并的依据。
