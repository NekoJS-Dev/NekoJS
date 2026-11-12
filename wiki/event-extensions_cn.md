<!-- wiki-page: event-extensions; locale: cn -->

> **中文** · [English](event-extensions_us)

<a id="wiki-section-1"></a>
# 事件扩展

本页讲怎样添加新的脚本事件，分两种情况：

- **插件作者**：用 Java 声明一个事件组，让脚本像使用 `ServerEvents` 一样监听你的事件。
- **脚本作者**：不写 Java，在启动脚本里用 `ScriptEvents` 声明事件组，由脚本自己触发。

事件系统的整体结构见 [项目架构](project-architecture_cn#wiki-event-system)。

<a id="wiki-section-2"></a>
## 插件事件组：最小示例

下面四步做出一个服务端事件 `MyEvents.playerGreet`。

**1. 事件对象。** 脚本回调收到的 `event` 就是这个对象。它可以是普通类或 record，不需要继承任何基类：

```java
package com.example.myaddon;

import net.minecraft.server.level.ServerPlayer;

public class PlayerGreetEvent {
    private final ServerPlayer player;
    private final String greeting;

    public PlayerGreetEvent(ServerPlayer player, String greeting) {
        this.player = player;
        this.greeting = greeting;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getGreeting() { return greeting; }
}
```

两个 getter 会成为只读属性 `event.player` 和 `event.greeting`。

**2. 事件组。** 用 `EventGroup.of` 创建组，再用工厂方法声明事件：

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;

public interface MyEvents {
    EventGroup GROUP = EventGroup.of("MyEvents");

    EventBusJS<PlayerGreetEvent, Void> PLAYER_GREET =
        GROUP.server("playerGreet", PlayerGreetEvent.class);
}
```

**3. 注册。** 在插件的 `registerEvents` 里注册事件组：

```java
@Override
public void registerEvents(EventGroupRegistry registry) {
    registry.register(MyEvents.GROUP);
}
```

**4. 触发。** 在合适的时机调用 `post`，比如你的模组处理某个原版事件时：

```java
MyEvents.PLAYER_GREET.post(new PlayerGreetEvent(player, "你好"));
```

脚本这样监听：

```javascript
// server_scripts/greet.js
MyEvents.playerGreet(event => {
  console.info(`${event.player.getName().getString()} 收到问候：${event.greeting}`)
})
```

用 interface 常量放事件组只是约定。`EventGroup` 是普通对象，放在类的静态字段里也一样。interface 的好处是字段自动是 `public static final`，类加载时初始化一次。

<a id="wiki-section-3"></a>
## 声明事件

<a id="wiki-section-4"></a>
### 工厂方法

| 方法 | 脚本侧 | 说明 |
|---|---|---|
| `GROUP.server(name, eventClass)` | 服务端 | 普通事件 |
| `GROUP.client(name, eventClass)` | 客户端 | 普通事件 |
| `GROUP.startup(name, eventClass)` | 启动 | 普通事件 |
| `GROUP.server(name, eventClass, dispatchKey)` | 服务端 | 按 key 分发，`client`/`startup` 同理 |
| `GROUP.add(name, scriptType, bus)` | 自己指定 | 传入自己创建的总线，比如强制可取消 |

工厂方法都是 `add` 的简写。`add` 会做两项检查：

- 同一个组里事件名重复，抛 `IllegalArgumentException`。
- 启动完成后事件组会被冻结，之后再 `add` 抛 `IllegalStateException`。

**组名重复不是错误。** `EventGroupRegistry` 按组名合并，两个插件用同一个组名，事件会并进同一个组。这也是给别人的事件组追加事件的正规做法，只有事件名相同才会报错。

<a id="wiki-section-5"></a>
### 按 key 分发

分发事件让脚本只监听某个 key，例如某种物品或某个 ID：

```java
EventBusJS<PlayerGreetEvent, Identifier> CUSTOM =
    GROUP.server("custom", PlayerGreetEvent.class, DispatchKey.of(Identifier.class));

// 触发时带上 key
MyEvents.CUSTOM.post(new PlayerGreetEvent(player, "Hello"), someIdentifier);
```

```javascript
MyEvents.custom('mymod:some_key', event => {
  // 只在 key 为 mymod:some_key 时触发
})
```

`DispatchKey` 有三个工厂：`of(keyType)`、`of(keyType, event -> key)`（从事件对象算出 key）和 `string()`。脚本传入的 key 会按 `keyType` 转换，所以适配器在这里同样生效。

key 的类型确定后不要轻易改。改了之后，所有已有脚本的第一个参数都会对不上。

本页的 Java 片段使用 26.x 的类名。在 1.21.1 上，把 `Identifier` 换成 `ResourceLocation`。

<a id="wiki-section-6"></a>
### 可取消事件

`post` 返回事件是否被取消。只有可取消的总线才会返回 `true`；不可取消的总线永远返回 `false`，脚本里 `return true` 也没用。

工厂方法按事件类判断能否取消：

- **NeoForge**：事件类实现了 `ICancellableEvent` 才可取消。
- **Fabric**：工厂方法创建的总线都不可取消。

所以上面的 `PlayerGreetEvent` 不可取消。想让自己的事件可取消，用 `add` 自己创建总线，**替换**原来的声明（不要再加一个同名事件）：

```java
EventBusJS<PlayerGreetEvent, Void> PLAYER_GREET =
    MyEvents.GROUP.add("playerGreet", ScriptType.SERVER, EventBusJS.of(PlayerGreetEvent.class, true));

boolean cancelled = PLAYER_GREET.post(new PlayerGreetEvent(player, "你好"));
```

带 key 又要可取消时，用 `EventBusJS.of(eventClass, true, dispatchKey)`。

<a id="wiki-section-7"></a>
### 客户端事件和逻辑侧

只在客户端存在的事件组，用 `registerClientEvents(EventGroupRegistry)` 注册，专用服务器不会加载它。

`.server(...)` 和 `.client(...)` 只决定**哪种脚本能访问**这个事件，不会帮你过滤逻辑侧或切换线程。单人游戏里，很多原生事件在客户端和服务端各触发一次。投递到服务端事件之前，要先过滤掉客户端那一次，否则会在渲染线程进入服务端脚本，报 GraalJS 多线程访问错误。NeoForge 的 `bind(bus, predicate)` 就是做这件事的，见下文。

脚本访问规则：

- 服务端事件在服务端、启动、测试脚本里都能访问；客户端事件在客户端和启动脚本里能访问。
- 在不允许的脚本里访问，报 `Event 'X.y' not available in CLIENT` 这类错误。
- 事件名不存在，报 `No such event bus: X.y`。

<a id="wiki-section-8"></a>
## 桥接原生事件

<a id="wiki-section-9"></a>
### NeoForge：EventBusForgeBridge

`EventBusForgeBridge` 把 NeoForge 的 `IEventBus` 事件接到 NekoJS 总线上。绑定后桥会自动 `post`，不需要你再手动触发：

```java
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

EventBusForgeBridge.create(NeoForge.EVENT_BUS)
    .bindTransformed(MyEvents.PLAYER_GREET,
        event -> new PlayerGreetEvent((ServerPlayer) event.getEntity(), "Hello"),
        PlayerEvent.PlayerLoggedInEvent.class);
```

| 方法 | 用途 |
|---|---|
| `bind(bus)` / `bind(bus, priority, receiveCancelled)` | 事件对象本身就是 NeoForge `Event`，直接投递 |
| `bind(bus, filter)` / `bind(bus, filter, priority, receiveCancelled)` | 只投递通过 `filter` 的事件，常用来过滤逻辑侧 |
| `bindTransformed(bus, transformer, eventClass)` | 先把原生事件转换成你的事件对象，再投递 |
| `bindCancellable(bus, cancelAction)` | 原生事件没有 `ICancellableEvent`，但有自己的“取消”方式（比如把伤害设为 0） |

总线可取消、原生事件也实现了 `ICancellableEvent` 时，脚本取消会自动写回原生事件。`bindCancellable` 要求总线可取消，否则绑定时直接报错。

内置事件组的写法可以参考 `ServerEvents.java` 末尾的 `FORGE_BRIDGE`。

<a id="wiki-section-10"></a>
### Fabric：FabricEventBusBridge

Fabric 的事件是回调接口，没有事件类可以按名字挂载。`FabricEventBusBridge` 让你在注册回调时把载荷交给 NekoJS 总线：

```java
FabricEventBusBridge.create().bind(
    CommandEvents.REGISTER,
    listener -> CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
        listener.accept(new CommandRegistryEventJS(dispatcher, registryAccess))));
