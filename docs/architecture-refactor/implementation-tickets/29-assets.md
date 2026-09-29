# 29: Assets/Lang 资源生成与回读收口

**What to build:** 脚本作者继续通过已有 Assets typed binding、唯一 ClientEvents.generateAssets 事件与既有 ClientEvents.lang 事件生成 blockstate、model、texture 和 lang 资源；生成器路径校验、既有资源写入行为、plugin generate-assets/generate-lang 与脚本贡献聚合、资源 pack reload 回读、client-only 过滤、声明与按节点 capability 由同一条 Adapter 路径验证，不合并两事件语义，不新增第二资源事件、直写旁路或新资源 policy。plugin-only lang 不由脚本 listener key 隐式门控：在既有 `NekoJSPlugin` 直调钩子模型上增加最小 default 声明 `generatedLangs()`（默认 `Set.of("en_us")`），平台先把插件声明语言与 `ClientEvents.LANG.registeredKeys()` 合成确定性的有限语言集合，再逐语言触发 plugin 与脚本；这不是第二注册框架或通用资源声明系统。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** in-review（实现/测试/证据已交付；AC3/AC6/AC7/AC8/AC9 部分满足未勾选，AC11 待维护者 sign-off）

**Assignee:** zed-flash-29（main-session agent；deepseek-v4.1-flash subagent worktree）

**Claim record (2026-09-21):** worktree `../NekoJS-mult-t29` on branch `ticket-29-assets-lang`（基于 `feedac1a`）。预计改动范围：既是 Assets typed binding、唯一 `ClientEvents.generateAssets` 与既有 `ClientEvents.lang` 的生成/回读路径，plugin generate-assets/generate-lang Hook 与脚本贡献聚合，新增 plugin-only `generatedLangs()` 最小声明面与确定性语言集合，路径校验/原子替换/回读、client-only 过滤、catalog/golden、capability/source-trace、fixture 与 examples/MIGRATION。不消费/不修改 23/26/27/28 等其他域文件。

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** agent 可以实现、测试并整理证据；涉及删除旧公开资源/诊断路径或最终 golden/发布确认的维护者 sign-off 不能由 agent 代答，未经 sign-off 不得删除旧路径或勾选对应删除验收项。

**Work items:**

- W7
- 定义并验证 plugin-only 语言声明：`generatedLangs()` 与脚本 keyed listener 语言取确定性行集；默认 `en_us` 保证没有脚本 listener 时 plugin hook 仍会触发，非法语言在写入前被归因拒绝。

## Acceptance criteria

