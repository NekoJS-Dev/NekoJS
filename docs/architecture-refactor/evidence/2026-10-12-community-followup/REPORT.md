# 五项社区提案的再次复核与修复

复核基线：`d1bfa0b29dbf1b0740f6e3dc9f96e44edab3f417`，分支 `stonecutter`。原实现提交为 `8922627e`；本次检查包含后续修复的当前代码。

按用户要求，三个 GPT-6 Luna xhigh 子代理分别只读分析 compiler、lambda／泛型、手写声明；没有修改文件或执行共享构建。主代理亲自复现、写回归、实现、验证和提交。

## 修改与契约

需求：[Issue #60](https://github.com/NekoJS-Dev/NekoJS/issues/60)、[Issue #63](https://github.com/NekoJS-Dev/NekoJS/issues/63)、[PR #61](https://github.com/NekoJS-Dev/NekoJS/pull/61)、[PR #65](https://github.com/NekoJS-Dev/NekoJS/pull/65)、[PR #66](https://github.com/NekoJS-Dev/NekoJS/pull/66)。现有三类能力已实现，本轮修复以下边界。

| 缺陷 | 修复与回归 |
| --- | --- |
| 泛型类后接 extends／implements 时残留类型参数 | 按已扫描的 class header 识别；验证继承、字段、构造器及行数。 |
| 类型注释中的分隔符干扰注解和括号检测 | 跳过注释，词法配对括号，箭头／对象类型起点跳过 trivia；验证运行值和行数。 |
| 无分号的 function／class 方法重载残留 | 识别换行结束的无体签名，覆盖有／无返回类型；保留下一行函数体，将方法识别限定在类成员层级，并保留函数和类字段中的跨行条件调用。 |
| namespace 把模板文本识别为 export | 各擦除／转换扫描共用完整模板跳过规则，覆盖普通、转义及嵌套模板。 |
| 手写依赖同名时重复 import | 无歧义的手写依赖优先保留标准名，其余分配唯一私有名；删除重复标准 import 回退。 |
| SAM 容器的 `? super T` 被投射为 T | 直接 SAM 边界与容器泛型区分；容器沿用现有宿主通配符映射，保留直接参数推导。 |
| 重命名依赖不再导出标准名称 | 保留重命名声明，补标准类型别名及实际存在的值声明，包含枚举输入别名。严格 tsc 覆盖泛型类与枚举。 |
| 枚举替换仍反射无关方法并被跳过 | 只读取常量字段名；隔离 loader 隐藏泛型依赖，并用拒绝初始化的 enum 验证反射绕过与不初始化。 |

compiler 回归实际失败 4 项、import 回归失败 2 项、lambda 回归失败 1 项、重命名／enum 回归失败 2 项：[compiler](compiler-red.log)、[imports](authored-red.log)、[lambda](lambda-red.log)、[replacement](replacement-red.log)。没有以 skip 处理新增回归。

没有新增公共注册通道、依赖、Minecraft／loader 引用或运行时所有者。Point 收集、全 Script Type 替换、优先级、依赖闭包及 hide 优先级保持原契约。

同名依赖在手写文本中使用 `import("java:package").$Foo` 内联类型，仍列入显式依赖集合。不重写不透明的 TypeScript。重命名依赖保留标准 Java 类型和值身份；以 `export type`／`export const` 发射，支持 module augmentation，泛型约束保留，纯实例 interface 不增加静态值。中英文插件文档同步说明。

容器通配符仍采用既有上界／any 擦除，不是完整 Java 方差模型。实际 Graal 测试确认容器可以装有字符串；错误假定为 Payload 调用 `getName()` 会失败。修复消除虚假的 Payload 推导，不保证 any 的静态安全。

现有 golden 检查没有内容差异，本轮无需更新派生产物。两份原有 `.d.ts` 状态噪声没有文本 diff，也未纳入提交。

## 验证

| 检查 | 命令与结果 |
| --- | --- |
| 编译器及实际模块定向回归 | `./gradlew.bat :common:test --tests 'com.tkisor.nekojs.core.compiler.*' --tests com.tkisor.nekojs.core.node.NodeModulesJsRegressionTest --tests com.tkisor.nekojs.core.module.DiagnosticPhaseMatrixTest --console=plain`：525 项，0 失败、0 跳过。[日志](compiler-final.log)。 |
| 完整 common、隔离、处理器及版本检查 | 下述完整命令成功，common 2236 项，0 失败、0 error、4 项既有 assumption 跳过；processor 13 项、0 失败（任务 UP-TO-DATE）。`checkCommonIsolation`、addon fixture 依赖隔离、Point 配对、API/golden 回归均通过。guardLint 扫描 548 个文件、384 个守卫块、0 警告。五节点 compileJava 成功，最终复测均 UP-TO-DATE；此前同一源代码的 NeoForge 编译已执行成功。最终为 44 tasks，4 executed、40 UP-TO-DATE。[日志](final-check.log)。 |
| 真实 Probe 输出 TypeScript | `npm.cmd run test:probe-types`：两套 tsc 均成功；community fixture 使用 strict 和 `skipLibCheck: false`，覆盖 RPC、同名依赖、重命名泛型类及 enum、lambda 容器边界。[日志](probe-types.log)。 |
| 最终 diff | `git diff --check`、`git diff --cached --check` 通过；无 golden 内容差异，没有降低检查或增加 skip。 |

完整命令：

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:compileJava :26.1.2:compileJava :26.2.0:compileJava :26.1.2-fabric:compileJava :26.2.0-fabric:compileJava --console=plain
```

初版无分号识别误删三元分支中的调用，导致 buffer／JSX runtime 启动失败；补充回归并收紧类成员检测后，实际 Node 模块与诊断矩阵复测通过。严格 tsc 捕获 module augmentation 不允许 export 列表的问题，改为类型／值声明后通过。完整检查首轮另有 PackSyncClientTest 临时目录移动失败（2236 项中 1 失败、4 跳过）；未修改该测试或生产逻辑，原命令复测通过。[首轮日志](final-check-first.log) 保留失败。

补充类字段回归时发现返回类型检测也会误判跨行普通调用；修复同时保留匿名函数、带注释的对象方法和静态方法，525 项编译器／Node／诊断测试复测通过。此后完整检查又出现 PackSyncClientTest 缓存目录移动失败，以及 PackSyncTrustStoreTest 损坏文件覆盖后重新读取的断言失败（2236 项中 2 失败、4 跳过）；[日志](final-check-pack-retry.log) 保留，PackSync 代码和测试没有改动。原命令无代码改动复测通过；这些文件操作失败的具体原因没有进一步诊断，不宣称其已修复。

## 剩余范围

未运行完整五节点 build、GameTest、真实游戏客户端或外部 RPC 插件集成。RPC 验证为真实 backend 输出与严格 tsc fixture；Point 注册由独立测试覆盖，不是外部插件注册到 RPC 运行时的端到端验收。没有维护者人工验收结论；没有推送或修改 GitHub Issue／PR。
