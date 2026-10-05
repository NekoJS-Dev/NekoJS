<!-- wiki-page: jsx-client-ui; locale: cn -->

> **中文** · [English](jsx-client-ui_us)

<a id="wiki-section-1"></a>
# JSX 客户端 UI

NekoJS 提供了一套用 JSX 描述 Minecraft 客户端 Screen 的 UI runtime。它不是浏览器 DOM，也不是 React 兼容层，而是把 JSX 组件树转换为受控的 Minecraft UI 节点，并负责布局、输入、状态更新和生命周期。

> **当前范围**：客户端 Screen UI 当前只在 **NeoForge 26.x** 提供。它只能在 `client_scripts/` 中创建；Fabric、NeoForge 1.21.1 和 Cleanroom 暂不承诺 UI 支持。详见 [平台与兼容性](platform-compatibility_cn)。

<a id="wiki-section-2"></a>
## 开始使用

<a id="wiki-section-3"></a>
### 1. 开启 automatic JSX runtime

在 `nekojs/config/engine.toml` 中设置：

```toml
jsxAutomaticRuntime = true
```

NekoJS 会使用内置的 `nekojs/jsx-runtime`，并在 workspace/probe 过程中把脚本目录的 `jsconfig.json` 更新为 automatic JSX 配置。不需要在 `node_modules/` 中创建同名 runtime 文件。

<a id="wiki-section-4"></a>
### 2. 创建一个 Screen

下面的脚本创建一个带计数按钮的 Screen：

```tsx
// client_scripts/counter.tsx
import { UI } from 'nekojs/jsx-runtime'

const adapter = ClientUI.screen('计数器', false)
const count = UI.createSignal(0)

const root = UI.createRoot(() => (
  <screen title="计数器" width="fill" height="fill" padding={12}>
    <column width="fill" height="fill" gap={8} align="center" justify="center">
      <label>{`当前值：${count.get()}`}</label>
      <button
        id="increment"
        text="增加"
        onClick={() => count.update(value => value + 1)}
      />
    </column>
  </screen>
), adapter, { id: 'counter' })

adapter.bindRoot(root)
adapter.open()
```

关键步骤：

1. `ClientUI.screen(title, pausesGame)` 创建 Minecraft Screen host adapter。
2. `UI.createSignal` 创建响应式状态。
3. `UI.createRoot(render, adapter)` 创建 UI root 并执行首次渲染。
4. `adapter.bindRoot(root)` 把 root 绑定到 Screen host。
5. `adapter.open()` 在客户端线程打开 Screen。

UI root 必须从受管的 CLIENT 脚本上下文创建，并且所有创建、状态更新和关闭操作都应发生在客户端 owner thread。

<a id="wiki-section-5"></a>
## 先明确：这不是 HTML/CSS

JSX 只是语法。`<panel>`、`<row>`、`<button>` 等小写标签不是浏览器标签，而是 NekoJS 固定登记的 host primitive。runtime 会把它们变成不可变 VNode，再由 host adapter 完成布局、绘制和输入。

因此下列写法没有对应能力：

- `<div>`、`<span>`、`<form>` 等浏览器标签；
- `className`、`style={{ ... }}`、CSS selector、CSS cascade；
- `margin`、`box-shadow`、`transform`、`z-index`、`font-weight`、`font-family` 等未登记属性；
- 把任意 Java 类、Minecraft Widget 或 `GuiGraphics` 直接当作 JSX 标签。

这不是说 UI 只能写成一堆静态标签。正确的扩展方式是用函数组件、显式状态、VNode 工厂和受控 props 组合界面。

<a id="wiki-section-6"></a>
## 四种表达方式

<a id="wiki-section-7"></a>
### 1. JSX primitive

适合直接描述稳定的界面结构：

```tsx
<panel background="#20252B" borderColor="#708090" borderWidth={1} radius={4} padding={8}>
  <column gap={6}>
    <label color="white" fontSize={10}>设置</label>
    <button text="保存" />
  </column>
</panel>
```

小写标签必须来自这 11 个 primitive：`screen`、`panel`、`row`、`column`、`stack`、`scroll`、`label`、`button`、`input`、`image`、`spacer`。可以用 `UI.primitives()` 查询当前 runtime 的登记清单。

