<!-- wiki-page: jsx-client-ui; locale: cn -->

> **中文** · [English](jsx-client-ui_us)

<a id="wiki-section-1"></a>
# JSX 客户端 UI

用 JSX 写 Minecraft 界面：按钮、输入框、滑块、页签、对话框，甚至多窗口桌面。你描述界面长什么样、状态怎么变，NekoJS 负责布局、绘制、焦点和键鼠输入。

> **只在 NeoForge 26.1.2 / 26.2.0 上可用。** 界面脚本放在 `client_scripts/`，通过全局对象 `ClientUI` 创建。Fabric、NeoForge 1.21.1 和独立服务器上没有 `ClientUI`。详见 [平台与兼容性](platform-compatibility_cn)。

<a id="wiki-section-2"></a>
## 准备工作

先开启 automatic JSX runtime。在 `nekojs/config/engine.toml` 中设置：

```toml
jsxAutomaticRuntime = true
```

改完后重启游戏，再运行一次 `/nekojs probe`，让编辑器拿到 JSX 补全。

`nekojs/jsx-runtime` 是 NekoJS 自带的模块，不需要在 `node_modules/` 里放任何文件。

<a id="wiki-section-3"></a>
## 第一个界面

把下面的脚本保存为 `nekojs/client_scripts/counter.tsx`，然后执行 `/nekojs reload client`：

```tsx
import { UI } from 'nekojs/jsx-runtime'

const count = UI.createSignal(0)
const host = ClientUI.screen('计数器', false)

const root = UI.createRoot(() => (
  <screen padding={12}>
    <column gap={8} align="center">
      <label>{`当前值：${count.get()}`}</label>
      <button id="increment" onClick={() => count.update(value => value + 1)}>
        加一
      </button>
    </column>
  </screen>
), host)

host.bindRoot(root)
host.open()
```

reload 之后界面会直接打开。每点一次按钮，数字加一。按 Esc 关闭。

这段代码做了五件事：

1. `UI.createSignal(0)` 创建一个状态，初始值是 0。
2. `ClientUI.screen(标题, 是否暂停游戏)` 创建一个界面宿主（host）。
3. `UI.createRoot(render, host)` 渲染界面。render 函数里读过的状态一变，界面就自动更新。
4. `host.bindRoot(root)` 把界面挂到宿主上。
5. `host.open()` 打开界面。

几条规则：

- 状态（signal）要在 render 函数**外面**创建。写在里面的话，每次重新渲染都会被重置。
- 要接收事件的元素必须有 `id`。只写 `onClick` 不写 `id`，点击不会有反应。
- 界面关闭后不能再 `open()`。要再次打开，重新创建 host 和 root，比如把上面的创建代码放进一个函数里，每次需要时调用。

<a id="wiki-section-4"></a>
## 这不是 HTML

JSX 只是语法。`<panel>`、`<row>`、`<button>` 这些小写标签是 NekoJS 的内置元素，不是网页标签。所以：

- 不能用 `<div>`、`<span>`、`<form>` 等网页标签。
- 没有 CSS：不能写 `className`、`style={{ ... }}`，也没有选择器和样式继承。
- `margin`、`box-shadow`、`transform`、`z-index`、`font-weight` 等属性都不存在。
- 不能把 Java 类、原版 Widget 或 `GuiGraphics` 当作标签用。

写了不支持的属性会报错，例如写 `style` 会得到 `Unsupported prop`。

想复用样式和结构，就写函数组件，见下文“组件和列表”一节。

<a id="wiki-section-5"></a>
## 内置元素

小写标签只能是下面 16 种。也可以在脚本里调用 `UI.primitives()` 查看。

| 元素 | 用途 | 常用属性 |
|---|---|---|
| `screen` | 界面根节点 | `title`、`pausesGame`、`closeOnEscape` |
| `panel` | 带背景和边框的容器 | `background`、`borderColor`、`borderWidth`、`radius`、`opacity` |
| `row` | 横向排列 | 通用布局属性 |
| `column` | 纵向排列 | 通用布局属性 |
| `stack` | 子元素叠在一起 | 通用布局属性，子元素用 `anchor` 定位 |
| `scroll` | 可滚动区域 | `scrollX`、`scrollY`、`scrollOffset` |
| `label` | 文字 | `text`、`color`、`fontSize`、`font`、`wrap`、`truncate` |
| `button` | 按钮 | `text`、`disabled`、`tooltip`、`appearance` |
| `input` | 单行输入框 | `value`、`placeholder`、`maxLength`、`disabled`、`appearance` |
| `slider` | 数值滑块 | `value`、`min`、`max`、`step`、`disabled`、`appearance` |
| `image` | 图片或图标 | `resource`、`icon`、`fit`、`crop`、`opacity` |
| `item` | 只读物品图标 | `item`、`count`、`itemTooltip`、`tooltip` |
| `dialog` | 模态对话框（建议用 `Dialog` 组件） | `title`、`closeOnEscape`、`closeOnBackdrop` |
| `spacer` | 占位、撑开空间 | 通用布局属性 |
| `workspace` | 多窗口桌面（由 `createWorkspace` 管理） | 通用布局属性 |
| `window` | 桌面里的一个窗口（由 `createWorkspace` 管理） | 见下文“多窗口工作区” |

