<!-- wiki-page: jsx-client-ui; locale: cn -->

> **中文** · [English](jsx-client-ui_us)

<a id="wiki-section-1"></a>
# JSX 客户端 UI

NekoJS 提供了一套用 JSX 描述 Minecraft 客户端 Screen 的 UI runtime。它不是浏览器 DOM，也不是 React 兼容层，而是把 JSX 组件树转换为受控的 Minecraft UI 节点，并负责布局、输入、状态更新和生命周期。

> **当前范围**：受管 `ClientUI` Screen Binding 只在 **NeoForge 26.x** 的 `client_scripts/` 中提供；Fabric、NeoForge 1.21.1 和独立服务器没有此 Binding。能编译 JSX/TSX 不代表具备这一原生 Binding 或 UI 兼容性。Cleanroom 暂不承诺 UI 支持。详见 [平台与兼容性](platform-compatibility_cn)。

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

小写标签必须来自这 16 个 primitive：`screen`、`panel`、`row`、`column`、`stack`、`scroll`、`label`、`button`、`input`、`slider`、`image`、`item`、`dialog`、`spacer`、`workspace`、`window`。可以用 `UI.primitives()` 查询当前 runtime 的登记清单。

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

表达式中的大写组件标签也会被降级，例如 `{items.map(item => <ItemCard key={item.id} item={item} />)}`。`UI.element(ItemCard, props, key)` 仍可作为显式工厂，但不再是必须采用的绕过方式。列表元素仍需稳定的 key。

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
| `workspace` | 一个逻辑桌面，通常由控制器管理 | 通用 logical 布局属性 |
| `window` | workspace 的直接子节点，通常由控制器管理 | 数字正常边界、`responsiveRect`、`active`、窗框能力／尺寸与受控缩放／最小化／最大化／还原请求，见下文 Workspace |

大写标签是普通 JavaScript/TypeScript 函数组件。组件可返回 VNode、文本、数字、空值、布尔或可渲染值数组；下面演示 VNode：

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

Probe 从 runtime 的具体签名生成回调声明：root／workspace render 是 `() => NekoUiRenderable`，组件是 `NekoUiComponent<Props>`；任意对象和 Promise 不能作为渲染结果，没有 return 的 `void` 也不算显式空渲染。signal updater 为 `(current: T) => T`，store 根据初始对象推导键和值类型，例如 `UI.createStore({count: 0}).update("count", current => current + 1)`；返回字符串会被类型检查拒绝。动态键可用 `UI.createStore<Record<string, unknown>>({})`，读取后需收窄 unknown。排队动作无参数，返回值被忽略；`UI.batch` 保留回调结果类型。此改进不改变运行时调度。

**受控组件与原生编辑边界**

从 `nekojs/jsx-runtime` 导入 `{ UI, Toggle, Slider, Select }`。`Toggle` 需要 `id`、`checked: boolean`、`onChange(boolean)`，可选 `label` / `text`；`Slider` 需要 `id`、`value: number`、`onChange(number)`；`Select` 需要 `id`、`value: string`、`options: readonly { value, label, disabled? }[]`、`expanded: boolean`、`onExpandedChange(boolean)`、`onChange(string)`。组件没有隐藏信号；每次 render 都应传入当前状态。选项 value 必须唯一且包含当前值；空选项只允许 `value=""`。Select 是最多六行、每行 20 像素的可滚动**内联列表**，不是悬浮原生下拉框。

展开后的 ID 必须在 root 内唯一；workspace 窗口的后代会在组件展开后按窗口加作用域，因此不同窗口可以复用局部 ID（见 workspace 节）。Toggle/Slider 使用基础 ID；Select 触发按钮使用基础 ID，并保留 `id:root`、`id:options`、`id:option:` 加 `encodeURIComponent(value)` 的派生 ID。禁用控件也会拒绝手动 `root.dispatch`，不能以 dispatch 绕过禁用状态。

原生 `input` 的 `maxLength` 按 Unicode 码点计数；Control/Super+A 全选，Shift+左/右/Home/End 扩展选区；Control 或 Super + C/X/V 可复制/剪切/粘贴。剪切先写剪贴板再删除，剪贴板失败不丢失输入。粘贴过滤 ISO 控制字符（包括 CR/LF、制表符和 DEL）及孤立代理项，保留合法补充字符。输入保持单行，长文本采用同一像素偏移完成光标跟随、鼠标命中、选择和裁剪；值或宽度缩小时会重新限制偏移。没有多行/IME composition 契约。

