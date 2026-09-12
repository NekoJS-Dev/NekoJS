# 自研转译与模块加载应如何拆分且保持语义可控？

Status: closed
Type: grilling
Mode: HITL
Parent: [NekoJS 架构重构路线图](../../architecture-refactor-map.md)
Assignee: session-0ccab587-1d9c-436f-b53b-a6428bdb1aaf (主 agent，与维护者共同裁决)
Blocked by: [维护者的最小理解范围与目标模块归属如何确定？](01-maintainer-module-design.md)

## Question

自研转译与模块加载应如何拆分且保持语义可控？

需要裁决语言处理和模块加载的职责分割，同时保持 GraalJS 执行语义可控。问题维度包括：

- 手写实现优先时，转译、解析、模块解析、缓存和执行之间的 seam 如何划分；候选纯 Java 小库应以什么正确性、语料覆盖、体积和维护条件比较；不引入非纯 Java 转译依赖，10+MB 量级的用户体验门槛必须被量化而不是默认接受。
- JS、TS、JSX、Python、CJS、ESM 的现有能力如何保留和验证；本票不能默认删除任何语言，也不能让同一语义长期存在两条并行管线或一个无法定位责任的 Global pipeline。
- 来源位置、source map、诊断、模块身份、缓存失效和 Graal 执行契约分别由哪个模块负责，脚本作者看到的错误如何仍能追到源文件。
- 本地可信脚本与远端显式受限授权如何影响加载与执行路径；加载器拒绝、降级和 reload 失败语义如何与运行时票的契约一致。

证据入口：[runtime evidence](../evidence/runtime-and-modules.md)、[proposal.md](../proposal.md)。本票只能提出可比较的 pipeline 与模块边界，不能先把任何语言、GraalJS 或候选库判定为删除或替换对象。

## Resolution

### 逻辑 Module

1. **Script Preparation**：输入 source/path、extension、requested mode 和 trust-approved source，输出不可变 prepared module，包含 language id、module mode、code/IR、source map、诊断位置和稳定 cache key。它不创建 Graal Context，不决定 HostAccess，也不读取 Minecraft。
2. **Module Resolution/Cache**：负责 CJS/ESM identity、require/import/link、dependency graph、cache hit/invalidation、legacy CJS bridge 和 module lifecycle。它消费 prepared module，不复制编译语义，也不持有平台 callback。
3. **Script Execution Environment**：负责 Graal Context、Node shim、bindings、managed surface、sandbox/trust、timer/listener/session 和执行/关闭；它消费 prepared/resolved module 与 frozen Plugin Runtime，遵守 05 的 reload 与失败保留契约。

这些是 common 内的逻辑 Module，不自动创建 Gradle project 或新的 runtime owner。调用者跨小 Interface；内部 parser、lowering、cache 和 factory 可以继续细分为 Implementation/internal Seam。

### 语言与依赖策略

1. JS、ESM、CJS、TS、JSX、TSX 和 Python 继续属于支持范围；本票不批准删除语言、legacy path 或 GraalJS。
2. 以可控的纯 Java Implementation 为默认。非纯 Java 转译依赖不进入候选；小型纯 Java 库只有在完整语义 corpus、source-map/diagnostic、许可证、维护状态和体积审查通过后，才可替换局部 Implementation。
3. 同一公开语义不得长期存在两条并行 pipeline；legacy bridge 只能是明确的迁移/兼容输入，必须有删除条件和 characterization。
4. source map、module identity、cache invalidation、错误阶段和 source location 是 Interface 的一部分；Graal execution 不得吞掉准备阶段或原始文件位置。
5. local trusted 与 remote explicitly-authorized source 走同一套可追踪阶段，但拒绝、降级和 reload failure 必须保留 05 的结果语义。

本票关闭语言/模块职责方向；不批准替换 compiler、引入候选库、删除语言或修改执行语义。具体 corpus、baseline、候选评估和阶段门禁由 07 记录。
