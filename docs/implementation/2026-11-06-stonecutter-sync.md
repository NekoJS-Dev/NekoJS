# Stonecutter 后续改动同步

## 诊断与范围

维护者切换活动节点到 1.21.1 后询问新版错误 UI，并授权提交、推送本轮相关改动。

- Git 分支为 `stonecutter`，同步前 HEAD 与 `origin/stonecutter` 均为 `3d024c4c6`（JSX composition/workspace 交付）。后续窗口反馈、文本性能、滚动条、内置报错 UI、I18n 和 JSX 类型改进尚未提交。
- 新版内置 JSX 报错界面及原生 JSX 宿主只覆盖 NeoForge 26.1.2／26.2.0。`versions/1.21.1/.../ErrorDashboardScreens.java` 明确保留旧原生界面；这不是缺少分支合并。同步本轮提交不会把该 UI 移植到 1.21.1。
- 使用既有 `switchVersion` 临时回到仓库原默认活动节点 26.1.2，排除切换节点造成的源码注释转换；提交后恢复维护者本地 1.21.1 选择。
- 只提交相关生产代码、测试、规范声明、示例和交付记录；保留未相关的本地工具目录、历史审计文件及 `nul` 文件，不上传日志、隔离游戏和临时备份。

## 验证依据

最近完整验证见 `2026-11-06-jsx-callback-contracts.md`：common 完整门禁、五节点构建／platform gate、完整 Probe 类型链、四类 CI gate、Wiki 与 8 个 JAR 核对全部通过。对应日志保留在 `common/build/jsx-callback-*`。提交前重新检查规范活动节点下的构建与 staged diff，随后核对远程提交并恢复本地活动节点。

提交前复核已通过：`guardLint :common:check`、五节点 `build` 及 platform／artifact gate（`common/build/remote-sync-precommit-build.log`），四类 CI gate（`common/build/remote-sync-ci-gates.json`），8 个 JAR 资料一致性和 Wiki strict；staged diff 无空白错误。已排除工具目录和活动节点转换，最终相关变更为 81 个文件。

1.21.1 的旧原生 UI 保持原行为；没有本轮 26.2.0 实机验收、通用 region 人工验收或新增平台移植结论。
