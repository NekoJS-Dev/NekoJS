## 6. 逐 AC 判定

| AC | 判定 | 证据 |
|---|---|---|
| AC1 独立 test-only 制品 + 五 jar 不含其类/资源/metadata | **pass** | §3；`evidence/jar-isolation.txt` |
| AC2 至少一个 NeoForge 节点真实 annotation/mod metadata discovery + 至少一个 Fabric 节点真实 `fabric.mod.json` entrypoint discovery；干净 run/mods 重复一致 | **pass** | §2.2/§2.3/§2.4/§2.5；`evidence/checks-*.json`、`counts-*.json` |
| AC3 fat jar 与 classpath 至少覆盖一种，未覆盖显式记录 | **pass** | fat jar 已覆盖（§2.1）；classpath **未覆盖**并在 `bench/plugin-addon/README.md` 与本文 §2.1 显式记录 |
| AC4 现有 Point 生命周期是唯一生命周期，不新建第二套 initializer/merger/finisher | **pass** | 生产源码只动 2 文件（§1.1），零新增 Point/框架类；`NekoPluginAddonBoundaryTest` 用既有 builder/Handle/Sealable 表达 |
| AC5 一个点不得经回调初始化/改写另一点的中间状态，finish 只冻结自身结果 | **pass** | `NekoPluginAddonBoundaryTest#onePointCannotObserveAnotherPointsIntermediateAccumulator` + `#stolenAccumulatorWritesAreRejectedAfterFinishWithoutChangingPublishedResult` |
| AC6 owner identity / priority / requiredMods / clientOnly 与旧契约一致，内部实例化不能冒充 | **pass** | §2.6：fixture 内两个兄弟控制类经**同一真实 discovery 路径**被 loader 扫到，但被 `NekoJSBasePluginManager` 按 `requiredMods` / `clientOnly` 正确过滤；两 loader 的 `addon_required_mods_filter_holds` / `addon_client_only_filter_holds` 均 true。owner identity 走 `PluginIdentity` 未改。 |
| AC7 最小示例 + 迁移材料可按公开说明复现 | **pass** | `bench/plugin-addon/MINIMAL-ADDON.md`（只用 `api.*` + `NodeModuleRegister`，无内部 seam） |
| AC8 按 Point 事实源收集，Hook/Contributor 投影等价，脚本侧可观察同一冻结产物 | **pass** | `NekoPluginAddonBoundaryTest#handleRejectsReadBeforeFinishAndServesProductAfter`（同一产物经 handle 与 runtime 两个观察面）；真实 addon 用基接口钩子形态消费同一冻结产物 |
| AC9 Handle finish 前拒读 / finish 后可读；依赖、环、重复 id、freeze 后注册带 addon 定位失败 | **pass** | `NekoPluginAddonBoundaryTest` 5 例（每个错误消息都断言包含 addon 定位符） |
| AC10 普通 reload 不重新 discovery/bootstrap/freeze，Point result 与 Handle 身份仍有效 | **pass** | 单测 `PluginRuntimeReloadLifetimeTest`；真机 `addon_bootstrap_once` / `addon_init_once` / `binding_alive_after_reload` / `module_alive_after_reload` 四项 |
| AC11 reload 后旧 generation 的 session object / 事件 token / 临时计划不能静默操作新 session，错误明确指向失效 generation | **pass** | `PluginRuntimeReloadLifetimeTest`：旧 Context `ScriptManager.from` 抛 `No ScriptManager registered`、`ScriptContextRegistry.scriptTypeOf(old)==null`、旧 listener 计数不变、旧 timer 不再分发 |
| AC12 session 清理不关闭进程级 Plugin Runtime 持有的共享 Java 对象；`Binding.value` 不被当 generation 快照 | **pass** | `NekoPluginAddonBoundaryTest#bindingCloseDoesNotMutateSharedRuntimeOwnedJavaObject`（身份 + 未 tear down + 冻结产物同一实例） |
| AC13 外部 addon 在至少一个 NeoForge 节点与 Fabric 当前节点的 loader 启动/烟测路径中被发现并执行 | **pass** | §2；`evidence/checks-neoforge.json`、`evidence/checks-fabric.json` |
| AC14 无调用者 facade/旧 legacy bootstrap/loader 私有 Point registry 旁路，仅在外部 addon 与 reload fixture 通过后删除 | **pass（部分目标为「不存在」）** | `bench/plugin-addon/DEAD-CODE-AUDIT.md`：legacy bootstrap 已删；私有 Point registry 旁路**全仓不存在**；`NekoJSBasePluginManager` 有 9 处生产调用者，保留并写明原因。删除发生在两次 loader 烟测通过之后 |
| AC15 冻结 Point 产物：保留旧累积器引用再写被拒或不影响已发布结果；三个边界不同 | **pass** | `NekoPluginAddonBoundaryTest#stolenAccumulatorWritesAreRejectedAfterFinishWithoutChangingPublishedResult`（seal 拒绝 + 已发布快照不变 + runtime 与 handle 同一实例） |