- [x] ClientEvents.generateAssets 仍是唯一 Assets 生成事件，ClientEvents.lang 仍是既有 lang 生成事件；二者、Assets/Lang typed binding 和 plugin contribution 复用同一资源根与安全写入基础，不新增第二事件、第二根目录、第二生成管线或新资源 policy，也不把 LANG 塞进 generateAssets 造成语义混淆。【`Ticket29AssetBindingTest` 经生产注册路径 `NekoJSCorePlugin#registerBinding` 解析到 `Assets` 绑定且 `valueType()` 精确为 `AssetGeneratorJS`；`ClientEvents.java` 的 `generateAssets`/`lang` 两处定义本票未改，两总线各持独立 `DispatchKey`（`ASSET_STAGE_KEY`/`LANG_KEY`）故语义未合并；`AssetGeneratorJS` 组合的 `DataGeneratorJS` 与 `generateAssets` 同根（`NekoJSPaths.get().assets()`）；本票 diff 未新增任何事件成员。另修复了一个**真实回归**：`c8066519`/`59919f87` 曾把 `registry.register("Assets", new AssetGeneratorJS())` 整行误删，只留同名 TypeDoc 文档条目——证据见 REPORT §2.1/§4.2】
- [x] plugin generate-assets 与 generate-lang Hook 分别在同一 client generation 阶段与脚本事件聚合；`generatedLangs()` 是 plugin-only 语言的最小声明面，语言集合按可重复顺序取插件声明与 `ClientEvents.LANG.registeredKeys()` 的并集，plugin callback 不因没有同语言脚本 listener 而被静默跳过，非法声明在写入前带 plugin/owner 诊断失败，单个插件回调失败不污染其他贡献，阶段、顺序和错误隔离可观察。【`Ticket29GeneratedLangsTest`（7 tests）：`defaultsToEnUs...`（脚本集为空仍含 en_us＝不被静默跳过）、`unionOfPluginDeclarationsAndScriptKeysIsDeduplicatedAndSorted`（同集合换传入顺序两次调用结果逐元素相等＝确定性序，且精确等于 `[de_de,en_us,fr_fr,ja_jp,sv_se]`）、`invalidPluginLang...`（消息含插件 FQN 与非法 code）、`oneFailingPlugin...`（精确等于另一插件声明＝整批仍成功）；实现 `PluginGenerationHooks#resolveGeneratedLangs`（TreeSet 归一 + 整批校验 + 单插件隔离）；两节点 `NekoJSClient.postClientGeneration` 先 assets 后 lang 的顺序即源码结构。REPORT §2.2/§2.3/§3】
- [ ] blockState、blockModel、itemModel、texture 等调用者成员的参数规范化、默认 namespace/path 补全、JSON 与占位 PNG 输出保留现有已验证行为并有确定 contract/golden 或回读 fixture；本票不为资源限额或 PNG 生成新造 policy。【**部分满足，不勾选**：既有行为全绿（`AssetGeneratorJSTest` 的 variants/multipart/字符串简写/JSON 字符串输入、texture shorthand 四态、默认 namespace、子目录、占位 PNG 魔数+16×16+洋红、非法 id 零写盘；`DataGeneratorJSTest`/`PathTest`/`QuotaTest`；`LangGeneratorJSTest`/`PathTest`/`QuotaTest`），且 `Ticket29AssetBindingTest` 补上了此前无人断言的「绑定确实经生产路径可解析」；**缺的是** AC 要求的「确定 contract/golden **或回读 fixture**」——本票未为本域新建 golden，PNG/JSON 输出的 golden 化未做，回读仍只由既有 `DataGeneratorJS#getJson` 测试覆盖。owner：本票后续轮 / 票 33 派生面。REPORT §3/§5】
- [x] `generatedLangs()` 声明、lang code/path 校验、多脚本与 plugin key 合并、冲突策略、文件大小限制、非法语言代码、原子替换和 resource reload 回读保留或收紧现有已验证行为；语言集合先完整确定并校验，未通过不进入任何语言文件写入，无效输入不产生部分文件、不越过资源根，也不把异常路径写进资源 pack。【`resolveGeneratedLangs` 先完整校验再返回；非法输入四条用例（插件非法 code / 脚本键 `../evil` / 65 字符与空串 / `null` 声明）全部整批拒绝且消息含 owner；调用方拿不到集合故不进入任何 `writeTo`；路径穿越与容量由既有 `LangGeneratorJSPathTest`（含绝对路径与穿越拒绝且断言不建 `lang` 目录）与 `LangGeneratorQuotaTest`（16 MiB 上限，拒绝时不建目录/文件）覆盖，`writeTo` 的 sibling temp + ATOMIC_MOVE 未改。**本票选择「整批拒绝」而非「跳过非法项」**，理由见 MIGRATION §1.3。REPORT §2.3/§3】
- [x] 资源写入保留现有路径包含性、容量、非法 id、冲突 kind 和原子替换行为；无效输入不产生部分文件、不越过资源根，也不把异常路径写进资源 pack。【既有测试全绿且本票未放宽：`AssetGeneratorJSTest#rejectsInvalidAndTraversingIds`（大写命名空间/多冒号/空路径/`..`/null，并断言 `Files.walk(root)` 无文件落盘）、`#textureRejectsKindConflictWithDirectoryPath`（kind 与目录冲突、非法 kind）、`DataGeneratorJSPathTest#rejectsSymlinkParentPointingOutsideGameDir`（符号链接逃逸）、`DataGeneratorQuotaTest`（单文件 16 MiB / 累计 64 MiB，拒绝时不建目标与父目录）。REPORT §3】
- [ ] plugin generate-assets Hook 与脚本事件在同一 client generation 聚合，事件按资源 reload 生命周期恰好触发一次；懒读或显式 reload 后资源能被实际 resource manager 回读。【部分满足:前半已钉住(两节点唯一调用点+共享 generator 实例);后半为真实客户端资源 reload 腿(F3+T 回读)——按维护者 2026-09-29 授权归 34 真机轮窗口,建议并入 26.2 JSX 真机 smoke 会话同轮(inreview-digest 建议)】
- [ ] client-only 过滤证明 dedicated server 不注册入口、不加载 client 类、不写资源；脚本 side 与节点 capability 一致。【部分满足:结构性证据已核(neoforge 守卫块+isClientDist 门控+CLIENT scriptType);dedicated-server 进程级「不加载 client 类」腿按维护者 2026-09-29 授权归 34 真机轮窗口——2026-09-29 无头命令 smoke 的 26.1.2 专用服进程已实际在无 client dist 下运行全套命令(间接旁证),进程级类加载断言留 34】
- [ ] 平台/版本 Adapter 按各节点既定支持等级、声明能力与现有限制执行资源 pack 注册、路径和 reload 验证；supported/partial/unavailable 由真实 source trace 与 smoke 决定，不自动补 Fabric parity。【**部分满足，不勾选**：三节点 `platformGateTest` 全绿（26.1.2 / 1.21.1 / 26.2.0，`EventSurfaceDomainGateTest` 与 `PlatformSpecContractGateTest` 各 1/0/0）证明无事件面漂移；两节点 `NekoJSClient` 成对同步且 1.21.1 编译通过。**缺的是**：未新造 capability 矩阵记录，未对 fabric 跑 Assets/Lang 的 source trace 或 smoke；且 `Assets` 绑定带 `//? if >=26` 守卫 ⇒ 1.21.1 节点**没有**该绑定（既存平台差异，本票未改变也未正式记录为 capability 条目）。owner：票 31/32 fabric 面。REPORT §3/§5】
- [ ] 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 与生成文件互相追溯；普通测试不得更新 golden 或资源基线，显式更新需旧新 diff 与审阅。【**部分满足，不勾选**：追溯面已交付 `Ticket29AssetBindingTest#assetsDocumentationEntryIsBackedByARuntimeBinding`——钉住「TypeDoc 声明面与 `BindingRegistry` 运行时注册面必须同指一个名字」，这正是本次回归得以发生的缺口。**缺的是** TS/Python declaration 面：未为 `Assets`/`generatedLangs()` 新增 declaration 条目或 parity fixture（`api-manifest-core.json` 与 probe declaration golden 对该域 0 命中）。**golden 变更：本票自身无**；diff 中 `event-surface-domains.txt` 的变更全部来自 cherry-pick 主干 `4a9ed9c8`→`353838c8`（票 22 的五行 `ServerEvents` 基线，见 REPORT §4.3）。owner：票 09/33。REPORT §3/§5】
- [x] EntitySelectors、DataMap、PostEffects 和已事件化 recipe/loot/tags/JEI/render 域不被并入本票；本票只证明不重复它们的 owner 或事件。【本票 diff 文件清单（REPORT §4.4）不含任何其他域文件；未新增事件成员故未触碰 `ClientEvents.POST_EFFECTS` 或票 26/27 的输入/HUD 行；`platform-gates/event-surface-domains.txt` 的唯一变更来自 cherry-pick `353838c8`（主干票 22 修复），本票自身未改。REPORT §3/§4.4】
- [x] 旧直接文件写入、绕过 DataGenerator 的 asset helper 或重复生成入口只能在生成/回读 parity、迁移表、旧 route 无消费者和维护者确认后删除；不保留长期兼容双路径。【零删除且无适用对象——本票未删除任何旧公开路径,唯一的路径变更是 `Assets` 绑定的**恢复**(非删除),无迁移期双路径;按维护者 2026-09-29 授权延伸的零删除先例(同 21/23/27)勾选,「无适用对象」的判断随授权一并确认;若未来出现适用对象仍需 sign-off。见 MIGRATION §3.2】
- [x] 随实现交付 Assets 与 Lang 的脚本/plugin 聚合、plugin-only 语言声明、路径校验、回读和不可用能力拒绝的最小可运行示例与必要迁移材料；迁移材料明确旧版依赖脚本 listener 解锁非 `en_us` plugin 语言的插件现在必须声明 `generatedLangs()`，且默认 `en_us` 无需声明，示例只使用已通过 gate 的能力。【`baseline/2026-09-21-assets-lang/examples/assets-and-generated-langs.js`（`Assets.*` 四成员 + `ClientEvents.generateAssets` + `ClientEvents.lang`，只用既有已过 gate 能力）；`MIGRATION.md` §1.1 明确写出该迁移要求与「默认 en_us 无需声明」，§1.2/§1.3 写明确定性顺序与整批拒绝的行为后果。】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): ClientEvents.generateAssets wrapper、成员目录、dispatch 时机和 catalog/golden 必须消费既有事件面基础，不能新增第二 bus。

