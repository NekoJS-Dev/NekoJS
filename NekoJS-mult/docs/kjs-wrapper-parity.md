# Spec: 转换层对齐 KubeJS wrapper 的三个缺口

Label: ready-for-agent
Stage: spec（未排期；排期时把阶段标记推进为 implementing / done，进度用 checkbox 记录在本文档内）

## Problem Statement

脚本作者从 KubeJS 迁移到 NekoJS 时会遇到三类"KJS 能写、NekoJS 写不出"的脚本：

1. 任何 mod 的注册表类型参数，KJS 里直接传字符串 ID 就能过（自动扫描全部 `ResourceKey<Registry<T>>`）；NekoJS 里只有手写了 adapter 的类型能过，没适配的 mod 类型直接报"Cannot convert"。
2. 类型之间的复用转换（如 `ItemLike` ↔ `ItemStack` 的 alias），KJS 一行注册；NekoJS 要么写完整 adapter，要么不支持。
3. Java record 参数，KJS 里 JS 对象字面量直接映射为 record 实例（还支持每字段默认值）；NekoJS/GraalJS 下对象字面量传不进 record 参数。

另外绑定层缺 6 个 KJS 已有的脚本全局：`KMath`/`JavaMath`、`DamageSource` 工厂、`ParticleOptions` 工厂、`DataMap` 遍历、`TextIcons`、`Registry` 的可迭代形态。

## Solution

在现有 adapter 体系上补三个机制，不新开层：

1. **自动注册表 adapter**：启动时扫描已加载类中的静态 `ResourceKey<Registry<T>>` / `Registry<T>` 字段，对每个"没有专属 adapter 的注册表类型"动态实例化现有的通用注册表查找 adapter 并注册。脚本侧效果与 KJS 一致：字符串 ID → 任意注册表对象，零 per-type 代码。
2. **alias 注册**：在 adapter 注册 API 上加"目标类型 X 复用类型 Y 的转换"的一行式声明，经 probe 时 alias 目标的输入形状同时放宽 X 的参数。
3. **record 输入形状**：为 Java record 目标类型提供"JS 对象字面量 → record 实例"的通用转换（按 record component 逐字段取值 + 每字段默认值声明）。

绑定层 6 个缺口按现有绑定/managed-facade 机制各补一个绑定，不引入新机制。

## User Stories

1. As a 脚本作者, I want 给任意 mod 的注册表类型参数直接传 `"modid:entry_id"` 字符串, so that 我不用等 mod 适配就能用第三方内容。
2. As a 脚本作者, I want 传了不存在的注册表 ID 时得到含注册表名与 ID 的明确报错, so that 我能自己定位拼写错误。
3. As a 脚本作者, I want 未注册任何 adapter 的类型在转换失败时报错信息里提示"该类型来自注册表 X", so that 我知道它应该走 ID 语法。
4. As a 插件作者, I want 一行声明"我的类型 F 复用类型 T 的输入形状", so that 我不必为实现类写转换代码。
5. As a 插件作者, I want 我的 codec 类型用一行注册（含默认值）, so that 简单数据类型不用手写 adapter。
6. As a 脚本作者, I want Java record 参数直接接受 JS 对象字面量, so that 调用 Java API 时不用逐个 new。
7. As a 脚本作者, I want record 字段缺省时使用声明的默认值, so that 对象字面量可以只写关心的字段。
8. As a 脚本作者, I want record 字段值本身也走类型转换（嵌套 ItemStack/ID 等）, so that 深层对象字面量也能整体传入。
9. As a 脚本作者, I want `KMath`/`JavaMath` 全局提供数学常量与向量工厂, so that 迁移 KJS 数学代码不用改写。
10. As a 脚本作者, I want `DamageSource` 工厂全局, so that 事件里能构造伤害来源。
11. As a 脚本作者, I want `ParticleOptions` 工厂全局, so that 粒子效果一行构造。
12. As a 脚本作者, I want 能遍历 NeoForge data-map 条目, so that 数据组件联动脚本可写。
13. As a 脚本作者, I want `TextIcons` 常量全局, so that 图标文本不用查 unicode。
14. As a 脚本作者, I want `Registry` 全局可 `for...of` 遍历, so that 批量枚举注册表内容的脚本与 KJS 写法一致。
15. As a 插件作者, I want 上述所有新输入形状自动进入 probe 生成的 `.d.ts`（生成 `$类型_` 别名并放宽参数）, so that TypeScript 补全与运行时行为一致。
16. As a 维护者, I want 自动注册表 adapter 遵守"专属 adapter 优先"的优先级, so that 现有手写 adapter（Item/Block/Ingredient 等）行为零变化。
17. As a 维护者, I want 新机制在 1.21.1 与 26.1.2 两个版本树下行为一致, so that 版本树不漂移。
18. As a 维护者, I want 绑定层新增全局走既有绑定扩展点而不是硬编码, so that 插件作者能用同一机制补自己的全局。

