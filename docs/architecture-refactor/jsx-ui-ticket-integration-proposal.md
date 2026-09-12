# NekoJS JSX UI 实施票据整合提案

Status: proposed
Type: implementation-ticket-proposal
Date: 2026-09-12
Input spec: [NekoJS 纯 JSX 客户端 Screen UI](../jsx-ui-spec.md)

本文件只是合并提案，不发布正式票据，不修改 `implementation-ticket-breakdown.md`，也不修改 `implementation-tickets/` 中任何正式票据的编号、Status、Blocked by、Selected 或验收勾选。用户批准后，才把本文中的新增票落成 `implementation-tickets/40-...md` 到 `48-...md`，并对既有票做下文列出的最小协调性修订。

## 结论

- JSX UI 不应作为 13 张独立横向票整体追加到现有 39 张票之后。它应收敛为 **9 张新增 feature 票（40–48）**，并复用既有语言、managed surface、runtime、client、assets、diagnostics 与 release 体系的边界。
- 现有票 **不因 JSX UI 自动新增 1.2.0 / P4 release blocker**。票 48 只关闭 JSX UI feature acceptance；若维护者之后决定把该 feature 纳入某个发布，再单独更新对应发布范围与 gate，不在本提案中代答。
- 声明、错误保留、root cleanup 和 owner-thread 语义在 **40、41、42** 中提前闭环，不推迟到最终 proof。
- Inspector 先于 AI 文档与网页转换工作流：**45 不依赖 46/47**。AI 文档（47）必须消费已冻结 declaration、真实 Probe/declaration 产物和 Inspector 输出。
- 网页转换是 **AI-assisted authoring/conversion workflow**，不是新增运行时 HTML parser、浏览器兼容层或自动 HTML/CSS 编译器承诺。46 只交付映射契约、输入清单、诊断、fixture 和可选辅助工具边界。
- NeoForge 26.2 是首个完整 runtime proof；其它节点保持 `not verified` 或按真实 Adapter/smoke 证据标记 `supported` / `partial` / `unavailable`，不进入本提案的全节点发布承诺。

## 与既有体系的归属裁定

| JSX UI 需求 | 归属裁定 | 理由 |
|---|---|---|
| JSX/TSX lowering、automatic runtime、source map、模块身份 | 合并到既有 [12: TS/JSX/TSX 编译、source map 与执行行为路径](implementation-tickets/12-language-ts.md) | 12 已拥有语言管线；新增票只消费 `jsx/jsxs/Fragment` 产物，不重做 compiler。 |
| UI facade、intrinsic elements、signal/store、事件与能力声明 | 新增 40 消费既有 [09: Managed Surface 单一规范源与声明/Probe 派生链](implementation-tickets/09-managed-surface.md) | 09 是唯一规范源；40 只让 UI symbol 进入该链路，不创建第二 catalog、第二 declaration DSL 或手写第二 golden。 |
| Runtime owner、candidate/active、owner thread、watchdog | 复用既有 [05](implementation-tickets/05-runtime-root.md)、[06](implementation-tickets/06-reload-commit.md)、[07](implementation-tickets/07-runtime-threads.md)，由新增 42 接线 | JSX UI root 是 CLIENT generation 资源，不是第二 runtime owner；不在 05/06/07 中塞入 UI 专属实现。 |
| Keybind、HUD、world render | 保持既有 [26](implementation-tickets/26-client-input-hud.md)、[27](implementation-tickets/27-client-gui-render.md) 边界 | JSX 第一阶段只做独立 Screen；不把 UI 绘制挂到 HUD/world render，不吞并 keybind owner。 |
| 原生 Screen、鼠标/键盘/焦点/滚动/字体/纹理 Adapter | 新增 41，与 26/27 并行协调 | 27 当前重点是既有 GUI/render Adapter 资源与只读错误报告清理，且被诊断票约束；把完整 JSX runtime 塞入 27 会让新 Screen 不必要地等待错误 dashboard 语义。 |
| 图片/图标/字体等资源标识与回读 | 新增 44 消费既有 [29: Assets/Lang 资源生成与回读收口](implementation-tickets/29-assets.md) | 29 拥有资源生成、路径、安全与 reload 语义；44 只定义 UI 资源解析、缺失诊断和绘制使用，不新增第二资源根或资源 policy。 |
| render/layout/event/host 错误投影 | 新增 40/42 消费既有 [30: 错误诊断、telemetry、workspace 与用户报告链路](implementation-tickets/30-diagnostics.md) | 30 拥有统一诊断 record；JSX UI 补充 phase/root/generation 归因，不建立私有错误总线。 |
| PostEffects | 不合并，仍归 [28](implementation-tickets/28-post-effects.md) | 规格明确排除后处理 JSX；二者只共享 CLIENT 生命周期约束。 |
| P4 能力矩阵、性能复测、维护者试做、1.2.0 handoff | 只协调，不自动改 [34](implementation-tickets/34-release-p4-validation.md)、[35](implementation-tickets/35-release-perf-compare.md)、[36](implementation-tickets/36-release-maintainer-trials.md)、[37](implementation-tickets/37-release-1-2-0-handoff.md) 的 Blocked by | 这是新增 feature 验收，不是既有 1.2.0 全节点 gate 的自动扩大。是否纳入发布需维护者另行选择。 |