`label` 和 `button` 的文字可以写成子元素（`<label>你好</label>`），也可以写成 `text` 属性。

大写开头的标签是你自己写的函数组件，见下文“组件和列表”一节。

<a id="wiki-section-6"></a>
## 布局

单位是 Minecraft 的 GUI 像素。所有元素都支持这些通用属性：

- 尺寸：`width`、`height`、`minWidth`、`maxWidth`、`minHeight`、`maxHeight`。值可以是数字、`"auto"`、`"fill"`（占满剩余空间）或百分比字符串如 `"80%"`。
- 间距：`gap`（子元素之间）、`padding`（四边统一的内边距）。
- 对齐：`align` 可选 `start`、`center`、`end`、`stretch`；`justify` 可选 `start`、`center`、`end`、`spaceBetween`、`spaceAround`。
- 方向和定位：`direction`（`row` 或 `column`）；在 `stack` 里用 `anchor` 放到九个位置之一，如 `topLeft`、`center`、`bottomRight`。
- 显示：`visible`。
- 身份：`id`，以及列表元素的 `key`。

```tsx
<panel width="80%" minWidth={240} maxWidth={640} padding={12}>
  <label>随窗口变宽，但不会小于 240 像素</label>
</panel>
```

文字的宽度和换行由游戏字体测量，脚本里不用自己估算。

<a id="wiki-section-7"></a>
### 按窗口大小调整

尺寸、间距、对齐、可见性等属性可以写成响应式值：`{ base, profiles: { 档位: 值 } }`。NekoJS 按当前界面大小算出一个 1 到 6 的档位：

| 档位 | 最小宽度 | 最小高度 |
|---|---|---|
| 1 | 无 | 无 |
| 2 | 320 | 180 |
| 3 | 480 | 240 |
| 4 | 640 | 360 |
| 5 | 854 | 480 |
| 6 | 1280 | 720 |

宽度和高度各算一个档位，取较小的那个。单位是 GUI 像素，所以调大 GUI 缩放会降低档位。

```tsx
<column
  padding={{ base: 8, profiles: { 1: 4, 5: 16, 6: 24 } }}
  gap={{ base: 6, profiles: { 1: 3, 6: 12 } }}
>
  {/* ... */}
</column>
```

取值规则：先找当前档位；没写就往低档找最近的；都没有就用 `base`。不会去取更高档的值。所以上例在 1～6 档的 `padding` 是 4、4、4、4、16、24。

窗口大小变化时，界面只重新布局，不重新执行 render 函数。

颜色等视觉属性不支持响应式值。

<a id="wiki-section-8"></a>
### 滚动

内容放不下时，用 `scroll` 包起来。内容溢出时会自动出现细滚动条，可以拖动，也可以点击轨道翻一页。`scroll` 的方向是 `row` 时横向滚动，其他情况纵向滚动。滚动条盖在内容边缘上，不占布局空间，必要时给内容留一点 `padding`。

长文本直接放进 `label` 再套一层 `scroll` 即可，不用自己分页。

<a id="wiki-section-9"></a>
## 样式

样式直接写成元素属性，每种元素只接受自己的那几个：

| 想要的效果 | 属性 | 适用元素 |
|---|---|---|
| 文字颜色 | `color` | `label` |
| 背景色 | `background` | `panel` |
| 边框颜色和宽度 | `borderColor`、`borderWidth` | `panel` |
| 圆角 | `radius` | `panel` |
| 透明度 | `opacity` | `panel`、`image` |
| 字号 | `fontSize` | `label` |
| 字体 | `font` | `label` |
| 换行、截断 | `wrap`、`truncate` | `label` |
| 图片缩放方式 | `fit`：`contain` / `cover` / `stretch` | `image` |
| 图片裁剪 | `crop`：`{ x, y, width, height }` 或四个数的数组 | `image` |
| 图片资源 | `resource`、`icon` | `image` |

<a id="wiki-section-10"></a>
### 颜色写法

颜色支持这些格式：

- `#RGB`、`#RRGGBB`、`#AARRGGBB`。
- 基础颜色名和 `transparent`，如 `red`、`navy`、`white`。
- 整数 ARGB，如 `0xFF4C566A`。

注意：八位十六进制是 **透明度在前**（`#AARRGGBB`），和网页的 `#RRGGBBAA` 相反。半透明白色要写 `#80FFFFFF`。

不支持 `rgb(...)`、`rgba(...)`、`hsl(...)`。

<a id="wiki-section-11"></a>
### 复用样式

用普通对象保存颜色，再写一个函数组件：

```tsx
const COLORS = {
  surface: '#20252B',
  border: '#4C566A',
  text: '#E5E9F0'
}

function Surface({ children }) {
  return (
    <panel background={COLORS.surface} borderColor={COLORS.border} borderWidth={1} radius={4} padding={8}>
      {children}
    </panel>
  )
}
```

不要写 `<panel style={COLORS}>`，`style` 不是支持的属性。

<a id="wiki-section-12"></a>
### 控件配色（appearance）

`button`、`input`、`slider` 以及下面介绍的 `Toggle`、`Slider`、`Select`、`Tabs` 都接受 `appearance` 对象：

