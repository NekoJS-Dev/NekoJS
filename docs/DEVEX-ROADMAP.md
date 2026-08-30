# DevEX 计划：守卫退场 · 节点构建收拢 · 加载器轴正名 · 工作流收尾

> 2026-08-30 定稿。承接 `docs/MIGRATION-ROADMAP.md`（P0–P4 已收束：守卫密度治理完成，opener 991 → 624，豁免清零）。
> 本计划治理下一层：守卫的**位置**（退出业务代码）与**包装层**（构建收拢、加载器正名、工作流）。
> 维护形态：本地文档（不发 GitHub issue）；轨道独立可合并，`sandboxCheck` 全绿为合并前置。
> 状态：定稿待实施；进度在下方清单勾选维护，依据见各节。

## 进度

- [x] T3 加载器轴正名：`deps.platform` / `deps.loader_version` 拆键 + guardLint 恒假常量规则 ✅ 2026-08-30
- [x] T1 档 1 重文件接缝化（首批 top 5）：`PostEffectManager` 整文件拆分（案例：几乎无共享逻辑）；`NekoJSNetwork`→`McPlatformCompat` facade（新增，3 版本实现 + services）；`NekoJSNetwork` 屏幕守卫并入既有 `McClientCompat`（新增 `dashboardLoadServerScript`）；`NekoJSCorePlugin` 守卫审计（排版假差异按 26.x 基准解析，15→8）；`RecipeFilter`→`RecipeEventJS` 中立静态助手（`recipeHolderId`/`recipeGroup`/`ingredientMatches`/双参 `getRecipeOutputId`）；`NeoForgeRegistryQueryService` 文件级启用 `//~ mc_legacy_api` + 中立 `tryParse`（16→2）。守卫块 624→557 ✅ 2026-08-30
- [x] T2 节点构建收拢：buildSrc convention plugin（nekojs.neoforge-node / fabric-node / forge-node，入口 331/190/142 行 → 各 5 行；`stonecutter.process` 反射桥、loom-back-compat 体内 apply 的 spike 通过；五节点 jar 验证）✅ 2026-08-30
- [ ] T1 档 2 长尾消化：**两批已落地（档 1 首批 top5 + 22 个 ≥8 文件 + 23 个 ≥3 版本守卫文件）**，`tools/extract_evaluated.py` 流水线复用。opener：grep 口径 624 → **321 → 265**（版本轴 204 → **101**）；guardLint 口径 557 → **252**。**剩余 ~70 文件（2 守卫 ×17 + 1 守卫 ×55，共 ~89 版本 opener）为逐文件判断层**——按 ADR-0008 修订版分流（facade/版本中立助手/假守卫解析/`//~` 局部启用/豁免），不适合整文件拆分流水线（共享逻辑占比高，拆分会双份化）。api/inject 包已处置（4 文件：2 净化/2 保留带因）。**判断层两条新教训**：① 尾层里混有"已是拆分对"的文件（如 NekoJSCommands——其 2 条"守卫"是拆分自身的 neoforge+>=26 包装，终态豁免，处置前先查 1.21.1 节点目录有无同名孪生）；② facade 复用须核对加载器面——McPlatformCompat 是 neoforge 整文件包装，fabric 也编译的类（如 PlayerExtension）不能引用，此类 OP 检查保留原 >=26 守卫（vanilla Commands 类，四节点安全）。已知豁免与特例：`NeoForgeRegistryQueryService` 的 2 条守卫是真 API 改名（get/getTag、lookup/registry，刻意保留）；`NekoJSCorePlugin` 的 7 条是真差异（KeyBindEvents/TriState/Assets/IdentifierAdapter，facade 化候选）；`HolderAdapter`/`ResourceLocationAdapter`/`ClientEvents`/`RegistryEventAdapter` 有未提交 WIP，落地后再批。**GoalRegistry 教训**：单条 neoforge 线不一定是整文件包装（本例为局部守卫，类本体 fabric 也编译），流水线前须核对守卫位置。
- [x] T1 档 3 ADR-0008 修订成文 ✅ 2026-08-30（随档 1 首批落地）
- [x] T4 工作流与文档收尾：`switchVersion` 任务（往返实测）+ wiki/构建系统.md 整页重写为现行架构 + README 悬空链接修复 ✅ 2026-08-30（CI 节点列表维持手写，Q4 裁定）
- [ ] 装机（零成本）：IDE 安装 Stonecutter Dev 插件

