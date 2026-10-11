# 精确 f84e2adb 启动及重载性能

生产源码 `f84e2adbc36cac31ec8b2da8a448fbc0b8bbebae` 的主节点正式验收 **PASS**。复用专属干净 checkout；在五个实机服务端及完整编译检查均结束后，正常切换到精确源码，再预声明两组 reload 和一组 startup。沿用原 sampler、fixture、路径/进程/端口检查、JDK25、26.1.2、独立缓存、两次启动预热和每组五个正式样本，不改门槛。

| 正式组 | 全部正式样本 ms | 均值 ms | 原门槛 ms | 结果 |
| --- | --- | ---: | ---: | --- |
| reload 1 | 306, 151, 265, 233, 232 | 237.4 | 285.3 | PASS |
| reload 2 | 181, 252, 234, 235, 222 | 224.8 | 285.3 | PASS |
| startup | 22141, 21490, 22050, 21599, 21886 | 21833.2 | 41173 | PASS |

首个 306ms reload 样本完整保留；启动预热 21547/22280ms 保留但依原计划不进入正式均值。没有删除首样本、追加替换组、混合 revision 或因均值失败而重跑。三次 sampler subprocess 和外层执行器直接 exit0，最终 guard exit0；九个会话正常 RCON 关闭、无 timeout/force kill、端口释放。sampler PID 是 cmd.exe Gradle 启动器，不能冒充实际 server JVM PID。

`sample.ps1` SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`，fixture 字节与历史 cb5 一致。新修复涉及的 NodeModuleTypeDocs 及 TSExtractor 编译 class 字节与实机安装构建相同。没有同时运行本轮其他构建、测试或专属游戏；不停止用户进程，不将测量差异归因于声明修复。

原始 JSONL、服务端日志、运行器退出值、路径检查、全部正式值和预热见 [summary.json](summary.json) 与 [证据 ZIP](f84-performance-evidence.zip)：54 条目、1195062 字节，SHA256 `a797a2049d583a6de4310399351b13fcb53b0ba12809ca51acc9ac3eae1b06d4`。[独立校验](archive-verification.json)确认所有 hash/readback、完成日志、原始样本及门槛计算匹配；归档器运行中的日志未装入 ZIP，完成后单独保留。实际密码扫描仅屏蔽固定缓存目录 `.gradle-perf02` 与密码相同文本造成的假阳性，其余内容和凭据行继续扫描；未提交密码、world、二进制制品或原始 JFR。

历史 b7/085/cb5 的 FAIL 保留。这里只认证主节点 dedicated-server 启动/重载；其他节点或客户端性能 NOT RUN，完整 IDE 及总票据仍未完成。维护者 NOT RECORDED，自动续跑 PAUSED，工具权限字段问题 UNRESOLVED；没有发布授权。
