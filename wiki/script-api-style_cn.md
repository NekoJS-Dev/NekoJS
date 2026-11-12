<!-- wiki-page: script-api-style; locale: cn -->

> **中文** · [English](script-api-style_us)

<a id="wiki-section-1"></a>
# 脚本 API 约定

NekoJS 的脚本 API 只有几种固定写法。认识这几种写法之后，看到新的事件或工具也能猜到怎么用。从 KubeJS 等其他项目复制来的代码，写法可能不一样，请按本页改写。

<a id="wiki-section-2"></a>
## 四种写法

| 写法 | 例子 | 用途 |
|---|---|---|
| 事件组 | `ServerEvents.recipes(event => { ... })` | 订阅一个游戏事件 |
| 绑定 | `Item.of('minecraft:stone')` | 直接调用的通用工具 |
| 配置回调 | `event.item(id, build => { ... })` | 创建新内容并设置它的属性 |
| 属性 | `event.player`、`build.maxStackSize = 16` | 读取或修改对象上的值 |

<a id="wiki-section-3"></a>
## 事件组：订阅事件

事件按用途分组，比如 `ServerEvents`、`PlayerEvents`、`ClientEvents`。在组名后面加事件名，再传入回调函数：

```javascript
// server_scripts/welcome.js
PlayerEvents.loggedIn(event => {
  event.entity.sendMessage('Welcome to NekoJS!')
})
```

回调收到的 `event` 叫事件对象，这次事件的数据和操作都从它上面取。

有些事件可以按 ID 过滤，过滤条件写在回调前面：`PlayerEvents.inventoryChanged('minecraft:diamond', event => { ... })`。

可以取消的事件，在回调里 `return true` 就会取消。没有通用的 `event.cancel()`。

同名事件在不同加载器上，事件对象的成员可能不一样。上面的登录事件在 NeoForge 上用 `event.entity`，在 Fabric 上要改成 `event.player`。每个事件在哪种脚本里能用、能不能取消、有哪些成员，查 [事件参考](event-reference_cn)。

<a id="wiki-section-4"></a>
## 绑定：通用工具

`Item`、`Text`、`JsonIO` 这类名字在脚本里直接可用，不需要 `require`，它们叫绑定。和某个事件无关的工具都放在绑定上，比如用 `Item.of(...)` 创建物品堆。

完整列表见 [全局绑定](global-bindings_cn)。

<a id="wiki-section-5"></a>
## 配置回调：创建新内容

创建新内容时，先传 ID 等必要参数，**最后**传一个配置回调。回调收到的 `build` 用来设置这个新内容的属性：

```javascript
// startup_scripts/content.js
RegistryEvents.register(event => {
  event.item('nekojs_example:ruby', build => {
    build.maxStackSize = 16
    build.rarity = 'rare'
  })
})
```

`event` 负责“创建”，`build` 负责“设置”，两者不要混用。回调结束就完成了，不需要再调用 `build()`，也不要把 `build` 存起来以后再改。

注册新物品要放在 `startup_scripts`，改完重启游戏。详见 [注册新内容](registering-new-content_cn)。

<a id="wiki-section-6"></a>
## 属性：像变量一样读写

Java 对象上的 `getXxx()` / `setXxx(value)` 在脚本里可以写成属性：

- 读：`event.player` 等同于 `event.getPlayer()`。
- 写：`build.maxStackSize = 16` 等同于 `build.setMaxStackSize(16)`。

只有 getter 的属性只能读，给它赋值会报错。需要参数的方法仍然写成方法调用，比如 `data.getInt('key')`。

不确定某个属性能不能写，或者到底叫什么名字时，执行 `/nekojs probe` 生成补全，以编辑器提示为准。不要根据 Java 类名去猜。

<a id="wiki-section-7"></a>
## 什么时候传对象

有些 API 接收一个普通对象 `{ ... }`。这些对象只是数据，不是另一种写法：

| 写法 | 例子 | 对象里是什么 |
|---|---|---|
| 配方筛选 | `event.remove({ id: 'minecraft:stick' })` | 筛选条件 |
| 物品输入 | `{ id: 'minecraft:stone', count: 2 }` | 物品 ID、数量和组件 |
| 绘制选项 | `ClientEvents.hudRender(id, { layer: 'foreground', priority: 10 }, ctx => { ... })` | 绘制层和优先级（仅 NeoForge） |
| 网络数据 | `Network.sendToServer('example:ping', { a: 1 })` | 要发送的 NBT 数据 |
| JSON 数据 | `JsonIO.write('example/save.json', { visits: 1 })` | 要保存的 JSON 内容 |

这些对象只接受文档里列出的字段。拼错的字段名通常会直接报错，不会被悄悄忽略。

绘制选项和后处理效果的完整写法见 [客户端绘制与后处理](client-rendering_cn)。

<a id="wiki-section-8"></a>
## 遇到不认识的 API

1. 执行 `/nekojs probe`，在编辑器里看补全和参数说明。
2. 查 [事件参考](event-reference_cn) 和 [全局绑定](global-bindings_cn)。
3. 用 AI 辅助时，让它查询当前游戏实例的 API，见 [AI 辅助脚本编写](ai-support_cn)。

其他项目里同名的事件或方法，不代表 NekoJS 里也有。补全里没有的成员，就当作不存在。

<!-- wiki-nav -->

---

[上一篇: 脚本基础](script-basics_cn) · [目录](Home) · [下一篇: 脚本属性](script-properties_cn)
