# 精确7d重载阶段诊断，正式失败保留

在[三组正式采样](../ticket37-node-assert-performance/README.md)结束后，预声明一次五重载的诊断。仅在专属 owned checkout 的 NekoRuntimeRoot.reload 和既有 ReloadProgressTracker 的 begin/step/finish 添加直接 stderr nano/epoch 时间点，原采样器、fixtures、JDK、cache、端口和路径/reparse/process 保护不变。主工作区生产源码没有变化，没有 profiler/agent/GUI/客户端或用户进程操作。源码基点7d77308bfa400721304b20940ada91092180a1ed，加诊断 patch SHA256 `c1c743a3b7bb72f752446066af788c033d8966c33b3b1788956e99af11e81bb6`；这不是干净官方源码验收。

| 观测 | 五次均值 ms | 范围/限制 |
|---|---:|---|
| root入口→manager返回代理 | 148.90326 | 183.8512,159.3041,136.2929,144.4994,120.5687 |
| RCON往返 | 266.4 | 包含认证、排队、网络和主体，未分别计时 |
| stdout proxy marker | 313.0 | 原指标，不能替换为较小的主体或RTT |
| RTT−观测root代理 | 117.49674 | 只是差额，不能全部称为排队 |
| marker−RTT | 46.6 | 原日志观察尾延迟 |

阶段内相邻时间点平均约：候选环境21.12852ms、binding10.41134ms、发现2.11398ms、脚本执行110.1928ms、commit3.90772ms。脚本执行区间包含 prepareScriptsForLoad 和 before/after hooks，不能把它全归为 guest eval；commit区间含提交及旧资源关闭。每次所有步骤、完整原始时间点和计算见[summary](summary.json)。同步时间点输出会扰动耗时，其成本未量化。root代理止于manager返回、成功结果构造之前；失败路径没有完整计时保证。

这组诊断 marker五值为363,393,248,346,215ms，均值313.0；原JSONL全保留，虽然其 kind 字段仍由原采样器写 formal，本组必须按本页 source+patch 判为 diagnosticOnly。原正式341.6/315.6ms FAIL没有改变。本组支持继续缩小脚本准备/执行阶段，同时说明主体以外的开销不能忽略；不证明环境是全部根因，也不把差额当作可安全删除的耗时。

观测到六个root调用：一个发生在实际 DedicatedServer Done00:29:50之前，五个后续调用与五个RCON样本的原索引和marker时刻对应。首次读取器误假设只有五个而退出1；原读取器、失败说明和完整六次事件保留。初始调用单列279.6984ms，未删第一正式样本或混入正式均值。准备读取源码时默认GBK失败，改为显式UTF-8，原失败前helper/原文件保留。首次恢复helper有多余括号、未执行，修正后恢复；该失败helper原文件没有另冻，未补造快照。

诊断sampler/外层/前后guard直接退出0，正常RCON停服，结束端口可绑定。只允许精确两文件源码hash/patch偏差，其余owned状态和所有安全检查保留。与此前官方JAR比较，1586编译class逐字相等，6项变化仅属于两个插桩类家族；不冒称这些诊断字节等于官方JAR。原源码在确认无owned进程且patch/hash完全符合记录后逐字恢复，Git工作树干净；诊断编译cache仍留本地，需重新编译才能再做官方字节认证，没有reset/clean。

[不可变证据](reload-phase-evidence.zip)：35entries、176682bytes，SHA256 `2d539989ab59566632d53eebd7f636aad938b4062fcfbe8f5e31d991a48eb405`。[独立验证器](verify.py)检查完整hash、sourcepatch/前后源码、全部六次事件、五个原样本/首值、阶段差额、直接退出和恢复记录。实际凭据扫描发现原sample.ps1含benchmark默认认证值，故没有归档该源码，只保留精确SHA绑定；properties/world/rawJFR/制品和用户资料也排除。没有放宽凭据检查。

下一步区分实际脚本预载/编译/模块初始化/eval/回调阶段，在确认事实后修共享原因；不绕过路径/信任/权限检查，不增加未证实的cache，不改正式指标或门槛。完整IDE/Pyright、正式重载、其他当前多人/下载/首像素/跨节点/领域/磁盘及真实维护者和发布回滚仍未完成。总体NOT COMPLETE，自动续跑暂停，权限字段问题未解决，A22/A28及发布授权边界不变。
