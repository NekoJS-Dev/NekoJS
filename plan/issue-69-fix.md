# Issue #69：修复预检查误报

状态：代码修复与 48 项定向回归已完成；common 完整检查有两项原始提交也存在的 Probe golden 失败；后续已执行[26.1.2 NeoForge 聚焦游戏验收](issue-69-live-acceptance.md)，维护者最终接受结论未取得。见[实施与验证记录](issue-69-verification.md)。

关联 issue：https://github.com/NekoJS-Dev/NekoJS/issues/69 。原合并方案见[历史设计](issue-69-preflight-false-positives.md)，延期事项见[任务索引](README.md)。本票不新增公开合同。

## 1. 问题与目标

后缀链内的合法注释会使对象成员调用被误拆为全局调用。入口绑定检查还会把转译前的 Python、TypeScript 或第三方语法交给 JS 解析器；准备阶段仅看扩展名也无法处理第三方接管 JS 后缀的情况。

修复应保留真实错误诊断、原始源码偏移、既有安全与生命周期保证。历史探针已记录在原方案中，不视为本次实现验证。

## 2. 注释解析

在 [ValParser](../common/src/main/java/com/tkisor/nekojs/core/compiler/ValParser.java) 的后缀衔接及直接相邻的成员、可选链、调用参数和方括号位置复用现有注释跳过机制。不得预先删除注释或重写通用解析器。

保持显式分号、后续独立语句、字符串与正则边界，以及 EOF/无法消费输入时仍能推进。用 AST 结构、准确成员偏移和真实诊断验证，不能只断言编译成功。

## 3. 实际前端与入口检查

- 在 [NekoModulePipeline](../common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePipeline.java) 的既有准备职责内统一分类；使用本次准备原子捕获的同一份 LanguageBinding 及其注册表修订。
- 只有实际前端为内置原生 NekoJavaScriptLanguagePlugin.INSTANCE 时检查原始 JS。其他前端一律使用既有准备流程的 JavaScript 产物，包括 Python、TypeScript、第三方扩展名、接管 JS 后缀的插件及旧 IScriptCompiler 适配器。
- 扩展名、语言 ID、实现名称都不能证明原文是 JS；不为每种语言添加硬编码分支或新公开能力描述。
- [ScriptExecutor](../common/src/main/java/com/tkisor/nekojs/script/ScriptExecutor.java) 到 [NekoModulePipelineCache](../common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePipelineCache.java) 的真实入口路径必须采用同一分类，不能简单跳过所有非 JS 入口。
- 每次入口执行仍以当前 generation/session 的 schema 和诊断接收端重检绑定，即使源码未变且准备缓存命中；准备阶段的完整规则与入口绑定检查的既有范围差别保持不变。

## 4. 配置、安全与缓存约束

保留 owner/session 开启状态检查。现有 scriptMemberValidation=false 同时关闭准备阶段与入口阶段的预检查；开关关闭时不做仅为预检查服务的额外准备或转译，不关闭正常执行所需的编译、授权或沙箱。

复用既有准备缓存、授权、源码戳、编译器修订失效、source map 和诊断去重。不得绕过流水线单独调用第三方编译器，不新建编译缓存，不跨代际缓存 schema 判断或已报告诊断。准备失败沿既有错误路径处理，不退回检查非 JS 原文；普通预检查诊断仍只报告、不阻止执行。

保持 NekoRuntimeRoot 单一所有权、root/generation 生命周期分离、owner-thread 串行及 active/candidate 接收端隔离。保留现有 source map 能力及限制，不扩大为映射系统改造。

## 5. 回归验收清单

- [ValParserTest](../common/src/test/java/com/tkisor/nekojs/core/compiler/ValParserTest.java)：独立行、行尾、块与连续注释；CRLF、EOF；成员、可选链、调用、方括号；准确偏移及后续独立语句。
- [ScriptSourcePreflightTest](../common/src/test/java/com/tkisor/nekojs/core/compiler/ScriptSourcePreflightTest.java)：非空 schema 与真实诊断接收端；合法链无假诊断，真实拼写错误仍报告。
- [NekoModulePipelinePreflightGateTest](../common/src/test/java/com/tkisor/nekojs/core/module/NekoModulePipelinePreflightGateTest.java)：原生 JS、真实 Python/TypeScript、自定义完整语言插件、旧编译器；自定义后缀、接管 JS 后缀、易混淆语言 ID；原文不会进入 JS-only 检查，产物真实错误仍报告。
- [NekoModulePipelineCacheSessionTest](../common/src/test/java/com/tkisor/nekojs/core/module/NekoModulePipelineCacheSessionTest.java) 与执行器入口集成回归：冷准备/缓存命中、schema 改变、当前诊断接收端、开关关闭、编译失败、修订失效、关闭 session、授权和 active/candidate 隔离；缓存命中不得为检查重复编译。

自动化覆盖已执行；新增[真实入口回归](../common/src/test/java/com/tkisor/nekojs/script/ScriptExecutorPreflightTest.java)验证 Python/TypeScript、JS 接管、开关和编译失败（含 Error）。结果与剩余限制见[验证记录](issue-69-verification.md)。

## 6. 明确不做

不新增稳定前端 ID、具名注册重载、前端选择配置、languagePipelines、sourceChecks/outputChecks、独立预检查扩展点、第三方检查合同、可组合调度器或 Probe 集成；不改现有前端自动选择优先级、Probe 命令/配置语义或配置失败回退合同。

这些功能分别归入 [D1](deferred-frontend-identity-config.md)、[D2](deferred-preflight-plugin.md)、[D3](deferred-composable-pipeline.md)、[D4](deferred-probe-integration.md)。它们不构成本票完成条件。

## 7. 验证记录

| 检查 | 当前结果 | 记录责任 |
| --- | --- | --- |
| 解析器、预检查、准备门控、缓存/session 与执行器入口定向回归 | 48 项通过；0 失败、0 跳过 | 实施验证 |
| common 完整检查（:common:check；:common:test 不能替代） | 3002 项中 2 项 Probe golden 失败、4 项跳过；原始提交复现相同失败 | 实施验证 |
| common 隔离、外部插件依赖面与 guardLint | 通过；guardLint 0 警告 | 实施验证 |
| 游戏启动、重载及维护者结论 | 26.1.2 NeoForge 聚焦正反用例已执行；独立问题与最终结论限制见游戏记录 | 实施验证／维护者 |

本表来自本轮实际实施验证，不沿用历史文档验收。命令、红绿复现、原始提交对照及未运行项详见[实施记录](issue-69-verification.md)。未提升版本、未发布、未关闭 GitHub issue，也未代签人工验收。