## 裁定记录

2026-08-30 grilling（Q1–Q5，用户"全按推荐"）：

- **Q1** 1.21.1 保持一等支持：新功能继续双实现，接缝化照计划执行。若未来转为功能冻结，档 2 与批量接缝化可缩水（届时修订本节）。
- **Q2** 顺序痛点优先：T3 → T1 档 1 首批 → T2 → T1 档 2/3 → T4（T1 档 1 与 T2 无依赖，提前以先兑现守卫观感）。
- **Q3** 完成定义取硬指标（见 Implementation Decisions 末条）。
- **Q4** 砍掉 CI 矩阵自动生成：4–5 个节点规模下节点列表手写一行不痛，生成机制复杂度大于收益。
- **Q5** 档 1 判据改为"版本 facade 优先、按共享占比分流"；整文件拆分仅用于几乎无共享逻辑的文件。动机：stonecutter 已消除"共享逻辑写两遍"（replacements + 单副本共享树），残余 624 条守卫是**逻辑真差异**（必然写两遍，方案只决定它住哪）；整文件拆分会让共享逻辑也变双份，facade 恰好消掉这一部分。

## Problem Statement

NekoJS 是多 MC 版本 × 多加载器的单仓项目（版本树：1.21.1 / 26.1.2 / 26.2.0 / 26.1.2-fabric，forge/1.20.1 独立分支）。贡献者的日常开发体验被四件事拖累：

1. **守卫住在业务代码里**：共享版本树 288 个 Java 文件中 204 个带 `//?` 守卫、共 624 条 opener。16 个重文件（≥10 条）占约 1/3，1.21.1 侧代码以 `/* ... *///?}` **注释死代码**形态住在 26.x 文件里——IDE 灰显、不能跳转、不能重构；另有 118 个文件只有 1–2 条守卫，是日常"写起来难受"的长尾。守卫读起来像 C 预处理器。
2. **节点构建逻辑是一堆复制**：5 个 gradle 脚本共 945 行，根脚本 331 行每节点整脚本重求值；fat-jar 装配等同构块在 neo/fabric 两脚本逐字复制 ×2；3 个 Gradle 求值顺序陷阱只活在注释里，改节点配置就可能再踩；加一个新 MC 版本要人工同步 settings、CI、versions/ 三处。
3. **加载器轴有静默失效隐患**：守卫常量读 `deps.loader`，但 fabric 节点该键的值是 loader 版本号（`0.19.3`）而非平台 token——三个平台常量在该节点全不命中。现在共享树恰好 0 条 fabric 守卫所以无症状，但按现注释指引新写的 `if fabric` 守卫会**静默失效**；共享测试树整树被 neoforge 守卫成空文件，fabric 节点 0 测试。
4. **工作流与文档缺位**：切换 active 节点靠手改控制器脚本；wiki 构建页整页描述已退役的 platforms/ 形态；README 指向本仓不存在的文档；"加版本的唯一真实文档"藏在 CI 注释里。

## Solution

一个**不替换 stonecutter**（已评估并否决，理由存档于 Further Notes）的四轨道计划，目标：业务代码零守卫、节点构建单副本、加载器轴一等语义、文档即事实。

- **T1 守卫退场**（三档，可分批合并）：
  - 档 1：**重文件接缝化**——按共享占比分流：共享逻辑占比高的文件抽版本 port/facade（主文件单副本、零守卫；两个时代的实现各自纯净，且只承载真差异的部分）；几乎无共享逻辑的文件才整文件拆分（P3 旧法：共享树纯 26.x + `versions/1.21.1/src` 纯 1.21.1）。1.21.1 保持一等支持（裁定 Q1）。候选 = 16 个 ≥10 opener 的文件，先做最高 5 个验证，再批量。
  - 档 2：**长尾逐类消化**——纯改名/形状差 → replacements 扩容（构建期替换，源码零痕迹）；逻辑差 → 抽小版本 facade（普通 Java 类，facade 文件内 ≤1 条守卫），业务文件归零。守卫的丑不被消灭，被集中。
  - 档 3：**纪律翻转成文**——ADR-0008 修订：26.x 基准从 GUI 推广到全树；facade 触发条件从"≥3 文件重复"改为"守卫出现即考虑"；新增判据"版本 facade 优先于整文件拆分"。
  - 进度仪表：guardLint 现成的"守卫块 N"输出（基线 624），每个 PR 可见下降；完成定义见 Implementation Decisions 末条（硬指标，裁定 Q3）。
