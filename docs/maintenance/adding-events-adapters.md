# 新增事件与 Adapter：维护者实操指南

这份指南面向需要改 NekoJS Java 侧和提供脚本示例的维护者。目标是从需求一路走到脚本可用、声明可见、测试守住、平台差异有记录。

示例分两类：

- `可直接改写的示例`：给出完整结构和真实仓库 API 名称；业务类名可替换。
- `平台模板`：原生 Minecraft/Loader 事件随版本变化，示例说明接线位置；必须换成目标版本真实存在的事件，不要复制虚构的原生类名。

本指南不是对所有节点能力的承诺。每个节点的支持状态要看实际事件、Adapter、版本源码和测试结果。

## 先判断改动类型

| 需求 | 该改什么 | 不要做什么 |
|---|---|---|
| 脚本订阅一个已有平台事件 | 事件组成员 + 平台投递接线 + payload/contract/test | 新造万能事件管理器 |
| 需要一个新脚本事件组 | `EventGroup`、插件注册、受影响平台 wiring、catalog/declaration/gate | 只定义 `EventGroup` 就认为已注册 |
| JS 字符串/对象要自动转换为 Java 类型 | `JSTypeAdapter` + `registerAdapters` + 输入形状声明 + tests | 在每个调用点手写重复解析 |
| 共享脚本事件要接不同 Loader API | loader Adapter/wiring + 中立 payload + capability 记录 | 把 loader/Minecraft 类型塞进 `common` |
| 第三方插件要扩展 NekoJS 的收集型能力 | 先使用现有 Point/Contributor；只有没有合适 Point 才提新 Point | 再造 static registry、全局 Map 或第二 bootstrap |

先确认目标 Script Type：`STARTUP`、`SERVER` 或 `CLIENT`。事件在哪个线程触发、是否可取消、是否按 key 定向分发、何时可安全访问 server/player/world，都属于契约，不是实现细节。

## 新增事件

### 典型改动路径

给现有 `ServerEvents` 增一个普通 NeoForge 事件时，通常需要：

1. 决定脚本可见 payload。跨 Loader 时优先用中立 wrapper，不把 NeoForge 原生事件直接暴露给脚本。
2. 在 `ServerEvents` 声明 lower-camel-case bus。
3. NeoForge 用 `EventBusForgeBridge` 绑定原生事件，或在现有 domain owner 的合法生命周期点显式 `post`。
4. Fabric 在对应 `Fabric*EventBindings` 注册 Callback/Mixin 接线；若没有等价 API，记录 unavailable/not verified，不伪装成功。
5. 确保组在 bootstrap 注册前完成所有成员定义；新建组才需要 `registry.register(NEW_GROUP)`。
6. 检查 catalog、Probe/TypeScript 声明、platform gate、事件面 ownership tests 和节点测试。
7. 写 JS 示例并验证运行时名称、成员和回调参数都真实存在。

仓库的共享概念/API 是 `EventGroup.of(...)`、`group.server/client/startup(...)` 和 `EventGroupRegistry#register(...)`。NeoForge 接线见 `EventBusForgeBridge`；Fabric 通常在 `src/fabric/java/.../Fabric*EventBindings.java`。不要把这些事实源复制成第二份表。

### 示例：增加 `ServerEvents.machineCrafted`

假设某个已有平台回调能提供玩家、机器 ID 和配方 ID。公开给脚本的 payload 用中立数据，不传平台事件、Screen、Level 或其它原生对象。

中立 payload（共享 Minecraft 源码，不放 common）：

```java
package com.tkisor.nekojs.wrapper.event.server;

import net.minecraft.server.level.ServerPlayer;

public final class MachineCraftedEventJS {
    private final ServerPlayer player;
    private final String machineId;
    private final String recipeId;
    private final int outputCount;

    public MachineCraftedEventJS(ServerPlayer player, String machineId, String recipeId, int outputCount) {
        this.player = player;
        this.machineId = machineId;
        this.recipeId = recipeId;
        this.outputCount = outputCount;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getMachineId() { return machineId; }
    public String getRecipeId() { return recipeId; }
    public int getOutputCount() { return outputCount; }
}
```

