# 2026-10-18 CPU 与入口校验对照

**正式结果仍是 `6e5bb8f0` 重载 466.4 / 469.4 ms，FAIL；启动 34110.6 ms，PASS。** 用户确认原验收环境已无保留，继续按原 285.3 / 41173 ms 门槛排查。本包不更换指标、不择优重跑正式组，也没有新增生产优化。

## 十条完整游戏重载

主代理只在专用性能工作树的命令入口临时加入 `[DEBUG-CPU-18]`，分别记录 root / recipe 的墙钟、当前线程 CPU、进程 CPU、GC 累计计时和 JVM 编译累计计时差。仍使用原 RCON 客户端、六个固定入口和三秒间隔，十条及首值全部保留；全部 COMMIT、无脚本错误、正常 RCON 停服。

| 阶段 | 墙钟均值（ms） | 当前线程 CPU 均值（ms） | 进程 CPU 均值（ms） | GC 计时增量均值（ms） | JVM 编译计时增量均值（ms） |
|---|---:|---:|---:|---:|---:|
| root | 188.93839 | 170.3125 | 478.125 | 0 | 244.1 |
| recipe | 55.52521 | 56.25 | 146.875 | 0 | 131.4 |

RCON / marker 均值为 322.68731 / 370.71310 ms，RCON 中两个阶段以外的差额均值 78.22371 ms。差额包含客户端启动、调度、回执和诊断自身的工作，不能全部归给某个等待点。

计数更支持优先检查 CPU 工作，未支持把 root 的大部分耗时归为阻塞等待。线程 CPU 在本次原始记录中呈约 15.625 ms 粒度，单条 recipe CPU 高于墙钟也不能解释成超过一核的执行；首次元数据捕获存在初始化开销。进程 CPU 和编译计时包含并行后台线程，**不能相加到墙钟或互相当作独占耗时**。窗口中 GC 累计计时未增长，不证明整个会话没有 GC，也不能用这轮不同会话的分数声称生产变快。

复读此前 `6e5bb8f0` 的原始有效 JFR，在原二十条命令窗口内得到 74 个 root 线程样本：叶栈较多落在 Windows 路径处理，其余分散在 guest 执行、校验及 source map 解析；样本数不是毫秒。该窗口内记录的编译事件含配方解码、classfile builder、Truffle 对象写入及校验器，记录总时长约 1438.57 ms。它说明存在多来源编译，**不证明编译本身是当前回归原因**，也不支持据此直接改 JVM 参数。

原始 JFR 保留在本机，归档只收录输入摘要、窗口、分析器及文本结果，没有对二进制作凭据替换。封存验证器能独立重算 CPU 和组件观测；JFR 叶栈重算仍需本机原始记录，归档中的摘要及与既有文本相符的 74 样本仅作绑定和一致性检查。

## 入口重复校验的组件对照

`ScriptExecutor` 每次清错后都会重新执行 GlobalBinding 校验；管线 miss 时还会对原始 JS 再校验。直接删除前者会损坏同代缓存命中后的错误重报；跳过后者还必须确认重新读取的源码、物理路径和 schema 快照相同，保留依赖和转译产物的校验，以及各次报告副作用。

在既有 common root fixture 中，仅比较六份预读源码的两次校验，与一次校验加捕获/重放报告。十对轮换顺序，每臂每对二十轮：约 10.89752 / 5.52306 ms，差 5.37446 ms / 六入口。该 fixture 缺少真实 Minecraft 的 Item / Utils / JavaMath 绑定，实际产生三条相关诊断，已完整保存；**不是完整游戏 schema 的性能成绩，也不是所有场景的严格收益上界**。它只量化了此组件去重的机会，没有实现或承诺跨边界复用凭据，没有缓存当前信任或跨代 guest 状态。

这个收益不足以解释已测正式差距，目前不为它增加跨执行器、loader 和管线的凭据协议。HostAccess 也已在 factory 构造时复用，不是每代 build 的重复项。

## 失败、更正及还原

第一次诊断写入器使用系统默认 cp936 写 Java 源码，导致六个源码追踪测试类中的十项检查以 MalformedInputException 失败。归档保留错误编码文件、首次日志和全部该 test 任务 XML：671 tests、10 failures、0 errors、81 skipped。该失败由本次诊断工具引入，不能归为历史或外部失败。

改为显式 UTF-8 后，诊断节点 build 通过；封存全部节点 test-results 为 674 tests、0 failures/errors、81 skipped（比首次快照多包含三个 platform gate 测试）。随后还原原始源码字节、再次节点 build 通过，清洁工作树，**1625 个 main 类与原正式测量逐个摘要相同**；诊断内部类和标记均消失。主工作区生产源码未改，Script API / wire / 数据合同未变。

另用 Windows [GetProcessInformation](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getprocessinformation) 及其 [throttling state](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/ns-processthreadsapi-process_power_throttling_state) 做了只读策略查询：当时仍在的 Java 进程与查询器的 ControlMask / StateMask 都为零。游戏已退出，不能追认其策略，也不能据此排除所有主机调度因素。没有修改电源方案、进程优先级、QoS、亲和性或 JVM 参数。

```powershell
python docs/architecture-refactor/evidence/2026-10-17-reload-performance/cpu-and-preflight/verify.py
```

完整性与独立重算 PASS；不扩大到正式性能通过。归档 333 项、744538 bytes，SHA-256 为 `1830ecfe23438490649852b9e0168444fd349250db280860868c1e2b28c28683`。文本凭据匹配按 `redactions.json` 脱敏。
