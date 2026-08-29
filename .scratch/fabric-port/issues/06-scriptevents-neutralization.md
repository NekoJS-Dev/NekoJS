# 06: ScriptEvents 自定义事件中立化（双平台）

**What to build:** 自定义脚本事件注册面（`ScriptEvents.server(ev => ev.register('组','名','net.neoforged...类名'))`）目前以 NeoForge 事件类名驱动、整文件 neoforge 守卫。中立化重设计：中立事件源描述（平台无关的注册参数）+ 双端解析。

**Blocked by:** None（用户裁定采用方案 A）。

**Status:** done（方案 A）

## 现状（读码结论）

- `ScriptEventsJS`（`src/main/java/.../bindings/static_access/ScriptEventsJS.java`，整文件 `//? if neoforge`）
  的第 3 个参数是 **NeoForge 事件类**（`Class` 或 FQCN 字符串），实现直接
  `NeoForge.EVENT_BUS.addListener(priority, receiveCancelled, eventClass, listener)`。
- 中立部分其实已经就位：`ScriptEventRegistrar`（接口）、`ScriptEventDefinition`、
  `ScriptEventRegistry`、`ScriptEventGroupJS`、`DefaultScriptEventBridge` 全在 common，
  不含 loader import。**唯一的 loader 面就是"事件类 → 平台总线"这一步。**
- fabric 上没有等价物：fabric 的事件是回调接口（`Event<T>` + 自定义函数式接口），
  没有"事件对象类"可以按类名挂载。所以"按类名挂任意原生事件"这件事**本质上不可中立**。
- 另有 `NativeEvents`（`NativeEventsJS`，同样 neoforge 面）已经承担"就地监听任意原生事件类"，
  与 `ScriptEvents` 是显式共存关系（2026-08-16 用户裁决，撤销 NativeEvents 弃用）。

## 方案 A（推荐）：ScriptEvents 收敛为"脚本自定义事件"，原生挂载留给 NativeEvents

- `ev.register(targetType, group, name)` —— **去掉事件类参数**。声明出来的事件是脚本自己的：
  payload 是脚本传入的任意值，监听面不变（`MyEvents.bossKilled(cb => ...)`），
  触发面把已有的 `EventBusJS#post` 暴露给脚本（`MyEvents.bossKilled.post(payload)`）。
- 两个加载器完全同构：`ScriptEventsJS` 去守卫下沉到 common，fabric 白拿（`NekoJSFabricMod`
  当前传的 `new DefaultScriptEventBridge(null)` 也就能换成真 registrar）。
- "挂原生事件类"继续由 `NativeEvents` 提供，并诚实标注为 NeoForge 面；fabric 若要类似能力
  另做 fabric 形态（回调接口，不是类名）。
- 破坏性：现有脚本里 `ev.register(group, name, 'net.neoforged...Event')` 的第 3 参失效，
  改用 `NativeEvents.onEvent(...)`；wiki 迁移表承担迁移 UX（ADR-0006 的既定方式，报错不带指引）。

## 方案 B：中立事件源 id

- `ev.register(group, name, 'entity.death')` —— NekoJS 维护一张中立事件源 id → 各平台原生事件的
  映射表，注册时查表挂载。能保住"监听原生游戏事件"且跨加载器。
- 代价：这张表就是 `EntityEvents` / `BlockEvents` / `ServerEvents` 等内置事件组已经在做的事，
  会长出第二套内置事件目录，两处都要维护、两处都可能漏。

**推荐 A**：唯一不可中立的能力（按类名挂原生事件）本来就有 `NativeEvents` 承担，
A 之后 `ScriptEvents` 与内置事件组职责不重叠，且 common 里现成的中立管线可以直接用。

- [x] 中立 API 形态确定：方案 A
- [x] NeoForge 侧迁移到中立形态（`ScriptEventsJS` 下沉 common，删掉 neoforge 版）
- [x] fabric 侧可用（`FabricCorePlugin` 注册 `ScriptEvents.GROUP`、`NekoJSFabricMod` 传真 registrar）
- [x] wiki《事件扩展》《全局绑定》《事件参考》+ README 同步；四节点编译 + 测试 + guardLint 绿

## 落地记录

- `ScriptEventsJS` 从 `src/main/java`（neoforge 守卫）移到 `common/src/main/java`，同包名，
  所以 `NekoJSMod` 的 import 不用改。`register(targetType, group, name, sourceScriptId)`：
  建一个不可取消的 `EventBusJS.of(Object.class, false)`，不挂任何平台总线，unregisterer 为空。
- 触发面：新增 `ScriptEventBusJS`（同时实现 `ProxyExecutable` + `ProxyObject`）——
  调用即注册监听（转发给 `EventBusJS`），`post` 成员触发。`ScriptEventGroupJS.getMember`
  返回它而不是裸 bus，所以只有 `ScriptEvents` 声明出来的事件允许脚本自己 post，
  内置事件组不受影响。
- `ScriptEventDefinition` 去掉 `eventClassName`（全仓无读取方）。
- 冻结基线（`api-manifest-core.json`）按门禁流程 `-Dnekojs.golden.regenerate=true` 再生成，
  diff 就是 `ScriptEventRegistrationEvent.register` 的 3 个原生类重载换成 `(object,object)`。
- `sourceScriptId` 现在由 `ScriptEventRegistrationEvent` 解析：从脚本传进来的任一 Graal 值
  反查其 Context 的当前脚本 id（所以位置形态签名是 `(Object, Object)` 而不是 `(String, String)`
  —— 拿不到 Value 就退化成整类型清理，会打破 per-file STARTUP reload 的按来源清理）。
- fabric 冒烟：startup 声明 `MyEvents.bossKilled` → server 脚本监听 + 第 40 tick `post`
  → 日志 `SE-SMOKE: listener got boss=ender_dragon hp=0`。
  第一次跑报 `Unknown identifier 'ScriptEvents'`：`ScriptEvents.GROUP` 原先只由
  NeoForge 核心插件注册，fabric 侧要在 `FabricCorePlugin.registerEvents` 里补一行。
