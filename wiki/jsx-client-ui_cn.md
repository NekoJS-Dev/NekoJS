<!-- wiki-page: jsx-client-ui; locale: cn -->

> **中文** · [English](jsx-client-ui_us)

<a id="wiki-section-1"></a>
# JSX 客户端 UI

在 NekoJS 里，你可以直接用 JSX 写游戏内的交互界面：按钮、输入框、滑块、页签、弹窗，甚至多窗口桌面。你只需要声明界面长什么样、状态怎么变，排版、渲染、焦点切换和键鼠事件都由 NekoJS 自动处理。

> **平台要求**：仅在 **NeoForge 26.1.2 / 26.2.0** 的客户端可用。脚本放在 `client_scripts/` 下，通过全局对象 `ClientUI` 创建界面。Fabric、NeoForge 1.21.1 及专用服务端没有 `ClientUI`（详见 [平台与兼容性](platform-compatibility_cn)）。

<a id="wiki-section-2"></a>
## 1. 准备工作

使用 JSX 前，先打开 `nekojs/config/engine.toml` 开启自动运行时：

```toml
jsxAutomaticRuntime = true
```

保存后**重启游戏**，并在游戏内执行一次 `/nekojs probe`，这样 VS Code 就能获得 JSX 标签和属性的补全提示。

> `nekojs/jsx-runtime` 是 NekoJS 内置模块，直接 `import` 即可，不需要安装任何 npm 包。

<a id="wiki-section-3"></a>
## 2. 第一个界面：计数器

新建 `nekojs/client_scripts/counter.tsx`，执行 `/nekojs reload client` 后界面会立刻打开：

```tsx
import { UI } from 'nekojs/jsx-runtime'

// 1. 在组件外创建响应式状态（初始值为 0）
const count = UI.createSignal(0)

// 2. 创建界面宿主（标题为“计数器”，打开时不暂停游戏）
const host = ClientUI.screen('计数器', false)

// 3. 渲染界面：读取过的 signal 一旦变化，界面自动更新
const root = UI.createRoot(() => (
  <screen padding={12}>
    <column gap={8} align="center">
      <label>{`当前值：${count.get()}`}</label>
      <button id="increment" onClick={() => count.update(v => v + 1)}>
        加一
      </button>
    </column>
  </screen>
), host)

// 4. 挂载并打开界面（按 Esc 关闭）
host.bindRoot(root)
host.open()
```

写 JSX 界面时，先记住**三条铁律**：

1. **状态（Signal）必须建在 render 函数外面**：如果把 `UI.createSignal()` 写在 `createRoot(() => ...)` 里面，每次界面重绘都会把状态重置回初始值。
2. **交互元素必须写 `id`**：按钮、输入框、滑块等要想响应 `onClick`、`onChange` 等事件，**必须**带上全界面唯一的 `id` 属性；光写回调不写 `id` 点击不会有任何反应。
3. **关闭的界面不能再次 `open()`**：玩家按 Esc 关掉界面后，原来的 `host` 和 `root` 就销毁了。如果要做“按键反复打开”的界面，把创建 `host` 和 `root` 的逻辑封装进一个函数，每次打开时调用即可。

<a id="wiki-section-4"></a>
## 3. 核心概念：这不是 HTML

JSX 在这里只是语法糖。你写的 `<panel>`、`<row>`、`<button>` 是 NekoJS 的原生 GUI 节点，**不是浏览器 DOM**：

- **没有网页标签**：不能写 `<div>`、`<span>`、`<form>`。
- **没有 CSS**：不支持 `className`、`style={{ ... }}`，也没有选择器、样式继承或 `margin`、`box-shadow`、`z-index` 等属性。传入不支持的属性会直接报 `Unsupported prop`。
- **不能混用原版/Java 控件**：不能把 Java 类、Minecraft 原版 `Widget` 或 `GuiGraphics` 当作标签写进 JSX。

想复用一套配色和间距，直接封装成普通的函数组件即可（见下文“组件拆分与列表”）。

<a id="wiki-section-5"></a>
### 16 种内置基础标签

