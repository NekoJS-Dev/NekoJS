# 2026-10-18 路径耗时与线程调度对照

正式判定仍为 `6e5bb8f0`：重载 466.4 / 469.4 ms，FAIL；启动 34110.6 ms，PASS。用户确认原验收环境已无保留，继续按原 285.3 / 41173 ms 门槛排查。本次没有替换正式采样器、删首值、放宽门槛或修改生产源码。

## 组件观测

所有组件复用固定六入口、真实 common root / Sandbox / Node / 文件系统和独立 Context；缺少 Minecraft、完整插件绑定、配方和 RCON。每次均确认 COMMIT、无 root 错误、Context 更换及旧 Context 拒绝 eval。三十次及首值全部保留；“后二十次”只是预定分段描述，不是正式分数，也不能与其他进程的后段直接作因果对照。

- CPU 分段：三十次墙钟 / 当前线程 CPU / user CPU 均值为 260.14794 / 240.10417 / 160.9375 ms；后二十次为 141.54371 / 133.59375 / 58.59375 ms。CPU 与 user 相减只是粗粒度系统态估计；读数并非同时取得，不能把它直接分配给某项 API。
- 路径归因：仅在临时目录编译两个 shadow 类，主工作区源文件和构建类不变。每次规范路径解析 24 次、目录包含校验 6 次；后二十次墙钟分别为 19.85523 / 11.39824 ms，root 为 129.90129 ms。两项解释不了全部开销，CPU 小窗口读数的粒度也不足以精确拆分系统态。
- 原生方法采样：追加 NativeMethodSample、ExecutionSample 及零阈值文件 I/O 事件。后二十次中 52 个原生样本和 59 个执行样本，原生叶栈较多为 FindFirstFile / FindClose；476 条主线程文件读取的累计时长是 I/O 事件统计，不是读取次数等于独立源文件数，也不是线程独占 CPU。**样本数不是毫秒**；inclusive 帧不可相加。原始 JFR 留在本机，不做二进制凭据替换。

Luna xhigh 只读核对未找到可安全删去的真实路径复验：resolver 的候选后复验、trust 的 covers、读前后检查仍覆盖重定向边界。唯一小候选为合并 FileStamp 的两次属性查询；尚无足够收益证据，未实施。

## 核心位置与临时线程策略

通过 Windows [GetSystemCpuSetInformation](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getsystemcpusetinformation) 查询本机二十个逻辑处理器：0–11 的 EfficiencyClass 为 1，12–19 为 0。[结构文档](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-system_cpu_set_information)说明较大值对应更快、能效较低的处理器。只读位置探针使用 [GetCurrentProcessorNumber](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getcurrentprocessornumber)；第 1 次结束以及随后 29 次前后观测均落在 class 0。边界观测不能证明整个重载从未迁核，也不能反推过去游戏会话的位置。

另开组件进程，先保留十次原策略，然后二十对 original / HighQoS 轮换顺序，全部保留。同一 root、Engine、源码与 fixture 下，原策略均值 113.45917 ms，临时 HighQoS 为 40.384975 ms；HighQoS 的结束位置均为 class 1，原策略结束位置均为 class 0。初始线程策略 `[1,0,0]`，每条后恢复并读回，最外层 finally 再恢复并记录相同策略。

这里使用 [SetThreadInformation 的官方 HighQoS 例子](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-setthreadinformation)：只对诊断进程的当前执行线程设置 execution-speed ControlMask=1、StateMask=0；不是开启 EcoQoS。未改 CPU 亲和性、进程优先级、电源方案、系统配置或生产代码。组件结果支持线程策略影响本次耗时，**尚不证明正式游戏的历史回归原因**。

## 完整游戏对照

真实游戏对照另行记录。保持原 RCON 客户端、fixture、三秒间隔及原 marker 读取顺序；只临时控制专用游戏的 Server thread。通过 RCON 监听端口定位游戏 PID，再用 jcmd Thread.print 识别原生线程 ID；每次操作核验所属进程及线程创建时间，拒绝 ID 重用。每条后及 finally 均恢复初始线程策略，正常 RCON 停服后核验实际正式编译类不变。

二十对轮换顺序、四十条及首值全部保留，均 COMMIT、无脚本错误。结果如下：

| 临时线程策略 | RCON 均值（ms） | 原 marker 均值（ms） | 观测窗口线程 CPU 均值（ms） |
|---|---:|---:|---:|
| original `[1,0,0]` | 255.434335 | 319.15904 | 178.125 |
| HighQoS `[1,1,0]` | 125.58642 | 230.872235 | 58.59375 |

二十个 original−HighQoS 的配对 RCON 差均为正，范围 30.486–263.1599 ms，均值 129.847915 ms。线程 CPU 窗口从策略读取开始到 marker 读取后的策略再读取结束，包含期间正常 tick 和探针开销，**不是纯重载阶段计时**。本轮游戏未在执行中采集核心位置，不能把组件位置观测移记为游戏线程迁核证据。

这一受控对照支持 Windows 自动线程策略对当前完整游戏重载有重要影响。HighQoS 的诊断 marker 均值低于原 285.3 ms 门槛，但这是同一长会话中轮换策略的四十条诊断，**不是原正式两组各五次及启动组**。既有正式 FAIL 不变；同样不能把这轮 original 的较低分数替换此前正式成绩，也没有证明所有历史差距均由调度造成。

最终恢复 `[1,0,0]` 并读回，正常 RCON 停服；实际 1625 个 main 类在运行前后均与原正式测量逐个摘要相同。工作树清洁，原采样器摘要未变。本轮会话启动墙钟 44469 ms，只是该诊断会话记录，未组成正式启动组。

## 独立复核

```powershell
python docs/architecture-refactor/evidence/2026-10-17-reload-performance/cpu-placement/verify.py
```

验证器检查归档完整性、1152 个 common 类的正式输入绑定、两份 shadow 原始/插桩源码摘要、固定 fixture、全部 Context 断言、组件分段和二十对策略均值，以及游戏四十条命令的顺序、策略读回、线程身份、均值和正常停服。JFR 的计数摘录在归档内作一致性检查；从事件重新提取仍需要本机原始 JFR，其输入摘要已保存。

归档共 99 项、427630 bytes，SHA-256 为 `b7606aaf1ef7daf76f0bb5174d009f61a48e4255fffb77d390bd2aef49f10f11`。文本中匹配凭据字节的内容按 redactions.json 脱敏，包含部分 fixture 标识；封存器在脱敏前核对原始 fixture 摘要。归档能独立验证脱敏后字节完整性和原摘要绑定，不能在没有被删字节的情况下重算这些原始 fixture 摘要。

首轮复核器误把脱敏后的 fixture 与原始摘要直接比较，失败于该断言；修正为分别核验归档字节、原始摘要绑定和替换次数，未改变归档、测量数据或性能门槛。修正后的完整性与独立均值重算 PASS；正式性能判定仍为 FAIL。

## 合同与缺口

Script API、Plugin API、wire、数据格式、信任策略及重载生命周期不变。新增内容是可复核的诊断，未交付生产优化。性能正式门禁、脚本包原生内存与真实客户端验收仍未完成；不以组件或改变调度条件的诊断分数代替正式通过。
