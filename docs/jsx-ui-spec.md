# Spec: NekoJS 纯 JSX 客户端 Screen UI

Status: ready-for-agent
Type: spec

## Problem Statement

NekoJS 已经支持 `.jsx` / `.tsx` 的语法 lowering，但当前 lowering 只把 JSX 转换为 `jsx`、`jsxs`、`Fragment` 或 classic factory 调用。Minecraft 客户端没有对应的 UI host、VNode 生命周期、布局、输入分发和状态更新协议，因此 JSX 目前只能生成脚本侧树状数据，不能直接成为可交互的游戏内 Screen。

如果直接把 React、DOM 或任意 Java GUI 类暴露给脚本，会同时引入几个问题：脚本 API 被浏览器语义绑死；Minecraft 版本差异泄漏到脚本；GraalJS Context、客户端 owner thread、Screen 生命周期和 reload generation 互相穿透；渲染帧热路径可能重复执行脚本；旧 generation 的事件闭包可能在 reload 后继续操作已经失效的对象。现有手写 Screen 可以工作，但每个新 GUI 都要重复处理布局、焦点、输入、滚动、异常和关闭清理，无法成为可复用的脚本 UI 基础。

脚本作者需要一种真正可用的 JSX UI：能用组件组合界面，能用响应式状态驱动更新，能处理鼠标、键盘、文本输入、焦点和滚动，能在窗口调整大小后稳定布局，并且在 CLIENT reload、脚本异常和 Screen 关闭时不泄漏 Context 或回调。维护者则需要一个不依赖 Minecraft 的 common 逻辑核心，以及由版本/平台 Adapter 承担原生 Screen、绘制和输入差异的边界。

## Solution

引入一个面向 Minecraft Screen 的 JSX UI runtime。JSX automatic runtime 产生不可变的脚本侧 VNode；一个 retained tree reconciler 将 VNode 树与受控的 Minecraft host node 树进行 diff、布局、更新和释放。脚本每次状态变化只重新执行受影响的 root render function，Minecraft 每帧读取已提交的 host tree 并执行原生绘制；正常渲染帧不重新执行 GraalJS JSX。

第一阶段只交付独立客户端 Screen。脚本用函数组件和固定的 host primitives 描述界面，用显式 signal/store 管理状态，用稳定的事件对象处理交互。UI root 绑定创建它的 CLIENT generation，并由唯一的 `NekoRuntimeRoot` 负责其生命周期。CLIENT reload 成功提交后，旧 root 和旧 Screen 失效并关闭，新 generation 不自动复活旧 Screen；需要恢复的业务状态必须由脚本显式持有或序列化。

common 只持有 VNode、状态依赖、reconciler、布局模型、事件模型和 host contract 等不依赖 Minecraft 的逻辑 Module。Screen、GuiGraphics、字体、纹理、原生输入控件和版本差异由客户端平台/版本 Adapter 持有。首个完整 proof 以 NeoForge 26.2 为目标；其它保留节点按真实 Adapter、声明和 runtime smoke 结果标记 `supported`、`partial` 或 `unavailable`，不以静态编译推断 parity。

## User Stories

