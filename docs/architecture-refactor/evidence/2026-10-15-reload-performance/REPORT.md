# 重载性能修复与验收证据

日期：2026-10-15。基线提交：`8ed398dd2a4877bf1382000e1b811eb2d667c381`；固定实现提交：`e473479af6dcb0f9483b36a0d93207721904025f`。

## 结论与实现

原基准的两组重载均值都超出 285.3 ms 门槛。修复后两组均通过，合并十个样本的均值从 375.6 ms 降到 221.4 ms，下降约 41%。启动复验也通过原有 41173 ms 门槛。结果仅代表本机、既定 fixture 和 NeoForge 26.1.2 的这轮测量；不是所有整合包、平台或设备的性能承诺。

原因是每个候选代重新执行了未变更内建模块的纯编译转换。新增内部 `BuiltinModuleTransformCache`，由 `NekoModulePipelineCache` 根对象持有，在候选代之间复用不可变 `NekoPreparedModule`。最多保存 256 个路径条目和 8 Mi 个字符，字符预算涵盖原始源码、产物代码和源码映射；这是保留字符预算，不是精确的堆内存字节上限。超限淘汰最久未使用条目，大产物不保留。同一路径仅保留一个版本。

复用只允许精确的内建 JavaScript/TypeScript 单例；相同语言 id 或扩展名不能让自定义语言插件/旧编译器进入缓存。输入比较涵盖规范路径、完整源码、语言/模块模式、插件实例和注册表修订号。每个根对象只持有一个配置不可变的管线，因此配置隔离由根对象边界保证，独立根对象不共享条目。

显式路径失效、按 Script Type 清理及全清会淘汰对应纯产物；关闭根对象终止保留。编译在短缓存锁之外执行，失效纪元阻止已开始的编译在 clear/invalidate/close 后重新填入。编译失败不缓存。关闭单个候选代不会释放根对象的纯产物。

## 影响的行为与契约

- Script API、Plugin API、配置项、声明和 wire 格式没有修改。
- 每个新代仍重新读取文件、检查内容指纹、授权和包目录限制，重新捕获并检查编译器修订号，再使用当前代的 Binding Schema 做两种静态校验。缓存位于纯转换阶段，原始 JS/转译后 JS 的校验顺序保留。
- 每代仍创建自己的 Context、模块执行状态、源码映射、虚拟模块和观察器，执行脚本并登记监听器。纯产物命中也会发布当前会话的源码映射和准备观察结果。
- 自定义编译器和语言插件在每个新代继续运行；候选失败保留 active、单次提交、清理及线程顺序仍走既有生命周期。
- `common` 没有增加 Minecraft/loader 依赖、公共入口或额外依赖。没有修改版本生成物。

## 正式性能结果

原 `bench/perf/sample.ps1` SHA-256：`1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`。原 fixture 摘要与基线完全相同。JDK 25.0.2、NeoForge 26.1.2、Gradle 缓存 `D:\mcmodDemo\NekoJS\.gradle-perf02`、25871/25872 端口不变。全部采样串行，期间没有并行构建、测试或其他本任务的游戏实例。

| 组 | 基线正式样本（ms） | 基线均值 | 修复正式样本（ms） | 修复均值 | 原门槛 | 判定 |
| --- | --- | ---: | --- | ---: | ---: | --- |
| reload-1 | 496, 402, 282, 300, 420 | 380.0 | 252, 268, 219, 219, 114 | 214.4 | 285.3 | PASS |
| reload-2 | 374, 345, 404, 313, 420 | 371.2 | 295, 126, 265, 220, 236 | 228.4 | 285.3 | PASS |
| startup | 26691, 26450, 25664, 26104, 26900 | 26361.8 | 13057, 19060, 18936, 19029, 18509 | 17718.2 | 41173 | PASS |

预先指定的两组各五次重载完整保留，没有删首值、择优选组或为成绩重试。startup 保留原先两次 warmup 加五次 formal。三次采样器直接退出码均为 0；重载记录全部包含 `phase=COMMIT - no errors.`。所有会话正常 RCON 停服，`killed` / `forced_kill` 为 false，没有超时或缺失 marker。

外层采集器固定 clean checkout 和实现提交，验证运行路径不存在重解析点及端口空闲，记录 fixture、制品和编译类摘要。所有编译类的采样前后 SHA-256 完全一致。基线失败结果保留原档，未改为通过。

重载指标是原采样器的 marker 耗时：包含启动 RCON 客户端、连接、命令返回和观察日志的时间，有 100 ms 轮询粒度；marker 位于 tick fixture 顶层输出处，不是独立的完整提交时间戳。提交成功另由 RCON 回执确认。startup 是从启动入口到 Done 的墙钟，包含 Gradle/加载器和文件缓存的影响。尽管沿用相同 warmup，前后系统缓存热度并非实验室控制变量；启动改善不全部归因于这项代码修改。

## 调查与被撤回的试验

GPT-6 Luna xhigh 只读调查采样含义和模块准备路径，禁止写文件、构建或采样；全部实现和测量由主代理完成。

阶段诊断单独保留初始候选，之后 20 次命令的候选阶段均值约 213.53 ms：脚本执行阶段 171.53 ms、环境创建 26.98 ms、Binding 安装 8.12 ms、提交 2.14 ms。这里的脚本执行阶段包含模块解析、准备及 guest 执行，不能解释为纯 guest 时间。命令中 root 平均 214.18 ms、配方应用平均 36.93 ms。

