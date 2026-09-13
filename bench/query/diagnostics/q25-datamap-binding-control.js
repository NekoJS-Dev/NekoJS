// Ticket 25 对照组探针（诊断用，**非**验收 fixture）：DataMap 的 class binding vs instance binding。
//
// 用途：给 F1（`NekoJSCorePlugin` 把 DataMap 从 `DataMapJS.class` 改为 `new DataMapJS()`）补
// 一条**可复现的对照记录**——原始「修前不可用」证据是上一阶段会话的 run 目录日志（已随日志
// 轮转丢失），只有作者自述，属循环证据。本脚本让任何人能自己把对照跑出来：
//
//   # 1) 对照组（class binding）：临时把 NekoJSCorePlugin 的
//   #    registry.register("DataMap", new DataMapJS()) 还原成 registry.register("DataMap", DataMapJS.class)
//   powershell -File bench/query/run-query.ps1 deploy -IncludeDiagnostics
//   powershell -File bench/query/run-query.ps1 start   -Session ctrl
//   powershell -File bench/query/run-query.ps1 wait-done -Session ctrl
//   powershell -File bench/query/run-query.ps1 test    -Session ctrl     # verify 会失败，属预期
//   powershell -File bench/query/run-query.ps1 extract -Session ctrl
//   powershell -File bench/query/run-query.ps1 stop    -Session ctrl
//   # 2) 还原源码 → 同样步骤跑「实验组」（instance binding）应全绿。
//
// 判定：class binding 下脚本侧拿不到 instance 方法（`typeof DataMap.furnaceFuel === 'undefined'`
// 或沙盒 preflight 直接报 `Unknown identifier: furnaceFuel`）；`typeof DataMap` 仍是 `function`
// （StaticClass 暴露的是类本身）。instance binding 下两者都正常，且 furnaceFuel 返回 1600。

Test.section('q25.diag.datamap-binding');

const typeOfDataMap = typeof DataMap;
Test.assertTrue(true, 'diag[binding]: typeof DataMap = ' + typeOfDataMap);

let memberType;
try {
  memberType = typeof DataMap.furnaceFuel;
  Test.assertTrue(true, 'diag[binding]: typeof DataMap.furnaceFuel = ' + memberType);
} catch (e) {
  Test.assertTrue(true, 'diag[binding]: typeof DataMap.furnaceFuel THREW: ' + e.message);
}

try {
  Test.assertTrue(true, 'diag[binding]: DataMap.furnaceFuel(coal) = '
    + DataMap.furnaceFuel('minecraft:coal'));
} catch (e) {
  Test.assertTrue(true, 'diag[binding]: DataMap.furnaceFuel(coal) THREW: ' + e.message);
}

Test.summary();
