# 本次事件 golden 基线维护者确认

- 确认人：本仓库维护者（当前主会话用户）。
- 时间：2026-10-09，Asia/Shanghai；本轮收到的直接用户回复。
- 维护者原文：**“接受这次基线变更，继续本地提交”**。
- 对应材料：本目录 README、source-and-golden-diff.patch、legacy-probe-golden-audit.json 及 REVIEW.md。

确认对象为本次 Probe 修复经现有 regenerate 工作流生成的 20 份事件 golden：仅移除未使用的传递 import，事件签名、载荷和其他非 import 正文逐字不变。五节点普通矩阵与实机部分生成结果已提供供审阅。此记录满足 [REGENERATE.md §3](../../baseline/2026-09-12-managed-surface/REGENERATE.md#3-旧新-diff原因影响与审阅记录必填) 的维护者结论要求，允许本地提交本次源码和派生基线。

此确认不表示完整 TypeScript/Pyright、客户端视觉、当前源码性能、整体票据或发布验收通过，不扩大 A22/A28 已确认删除范围。HTTPS 登录由用户稍后恢复，本轮只做本地提交。

原始 manifest.json 和 probe-linkage-evidence.zip 是确认前的不可变采集快照，其中 PENDING/sourceWip 字段记录当时状态；本文件记录随后取得的真实结论，不改写原始试验记录。
