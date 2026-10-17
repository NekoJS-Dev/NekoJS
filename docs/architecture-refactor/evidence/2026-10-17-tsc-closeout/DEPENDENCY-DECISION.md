# 完整声明与扫描边界的决策提案

## 已确认的问题

当前默认 SMART 扫描的 include/exclude/forceScanMods 与 `maxDepth = 5` 控制可达类集合，但 TS 后端仍输出集合外的签名引用。`3954116c` 的十个默认 server/startup 入口共有 6991 条诊断，其中 3433 条 TS2307。最常出现的三个缺失模块为 `java:com/mojang/serialization`（486）、`java:io/netty/buffer`（476）、`java:it/unimi/dsi/fastutil/ints`（418）。

这些数字来自已有实际制品的完整检查，不是定向 fixture。当前没有用空 interface、any、skipLibCheck、移除继承或移除公开成员消除诊断。

普通通配上界候选修复已在共享收集器补充 `WildcardType.getUpperBounds()`：三项新增回归通过，原有扫描深度、include/exclude 与 SAM 下界投影保持。五节点构建、实际运行和完整 Pyright 均通过，但完整 TSC 变为 7205 条：26.2 新收集的龙阶段等类位于深度边界，父类及其他签名依赖仍缺失。这一候选不能按“完整 TSC 收敛通过”交付。

## 建议的后续范围

采用完整依赖声明策略：原扫描配置继续决定入口/主扫描集合；TypeScript 后端对实际发射的公开签名另外构建依赖闭包，包括 heritage、泛型上界、字段、构造器、方法和适配器引用。由该闭包新增的类提供真实声明，不生成空壳。不自动扩大运行时 Java.type 权限、Binding、事件、能力矩阵或支持节点。

这会改变默认生成的范围、文件量和耗时；原 maxDepth/include 不再能被解释为全部 TS 类型输出的硬边界。必须在 Probe 配置说明和迁移材料中明确区分主扫描与声明依赖闭包。exclude 与隐藏类如何进入签名仍需显式制定规则，不能暗中绕过；若必需依赖被禁止且无法正确表达，应明确报告未完成，不伪造可用类型。

预计涉及文件：

- `common/.../probe/ProbeClassCollector.java`：保留主扫描的边界，并正确收集实际投影的普通通配上界。
- `common/.../probe/backend/typescript/TypeScriptProbeBackend.java`：建立、复用依赖 IR，按闭包生成声明；不重新执行 Probe 编辑事件或反射覆盖已编辑的 IR。
- `common/.../probe/backend/typescript/IndexFileGenerator.java`：核对每个实际引用与真实导出，保留模块局部别名和手写替换声明。
- 现有 ProbeConfig/配置输出的说明与 wiki：说明新的默认输出范围、禁止项处理及性能影响。
- 对应 common 回归、真实严格 TSC 正负例和五节点实际制品证据。

本提案不是承诺依赖闭包单独就能解决全部诊断。字段/方法同名、静态继承、构造器泛型和非静态内部类泛型仍需各自修复；不能借此删除 MinecartFurnace.push 等未批准的公开符号。

## 验收

先以独立原型验证闭包规模、默认扫描边界、exclude/hide、已编辑 IR 和手写替换声明，再确定最终实现。完整检查必须保留原默认 server/startup 入口、strict 与 skipLibCheck=false，逐条比较新增/移除诊断。新增真实类也必须检查父类、成员引用、Python 影响、五节点构建与实际运行。生成耗时需与原候选对照；正式性能与发布门禁仍单独执行。

## 保持原边界的替代方案

继续修复旧扫描边界内的独立问题，并记录依赖缺失；不能承诺默认完整 TSC 归零。普通通配上界候选及其新增诊断应完整保留，不把定向测试通过当作整体验收。

维护者决定：2026-10-17 主会话明确选择「允许调研并实现完整依赖声明方案（推荐）」。先验证原型，再实施扩大默认依赖闭包的改动；此授权不包含删除其他公开成员或正式发布。

## 完整库图暴露的编译器问题

首次完整图在十个默认入口均触发 TypeScript 5.8.3 的 `RangeError: Maximum call stack size exceeded`，并非零诊断。追踪定位到 fastutil 的函数接口、compose/andThen 以及回调/主机对象联合的递归方差推导。提高进程栈只用于读取底层诊断，不能当成默认检查通过。移除重复继承成员、改写别名包装均未解决；全面使用 `in out` 虽避免崩溃，却会改变输入类型关系，因此拒绝采用。

进一步原型仅对 fastutil 的 19 个 Object/Reference 结果函数接口标记 `out V`，保留 K 的推导和所有成员、继承、回调联合。TypeScript 调试得到的既有接口关系是 K 双向、V 协变，原型不额外收紧为不变泛型；默认编译器可返回诊断且不增加 TS2636。生成器沿用既有精确覆盖机制，仅对这组未编辑的接口应用已测量结果规则；已编辑 IR 保持权威。实际库的主机对象、通配输入、回调正负例及完整五节点复测仍是最终验收条件。

机制依据：[TypeScript 4.7 官方说明](https://www.typescriptlang.org/docs/handbook/release-notes/typescript-4-7.html#optional-variance-annotations-for-type-parameters)解释了显式方差可避免递归推导，也明确反对无依据地全面收紧参数。这里以实际测量和兼容用例为依据，不将文档中的一般建议直接当作本项目验收结论。