原生 `slider` 的 value/min/max/step 默认 0/0/100/1，要求有限值、有限跨度及跨度/步长比，`min < max`、`step > 0` 且 value 在范围内。内部位置交互按 `min + round((raw-min)/step)*step` 吸附并限幅；拖到端点或使用 Home/End 会精确到达边界，即使边界不在步长网格上。传值本身不自动吸附。左/下减一步，右/上加一步，Home/End 选端点；主键拖动捕获只属于滑块，不是通用拖放 API。**小写 primitive 的 `onChange` 收到 event，`event.value` 是字符串；大写 `Slider` 的回调直接收到 number。**

**Tabs、校验、真正 Dialog 与只读 item 契约**

- 使用具名导出 `{ Tabs, Dialog, validateForm, Validators }`，没有 `UI.Tabs`/`UI.Dialog` 别名。Tabs 接收 `id`、受控 `value`、value 唯一的 `tabs: [{ value, label, disabled?, render: () => ... }]` 和 `onChange(string)`；tabs 必须非空且包含当前 value。选中标题使用 `[Label]` 文字标记，不会强制覆盖传入的 appearance，也没有 roving-focus API；只执行/挂载选中页的 renderer，外部信号在切页后保留。默认宽高 fill，嵌入其它内容时应像示例那样明确高度。应用 workspace/window 作用域后的 root 内唯一 ID 还需预留 `id:tabs`、`id:tab:` + `encodeURIComponent(value)`、`id:content`。方向键/Home/End 导航跳过禁用页签。
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

需要按比例缩放时，向 `UI.createRoot` 的 viewport 同时提供实际逻辑宽高与成对的 `designWidth`／`designHeight`，并在每个参与缩放的原语上设置 `coordinateSpace="design"`；该属性不向子节点继承。缩放比例为 `min(contentWidth / designWidth, contentHeight / designHeight)`。数字几何、label 字体和原生按钮／输入框／滑块细节同步缩放；光标、选区、横向滚动与滑块命中使用同一设计坐标。转发原生 resize 时应保留设计宽高，因为宿主只报告逻辑宽高。`logical` 控件仍使用普通 Minecraft GUI 尺寸，不需要为缩放切换成另一套导航布局。

Java JSX 宿主自动复用有容量上限的文本排版结果，并只提交可见行范围的绘制。多行文字在字体基础坐标中推进后统一缩放，避免小数 GUI 缩放累计行距取整而使末行不可达。排版输入或字体资源变化会失效，宿主释放时清理缓存。普通脚本只需使用 `label` 和 `scroll`，不必手动给长文本分页或维护 JavaScript 文本缓存；复制操作仍可读取完整原文。

原生 `scroll` 在内容溢出时自动显示细窄覆盖式滚动条。滑块表示可见比例与当前位置，支持拖动，点击轨道移动一屏。排列方向为 row 时使用横向滚动条，其余为纵向。滚动条覆盖末端边缘，不额外占用布局空间；必要时为内容留出内边距。嵌套滚动条各自独立，遵守裁剪和模态边界；节点不可用或内容不再溢出时取消旧捕获。脚本无需增加节点或逐帧回调。

NeoForge 26.1.2／26.2.0 的内置报错面板使用随 JAR 打包的 `nekojs/builtin-ui/error-dashboard.tsx`。它通过原生 ESM 显式从 `nekojs/jsx-runtime` 导入 `UI`，使用自动 JSX 导入和固定的内存打包资源，不安装全局 UI 或 classic JSX 工厂。布局与交互由 JSX／JS 实现；Java 提供 Screen、真实错误快照、读回校验的剪贴板写入与经检查的异步源码定位。会话由运行时 root 拥有，独立于用户 CLIENT generation，随 Screen 关闭释放；内置 UI 失败时回退现有原生面板。该内部会话不是公共 Script API，也不豁免普通宿主的 generation 校验；其他节点保留当前错误 UI。

CLIENT-only `I18n.translate(key, ...args)` Binding 在五个受支持节点均可使用，包括 Fabric 和 NeoForge 1.21.1。它按当前客户端语言返回普通字符串，读取资源包翻译并沿用 Minecraft 占位符（`%s`、`%1$s`），缺失键保留原键。参数支持字符串、数值、布尔值和 null，例如按钮文字可写为 `I18n.translate("gui.done")`。切换语言或资源后重建界面时应重新调用；已存储的字符串不会自动更新。SERVER 脚本应使用 `Text.translatable(...)` 保留接收玩家的语言选择。该 Binding 不增加其他节点的 JSX 支持。

