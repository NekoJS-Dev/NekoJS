# 移除脚本重载进度条

## 实现与边界

- 删除 NeoForge 的 `NekoReloadProgressHud`，移除 26.x 共用入口和 1.21.1 独立入口的渲染订阅注册。Fabric 没有此 HUD。
- 脚本重载流程、内部进度快照、聊天结果、日志和错误界面保留；本轮只移除屏幕进度面板。
- 更新内部注释、英文内嵌手册和双语 wiki。未增加配置项、依赖或版本号。
- 保留工作区中此前的 Probe 生成器修改，未将其混入本任务实现。

## 验证结果

- `:common:check guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build` 通过，覆盖原有重载与 HUD 生命周期回归。日志：`common/build/remove-reload-progress-build.log`。
- guardLint 扫描 417 个守卫块、618 个文件，零警告；源码无残留 HUD 注册引用。
- 8 个 JAR 中均不存在 `NekoReloadProgressHud`；各 36 个 AI payload 与规范源及 manifest 哈希一致。审计：`common/build/remove-reload-progress-artifacts.json`。
- `npm.cmd run check:wiki` 通过，30 个双语主题零问题；`git diff --check` 通过。

## 维护者实机验收

2026-11-06，用户反馈“我测了没问题”，并要求开始下一个设计目标。本次结论记录为：**当前客户端移除脚本重载进度条的已执行范围验收通过**。不扩大为其他节点实机、全部重载边界或其他未测功能的验收。
