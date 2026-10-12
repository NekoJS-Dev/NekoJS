# 包同步重载拒绝测试阶段守卫

完整矩阵曾在 `PackSyncClientTest.reloadFailureRejectsBundleAndRevokesSelectedRuntimeState` 失败：断言期望缓存文件无审批，却观察到 LOCAL_TRUSTED。此历史失败保留；单方法和整类隔离运行未复现，后续完整矩阵通过。**根因仍未确认，没有生产信任修复或实机包下载通过结论。**

原断言只要求拒绝消息含 `reload`，而该 fixture 的 packId/keyId 本身就含此字样，无法证明实际进入 CLIENT 重载拒绝阶段。现在要求准确的 `CLIENT script reload failed`，失败时展示实际 outcome。断连、缓存撤销和信任撤销断言原样保留；这是更严格的验收，不是放宽失败检查。

| 执行 | 直接退出 | 结果 |
|---|---:|---|
| 原完整矩阵 | 1 | FAIL，原始 XML 已冻结 |
| 更严格单方法 | 0 | PASS |
| 更严格完整 PackSyncClientTest | 0 | PASS，21 tests |
| 更严格完整五节点矩阵 | 0 | PASS，4808 tests/271 skipped/0 failures/errors |

源码基点 da6bad60385d8685b34e58af02c92d3da8d293b5，只有该测试的一行阶段断言更改，包内有真实 diff/源码。同期 Probe 的生产补丁另行验收；不把旧失败归因于它。完整矩阵 XML 中的21项包同步测试保留，隔离运行的 XML 未单独冻结，隔离执行依据直接退出记录和原始日志。

[不可变证据](pack-stage-evidence.zip)、[结果与 hash](summary.json)、[独立验证器](verify.py)：21788bytes，SHA256 `1d5cbd01beede80c418614b1ede9f816de327d33610bdc8b0d4098588be4b082`。没有信任配置/world/私钥/RCON凭据或用户进程操作。自审确认没有删除或放宽测试；真实维护者结论未记录，总体验收未完成。