## 新增票提案

编号从 40 开始，避免重排既有 01–39。以下 Blocked by 均指未来正式发布时应写入新增票的依赖；本提案不修改既有票据。

### 40: JSX UI common core、公开契约与 Fake Host Proof

**What to build:** 在 common 中交付不依赖 Minecraft/loader 的 JSX UI 核心路径：automatic runtime 产生不可变 VNode，函数组件与 Fragment 展开为候选树，signal/store 依赖触发了受影响 root reconcile，keyed reconciler 通过公开 host Adapter contract 创建、更新、排序和释放 fake host node。同一票冻结最小 UI facade、primitive props、事件对象、signal/store、root handle、布局约束和诊断字段的 managed contract，并让 TypeScript JSX declaration / Probe 输出与 fake runtime 语义一致。

**Blocked by:**
- [05: 单 owner 预整理：闭合两个 loader 的运行生命周期入口](implementation-tickets/05-runtime-root.md)
- [09: Managed Surface 单一规范源与声明/Probe 派生链](implementation-tickets/09-managed-surface.md)
- [12: TS/JSX/TSX 编译、source map 与执行行为路径](implementation-tickets/12-language-ts.md)

**Status:** proposed

**关键验收：**

- [ ] `jsx` / `jsxs` / `Fragment` 到 VNode、函数组件、条件、数组、spread、key 和 children 的语义有公开 contract fixture；VNode 不持有 Minecraft、loader、`GuiGraphics`、Font、Screen、GL 或长期可变 host 对象。
- [ ] 初始 primitive 集合 `screen`、`panel`、`row`、`column`、`stack`、`scroll`、`label`、`button`、`input`、`image`、`spacer` 有唯一登记表；未知 primitive、非法 props、非法布局值和重复 key 显式失败。
- [ ] signal/store 的读取、写入、批量失效、依赖重建、disposed root 写入和 owner-thread 拒绝/排队语义可从公开调用者 Interface 观察，不检查私有依赖集合。
- [ ] reconciler 的候选 render、结构/属性/能力校验、原子提交、keyed diff、事件替换、子节点排序、布局失效和幂等 cleanup 均可由 fake host 观察；失败更新保留最后一次有效树。
- [ ] render、component、layout、event、host update 阶段错误可区分并进入统一诊断 seam；单个 event 错误不影响其它控件，render/layout 失败不提交半成品树。
- [ ] UI facade、primitive、props、事件对象、signal/store 和 capability 进入 09 的 NormativeApiContract 派生链；TypeScript JSX declaration 与 Probe 输出同源生成，不存在声明有而 runtime 无、或 runtime 有而声明缺失的能力。
- [ ] common Module 不导入 Minecraft/loader，不新增 Gradle 项目、API jar、第二 runtime owner、第二事件 bus 或独立声明事实源。
- [ ] close/dispose、重复 close、候选失败清理和最后有效树保留都有 fake Adapter 测试；cleanup 不推迟到后续真实客户端票。

