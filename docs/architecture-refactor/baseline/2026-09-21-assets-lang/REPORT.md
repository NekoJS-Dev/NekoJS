# 票 29 证据报告：Assets/Lang 资源生成与回读收口

日期：2026-09-21
Worktree：`D:/mcmodDemo/NekoJS-mult-t29`（分支 `ticket-29-assets-lang`，基线 `f9c725f0`，
另 cherry-pick 主干的 `4a9ed9c8` → 本分支 `353838c8`）
执行者：zed-flash-29（deepseek-v4.1-flash subagent worktree）

## 1. 范围

把 Assets 与 Lang 两条**既有**生成路径收口验证，并新增最小 plugin-only 语言声明面
`NekoJSPlugin.generatedLangs()`：

- 不新增第二资源事件、第二资源根、第二生成管线或新资源 policy；
- `ClientEvents.generateAssets` 仍是唯一 Assets 生成事件，`ClientEvents.lang` 仍是既有
  lang 事件，二者语义不合并（LANG 未被塞进 generateAssets）；
- `generatedLangs()` 是**回调面（直调型）** default 钩子，不新增 Point，不是通用资源声明系统；
- 不复用/不清理 26（输入/HUD）、27（GUI/render）、28（PostEffects）的资源。

## 2. 实现摘要

### 2.1 真实回归修复：`Assets` typed binding 被静默删除（本票最高价值产出）

`src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java` 的 `registerBinding` 中，
`registry.register("Assets", new AssetGeneratorJS())` **整行消失**，只剩 `registerTypeDocs` 里
一条同名的 `TypeDocCatalogEntry`。

- 引入提交：`c8066519` / `59919f87`（「档1 首批守卫退场——top5 重文件接缝化」）。该提交做的是
  「把两套排版不同、折叠后逐字相同的 TypeDoc 文案按 26.x 基准合并」，`Assets` 的注册行夹在
  被折叠的两个守卫分支里，随折叠一起被删除；`NekoJSCorePlugin` 顶部注释仍写
  「Assets 是 26.x 独有」，但已无任何生产注册点。
- 后果：脚本侧 `Assets.blockState/blockModel/itemModel/texture` 变成「未定义标识符」
  （preflight 失败），而 probe/补全仍宣称该绑定存在——**文档说有、实际没有**。
- 全仓证据：`git grep '"Assets"'` 修复前只命中文档条目一处；无任何 `new AssetGeneratorJS()`
  生产调用点（`common/src/test` 里的 `AssetGeneratorJSTest` 直接 `new` 实现类，因此该单元测试
  在绑定缺失时**照样全绿**，这正是它能逃逸的原因）。
- 修复：把注册行按原位/原守卫形态放回 `registerBinding`；`AssetGeneratorJS` 的 import 随之
  由「未使用」恢复为已使用（未删 import，因为已需要它）。

### 2.2 `generatedLangs()`：plugin-only 语言的最小声明面

| 文件 | 作用 |
|---|---|
| `common/.../api/NekoJSPlugin.java` | 新增 `default Set<String> generatedLangs() { return Set.of("en_us"); }`（回调面直调钩子，javadoc 说明不需要配对扩展点、非通用资源声明系统） |
| `common/.../core/plugin/PluginGenerationHooks.java` | 新增 `resolveGeneratedLangs(Collection<String>)`：TreeSet 归一 + 整批校验 + 单插件隔离 |
| `common/.../wrapper/LangGeneratorJS.java` | 抽出 `public static boolean isValidLangCode(String)`，`writeTo` 改调同一判据 |
| `src/main/.../client/NekoJSClient.java` 与 `versions/1.21.1/.../client/NekoJSClient.java` | 成对改走新入口，循环体未动 |

