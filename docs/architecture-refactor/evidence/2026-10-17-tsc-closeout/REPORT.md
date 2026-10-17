# TypeScript 声明依赖补齐：实现与验收

本批完成已批准的完整依赖声明机制；完整 IDE 门禁仍未通过，票 37 保持开放。默认十入口 TSC 从 6991 条变为 7071 条，净变化 +80。缺失模块 TS2307 从 3433 条减少到 29 条，但新增真实库的继承、成员冲突和泛型问题仍须修复。不能将依赖补齐等同于错误总量收敛。

## 实现与契约

- 主扫描保留 include、exclude、forceScanMods 和 maxDepth；普通通配上界与构造器上界按同一个签名边计入原 BFS。TS 后端另外补齐实际声明、Binding、事件/dispatch、输入别名的真实 Java 引用，不扩大运行时访问权限、Binding 注册、持久数据或网络协议。
- 新依赖按真实反射 IR 渲染；每轮生成只反射一次，已编辑 IR 保持权威，手写类替换先于原签名反射。补齐同包依赖、heritage、泛型上界及上下文回调引用，并固定依赖加入顺序。没有空壳、批量 any、移除继承或公开成员。
- exclude/hide 继续生效。默认实际图仍引用排除的 `ModificationCandidatePlan`；NeoForge 还引用 `VillagerTradeQuerySurface`，1.21.1 另有在专服不可装载的客户端扩展/关联签名。`@special/dependencies.json` 如实记录 `complete: false` 和原因，产生 NEKO-4048。此 complete 字段只描述 Java 声明依赖，不证明所有手写声明或完整 IDE 正确。
- class 构造器局部泛型投影到第一个 Java 上界；无界/递归位置按使用点擦除，类级/方法级变量与显式编辑不变。该近似不保留跨构造参数的类型关联。
- 完整库图曾触发十入口 TypeScript 5.8.3 栈溢出。最终仅为 19 个 fastutil 函数接口的已测量结果参数设置 `out V`，保留 K、主机对象/回调分支及所有成员。已编辑 IR 不套用原库规则。实际库的输入关系正负例前后均通过；最终默认编译器无需增大栈，十入口均返回完整诊断。初始崩溃、拒绝的原型及仅用于调查的增大栈结果保留，不作通过证据。
- canonical legacy-tree 通过原有 `:common:regenerateGoldens` 生成：189 个主类补齐到 359 个引用类，新增实际 JDK 包与依赖报告，并去掉未发射类型的无用 import。字节级快照回归通过，没有手改或排序弱化快照断言。

## 验证

基线 stonecutter `3954116c`。候选由本提交所含源文件、压缩包内实际源文件快照、preparation manifest 的 sourceHashes、五节点 JAR SHA256 与 classBindings 共同定位；旧 HEAD 字段不冒充已提交候选。全部实现和验证由主代理完成；Luna xhigh 只读调查。

通过：

1. `./gradlew.bat --offline :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`：4949 tests，271 skipped，0 failures / errors。包括 common 隔离、制品与节点门禁。
2. `npm run test:probe-types`：原有项目与通配、构造器、依赖闭包、结果方差的实际生成严格正负例通过；没有启用 skipLibCheck。
3. 五节点新官方 JAR 全部类字节绑定后实机通过：注册、生成、重载与适用的领域断言，全部正常停止；全部 Python stub 默认 Pyright 为 0 error / warning。
4. 19 个实际 fastutil 接口的协变、主机输入、K 双向关系、回调正负例在五节点均无 fixture 诊断。before 需要增大栈读取原始问题，仅作对照；after 使用默认栈。所有完整 TSC 非零退出仍按失败记录。
5. `python tools/nekojs-ci-gates.py all`：4 checks / 0 failures。`node scripts/wiki-docs.mjs check --structure-only`：0 problems。

| 节点 | 默认 TSC server / startup | Python stub / errors / warnings | 主扫描 / TS 引用总类 |
|---|---:|---:|---:|
| 1.21.1 | 643 / 644 | 320 / 0 / 0 | 2460 / 3038 |
| 26.1.2 | 708 / 709 | 378 / 0 / 0 | 2934 / 3467 |
| 26.2.0 | 697 / 698 | 349 / 0 / 0 | 2647 / 3257 |
| 26.1.2-fabric | 755 / 756 | 296 / 0 / 0 | 2443 / 3394 |
| 26.2.0-fabric | 730 / 731 | 266 / 0 / 0 | 2161 / 3192 |

失败与未运行：

- 完整默认严格 TSC 仍失败，十入口共有 7071 条。按文件、代码、首行消息的诊断多重集对照（忽略行列）新增 5142、移除 5062，不是“无新增错误”。原始逐条结果已封存。
- `node scripts/wiki-docs.mjs check --strict` 仍有 6 个既有问题：plugin-development、type-adapters、event-extensions 的源摘要/翻译审阅状态。本批两组文档的摘要已更新，未代签人审。
- 暂存后完整 `git diff --cached --check` 报告两处新增 golden 的 EOF 空行：`@package/javax/index.d.ts`、`@package/javax/security/index.d.ts`。它们是仅含子包转出的模块，沿用既有生成器在转出段后添加空行的格式。本批保留原始生成字节，不手工修补 golden，也没有关闭该检查；完整检查结果按有告警记录。
- 未执行正式 startup/reload 性能、客户端多节点首帧、最终候选 release/data rollback 或正式发布。既有性能失败与这些门禁没有被本批覆盖；没有维护者整票或发布结论。

## 输出规模与单次观察

以下 before 是普通通配上界候选，after 是本批实际新制品，均为相同验收夹具的五节点并行运行。耗时是单次 `/nekojs probe typescript` 观察，包含环境噪声，不能代替正式性能测试。

| 节点 | d.ts 文件数 | d.ts 字节数 | Probe 毫秒 |
|---|---:|---:|---:|
| 1.21.1 | 337 → 392 | 4088377 → 5215761 | 2328 → 9174 |
| 26.1.2 | 392 → 446 | 4941135 → 6059154 | 2816 → 11355 |
| 26.2.0 | 363 → 428 | 3672644 → 4840414 | 2232 → 9929 |
| 26.1.2-fabric | 310 → 539 | 4067235 → 5554965 | 2200 → 10626 |
| 26.2.0-fabric | 280 → 518 | 3023471 → 4600302 | 1219 → 5105 |

## 继续工作

依赖缺失已显式化，但完整声明仍需处理：Java 实例字段/方法及 Bean 同名冲突、子类与父类的静态/实例契约、外部库泛型/内部类作用域、手写声明中未定义或值/类型混用的名字。优先按诊断多重集处理共享原因，并保留 Graal 实际语义。其他公开符号删除不在既有五处 Entity 批准范围内。

票 28 的客户端矩阵、票 37 的完整 IDE、正式性能与最终发布/回滚验收继续开放。本批未创建 PR 或 GitHub issue，未推送或发布。

## 证据

- [范围与批准](DEPENDENCY-DECISION.md)、[工作记录](WORKPLAN.md)
- [机器摘要](dependency-summary.json)、[文件清单与 SHA256](dependency-manifest.json)、[原始证据包](dependency-evidence.zip)
- `python docs/architecture-refactor/evidence/2026-10-17-tsc-closeout/verify.py` 校验完整性、XML、实机/IDE 结论及正负例；不会把完整 TSC 失败转换成通过。
