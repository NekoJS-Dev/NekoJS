<!-- wiki-page: jsx-client-ui-advanced; locale: cn -->

> **中文** · [English](jsx-client-ui-advanced_us)

<a id="wiki-section-1"></a>
# JSX 进阶控件与工作区

本页承接 [JSX 客户端 UI](jsx-client-ui_cn)，介绍 NekoJS 内置的高阶交互组件（开关、滑块、选择列表、页签、表单校验、模态弹窗）、多窗口桌面工作区（Workspace），以及面向 Java 插件开发者的界面嵌入接口。

> 以下所有示例均为客户端脚本（放在 `nekojs/client_scripts/` 下，仅支持 NeoForge 26.1.2 / 26.2.0）。测试新示例前，建议先删掉或注释掉其他会自动弹窗的示例脚本，以免多个界面抢着打开。

<a id="wiki-section-2"></a>
## 1. 常用受控组件（Toggle / Slider / Select）

从 `nekojs/jsx-runtime` 可以导入封装好的 `Toggle`（开关）、`Slider`（滑块）和 `Select`（选择列表）。

它们都是**受控组件**：组件自身不保存状态，你需要把当前的 `Signal` 值传给它，并在 `onChange` 回调里更新 `Signal`。

<a id="wiki-section-3"></a>
### 各组件属性速查

| 组件 | 必填属性 | 可选属性与说明 |
|---|---|---|
| `<Toggle>` | `id`、`checked`、`onChange(boolean)` | `label`（开关旁边的文字）、`disabled`、`appearance` |
| `<Slider>` | `id`、`value`、`onChange(number)` | `min`（默认 `0`）、`max`（默认 `100`）、`step`（默认 `1`）、`disabled`、`appearance`。<br>⚠️ 不同于小写 `<slider>` 返回字符串，大写 `<Slider>` 的 `onChange` **直接返回数字**。 |
| `<Select>` | `id`、`value`、`options`、`expanded`、`onExpandedChange(boolean)`、`onChange(string)` | `options` 格式为 `[{ value, label, disabled? }]`，`value` 不可重复且必须包含当前选中值。<br>展开后会在原位置向下展开列表（最多显示 6 行，超出可滚动），不是悬浮下拉层。 |

> **细节点提示**：
> - **输入框（`<input>`）**：仅支持单行文本，不支持输入法候选框（IME）。支持 `Ctrl+A` 全选、`Shift+方向键` 选区、`Ctrl+C/X/V` 复制剪切粘贴（macOS 为 `Cmd`），粘贴时会自动过滤换行与制表符。
> - **滑块校验**：必须满足 `min < max`、`step > 0` 且 `value` 在范围内，否则报 `NEKO-7011`。支持鼠标拖拽或聚焦后按方向键、`Home`、`End` 调节。
> - **派生 ID 占用**：高阶组件内部会生成带后缀的子元素 ID（例如 `id="quality"` 的 `Select` 会占用 `quality:root`、`quality:options` 等），给其他元素起 `id` 时请避开这种命名。

<details>
<summary>📄 点击展开：完整可运行的控件示例（<code>client_scripts/controls.tsx</code>）</summary>

保存后执行 `/nekojs reload client`，你可以测试开关联动禁用滑块、键盘微调滑块、展开选择列表以及文本框复制粘贴：

```tsx
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
          `enabled=${enabled.get()}; volume=${volume.get()}; quality=${quality.get()}`
        }</label>
      </column>
    </scroll>
  </screen>
), host);

host.bindRoot(root);
host.open();
```
</details>

<a id="wiki-section-4"></a>
## 2. 页签（Tabs）、表单校验与对话框（Dialog）

从 `nekojs/jsx-runtime` 还可以导入 `Tabs`、`Dialog`、`validateForm` 和 `Validators`，用来构建多页签配置面板与带校验的确认弹窗。

<a id="wiki-section-5"></a>
### 页签（`<Tabs>`）

```tsx
const currentTab = UI.createSignal("profile");
const tabs = [
  { value: "profile", label: "个人资料", render: () => <label>资料页内容</label> },
  { value: "preview", label: "预览", render: () => <label>预览页内容</label> },
  { value: "locked", label: "未解锁", disabled: true, render: () => <label>不可选</label> }
];

// 在 render 中使用：
<Tabs id="my-tabs" width="fill" height={190} value={currentTab.get()} tabs={tabs}
  onChange={val => currentTab.set(val)} />
```

- **按需渲染**：只有当前选中页签的 `render` 函数会执行。因此页签内的输入框状态必须放在外部 `Signal` 中，切页后再切回来才不会丢失。
- **尺寸注意**：`<Tabs>` 默认会撑满父容器；如果它下面还有别的按钮或文字，请给 `<Tabs>` 显式指定一个 `height`。
- **键盘导航**：聚焦页签栏时，可用方向键、`Home`、`End` 切换页签（自动跳过 `disabled` 项）。