注意：这只是 server-side 例子；如果要 Fabric/跨平台一致，`ServerPlayer` 这种原生对象是否适合公开必须先按现有 payload 契约核实。更可移植的 payload 是 UUID、玩家名、ID 字符串和数字等中立值。

给现有组增加成员：

```java
// src/main/java/com/tkisor/nekojs/bindings/event/ServerEvents.java
EventBusJS<MachineCraftedEventJS, Void> MACHINE_CRAFTED =
        GROUP.server("machineCrafted", MachineCraftedEventJS.class);
```

`ServerEvents.GROUP` 已在核心插件 `registerEvents` 中注册，因此给这个既有组加成员，不需要重复注册组。

NeoForge wiring 模板（把 `NativeMachineCraftedEvent` 换成真实事件类）：

```java
// ServerEvents.java 的 FORGE_BRIDGE 初始化链
EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
        .bind(TICK_PRE)
        .bind(TICK_POST)
        .bindTransformed(
                MACHINE_CRAFTED,
                event -> new MachineCraftedEventJS(
                        event.getPlayer(),
                        event.getMachineId().toString(),
                        event.getRecipeId().toString(),
                        event.getOutputCount()),
                NativeMachineCraftedEvent.class);
```

实际 `bindTransformed` 重载顺序以当前源码为准。若转换需要检查 side、记录状态或只在某个 owner commit 点发布，不要硬塞进 lambda；建立与现有业务域一致的平台 owner，并在正确生命周期点调用 `MACHINE_CRAFTED.post(payload)`。

Fabric wiring 模板：

```java
// src/fabric/java/.../FabricMachineEventBindings.java
public final class FabricMachineEventBindings {
    private FabricMachineEventBindings() {}

    public static void register() {
        SomeFabricCallback.EVENT.register((player, machine, recipe, output) -> {
            MachineCraftedEventJS payload = new MachineCraftedEventJS(
                    player,
                    machine.getId().toString(),
                    recipe.getId().toString(),
                    output.getCount());
            ServerEvents.MACHINE_CRAFTED.post(payload);
        });
    }
}
```

这里的 `SomeFabricCallback` 是占位符，必须由目标 Fabric API 的真实 callback 替换。如果没有 callback，检查项目是否已用 mixin/domain owner 覆盖该语义；没有实现就明确记录 capability，而不是留一个永远不触发的 bus。

JS 侧调用：

```js
ServerEvents.machineCrafted(event => {
  console.info(
    `[machines] ${event.player.name} crafted ${event.outputCount}x ` +
    `${event.recipeId} at ${event.machineId}`
  );
});
```

这里假定 wrapper 确实暴露 `player.name`。若最终选择只传中立字符串，应相应改成 `event.playerName`，并同步 TS/Python declaration 与示例；不可只凭 Java getter 猜 JS property 行为。

### 新建事件组时多做一步

如果不是给既有 `ServerEvents` 增成员，而是新建 `MachineEvents`：

```java
public interface MachineEvents {
    EventGroup GROUP = EventGroup.of("MachineEvents");

    EventBusJS<MachineCraftedEventJS, Void> CRAFTED =
            GROUP.server("crafted", MachineCraftedEventJS.class);
}
```

然后在相应 `NekoJSPlugin#registerEvents(EventGroupRegistry)` 里：

```java
@Override
public void registerEvents(EventGroupRegistry registry) {
    registry.register(MachineEvents.GROUP);
}
```

如果只需内置组，放在现有核心插件的注册路径；如果是独立功能插件，就按自动发现插件形态注册。CLIENT 组走 `registerClientEvents`。Fabric 有自己的 `FabricCorePlugin`/bindings 装配，别假设 NeoForge 核心插件在 Fabric 节点运行。

### 可取消、dispatch key 和时序

