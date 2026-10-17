# 当前版本重载性能：尚未通过

测量日期：2026-10-17。基线 `f41db5d9819df7f501069163d2cbb7a0397b7d6e`；已提交的解析优化 `a09e5bc29cfef094467446c46d05a18ae4a1c346`。本目录只验收到这两个精确提交，不代表后续信任路径修复或整个工作目标已经完成。

## 正式结果

| 源码 | 组 | 五个正式值（ms） | 均值（ms） | 原门槛（ms） | 判定 |
| --- | --- | --- | ---: | ---: | --- |
| f41db5d9 | reload-1 | 623, 416, 504, 462, 497 | 500.4 | 285.3 | FAIL |
| f41db5d9 | reload-2 | 623, 429, 416, 421, 425 | 462.8 | 285.3 | FAIL |
| f41db5d9 | startup | 59601, 59914, 50502, null, 57501 | — | 41173 | INVALID |
| a09e5bc2 | reload-1 | 513, 481, 369, 495, 355 | 442.6 | 285.3 | FAIL |
| a09e5bc2 | reload-2 | 351, 405, 227, 351, 356 | 338.0 | 285.3 | FAIL |
| a09e5bc2 | startup | 26119, 26027, 26718, 26584, 26528 | 26395.2 | 41173 | PASS |

每个源码预先指定两组五次重载、一组两次预热加五次正式启动。原采样器 SHA-256 为 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`；NeoForge 26.1.2、JDK 25.0.2、原 fixtures、固定 Gradle 缓存和 25871/25872 端口不变。在专属干净工作树串行采集，期间未并行运行本任务构建、测试或其他游戏。没有删首值、改阈值、换计时口径或按成绩择优重试正式组。

每组前后检查编译类和 fixture 摘要；全部 Neko 编译类与对应正式构建 JAR 内同名类逐字绑定。归档保留制品摘要和类摘要，不含二进制 JAR。计时实际运行的是既有源码 `runServer` 路径，不是安装 JAR 的启动耗时。两个提交之间系统缓存状态并非受控，正式分数差异不能全部归因于优化。

基线启动组的预热索引 2、正式索引 6 未到 Done；daemon 记录客户端断连后取消任务，断连根因未确认。两行没有正常停服回执，因此整组 INVALID，不取剩余四值计算合格均值。其他基线会话及全部 a09e5bc2 正式会话均正常 RCON 停服；所有正式重载回执均为 `phase=COMMIT - no errors.`。采样器退出 0 只表示采集流程结束，不表示性能通过。

重载 `marker_ms` 沿用 RCON 发送到最后一个 fixture load 日志可见的墙钟差，含客户端启动、命令处理和日志观察，有 100 ms 轮询粒度；它不是引擎独占耗时，也不是完整提交的独立时间戳。提交另由回执确认。启动指标含 Gradle/加载器。未改变系统电源设置、终止无关进程或声称整机完全独占。

## 已实现的解析优化

`NekoModuleResolver` 在一次文件解析内复用 game/containment 根的规范形式，实际候选仍检查，最终文件仍重新 `toRealPath()` 并校验边界。后续调用会重新观察根、符号链接和文件状态。没有跨调用路径缓存，没有复用 guest 状态、信任结果或 Context，也没有 Script API、wire 或数据格式变更。

同一 JVM 内，精确旧解析器与新解析器交替十对测量，六条生产入口路径均值为 **30.88 → 10.22 ms**，组件节省约 **20.67 ms**；六个身份场景、五个拒绝场景相同，符号链接实际执行。组件测量不是正式性能门禁，不能代替上表失败结论。

回归新增重定向符号链接、根别名变更、node_modules 后创建和文件后删除场景。最初新增 ZipFS 探测导致 common 检查失败；精确旧/新源码对同一输入均出现 `ProviderMismatchException`，说明字符串入口未支持该路径提供者。失败 fixture、XML、日志和旧/新对照均保留；本轮不新增 ZipFS 能力，最终改为相关的文件删除边界回归。既有回归和门禁没有放宽。

## 验证

以下验证属于 a09e5bc2；归档保存最终 XML、命令日志及源文件摘要：

- 初次五节点 build 全部通过；同批 common 检查因新增 ZipFS 探测失败，保留为 FAIL。
- 修正探测范围后重新执行 `gradlew.bat --offline :common:check :common-api-processor:test guardLint --console=plain`：PASS。
- 五节点 `:1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build`：PASS。
- 最终 XML 合计 4957 tests、0 failures/errors、271 skipped；解析器 17 项实际执行，无跳过。
- `:26.1.2:runGameTestServer`：PASS，实际出现 `All 1 required tests passed :)`。
- `python tools/nekojs-ci-gates.py all`：最终四项 PASS。较早的一份报告在构建尚未结束时生成，保留但不作最终依据。
- `git diff --check`：PASS。没有改声明/API，因此没有为此重跑 TypeScript 声明检查。

## 后续诊断与限制

两轮各二十次诊断、JFR 命令窗口、临时 patch 和恢复脚本单独归档。诊断只用于定位，不混入正式成绩；嵌套计时不能相加，JFR 样本数不是毫秒。

f41db5d9：root.reload 约 163.98 ms，配方约 35.17 ms，六次入口解析约 52.91 ms。a09e5bc2 的另一会话：root 约 190.83 ms，配方约 54.21 ms；manifest 读取约 0.88 ms，不支持优先增加清单缓存。其信任批准约 18.98 ms / 六次，规范路径约 46.84 ms / 三十次，下一步调查重复规范化。诊断会话负载、JIT 和缓存状态不同，这些前后值不能直接构成优化因果对照。

a09 第一次临时诊断在首条 RCON 调用失败，未产生重载样本；修改诊断客户端的错误捕获方式后，第二次二十条均提交并正常停服。失败记录保留。诊断编译前还有一次方法签名不匹配的准备失败，保存原始备份并按精确源码恢复后重做；没有把它计为采样。所有临时生产插桩都已按摘要恢复，专属性能工作树恢复干净。

配方调查没有找到现成、足以保证旧配方还原、脚本/插件回调和资源同步语义的安全 no-op 条件，本轮没有跳过配方处理。脚本日志折叠也没有取得足以解释正式失败的证据，没有为基准提前输出 marker 或修改刷新行为。

## 复算和归档

[evidence.zip](evidence.zip) 有 1069 个条目、4531909 字节，SHA-256 为 `f4ba0ff9b51de98337be69006d73267708c05def0da7f5a69e96e784a62bfc2a`。[manifest.json](manifest.json) 绑定每项长度和摘要；包含正式 JSONL/日志、来源记录、诊断的事件白名单 JFR、组件对照、失败探测、最终测试 XML 和采集工具。不含 world、server.properties、凭据、原始未筛选 JFR 或 JAR。与实际 RCON 凭据相同的字节已替换；`redactions.json` 记录条目、替换次数及原始摘要，数值样本和判定没有变更。工具中的脱敏占位符需在复现实验前配置成本地路径。

```powershell
python docs/architecture-refactor/evidence/2026-10-17-reload-performance/verify.py
python docs/architecture-refactor/evidence/2026-10-17-reload-performance/verify.py --require-performance-pass
```

第一条退出 0 表示归档一致、样本判定可重算、XML 总数一致；第二条应退出 2，因为性能尚未全部通过。它不能离线重新证明 JAR 字节或当时环境，只能复核采集时记录的绑定；完整运行复现仍需原环境及精确源码。

剩余：继续修复并按原标准正式复验；随后再推进脚本包最终验收、社区事项核对与 Fabric 目录资源桥。维护者人工验收尚未取得，代理不代签。