- 颜色字段只有八个：`background`、`borderColor`、`color`、`placeholderColor`、`selectionColor`、`cursorColor`、`trackColor`、`thumbColor`。
- 状态子对象 `hover`、`focused`、`pressed`、`disabled` 里也只能写这八个字段。
- 多个状态同时成立时，后面的覆盖前面的：基础 → hover → focused → pressed → disabled。

写了别的字段会报错。完整写法见下文“常用控件”的示例。

<a id="wiki-section-13"></a>
### 资源 id

`resource`、`icon`、`font` 只接受资源 id，例如 `mymod:gui/panel`。不能写文件路径或网址。资源找不到时会记录错误，可以用 `/nekojs error` 查看。

<a id="wiki-section-14"></a>
## 从 HTML/CSS 迁移

已有网页设计时，可以照着下表改写：

| 网页写法 | NekoJS 写法 | 说明 |
|---|---|---|
| `div` | `column` | 默认纵向排列 |
| `display: flex; flex-direction: row` | `row` | `gap` 直接对应 |
| 绝对定位的浮层 | `stack` + 子元素 `anchor` | 只有九个锚点 |
| `overflow: auto` | `scroll` | |
| `span`、`p`、文本 | `label` | |
| `img` | `image` | `src` 改成资源 id |
| `flex: 1` | `width="fill"` 或 `height="fill"` | |
| `@media` | `{ base, profiles }` | 见上文档位 |
| `z-index` | `stack` 里的子元素顺序 | 后面的盖住前面的 |
| `margin` | 父元素的 `gap`、`padding` 或 `spacer` | |
| CSS Grid、动画、阴影、渐变 | 没有对应写法 | 用 `row`/`column` 组合或换方案 |

<a id="wiki-section-15"></a>
## 状态

<a id="wiki-section-16"></a>
### Signal

`UI.createSignal(初始值)` 保存一个值：

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
), host)
```

- `get()` 读取当前值。在 render 函数里读过的 signal，变化后界面会自动更新。
- `set(value)` 设置新值。
- `update(old => new)` 根据旧值算出新值。

<a id="wiki-section-17"></a>
### Store

几个相关的值可以放进一个 store：

```javascript
const form = UI.createStore({ name: '', enabled: true })
form.set('name', 'Neko')
form.update('name', old => old + 'JS')
console.log(form.get('name'), form.snapshot())
```

signal 和 store 都只存在内存里。`/nekojs reload client` 或重启游戏后，它们回到初始值。需要保存的数据请看 [数据与状态](data-and-state_cn)。

<a id="wiki-section-18"></a>
### 批量更新

一次改多个值时，用 `UI.batch` 包起来，界面只更新一次：

```javascript
UI.batch(() => {
  form.set('name', 'Neko')
  form.set('enabled', false)
})
```

<a id="wiki-section-19"></a>
## 事件

| 属性 | 什么时候触发 |
|---|---|
| `onClick` | 点击；按钮获得焦点时按 Enter 或空格也会触发 |
| `onRelease` | 松开鼠标 |
| `onScroll` | 滚轮 |
| `onKey` | 按键 |
| `onTextInput` | 输入字符 |
| `onFocus` / `onBlur` | 获得 / 失去焦点 |
| `onChange` | `input` 或 `slider` 的值变了 |
| `onSubmit` | 在 `input` 里按 Enter |

处理函数收到一个事件对象，常用字段有 `type`、`target`（元素的 id）、`x`、`y`、`button`、`key`、`value`、`delta`。

注意：

- **元素必须有 `id` 才能收到事件**，而且 id 在整个界面里不能重复。
- 小写 `input` 和 `slider` 的 `onChange` 收到的是事件对象，`event.value` 是**字符串**。下文大写的 `Slider` 组件则直接收到数字。
- 没有网页那样的冒泡、`preventDefault()` 或 `stopPropagation()`。
- 禁用（`disabled`）的元素不会收到事件。
- 处理函数里报错只会记录这一次错误，界面其他部分照常工作。

<a id="wiki-section-20"></a>
## 组件和列表

界面复杂了，就拆成函数组件。组件是普通函数，接收 props，返回 JSX：

```tsx
function Card({ title, children }) {
  return (
    <panel background="#20252B" borderColor="#4C566A" borderWidth={1} radius={4} padding={8} gap={4}>
      <label color="#E5E9F0">{title}</label>
      {children}
    </panel>
  )
}

const items = [
  { id: 'a', name: '钻石', description: '很贵' },
  { id: 'b', name: '泥土', description: '很多' }
]

const root = UI.createRoot(() => (
  <scroll width="fill" height="fill">
    <column gap={6}>
      {items.map(item => (
        <Card key={item.id} title={item.name}>
          <label>{item.description}</label>
        </Card>
      ))}
    </column>
  </scroll>
), host)
```

列表里的每一项都要有稳定且不重复的 `key`。

组件函数要定义在 render 外面，不要每次渲染都新建一个函数。

需要在代码里动态创建元素时，可以用 `UI.element(类型, props, key)`，效果和写 JSX 一样，例如 `UI.element(Card, { title: '钻石' }, 'a')`。

<a id="wiki-section-21"></a>
## 常用控件

从 `nekojs/jsx-runtime` 导入 `Toggle`（开关）、`Slider`（滑块）和 `Select`（选择列表）。它们都是"受控"组件：自己不保存状态，你每次渲染都要传入当前值，并在回调里更新 signal。

把下面的脚本保存为 `nekojs/client_scripts/controls.tsx`，执行 `/nekojs reload client`：

```tsx
// CLIENT only: requires the ClientUI binding published on NeoForge 26.x.
import { UI, Toggle, Slider, Select } from "nekojs/jsx-runtime";