- 普通 `GROUP.server(name, type)` 是不可取消、非 dispatch 的总线。
- 需要 selector/过滤时才声明 `DispatchKey`，并让 bus payload 提供稳定 key；调用方将 selector 放在 callback 前。
- 可取消事件要确认原生取消机制和 NekoJS bus 同时可取消。原生事件没有 `ICancellableEvent` 但有等价操作时，使用 `bindCancellable` 这类明确桥接；不要让脚本 `return true` 静默无效。
- CLIENT 逻辑不要由 integrated server 的 render thread 调 server Context；服务端双逻辑侧事件应过滤 `!level.isClientSide()`。
- 记录 callback 发生于启动前、world ready 前后、server/client thread、reload 阶段哪一侧。Fabric 与 NeoForge 时机不同就文档化，不要伪装同一语义。

## 新增类型转换 Adapter

本仓库存在两种接口：

- 旧/主注册契约 `com.tkisor.nekojs.api.JSTypeAdapter<T>`：`getTargetClass/test(Value)/apply(Value)`，由 `JSTypeAdapterRegistry#register` 注册。
- 较新的隔离转换接口 `com.tkisor.nekojs.api.data.JsTypeAdapter<T>`：显式 `supports/convert/ConversionContext`，经 bridge 进入同一 registry。

新增时优先沿用相邻目标类型已经采用的接口，不要为了新代码引入第三种转换路径。下面示例使用当前插件注册面广泛使用的 `JSTypeAdapter`。

### 示例：JS 字符串或 RGB 对象转 `ColorToken`

目标类型是纯 Java 值，所以放在 `common`；这里不需要 Minecraft/Loader 类。

```java
// common/src/main/java/com/example/ColorToken.java
package com.example;

public record ColorToken(int red, int green, int blue) {
    public ColorToken {
        if (red < 0 || red > 255 || green < 0 || green > 255
                || blue < 0 || blue > 255) {
            throw new IllegalArgumentException("RGB channels must be between 0 and 255");
        }
    }
}
```

```java
// common 或插件实现源码
package com.example;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import graal.graalvm.polyglot.Value;

import java.util.List;
import java.util.Optional;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

public final class ColorTokenAdapter implements JSTypeAdapter<ColorToken> {
    @Override
    public Class<ColorToken> getTargetClass() {
        return ColorToken.class;
    }

    @Override
    public boolean test(Value value) {
        if (value.isString()) return value.asString().matches("#[0-9a-fA-F]{6}");
        return value.hasMembers()
                && value.hasMember("r") && value.getMember("r").fitsInInt()
                && value.hasMember("g") && value.getMember("g").fitsInInt()
                && value.hasMember("b") && value.getMember("b").fitsInInt();
    }

    @Override
    public ColorToken apply(Value value) {
        if (value.isString()) {
            String hex = value.asString();
            if (!hex.matches("#[0-9a-fA-F]{6}")) {
                throw new ValueConversionException(
                        ColorToken.class, "#RRGGBB", hex, "expected six hexadecimal digits");
            }
            int rgb = Integer.parseInt(hex.substring(1), 16);
            return new ColorToken((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
        }

        if (value.hasMembers()
                && value.hasMember("r") && value.getMember("r").fitsInInt()
                && value.hasMember("g") && value.getMember("g").fitsInInt()
                && value.hasMember("b") && value.getMember("b").fitsInInt()) {
            return new ColorToken(
                    value.getMember("r").asInt(),
                    value.getMember("g").asInt(),
                    value.getMember("b").asInt());
        }

        throw new ValueConversionException(
                ColorToken.class, "#RRGGBB | { r: number, g: number, b: number }",
                value, "expected a hex string or RGB object");
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                string(),
                object(
                        Slot.req("r", number()),
                        Slot.req("g", number()),
                        Slot.req("b", number())));
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("RGB color as #RRGGBB or { r, g, b }, each channel 0..255");
    }
}
```

注意：上面的 `test` 应与 `apply` 接受面严格一致。生产实现还应在 `test` 或 `apply` 明确检查三个通道在 `0..255`，并由测试覆盖负数、超界值、缺字段、错误类型和非法 hex。这里为让转换规则易读，范围校验也由 `ColorToken` 构造器兜底；如果构造器异常要有清晰错误归因，可以在 Adapter 边界转成 `ValueConversionException` 并保留 cause。

注册 Adapter 插件：

