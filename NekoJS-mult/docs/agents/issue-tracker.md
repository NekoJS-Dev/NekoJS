# Issue tracker: 本地文档（GitHub 仅作档案）

**2026-08-30 裁定**：内部计划、spec、工单一律落仓库本地文档，**不发 GitHub issue**（GitHub issue 无法删除；首个误发的 #51 已关闭存证）。GitHub Issues 保留为历史档案与社区来件面：#1–#51 只读，不再主动新增；`/triage` 对社区来件的处理仍在 GitHub。

## 本地约定

- 计划 / spec / 工单写成 `docs/` 下的 Markdown，与现有平铺风格一致（`MIGRATION-ROADMAP.md`、`DEVEX-ROADMAP.md`）；文件名按主题命名。
- 进度与状态直接维护在文档内（轨道勾选、阶段标记），git 提交历史即工单历史；不建镜像 issue。
- 技能说 "publish to the issue tracker" → 写本地文档。
- 技能说 "fetch the relevant ticket" → 读对应的本地文档（按主题在 `docs/` 下找，找不到就问用户）。
- 仅当用户**明确点名**要发 issue 时才使用 `gh issue create`。

## GitHub 操作备忘（档案与例外）

社区来件与历史考古仍走 `gh` CLI（仓库 clone 内自动解析远端）：

- 读历史：`gh issue view <number> --comments`；列表：`gh issue list --state all --json number,title,labels --jq '...'`
- 历史票上的标签（`wayfinder:*`、`ready-for-agent` 等）仅作存档含义，不再用于新工单；新工单无标签体系，状态归文档本身。
- **PRs as a request surface: no.** 外部 PR 不走工单化流程。
- Wayfinder：过去的地图与子票以 GitHub issue 维护（#38–#50，已收官，只读）；今后的地图 / 子票用本地文档承载（参照 `docs/DEVEX-ROADMAP.md` 的轨道 / 勾选结构），GitHub sub-issue 与 dependency 机制不再使用。
- GitHub 分 issue 与 PR 共享编号空间，考古时裸 `#42` 需先用 `gh pr view 42` 再 `gh issue view 42` 消歧。