const text = UI.createSignal("Paste or type a long single-line message here.");
const enabled = UI.createSignal(true);
const volume = UI.createSignal(40);
const quality = UI.createSignal("balanced");
const expanded = UI.createSignal(false);
const options = [
    { value: "balanced", label: "Balanced" },
    { value: "fast", label: "Fast" },
    { value: "unavailable", label: "Unavailable", disabled: true }
];
const appearance = {
    background: "#20252B",
    borderColor: "#708090",
    color: "#FFFFFF",
    placeholderColor: "#A0A0A0",
    selectionColor: "#804488CC",
    cursorColor: "#FFFFFF",
    trackColor: "#4C566A",
    thumbColor: "#88C0D0",
    hover: { background: "#303B46" },
    focused: { borderColor: "#88C0D0" },
    pressed: { background: "#405466" },
    disabled: { color: "#777777", background: "#181C20" }
};

const host = ClientUI.screen("Authoring controls", false);
const root = UI.createRoot(() => (
    <screen padding={12}>
        <scroll id="controls-scroll" width="fill" height="fill">
            <column gap={8} width={280}>
                <label>Clipboard and controlled components</label>
                <input id="controls-text" width="fill" height={20}
                    value={text.get()} maxLength={160} appearance={appearance}
                    onChange={event => text.set(event.value || "")} />
                <Toggle id="controls-enabled" width="fill" height={20}
                    checked={enabled.get()} label="Enable volume"
                    appearance={appearance} onChange={value => enabled.set(value)} />
                <Slider id="controls-volume" width="fill" height={20}
                    value={volume.get()} min={0} max={100} step={5}
                    disabled={!enabled.get()} appearance={appearance}
                    onChange={value => volume.set(value)} />
                <Select id="controls-quality" width="fill"
                    value={quality.get()} options={options} expanded={expanded.get()}
                    onExpandedChange={value => expanded.set(value)}
                    appearance={appearance} onChange={value => quality.set(value)} />
                <label id="controls-summary">{
                    "enabled=" + String(enabled.get()) + "; volume=" + String(volume.get())
                    + "; quality=" + quality.get()
                }</label>
                <label>Close this screen, then reload CLIENT to reset the example.</label>
            </column>
        </scroll>
    </screen>
), host);
host.bindRoot(root);
host.open();
```

打开后摘要显示 `enabled=true; volume=40; quality=balanced`。可以试试：

- 点 Enable volume 开关，滑块随之禁用或启用。
- 拖动滑块，或选中后用方向键、Home、End 调整。
- 点开选择列表，选 Fast，摘要变成 `quality=fast`。Unavailable 是灰的，选不了。
- 在输入框里输入、选择文字、复制粘贴。

各组件需要的属性：

- `Toggle`：`id`、`checked`、`onChange(布尔值)`，可选 `label`。
- `Slider`：`id`、`value`、`onChange(数字)`，可选 `min`、`max`、`step`（默认 0、100、1）。
- `Select`：`id`、`value`、`options`、`expanded`、`onExpandedChange(布尔值)`、`onChange(字符串)`。`options` 里每项是 `{ value, label, disabled? }`，`value` 不能重复，而且必须包含当前值。

`Select` 展开后是直接显示在原位置的列表，最多显示六行，多了可以滚动。它不是浮在上层的下拉框。

组件会占用一些派生 id。例如 `id="quality"` 的 `Select` 还会用到 `quality:root`、`quality:options` 和 `quality:option:...`。给其他元素起名时避开这些。

<a id="wiki-section-22"></a>
### 输入框和滑块的细节

- `input` 只支持单行，不支持输入法候选框（IME）。`maxLength` 按字符数计算。
- 快捷键：Ctrl+A 全选，Shift+方向键扩选，Ctrl+C / X / V 复制、剪切、粘贴（macOS 上用 Cmd）。粘贴时会去掉换行和制表符。
- `slider` 要求 `min < max`、`step > 0`，且 `value` 在范围内，否则报错。拖动时按 `step` 吸附；拖到两端或按 Home / End 会正好停在 `min` / `max`。

<a id="wiki-section-23"></a>
## 页签、表单和对话框

`Tabs`、`Dialog`、`validateForm` 和 `Validators` 也从 `nekojs/jsx-runtime` 导入。

把下面的脚本保存为 `nekojs/client_scripts/forms-and-dialogs.tsx`，执行 `/nekojs reload client`。如果之前的示例还在，先删掉，否则几个界面会抢着打开。

```tsx
// CLIENT only: requires the ClientUI binding published on NeoForge 26.x.
import { UI, Tabs, Dialog, validateForm, Validators } from "nekojs/jsx-runtime";

