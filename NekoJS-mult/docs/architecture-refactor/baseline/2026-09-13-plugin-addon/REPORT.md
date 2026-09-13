# Ticket 08 — 真实外部 PluginAddon 端到端：报告与证据

分支 `ticket-08-plugin-addon`（基于 master `4e258d3a`）。执行者：dsh-agent。

范围：只补真实消费链与边界修正。**未新增任何 ExtensionPoint、Collector/initializer/merger/
finisher 框架、Point registry、Gradle 项目或 API jar**；未改任何既有公开契约签名。

## 1. 交付物

### 1.1 生产源码改动（3 处，各自对应一条 AC）

| 文件 | 改动 | 对应 AC |
|---|---|---|
| `common/.../core/module/NekoModuleResolver.java` | `resolveBareModule` 捕获 `InvalidPathException`，把含冒号的 bare specifier 交给既有 SPECIAL 档 | AC2/AC8/AC9 —— 真实 addon 的 node 模块产物必须能被脚本消费 |
| `common/.../core/plugin/NekoPluginBootstrap.java` | `bootstrap(List, ScriptPropertyRegistry)` 从 `public` 收窄为 package-private | AC14 —— 无调用者入口 |
| `common/.../core/plugin/NekoPluginRuntime.java` | 删除 `@Deprecated bootstrap(List<NekoJSPlugin>, ScriptPropertyRegistry)` | AC14 —— 旧 legacy bootstrap |

三处都**不是契约变更**：`registerNodeModules` 的 id 语义本来就在 `NodeModulesPoint` 与
`wiki/插件开发.md` 里写成 `moduleId`（注册表键）；被删的 `bootstrap` 是 `@Deprecated` 且零调用者；
被收窄的 `NekoPluginBootstrap.bootstrap` 也已无生产调用者（只服务同包语义测试）。

### 1.2 测试

| 文件 | 用例数 | 覆盖 |
|---|---|---|
| `common/src/test/.../core/plugin/NekoPluginAddonBoundaryTest.java` | 8 | AC9（Handle 未 finish 拒读/finish 后可读、环、未知依赖、重复 id、freeze 后注册）、AC4/AC5（不得读他点中间态）、AC12（Binding 共享对象不被 generation 清理误动）、AC15（注册图 freeze / 累积器 seal / 结果发布三层边界） |
| `common/src/test/.../core/plugin/PluginRuntimeReloadLifetimeTest.java` | 1 | AC10（reload 不重新 bootstrap/freeze，进程级产物身份保持）、AC11（旧 generation 的 session object / 事件 token / timer 不能静默操作新 session） |
| `common/src/test/.../core/module/NekoPluginNodeModuleResolutionTest.java` | 7 | 命名空间 node module id 的解析回归 + **未注册 id 仍按 MODULE_NOT_FOUND 失败** + 单字母盘符仍走文件语义 |
| `common/src/test/.../core/module/NekoModuleResolverTestSupport.java` | — | 两个 resolver 测试类共享的反射构造（去重） |

### 1.3 bench 资产（不入生产 jar）

- `bench/plugin-addon/fixture/` —— test-only 外部 addon 源（`com.example.nekojs08`），
  只 import `com.tkisor.nekojs.api.*` + `core.module.NodeModuleRegister` + slf4j，**不 import 任何
  内部测试 seam**；另含两个 AC6 控制类。
- `bench/plugin-addon/build-addon.ps1` —— `javac` + `jar` 打两个 loader 形态的独立制品；不新增
  Gradle 项目，因此物理上不可能被打进生产 jar。
- `bench/plugin-addon/fixtures/nekojs/` —— 消费脚本（STARTUP 读 binding 与过滤断言；SERVER 读
  binding + node module）。
- `bench/plugin-addon/run-addon-smoke.ps1` —— 干净 run/mods → 多轮启动 → `nekojs reload server`
  → 计数 → **轮间逐字段一致性比较** → 判定。`-Rounds` 默认 2。
- `bench/plugin-addon/verify-jars.py` —— 五个生产 jar 的打包隔离校验。
- `bench/plugin-addon/MINIMAL-ADDON.md` —— AC7 的最小示例与迁移材料。
- `bench/plugin-addon/DEAD-CODE-AUDIT.md` —— AC14 逐条取证。
- `bench/plugin-addon/README.md` —— 复现步骤、判定口径、覆盖形态诚实记录。