<a id="wiki-section-8"></a>
### 2. 函数组件

复杂界面应拆成普通 JavaScript/TypeScript 函数组件。函数组件返回 VNode，可以封装布局、视觉 token、状态和事件：

```tsx
const CARD_STYLE = {
  background: '#20252B',
  borderColor: '#4C566A',
  borderWidth: 1,
  radius: 4,
  padding: 8
}

function Card({ title, children }) {
  return (
    <panel {...CARD_STYLE} gap={4}>
      <label color="#E5E9F0" fontSize={10}>{title}</label>
      {children}
    </panel>
  )
}
```

这里的 `CARD_STYLE` 是普通 JS 对象展开，不是 CSS `style` 属性。共享样式应通过 props token 或函数参数复用；不要期待 CSS class、选择器或继承机制。

<a id="wiki-section-9"></a>
### 3. `UI.element` 直接创建 VNode

需要动态选择标签、构造数据驱动列表，或在表达式中创建函数组件时，可以使用 `UI.element(type, props, key)`：

```tsx
function ItemCard({ item }) {
  return <Card title={item.name}><label>{item.description}</label></Card>
}

const body = items.map(item =>
  UI.element(ItemCard, { item }, item.id)
)

const root = UI.createRoot(() => (
  <scroll id="items" width="fill" height="fill">
    <column>{body}</column>
  </scroll>
), adapter)
```

当前 lowering 有一个需要记住的限制：`{items.map(item => <ItemCard ... />)}` 这种“表达式内的大写组件标签”当前不能通过编译。列表表达式中使用 `UI.element(ItemCard, props, key)`；小写 primitive 在表达式中没有这个限制。

`UI.element` 同样可以创建 primitive：

```javascript
const button = UI.element('button', {
  id: 'apply',
  text: '应用',
  disabled: false,
  onClick: event => console.log(event.target)
}, 'apply-button')
```

`type` 只能是登记的 primitive、函数组件或 `Fragment`。VNode props 会被冻结；不能放入循环对象、原生 Minecraft 对象或任意 host handle。

<a id="wiki-section-10"></a>
### 4. 纯数据树与 classic runtime

如果界面只是要生成树状数据，而不是打开 Minecraft Screen，可以继续使用普通 JSX/VNode 或 classic runtime 的自定义 factory。classic runtime 的 factory 决定返回什么数据，但它不会自动获得 `ClientUI` 的布局、输入和 Screen 生命周期。

也就是说：

- **要做可交互 Screen**：使用 `UI.createRoot` + `ClientUI.screen`。
- **要生成任意脚本数据**：可以使用普通 JSX 或 classic factory。
- **要逐帧手动画矩形、文本、纹理**：使用后面的 `PainterJS` 路径。

<a id="wiki-section-11"></a>
## 内置元素

JSX 小写标签只能使用 NekoJS 登记的 host primitive。任意 Java 类、Minecraft 原生 Widget 或 `Screen` 子类都不会自动变成 JSX 元素。

| 元素 | 用途 | 常用属性 |
|---|---|---|
| `screen` | Screen 根节点 | `title`、`pausesGame`、`closeOnEscape` |
| `panel` | 背景和边框容器 | `background`、`borderColor`、`borderWidth`、`radius`、`opacity` |
| `row` | 横向排列 | 通用布局属性 |
| `column` | 纵向排列 | 通用布局属性 |
| `stack` | 子节点叠放 | 通用布局属性 |
| `scroll` | 可滚动容器 | `scrollX`、`scrollY`、`scrollOffset` |
| `label` | 文本 | `text`、`color`、`fontSize`、`font`、`wrap`、`truncate` |
| `button` | 可点击按钮 | `text`、`disabled`、`tooltip` |
| `input` | 文本输入 | `value`、`placeholder`、`maxLength`、`disabled` |
| `image` | 图片或图标 | `resource`、`fit`、`opacity`、`icon`、`crop` |
| `spacer` | 占位和撑开空间 | 通用布局属性 |

大写标签是普通 JavaScript/TypeScript 函数组件。组件应返回 VNode：

