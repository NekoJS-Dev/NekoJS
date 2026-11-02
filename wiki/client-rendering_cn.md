<!-- wiki-page: client-rendering; locale: cn -->

> **中文** · [English](client-rendering_us)

<a id="wiki-section-1"></a>
# 客户端绘制与后处理

脚本放在 `nekojs/client_scripts/`。客户端回调在客户端 owner thread 执行，不在服务端线程执行；独立服务器不能绘制 HUD 或打开 Screen。需要保留式交互界面时使用 [JSX 客户端 UI](jsx-client-ui_cn)，本文的绘制回调则是立即模式。

<a id="wiki-section-2"></a>
## 先选择实际平台入口

| 入口 | NeoForge 1.21.1 | NeoForge 26.1.2 | NeoForge 26.2.0 | Fabric 26.x |
|---|---|---|---|---|
| `ClientEvents.hud` / `screenRender` Painter | 有 | 有 | 有 | 有 |
| `ClientEvents.hudRender(id, options, callback)` | 有 | 有 | 有 | 未移植 |
| `ClientEvents.worldRender(id, options, callback)` | 有 | 有 | 有，使用 staged 提交 | 未移植 |
| `ClientEvents.postEffects` / `PostEffects` | 有，旧版 chain 格式 | 有，现代 chain 格式 | 有，现代 chain 格式 | 未移植 |
| `ClientUI` 保留式 JSX Screen | 无 | 有 | 有 | 无 |

上表描述源码支持面，不代表已完成画面验收。26.2 世界线框使用独立 staged buffer 提交，不再依赖已移除的 immediate buffer source。跨加载器复制示例前请阅读 [平台与兼容性](platform-compatibility_cn)。

<a id="wiki-section-3"></a>
## 在 HUD 或当前 Screen 上绘制

以下入口适用于当前 NeoForge 和 Fabric 节点：

```javascript
// nekojs/client_scripts/painter.js
ClientEvents.hud(painter => {
  painter.rect(8, 8, 120, 18, 0xB020252B)
  painter.text("NekoJS HUD", 14, 14, 0xFFFFFFFF)
})

ClientEvents.screenRender(event => {
  event.painter.text(`Mouse: ${event.mouseX}, ${event.mouseY}`, 8, 8)
})
```

在客户端执行 `/nekojs reload client`，进入游戏观察 HUD 文字，再打开一个 Screen 观察鼠标坐标。删除文件后再次 reload CLIENT 即可移除监听器。`painter`、`event.painter` 和原生图形对象仅供当前回调使用，不应放入 timer、global 或后续帧复用。

Painter 提供文字、矩形/边框、渐变、物品图标、纹理、变换和裁剪，但不会创建按钮、焦点、输入框、布局或联网容器菜单。绘制回调应保持轻量，避免阻塞 I/O 和修改服务端/世界状态。

<a id="wiki-section-4"></a>
## 按 id 注册 HUD renderer（NeoForge）

```javascript
// nekojs/client_scripts/named-hud.js
ClientEvents.hudRender("nekojs:status", {
  layer: "foreground",
  priority: 10
}, ctx => {
  ctx.rect(8, 32, 120, 18, 0xB020252B)
  ctx.text("Named HUD renderer", 12, 36, 0xFFFFFFFF)
})
```

支持 `(id, callback)` 和 `(id, options, callback)`；options 可为 `{ layer, priority }`、层名字符串或优先级数字。默认为 `normal`、priority `0`。这里的对象是显式调度数据，**不是** builder 回调，也不是事件 payload。对象只接受可选的 `layer` 与 `priority`；未知字段、错误层名/类型会拒绝注册。priority 必须是有限的 32 位有符号整数（`-2147483648` 至 `2147483647`），小数或越界值不再静默截断。Probe 从同一运行时 schema 输出字段和层名字面量；TypeScript 的 `number` 不能单独证明整数与范围合法。

- HUD 的 `background` 在原版 HUD 前绘制；`normal`、`foreground` 依次在其后绘制。
- 同层内 priority 越小越先绘制；相同优先级不承诺稳定顺序。
- 回调参数为 `(ctx, graphics)`。优先使用 `ctx` 的版本无关 helper；原生 graphics 在 1.21.1 与 26.x 不同。只读 Bean 属性包括 `ctx.width`、`ctx.height`、`ctx.partialTick`；绘制仍调用 `ctx.text(...)`、`rect(...)`、`texture(...)`、`item(...)` 等方法。
- 同代重复注册同一 id 会覆盖它。完整 CLIENT reload 成功后整批替换 renderer，未再声明的 id 退役；候选失败时旧 active 批次继续服务。

加载后观察 Painter 示例下方的文字。清理时删除文件并执行 `/nekojs reload client`。当前没有已发布的脚本 renderer builder 或按 id 删除 Binding；请通过完整批次 reload 表达期望集合。

<a id="wiki-section-5"></a>
## 世界空间线框（NeoForge）

```javascript
// nekojs/client_scripts/world-lines.js
ClientEvents.worldRender("nekojs:origin-box", {
  layer: "late",
  priority: 0
}, ctx => {
  ctx.box(0, 64, 0, 1, 65, 1, 0xFF00FF00)
})
```

在测试世界执行 `/nekojs reload client`，到世界坐标 `(0, 64, 0)` 观察一格大小的边框；不会放置方块。1.21.1、26.1.2 和 26.2.0 的 NeoForge 入口均提交线框；26.2 一次 `box` 将 12 条边合并为一次独立 staged 提交。每次 helper 调用拥有独立临时 stage，GPU 分配至少按 262,144 字节取整；应控制每帧调用数，不要用 12 次 `line` 代替一次 `box`。源码/几何回归不等于实际 GPU 画面验收。删除文件并 reload CLIENT 即可退役。