1. 作为脚本作者，我希望可以用 `.jsx` / `.tsx` 文件描述一个 Minecraft Screen，以便界面结构不再需要手写 Java Screen 类。
2. 作为脚本作者，我希望 JSX 使用 NekoJS 已有的 automatic runtime，以便不需要在每个脚本中手动设置全局 factory。
3. 作为脚本作者，我希望 JSX runtime 返回纯脚本侧 VNode，而不是立即创建 Minecraft 对象，以便组件树可以在 common 逻辑中测试并在提交前校验。
4. 作为脚本作者，我希望 JSX 的 `Fragment`、嵌套元素、条件表达式、数组映射、spread props 和函数组件继续按现有 JSX lowering 语义工作，以便能用熟悉的组合方式组织 UI。
5. 作为脚本作者，我希望小写 JSX 标签只对应 NekoJS 明确登记的 host primitive，以便标签语义稳定而不会意外依赖浏览器 DOM 或某个版本的 Java 类名。
6. 作为脚本作者，我希望大写 JSX 标签可以是返回 VNode 的普通 JS/TS 函数组件，以便把复杂界面拆成可复用的业务组件。
7. 作为维护者，我希望任意 Java 类不能自动成为 JSX host，以便 Minecraft 类型、HostAccess 和版本差异不会泄漏成不受控的脚本 API。
8. 作为脚本作者，我希望可以打开一个由 render function 产生的 Screen root，以便状态改变后 runtime 能重新计算组件树而不是只显示首次构建结果。
9. 作为脚本作者，我希望一个 root 有明确的打开、提交、关闭和 disposed 生命周期，以便我知道什么时候可以继续使用 signal、handle 和事件闭包。
10. 作为脚本作者，我希望可以创建 signal 或 store，并在 render function 中读取它们，以便状态变化能自动触发对应 UI root 的更新。
11. 作为脚本作者，我希望 signal 支持读取、直接设置和基于旧值更新，以便计数器、表单、筛选器和异步结果都能用同一套状态语义表达。
12. 作为脚本作者，我希望状态更新不会要求我手动查找和更新每个 Minecraft widget，以便 UI 逻辑保持声明式。
13. 作为维护者，我希望 reconciler 通过依赖跟踪只让受影响的 root 或子树重新计算，以便频繁状态变化不会把全部脚本重新执行到每个渲染帧。
14. 作为维护者，我希望 VNode 带有稳定的 `key` 语义，以便列表插入、删除和重排不会错误复用输入框、焦点或滚动位置。
15. 作为脚本作者，我希望列表和条件节点能被添加、删除和替换，以便动态菜单、错误列表、配置项和分页内容无需命令式增删子节点。
16. 作为维护者，我希望 host node 的创建、属性更新、子节点排序和销毁都有确定的 diff 结果，以便状态更新不会产生重复控件或遗留事件监听器。
17. 作为脚本作者，我希望有面向 Minecraft 的 `screen`、`panel`、`row`、`column`、`stack`、`scroll`、`label`、`button`、`input`、`image` 和 `spacer` 等基础元素，以便常见 Screen 不需要直接绘制底层矩形。
18. 作为脚本作者，我希望 row/column 等布局支持固定尺寸、填充尺寸、最小/最大尺寸、间距、内边距、对齐和锚定，以便界面能适应不同窗口大小和内容长度。
19. 作为脚本作者，我希望布局以 Minecraft 逻辑像素和受控约束为基础，而不是实现浏览器 CSS，以便布局行为可测量、可预测且不产生完整 CSS 的维护负担。
20. 作为脚本作者，我希望文本测量由 Minecraft Font Adapter 完成，以便不同版本字体宽度、换行和文本缩放不会由脚本自行猜测。
21. 作为脚本作者，我希望 scroll 能管理裁剪区域、内容高度、滚轮输入和滚动位置，以便长列表不必手写 scissor 和边界判断。
22. 作为脚本作者，我希望 button 有禁用、悬停、按下、焦点和 tooltip 等可观察状态，以便交互反馈不需要复制原生 widget 的内部状态机。
23. 作为脚本作者，我希望 input 能处理文本值、光标、选择、焦点、最大长度和文本事件，以便配置界面和搜索界面可以完全由 JSX 组合。
24. 作为脚本作者，我希望可以声明鼠标点击、鼠标释放、滚动、键盘、文本输入、焦点变化和提交事件，以便 UI 交互不需要直接订阅版本特有的 Screen 回调。
25. 作为脚本作者，我希望事件回调收到稳定的脚本事件对象，而不是 `GuiGraphics`、原生 `Screen` 或版本特有的输入对象，以便同一脚本面不随 Minecraft 版本变化。
26. 作为维护者，我希望事件回调替换和移除与 host node 生命周期绑定，以便旧闭包不会在节点销毁后继续响应输入。
27. 作为脚本作者，我希望焦点顺序、Tab 导航、Shift+Tab、Enter/Space 激活和 Escape 关闭有确定语义，以便 Screen 不只能用鼠标操作。
28. 作为脚本作者，我希望 host node 能提供稳定的 narration 文本和焦点顺序，以便 Minecraft 的辅助叙述不会因为自定义 UI 而完全丢失。
29. 作为维护者，我希望所有 UI state 写入和 host 操作都发生在客户端 owner thread，以便 GraalJS Context 与 Minecraft 客户端对象不发生跨线程访问。
30. 作为脚本作者，我希望从非 owner thread 更新 UI 时得到明确失败或显式排队结果，而不是偶发的并发异常或静默丢更新。
31. 作为维护者，我希望普通绘制帧不调用 GraalJS render function，以便脚本执行、异常和 GC 不进入渲染热路径。
32. 作为维护者，我希望一次状态更新先生成并校验候选 VNode/布局结果，再原子提交 host tree，以便渲染线程不会看到半更新的 UI。
33. 作为脚本作者，我希望组件或事件回调抛错时错误被记录到既有 CLIENT 诊断链路，且其它控件和最后一次有效 UI 不被破坏，以便单个脚本错误不会让整个客户端 Screen 失控。
34. 作为维护者，我希望 reconciler 在 render、布局、事件和 host Adapter 出错时能区分错误阶段、脚本来源、UI root 和 generation，以便错误面板能定位问题。
35. 作为维护者，我希望 UI root 只属于创建它的 CLIENT generation，以便 reload 后旧 Context、signal 订阅、事件 token 和 host node 能整体清理。
36. 作为客户端玩家，我希望 CLIENT reload 关闭旧 generation 的 JSX Screen，而不是留下一个看似可用但已经没有有效脚本环境的界面。
37. 作为维护者，我希望 reload 失败时当前 active generation 和正在显示的旧 Screen 继续可用，以便候选 UI 不会污染线上界面。
38. 作为脚本作者，我希望旧的 Screen handle 在 root disposed 后明确失败，以便不会静默操作新 generation 或另一个 Screen。
39. 作为维护者，我希望 UI root 的关闭、屏幕移除、Context 关闭和 listener 清理幂等，以便窗口关闭、reload 和客户端退出的顺序变化不会造成二次释放。
40. 作为脚本作者，我希望纯 UI 脚本只在客户端环境可见，以便 dedicated server 不加载 Minecraft client class 或执行客户端 JSX root。
41. 作为维护者，我希望 common UI 逻辑不依赖 Minecraft 或 loader，以便 VNode、状态和 reconciler 可以使用普通测试验证。
42. 作为平台维护者，我希望 Screen、绘制、字体、纹理和输入由平台/版本 Adapter 提供，以便版本差异不散落在 JSX runtime 和组件实现中。
43. 作为平台维护者，我希望首个完整实现只针对 NeoForge 26.2，并对其它节点显式报告能力等级，以便不会把单节点 proof 误写成全节点 parity。
44. 作为脚本作者，我希望 JSX UI 的 TypeScript declaration 提供 intrinsic elements、组件 props、事件对象和 signal/store 类型，以便 IDE 能在没有 React 类型包的情况下完成检查和补全。
45. 作为维护者，我希望声明、managed contract、Probe 输出和 automatic runtime 的模块解析只有一条事实链，以便 UI API 不会出现运行时支持但声明缺失，或声明存在但 runtime 不支持的分裂。
46. 作为维护者，我希望 UI 的公共契约不暴露原生 widget 身份、Minecraft 对象身份或内部 reconciler 结构，以便版本升级可以替换实现而不制造新的 breaking surface。
47. 作为脚本作者，我希望 UI root 可以通过显式选项设置标题、初始尺寸策略、关闭行为和是否暂停游戏，以便不同 Screen 能表达合理的 Minecraft 使用场景。
48. 作为维护者，我希望 Screen 的默认关闭行为、暂停行为和外部 `setScreen` 替换行为都有清晰的 root cleanup 语义，以便 UI 不依赖某个调用者记得手动释放。
49. 作为维护者，我希望 UI runtime 能通过 fake host Adapter 观察布局、输入、提交和清理结果，以便大多数逻辑不需要启动 Minecraft 才能测试。
50. 作为发布负责人，我希望 JSX UI 的 contract、声明、NeoForge runtime smoke 和节点能力记录在同一套验收材料中，以便新功能可以纳入后续 release gate。

