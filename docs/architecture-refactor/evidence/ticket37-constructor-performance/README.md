# 精确 7b724a50 启动与重载性能

本包认证源码 `7b724a50f51367ad43f5903b1e7240e8e69bad10` 的主节点 NeoForge 26.1.2 独立服务端性能；与 [新 JAR 声明验收](../ticket37-ts-constructor-member-repair/README.md) 分开记录，不覆盖历史 FAIL，也不认证完整 IDE、客户端或其他领域。

五个安装测试服、完整矩阵和 IDE 收集器全部结束后，使用既有专属 checkout 正常切换到精确提交，保持 clean。JDK 25.0.2、原始 sampler SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`、历史 fixture 字节、路径/reparse/进程/端口守卫和门槛不变；预先声明两组重载及一组启动，每组五个正式值。没有其他本轮构建、测试、编译器或测试服并行采样。

| 正式组 | 全部正式值 ms | 均值 ms | 原门槛 ms | 结果 |
| --- | --- | --- | --- | --- |
| 重载 1 | 261, 249, 219, 236, 204 | 233.8 | 285.3 | PASS |
| 重载 2 | 249, 233, 204, 217, 201 | 220.8 | 285.3 | PASS |
| 启动 | 17994, 17601, 17883, 18154, 17565 | 17839.4 | 41173 | PASS |

启动预热 13854 / 18263ms 保留。所有首值参与原组计算；没有删首值、替换组或混合不同源码。三个 sampler 直接退出 0，外层作业 session 60112 直接退出 0，最终专属守卫直接退出 0；九个服务端会话均走正常 RCON 关闭，无 forced kill 或 timeout，端口 25871/25872 释放。采样 PID 是 cmd Gradle launcher，不是实际 server JVM PID。

`TypeScriptClassRenderer.class` 的实际编译字节 SHA256 `d4f2ae48dadd54cc6570d50741a8b969285928c9eecb1d914bcc252d96c628eb` 与五个安装 JAR 的对应 class 一致。这里不推断声明修复带来的性能因果关系；此前 b7/085/cb5 等失败和独立历史 PASS 保持。

[summary.json](summary.json) 含全部原始值、退出记录和制品绑定；[证据 ZIP](constructor-performance-evidence.zip) 含 52 项、1,195,454 bytes，SHA256 `e2af5853872a0077885f0f49379397474ae8b3049dbe4b236e75fcc3ecad45bb`。[独立验证](archive-verification.json) 核对全部 hash/读回、完成控制台、原始 JSONL、首样本及预热、固定门槛和实际计算结果。实际凭据扫描仅移除固定 `.gradle-perf02` 目录文本的密码子串误报，继续扫描其余内容及凭据行；不收录 properties、world、二进制 JAR、原始 JFR 或无关数据。打包自己的活动控制台不进 ZIP，完成输出另存。

总体 NOT COMPLETE：完整 TS/Python IDE FAIL、真实 field/method 建模、名称确定性、文件系统根因、native crash、fireResistant、其他领域、多人的独立下载窗口、首帧像素及跨节点视觉、维护者结论和发布/回滚尚有缺口。维护者 NOT RECORDED，自动续跑 PAUSED，工具权限字段问题 UNRESOLVED；没有发布授权。
