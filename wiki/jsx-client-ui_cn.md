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

**完整表单、页签、对话框与只读物品示例**

将下面的完整脚本保存为 `nekojs/client_scripts/authoring-examples/forms-and-dialogs.tsx`，在支持的 NeoForge 26.x 客户端运行 `/nekojs reload client`。先移除其它会打开 Screen 的示例脚本，否则它们会争用当前 Screen。代码与随附的 `examples/client/forms-and-dialogs.tsx` 完全一致；页签、表单值和待确认快照等信号均在 render 之外创建。

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

**运行与观察：**初始选中 Profile，姓名为空、数量为 `1`，错误为空，状态为 `Nothing confirmed`，背景计数为零。空姓名时点 Review，应显示错误且不打开对话框；数量 `0`、`65`、文本或 `1.5` 会被范围/整数校验拒绝。输入 `Alex` 和 `3` 后 Review，再点 Cancel 或按 Escape/点击背景关闭，状态不变；再次 Review 并 Confirm 后，状态变为 `Confirmed Alex x3`。它不修改世界、背包或网络状态。Preview 展示只读钻石和 tooltip，Locked 不可选；切回 Profile 保留外部信号中的字段值。关闭 Screen、移除脚本并 reload CLIENT 会释放 root 与回调；重新加载重置的是本示例状态，不是持久化存储。

**真实客户端待验收清单——尚未运行**

认定 UI 已验收前，应逐项记录节点、loader、GUI scale、视口、实际结果与维护者结论。内存 native/common-engine 替身不是实机证据。

| 检查 | 预期观察 | 状态 |
|---|---|---|
| 无效/有效表单 | 无效值不打开对话框；Alex/3 可取消或确认本地快照 | 未运行 |
| Tabs | 指针及方向键/Home/End 跳过 Locked；仅挂载选中页，切换后字段值保留 | 未运行 |
| 模态边界 | 背景 action/滚动、键盘及手动背景 dispatch 均被阻止；旧指针/滑块捕获取消 | 未运行 |
| 焦点与关闭 | Tab/Shift+Tab 留在顶层模态；Escape/背景遵循开关且不关闭 Screen；恢复仍可用的原焦点 | 未运行 |
| 只读物品 | 实际图标/模型/数量与原版或自定义覆盖 tooltip 可见，背包不变 | 未运行 |
| 视口与生命周期 | 小窗口、缩放和嵌套滚动不限制 portal，内容仍可达；关闭/重载/失败候选保留不留下旧回调或模态状态 | 未运行 |

维护者结论：**待定**。真实滑块/系统剪贴板检查使用前一个 controls 示例，表单示例没有滑块。实际 GPU 画面、原生焦点/朗读、tooltip、IME 与多人环境验收仍未建立。

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

**完整控件示例：输入、开关、滑块和内联选择器**

把下面的完整脚本保存为 `nekojs/client_scripts/authoring-examples/controls.tsx`，仅在支持的 NeoForge 26.x 客户端执行 `/nekojs reload client`。信号必须在 render 回调之外创建；否则重新协调可能把状态重置。此代码与随附 AI 文档中的 `examples/client/controls.tsx` 一致。

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

**观察与清理：**初始摘要为 `enabled=true; volume=40; quality=balanced`。点击 Enable volume 可禁用/启用滑块；拖动或聚焦后用方向键、Home/End 改值。展开选择器并选择 Fast 后应显示 `quality=fast`，Unavailable 不可选。输入框支持单行长文本、选择与剪贴板操作，光标移动时横向跟随。关闭 Screen，移除脚本，再 reload CLIENT；重新加载重置信号并释放旧 generation 的回调。引擎/平台替身测试不能代替真实客户端的绘制、剪贴板和焦点验收。

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

小写标签必须来自这 14 个 primitive：`screen`、`panel`、`row`、`column`、`stack`、`scroll`、`label`、`button`、`input`、`slider`、`image`、`item`、`dialog`、`spacer`。可以用 `UI.primitives()` 查询当前 runtime 的登记清单。

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
| `input` | 单行文本输入 | `value`、`placeholder`、`maxLength`、`disabled`、`appearance` |
| `slider` | 数值滑块 | `value`、`min`、`max`、`step`、`disabled`、`appearance` |
| `item` | 只读物品展示 | `item`、`count`、`itemTooltip`、`tooltip` |
| `dialog` | 模态视口 portal | `open`、`title`、`width`、`height`、`closeOnEscape`、`closeOnBackdrop`、`onDismiss` |
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

