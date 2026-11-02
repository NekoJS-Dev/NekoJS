# 五项社区提案对 stonecutter 的适用性

调查日期：2026-10-08，2026-10-09 完成结论复核。本地基线为 `ec7fe6848afc1970661ee1f38a7c7c6d4575530c`；读取时三个 PR 均开放且目标是 `master`。调查由三个 GPT-6 Luna xhigh 子代理分别检查 compiler corpus、lambda Probe、自定义声明，主代理复核并执行检查。以 GitHub 的 PR 实际 diff 为准，不把与当前 master 的整段分支差异算成 PR 修改。

本地 HEAD 与这三个 PR head 的 `git merge-base` 均无结果。不能通过提交包含关系判断功能是否已覆盖；应按实际内容适配，而不是把 master 的旧收集架构带回来。

## 结论

| 提案 | 当前 stonecutter | 建议 |
| --- | --- | --- |
| [Issue #60](https://github.com/NekoJS-Dev/NekoJS/issues/60)：函数接口类型导出 | Graal 可接收回调，源码校验器也识别 SAM；Probe 仍缺少通用 lambda 输入声明 | 需求成立，与 #65 一起处理。属于声明能力补齐，不必重做运行时转换。 |
| [PR #65](https://github.com/NekoJS-Dev/NekoJS/pull/65)：lambda 输入别名 | 现有适配器别名管线可以复用；继承泛型关系仍有信息丢失 | 采纳方向，修正 SAM 和泛型边界后移植，不能原样接受。 |
| [Issue #63](https://github.com/NekoJS-Dev/NekoJS/issues/63)：自定义类型声明 | 有追加手写声明、绑定 typeOverride、结构化 Builder 契约，但没有任意 Java 类的完整声明替换 | 需求成立；追加声明不足以覆盖原有 class，需明确增加替换能力。 |
| [PR #66](https://github.com/NekoJS-Dev/NekoJS/pull/66)：插件注册类声明替换 | 当前 Plugin Runtime 已由 Point 产品驱动，不再采用 PR 中的 BootstrapState 收集方式 | 迁移设计后引入，先解决收集归属、作用域、依赖闭包与冲突规则。它提供插件注册 API，而非 Issue 原先提出的注解。 |
| [PR #61](https://github.com/NekoJS-Dev/NekoJS/pull/61)：TS 擦除语料扩充 | 隔离复跑仍有 19 个失败，且现有测试未覆盖这些位置组合 | 语料很有价值；先校正无效输入，再按真实缺陷补回归与修复。不能用全量 skip 代表支持完成。 |

建议先修复 #61 暴露的有效 TS 擦除失败，随后推进 #60/#65；#63/#66 先明确替换契约，再实现。三项工作独立，不必捆绑成一次合并。

## #60 / #65：具体问题

PR head：`255e9ba723f0d0bc6a950628585895da92120dd2`。

1. **合法 SAM 被漏判。** 新增 `FunctionalInterfaceAliasGenerator.singleAbstractMethod` 仅排除 declaring class 是 Object 的方法。`Comparator<T>` 自己声明了 `equals(Object)`，因此被误判为多抽象方法。继承的重复签名、协变返回也未合并。`Pr65SamAudit.java` 复刻该算法，实际输出 `Comparator SAM: null`。
2. **继承泛型未代入。** `interface StringConsumer extends Consumer<String>` 没有自己的形参，但反射继承方法的参数仍是父接口的 `T`；PR 直接输出该名字，会生成未声明类型变量。审计输出记录了 `own type parameters: []`、`SAM parameter: T` 与父接口 `Consumer<String>`。
3. **已有更完整的规则可复用。** 当前 `EventCallbackSourceValidator.java:181-240` 已处理 Object 签名排除、重复签名、协变返回和继承类型变量替换；`NativeSamCallbackTypesTest` 已覆盖这些边界。应统一 SAM 规则，避免 Probe 新增一个更弱实现。
4. **输入契约需要保留。** PR 的别名只保留箭头函数，排除真实 Java 接口对象；当前输入参数本来可接收这些对象，直接改成纯函数类型会收窄声明。需要同时测试 JS lambda 与真实 host 实例。raw 泛型接口也需要合法的默认实参，不能引用缺少类型参数的 `$Foo_`。
5. **回调参数方向需要核对。** PR 对回调参数和返回值均使用 `input=true`。回调参数由宿主传入，回调返回值由脚本提供；例如实际 ItemStack 参数应允许读取宿主成员，不能无理由扩展成字符串输入别名。这个问题属于静态契约审查结论，尚未执行移植后的端到端验证。

当前 `TypeReflector.java:49-60` 使用裸 `getSuperclass()` / `getInterfaces()`，所以 PR 的继承泛型修复仍有价值。建议与 lambda 检测分别验证，再接入同一个 Probe 管线。现有 goldens 应通过生成工作流更新，并检查每项差异；PR 未增加针对新增生成器的完整正负类型测试。

当前 `IndexFileGenerator.java:268-287` 已硬编码部分 JDK 函数接口别名，但普通参数渲染并未把它们完整接入，因此不能据此认为 #60 已解决。此外，PR 仅对顶层方法类型变量降级 `any`，`<U> void accept(List<U> values)` 中嵌套的 U 仍会泄漏。移植测试应覆盖嵌套类型变量、适配器别名与 lambda 别名同名、hidden 接口别名输出，以及共享 `toRef` 对通配符下界修改的影响。

## #63 / #66：具体问题

PR head：`9d45c1fdf676bb6ab67652e75c4ae177fe81e22b`。

1. **移植入口是现有 Point。** 扩展 `TypeDocsRegister` 和 `TypeDocsPoint.Bucket` / `TypeDocsSnapshot`，同时覆盖共享该 Bucket 的 `NodeTypeDocsPoint`；由 `NekoPluginRuntime` 合并两点产物。当前 `TypeDocsPoint.java:36-86` 和 `NekoPluginRuntime.java:310-315` 提供现成模式。不要恢复旧 BootstrapState 的平行收集器；也无须为既有 type_docs 通道另加一个 Point。
2. **ScriptType 过滤缺失。** 生产路径 `ProbeCoordinator.java:322` 使用全量 `NekoScriptCatalog.snapshot(runtime)`。PR 后端对这份全量清单按 FQN 取最高优先级，没有按 ScriptType 过滤，而 Java package 声明又是共享输出。只改 per-type snapshot 不能修复生产路径。需要明确替换是否必须全类型一致，或另设计按类型隔离的输出。
3. **显式 import 不形成生成闭包。** PR 仅把 `importFqns` 写入 `IndexFileGenerator` 的 import cache；当前后端在预声明之前已建立 `classesToGenerate` 与 PackageTree（`TypeScriptProbeBackend.java:101-147`）。只有手写声明引用的新类可能根本不生成对应模块。需要把有效依赖纳入收集和 IR 流程，并对不能生成的依赖给出可诊断结果。
4. **冲突规则不确定。** 同 FQN 同 priority 使用后者覆盖，结果依赖收集顺序。应明确拒绝冲突或使用稳定、公开的规则。
5. **隐藏声明会被重新输出。** PR 替换分支提前 return，跳过正常 IR 声明与 edited import 收集。正常 renderer 会对 `decl.hidden` 返回空串，但 `IndexFileGenerator.java:151-159` 直接输出缓存声明；其隐藏集合只过滤 import/别名等引用，无法挡住新写入的手写声明。需要检查 hidden 再替换，并通过测试明确 replacement、hide 和成员编辑的优先级。

最低验收应包括插件实际注册、node_type_docs 合并、ScriptType 冲突、新依赖模块与别名输出、同优先级冲突、modify_type 交互，以及 Issue RPC 示例的 schema → returns → fn 正确推导和错误类型拒绝。声明替换只改善 TS，不证明 Java RPC 运行时支持新增行为。

## #61：复跑与语料边界

PR head：`c7e875713f43e37274d9ffb38c45c13ef62e5720`。

没有修改正式测试源。审计从固定 PR 提交提取测试，仅改临时类名并绕过 KNOWN_GAPS 的 abort，继续使用当前分支的 compiler 和 `CompilerExecutionAssertions`，保留 parse、module parse、行数、行为和拒绝断言。

- 实际 corpus 从 **10 条扩展到 59 条**，不是 PR 描述的 13 → 61 条；40 通过、19 失败。
- 两个 decorator 拒绝用例和两个行为断言全部通过，总计 **44 通过、19 失败、0 跳过**。
- 19 个失败都在 PR 的 KNOWN_GAPS 中，包括十个函数类型位置、对象类型/解构/交叉类型、泛型字段、type re-export、重载、this 参数、嵌套 namespace、for-of。
- 其中 `for (const item: string of list)` 被本地 TypeScript 5.8.3 明确拒绝：**TS2483**。它不能作为“有效 TS 应成功擦除”的正向用例。至少要把它移入输入拒绝/诊断类别。
- `enum Mixed { A = 1, B = 'two', C }` 也不宜作为规范有效语料：字符串成员后的 C 需要显式初始化。parse 成功只证明产物语法有效，不能证明 enum 语义正确。
- interface 整体擦除后 parse 成功，不能证明其中的具体箭头签名处理正确；语料也不等于 TS type-check 或“全部语法覆盖”。移植三个 PR 时，新写注释/Javadoc 应改为当前规范要求的英文。
- KNOWN_GAPS 在调用编译器前就 abort，所以即使某条后来已修好，也不会自动检测到修复。接受 skips 作为临时缺口清单时，应有能实际执行它们的审计与明确移除条件。

复跑命令：`powershell -File docs/architecture-refactor/evidence/2026-10-08-community-stonecutter/audit-pr61.ps1`。审计任务的 Gradle 退出成功表示 harness 成功执行；**判断缺陷要读取 AUDIT_TOTAL，不能把 BUILD SUCCESSFUL 当作 63 项通过。**

## 验证与剩余缺口

| 检查 | 结果 |
| --- | --- |
| `./gradlew.bat :common:check --console=plain` | 通过；本次隔离检查执行，完整 test 任务 UP-TO-DATE。 |
| `:common:test`，过滤 compiler corpus/compiler/native SAM、TypeReflector/renderer、PluginHookPairing、ProbeBackend、Catalog、TypeDocs、IndexFileGenerator | 新执行 168 项，0 失败、0 跳过，完整命令见 PLAN.md。 |
| `npm.cmd run test:probe-types` | 通过，校验当前 managed API / JSX fixture 声明；当前 tsconfig 不覆盖 legacy Probe golden tree，也不覆盖尚未移植的 lambda 声明。 |
| PR 61 隔离语料审计 | 执行完成；44 通过、19 失败、0 跳过。见 pr61-corpus-audit.log。 |
| PR 65 SAM 算法最小复现 | 确认 Comparator 漏判和继承类型变量未代入。见 pr65-sam-audit.log。 |
| for-of 输入合法性检查 | tsc 退出 2，明确 TS2483；这是预期的反证，见 pr61-invalid-fixture.log。 |
| `git diff --check` | 通过。 |

没有实施、合并或 cherry-pick 三个 PR，没有修改 GitHub Issue/PR 或标签；新增的只有本地调查记录和复现材料。现有配置同步笔记及其他未跟踪文件保持原状。未运行移植后检查、五节点构建、真实游戏运行或外部 RPC 插件集成；本次不是维护者合并验收，尚无维护者验收结论。
