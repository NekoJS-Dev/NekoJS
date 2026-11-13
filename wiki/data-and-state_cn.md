<!-- wiki-page: data-and-state; locale: cn -->

> **中文** · [English](data-and-state_us)

# 数据、状态与持久化

写整合包脚本经常需要“存点东西”：小到临时计数器，大到玩家的任务进度、物品的专属绑定信息或全局配置文件。不同方案的**存活时间、存档隔离性和联机同步能力**完全不同，选错会导致重启丢数据或多人服串档。

## 1. 一张表选对存储方案

| 你的需求 | 推荐方案 | 数据存在哪 / 保留多久 |
|---|---|---|
| 同一类脚本的多个文件共享临时变量 | `global` | 进程内存；reload 保留，**关游戏清空** |
| 单人游戏下跨脚本类型共享临时变量 | `shared` | 进程内存；仅限单机同一进程，**关游戏清空** |
| 跨存档通用的本地配置文件或持久化记录 | `JsonIO` | 写入 `nekojs/data/*.json` 磁盘文件，**永久保留** |
| 绑定在某个玩家或生物身上的进度/属性 | `entity.pdata()` | 随实体 NBT 一起写进**当前世界存档**，自动同步给周围客户端 |
| 绑定在某件具体物品上的自定义属性 | `stack.pdata()` | 存入物品栈的 `minecraft:custom_data` 组件，**跟着物品走** |
| 服务端把状态数值推给客户端画 HUD/UI | `ClientData` → `clientData` | 客户端内存；reload 保留，**退出世界或断连清空** |
| 客户端与服务端互相发指令/触发动作 | `Network` + `NetworkEvents` | 即时网络包，**不保存**，收到时在回调里处理 |

> 以上各对象的完整方法签名也可查阅 [全局绑定](global-bindings_cn)。

## 2. `global` 与 `shared`：纯内存临时共享

默认每个脚本文件的变量只在文件内部可见。要跨文件共享内存变量，挂在 `global` 或 `shared` 上（注意：不要往标准 JS 的 `globalThis` 上挂，不会跨文件共享）：

```javascript
// server_scripts/counter.js
global.loads = (global.loads ?? 0) + 1
shared.mode = 'cooperative'
console.info('服务端脚本已加载次数：' + global.loads)
```

- **作用域区别**：`global` 按脚本类型隔离（服务端脚本和客户端脚本各用各的 `global`）；`shared` 跨类型互通，但**仅在单人游戏（同一 JVM 进程）有效**——多人服务器和玩家客户端分别跑在两台电脑上，绝对读不到对方的 `shared`。
- **生命周期与大坑**：
  - **退出当前存档、进另一个存档时不会清空**！千万别把“某个存档独有的玩家进度”只放进 `global`，否则切档会串数据。
  - **reload 时的清理规则**：每次重载成功后，上一次脚本存进 `global` 的**复杂 JS 对象、数组和函数会被自动剔除**，只有数字、字符串、布尔值这类基础原始值会留下来。如果要跨 reload 保存结构化数据，请转成 JSON 字符串存，或在每次脚本加载时重新初始化。
  - **reload 失败时**：顶层新的 `global.xxx = ...` 赋值不会生效；但如果报错前已经原地修改了旧对象的内部属性（如 `global.box.v = 2`），该原地修改会留下。

## 3. `JsonIO`：读写本地 JSON 文件

如果你想保存重启游戏后依然存在的全局配置或统计记录，用 `JsonIO` 把数据写进 `nekojs/data/` 目录：

```javascript
// server_scripts/visits.js
const saved = JsonIO.read('example_store/visits.json')
const visits = saved === null ? 0 : JSON.parse(saved.toString()).visits

JsonIO.write('example_store/visits.json', { visits: visits + 1 })
```

- **读取 `JsonIO.read(path)`**：文件不存在时返回 `null`；存在时返回 JSON 包装值，调用 `.toString()` 再 `JSON.parse(...)` 即可拿到普通 JS 对象。
- **写入 `JsonIO.write(path, value)`**：支持普通对象、数组、字符串、数字、布尔值或 `null`（自动创建父文件夹）。写入采用**原子替换**，就算中途断电也不会留下写坏一半的残损文件。