- **T2 节点构建收拢**：buildSrc convention plugin 承载"节点如何构建"（平台适配、fat-jar、元数据展开、验证门禁、求值陷阱），每个节点脚本入口瘦身为 1 行；跨平台同构逻辑单副本；加新版本 = 一份 gradle.properties + 版本图一行。
- **T3 加载器轴正名**：`deps.loader` 拆成 `deps.platform`（守卫常量）与 `deps.loader_version`（依赖坐标）两个键；guardLint 新增"恒假常量"规则——守卫用到的常量必须至少在一个节点取值为真，否则 hard fail。
- **T4 工作流与文档收尾**：`switchVersion` 小任务（改 active 行 + 提示 IDE 重新 sync）；wiki 构建页与 README 按现状 + CI 重写；CI 节点列表**维持手写**（裁定 Q4），"加版本三步"文档注明同步位置。

顺序与依赖（裁定 Q2，痛点优先）：**T3（几行改动）→ T1 档 1 首批 → T2（先迁 NeoForge 平台，再 fabric/forge）→ T1 档 2 → T1 档 3 → T4 随 T2 收尾**。每轨道独立可合并，`sandboxCheck` 全绿为合并前置。零成本装机动作（不占轨道）：IDE 安装官方 Stonecutter Dev 插件（守卫高亮/错误检查/补全）。

## User Stories

1. 作为贡献者，我想在业务文件里写纯 26.x Java 而不遇到守卫，以便专注逻辑而不是版本分叉。
2. 作为贡献者，我想让 1.21.1 的实现在节点目录里是可编译、可跳转、可重构的真代码，以便不再读注释死代码。
3. 作为贡献者，当同一版本差异出现在多个文件时，我想只改一个版本 facade，以便改动集中且两侧一致。
4. 作为贡献者，我想让纯改名类差异由构建期 replacements 处理，以便源码里完全看不到它们。
5. 作为贡献者，我想看到 guardLint 的守卫块计数随每个 PR 下降，以便度量守卫退场的进度。
6. 作为贡献者，我想让 fat-jar、编译与测试约定改一处即作用于全部节点，以便不再双份维护。
7. 作为贡献者，我想让新 MC 版本的接入只需一份 gradle.properties + 版本图一行，以便三处人工同步退役。
8. 作为贡献者，我想让 Gradle 求值顺序陷阱封死在插件内部，以便改节点配置不可能再踩坑。
9. 作为贡献者，我想让两个时代共享的逻辑始终只有一份实现，以便不为重复劳动同步两处（facade 的核心价值）。
10. 作为贡献者，我想用一条命令切换 active 节点，以便不手改控制器脚本、IDE 立即给出同步提示。
11. 作为新加入的贡献者，我想读到与现状一致的构建文档（版本图、守卫模型、加版本步骤），以便不用考古 CI 注释和失效命令。
12. 作为 AI 代理贡献者，我想让加载器差异有一等常量语义，以便写 fabric 守卫不会静默失效。
13. 作为贡献者，我想让"对所有节点恒假的守卫常量"直接让 lint 失败，以便整类静默分支在合并前被拦截。
14. 作为贡献者，我想保留 sandboxCheck 全节点验证作为所有轨道的总门面，以便任何构建/守卫改动都有行为保持证据。
15. 作为贡献者，我想让重文件接缝化（facade 化或整文件拆分）不改变行为——拆分以节点编译产物逐字一致验证、facade 化以现有测试护航，以便重构零回归。
16. 作为贡献者，我想在 IDE 里看到守卫指令高亮与条件错误，以便守卫写坏时立即可见。
17. 作为贡献者，我想让 ADR-0008 的修订记录新纪律，以便未来架构评审不再把内联守卫当默认写法。
18. 作为贡献者，我想让每条 replacements 转正规则都附归一化等价性证据，以便改名表不变成隐式行为变化。

