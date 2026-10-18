# 2026-10-17 后续工作目标与进度

用户确认按以下顺序推进；实现、测试及提交由主代理完成，Luna xhigh 仅作明确范围内的只读调查。不创建 PR，不创建新 Issue，保留其他贡献者的工作区改动。

## 1. 重载性能（进行中）

- 当前测量起点：`f41db5d9819df7f501069163d2cbb7a0397b7d6e`。
- 复用本任务已附加的专属性能工作树，使用原 `bench/perf/sample.ps1` 与 fixtures、NeoForge 26.1.2、JDK 25.0.2、固定 Gradle 缓存及 25871/25872 端口。
- 预先指定两组各五次重载，一组两次预热加五次正式启动；原门槛分别为均值 285.3 / 41173 ms。不改计时口径，不删首值，不按成绩择优重测。
- 正式测量期间不并行运行本任务构建、测试或其他游戏。测量绑定精确源码、采样器、fixture 和实际编译类，完整保留失败记录。
- 正式结果与定向诊断分开；基于当前热点实现可归因的修复，保留授权、当前代校验、独立 Context、候选失败保留和资源所有权，再按原标准复验。
- 历史情况：`e473479a` 的纯编译产物复用曾通过；后续 `0965e29b`、`6faed0e9` 又失败。历史通过不能代替当前通过。

完成条件：当前最终源码的两组重载与启动均通过原门槛；影响路径的回归、隔离检查及节点验证通过，并提交可重算的原始证据。

本轮进度：

- `f41db5d9` 两组重载均值为 500.4 / 462.8 ms，均 FAIL。启动组原始正式值为 59601、59914、50502、null、57501 ms；预热 2 与正式 6 未到达 Done，整组 INVALID，不能取剩余四值判定。Gradle daemon 记录了两次客户端断连并取消运行任务，断连原因未确认。
- 二十次独立诊断：root.reload 平均 163.98 ms、配方重应用 35.17 ms；六入口解析 52.91 ms、模块准备 46.03 ms。后两项有嵌套耗时，不能与其子项相加；JFR 只统计命令窗口，采样次数不是独占毫秒。
- 实现提交 `a09e5bc2` 在单次解析内复用根规范形式，保留候选检查与最终真实路径复验。没有跨代缓存文件路径、信任判断或 guest 状态，没有 Script API / wire / 数据变更。
- 同一 JVM 内十对交替组件测量：六路径 30.88 → 10.22 ms，节省 20.67 ms；六身份和五拒绝场景相等，实际检查符号链接。组件收益不替代正式门禁。
- 最终测试 XML 共 4957 tests、0 failures/errors、271 skipped；17 项解析器测试均执行。common:check、处理器检查、guardLint、五节点 build、最终四项 CI gate 和 NeoForge 26.1.2 required GameTest 均通过。
- 新增的 ZipFS 探测曾使首轮 common 检查失败；精确旧源码对照和新实现对同一输入均为 ProviderMismatchException，因此没有把未支持的输入能力当作本轮承诺。保留失败 fixture / XML / 日志与对照，最终回归改测相关的文件删除后拒绝边界，再运行完整 common 检查；既有门禁未变。
- `a09e5bc2` 的原标准正式复验：两组重载 442.6 / 338.0 ms，均 FAIL；启动五正式值 26119、26027、26718、26584、26528 ms，均值 26395.2 ms，PASS。所有正式会话均正常 RCON 停服；不能据此宣布性能目标完成。
- `a09e5bc2` 二十次后续定向诊断中，manifest 读取仅约 0.88 ms，含每 Context eval 的整体约 9.08 ms，不支持优先增加清单缓存。新 Context 约 25.60 ms；信任批准约 18.98 ms / 六次、规范路径约 46.84 ms / 三十次。前五次 root / recipe 均值 206.16 / 70.19 ms，后十五次 185.71 / 48.88 ms；这些是带诊断的独立会话，不能与不同会话正式分数直接作修复因果对照。第一次诊断在首条 RCON 命令处失败，无重载样本；保留后修改诊断错误捕获方式，第二次二十条均 COMMIT，正常 RCON 停服。没有重试正式组以选取成绩。

配方 no-op 只读调查没有找到足够的已有状态来安全早退：需要同时保留旧配方还原、脚本与插件前后回调、数据包资源重载、ingredient actions 清理和客户端行为。另观察到现有 base/raw JSON 为浅复制，不能直接当作不可变基线。没有据此改配方生产源码，也没有以跳过配方处理来降低固定工作负载。

后续进度：