**为何需要**：旧实现只遍历 `ClientEvents.LANG.registeredKeys()`，而它来自
`DispatchEventBusBase` 的 `Set.copyOf(dispatched.keySet())`——**无序**，且当没有任何脚本注册
`ClientEvents.lang` 监听器时为空集，导致 plugin `generateLang` 回调**从不触发**（静默门控）。
本票同时修掉两个问题：默认 `en_us` 保证回调触发；TreeSet 保证顺序确定。

### 2.3 关键语义

- **并集与确定性序**：语言集合 = 全插件 `generatedLangs()` 声明 ∪ 调用方传入的脚本 keyed
  listener 语言，去重后字典序（`TreeSet` → `List.copyOf`）。脚本语言经**参数**传入而非内部
  读取：`ClientEvents` 是 MC-facing 类型，`common` 不能引用它（`:common:checkCommonIsolation`），
  取键由平台 Adapter 负责。
- **整批拒绝（本票的选择）**：校验在返回**之前**完整完成，收集齐**所有**违规后抛一次
  `IllegalStateException`，消息含 owner（插件 FQN 或 `script lang listener`）与非法 code。
  因为返回值必然全合法，调用方拿到集合时非法输入**不可能产生任何语言文件**。
  票据 AC4 允许「保留或收紧」，本票选**更严的整批拒绝**，理由是它直接兑现同一 AC 的
  「无效输入不产生部分文件」——跳过非法项会让本轮产出「一半语言有文件、一半没有」的
  不可诊断中间态。行为代价已写入 MIGRATION §1.2。
- **单插件隔离**：`generatedLangs()` 抛异常 → 记 error 日志（含插件 FQN）+ `continue`，只作废
  该插件声明，不污染其他插件与其他语言；返回 `null` 或集合含 `null`/非法 code → 只进违规清单
  （前者抛异常、后者属整批拒绝）。两种归因方式刻意不同：**代码缺陷**（抛异常）不牵连他人，
  **非法输入**（非法 code）整批拒绝——不静默降级也不过度连坐。
- **异常选型**：消费点 `NekoJSClient.postClientGeneration` 已有
  `catch (Exception e) { recordCallbackError(CLIENT, "generate_assets", e) }`，抛异常因此自动获得
  「整批中止 + 进错误面板（玩家可见）」；返回错误结果类型会逼调用方重复同一段判断逻辑。
  与 `DataGeneratorJS`/`LangGeneratorJS` 既有的超限抛 `IllegalStateException` 形状也一致。

### 2.4 `isValidLangCode` 抽取的行为等价性

`writeTo` 的 `lang.matches("^[A-Za-z0-9_]{1,64}$")` 改为 `!isValidLangCode(lang)`：同一正则、
同一位置、同一异常类型与消息文本；`Objects.requireNonNull(lang, "lang")` 仍先于校验，故
`writeTo(root, null)` 仍抛 NPE 而非 IAE。`Pattern` 预先编译，与 `String.matches` 语义等价。
`PluginGenerationHooks` 里**不再**复制一份正则，预检与写入是**同一条判据**，不存在
「预检放过、写入拒绝」的裂缝。

## 3. 逐条 AC 判定