**受控组件与原生编辑边界**

从 `nekojs/jsx-runtime` 导入 `{ UI, Toggle, Slider, Select }`。`Toggle` 需要 `id`、`checked: boolean`、`onChange(boolean)`，可选 `label` / `text`；`Slider` 需要 `id`、`value: number`、`onChange(number)`；`Select` 需要 `id`、`value: string`、`options: readonly { value, label, disabled? }[]`、`expanded: boolean`、`onExpandedChange(boolean)`、`onChange(string)`。组件没有隐藏信号；每次 render 都应传入当前状态。选项 value 必须唯一且包含当前值；空选项只允许 `value=""`。Select 是最多六行、每行 20 像素的可滚动**内联列表**，不是悬浮原生下拉框。

ID 必须在 root 内唯一。Toggle/Slider 使用基础 ID；Select 触发按钮使用基础 ID，并保留 `id:root`、`id:options`、`id:option:` 加 `encodeURIComponent(value)` 的派生 ID。禁用控件也会拒绝手动 `root.dispatch`，不能以 dispatch 绕过禁用状态。

原生 `input` 的 `maxLength` 按 Unicode 码点计数；Control/Super+A 全选，Shift+左/右/Home/End 扩展选区；Control 或 Super + C/X/V 可复制/剪切/粘贴。剪切先写剪贴板再删除，剪贴板失败不丢失输入。粘贴过滤 ISO 控制字符（包括 CR/LF、制表符和 DEL）及孤立代理项，保留合法补充字符。输入保持单行，长文本采用同一像素偏移完成光标跟随、鼠标命中、选择和裁剪；值或宽度缩小时会重新限制偏移。没有多行/IME composition 契约。

原生 `slider` 的 value/min/max/step 默认 0/0/100/1，要求有限值、有限跨度及跨度/步长比，`min < max`、`step > 0` 且 value 在范围内。内部位置交互按 `min + round((raw-min)/step)*step` 吸附并限幅；拖到端点或使用 Home/End 会精确到达边界，即使边界不在步长网格上。传值本身不自动吸附。左/下减一步，右/上加一步，Home/End 选端点；主键拖动捕获只属于滑块，不是通用拖放 API。**小写 primitive 的 `onChange` 收到 event，`event.value` 是字符串；大写 `Slider` 的回调直接收到 number。**

**Tabs、校验、真正 Dialog 与只读 item 契约**

- 使用具名导出 `{ Tabs, Dialog, validateForm, Validators }`，没有 `UI.Tabs`/`UI.Dialog` 别名。Tabs 接收 `id`、受控 `value`、value 唯一的 `tabs: [{ value, label, disabled?, render: () => ... }]` 和 `onChange(string)`；tabs 必须非空且包含当前 value。选中标题使用 `[Label]` 文字标记，不会强制覆盖传入的 appearance，也没有 roving-focus API；只执行/挂载选中页的 renderer，外部信号在切页后保留。默认宽高 fill，嵌入其它内容时应像示例那样明确高度。root 内唯一 ID 还需预留 `id:tabs`、`id:tab:` + `encodeURIComponent(value)`、`id:content`。方向键/Home/End 导航跳过禁用页签。
- 显式调用 `validateForm(values, rules)` 得到深冻结的 `{ valid, errors }`；仅失败字段出现在 errors，其只读消息数组按字段/规则顺序排列。它不渲染错误、不提交、不归一化、不联系服务器。values 必须是普通 record，值为扁平 string/boolean/null/有限 number；显式 undefined 非法，但规则对应的缺失字段会以 undefined 传给规则。所有规则都会执行，一个字段可有多条消息；返回空字符串也算错误。内置规则为 `Validators.required(message?)`、`minLength(n, message?)`、`maxLength(n, message?)`、`numberRange(min, max, message?)`。required 拒绝缺失/null/空白字符串，但接受 `0`/`false`；长度规则要求字符串，按 JavaScript UTF-16 code unit 计数，与原生 input 的 maxLength 码点计数不同。numberRange 接受有限数值或经 `Number` 转换的非空白字符串，不保证整数。自定义 `(value, frozenValues) => message | null | undefined` 负责整数规则；Promise 或其它返回类型非法。不支持嵌套/异步校验。
- Dialog 接收 `id`、受控 `open`、`onOpenChange(boolean)`，可选 `title`、`children`、`width`、`height`、`closeOnEscape`、`closeOnBackdrop`。关闭时不返回节点；打开时创建覆盖安全视口的 portal 和默认 80% × 80% 居中面板，不是普通流内 panel。宽高调整的是面板；溢出内容需显式放入 scroll。预留 `id:panel`、`id:content`。两个关闭开关默认 true；关闭请求只回调 `false`，仍由调用方更新信号才能关闭。
- 按深度优先树顺序最后一个可见 dialog 为顶层模态。公共 dispatch 阻止背景 ID，包括打开后才分发的排队事件；原生指针/捕获/滚轮/键盘/文本/Tab/朗读留在其子树内。Escape 在 Screen 关闭前被消费，关闭开关为 false 时也不穿透；仅主键点击面板外才请求背景关闭。模态变化取消旧捕获并聚焦可用后代；关闭仅在原焦点仍保留、可见、启用时恢复。父滚动/裁剪不限制 portal。必须执行原生待验清单，不能用一张 panel 截图代替模态证明。
- `<item item="minecraft:diamond" count={1} itemTooltip={true} />` 接收物品资源 ID，而不是 ItemStack/组件描述符。count 为 1..99 整数，默认 1；这是显示范围，不保证物品堆叠上限。itemTooltip 默认 true，`tooltip: string` 自定义文本覆盖原版 tooltip。每次 staged native create/update 仅解析一次已知非空气物品；未知/非法 ID 拒绝候选并保留当前树。在边界内居中绘制固定 16×16 图标和数量，不是背包 Slot，也不授予修改/网络权限。仅最上层、符合命中的物品显示 tooltip，模态下的背景物品不会显示。native 物品拒绝为 `NEKO-6012`；更早的公共 layout 校验仍可能为 `NEKO-7007`。

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