小写开头的标签只能是以下 16 种（也可在脚本中调用 `UI.primitives()` 查看）：

| 分类 | 标签 | 用途 | 常用专属属性 |
|---|---|---|---|
| **根节点** | `screen` | 整个界面的根容器 | `title`、`pausesGame`、`closeOnEscape` |
| **布局容器** | `panel` | 带背景色、边框、圆角的面板 | `background`、`borderColor`、`borderWidth`、`radius`、`opacity` |
| | `row` | 横向排列子元素 | 通用布局属性 |
| | `column` | 纵向排列子元素 | 通用布局属性 |
| | `stack` | 层叠容器（子元素叠放） | 子元素通过 `anchor` 定位到九宫格锚点 |
| | `scroll` | 超出范围自动出现滚动条 | `scrollX`、`scrollY`、`scrollOffset` |
| | `spacer` | 空白占位符（用于撑开空间） | 通用布局属性 |
| **展示元素** | `label` | 文本标签 | `text`、`color`、`fontSize`、`font`、`wrap`、`truncate` |
| | `image` | 贴图或图标 | `resource`、`icon`、`fit`、`crop`、`opacity` |
| | `item` | 只读的物品图标（含原版悬浮提示） | `item`、`count`、`itemTooltip`、`tooltip` |
| **基础交互** | `button` | 按钮 | `text`、`disabled`、`tooltip`、`appearance` |
| | `input` | 单行文本输入框 | `value`、`placeholder`、`maxLength`、`disabled`、`appearance` |
| | `slider` | 基础数值滑块 | `value`、`min`、`max`、`step`、`disabled`、`appearance` |
| **高阶容器** | `dialog` | 模态对话框底层节点 | 建议直接用封装好的 `<Dialog>` 组件 |
| | `workspace` / `window` | 多窗口桌面与子窗口底层节点 | 由 `createWorkspace` 自动管理，详见 [高阶指南](jsx-client-ui-advanced_cn) |

> `<label>` 和 `<button>` 的文字既可以写在标签内部（`<label>你好</label>`），也可以传给 `text` 属性。

<a id="wiki-section-6"></a>
## 4. 布局与响应式

所有长度单位都是 Minecraft 的 **GUI 像素**。任何元素都可以使用以下通用布局属性：

- **尺寸**：`width`、`height`、`minWidth`、`maxWidth`、`minHeight`、`maxHeight`。支持具体数字（如 `120`）、`"auto"`、`"fill"`（撑满父容器剩余空间）或百分比字符串（如 `"80%"`）。
- **间距**：`gap`（子元素之间的间距）、`padding`（四边统一内边距）。没有 `margin`，外边距请用父容器的 `padding`、`gap` 或 `<spacer />` 实现。
- **对齐**：
  - `align`（交叉轴对齐）：`"start"` | `"center"` | `"end"` | `"stretch"`
  - `justify`（主轴对齐）：`"start"` | `"center"` | `"end"` | `"spaceBetween"` | `"spaceAround"`
- **层叠定位**：在 `<stack>` 容器中，给子元素设置 `anchor`（如 `"topLeft"`、`"center"`、`"bottomRight"` 等九个位置），后面的子元素会盖在前面的上面。
- **显隐与标识**：`visible`（布尔值控制是否显示）、`id`（事件与焦点标识）、`key`（列表项唯一标识）。

```tsx
<panel width="80%" minWidth={240} maxWidth={640} padding={12}>
  <label>随窗口变宽，但最窄不会小于 240 像素</label>
</panel>
```

<a id="wiki-section-7"></a>
### 滚动区域（`<scroll>`）

内容可能超出容器时，在外面包一层 `<scroll>`。内容溢出时会自动出现细滚动条（支持鼠标拖拽或点击轨道翻页）。
- `direction="row"` 时为横向滚动，其余情况为纵向滚动。
- 滚动条浮在内容边缘，不额外占用布局宽度，建议给内部容器留一点 `padding` 免得文字贴边。
- 超长文本直接放进 `<label>` 再套一层 `<scroll>` 即可，游戏字体会自动测量宽度并换行。