| AC | 判定 | 证据 |
|---|---|---|
| AC1 `generateAssets` 仍是唯一 Assets 生成事件、`lang` 仍是既有 lang 事件；二者与 typed binding、plugin contribution 复用同一资源根与安全写入基础；不新增第二事件/根/管线/policy；不把 LANG 塞进 generateAssets | 满足 | `Ticket29AssetBindingTest` 经**生产注册路径**（`NekoJSCorePlugin#registerBinding`）解析到 `Assets` 绑定且实现类精确为 `AssetGeneratorJS`；`AssetGeneratorJS` 组合的 `DataGeneratorJS` 与 `generateAssets` 事件同一实例类型、同一 `NekoJSPaths.get().assets()` 根（`AssetGeneratorJS` 构造器）；`ClientEvents.java` 的 `generateAssets`/`lang` 两处定义**未改动**，两总线各自携带独立 `DispatchKey`（`ASSET_STAGE_KEY`/`LANG_KEY`），语义未合并；本票 diff 未新增任何事件成员 |
| AC2 plugin generate-assets 与 generate-lang Hook 分别在同一 client generation 阶段与脚本事件聚合；`generatedLangs()` 是 plugin-only 语言最小声明面；语言集合按可重复顺序取插件声明与 `registeredKeys()` 并集；plugin callback 不因无同语言脚本 listener 被静默跳过；非法声明写入前带 plugin/owner 诊断失败；单插件失败不污染其他贡献；阶段/顺序/错误隔离可观察 | 满足 | `Ticket29GeneratedLangsTest`（7 tests）：`defaultsToEnUsSoPluginCallbacksAreNotGatedByScriptListeners`（脚本集为空仍含 en_us＝不被静默跳过）、`unionOfPluginDeclarationsAndScriptKeysIsDeduplicatedAndSorted`（同集合换传入顺序两次调用结果逐元素相等＝确定性序，且精确等于 `[de_de,en_us,fr_fr,ja_jp,sv_se]`）、`invalidPluginLangRejectsTheWholeBatchWithOwnerAndCodeInTheMessage`（消息含插件 FQN 与非法 code）、`oneFailingPluginDoesNotPoisonOtherPluginsDeclarations`（精确等于另一插件的 `[fr_fr,ja_jp]`＝整批仍成功）；两节点 `NekoJSClient` 在同一 `postClientGeneration` 内先 `fireGenerateAssets`+`GENERATE_ASSETS.post` 再逐语言 `fireGenerateLang`+`LANG.post`+`writeTo`（阶段与顺序即源码结构） |
| AC3 blockState/blockModel/itemModel/texture 的参数规范化、默认 namespace/path 补全、JSON 与占位 PNG 输出保留既有行为并有确定 contract/golden 或回读 fixture；本票不为资源限额或 PNG 新造 policy | 部分满足（不勾选） | 既有行为由**既有**测试覆盖且本票全绿：`AssetGeneratorJSTest`（variants/multipart/字符串简写/JSON 字符串输入、texture shorthand 四态、默认 namespace、子目录、占位 PNG 魔数+16×16+洋红像素、非法/穿越 id 零写盘）、`DataGeneratorJSTest`/`DataGeneratorJSPathTest`/`DataGeneratorQuotaTest`、`LangGeneratorJSTest`/`PathTest`/`QuotaTest`；`Ticket29AssetBindingTest` 补齐「绑定确实经生产路径可解析」这一**此前无人断言**的环节。**未勾选原因**：AC 要求「确定 contract/golden **或回读 fixture**」，本票交付的是既有单元测试 + 新钉绑定测试，**没有为本域新建 golden/回读 fixture**（回读语义仍由既有 `DataGeneratorJS#getJson` 测试覆盖），且 PNG 生成的 golden 化未做。缺的部分见 §5 |
| AC4 `generatedLangs()` 声明、lang code/path 校验、多脚本与 plugin key 合并、冲突策略、文件大小限制、非法语言代码、原子替换与 resource reload 回读保留或收紧既有行为；语言集合先完整确定并校验，未通过不进入任何语言文件写入；无效输入不产生部分文件、不越资源根 | 满足 | `resolveGeneratedLangs` 先完整校验再返回（AC 明列的「先确定并校验」）；非法输入用例四条：`invalidPluginLangRejectsTheWholeBatchWithOwnerAndCodeInTheMessage`、`invalidScriptLangKeyIsRejectedBeforeAnyWrite`（`../evil`）、`overlongAndEmptyLangCodesAreRejected`（65 字符、空串）、`nullDeclarationIsTreatedAsAViolation`；写入前拒绝＝调用方拿不到集合故不会进入任何 `writeTo`；路径/容量/原子替换保留既有行为（`LangGeneratorJSPathTest` 的穿越与绝对路径用例、`LangGeneratorQuotaTest` 的 16 MiB 上限且拒绝时不建目录、`writeTo` 的 sibling temp + ATOMIC_MOVE 未改）。**选整批拒绝而非跳过**已在本节与 §2.3 说明 |
| AC5 资源写入保留现有路径包含性、容量、非法 id、冲突 kind 和原子替换行为；无效输入不产生部分文件、不越资源根 | 满足 | `AssetGeneratorJSTest#rejectsInvalidAndTraversingIds`（大写命名空间、`..`、多冒号、空路径、null，且断言 `Files.walk(root)` 无任何文件落盘）、`#textureRejectsKindConflictWithDirectoryPath`（kind 与目录冲突、非法 kind）、`DataGeneratorJSPathTest#rejectsSymlinkParentPointingOutsideGameDir`（符号链接逃逸）、`DataGeneratorQuotaTest`（单文件 16 MiB / 累计 64 MiB，拒绝时不建目标与父目录）——全部为**既有**测试且本票全绿 |
| AC6 plugin generate-assets Hook 与脚本事件在同一 client generation 聚合，事件按资源 reload 生命周期恰好触发一次；懒读或显式 reload 后资源能被实际 resource manager 回读 | 部分满足（不勾选） | 「同一 client generation 聚合」满足：两节点 `postClientGeneration` 是唯一调用点，`fireGenerateAssets` 与 `GENERATE_ASSETS.post` 用同一 generator 实例（`PluginGenerationHooksTest#fireGenerateAssetsInvokesRegisteredPluginWithSharedGenerator` 钉住「同一实例送达」）。**不勾选原因**：「恰好触发一次」与「被实际 resource manager 回读」需要真实 client 资源 reload（`NekoJSPackLoader` + MC resource manager），本票**未做真机/GameTest smoke**，只有源码结构与时序的单测证据。缺的部分见 §5 |
| AC7 client-only 过滤证明 dedicated server 不注册入口、不加载 client 类、不写资源；脚本 side 与节点 capability 一致 | 部分满足（不勾选） | 结构性证据：`NekoJSClient` 整文件在 `//? if neoforge` 块内；`NekoJSMod#registerClient` 以 `McPlatformCompat.get().isClientDist()` 门控后才调用 `NekoJSClient.register`（源码可核）；`ClientEvents` 的 `GROUP.client(...)` 把两总线标为 `ScriptType.CLIENT`。**不勾选原因**：本票未新增 dedicated-server 进程级 smoke 断言（「不加载 client 类」在真机 dist 下无测试），AGENTS.md 亦要求 loader/dist 行为走运行时 smoke + MCP 证据，本票未做。缺的部分见 §5 |
| AC8 平台/版本 Adapter 按各节点既定支持等级、能力与限制作 pack 注册/路径/reload 验证；supported/partial/unavailable 由真实 source trace 与 smoke 决定，不自动补 Fabric parity | 部分满足（不勾选） | 三节点 `platformGateTest` 全绿（26.1.2 / 1.21.1 / 26.2.0；`EventSurfaceDomainGateTest` 1/0/0、`PlatformSpecContractGateTest` 1/0/0）证明本票未造成事件面漂移；两节点 `NekoJSClient` 成对同步。**不勾选原因**：本票**未新造 capability 矩阵记录**，也未对 fabric 节点跑 Assets/Lang 的 source trace 或 smoke；`AssetGeneratorJS` 绑定的 `//? if >=26` 守卫意味着 1.21.1 **没有** `Assets` 绑定——这是既存平台差异，本票未改变也未正式记录为 capability 条目。缺的部分见 §5 |
| AC9 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 与生成文件互相追溯；普通测试不得更新 golden；显式更新需旧新 diff 与审阅 | 部分满足（不勾选） | 追溯面已交付：`Ticket29AssetBindingTest#assetsDocumentationEntryIsBackedByARuntimeBinding` 钉住「TypeDoc 声明面与 `BindingRegistry` 运行时注册面必须同指一个名字」——这正是回归能发生的缺口。**不勾选原因**：未触及 `api-manifest-core.json` 或 probe 的 TS/Python declaration 产物（`Assets` 在这些 golden 中 0 命中），本票未为 `Assets`/`generatedLangs()` 新增 declaration 条目或 declaration parity fixture。**golden 变更：无**（见 §4.3） |
| AC10 EntitySelectors、DataMap、PostEffects 和已事件化 recipe/loot/tags/JEI/render 域不被并入本票；本票只证明不重复它们的 owner 或事件 | 满足 | 本票 diff 文件清单（§4.4）不含任何其他域文件；未新增事件成员，故未触碰 `ClientEvents.POST_EFFECTS` 或 26/27 的输入/HUD 行；`platform-gates/event-surface-domains.txt` 的**唯一**变更来自 cherry-pick `353838c8`（主干票 22 的修复），本票自身未改该文件 |
| AC11 旧直接文件写入、绕过 DataGenerator 的 asset helper 或重复生成入口只能在生成/回读 parity、迁移表、旧 route 无消费者和维护者确认后删除；不保留长期兼容双路径 | 不勾选（维护者 sign-off 门禁） | 本票**未删除任何旧公开路径**：`generateAssets`/`lang` 事件、`DataGeneratorJS`/`LangGeneratorJS`/`AssetGeneratorJS` 的公开方法面保持不变，无「旧 route」可清理（唯一的路径变更——`Assets` 绑定恢复——是**恢复**而非删除）。故本 AC 的删除条件在本票范围内**无适用对象**，但按 Human input note 不由 agent 代答确认，保持未勾选 |
| AC12 随实现交付脚本/plugin 聚合、plugin-only 语言声明、路径校验、回读与不可用能力拒绝的最小可运行示例与必要迁移材料；迁移材料明确旧版依赖脚本 listener 解锁非 en_us plugin 语言的插件现在必须声明 `generatedLangs()`，默认 en_us 无需声明 | 满足 | `examples/assets-and-generated-langs.js`（Assets typed binding + `ClientEvents.generateAssets`/`lang` + plugin `generatedLangs()` 的最小形态）；`MIGRATION.md` §1 明确写出该迁移要求与「默认 en_us 无需声明」，§1.2 写明整批拒绝的行为后果 |