`I18n.has(key)` 检查当前翻译表（含语言回退）是否存在键，翻译恰好等于键也算存在。`I18n.translateWithFallback(key, fallback, ...args)` 优先翻译已有键，缺失时按 `Locale.ROOT` 格式化备用原文；备用原文不是另一个翻译键。参数类型同上，字面百分号写 `%%`，非法备用格式返回 `Format error: ` 加原模板。只读属性 `I18n.languageCode` 返回所选语言代码（如 `zh_cn`），`I18n.rightToLeft` 返回当前翻译表是否从右向左排列。属性每次读取当前状态，语言／资源重载结束后应重新读取；不会修改语言、自动订阅变化或自动镜像／重建界面。这些成员在普通 CLIENT 脚本和内置诊断会话中一致可用。

I18n 直接走普通 Java Binding 互操作和 Bean 属性机制，输入检查由原生实现负责。Java 可变参数可展开末尾数组；类型脚本建议使用 `I18n.translate(key, ...values)`。单个 null 格式参数仍算一个值；null key／fallback 和对象格式值被拒绝。错误参数数量、Java 参数类型及只读属性写入使用标准互操作错误，不再单独维护 I18n 成员代理。

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

profile 取安全内容区宽/高所对应档位的较小值，再按已提供的 `capabilities.maxProfile` 限制上限。2–6 档的最低宽度为 `320/480/640/854/1280`、最低高度为 `180/240/360/480/720`（GUI 单位）；`guiScale` 不是额外倍率。响应式值依次选择当前档、最近的已提供低档、`base`、调用方默认值，不向更高档取值。因此示例在 1–6 档的 padding 为 `4/4/4/4/16/24`，gap 为 `3/3/3/3/3/12`。resize 只重新布局保留内容，不重新执行 render；应通过响应式属性适配尺寸变化。

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

事件没有 DOM 的冒泡、捕获、`preventDefault()` 或 `stopPropagation()`。所有需要接收回调的节点都必须有展开后在 root 内唯一的 `id`（workspace 不同窗口的后代可复用编写时的局部 ID）：真实鼠标、键盘输入和手动 `root.dispatch(id, eventName, event)` 都通过这个 id 查找处理器。只写 `onClick` 或 `onChange` 而不写 id，不会收到脚本事件。事件对象由 runtime 冻结，处理器异常会进入 `event` 阶段诊断，不会让同一 Screen 的其它事件失效。

`button`、`input`、`scroll` 等元素会自行维护命中、焦点、禁用、滚动和文本编辑状态。旧节点被删除或 root 被关闭后，其事件回调不会继续接收输入。

**协作式区域，不是另一个 Screen**

`ClientUI.region(int x, int y, int width, int height)` 返回受管 JsxHostAdapter，不创建或导航 Screen。使用 GUI 坐标、正数且可表示的宽高；创建仍要求 NeoForge 26.x 的 CLIENT 上下文／owner thread。调用方 Screen 或非 Screen GUI owner 必须主动配合。下面构造片段**不是可独立自动打开的脚本**：实际集成必须保留 adapter 并转发帧、输入、焦点和 detach。没有自动监听器、Widget 或第三方 UI 桥。

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

转发真实的 `render(GuiGraphicsExtractor, int mouseX, int mouseY)`、`mouseClicked(double x, double y, int button, int modifiers)`、`mouseDragged(double x, double y, int button)`、`mouseReleased(double x, double y, int button)`、`mouseScrolled(double x, double y, double delta)`、`keyPressed(int key, int modifiers)`、`charTyped(int codepoint)`。传调用方 GUI 坐标，不要预先减去区域原点。仅已提交 generation 可以绘制／处理输入；绘制平移、裁剪后恢复调用方 pose/scissor。每个输入方法返回消费 boolean：仅为 false 时再交调用方控件，不得重复触发同一动作。

resize 时调用 `setRegion(int x, int y, int width, int height)`；用 `setFocused(boolean)`／`isFocused()` 配合外部焦点链。区域外点击清除键盘焦点，但仍须把捕获后的区域外 drag/release 转发进来。没有模态时边界 Tab 清除焦点并返回 false，让调用方接续。仅丢失键盘焦点不会取消指针捕获；模态保持自身边界。绘制／输入先后由调用方决定，API 不会自动遮挡任意原生控件。