<a id="wiki-section-8"></a>
### 按窗口大小自动调整（响应式档位）

如果希望界面在不同窗口尺寸或 GUI 缩放下自动调整大小，可以把尺寸、间距、对齐或 `visible` 写成 `{ base, profiles: { 档位: 值 } }`：

```tsx
<column
  padding={{ base: 8, profiles: { 1: 4, 5: 16, 6: 24 } }}
  gap={{ base: 6, profiles: { 1: 3, 6: 12 } }}
>
  {/* ... */}
</column>
```

NekoJS 会根据当前界面的宽高（GUI 像素）算出一个 **1 到 6 的档位**（宽高分别计算，取较小值）：

| 档位 | 最小宽度 | 最小高度 | 匹配规则 |
|---|---|---|---|
| 1 | 无限制 | 无限制 | 先找当前档位；没写就**往低档位找最近的**；都没有才用 `base`（绝不会往上取高档位的值）。因此上例在 1～6 档下的 `padding` 依次为 `4, 4, 4, 4, 16, 24`。 |
| 2 | 320 | 180 | |
| 3 | 480 | 240 | |
| 4 | 640 | 360 | |
| 5 | 854 | 480 | |
| 6 | 1280 | 720 | |

> 窗口缩放时只重新计算布局，不会重新执行 render 函数。注意：颜色等视觉属性不支持响应式档位。

<details>
<summary>💡 附：写习惯了网页？点此查看 HTML/CSS 到 NekoJS 的写法对照表</summary>

| 网页 HTML/CSS 写法 | NekoJS 写法 | 说明 |
|---|---|---|
| `<div>`（默认块级） | `<column>` | 默认纵向排列 |
| `display: flex; flex-direction: row` | `<row>` | `gap` 属性直接对应 |
| `position: absolute` 浮层 | `<stack>` + 子元素 `anchor` | 支持九宫格锚点定位 |
| `overflow: auto` | `<scroll>` | 内容超出自动出现滚动条 |
| `<span>`、`<p>` | `<label>` | |
| `<img src="...">` | `<image resource="...">` | 只接受 Minecraft 资源 ID（如 `mymod:gui/panel`） |
| `flex: 1` | `width="fill"` 或 `height="fill"` | 占满剩余空间 |
| `@media` 媒体查询 | `{ base, profiles }` | 按 1～6 档窗口尺寸响应 |
| `z-index` | `<stack>` 内的子元素顺序 | 后写的盖住先写的 |
| `margin` | 父元素 `gap` / `padding` 或 `<spacer>` | 没有外边距属性 |
</details>

<a id="wiki-section-9"></a>
## 5. 样式、颜色与贴图

视觉样式直接作为对应标签的属性传入：

- **`<panel>`**：`background`（背景色）、`borderColor`（边框色）、`borderWidth`（边框粗细）、`radius`（圆角半径）、`opacity`（透明度 `0`～`1`）。
- **`<label>`**：`color`（文字颜色）、`fontSize`（字号）、`font`（字体资源 ID）、`wrap`（自动换行）、`truncate`（超出截断）。
- **`<image>`**：`resource` 或 `icon`（贴图资源 ID）、`fit`（缩放模式：`"contain"` | `"cover"` | `"stretch"`）、`crop`（裁剪区域 `{ x, y, width, height }` 或四元数组）、`opacity`。

> **资源 ID 说明**：`resource`、`icon`、`font` 只接受 Minecraft 资源定位符（例如 `mymod:gui/panel`），不能填本地文件路径或网络 URL。如果资源不存在，可用 `/nekojs error` 查看报错。

<a id="wiki-section-10"></a>
### 颜色格式（注意透明度顺序！）

支持三种写法：
1. **十六进制字符串**：`#RGB`、`#RRGGBB`、`#AARRGGBB`。
2. **整数 ARGB**：如 `0xFF4C566A`。
3. **基础颜色名**：如 `"red"`、`"navy"`、`"white"`、`"transparent"`。