const tab = UI.createSignal("profile");
const name = UI.createSignal("");
const quantity = UI.createSignal("1");
const errors = UI.createSignal("");
const dialogOpen = UI.createSignal(false);
const reviewed = UI.createSignal({ name: "", quantity: 1 });
const status = UI.createSignal("Nothing confirmed");
const backgroundClicks = UI.createSignal(0);

function review() {
    const values = { name: name.get(), quantity: quantity.get() };
    const result = validateForm(values, {
        name: [Validators.required("Enter a name"), Validators.maxLength(24, "Use at most 24 characters")],
        quantity: [
            Validators.required("Enter a quantity"),
            Validators.numberRange(1, 64, "Quantity must be between 1 and 64"),
            value => Number.isInteger(Number(value)) ? null : "Quantity must be a whole number"
        ]
    });
    errors.set(Object.keys(result.errors).map(field => result.errors[field].join("; ")).join(" | "));
    if (!result.valid) return;
    reviewed.set({ name: values.name.trim(), quantity: Number(values.quantity) });
    dialogOpen.set(true);
}

const tabs = [
    {
        value: "profile", label: "Profile", render: () => (
            <column gap={6} width="fill">
                <label>Name and requested quantity (local preview only)</label>
                <input id="forms-name" width="fill" height={20} value={name.get()} maxLength={24}
                    placeholder="Name" onChange={event => name.set(event.value || "")} />
                <input id="forms-quantity" width="fill" height={20} value={quantity.get()} maxLength={8}
                    placeholder="Whole number 1..64" onChange={event => quantity.set(event.value || "")} />
                <label id="forms-errors">{errors.get()}</label>
                <button id="forms-review" height={20} onClick={review}>Review</button>
            </column>
        )
    },
    {
        value: "preview", label: "Preview", render: () => (
            <column gap={6}>
                <item id="forms-preview-item" item="minecraft:diamond" count={1} itemTooltip={true}
                    width={32} height={32} />
                <label>Read-only icon and tooltip; no inventory is changed.</label>
            </column>
        )
    },
    { value: "locked", label: "Locked", disabled: true, render: () => <label>Not selectable</label> }
];

const host = ClientUI.screen("Authoring forms and dialogs", false);
const root = UI.createRoot(() => (
    <screen padding={12}>
        <scroll id="forms-scroll" width="fill" height="fill">
            <column gap={8} width="fill">
                <Tabs id="forms-tabs" width="fill" height={190} value={tab.get()} tabs={tabs}
                    onChange={value => tab.set(value)} />
                <button id="forms-background" height={20}
                    onClick={() => backgroundClicks.update(value => value + 1)}>Background action</button>
                <label id="forms-background-count">{"Background clicks: " + backgroundClicks.get()}</label>
                <label id="forms-status">{status.get()}</label>
            </column>
        </scroll>
        <Dialog id="forms-dialog" open={dialogOpen.get()} title="Confirm local profile"
            onOpenChange={value => dialogOpen.set(value)}>
            <scroll id="forms-dialog-scroll" width="fill" height="fill">
                <column gap={8} width="fill">
                    <label>{reviewed.get().name + " x" + reviewed.get().quantity}</label>
                    <label>Confirmation changes only this screen, not a world or inventory.</label>
                    <button id="forms-cancel" height={20} onClick={() => dialogOpen.set(false)}>Cancel</button>
                    <button id="forms-confirm" height={20} onClick={() => {
                        status.set("Confirmed " + reviewed.get().name + " x" + reviewed.get().quantity);
                        dialogOpen.set(false);
                    }}>Confirm</button>
                </column>
            </scroll>
        </Dialog>
    </screen>
), host);
host.bindRoot(root);
host.open();
```

可以这样试：

1. 名字留空，点 Review，会显示错误，对话框不会打开。
2. 数量填 `0`、`65`、`abc` 或 `1.5`，都会被拒绝。
3. 填 `Alex` 和 `3`，点 Review，对话框打开。点 Cancel、按 Esc 或点对话框外面，都会关闭，状态不变。
4. 再次 Review 并点 Confirm，状态变成 `Confirmed Alex x3`。
5. 对话框打开时，背后的 Background action 按钮点不到。
6. 切到 Preview 页签能看到钻石图标；Locked 页签选不了。切回 Profile，刚才填的内容还在。

这个示例只改界面上的状态，不会动世界或背包。

<a id="wiki-section-24"></a>
### Tabs

- 需要 `id`、`value`（当前页签）、`tabs` 和 `onChange`。
- `tabs` 里每项是 `{ value, label, disabled?, render }`。只有选中页签的 `render` 会执行。
- 页签内容里的状态要放在外部 signal 里，切页后才能保留。
- 方向键、Home、End 可以切换页签，会跳过禁用的页签。
- `Tabs` 默认占满父元素。像示例那样放在其他内容旁边时，要给它一个明确的 `height`。

<a id="wiki-section-25"></a>
### 表单校验

`validateForm(values, rules)` 返回 `{ valid, errors }`。`errors` 里只有出错的字段，每个字段对应一组错误消息。它只负责检查，不会显示错误，也不会提交任何东西。

内置规则：

- `Validators.required(消息?)`：不能为空。空白字符串也算空，但 `0` 和 `false` 算有值。
- `Validators.minLength(n, 消息?)` / `maxLength(n, 消息?)`：字符串长度。
- `Validators.numberRange(min, max, 消息?)`：数值范围。它不检查是不是整数。

自定义规则是一个函数 `(value, values) => 错误消息 | null`，例如示例里的整数检查。不支持异步校验。

<a id="wiki-section-26"></a>
### 对话框

- `Dialog` 需要 `id`、`open` 和 `onOpenChange`，可选 `title`、`width`、`height`（默认 80%）、`closeOnEscape`、`closeOnBackdrop`（默认都是 `true`）。
- 按 Esc 或点外面时，`Dialog` 只是调用 `onOpenChange(false)`，要由你把 signal 设成 `false` 才真正关闭。
- 对话框打开时，背后的界面收不到点击、滚轮和键盘输入；Tab 键只在对话框内切换焦点。在对话框里按 Esc 只关对话框，不会关掉整个界面。
- 内容可能放不下时，在里面套一层 `scroll`。

<a id="wiki-section-27"></a>
### 物品图标

```tsx
<item item="minecraft:diamond" count={1} itemTooltip={true} />
```

- `item` 写物品 id，不是物品栈。
- `count` 是 1 到 99 的整数，默认 1。
- `itemTooltip` 默认 `true`，鼠标悬停时显示原版提示；写 `tooltip="..."` 可以换成自己的文字。
- 这只是一个图标，不是物品栏格子，不能拿取物品，也不会改背包。
- 物品 id 不存在时报 `NEKO-6012`，界面保持上一次的样子。

<a id="wiki-section-28"></a>
## 多窗口工作区

`nekojs/jsx-workspace` 提供的 `createWorkspace` 可以在一个界面里放多个能拖动、缩放、最小化的窗口。它们都在同一个 Minecraft 界面里，不会创建系统窗口。

把下面的脚本保存为 `nekojs/client_scripts/workspace.tsx`，删掉其他会自动打开界面的示例，执行 `/nekojs reload client`：

```tsx
// CLIENT only: requires the ClientUI binding published on NeoForge 26.x.
import { UI } from "nekojs/jsx-runtime";
import { createWorkspace } from "nekojs/jsx-workspace";

