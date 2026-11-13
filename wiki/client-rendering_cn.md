<!-- wiki-page: client-rendering; locale: cn -->

> **中文** · [English](client-rendering_us)

# 客户端绘制与后处理

想在游戏画面角落常驻显示一段状态文字、给容器界面加个悬浮提示、在 3D 世界里框出某个方块坐标，或者按下快捷键开启“夜视/模糊/反色”滤镜？这些都通过 `nekojs/client_scripts/` 里的绘制与后处理 API 完成（改完执行 `/nekojs reload client` 立刻生效；专用服务端无画面，不可用）。

> **选型提示**：本页介绍的绘制都是**每帧重画（Immediate Mode）**，自己算像素或世界坐标，没有排版和焦点管理，适合**纯信息展示与视觉特效**。如果你要做带按钮、输入框、滑块的可交互面板，请用 [JSX 客户端 UI](jsx-client-ui_cn)。

## 1. 平台支持一览

| 功能模块 | NeoForge 1.21.1 | NeoForge 26.1.2 / 26.2.0 | Fabric 26.x |
|---|---|---|---|
| `ClientEvents.hud` / `screenRender`（基础 HUD 与界面叠加绘制） | 可用 | 可用 | 可用 |
| `ClientEvents.hudRender`（按 ID 分层排序的 HUD 绘制） | 可用 | 可用 | 不可用 |
| `ClientEvents.worldRender`（3D 世界线框与路径绘制） | 可用 | 可用 | 不可用 |
| `ClientEvents.postEffects` / `PostEffects`（全屏后处理滤镜） | 可用（旧版链格式） | 可用 | 不可用 |
| `KeyBindEvents`（注册自定义按键绑定） | 不可用 | 可用 | 可用 |

跨加载器移植客户端脚本前，建议先核对 [平台与兼容性](platform-compatibility_cn)。

## 2. 基础屏幕绘制（`hud` 与 `screenRender`）

全平台通用的最简写法。新建 `nekojs/client_scripts/painter.js` 并执行 `/nekojs reload client`：

```javascript
// 1. 游戏主画面 HUD：每帧在左上角画半透明底框和白字
ClientEvents.hud(painter => {
  painter.rect(8, 8, 120, 18, 0xB020252B)
  painter.text('NekoJS HUD', 14, 14, 0xFFFFFFFF)
})

// 2. 容器/菜单界面叠加：打开任意 GUI（如背包、箱子）时显示鼠标坐标
ClientEvents.screenRender(event => {
  // 可通过 event.screen 或 event.screenTitle 判断当前打开的是哪个界面
  event.painter.text(`Mouse: ${event.mouseX}, ${event.mouseY}`, 8, 8)
})
```

> 删掉脚本文件再执行一次 `/nekojs reload client`，画面上的内容就会消失。颜色统一使用 **整数 ARGB（`0xAARRGGBB`，前两位是透明度）**。

### `Painter` 画笔方法速查

`hud` 直接收到 `painter`，`screenRender` 从 `event.painter` 拿到它（单位均为界面缩放后的 GUI 像素）：

| 类别 | 方法 / 属性 | 说明 |
|---|---|---|
| **画布信息** | `width` / `height`（或 `getWidth()` / `getHeight()`） | 当前屏幕的 GUI 宽高 |
| | `partialTick`（或 `getPartialTick()`） | 当前帧在两 tick 间的插值（`0`～`1`），做平滑动画必备 |
| | `getWorldTime()` | 客户端世界总 tick 数，适合算呼吸灯/循环动画 |
| **图形与颜色** | `rect(x, y, w, h[, color])` | 填充实心矩形 |
| | `outline(x, y, w, h[, color])` | 画 1 像素宽的空心矩形边框 |
| | `gradient(x, y, w, h, topColor, bottomColor)` | 上下垂直渐变矩形 |
| | `gradientH(x, y, w, h, leftColor, rightColor)` | 左右水平渐变矩形（宽度过大时注意性能开销） |
| | `color(argb)` / `resetColor()` | 设置 / 重置默认画笔色（后续省略 `color` 参数的方法都会沿用它） |
| **文字排版** | `text(str, x, y[, color])` / `centerText(str, x, y[, color])` | 左对齐画字 / 以 `x` 为中心居中画字 |
| | `textWidth(str)` / `lineHeight()` / `wrapText(str, maxW)` | 测量字符串像素宽度、行高、按最大宽度切分换行 |
| **贴图与物品** | `item(stack, x, y)` | 绘制物品图标（如 `Item.of('minecraft:diamond')`） |
| | `textureFull(id, x, y, w, h)` | 把整张贴图缩放到 `w × h` 绘制 |
| | `texture(id, x, y, w, h)`<br>`texture(id, x, y, u, v, w, h[, texW, texH])` | 截取贴图局部区域绘制（不传贴图总宽高时默认按 `256 × 256` 计算 UV） |
| **坐标与裁剪** | `push()` / `translate(x, y)` / `pop()` | 保存矩阵、平移坐标原点、恢复矩阵（**必须成对调用**） |
| | `scissor(x, y, w, h)` / `resetScissor()` | 限制只在指定矩形内绘制（超出部分裁掉）/ 取消裁剪（**必须成对调用**） |

