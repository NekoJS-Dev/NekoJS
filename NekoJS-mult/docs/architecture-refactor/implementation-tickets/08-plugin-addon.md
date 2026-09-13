# 08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活

**What to build:** 用一个真实外部 addon fixture 证明 Java 插件能通过 loader discovery 和 fat jar 依赖进入 Plugin Runtime，沿既有 Point/Contributor/Hook 贡献，bootstrap 后经 Extension Handle 或脚本 binding 消费产物；普通 reload 不重新 bootstrap/freeze 插件，而 generation session object 失效。该票只补真实消费链和边界修正，不新增 Point 或插件框架。

**Blocked by:** [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)

**Status:** closed

**Assignee:** dsh-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 构造带 NeoForge production metadata 和 Fabric `fabric.mod.json` NekoJS entrypoint 的 test-only 外部 addon 制品，模拟真实 fat jar/classpath discovery 输入。
- 让 addon 通过既有 Contributor/Hook 与一个既有扩展点贡献，bootstrap 后由 Handle/result 和脚本可见 binding 消费同一冻结产物。
- 验证 discovery、contribution、dependsOn 拓扑、initialize/collect/finish、freeze、initialization/result 的外部顺序与错误定位；保留现有 Point 生命周期，不新建第二套 Collector 框架。
- 在普通脚本 reload 前后断言 Plugin Runtime bootstrap/freeze 只发生一次，Handle 产物仍可读，generation token 不能操作新 session。
- 补依赖错误、重复 id、freeze 后注册和未知依赖的 addon 级失败输出。
- 交付最小外部 addon 可运行示例与插件作者迁移材料；示例只使用已通过 gate 的公开依赖和入口。
- 删除无生产调用者的 legacy manager facade/bootstrap 入口；仅当外部 fixture 继续需要嵌入入口时保留并记录原因。

## Acceptance criteria

- [x] 外部 addon fixture 是独立 test-only artifact：依赖面与未来第三方插件一致，不 import 生产内部测试 seam，且五个生产 jar 均验证不包含其类、资源或 metadata。
- [x] 至少一个 NeoForge 节点通过真实 annotation/mod metadata discovery、至少一个 Fabric 节点通过真实 `fabric.mod.json` entrypoint discovery 发现并执行 addon；干净 run/mods 目录重复执行结果一致。
- [x] fat jar 与 classpath 两种 discovery 输入至少覆盖一种真实目标发布形态；未覆盖形态显式记录，不得宣称两者均已证明。
- [x] 现有 Point initialize/collect/finish、dependsOn、Contributor/Hook 与 Handle 语义保持为唯一插件生命周期；扩展点间依赖和结果可见性用真实 fixture 表达，不新建第二套 initializer/merger/finisher 框架。
- [x] 一个扩展点不得通过回调初始化或改写另一个扩展点的中间状态，finish 只冻结自身结果；Collector 式思路只能作为改进现有 Point 的评估输入，不能先造通用框架。
- [x] 真实 loader discovery 能发现外部 addon，owner identity、priority、requiredMods/clientOnly 规则与旧契约一致，内部实例化不能冒充通过。
- [x] 随实现交付的外部 addon 最小可运行示例和插件作者迁移材料可按公开说明复现；示例只使用已通过 gate 的公开依赖、loader metadata 和入口，不依赖生产内部测试 seam。
- [x] addon 贡献按 Point 事实源收集，Hook/Contributor 投影效果等价，脚本侧可观察到同一冻结产物。
- [x] Extension Handle 在 finish 前拒绝读取，finish 后可读产物；依赖、环、重复 id、freeze 后注册在对应阶段带 addon 定位失败。
- [x] 普通 reload 不重新 discovery、bootstrap 或 freeze Plugin Runtime；Point result 与 Handle 身份保持有效。
- [x] reload 后旧 generation 的 session object、事件 token 或临时计划不能静默操作新 session，错误明确指向失效 generation。
- [x] session 清理不关闭仍由进程级 Plugin Runtime 持有的共享 Java 对象；Binding.value 的共享对象不被误当作 generation 快照。
- [x] 外部 addon 在至少一个 NeoForge 节点与 Fabric 当前节点的 loader 启动/烟测路径中被发现并执行。
- [x] 无调用者 manager facade、旧 legacy bootstrap 和 loader 私有 Point registry 旁路仅在外部 addon 与 reload fixture 通过后删除。

- [x] 对声明为冻结的 Point 产物，保留旧累积器引用再写的 fixture 证明完成后写入被拒绝或不影响已发布结果；注册图 freeze、累积器 seal 和结果发布是不同边界。共享 Java 对象按已定生命周期处理，不由只读 Map 外壳推导任意对象深冻结。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [PR 37 维护体验回归约束规格](../specs/00-pr37-maintainer-research.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md): 进程级 Handle 与 generation session 的分离只有在完整 candidate reload 后才可观察。

## Scope and coordination