```java
package com.example;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;

@RegisterNekoJSPlugin
public final class ColorPlugin implements NekoJSPlugin {
    @Override
    public void registerAdapters(JSTypeAdapterRegistry registry) {
        registry.register(new ColorTokenAdapter());
    }
}
```

若插件是在 NekoJS 仓库内部新增，并要符合 Point pairing guard，建议显式实现 `AdaptersPoint.Contributor`（它继承 `NekoJSPlugin`，贡献内容仍走 `registerAdapters`）：

```java
@RegisterNekoJSPlugin
public final class ColorPlugin implements AdaptersPoint.Contributor {
    @Override
    public void registerAdapters(JSTypeAdapterRegistry registry) {
        registry.register(new ColorTokenAdapter());
    }
}
```

JS 侧使用示意：

```js
// 一个接受 ColorToken Java 参数的方法会自动经过 adapter。
ColorTools.setAccent('#22AADD');
ColorTools.setAccent({ r: 34, g: 170, b: 221 });
```

`ColorTools.setAccent` 只是示例消费者。添加 Adapter 不会自动创造脚本 Binding；如果脚本需要新 helper，另走 `registerBinding` 和对应文档/declaration 流程。输入形状会被 Probe 用于生成 TS `$ColorToken_` 输入别名；如果不声明 `inputShapes()`，运行时可能能转换，但 IDE 类型不会知道这些形式。

### Adapter 必须验证什么

1. `getTargetClass()` 唯一且准确。
2. `test` 不接受 `apply` 无法解析的输入。
3. 错误用 `ValueConversionException`，不返回 `null`。
4. `inputShapes()` 覆盖实际支持形状，不比运行时更窄或更宽。
5. 多个 Adapter 可能接受同一输入时，评估 `getPrecedence()`；默认 `LOWEST`，不要靠插件扫描顺序。
6. GraalJS `Value` 的成员/数组访问在当前 runtime HostAccess 下测试；也可采用 `JsTypeAdapter` + `JsValueView` 获得更易隔离的转换测试。
7. 目标含 Minecraft 类型时实现放版本/平台源码；`common` 不得导入 Minecraft/Loader。

## 新增或使用扩展点

先找已有 Point：`AdaptersPoint`、`BindingsPoint`、`EventsPoint`、`TypeDocsPoint` 等。第三方插件通常只覆写已有 `NekoJSPlugin` hook，不需要自己建 Point。

仅当要增加一个全新的 builtin collection channel 时，仓库内才需要完整配对：

1. 新建自包含 `XxxPoint`：ID、Contributor、Accumulator、MergePolicy、collector 和 finisher 写在同一事实源。
2. 若暴露 `NekoJSPlugin` 默认 hook，则在 `PluginHookPairingTest` 加 pairing row。
3. 在 `NekoBuiltinPointsPlugin#registerPluginExtensionPoints` 加注册行。
4. 写 Point collect/freeze/order/reload tests；普通脚本 reload 不应意外重建 Plugin Runtime。
5. 外部 addon 从 provider/Contributor 走公开路径，不能要求 engine 增静态注册表。

简化的形状示意（不是新增 engine 类型的完整替代方案）：

```java
public final class ColorRulesPoint {
    public static final String ID = "example:color_rules";

    public interface Contributor extends NekoJSPlugin {
        default void registerColorRules(ColorRuleRegistry registry) {}
    }

    public static final NekoPluginExtensionPoint<Contributor, ColorRuleRegistry, List<ColorRule>> POINT =
            NekoPluginExtensionPoint.<Contributor, ColorRuleRegistry, List<ColorRule>>builder(ID, Contributor.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new ColorRuleRegistry.Impl())
                    .collector(Contributor::registerColorRules)
                    .finish(ColorRuleRegistry::freezeAndSnapshot)
                    .build();
}
```

上述代码中的 `ColorRuleRegistry`/`ColorRule` 是你需要设计并测试的领域类型，不是 NekoJS 现成类型。正式实现时确认 builder 实际方法签名，并在 builtin Point manifest、pairing test、生命周期测试中配对齐全。不要复制一个不受 bootstrap 所有权管理的 static `List`。

第三方插件侧的 Java 形态大致如下：

