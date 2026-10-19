# 绑定枚举与固定正则的集中检查

源码绑定 `26844b7e65a6e71f7623af631192b03bbd5543b4`，包含 `fed76440` 的 guest 全局名枚举 Context 范围与 `7bc74ae8` 的固定 Pattern 复用。主代理负责实现和运行；本轮没有启动游戏或正式性能组。

## 行为与产物

- 定向 common 测试：121 tests，0 failures/errors/skipped，包括四项真实 Context 安装、嵌套恢复与失败保留回归。
- `:common:check`：2392 tests，0 failures/errors、4 skipped；包含 common isolation。guardLint 为 377 guards / 559 files、0 warnings；构建日志另有现有编译/弃用警告。
- 三个 NeoForge 节点的 `verifyNeoForgeRuntimeArtifact` / `verifyDevModSourceSets`，两个 Fabric 节点的 `verifyFabricRuntimeArtifact` 均通过；依赖任务重建了相关 JAR。这不等于本轮运行过五节点完整测试、GameTest 或 CI 全矩阵。
- 两份预先修改的声明文件原始摘要保持；没有提交或覆盖它们。

完整调用参数、日志、JUnit XML、源码和两类实际 class 摘要见 evidence.zip；验证入口为 `python -X utf8 docs/architecture-refactor/evidence/2026-10-20-binding-preflight-checks/verify.py`。校验器只读归档，不启动 Gradle 或游戏。

## 组件对照

同一 JVM 对照精确 `24d35a5d` 源码与当前编译类。旧类仅机械改名，方法体不改；编译和运行均 exit 0。两个组件各保留二十对预热、三十对正式组件样本与全部首值，交替顺序，没有按成绩删除样本。

| 组件 | before 均值 | after 均值 | 配对收益与边界 |
| --- | ---: | ---: | --- |
| 六份 JS 的成员校验 | 1.288540 ms | 1.228140 ms | 均值省 0.060400 ms；线程分配 620440.253 → 528880.467 bytes，下降约 14.76%；23/30 对墙钟下降 |
| 全部环境绑定安装 | 3.343587 ms | 2.412953 ms | 配对中位省 0.196900 ms，19/30 对下降；均值受长样本影响，不能当作稳定的 0.93 ms 收益 |

正则语料包含六份仓库 fixture 和九组额外语料，与三种合成 schema 组合，共 45 组诊断等价；六 fixture 在此合成视图下产生 26 条诊断，不代表真实游戏的诊断数量。每个测量 arm 连跑二十轮，表中除以二十。

绑定组件在同 Engine 下为每个 arm 新建独立 Context，安装后丢弃候选并关闭。使用 72 个合成绑定、空事件 bridge、无 managed API，共 155 个 knownGlobals；一百次安装的 schema 与全局集合一致。它不包含游戏中 Context/Node 创建，也不等于实际游戏的 72 个 schema 与 194 个 knownGlobals。Windows CPU 计数粒度较粗，两 arm CPU 均值相同不能证明没有 CPU 成本。

两项改动保持局部、没有新增缓存失效协议；组件收益小。当前正式结论仍为 [24d35a5d 的 289.4 / 338.0 ms，均 FAIL](../2026-10-20-source-map-storage-performance/README.md)。组件通过不能替代原 285.3 ms 门槛或当前最终源码的正式复验。