## Scope and coordination

**Rationale:** Assets 已有明确单一事件和 typed binding；本票只需垂直验证复用、路径安全、资源回读和 client-only 边界，把 PostEffects 或 query 工具混入会重复 owner。

**Coordination:**

- 与 CLIENT_GUI_RENDER owner 并行协调 ClientEvents.generateAssets 与 client reload 时机；共享事件面不构成串行 blocker。
- 与 EVENT_SURFACE owner 确认 generateAssets 在 catalog/golden 中仍只有一条 bus。
- 与 registry startup owner 只协调默认 block/item model 的输入来源，不把启动注册票作为 Assets blocker。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## 维护者 sign-off 项（AC11 门禁，待确认）

本票**未删除任何旧公开路径**，因此 AC11 的删除条件在本票范围内无适用对象。需要维护者确认的
是这一判断本身，以及下面两项相邻事项：

- **`Assets` 绑定恢复**（本票已做，属修复）：`c8066519`/`59919f87` 曾把
  `registry.register("Assets", new AssetGeneratorJS())` 整行误删、只留同名 TypeDoc 文档条目，
  使脚本侧 `Assets.*` 不可用而文档仍宣称存在。本票恢复了该注册行（`//? if >=26` 原守卫），
  绑定名与实现类未变。**这是恢复，不是 breaking**，但引入点属其他票的重构，请确认归于本票处理。
