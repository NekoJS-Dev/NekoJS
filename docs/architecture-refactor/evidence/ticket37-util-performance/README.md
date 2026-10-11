# 精确 60139fff 启动与重载性能

本包认证源码 `60139fffffa6e54b2248797c2a32d4fd75025a54` 的主节点 NeoForge 26.1.2 独立服务端性能；与 [新 JAR 声明验收](../ticket37-node-util-predicate/README.md) 分开记录，不覆盖历史 FAIL，也不认证完整 IDE、客户端或其他领域。

五个安装测试服、完整矩阵和 IDE 收集器全部结束后，使用既有专属 checkout 正常切换到精确提交，保持 clean。JDK 25.0.2、原始 sampler SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`、历史 fixture 字节、路径/reparse/进程/端口守卫和门槛不变；预先声明两组重载及一组启动，每组五个正式值。没有其他本轮构建、测试、编译器或测试服并行采样。

| 正式组 | 全部正式值 ms | 均值 ms | 原门槛 ms | 结果 |
| --- | --- | --- | --- | --- |
| 重载 1 | 308, 265, 236, 251, 232 | 258.4 | 285.3 | PASS |
| 重载 2 | 291, 235, 234, 236, 230 | 245.2 | 285.3 | PASS |
| 启动 | 22328, 22847, 21999, 23577, 22922 | 22734.6 | 41173 | PASS |

启动预热 22523 / 22789ms 保留。308ms 和 291ms 首值均参与原组计算；没有删首值、替换组或混合不同源码。三个 sampler 直接退出 0，外层作业 session 46329 直接退出 0，最终专属守卫直接退出 0；九个服务端会话均走正常 RCON 关闭，无 forced kill 或 timeout，端口 25871/25872 释放。采样 PID 是 cmd Gradle launcher，不是实际 server JVM PID。

`util.ts` 的实际编译资源 SHA256 `8c1b3481789f060647bd717825916da1c3d3201e2cb90da4c261b583ec269b5d` 与五个安装 JAR 的资源字节一致。这里不推断注解修复带来的性能因果关系；此前 b7/085/cb5 等失败和独立 f84 PASS 保持。

[summary.json](summary.json) 含全部原始值、退出记录和制品绑定；[证据 ZIP](util-performance-evidence.zip) 含 53 项、1,195,052 bytes，SHA256 `9e5fc7069ca67498a34b4c08955491f4306aa0482ff6898cce4e563ac8b69b77`。[独立验证](archive-verification.json) 核对全部 hash/读回、完成控制台、原始 JSONL、首样本及预热、固定门槛和实际计算结果。实际凭据扫描仅移除固定 `.gradle-perf02` 目录文本的密码子串误报，继续扫描其余内容及凭据行；不收录 properties、world、二进制 JAR、原始 JFR 或无关数据。打包自己的活动控制台不进 ZIP，完成输出另存。

总体 NOT COMPLETE：完整 TS/Python IDE FAIL、真实 field/method 建模、名称确定性、文件系统根因、native crash、fireResistant、其他领域、多人的独立下载窗口、首帧像素及跨节点视觉、维护者结论和发布/回滚尚有缺口。维护者 NOT RECORDED，自动续跑 PAUSED，工具权限字段问题 UNRESOLVED；没有发布授权。
