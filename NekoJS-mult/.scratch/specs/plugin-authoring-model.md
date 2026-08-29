# Spec: 插件作者体验优先的双形态模型（插件注册面重设计）

Triage: ready-for-agent
取代: `v1-plugin-compat.md`（grilling 后框架变更——从"V1 兼容零破坏"改为"作者体验模型"，V1 shim 层整体取消）

## Problem Statement

（插件作者视角）V2 重设计后，注册一个插件要翻十几个 Point 文件才能知道自己该 implements 哪些 `Contributor` 接口；一个功能横跨多条通道时类声明一长串；NekoJS 以后每加一条新通道，作者还要继续学新接口。（NekoJS 开发者视角）这个成本不能靠把核心改回 V1 的万能 Context 来解决——那会重新引入"加内置通道要波及 bootstrap/runtime"的耦合。

要求：**作者只面对一个接口、覆写即用；NekoJS 加通道不碰核心；两个体验长期共存、互不腐蚀。**

## Solution

双形态模型，一层事实源 + 一层门面：

- **作者面（主形态，即 V1 形态）**：`NekoJSPlugin` 单接口携带全部通道钩子（default 空实现），覆写即用；IDE 覆写补全即发现性，javadoc 按"我想做 X"分组。
- **引擎面（事实源，即 V2 形态）**：每条通道一个自包含 Point 文件（Contributor + MergePolicy + initializer/finisher + dependsOn 全在点内），加通道核心零触碰。
- **共存机制 = 配对协议**：Point 是唯一事实源，基接口钩子是它的门面投影；加通道 = 三件套（Point 文件 + 基接口 default 钩子 + 内置清单一行）同一 PR；架构单测机械化校验配对，防 V1 式膨胀。
- 两种写法**等价收集**：内置点收集面放宽到 `NekoJSPlugin`，作者自选形态，文档把单接口覆写定为推荐。

## User Stories

1. 作为插件作者，我想只 implements `NekoJSPlugin` 并覆写需要的钩子，所以不用翻 Point 文件就能注册事件组/绑定/适配器。
2. 作为插件作者，我想 implements 后在 IDE 里一键看到全部可覆写钩子，所以发现性靠 IDE 而不是读源码。
3. 作为插件作者，我想基接口 javadoc 按"我想做 X"组织，所以按目的找钩子而不是按类名找接口。
4. 作为偏好显式风格的作者，我想继续 implements `Contributor` 接口，所以两种写法等价、风格自选。
5. 作为插件作者，NekoJS 新增通道时我想继续只覆写基接口新钩子，所以学习成本不随版本增长。
6. 作为插件作者，我覆写 `generateData`/`attachServerData` 等回调钩子时不需要任何额外注册，所以回调族零门槛。
7. 作为插件作者，注册冲突（如同名事件组）时我想得到明确报错，所以不静默失效。
8. 作为 NekoJS 维护者，我想加新通道只写一个 Point 文件 + 基接口一行 + 清单一行，所以核心永不耦合。
9. 作为 NekoJS 维护者，我想架构单测拦住"没有 Point 配对的基接口方法"，所以基接口不会 V1 式膨胀。
10. 作为 NekoJS 维护者，我想作者 API 面进 golden 快照，所以对作者的签名承诺由 CI 守护。
11. 作为 NekoJS 维护者，我想 shim 层不存在，所以 API 面没有只为假想兼容服务的死代码。
12. 作为内部插件（`NekoRegistryPointsPlugin` 等），我的 implements Contributor 写法继续有效，所以存量 V2 代码零翻新。
13. 作为整合包作者，插件 API 面签名稳定（golden 守门），所以升级无连锁成本。
14. 作为文档读者，我想两种形态的关系（谁是事实源、谁是门面）有明确说明，所以不被双形态困惑。

## Implementation Decisions

