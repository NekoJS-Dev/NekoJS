# 精确 63d538dc 正式性能：重载 FAIL

当前生产提交 `63d538dc14e7d7cdcf00f6217c5617fd285e7933` 两组正式重载仍未通过原285.3ms门槛；启动通过41173ms门槛。旧 d109 的 PASS 不能代替本次结果。

| 预声明组 | 全部正式样本 ms | 均值 ms | 固定门槛 ms | 结果 |
|---|---|---:|---:|---|
| reload-confirmed-1 | 435,371,343,387,325 | 372.2 | 285.3 | FAIL |
| reload-confirmed-2 | 488,401,246,360,339 | 366.8 | 285.3 | FAIL |
| startup-confirmed | 12382,11994,11671,11663,11996 | 11941.2 | 41173 | PASS |

两个启动 warmup24788/11968ms也完整保留。每组只运行预声明的一次，没有重试挑组、删首样本、混revision或改门槛。所有专属安装及 IDE 作业结束后才开始；期间无并行构建/测试，静态文件阅读及下一处测试源码编写没有启动构建。主机其他用户工作未干预。

干净专属 checkout `build/ticket37-perf-owned-ac810fa7`、JDK25.0.2、NF26.1.2、原fixtures、Gradle cache、端口25871/25872与 [d109 测量](../ticket37-current-d109-performance/README.md)相同。原sampler SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`；原路径/reparse/进程/端口守卫执行，最终守卫0。三次 sampler 和外层直接退出0只表示采样完成，不等于性能验收通过。九次正常 RCON 停服，无forcedkill/timeout；记录PID是cmd/Gradle launcher，不伪称服务器JVM。

1592个实际编译Neko类逐项等于主工作区同节点类及 [本补丁实机](../ticket37-manual-item-repair/README.md)安装的NF26.1.2官方JAR SHA256 `746d776e772a479133b47f0bf54d0e22f12a857abf07c93b779df039f288255f`。源码和制品对应，不套用旧SHA。

生产差异仅修复manual声明字符串，不触及普通重载热路径；两revision顺序测量没有随机化、主机资源监测或一变量热路径对照，因此不能将变慢归因于manual修复，也不能宣称环境波动已证实。保留本次真实失败，后续需分阶段诊断。旧93/b7失败和d109通过继续保留。

[不可变证据](manual-item-performance-evidence.zip)、[完整原值/类绑定/hash](summary.json)、[独立验证器](verify.py)：49entries、1191204bytes，SHA256 `328c03382f7e81e767e4d2921cd146b39b596af539ecce36ef885a12b4f7e140`。验证器PASS表示证据真实一致，同时明确性能overallFAIL；实际凭据扫描通过，properties/world/JAR/rawJFR和无关数据不入库。

```powershell
python build/ticket37-config-sync-repair/prepare-manual-item-performance.py
python build/ticket37-config-sync-repair/run-manual-item-performance.py
python build/ticket37-config-sync-repair/archive-manual-item-performance-preparation.py
python build/ticket37-config-sync-repair/archive-manual-item-performance.py
python docs/architecture-refactor/evidence/ticket37-manual-item-performance/verify.py
```

完整 IDE/Pyright 和总体票据仍 FAIL/未完成。维护者结论未记录，自动续跑暂停，工具权限字段问题未解决，A22/A28/发布授权边界不变。