## 4. 真实验证命令与结果

全部在本 worktree 真跑；摘录存于本目录 `command-output/`。

| # | 命令 | 结果 |
|---|---|---|
| 1 | `gradlew :common:check --console=plain` | **BUILD SUCCESSFUL in 2m 11s**；`:common:checkCommonIsolation`、`:common:test`、`:common:check` 真执行。逐用例汇总：**1763 tests, 0 failures, 0 errors, 4 skipped** |
| 2 | `gradlew :26.1.2:test --tests '*Ticket29*' --rerun --console=plain` | **BUILD SUCCESSFUL in 8s**；XML `Ticket29AssetBindingTest: tests=2 failures=0 errors=0 skipped=0` |
| 3 | `gradlew :1.21.1:test --console=plain` | **BUILD SUCCESSFUL in 35s**；汇总 **237 tests, 0 failures, 0 errors, 10 skipped** |
| 4 | `gradlew :26.1.2:platformGateTest :1.21.1:platformGateTest :26.2.0:platformGateTest --console=plain` | **BUILD SUCCESSFUL in 27s**；三节点 gate 全真执行（`Task :26.1.2:platformGateTest` / `:1.21.1:` / `:26.2.0:`）。26.1.2 XML：`EventSurfaceDomainGateTest tests=1 failures=0 errors=0`、`PlatformSpecContractGateTest tests=1 failures=0 errors=0`。**无 member-drift** |
| 5 | `gradlew guardLint --console=plain` | **BUILD SUCCESSFUL in 6s**；`288 个守卫块，扫描 438 个文件；超限豁免 0 个；警告 0 条` |
| 6 | `gradlew :26.1.2:test --console=plain`（补跑全量，为报告取诚实计数） | **BUILD SUCCESSFUL in 21s**；汇总 **332 tests, 0 failures, 0 errors, 54 skipped** |
| 7 | `gradlew :26.1.2:compileJava` / `:1.21.1:compileJava` / `:common:compileJava` | 全部 **BUILD SUCCESSFUL**（仅有仓库既存 deprecation / this-escape / unchecked 提示） |

