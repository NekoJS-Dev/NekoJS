# 2026-10-18 脚本包进程内存与 NMT 观测

五项既有资源场景在独立 JVM 中全部执行并通过，未跳过；补采 29 组 Windows 进程计数及 HotSpot NMT 快照。**这是 common 运行时观测，不是完整 Minecraft 客户端的原生内存最终验收。**

## 输入、执行与口径

以已提交的 `PackResourceStabilityTest` 为基线，在临时目录生成 `PackNativeObservationTest`，只更换测试类名并加入观测调用。原 Context、注册表、事件、timer、候选失败保留及堆窗口断言不变；主源码、原测试文件及 1152 个 common main 类未改。单次运行五项全部成功，耗时 50.199 秒。完整 common 检查的既有结果见[资源验收](../2026-10-18-pack-resource-acceptance/README.md)；本次没有再次运行全矩阵。

JDK 25.0.2，64 位 Windows，单独开启 `-XX:NativeMemoryTracking=summary`，每个观测点先读取进程及堆计数，再调用本进程 `jcmd VM.native_memory summary scale=KB`。两类快照有先后间隔，不是同时采样。NMT 与 attach 自身也有开销；本组不用于重载性能评分。

进程 `privateCommitBytes` 为 Windows `PrivateUsage`，包含 Java 堆；`workingSetBytes` 为整个进程工作集，不能将二者称为每个 Context 的原生分配。[Windows 结构定义](https://learn.microsoft.com/en-us/windows/win32/api/psapi/ns-psapi-process_memory_counters_ex)支持该口径。`vmNonHeapCommittedKiB` 仅为 NMT Total committed 减 Java Heap committed；它不等于驻留内存，也不覆盖全部第三方原生及 JDK 类库分配。[NMT 官方范围说明](https://docs.oracle.com/en/java/javase/25/vm/native-memory-tracking.html)。

日志明确报告 Graal fallback runtime、JVMCI 未启用；因此本次未覆盖完整游戏使用 GraalMC 编译 guest 的全部成本。三个包数场景在同一 JVM 依次运行，有共享编译器、类与缓存预热，不能将进程增量除以包数当作单 Context 成本。没有设立新的原生内存通过门槛，也没有将既有 32 MiB **Java 堆**窗口门槛套用到进程内存。

## 观测结果

每包保留 4096 项私有负载、事件订阅及 timer；Context 数包含既有 legacy Context。下表进程增量为同场景 active 减 legacy，原始全部点均在归档中。

| 包数 | active / candidate Context | 私有提交增量（bytes） | NMT 非堆提交增量（KiB） | root 关闭后 Context |
| --- | --- | ---: | ---: | ---: |
| 1 | 2 / 4 | 5,918,720 | 2,789 | 0 |
| 4 | 5 / 10 | 8,617,984 | 1,420 | 0 |
| 8 | 9 / 18 | 7,409,664 | -150 | 0 |

部分 candidate 点内存低于 active，NMT 也可出现负增量；堆回收/退还、编译器 arena 回收与操作系统驻留变化都包含在整进程计数中，这不是 Context 分配为负的证据。初始化首点较大的私有提交值及所有关闭后点均保留，没有挑选较好的值。

四包连续五十次替换，五个观测窗口如下；始终为 5 个 active Context、13 个 Java 线程，root 关闭后 Context 为 0、Java 线程回到 9。

| 重载次数 | 私有提交（bytes） | 工作集（bytes） | 旧 Context / 观察到回收 |
| --- | ---: | ---: | --- |
| 10 | 287,580,160 | 235,364,352 | 50 / 50 |
| 20 | 297,193,472 | 246,960,128 | 100 / 100 |
| 30 | 292,114,432 | 240,619,520 | 150 / 150 |
| 40 | 340,262,912 | 254,816,256 | 200 / 200 |
| 50 | 291,950,592 | 239,190,016 | 250 / 250 |

10→50 的私有提交增加 4,370,432 bytes、工作集增加 3,825,664 bytes；NMT 非堆提交增加 2,461 KiB，其中 Code 提交增加 5,136 KiB，其他类别有下降。曲线并非单调增长，40 次窗口峰值随后回落；它不证明无限重载没有泄漏。五次 DOMAIN_PLAN 候选失败的资源关闭、健康代保留和恢复断言也通过。

## 归档与复核

`evidence.zip` 包含 86 项，2,283,239 字节，SHA-256 为 `45f087ccac369de8e84735999b08f3a8d9546a5a073d4a72d4ae1e5c2864c5df`。包含原测试、实际观测源码与类、运行 main 类、完整日志、29 组进程/NMT 原始点、JUnit 事件及依赖摘要。第三方依赖 jar 未重复归档，重跑还需要对应依赖；离线复核不需要运行 JVM。

```powershell
python docs/architecture-refactor/evidence/2026-10-18-pack-native-memory/verify.py
```

复核通过：归档完整性、原/观测测试仅有声明的新增观测、五项成功事件、实际 main 类绑定、29 份 NMT 分类与汇总、Context 数、五个回收窗口及增量重算。首版复核器误把日志字段写为 `retired/collected`，按原日志 `retiredContexts/observedCollected` 修正；原运行、样本和断言未修改，也未重跑择优。

## 剩余验收

多包资源释放的自动化通过，整进程与 HotSpot 非堆成本已有有界观测。完整游戏/GraalMC 的内存成本、同一真实客户端 A→B→A、带签名包跨进程同步及维护者接受结论仍未完成；不代签、不以本组替代。