```tsx
function Section({ title, children }) {
  return (
    <panel padding={8} gap={4}>
      <label text={title} />
      {children}
    </panel>
  )
}
```

<a id="wiki-section-12"></a>
## 布局

UI 使用 Minecraft 逻辑像素和受控约束，不使用浏览器 CSS。常用通用属性包括：

- 尺寸：`width`、`height`、`minWidth`、`maxWidth`、`minHeight`、`maxHeight`。
- 间距：`gap`、`spacing`、`padding`。
- 排列：`align`（`start`、`center`、`end`、`stretch`）、`justify`（`start`、`center`、`end`、`spaceBetween`、`spaceAround`）。
- 定位：`direction`、`anchor`（如 `topLeft`、`center`、`bottomRight`）。
- 显示：`visible`、`coordinateSpace`（`logical` 或 `design`）。
- 稳定身份：`id` 和列表节点的 `key`。

尺寸可以是数字、`auto`、`fill` 或百分比字符串：

```tsx
<panel width="80%" minWidth={240} maxWidth={640} padding={12}>
  <label text="会随窗口变化，但不会小于 240 像素" />
</panel>
```

需要按视口档位调整时，可以传响应式值。profile 使用 1 到 6 的逻辑档位，不是固定的屏幕分辨率：

```tsx
<column
  padding={{ base: 8, profiles: { 1: 4, 5: 16, 6: 24 } }}
  gap={{ base: 6, profiles: { 1: 3, 6: 12 } }}
>
  {/* ... */}
</column>
```

文本尺寸、换行和裁剪由 Minecraft 字体 adapter 测量，不应在脚本中猜测字符宽度。

<a id="wiki-section-13"></a>
## 视觉样式与资源

JSX UI 的样式是“节点 props + host resolver”，不是 CSS。视觉属性是平面的、按 primitive 校验的：

| 视觉意图 | NekoJS 写法 | 适用元素 |
|---|---|---|
| 文本颜色 | `color` | `label` |
| 背景颜色 | `background` | `panel` |
| 边框颜色/宽度 | `borderColor`、`borderWidth` | `panel` |
| 圆角 | `radius` | `panel` |
| 整体透明度 | `opacity` | `panel`、`image` |
| 字号 | `fontSize` | `label` |
| 字体资源 | `font` | `label` |
| 换行/省略 | `wrap`、`truncate` | `label` |
| 图片显示模式 | `fit` | `image`，`contain` / `cover` / `stretch` |
| 图片裁剪 | `crop` | `image`，`{ x, y, width, height }` 或四项数组 |
| 图片/图标资源 | `resource`、`icon` | `image` |

这些视觉属性是 flat props；当前不支持把 `color`、`background`、`borderColor`、`opacity` 等写成 `{ base, profiles }` 响应式对象。响应式 profile 主要用于尺寸、间距、排列、可见性和滚动参数。

<a id="wiki-section-14"></a>
### 颜色语法

颜色只接受受控格式：

- `#RGB`、`#RRGGBB`、`#AARRGGBB`；
- 基础 CSS 命名色和 `transparent`，例如 `red`、`navy`、`white`；
- 整数 ARGB，例如 `0xFF4C566A`。

不支持 `rgb(...)`、`rgba(...)`、`hsl(...)` 或任意扩展 CSS 颜色名。八位 hex 总按 `#AARRGGBB` 解释，不能套用 CSS 的 `#RRGGBBAA` 排列；半透明白色应写 `#80FFFFFF`，`#FFFFFF80` 会被解释成另一种 ARGB 颜色。

<a id="wiki-section-15"></a>
### 样式复用的正确方式

可以使用普通对象、spread 和函数组件复用视觉 token：

```tsx
const COLORS = Object.freeze({
  surface: '#20252B',
  border: '#4C566A',
  text: '#E5E9F0'
})

function Surface({ children }) {
  return (
    <panel background={COLORS.surface} borderColor={COLORS.border} borderWidth={1} radius={4} padding={8}>
      {children}
    </panel>
  )
}
```

不要写成 `<panel style={COLORS}>`。`style` 不是登记的 prop，会触发 `Unsupported prop` 的 layout 错误。

<a id="wiki-section-16"></a>
### 资源 id

