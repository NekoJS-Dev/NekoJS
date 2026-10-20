# 完整 Fabric 游戏的包资源与成本观测

源码 `4c2fd226`，实际生产类与 `cc55c4ce` 的集中检查相同。隔离 Fabric 26.2 服务端 PID 4596，GraalMC 25.1.3.7 / Java 25.0.2，Engine 为 `Interpreted`、`supportsCompilation=false`。主代理编译、运行并核对；没有修改生产调度或添加性能策略。

## 行为结果

- 0 → 1 → 4 → 8 → 4 包，随后四包连续重载 50 次，最后禁用全部包，共 55 次 COMMIT/no errors。
- SERVER Context 为 legacy 1 加独立包数量，另有 1 个非 SERVER Context。每包 private owner、UUID、4096 项 payload 与活跃求值均独立；候选峰值等于非 SERVER + 旧 SERVER + 新 SERVER，没有多余注册 Context。
- 729 个被检查的原生 tick 中没有重复或错误归属的事件回调。每个采样窗口的活跃包 immediate 均恰好一次，一小时间隔任务在短观测期内未执行；样本间 tick 持续前进。
- 每次替换检查旧 Context 拒绝求值、manager 与四张 metadata 表移除、scheduler terminated、任务/ready 队列清空。退役 Context 仅保留弱引用；11 个采样点各主动请求三次 GC，最终 272/272 观察到回收。
- 正常 RCON stop、launcher exit 0、无强制终止。最后禁用后仍保留 legacy SERVER 和 STARTUP，共 2 个 Context；没有宣称在关 root 后直接测得零 Context。

## 实际成本样本

单位 MiB。堆 used 是每次显式 GC 后的 JVM 读数；private commit 包括 JVM 堆，不能与 NMT 非堆相加或视为包自身成本。

| 状态 | SERVER Context | heap used | heap committed | private commit | NMT non-heap committed |
| --- | ---: | ---: | ---: | ---: | ---: |
| legacy | 1 | 162.325 | 416 | 862.051 | 387.203 |
| 1 包 | 2 | 159.820 | 416 | 803.258 | 330.108 |
| 4 包 | 5 | 160.683 | 416 | 851.289 | 327.375 |
| 8 包 | 9 | 161.820 | 416 | 852.066 | 327.292 |
| 回到 4 包 | 5 | 160.902 | 416 | 852.133 | 332.129 |
| 第 10 次 | 5 | 160.923 | 416 | 852.828 | 345.330 |
| 第 20 次 | 5 | 160.993 | 640 | 1066.488 | 338.622 |
| 第 30 次 | 5 | 161.006 | 640 | 1069.969 | 336.677 |
| 第 40 次 | 5 | 161.041 | 640 | 1076.758 | 339.410 |
| 第 50 次 | 5 | 160.507 | 640 | 1075.902 | 342.250 |
| 禁用 | 1 | 159.903 | 640 | 1075.605 | 337.423 |

四包重载窗口的 used heap 未持续增长，全部旧 Context 回收；但进程 private commit 增长，JVM 堆提交容量同时由 416 升至 640 MiB。它不能被解释为全部进程内存恒定，也不能把首个冷样本与后续样本的差值精确归因到每个 Context。该测试不覆盖长期世界运行、编译型 guest、所有平台或最终人类接受；common JVM 的 32 MiB 预算没有移用于完整游戏。候选求值等待路径允许冲刷候选 timer，不据此宣称任意外部副作用仅在 commit 后执行。

## 复现与范围

归档保留全部控制器、fixture、日志、RCON 响应、11 组 OS/NMT/heap 样本、66 条资源记录、观察器 JAR/源码和 1,569 个生产类摘要。它是原生 loader 开发 `runServer`，不等于发行 JAR 安装启动。准备和执行入口在归档中，使用新的 owned 目录及空闲 25891/25892；私有 RCON 配置、环境、存档未打包。

前一次 v3 的[空服暂停失败](../2026-10-20-pack-game-pause/README.md)保留不变。v4 仅给隔离观测服设置 `pause-when-empty-seconds=0`，并增强 tick 前进与具体 immediate 次数断言；没有主动手动冲刷队列、延长任务时限或弱化资源检查。

独立只读复核：`python -X utf8 docs/architecture-refactor/evidence/2026-10-20-pack-game-resources/verify.py`。这不是正式重载性能采样；原标准性能结论仍引用既有通过组。