## 2. 真实 discovery 证据（AC2 / AC3 / AC6 / AC13）

### 2.1 形态：fat jar（classpath 未覆盖）

addon 是**独立 jar**，放进 `run/mods`（NeoForge）/ `run-server/mods`（Fabric），由 loader 从 mods
目录发现 —— 即真实目标发布形态。`build-addon.ps1` **没有** classpath 形态的开关（早期版本的
`-Skipped` 空开关已删除），所以本票只覆盖 fat jar 一种形态，classpath 形态记为**未覆盖**。

### 2.2 NeoForge 26.1.2 —— 真实 annotation/mod metadata discovery

`META-INF/neoforge.mods.toml` 声明 `modId="nekojs08addon"` 与 `nekojs` 依赖；类标
`@RegisterNekoJSPlugin(priority = 777)`。发现链：

```
NekoJSMod.initializeScripts
  -> NeoForgePluginLoader.loadAnnotatedPlugins()
     -> ReflectionUtils.findAnnotationClasses(RegisterNekoJSPlugin.class, null, ...)
        -> ModList.get().getAllScanData()      // FML 真实注解/mod metadata 扫描
           -> NekoJSBasePluginManager.registerClass(Class)
```

### 2.3 Fabric 26.1.2-fabric —— 真实 `fabric.mod.json` entrypoint discovery

`fabric.mod.json` 声明自定义 entrypoint 键 `"nekojs": [ ... ]`（含 addon 与两个控制类）。发现链：

```
NekoJSFabricMod.initializeScripts
  -> FabricPluginLoader.loadPlugins()
     -> FabricLoader.getInstance().getEntrypointContainers("nekojs", NekoJSPlugin.class)
        -> NekoJSBasePluginManager.registerClass(container.getEntrypoint().getClass())
```

### 2.4 计数解读（`counts-*-round1.json` / `round2.json`）

| 计数 | NeoForge | Fabric | 含义 |
|---|---|---|---|
| `discovered` | 1 | 2 | loader 侧 addon 类的构造次数 |
| `instanceMarker` | 1 | 1 | NekoJS **真正发现**的实例只有 1 个（`instance=1`） |
| `bindingReg` | 3 | 3 | `registerBinding` 在 bootstrap 中的调用次数（进程内一次） |
| `initStartup` | 1 | 1 | 生命周期钩子只触发一次 |
| `preServerConsumed` → `postServerConsumed` | 1 → 2 | 1 → 2 | reload 前后脚本仍能消费冻结 binding |
| `preModuleConsumed` → `postModuleConsumed` | 1 → 2 | 1 → 2 | 同上，node module 产物 |
| `requiredModsLeak` / `clientOnlyLeak` | 0 / 0 | 0 / 0 | AC6 控制类未泄漏 |

Loader 差异（实测，不掩盖）：NeoForge 的 FML 注解扫描只实例化 addon 类一次；Fabric loader 在
pre-launch 会探测每个声明的 entrypoint，`AddonPlugin` 构造器因此被调用两次（一次是 loader 的
一次性探测、NekoJS 从未登记，一次是 NekoJS 真正发现）。`addon_instances_are_deduped` 把
「NekoJS 只发现 1 个」与「总构造次数与 loader 形态吻合」**两个事实同时**钉住，两边漂移都会红。

### 2.5 干净 run/mods 目录 + 重复执行一致（AC2）

`run-addon-smoke.ps1 -Rounds 2` 每轮：清 `run/nekojs`、清 `run/logs/nekojs`、删掉上次装入的
`nekojs08addon-*.jar`，再重新铺 fixture 与 addon jar，然后起一个**新的**服务器进程。
每轮产出 `round-N-checks.json` / `round-N-counts.json` / `round-N-rcon-*.txt` / `round-N-server.log`。
全部轮次跑完后逐字段比较，写 `consistency.json`。

本轮实测：两 loader 各 2 轮，`all_rounds_passed: true`、`rounds_are_consistent: true`、
`per_round_failed_checks` 为空。`env-*.txt` 记录采样时的真实 `git head`、`git worktree` 是否 dirty、
轮数、run dir、addon jar 的 SHA-256。