const note = UI.createSignal("Draft note");
const toolText = UI.createSignal("Tool input");
const saved = UI.createSignal(0);
const clicks = UI.createSignal(0);
const host = ClientUI.screen("Authoring workspace", false);
const workspace = createWorkspace(host, {
    minimizedPresentation: "taskbar",
    taskbarHeight: 28
});

workspace.add("notes", () => (
    <scroll width="fill" height="fill">
        <column padding={6} gap={6} width="fill">
            <input id="notes-text" width="fill" height={20} maxLength={80}
                value={note.get()} onChange={event => note.set(event.value || "")} />
            <button id="notes-save" height={20} onClick={() => saved.update(value => value + 1)}>
                Save locally
            </button>
            <label id="notes-count">{"Notes saved: " + saved.get()}</label>
        </column>
    </scroll>
), {
    title: "Notes", rect: { x: 12, y: 12, width: 190, height: 136 },
    responsiveRect: {
        x: { base: 12, profiles: { 1: 8, 2: 12 } },
        width: { base: 190, profiles: { 1: "70%", 2: "60%" } },
        height: "65%"
    },
    minWidth: 160, minHeight: 100, maxWidth: 480, maxHeight: 320,
    resizable: true, minimizable: true, maximizable: true,
    titlebarHeight: 24, titlebarButtonWidth: 18, titlebarTitle: true
});

workspace.add("tools", () => (
    <scroll width="fill" height="fill">
        <column padding={6} gap={6} width="fill">
            <input id="tools-text" width="fill" height={20} maxLength={80}
                value={toolText.get()} onChange={event => toolText.set(event.value || "")} />
            <button id="tools-click" height={20} onClick={() => clicks.update(value => value + 1)}>
                Count this window
            </button>
            <label id="tools-count">{"Tools clicks: " + clicks.get()}</label>
        </column>
    </scroll>
), {
    title: "Tools", rect: { x: 90, y: 42, width: 190, height: 136 },
    minWidth: 140, minHeight: 100,
    resizable: true, minimizable: true, maximizable: true,
    titlebarTitle: false
});

workspace.add("preview", () => (
    <scroll width="fill" height="fill">
        <column padding={6} gap={6} width="fill">
            <item id="preview-item" item="minecraft:diamond" count={1} />
            <label id="preview-summary">{
                "Saved=" + saved.get() + "; clicks=" + clicks.get()
            }</label>
            <label>Drag a titlebar; close one window without closing the desktop.</label>
        </column>
    </scroll>
), {
    title: "Preview", rect: { x: 160, y: 72, width: 190, height: 136 },
    resizable: true, minimizable: true, maximizable: true
});