<a id="wiki-section-6"></a>
### 表单校验（`validateForm`）

`validateForm(values, rules)` 用于纯数据校验（只返回结果，不会自动改界面），返回值格式为 `{ valid: boolean, errors: { [字段名]: string[] } }`：

```javascript
const result = validateForm({ name: name.get(), qty: quantity.get() }, {
  name: [
    Validators.required("名字不能为空"),
    Validators.maxLength(24, "最多 24 个字符")
  ],
  qty: [
    Validators.required("请输入数量"),
    Validators.numberRange(1, 64, "数量必须在 1 到 64 之间"),
    // 自定义规则函数：(当前值, 全部字段值) => 错误消息字符串 | null
    val => Number.isInteger(Number(val)) ? null : "数量必须是整数"
  ]
});

if (!result.valid) {
  // result.errors 里只包含未通过校验的字段
}
```

- 内置规则包括：`Validators.required(msg?)`（空白字符串视为未填，但 `0` 和 `false` 算有效值）、`Validators.minLength(n, msg?)`、`Validators.maxLength(n, msg?)`、`Validators.numberRange(min, max, msg?)`（不校验是否为整数）。不支持异步校验。

<a id="wiki-section-7"></a>
### 模态对话框（`<Dialog>`）

```tsx
<Dialog id="confirm-dialog" open={dialogOpen.get()} title="确认提交"
  onOpenChange={open => dialogOpen.set(open)}>
  <column gap={8}>
    <label>确定要应用这些设置吗？</label>
    <button id="btn-ok" onClick={() => dialogOpen.set(false)}>确定</button>
  </column>
</Dialog>
```

- **受控关闭**：玩家按 Esc 或点击遮罩层背景时，`Dialog` 会调用 `onOpenChange(false)`，你需要在这里把 `dialogOpen` 设为 `false` 才会真正关掉弹窗（可通过 `closeOnEscape={false}` 或 `closeOnBackdrop={false}` 禁用这两种关闭方式）。
- **模态隔离**：弹窗打开期间，底层界面不会收到鼠标点击、滚轮或键盘事件，`Tab` 焦点切换也会被限制在弹窗内部；此时按 Esc 只会关掉弹窗，不会关掉整个 Screen。

<details>
<summary>📄 点击展开：页签 + 表单校验 + 确认弹窗完整示例（<code>client_scripts/forms-and-dialogs.tsx</code>）</summary>

```tsx
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
</details>

<a id="wiki-section-8"></a>
## 3. 多窗口桌面工作区（Workspace）

如果你想在一个 Minecraft 界面里做出类似操作系统的“多窗口桌面”（支持拖拽标题栏移动、拖边缘缩放、最小化到任务栏、最大化、单独关闭子窗口），可以使用 `nekojs/jsx-workspace` 模块提供的 `createWorkspace`。

<a id="wiki-section-9"></a>
### 基本用法与规则

1. **接管 Root**：`createWorkspace(host, options)` 会自己创建并绑定 `root`，你**不要**再去调用 `UI.createRoot` 或 `host.bindRoot`。
2. **添加子窗口**：调用 `workspace.add(窗口ID, render函数, 窗口配置)`。
3. **ID 作用域**：不同子窗口内部的元素可以使用相同的 `id`，只要在同一个子窗口内不重复即可。
4. **打开桌面**：配置完所有窗口后，调用 `workspace.open()`。

<a id="wiki-section-10"></a>
### 窗口控制方法与配置项

`workspace` 实例提供以下窗口管理方法（⚠️ **切勿在窗口的 render 函数内部调用这些方法**）：

| 方法 | 作用 |
|---|---|
| `add(id, render, config?)` | 添加一个新子窗口 |
| `update(id, patch)` | 动态修改窗口配置，或替换其 `render` 函数 |
| `activate(id)` | 激活并置顶指定窗口（若已最小化会先自动还原） |
| `show(id)` / `hide(id)` | 显示 / 隐藏窗口（保留内部状态） |
| `minimize(id)` / `maximize(id)` / `restore(id)` | 最小化 / 最大化 / 还原窗口 |
| `resize(id, rect)` | 用代码改变窗口的位置或尺寸 `{ x, y, width, height }` |
| `closeWindow(id)` | 彻底移除并关闭该子窗口 |
| `open()` / `close()` | 打开 / 关闭整个工作区界面 |
| `snapshot()` | 读取当前所有子窗口的状态快照 |

`workspace.add(id, render, config)` 的 `config` 支持以下配置：
- **基础外观**：`title`（标题文字）、`titlebarHeight`（标题栏高度，默认 `20`；设为 `0` 会连同按钮一起隐藏）、`titlebarButtonWidth`（标题栏按钮宽度，默认 `20`）、`titlebarTitle`（布尔值，设为 `false` 仅隐藏标题文字）。
- **位置与尺寸**：`rect`（初始位置大小，默认 `{ x: 16, y: 16, width: 240, height: 160 }`）、`minWidth` / `minHeight`（最小限制，不低于 `40 × 20`）、`maxWidth` / `maxHeight`。
- **响应式初始位置**：`responsiveRect`，支持数字、相对工作区的百分比或 `{ base, profiles }`（一旦玩家手动拖动或缩放过该窗口，响应式位置自动解除，固定在玩家调整后的位置）。
- **窗口能力开关**：`resizable`（允许拖拽缩放）、`minimizable`（允许最小化）、`maximizable`（允许最大化），默认均为 `false`。

<a id="wiki-section-11"></a>
### 最小化窗口展示方式（`minimizedPresentation`）

创建工作区时，可以通过 `createWorkspace(host, { minimizedPresentation: ... })` 决定窗口最小化后去哪里：
- `"none"`（默认）：最小化后直接隐藏，需要你自己写按钮调用 `workspace.restore(id)` 还原。
- `"taskbar"`：在屏幕底部常驻一条任务栏（高度由 `taskbarHeight` 控制，默认 `28`）。
- `"floating"`：在左下角显示一个紧凑的浮动还原面板，不挤占工作区高度。
- **自定义渲染函数**：传入 `context => <panel ...>...</panel>`，自行绘制还原面板。

<details>
<summary>📄 点击展开：多窗口工作区与自定义最小化栏示例（<code>client_scripts/workspace.tsx</code>）</summary>

```tsx
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
      <button id="notes-save" height={20} onClick={() => saved.update(v => v + 1)}>
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
      <button id="tools-click" height={20} onClick={() => clicks.update(v => v + 1)}>
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
      <label id="preview-summary">{`Saved=${saved.get()}; clicks=${clicks.get()}`}</label>
      <label>Drag a titlebar; close one window without closing the desktop.</label>
    </column>
  </scroll>
), {
  title: "Preview", rect: { x: 160, y: 72, width: 190, height: 136 },
  resizable: true, minimizable: true, maximizable: true
});