### 4.1 逐节点计数汇总

```
node;    tests; failures; errors; skipped
common;  1763;  0;        0;      4
26.1.2;  332;   0;        0;      54
1.21.1;  237;   0;        0;      10
26.2.0;  （未跑普通 test，仅跑 platformGateTest 通过；见 §5）
```

### 4.2 红/绿证据（`Assets` 绑定回归钉）

**红**（把 `registry.register("Assets", new AssetGeneratorJS())` 临时注释掉，
`:26.1.2:test --tests '*Ticket29AssetBinding*'`）：

```
BUILD FAILED in 13s
Ticket29AssetBindingTest > assetsBindingResolvesThroughTheProductionRegistrationPath() FAILED
    org.opentest4j.AssertionFailedError at Ticket29AssetBindingTest.java:62
Ticket29AssetBindingTest > assetsDocumentationEntryIsBackedByARuntimeBinding() FAILED
    org.opentest4j.AssertionFailedError at Ticket29AssetBindingTest.java:96
2 tests completed, 2 failed

--- assetsBindingResolvesThroughTheProductionRegistrationPath()
AssertionFailedError: 'Assets' must be registered by NekoJSCorePlugin.registerBinding: the
script-facing typed asset generators (Assets.blockState/blockModel/itemModel/texture) have no
other registration path ==> expected: not <null>
--- assetsDocumentationEntryIsBackedByARuntimeBinding()
AssertionFailedError: documented binding 'Assets' has no runtime registration: declarations and
the binding registry must agree ==> expected: not <null>
```