## Implementation Decisions

- **不替换 stonecutter**（原 C4 候选，否决存档）：评估过 ReplayMod preprocessor（+ 源级 remap）、Polyfrost toolkit（绑 architectury-loom 栈）、自研 Gradle 插件。语法迁移要动 624 个 opener 点并重验 P0–P4 全部治理成果，换来的只是 `//?` → `//#if` 的长相和一套新 DSL 陷阱；自研等于长期养一个预处理器。守卫语法不变；本计划解决的是守卫的**数量与位置**，不是字形。
- **T1 档 1（重文件接缝化，裁定 Q5）**：逐文件判"共享占比"。共享逻辑显著 → 抽版本 port/facade：主类单副本、零守卫；port 接口签名必须**时代中立**（只引用两个时代共有的类型，如 `Identifier`），做不到中立的差异不适用 facade，留在守卫或时代专属文件；26.x 实现住共享树（整文件 `//? if >=26` 守卫包裹的纯 26.x 文件，1 条 opener），1.21.1 实现住节点目录（纯 1.21.1、零守卫）；26.x 独有功能不为 1.21.1 写空壳。几乎无共享逻辑 → 整文件拆分（P3 规程：从生成产物提取已求值版本，共享树侧整文件守卫）。facade 消除的是"共享逻辑双份"（重复劳动）；差异实现的双份是真差异，属双版本支持固有成本。首批按守卫密度取 top 5，并刻意覆盖两种判据分支（facade 案例为主 + 拆分案例 1–2 个），验证接口设计成本后再批量。
- **1.21.1 地位（裁定 Q1）**：保持一等支持——新功能继续双实现；接缝化后的 1.21.1 实现文件是活跃维护面，不是冻结存档。
- **T1 档 2（长尾消化）**：两类处置——纯改名/机械形状差走 replacements，门槛沿用 `!mc_ids` 的既有判据（token 在某一侧完全不出现，全局替换不可能误伤）；逻辑差走小版本 facade，落在 wrapper 层版本接缝处，facade 文件内 ≤1 条守卫，不引入注解、APT、ServiceLoader 等新机器（遵守 ADR-0008"不引入新框架"约束）。
- **T1 档 3（ADR-0008 修订）**：三点——① 规则 2 的"26.x 现代基准"从 GUI 推广到全树（新代码默认零守卫写法）；② 规则 3 的 facade 触发条件从"同一差异主题 ≥3 文件重复"改为"守卫出现即考虑 facade"；③ 新增判据"版本 facade 优先于整文件拆分，后者仅用于几乎无共享逻辑的文件"。豁免机制保留。修订应与档 1 首批同 PR 提交，延续"纪律先行"的既有风格（P0 先例）。
- **T2（convention plugin）**：buildSrc 内 precompiled script plugin，按平台分（neoforge 节点 / fabric 节点）+ 跨平台共享件；节点入口脚本只剩 plugins 声明，节点可变项全部来自节点 gradle.properties。三个已知求值顺序陷阱（跨项目求值依赖、预处理产物对 MDG 的隐式依赖、fat-jar 执行期求值）封装进插件，节点配置层面不再可触发。**Spike 项**：fabric 侧 loom-back-compat 从控制器脚本读 Loom 版本的解析链在 buildSrc 形态下需验证；备选是 fabric 插件内直接声明 Loom 版本。guardLint 与 sandboxCheck 留在控制器脚本，原样不动。
- **T3（拆键 + lint 规则）**：`deps.platform`（守卫常量，取值 neoforge/fabric/forge）与 `deps.loader_version`（依赖坐标）分离；常量声明改读 platform 键。guardLint 新增规则：源码守卫引用的每个常量必须至少在一个节点取值为真，否则 hard fail（防止"永不激活"分支）。
- **T4（工作流，裁定 Q4）**：switchVersion 任务接受节点名参数，改 active 行并提示 IDE 重新 sync；构建文档按"版本图 / 守卫模型 / 加版本三步（properties、settings、CI 各一行）"重写，README 构建入口指向新文档；CI 节点列表维持手写，不做自动生成。
- **完成定义（硬指标，裁定 Q3）**：① 业务包（bindings、client、wrapper 消费侧、脚本 API 装配）零守卫；② 全树 opener ≤ 200；③ 豁免仅限 facade 与平台面文件（ADR-0008 豁免机制）；④ 度量口径唯一——guardLint 的"守卫块 N"（基线 624）。
- **基线数据**（进度度量锚点）：288 文件 / 204 带守卫 / 624 opener（>=26 434、neoforge 133、<26 52、复合 5）；118 文件 1–2 条、24 文件 3–5、19 文件 6–9、16 文件 ≥10；gradle 脚本 945 行。