- **Rationale:** 插件链路的主要缺口不是重造 Point 框架，而是真实 discovery 到消费的端到端证据；单 context 可用 addon marker、Handle result、reload 次数和失效 token 独立验证。
- **Coordination:**
  - MANAGED_SURFACE 组拥有公开签名和 declaration 观测；本票只消费既有 Point/binding，不新增规范源。
  - BUILD_BASELINE 提供旧 addon/Probe 输出输入；若外部 fixture 制品接线需要构建支持，只协调不重开 build/release 票。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
## Closure record（2026-09-13）

- 执行者：dsh-agent。分支 `ticket-08-plugin-addon`（基于 master `4e258d3a`），未 push、未 merge。
- 实施区间：首发 commit 至本 record 所在 commit。逐 commit 清单见最终 `git log --oneline 4e258d3a..HEAD`。

### 交付物

- 生产源码（3 处，均为最小边界修正，无公开契约变更）：
  1. `core/module/NekoModuleResolver.java` —— `resolveBareModule` 捕获 `InvalidPathException`，把含冒号的
     bare specifier 交给既有 SPECIAL 档（修真实 addon 暴露的 `Illegal char <:>` 缺陷）。
  2. `core/plugin/NekoPluginBootstrap.java` —— `bootstrap(List, ScriptPropertyRegistry)` 由 `public`
     收窄为 package-private（AC14）。
  3. `core/plugin/NekoPluginRuntime.java` —— 删除 `@Deprecated` 且零调用者的 legacy `bootstrap` 入口（AC14）。
- 测试：`NekoPluginAddonBoundaryTest`（8）、`PluginRuntimeReloadLifetimeTest`（1）、
  `NekoPluginNodeModuleResolutionTest`（7）、`NekoModuleResolverTestSupport`（共享构造）。
- bench：`bench/plugin-addon/`（fixture + 两个 AC6 控制类、build-addon.ps1、run-addon-smoke.ps1、
  verify-jars.py、MINIMAL-ADDON.md、DEAD-CODE-AUDIT.md、README.md）。
- 报告与证据：`../baseline/2026-09-13-plugin-addon/REPORT.md` + `evidence/`。

### 行为保持

- `:common:check` 与 `guardLint` 全绿（248 守卫 / 331 文件 / 0 问题 0 警告），**无 golden 更新**；
  证据 `evidence/gradle-common-check-guardlint.txt`。
- `NekoModuleResolverTest` 既有 7 例断言（含 `resolveForRequire` 的未知名包 MODULE_NOT_FOUND 语义）全部不变。
- 被删/被收窄的两个 bootstrap 入口均零生产调用者；收窄后跨包的 `NekoProbeBuiltinPluginTest` 改走
  生产入口 `NekoPluginRuntime.bootstrapOwned`。

### 逐 AC 判定：15/15 pass

| AC | 判定 | 一句话证据 |
|---|---|---|
| AC1 | pass | `verify-jars.py`：五个生产 jar 的入口表与 metadata 文本均无 addon 类/资源/metadata |
| AC2 | pass | NeoForge 走 FML annotation/mod metadata、Fabric 走 `fabric.mod.json` 的 `nekojs` entrypoint；两 loader 各跑 **2 轮**，每轮从干净 run/mods 起，`consistency-*.json` 的 `consistent=true` |
| AC3 | pass | fat jar 已覆盖；classpath **未覆盖**并在 REPORT §2.1 与 bench README 显式记录（且已删除那个只打印不干事的 `-Skipped` 空开关） |
| AC4 | pass | 生产源码只动 3 文件，零新增 Point/框架类；边界语义全用既有 builder/Handle/Sealable 表达 |
| AC5 | pass | `NekoPluginAddonBoundaryTest`：不得读他点中间态（违序立即抛）+ finish 只冻结自身结果 |
| AC6 | pass | 两个兄弟控制类经**同一真实 discovery 路径**被 loader 扫到但被 `NekoJSBasePluginManager` 按 `requiredMods`/`clientOnly` 过滤，两 loader 的对应 check 均 true 且 LEAKED 日志为 0 |
| AC7 | pass | `MINIMAL-ADDON.md`，只用 `api.*` + `NodeModuleRegister` + loader metadata，无内部 seam |
| AC8 | pass | 同一产物经 Handle 与 runtime 两个观察面拿到同一实例；真实 addon 用基接口钩子形态消费同一冻结产物 |
| AC9 | pass | Handle 未 finish 拒读/finish 后可读；环/未知依赖/重复 id/freeze 后注册四种失败消息都断言含 addon 定位符 |
| AC10 | pass | 单测 `PluginRuntimeReloadLifetimeTest`（reload 前后同一 Map 实例、同一 `Binding.value()` 共享对象、generation 递增）+ 真机 4 项 check。白名单口径见 REPORT §4.3 |
| AC11 | pass | 旧 generation：`ScriptManager.from(oldCtx)` 抛 `No ScriptManager registered`、`ScriptContextRegistry.scriptTypeOf(old)==null`、旧 listener 计数不变、旧 timer 不再分发 |
| AC12 | pass | `bindingCloseDoesNotMutateSharedRuntimeOwnedJavaObject`：身份保持 + 未 tear down + 冻结产物同实例 |
| AC13 | pass | 两 loader 真机 `checks-*.json` 的 `all_rounds_passed=true` |
| AC14 | pass | `DEAD-CODE-AUDIT.md`：legacy `NekoPluginRuntime.bootstrap` **已删除**；`NekoPluginBootstrap.bootstrap` **已收窄为 package-private**；loader 私有 Point registry 旁路经全仓取证**不存在**；`NekoJSBasePluginManager` 有 9 处生产调用者故保留并写明。删除/收窄发生在两 loader 烟测通过之后 |
| AC15 | pass | 保留旧累积器引用再写被拒（seal）+ 已发布快照不变 + runtime 与 handle 同一实例；三个边界分开断言 |