> ⚠️ **避坑**：八位十六进制颜色是 **透明度在前（`#AARRGGBB`）**，和网页 CSS 的 `#RRGGBBAA` 相反！例如半透明白色要写 `#80FFFFFF`。不支持 `rgb()`、`rgba()`、`hsl()`。

<a id="wiki-section-11"></a>
### 复用配色与样式

把常用色值存进普通对象，再封装成函数组件即可：

```tsx
const COLORS = {
  surface: '#20252B',
  border: '#4C566A',
  text: '#E5E9F0'
}

function Card({ title, children }) {
  return (
    <panel background={COLORS.surface} borderColor={COLORS.border} borderWidth={1} radius={4} padding={8} gap={4}>
      <label color={COLORS.text}>{title}</label>
      {children}
    </panel>
  )
}
```

<a id="wiki-section-12"></a>
### 交互控件配色（`appearance`）

按钮、输入框、滑块以及高阶控件（`Toggle`、`Slider`、`Select`、`Tabs`）都支持传入 `appearance` 对象来定制不同交互状态下的颜色：

```tsx
const myAppearance = {
  background: '#20252B',
  borderColor: '#708090',
  color: '#FFFFFF',
  hover: { background: '#303B46' },
  focused: { borderColor: '#88C0D0' },
  pressed: { background: '#405466' },
  disabled: { color: '#777777', background: '#181C20' }
}
```

- **仅限 8 个颜色字段**：`background`、`borderColor`、`color`、`placeholderColor`、`selectionColor`、`cursorColor`、`trackColor`、`thumbColor`（状态子对象 `hover` / `focused` / `pressed` / `disabled` 内也只能写这 8 个字段，写别的会报 `NEKO-7008`）。
- **状态叠加优先级**：基础色 → `hover` → `focused` → `pressed` → `disabled`（后面的覆盖前面的）。

<a id="wiki-section-13"></a>
## 6. 状态管理与事件

<a id="wiki-section-14"></a>
### Signal（单个状态）

用 `UI.createSignal(初始值)` 保存单个响应式状态：

```tsx
const query = UI.createSignal('')

const root = UI.createRoot(() => (
  <column gap={6}>
    <input
      id="query"
      value={query.get()}
      placeholder="输入搜索词..."
      onChange={event => query.set(event.value || '')}
    />
    <label>{`当前搜索：${query.get()}`}</label>
  </column>
), host)
```

- `get()`：读取当前值。只要在 render 函数里调用过 `get()`，后续该状态一变，界面就会自动刷新。
- `set(newValue)`：直接设置新值。
- `update(old => newValue)`：基于旧值计算新值。

<a id="wiki-section-15"></a>
### Store（多字段状态）与批量更新

如果有多个关联字段，可以用 `UI.createStore` 集中管理；需要一次性修改多个状态时，用 `UI.batch` 包裹，让界面只重绘一次：

```javascript
const form = UI.createStore({ name: '', enabled: true })

UI.batch(() => {
  form.set('name', 'Neko')
  form.update('name', old => old + 'JS')
  form.set('enabled', false)
})

console.log(form.get('name'), form.snapshot())
```

> `Signal` 和 `Store` 都只保存在客户端内存中，执行 `/nekojs reload client` 或重启游戏后会回到初始值。如果需要持久化保存或与服务端同步，请看 [数据、状态与持久化](data-and-state_cn)。

<a id="wiki-section-16"></a>
### 事件处理

| 事件属性 | 触发时机 |
|---|---|
| `onClick` | 鼠标点击；或者按钮处于焦点状态时按下 Enter / 空格 |
| `onRelease` | 松开鼠标按键 |
| `onScroll` | 滚动鼠标滚轮 |
| `onKey` | 按下键盘按键 |
| `onTextInput` | 输入字符 |
| `onFocus` / `onBlur` | 元素获得 / 失去焦点 |
| `onChange` | `<input>` 或 `<slider>` 的值发生变化 |
| `onSubmit` | 在 `<input>` 内按下 Enter |

事件回调会收到一个事件对象，包含 `type`、`target`（触发元素的 `id`）、`x`、`y`、`button`、`key`、`value`、`delta` 等字段。