**使用约束与避坑：**
1. **路径限制**：只能写相对于 `nekojs/data/` 的正斜杠路径（如 `mymod/settings.json`），严禁使用盘符、绝对路径、反斜杠 `\`、`.` 或 `..`。
2. **避开原版数据包保留目录名**：`nekojs/data/` 同时也是内置数据包根目录！**千万不要**把自定义文件放进 `recipe/`、`tags/`、`loot_table/` 这类原版数据包同名目录下，否则 Minecraft 会试图把它当数据包解析并报错。
3. **跨存档共用**：这里的文件跟着游戏实例走，**不按世界存档隔离**。需要区分存档时，请自己在文件名里拼上世界标识。
4. **体积上限**：嵌套最多 64 层、最多 10,000 个节点、文本总长约 100 万字符；超限、包含函数或循环引用都会直接报错。

## 4. `PData`：跟随意体与物品进存档

`PData` 是附加在实体（玩家、生物）或物品栈上的一块专属 NBT 数据，由游戏存档自动持久化。键名强烈建议带上你自己的命名空间前缀（如 `'mymod:mana'`），防止与其他脚本或模组撞名。

### 玩家与生物实体（`entity.pdata()`）

```javascript
// server_scripts/visits.js
PlayerEvents.loggedIn(event => {
  // NeoForge 用 event.entity，Fabric 用 event.player
  const player = event.entity ?? event.player
  const data = player.pdata()

  const visits = data.getInt('example:visits') + 1
  data.putInt('example:visits', visits)
  player.sendMessage('这是你第 ' + visits + ' 次进入世界！')
})
```

- **常用读写方法**：
  - 读取：`getInt`、`getLong`、`getDouble`、`getString`、`getBoolean`、`getCompound` 等。键不存在时默认返回 `0`、`''` 或 `false`；需要区分“没存过”还是“值恰好为 0”时，先调 `data.contains(key)`。
  - 写入与清理：`putInt`、`putString`、`putBoolean`、`putCompound`、`remove(key)`、`clear()`。注意 `getCompound` 拿到的是副本，改完里面内容后要 `putCompound` 写回。
  - 事务批处理：`data.edit(tag => { tag.putInt('a', 1); tag.putString('b', 'x') })`——若回调中途抛错，整批修改自动作废。
- **自动存盘与同步机制**：
  - 在服务端写入后，NekoJS 会**自动同步给周围能看到该实体的客户端**（一般无需手动调 `markDirty()` 或 `sync()`；仅当你不想等下一个 tick、要求当前帧立刻发包时才调 `sync()`）。
  - 玩家死亡重生或从末地传送门返回主世界时，身上的 PData 会**自动继承**到新玩家实体上。
  - **客户端只读**：客户端脚本里只能 `get*` 读取同步过来的副本（可能有轻微网络延迟），在客户端调用写入会直接报 `NEKO-4013`。
  - **同步体积上限**：单个实体的 PData 序列化后若超过 **32,768 个字符**，游戏存档依然能存下，但网络同步会被跳过并报 `NEKO-3010`（客户端保持旧值）。PData 只适合存轻量状态，海量数据请换 `JsonIO`。

> **旧版 Fabric 存档兼容提示**：极早期 Fabric 版本曾把实体 PData 多包了一层存进 `NekoJSPersistentData` 同名子复合标签下。新版不会强行改写旧档结构；若升级后发现读不到老存档数据，可先检查 `data.getCompound('NekoJSPersistentData')`，备份存档后再做迁移。

### 物品栈（`stack.pdata()`）

```javascript
const stack = Item.of('minecraft:paper')
stack.pdata().putString('example:owner', 'Steve')
```

- 物品 PData 底层存放在原版 `minecraft:custom_data` 数据组件中——无论物品被丢在地上、放进箱子还是交易给别的玩家，数据始终跟着这件物品走（前提是该 `ItemStack` 确实放进了世界或背包，而不只是内存里的临时变量）。
- **慎用 `clear()`**：其他模组或原版数据包也可能往 `minecraft:custom_data` 里写字段。平时删除自己的数据请用 `remove('mymod:key')`；调用 `clear()` 会把整个 `custom_data` 组件连同别人的数据一锅端掉！
- 修改物品数据请始终在**服务端脚本**进行，客户端改物品不会同步回服务端真实背包。

## 5. 联机通信：`ClientData` 与 `Network`

### `ClientData`：服务端向客户端单向推状态

最适合“服务端算好数值（如自定义法力值、副本倒计时、全局阶段），推给客户端画 HUD 或 JSX 界面”：

```javascript
// 服务端：server_scripts/status.js
PlayerEvents.loggedIn(event => {
  const player = event.entity ?? event.player
  // 推给单个玩家（若要广播给所有在线玩家，用 ClientData.sync(key, value)）
  ClientData.syncTo(player, 'example:status', { ready: true, score: 10 })
})
```

```javascript
// 客户端：client_scripts/status.js
function readStatus() {
  if (!clientData.has('example:status')) return null
  return clientData.get('example:status') // 不存在时返回 null；另有 keys() 和 size()
}
```

- 同名 key 再次推送会覆盖旧值。`key` 最长 256 字符；`value` 必须是可 JSON 序列化的数据（序列化后上限 32,768 字符，超限直接报错）。
- 客户端脚本 reload 不会丢失 `clientData`，但**玩家退出世界或断线重连时会全部清空**——所以记得在 `PlayerEvents.loggedIn` 里给刚进服的玩家补发一次初始状态。

### `Network` + `NetworkEvents`：双端收发自定义消息

当需要“客户端按下某按钮通知服务端办事”或“服务端触发客户端播放一次性特效”这类动作型通信时，按频道名（最长 64 字符）收发网络包：

```javascript
// 客户端发送：client_scripts/refresh.js
Network.sendToServer('example:refresh', { page: 1 })
```

```javascript
// 服务端接收并处理：server_scripts/refresh.js
NetworkEvents.server('example:refresh', event => {
  // event.player 为真实发包玩家，event.data 为自动转好的 NBT 数据
  ClientData.syncTo(event.player, 'example:status', { ready: true })
})
```

- **发送端**：客户端调 `Network.sendToServer(channel, data)`；服务端调 `Network.sendToPlayer(player, channel, data)` 或 `Network.sendToAll(channel, data)`（`data` 可传普通 JS 对象或 `null`）。
- **接收端**：服务端用 `NetworkEvents.server(channel, cb)`，客户端用 `NetworkEvents.client(channel, cb)`。
- ⚠️ **服务端防作弊铁律**：任何来自客户端的消息都可能被外挂伪造！服务端处理 `NetworkEvents.server` 时，**永远以 `event.player` 判定是谁发的包**（别信 `event.data` 里写的玩家名），并务必校验该玩家当前是否有权限、发包频率是否异常、数值是否越界。