`resource`、`icon` 和 `font` 只能使用受控资源 id，例如 `mymod:gui/panel`。它们不接受本地文件路径、绝对路径或 URL。资源缺失、id 格式不合法或尺寸不合法时，会记录带节点和 root 位置的 UI 诊断；不要在 UI runtime 中读取任意文件或远程资源。

<a id="wiki-section-17"></a>
## 从 HTML/CSS 迁移

如果你手里已有网页结构，可以把它当作**设计输入**，但不能直接把 HTML/CSS 原样搬进 runtime：

| Web 写法 | NekoJS 写法 | 说明 |
|---|---|---|
| `div` 普通块 | `column` | 默认纵向排列 |
| `display: flex; flex-direction: row` | `row` | `gap` 可直接对应 |
| 绝对定位覆盖层 | `stack` + 子节点 `anchor` | 只有 9 个锚点，不是任意 top/right/bottom/left |
| `overflow: auto` | `scroll` | 使用 `scrollX` / `scrollY` / `scrollOffset` |
| `span` / `p` / 文本节点 | `label` | 通过文本子节点或 `text` |
| `img` | `image` | `src` 改成受控 `namespace:path` |
| `flex: 1` | `width="fill"` 或 `height="fill"` | 按主轴填充剩余空间 |
| `@media` | `{ base, profiles: { 1..6 } }` | 使用逻辑 viewport profile |
| `z-index` | `stack` 的子节点顺序 | 后绘制的子节点覆盖前面的子节点 |
| CSS Grid、复杂 selector、动画 | 暂无直接等价物 | 需要改写成 row/column、signal 或人工处理 |

特别注意：`margin` 没有对应 prop；通常用父级 `gap`、`padding` 或 `spacer`。单个 `padding` 是统一内边距，不支持四边分别设置。`font-weight`、`font-style`、`line-height`、`letter-spacing`、`box-shadow`、CSS gradient、transition 和 pseudo-element 也不属于当前 JSX UI contract。

<a id="wiki-section-18"></a>
## 响应式状态

<a id="wiki-section-19"></a>
### Signal

`UI.createSignal(initial)` 提供单值状态：

```tsx
const query = UI.createSignal('')

const root = UI.createRoot(() => (
  <column>
    <input
      id="query"
      value={query.get()}
      placeholder="搜索"
      onChange={event => query.set(event.value || '')}
    />
    <label>{`当前查询：${query.get()}`}</label>
  </column>
), adapter)
```

- `get()` 读取当前值，并在 render function 中建立依赖。
- `set(value)` 直接设置值。
- `update(updater)` 根据旧值计算新值。
- 状态变化只会让依赖它的 root 重新 reconcile，不会让 JSX 在每个渲染帧重新执行。

<a id="wiki-section-20"></a>
### Store

多个相关字段可以使用 `UI.createStore(initial)`：

```javascript
const form = UI.createStore({ name: '', enabled: true })
form.set('name', 'Neko')
form.update('name', oldName => oldName + 'JS')
console.log(form.snapshot())
```

Signal、store 和 UI root 不会自动跨 CLIENT reload 持久化。需要保留的业务状态必须由脚本显式保存或重新初始化。

<a id="wiki-section-21"></a>
### 批量更新

多个状态在同一操作中更新时，可以使用 `UI.batch` 合并失效通知：

```javascript
UI.batch(() => {
  form.set('name', 'Neko')
  form.set('enabled', false)
})
```

<a id="wiki-section-22"></a>
## 事件

通用事件属性包括：

| 属性 | 触发场景 |
|---|---|
| `onClick` | 点击 |
| `onRelease` | 鼠标释放 |
| `onScroll` | 滚轮 |
| `onKey` | 键盘输入 |
| `onTextInput` | 文本输入 |
| `onFocus` / `onBlur` | 获得/失去焦点 |
| `onChange` | 输入值变化 |
| `onSubmit` | 输入提交 |

事件对象只包含稳定的脚本字段，例如 `type`、`target`、`x`、`y`、`button`、`key`、`value` 和 `delta`。脚本不需要直接处理 `GuiGraphics`、原生 `Screen` 或 Minecraft 版本特有的输入事件。

