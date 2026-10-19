# Fabric 目录桥：实现编译记录，非最终验收

按用户“先完成任务，不要着急测试”收口实现，本阶段没有执行测试、启动游戏或重跑性能采样。

| 检查 | 结果 | 边界 |
| --- | --- | --- |
| 五节点 `compileTestJava` + `guardLint` | PASS，exit 0，4m 8s | 含配方 baseline 隔离、注册 receipt、节点布局与首轮目录桥；不等于五节点 build/test 通过。 |
| 最终资源栈展开后的两 Fabric `compileTestJava` + `guardLint` | PASS，exit 0，22s | 覆盖最后新增的原版资源管理器 Mixin、目录层及资源栈测试源码；没有执行测试。 |
| 最终合法标签/语言 fixture 的两 Fabric 重编 + `guardLint` | PASS，exit 0，13s | 只收口资源栈测试数据，未执行测试。 |
| 编译器零警告 | 不满足 | 原始日志保留 Gson InlineMe 缺失批注、弃用等警告，不称零警告编译。两轮 guardLint 均零警告。 |
| 单元/平台门禁测试、制品校验、GameTest | NOT RUN | 本阶段延后集中执行；不复用旧 XML 作为新实现通过证据。 |
| Fabric 初次模型、WORLD A→B→A、远端资源撤回、标签/语言合并实机 | NOT RUN | 仍需真实运行证据。 |
| 原性能标准复验 | NOT RUN | 最近冻结结果仍未整体通过，不将编译结果代替性能成绩。 |

完整日志：[五节点编译](compile.log)、[资源栈展开编译](compile-resource-stacks.log)、[最终 fixture 编译](compile-final-fixtures.log)。[记录与 SHA-256](verification.json)包含复现命令和当前磁盘源文件摘要（包含本机行尾，不是跨平台 Git blob 摘要）。先完成五节点编译，静态复核再补原版资源栈展开，随后只重编受影响的两个 Fabric 节点。共享及 1.21.1 的配方修复已提交 `3e692eff`；其源码在第一轮编译后没有继续修改。

归档预检首轮发现 Git 行尾转换导致索引日志 SHA 比较失败（AssertionError）。本目录日志现统一 LF，原始 CRLF 字节摘要另存 `original_log_sha256`；`log_sha256` 对应归档 LF 字节。修正后的 Git 索引日志与当前源文件摘要逐项复核通过。该归档修正没有修改编译结果。

范围、覆盖顺序、依赖门禁、生命周期、脚本/原版资源独立失败边界见[实现记录](../../../research/2026-10-19-fabric-directory-resource-bridge.md)。后续必须执行这里尚未运行的检查，不能据本目录命名或编译成功宣告功能验收完成。
