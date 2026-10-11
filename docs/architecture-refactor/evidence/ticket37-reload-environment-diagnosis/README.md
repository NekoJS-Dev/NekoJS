# 隐藏后台验收与重载性能对照

当前正式性能结论仍为 **FAIL**，根因未确认。没有生产代码变更。此次在专属服务端继续诊断，并按用户要求避免客户端窗口操作：采样器使用独立隐藏控制台，普通控制台子进程继承隐藏控制台；没有启动 GUI 客户端、输入点击、激活窗口或切换桌面。现有工具没有已验证的另一图形桌面入口；本机 Docker 引擎未运行，没有为此启动桌面应用。后台运行仍共享 CPU/内存，不能保证资源影响为零，也不能把它称为另一个桌面。客户端视觉验收没有新增 PASS。

三次会话均在 `build/ticket37-perf-owned-ac810fa7` 内，保持原采样器 SHA `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`、fixtures、JDK25、26.1.2 节点、缓存、端口、source/path/reparse/process 检查。没有停止未知/用户进程。每次在启动前声明一次、五个完整样本；不按分数重试或补挑组。旧7b、新93紧邻对照和额外一次带 profiler 的93会话均保留第一值与完整日志。

| 诊断会话 | marker 原值 ms | marker 均值 | RTT 均值 | marker−RTT 均值 | 结论 |
|---|---|---:|---:|---:|---|
| 旧7b环境对照 | 407,224,342,207,219 | 279.8 | 232.8 | 47.0 | PASS285.3，仅旧源码 |
| 紧邻新93对照 | 378,201,344,310,234 | 293.4 | 201.0 | 92.4 | FAIL285.3 |
| 新93带 profiler | 355,218,209,175,233 | 238.0 | 193.0 | 45.0 | instrumented，仅诊断 |

完整 revision、source guard、direct exit、原始样本见 [summary](summary.json)。三个 sampler 和工具观察的外层71025/12267/75839直接退出0；三次正常 RCON 停服、结束后的专属端口和 owned process 检查通过。旧7b比此前历史均值233.8/220.8慢，但这次均值没有失败；紧邻新93仍失败。新旧紧邻 marker 差13.6ms，同时新93 RTT低31.8ms、观察尾延迟高45.4ms。因此这一对照不支持把 marker 差直接归因于重载主体变慢。RTT也不是精确主体耗时，时间漂移和未知外部负载仍未控制，不能据此确认环境就是全部根因。

[原正式93验收](../ticket37-same-name-performance/README.md) 两组383.8/330.4ms仍为 FAIL，启动22134.8ms PASS保留。这次238.0ms带 profiler 的结果不能替代它们或认证新源码通过。没有删首值、换指标、改门槛或混 revision。

性能记录只附加到新启动、命令行属于专属 checkout、排除 Gradle launcher/daemon/worker 且实际启动日志确认 owned game directory 的 JVM23332。原始 JFR 和完整事件 JSON 留在专属 `build`，不进入交付。聚合观察到86个 Server thread 样本，其中19个包含真实 `reloadScriptsTransactional`；重叠计数为路径检查2、成员验证2、候选创建5、binding安装2、脚本执行9、commit3。这是稀疏采样的存在计数，不是耗时比例或阶段计时。

唯一 Probe 栈在20:36:04的 modloading-worker-0，包含 backend 构造和插件注册，早于重载样本；没有观察到新增 import allocator/rendering 的重载栈。源码链为 `NekoJSCommands.reloadType → NekoRuntimeRoot.reload → ScriptManager.reloadScriptsTransactional`；Probe生成入口是单独命令或 ServerStarted opt-in。7b→93生产差异仅六个 Probe 文件，普通重载调用链未变。静态路径和现有采样未支持“新增生成代码进入普通重载”的假设，但采样缺失不能证明从未执行。下一步需要更精确地区分命令排队、候选创建/安装/脚本执行与日志观察时间；不绕过任何路径安全检查，不改验收指标，不在尚未证实的阶段加缓存。

新93采样 checkout 的十四个改变过的实际编译 class hash再次匹配此前新官方 JAR 绑定。旧对照在切回93前没有另冻编译字节；保留旧源码身份、前后检查和完整构建日志，不宣称额外旧制品字节验收。紧邻 runner复制时有一条摘要文案仍写“old-source control”；原文保留，actual revision/guard/raw样本均为93，不能据那条文案误认旧源码。

不可变 [原始诊断包](reload-environment-evidence.zip)：66 entries、440401 bytes、SHA256 `dc926f26979b341be6186df5142bc55336023aa13568b5a90bdc20cbb1e6753f`。[独立验证](archive-verification.json)及其包外[实际源码](verify.py)核对全部hash读回、原始五值/首值、固定门槛、direct sampler exit、instrumented仅诊断、聚合计数和当前十四个编译hash。实际凭据扫描通过；固定缓存路径同文子串只在扫描副本中依既有规则处理。不含properties、world、制品、rawJFR或完整事件payload。

执行入口：`run-old-environment-control.py`、`prepare-adjacent-current-control.py`/`run-adjacent-current-control.py`、`run-owned-reload-profile.py`、`read-owned-reload-profile.py`、`archive-environment-diagnosis.py`、`verify-environment-diagnosis.py`，位于专属 `build/ticket37-config-sync-repair`；实际执行源、预声明计划、路径检查、日志和直接退出记录归档。所有后台会话已结束。此次没有修改生产源码，没有重跑已通过的完整矩阵；此前矩阵结果仍仅认证其精确源码。

完整IDE、性能收口、legacy顺序确定性、历史FS/native crash/fireResistant/领域/多人下载/首帧与跨节点视觉、真实维护者结论、发布回滚仍未完成。维护者 NOT RECORDED；总体 NOT COMPLETE；自动续跑 PAUSED；工具权限字段问题 UNRESOLVED。没有新增发布授权。