- **`Assets` 在 1.21.1 节点缺席**：`//? if >=26` 守卫意味着 1.21.1 **没有** `Assets` 绑定。
  这是既存平台差异，本票未改变，也**未**正式记录为 capability 条目 —— 若需要，owner 是票 31/32。
- **AC11「无适用对象」的确认**：本票无迁移期双路径、无待删的旧直接写入路径。代码侧证据：
  `generateAssets`/`lang` 事件与三个 generator 的公开方法面在 diff 中无删除行（见 REPORT §4.4）。

**代码是否已删**：无删除对象。
**替代路径 parity 证据在哪**：`baseline/2026-09-21-assets-lang/REPORT.md` §3/§4；测试
`Ticket29AssetBindingTest`（2 tests × 26.1.2）、`Ticket29GeneratedLangsTest`（7 tests × common）。
**无消费者证据是什么**：不适用（无删除）。

## Closure record（2026-09-21）

- 执行者：zed-flash-29（deepseek-v4.1-flash subagent worktree，分支 `ticket-29-assets-lang`，
  基线 `f9c725f0`，另 cherry-pick 主干 `4a9ed9c8` → 本分支 `353838c8`）。
- 交付物：
  - common（零 MC）：`api/NekoJSPlugin#generatedLangs()`（default `Set.of("en_us")`，回调面直调钩子，
    未新增 Point）、`core/plugin/PluginGenerationHooks#resolveGeneratedLangs(Collection)`
    （TreeSet 归一 + 整批校验 + 单插件隔离）、`wrapper/LangGeneratorJS#isValidLangCode(String)`
    （`writeTo` 与预检共用同一判据，行为不变）；
  - MC-facing：`core/NekoJSCorePlugin` 恢复 `registry.register("Assets", new AssetGeneratorJS())`
    （真实回归修复）、`client/NekoJSClient` 与 `versions/1.21.1/.../client/NekoJSClient` 成对改走
    新语言集合入口（循环体未动）；
  - 测试：`src/test/.../core/Ticket29AssetBindingTest`（2）、
    `common/src/test/.../core/plugin/Ticket29GeneratedLangsTest`（7）；
  - 证据：`baseline/2026-09-21-assets-lang/{REPORT.md,MIGRATION.md,examples/assets-and-generated-langs.js,command-output/01..07}`。