workspace.open();
```

如果想自定义最小化还原栏，只需把上面的 `createWorkspace` 替换为函数形式（函数收到 `{ windows, restore, activate }`，必须返回带 `width`、`height`、`anchor` 的 `<panel>` 或 `null`）：

```tsx
const workspace = createWorkspace(host, {
  minimizedPresentation: context => {
    if (context.windows.length === 0) return null;
    return (
      <panel width={220} height={32} anchor="bottomRight" background="#202020">
        <row padding={4} gap={4} width="fill" height="fill">
          {context.windows.map(win => (
            <button key={win.id} id={`restore:${win.id}`} width="fill" height="fill"
              onClick={() => context.restore(win.id)}>{win.title}</button>
          ))}
        </row>
      </panel>
    );
  }
});
```
</details>

<a id="wiki-section-12"></a>
## 4. 嵌入已有界面（面向 Java / 自定义 Screen 开发者）

如果你在写 Java 插件或自定义原版 `Screen`，想把一块 JSX 界面作为局部区域嵌进去，可以用 `ClientUI.region(x, y, width, height)` 创建宿主：

```tsx
import { UI } from "nekojs/jsx-runtime";

const count = UI.createSignal(0);
const region = ClientUI.region(24, 24, 180, 100);
const regionRoot = UI.createRoot(() => (
  <column width="fill" height="fill" padding={6} gap={6}>
    <label>Region clicks: {count.get()}</label>
    <button id="region-click" onClick={() => count.update(v => v + 1)}>Count</button>
  </column>
), region);

region.bindRoot(regionRoot);
```

`ClientUI.region` **不会**自己打开新界面。你的外层 Screen 需要在对应生命周期里手动把渲染和输入转发给 `region`：
- **绘制**：调用 `region.render(graphics, mouseX, mouseY)`。
- **输入转发**：调用 `mouseClicked`、`mouseDragged`、`mouseReleased`、`mouseScrolled`、`keyPressed`、`charTyped`（直接传外层界面的原始坐标，不用自己减去区域偏移）。当方法返回 `true` 时代表 JSX 区域已消费该输入，外层界面不应再重复处理。
- **尺寸与焦点**：外层窗口缩放时调用 `region.setRegion(x, y, width, height)`；焦点状态通过 `setFocused` / `isFocused` 同步。
- **销毁**：外层界面关闭时务必调用 `region.dispose()` 释放资源。

<!-- wiki-nav -->

---

[上一篇: 构建系统](build-system_cn) · [目录](Home) · [下一篇: AI 查询协议与底层接口](ai-support-protocol_cn)