1. **基接口钩子恢复**：`NekoJSPlugin` 恢复 18 个 default 空钩子，签名与参数类型 FQCN 与 V1 逐字一致——`registerEvents`/`registerClientEvents`/`registerBinding`/`registerAdapters`/`registerScriptCompilers`/`registerScriptProperty`/`registerTypeDocs`/`registerNodeTypeDocs`/`registerNodeModules`/`registerProbeBackends`/`registerRecipeNamespaces`/`registerRecipeSchemas`/`registerLifecycleHooks`/`registerRecipeLifecycleHooks`/`generateData`/`generateAssets`/`generateLang`/`modifyWorkspaceConfig`（V2 现已保留的 12 个不动）。javadoc 按"我想做 X"重组（→ wiki 索引同步，决策见 §9）。
2. **内置点收集面放宽**：14 个 bootstrap 收集型内置点的目标类型 `Contributor.class` → `NekoJSPlugin.class`，collector 改引基接口方法（`NekoJSPlugin::registerEvents` 等）；收集宽度回到"所有插件都被收集、空覆写 no-op"。`Contributor` 接口全部保留（显式形态；`BindingsPoint` 的 ScriptType 过滤、`ClientEventsPoint` 的 clientOnly+dependsOn 等特例逻辑不变）。
3. **模块归位**：`NekoJSPlugin` 从 common-api 移回 common 模块——18 个钩子的参数类型（`ScriptCompilerRegistry`/`TypeDocsRegister`/`NodeModuleRegister`/`ProbeBackendRegistry`/`ScriptPropertyRegistry`/`JSConfigModel`/`DataGeneratorJS`/`LangGeneratorJS` 及 `EventGroupRegistry` 等注册载体）全部在 common，反向搬 14 个类型会级联拖出 common 内部依赖；单文件移动 + FQCN 不变是唯一小动作。已核实 common-api 内部无其他引用（仅注解文件提及）；`@RegisterNekoJSPlugin` 留在 common-api 不动。
4. **GenerationPoint 族修正**：`GenerationPoint.POINT` 当前定义了但从未注册，`PluginGenerationHooks` 是运行时直调（`instanceof Contributor` 过滤后逐插件触发）。钩子上基接口后：`PluginGenerationHooks` 去掉 instanceof 过滤改为全员调用（try/catch 隔离已有）；未注册的 `POINT` 定义删除（配对协议不容许死定义）；`modifyWorkspaceConfig` 的调用点同步核实放宽。
5. **配对协议 + 架构单测**：不变式 = "基接口每个收集型钩子恰好配对一个内置 Point 的 collector，且该 Point 的 pluginType 为 `NekoJSPlugin.class`"。新架构单测（形态沿用 `checkCommonIsolation` 一类既有架构测试）：枚举内置点清单断言 pluginType 与 collector 绑定（方法引用经 lambda introspection 或清单比对），并断言基接口收集型钩子集合与点集合双射；回调族钩子（`init` 族、`attachXxxData`、`beforeScriptsLoaded` 等事件时直调的）列入豁免清单。
6. **作者面签名守卫**：`ApiManifestGoldenTest` 经实施期核实只冻结**脚本面**绑定/member（不含 Java 插件 SPI，本次基线零变化）；作者面签名漂移改由 `PluginHookPairingTest` 的 `FROZEN_SIGNATURES` 参数类型冻结表拦截（ADR-0010 §7）。
7. **ADR 记录**：新增 ADR-0010（双形态作者模型）：Point 事实源 / 基接口门面 / 配对协议 / 收集面放宽 / shim 取消理由 / handle 保留理由。
8. **V1 shim 层取消**（grilling FQ1）：`of(...)`/`clientOnly(...)` 工厂、Context 11 个废弃存根、`register` void 回退——全部不做。理由：插件作者存量为零（FQ6）、预发布无兼容包袱、handle 产物访问有独立价值（PR37 作者本人要的"注册后可取结果的对象"）。第三方自定义 EP 一律 V2 builder，文档给一个定义示例。
9. **文档**：基接口 javadoc 按用途分组（事实源）+ wiki 一页"插件开发通道索引"（导航）+ 自定义 EP 定义示例（FQ3 决策 (c)）。

## 改动清单（审阅用）