```

| 方法 | 用途 |
|---|---|
| `bind(bus, register)` | 回调载荷就是脚本事件对象 |
| `bindTransformed(bus, register, transform)` | 先转换再投递；没有脚本监听时跳过转换 |
| `bindCancellable(bus, register)` | 把脚本的取消结果返回给 Fabric 回调 |
| `bindDispatched` / `bindTransformedDispatched` / `bindCancellableDispatched` | 上面三种的按 key 分发版本 |

两个加载器上尽量保持同一个事件组、事件名、载荷和取消规则。真实的回调签名不同，注册代码要分别写。Fabric 没有对应 API 时，用 Mixin 实现，或者明确说明这个事件在 Fabric 上不可用，不要假装支持。

<a id="wiki-section-11"></a>
## 发布到 API 模块

事件组默认作为全局名字提供。你也可以把同一个事件发布到 `nekojs/api/version/<模组ID>` 或 `nekojs/api/stable/<模组ID>` 模块，脚本用 `import` 引入（见 [模块系统](module-system_cn#wiki-api-modules)）：

```java
EventBusJS<PlayerGreetEvent, Void> PLAYER_GREET = GROUP.server(
        "playerGreet", PlayerGreetEvent.class,
        publication -> publication.versioned().scriptTypes(ScriptType.SERVER));
