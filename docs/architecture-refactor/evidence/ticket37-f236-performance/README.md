# 精确 f236a983 正式性能：重载 FAIL，启动 PASS

专属干净 checkout 精确源码 `f236a983b915023283b2baf200ddec4074844712`，NeoForge26.1.2、JDK25.0.2、原 sampler SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`、原 fixtures、固定 Gradle 缓存及25871/25872端口。准备与结束检查 source/path/reparse/port/process；每组前检查 source/sampler/port。没有并行构建、测试或游戏验收，没有更换指标、删首值、混 revision 或按成绩重试。

| 预声明组 | 五个正式样本 ms | 均值 ms | 固定门槛 ms | 结果 |
|---|---|---:|---:|---|
| reload-1 | 431,399,388,375,337 | 386.0 | 285.3 | **FAIL** |
| reload-2 | 389,399,269,302,329 | 337.6 | 285.3 | **FAIL** |
| startup | 24003,25733,22382,21116,24141 | 23475.0 | 41173 | **PASS** |

启动两次 warmup 原样保留，正式原始索引仍3–7，重载仍1–5。三 sampler 和外层 runner 直接0、最终 owned guard直接0只证明采集成功；性能整体 **FAIL**。9次独立会话正常 RCON stop，所有样本无 forced kill/killed/timeout，最终专属端口可绑定。sampler的pid记录是 cmd/Gradle launcher，不能称实际服务器JVM pid。

实际性能 checkout 的1592个编译 Neko class 与主工作区、最终五节点实机包的26.1.2正式 JAR逐字一致，JAR SHA256 `ddda717112f513d86196969cd067819b3f38864103bba1488f21b2c049135cd8`；fixture逐文件hash与7d源码不变。新模块声明修复不构成性能优化证据，分数差异不归因于源码或全部归因于环境。[7d的341.6/315.6失败及25795.4启动通过](../ticket37-node-assert-performance/README.md)仍保留。

原工作负载命令（两次reload、一次startup，warmup2/samples5/reloads5）：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode reload -Node 26.1.2 -Warmup 2 -Samples 5 -Reloads 5 -GradleUserHome D:/mcmodDemo/NekoJS/.gradle-perf02 -ServerPort 25871 -RconPort 25872
```

[完整数据](summary.json)、[不可变原始日志/JSONL/guard/runner](evidence.zip)：41条目1186315字节，SHA256 `9b4d0e01279b69aabe19ddb5b18438b145f4cd681f24983d491df4e0213388b6`。排除 world、RCON配置/密码、原始JFR、二进制JAR和无关进程数据；sampler正文只绑定SHA，不复制其默认凭据。缓存目录名称中与benchmark凭据相同的文本仅按原有路径角色匹配处理，未删除日志或替换采样数据。独立检查 `python docs/architecture-refactor/evidence/ticket37-f236-performance/verify.py` 的 PASS 是数据一致，性能仍 FAIL。

下一步继续同一版本的具体重载路径诊断与修复，再在源码收敛后集中验收。完整 IDE/Pyright、部分第一帧/其他客户端节点/领域/磁盘窗口、维护者结论及交接/发布回滚材料仍未完成。自动续跑 PAUSED，权限字段 UNRESOLVED，A22/A28及发布授权边界保持，整体 NOT_COMPLETE。当前性能作业与专属服务端已正常结束。