## Implementation Decisions

- 自动注册表 adapter 的引擎就是现有的通用注册表查找 adapter 基类：扫描器产出的每个 `(registry, targetClass)` 对生成一个实例，走现有注册管线，优先级低于所有手写 adapter（现优先级体系已支持）。
- 扫描器只做"发现 + 去重"，不做类型推导 magic；发现失败（类不可达、字段非静态）静默跳过并记 debug 日志，不阻断启动。
- alias 注册是注册 API 的声明式新入口，不新转换引擎；probe 侧由现有别名生成器消费（alias 声明即"目标 X 复用 Y 的形状"）。
- record 转换作为一个通用 adapter（目标类 `record` 判定），字段级取值复用现有转换管线以支持嵌套；每字段默认值是可选声明，默认无。
- 绑定层 6 个全局按既有绑定模式注册：有 helper 工厂需求的走委托绑定（helper + 委托原类 statics），纯常量/遍历需求走 managed facade 或直接类绑定；全部经绑定扩展点，由核心内置插件贡献。
- 遵守 ADR-0007 模块边界：扫描器与 record 转换的平台差异部分放平台层，纯逻辑（形状、优先级、报错格式）放 common；遵守 ADR-0008 守卫纪律同步版本树。
- 遵守 ADR-0010 双形态：若绑定注册需要新的插件钩子，必须与扩展点成对出现（配对测试约束）。
- 报错沿用现有统一转换异常格式（含目标类型、实际值形状、期望形状），不另起格式。
- 现有 adapter 与其测试行为零变化；`Fireworks` 之外不再手动补 codec 注册示例。

## Testing Decisions

- 好测试只测脚本可见行为："JS 值进 host 方法参数后得到什么 Java 对象 / 什么报错"，不测内部扫描顺序。
- 纯逻辑测试沿用现有接缝：裸 JUnit + Graal polyglot `Value`/`Context`，不启动游戏（现有 adapter 测试即此前例）。
- 自动注册表 adapter 的注册表依赖路径：用 `Bootstrap` 可用性探测（现 Vanillaregistry 探针模式）做 assume-skip 的注册表真值测试；扫描器本体用合成类（测试内声明的静态字段类）测发现与去重。
- alias 与 record 转换测试放 common 层接缝：合成 record/类型对，断言 test/apply 一致性与 probe 形状产出（现别名生成器测试即此前例）。
- 绑定层新全局：断言已知全局集合含新名字 + 现有 preflight 机制不误报；工厂行为的回归走对应 helper 的单测。
- 版本树一致性沿用现有平台漂移检查脚本，不新增检查。

## Out of Scope

- 不移植 KJS 的 `ItemWrapper` 解析细节差异（NBT 后缀字符串语法等）——现有 `3x` 前缀 + 对象字面量已覆盖主路径，另行评估。
- 不做 Rhino 式 `internalJsToJavaLast` 全局兜底钩子（Graal targetTypeMapping 按精确类匹配，泛型不可见是既有约束）。
- 不补 `TypeWrapperValidator` 软失败机制——现有 test/apply 一致性契约实质等价。
- 不动 KJS 侧 typings 生成（那是外部工具）；probe 现状即优势，不重构。
- 不新增 6 个全局之外的绑定面（`EntitySelectors`、client 绑定等已有）。

## Further Notes

- 背景：KJS 1.21 的 `wrapper` 包横跨绑定与转换两层；NekoJS 的对标物分别是绑定扩展点体系和 `type_adapter` → targetTypeMapping 管线。本 spec 只补转换层三缺口 + 绑定层 6 全局，其余已对齐（codec 一行注册已有、错误面已对齐、probe/typings 反超）。
- 用户裁定记录：spec 落本地 docs、不发 GitHub issue（issue-tracker 裁定沿用）。
