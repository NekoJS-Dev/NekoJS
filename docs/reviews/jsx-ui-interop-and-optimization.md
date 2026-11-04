# JSX UI 现状审计与优化：组合式互操作及游戏内多窗口

## 1. 结论先行

**当前 JSX UI 更接近“由 NekoJS 自己管理的独立客户端 Screen”，不是“任意 Minecraft 界面的 JSX 扩展框架”。** 它已经有状态更新、布局、事务式宿主更新、焦点、输入、模态 Dialog、Tabs、表单校验和只读物品展示，不能说只是一个画板；但它尚未建立与已有 Screen / Widget 的正式互操作契约。

针对两个核心问题：

- **能否继承原版 UI？** 底层 `JsxScreen` 确实继承 Minecraft 的 `Screen`；但脚本不能通过当前 `ClientUI.screen` 选择 `InventoryScreen`、某个原版设置页或任意第三方 Screen 作为父类。也没有正式入口将 JSX root 挂入一个现成 Screen。
- **能否支持第三方 UI？** 必须分类。返回 NekoJS VNode 的普通 JS 函数组件可以复用；React/DOM 组件库不能因为同样使用 JSX 就直接运行；第三方 mod 的 Screen 可以在现有绘后事件覆盖范围内叠画内容，但“嵌入第三方控件、接管其焦点、共享模态和生命周期”目前不是现成能力。

此外，核心源码中还发现了组件 key、signal 重入、失败关闭协议和无效 Screen props 四处值得先补回归的问题，详见第 5.1 节。

**推荐顺序：先补核心回归与真实客户端验收，再做已有 Screen 的区域挂载及明确需要的 Workspace 多窗口，之后做受控原生 Widget 桥，最后才是特定第三方 UI 框架和容器交互。**

针对游戏内多窗口：**设计上可实现，但需要 Workspace 统一管理拖动、叠放、焦点、捕获和模态，不能靠连续打开多个 Screen 实现。** 第 7 节说明最小范围、第三方限制与验收；它不是额外的操作系统窗口。 不建议只删除 `final`，也不建议为了复用一个设置页就重新实现整套原版 UI。

### 审计范围与证据等级