51. 作为脚本作者，我希望网页转换工具能接收结构、样式、资源、字体、交互说明和不同尺寸参考图，而不是只依赖一张截图，以便转换结果有足够的事实依据。
52. 作为脚本作者，我希望网页中的常见结构元素能映射到 NekoJS 的受控 host primitive，以便转换后的 JSX 保持原网页的层次和语义。
53. 作为维护者，我希望网页转换使用明确的 HTML/CSS 子集映射，而不是在 GraalJS 中实现完整浏览器 DOM 和 CSS 引擎，以便功能范围可验证且实现深度可控。
54. 作为脚本作者，我希望不支持的网页能力能在转换结果中被明确标记，而不是被静默近似，以便我知道哪些视觉或交互差异需要人工处理。
55. 作为脚本作者，我希望转换结果优先保持网页的结构、视觉层次、交互状态和响应式行为，而不是承诺跨渲染器的逐像素一致，以便验收标准符合 Minecraft 的字体和绘制差异。
56. 作为脚本作者，我希望网页中的 flex 行列、间距、内边距、对齐、溢出和常见定位规则能转换为 NekoJS 布局约束，以便常见页面无需手写 Minecraft 坐标。
57. 作为脚本作者，我希望网页中的颜色、背景、边框、圆角、透明度、字体层级和图片裁剪能映射到受控视觉属性，以便转换结果保留页面的主要视觉识别。
58. 作为脚本作者，我希望转换器能把网页按钮、输入框、滚动区域、链接和表单提交转换为稳定的 NekoJS 事件与状态模型，以便不携带浏览器事件对象或 DOM 身份。
59. 作为脚本作者，我希望转换器能把 CSS 动态状态如 hover、focus、active、disabled 和 loading 转换为显式 UI 状态，以便 Minecraft 中的交互反馈与网页意图一致。
60. 作为维护者，我希望网页资源和字体引用在转换阶段经过受控解析，并在 Minecraft 资源不可用时给出可定位错误，以便不会把任意文件路径或浏览器 URL 直接泄漏给脚本。
61. 作为脚本作者，我希望 UI 能根据可用逻辑视口选择六个 NekoJS Viewport Profile，以便不把 Minecraft GUI scale 数字误当成实际布局尺寸。
62. 作为脚本作者，我希望每个 Viewport Profile 可以覆盖宽度、间距、内边距、字号、布局方向和可见性，以便窄屏和宽屏能采用不同布局策略。
63. 作为维护者，我希望响应式属性支持连续比例、最小值、最大值和 profile 覆盖，以便界面既能平滑适应窗口，又能在关键断点进行明确重排。
64. 作为脚本作者，我希望 profile 缺省值有确定的继承或回退规则，以便不必为每个属性在六个 profile 中重复填写。
65. 作为脚本作者，我希望宽屏和窄屏可以在同一组件中切换 row/column、显示/隐藏次要内容和调整文本规格，以便转换网页的 responsive 设计意图。
66. 作为维护者，我希望 Viewport Profile 的判定基于实际逻辑宽高和安全区域，而不是单独基于 Minecraft 原生 GUI scale，以便布局在不同窗口和显示缩放下可重复。
67. 作为脚本作者，我希望能以设计坐标、逻辑像素和比例尺寸表达网页布局，以便不必手动适配每个 Minecraft 分辨率。
68. 作为维护者，我希望布局检查能输出每个可见节点的 profile、矩形、裁剪区域和最终解析样式，以便 AI 和人类可以根据事实迭代转换结果。
69. 作为测试人员，我希望能在六个 Viewport Profile 下生成稳定的参考输出或截图差异报告，以便验证响应式行为而不是只验证默认窗口。
70. 作为脚本作者，我希望网页转换遵循“转换、运行、测量、对比、修正”的迭代流程，以便在 Minecraft 字体和渲染差异存在时仍能获得接近网页的结果。

