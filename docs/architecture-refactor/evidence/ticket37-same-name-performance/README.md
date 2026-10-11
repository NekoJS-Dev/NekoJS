# 精确同名导入修复源码性能

采样源码 `93f4b259ffafb6466918582fcfa950c2c2480d77`，即 [共享同名导入修复](../ticket37-ts-same-name-import-repair/README.md)。源码及修复证据已推送，直接 push 退出 0，随后远端 SHA 与本地精确一致。这里仅验收 primary NeoForge26.1.2 专属服务器启动/重载，不能外推客户端、其他节点或整体票据。

采样前已观察五节点安装服务器、严格 IDE collector、唯一旧 JAR 顺序对照直接退出；专属端口释放。干净专属 checkout 切到精确源码，原 sampler SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`、fixture、JDK25、节点、缓存、路径及 reparse 检查保持。没有本轮构建/测试/游戏/编译器并行，没有停止用户或未知旧 Java 作业。旧作业的贡献未知，不能声明系统完全空闲或据此丢弃样本。

预先写明两组 reload、一次 startup，每组五个正式样本；启动另外保留两个 warmup，门槛保持 reload285.3ms/startup41173ms。三组均由原采样器运行，完整原始值见 [summary](summary.json)：

| 组 | 正式样本 ms | 均值 ms | 验收 |
|---|---|---:|---|
| reload-confirmed-1 | 536, 269, 358, 363, 393 | 383.8 | FAIL |
| reload-confirmed-2 | 324, 270, 360, 369, 329 | 330.4 | FAIL |
| startup-confirmed | 36090, 31451, 17442, 11886, 13805 | 22134.8 | PASS |

startup warmups29577/29147保留。没有删首值、替换组、混不同 revision、改门槛或用 RCON RTT 替代 marker_ms。三次 sampler、外层17148、最终 guard 均直接退出 0；这表示采集与关闭成功，性能整体仍 **FAIL**。九个会话正常 RCON 关闭，专属端口释放。采样源码十四个实际编译类字节等于五个新安装 JAR，源码/制品身份复核通过。既有精确7b性能 PASS 与此前 FAIL 均独立保留，不互相替代，不宣称此次命名修复造成性能变化。

不可变 [原始包](same-name-performance-evidence.zip)：52 entries、1195557 bytes、SHA256 `da37b810c7c3621483e52e3e5972673b21e3afd029d0d51cbec553e3b270d799`。[独立验证](archive-verification.json) 检查全 hash/读回、完成后的控制台及原始样本、首样本/warmup、固定门槛和计算结果、直接退出与制品类绑定。实际凭据扫描通过，固定缓存路径的同文子串只在扫描副本中按原规则处理；不含 properties、world、制品或 raw JFR。

下一步诊断有三项可证伪假设：当前环境中一次预声明旧7b对照是否也变慢；marker 观察尾延迟是否大于实际重载主体变化；Probe 修改是否实际进入重载路径。原 RTT/marker/phase 日志先比对，之后只在拥有的测试会话采集 profile；旧 JFR 如需产生仅留本地，公开脱敏聚合。诊断不能替换本次失败组或放宽指标。

完整 TS/Pyright IDE、重载性能、legacy 重载/名称确定性、历史文件系统问题、native crash、fireResistant、剩余领域、多人独立下载窗口、首帧像素/跨节点视觉、维护者、发布及回滚均未完成。维护者 NOT RECORDED、总体 NOT COMPLETE、自动续跑 PAUSED、工具权限字段 UNRESOLVED，无发布授权。当前采样作业全部结束。