**绿**（恢复注册后 `:26.1.2:test --tests '*Ticket29AssetBinding*' --rerun`）：

```
BUILD SUCCESSFUL in 4s
> Task :26.1.2:test          （非 UP-TO-DATE / FROM-CACHE）
tests=2 failures=0 errors=0 skipped=0 time=0.306
```

### 4.3 golden / 基线变更

**本票自身未修改任何 golden。** `git diff` 中 `src/test/resources/nekojs/platform-gates/event-surface-domains.txt`
的变更**全部**来自 cherry-pick `353838c8`（主干票 22 的修复：五行 `ServerEvents` 基线补
`tradeDeclaration,tradeReload`），不是本票产生的内容：

```diff
-ServerEvents | 26.1.2 = present | buses=...,tickPre
+ServerEvents | 26.1.2 = present | buses=...,tickPre,tradeDeclaration,tradeReload
（1.21.1 / 26.2.0 / 26.1.2-fabric / 26.2.0-fabric 同形，共 5 行）
```

理由与来源见该提交自身的 message；本票 cherry-pick 它是为了不在 `platformGateTest` 上
继承主干的既存红。

### 4.4 本票 diff 文件清单

```
common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java           (+Set import, +generatedLangs())
common/src/main/java/com/tkisor/nekojs/core/plugin/PluginGenerationHooks.java  (+resolveGeneratedLangs)
common/src/main/java/com/tkisor/nekojs/wrapper/LangGeneratorJS.java    (+isValidLangCode, writeTo 改调)
src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java             （+恢复 Assets 注册 3 行）
src/main/java/com/tkisor/nekojs/client/NekoJSClient.java               （循环改走 resolveGeneratedLangs）
versions/1.21.1/src/main/java/com/tkisor/nekojs/client/NekoJSClient.java（成对同上）
src/test/java/com/tkisor/nekojs/core/Ticket29AssetBindingTest.java     （新增，2 tests）
common/src/test/java/com/tkisor/nekojs/core/plugin/Ticket29GeneratedLangsTest.java（新增，7 tests）
docs/architecture-refactor/baseline/2026-09-21-assets-lang/**           （本证据目录）
docs/architecture-refactor/implementation-tickets/29-assets.md         （票据勾选 + Closure record）
```