## Implementation Decisions

- **网页转换定位**：网页转换不是浏览器兼容层，也不是运行时 HTML parser。输入可以包含 HTML/结构描述、CSS/样式描述、图片和字体资源、交互说明以及各 Viewport Profile 的参考图；输出是人工可读、可修改、可检查的 NekoJS JSX 和脚本状态代码。
- **转换目标**：验收目标是结构等价、视觉等价、交互等价和响应式行为等价；不承诺浏览器与 Minecraft 字体栅格化、抗锯齿、阴影采样和文本度量的逐像素一致。
- **支持子集**：转换器只承诺映射文档定义的 HTML/CSS 子集。常见结构、flex 行列、间距、内外边距、对齐、尺寸约束、背景、边框、圆角、透明度、文字层级、图片和滚动可转换；未知或不支持能力必须产生显式诊断，不得静默伪造。
- **资源边界**：图片、图标和字体通过受控资源标识进入 UI contract；转换结果不得把浏览器 DOM、任意 URL、文件句柄、Canvas、WebGL 对象或原生 Minecraft 对象作为长期脚本状态。
- **事件映射**：网页事件转换为 NekoJS 稳定事件对象和 signal/store 更新；不暴露 DOM Event、事件冒泡树或浏览器默认行为。hover、focus、active、disabled、loading 等动态样式转换为显式状态或受控 host state。
- **响应式模型**：新增六个 NekoJS Viewport Profile，编号为 1 至 6。Profile 由实际逻辑视口宽高、安全区域和布局能力判定，不能直接等同 Minecraft GUI scale。每个 profile 可覆盖尺寸、比例、间距、内边距、字号、方向、可见性和资源规格。
- **响应式解析**：布局属性支持连续基础值与 profile 覆盖值；解析顺序、缺省 profile 回退、最小/最大约束、百分比相对包含块和非法值处理必须进入公开 contract，并由 common fake Adapter 测试固定。
- **比例与设计坐标**：布局同时支持逻辑像素、百分比、fill、auto、min/max 和受控设计坐标缩放。文本字号和可读性相关属性不能被强制按同一比例无限缩放；最终结果由测量阶段和约束裁剪决定。
- **响应式重排**：row/column 方向、次要节点可见性、滚动策略和文本规格可以按 profile 改变；profile 变化视为布局失效并重新 measure/arrange，但不在每个绘制帧执行脚本。
- **转换产物可检查**：转换结果应保留来源标识、支持状态和诊断信息，并能由 UI inspector 输出 profile、最终矩形、裁剪、解析样式、资源引用和事件绑定摘要。
- **迭代验收**：网页转换采用转换、真实客户端运行、六档截图/测量、差异分析和修正的闭环。首次转换不是一次性正确性的承诺；转换器、文档和 inspector 共同构成 AI 可持续修正的工作面。


