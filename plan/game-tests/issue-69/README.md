# Issue #69 游戏内验收脚本

仅覆盖 Minecraft 26.1.2 NeoForge 的 #69 注释链与转译前端预检查回归。已通过 Gradle 启动真实游戏，当前聚焦正例在 CLIENT/SERVER 加载与重载通过；原始受阻探针并未通过。实际证据和剩余步骤见[游戏验收记录](../../issue-69-live-acceptance.md)，不代签维护者结论。

## 当前聚焦脚本

仅部署 [JS](issue69-comments.js)、[TS](issue69-typescript.ts)、[Python](issue69-python.py) 到同一 SERVER 脚本目录，确认现有配置 `scriptMemberValidation=true`。不要整目录复制，不要部署 `.disabled` 文件。脚本只创建内存文本并读取结果，不修改世界、玩家、共享或持久状态。

当前 Text 是受管理的 TextFacade/TextValue，不是 Minecraft Component；没有 `copy()` 或 Bean `.string`，因此使用已在现有集成测试中验证的 `Text.of("issue69").isEmpty()`。这仍覆盖相同后缀注释解析机制，但**不替代 ItemStack Bean 属性验收**。

| 脚本 | 每轮必须观察的输出 | 额外要求 |
| --- | --- | --- |
| JS | `ISSUE69_JS_LOAD`、`ISSUE69_JS_PASS line=end=block textEmpty=false` | 三种注释链断言均通过，无假未知函数/成员诊断 |
| TS | `ISSUE69_TS_LOAD`、`ISSUE69_TS_PASS textEmpty=false` | 类型专用方法 `issue69TypeOnlyMethod` 不被误当作原始 JS 调用 |
| Python | `ISSUE69_PY_LOAD`、`ISSUE69_PY_RESULT 7`、`ISSUE69_PY_TEXT_EMPTY False`、`ISSUE69_PY_EXECUTED` | 必须由外部日志检查核对实际值，无函数定义假诊断 |

Python 不含 assert/raise/字符串加法；打印实际值而不是无条件宣称 PASS。`EXECUTED` 只证明执行到末尾；值不符、输出缺失或正例误报均不能判定通过。Python 的 False 大小写来自现有 PythonEmitter 的字符串转换。

正常加载后保持源码不变，使用实例现有 SERVER 重载入口再执行一轮，并记录实际 generation、提交结果和日志区间。LOAD 不证明候选提交，重复输出也不独立证明缓存命中。不得将启动失败或错误阶段的客户端执行当作通过。

## 可选静态负例

正例之后单独启用 [负例部署副本](issue69-negative.js.disabled)，仅在部署副本中移除 `.disabled` 后缀。应报告 `Item.issue69MissingMember` 不存在及其脚本位置，同时出现 `ISSUE69_NEGATIVE_LOAD`、`ISSUE69_NEGATIVE_EXECUTED unreachable-call-not-run`，不得出现该调用的运行时异常。完成后移除部署副本并重载，确认恢复正例无诊断。

## 保留的原始受阻探针（默认禁用，未通过）

根代理实测发现两项独立限制，故保留原始内容供显式选择复现，不删除失败证据、不扩大本票生产修复：

- [原始 JS Item 探针](issue69-item-comments.js.disabled) 和 [原始 TS Item 探针](issue69-item-typescript.ts.disabled)：SERVER 候选脚本在世界数据加载阶段调用 Item.of 时出现 `Components not bound yet`；客户端错误阶段的尝试也不是有效验收。
- [原始 Python Item/assert 探针](issue69-item-python-assert.py.disabled)：assert 生成异常类数组前导代码，引出未知 forEach 诊断；同时包含上述 Item 调用限制。其历史 PASS 字面量只保留原文，不能视为已观察到或已通过。

这些探针只允许分别显式启用，不与聚焦正例混跑；ItemStack copy/count 与 Python assert 限制仍是剩余缺口。维护者结论、原始错误和成功日志由根代理在既有验收记录中如实归档。
