<!-- wiki-page: data-and-state; locale: cn -->

> **中文** · [English](data-and-state_us)

<a id="wiki-section-1"></a>
# 数据、生命周期与权威来源

先选**谁拥有数据**，再选保存多久、怎样传输。共享内存、写入存档、发送数据包是三件不同的事。完整签名见 [global/shared](global-bindings_cn#wiki-section-41)、[JsonIO](global-bindings_cn#wiki-section-19)、[实体/玩家 PData](global-bindings_cn#wiki-section-4)、[ItemStack PData](global-bindings_cn#wiki-section-3)、[ClientData/clientData](global-bindings_cn#wiki-section-25) 和 [Network](global-bindings_cn#wiki-section-37)。

<a id="wiki-section-2"></a>
## 该选哪一种

| 需求 | 选择 | 权威来源与生命周期 |
|---|---|---|
| 同类脚本多个文件共用缓存或计数器 | `global` | 同一运行时根内按 `ScriptType` 隔离；仅内存 |
| 同一运行时内明确跨脚本类型共享 | `shared` | 只在同一运行时根/进程内共享；不是客户端与服务端通信 |
| 重启后仍需读取的小型 JSON 文件 | `JsonIO.read/write` | 本地安装的数据目录；脚本明确决定何时读写 |
| 随实体/玩家保存的数据 | `entity.pdata()` / `player.pdata()` | 服务端实体为权威；客户端只读同步镜像 |
| 随物品堆叠流转的数据 | `stack.pdata()` | 原版 `CUSTOM_DATA` 组件；遵循堆叠的保存/同步路径 |
| 界面需要服务端提供的最新值 | `ClientData.sync/syncTo` → `clientData` | 客户端临时缓存；不是存档或请求协议 |
| 请求、动作或自定义响应 | `Network` + `NetworkEvents` | 显式消息；处理器负责校验，必要时另行存储 |

<a id="wiki-section-3"></a>
## 内存不等于存档

`global` 是 NekoJS 状态容器，**不是** JavaScript 的 `globalThis`。普通脚本目录中，同类型文件共享 `global`；SERVER 与 CLIENT 各有自己的私有 `global`。`shared` 只在同一运行时根内跨类型共享。独立服务端与远程客户端拥有不同进程和运行时根。

| 边界 | 实际行为 |
|---|---|
| 整类普通重载 | 根持有的存储保留。候选环境的顶层赋值、删除和 `clear()` 仅在成功提交后发布；旧代关闭会清除它拥有的客体值 |
| 单文件重载/入口执行 | 受影响入口在活动环境中重跑；代码可能再次覆盖或增加已有值。这不是整代重载事务 |
| 同一运行时根内停服或切世界 | 传统目录的存储仍保留；不能把它当作自动按世界隔离的存档 |
| 运行时根关闭或进程重启 | 内存释放；新的独立运行时根从空状态开始 |

显式启用的脚本包，其私有 `global` 按**脚本类型与来源实例**隔离。世界/连接作用域结束会释放相应私有存储；`shared` 仍由运行时根持有。不要把传统目录的切世界行为推广到所有脚本包。

跨重载保留状态宜使用简单值。属于即将关闭的 GraalJS 代的对象/函数不是可靠的长期引用。候选隔离只覆盖受管**顶层**写入，不会回滚已有对象的深层修改、文件写入、数据包、Java 对象或世界变化。

```javascript
// server_scripts: every entry rerun increments this retained scalar.
global.exampleLoads = (global.exampleLoads ?? 0) + 1
shared.exampleMode = 'cooperative'
```

<a id="wiki-section-4"></a>
## JsonIO：显式、受约束的本地持久化

路径相对于 `<游戏目录>/nekojs/data`，**不是当前世界存档目录**。需要隔离世界或服务器时，自行设计不同文件名/键。成功写入只替换文件：不会自动保存 `global`、跟踪后续修改、同步客户端，也不提供多写入者事务。重载回滚不会撤销已经完成的文件写入。

```javascript
// An intentional one-time update, not an automatic save hook.
const saved = JsonIO.read('example/visits.json')
const visits = saved === null ? 0 : JSON.parse(saved.toString()).visits
JsonIO.write('example/visits.json', { visits: visits + 1 })
```

`read` 返回 `$JsonValue`，仅文件不存在时返回 JavaScript `null`。文件内容是 JSON `null` 时，返回值的 `toString()` 为 `"null"`。无效 JSON/UTF-8、目录和 I/O 失败会抛错；业务数据结构也应自行校验。

- 使用非空、正斜杠相对路径。绝对路径、盘符、反斜杠、空路径段、`.`/`..`、符号链接/重解析点穿越均被拒绝。
- 支持 JSON 基本值、数组、普通对象或 `$JsonValue`；拒绝函数、宿主对象、循环引用及非有限数。JSON 重复键也被拒绝。
- 限制包括深度 64、10,000 个节点，输入/字符串/输出各 1,048,576 字符；文件读取另有 3,145,728 字节上限。各项限制独立，不代表可以读写任意文件。
- 使用带缩进的 UTF-8 JSON、临时文件及原子替换。不支持原子替换时直接失败，不退化为可能留下半个文件的覆盖。

<a id="wiki-section-5"></a>
## 实体/玩家 PData 与物品 PData

<a id="wiki-section-6"></a>
### 实体/玩家：服务端 NBT 与客户端镜像

先调用方法 `pdata()`，再使用 `contains`、`getInt`、`putInt`、`putString`、`getCompound`、`putCompound` 等类型化方法。没有通用的 `put(key, object)` 或 `save()`。类型化读取在缺失时返回默认值；若“缺失”不同于零、假或空值，先用 `contains(key)`。

```javascript
// NeoForge server_scripts: the event exposes a player property.
PlayerEvents.loggedIn(event => {
  const data = event.player.pdata()
  data.putInt('example:visits', data.getInt('example:visits') + 1)
})
```

平台实体保存容器承载 `NekoJSPersistentData` 数据；玩家克隆处理会复制这部分数据。服务端写入先更新内存中的实体容器，并自动标脏等待同步；由 **Minecraft 的实体/世界保存生命周期**负责落盘，不是即时刷盘或外部备份。平台存储未安装时的后备实现仅保存在内存中。

数组、复合标签及 `copyTag()` 返回脱离原数据的副本。修改复合标签后用 `putCompound` 写回，或用 `edit(tag => { ... })` 在一次复制中批量修改。编辑回调抛错时，该批次不会保存。

- 脏队列每个服务端刻最多处理 **256 个实体**，其余延后。`markDirty()` 只入队；`sync()` 请求立即快照，不占该队列预算（非服务端线程调用会调度到服务端）。两者都不是落盘方法。
- 快照发送给跟踪实体的玩家，以及适用时的实体自身。开始跟踪会发送快照；停止跟踪/实体移除会发送清空快照。版本号防止旧包恢复过期镜像。
- 超过 **32,768 个 Java `String.length()` 字符的 SNBT** 快照会跳过并记录 `NEKO-3010`，不会截断。这是同步限制，不是存档大小限制。此时脏队列项已移除，不会自动重试；客户端可能仍保留上一次有效值。
- 客户端实体/玩家 `pdata()` 只读：写入、`edit`、`markDirty`、`sync` 均抛出 `NEKO-4013`。断线/世界切换会清除镜像，普通脚本重载不会。镜像可能缺失或延迟，不能作为权威依据。

**Fabric 保存格式与兼容性：**实体 mixin 现在保留完整 `NeoForgeData` 容器，桥接层只选择一次 `NekoJSPersistentData`，与 NeoForge 的规范格式一致；其他容器字段保留。回归覆盖真实桥接层、脚本读写、保存/加载与同步服务，但不替代真实世界重启或多人联机验收。

旧 Fabric 可能已写出 `NeoForgeData.NekoJSPersistentData.NekoJSPersistentData.<用户键>` 双层数据。修复后不会猜测、扁平化或删除它，脚本看到的仍是嵌套 compound，而不是自动提升后的键；这可能要求旧脚本调整读取路径。仅凭同名 compound 无法区分历史错误包装与合法用户数据。先备份并人工确认具体实体/玩家存档，再有意迁移或临时按嵌套路径读取；不要无条件批量拆包。

<a id="wiki-section-7"></a>
### ItemStack：原版组件归属

```javascript
const stack = Item.of('minecraft:paper')
stack.pdata().putString('example:owner', 'guide')
```

这里编辑的是堆叠的**整个**原版 `CUSTOM_DATA` 组件，不是实体的 `NekoJSPersistentData` 子项。请使用自己的命名空间键；`clear()` 会删除整个组件，包括其他自定义数据。物品必须实际进入可保存的物品栏/世界路径，才能跨重启保留。

它不走实体 PData 数据包，也没有客户端只读保护；继承的 `markDirty()`/`sync()` 钩子为空操作。但原版堆叠/组件同步仍可能携带数据。客户端修改不等于权威的服务端物品栏修改，创建本地堆叠也不会自动分发。

<a id="wiki-section-8"></a>
## ClientData 是缓存，Network 是协议

服务端 `ClientData.sync(key, value)` 广播给已连接玩家；`syncTo(player, key, value)` 发送给指定玩家。同键重推会覆盖客户端缓存。值必须兼容 JSON，不能是任意 Java/客体对象。键必须非空白且不超过 **256 个 Java 字符**；发送端检查序列化 JSON 不超过 **32,768 个 `String.length()` 字符**，**不是 32 KB**。传输编解码器另有限制，仅通过字符检查不保证一定送达。

```javascript
// server_scripts: a targeted UI snapshot, not a durable save.
PlayerEvents.loggedIn(event => {
  ClientData.syncTo(event.player, 'example:status', { ready: true })
})
```

```javascript
// client_scripts: JSON null and a missing key both read as null.
if (clientData.has('example:status')) {
  console.log(clientData.get('example:status'))
}
console.log(clientData.keys(), clientData.size())
```

`get(key)`、`has(key)`、`keys()`、`size()` 都是方法。`has` 区分“键缺失”和“明确推送 JSON `null`”；`keys()` 返回无序快照。收包直接更新缓存，不依赖脚本监听器。缓存跨普通脚本重载保留，但客户端卸载世界/断线时清空；重连或新玩家需要服务端明确重发。发送一次并不等于为未来玩家注册自动重放。

需要“收到消息就执行动作”时，使用 `Network.sendToServer`、`sendToPlayer`、`sendToAll` 配合 `NetworkEvents.server/client`。负载为 `CompoundTag`；通道名必须非空白且最多 64 字符。这些消息不会自动保存、同步 PData 或填充 `clientData`。

```javascript
// client_scripts: null becomes an empty CompoundTag.
Network.sendToServer('example:refresh', null)
```

```javascript
// server_scripts: publish server-selected data to the actual sender.
NetworkEvents.server('example:refresh', event => {
  ClientData.syncTo(event.player, 'example:status', { ready: true })
})
```

实际请求必须校验权限、频率、负载结构与当前服务端状态；发送者取自 `event.player`，不能信任客户端自报身份。用作 JSON/NBT/网络**负载**的普通对象是合法数据，不是在用对象替代事件组或具名绑定。

<a id="wiki-section-9"></a>
## 脚本类型与生命周期限制

绑定安装会筛选脚本可见接口，但“能访问”不等于“有权修改”：实体 PData 按实体的逻辑侧决定权限；ClientData 发送与 Network 方向要求相应服务端/客户端上下文。在正确脚本目录订阅对应事件组，不要在玩家/世界尚不存在的顶层访问它们。STARTUP 注册不是普通可重载状态；注册变更需要重启。参见[重载命令与边界](commands_cn)。

<a id="wiki-section-10"></a>
## 源码依据与验证边界

以下结论来自实现与测试源码核对，不代表已完成游戏内实测。

- [内存归属与脚本包作用域](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/core/state/GlobalStateStores.java#L8-L92)、[客体值清理与候选写入](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/core/state/GlobalStore.java#L14-L29)、[生命周期/客体值测试](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/test/java/com/tkisor/nekojs/core/state/Ticket10GlobalStateTest.java#L548-L680)、[单文件重跑](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java#L1432-L1502)。
- [JSON 路径与文件操作](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/core/api/json/JsonFileStore.java#L49-L245)、[值大小限制](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/api/data/JsonValue.java#L30-L38)、[文件失败路径测试](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/test/java/com/tkisor/nekojs/core/api/json/JsonFileStoreTest.java#L28-L132)。
- [实体权限](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/api/inject/EntityExtension.java#L67-L76)、[类型化/副本/只读 API](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/pdata/PersistentDataJS.java#L49-L164)、[队列与快照限制](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/pdata/PDataSyncService.java#L27-L175)、[边界测试](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/test/java/com/tkisor/nekojs/wrapper/pdata/PDataSnapshotBehaviorTest.java#L44-L54)。
- [NeoForge 保存容器桥接](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/NekoJSMod.java#L116-L133)；已修正的 Fabric 容器与调用链依据：[桥接层](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java#L98-L109)、[保存混入](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/fabric/java/com/tkisor/nekojs/fabric/mixin/NekoEntityPDataMixin.java#L53-L67)、[保存形状夹具](https://github.com/NekoJS-Dev/NekoJS/blob/master/versions/26.1.2-fabric/src/test/java/com/tkisor/nekojs/fabric/mixin/NekoEntityPDataSaveShapeTest.java#L99-L131)、[真实桥接层与同步服务回归](https://github.com/NekoJS-Dev/NekoJS/blob/master/versions/26.1.2-fabric/src/test/java/com/tkisor/nekojs/fabric/FabricPDataPersistenceTest.java#L71-L195)。这些夹具不能证明混入已在实机应用、世界存档持久化或多人网络送达。
- [物品组件包装](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/item/PersistentDataJS.java#L9-L22)、[同堆叠重读/组件清空测试](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/test/java/com/tkisor/nekojs/wrapper/pdata/ItemStackPersistentDataTest.java#L16-L27)。
- [客户端读取方法](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/bindings/static_access/ClientDataJS.java#L29-L56)、[键校验](https://github.com/NekoJS-Dev/NekoJS/blob/master/common/src/main/java/com/tkisor/nekojs/wrapper/clientdata/ClientDataStore.java#L35-L77)、[序列化字符限制](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/clientdata/ClientDataSyncJS.java#L24-L56)、[客户端卸载清理](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/client/NekoJSClient.java#L97-L102)。
- [Network 方向](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/network/NetworkJS.java#L22-L46)、[通道校验](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/network/NekoScriptPayload.java#L13-L24)、[发送者属性](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/wrapper/network/NetworkDataEventJS.java#L29-L39)、[PData 扩展暴露范围](https://github.com/NekoJS-Dev/NekoJS/blob/master/src/main/java/com/tkisor/nekojs/api/NeoForgeCatalogPlatformProvider.java#L53-L64)。

<!-- wiki-nav -->

---

[上一篇: 全局绑定](global-bindings_cn) · [目录](Home) · [下一篇: Python 脚本](python-scripts_cn)