五次不同诊断运行各保留 20 个 RCON 观测，均正常提交和停服：

| 诊断 | RCON 均值（ms） | 用途 |
| --- | ---: | --- |
| 基线 JFR | 284.70 | 识别主路径 |
| 基线深栈 JFR | 299.44 | 模块准备/配方子路径 |
| 配方 RegistryOps 复用 | 322.85 | 没有收益，已撤回 |
| 临时阶段计时 | 325.15 | 定位候选脚本阶段；含计时扰动 |
| 内建转换复用 | 158.20 | 与深栈基线同设置的诊断改善 |

以上均为诊断数据，不能替换正式成绩。JFR JSON 导出的默认栈深为 5；后续用 `--stack-depth 128` 导出以读取调用链，不能把默认导出截断说成实际录制只有五层。临时 `[DEBUG-RELOAD-15]` 计时已撤回，没有进入实现提交。

Luna 还确认两种校验器各解析同一源码，以及事件校验的重复子树遍历。本次未修改它们：单项转换复用已满足正式门槛，进一步修改需要独立测量和校验等价性回归。

早期采集故障也保留：PowerShell 5 把 JDK stderr 警告处理为异常；外层 native stdout EOF 挂起导致一组 268.2 ms 的数据缺少直接退出码；用户 steering 中断不完整组；Windows 模块搜索路径导致 `Get-FileHash` 缺失；未提前缓存 Process.Handle 导致直接 ExitCode 为 null。它们不是合格正式样本，其中一个完整但退出码缺失的组均值为 395.4 ms。外层采集器修正进程及输出处理，未修改原采样器。缓存试验的第一次诊断启动也因外层路径清单表达式出错在启动游戏前拒绝，修正后才测量。固定提交的正式复测未发生采集重试。

## 检查命令与结果

构建验证在独立 checkout 进行，其五个修改文件与实现提交一致；固定提交后又执行目标节点 build。Windows 使用 `gradlew.bat`，JDK 设置为 25.0.2。

| 命令 / 检查 | 结果 |
| --- | --- |
| `:common:test --tests '*BuiltinModuleReuseTest'`，实现前 | 预期失败：7 项中 3 项暴露缺少跨代复用；保留 red 日志 |
| 模块准备、trust、类型清理及新增缓存聚焦测试 | PASS；第一次运行的两个测试 fixture 问题（缺少 TS 注册、映射 fallback 非 null）修正后复验，没有削弱生产门禁 |
| `:common:check :common-api-processor:test guardLint` | PASS；common 2314 项、0 失败、4 环境 skip；含隔离门禁 |
| `:1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build` | 五节点全部 PASS |
| `:1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest` | 三组 PASS |
| `python tools/nekojs-ci-gates.py source-roots --node <node>`，五节点 | PASS |
| `python tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json` | PASS，零失败项 |
| `npm run test:probe-types` | PASS |
| `:26.1.2:runGameTestServer`，同时断言本次 required success marker | PASS：`All 1 required tests passed`，退出码 0 |
| 固定 `e473479a` 后 `:26.1.2:build` | PASS |
| 原 sampler：两组 `-Mode reload -Reloads 5`；一组 `-Mode startup -Warmup 2 -Samples 5` | 直接退出码 0，性能门槛全部 PASS |
| `python docs/architecture-refactor/evidence/2026-10-15-reload-performance/verify.py --candidate` | PASS：原始数据完整性、冻结输入、回归记录及原性能门槛 |
| 最终 diff / 临时计时标签检查 | PASS；既有改动与资料未进入提交 |

新增 12 项测试覆盖跨代内建复用、会话映射/观察器发布、等长同时间戳修改、自定义编译器及相同 id 自定义语言插件仍运行、当前代 schema、实时授权/包目录限制、路径/type/full clear、独立 owner、LRU/字符预算、失败不保留、注册表变化和编译期间失效/关闭。完整 common 回归同时覆盖候选失败保留、清理、线程及 watchdog 等既有生命周期。

4 项 skip 原因：两个文件定位测试涉及 Windows 禁止的 `?` / `:` 文件名；两个 Probe IR 测试需要 common 测试 classpath 不包含的 `RecipeEventJS`。未改测试条件或 golden。

## 证据与剩余范围

- `baseline-evidence.zip` / `summary.json`：固定基线、失败采集和原始正式数据。其性能结论仍为 FAIL。
- `diagnostics.zip` / `diagnostics.json`：JFR、原始日志、阶段数据、撤回试验及 red/green 记录。
- `candidate-evidence.zip` / `candidate.json`：固定实现的正式样本、直接退出码、冻结制品/类/源码摘要、验证日志和测试统计。
- `verify.py` 默认只验证基线数据完整性；加 `--candidate` 同时验证修复档案并强制要求原性能门槛 PASS。输出中的基线 FAIL 不是修复后的 FAIL。

归档前扫描了凭证模式，未命中；没有归档 world、server.properties 或带认证值的诊断启动脚本。本轮没有修改社区票据状态、实施 Fabric 资源桥或发布制品。

未运行：其他四个节点的性能采样、真实客户端 UI/视觉验收和大型整合包测量。既有单项 GameTest 只作加载器冒烟，不能替代十次正式 RCON 重载及 common 的生命周期回归。维护者人工验收结论仍待维护者填写，没有代签通过。