**控件 appearance：**`button`、`input`、`slider` 及上述组件支持复用的 `appearance` 对象。只允许 `background`、`borderColor`、`color`、`placeholderColor`、`selectionColor`、`cursorColor`、`trackColor`、`thumbColor` 八个颜色字段；`hover`、`focused`、`pressed`、`disabled` 子对象也只能包含这些颜色字段，不能再嵌套状态。优先级为 base → hover → focused → pressed → disabled。不接受未知键、null 或响应式颜色对象；布局 props 仍单独传入。这是有限的颜色配置，不是 CSS，也不会让 input 变成别的 primitive。颜色仍使用上述 ARGB/命名色规则；整数支持有符号/无符号 32 位 ARGB。

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
- `NEKO-7008`：原生 host 校验发现控件 appearance 的字段、类型或颜色不合法。
- `NEKO-7011`：原生 host 校验发现 slider 的范围、步长或值不合法。

公共 JSX runtime 可能先在 layout 阶段拒绝不合法 props，此时可通过 `NEKO-7007` 携带 layout 诊断，而不是上述原生代码；不能保证每种错误 prop 都产生同一个原生代码。剪贴板 host 失败也会进入 `NEKO-7007`。

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
  const painter = event.painter
  painter.text(`鼠标：${event.mouseX}, ${event.mouseY}`, 8, 8)
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

这些**注册式**入口仅 NeoForge，不同于 Fabric 也具备的 Painter HUD/Screen 事件。NeoForge 26.2 的 `ctx.line` / `ctx.box` 使用独立 staged buffer 提交；源码/几何回归仍不能替代实际画面验收。layer 顺序、priority、替换清理以及后处理声明与激活的区分见 [客户端绘制与后处理](client-rendering_cn)。上面的 options map 是现有运行时契约，不是虚构的 builder 重载；JSX props 是数据，不是 NekoJS 定义 builder。见 [脚本 API 风格](script-api-style_cn)。

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
- 通用拖放、虚拟化列表、IME composition 或悬浮原生 Select popup。

现有 `ClientEvents.hud`、`hudRender`、`worldRender`、`screenRender` 和手写 Java Screen 仍按原有 API 工作，不会因为启用 JSX UI 而自动迁移。

<a id="wiki-section-32"></a>
## 相关页面

- [TypeScript 与 JSX](typescript-and-jsx_cn) —— TS 擦除、JSX lowering 和 automatic runtime。
- [事件参考](event-reference_cn) —— `ClientEvents`、`KeyBindEvents` 和客户端事件边界。
- [平台与兼容性](platform-compatibility_cn) —— NeoForge/Fabric 当前支持状态。
- [常见问题](faq_cn) —— automatic JSX 和客户端脚本排错。

<!-- wiki-nav -->

---

[上一篇: TypeScript 与 JSX](typescript-and-jsx_cn) · [目录](Home) · [下一篇: 客户端绘制与后处理](client-rendering_cn)