**事件机制注意事项：**
- **必须有唯一 `id`**：没有 `id` 的元素收不到任何事件。
- **返回值类型差异**：基础标签 `<input>` 和 `<slider>` 的 `onChange` 收到的是事件对象，且 `event.value` **永远是字符串**；而高阶组件 `<Slider>` 的 `onChange` 直接收到数字（见 [高阶指南](jsx-client-ui-advanced_cn)）。
- **无事件冒泡**：没有 DOM 的事件冒泡机制，也没有 `preventDefault()` 或 `stopPropagation()`。
- **错误隔离**：事件回调里如果抛错，只会记录一次报错日志，不会导致整个界面崩溃。

<a id="wiki-section-17"></a>
## 7. 组件拆分与列表渲染

界面变复杂后，可以拆分成普通的函数组件。**组件函数必须定义在 render 函数外面**，渲染列表时每一项都要传稳定且不重复的 `key`：

```tsx
function ItemRow({ name, desc }) {
  return (
    <panel background="#20252B" padding={8} gap={4}>
      <label color="#E5E9F0">{name}</label>
      <label color="#A0A0A0">{desc}</label>
    </panel>
  )
}

const items = [
  { id: 'diamond', name: '钻石', desc: '非常稀有' },
  { id: 'dirt', name: '泥土', desc: '到处都是' }
]

const root = UI.createRoot(() => (
  <scroll width="fill" height="fill">
    <column gap={6}>
      {items.map(item => (
        <ItemRow key={item.id} name={item.name} desc={item.desc} />
      ))}
    </column>
  </scroll>
), host)
```

> 如果需要在纯代码里动态创建节点，也可以调用 `UI.element(组件或标签名, props, key)`，效果与写 JSX 完全一致。

<a id="wiki-section-18"></a>
### 只读物品图标（`<item>`）

想在界面里展示 Minecraft 物品图标及原版悬浮提示，直接用 `<item>`：

```tsx
<item id="preview-diamond" item="minecraft:diamond" count={1} itemTooltip={true} width={32} height={32} />
```

- `item` 传物品注册名字符串（不是 `ItemStack`），写错物品 ID 会报 `NEKO-6012`。
- `count` 范围 `1`～`99`（默认 `1`）。
- `itemTooltip` 默认为 `true`（显示原版物品提示）；也可传 `tooltip="自定义提示"` 覆盖。
- 注意：这只是纯展示图标，不是背包槽位，不能点击拿取物品。

<a id="wiki-section-19"></a>
## 8. 国际化、生命周期与排错

<a id="wiki-section-20"></a>
### 界面文字翻译（`I18n`）

在客户端脚本中，可以用全局对象 `I18n` 根据玩家当前的客户端语言获取翻译文本（全平台客户端脚本通用）：

```javascript
const done = I18n.translate('gui.done') // 找不到时返回 key 本身
const hello = I18n.translateWithFallback('mymod.hello', 'Hello, %s', 'Alex') // 找不到时使用备用文本
if (I18n.has('mymod.title')) {
  console.log('当前语言代码：', I18n.languageCode) // 例如 "zh_cn"
}
```

<a id="wiki-section-21"></a>
### 关闭、重载与调试方法

- **关闭界面**：玩家按 Esc 即可关闭（若不想让 Esc 关闭，在 `<screen>` 上设置 `closeOnEscape={false}`）；也可以在代码里主动调用 `host.close()`。
- **热重载行为**：执行 `/nekojs reload client` 时，旧脚本打开的界面会自动关闭；如果新脚本报错，重载会回滚并保留上一次正常的版本。

<details>
<summary>调试专用：<code>root</code> 对象上的底层方法（日常开发一般用不到）</summary>

| 方法 | 作用 |
|---|---|
| `root.refresh()` | 手动强制触发一次重新渲染 |
| `root.layout()` | 读取当前计算出的布局树信息（调试排版用） |
| `root.dispatch(id, eventName, event)` | 用代码模拟触发某个元素的事件，如 `root.dispatch('increment', 'click', {})` |
| `root.isDisposed()` | 检查界面是否已经销毁关闭 |
</details>

