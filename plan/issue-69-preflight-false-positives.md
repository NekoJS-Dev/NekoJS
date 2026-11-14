# 预检查插件化与可组合语言流水线方案

状态：历史合并设计，已由[当前 #69 修复票](issue-69-fix.md)与[延期任务索引](README.md)拆分替代。

> 以下正文保留原设计及当时诊断，供后续任务参考；其中“本次”“实施阶段”“完成标准”不再代表当前授权范围。当前 #69 仅包含局部解析器修复和现有流水线的实际前端分类／配置门控，不新增插件合同、配置选择或 Probe 行为。本文中的新增合同、配置示例、诊断码和验收命令均为历史提案，不可视为已实现或当前通过证据。当前实现验证待根代理记录。

关联 issue：https://github.com/NekoJS-Dev/NekoJS/issues/69

核查基线：本地 `main`，提交 `aa4ec104`。本方案包含 Issue #69 的修复，以及用户新增的要求：将现有 JS 预检查分离为内置插件，并在 NekoJS 引擎配置中独立组装语言前端、源码预检查、编译产物预检查和 Probe 后端。本次仅交付文档，下述功能属于拟议设计，尚未实现。

## 1. 已确认的问题

目前存在两处独立的静态分析缺陷，会为实际能够成功执行的脚本报错：

- 后缀链中的注释可能截断后续成员调用，使 `recipePool(...)` 被当成全局函数调用，而不是对象方法调用。[ValParser.java](../common/src/main/java/com/tkisor/nekojs/core/compiler/ValParser.java#L325-L355) 的后缀循环只跳过空白，没有跳过注释。
- 入口检查在 [ScriptExecutor.java](../common/src/main/java/com/tkisor/nekojs/script/ScriptExecutor.java#L110-L127) 中读取原始文件，随后直接交给 [NekoModulePipelineCache.java](../common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePipelineCache.java#L256-L260)，缺少前端类型和配置开关判断。因此，Python、TypeScript 原始语法可能进入只支持 JS 的解析器。

[NekoModulePipeline.java](../common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePipeline.java#L128-L171) 的准备路径已经按配置和扩展名控制检查。但为了兼容前端覆盖，其仅依据扩展名分类的规则也需要调整。

### 已测得的诊断证据

诊断时将当前解析器源码直接载入 JShell 内存执行，没有依赖可能过期的已编译类。探针检查 AST 是否凭空产生了脱离对象的函数调用：

| 样例 | 是否出现脱离对象的调用目标 | 结论 |
| --- | --- | --- |
| 不含注释的链式调用 | `recipePool`：false | 对照样例仍保持成员调用 |
| 含独立行注释的链式调用 | `recipePool`：true | 被错误拆开 |
| 含行尾注释的链式调用 | `maxParallelAmount`：true | 被错误拆开 |
| 原始 Python 函数声明 | `declare_machines`：true | 被误解析为 JS 调用 |

以上属于解析器级复现，不是完整游戏环境复现，也不表示每一种 TypeScript 语法都会触发误报。尚未运行新增回归测试或游戏验收。

## 2. 目标行为与范围

- 合法注释不破坏后缀链，也不改变原始源码偏移。
- 只支持 JS 的检查器只能接收 JavaScript，不能接收任意语言前端的原始语法。
- 每次执行入口脚本时，仍然按当前脚本代际的 schema 检查；准备缓存命中时也不能跳过。
- `scriptMemberValidation=false` 同时关闭准备阶段和入口阶段的预检查。真正的编译、执行失败仍需正常报告。
- 用户可通过引擎配置，按扩展名明确指定前端、两阶段预检查列表和 Probe 后端，不再受插件注册顺序影响。
- JS 默认内置一套 NekoJS 预检查插件；现有绑定、未定义名称和事件回调检查通过它提供，不再由入口执行器或编译流水线直接硬编码调用。
- 各能力可以来自不同插件，但必须通过输入阶段、语言／方言、能力及语义配置兼容校验，不能任意混搭。
- 保持 Script API、现有 Plugin API 入口、持久化、网络合同、运行时所有权、授权及 active/candidate 隔离。为稳定选择前端增加可选的具名注册重载，为预检查增加独立的可选扩展合同。旧插件无需实现新增合同，不新增外部依赖。

## 3. 修复 A：后缀解析正确跳过注释

在后缀解析的衔接处，使用现有 `skipWsCmt()` 代替仅跳过空白的逻辑。同时检查后缀解析直接消费的相邻位置：成员访问操作符之后、可选链操作符之后、调用参数分隔符周围、方括号访问内部。仅调整与这些位置有关的空白处理。

不能先从源码副本中删除注释，否则会破坏诊断偏移。保留显式分号、后续独立语句、字符串和正则边界，以及文件结束时解析器仍能推进的保证。本次不进行通用解析器重写。

扩展 [ValParserTest.java](../common/src/test/java/com/tkisor/nekojs/core/compiler/ValParserTest.java)，断言 AST 结构与准确的成员偏移。扩展 [ScriptSourcePreflightTest.java](../common/src/test/java/com/tkisor/nekojs/core/compiler/ScriptSourcePreflightTest.java)，使用非空 schema 和真实诊断接收端，验证合法链不误报、真正拼错的成员仍能报告。

## 4. 修复 B：依据实际前端，而不是扩展名分类

### 共用的分类规则

迁移初期先用以下共用规则修复误报，最终由第 5 节的预检查描述及流水线选择代替类型硬编码：

1. 实际捕获的前端为 `NekoJavaScriptLanguagePlugin.INSTANCE` 时，允许对原始 JS 源码进行预检查。
2. 其他所有前端默认检查现有准备流程生成的 JavaScript，包括内置 Python/TypeScript、第三方扩展名、接管 `.js` 的插件，以及旧版 `IScriptCompiler` 适配器。用户可额外选用兼容的原始语言检查器，但不能将原文交给不兼容的 JS 检查器。

扩展名和语言 ID 都不能证明用户编写的文本是 JavaScript。即使某实现名为 `javascript`，也不能仅凭名称将其当成原生 JS。自定义但兼容 JS 的前端，同样可以安全地采用编译后检查路径。

分类时必须使用本次准备实际捕获的同一份 `LanguageBinding`。保留注册表原子捕获和修订检查，不能分类时查到一个前端，编译时却选择另一个。参见 [ScriptCompilerRegistry.java](../common/src/main/java/com/tkisor/nekojs/core/compiler/ScriptCompilerRegistry.java#L134-L157) 和 [NekoModulePipeline.java](../common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePipeline.java#L228-L246)。

策略留在现有流水线和缓存职责内，必要时提供包内辅助方法。替换准备阶段及其测试中“只按扩展名判断”的假设。误报修复本身不要求语言插件新增预检查方法；最终提供独立预检查扩展点，由其描述输入合同，语言前端与预检查器不互相持有对方实现。

### 入口数据流与配置

1. 保留 owner/session 开启状态检查。
2. 在进行仅为预检查服务的工作前，读取现有流水线配置。关闭检查时直接返回，不额外准备模块或转译。
3. 默认情况下，内置原生前端检查原始 JS；其他前端复用当前 session 的准备结果，检查 `NekoPreparedModule.code()`。插件化后统一调度配置中的 `sourceChecks`、`outputChecks`，分别传入原文和生成 JS，不再直接调用内置验证器。
4. 复用现有准备缓存、授权、源码戳、编译器修订失效、source map 和诊断去重。不能绕过流水线单独调用第三方编译器，也不新增编译缓存。
5. 始终使用当前脚本代际的 schema 和诊断接收端。可以共享纯解析事实，不能跨代际保留 schema 判断或已报告诊断。
6. 准备失败沿现有错误路径处理，不能在编译失败后退回检查非 JS 原始源码。普通预检查诊断仍只报告，不阻止执行。

入口实现必须保持前端分类与准备结果之间的一致性，包括缓存命中场景。必要时调整内部方法签名和调用者，而不是把引擎内部对象泄漏到公开预检查合同中。错误清理和执行顺序继续由现有执行器负责。

仅跳过非 JS 入口并不充分：脚本代际的 schema 改变后，准备缓存命中可能跳过准备阶段的检查。保留每次入口执行时的检查才能补上这个缺口。

### 兼容性限制

共用准备流程已经能将语言插件或旧版编译器的输出转换成可执行 JS，参见 [NekoModulePipeline.java](../common/src/main/java/com/tkisor/nekojs/core/module/NekoModulePipeline.java#L173-L217) 和 [NekoLegacyLanguagePlugin.java](../common/src/main/java/com/tkisor/nekojs/core/compiler/NekoLegacyLanguagePlugin.java)。不需要为每种语言硬编码适配器。

这里保证的是前端注册兼容性，不是对任意生成的 JavaScript 都能进行完美分析。生成代码的辅助全局变量和诊断映射仍遵循现有注册、映射机制。保持 source map 行为不变，不整体屏蔽真实错误，也不把本次任务扩大为映射系统重构。

## 5. 可组合语言流水线与预检查插件化

### 插件作者：当前已经支持替换

[NekoJSPlugin.java](../common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java#L93-L100) 提供 `registerScriptCompilers`。插件可以注册新前端，也可以使用 `replaceLanguage`。注册表从最新注册项向前查找，因此替换必须考虑实际启动顺序，包括后续插件的注册。参见 [ScriptCompilerRegistry.java](../common/src/main/java/com/tkisor/nekojs/core/compiler/ScriptCompilerRegistry.java#L63-L157)。

内置 Python 前端也通过同一通道注册，见 [PythonTranspilerPlugin.java](../common/src/main/java/com/tkisor/nekojs/core/compiler/python/PythonTranspilerPlugin.java#L19-L24)。没有已注册前端或编译器接管原生 JS 扩展名时，才回退到内置 JS，因此第三方同样可以接管 `.js`。替换语言前端不等于替换 GraalJS 执行引擎。

### 普通用户：当前尚无内置前端选择项

选择 `.js` 或 `.py`，只是通过扩展名映射选择语言，不是在同一种语言的多个实现之间选择。已检查的引擎配置和脚本属性中，没有统一的前端提供者选择项。目前实际实现由已安装插件的注册结果决定。某个插件可以自行提供配置，但那属于插件自身能力。

[ScriptCompilersPoint.java](../common/src/main/java/com/tkisor/nekojs/core/plugin/ScriptCompilersPoint.java#L27-L36) 在启动收集完成后冻结注册表。普通脚本重载不会重新注册前端，改变插件注册选择通常需要重启运行环境。

### 拟新增的统一配置合同

现有标准配置位置是 `<gamedir>/nekojs/config/engine.toml`；旧位置 `<gamedir>/config/nekojs-engine.toml` 继续作为只读回退。参见 [NekoJSPaths.java](../common/src/main/java/com/tkisor/nekojs/core/fs/NekoJSPaths.java#L75-L83) 和 [SandboxConfigLoader.java](../common/src/main/java/com/tkisor/nekojs/core/config/SandboxConfigLoader.java)。在同一配置文件中使用 `languagePipelines` 表集中描述组装关系，不新增配置文件或脚本局部选择器：

```toml
[languagePipelines.".js"]
frontend = "nekojs:javascript"
sourceChecks = ["nekojs:javascript"]
outputChecks = []
probe = { language = "typescript", backend = "builtin" }

[languagePipelines.".py"]
frontend = "example:python"
sourceChecks = ["example:python-lint"]
outputChecks = ["nekojs:javascript"]
probe = { language = "python", backend = "builtin" }
```

以上是拟议格式，不是当前版本已经可用的配置。`example:python`、`example:python-lint` 是已安装第三方实现的占位 ID。JS 示例使用 NekoJS 内置源码检查，同时显式关闭重复的产物检查；Python 示例组合第三方前端、另一项 Python 源码检查、NekoJS 内置 JS 产物检查及内置 Python Probe。这是独立选择，不要求由同一个插件提供整套能力。

`languageFrontends` 只是本方案上一版尚未发布的草案键，现由 `languagePipelines` 替代，不实现两个等价配置入口或无依据的迁移兼容层。

默认值为空表：未配置的扩展名保持现有自动前端选择和默认 JS 预检查。对象内部未填写的字段使用默认策略：`frontend` 自动解析；内置原生 JS 默认仅启用内置源码检查，其他前端默认仅启用内置 JS 产物检查；`probe` 沿现有语言级选择策略解析。检查数组显式写为 `[]` 表示关闭该阶段，不能又悄悄回退到默认检查。`scriptMemberValidation=false` 是总开关，关闭所有插件预检查；不关闭编译器语法校验、授权或沙箱。

内置前端稳定 ID 仍为 `nekojs:javascript`、`nekojs:typescript`、`nekojs:python`。内置 JS 预检查也使用 `nekojs:javascript`，但属于独立的预检查注册表，和同名的前端不是同一个实现。Probe 继续使用现有 `(languageId, name)` 身份，不强行改成新的注册协议。

键按现有扩展名规则规范化；选择 `.js` 不自动影响 `.mjs` 或 `.cjs`。拒绝规范化冲突，使用 NightConfig 表项读取带点号的字面量键，不能当成点分路径查询。删除配置项恢复自动解析，不引入 `auto` 特殊实现 ID。

### 稳定标识与注册表所有权

当前 [NekoScriptLanguage.java](../common/src/main/java/com/tkisor/nekojs/core/compiler/NekoScriptLanguage.java) 只有语言 ID，没有独立的提供者标识；不同实现可能使用同一个语言 ID。不能将语言 ID、加载顺序、实现类名或对象标识当成持久化选择键。

在现有 `ScriptCompilerRegistry` 中新增可选的 `registerFrontend(String frontendId, NekoScriptLanguage language)` 重载。具名注册只参与一次普通语言注册，复用同一个编译器或插件实例，并将明确的前端标识发布到注册表拥有的可选目录。通过现有语言描述构造器，同时支持完整前端插件和旧版编译器适配器。旧注册方法保持源码、二进制兼容，并继续参与自动解析；未具名的旧实现必须采用具名注册后，用户才能显式选择。

可选目录和自动解析索引保留在现有 Point 拥有的注册表中，不增加运行时所有者。前端 ID 必须是合法、唯一的小写 `namespace:path` 标识；重复 ID 应使启动收集失败，而不是按加载顺序选出一个。普通 `replaceLanguage` 继续保留对自动解析的既有含义，但不能从可选目录中抹掉具名候选。这样即使内置实现被自动覆盖，用户仍能显式选回内置实现。将内置 JS 注册为可选的回退候选，不能因此改变其自动解析优先级；TypeScript/Python 则在现有自动注册项上补充具名标识。

从注册表公开只读描述列表，包含前端 ID、语言 ID 和支持的扩展名。启动时报告一次，选择错误时也列出兼容 ID，让用户不必猜测 Java 类名。本次不增加命令、GUI、按世界或按脚本覆盖，也不增加网络前端协商。

### 解析、失败处理与生命周期

- 将配置解析为 `SandboxConfig` 中不可变的扩展名到流水线描述映射；描述包含可选前端、可选有序检查数组和可选 Probe 引用。保留现有构造器，默认空映射，并区分字段缺省与显式空数组。保持标准配置可写、旧位置只读的区别。
- 插件注册回调中不读取配置。组装现有运行时流水线时，对冻结注册表校验整份配置映射；通过现有配置注入不可变策略。不新增全局配置单例或第二份注册表。
- 将显式提供者解析放在注册表的原子捕获接口中。增加接受可选前端 ID 的重载，返回实际语言、插件、编译器及注册表修订。流水线准备及其修订检查共用该策略。旧捕获方法继续作为自动解析的兼容入口，运行时执行必须采用配置感知路径。
- 显式选择的前端必须存在，且支持目标扩展名。未知 ID、插件缺失、扩展名不兼容、配置表类型或值无效、规范化后键冲突，均通过稳定诊断码 `NEKO-LANG-SELECT-001` 报错。诊断包含配置位置、扩展名、请求 ID 和已安装的兼容 ID。不能静默回退到其他编译器。应阻止 NekoJS 脚本运行时初始化，而不是任意导致游戏进程崩溃。
- 显式选择无效时，不能被配置加载器现有的宽泛默认回退吞掉。引擎配置不可读或格式错误时，不能悄悄丢失用户指定的前端并使用其他实现启动脚本；应报告配置失败，并使脚本初始化保持失败。这是有意采用“失败时关闭”的合同变更，需要专门测试。
- 修改 `languagePipelines` 中的选择后必须完整进程重启。普通脚本重载不会修改已冻结注册表和运行时拥有的选择策略。继续使用启动时的选择；若现有配置重载路径发现变更，则提示需要重启，不承诺在线切换。
- 入口脚本与依赖、CJS/ESM 路径，以及 startup/server/client 分区，共用同一份运行时选择策略。前端标识进入准备模块标识和缓存戳，不能混淆使用相同语言 ID 的不同实现；检查器 ID、修订与阶段进入各自的解析事实缓存键，不能复用其他检查器的分析结果。复用现有失效机制，并按实际选中前端进行 Issue #69 的预检查分类。
- 配置只能选择已安装、可信的插件，不负责加载任意类、下载提供者、授予额外权限或替换 GraalJS。客户端和服务端配置仍属于本地配置；本范围内，多人游戏的前端配置一致性由管理员负责。

### 将现有 JS 预检查分离为 NekoJS 内置插件

这不是只把类名换成插件：最终只有共用调度器持有预检查提供者，入口执行器、准备流水线通过调度器执行阶段和检查范围。现有 [ScriptSourcePreflight.java](../common/src/main/java/com/tkisor/nekojs/core/compiler/ScriptSourcePreflight.java) 及其绑定、未定义名称、事件回调验证器，作为内置 JS 插件的内部实现复用，不复制一套检查逻辑。

- 新增独立的 `nekojs:script_preflights` 扩展点，并配对 `NekoJSPlugin.registerScriptPreflights` 默认空 Hook、Contributor、内置 Point 注册与 `PluginHookPairingTest`。Point 是收集与冻结的唯一事实来源，注册方法不能只挂在某个语言前端上。
- NekoJS 随包提供内置 JS 预检查插件，并以 `nekojs:javascript` 注册；第三方通过同一个合同注册。默认行为开箱即用，不要求用户另装 JS 检查插件。所有实现都必须声明输入合同，内置实现不绕过调度和兼容检查。
- 公共预检查接口、描述、请求和诊断合同放在 `com.tkisor.nekojs.api.*`；调度器、解析器、缓存及默认实现保留在内部实现中。`common` 继续不依赖 Minecraft 或 loader。新增 Hook 默认空实现，旧语言插件不必新增方法。
- 检查描述至少包括稳定 ID、实现修订、支持的阶段（`SOURCE`／`OUTPUT`）、语言／方言及输入表示、支持的检查范围与所需语义配置。只用扩展名或语言名称相同不能证明兼容；第三方同名但语法不兼容时仍应拒绝组合。
- 请求传入不可变的路径、源码表示、阶段、当前脚本代际 schema 视图、实际前端描述和源码映射信息；不能暴露可修改的注册表、Graal Context 或其他代际的运行时对象。返回统一的诊断，包含提供者 ID、稳定诊断码、严重性、检查阶段和位置；引擎统一归属、映射和去重。产物位置按现有 source map 映射，不能伪装成原文位置。
- 明确 `FULL` 与 `BINDINGS_ONLY` 调用范围：准备阶段检查完整规则，入口每次执行重检绑定相关规则；内置实现保持现有范围差别，事件检查不因插件化额外重复。第三方必须声明所支持范围，不能将不支持的请求当成已完成。
- 源码检查发生在准备前，产物检查发生在准备成功后；普通诊断只报告、不阻断编译或执行。检查器异常通过现有错误路径报告提供者失败，不得吞掉、缓存成成功或回退到其他实现；真正编译失败与配置不兼容仍保持失败。
- 单阶段检查器按数组顺序运行，禁止重复 ID；缺省不会隐式执行所有已安装检查器。纯解析事实可按提供者和源码有界缓存，schema 判断和诊断必须使用当前代际。遵循已有 owner-thread 串行和关闭清理规则，不能额外长期持有脚本上下文。
- 运行时 root 拥有冻结注册结果和共享纯分析资源；generation/session 仅拥有当次视图及诊断上下文。普通 reload 不重建 Plugin Runtime，candidate 失败不影响 active；重启前不热替换提供者。

### 前端、检查器和 Probe 的兼容组装

启动时先解析完整的有效组合，再允许脚本加载。前端描述其源码语言／方言、生成表示及语义配置；检查器对其输入阶段提出约束；Probe 描述输出声明与编辑器资料适配的目标语义。共享语义标识和能力来自实际合同，不以插件厂商相同作为兼容条件，也不要求三者的语言字符串机械相等。JS 配 TypeScript 声明 Probe 是有效组合，Python 原文配仅支持 JS 的源码检查是无效组合。

复用现有 [ProbeBackend.java](../common/src/main/java/com/tkisor/nekojs/probe/ProbeBackend.java)、[ProbeBackendRegistry.java](../common/src/main/java/com/tkisor/nekojs/probe/ProbeBackendRegistry.java) 和 [ProbeConfig.java](../common/src/main/java/com/tkisor/nekojs/probe/ProbeConfig.java#L34-L46)，不新建另一套 Probe 注册表。所需兼容元数据通过可选描述方法／适配器补充，保留现有方法和身份；不能默认为未知第三方实现证明它具备未声明能力。

现有 [ProbeBackendSelector.java](../common/src/main/java/com/tkisor/nekojs/probe/ProbeBackendSelector.java#L43-L65) 会对找不到的旧配置回退，本次新增的显式组装引用不能照搬这种静默回退。选择优先级明确为：显式命令参数高于引擎组装中对目标语言的选择，再高于现有 Probe 语言级配置，最后才是注册表默认。显式命令覆盖只影响当次 Probe，不修改运行时前端或预检查。旧 Probe 配置与默认扫描、输出目录继续由原模块负责，不复制到引擎配置中。

`probe` 为空缺省时沿原规则；显式引用必须存在并兼容，不能找不到就换一个。多个扩展名引用同一个 Probe 目标语言时必须选择同一个后端，否则启动时报歧义，避免在同一输出目录相互覆盖。无参数 Probe 命令仍面向既有 TypeScript 默认目标，但采用该目标的显式引擎选择；`all`、启动生成与编辑器配置协调器均复用同一有效选择解析结果，并保护现有文件提交、回滚及清理机制。

组装不能改变授权、沙箱、安全检查或执行引擎；这些不是可替换预检查插件。新增预检查、兼容性和 Probe 选择失败需要各自稳定的诊断码，实施时按现有错误码规则分配并更新中英文错误参考，不能靠日志文本或提供者名称猜测错误类型。

### 新增合同与文档工作

具名前端注册、独立预检查 Point/Hook/Contributor、内置 JS 检查插件、稳定 ID、能力描述和统一流水线配置都属于新增合同。配置表为空时，现有自动解析行为保持不变。无效引擎配置采用“失败时关闭”是有意的行为变更，交付说明必须明确指出。实施时更新规范来源中的内置手册、配置引用、插件作者示例，并使用正常的文档生成和检查流程；不手动修改生成产物，也不在本次仅文档任务中提升版本。

## 6. 实施阶段与验收

### 阶段一：先补充能够失败的回归测试

扩展下列现有测试。完成标准是在修改业务代码前，能够通过 AST 或诊断行为复现问题，不能仅使用空 schema 证明编译成功。

- [ValParserTest.java](../common/src/test/java/com/tkisor/nekojs/core/compiler/ValParserTest.java)：独立行、行尾注释，块注释及连续注释，CRLF、EOF、可选链、调用、方括号访问、源码偏移，以及后续独立语句不受影响。
- [ScriptSourcePreflightTest.java](../common/src/test/java/com/tkisor/nekojs/core/compiler/ScriptSourcePreflightTest.java)：合法成员不产生假诊断，真正的拼写错误仍能报告。
- [NekoModulePipelinePreflightGateTest.java](../common/src/test/java/com/tkisor/nekojs/core/module/NekoModulePipelinePreflightGateTest.java)：使用真实 Python/TypeScript 前端和非空 schema，替换旧的扩展名门控合同。
- [NekoModulePipelineCacheSessionTest.java](../common/src/test/java/com/tkisor/nekojs/core/module/NekoModulePipelineCacheSessionTest.java)：源码不变但 schema 改变、缓存命中、当前诊断接收端及关闭后的 session。

分别为完整语言插件接口和旧编译器接口添加自定义前端测试夹具。覆盖自定义扩展名、覆盖 `.js`、使用容易与内置混淆的语言 ID。断言原始语法不会进入仅支持 JS 的检查器，同时真正的生成 JS 错误仍能检测。覆盖执行器到缓存的真实入口调用链，而不只是直接调用解析器。

### 阶段二：实现两处误报修复

应用局部解析器修改与共用的前端、配置策略。覆盖原生和转译输入在开关开启、关闭，以及冷准备、缓存命中时的行为。验证源码和编译器变化会使准备缓存失效，缓存命中不会为检查重复编译，active/candidate 诊断接收端保持隔离，真实编译失败仍然失败。按现有去重机制审查重复报告行为。

完成标准是新增回归全部通过，不压制合法诊断，也不削弱所有权或安全检查。

### 阶段三：实现稳定提供者身份与统一流水线配置

实施新增合同前，先增加注册表选择与 `languagePipelines` 配置回归测试。验证前端、`sourceChecks`、`outputChecks`、`probe` 四项可由不同插件提供，并区分字段缺省与显式空检查数组。覆盖空映射、默认配置兼容，后续插件覆盖后仍可强制选回内置，实现第三方前端选择，同一语言 ID 下存在多个前端，重复前端 ID，规范化键冲突，格式或类型错误的配置表，提供者不可用或不兼容，以及配置解析失败时不能悄悄恢复自动解析。对字面量扩展名键进行真实 TOML 读写往返测试，并覆盖旧位置只读路径。

同时测试具名完整前端插件与具名旧编译器。未具名旧插件仍应自动工作，并明确标记为不可显式选择。验证 `replaceLanguage` 后具名候选仍被保留、原生回退不会改变自动优先级、前端描述可发现、入口与依赖及 CJS/ESM 共用配置、缓存标识区分提供者 ID。普通重载必须保持启动时策略，重启后的运行时必须采用修改后的选择。

扩展 [SandboxConfigLoaderTest.java](../common/src/test/java/com/tkisor/nekojs/core/config/SandboxConfigLoaderTest.java)，新增 `ScriptCompilerRegistrySelectionTest`，并扩展模块准备及缓存测试。内置文档检查须验证新增配置表、提供者示例，以及声明的重启、失败行为。这些实施测试均为待执行，尚未运行。

### 阶段四：分离内置 JS 预检查并接入第三方组合

先为现有 JS 预检查建立诊断兼容基线，再把调用入口移到内置 `nekojs:javascript` 提供者。不要先删除旧检查后留下空实现，也不要同时执行旧硬编码路径和新插件路径。迁移后生产流程对现有验证器的调用仅保留在内置提供者内部，其他位置调用统一调度器；迁移过渡只存在于同一实施过程，不发布双路径开关。

新增 `ScriptPreflightRegistryTest`、`ScriptPreflightPipelineTest`，扩展 [PluginHookPairingTest.java](../common/src/test/java/com/tkisor/nekojs/core/plugin/PluginHookPairingTest.java) 与既有 Probe 选择测试。覆盖：

- 内置 JS 默认可用，不额外安装插件；完整与仅绑定检查范围保持不变，真实错误继续报告，不增加重复诊断。
- Point/Hook/Contributor 配对、内置 Point 列表、提供者唯一 ID、冻结后禁止注册、旧语言插件无需实现新 Hook。涉及规范来源或生成清单时，通过既有流程再生成。
- 源码 Python 检查与 JS 产物检查同时运行，输入和诊断位置各属正确阶段；JS/TypeScript Probe 跨语言但语义兼容组合成功，Python 原文与 JS-only 检查组合在执行前失败。
- 缺失提供者、阶段不支持、方言／语义不兼容、重复检查 ID、有序执行、显式空数组、默认策略与总开关。未知旧插件的能力不能被默认认定为已兼容。
- 检查器抛异常必须报告失败，不回退或吞掉；普通错误诊断不阻断执行；安全、授权和沙箱不受任何检查配置影响。
- 检查器修订和源变化正确失效，准备缓存命中仍按当前 schema 重查，active/candidate 接收端隔离，关闭时资源被释放且无上下文泄漏。
- Probe 的显式命令、引擎选择、旧语言配置、注册表默认的优先级；显式缺失不回退；同目标语言冲突启动即报错；启动生成、无参数命令、`all` 与编辑器配置沿统一解析结果工作，已有文件事务和回滚保证不变。

完成标准是内置 JS 行为兼容、插件可独立提供两阶段检查、合法组合端到端可运行、非法组合有定位明确的诊断，且没有通过插件化绕过安全合同。这些仍是待实施验收，不是本次文档修订已完成的测试。

### 阶段五：验证与审查

计划运行的定向命令：

```powershell
./gradlew.bat :common:test --tests '*ValParserTest' --tests '*ScriptSourcePreflightTest' --tests '*NekoModulePipelinePreflightGateTest' --tests '*NekoModulePipelineCacheSessionTest' --tests '*SandboxConfigLoaderTest' --tests '*ScriptCompilerRegistrySelectionTest' --tests '*ScriptPreflightRegistryTest' --tests '*ScriptPreflightPipelineTest' --tests '*ProbeBackendSelectorTest' --tests '*PluginHookPairingTest'
```

定向测试还需包含新增的执行器集成测试。随后运行[编码约定](../docs/agents/coding.md#verification)要求的 common 完整检查：

```powershell
./gradlew.bat :common:check
```

最终审查差异的范围、语言、公开合同、隔离与生成产物。记录实际测试结果、失败及跳过项。游戏启动、重载验收必须记录真实平台和维护者结论；无法运行时应明确标记未运行。

## 7. 当前交付状态

本次仅交付文档。前述解析器证据来自已执行的诊断；实施阶段命令和新增回归测试均为待执行，尚未运行。不能声称已经修复业务代码、完成预检查插件化、提供可用的组装配置、完成发布或通过人工游戏验收。内置 JS 预检查插件、独立第三方预检查，以及前端／预检查／Probe 的兼容组装均已纳入待实施范围。