### 2.6 AC6 控制组：requiredMods / clientOnly 过滤

同一个外部 addon jar 里另有**两个兄弟类**，由 loader 经**完全相同的真实 discovery 路径**扫到：

| 类 | 注解 | 期望 |
|---|---|---|
| `AddonFilteredPlugin` | `requiredMods = {"nekojs08-absent-mod"}` | 在服务器上被过滤掉 |
| `AddonClientOnlyPlugin` | `clientOnly = true` | 在 dedicated server 上被过滤掉 |

两者的 `registerBinding` 都会打一条 `...-LEAKED` 的 ERROR 日志。STARTUP 脚本另外断言
`typeof AddonFilteredLeak === 'undefined'` 与 `typeof AddonClientOnlyLeak === 'undefined'`。

判定：`addon_required_mods_filter_holds` / `addon_client_only_filter_holds` 要求「脚本侧确认绑定
缺席」**且**「LEAKED 日志为 0」。若过滤回归，两个类会注册成功并打日志，两项立刻变红。
NeoForge 与 Fabric 两侧都成立（Fabric 侧是把三个类都声明为 `nekojs` entrypoint 实现的——loader
会让 `FabricPluginLoader` 拿到全部三个，过滤必须发生在 `NekoJSBasePluginManager` 里）。

## 3. 打包隔离（AC1）

`python bench/plugin-addon/verify-jars.py` 对五个生产 jar 断言：

1. 入口表不含 `com/example/nekojs08/`、`Addon*`、`nekojs08*`；
2. 平台自己的 metadata（`META-INF/neoforge.mods.toml` / `fabric.mod.json`）文本不含
   `nekojs08addon` / `com.example.nekojs08` / `NekoJS Ticket 08 Test Addon`。

结果（`evidence/jar-isolation.txt` / `.json`）：1.21.1(1395 项) / 26.1.2(1448) / 26.2.0(1448) /
26.1.2-fabric(7424) / 26.2.0-fabric(7424) —— **五个全 OK**。

结构性保证：fixture 不由 Gradle 构建，只经 `javac`+`jar` 写入 `bench/plugin-addon/build/`，
不进入任何 source set 或 jar 输入；`common/src/test` 也不参与生产 jar。

## 4. 行为保持与取舍边界

### 4.1 行为保持

- `:common:check`（200+ 套件）与 `guardLint` 全绿，**无 golden 更新**（见 §10 的两份 `.txt` 证据）。
- 被删的 `NekoPluginRuntime.bootstrap` 是 `@Deprecated`、零生产调用者、零测试调用者，删除不改变
  任何现存调用路径。
- `NekoPluginBootstrap.bootstrap` 收窄为 package-private：仅同包测试可见，跨包的
  `NekoProbeBuiltinPluginTest` 改走生产入口 `NekoPluginRuntime.bootstrapOwned`（2 参重载）。
- `NekoModuleResolver` 的改动只影响 `Path.resolve` 会抛 `InvalidPathException` 的输入；
  `NekoModuleResolverTest` 的 7 例既有断言（含 `resolveForRequire` 的未知名包语义）全部不变并通过。

### 4.2 取舍边界：为什么把修复放在 `resolveBareModule` 而不是 `isBuiltinSpecifier`

这是本票 code-review 的**语义修正**，值得单独说明。首版把「含冒号的 bare specifier」归为
`BUILTIN`。复核发现这会截走本该报错的输入：

- 修改前 `some:pkg`：`specialKind` 返回 null → `!isFileSpecifier` → `resolveBareModule` →
  node_modules 未命中 → `SPECIAL`；`resolveForRequire`(:59-65) 只拦 `kind()==SPECIAL` → 抛
  `Cannot resolve module`。这是既有契约，`NekoModuleResolverTest:69` 与 `:86` 都在钉它。
- 归为 `BUILTIN` 后：`resolveForRequire` 的 SPECIAL 检查**放行**，一路走到 `specialResolver`，
  注册表没有 → `resolveJavaModule` 返回 `NO_MODULE` → **不再抛 MODULE_NOT_FOUND**。