| # | 文件 | 改动 | 量级 |
|---|---|---|---|
| 1 | `common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java` | 自 common-api 移入；+18 个 default 钩子（签名=V1）；javadoc 按"我想做 X"重组 | ~+150 行 |
| 2 | `common-api/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java` | 删除（移出）；`api/annotation/RegisterNekoJSPlugin.java` 留守 | −83 行 |
| 3 | `common/.../core/plugin/` 下 14 个 Point（EventsPoint、ClientEventsPoint、BindingsPoint、AdaptersPoint、TypeDocsPoint、NodeTypeDocsPoint、NodeModulesPoint、ScriptCompilersPoint、ScriptPropertiesPoint、RecipeNamespacesPoint、RecipeSchemasPoint、RecipeLifecyclePoint、LifecyclePoint、ProbeBackendsPoint） | 每个文件两处：`builder(ID, Contributor.class)` → `builder(ID, NekoJSPlugin.class)`；`Contributor::xxx` → `NekoJSPlugin::xxx` | 每文件 ~2 行 |
| 4 | `common/.../core/plugin/PluginGenerationHooks.java` | 去 `instanceof GenerationPoint.Contributor` 过滤 → 全员调用 | ~−6 行 |
| 5 | `common/.../core/plugin/GenerationPoint.java` | 删除未注册的 `POINT` 定义（Contributor 接口保留供运行时无关引用？→ 一并删除，钩子全在基接口） | −40 行 |
| 6 | `common/.../core/plugin/` 中 `modifyWorkspaceConfig` 调用点（实施时定位） | instanceof 过滤 → 全员调用，同 §4 | ~2 行 |
| 7 | `common/src/test/.../plugin/PluginHookPairingTest.java` | 新增架构单测（配对不变式，§5） | ~80 行 |
| 8 | `src/test/.../ApiManifestGoldenTest` golden 基线 | `-Dnekojs.golden.regenerate=true` 再生成 + diff 评审 | 基线文件 |
| 9 | `docs/adr/0010-plugin-authoring-model.md` | 新增 | 一篇 |
| 10 | wiki 插件开发页 + 通道索引 | 新增/更新 | 一页 |

回归保障：`NekoRegistryPointsPlugin`、`NekoBuiltinPointsPlugin`、`NekoCommonBuiltinPlugin` 等存量 V2 形态代码**零改动**（implements Contributor 在放宽后自动仍被收集），编译 + 既有测试全绿即回归通过。

## 写法变化（审阅用）

**插件作者——注册事件组 + 绑定 + 适配器：**

```java
// ===== before（V2 现状）：翻 3 个 Point 文件才知道这些接口，implements 一长串 =====
@RegisterNekoJSPlugin
public class MyPlugin implements NekoJSPlugin,
        EventsPoint.Contributor, BindingsPoint.Contributor, AdaptersPoint.Contributor {
    @Override public void registerEvents(EventGroupRegistry r) { r.register(myGroup); }
    @Override public void registerBinding(BindingRegistry r) { r.register("MyItem", ...); }
    @Override public void registerAdapters(JSTypeAdapterRegistry r) { r.register(...); }
}

// ===== after：一个接口，覆写即用（IDE 列出全部可覆写钩子）=====
@RegisterNekoJSPlugin
public class MyPlugin implements NekoJSPlugin {
    @Override public void registerEvents(EventGroupRegistry r) { r.register(myGroup); }
    @Override public void registerBinding(BindingRegistry r) { r.register("MyItem", ...); }
    @Override public void registerAdapters(JSTypeAdapterRegistry r) { r.register(...); }
}
```

偏好显式的作者仍可写 `implements EventsPoint.Contributor`——两种写法被同一个点收集，效果完全等价。

**NekoJS 开发者——新增一条通道（如 `SoundEvents` 组）：**

```java
// ===== before（V1）：4 处核心联动 =====
// Context 接口加访问器 + BootstrapState 实现 + bootstrap 内联点表 + 消费逻辑硬编码

// ===== after（配对协议三件套，核心零改动）=====
// ① 新文件 SoundEventsPoint.java —— 自包含：Contributor + merge 策略 + initializer/finisher + 产物访问
// ② NekoJSPlugin 加一个门面钩子（3 行）：
//      /** 我想注册 SoundEvents 事件组 → 覆写本方法。 */
//      default void registerSoundEvents(SoundGroupRegistry registry) {}
// ③ NekoBuiltinPointsPlugin 清单一行：registry.register(SoundEventsPoint.POINT);
// PluginHookPairingTest 自动校验 ①↔② 配对，漏一件即 CI 红。
```