- 源码基线：`3022010ff57b4e8ef5adb67c7681ff661c1ded30`。本文是面向维护者的中文分析，不是内嵌给 AI 的英文手册，也不是已实现的新接口说明。
- 本轮只读审计实现、调用路径、声明契约和测试，新增本文；没有修改生产代码、升级版本或执行 Minecraft 客户端。
- 下文区分：**已实现**、**明确功能缺口**、**需要实测的风险**、**建议设计**。功能缺口不等于已经复现了崩溃。
- [上次交付记录](<../implementation/2026-11-01-jsx-advanced-controls.md#L26-L61>)记录了 common / 五节点构建与 fixture 结果，也明确记录真实客户端验收未运行；这些是历史结果，不是本轮重跑的测试。

## 2. 当前到底支持什么

| 场景 | 当前结论 | 关键限制 |
|---|---|---|
| 用 JSX 创建独立配置页、表单、对话框 | 已实现，原生宿主限当前 NeoForge 26.1.2 / 26.2.0 节点 | 不代表已经做过真实窗口、GPU、物理输入验收 |
| 使用函数封装可复用 JSX 控件 | 已实现 | 必须返回 NekoJS VNode / 数组 / 文本等受支持值；不是 React class component / hooks |
| 在原版或 mod Screen 上叠画文字、图标 | 已有 `ClientEvents.screenRender` + `event.painter` 路径 | 依赖对应平台事件实际触发；只绘制，不自动获得 JSX 输入和布局 |
| 创建继承某个具体原版 Screen 的 JSX 页面 | 当前正式入口不支持 | 构造器固定创建 `JsxScreen`，没有可选父类或 Screen factory |
| 向已打开的原版 / mod Screen 挂入可交互 JSX 区域 | 当前正式入口不支持 | 没有 attach / detach、焦点交接、事件消费和宿主生命周期契约 |
| 将原版 Button / EditBox 或第三方 Widget 当 JSX 叶子 | 当前不支持通用桥 | JSX primitive、props 校验和原生绘制都有限定目录 |
| 将整个第三方 Screen 当 JSX 子元素 | 当前不支持，也不建议直接这样建模 | Screen 是顶层会话，不只是一个可测量矩形控件 |
| Item 图标、数量与悬浮说明 | 已实现只读展示 | 不是 Slot，不支持背包拖放、菜单事务或服务端权威同步 |
| React / 浏览器 UI 库直接迁入 | 不支持通用兼容 | 没有 DOM、React dispatcher、浏览器事件、完整 CSS 等语义 |
| Fabric / NeoForge 1.21.1 原生 JSX Screen | 当前没有 parity | common runtime 成功、Painter 可用均不能证明原生 JSX 宿主存在 |

平台结论来自[Binding 注册门控](<../../src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java#L224-L230>)、[宿主入口](<../../src/main/java/com/tkisor/nekojs/bindings/static_access/ClientUiJS.java#L1-L17>)及[原生 Screen 门控](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxScreen.java#L1-L18>)，不是根据文件在共享目录里就推定跨平台支持。

## 3. 为什么“继承了 Screen”仍不等于能复用原版 UI

### 3.1 当前调用链是固定的

```text
CLIENT 脚本
  → ClientUI.screen(title, pausesGame)
  → JsxHostAdapter
  → new JsxScreen(title, pausesGame, adapter)
  → UI.createRoot(render, host, options) / host.bindRoot(root)
  → host.open()
  → 切换当前 Minecraft Screen
```

[创建入口](<../../src/main/java/com/tkisor/nekojs/bindings/static_access/ClientUiJS.java#L11-L16>)只有标题和是否暂停；[宿主构造](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L120-L151>)固定创建 `JsxScreen`；[显示路径](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L955-L990>)是在 generation 合法后切换当前 Screen，不是向现有 Screen 注入一棵树。

因此，需要区分三件事：

1. **实现继承**：NekoJS 用 Minecraft 的 `Screen` 承载自己的界面，已经做到。
2. **已有界面的行为复用**：保留原版设置项、菜单、快捷键、第三方控件，外加一块 JSX 区域，没有正式支持。
3. **脚本自选 Java 父类**：通过正式 JSX 入口创建某个原版 / 第三方 Screen 子类，没有正式支持。

### 3.2 删除 `final` 解决不了核心问题

[当前 Screen](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxScreen.java#L12-L67>)是 `final`，但更重要的是：

- Java 父类在类定义时确定，不能通过一个 JSX prop 动态变成任意已有 Screen；
- 渲染和鼠标、键盘入口直接交给 adapter，没有通用的“原生 children + JSX children”混合路由；未消费按键也不是自动调用 `super.keyPressed`；
- adapter 固定持有自己创建的 Screen，没有提供既有宿主的生命周期抽象。

即使让 Java 插件继承这个类，也还需要解决构造可见性、宿主绑定、绘制顺序、焦点、坐标和资源所有权；**开放继承本身不是互操作实现**。同样，单纯往 Screen 的原生 children 列表加 Button，也不能假定当前 JSX 专用绘制和输入方法会自动处理它。

### 3.3 原生 Java 互操作不能被误报为“完全不可能”

CLIENT 已暴露 [Minecraft / Screen Binding](<../../src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java#L224-L230>)。引擎启用了[公开宿主访问及类实现能力](<../../common/src/main/java/com/tkisor/nekojs/core/NekoSharedHostAccess.java#L22-L41>)，并配置了[类过滤和 Nashorn 兼容](<../../common/src/main/java/com/tkisor/nekojs/core/NekoSandboxFactory.java#L128-L145>)。

因此，Java 插件可编写原生 Screen / Adapter；脚本通过 Java 互操作自行构造或扩展某些可访问类，也可能有可行路径。但本轮没有证明任意 `Java.extend` 示例可以跨节点运行：类过滤、构造器、访问级别、映射、第三方依赖和回调线程均需验证。

**准确表述是：不存在正式的 JSX 原版 / 第三方 Screen 集成入口，不是 JVM 永远无法实现。** 绕过 managed host 的 Java 回调也不能自动获得现有 generation 的清理保证。

## 4. “第三方 UI”应拆成四种兼容需求

### 4.1 NekoJS 函数组件：可以做，且应优先复用

[展开逻辑](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L971-L1036>)会调用函数并继续展开其结果。这适合封装设置行、分页、业务表单等可复用控件。前提是模块能在现有脚本加载机制中加载，且只依赖 NekoJS 支持的运行时语义。

“第三方提供了一个返回 NekoJS VNode 的函数”可以兼容；“第三方 npm 包里有 JSX”不能直接得出兼容结论。

### 4.2 第三方 mod 的 Screen：能观察 / 覆盖绘制，不等于交互集成

[Screen 绘后事件](<../../src/main/java/com/tkisor/nekojs/bindings/event/client/ClientEvents.java#L178-L181>)提供 [painter、screen 和鼠标位置](<../../src/main/java/com/tkisor/nekojs/wrapper/client/ScreenRenderEventJS.java#L19-L58>)。Fabric 也有[独立的绘后桥](<../../src/fabric/java/com/tkisor/nekojs/fabric/event/FabricClientEventBindings.java#L54-L68>)，通过 [Screen 初始化后的提取回调](<../../src/fabric/java/com/tkisor/nekojs/fabric/event/FabricClientEventBindings.java#L93-L94>)安装。

这能用于当前界面的状态提示、辅助标签和标记。但该 payload 没有 JSX mount、输入取消、共同焦点链或移除通知的集成契约。第三方绕开正常 Screen 流程时，还需要单独核实事件是否发生。

**不能把“Screen 上能画东西”写成“支持在任意 mod 界面内放 JSX Input / Dialog”。**

### 4.3 第三方 Widget / UI 框架：需要显式 Adapter

当前 common 有[结构化宿主 Interface](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L73-L126>)，这是可以保留的 Seam；但它不是开放任意标签的注册表：

- common [primitive 与 props 目录](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L340-L360>)是固定的；[校验](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L555-L569>)拒绝未知 primitive / prop；
- native [primitive 目录](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L38-L45>)及[绘制分支](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L539-L611>)也使用内建控件模型；
- 普通组件函数最终仍需展开成这些支持的值，不会把第三方 Java Widget 自动转换为可交互叶子。

不同 UI 框架可能自带布局、焦点、剪裁、纹理和事件循环。接入时应由一个指定框架的 Adapter 翻译契约，而不是让同一个点击被两套系统各执行一遍。没有测试过的第三方库不能列为“兼容”。

### 4.4 React / Web 组件库：语法相同不代表运行时相同

[内嵌手册](<../../common/src/main/resources/nekojs/authoring/handbook/jsx-ui.md#L230-L240>)已经明确：有限 appearance 对象不是 CSS，inline Select 不是原生 popup，没有 React / DOM / IME composition 契约。

可迁移的是业务状态与部分布局意图；不是直接运行依赖 DOM、React hooks、浏览器焦点或 CSS 布局的现成组件库。应做业务级包装或转换，不建议在 Minecraft 中额外实现一套完整浏览器兼容层。

## 5. 目前最值得修的缺口与风险

| 优先级 | 项目 | 证据与性质 | 用户影响 / 优化方向 |
|---|---|---|---|
| P0 | 真实客户端闭环验收缺失 | [交付记录仍标 Not run](<../implementation/2026-11-01-jsx-advanced-controls.md#L37-L61>)；这是证据缺口，不是已确认渲染故障 | 先在两个受支持的 NeoForge 节点证明打开、输入、模态、关闭、reload 与真实 GPU 行为 |
| P1 | 独立 Screen 与挂载区域未分离 | [固定构造](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L120-L151>)、[固定显示](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L988-L990>)；明确功能缺口 | 无法保留原版 / mod 页面的同时嵌入表单；引入受管区域挂载，不更换整个 Screen |
| P1 | 没有显式 parent / 返回会话策略 | [关闭调用原版父方法](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxScreen.java#L84-L95>)且[清理当前 Screen](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L1032-L1045>)；没有自行保留和恢复原宿主的契约 | 从设置页进入 JSX 后不能假定关闭就返回原设置页；按明确返回策略恢复仍有效的宿主，不能复活已销毁 UI |
| P1 | 焦点、输入、模态只协调 JSX 树 | [本地焦点遍历](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L1066-L1092>)、[本地模态按键](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L810-L850>)；明确互操作缺口 | 混合原生按钮后需唯一焦点与事件消费仲裁，防止点击穿透、重复提交、Escape 关错层 |
| P1 | 缺少通用 native Widget bridge | [固定校验](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L555-L569>)和[有限绘制类型](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L539-L611>)；明确功能缺口 | 不能直接复用原版编辑器或第三方控件；先接一类真实需要的 Widget，建立可验证叶子 Adapter |
| P2 | 输入法与无障碍仅有部分能力 | [字符与按键入口](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxScreen.java#L58-L81>)、[聚焦节点 narration](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L468-L499>)已存在；IME 和混合焦点实测不足 | 不是“无输入 / 无 narration”；需要 OS 输入、剪贴板、读屏与原生焦点交接验收，不能仅加字符回调就声称支持 IME composition |
| P2 | 大树更新和长列表尚需测量 | [root 重新展开、布局、协调](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L1230-L1289>)与[原生树遍历绘制](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L502-L611>)；性能风险，未证明已经卡顿 | 先测长列表、连续输入、资源更新的耗时和分配；按测量结果做批处理、缓存或虚拟化 |
| P2 | 平台覆盖有限 | [原生宿主门控](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L1-L2>)；明确支持范围 | 保持 capability 明确，后续按真实节点实现 Adapter，不能通过去掉 guard 虚构 parity |
| 单独项目 | 菜单 / Slot / 网络权威能力缺失 | [现有范围说明](<../../common/src/main/resources/nekojs/authoring/handbook/jsx-ui.md#L238-L240>)；明确非目标 | 这是完整玩法 UI 的缺口，但不应混入普通 Widget bridge；需要服务端验证、同步和关闭事务设计 |

### 5.1 静态路径可推导的四处问题：先补回归，再修实现

以下不只是“缺少第三方支持”，而是现有核心路径中值得先处理的问题。它们经独立审计及主审源码复核，**本轮尚未运行最小复现或新增测试**，不能写成已经动态复现、已经修复或已影响所有页面。

#### A. 函数组件内部 key 扁平化后可能撞名并重复复用同一 handle

- [外层 siblings 校验](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L971-L990>)检查的是组件自己的 key；[函数返回值展开](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L996-L1009>)使用新的 key 集合，且只有“单个返回节点自身无 key”时才继承组件 key。输出扁平合并后没有再校验同级 key。
- 最小分析场景：两个 `Cell` 外层 key 分别为 `a` / `b`，各自返回 id 不同、内部 key 都为 `inner` 的 input。外层和各自内部的校验都能通过，但最终同一父级出现两个 `inner`。这不是用户直接写了两个相同 sibling key，也不是重复 id。
- 下一次刷新时，[oldByKey](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L1067-L1095>)后写覆盖前写，keyed 分支又未检查 `used`；两个候选可复用同一个旧 handle。[native order](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostTree.java#L127-L132>)也没有拒绝重复 child handle。可能出现节点身份、输入状态或顺序混乱。
- 建议：保留组件 identity / key scope，或定义清晰的组合 key 规则；最终展开后的同级结果再校验唯一性，keyed reuse 也应拒绝重复使用同一旧节点。只加最后一道拒绝可防损坏，但不等于提供正确的组件局部 key 语义。
- 回归：两个组件内部相同 key、带 key 组件返回多个根节点、组件 reorder 后输入焦点 / 选择保留；同时保留直接重复 key 的拒绝行为。

#### B. render 中写 signal 可能同步重入，使旧输出覆盖新状态

- [owner-thread 调度](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L1099-L1130>)直接执行 reconcile；[signal 写入](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L1140-L1151>)同步使已订阅 root 失效；[reconcile](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L1230-L1289>)没有进行中保护。
- 最小分析场景：signal 初值 0，首次 render 成功并完成订阅；随后设为 1。render 读取局部值 `n=1` 后将 signal 设为 2，最终仍返回 `n`。内层 reconcile 可以先提交显示 2，外层再提交显示 1，造成 signal 为 2、界面却为 1。
- [手册](<../../common/src/main/resources/nekojs/authoring/handbook/jsx-ui.md#L9-L11>)要求在 render 外创建 signal，但这不等于运行时已禁止 render 中写入已有 signal。即使决定不支持 render-phase mutation，也应明确拒绝，而非允许嵌套提交留下不一致结果。
- 建议：选择并明确一种策略——进行中只标 dirty，外层结束后有界地刷新；或拒绝 render-phase 写入并给出诊断。不要无界重试造成无限循环。
- 回归：render / 函数组件内写入、连续写入、跨 root 共享 signal、回调重入和异常，断言最终 host snapshot 与已提交状态一致。

#### C. 普通关闭失败时，guest 与 native 的释放协议不一致

- [common close](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L1357-L1385>)在宿主事务失败时返回 `false`，保留订阅、事件和未 disposed 的 root；[现有 fixture](<../../common/src/test/resources/nekojs/language-ts-examples/tsx/ui-core.tsx#L346-L351>)明确把它作为可重试行为。
- [native teardown](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L1025-L1045>)调用 guest `close` 却不检查返回值，然后清树、关资源并丢弃 root 引用。若这一步事务失败，guest 仍可能被 signal 订阅保留，而 native 已关闭，普通重试协议失效。该失败注入路径需补真实 adapter 回归，不能仅靠源码调用顺序检查。
- 建议：明确“用户主动关闭可失败并重试”与“generation 终结必须释放”的不同语义，提供可靠的最终 detach，且不在已失效的 Graal Context 中强行执行回调。不要简单删掉失败保留机制或吞掉返回值。
- 回归：begin / remove / commit 抛错、guest close 返回 false、generation 清理、重复 removed；断言订阅、事件、资源和持有关系都符合选定协议。

#### D. screen 的 title / pausesGame props 被接受，但不控制真实 Screen

- [类型与目录](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L273-L274>)接受这两个 prop，[运行时目录](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L337-L338>)亦登记。
- native 的标题和暂停值在[构造器](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxScreen.java#L13-L21>)从 `ClientUI.screen` 参数固定；[isPauseScreen](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxScreen.java#L70-L72>)读取 final 字段。相比之下，[closeOnEscape](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L455-L457>)确实读取 root props。
- 分析示例：factory 使用标题 A、暂停 false，而 JSX screen props 使用标题 B、暂停 true；现有 native 仍取 A / false。类型可写不等于实际生效。
- 建议：确定唯一事实源，再选择同步到真实 Screen、明确限定为元数据，或经兼容迁移后拒绝无效配置。不能只在声明中继续保留并暗示有动态效果。
- 回归：factory 与 JSX 参数一致 / 冲突、重新 render 修改 props，验证真实标题、暂停行为和声明说明一致。

这四项建议排在扩张第三方框架之前。A / B 首先影响 common，C 影响 shared/native 边界，D 影响对外 props 契约；修复时应按范围同步测试、声明和英文内嵌资料，本轮没有实施这些变更。

### 5.2 性能与类型优化：有明确机制，但需要测量收益

- 成功更新目前重新展开和布局 root、更新保留节点及其顺序；native 事务[复制整棵 host tree](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostTree.java#L88-L94>)。**普通帧并不重新执行 guest render**，见[paint 路径](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxScreen.java#L30-L35>)，不能混淆两种开销。
- [owner-thread 调度](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L1099-L1130>)同步更新，只有显式 `UI.batch` 等批处理边界才合并；与[规范中同 owner tick 合并的目标](<../jsx-ui-spec.md#L120>)有差距。先规定顺序和可见性，再设计 coalescing，避免优化后悄悄改变事件契约。
- [移除 unused siblings](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L1094>)对每个旧节点扫描 `next`；特定宽列表场景呈二次扫描风险。先修 key identity，再考虑 handle Set、无变化 props / order 跳过和布局缓存；没有帧耗时数据时不声称“已卡顿”或“能提速多少”。
- [createSignal 已有泛型](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L325-L334>)，不能误报完全不支持类型推断。但[signal update、Store 和 dispatch](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L128-L148>)仍偏宽：可逐步加强 `update((prev: T) => T)`、泛型 Store、按事件 / 控件区分 payload，减少回调参数不匹配。
- 当前函数组件没有 React mount / unmount / effect 生命周期协议，不能把业务自己管理 cleanup 写成框架已自动托管组件副作用。

### 已有机制不应推倒重来

[UI 生命周期状态机](<../../src/main/java/com/tkisor/nekojs/client/ui/UiRootLifecycle.java#L11-L21>)已经区分 candidate / active / closing / closed，并用 generation 加 globals 实例识别失败候选；[延迟打开](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L955-L985>)不在候选提交前显示页面；[teardown](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L1008-L1050>)有重入保护和释放路径。common 更新也有[事务 commit / rollback 和失败保留](<../../common/src/main/resources/nekojs/node/modules/jsx-runtime.ts#L1264-L1289>)。

优化应扩展这些机制，而不是复制第二套 root owner 或让第三方 UI 绕过 generation 清理。也不能把“需要真实 reload 验收”误说成“当前完全没有 reload 清理代码”。

## 6. 推荐的优化设计：组合优先，继承作为 Java 侧实现细节

以下全部是**建议设计，尚未实现**；名称用于解释职责，不是可以调用的新 Script API。

### 6.1 拆开“内容”和“宿主会话”

```text
现有 JSX 内容 Module
  VNode → 校验 → 布局 → reconciliation → 宿主事务
                       ↓
受管 Mount 会话 Interface（建议）
  绑定内容 / 更新区域 / 卸载 / 检查能力
                       ↓
  独立 Screen Adapter    已有 Screen 区域 Adapter    非 Screen UI Adapter
  拥有整个页面           只借用指定区域              接入第三方宿主能力
                       ↓
          可选：Workspace 多窗口层与受控 native Widget 桥
```

这是沿现有宿主 Seam 深化 Module，而不是重写 JSX runtime。调用者不应理解 Minecraft 每个版本的所有 Screen 回调；事件、线程、坐标、焦点和清理的复杂度应集中在少量 Adapter 的 Implementation 中，获得 Locality。

**先有真实使用场景再抽象**：保留现有独立 Screen，选一个简单原版设置页实现区域挂载，以这两个 Adapter 验证 Interface；之后必须选一个真实的非 Screen 宿主，排查 Interface 是否仍暗藏 Screen 假设。不要先设计能兼容所有第三方框架的空壳 SPI。

通用 Interface 不暴露 `Screen`，也不要求第三方继承 NekoJS 类。Java 侧可以为特定原版类写子类 Adapter，但它只是 Implementation；JSX 内容与具体父类解耦。原版类为 final、构造器不可访问或第三方不开放 Hook 时，不能承诺自动继承或可靠嵌入。

### 6.2 宿主会话必须明确的契约

| 维度 | 必须决定的行为 |
|---|---|
| 所有权 | 独立模式拥有 Screen；区域模式只拥有 JSX 挂载和监听器。区域卸载不能调用宿主关闭，更不能把当前 Screen 置空 |
| 可见性 | candidate 可以准备内容，但提交前不注入原生 children、不展示、不接输入；失败仅释放候选资源 |
| 事件结果 | 区分未处理、已消费、已捕获；规定前置 / 后置路由和模态优先级。同一点击只能有一个最终消费者 |
| 焦点 | 原生与 JSX 共用一个仲裁者；Tab / Shift-Tab 可以跨区域，模态阻止背景输入，关闭模态有合法焦点回退 |
| 绘制 | 明确背景 / 前景阶段、剪裁、tooltip 和模态顺序；不要仅从绘后事件补一个渲染回调就声称完整接入 |
| 坐标 | 物理窗口、GUI scaled 坐标、宿主局部区域、设计坐标的变换由 Adapter 统一管理，绘制与命中测试必须用同一映射 |
| 宿主变化 | init / resize / GUI scale / removed / replacement 均有处理；重复 init 不重复挂载监听器 |
| reload | 成功替换才撤销旧会话；失败保留旧会话；旧 generation 的异步任务不得修改新宿主 |
| 关闭与返回 | 独立页面的 return target 必须显式且仍有效。清理只操作自己仍拥有的当前对象；若新页面已打开，不得被旧页面的晚到清理关闭 |
| 降级 | 不支持的宿主 / Widget 返回明确不可用结果，不静默退化成不可交互的图片 |

这里需要区分 **detach、隐藏、销毁**。第三方 Screen 的暂时覆盖或重新初始化不一定等于业务对象可安全销毁；反过来，现有 JSX Screen 的 removed 路径会释放 root，也不能盲目缓存后当作 parent 再次打开。

### 6.3 第三方支持分三种接入粒度，不要混为“任意 UI 插件”

1. **已有 Screen 区域扩展**：按稳定类型 / 标识匹配已知宿主，在明确区域叠加或混合交互。原宿主继续拥有菜单和原生控件。首版不要承诺替换全部布局。
2. **原生 Widget 叶子**：选一类确实需要复用的原版 Widget，定义创建、测量、更新、绘制、输入、焦点、narration、销毁和失败回滚。然后再用一款实际安装、版本匹配的第三方 Widget 验证相同 Interface。受控值以 JSX 状态为准，原生编辑通过事件反馈，不能维持两份互相覆盖的状态。
3. **第三方 UI 区域（隔离区域）**：如果对方已有布局和控件树，NekoJS 只管理外部矩形、可见性、数据与生命周期，内部继续由对方管理。整个第三方框架不必逐节点翻译成 NekoJS primitive，更不能让两套 reconciler 同时拥有同一批原生对象。该区域也可来自不继承 Screen 的系统。

Widget 创建如果会注册全局监听器或加载资源，必须有 prepare / publish / rollback 策略；不能仅在宿主事务中缓存 Java 对象引用就称为原子更新。第三方无法撤销的副作用不能虚报为可回滚，应延迟到发布阶段、提供补偿清理或拒绝该集成模式。Screen 级框架另用会话 Adapter 或导航，不将整个 Screen 塞成 JSX 叶子。

第三方扩展若形成新的 collection channel，应沿仓库现有 Point / Plugin Hook / Contributor 机制建设，保持配对检查；不要增加第二个全局静态注册表。通用数据、校验和不透明句柄可以放 common，Minecraft 的 `Screen` / Widget 类型及 loader hook 留在版本侧，不能让 common 依赖 Minecraft。

### 6.4 能力与类型必须一起交付

建议按当前安装节点和具体宿主报告能力，例如：独立 Screen、区域挂载、native Widget、混合焦点、菜单交互。绘制、指针、键盘 / 焦点、模态协调也要分级；只有绘制 Hook 的 HUD 只能声明只读展示，不能悄悄将可编辑控件变成图片。它们不能只有一个笼统的 `uiSupported=true`。

扩展控件需要统一来源的描述与 props 校验，再同步 Probe、TypeScript 声明、运行时支持和英文内嵌文档。当前 `supportsPrimitive` 在 common 的固定校验之后才发挥作用，**只让 native 返回 true 并不能让新标签通过 common 校验**。不能只手改一份声明或扩大任意 Java 对象入口。

### 6.5 把调用者需要知道的内容控制在小 Interface 内

脚本只应描述“放什么内容、放在哪里、如何响应事件”，以及显式执行打开、更新或卸载；不应自行拼接每个版本的鼠标 Hook、scissor 恢复或 generation 检查。用受管会话返回稳定句柄，隐藏 Java 对象；将宿主线程、能力拒绝、候选提交和清理的复杂度留在 Module 的 Implementation 内。

布局也只有一个负责人：普通 JSX 由 NekoJS 布局；native 叶子提供测量并服从分配矩形；第三方隔离区域自行布置内部节点。三者之间通过矩形和受控数据交接，不共享可变控件树。

## 7. 游戏内多窗口：可以实现，但需要独立的 Workspace 层

### 7.1 能做到什么，不能混淆什么

**方案可以扩展为类似 Windows 桌面的游戏内多窗口：同时可见、拖动、调整尺寸、前后叠放、激活切换，以及后续的最小化 / 最大化 / 吸附。** 这是设计可行性判断，不是当前已实现或已做性能验收的功能。

推荐形态是“一个 Minecraft Screen 承载一个 Workspace，Workspace 管多个逻辑窗口”，不是反复调用 `setScreen`，也不是同时维护多个活跃 Minecraft Screen。现有[显示路径](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L988-L990>)切换当前页面；[removed 路径](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxScreen.java#L90-L95>)会释放原 root，不能据此叠加独立页面。

```text
一个实际 Minecraft Screen（或有相应能力的其他宿主）
└─ Workspace：窗口、叠放、焦点、捕获、模态的共同负责人
   ├─ 窗口 A：标题栏 + JSX 内容
   ├─ 窗口 B：标题栏 + JSX / 原生 Widget
   └─ 窗口 C：标题栏 + 第三方隔离区域
```

这里的窗口是游戏 framebuffer 内的逻辑窗口，不是额外的操作系统窗口，不会自动出现在系统任务栏或移到另一个显示器。创建真正的 OS 多窗口还涉及 GLFW 窗口、图形上下文和 Minecraft 主循环，是另一项工程，不在此方案内。多窗口也不代表多线程，各窗口 UI 仍在客户端 owner thread 串行处理。

### 7.2 Workspace 是可选 Module，不是每个挂载都必须变成桌面

简单的独立页、原版页面上的一小块表单无需 Workspace。需要多个浮动窗口时才启用它；它可放在独立 Screen，也可放在宿主允许的区域中，后者的窗口不能任意越出区域抢占整个游戏输入。

职责分配：

| Module / Adapter | 独占职责 | 不应承担的职责 |
|---|---|---|
| 宿主 Adapter | 真实帧绘制、原生输入入口、viewport、线程和宿主生命周期 | 不让每个子窗口分别切换 Minecraft Screen |
| Workspace | 窗口身份、矩形、叠放、激活、跨窗口焦点、捕获、模态范围和关闭顺序 | 不接管第三方隔离区域内部的控件树 |
| Window 内容 | 正常 JSX 状态、布局和业务事件，或经 Adapter 托管的第三方内容 | 不各自注册全局键盘 / 鼠标监听器，不直接修改其他窗口的原生状态 |

首版建议在一个 Workspace 下使用一棵受管 JSX 内容树，窗口通过稳定 ID / key 保持身份，并由共同事务发布内容与窗口顺序。这能复用现有 root 机制，避免立即引入多个独立 root 的焦点竞争和跨 root 提交。第 5.1 节的 key 作用域问题必须先修，不能靠各窗口碰巧使用不同字符串规避。

若以后测量证明需要每窗口独立 root，再在同一个 Workspace 下增加隔离；多 root 默认不具备跨 root 原子提交，也不能各自拥有完整宿主的焦点和模态控制权。

### 7.3 窗口状态与生命周期

每个窗口至少有稳定 ID、标题、矩形、最小 / 最大尺寸约束、是否可拖动 / 缩放 / 关闭、显示状态，以及可选的所属窗口。Workspace 保存有序窗口列表和活动窗口，避免让脚本随意维护无限增大的全局 zIndex。

几何状态采用一个事实源：默认由 Workspace 管理拖动位置和尺寸，业务收到提交后的变化通知；若以后支持受控矩形模式，则由外部状态驱动，不能再同时让内部位置成为第二套权威状态。鼠标命中、绘制与检查信息必须来自同一个已发布几何快照。

- **隐藏 / 最小化**：窗口不绘制、不命中、不参与焦点；显式保留其可恢复状态。后台更新或暂停策略必须定义，不能默认已经销毁，也不能继续执行不可见输入。
- **最大化**：保存还原矩形，限制于 Workspace 可用区域；不会另开一个 Screen。
- **关闭**：内容退出与资源释放一次；关闭单个逻辑窗口不关闭整个 Workspace。最后一个窗口关闭后保留空桌面还是退出，作为 Workspace 的明确策略。
- **宿主丢失 / generation 终结**：取消拖动与捕获，统一释放所有窗口、监听器和外部区域；不能仅关闭最上面的一个。
- **reload**：新内容先准备，成功提交才替换旧会话；失败保留旧可用桌面。可以按稳定 ID 迁移经过校验的纯数据布局，不能跨代复用 guest 回调、Graal Value 或旧原生 Widget。
- **暂停游戏**：由真实宿主决定，不按每个子窗口的 `pausesGame` 各自决定；多人模式也不能因此暂停服务端。

布局持久化应为显式后续选项，只存版本化的纯数据矩形 / 展示状态；不把整个业务 signal store 默认写盘。加载时按当前分辨率约束位置，至少让标题栏和关闭入口仍可触达。

### 7.4 叠放、拖动和输入规则必须同时设计

| 行为 | 建议规则 |
|---|---|
| 叠放与绘制 | 普通窗口按 Workspace 顺序由后向前绘制；每窗口先 frame / content，再其局部浮层；受层级限制的模态在所属组之上。tooltip 不能从后方窗口穿过前方窗口 |
| 命中 | 从前向后找最上面符合条件的目标；不透明窗口框架 / 内容区默认阻挡下层点击，控件没处理不代表穿透。只允许显式设计的穿透区域 |
| 点击激活 | 点击非活动窗口先合法置前并激活，同一次事件只路由一次；被模态阻止的窗口不能置前 |
| 拖动 | 标题栏开始拖动并捕获指针；鼠标离开原窗口后仍由原拖动者接收 move / release，不重新命中下层。按钮、输入框和 resize grip 不触发标题栏拖动 |
| 缩放 | 边 / 角拖动遵循最小尺寸、viewport 与内容约束；尺寸变化触发布局，不只把已有图片拉伸 |
| 键盘焦点 | 一次只有一个活动键盘目标；Tab 默认在活动窗口内，跨窗口切换使用单独约定的操作。不能覆盖操作系统保留快捷键并声称等价 Windows |
| 捕获取消 | 关闭、隐藏、宿主失焦 / 替换、reload 或被阻塞时取消 capture，清理 pressed 状态；晚到 release 不触发其他窗口点击 |
| Escape | 从最上层可关闭 popup / 模态处理，再按窗口策略处理活动窗口，最后由 Workspace 决定是否交给宿主；一次按键不能连关多层 |
| 输入到游戏 | 全屏桌面按宿主规则接管输入；HUD / 区域挂载只处理其明确授权的事件，未处理事件按契约交回游戏，避免误触移动 / 攻击 |

模态要分为“阻塞所属窗口组”和“阻塞整个 Workspace”两种，不能让每个窗口各自声称是全局最高层。关闭一个窗口级 Dialog 后，只恢复到仍存在且允许接收输入的原焦点。无障碍顺序也应反映活动窗口及其标题，而不是朗读所有被遮挡窗口。

当前 native 已有单树的[模态状态](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostAdapter.java#L68>)和[指针捕获](<../../src/main/java/com/tkisor/nekojs/client/ui/JsxHostTree.java#L36-L67>)，可以复用机制，但尚无 Workspace / Window 分组与跨窗口协调，不能仅给几个 panel 加 zIndex 就声称多窗口完成。

### 7.5 第三方 UI 能不能放进这些窗口

- 返回 NekoJS VNode 的业务界面可以作为窗口内容，前提是完成 Workspace 支持。
- 可接受外部矩形、剪裁、输入与生命周期的第三方 Widget / 隔离区域，可通过 Adapter 放入窗口；不要求继承 Screen。
- 只支持全屏、直接切换 Minecraft 当前 Screen、依赖全局焦点或不能接受区域裁剪的第三方页面，不能自动窗口化。需要专用改造，或作为外部页面导航打开。
- 不能直接循环调用多个现有 Screen 的 render 方法，就宣称它们同时可交互；其 init / removed、输入、背景、坐标和资源所有权都可能仍假定独占整个屏幕。
- 原版容器 / Slot 仍有服务端菜单与同步约束。视觉上放两个物品窗口不意味着能同时打开两个独立权威容器，不能绕过原版菜单协议。

如果第三方需要离屏 framebuffer 才能嵌入，这是该 Adapter 额外的渲染能力、状态恢复和资源预算，不要求所有窗口默认创建离屏纹理；首版普通 JSX 窗口直接裁剪绘制即可。

### 7.6 最小可交付范围和验收

首版只做：两个以上可见窗口、标题栏、拖动、点击置前、焦点隔离、关闭、边界约束，以及完整的宿主 / reload 清理。随后加缩放、最小化 / 最大化，再考虑吸附、平铺、任务栏和复杂动画；这些不是实现基本叠放的前置条件。

自动测试应通过同一 Workspace Interface 覆盖矩形 / z 顺序 / 路由与清理；真实 NeoForge 客户端再验证可见结果和原生输入链。最低用例：

1. 三窗口交叠，点击前窗按钮只计数一次，后窗对应位置无事件；点击露出的后窗区域后顺序与焦点一致更新。
2. 拖动跨越另一窗口并移出原区域，capture 不转移；拖动中关闭 / reload / 失焦后不继续移动或误提交点击。
3. 两个输入窗口切换后，文本、选择和各自业务状态不串；隐藏 / 最小化窗口不接键盘。
4. 一个窗口级模态不应误封其他被允许的窗口；Workspace 模态则真正阻断所有背景窗口及原生区域。
5. GUI scale / 分辨率变化、最小尺寸和屏幕边缘限制下，标题栏可找回，命中与绘制一致。
6. 关闭一个窗口不销毁其余窗口；关闭 Workspace 和 generation 终结均无遗留 capture、监听器或旧回调。
7. 混合一个第三方隔离区域，验证 clip / 绘制状态恢复，以及焦点和模态不会被第三方绕过；不支持的区域被明确拒绝。
8. 记录不同窗口数 / 节点数下的布局、更新耗时与分配。拖动不应无条件让每个窗口的业务 render 重跑；需要同时保证用于绘制与命中的几何一致，再按实测选择 transform / 缓存优化。

多窗口验收当前全部待运行；此节没有把现有单页 fixture 结果算作多窗口证明。

## 8. 测试应该怎么补

### 8.1 现有测试有价值，但不等于真实客户端

[原生 fixture](<../../src/test/java/com/tkisor/nekojs/client/ui/NativeUiScreenFixture.java#L36-L88>)使用 Unsafe 分配 Minecraft / adapter / Screen，并填充字段；[runtime 安装](<../../src/test/java/com/tkisor/nekojs/client/ui/NativeUiScreenFixture.java#L104-L113>)确实运行 canonical JS，但并未通过真实启动、真实窗口和正常构造路径建立完整客户端。

现有[模态测试](<../../src/test/java/com/tkisor/nekojs/client/ui/NativeDialogModalTest.java#L19-L96>)能验证大量路由规则；[计数器测试](<../../src/test/java/com/tkisor/nekojs/client/ui/Ticket48PerformanceCountersTest.java#L12-L45>)能检查更新 / 布局 / 绘制 / 清理路径。它们不证明真实鼠标事件注入、GPU 绘制、OS 剪贴板、输入法或与其他 mod 共存。应保留并补上另一层，而不是否定或复制它们。

### 8.2 第一阶段：先把现有功能跑通真实窗口

只在当前支持的 NeoForge 26.1.2 / 26.2.0 启动真实客户端，用正常 ScriptManager 与 CLIENT 脚本加载示例，不用 Unsafe 替换构造。建立一页诊断 fixture，覆盖：

| 用例 | 通过判据 |
|---|---|
| 打开、绘制与坐标 | 真实 screen 存在、可见控件与 inspector 一致；GUI scale / 窗口变化后点击位置正确 |
| Button / Input / Slider | 物理或真实窗口输入链路中事件计数准确，焦点、退格、选择、剪贴板和拖动可观察 |
| Tabs / Dialog | 未选中页不接输入；Dialog 的 Tab、Escape、遮罩规则正确且背景无重复回调 |
| Item / tooltip | 图标实际显示，数量与 tooltip 正确，clip 与悬浮命中一致 |
| 关闭 / 替换 / 重新打开 | 旧 root disposed，旧回调不执行；创建新实例后仍可操作 |
| CLIENT reload 成功 / 失败 | 成功后旧 generation 失效；候选失败保持旧可用页面；连续 reload 没有重复监听器 |
| 晚到任务与异常 | 页面关闭后异步任务不复活 UI；回调异常不导致无法关闭，下次打开可恢复 |

产物应包含版本 / loader / mod 列表、脚本、操作、事件计数、日志和必要截图。截图支持布局判断，不能替代事件和资源释放断言；自动化点击也不能冒充物理输入或人工视觉结论。IME composition 若未实现，应记明确“不支持”及普通字符输入的观察，不把它列成既有能力的必过项。

**不是先做五节点相同 JSX 页面冒烟**：1.21.1 和两个 Fabric 节点当前缺少原生宿主，只应验证不误暴露 Binding / 不错误加载客户端类等拒绝路径；实现 parity 后才加入对应的真实页面验收。

### 8.3 第二阶段：优化实现的关键红线用例

- 原版设置页中挂一块 JSX 表单：原版按钮仍可用；区域之外事件传回宿主。
- 混合焦点：原生 → JSX → 原生的 Tab / Shift-Tab 顺序一致；一次 Enter 只提交一个目标。
- 模态竞争：JSX 模态出现时原生背景不能接点击；原生顶层弹窗出现时 JSX 不越级抢输入。
- 重入与晚到清理：宿主 removed、reload、root.close 相互嵌套仍只释放一次；新 screen 不被旧会话关闭。
- 宿主 init / resize 重复发生：不重复安装 root，不累计监听器。
- 第三方 Adapter 拒绝当前版本 / Widget 或 prepare 失败：保留原 Screen 与旧 JSX 树，无半安装资源。
- 容器先只测“旁边加只读区域且不破坏原版 Slot”，不要把 Slot 点击权威迁移混入挂载首版。

之后再选定一个实际支持的第三方 mod 做版本固定的契约与真实客户端测试；记录具体兼容组合，不宣称“任意第三方 UI”。

## 9. 建议的交付顺序与完成条件

| 阶段 | 交付 | 完成条件 |
|---|---|---|
| A：核心正确性与当前能力验收 | 第 5.1 节四处问题的回归与修复；真实客户端 fixture、输入 / 生命周期结果、未通过清单 | common / native 分层回归；两个受支持 NeoForge 节点分别记录结果；自动断言与维护者视觉结论分开；先回归再修复 |
| B：宿主区域挂载 | 独立与借用宿主的两种 Adapter，显式返回策略，混合输入 / 焦点契约 | 一类简单原版 Screen 的完整闭环，候选失败保留、重复 init、关闭与 reload 都有回归 |
| B2：游戏内 Workspace | 一个宿主内的多逻辑窗口，按第 7.6 节分期实现 | 三窗口交叠、拖动捕获、焦点 / 模态隔离、关闭与 reload 回归；真实客户端验证绘制和输入；不声称任意 Screen 可窗口化 |
| C：原生控件复用 | 一种原版 Widget 叶子桥，再验证一种真实第三方 Widget | 生命周期 / 焦点 / 事务回滚 / 类型生成一致；能拒绝不支持版本，而非悄悄失效 |
| D：按需求扩展 | 选定第三方 UI 框架；平台 parity；长列表优化 | 每项由真实用例驱动，独立证明支持矩阵；性能先测后优化 |
| E：玩法容器 | 客户端布局 + 服务端菜单 / Slot 契约 | 单独设计权限、同步、关闭、断线与多人一致性；只读 Item 不能作为成功依据 |

优先暂缓：完整 React 兼容、任意 Java 类作为标签、一次性对接所有 UI 框架、无实测依据的全量重写和动画扩张。

## 10. 本轮交付与未完成事项

- 已完成：对正式入口、原版 / 第三方互操作缺口、现有生命周期与测试证据的源码审计；完善组合式宿主、原生叶子及第三方隔离区域设计；补充游戏内 Workspace 多窗口的职责、状态、输入规则、分期与验收项。
- 行为 / 契约影响：**无运行时变更**。本文的 Mount、Widget bridge、非 Screen Adapter、Workspace、capability 和返回策略均为建议，不是已交付功能。
- 文档验证：本页相对文件链接、源码行范围和行尾空白，以及 `git diff --check` 均已检查；本轮仅更新此设计文档，未修改生产代码。
- 未运行：本轮 Gradle / GPU / 真实 UI / 多窗口 / 物理输入 / 多人容器测试；未验证任何具体第三方 mod 的兼容性。
- 维护者验收：**待执行**。源码与 fixture 不能代替真实客户端结论。

**下一步最值得做的不是继续堆控件，而是“核心正确性回归 + 真实 UI 验收 → 在原版页挂一个 JSX 区域”这条闭环。** 它既能暴露现有宿主问题，也会给后续第三方集成提供一个有真实需求、可测试、不会破坏原界面所有权的 Seam。