真正炸的地方也证明了修复位置：`Path.resolve` 对相对实参惰性解析，`Path.of` 不抛，
是在后续 `toAbsolutePath()` 时才抛 `InvalidPathException: Illegal char <:> at index N`。
因此正确落点是 `resolveBareModule` 里捕获该异常并**复用既有的 SPECIAL 档**——
SPECIAL 本来就表示「不算文件，交给 `define.js` 的 `__nekoNodeResolve` 去注册表里查」，
未命中时 `resolveForRequire` 仍然抛 `Cannot resolve module`。既有契约与真实功能同时满足。

### 4.3 AC10 的白名单口径

AC10 断言的是「普通 reload 不重新 discovery/bootstrap/freeze Plugin Runtime」。本票用
**进程级产物身份保持**来表达：`PluginRuntimeReloadLifetimeTest` 断言 reload 前后
`IPluginRuntime.bindings()` 返回**同一个 Map 实例**、其中的 `Binding.value()` 是**同一个共享 Java 对象**，
且 `ScriptManager.generationId()` 递增（说明确实发生了 reload）。真机侧用 `bindingReg` /
`initStartup` 计数在 reload 前后不变来钉同一件事。

这里的白名单是**刻意的**：reload 会替换 generation 级的脚本环境（Context、listener、timer），
以及 `ScriptEventBridge.clearListeners` 的清扫；这些都是 generation-owned，不是 Plugin Runtime
的产物。把 Plugin Runtime 的产物排除在白名单之外（即要求它们也变化）会与 AC11 直接冲突。

### 4.4 未把 host 移出：为什么本票不动 loader 的探测实例

Fabric loader 对 entrypoint 的 pre-launch 探测实例是 **loader 行为**，发生在 NekoJS 拿到控制权之前。
NekoJS 侧的 `NekoJSBasePluginManager.registerClass` 已按 `(identity, class)` 去重（既有行为，
本票未改）。要让「探测实例」也不构造，只能去改 Fabric loader 或放弃 entrypoint 机制引入自有
发现通道——后者等于新建第二套 discovery 框架，与本票「不新建 Point 或插件框架」的硬约束冲突。
因此本票的处理是：**如实记录并断言两个数字**（`env-*.txt` 的 `expected nkoj-framework discoveries`），
不做掩盖，也不越权换发现机制。

## 5. 门禁结果

| 门禁 | 结果 | 证据 |
|---|---|---|
| `:common:check` | BUILD SUCCESSFUL | `evidence/gradle-common-check-guardlint.txt` |
| `guardLint` | 248 守卫 / 331 文件 / 0 问题 0 警告 | 同上 |
| `:common:test` **全量**（`--rerun-tasks --no-build-cache`，真跑） | BUILD SUCCESSFUL | `evidence/gradle-common-full-test-rerun.txt` |
| `:common:test`（plugin + module 子集） | BUILD SUCCESSFUL | `evidence/gradle-common-plugin-tests.txt` |
| 五节点 `compileJava` | 全部成功（只有两条既有 deprecation/this-escape 提示，非新增） | 本轮构建输出 |
| `run-addon-smoke.ps1 -Node 26.1.2 -Rounds 2` | PASSED（2 轮，一致） | `evidence/checks-neoforge.json`、`consistency-neoforge.json` |
| `run-addon-smoke.ps1 -Node 26.1.2-fabric -Rounds 2` | PASSED（2 轮，一致） | `evidence/checks-fabric.json`、`consistency-fabric.json` |
| `verify-jars.py` | PASS（5/5） | `evidence/jar-isolation.txt` / `.json` |

## 6. 逐 AC 判定