> ⚠️ **画笔生命周期禁忌**：`painter` 只在当帧回调执行期间有效！**严禁**把它存进 `global` 或定时器里留到下一帧调；每帧回调里逻辑也要尽量轻量，别在里面每次 `new` 大对象或读文件。

## 3. 按 ID 分层注册 HUD（`hudRender`，仅 NeoForge）

当你的整合包有多个 HUD 模块，需要精确控制谁画在原版血条前面、谁画在后面，或者方便单独替换某块 HUD 时，用带 ID 的 `ClientEvents.hudRender`：

```javascript
// nekojs/client_scripts/named-hud.js
ClientEvents.hudRender('nekojs:status', {
  layer: 'foreground', // 图层：'background'（原版HUD下）| 'normal'（默认）| 'foreground'（最上层）
  priority: 10         // 同层绘制顺序：整数，数字越小越先画（默认 0）
}, ctx => {
  ctx.rect(8, 32, 120, 18, 0xB020252B)
  ctx.text('Named HUD renderer', 12, 36, 0xFFFFFFFF)
})
```

- **简写支持**：第二个参数可以省略（`hudRender(id, cb)`），也可以直接传层名字符串或优先级整数（传错字段名、层名或非整数优先级会直接报错）。
- **回调参数**：`ctx` 拥有与 `Painter` 相同的绘制方法及 `width`、`height`、`partialTick`（第二个参数是底层原生图形上下文，跨版本签名不固定，尽量别碰）。
- **热更新与清理**：同一个 `id` 重复注册会自动覆盖旧回调；reload 时未再次注册的 `id` 会自动卸载。

## 4. 在 3D 世界里画线框（`worldRender`，仅 NeoForge）

用来在世界方块坐标上画领地边界、机器工作范围或任务引导线：

```javascript
// nekojs/client_scripts/world-lines.js
// 在世界坐标 (0, 64, 0) 到 (1, 65, 1) 画一个一格大的纯绿线框
ClientEvents.worldRender('nekojs:origin-box', {
  layer: 'normal',
  priority: 0
}, ctx => {
  ctx.box(0, 64, 0, 1, 65, 1, 0xFF00FF00)
})
```

- **绘制方法**：
  - `ctx.box(minX, minY, minZ, maxX, maxY, maxZ, color)`：一次画出立方体的 12 条棱（性能远好于自己调 12 次 `line`，画框请务必用 `box`）。
  - `ctx.line(x1, y1, z1, x2, y2, z2, color[, width])`：画单条 3D 线段。
- **坐标系与图层**：直接传**世界绝对坐标**（不用自己减相机位置；需要时可读 `ctx.cameraPos` 和 `ctx.partialTick`）。`layer` 支持 `'early'`（半透明方块后）、`'normal'`（天气渲染后，默认）、`'late'`（全场景画完后）。
  > 注意：在 26.2 上，`'late'` 层的线条可能呈现透视效果（不被墙体遮挡）；如果希望线框正常被前景方块挡住，请用默认的 `'normal'` 层。

## 5. 全屏后处理滤镜（`PostEffects`，仅 NeoForge）

