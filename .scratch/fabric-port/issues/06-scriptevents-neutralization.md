# 06: ScriptEvents 自定义事件中立化（双平台）

**What to build:** 自定义脚本事件注册面（`ScriptEvents.server(ev => ev.register('组','名','net.neoforged...类名'))`）目前以 NeoForge 事件类名驱动、整文件 neoforge 守卫。中立化重设计：中立事件源描述（平台无关的注册参数）+ 双端解析。

**Blocked by:** 等用户裁定 API 形态（下方两案）。

**Status:** needs-info（设计已成型，等一次裁定）

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

- [ ] 中立 API 形态确定（与用户确认：A / B）
- [ ] NeoForge 侧迁移到中立形态
- [ ] fabric 侧最小子集可用（`ScriptEventsJS` 下沉 common + fabric 传真 registrar）
- [ ] wiki《事件扩展》同步 + 四节点编译 + 测试 + guardLint 绿