| AC | 判定 | 证据 |
|---|---|---|
| AC1 独立 test-only 制品 + 五 jar 不含其类/资源/metadata | **pass** | §3；`evidence/jar-isolation.txt` |
| AC2 真实 loader discovery（两 loader）+ 干净 run/mods 重复执行一致 | **pass** | §2.2/§2.3/§2.5；`evidence/checks-*.json`、`consistency-*.json`、`counts-*-round1.json`、`counts-*-round2.json` |
| AC3 fat jar 与 classpath 至少覆盖一种，未覆盖显式记录 | **pass** | fat jar 已覆盖（§2.1）；classpath **未覆盖**并在 `bench/plugin-addon/README.md` 与 §2.1 显式记录 |
| AC4 现有 Point 生命周期是唯一生命周期，不新建第二套 initializer/merger/finisher | **pass** | 生产源码只动 3 文件（§1.1），零新增 Point/框架类；`NekoPluginAddonBoundaryTest` 用既有 builder/Handle/Sealable 表达 |
| AC5 一个点不得经回调初始化/改写另一点的中间状态，finish 只冻结自身结果 | **pass** | `NekoPluginAddonBoundaryTest#onePointCannotObserveAnotherPointsIntermediateAccumulator` + `#stolenAccumulatorWritesAreRejectedAfterFinishWithoutChangingPublishedResult` |
| AC6 owner identity / priority / requiredMods / clientOnly 与旧契约一致 | **pass** | §2.6：两个兄弟控制类经同一真实 discovery 路径被扫到但被正确过滤；两 loader 的 `addon_required_mods_filter_holds` / `addon_client_only_filter_holds` 均 true。owner identity 走 `PluginIdentity` 未改 |
| AC7 最小示例 + 迁移材料可按公开说明复现 | **pass** | `bench/plugin-addon/MINIMAL-ADDON.md`（只用 `api.*` + `NodeModuleRegister`，无内部 seam） |
| AC8 按 Point 事实源收集，投影等价，脚本侧可观察同一冻结产物 | **pass** | `NekoPluginAddonBoundaryTest#handleRejectsReadBeforeFinishAndServesProductAfter`（同一产物经 handle 与 runtime 两个观察面）；真实 addon 用基接口钩子形态消费同一冻结产物 |
| AC9 Handle finish 前拒读 / finish 后可读；依赖、环、重复 id、freeze 后注册带 addon 定位失败 | **pass** | `NekoPluginAddonBoundaryTest` 5 例（每个错误消息都断言包含 addon 定位符） |
| AC10 普通 reload 不重新 discovery/bootstrap/freeze，Point result 与 Handle 身份仍有效 | **pass** | 单测 `PluginRuntimeReloadLifetimeTest`；真机 `addon_bootstrap_once` / `addon_init_once` / `binding_alive_after_reload` / `module_alive_after_reload` 四项。口径见 §4.3 |
| AC11 reload 后旧 generation 的 session object / 事件 token / 临时计划不能静默操作新 session | **pass** | `PluginRuntimeReloadLifetimeTest`：旧 Context `ScriptManager.from` 抛 `No ScriptManager registered`、`ScriptContextRegistry.scriptTypeOf(old)==null`、旧 listener 计数不变、旧 timer 不再分发 |
| AC12 session 清理不关闭进程级 Plugin Runtime 持有的共享 Java 对象 | **pass** | `NekoPluginAddonBoundaryTest#bindingCloseDoesNotMutateSharedRuntimeOwnedJavaObject`（身份 + 未 tear down + 冻结产物同一实例） |
| AC13 外部 addon 在两 loader 的 loader 启动/烟测路径中被发现并执行 | **pass** | §2；`evidence/checks-neoforge.json`、`evidence/checks-fabric.json` |
| AC14 无调用者 facade / 旧 legacy bootstrap / loader 私有 Point registry 旁路 | **pass** | `bench/plugin-addon/DEAD-CODE-AUDIT.md`：legacy bootstrap 已**删除**；`NekoPluginBootstrap.bootstrap` 已**收窄为 package-private**；私有 Point registry 旁路**全仓不存在**；`NekoJSBasePluginManager` 有 9 处生产调用者，保留并写明原因 |
| AC15 冻结 Point 产物：保留旧累积器引用再写被拒或不影响已发布结果 | **pass** | `NekoPluginAddonBoundaryTest#stolenAccumulatorWritesAreRejectedAfterFinishWithoutChangingPublishedResult`（seal 拒绝 + 已发布快照不变 + runtime 与 handle 同一实例） |

说明：票据正文的 AC 列表共 15 条，上表按行序编号。

## 7. 未完成 / 未验证项（据实）

