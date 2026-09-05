# 票：kjs-wrapper-parity

状态：T1–T5 已实现（分支 feat/kjs-wrapper-parity，未 push）；spec 见 `docs/kjs-wrapper-parity.md`。任务图（无阻塞关系的票可并行）：

- T1 alias 注册机制 —— 无阻塞
- T2 record 通用转换 adapter —— 无阻塞
- T3 自动注册表 adapter 扫描器 —— 无阻塞
- T4 绑定层 6 全局 —— 无阻塞
- T5 合并 + spec 勾选 —— 阻塞于 T1–T4

T1–T4 各自在独立分支/工作树实现，汇入 PR 分支 `feat/kjs-wrapper-parity`。
共同的冲突点只有 `NekoJSCorePlugin` 的注册行，合并时人工解决。

---

## T1 — alias 注册机制（common + probe）

- [x] API：`JSTypeAdapterRegistry` 增加 alias 注册入口，语义 = "目标类型 X 复用来源类型 Y 的输入形状，值转换成功到 Y 后经 `Function<F,T>` 变成 X"（KJS `registerAlias(Class<T> target, TypeInfo from, Function<F,T>)` 的对标）
- [x] 实现：合成 adapter（内部持有 Y 的 adapter + 转换函数），优先级沿用来源 adapter 或显式指定
- [x] probe：`AdapterAliasGenerator` 让 `$X_` 别名复用 `$Y_` 的形状（不重复渲染）
- [x] 测试：common 层合成类型对，断言 test/apply 一致性 + probe 别名产出（沿 `AdapterAliasGeneratorTest` 接缝）

## T2 — record 通用转换 adapter

- [x] 通用逻辑（common，`api.data`）：给定 record 类，JS 对象字面量按 record component 名逐字段取值，字段值用 `Value.as(字段类型)` 递归走现有转换管线；字段缺失 → 用声明的默认值，无默认值 → `ValueConversionException`
- [x] 注册入口：`TypeAdapterDsl.registerRecord(registry, recordClass)`（+ 可选每字段默认值参数）
- [x] probe：record adapter 自动产出 `AdapterInputShape.object(...)` 形状（字段名 → 字段类型形状）
- [x] 报错沿用 `ValueConversionException` 格式，字段级错误带字段名
- [x] 测试：common 层合成 record（含嵌套字段、缺字段、默认值路径）

注意：Graal targetTypeMapping 按精确类匹配，所以"通用"指逻辑通用（一个工厂/基类），注册仍是每 record 一行。

## T3 — 自动注册表 adapter 扫描器（platform）

- [x] 启动时扫描已知 holder 类的 `public static final ResourceKey<? extends Registry<T>>` / `Registry<T>` 字段（vanilla `Registries`/`BuiltInRegistries`、NeoForge 注册表类；插件可贡献额外类）
- [x] 对每个"没有专属 adapter 的注册表类型"动态实例化 `SimpleRegistryBasedAdapter` 同款逻辑并注册，优先级 LOWEST（手写 adapter 零行为变化）
- [x] 转换期查表：RegistryAccess 懒初始化（HolderAdapter 模式），裸 JVM 单测不炸
- [x] 失败静默跳过 + debug 日志，不阻断启动
- [x] 未适配类型转换失败时，报错信息提示"该类型属于注册表 X"（若扫描器认识它）
- [x] 测试：合成静态字段类测发现/去重；注册表真值路径沿 `VanillaRegistryProbe` assume-skip
- [x] 1.21.1 / 26.x 类名差异（`ResourceLocation`/`Identifier` 等）用守卫表达

## T4 — 绑定层 6 全局（platform，走 BindingsPoint）

- [x] `KMath`：数学常量 + BlockPos/Vec3 工厂（新 helper 类绑定）
- [x] `JavaMath`：直接绑定 `java.lang.Math`（KJS 同款一行）
- [x] `DamageSource` 工厂：从 `DamageType`/ResourceKey + Level 构造（委托绑定模式）
- [x] `ParticleOptions` 工厂：ID + options → ParticleOptions
- [x] `TextIcons`：unicode 常量接口绑定
- [x] `DataMap`：NeoForge data map 遍历/查询（`//? if neoforge` 守卫；fabric 节点不注册该名字）
- [x] `Registry` facade 可迭代：`for (x of Registry.of("..."))` 形态（managed facade 侧）
- [x] 每个全局：断言已知全局集合含新名字（preflight 不误报）+ 工厂行为单测

## T5 — 合并与收尾

- [x] T1–T4 合入 `feat/kjs-wrapper-parity`（冲突点：`NekoJSCorePlugin` 注册行）
- [x] spec `docs/kjs-wrapper-parity.md` 勾选、阶段推进
- [x] code review 修复
