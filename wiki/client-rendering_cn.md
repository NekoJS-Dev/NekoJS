<!-- wiki-page: client-rendering; locale: cn -->

> **中文** · [English](client-rendering_us)

<a id="wiki-section-1"></a>
# 客户端绘制与后处理

本页讲怎么用客户端脚本在屏幕上画东西：HUD 文字、界面上的提示、世界里的线框，以及反色、模糊这类画面后处理效果。

这些脚本都放在 `nekojs/client_scripts/`，改完执行 `/nekojs reload client` 生效。专用服务器上没有画面，这些功能都不可用。

本页的绘制是“每帧重画”的方式，适合显示信息。需要按钮、输入框这类可交互界面时，请看 [JSX 客户端 UI](jsx-client-ui_cn)。

<a id="wiki-section-2"></a>
## 各平台能用什么

| 功能 | NeoForge 1.21.1 | NeoForge 26.1.2 | NeoForge 26.2.0 | Fabric 26.x |
|---|---|---|---|---|
| `ClientEvents.hud` / `screenRender`：在 HUD 和界面上绘制 | 可用 | 可用 | 可用 | 可用 |
| `ClientEvents.hudRender`：按 id 注册 HUD 绘制 | 可用 | 可用 | 可用 | 不可用 |
| `ClientEvents.worldRender`：在世界里画线框 | 可用 | 可用 | 可用 | 不可用 |
| `ClientEvents.postEffects` / `PostEffects`：后处理效果 | 可用（旧版格式） | 可用 | 可用 | 不可用 |
| `KeyBindEvents`：自定义按键 | 不可用 | 可用 | 可用 | 可用 |

跨加载器复制示例之前，先看 [平台与兼容性](platform-compatibility_cn)。

<a id="wiki-section-3"></a>
## 在 HUD 和界面上绘制

新建 `nekojs/client_scripts/painter.js`：

```javascript
// 每帧在 HUD 左上角画一个半透明底框和一行字
ClientEvents.hud(painter => {
  painter.rect(8, 8, 120, 18, 0xB020252B)
  painter.text('NekoJS HUD', 14, 14, 0xFFFFFFFF)
})

// 打开任意界面（例如背包）时，在左上角显示鼠标坐标
ClientEvents.screenRender(event => {
  event.painter.text(`Mouse: ${event.mouseX}, ${event.mouseY}`, 8, 8)
})
```

执行 `/nekojs reload client`。回到游戏能看到 HUD 上的文字；打开背包能看到鼠标坐标。想去掉它们，删除这个文件再 reload 一次。

颜色写成 `0xAARRGGBB`，前两位是透明度。`screenRender` 的事件对象还有 `event.screen` 和 `event.screenTitle`，可以用来判断当前是哪个界面。

<a id="wiki-section-4"></a>
### Painter 能画什么

| 方法 | 作用 |
|---|---|
| `text(text, x, y[, color])` / `centerText(...)` | 画文字 / 以 x 为中心画文字 |
| `rect(x, y, w, h[, color])` / `outline(...)` | 填充矩形 / 矩形边框 |
| `gradient(x, y, w, h, top, bottom)` / `gradientH(...)` | 上下渐变 / 左右渐变 |
| `item(stack, x, y)` | 画物品图标 |
| `texture(id, x, y, w, h, ...)` / `textureFull(...)` | 画纹理的一部分 / 整张纹理 |
| `push()` / `translate(x, y)` / `pop()` | 保存、平移、恢复坐标 |
| `scissor(x, y, w, h)` / `resetScissor()` | 只在指定区域内绘制 / 取消限制 |
| `textWidth(text)` / `wrapText(text, maxWidth)` / `lineHeight()` | 测量文字、自动换行 |

另外可以读取 `painter.width`、`painter.height`（屏幕尺寸）和 `painter.partialTick`。

> `painter` 只在当前这一次回调里有效。不要把它存进 `global` 或定时器里留到之后用。

<a id="wiki-section-5"></a>
## 按 id 注册 HUD 绘制（NeoForge）

`ClientEvents.hudRender` 给每块 HUD 起一个 id，并能控制它画在原版 HUD 的前面还是后面：

```javascript
// nekojs/client_scripts/named-hud.js
ClientEvents.hudRender('nekojs:status', {
  layer: 'foreground',
  priority: 10
}, ctx => {
  ctx.rect(8, 32, 120, 18, 0xB020252B)
  ctx.text('Named HUD renderer', 12, 36, 0xFFFFFFFF)
})
```

reload 后，这行字出现在上一个示例的下方。

- **写法**：`hudRender(id, callback)` 或 `hudRender(id, options, callback)`。`options` 可以是 `{ layer, priority }`，也可以只写一个层名字符串或一个优先级数字。
- **层**：`background` 画在原版 HUD 之前（会被原版 HUD 盖住），`normal` 和 `foreground` 依次画在原版 HUD 之后。默认 `normal`。
- **优先级**：同一层里数字小的先画。默认 `0`，必须是整数。
- **回调参数**：`ctx` 提供和 Painter 类似的方法（`text`、`rect`、`outline`、`gradient`、`texture`、`item`、`scissor` 等），以及 `ctx.width`、`ctx.height`、`ctx.partialTick`。第二个参数是原生的图形对象，各版本不同，尽量不用。
- **更新和删除**：同一个 id 再注册一次会替换旧的。reload 之后，这次没有再注册的 id 会自动消失。所以删掉脚本再 reload 就能去掉它。

`options` 里写了不认识的字段、不存在的层名，或者优先级不是整数，注册会直接报错。

<a id="wiki-section-6"></a>
## 在世界里画线框（NeoForge）