## Testing Decisions

好测试只断言外部行为（钩子被收集执行、两种形态等价、配对不变式成立），不断言引擎内部状态。

- **缝 1（主缝，既有）：bootstrap 收集行为**。测试一个"V1 形态"插件（只 implements `NekoJSPlugin`、覆写钩子、不实现任何 Contributor），跑一轮 bootstrap，断言每个内置点收集到其贡献（事件组出现在产物、绑定进快照、适配器生效）；再放一个 V2 形态（implements Contributor）插件作回归对照。先例：`NekoPluginBootstrapV2Test`、`NekoPluginBootstrapHookDeliveryTest`、`NekoPluginExtensionPointTest`。
- **缝 2（新缝，沿用架构测试先例）：配对不变式**。`PluginHookPairingTest`：14 个内置点 pluginType 全为 `NekoJSPlugin.class`；基接口收集型钩子集合与点集合双射（豁免清单覆盖回调族）。先例：`checkCommonIsolation` 一类架构测试。
- **缝 3（既有门禁）：golden manifest**（脚本面绑定/member）常态绿、本次基线零变化；作者面签名冻结在缝 2 的配对测试内（`FROZEN_SIGNATURES`，实施期修正——golden 不覆盖插件 SPI）。
- 不做：重编译矩阵（无存量作者）、旧 jar 冒烟（同因）。

## 实施记录（2026-08-29）

| 票 | 内容 | commit |
|---|---|---|
| 01 | NekoJSPlugin 移回 common + 恢复 18 钩子 + javadoc 重组 | ffdd8ab |
| 02 | 14 内置点收集面放宽（builder/collector/generics） | 9ed6acf |
| 03 | GenerationPoint 族修正（直调、删死 POINT、测试更新） | ec989a9 |
| 04 | PluginHookPairingTest 配对不变式 | （05 提交内） |
| 05 | 作者面签名冻结表 + golden 核实 + 四节点强制回归 + guardLint | 1a15679 |
| 06 | ADR-0010 + wiki 双形态说明/索引补行/自定义 EP 示例 | 本提交 |

## Out of Scope

- **V1 shim 层**（`of`/`clientOnly`/Context 存根/`register` void 回退）——grilling FQ1 取消；自定义 EP 走 V2 builder + 文档示例。
- 注解驱动注册（`@OnEvents` 类）——已否决：丢编译期契约、签名漂移静默失效。
- 脚本侧（JS）API——R3 裁定不承诺稳定。
- 回调族钩子 Point 化（`attachXxxData`、`init` 族回调本体等事件时直调的保持现状）。
- 存量插件普查——无作者（FQ6）。
- 实机测试——既有裁定，编译 + 单测 + 代码审查验收。

## Further Notes

**grilling 决策轨迹**：Q1 改进轴地图确认（①③⑤ 零回退、④ 有界让步）→ Q2 永久冻结被用户否决（功能会增多）→ 改为配对协议（FQ2）→ Q3 保留 handle（预发布 + 有独立价值）→ Q4 存根随 shim 层取消 → Q5 "v1 优先 + 共存"落为双形态模型 → Q6 零作者，普查与 shim 均不需要。

**量级总计**：主改动 ~200 行 + 架构测试 ~80 行 + golden 基线 + ADR/wiki 各一篇；引擎核心（bootstrap 收集循环、MergePolicy、handle、拓扑 freeze）零改动；存量 V2 插件零改动。

**已知权衡**：① 双形态的 javadoc/文档一致性靠评审维护（配对测试管代码不管注释）；② 参与关系从 implements 退到"是否覆写"（IDE goTo-super 兜底）；③ 基接口收集型钩子签名从此受 golden 约束，改动即 CI 红——这是承诺的代价，也是承诺的目的。
