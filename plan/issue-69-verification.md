# Issue #69 实施与验证记录

## 实施范围

仅实施 [#69 修复票](issue-69-fix.md)：局部后缀注释解析、根据实际捕获前端选择 JS 检查输入，以及现有配置开关和当前代际入口重检。四项 [延期任务](README.md) 均未实施。

- 后缀衔接、成员操作符后、调用参数与方括号内复用 skipWsCmt；原始文本和成员偏移不变。
- 只有内置原生 JS 单例检查原文；其他前端检查既有准备流程的 JS 产物，包括同名语言、接管 .js 的完整插件与旧编译器。
- 入口分类和准备共用同一 SourceSnapshot/LanguageBinding；继续复用授权、源码戳、注册表修订、缓存和 source map。
- 入口每次使用当前 session 的 schema/reporter；关闭总开关时不做额外读取或准备，仍检查 owner/session 是否关闭。
- 准备失败按普通入口失败路径记录；不回退到非 JS 原文，不把编译失败伪装为验证器回调失败。普通诊断仍不阻止执行。
- 未新增公开 Script API、Plugin API、配置键、扩展点、缓存所有者或 Probe 行为。入口到缓存的内部方法改为只传路径，由缓存统一读取与选择输入。

## 红灯复现

以下定向检查在相应修复前实际失败，失败原因均为回归断言：

| 回归 | 失败信号 |
| --- | --- |
| ValParserTest.commentsBetweenChainedCallsKeepTheReceiverAndMemberOffset | 注释链被拆成多个 AST 语句 |
| ValParserTest.commentsMaySeparateMemberKeysAndCallArguments | 相邻注释使 AST 结构错误 |
| NekoModulePipelinePreflightGateTest.overriddenJsFrontendChecksGeneratedCodeInsteadOfItsRawSyntax | 非空 schema 捕获了接管 .js 后对 Python 原文的错误检查 |
| ScriptExecutorPreflightTest 的 Python 入口 | 真实脚本执行成功，但错误面板记录原文函数误报；随后最小化为返回数字的函数，排除下述辅助代码问题 |
| ScriptExecutorPreflightTest.compilerErrorStopsEntryWithoutRetryingOrInvokingFallbackLoader | 首次编译抛出 Error 后仍进入 loader 重试，脚本被误标为成功；修复后保留原 Error，只编译一次且不调用 loader |

第一个使用持久 daemon 的红灯运行和早期基线在 Gradle 输出结论后仍有启动进程挂接，已终止挂接进程。最终通过证据来自之后 --no-daemon 的正常退出运行，不将被终止的进程记作成功。

## 当前检查结果

| 检查 | 结果 |
| --- | --- |
| 五组最终定向回归 | 通过：48 tests，0 failures，0 errors，0 skipped；Gradle exit 0 |
| guardLint | 通过：433 个守卫块，639 个文件，0 警告；Gradle exit 0 |
| :common:check | 失败：最终 3002 tests，2 failed，4 skipped；两项失败在原始提交同样复现，见下节；不得记作整体通过 |
| checkCommonIsolation / verifyAddonFixtureDependencySurface | 通过，最终 --continue 运行未绕过这些检查 |
| 最终差异与未跟踪文件 | 已审查 4 个生产文件、4 个已跟踪测试文件及新增入口测试；git diff --check 通过，无 Probe/golden/生成文件变更 |
| 文档链接与空白 | 11 份文档已逐页读取并检查本地目标；最终计数见任务索引 |
| 游戏启动与重载 | 后续已在 26.1.2 NeoForge 执行聚焦正例和真实错误负例，见[游戏记录](issue-69-live-acceptance.md)；不代表所有平台／功能通过 |
| 真实维护者最终接受结论 | 未取得，不代签人工验收 |

完整检查的 4 项跳过来自既有条件：LocalErrorSourceTest 的两个文件名用例因 Windows 不允许问号／冒号而跳过；TypeScriptNoopIrGoldenTest 的两个用例因 RecipeEventJS 不在 common 测试 classpath 而跳过。未修改这些条件。

实际最终命令：

~~~powershell
./gradlew.bat --no-daemon :common:test --tests '*ValParserTest' --tests '*ScriptSourcePreflightTest' --tests '*NekoModulePipelinePreflightGateTest' --tests '*NekoModulePipelineCacheSessionTest' --tests '*ScriptExecutorPreflightTest' --console=plain
./gradlew.bat --no-daemon guardLint --console=plain
./gradlew.bat --no-daemon :common:check guardLint --continue --console=plain
~~~

## 原始提交对照

在隔离的 detached worktree 中对未修改的 aa4ec104 实际运行：

~~~powershell
./gradlew.bat --no-daemon :common:test --tests '*LegacyProbeTreeTest' --tests '*ProbeOutputCompatibilityTest' --console=plain
~~~

结果：3 tests，2 failed，Gradle exit 1。同样是 legacyTreeMatchesGolden 与 legacyFilesStillExistWithManagedOutput，后者报告 @mixin/index.d.ts 第 63 行 java.lang.invoke.VarHandle$AccessMode 声明漂移，与本轮完整检查一致。隔离工作区无源文件改动，验证后已清理。本票没有修改 Probe、golden 或测试门禁，不以生成新 golden 消除失败。

## 覆盖与剩余限制

覆盖实际 Python/TypeScript 执行入口、完整前端与旧编译器、自定义扩展名和 .js 接管、同名语言、真实生成 JS 错误、缓存命中与编译次数、源码/注册表变化、当前 schema、active/candidate 接收端隔离、关闭 session、总开关、普通诊断去重，以及编译失败原因及位置。

本次还观察到范围外的 Python 复杂辅助代码分析限制，未扩张修复：字符串加法会触发辅助代码与异常类前导，当前浅层解析器仍可能把其中数组 forEach 当成独立调用。未屏蔽该诊断，也未将 forEach 冒充合法全局；回归采用不触发这些辅助代码的最小函数。现有 source map 能力和限制保持不变。

审查发现的编译器 Error 恢复问题已通过红绿回归修复。两阶段检查仍遵循现有计数语义：冷准备的完整检查与入口绑定检查可能调用 reporter 两次；ErrorTracker 合并为同一条面板记录，但 occurrence 次数与里程碑日志可能增加。未增加跨 schema 的诊断缓存或额外开关；不宣称每次入口只增加一次 occurrence。

后续已执行 26.1.2 NeoForge 的聚焦真实游戏验收，结果与独立问题另见游戏记录；未运行完整 Minecraft/loader 平台矩阵，也未取得维护者最终接受结论。Graal 在 common 的 Java 21 测试中使用解释器回退，并有现有 Gradle/native-access 警告；不等于验证了生产平台上的优化运行时。