区域 `open()` 可省略，绝不导航或使调用方可见。普通 `close(): boolean` 仅 literal true 才成功，失败后可重试；最终 detach／removed 使用 `dispose(): void`。二者不得关闭调用方 Screen 或较新的替换页面。resize／重复 init 应复用 adapter，不另装一条输入链。非 Screen owner 同样要提供 owner thread、真实 GUI extractor、viewport、有序输入／焦点及 cleanup；未提供自动 HUD 订阅或通用 renderer adapter。真实借用／非 Screen 集成验收仍为**未运行**。

**一个 Screen 中的三个逻辑窗口**

独立模块 `nekojs/jsx-workspace` 在一个 host 内管理一个 root；不是同时激活多个 Minecraft Screen、创建额外 OS 窗口，也不是第二套 reconciler。将下面完整内嵌示例保存为 `nekojs/client_scripts/authoring-examples/workspace.tsx`，移除其它自动打开页面的示例，再在具备 ClientUI 的 NeoForge 26.x 客户端运行 `/nekojs reload client`。业务状态在窗口 render 外创建。

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

Notes 与 Tools 的文本和计数彼此独立，首个按钮仅更新各自计数并反映在 Preview。编辑 Notes、最小化，再点击任务栏中标题为 Notes 的还原按钮；文本、计数与 keyed 内容应保留。尝试最大化 → 最小化 → 还原、边／角缩放以及 GUI profile／缩放变化。Notes 使用数字正常边界、响应式策略与 24／18 标题栏尺寸；Tools 仅隐藏标题文字。Preview 仍是只读钻石图标，不修改物品栏。任务栏为空也预留工作区。关闭 Screen、移除脚本并重载 CLIENT 会重置四个 signal，不持久化到磁盘或世界。这些是待观察操作，不代表已通过 native／GPU／真实客户端验收。

**控制器与窗口状态：** `createWorkspace(host, options?)` 创建并绑定唯一 root，不要另建／另绑第二个 root。提供 `add(id, render, config?)`、`update(id, patch)`、`activate`、`hide`、`show`、`minimize`、`maximize`、`restore`、`resize(id, rectPatch)`、`closeWindow`、`open`、`close`、`isDisposed`、`snapshot`、`layout`。配置为普通数据；ID 是稳定的非空字符串。owner thread 变更返回 boolean，跨线程可返回 `"queued"`；入队不等于完成，刷新失败保留已提交状态，render 中禁止修改控制器。

不可变 `snapshot().windows` 按后到前排列，保存 `mode: "normal" | "minimized" | "maximized"`、`restoreMode: "normal" | "maximized"`、可见性、能力与正常还原几何；`activeId` 表示活动窗口。`layout()` 才是实际发布、受约束后的几何，释放后为 null。

| 操作 | 含义 |
|---|---|
| `minimize(id)` | 要求 `minimizable: true`；保留 keyed 内容，并记住来自正常还是最大化状态 |
| `maximize(id)` | 要求 `maximizable: true`；填满工作区，不覆盖正常还原边界／响应式策略 |
| `restore(id)` | 最小化时返回记忆的模式，最大化时返回正常模式 |
| `show(id)`／`activate(id)` | 先还原最小化窗口，再提升并激活；show 还会设为可见 |
| `hide(id)` | 保留模式与内容，同时隐藏窗口和最小化展示 |
| `resize(id, rectPatch)` | 仅对启用 `resizable` 的正常窗口请求数字矩形变更，不接受 native edge 参数 |
| `closeWindow(id)` | 真正移除逻辑窗口，与最小化／隐藏不同 |

最小化／隐藏不暂停 signal 更新，也不写入磁盘。`closeWhenEmpty: true` 仅在真正移除最后一个窗口时请求普通 host close，失败不代表已释放。

**正常与响应式几何：** `rect: { x, y, width, height }` 始终是数字形式的正常／还原请求边界，默认 `{ x: 16, y: 16, width: 240, height: 160 }`，不是当前最大化矩形。正常最小宽／高为 40／20；`minWidth`、`minHeight`、`maxWidth`、`maxHeight` 必须有限且有序，不能降低上述下限。可用工作区更小时，实际边界优先；最大化填满工作区，不使用正常 min/max 限制。

`responsiveRect` 的 `x/y/width/height` 可分别使用有限数字、严格正百分比字符串，或 `{ base, profiles: { 1: ..., 6: ... } }`。宽／高数字不得为负；profile 是逻辑层级 1–6，不是 CSS 媒体查询。百分比相对于 workspace 工作区，不是物理屏幕。缺失字段回退至数字 `rect`。`update` 中提供策略会整体替换，不深合并；`responsiveRect: null` 清除策略。正常模式下手动移动／缩放会把当前显示边界实体化为数字并清除响应式策略；仅最大化不清除。

