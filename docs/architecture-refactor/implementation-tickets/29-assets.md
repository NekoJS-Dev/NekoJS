# 29: Assets/Lang 资源生成与回读收口

**What to build:** 脚本作者继续通过已有 Assets typed binding、唯一 ClientEvents.generateAssets 事件与既有 ClientEvents.lang 事件生成 blockstate、model、texture 和 lang 资源；生成器路径校验、既有资源写入行为、plugin generate-assets/generate-lang 与脚本贡献聚合、资源 pack reload 回读、client-only 过滤、声明与按节点 capability 由同一条 Adapter 路径验证，不合并两事件语义，不新增第二资源事件、直写旁路或新资源 policy。plugin-only lang 不由脚本 listener key 隐式门控：在既有 `NekoJSPlugin` 直调钩子模型上增加最小 default 声明 `generatedLangs()`（默认 `Set.of("en_us")`），平台先把插件声明语言与 `ClientEvents.LANG.registeredKeys()` 合成确定性的有限语言集合，再逐语言触发 plugin 与脚本；这不是第二注册框架或通用资源声明系统。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** agent 可以实现、测试并整理证据；涉及删除旧公开资源/诊断路径或最终 golden/发布确认的维护者 sign-off 不能由 agent 代答，未经 sign-off 不得删除旧路径或勾选对应删除验收项。

**Work items:**

- W7
- 定义并验证 plugin-only 语言声明：`generatedLangs()` 与脚本 keyed listener 语言取确定性行集；默认 `en_us` 保证没有脚本 listener 时 plugin hook 仍会触发，非法语言在写入前被归因拒绝。

## Acceptance criteria

- [ ] ClientEvents.generateAssets 仍是唯一 Assets 生成事件，ClientEvents.lang 仍是既有 lang 生成事件；二者、Assets/Lang typed binding 和 plugin contribution 复用同一资源根与安全写入基础，不新增第二事件、第二根目录、第二生成管线或新资源 policy，也不把 LANG 塞进 generateAssets 造成语义混淆。
- [ ] plugin generate-assets 与 generate-lang Hook 分别在同一 client generation 阶段与脚本事件聚合；`generatedLangs()` 是 plugin-only 语言的最小声明面，语言集合按可重复顺序取插件声明与 `ClientEvents.LANG.registeredKeys()` 的并集，plugin callback 不因没有同语言脚本 listener 而被静默跳过，非法声明在写入前带 plugin/owner 诊断失败，单个插件回调失败不污染其他贡献，阶段、顺序和错误隔离可观察。
- [ ] blockState、blockModel、itemModel、texture 等调用者成员的参数规范化、默认 namespace/path 补全、JSON 与占位 PNG 输出保留现有已验证行为并有确定 contract/golden 或回读 fixture；本票不为资源限额或 PNG 生成新造 policy。
- [ ] `generatedLangs()` 声明、lang code/path 校验、多脚本与 plugin key 合并、冲突策略、文件大小限制、非法语言代码、原子替换和 resource reload 回读保留或收紧现有已验证行为；语言集合先完整确定并校验，未通过不进入任何语言文件写入，无效输入不产生部分文件、不越过资源根，也不把异常路径写进资源 pack。
- [ ] 资源写入保留现有路径包含性、容量、非法 id、冲突 kind 和原子替换行为；无效输入不产生部分文件、不越过资源根，也不把异常路径写进资源 pack。
- [ ] plugin generate-assets Hook 与脚本事件在同一 client generation 聚合，事件按资源 reload 生命周期恰好触发一次；懒读或显式 reload 后资源能被实际 resource manager 回读。
- [ ] client-only 过滤证明 dedicated server 不注册入口、不加载 client 类、不写资源；脚本 side 与节点 capability 一致。
- [ ] 平台/版本 Adapter 按各节点既定支持等级、声明能力与现有限制执行资源 pack 注册、路径和 reload 验证；supported/partial/unavailable 由真实 source trace 与 smoke 决定，不自动补 Fabric parity。
- [ ] 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 与生成文件互相追溯；普通测试不得更新 golden 或资源基线，显式更新需旧新 diff 与审阅。
- [ ] EntitySelectors、DataMap、PostEffects 和已事件化 recipe/loot/tags/JEI/render 域不被并入本票；本票只证明不重复它们的 owner 或事件。
- [ ] 旧直接文件写入、绕过 DataGenerator 的 asset helper 或重复生成入口只能在生成/回读 parity、迁移表、旧 route 无消费者和维护者确认后删除；不保留长期兼容双路径。
- [ ] 随实现交付 Assets 与 Lang 的脚本/plugin 聚合、plugin-only 语言声明、路径校验、回读和不可用能力拒绝的最小可运行示例与必要迁移材料；迁移材料明确旧版依赖脚本 listener 解锁非 `en_us` plugin 语言的插件现在必须声明 `generatedLangs()`，且默认 `en_us` 无需声明，示例只使用已通过 gate 的能力。

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

## JSX UI feature coordination（2026-09-12）

[44: JSX 文本、视觉与资源](44-jsx-ui-text-visual-assets.md) 消费本票的资源根、安全策略和回读/reload 语义；UI 不建立第二资源根或独立 policy，本票不反向依赖 44。