## Testing Decisions

- **只测外显行为**：守卫退场每一步的验收面 = guardLint 计数变化 + sandboxCheck 全节点 check 全绿；不为拆分/facade 化新增实现细节断言。
- **接缝化 = 行为保持**：整文件拆分以节点编译产物与拆分前逐字一致验证（P3 既定标准）；facade 化以现有 golden 测试 + nbtSmokeTest 护航（prior art：P3 对 GUI 三屏 / mixin / NBT codec 等豁免文件的处置、现有 golden 覆盖）。
- **convention plugin（唯一新 seam）**：Gradle TestKit 测"节点属性 → 任务配置"映射，断言外显结果而非内部结构，例如：modern 节点挂 era 资源层、data run 走 clientData 变体、fat-jar 内嵌引擎模块产物且排除 Graal。新 seam 保持小而少（理想数量 1），不复制 guardLint 的职责。
- **恒假常量规则**：以一次性 fixture 样本验证失败路径（已知恒假样本必须让 guardLint 红），验证后进 CI 常态化。
- **replacements 转正**：每条新规则附"归一化后逐字节等价"证据；若等价性校验工具不在本仓，先重建该小脚本再转正——字节码等价是改名表的安全边界。

## Out of Scope

- 替换 stonecutter 或更换守卫语法（否决理由存档，见 Implementation Decisions 首条）。
- CI 节点列表自动生成（裁定 Q4：YAGNI，4–5 个节点手写一行即可，机制复杂度大于收益）。
- 加载器轴 133 条 `neoforge` 整文件守卫的重排——平台面形态归 ADR-0004/0007，随 LoaderBridge / fabric 移植演进，本计划不动。
- Forge 1.20.1 端口（明确排到 B4 之后）。
- guardLint 既有七条规则的重写（原样保留，仅新增恒假常量一条）。
- 节点目录漂移检测工具化（先靠 review + 拆分产物对比；出现真实漂移案例再议）。
- 构建系统之外的其余文档工作（仅重写构建系统相关页面与 README 构建入口）。

## Further Notes

- 推导依据是一次全仓只读普查（守卫分布、脚本行数、节点构成、CI 工作流、文档状态）加生态调研（Stonecutter 为多版本事实标准；ReplayMod preprocessor / Polyfrost toolkit / 自研为被否决的替代）。详细报告在评审会话的临时目录，不落入仓库；关键数字已摘录进本计划。
- 守卫退场的成本结构（grilling 中确认）：stonecutter 能消除的只有"机械改名与同构"（已消，991 → 624）；**逻辑真差异的双份是双版本支持的固有成本，任何方案只能决定它住哪**。本计划的答案：facade（共享逻辑单副本 + 差异各自纯净）> 内联守卫（差异挤在业务文件）> 整文件拆分（连共享逻辑也双份，仅限无共享逻辑的文件）。
- fabric 节点当前 0 测试（共享测试树整树被 neoforge 守卫成空文件）是 LoaderBridge 移植的既有排期问题，不属于本计划，但 T3 的常量正名是其前置。
- 历史脉络：守卫密度治理（P0–P4）已把 opener 从 991 压到 624、豁免清零；本计划是"治理密度"之后的下一阶段——治理**位置**（守卫退出业务代码）与治理**包装层**（构建收拢、加载器正名、工作流）。
- 维护约定（2026-08-30 用户裁定）：计划与 spec 一律落仓库本地文档，不再发 GitHub issue（GitHub issue 无法删除，首个误发的 #51 已关闭存证）。