```java
@RegisterNekoJSPlugin
public final class ExamplePlugin implements ColorRulesPoint.Contributor {
    @Override
    public void registerColorRules(ColorRuleRegistry registry) {
        registry.add(new ColorRule("warning", 0xFFFFAA00));
    }
}
```

扩展点是 Java/plugin collection API，不等于脚本 JS event。若还需要 JS 脚本写入，设计独立脚本事件或 Binding，并定义二者的 owner、时机和事务边界。

## 版本与平台落点

| 代码种类 | 通常落点 |
|---|---|
| Loader-free contract/纯转换模型 | `common/src/main/java` |
| NeoForge 共享版本 API/事件绑定 | `src/main/java`，按 Stonecutter guard/facade 管理版本差异 |
| Fabric callbacks、mixins、loader 入口 | `src/fabric/java` 或 `src/fabric/resources` |
| 某节点独有 API/行为 | `versions/<node>/src` 成对实现，或已有版本 facade/replacement |
| JS fixture / golden inputs | `src/test/resources` 的规范输入，由生成/验证 workflow 派生节点结果 |

不要为了小的符号改名复制整套逻辑；用已有 facade/replacement。不要手工改生成目录或节点的 `build/generated`。

## 测试与验证

每个改动至少从最窄行为开始：

### 新事件

- bus contract：名字、Script Type、payload、dispatch key、cancellable。
- ownership/source trace：生产投递点唯一，platform bridge/mixin 确实能到达 bus。
- 脚本行为：注册 callback 后由平台事件触发，断言实际 payload/修改/取消结果。
- 多节点：受影响节点 `platformGateTest`、catalog/declaration tests；Fabric 不支持的成员在 gate/source trace 显式记录。

### Adapter

- GraalJS 或 `JsValueView` 测试真实支持/转换行为。
- `inputShapes()` 生成的 TS 别名与运行时支持面一致。
- 错误输入/边界值有明确 `ValueConversionException`。
- 插件 bootstrap 收集路径可到达 registry；没有只在单测手工 new adapter 的假阳性。

### 常用命令

```powershell
./gradlew.bat :common:check
./gradlew.bat :26.1.2:platformGateTest :26.2.0:platformGateTest
./gradlew.bat :26.1.2-fabric:platformGateTest :26.2.0-fabric:platformGateTest
./gradlew.bat :1.21.1:platformGateTest
./gradlew.bat :26.1.2:build :26.2.0:build :1.21.1:build
./gradlew.bat :26.1.2-fabric:build :26.2.0-fabric:build
./gradlew.bat guardLint
```

只跑受影响节点是迭代阶段；完成前应按改动范围跑完整 matrix。对真实行为有要求的票还要按票据规定启动 dedicated/client/MCP fixture；编译或 fake host 通过不能代替真实客户端证据。

## 提交前检查

- JS 名称、参数、回调成员、示例和 declarations 一致。
- 事件组在 freeze 前注册；platform callback 不重复、不漏触发，reload 不累积 listener。
- 异步/网络/跨线程回调切到 owner thread；候选期只收集，commit 点才做外部副作用。
- 是否要取消？原生取消和 JS 返回语义一致。
- capability matrix 明确 supported / partial / unavailable / not verified。
- 新增公开 Plugin hook 是否配套 Point 和 pairing test。
- migration、golden 与用户数据保护边界已检查；未授权 breaking 删除不做。
- 测试观察公开行为，避免断言私有 static Map 或只看 source 字符串。

## JS 例子汇总

订阅新事件：

```js
ServerEvents.machineCrafted(event => {
  console.info(`${event.machineId}: ${event.recipeId} x${event.outputCount}`);
});
```

利用 Adapter 输入形状（传给已声明的 Java Binding 方法）：

```js
ColorTools.setAccent('#22AADD');
ColorTools.setAccent({ r: 34, g: 170, b: 221 });
```

JS 侧不负责创建 Java Adapter，也不通过运行时反射注册事件。事件是作者订阅的平台行为；Adapter 是 Java 参数边界的转换器；Plugin Point 是 Java 插件 bootstrap 的收集通道，三者职责不同。
