# 精确 d109 正式启动/重载性能

当前 d109正式性能门槛通过：两组完整重载均值227.0ms、207.6ms，低于原285.3ms；五个正式启动样本均值13036.2ms，低于原41173ms。此结论只适用于精确源码、节点和本次环境，不表示所有票据完成，也不证明旧93失败的根因已经修复。

专属 checkout `build/ticket37-perf-owned-ac810fa7` 切到干净的 `d10912ebbf24d9d5df75a908dc7a5529da09979a`，原采样器SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`、fixtures、JDK25.0.2、NeoForge26.1.2、Gradle cache和端口25871/25872不变。全部专属客户端/五节点安装/类型检查已结束后才开始，未并行构建或测试，未停止未知或用户进程。

| 预声明组 | 正式原值 ms | 均值 ms | 固定门槛 ms | 结果 |
|---|---|---:|---:|---|
| reload-confirmed-1 | 277,127,235,249,247 | 227.0 | 285.3 | PASS |
| reload-confirmed-2 | 273,248,140,248,129 | 207.6 | 285.3 | PASS |
| startup-confirmed | 13006,13310,12942,12825,13098 | 13036.2 | 41173 | PASS |

启动 warmups13087/12863ms原样保留。每组只运行预声明的一次；没有丢首样本、挑组、改门槛或混源码。sampler三次直接退出0，工具观察外层53796完成退出0，九次正常RCON停服，没有 forced kill/timeout；最后原source/sampler/path/reparse/process/port守卫通过。样本PID为采样器启动的Gradle/cmd launcher，不伪称实际服务器JVM PID。

性能 checkout的common/26.1.2实际编译类逐项匹配主工作区同节点编译字节和[当前五节点实机](../ticket37-current-d109-ide/README.md)中实际安装的26.1.2官方 JAR（SHA256 `83b147ed95c874fcdc3aaf534c8e1dd48e1540801770d19f6575d32e00af86bc`）。完整class/hash清单见[结果](summary.json)。没有把26.1.2性能运行描述成另外四节点性能验收。

[旧93两组失败383.8/330.4ms](../ticket37-same-name-performance/README.md)继续保留，环境诊断中的293.4ms失败和带profiler238.0ms也未被本轮替换。d109期间编译器、STARTUP组件及运行环境都有变化，没有本次精确的一变量对照，不能把改善归因于某一修复或确认旧波动根因。

## 证据

[不可变日志及采样原值](current-d109-performance-evidence.zip)、[完整结果与逐项hash](summary.json)、[独立验证器](verify.py)。48 entries、1189763 bytes，SHA256 `94a19bec35a4193b6e16257ca98a3e9fd73258a48625123faae911ed41669dbc`。独立读回全部entry、原始样本、首值、warmups、固定门槛、实际directexit及finalguard。实际RCON凭据扫描通过；固定 `.gradle-perf02` 路径的同文子串仅在扫描副本按既有规则处理，原始证据不改写。properties/world/JAR/rawJFR及无关数据不入库。

执行源与预先确定组名位于专属 `build/ticket37-config-sync-repair`，已保留在包内：

```powershell
python build/ticket37-config-sync-repair/prepare-current-d109-performance.py
python build/ticket37-config-sync-repair/run-current-d109-performance.py
python build/ticket37-config-sync-repair/prepare-d109-performance-archive.py
python build/ticket37-config-sync-repair/archive-current-d109-performance.py
python docs/architecture-refactor/evidence/ticket37-current-d109-performance/verify.py
```

准备器拒绝覆盖已有证据目录。每组重用原采样器及其原路径安全检查，而不是更换指标或绕过Windows守卫。

完整 IDE/Pyright仍失败；并发 GLFW激活和bridge退出缺陷仅已定位、未修复；多人abort/download、首像素/其他客户端节点、磁盘及其余领域、真实维护者结论和发布回滚仍有缺口。性能单项PASS不是总体PASS。自动续跑暂停，工具权限字段问题未解决，A22/A28及发布授权边界不变，整体未完成。