- **公开模型**：新增客户端 UI facade，提供 root 的创建/打开、关闭、状态失效和生命周期查询；具体最终符号名在实施时按现有 Script API 命名规则冻结，但不得把原生 `Screen` 作为脚本作者需要继承的基类。
- **root 输入**：root 接收一个 render function 和 Screen 选项。render function 返回单个 VNode 或 fragment；每个 root 有自己的依赖订阅、提交队列、generation token、host tree 和 disposed 状态。
- **VNode 语义**：automatic runtime 的 `jsx`、`jsxs` 和 `Fragment` 产出不可变 VNode。VNode 至少表达 host/component type、props、children 和可选 key；VNode 不持有 Minecraft 对象、GuiGraphics、Font、Screen 或跨帧可变 host 状态。
- **runtime 选择**：UI 使用 automatic runtime。classic runtime 继续作为通用 JSX lowering 能力保留，但不作为 UI 的规范示例或 UI host 的第二事实源。UI runtime 不依赖 React、Preact、DOM 或其它浏览器兼容层。
- **组件模型**：大写标签是普通 JS/TS 函数组件。组件必须返回 VNode、fragment 或空值；不实现 class component、hook 调用顺序协议、React effect 兼容层或任意 Java 类自动映射。需要副作用的逻辑通过显式生命周期 API 或业务代码管理，并承担自己的 cleanup。
- **初始 host primitive 集合**：第一阶段提供 screen root、panel、row、column、stack、scroll、label、button、input、image 和 spacer。它们是受控的公开元素，不对应 HTML 标签，也不允许脚本借助未知标签绕过登记。
- **props 规则**：props 分为内容、布局、视觉、可访问性、状态和事件几类。布局只支持明确的 Minecraft UI 约束；不实现 CSS selector、继承、浏览器 box model、任意 CSS 字符串或 DOM attribute 扩展。事件属性使用 lowerCamelCase，事件值必须是脚本函数或明确的空值。
- **布局**：布局分为 measure、arrange、paint 三步。row/column 提供主轴排列，stack 提供重叠，scroll 提供裁剪和偏移；尺寸支持逻辑像素、填充、最小/最大约束和受控百分比约束。布局结果在窗口 resize、字体变化、props 变化和内容变化时失效，不在每帧重新运行脚本。
- **文本与资源**：label 的文本测量、换行和字体访问由 Client UI Adapter 完成。image 只接受受控的资源标识和裁剪/尺寸属性；第一阶段不接受脚本提供任意 GPU 对象或长期持有的渲染上下文。
- **状态**：`createSignal` / `createStore` 是显式响应式原语。读取操作在 render function 执行时建立 root 依赖；写入操作合并同一 owner tick 内的重复失效，并在客户端 owner thread 触发下一次 reconcile。状态原语不自动跨 reload 持久化，也不把可变 Java 对象作为共享快照。
- **状态线程**：UI state、VNode、host tree 和事件闭包只能由其 root 的 client owner thread 访问。非 owner thread 的写入必须通过明确的 client 调度边界进入 owner thread；没有调度的直接写入失败并记录可定位错误。不得依靠 GraalJS Context 的隐式跨线程能力。
- **reconciler**：reconciler 按 type、key 和 sibling position 对 VNode 做 keyed diff。它负责 component 展开、host node 创建/更新/删除、事件绑定替换、子节点顺序、布局失效和 cleanup；它不负责业务数据获取、网络协议或服务端菜单同步。
- **提交语义**：render 和布局先产生候选结果；候选结果通过基本结构、属性类型、尺寸约束和 host capability 校验后一次提交。提交前 active host tree 不变；提交失败保留最后一次有效树，并将失败归属于 render、layout、host update 或 event 阶段。
- **输入与焦点**：Client UI Adapter 将原生鼠标、滚轮、键盘和文本输入转换为稳定脚本事件。root 维护焦点、hover、按下、鼠标捕获和 Tab 顺序；input 的光标/选择可以复用版本原生编辑能力，但原生对象身份不进入 Script API。
- **键盘与 narration**：第一阶段实现确定性的焦点遍历、Enter/Space 激活、Escape 关闭和基础 narration。复杂无障碍语义、输入法特化和第三方辅助技术适配不扩张为新的公共框架；缺失能力必须在节点能力表中记录。
- **异常处理**：组件 render、事件回调、布局计算和 Adapter 操作分别捕获并上报。事件回调异常不阻断同一 Screen 的其它事件；render/布局异常不提交半成品树并保留最后有效树。若 root 无有效树可显示，Screen 通过既有错误报告链路呈现普通失败状态，不在普通错误中携带修复指引。
- **generation 生命周期**：root 在创建时绑定当前 CLIENT generation。generation 失效时停止接受新事件，取消待处理 reconcile，释放 signal 订阅、host node、原生 widget、输入 token 和 Screen handle；关闭路径幂等。旧 handle、旧事件闭包和旧 VNode 不得操作新 generation。
- **reload 事务**：candidate generation 可以准备脚本、组件和 UI 计划，但在 commit 前不能对生产 Screen、真实输入路由或 active host tree 可见。candidate 失败或取消时全部候选资源清理，active UI 保持可用；commit 时旧 generation 先停止接收回调，再关闭旧 Screen，随后新 generation 接管。旧 Screen 不自动转移到新 Context。
- **Screen 行为**：root 的 Screen 是独立的原生 Screen 投影，不与 HUD/render callback 合并。默认不把 UI 绘制挂到 `ClientEvents.hud`、`hudRender` 或世界渲染路径；这些既有 owner 继续拥有 HUD/世界绘制。
- **暂停与替换**：Screen 选项可以声明是否暂停游戏以及关闭策略，但最终行为由 Minecraft Screen contract 和平台 Adapter 校验。外部 Screen 替换、玩家主动关闭、客户端退出和 reload 都必须进入同一 root cleanup 路径。
- **平台边界**：common UI Module 不导入 Minecraft/loader。NeoForge 26.2 Adapter 先实现完整 Screen、绘制、字体、输入、纹理和生命周期接线；其它节点只能在真实 Adapter 与 smoke 完成后标记对应 capability，不以 common 编译成功推导可用。
- **Runtime owner**：UI root 的创建、reload、关闭和错误关联通过唯一 `NekoRuntimeRoot` 及其 CLIENT ScriptManager 进入。不得新增第二个 runtime owner、UI 专用 global singleton、静态跨 generation registry 或绕过 Runtime Root 的装配入口。
- **Managed Surface**：客户端 UI facade、host primitives、props、事件对象、signal/store 和组件类型进入既有 managed contract；TypeScript JSX declaration、Probe 和 workspace 配置是派生物。UI 不创建第二套 catalog、声明 DSL 或独立插件 registry。
- **插件扩展**：第一阶段不开放任意插件注册新 JSX primitive。若后续出现至少两个真实 Adapter 或独立 host 生命周期需要扩展，再通过既有插件扩展模型提出窄 Interface；本规格不创建万能 UI extension point。
- **兼容与迁移**：现有 JSX lowering、classic runtime、手写 Java Screen 和既有 HUD/render API 不因本规格自动删除。UI 新 API 作为独立能力验证；未来如将某个既有 Screen 迁移到 JSX，必须单独完成行为 parity、声明、节点 smoke 和旧路径无调用者证据。
- **性能边界**：P0 建立初始创建、首次布局、增量更新、列表更新、输入事件和持续绘制的基线。未取得基线前不设置发布阻断数字；实现不得把每帧 JSX 执行、全树 Java 对象重建或 full-text 全量测量作为默认路径。

