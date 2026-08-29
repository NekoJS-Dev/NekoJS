# 01: 工作树入库：NekoJS-mult 提交进仓库

**What to build:** 本 session 的全部架构重设计成果（P0-P4d：V2 扩展点系统、通用注册表、脚本面切换、守卫豁免清零、CI、fabric 运行时 bring-up 与事件面）从未跟踪工作树进入版本控制。开发产物（run 目录、日志、loom 缓存）不入库（补 .gitignore）。后续所有票在新提交之上工作。

**Blocked by:** None (can start immediately).

**Status:** done (commit 50029bd)

- [x] .gitignore 覆盖 versions/*/run/、logs/、.gradle/、loom-cache 等开发产物
- [x] git add + commit 完成，`git status` 干净（除既有未跟踪备份 tarball）
- [x] 提交信息概括本批里程碑（P2 + P3 + P4a-d）