- `6e5bb8f0` 修复本地信任批准的主体切换：直接绑定已通过保护根检查的规范主体，保留后续 covers 和读前后校验。红绿回归成立；精确旧/新组件对照约节省 5.61 ms / 六文件。完整 common/处理器检查、guardLint、五节点 build、四项 CI gate 和 required GameTest 均 PASS；冻结 XML 为 4959 tests、0 failures/errors、271 skipped。
- **最新正式结果为 `6e5bb8f0`：两组重载 466.4 / 469.4 ms，均 FAIL；启动 34110.6 ms，PASS。** 不以旧 a09 的较好成绩替代当前结果。原始样本、失败记录、源码绑定及独立复核器见[本轮证据](../architecture-refactor/evidence/2026-10-17-reload-performance/trust-6e5bb8f0/README.md)。
- Python 客户端启动诊断：二十对正常启动 / `-S` 子进程，仅导入 RCON 所需模块而不连游戏，约 41.25 / 36.70 ms。没有修改原采样器或 Python 环境。
- CommonJS 隔离原型：共享 Engine、每轮新 Context、六最小模块，十对交替测量；动态 Function / 复用 Source 约 0.507 / 0.267 ms，执行段均未调用沙盒 `toRealPath`。直接 Source 包装改变 `this` 语义，因此否决该替换。最小模块不代表真实 fixture 的全部行为，但足以反驳把文件系统开销直接归因于 sourceURL；继续定位真实调用。原型未进入生产代码。
- 八个固定正则预编译的组件对照仅节省约 0.0166 ms / 六源码，未修改生产。已建立生产 root / Sandbox / Node / fs 的快速闭环，原六 fixture 连续三十次 COMMIT、每代新 Context、旧 Context 关闭后拒绝 eval；补真实日志桥后留存实际脚本日志。该诊断缺少 Minecraft、完整绑定、配方及 RCON，不替代正式门禁或包内存验收。[原始对照和失败记录](../architecture-refactor/evidence/2026-10-17-reload-performance/diagnostic-controls/README.md)已归档；下一步在完整游戏环境补阶段计时。
- 完整游戏 `6e5bb8f0` 二十条阶段诊断已完成：root 104.39 ms、recipe 29.74 ms、RCON 209.15 ms；JSX eval 0.88 ms、managed globals 0.70 ms，均不足以优先改造。模块规范化 24 次，符合已实现的主体修复；全部 COMMIT、正常停服。插桩按摘要还原、重新 build 通过，1625 个 main 类无诊断标记。该会话没有正式 marker 指标，不改判此前 FAIL；下一步分开观察命令/回执/日志可见延迟。[证据与旧 JFR 回读限制更正](../architecture-refactor/evidence/2026-10-17-reload-performance/current-phase-profile/README.md)。
- 2026-10-18：完成十对查询/重载控制及十条三日志 sink 诊断。后者的每条 marker 在游戏日志都先于 Gradle 转发 stdout 可见，均值相差 80.04 ms（48.33–110.51 ms）；root / recipe 为 64.40 / 18.55 ms。原 marker 发生于脚本执行中，原采样器仍先等完整 RCON 返回再轮询 stdout；不把阶段计时或直接游戏日志替代原指标，不将不同会话差异声称为生产优化。两轮正常停服，插桩恢复、重新 build 和类扫描通过；[完整原始证据与重算器](../architecture-refactor/evidence/2026-10-17-reload-performance/output-visibility/README.md)已保存。继续检查 Gradle 转发机制，正式重载目标仍 FAIL。
- [调用链控制](../architecture-refactor/evidence/2026-10-17-reload-performance/invocation-controls/README.md)：同一原客户端的空响应端点二十对原 native / 直接进程调用，以及二十轮 C/D 路径四臂对照，共 120 条成功控制命令，原 native 约 48–50 ms、C/D 差不到 1 ms；不支持这两项解释正式数百毫秒 RCON。实际 Gradle 字节码确认 plain 也经默认 100 ms 节流，daemon dispatcher 空闲 sleep 为 10 ms；没有修改内部参数或正式口径。下一步重建精确历史源码同机对照；旧 worktree/二进制缺失，不能仅据缓存存在声称可复现。
- 历史正式 RCON→marker 均值增加 89.0 ms，当前增加 52.8 / 64.4 ms；当前 RCON 自身已为 413.6 / 405.0 ms，主要回归在返回前。因此去掉转发等待也不能使已测两组通过，不继续把输出节流当作主要生产修复方向。[旧源码 `3400e97e` 同机重建对照](../architecture-refactor/evidence/2026-10-17-reload-performance/historical-reference-3400/README.md)已完成：启动 43231.6 ms、两组重载 430.6 / 452.6 ms，均超过原门槛，九会话正常停服。旧源码生产代码未改，但原 world / 二进制缺失、使用本次重新构建和新建 world，因此不证明环境是唯一原因，也不替代当前正式 FAIL。
- [游戏内四十条调用方式控制](../architecture-refactor/evidence/2026-10-17-reload-performance/real-invocation-control/README.md)：同一原客户端、相同 1625 个正式编译类，原 native / 直接 .NET Process 的 RCON 均值为 265.54 / 251.00 ms，marker 为 352.37 / 330.46 ms；二十对轮换顺序，差值有正有负，两臂均超过门槛。没有据此改调用器或重判正式结果。后续回到模块准备中的可归因重复工作；HostAccess 已在 factory 构造时复用，不是每代 build 的重复项。
- 用户确认原环境已无保留，继续按原门槛排查。[十条 CPU 诊断](../architecture-refactor/evidence/2026-10-17-reload-performance/cpu-and-preflight/README.md)的 root 墙钟 / 线程 CPU 为 188.94 / 170.31 ms，recipe 为 55.53 / 56.25 ms（CPU 计时较粗）；GC 窗口计时未增长，进程 / 编译计数含并行工作，不能与墙钟相加。现有 JFR 复读未定位单一原因。入口重复校验的 common 组件原型约节省 5.37 ms / 六源码，schema 不含完整 MC 绑定；暂未引入跨边界凭据协议。诊断工具首次默认编码造成十项源码追踪失败，显式 UTF-8 后节点 build 通过；原始源码恢复并重建后 1625 个 main 类与正式版本完全相同。正式重载仍 FAIL，继续定位必要 CPU 工作中的重复初始化，保留原检查和公开行为。