### 4.5 失败诊断（真实发生过的红）

1. **新测试首次编译失败**：我最初把测试放在 `src/test` 根树却 import 了
   `com.tkisor.nekojs.testfixture.TestPlatformInit`——该 helper **只存在于 common 测试树**，
   根树没有。改为照根树既有的 `KeyBindEventsTest`/`QueryToolDeclarationParityTest` 形态
   内联一个最小 `Platform` 桩（`TestGameDirs.unique(...)` 取 gameDir）。
2. **测试自身写错（自我修正）**：第三条断言原为
   `assertInstanceOf(AssetGeneratorJS.class, assets.valueType())`——`valueType()` 返回
   `Class<?>`，该断言恒不可满足。改为**更严的** `assertEquals(AssetGeneratorJS.class, assets.valueType())`
   （精确类相等），红证据在改完后重跑，两段红/绿对应同一份最终测试。
3. **用例 4 原版半句恒真（自我修正）**：`oneFailingPluginDoesNotPoisonOtherPluginsDeclarations`
   初版用 `containsAll(...)` + `!contains("ko_kr")`，后半句恒真（桩插件根本不返回 `ko_kr`），
   区分不了「整批失败」/「只废弃该插件」。改为 `assertEquals(List.of("fr_fr","ja_jp"), langs)` 精确相等。

## 5. 未验证项与 owner

| 未验证项 | 缺什么 | owner |
|---|---|---|
| AC3 的 contract/golden 或回读 fixture | 本域未新建 golden；PNG 与 JSON 输出的 golden 化未做（现有证据是既有单元测试 + 新钉绑定测试） | 票 29 后续轮 / 票 33 派生面 owner |
| AC6「事件按 reload 生命周期恰好触发一次」+「resource manager 实际回读」 | 真实客户端资源 reload（F3+T 首帧、`NekoJSPackLoader` 经 MC resource manager 读到 `nekojs/assets` 下本票写出的文件）——需真机/GameTest smoke | CLIENT_GUI_RENDER owner / 票 34 真机试做 |
| AC7 dedicated server 不注册入口/不加载 client 类/不写资源 | 进程级 dist smoke；本票只有源码结构与 `isClientDist()` 门控的静态证据 | loader/dist 面 owner / 票 34 |
| AC8 每节点 capability 与 fabric source trace | 未新造 capability 矩阵；未对 fabric 跑 Assets/Lang 的 source trace 或 smoke。**已知既存差异**：`Assets` 绑定带 `//? if >=26` 守卫，1.21.1 节点没有该绑定（本票恢复的是 26.x 守卫分支内的注册行，未扩大平台面） | 票 31/32 fabric 面 owner |
| AC9 TS/Python declaration 追溯 | 未为 `Assets`/`generatedLangs()` 新增 declaration 条目；`api-manifest-core.json` 与 probe declaration golden 对该域 0 命中 | 票 09/33 declaration 面 owner |
| 26.2.0 普通 `:26.2.0:test` | 本票未跑（只跑了 26.2.0 的 `platformGateTest`）；两个 26.x 节点共享同一份源码，26.1.2 全量 332 tests 已覆盖该共享面 | 票 34 五节点收口 |
| 端到端「非 en_us 插件语言现在必须声明」 | `resolveGeneratedLangs` 的单测层并集语义已有；真实 `NekoJSClient` 路径上「声明了 ja_jp 的插件确实产出 `lang/ja_jp.json`」未被端到端断言 | 票 29 后续轮 / 票 34 |

