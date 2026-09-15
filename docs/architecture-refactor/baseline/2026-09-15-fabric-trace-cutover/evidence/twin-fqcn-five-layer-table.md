# 工单 32：七个同名 FQCN 孪生的五层 origin 解释（2026-09-15）

数据源：`fabric-trace-five-layers.txt`（同目录）。FQCN 列表 = 交接单 §4.3 的 7 个
`bindings/event` 孪生。两 fabric 节点模式完全一致（源同、编译环境同、class 哈希一致），
下表以 26.1.2-fabric 为准、26.2.0-fabric 仅在差异处标注。

## 逐层解释

| 层 | 事实 | 对唯一性的含义 |
|---|---|---|
| L1 raw source（源码事实层） | 每个 FQCN 在仓库里恰有 2 个源文件：共享树 `src/main/java/.../X.java`（整文件 `//? if neoforge {` 守卫，首行守卫核验 7/7 GUARDED）+ `src/fabric/java/.../X.java`（fabric 实现，哈希清单见 trace L1）。这就是"孪生"的 origin：**设计上的双面接口**——neoforge 面由守卫只对 NeoForge 节点激活，fabric 面住在 raw loader root | 源层"两个同名文件"是刻意设计而非泄漏；判定标准是**每节点每 FQCN 只有一个有效编译输入**，见下两层 |
| L2 processed source（生成副本层） | stonecutter 只预处理共享树。两 fabric 节点的生成树（`build/generated/stonecutter/main/java`，291 文件）中，7 个孪生路径的同名文件是 **910–18471 B 的注释空壳**（stonecutter 把失活整文件守卫整体 `/* */` 注释化；剥离注释后零有效代码，trace 判定 HOLLOW 7/7）；`src/fabric` 的内容以**有效代码**形式出现在生成树 = **0**（仅上述 7 个守卫壳与 raw root 路径同名，非 fabric 内容）；共享树全部 63 个整文件 neoforge 守卫在生成树 100% 空壳化 | 生成副本**不是第二事实源**：空壳 javac 不产出 class。文件名同名 ≠ 双源，判定依据是有效代码剥离检验（`strip_comments` 后为空），不是"集合里没有这个名字" |
| L3 编译 class | 节点 `build/classes/java/main`：每 FQCN 恰 1 个 class（哈希见 trace；两节点逐一相同，如 CommandEvents.class = `2f05ffd4cf63…`）。`common/build/classes/java/main`：7 个 FQCN **全部为 0**（:common 编译自己的 `common/src`，不挂共享树或 src/fabric——srcDirs 探针 `:common java=[src\main\java]` 指 `common/src/main/java`） | 编译产物层唯一起点是 src/fabric；common 不是孪生来源 |
| L4 Jar 去重前打包输入 | 按 jar 任务 from 注册序（节点 main output → common output → common runtime deps【过滤 graal 后为空，COMMON_RT_COUNT::0】→ bundled night-config×2）聚合：两节点各 7268 个文件 entry，多来源 entry 仅 1 个 = `META-INF/MANIFEST.MF`（来自 core-3.8.3.jar 与 toml-3.8.3.jar，且实际胜者是 Jar 任务自生成的 manifest）。**7 个孪生 class 来源数全部 x1，唯一来源 = `<node>:sourceSets.main.output`** | 去重前就没有第二来源竞争；`DuplicatesStrategy.EXCLUDE` 对孪生**不承担任何去重**（它唯一消化的是 MANIFEST.MF 一项）——即"去重策略不是唯一性证明"的反证：唯一性在输入侧已经成立 |
| L5 最终 ZIP entries | 两节点 jar 各 7433 entries（7268 文件 + 165 目录项），ZIP 层重复 0；7 个孪生 **x1**；jar 内 class 与 L3 节点编译产物**字节级一致**（SHA-256 全等）。L4↔L5 闭合：序首胜出集合与 jar 文件 entry 全等（仅 jar 0 / 仅 L4 0） | 最终计数与来源链闭合：raw → processed（空壳）→ class（唯一）→ pre-dedup（唯一）→ jar（x1 且字节==编译产物） |

## 跨 loader 终态（NeoForge 对照）

NeoForge 三节点 jar 中孪生 x1（1.21.1 的 `client/KeyBindEvents` x0 = 该版本无客户端
KeyBind 事件面，票 01 起已知差异），且三节点孪生 class 与两 fabric 节点的编译产物
**零字节重合**（trace L5 逐 FQCN 比对无 !! 行）。即：fabric 孪生从未进入 NeoForge jar，
neoforge 孪生从未进入 fabric jar——`//? if neoforge` 守卫 + `src/fabric` 挂载边界的
跨 loader 隔离在字节层面成立。

## 结论

- 唯一性证明链：L1 守卫核验（7/7）→ L2 空壳剥离检验（7/7 + 63 守卫全量）→ L3 唯一 class
  + common 零携带 → L4 唯一来源（唯一多来源项与孪生无关）→ L5 x1 + 字节级回链。
  集合存在性、guard 数量、源文件数均未单独用作证明。
- 生成副本（stonecutter 树）在证据链中的角色是"证明守卫被正确剥除"，不是第二事实源。