```javascript
// nekojs/client_scripts/world-lines.js
// 在世界坐标 (0, 64, 0) 处画一个一格大小的绿色方框
ClientEvents.worldRender('nekojs:origin-box', {
  layer: 'normal',
  priority: 0
}, ctx => {
  ctx.box(0, 64, 0, 1, 65, 1, 0xFF00FF00)
})
```

reload 后走到 `(0, 64, 0)` 附近，能看到一个方框。它只是画出来的线，不会放置方块。

- `ctx.box(minX, minY, minZ, maxX, maxY, maxZ, color)` 画一个方框的 12 条边。
- `ctx.line(x1, y1, z1, x2, y2, z2, color[, width])` 画一条线。
- 坐标都是世界坐标，不需要自己减去相机位置。`ctx.cameraPos` 和 `ctx.partialTick` 可以读取当前帧的信息。
- 层有三个：`early`（半透明方块之后）、`normal`（天气之后，默认）、`late`（整个世界画完之后）。同一层里优先级小的先画。
- 写法、优先级和 id 的规则与 `hudRender` 相同。

画框时用一次 `box`，不要用 12 次 `line`。每次调用都有固定开销，每帧调用次数越少越好。

> 在 26.2 上，`late` 层画的线可能不会被方块挡住。需要线框被墙遮住时，用 `normal` 层。

<a id="wiki-section-7"></a>
## 后处理效果（NeoForge）

后处理效果会改变整个画面，比如反色、模糊、苦力怕视角。

<a id="wiki-section-8"></a>
### 打开和关闭效果

最简单的用法是切换原版自带的效果。下面的例子按 G 键切换反色（`KeyBindEvents` 在 NeoForge 1.21.1 上没有，需要 26.x）：

```javascript
// nekojs/client_scripts/effect-key.js
KeyBindEvents.register('nekojs:toggle_invert', 'key.keyboard.g')

KeyBindEvents.pressed('nekojs:toggle_invert', event => {
  if (PostEffects.isAvailable('minecraft:invert')) {
    PostEffects.toggle('minecraft:invert')
  }
})
```

reload 后按 G，画面反色；再按一次恢复。玩家可以在按键设置里改成别的键。

`PostEffects` 的常用方法：

| 方法 | 作用 |
|---|---|
| `set(id)` | 打开一个效果 |
| `toggle(id)` | 没开就打开，已经开着就关闭 |
| `clear()` | 关闭当前效果 |
| `current()` | 当前效果的 id，没有时为 `null` |
| `isAvailable(id)` | 资源包里有没有这个效果，有才能打开 |
| `presets()` | 原版自带的效果：`minecraft:invert`、`spider`、`creeper`、`blur`、`entity_outline`、`transparency` |

另外 `PostEffects.active` 表示现在是否有效果在生效。

`set` 和 `toggle` 返回 `true` 只表示请求已接受，画面会在之后的帧里变化。

> 打开的效果在删除脚本、reload 之后**不会**自动关闭。移除这类脚本前，先执行一次 `PostEffects.clear()`。也不要在脚本顶层直接调用 `set`，放在按键或其他事件里调用。

<a id="wiki-section-9"></a>
### 声明自己的效果

想用自己的着色器参数，在 `ClientEvents.postEffects` 里声明。下面的写法**只适用于 26.x**：

```javascript
// nekojs/client_scripts/effect-declarations.js
ClientEvents.postEffects(event => {
  event.register('nekojs:sample_blur', build => {
    build.blurRadius = 4   // 0 到 64
    build.blurRounds = 1   // 1 到 8 的整数
  })
})
```

执行 `/nekojs reload client`。可以用 `PostEffects.hasDefinition('nekojs:sample_blur')` 检查声明是否生效，`PostEffects.installed()` 列出所有已声明的 id。删除声明后再 reload，它就会被移除；也可以在事件里调用 `event.unregister(id)`。

不同版本支持的配置不一样：

| 版本 | 可以设置的属性 | 效果资源文件放在 |
|---|---|---|
| NeoForge 1.21.1 | `chainJson` 或 `program` | `assets/<命名空间>/shaders/post/<路径>.json` |
| NeoForge 26.x | `chainJson`；或 `fragmentShader`（可配 `fragmentShaderId`、`uniformsJson`）；或 `blurRadius`（可配 `blurRounds`）；另外可以提供 `fragmentShaders`、`vertexShaders` | `assets/<命名空间>/post_effect/<路径>.json` |

**声明不等于能打开。** 声明只提供着色器内容。要用 `set` 打开一个效果，资源包里还必须有同 id 的效果文件（路径见上表）。只有声明、没有资源文件时，`set` 返回 `false` 并在日志里警告。所以 `hasDefinition`（有没有声明）和 `isAvailable`（能不能打开）是两回事。

两个版本的 chain JSON 格式不同，不能互相照抄。

属性名写错、类型不对或者数值超出范围，这次 reload 会报错，之前的声明继续有效。

<a id="wiki-section-10"></a>
## 原生客户端类

客户端脚本还可以直接使用 `Minecraft`、`Screen`、`Window`、`KeyMapping`、`InputConstants` 这几个原生类。它们是 Minecraft 本身的类，不同版本的方法可能不一样，换版本时需要自己检查。

`registerKeyMappings`、`registerMenuScreens` 这类注册事件只在游戏启动时触发一次，之后 reload 不会再触发。改了这类脚本需要重启游戏。详见 [事件参考](event-reference_cn)。

<!-- wiki-nav -->

---

[上一篇: JSX 客户端 UI](jsx-client-ui_cn) · [目录](Home) · [下一篇: MixinJS（实验性）](mixinjs_cn)
