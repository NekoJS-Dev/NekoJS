# 07: wrapper 零 loader import 清算 + wrapperLoaderImportHardFail 翻转

**What to build:** guardLint 报告的 13 文件 37 处 wrapper 层 loader import 分类清算：① lint 规则细化——整文件 neoforge 守卫的文件（FluidBuilder 等）不计入（import 在 fabric 编译单元根本不存在）；② `GoalRegistry` 的 EntityJoinLevelEvent 单点经 02 的 mixin 通道桥接；③ 配方簇（RecipeEventJS/FluidResolver/IngredientResolver 等）依 06 的中立化或标记平台面豁免；④ 能力系统簇（CapabilityRegistryEventJS 10 处）按平台面处置（挪 platform 包或永久豁免）。收尾翻转 `wrapperLoaderImportHardFail=true` 并保持全绿。

**Blocked by:** 02（GoalRegistry 桥接）、06（配方簇中立化）。

**Status:** done

- [x] lint 细化 + 分类处置后 wrapper 层 loader import 计数归零或全部为显式平台面豁免
      （未守卫 wrapper 文件 0 违规；GoalRegistry 已桥接中立；10 个整文件守卫文件列 informational）
- [x] `wrapperLoaderImportHardFail=true`，全树绿（四节点编译 + 节点 test + common test + guardLint）
- [x] CI 步骤无需改动即通过（`.github/workflows/ci-build.yml` 跑的是同一 `./gradlew guardLint`）

## 处置记录

- **GoalRegistry / NekoScriptMob 去整文件守卫下沉**（票 ② 的桥接）：GoalRegistry 唯一的
  loader 依赖是 `EntityJoinLevelEvent` 一个 import + 一个方法签名——改成中立
  `onEntityJoinLevel(Entity, Level)`；NeoForge 侧 `NekoJSMod` 解包原生事件，fabric 侧
  `FabricEntityEventBindings.postJoinLevel`（02 的 mixin 通道）追加调用。NekoScriptMob 本就
  纯 vanilla。fabric 冒烟：服务器 Done 零异常（出生点实体加载即大量走过新接线）。
- **Mob#goalSelector/targetSelector 是 protected**：NeoForge 侧靠 AT 放宽，fabric 用
  **Access Widener** 等价（`nekojs-fabric.accesswidener` + `loom.accessWidenerPath` +
  fabric.mod.json `accessWidener` 键），GoalRegistry 代码不动。26.x 无混淆 → AW namespace
  是 `official` 不是 `named`（loom 报错提示）。
- **GoalRegistry 对 EntityTypeBuilder 的单点引用**保留方法级 `//? if neoforge` 守卫：
  fabric 上脚本实体注册面未移植（EntityTypeBuilder 整文件守卫），“查不到脚本实体 → 未知目标”
  在 fabric 上语义正确。
- **配方簇 / 能力系统 / NetworkJS / FluidBuilder / LootTableEventJS**（10 文件 34 处）：
  整文件平台面豁免。配方簇的 fabric 移植是独立大票；capabilities 是 NeoForge 概念
  （fabric 是 Lookup 模型）；NetworkJS 的 C2S 通道 fabric 未接。
- **lint 细化语义**：违规判定 = wrapper 文件**最外层守卫**（文件里第一个 `//? if` 行）不含
  loader token 但出现 loader import。整文件平台面文件列 `[platform-facing]` informational
  （每次运行输出清单，落地一个移植件就去掉一个守卫）。

## 踩坑记录

- 判定"整文件守卫"不能只看守卫总数==1 或首行形态：`RecipeEventJS` 是复合条件
  （`neoforge && >=26`）、`FluidBuilder` 外层 loader 守卫前有 TODO 注释且内部还嵌着 7 个
  版本守卫。正确判定是"首个 `//? if` 行的条件含 loader token"（嵌套守卫必在内层）。
- 通过 bash heredoc 往 python 里塞正则时，`\\b` 被 JSON→bash 两层剥成真**退格字符**——
  sed/grep 输出完全看不见，正则永远不匹配，lint 全员误报。用 `chr(8)` 扫描定位后修掉。
- 去 GoalRegistry 尾部整文件守卫时误删了内层 `>=26` 守卫的闭合 `//?}`（guardLint 的
  配对检查当场抓住——这正是这条 lint 的价值）。