## Testing Decisions

- 最高测试 Seam 是客户端 UI facade 的 root 生命周期和可观察 host Adapter contract：从脚本输入 render function、signal/store、VNode、事件和关闭动作出发，观察提交的 host tree、布局、输入结果、状态变化、异常和 cleanup，而不是检查 reconciler 私有字段或对象身份。
- common 逻辑测试使用 fake host Adapter，覆盖 VNode 展开、fragment、函数组件、条件节点、数组 key、props 更新、事件替换、布局约束、滚动边界、焦点移动、signal 依赖、批量失效、异常保留最后有效树和幂等释放。
- VNode/reconciler 成功断言应关注外部树语义：首次渲染产生正确 primitive 顺序，keyed list 更新保留正确节点状态，未变化子树不被不必要地重建，删除节点不再接收事件，空/非法节点按契约处理。失败断言应覆盖重复 key、未知 primitive、非法布局约束、过期 root 和提交中异常。
- 状态测试应通过 signal/store 的读取与写入结果观察订阅关系、批量更新、重复写入合并和 disposed root 行为。不得用私有 dependency set、reconciler 队列或 Java 对象数量作为唯一契约。
- 布局测试应以给定屏幕尺寸、字体测量结果和 props 观察每个可见 node 的最终矩形、裁剪区域、对齐、滚动偏移和 resize 后结果。测试不得依赖具体绘制调用顺序，除非该顺序是 host contract 的可观察层级语义。
- 输入测试应穿过 fake Adapter 的稳定事件对象，观察点击命中、鼠标捕获、滚轮消费、焦点移动、Tab/Shift+Tab、Enter/Space、Escape、文本输入和 disabled 控件行为。不得把 Minecraft 原生事件类身份写进 common contract。
- 异常测试应分别覆盖 render、component、layout、event 和 host Adapter 阶段。成功结果是错误进入既有 CLIENT 诊断链路、最后有效树仍可用、其它事件继续分发并且 root 最终可关闭；失败结果是半提交树、重复回调、错误吞失或 Context 泄漏。
- generation 测试应覆盖旧 root 在 reload 后停止接收回调、旧 handle 被拒绝、candidate 失败保留 active Screen、commit 后新 generation 只接收一次事件、Screen 外部关闭与 reload 重复 cleanup，以及客户端退出时所有 root 释放。
- owner-thread 测试应覆盖 owner thread 直接创建/更新、非 owner 调度、非法跨线程写入、回调内重复 invalidate、回调内 close 和关闭期间到达的输入。断言应是可观察的执行顺序和结果，不依赖具体锁实现。
- declaration/contract 测试应验证 automatic runtime、UI facade、intrinsic elements、component props、events、signal/store 和 capability 声明一致；普通测试只读取基线，声明/golden 更新必须有旧新 diff、原因和审阅记录。
- NeoForge 26.2 runtime smoke 使用真实客户端验证打开 Screen、首次绘制、文本和按钮、输入框、滚动、窗口 resize、关闭、脚本异常、CLIENT reload 和旧 Screen 清理。需要 in-game evidence 的部分使用仓库规定的 Minecraft MCP，不把 fake Adapter 测试冒充真实客户端验证。
- 其它节点先执行构建、声明和 capability 检查；只有对应 Adapter 完成后才执行真实 Screen smoke。`supported`、`partial`、`unavailable` 与 `not verified` / `deferred` 分开记录，不能用未验证结果伪造 parity。
- 性能测试先记录环境、脚本规模、组件数量、列表规模、状态更新频率、预热、重复次数和统计口径，覆盖首次构建、增量 reconcile、输入和持续绘制。发布阈值由后续维护者基于 P0 数据决定。
- 现有编译器 JSX golden、脚本 reload 回归、client render/HUD 注册清理、dashboard Screen 测试、资源生命周期和节点 runtime smoke 是 prior art。新测试应复用这些测试形状，并将断言提升到公开调用者 Interface，不复制一套 UI 专用测试框架。