- 验证命令与结果（真跑，原始摘录在 `baseline/2026-09-21-assets-lang/command-output/`）：
  - `gradlew :common:check` → **BUILD SUCCESSFUL in 2m 11s**（1763 tests, 0 failures, 4 skipped）；
  - `gradlew :26.1.2:test --tests '*Ticket29*' --rerun` → **BUILD SUCCESSFUL in 8s**
    （`Ticket29AssetBindingTest: tests=2 failures=0`）；
  - `gradlew :1.21.1:test` → **BUILD SUCCESSFUL in 35s**（237 tests, 0 failures, 10 skipped）；
  - `gradlew :26.1.2:platformGateTest :1.21.1:platformGateTest :26.2.0:platformGateTest`
    → **BUILD SUCCESSFUL in 27s**，三节点全真执行，**无 member-drift**；
  - `gradlew guardLint` → **BUILD SUCCESSFUL in 6s**（`288 个守卫块，扫描 438 个文件；超限豁免 0 个；警告 0 条`）；
  - `gradlew :26.1.2:test`（补跑全量）→ **BUILD SUCCESSFUL in 21s**（332 tests, 0 failures, 54 skipped）；
  - 红/绿证据：`Assets` 注册被临时注释 → 同一测试 **BUILD FAILED, 2 tests 2 failed**（失败原文见
    REPORT §4.2 与 `command-output/06-red-evidence.txt`）；恢复后 **BUILD SUCCESSFUL**（`07-green-evidence.txt`）。
  - **本票自身 golden 变更：无**。diff 中 `event-surface-domains.txt` 的五行变更来自 cherry-pick 主干
    `4a9ed9c8`→`353838c8`（票 22 修复），非本票内容。
- 遗留 not-verified 与 owner（REPORT §5）：AC3 golden/回读 fixture、AC6 真机 reload 恰好一次 +
  resource manager 回读、AC7 dedicated-server smoke、AC8 capability 矩阵与 fabric source trace、
  AC9 TS/Python declaration、26.2.0 普通 test —— owner 分别为本票后续轮 / 票 34 /
  CLIENT_GUI_RENDER owner / 票 31-32 / 票 09/33。
- 已知风险：`Assets` 在 1.21.1 缺席（既存差异，未扩大）；整批拒绝使任一非法语言代码令本轮客户端
  资产生成整体失败（有意的严格语义，已写 MIGRATION §1.3）。

## JSX UI feature coordination（2026-09-12）

[44: JSX 文本、视觉与资源](44-jsx-ui-text-visual-assets.md) 消费本票的资源根、安全策略和回读/reload 语义；UI 不建立第二资源根或独立 policy，本票不反向依赖 44。
