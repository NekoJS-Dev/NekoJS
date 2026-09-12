# 架构重构决策票导航

本目录承载本图的本地 Markdown decision ticket。canonical map 在 [NekoJS 架构重构路线图](../../architecture-refactor-map.md)；本地 tracker 不使用 GitHub 新 issue、labels 或镜像状态表。

## 查询

- 按文件名字典序扫描本目录中的决策票，并从 frontier 中选择最先出现的一张；每张票顶部的 Status、Assignee 和 Blocked by 是唯一状态来源。
- frontier 是 Status 为 open、Assignee 为 unassigned、且 Blocked by 链接指向的每一张票都已 closed 的票。
- 本导航不复制票的状态，也不列出当前 open 票；需要知道当前 frontier 时直接扫描目录中的票文件。

## 认领

- 开始任何工作前先把目标票的 Assignee 从 unassigned 改为当前调查者或维护者；认领本身使其他会话跳过该票。
- 每个会话最多裁决一张非 research 票；research 票可以并行调查。
- 票是要裁决的问题，不是实现任务清单；实现步骤只有在决策得到记录后，交给后续规划或实施流程。

## 阻塞

- Blocked by 必须使用指向其他票文件的相对 Markdown 链接，不使用裸编号或只写文字编号。
- 只有所有 Blocked by 链接对应的票都 closed 后，票才算可取；阻塞关系变化时直接编辑被阻塞票的 metadata。

## 关闭与回写

- 裁决后在该票文件单独追加 Resolution 区，记录结论、依据和仍需保留的边界；不要把答案改写回 Question。
- 再把 Status 改为 closed，并在 map 的 Decisions so far 追加该票标题链接和简短 gist。map 仍是索引，不承载决策正文。
- research 票的调查资产链接放在票文件中；本轮 00 的调查完成后由父代理复核并关闭。

## 派生规格

全部已闭合票的一一对应 spec 见 [规格索引](../specs/README.md)。spec 是实施与验收视图，裁决正文仍以各票 Resolution 为准；生成规格不改变原票状态，也不授权代码实施。
