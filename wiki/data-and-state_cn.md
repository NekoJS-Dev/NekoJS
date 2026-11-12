<!-- wiki-page: data-and-state; locale: cn -->

> **中文** · [English](data-and-state_us)

<a id="wiki-section-1"></a>
# 数据与状态

脚本经常需要记住一些东西：一个计数器、玩家的进度、一份设置。本页说明数据应该放在哪里，以及它能保留多久。

<a id="wiki-section-2"></a>
## 选哪一种

| 需求 | 用什么 | 能保留多久 |
|---|---|---|
| 同一类脚本的几个文件共用变量 | `global` | 到关闭游戏为止 |
| 不同类型的脚本共用变量（单人游戏） | `shared` | 到关闭游戏为止 |
| 重启游戏后还要读到的设置或记录 | `JsonIO` | 一直保留，保存在文件里 |
| 跟着某个玩家或实体的数据 | `entity.pdata()` | 跟实体一起存进存档 |
| 跟着某个物品的数据 | `stack.pdata()` | 跟物品一起存进存档 |
| 服务端把数值推给客户端显示 | `ClientData` → `clientData` | 到客户端退出世界为止 |
| 客户端和服务端互发消息 | `Network` + `NetworkEvents` | 不保存，收到时处理 |

各个 API 的完整方法列表见 [全局绑定](global-bindings_cn)。

<a id="wiki-section-3"></a>
## global 和 shared：内存里的共享数据

每个脚本文件的变量只在自己的文件里可见。要在文件之间共享，就写到 `global` 或 `shared` 上：

```javascript
// server_scripts/counter.js
global.loads = (global.loads ?? 0) + 1
shared.mode = 'cooperative'
console.info('Server scripts loaded ' + global.loads + ' times')
```

- **`global`**：同一类脚本共用。所有服务端脚本共用一个 `global`，客户端脚本有另一个，互相看不到。
- **`shared`**：所有类型的脚本共用。只在同一个游戏进程里有效：单人游戏时服务端脚本写入、客户端脚本可以读到；多人游戏时服务器和玩家客户端是不同的进程，读不到对方的 `shared`。

`global` 不是 JavaScript 的 `globalThis`。写在 `globalThis` 上的值不会共享。

<a id="wiki-section-4"></a>
### 什么时候会清空

- **reload**：保留。上面的例子每次 `/nekojs reload` 都会让 `global.loads` 加 1。
- **退出世界、进入另一个世界**：保留。所以不要把它当成“每个存档一份”的数据。
- **关闭游戏**：全部清空。需要长期保存的数据请用 `JsonIO` 或 PData。

reload 之后，**上一次加载时**存进去的 JavaScript 对象、数组和函数会被移除，只有数字、字符串和布尔值会留下来。需要跨 reload 保留复杂数据时，存成 JSON 字符串，或者每次加载时重新创建。

reload 失败时，这次加载对 `global.xxx = ...` 的赋值不会生效。但已经存在的对象如果被改了里面的内容（比如 `global.box.v = 2`），这个修改会保留。

<a id="wiki-section-5"></a>
## JsonIO：保存到文件

`JsonIO` 把 JSON 保存到 `nekojs/data/` 下的文件里，关闭游戏后仍然存在：

```javascript
// server_scripts/visits.js
const saved = JsonIO.read('example_store/visits.json')
const visits = saved === null ? 0 : JSON.parse(saved.toString()).visits
JsonIO.write('example_store/visits.json', { visits: visits + 1 })
```

- `JsonIO.read(path)`：文件不存在时返回 `null`。存在时返回一个 JSON 值，用 `toString()` 得到 JSON 文本。
- `JsonIO.write(path, value)`：value 可以是普通对象、数组、字符串、数字、布尔值或 `null`。会自动创建目录。写入是原子的：要么写完整，要么保持原样，不会留下写了一半的文件。

路径要求：

- 用 `/` 分隔的相对路径，例如 `mymod/settings.json`，从 `nekojs/data/` 开始算。
- 不能用绝对路径、盘符、反斜杠、`.` 或 `..`。

只在需要时读写。`JsonIO` 不会自动保存 `global`，也不会自动同步给客户端。

文件不分存档：所有世界读写的是同一个文件。需要按世界区分时，自己在文件名里加上区分用的名字。

`nekojs/data/` 同时也是 NekoJS 的数据包目录。不要在里面用 `recipe`、`tags`、`loot_table` 这类数据包目录名，否则游戏会把你的 JSON 当成数据包内容加载。

大小限制：嵌套最多 64 层、最多 10,000 个值，文本最多约 100 万个字符。超出限制、JSON 格式错误、函数或循环引用都会报错。

<a id="wiki-section-6"></a>
## PData：存在实体和物品上

PData 是附加在实体或物品上的一小块 NBT 数据，随存档一起保存。先调用 `pdata()`，再用带类型的方法读写。

<a id="wiki-section-7"></a>
### 实体和玩家

```javascript
// server_scripts/visits.js（NeoForge；Fabric 上把 event.entity 换成 event.player）
PlayerEvents.loggedIn(event => {
  const data = event.entity.pdata()
  const visits = data.getInt('example:visits') + 1
  data.putInt('example:visits', visits)
  event.entity.sendMessage('Visit #' + visits)
})
```

常用方法：

