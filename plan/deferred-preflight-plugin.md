# D2：抽取内置 JS 预检查插件

状态：延期；仅本地任务票，未实施，未创建 GitHub issue。前置：[Issue #69](issue-69-fix.md)。后续：[D3 可组合流水线](deferred-composable-pipeline.md)。可与 [D1](deferred-frontend-identity-config.md) 独立设计。

## 目标与范围

将现有 JS 检查抽取为随包提供的内置插件，并允许第三方通过独立合同贡献检查；不要求语言前端持有检查器，也不复制现有解析和验证实现。

- 拟议的独立预检查 Extension Point 必须配对默认空 Plugin Hook、Contributor、内置 Point 注册和配对测试；Point 是收集与冻结的唯一事实来源。具体名称仍待审，#69 不新增这些合同。
- 公共接口、描述、请求和诊断位于 api.*，解析器、缓存、调度与默认实现保持内部；common 无 Minecraft/loader 依赖，旧语言插件无需实现新增 Hook。
- 描述需涵盖稳定身份、修订、SOURCE/OUTPUT 阶段、语言／方言／表示、FULL/BINDINGS_ONLY 范围及必要语义能力。请求只传不可变当次视图，不暴露可修改注册表、Graal Context 或其他代际对象。
- 统一诊断归属、阶段、位置、source map 和去重；普通诊断不阻断执行，检查器异常不得吞掉或缓存成成功。纯解析事实可有界复用，schema 判断和接收端必须属于当前代际。
- root 拥有冻结提供者和共享纯分析资源；session/generation 拥有当次上下文，遵守 owner-thread、关闭清理及 active/candidate 隔离。

## 迁移与验收

先建立内置检查的兼容诊断基线，再迁移到唯一调度路径；不得留下无检查窗口、复制实现或发布新旧双路径开关。保持准备完整检查与入口绑定重检的既有范围，普通 reload 不重建 Plugin Runtime。

待授权后验证开箱即用、真实错误不丢失/不重复、Point/Hook/Contributor 配对、冻结/重复 ID、旧插件兼容、范围不支持与提供者异常、缓存修订、当前 schema、关闭清理和诊断隔离。运行相关插件与预检查回归以及 common 完整检查；公开合同及示例同步规范来源并生成检查。

本票不引入用户组合配置或 Probe 选择；两阶段的实际配置组装归 D3。当前验证：未运行。详见[历史设计](issue-69-preflight-false-positives.md)，其合同均不是当前版本能力。