**显式启用的窗框：** `resizable`、`minimizable`、`maximizable` 默认均为 false，旧窗口仍是 20 高标题栏与关闭按钮。扁平字段 `titlebarHeight`、`titlebarButtonWidth`、`titlebarTitle` 默认 20、20、true；高度必须为整数且 ≥0，按钮宽度必须为整数且 ≥1。实际窗框受窗口边界约束。高度 0 隐藏全部标题栏控件和拖动区域，必须保留程序化还原路径；`titlebarTitle: false` 只隐藏文字。没有嵌套 titlebar 对象或 CSS 语义。

**默认窗口描边：** 每个原生窗口默认绘制一条低对比度的灰色内描边，活动窗口略亮，非活动窗口更暗。描边按 Minecraft GUI scale 换算，标称厚度保持为一个 framebuffer 像素，不随 GUI 放大而加粗。它在窗口内容之后、缩放悬停高亮之前绘制，帮助区分相邻窗口，不扩大窗口边界或缩放命中带。隐藏标题栏仍保留描边，隐藏窗口不绘制。本项默认样式不新增脚本配置。

**窗框悬停反馈：** 可缩放的正常窗口在左右边缘请求横向缩放指针，上下边缘请求纵向缩放指针。四角使用原生多方向缩放指针，并高亮对应的两条边；当前受支持 Minecraft profile 没有内置对角缩放指针类型。边框高亮绘制在窗口内容之上，缩放捕获期间保持起始方向，直至释放或取消。标题栏按钮优先于重叠的缩放带：关闭按钮悬停为红色，最小化、最大化／还原按钮为较浅背景。被遮挡、隐藏或禁用的窗口不显示悬停反馈，模态会阻止底层窗框反馈。指针请求仅作用于当前帧；协作区域只在命中自身缩放边或保留缩放捕获时请求指针，不覆盖调用方其他位置的指针选择。

原始小写 `window` 的 layout `mode` 仅接受 `normal/maximized`；控制器通过保留但隐藏内容实现自己的最小化状态。`onMinimize/onMaximize/onRestore` 只是变更请求；`onResize` 携带 `{ x, y, width, height, edge }`，edge 为 `n/ne/e/se/s/sw/w/nw`。完整示例由控制器接线；手动 dispatch 仍受模态、可见性、活动窗口与能力检查限制。

**最小化展示：** 创建时的 `minimizedPresentation` 默认 `"none"`，保留旧 workspace 根布局，需要自行提供还原操作。`"taskbar"` 使用全宽、等分按钮行；`taskbarHeight` 默认 28，必须为 ≥1 的整数，即使没有最小化窗口也预留高度。工作区获得非负剩余高度；安全视口比任务栏更矮时，任务栏保留请求高度并被裁剪，工作区可降至 0，并不保证最小尺寸覆盖这一限制。

`"floating"` 是左下角宽 220 的面板，高度 `min(160, 24 * 可见最小化窗口数 + 4)`；内部为可滚动 column，按钮高 20、间距 4、内边距 4，不预留工作区。隐藏窗口不出现在任何最小化展示中。

自定义回调接收不可变 `NekoWorkspaceMinimizedContext`：`{ windows, restore, activate }`，windows 仅含可见且最小化的快照。只可返回一个直接的 `panel` VNode（明确提供 width、height、anchor）或 null；子级可用组件，最外层不可为组件、数组或 Fragment。控制器标记面板并保留 key/children，不添加隐式铺满屏幕的包装层。可使用已有响应式样式，但大面板的边界也可能遮挡后方窗口；不要在回调执行期间产生副作用。

**替换初始化语句，不要追加第二个控制器：** 保持原 host、业务 signal 与窗口定义不变，只把示例中的 `const workspace = createWorkspace(...)` 替换为以下其中一个。不要作为额外自动打开脚本一起安装。

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

小型替换面板没有隐藏的全屏 wrapper；边界之外不属于它的输入表面。上下文不提供 native Screen 或物品栏权限。 每个可交互的原始按钮必须有显式稳定 `id`；`key` 只负责协调身份，不注册事件。展示面板在 window 作用域之外，因此这些 ID 必须在 root 内唯一。示例的 `demo-workspace-restore:` 前缀也必须避开应用自己的窗口／节点 ID。