- [路径与线程策略对照](../architecture-refactor/evidence/2026-10-17-reload-performance/cpu-placement/README.md)缩小了诊断范围：每次组件 root 的 24 次规范化与 6 次包含校验，后二十次分别约 19.86 / 11.40 ms，解释不了全部开销；未删安全复验。只读位置观测落在较低性能 CPU 类别。组件二十对临时 owner-thread HighQoS 对照为 113.46 → 40.38 ms；随后同一未改动游戏代码的四十条诊断中，original / HighQoS 的原 marker 均值为 319.16 / 230.87 ms，RCON 为 255.43 / 125.59 ms，二十个配对 RCON 差均下降。只控制专用游戏 Server thread，每条后及 finally 恢复原策略，正常停服；1625 个正式类不变。这支持 Windows 自动线程策略是当前耗时的重要因素，但改变运行条件的诊断不能代替原正式两组和启动组，正式 FAIL 仍保留。下一步需在明确、相同的运行条件下复验当前与旧源码，保留原采样器、门槛和失败记录。

## 2. 脚本包最终验收（资源自动化通过，实机与原生内存未完成）

复用 [A–E 交付与五节点证据](2026-10-14-script-pack-final-verification.md)，补齐多包独立 Context 的内存成本、重复重载的资源释放、客户端双世界切换、签名同步实机验证。自动化、实际客户端观察与维护者结论分别记录；缺少人工结论时不代签。

2026-10-18 新增五项资源检查通过：1/4/8 包活动 Context 为 2/5/9、替换峰值 4/10/18；四包五十次重载中旧 Context、注册表、事件监听器和 timer scheduler 均释放，最终观察到 250/250 个旧 Context 回收；五次 DOMAIN_PLAN 拒绝保留健康代并成功恢复。[资源验收证据](../architecture-refactor/evidence/2026-10-18-pack-resource-acceptance/README.md)保留 Java 堆窗口。首次 common 检查遇到语言文件原子替换 AccessDenied；失败已归档，单测控制和未改源码的完整复验通过（2375 tests、0 failures/errors、4 skipped），根因未知。原生内存、真实客户端双世界和签名同步仍缺证据，不能标为整体验收通过。

只读核对确认当前没有可直接重放的真实客户端切世界 / 签名网络验收脚本。`PackActivationTest` 的 A→B→A 为 common SERVER 类型语义测试，`PackSyncClientTest` 直接调用接收处理器；已有跨进程实机 fixture 是授权无签名包。可复用这些断言和现有 NeoForge 26.2 客户端启动记录，但仍需真实客户端控制和带测试签名密钥的网络 fixture，不能将这些逻辑测试补记为实机通过。

## 3. 社区事项收尾（本地核对已完成，外部收尾未完成）

逐项核对 #60、#63 和 PR #61/#65/#66 的当前正文、讨论、提案差异及 stonecutter 实现，记录关闭或保留的依据。它们目标为 master；适配到 stonecutter 不等于原 PR 已合并。外部评论属于向他人发送消息，需要用户明确授权；可以先完成本地核对与结论。

已保存[逐项核对与保留依据](2026-10-17-community-closeout.md)和 GitHub 最新读取快照：两 Issue / 三 PR 仍开放，三个 PR 均未合并。#63 建议 needs-info，其余建议 ready-for-human；标签尚未外部应用。#63 的注解诉求/注册 API 取舍、外部 RPC 实际集成，以及 #61 精确独立语料仍有差距，因此没有关闭任何一项。

## 4. Fabric 目录包资源桥（待进行）

为 Fabric 目录脚本包挂载资源，并接入已有自动物品模型生成，使 `parentModel`、`texture` 能通过实际资源加载生效。先调查现有资源所有权、生命周期和节点 API，再实现共享机制及必要节点适配；目录包不冒充已有 MOD JAR 挂载证据。

完成条件：受影响 Fabric 节点构建及运行验证通过，资源覆盖顺序、重载和卸载行为明确，自动生成模型有实际客户端资源证据。

## 验收边界

每阶段分别记录通过、失败和未运行项。发布、最终回滚政策和真实维护者验收不由代理代签；当前总目标仍未完成。