世界层 `early` 在半透明方块后，`normal` 在天气后，`late` 在 level render 收尾时执行。默认 `normal`，同层 priority 升序。26.2 原版可能在 `AfterLevel` 前清空主深度缓冲，因此 `late` 不保证保留原始世界遮挡；依赖遮挡的线框应优先使用较早的 `normal`，并在实际节点做视觉验证。`ctx.cameraPos`、`ctx.partialTick` 描述当前帧。`line(...)`、`box(...)` 接收世界坐标并在内部减去相机位置；不会注册实体、修改世界方块或提供任意 GPU pipeline。

<a id="wiki-section-6"></a>
## 声明后处理与激活后处理分开（NeoForge）

声明事件只收集 inert 候选计划。例如以下配置**仅适用于 26.x**：

```javascript
// nekojs/client_scripts/effect-declarations.js
ClientEvents.postEffects(event => {
  event.register("nekojs:sample_blur", build => {
    build.blurRadius = 4
    build.blurRounds = 1
  })
})
```

必要参数在前、配置回调在最后。builder 通过公开属性设置，回调结束后封闭，不能保留后继续修改；不需要额外调用 `build()`。兼容旧对象/Map 重载，但它也遵守节点专属字段和严格校验；拼错字段或写入错误类型会失败，不再忽略。26.x 的 `blurRadius` 必须为有限数值 `[0, 64]`，`blurRounds` 必须为整数 `[1, 8]`，不再钳位非法值。

用 `/nekojs reload client` 加载。`register` 返回 true 只表示收集声明，不表示 shader 已显示，也不表示整个候选已提交。非法声明导致整批 preflight 失败，被拒候选保留旧声明。可用 `PostEffects.hasDefinition(id)`、`installed()`、`activeGeneration()` 查询已提交集合。删除文件并 reload CLIENT 可退役声明；`event.unregister(id)` 同样只在 commit 时声明退役。旧的 `PostEffects.register/unregister` 运行时入口已不存在。

| 版本 | 接受的声明配置 | 资源支持的激活路径 |
|---|---|---|
| NeoForge 1.21.1 | `chainJson` 或 `program` | `assets/<namespace>/shaders/post/<path>.json` |
| NeoForge 26.x | `chainJson`，或 `fragmentShader` 搭配可选 `fragmentShaderId` / `uniformsJson`，或 `blurRadius` 搭配可选 `blurRounds`；另可提供 `fragmentShaders` / `vertexShaders` 表 | `assets/<namespace>/post_effect/<path>.json` |

不同 Minecraft 版本的 chain JSON 格式不同。声明本身不会挂载对应的可激活资源，只有声明而没有资源的 id 会被 `set` 拒绝。需要提供正确的资源包 chain，或选取已有的原版/资源包效果。`PostEffects.hasDefinition(id)` 与 `PostEffects.isAvailable(id)` 不是同一个判断。

另一个 **NeoForge 26.x 运行时**示例通过 G 切换已有资源（1.21.1 没有 `KeyBindEvents`；Fabric 有自己的按键实现，但没有 `PostEffects`）：

```javascript
// nekojs/client_scripts/effect-key.js
KeyBindEvents.register("nekojs:toggle_invert", "key.keyboard.g")
KeyBindEvents.pressed("nekojs:toggle_invert", event => {
  if (PostEffects.isAvailable("minecraft:invert")) {
    PostEffects.toggle("minecraft:invert")
  }
})
```

CLIENT reload 后在游戏中按 G（或玩家重绑的按键）；效果是否可用由活动资源决定。再次按键清除同一效果。`PostEffects.clear()` 显式清除当前效果；`current()` 返回所选 id，只读 `active` Bean 属性报告应用状态。`set` / `toggle` 返回 true 表示接受操作，不是 GPU 编译或画面完成证明。运行时 `set`、`clear`、`toggle` 是即时客户端动作，不属于 reload 声明事务；避免在候选顶层求值中调用。移除按键脚本前先清除画面效果，删除监听器不会撤销它之前执行过的运行时动作。

<a id="wiki-section-7"></a>
## 客户端 Binding 与当前 API 风格

CLIENT 提供 `Minecraft`、`Screen`、`Window`、`KeyMapping`、`InputConstants` 类 Binding。这是高级原生访问入口，不是跨版本 wrapper 承诺；`ClientUI` 只覆盖上表的 NeoForge 26.x。NeoForge/Fabric 26.x 的 `KeyBindEvents.register(id, key[, category])` 是位置参数注册入口，`pressed`、`released`、`tick` 是事件订阅，可在回调前提供 id selector。`registerMenuScreens`、`registerKeyMappings` 等原生注册事件有特定生命周期，晚期 reload 不会自动重放加载器启动阶段。详见 [事件参考](event-reference_cn)。

可配置后处理定义现在使用实际存在的 `event.register(id, build => ...)`；兼容对象重载仍保留并校验。renderer options 则是显式调度数据，继续使用 `{ layer, priority }`，不应凭空改成 builder。JSX props/VNode、Painter 数据和 shader JSON 也保留各自数据契约。动作使用已发布的方法；暴露 getter 的只读数据使用 Bean 属性。

<!-- wiki-nav -->

---

[上一篇: JSX 客户端 UI](jsx-client-ui_cn) · [目录](Home) · [下一篇: Node.js 兼容](nodejs-compatibility_cn)