### code-review 修订（逐条）

| 项 | 问题 | 处理 |
|---|---|---|
| A | 证据缺档：`gradle-common-plugin-tests.log` / `gradle-common-check-guardlint.log` 被仓级 `*.log` 忽略，未入库 | **已修**：改名归档为 `.txt`（不再被忽略），已 `git add` 入库；REPORT §10 全部文件名与盘上/库内一致 |
| B | **越权语义变更**：`isNamespacedRegistryKey` 把**任何**含冒号的 bare specifier 归为 `BUILTIN`，使未注册 id 绕过 `resolveForRequire` 的 MODULE_NOT_FOUND 检查 | **已修（语义修正）**：删除 `isNamespacedRegistryKey`；改为在 `resolveBareModule` 捕获 `InvalidPathException` 并复用既有 **SPECIAL** 档。既有契约（`NekoModuleResolverTest:69/:86`）保持，新增 `unregisteredNamespacedIdStillFailsUnderRequireSemantics` 钉住「未注册 namespaced id 仍抛 `Cannot resolve module`」。论证见 REPORT §4.2 |
| C | AC2 未真正闭合：只有单轮证据，且 `env` 记的是分支点而非真实 HEAD | **已修**：`run-addon-smoke.ps1` 改为真多轮（`-Rounds`，默认 2），每轮清干净 run/mods 并起新进程；新增 `consistency.json` 做逐字段比较；`env.txt` 改记真实 `git head`、worktree 是否 dirty、轮数、jar SHA-256。两 loader 各 2 轮，`consistent=true` |
| D | AC14 半成品：`NekoPluginBootstrap.bootstrap` 零生产调用者却仍 `public` | **已修**：收窄为 **package-private**；跨包的 `NekoProbeBuiltinPluginTest`（2 处）迁移到 `NekoPluginRuntime.bootstrapOwned`。选择收窄而非删除，因为删除会迫使 33 处同包语义测试重造 managed API bootstrap（= 新造测试框架） |
| E1 | `build-addon.ps1 -Skipped` 零调用者、只 print + exit 0 | **已删**；bench README 关于 classpath 未覆盖的表述改为如实描述（含如何手动构造） |
| E2 | `colon==1` 盘符判定与 `isFileSpecifier` 语义重复；`jar:` 守卫无消费 | **随 B 自动消除**：两处都住在被删除的 `isNamespacedRegistryKey` 里；`isFileSpecifier` 的 `^[A-Za-z]:[\\/]` 仍是唯一盘符判定 |
| E3 | `resolverFor` 与 `NekoModuleResolverTest` 逐字重复的反射构造 | **已抽** `NekoModuleResolverTestSupport.resolverFor`，两处共用 |
| E4 | `RecordingPluginRuntime` 是第 12 个 `IPluginRuntime` stub；AddonBoundary 三例与 `NekoPluginBootstrapV2Test` 重叠 | **保留**：`NekoPluginAddonBoundaryTest` 的环/未知依赖/重复 id/freeze 四例断言的是 **addon 定位符**（错误消息里必须出现 `addon08:*` id 或 pointId），这是 V2 测试没有的语义；`RecordingPluginRuntime` 记录的是 reload 前后的产物身份（bindings 投递计数），与其它 stub 职责不同。差异已在类 javadoc 注明 |

### 遗留与未验证（owner）

1. **1.21.1 / 26.2.0 / 26.2.0-fabric 未跑真机烟测**（只做 `compileJava` + 打包隔离校验）。Owner：维护者。
2. **真实 CLIENT 端未验证**（全部真机证据来自 dedicated server 的 STARTUP + SERVER）。Owner：维护者 / minecraft-mcp。
3. **classpath discovery 形态未覆盖**（AC3 允许，已显式记录）。Owner：维护者。
4. **真机采样的工作树是 dirty**（`env-*.txt` 明确记录），因为修订尚未提交。若需「干净 HEAD」证据，可在
   修订提交后重跑 `run-addon-smoke.ps1 -Rounds 2`。Owner：维护者；不阻塞验收（jar SHA-256 与每轮计数已留档）。
5. Fabric loader 的 entrypoint 探测实例是 loader 行为，本票不动（见 REPORT §4.4）。Owner：维护者（如需对插件作者说明，可并入 wiki）。