<a id="wiki-section-22"></a>
### 常见报错速查

渲染或布局出错时，界面会保持上一次正常渲染的样子，不会直接崩游戏。你可以用 `/nekojs error` 查看具体报错：

| 错误码 | 常见原因 | 解决办法 |
|---|---|---|
| `NEKO-7001` | 复用了已关闭或旧脚本遗留的界面对象 | 每次重新打开界面或 reload 后，重新创建 `host` 和 `root` |
| `NEKO-7003` | 在非客户端脚本中调用了 `ClientUI` | 确保代码放在 `client_scripts/` 目录下 |
| `NEKO-7004` | 在后台线程中操作了界面 | 只在客户端主线程 / 界面事件回调中更新界面 |
| `NEKO-7007` | 渲染、布局或事件报错（如写了不支持的属性 `style`） | 执行 `/nekojs error` 查看具体是哪个属性或哪行代码出错 |
| `NEKO-7008` | `appearance` 配色对象里写了不支持的字段 | 只使用上文列出的 8 个颜色字段 |
| `NEKO-7011` | 滑块的 `min`、`max`、`step` 或 `value` 不合法 | 确保 `min < max`、`step > 0`，且 `value` 在最小和最大值之间 |
| `NEKO-7012` | `<Dialog>` 属性不合法 | 检查对话框的 `id` 和 `title` |
| `NEKO-7013` | 多窗口工作区（Workspace）配置不合法 | 检查窗口 `id` 及 `workspace.add()` 的配置项 |
| `NEKO-7014` | 同一个 `host` 绑定了多个 `root`，或嵌入区域尺寸非法 | 每个 `host` 只调用一次 `bindRoot` |
| `NEKO-6012` | `<item>` 的物品 ID 不存在 | 检查物品注册名是否拼写正确 |

<a id="wiki-section-23"></a>
## 9. 进阶与周边

当基础标签不够用，或者你想做更复杂的交互界面时，请继续阅读：

- 👉 **[JSX 进阶控件与工作区](jsx-client-ui-advanced_cn)**：封装好的受控组件（`Toggle` 开关、`Slider` 滑块、`Select` 下拉列表）、页签（`Tabs`）、表单校验（`validateForm`）、模态对话框（`Dialog`）、多窗口桌面（`createWorkspace`）以及如何用 `ClientUI.region` 把 JSX 嵌进 Java 自定义 Screen。

<a id="wiki-section-24"></a>
### 其他做界面的方式对比

JSX 适合做点击、输入类的面板；如果是做常驻 HUD 或世界内特效，请用对应的绘制事件：

| 你的需求 | 推荐方案 | 参考文档 |
|---|---|---|
| 设置面板、表单、列表、自定义弹窗 | **JSX 客户端 UI**（本页） | 本页及 [JSX 进阶指南](jsx-client-ui-advanced_cn) |
| 屏幕角落常驻显示文字、血条、图标 | `ClientEvents.hud` / `hudRender` | [客户端绘制与后处理](client-rendering_cn) |
| 在原版背包/界面上叠加额外提示 | `ClientEvents.screenRender` | [客户端绘制与后处理](client-rendering_cn) |
| 在 3D 世界里画线框、路径、高亮块 | `ClientEvents.worldRender` | [客户端绘制与后处理](client-rendering_cn) |
| 带真实物品槽位、服务端同步的机器容器 GUI | 编写 Java 模组/插件 | [插件开发](plugin-development_cn) |

> **当前限制（暂不支持）**：Fabric 与 NeoForge 1.21.1 平台、真实容器槽位与背包物品同步、CSS/动画/阴影/渐变、React Hooks（`useState` 等）、拖放、虚拟列表、多行文本框以及输入法候选框（IME）。

<!-- wiki-nav -->

---

[上一篇: TypeScript 与 JSX](typescript-and-jsx_cn) · [目录](Home) · [下一篇: 客户端绘制与后处理](client-rendering_cn)