事件没有 DOM 的冒泡、捕获、`preventDefault()` 或 `stopPropagation()`。所有需要接收回调的节点都必须有 root 内唯一的 `id`：真实鼠标、键盘输入和手动 `root.dispatch(id, eventName, event)` 都通过这个 id 查找处理器。只写 `onClick` 或 `onChange` 而不写 id，不会收到脚本事件。事件对象由 runtime 冻结，处理器异常会进入 `event` 阶段诊断，不会让同一 Screen 的其它事件失效。

`button`、`input`、`scroll` 等元素会自行维护命中、焦点、禁用、滚动和文本编辑状态。旧节点被删除或 root 被关闭后，其事件回调不会继续接收输入。

<a id="wiki-section-23"></a>
## Root 生命周期

`UI.createRoot` 返回的 handle 提供以下操作：

| 方法 | 说明 |
|---|---|
| `id` | root 的稳定标识 |
| `refresh()` | 请求重新渲染 |
| `resize(viewport)` | 按新的逻辑视口重新布局 |
| `layout()` | 读取最近一次布局快照 |
| `dispatch(id, eventName, event)` | 向指定节点派发受控事件 |
| `isDisposed()` | 检查 root 是否已经释放 |
| `close()` | 释放 root 的节点、订阅和事件处理器；不自动退出原生 Screen |

root 与创建它的 CLIENT generation 绑定：

- CLIENT reload 成功后，旧 root、旧 Screen、旧事件闭包和旧 VNode 都会失效。
- candidate generation 在提交前不能影响当前运行中的 Screen。
- reload 失败时，当前 active UI 保持可用。
- Screen 被玩家关闭、被其它 Screen 替换或客户端退出时，都会进入同一套清理流程。
- 过期 handle 的操作会被拒绝并记录对应的 `NEKO-700x` 诊断，而不是静默操作新 generation。

<a id="wiki-section-24"></a>
## 资源与诊断

`image` 使用受控的资源 id，字体和纹理由 Minecraft resource manager 解析。资源读取、解码、尺寸和上传失败会进入 JSX UI 诊断链路。

常见诊断包括：

- `NEKO-7001`：root 已失效或属于旧 generation。
- `NEKO-7003`：host adapter 不是从受管 CLIENT 脚本上下文创建。
- `NEKO-7004`：操作不在客户端 owner thread。
- `NEKO-7006`：旧 generation 的延迟操作被丢弃。
- `NEKO-7007`：render、layout、event 或 host-update 阶段失败。

render 或布局失败时不会提交半成品树；如果已有有效树，runtime 会尽量保留最后一次有效结果，并把错误交给既有错误报告链路。

<a id="wiki-section-25"></a>
## 还有哪些做法

“用 JSX 做 UI”不是唯一客户端界面方案。应按需求选择不同的 owner：

<a id="wiki-section-26"></a>
### 方案 A：JSX retained Screen

使用本页的 `ClientUI.screen` + `UI.createRoot`。runtime 保留已提交的 host tree，状态变化时重新 render/layout/commit，普通 paint 帧只读取已提交树。

适合：

- 设置页、配置页、搜索框、列表、分页和可复用表单；
- 需要焦点、键盘、文本输入、滚动和 root reload 清理；
- 希望用函数组件和 signal/store 管理状态。

不适合：

- 每帧动画绘制、粒子化效果、自由绘制路径；
- 需要自定义 Minecraft 原生控件或菜单/Slot 网络同步；
- 需要新增一个 JSX primitive。当前 primitive 白名单是固定的，脚本不能注册新标签。

<a id="wiki-section-27"></a>
### 方案 B：`PainterJS` 直接绘制

如果只需要 HUD 或在现有 Screen 上画一层内容，可以使用 `ClientEvents.hud` 或 `ClientEvents.screenRender`：

```javascript
ClientEvents.hud(painter => {
  painter.rect(8, 8, 120, 18, 0xB020252B)
  painter.outline(8, 8, 120, 18, 0xFF708090)
  painter.text('任务进行中', 14, 14, 0xFFFFFFFF)
})

ClientEvents.screenRender(event => {
  const painter = event.getPainter()
  painter.text(`鼠标：${event.getMouseX()}, ${event.getMouseY()}`, 8, 8)
})
```

