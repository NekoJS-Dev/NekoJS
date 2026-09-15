// NekoJS ProbeEvents 最小示例（票 14 AC10）
//
// tier：Probe 扩展面（SERVER-only）——只在 /nekojs probe 生成类型声明时被
// ProbeCoordinator/ProbeIrBuilder post，不是通用运行时事件，也没有第二事件
// bus（成员集合冻结为 modifyType/assignType/addGlobal/snippets 四条，见
// ProbeEventsSurfaceGoldenTest）。
// catalog/declaration golden：common/src/test/resources/nekojs/probe/
// probe-events.expected.d.ts（SERVER 侧 4 成员各一次；CLIENT 侧零条目）。
//
// 放置：server_scripts/probe-tweaks.js
// 运行：游戏内执行 /nekojs probe（单测覆盖 catalog/声明派生面；golden 即证据）。

// 改类型声明：对反射出的 TypeDecl IR 做参数级编辑
// （bus 本身就是可调用代理：直接调用即注册，没有 .listen 成员——与 TS golden 的
//   function modifyType(handler: ...) 形状一致）
ProbeEvents.modifyType(event => {
  event.forClass('net.minecraft.world.entity.player.Player')
       .renameMethod('getX', 'getCustom')
       .hideMethod('setY');
});

// 全局类型重定向：ItemStack 处处改写为 string（IR 构建后应用，TS/Python 均生效）
ProbeEvents.assignType(event => {
  event.assign('net.minecraft.world.item.ItemStack', 'string');
});

// 登记额外全局声明（TS → @manual/globals.d.ts；Python → nekojs/__init__.pyi）
ProbeEvents.addGlobal(event => {
  event.add('MyFlag', 'boolean');
  event.add('MyCount', 'int');
});
