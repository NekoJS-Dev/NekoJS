// 票 36 脚本作者试做示例 11/11：capability 识别（从公开材料回答「我这个节点有什么」）
//
// 放置：server_scripts/（全节点可运行；本脚本只做存在性盘点，永远不抛错）。
//
// 脚本作者的公开识别入口（试做要逐一验证是否足够，AC2/AC4）：
//   1) probe 声明目录（wiki/Probe-类型生成.md）：
//      <gameDir>/nekojs/.neko_probe/typescript/@side-only/<side>/events/index.d.ts
//      列出本节点该 side 的全部事件组；@side-only/<side>/bindings/ 列出绑定。
//      节点没有的能力不会出现在声明里（例如 fabric 声明里没有 DynamicRegistryEvents）。
//   2) wiki：事件参考.md（事件组与成员）、全局绑定.md（绑定）、注册新内容.md（启动注册）、
//      命令.md（/nekojs registry / probe 等命令与平台列）。
//   3) 运行时显式拒绝：引用不存在的事件组成员 → "No such event bus: <组>.<成员>"；
//      引用未注册的绑定 → 「未知标识符」定位诊断 + ReferenceError。见 01b/02b/03b/04b/06b
//      变体——拒绝是能力识别的一部分，不是要绕过的障碍。
//
// 本脚本用 `globalThis[name]` 做只读存在性盘点（globalThis 是语言全局对象；注意
// `global` 是 NekoJS 状态容器，两者不恒等——票 10 迁移表第 7 条）。预期对照（HEAD
// 源码核对；试做时逐行记录实际输出并与声明目录交叉验证）：
//
//   名字                   | 26.x NeoForge | 1.21.1 | fabric 26.x
//   -----------------------+---------------+--------+------------
//   RegistryEvents         | 有            | 有     | 有
//   ServerEvents           | 有            | 有     | 有
//   BlockEvents/ItemEvents/PlayerEvents/EntityEvents/LevelEvents/CommandEvents | 有 | 有 | 有（成员面按节点有差异）
//   VillagerTrades         | 有            | 有     | 无（TODO 见 03b：HEAD 复核 fabric 未注册该 binding）
//   DynamicRegistry（旧）  | 有（配置门默认 false） | 无 | 无
//   DynamicRegistryEvents  | 有（inert 计划，激活 not verified） | 无 | 无
//   KeyBindEvents          | 有            | 无     | 有（client 侧；server 脚本里组名可见但成员按 side 拒绝）
//   ClientEvents           | 组名可见（成员按 side/节点拒绝） | 同左 | 同左（只有 tick/tickPre/tickPost 总线）
//
// 注意：事件组绑定对每个 ScriptType 都安装（DefaultScriptEventBridge.bindEvents），
// 「组名存在」不等于「成员可用」——成员级可用性以声明目录与显式拒绝为准。

const CAPABILITY_NAMES = [
  'RegistryEvents', 'ServerEvents',
  'BlockEvents', 'ItemEvents', 'PlayerEvents', 'EntityEvents', 'LevelEvents', 'CommandEvents',
  'VillagerTrades',
  'DynamicRegistry', 'DynamicRegistryEvents',
  'KeyBindEvents', 'ClientEvents',
]

console.info('=== ticket-36 trial: capability inventory (presence of global bindings only) ===')
for (const name of CAPABILITY_NAMES) {
  const present = globalThis[name] !== undefined
  console.info('[capability] ' + name + ': ' + (present ? 'present' : 'absent'))
}
console.info('=== cross-check: .neko_probe/typescript/@side-only/server/{events,bindings} must agree with the rows above ===')