`PainterJS` 提供矩形、边框、渐变、文本、纹理、物品图标、变换、裁剪、字体测量和文本换行。它是**立即模式绘制**：回调每个渲染帧执行，脚本自己负责坐标、绘制顺序和动画状态。

它不会自动提供 JSX UI 的布局、焦点、输入框、滚动、root reconciliation 或状态订阅。`screenRender` 只能观察当前 Screen 并在其上绘制，不能把当前 Screen 变成你的 JSX root。

<a id="wiki-section-28"></a>
### 方案 C：注册式 HUD / 世界渲染

需要按 id 管理常驻渲染器时，使用 `ClientEvents.hudRender` 和 `ClientEvents.worldRender`：

```javascript
ClientEvents.hudRender('status-bar', { layer: 'foreground', priority: 0 }, (ctx, gui) => {
  ctx.text('状态', 8, 8)
})

ClientEvents.worldRender('quest-path', { layer: 'normal', priority: 0 }, ctx => {
  ctx.line(0, 64, 0, 10, 70, 10, 0xFFFFFF00, 2)
})
```

这两个入口由 CLIENT generation 统一登记和替换，适合 HUD/world overlay；它们仍然是渲染回调，不是可交互 Screen，也不是 JSX host tree。

<a id="wiki-section-29"></a>
### 方案 D：原生 Java Screen / Widget

如果需要完整的 Minecraft 原生控件、菜单、Slot、复杂文本编辑或自定义输入路由，应使用 Java 插件或现有原生 Screen 扩展点。它拥有最大的控制力，但也需要自己处理 Minecraft 版本差异、生命周期、焦点、输入和 reload 清理。

`Screen`、`GuiGraphics` 等原生对象不会自动成为 JSX 元素。把原生 Screen 嵌进 JSX 也不是当前 runtime 的扩展方式；应在插件/平台 Adapter 层完成这类集成。

<a id="wiki-section-30"></a>
### 方案选择

| 需求 | 推荐路径 |
|---|---|
| 配置页、表单、列表、输入框 | JSX Screen |
| HUD 上画文本、血条、矩形或纹理 | `ClientEvents.hud` / `hudRender` |
| 在现有界面上叠加坐标或提示 | `ClientEvents.screenRender` |
| 世界中的线框、路径和 3D 标记 | `ClientEvents.worldRender` |
| 原生菜单、Slot、复杂 Widget | Java Screen / 插件 Adapter |
| 纯数据树或渲染配置 | classic JSX factory 或普通 JS 对象 |

<a id="wiki-section-31"></a>
## 当前边界

JSX 客户端 UI 当前不提供：

- Fabric、NeoForge 1.21.1 或 Cleanroom 的 UI parity；
- 服务端容器、菜单、Slot、背包同步或多人权威状态；
- HUD、Overlay、世界渲染和 PostEffects 的 JSX 替代入口；
- 任意 Java 类、原生 Widget、`GuiGraphics` 或 GL 对象作为 JSX 标签；
- React/Preact hooks、DOM、CSS parser、CSS Grid 或完整浏览器布局；
- `className`、`style`、CSS selector/cascade、margin、阴影、渐变、transform、z-index、字体粗细/斜体/字族；
- 远程 UI 下载、网络资源代理、通用动画系统或任意 shader。

现有 `ClientEvents.hud`、`hudRender`、`worldRender`、`screenRender` 和手写 Java Screen 仍按原有 API 工作，不会因为启用 JSX UI 而自动迁移。

<a id="wiki-section-32"></a>
## 相关页面

- [TypeScript 与 JSX](typescript-and-jsx_cn) —— TS 擦除、JSX lowering 和 automatic runtime。
- [事件参考](event-reference_cn) —— `ClientEvents`、`KeyBindEvents` 和客户端事件边界。
- [平台与兼容性](platform-compatibility_cn) —— NeoForge/Fabric 当前支持状态。
- [常见问题](faq_cn) —— automatic JSX 和客户端脚本排错。

<!-- wiki-nav -->

---

[上一篇: TypeScript 与 JSX](typescript-and-jsx_cn) · [目录](Home) · [下一篇: Node.js 兼容](nodejs-compatibility_cn)