workspace.open();
```

可以试试：拖动标题栏；拖边或角改变大小；最小化 Notes 后从底部任务栏点它还原，输入的文字还在；最大化再还原；关掉其中一个窗口，其他窗口不受影响。

注意：

- `createWorkspace` 自己创建并绑定 root。不要再调用 `UI.createRoot` 或 `bindRoot`。
- 每个窗口的状态照样放在 render 外的 signal 里。
- 不同窗口里的元素可以用相同的 `id`，同一窗口里不能重复。
- 最小化或隐藏不会暂停状态更新，也不会保存任何东西。

<a id="wiki-section-29"></a>
### 控制窗口

| 方法 | 作用 |
|---|---|
| `add(id, render, config?)` | 添加窗口 |
| `update(id, patch)` | 修改窗口设置，也可以换 `render` |
| `activate(id)` | 激活并置顶，最小化的会先还原 |
| `show(id)` / `hide(id)` | 显示 / 隐藏窗口，内容保留 |
| `minimize(id)` / `maximize(id)` / `restore(id)` | 最小化 / 最大化 / 还原 |
| `resize(id, rect)` | 改变位置或大小 |
| `closeWindow(id)` | 彻底移除这个窗口 |
| `open()` / `close()` | 打开 / 关闭整个界面 |
| `snapshot()` | 读取所有窗口的状态 |

不要在窗口的 render 函数里调用这些方法。

<a id="wiki-section-30"></a>
### 窗口设置

`add` 的第三个参数：

- `title`：标题。
- `rect`：`{ x, y, width, height }`，正常状态下的位置和大小，默认 `{ x: 16, y: 16, width: 240, height: 160 }`。
- `minWidth`、`minHeight`、`maxWidth`、`maxHeight`：缩放范围。最小不能低于 40 × 20。
- `resizable`、`minimizable`、`maximizable`：是否允许缩放、最小化、最大化，默认都是 `false`。
- `titlebarHeight`、`titlebarButtonWidth`：标题栏高度和按钮宽度，默认都是 20。`titlebarTitle: false` 只隐藏标题文字。标题栏高度设为 0 会连按钮一起隐藏，这时要自己提供还原窗口的办法。
- `responsiveRect`：随窗口大小变化的位置和大小。每个字段可以是数字、百分比，或 `{ base, profiles }`。百分比相对于工作区。用户手动拖动或缩放后，这个设置会被清除，窗口固定在当前位置。

<a id="wiki-section-31"></a>
### 最小化后显示在哪里

`createWorkspace` 的 `minimizedPresentation` 选项：

- `"none"`（默认）：最小化的窗口直接消失，需要你自己提供还原按钮。
- `"taskbar"`：底部一条任务栏，高度由 `taskbarHeight` 决定（默认 28）。没有最小化窗口时也会留出这块空间。
- `"floating"`：左下角一个小面板，不占工作区空间。
- 一个函数：自己画还原面板。

要换一种方式，只需替换示例里的 `const workspace = createWorkspace(...)` 这一行，不要再加第二个 workspace：

```tsx
const workspace = createWorkspace(host, { minimizedPresentation: "floating" });
```

```tsx
const workspace = createWorkspace(host, {
    minimizedPresentation: context => {
        if (context.windows.length === 0) return null;
        return (
            <panel width={220} height={32} anchor="bottomRight" background="#202020">
                <row padding={4} gap={4} width="fill" height="fill">
                    {context.windows.map(window => (
                        <button key={window.id} id={`demo-workspace-restore:${window.id}`}
                            width="fill" height="fill"
                            onClick={() => context.restore(window.id)}>{window.title}</button>
                    ))}
                </row>
            </panel>
        );
    }
});
```

自定义函数收到 `{ windows, restore, activate }`，`windows` 是当前最小化的窗口。它必须返回一个设置了 `width`、`height`、`anchor` 的 `panel`，或者返回 `null`。面板里的按钮同样需要 `id`，而且不能和窗口里的 id 重复。

<a id="wiki-section-32"></a>
## 界面文字的翻译

客户端脚本里可以用 `I18n` 按玩家当前的语言取翻译：

```javascript
const done = I18n.translate('gui.done')
const hello = I18n.translateWithFallback('mymod.hello', 'Hello, %s', 'Alex')
```

- `I18n.translate(key, ...参数)` 返回翻译后的字符串，找不到时返回键本身。
- `I18n.translateWithFallback(key, 备用文字, ...参数)` 找不到时用备用文字。
- `I18n.has(key)` 检查翻译是否存在。
- `I18n.languageCode` 是当前语言，例如 `zh_cn`。

`I18n` 在所有平台的客户端脚本里都能用，不只是 NeoForge 26.x。

切换语言后，已经算好的字符串不会自动更新，要重新打开界面。服务端脚本要给玩家发消息时，用 `Text.translatable(...)`。

<a id="wiki-section-33"></a>
## 关闭和重载

- 按 Esc 关闭界面。不想让 Esc 关闭，就在 `<screen>` 上写 `closeOnEscape={false}`。
- 脚本里调用 `host.close()` 也可以关闭，成功时返回 `true`。
- 执行 `/nekojs reload client` 时，旧脚本打开的界面会被关闭。
- reload 失败时，当前界面保持不动，继续使用上一次成功加载的脚本。
- 只有 `client_scripts` 加载成功后，界面才会真正显示出来。

`UI.createRoot` 返回的 root 还有这些方法，一般用不到：

| 方法 | 作用 |
|---|---|
| `refresh()` | 手动重新渲染 |
| `layout()` | 读取当前布局，调试用 |
| `dispatch(id, eventName, event)` | 用代码触发某个元素的事件，例如 `root.dispatch('increment', 'click', {})` |
| `isDisposed()` | 界面是否已关闭 |

<a id="wiki-section-34"></a>
## 常见错误

| 错误码 | 原因 | 怎么办 |
|---|---|---|
| `NEKO-7001` | 用了旧脚本留下的界面对象 | reload 后重新创建 host 和 root |
| `NEKO-7003` | 不是在客户端脚本里创建 host | 在 `client_scripts/` 里调用 `ClientUI.screen` |
| `NEKO-7004` | 在其他线程里操作界面 | 在客户端回调中操作界面 |
| `NEKO-7007` | 渲染、布局或事件处理出错，包括不支持的属性 | 看 `/nekojs error` 里的具体信息 |
| `NEKO-7008` | `appearance` 写错了 | 只用上文列出的八个颜色字段 |
| `NEKO-7011` | 滑块的范围、步长或值不合法 | 检查 `min`、`max`、`step`、`value` |
| `NEKO-7012` | 对话框属性不合法 | 检查 `id` 和 `title` |
| `NEKO-7013` | 工作区或窗口的设置不合法 | 检查窗口 id 和 `add` 的设置 |
| `NEKO-7014` | 一个 host 绑定了两个 root，或区域大小不合法 | 每个 host 只绑定一个 root |
| `NEKO-6012` | 物品 id 不存在 | 检查 `<item>` 的 `item` |

渲染或布局出错时，界面会保持上一次正确的样子，错误显示在 `/nekojs error` 里。完整列表见 [错误与日志参考](error-reference_cn)。

<a id="wiki-section-35"></a>
## 嵌进别的界面

`ClientUI.region(x, y, width, height)` 创建一个嵌入用的 host。它不会打开新界面，而是由你已有的界面负责把绘制和输入转交给它。这主要给写 Java 插件或自定义 Screen 的人使用。

```tsx
import { UI } from "nekojs/jsx-runtime";

