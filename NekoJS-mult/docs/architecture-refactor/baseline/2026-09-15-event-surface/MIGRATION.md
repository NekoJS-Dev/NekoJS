# 事件面迁移材料（ticket 14）

> 面向脚本作者的「三个事件声明面」使用与迁移要点。tier 词汇沿用 managed surface
> 模型（ticket 09）：managed（契约反射承诺）/ legacy 观察（catalog legacySurface）/
> raw（loader 专属 bridge）。

## 1. 三类事件面 tier 归属（当前快照）

| 面 | 入口 | tier | 声明/文档来源 | side | reload 清理 |
|---|---|---|---|---|---|
| 内置事件组（ServerEvents.recipes 等 14 组） | `EventGroup` + `EventBusForgeBridge`（平台 callback 在 Adapter 层） | legacy 观察（catalog events → legacySurface → probe TS/Python declaration）；回调 schema 经 `EventContractReflector` 进 managed 回调校验面 | probe declaration（catalog 派生） | 各组规范 side | `EventBusJS.clearTokens`（票 06/07 事务语义） |
| ScriptEvents 动态声明 | `ScriptEvents.server/client(event => event.register(...))`（STARTUP） | managed 声明 API（`ScriptEventRegistrationEvent` 符号在 portable-core 契约内）；声明出的动态事件进 catalog events（同上派生路径） | probe declaration（同一 catalog 派生；TS any payload + post、Python Any + post） | 声明的 target side（SERVER/CLIENT） | STARTUP 全量清扫 + 按来源/前缀清理 |
| NativeEvents | `NativeEvents.onEvent(...)`（STARTUP，NeoForge-only） | **legacy/raw Adapter 观察面**（保持现状，不升 managed stable） | `TypeDocCatalogEntry`（probe 补全/文档），不进 catalog events | STARTUP 注册；事件本身由 NeoForge bus 决定 | `Binding.close()` → `clear()` 注销上一轮 |
| ProbeEvents | `ProbeEvents.modifyType/...`（server_scripts） | Probe 扩展面（仅 probe 管线触发） | catalog events → probe declaration（golden：`probe-events.expected.d.ts`） | SERVER-only | 一般事件监听清理路径 |

## 2. 迁移要点（1.2.0）

1. **从 NativeEvents 迁到内置事件组**：需要跨平台/可迁移语义的监听优先改用
   对应内置组（如生物死亡→`EntityEvents`）；`NativeEvents` 保留给一次性/raw
   场景，不会消失（合法 legacy/raw tier 不删除）。
2. **需要跨脚本复用的事件**：用 `ScriptEvents` 声明命名组（STARTUP 声明 +
   server/client 监听/post），而不是在多个脚本里重复挂 raw 监听。
3. **NativeEvents 的 KubeJS 兼容签名**：`onGenericEvent` 的 genericClassType
   参数被忽略（兼容保留）；新脚本请用 `onEvent`/`onEventTyped`。
4. **取消语义**：内置/动态事件可取消总线返回 `true` 即取消；`NativeEvents`
   返回 `true` 映射 `setCanceled(true)`（仅 ICancellableEvent）。错误监听器
   默认不取消。
5. **Fabric 用户**：`NativeEvents` 是 NeoForge-only raw 面，Fabric 侧没有对应
   实现（tier 记录见 REPORT §能力矩阵）；迁移目标是内置事件组。

## 3. 已知边界（不在本票解决）

- `ScriptEventsJS.register` 在注册入口先做 `validateAvailable`：未经 STARTUP
  清扫的同 key 重注册（含同来源）会得到可诊断的 `Script event already
  registered`；同来源 replacement 分支只在 `ScriptEventRegistry.register`
  直接调用时可达（见 REPORT §问题与处理 P2）。
- Probe 事件只在 probe 运行期触发；把 ProbeEvents 当运行时事件使用不受支持。
- Python managed declaration 的独立渲染（managed API 面）仍走 IR 反射路径
  （ticket 09 已登记的 W5 跟进项）；本票只对齐事件声明面（catalog 派生）。