- 读：`getInt`、`getLong`、`getDouble`、`getString`、`getBoolean`、`getCompound` 等。键不存在时返回 0、`''` 或 `false`。需要区分“没有”和“值是 0”时，先用 `contains(key)`。
- 写：`putInt`、`putString`、`putBoolean`、`putCompound` 等，还有 `remove(key)`、`clear()`。
- 一次改多个值：`data.edit(tag => { tag.putInt('a', 1); tag.putString('b', 'x') })`。回调里出错时这批修改全部不保存。

`getCompound` 返回的是副本，改完要用 `putCompound` 写回。

数据会这样保存和同步：

- 在服务端写入后，NekoJS 自动把数据同步给能看到这个实体的玩家，你不需要做别的事。
- 数据跟实体一起存进存档，由游戏正常的保存流程写入硬盘。
- 玩家死亡重生、从末地返回时，数据会自动带到新的玩家实体上。
- **客户端只能读，不能写。** 在客户端脚本里写入会报 `NEKO-4013`。客户端读到的是服务端同步过来的副本，可能稍有延迟。

键名建议加上自己的前缀，例如 `example:visits`，避免和其他脚本冲突。

<a id="wiki-section-8"></a>
### 物品

```javascript
const stack = Item.of('minecraft:paper')
stack.pdata().putString('example:owner', 'Steve')
```

物品的 PData 存在原版的 `minecraft:custom_data` 组件里，跟着物品走：放进箱子、丢在地上、交给别人，数据都还在。物品要真正进入物品栏或世界，才会被存档保存。

这个组件也可能被其他模组或数据包使用。请用带前缀的键名；`clear()` 会删掉**整个**组件，包括别人的数据。

物品数据请在服务端修改。客户端上改物品不会影响服务端的真实物品。

<a id="wiki-section-9"></a>
## ClientData：把数值推给客户端

服务端可以把一些值推给客户端，客户端脚本用来显示 HUD 或界面。

```javascript
// server_scripts/status.js（NeoForge；Fabric 上把 event.entity 换成 event.player）
PlayerEvents.loggedIn(event => {
  ClientData.syncTo(event.entity, 'example:status', { ready: true, score: 10 })
})
```

```javascript
// client_scripts/status.js
function readStatus() {
  if (!clientData.has('example:status')) return null
  return clientData.get('example:status')
}
```

- 服务端：`ClientData.sync(key, value)` 发给所有在线玩家，`ClientData.syncTo(player, key, value)` 发给一个玩家。同一个键再发一次会覆盖旧值。
- 客户端：`clientData.get(key)`、`clientData.has(key)`、`clientData.keys()`、`clientData.size()`。键不存在时 `get` 返回 `null`。
- 值必须是 JSON 能表示的数据：对象、数组、字符串、数字、布尔值或 `null`。
- 键不能为空，最长 256 个字符。值转成 JSON 后最长 32,768 个字符，超出会直接报错。

客户端退出世界或断开连接时，这些数据全部清空；reload 不会清空。玩家每次进入服务器都需要服务端重新发送，所以通常写在登录事件里。

<a id="wiki-section-10"></a>
## Network：客户端和服务端互发消息

需要“收到消息就做某件事”时，用 `Network` 发送，用 `NetworkEvents` 接收。消息按通道名区分：

```javascript
// client_scripts/refresh.js
Network.sendToServer('example:refresh', { page: 1 })
```

```javascript
// server_scripts/refresh.js
NetworkEvents.server('example:refresh', event => {
  ClientData.syncTo(event.player, 'example:status', { ready: true })
})
```

- 发送：客户端用 `Network.sendToServer(channel, data)`；服务端用 `Network.sendToPlayer(player, channel, data)` 或 `Network.sendToAll(channel, data)`。
- 接收：服务端脚本用 `NetworkEvents.server(channel, event => { ... })`，客户端脚本用 `NetworkEvents.client(channel, event => { ... })`。
- `event.data` 是收到的 NBT 数据；在服务端，`event.player` 是发消息的玩家。
- `data` 可以是普通对象，会自动转成 NBT；传 `null` 时发送空数据。
- 通道名不能为空，最长 64 个字符。

消息不会被保存。要保存，请在处理函数里自己写入 PData 或 `JsonIO`。

服务端收到的消息来自玩家的客户端，可能被伪造。处理之前要检查：这个玩家有没有权限、发送是否过于频繁、数据格式是否正确。发送者以 `event.player` 为准，不要相信数据里写的玩家名。

<a id="wiki-section-11"></a>
## 注意事项

- **PData 太大时不同步。** 一个实体的 PData 转成文本超过 32,768 个字符时，不会同步给客户端，日志出现 `NEKO-3010`，客户端保留上一次收到的值。存档不受影响。PData 适合存少量数据，大量数据请用 `JsonIO`。
- **一般不需要 `markDirty()` 和 `sync()`。** 写入后会自动同步。`sync()` 会立刻发送一次同步，不等下一个 tick；两者都和存盘无关。
- **启动脚本的注册不能 reload。** 注册新内容需要重启游戏，见 [脚本基础](script-basics_cn)。
- **旧版 Fabric 存档。** 早期的 Fabric 版本可能把实体 PData 多套了一层，存成了 `NekoJSPersistentData` 下面的同名子项。现在的版本不会自动修改这些数据。如果升级后读不到旧数据，试试 `data.getCompound('NekoJSPersistentData')`，确认后再迁移。迁移前先备份存档。

<!-- wiki-nav -->

---

[上一篇: 全局绑定](global-bindings_cn) · [目录](Home) · [下一篇: Python 脚本](python-scripts_cn)