const count = UI.createSignal(0);
const region = ClientUI.region(24, 24, 180, 100);
const regionRoot = UI.createRoot(() => (
    <column width="fill" height="fill" padding={6} gap={6}>
        <label>Region clicks: {count.get()}</label>
        <button id="region-click" onClick={() => count.update(value => value + 1)}>Count</button>
    </column>
), region);
region.bindRoot(regionRoot);
```

光有这段代码，屏幕上什么也看不到。外层界面要在对应时机调用 region 的方法：

- 绘制：`render(graphics, mouseX, mouseY)`。
- 输入：`mouseClicked`、`mouseDragged`、`mouseReleased`、`mouseScrolled`、`keyPressed`、`charTyped`。坐标直接传外层界面的坐标，不用减去区域位置。每个方法返回 `true` 表示已处理，这时外层界面不要再处理同一个输入。
- 界面大小变化时调用 `setRegion(x, y, width, height)`；焦点用 `setFocused` / `isFocused`。
- 外层界面关闭时调用 `dispose()`。

<a id="wiki-section-36"></a>
## 其他做界面的方式

JSX 界面适合设置页、表单、列表这类需要点击和输入的界面。其他需求可以选别的方式：

| 需求 | 推荐方式 |
|---|---|
| 设置页、表单、列表、输入框 | JSX 界面（本页） |
| 在 HUD 上画文字、血条、图标 | `ClientEvents.hud` / `hudRender` |
| 在已有界面上叠加提示 | `ClientEvents.screenRender` |
| 在世界里画线框、路径 | `ClientEvents.worldRender` |
| 原版那样的容器界面、物品栏格子 | Java 插件 |

HUD 和世界绘制是每帧执行的绘制回调，自己负责坐标，没有布局和输入：

```javascript
ClientEvents.hud(painter => {
  painter.rect(8, 8, 120, 18, 0xB020252B)
  painter.text('任务进行中', 14, 14, 0xFFFFFFFF)
})
```

详见 [客户端绘制与后处理](client-rendering_cn)。

<a id="wiki-section-37"></a>
## 暂不支持

- Fabric、NeoForge 1.21.1 上的 JSX 界面。
- 容器、物品栏格子、背包同步。
- CSS、`className`、`style`、Grid、动画、阴影、渐变。
- React hooks。
- 自定义新的小写标签，或把原版 Widget 当作标签。
- 拖放、虚拟列表、输入法候选框、多行输入框。

<a id="wiki-section-38"></a>
## 相关页面

- [TypeScript 与 JSX](typescript-and-jsx_cn)：JSX 语法和 automatic runtime。
- [客户端绘制与后处理](client-rendering_cn)：HUD、世界绘制和后处理。
- [事件参考](event-reference_cn)：`ClientEvents`、`KeyBindEvents`。
- [平台与兼容性](platform-compatibility_cn)：各平台支持情况。

<!-- wiki-nav -->

---

[上一篇: TypeScript 与 JSX](typescript-and-jsx_cn) · [目录](Home) · [下一篇: 客户端绘制与后处理](client-rendering_cn)