说明：票据正文的 AC 列表共 15 条（`AC1`..`AC15` 按行序），上表按该顺序编号。

## 7. 未完成 / 未验证项（据实）

1. **classpath discovery 形态未覆盖**（AC3 允许）。Owner：维护者；需要时用
   `build-addon.ps1 -Skipped` 的反面，把 addon 塞进 dev classpath 再跑一次。
2. **1.21.1 / 26.2.0 / 26.2.0-fabric 节点未跑真机烟测**。只做了 `compileJava` 与产物构建 +
   打包隔离校验。26.2.0 / 26.2.0-fabric 复用 26.1.2 的 Fabric bridge（`deps.fabric_source_node`），
   但没有实际启动。Owner：维护者。
3. **未做真实客户端（CLIENT 侧）烟测**。所有真机证据都是 dedicated server；脚本面走的是
   STARTUP + SERVER 两种 ScriptType。Owner：维护者 / minecraft-mcp。
4. **客户端 GUI / 渲染路径未验证**（本票不涉及）。

## 8. 行为保持声明

- 既有 50 个 plugin 包用例 + 全量 `:common:check`（200+ 套件）全绿，**无 golden 更新**。
- 被删的 `NekoPluginRuntime.bootstrap` 是 `@Deprecated`、零生产调用者、零测试调用者（§1.1 /
  `DEAD-CODE-AUDIT.md`），删除不改变任何现存调用路径。
- `NekoModuleResolver` 的改动只影响「bare specifier 含冒号」这一分支；`NekoModuleResolverTest`
  的 7 例既有断言（含 `resolveForRequire` 的未知名包语义）全部不变并通过。

## 9. 遗留 / 维护者裁决点

1. `NekoPluginBootstrap.bootstrap(List, ScriptPropertyRegistry)` 删除 legacy 入口后已无生产调用者，
   仅作为测试 harness seam 存在（34 个调用点）。若维护者要求「唯一生产入口」字面成立，应把它
   收窄为 package-private，而不是删除（删除会迫使全部语义测试重造 managed API bootstrap，
   违反本票「不新建测试框架」的约束）。见 `DEAD-CODE-AUDIT.md` §4。
2. Fabric loader 对 entrypoint 的双实例化是 loader 行为、不是 NekoJS 缺陷；但 NekoJS 目前
   只在 `registerClass` 内按 `(identity, class)` 去重，无法影响 loader 自己的探测实例。若维护者
   认为这值得在文档里对插件作者显式说明，可并入 wiki。

## 10. 证据清单

- `evidence/checks-neoforge.json`、`counts-neoforge.json`、`env-neoforge.txt`
- `evidence/checks-fabric.json`、`counts-fabric.json`、`env-fabric.txt`
- `evidence/jar-isolation.txt`、`jar-isolation.json`
- `evidence/gradle-common-plugin-tests.log`
- `evidence/gradle-common-check-guardlint.log`
- 复现脚本与 fixture：`bench/plugin-addon/`