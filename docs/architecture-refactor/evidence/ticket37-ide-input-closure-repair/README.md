# Probe 输入类型、数组依赖与接口重载修复验收

本包是局部技术验收，整体 **NOT_COMPLETE**。来源是 `2858b3bd8f4b027fd678caf284b3e395a237a88c` 加生产补丁 SHA256 `f25a5eb54a7d2890d1123ea83c9e906a85ffaf22bc84a8c6715823fbd545d139`。源码、正式 JAR SHA、每个 JAR 的完整编译类绑定、生成输出、默认配置、日志、XML 和失败尝试均在 [evidence.zip](evidence.zip)、[manifest.json](manifest.json)；汇总见 [summary.json](summary.json)。

## 修改与契约

- Node 声明提取器只在实际函数导出带已声明的简单命名类型转换时生成类型化 default；保留真实 Object.assign 命名成员，不伪造函数名导出。`assert` 的 default、自引用 default、布尔 `deepEqual`、0–4 参数 `fail` 与实际实现对齐；运行时逻辑不变，不声称符合标准 Node assert 的全部语义。
- 原始 Java 数组和多维数组递归到实际组件，组件仍使用同一签名边深度及原有 include/exclude。泛型数组、vararg、原始类型和深度边界有回归；没有扩大 maxDepth 或安全权限。
- 子接口重新发射真实继承的同名实例重载，使用实际泛型继承关系替换类型变量，不继承父接口静态工厂，不恢复隐藏方法。
- 删除无实际默认转换器依据的 File/Path/UUID 字符串输入映射，保留宿主输入与输出身份。真实显式适配器仍能覆盖映射，clear 恢复默认。生产 HostAccess 实测宿主值可传入，未适配字符串会拒绝；没有修改转换器、ClassFilter 或 HostAccess。
- TS 预声明中原先静默的第三方失败现在使用 NEKO-4038；任务失败使用 NEKO-4039，双语错误页同步。失败仍遗漏真实声明，不生成替代正文或假类型。

## 集中验证

按用户要求先收敛源码再集中运行，未把中间编译当验收。

| 检查 | 结果 |
|---|---|
| 新增回归及既有 Node 真实运行回归 | PASS |
| 严格 assert 与接口调用正例；负例分别产生 5/3 条预期调用诊断 | PASS，skipLibCheck=false |
| `:common:check :common-api-processor:test guardLint`、三节点 nbtSmokeTest、五节点 build | PASS：4818 测试、271 跳过、812 XML、零失败，109 任务，1m19s |
| `npm run test:probe-types` | PASS |
| `tools/nekojs-ci-gates.py all` | PASS，4 类 gate；结构化报告保留，控制台编码失真不作为内容依据 |
| 五节点新 JAR、全编译类逐字绑定、实机注册/放置/重载、NF fluid 和 26.1 effect | PASS，全部正常 RCON 关闭、direct0、10 端口空闲、无 hs_err |
| 五节点真实 ESM assert/default/deepEqual/fail 与 UUID 参数身份 | PASS：每节点首次执行及重载后各一个真实标记 |
| Python 1605 份实机 stub AST | PASS |
| 真实默认 server/startup editor 项目，只覆盖 skipLibCheck=false/noEmit | **FAIL**，下表 |
| Pyright 1.1.414 全部实机 stub | **FAIL**：7/7/7/13/13 个同名重声明 |
| 当前源码正式性能 | NOT_RUN；历史 7d 的 reload FAIL 不变 |

初次矩阵的两个旧 golden 断言失败被完整保留。通过既有 `:common:regenerateGoldens --tests '*LegacyProbeTreeTest'` 生成并审查 **6 份**基线：java/io 的 10 处宿主输入修正；java/lang 的 StackTraceElement；java/lang/invoke 的 Lookup.ClassOption；java/lang/reflect 的 Parameter/RecordComponent；java/security 的 Principal、SAM 及其真实引用；java/util 的继承重载。后五项来自修复后的真实扫描/反射，不是手改产物。基线重生成本身跳过断言，最终普通矩阵才是 PASS。没有删除断言、放宽比较或修改无关 EOL 变更。

验收汇总器在所有服务端正常关闭后用 UTF-8 读取 legacy 混合编码日志失败（exit1）。原始失败和脚本保留；补充工具对同一原始字节中的 ASCII 标记、XML、退出文件做离线验证，未重跑服务端、改写日志或重采样。补充工具 exit0 不替换原始 exit1。1.21.1 新 NEKO-4038 的 8 次遗漏真实记录也保留，其原因包括 dedicated server 不提供客户端类；其余节点此代码计数为零，不代表完整依赖闭包通过。

## 完整 IDE 仍失败

| 节点 | 原 server/startup 诊断 | 新 server/startup 诊断 | Python stub / Pyright 错误 |
|---|---:|---:|---:|
| 1.21.1 | 632 / 633 | 588 / 588 | 320 / 7 |
| 26.1.2 | 789 / 790 | 746 / 746 | 379 / 7 |
| 26.2.0 | 684 / 685 | 640 / 640 | 347 / 7 |
| 26.1.2-fabric | 1195 / 1196 | 1164 / 1164 | 296 / 13 |
| 26.2.0-fabric | 951 / 952 | 919 / 919 | 263 / 13 |

逐项目 header 多重集对比已冻结：NeoForge 移除49条、新增4–6条；Fabric 移除37条、新增4–6条。忽略位置和专属安装根目录，不忽略代码/首行消息/多重数量；不宣称所有原错误来自此修复。新增项包括深度边界上 MemoryLayout.PathElement、Provider.Service 等真实符号缺失，以及测试 TS 中实际 console 缺声明；未删默认 includes、隐藏负例、修改生成正文或假造别名来消除它们。ParticleOptions 的真实类 Binding 在 TS 中仍被当作仅类型，是后续修复点。手写声明生产器和全部旧错误仍保留。

后续先修复实际 console/接口类值声明及依赖闭包，再解决 Python 字段/调用双行为、剩余领域/第一帧/其他节点客户端/文件并发窗口与精确源码性能。维护者结论仍 **NOT_RECORDED**；自动续跑 **PAUSED**；工具权限字段问题 **UNRESOLVED**。A22/A28 范围及发布授权边界未改变，用户委托自动测试不构成人类验收或发布许可。

独立复核：`python docs/architecture-refactor/evidence/ticket37-ide-input-closure-repair/verify.py`。只校验本包的真实数据和保留的失败，不把报告完成当完整 IDE、性能、多人、视觉或维护者验收通过。归档排除 world、密码、私钥、二进制 JAR、原始 JFR 和进程命令行；五个实际 RCON 密码及私钥标记扫描通过。