```

仍然用 `registerEvents` 注册 `GROUP`。如果模组 ID 是 `myaddon`，服务端脚本可以从 `nekojs/api/version/myaddon` 导入 `MyEvents`。

- 发布复用原来的事件，不会多一个总线或多一份原生监听。原来的全局写法仍然可用。脚本同时用两种写法订阅，会注册两个监听器。
- `publication.stable()` 只能发布通过稳定公开类型校验的载荷。需要转换时用 `stable(View.class, converter)`，但可取消事件不能用这种转换。
- 也可以对整个组调用 `GROUP.expose(...)`。stable 组必须用 `events(...)` 明确列出要发布的事件。
- 事件名不存在、重复发布、视图不合法时，报 `NEKO-4057`。

<a id="wiki-section-12"></a>
## ScriptEvents：在脚本里定义事件

脚本作者不写 Java，也能声明自己的事件组。在启动脚本里声明，然后在服务端或客户端脚本里监听和触发：

```javascript
// startup_scripts/register_events.js
ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))
ScriptEvents.client(event => event.register('MyClientEvents', 'hudRefresh'))

// 也可以写成对象形式
// ScriptEvents.server(event => event.register({ group: 'MyEvents', name: 'bossKilled' }))
```

```javascript
// server_scripts/boss.js
MyEvents.bossKilled(payload => {
  console.info(`击杀了 ${payload.boss}，掉落 ${payload.loot}`)
})

// 在任意服务端脚本里触发
MyEvents.bossKilled.post({ boss: 'ender_dragon', loot: 'dragon_egg' })
```

```javascript
// client_scripts/hud.js
MyClientEvents.hudRefresh(payload => console.info(payload.reason))
MyClientEvents.hudRefresh.post({ reason: 'manual' })
```

- `ScriptEvents.server` 声明的事件在服务端脚本里用，`ScriptEvents.client` 声明的在客户端脚本里用。
- `post` 传入的值原样交给监听器，可以是任意 JS 值。这些事件不能取消，`post` 没有返回值。
- 声明是启动脚本的一部分，改了要重启游戏。`/nekojs reload` 只会更新监听器。
- 声明的事件会出现在 `/nekojs probe` 生成的类型里。
- NeoForge 和 Fabric 都支持。

以下情况会报错：

- 组名或事件名不是合法的 JS 标识符（必须匹配 `[A-Za-z_$][A-Za-z0-9_$]*`）。
- 组名和内置事件组或任何一种脚本里的内置绑定同名，报 `conflicts with built-in ...`。
- 同一种脚本里同一个组名加事件名已经声明过，报 `Script event already registered`。

如果插件已经注册了一个叫 `MyEvents` 的事件组，脚本就不能再用这个组名。

<a id="wiki-section-13"></a>
### 从旧写法迁移

旧版 `register` 的第三个参数可以是 NeoForge 事件类，用来直接挂原生事件。这种写法没法在 Fabric 上实现，已经移除：

| 旧写法 | 现在的写法 |
|---|---|
| `event.register('G', 'n', 'net.neoforged...Event$Post')` | `NativeEvents.onEvent('net.neoforged...Event$Post', e => {})`（仅 NeoForge） |
| `event.register({ group, name, event, priority, receiveCancelled })` | `NativeEvents.onEvent(priority, receiveCancelled, eventClass, handler)`（仅 NeoForge） |
| 需要命名事件组并跨加载器 | `event.register('G', 'n')`，在合适的时机自己调用 `G.n.post(payload)` |

<a id="wiki-section-14"></a>
## 设计建议

- **事件对象尽量简单。** 普通类或 record 反射出来的脚本成员最干净。可以用 `@Remap`、`@HideFromJS` 调整成员，用 `@Doc` 给 probe 生成说明，见 [注解体系](annotations_cn)。
- **构造代价高时先检查监听器。** 在热点路径上先调用 `EventBusJS.hasListeners()`，没有脚本监听就不要创建事件对象。
- **监听器的异常不会传到你的代码。** `post` 会捕获并记录监听器里的异常，然后返回 `false`。

<a id="wiki-section-15"></a>
## 下一步

- [插件开发](plugin-development_cn)：`registerEvents` 和其他插件钩子。
- [注解体系](annotations_cn)：控制事件对象在脚本里显示哪些成员。
- [事件参考](event-reference_cn)：内置事件组列表。

<!-- wiki-nav -->

---

[上一篇: 类型适配器](type-adapters_cn) · [目录](Home) · [下一篇: 注解体系](annotations_cn)