### 41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter

**What to build:** 将 40 的 host Adapter contract 接到 NeoForge 26.2 真实客户端：脚本可打开 JSX 描述的独立 Screen，看到 label/button/input/scroll/layout 的首次绘制，并通过稳定事件对象完成点击、文本输入、滚轮、Tab/Shift+Tab、Enter/Space、Escape、焦点、hover、disabled 和基础 narration。正常绘制帧只 paint 已提交 host tree，不重新执行 GraalJS render。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](#40-jsx-ui-common-core公开契约与-fake-host-proof)

**Status:** proposed

**协调但不硬阻塞：**
- [26: CLIENT 输入与 HUD callback 生命周期](implementation-tickets/26-client-input-hud.md)：26 继续拥有 keybind/HUD；41 拥有 JSX Screen 内输入路由。
- [27: CLIENT GUI 与 render Adapter 资源呈现清理](implementation-tickets/27-client-gui-render.md)：27 继续拥有既有 error GUI/render callback 域；41 与其确认平台 Adapter 和 client owner 线程边界，不把 JSX runtime 塞入 27。
- [30: 错误诊断、telemetry、workspace 与用户报告链路](implementation-tickets/30-diagnostics.md)：41 先通过公开诊断 seam 报告阶段化错误；与 30 的最终 record 字段对齐在 42 完成整合验收。

**关键验收：**

- [ ] NeoForge 26.2 真实客户端能打开、绘制、操作、resize 和关闭 JSX Screen；Screen 选项覆盖标题、关闭行为、暂停策略和外部替换 cleanup。
- [ ] 原生鼠标、滚轮、键盘、文本输入、焦点和捕获被转换为稳定脚本事件；事件对象不暴露原生 Screen、widget、`GuiGraphics`、Java 对象身份或版本特有字段。
- [ ] input 的值、光标、选择、焦点、最大长度和文本事件可用；scroll 正确管理内容范围、裁剪、偏移和滚轮消费。
- [ ] 焦点顺序、Tab/Shift+Tab、Enter/Space、Escape、hover、pressed、disabled、tooltip 和基础 narration 行为与 fake Adapter contract 一致。
- [ ] 首次构建、resize、状态更新和事件回调都不在普通 paint 帧重新执行 JSX render；每帧只绘制已提交 host tree。
- [ ] host Adapter 持有 Minecraft/loader 类型，shared/common author contract 不引入平台类型；脚本侧状态、VNode、host tree 和事件闭包只在 client owner thread 访问。
- [ ] 节点删除后旧事件闭包不再响应；Screen 关闭、外部替换和重复 cleanup 进入同一释放路径。
- [ ] NeoForge 26.2 真实客户端 smoke 使用仓库规定 Minecraft MCP 或等价注册 smoke 通道完成；其它节点只记录 `not verified`，不因 common 编译通过而标记支持。

### 42: JSX UI CLIENT generation、reload、诊断与 cleanup 接线

**What to build:** 把 JSX UI root 纳入唯一 `NekoRuntimeRoot` 与 CLIENT generation 生命周期：candidate 中准备的 UI 计划和 root 对生产 Screen、输入路由不可见；commit 后旧 generation 停止接收事件并关闭旧 Screen；candidate 失败或取消只清理候选资源并保留 active UI。render/layout/event/host/resource 错误进入既有诊断 record，旧 handle 与过期 generation 明确失败。

**Blocked by:**
- [06: 候选环境、阶段结果与 owner-thread commit 点](implementation-tickets/06-reload-commit.md)
- [07: 同类型串行、close 优先与 watchdog 隔离恢复](implementation-tickets/07-runtime-threads.md)
- [30: 错误诊断、telemetry、workspace 与用户报告链路](implementation-tickets/30-diagnostics.md)
- [40: JSX UI common core、公开契约与 Fake Host Proof](#40-jsx-ui-common-core公开契约与-fake-host-proof)
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](#41-neoforge-262-jsx-screen输入焦点与滚动-adapter)

**Status:** proposed

**关键验收：**

- [ ] UI root 创建时绑定当前 CLIENT generation；不新增 UI global singleton、静态跨 generation registry 或第二 runtime owner。
- [ ] candidate UI 计划、binding、signal 订阅、事件 token 和 host 计划在 commit 前对生产 Screen/输入路由不可见；candidate 失败全部清理且 active Screen 继续可用。
- [ ] commit 后旧 generation 停止接收新事件，取消 pending reconcile，释放订阅、host node、原生 widget、输入 token 和 Screen handle；新 generation 恰好接收一次事件。
- [ ] reload 成功、reload 失败、Screen 主动关闭、外部 `setScreen` 替换、客户端退出和 close 抢占的 cleanup 幂等；旧 handle 不能操作新 generation。
- [ ] 非-owner-thread 状态更新只能显式排队或明确失败；回调内 invalidate/close、关闭期间输入和 watchdog/cancel 路径可观察。
- [ ] render、component、layout、event、host Adapter、resource 和 disposed/stale root 错误进入 30 的统一诊断链路，包含阶段、脚本来源、UI root、generation 和 owner；不建立 UI 专用错误事实源。
- [ ] 真实 NeoForge 26.2 smoke 覆盖成功 reload、失败 reload、旧 Screen 关闭、旧事件失效和 active UI 保留。

### 43: 六档 Viewport Profile 与响应式布局系统

**What to build:** 在 common 布局模型中实现 NekoJS 自己的 Viewport Profile 1–6 判定和响应式属性解析：由实际逻辑视口、安全区域和布局能力选择 profile，支持连续比例、逻辑像素、`fill`、`auto`、百分比、min/max、间距、内边距、对齐、锚定、方向切换、可见性和字号覆盖。窗口 resize/profile 变化触发 measure/arrange 失效，但不触发每帧脚本 render。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](#40-jsx-ui-common-core公开契约与-fake-host-proof)

**Status:** proposed

**关键验收：**

- [ ] Profile 1–6 的判定规则、边界、优先级、tie-break 和可观察输出进入公开 contract；不把 Minecraft GUI scale 数字等同 Profile，只允许原生 scale 作为输入之一。
- [ ] 基础值与 profile 覆盖值的解析顺序、缺省回退、非法 profile、非法比例和 min/max 裁剪规则固定，并由 fake Adapter golden 覆盖。
- [ ] row/column、stack、scroll、spacing、padding、align、anchor、可见性和文本规格可按 profile 覆盖；窄屏/宽屏重排不需要每个属性重复填写六遍。
- [ ] 设计坐标、逻辑像素和连续比例可共存；文本字号和可读性相关属性不被强制整体等比无限缩放。
- [ ] resize/profile 切换只使布局失效并重新 measure/arrange，不重新执行 GraalJS render 或重建全部 host node；稳定输出包含最终矩形、裁剪和溢出诊断。
- [ ] 六个 profile 均有稳定 fake 输出；真实 NeoForge 26.2 resize smoke 在 41 完成后补入同一公开 contract，不用私有 widget 布局作为断言。

### 44: 文本测量、视觉样式、图片与受控资源解析

**What to build:** 补齐网页等价视觉所需的第一批受控能力：Minecraft Font Adapter 文本测量与换行、文本颜色和层级、背景、边框、圆角、透明度、图片/图标、裁剪与资源状态。图片/图标/字体通过受控资源标识进入 UI contract，复用既有 Assets/resource pack 根与安全边界，缺失资源产生可定位诊断而不是任意路径或 URL 访问。

**Blocked by:**
- [29: Assets/Lang 资源生成与回读收口](implementation-tickets/29-assets.md)
- [40: JSX UI common core、公开契约与 Fake Host Proof](#40-jsx-ui-common-core公开契约与-fake-host-proof)
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](#41-neoforge-262-jsx-screen输入焦点与滚动-adapter)

**Status:** proposed

**关键验收：**

- [ ] 文本测量、换行、截断、baseline、颜色、字号和字体层级由平台 Font Adapter 提供；common 只消费测量结果，不猜 Minecraft 字体宽度。
- [ ] background、border、radius、opacity、image、icon、crop 和资源规格进入受控 props；不支持任意 CSS 字符串、浏览器 URL、文件句柄、Canvas/WebGL 对象或原生纹理长期进入脚本状态。
- [ ] 图片/图标/字体资源标识复用 29 的资源根、路径校验、pack reload 和回读语义；不新增第二资源根、第二事件或第二资源 policy。
- [ ] 资源缺失、非法标识、加载失败、尺寸非法和解码失败进入统一诊断 seam，并可定位到 UI root、节点、资源和 generation。
- [ ] 视觉属性模型不得与 43 的 profile 覆盖模型冲突；二者组合后的 resize 布局与绘制结果由 45 统一验收。
- [ ] 至少一个真实 NeoForge 26.2 Screen smoke 覆盖多行文本、字体层级、图片/图标、透明度、裁剪和资源缺失诊断。

### 45: UI Inspector、布局测量与截图差异基线

**What to build:** 提供 JSX UI 的公开 Inspector contract 与 fake/NeoForge 双输出：当前 profile、逻辑视口、安全区域、节点树、矩形、裁剪、最终解析样式、溢出、资源状态、事件绑定摘要和 layout diagnostics；支持采集 actual 截图并与参考图/参考尺寸输出差异报告。Inspector 是 runtime 事实工具，不依赖 AI 文档、网页转换映射或自动转换器。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](#40-jsx-ui-common-core公开契约与-fake-host-proof)
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](#41-neoforge-262-jsx-screen输入焦点与滚动-adapter)
- [43: 六档 Viewport Profile 与响应式布局系统](#43-六档-viewport-profile-与响应式布局系统)
- [44: 文本测量、视觉样式、图片与受控资源解析](#44-文本测量视觉样式图片与受控资源解析)

**Status:** proposed

**关键验收：**

- [ ] fake Adapter 与 NeoForge 26.2 均可输出同构 Inspector record；字段来自公开 host contract，不暴露 Java 对象身份、原生 widget、`GuiGraphics` 或内部 reconciler 结构。
- [ ] Inspector 能定位节点、最终 props、resolved style、profile、矩形、裁剪、滚动偏移、焦点、事件绑定摘要、资源引用和错误阶段。
- [ ] 六个 Viewport Profile 都能生成稳定测量输出；输出可作为 golden，普通测试只读，更新必须走旧新 diff 与维护者审阅。
- [ ] actual 截图或等价像素 evidence 只作为辅助，必须与公开测量/行为断言配合，不把截图对象或私有渲染缓冲作为脚本 API。
- [ ] 差异报告能指出偏差最大的节点/属性，并保留输入、环境、profile、参考图和实际输出来源。
- [ ] 45 的实现与验收不读取、不依赖 46/47 的 AI 文档或网页转换规则；后续 AI 工作流只消费 45 输出。

### 46: AI-assisted 网页转换映射与输入契约

**What to build:** 定义 AI 辅助把受控网页资料转换为 NekoJS JSX 的映射契约：输入检查清单、HTML 结构到 host primitive 的映射、支持 CSS 子集到 NekoJS 布局/视觉属性的映射、事件与状态映射、资源引用规则、不确定性记录和 unsupported 诊断。交付代表性 fixture 与 conversion report；不承诺通用自动 HTML/CSS 编译器、运行时 parser 或一键像素级复刻。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](#40-jsx-ui-common-core公开契约与-fake-host-proof)
- [43: 六档 Viewport Profile 与响应式布局系统](#43-六档-viewport-profile-与响应式布局系统)
- [44: 文本测量、视觉样式、图片与受控资源解析](#44-文本测量视觉样式图片与受控资源解析)
- [45: UI Inspector、布局测量与截图差异基线](#45-ui-inspector布局测量与截图差异基线)

**Status:** proposed

**关键验收：**

- [ ] 转换输入契约明确要求结构、样式、资源、字体、交互说明和六个 profile 的参考尺寸/截图；缺失资料产生显式 uncertainty，不由 AI 静默编造。
- [ ] 常见结构、flex row/column、gap、padding/margin、尺寸约束、align/justify、overflow/scroll、背景、边框、圆角、透明度、字体层级、图片和基础表单控件有确定映射。
- [ ] hover、focus、active、disabled、loading 转换为显式 signal/store 或受控 host state；网页事件转换为 NekoJS 稳定事件对象，不携带 DOM Event、事件冒泡或浏览器默认行为。
- [ ] 支持 CSS Grid、复杂 selector、伪元素、动画、脚本 DOM 操作、iframe/video/canvas/WebGL 和第三方框架生命周期时必须输出 unsupported/needs-human 诊断，不得静默丢弃或伪装成功。
- [ ] 转换产物是人工可读、可修改的 JSX/TSX、函数组件、signal/store 和 conversion report；不得生成 React/Preact/DOM API、React Hooks、任意 Java host 或未受控资源访问。
- [ ] representative fixture 的输入、映射决策、unsupported 项、产出和 Inspector 验证结果可追溯；若实现辅助命令，它只是显式工具，不进入 runtime 热路径，也不承诺完整浏览器兼容。
- [ ] 不新增 HTML parser、CSS parser、DOM、浏览器布局引擎或自动网页抓取/远程资源代理承诺。

### 47: AI UI Authoring Contract 与转换 Cookbook

**What to build:** 交付面向 AI 的 UI authoring 文档：以已发布 declaration/Probe 输出、primitive catalog、真实事件对象、signal/store 语义、Profile 规则、资源规则和 Inspector record 为事实源，说明如何生成、自检、诊断和局部修正 JSX。文档包含网页转换 cookbook、禁令、输入清单、输出结构、unsupported 报告格式和 Inspector 修正协议。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](#40-jsx-ui-common-core公开契约与-fake-host-proof)
- [45: UI Inspector、布局测量与截图差异基线](#45-ui-inspector布局测量与截图差异基线)
- [46: AI-assisted 网页转换映射与输入契约](#46-ai-assisted-网页转换映射与输入契约)

**Status:** proposed

**关键验收：**

- [ ] 文档中的每个 primitive、props、事件字段、状态 API、资源标识和 capability 均引用或生成自已冻结 managed declaration / Probe 输出；不存在仅存在于文档的 aspirational API。
- [ ] 每个 primitive 条目包含用途、children 规则、props 类型、默认值、布局/视觉行为、事件对象、profile 行为、常见错误和最小示例。
- [ ] AI 自检清单覆盖未知标签、未知 CSS 能力、非法资源、错误线程、每帧 render、key、焦点、滚动、文本可读性、reload 残留 callback 和 conversion uncertainty。
- [ ] Inspector 修正协议要求基于 profile、节点矩形、裁剪、resolved style、资源状态和差异报告做局部修正；禁止每次偏差重写整棵 UI。
- [ ] 文档明确优先级为结构等价、交互等价、响应式等价、视觉等价，并说明 Minecraft 与浏览器字体/栅格化差异导致不承诺逐像素一致。
- [ ] 文档示例可被 typecheck/contract fixture 执行或验证；示例不使用 unavailable/not verified 能力。
- [ ] 文档不把 AI 辅助转换描述为自动浏览器兼容层、通用编译器或无需验收的一次性生成。

### 48: JSX UI feature end-to-end proof 与证据包

**What to build:** 用一个受控网页资料样例完成 AI-assisted 转换到 NeoForge 26.2 JSX Screen 的端到端 proof：结构、状态、输入、滚动、资源、响应式 Profile、Inspector、差异修正、reload、错误保留和 cleanup 全链路可验证，并采集 feature 性能与能力证据。该票关闭 JSX UI feature acceptance，不自动成为既有 1.2.0 / P4 发布 blocker。

**Blocked by:**
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](#41-neoforge-262-jsx-screen输入焦点与滚动-adapter)
- [42: JSX UI CLIENT generation、reload、诊断与 cleanup 接线](#42-jsx-ui-client-generationreload诊断与-cleanup-接线)
- [43: 六档 Viewport Profile 与响应式布局系统](#43-六档-viewport-profile-与响应式布局系统)
- [44: 文本测量、视觉样式、图片与受控资源解析](#44-文本测量视觉样式图片与受控资源解析)
- [45: UI Inspector、布局测量与截图差异基线](#45-ui-inspector布局测量与截图差异基线)
- [46: AI-assisted 网页转换映射与输入契约](#46-ai-assisted-网页转换映射与输入契约)
- [47: AI UI Authoring Contract 与转换 Cookbook](#47-ai-ui-authoring-contract-与转换-cookbook)

**Status:** proposed

**关键验收：**

- [ ] 代表性网页输入资料经过 46/47 的 AI-assisted 流程生成 JSX、状态、资源引用和 conversion report；报告记录假设、unsupported 项和人工处理点。
- [ ] NeoForge 26.2 真实客户端能打开、操作、resize、reload、触发资源/脚本错误并关闭样例 Screen；六个 Profile 均有测量输出，必要时有截图辅助。
- [ ] Inspector 差异驱动至少一次有记录的局部修正，最终结构、交互、响应式和视觉等价结果可追溯；明确列出不能逐像素一致的原因。
- [ ] reload 成功/失败、旧 Screen 关闭、旧事件失效、active UI 保留、资源缺失和 render/event 异常均进入证据包。
- [ ] 记录首次构建、首次布局、增量 reconcile、列表更新、输入、持续 paint、profile 切换和 cleanup 的测试环境与统计口径；未取得数据前不设立发布阻断数字。
- [ ] NeoForge 26.2 capability 只按真实 Adapter/smoke 结果记录；其它节点保持 `not verified` 或按证据标记 `supported` / `partial` / `unavailable`。
- [ ] 不修改 34–37 的既有 Blocked by；若维护者决定纳入某个发布，另行发布范围决策并更新对应 gate。

## 依赖图

```text
05 ─┬─► 40 ─┬─► 41 ─┬─► 42
09 ─┘       │       ├──► 44 ─► 45 ─► 46 ─► 47 ─┐
12 ─────────┘       │                          │
                    ├──► 43 ──► 45             │
                    │                          │
06 ─────────────────────────────────────────────►
07 ─────────────────────────────────────────────►
30 ─────────────────────────────────────────────►
                                                48
```

按票号表述的直接依赖：

- 40 ← 05, 09, 12
- 41 ← 40
- 42 ← 06, 07, 30, 40, 41
- 43 ← 40
- 44 ← 29, 40, 41
- 45 ← 40, 41, 43, 44
- 46 ← 40, 43, 44, 45
- 47 ← 40, 45, 46
- 48 ← 41, 42, 43, 44, 45, 46, 47

可并行示意：

- 40 完成后，41 与 43 可并行。
- 41/43/44 可以按 Adapter、布局和资源三线并行推进。
- 42 可在 41 完成后与 43/44 并行，但它需要 06/07/30 的既有 runtime/diagnostic 基础。
- 45 必须等 Inspector 所需的布局与视觉事实齐备，但不能依赖 46/47。
- 46/47 只消费已存在的 runtime contract、declaration 与 Inspector 输出。
- 48 是 feature proof，不是既有 1.2.0 / P4 gate 的自动前置。

## 对前序 13 票方案的修正

| 前序方案 | 本提案处理 | 修正原因 |
|---|---|---|
| 01 Fake Host / contract | 合并为 40，并提前包含 declaration、错误保留与 dispose | 公开契约和可测 fake host 是同一 vertical slice；声明与失败保留不应推迟。 |
| 02 最小 Screen + 03 输入/焦点/scroll | 合并为 41 | 首个真实 Adapter 若不能处理核心输入/焦点/scroll，不足以支撑后续资源、转换和 proof；最小 label/button proof 过窄。 |
| 04 Viewport Profile | 保留为 43，且仅依赖 40 | 响应式布局是核心布局风险，可与真实 Adapter 并行，不应被网页转换或 AI 文档阻塞。 |
| 05 文本/图片/字体 | 保留为 44，并明确消费 29 | 资源边界必须并入现有 Assets/resource pack owner，避免第二资源 policy。 |
| 06 网页转换 | 收敛为 46，明确 AI-assisted 而非自动编译器 | 用户目标是让 AI 更稳定地辅助转换；不应无意承诺通用 HTML/CSS parser/compiler 或浏览器兼容层。 |
| 07 AI 文档 | 保留为 47，并改为消费真实 declaration 与 Inspector | 文档不能先于事实源发布，也不能成为 Inspector 的依赖。 |
| 08 Inspector | 提前为 45，且不依赖 AI 文档/网页转换 | Inspector 是 runtime 事实工具；AI 工作流只是其消费者。 |
| 09 reload/cleanup | 调整为 42，提前到真实 Adapter 后立即闭环 | cleanup、generation、诊断不能全部推迟到最终 proof。 |
| 10 managed declaration/capability | 合并进 40 与 48 | declaration 在 40 提前同源冻结；节点能力在 48 只记录 feature 事实，不改全节点 gate。 |
| 11 异常/失败保留 | 拆入 40、41、42、44、48 | render/event/layout/resource/generation 失败应随各 slice 验收，而不是最后集中补测。 |
| 12 end-to-end proof | 保留为 48，并明确不是既有 release blocker | 新 feature proof 与 1.2.0/P4 全节点验收必须分层。 |
| 13 性能与发布材料 | 收敛进 48 的 feature evidence | 不 hijack 既有 02/35 的 P0/P4 性能政策，也不自动修改 34–37 依赖。 |

## 批准后需要的最小落盘动作

1. 新增 9 个正式票据文件：`40-jsx-ui-common-core.md`、`41-jsx-ui-neoforge-screen-adapter.md`、`42-jsx-ui-generation-reload-cleanup.md`、`43-jsx-ui-viewport-profiles.md`、`44-jsx-ui-text-visual-assets.md`、`45-jsx-ui-inspector.md`、`46-jsx-ui-web-conversion-contract.md`、`47-jsx-ui-ai-authoring-docs.md`、`48-jsx-ui-end-to-end-proof.md`。
2. 更新 `implementation-tickets/README.md` 的票据导航和功能覆盖导航，但保留历史发布快照不改写，并新增本提案日期的结构检查快照。
3. 只做以下协调性文字修订，不改变既有票验收语义或完成状态：
   - 09：补充 UI facade 由 40 进入同一 NormativeApiContract 派生链。
   - 26：明确 JSX Screen 内输入路由由 41 负责，keybind/HUD 仍归 26。
   - 27：明确 JSX UI runtime 是 40–48 的 feature 域，不并入既有 error GUI/render callback 清理票。
   - 29：补充 44 消费既有资源根与回读语义，不新增资源 policy。
   - 30：补充 42 消费统一 diagnostic record，不建立 UI 专用错误事实源。
4. 不修改 `implementation-ticket-breakdown.md`。
5. 不把 40–48 加进 34–37 的 Blocked by；不把 JSX UI 自动纳入 1.2.0 发布范围。
6. 重新执行本地文档结构检查，确认编号连续、无缺失 blocker、无环、状态字段完整，并如实记录新增后的票数与验收项数量。