后处理效果（Post-processing Shader）可以对整个游戏画面做反色、高斯模糊、苦力怕视角等滤镜处理。

### 开关与切换滤镜

最简单的用法是直接调用原版内置滤镜（下面结合 26.x 的 `KeyBindEvents` 实现按 `G` 键切换反色）：

```javascript
// nekojs/client_scripts/effect-key.js
KeyBindEvents.register('nekojs:toggle_invert', 'key.keyboard.g')

KeyBindEvents.pressed('nekojs:toggle_invert', event => {
  if (PostEffects.isAvailable('minecraft:invert')) {
    PostEffects.toggle('minecraft:invert')
  }
})
```

| `PostEffects` 成员 | 作用 |
|---|---|
| `set(id)` / `toggle(id)` | 开启指定效果 / 在开启与关闭间切换（返回 `true` 代表请求已受理，下一帧起生效） |
| `clear()` | 关闭当前正在生效的后处理滤镜 |
| `current()` / `active` | 读取当前滤镜 ID（无则为 `null`）/ 布尔值判断当前是否有滤镜开启 |
| `isAvailable(id)` | 检查当前资源包里是否存在该滤镜资源（存在才能 `set` 打开） |
| `presets()` | 返回原版自带预设列表（如 `minecraft:invert`、`spider`、`creeper`、`blur`、`entity_outline`、`transparency`） |
| `hasDefinition(id)` / `installed()` | 检查脚本是否声明过该着色器配置 / 列出所有脚本声明的 ID |

> ⚠️ **清理提醒**：一旦打开了某个滤镜，就算你把脚本删掉并 `reload client`，**已经开启的滤镜也不会自动关掉**！删脚本前请先调一次 `PostEffects.clear()`；同时千万不要在脚本文件顶层直接裸调 `PostEffects.set(...)`，请放在按键或事件回调里触发。

### 声明自定义着色器参数（`ClientEvents.postEffects`）

在 `ClientEvents.postEffects` 中可以用脚本配置自定义模糊或着色器参数（下例写法仅适用于 **NeoForge 26.x**）：

```javascript
// nekojs/client_scripts/effect-declarations.js
ClientEvents.postEffects(event => {
  event.register('nekojs:sample_blur', build => {
    build.blurRadius = 4   // 模糊半径：0～64
    build.blurRounds = 1   // 模糊轮数：1～8 的整数
  })
  // 如需移除可用 event.unregister(id)，或删掉声明后 reload
})
```

**版本差异与资源包前置条件：**

| 平台版本 | `build` 支持设置的属性 | 配套资源包 JSON 必须放置的路径 |
|---|---|---|
| **NeoForge 1.21.1** | `chainJson` 或 `program` | `assets/<命名空间>/shaders/post/<路径>.json` |
| **NeoForge 26.x** | `chainJson`；或 `fragmentShader`（可配 `fragmentShaderId`、`uniformsJson`）；或 `blurRadius`（可配 `blurRounds`）；另可附加 `fragmentShaders`、`vertexShaders` | `assets/<命名空间>/post_effect/<路径>.json` |

> **关键区分：声明了 ≠ 能直接 `set` 打开！**
> `ClientEvents.postEffects` 只负责向引擎提供着色器参数定义（`hasDefinition` 为 `true`）；要想真正用 `PostEffects.set(id)` 激活它，资源包对应路径下**还必须存在同 ID 的效果描述文件**（此时 `isAvailable` 才为 `true`），否则 `set` 会返回 `false` 并打警告日志。注意 1.21.1 与 26.x 的 `chainJson` 结构不同，不能直接混用。

## 6. 原生客户端类与启动注册提示

- 客户端脚本中还直接暴露了 `Minecraft`、`Screen`、`Window`、`KeyMapping`、`InputConstants` 等底层原版类。注意它们属于未经包装的 Minecraft 原生类，升级游戏大版本时方法名可能变动。
- 像 `ClientEvents.registerKeyMappings`、`registerMenuScreens` 这类底层客户端注册事件只在**游戏启动阶段运行一次**，普通热重载不会再次触发，修改后必须重启游戏（日常加快捷键优先用支持热重载的 `KeyBindEvents`，详见 [事件参考](event-reference_cn#wiki-keybind-events)）。