每个控制器使用全新受管 host。重复绑定同一 guest root handle 幂等；`bindRoot` 拒绝其它 handle，layout 在发布前拒绝不匹配的 root ID（`NEKO-7014`）。这些校验不代表可以在同一 host 上创建 ID 文本相同的多个 root。最多一个 workspace，必须是 root 或根 `screen` 的直接子节点；workspace 的直接子节点只能是 window，最多一个 active。几何及其祖先必须使用 logical 坐标，不能处于 design-space。`show` 提升并激活窗口；`hide` 保留身份与 signal 更新，但阻止输入。`closeWhenEmpty: true` 只请求普通 host close，失败不代表已释放。root 发布／嵌套 render 中修改控制器会被拒绝。子内容显式 ID 在组件展开后按窗口作用域处理：不同窗口可复用局部 ID，同一窗口重复仍失败；原生窗口 ID 保持原值。手动 dispatch 必须从已发布 layout／native props 读取不透明实际 ID，不要拼接其序列化格式。

标题栏／缩放捕获、激活、活动窗口键盘焦点及窗口／展示面板遮挡由同一 host 管理；不要为每个窗口另装全局输入监听器。最小化／隐藏保留 keyed 内容和 signal 更新，不暂停后台工作或持久化数据。仍不提供吸附、布局持久化、任意 Screen／Widget 嵌入、服务端物品栏权限或未经测量的提速。真实 GPU、物理输入、焦点／遮挡及辅助功能验收仍为**未运行**。

| 待验收项目 | 通过真实客户端输入观察 | 状态 |
|---|---|---|
| 最小化与还原保留状态 | Notes 文本／计数保留，隐藏内容不接收输入；来自最大化的最小化窗口先还原为最大化 | 未运行 |
| 缩放／窗框／响应式边界 | 八个边／角、min/max 与极小工作区绘制和命中一致；手动移动／缩放清除响应式策略；零标题栏有显式还原路径 | 未运行 |
| 展示层所有权 | 任务栏预留空间，浮动／自定义面板不预留；仅面板边界遮挡合格后方窗口；模态仍优先 | 未运行 |

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
| `isUpdating()` | render、展开、layout、stage、commit 及普通清空 host 全程为 true；空闲或已释放时为 false |
| `close()` | 可重试释放；更新中或清空 host 失败返回 false，不自动退出原生 Screen |
| `forceClose()` | 仅 owner thread 的最终释放，即使清空 host 失败也释放 guest 引用；无成功 boolean |

函数身份加外层 key／无 key 位置构成组件局部子 key 的私有作用域。不同组件实例可以复用局部 key；直接同级重复 key 仍会失败，无 key fragment／数组仍透明展开。展开后的 ID 在 root 内仍唯一。保持组件函数稳定，不要每次 render 都创建新函数身份。

owner-thread 重入 refresh 返回 `"queued"` 并串行安排后续工作，不递归提交。只有稳定候选可发布，最多 32 轮后记录 render 诊断。`true` 表示至少一次提交成功，即使后续重试失败；`false` 表示没有提交。跨 owner 入队同样可返回 `"queued"`，不等于完成；render 应保持纯函数。

普通 close 在失败／忙碌后仍可重试。owner-thread resize 在忙碌时同样返回 false，不暗中排队另一个 viewport；空闲后重试。最终 force-close 立即标记释放并丢弃 render／事件／依赖引用；更新中则把原生清理延后到事务结束。排队工作不能复活它，调用必须在 owner thread。用户导航应走 host 普通 close，不把 force-close 当作日常事件操作。

root 与创建它的 CLIENT generation 绑定：

- CLIENT reload 成功后，旧 root、旧 Screen、旧事件闭包和旧 VNode 都会失效。
- candidate generation 在提交前不能影响当前运行中的 Screen。
- reload 失败时，当前 active UI 保持可用。
- 独占 Screen 的标题／暂停策略来自已提交根 `screen` props，缺省时回退工厂参数；失败候选不能改变它们，`pausesGame` 不会暂停远程服务器。
- 显式普通 host close 可重试；原生 Screen removed／替换／客户端退出强制最终清理，晚到清理不能关闭较新的 Screen。
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
- `NEKO-7013`：Workspace 编写契约拒绝无效配置／ID 或发布中修改控制器；直接调用可在任何原生日志之前抛出。
- `NEKO-7014`：GUI 区域边界无效、独占 Screen 上执行区域专用操作或违反单 root 所有权；构造／setRegion／绑定／发布均可直接拒绝。
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