- 网页转换测试以公开转换 contract 为边界，使用代表性 HTML/CSS 子集、资源、状态和六档 viewport 输入，观察 JSX/状态产物、诊断和响应式布局结果，不测试私有解析器类的调用细节。
- 建立网页映射 golden，覆盖结构、flex 行列、尺寸约束、视觉属性、事件状态、资源错误和不支持能力诊断；golden 变更必须包含旧新差异、原因和审阅记录。
- 建立六档 Viewport Profile golden，覆盖 profile 判定、缺省回退、连续比例、min/max、profile 覆盖、方向切换、节点可见性和窗口 resize。
- common fake Adapter 测试输出最终节点矩形、裁剪区域、解析样式和事件摘要；不能用 Minecraft GUI scale 传入值直接代替逻辑视口 contract。
- NeoForge 26.2 runtime smoke 在真实客户端验证至少一个网页转换样例的默认尺寸、六档 resize/窗口变化、字体/图片资源、按钮/输入/滚动和失败诊断；截图只能作为证据，必须与布局 inspector 或公开行为断言配合。
- 转换器测试必须证明未知 CSS/HTML 能力产生显式 unsupported 诊断，并且不会生成任意 Java host、DOM 对象或未受控资源访问。

## Out of Scope

- 不实现完整 HTML parser、DOM、浏览器 JavaScript、CSS cascade、selector、specificity、继承、CSS Grid、任意 CSS 动画、伪元素或浏览器布局的全部细节。
- 不承诺任意网页一键像素级复刻；没有结构、样式、资源、字体、交互说明或参考视口证据时，只能产出带不确定性诊断的近似转换。
- 不把 Minecraft GUI scale 1～6 自动定义成 NekoJS Viewport Profile；原生 scale 只是 profile 判定的一个输入因素。
- 不把六个 profile 限制成六个固定屏幕分辨率；profile 必须由逻辑视口规则和可观察布局能力定义。
- 不提供网页运行时加载、远程网页抓取、浏览器 URL 导航、iframe、视频、Canvas、WebGL 或任意网络资源代理。
- 不在第一阶段自动转换复杂 CSS Grid、复杂文本排版、脚本驱动 DOM 操作、浏览器表单默认行为和第三方前端框架生命周期。