## 6. 双轴自查（/code-review 口径）

### 6.1 标准轴（AGENTS.md / docs/agents/coding.md）

- **范围与单一目标**：改动集中在本票域；唯一越出「Assets/Lang 既有文件」的是
  `NekoJSCorePlugin` 的 3 行恢复——已获维护者裁定属本域根因修复（AC1/AC3 要求该 typed
  binding 是调用者入口）。`ClientEvents.java` 的输入/HUD 行未碰；共享 golden 未改。
- **注释语言**：新增 javadoc/注释沿用邻近文件的中文风格（与票 28 §6.3 记录的仓库现状一致），
  未做批量语言重写；新增英文日志消息（`generatedLangs hook failed for ...`）按
  AGENTS.md#Language 的「developer-facing log templates 用英文」。**未**出现描述不存在方法
  或把平台事实写成既成事实的注释：`generatedLangs()` 的 javadoc 只陈述实现确实有的语义，
  `resolveGeneratedLangs` 的 javadoc 明确写了「脚本语言由调用方传入」这一真实接缝形状。
- **无未请求抽象**：没有为单一实现造 interface/factory；`resolveGeneratedLangs` 是唯一
  消费者直接调用的静态方法；`isValidLangCode` 有**两个**真实消费者（`writeTo` 预检 +
  `resolveGeneratedLangs`），不是投机抽取。
- **日志**：新日志以事件/失败开头（`generatedLangs hook failed for <FQN>`）并说明后果
  （`its language declarations are skipped`），含定位所需的 owner，异常作为 cause 附上；
  未吞异常、未把失败伪装成成功。
- **验证**：#1–#7 按 coding.md 验证表选取（common 改动跑了 `:common:check` 而非只跑
  `:common:test`；边界/守卫改动跑了 `guardLint`；事件面跑了三节点 `platformGateTest`）。
  非平凡新逻辑（并集+排序+整批校验+隔离）配了 7 个能区分正确/破功的用例，无恒真断言
  （已自查并修掉一处，见 §4.5.3）。
- **已知标准缺口**：与票 28 §6.3 同类——本仓 912 个 Java 中 663 个含中文注释，未落地
  AGENTS.md 的英文注释规范；本票沿用邻近风格，如实记录为仓库既存状态。

### 6.2 规格轴

- **AC 逐条**：见 §3。**4 条勾选、3 条部分满足不勾选、1 条 sign-off 门禁不勾选**，每条都写了
  缺什么。
- **越权**：`Assets` 绑定恢复是本票 diff 中唯一「票据字面未点名、但 AC 语义要求」的改动，
  已由维护者书面裁定纳入；除此之外无 scope creep——未新增事件、未改声明产物、未碰其他域。
- **做的比要求多**：`resolveGeneratedLangs` 的 `scriptRegisteredLangs` 形参用
  `Collection<String>` 而非 `Set<String>`（`registeredKeys()` 返回 `Set`，宽形参让测试能传
  `List` 并覆盖「输入顺序无关」这一点）；`null` 形参被当空集处理——两处都是为可测性与健壮性
  的最小放宽，未扩大公开面（方法本身是 internal 平台入口，不在脚本/插件作者契约内）。
- **做的比要求少**：见 §5，主要是真机 smoke、capability 矩阵、declaration 面三块，均已记
  owner，**未**在 REPORT 里宣称已满足。

## 7. 归档与可追溯

- 命令原始摘录：本目录 `command-output/01..07`。
- 迁移与行为后果：同目录 `MIGRATION.md`。
- 最小示例：同目录 `examples/assets-and-generated-langs.js`。
- 回归引入点：`c8066519` / `59919f87`（`git show c8066519 -- src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java`
  的 diff 里可见 `-        registry.register("Assets", new AssetGeneratorJS());`）。
