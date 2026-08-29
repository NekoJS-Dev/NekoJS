# 07: wrapper 零 loader import 清算 + wrapperLoaderImportHardFail 翻转

**What to build:** guardLint 报告的 13 文件 37 处 wrapper 层 loader import 分类清算：① lint 规则细化——整文件 neoforge 守卫的文件（FluidBuilder 等）不计入（import 在 fabric 编译单元根本不存在）；② `GoalRegistry` 的 EntityJoinLevelEvent 单点经 02 的 mixin 通道桥接；③ 配方簇（RecipeEventJS/FluidResolver/IngredientResolver 等）依 06 的中立化或标记平台面豁免；④ 能力系统簇（CapabilityRegistryEventJS 10 处）按平台面处置（挪 platform 包或永久豁免）。收尾翻转 `wrapperLoaderImportHardFail=true` 并保持全绿。

**Blocked by:** 02（GoalRegistry 桥接）、06（配方簇中立化）。

**Status:** ready-for-agent

- [ ] lint 细化 + 分类处置后 wrapper 层 loader import 计数归零或全部为显式平台面豁免
- [ ] `wrapperLoaderImportHardFail=true`，全树绿
- [ ] CI 步骤无需改动即通过