- 第一阶段不实现 HUD、Overlay、世界渲染或后处理 JSX；既有 `ClientEvents.hud`、`hudRender`、world render 和 PostEffects owner 保持独立。
- 不实现服务端容器、菜单、Slot、背包同步、服务端权威状态、网络协议或多人协作 UI。
- 不把现有错误 dashboard、内置 workspace、脚本编辑器或文件同步改写成 JSX；这些能力有独立的产品与诊断边界，迁移必须另开行为 parity 工作。
- 不引入 React、Preact、DOM、浏览器事件模型、CSS parser、CSS selector、完整 CSS box model 或其它大型前端运行时。
- 不允许任意 Java 类、Minecraft 原生 widget、`Screen` 子类、`GuiGraphics`、Font 或 GL 对象直接成为 JSX 标签、props 或长期脚本状态。
- 不提供 class component、hook 兼容层、effect 依赖数组、React reconciliation 兼容语义或 React 生态包的自动运行保证。
- 不实现通用动画系统、GPU 自定义绘制、任意 shader、拖拽设计器、可视化编辑器、远程 UI 下载或脚本 UI 持久化格式。
- 不在本规格中改变 classic JSX runtime、TS/TSX lowering、ESM/CJS 模块、GraalJS HostAccess 或高级 Java 访问的既有语义。
- 不新增 Gradle 子项目、API jar、第二 runtime owner、第二事件总线、万能 UI registry 或独立声明事实源。
- 不在未通过 NeoForge 26.2 proof 前宣称 Fabric 或其它 Minecraft 节点的 UI parity，也不以静态构建结果替代真实客户端能力验证。
- 不设置未经过性能基线支持的发布阻断数字，不把普通测试自动写入 golden，也不在本轮修改版本号或发布 `1.2.0`。

## Further Notes

- 对 AI 最有价值的交付物不是 HTML parser，而是稳定的 UI contract、HTML/CSS 子集映射表、资源约定、六档 profile 规则、声明文件和 inspector 输出。
- 网页转换应优先生成普通函数组件、显式 signal/store 和受控 host primitive；不应生成浏览器 DOM API、React Hooks、任意 Java 类或依赖隐藏全局状态的代码。
- 实施时应提供至少一个完整转换样例作为端到端 proof，并记录输入资料、转换诊断、六档截图、布局测量和人工修正过程；该样例不能替代通用 contract 测试。
- 本次补充没有改变首个实现节点、CLIENT generation、唯一 Runtime owner、common 隔离、fake Adapter 优先和 NeoForge 26.2 smoke 的既有决定。


- 本规格直接承接仓库已有的 JSX/TSX lowering 与 automatic runtime 语义；现有 JSX 文档描述的是通用树状数据能力，本规格新增的是 Minecraft Screen host 和生命周期 contract。
- 现有客户端 GUI/render 规划将 Screen、render、HUD 和 keybind 分开，并要求 client owner thread、generation 清理、Adapter 以及 `supported` / `partial` / `unavailable` 能力说明。本规格沿用这些边界，不把 JSX 作为绕过既有 owner 的新入口。
- 运行时 reload 继续遵守候选 generation、失败保留 active、commit 后旧 generation 失效和唯一 `NekoRuntimeRoot` owner 的契约。UI root 是脚本环境 session 资源，不是进程级 Plugin Runtime 产物。
- 语言管线继续把 JSX lowering、模块解析和 GraalJS 执行分开；UI runtime 不把编译器改造成 Minecraft-aware compiler。JSX 只生成 VNode，host Adapter 在客户端执行。
- 本地 issue tracker 约定要求计划、spec 和工单落在 `docs/`，因此本规格不创建 GitHub issue。`ready-for-agent` 表示规格已经可以交给实施者，不表示源码、测试、构建或节点 smoke 已获执行授权。
- 实施前需要冻结的细节包括公开 facade 的最终符号名、Screen option 字段、host primitive 的完整 props、事件对象字段、布局约束的边界值和 NeoForge 26.2 Adapter 的能力清单。这些属于 API contract 实施输入，不应由 private class 布局反推。
- 推荐实施顺序是：先 common fake Adapter 上的 VNode/reconciler 与 signal contract，再做 NeoForge 26.2 Screen/input/render Adapter，然后接入 CLIENT generation/reload，最后补 Probe/declaration、真实客户端 smoke、性能基线和其它节点能力记录。
- 任何将既有 Java Screen 迁移到 JSX 的工作都必须证明行为相同或明确列出产品变化；不能因为新 runtime 可用就删除现有 Screen、错误报告或 dashboard 路径。