1. **classpath discovery 形态未覆盖**（AC3 允许）。`build-addon.ps1` 现在**没有**专用开关；
   需要时把 addon 的 classes 目录塞进 dev run 的额外 classpath 再跑一次。Owner：维护者。
2. **1.21.1 / 26.2.0 / 26.2.0-fabric 节点未跑真机烟测**。只做了 `compileJava` 与产物构建 +
   打包隔离校验。26.2.0 / 26.2.0-fabric 复用 26.1.2 的 Fabric bridge（`deps.fabric_source_node`），
   但没有实际启动。Owner：维护者。
3. **未做真实客户端（CLIENT 侧）烟测**。所有真机证据都是 dedicated server；脚本面走的是
   STARTUP + SERVER 两种 ScriptType。Owner：维护者 / minecraft-mcp。
4. **客户端 GUI / 渲染路径未验证**（本票不涉及）。

## 8. 遗留 / 维护者裁决点

1. Fabric loader 对 entrypoint 的探测实例是 loader 行为、不是 NekoJS 缺陷（§4.4）。NekoJS 只在
   `registerClass` 内按 `(identity, class)` 去重。若维护者认为值得对插件作者显式说明，可并入 wiki。
2. 真机采样时工作树是 dirty（`env-*.txt` 明确记录），因为 code-review 修订尚未提交。修订提交后
   若要一份「干净 HEAD」证据，可再跑一次 `run-addon-smoke.ps1 -Rounds 2`（脚本会记录新的 head 与
   worktree 状态）。这不影响结论：`env-*.txt` 记录了 addon jar 的 SHA-256，且 `counts-*` /
   `consistency-*` 与提交后的源码一一对应。

## 9. 已修复的既有缺陷（真实 addon 暴露，非本票新造）

**症状**：脚本 `require('nekojs08:hello')` 抛 `Illegal char <:> at index 8`。

**根因**：`NekoModuleResolver.resolve` → `resolveBareModule` 里 `nodeModules.resolve(specifier)`
对相对实参惰性解析，`nekojs08:hello` 在后续 `toAbsolutePath()` 时才抛 `InvalidPathException`。
该模块其实**已经注册成功**（`__nekoNodeBuiltins['nekojs08:hello']` 是 object），只是 require 的
解析路径根本到不了注册表。任何按文档命名自己 node 模块的插件都会撞上。

**修复**：`resolveBareModule` 捕获 `InvalidPathException` 并返回既有的 SPECIAL 档（详见 §4.2 的
取舍论证）。不改 `NodeModulesPoint` 的 id 契约、不改 `NekoModuleKind` 枚举、不改任何公开签名。

## 10. 证据清单

全部位于 `docs/architecture-refactor/baseline/2026-09-13-plugin-addon/`：

**真机烟测（两 loader × 2 轮，干净 run/mods）**
- `evidence/checks-neoforge.json`、`evidence/checks-fabric.json` —— 总判定（轮数 / 全轮通过 / 轮间一致）
- `evidence/consistency-neoforge.json`、`evidence/consistency-fabric.json` —— 逐轮逐字段一致性比较
- `evidence/counts-neoforge-round1.json`、`counts-neoforge-round2.json` —— 两轮计数
- `evidence/counts-fabric-round1.json`、`counts-fabric-round2.json` —— 两轮计数
- `evidence/env-neoforge.txt`、`evidence/env-fabric.txt` —— 采样 HEAD / worktree 状态 / 轮数 / jar sha256

**打包隔离（AC1）**
- `evidence/jar-isolation.txt`、`evidence/jar-isolation.json` —— 五个生产 jar 的类/资源/metadata 校验

**门禁**
- `evidence/gradle-common-check-guardlint.txt`（`:common:check` + `guardLint`）
- `evidence/gradle-common-full-test-rerun.txt`（`:common:test --rerun-tasks --no-build-cache` 全量真跑）
- `evidence/gradle-common-plugin-tests.txt`（plugin + module 子集）

**报告与复现资产**
- 本文件 `REPORT.md`；`bench/plugin-addon/`（fixture、build-addon.ps1、run-addon-smoke.ps1、
  verify-jars.py、MINIMAL-ADDON.md、DEAD-CODE-AUDIT.md、README.md）
