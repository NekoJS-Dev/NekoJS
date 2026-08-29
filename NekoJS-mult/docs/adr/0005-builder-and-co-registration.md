# Builder 体系与连带注册：public field + 回调式三件套

现状 15 个 `XxxBuilderJS` 全部是 `return this` 链式 setter（链式方法返回类型固定、子类无法表达 self type——PR #37 §3.1 痛点），EventJS 侧工厂方法命名分裂（9 个 `create()` / `createItem` / Fluid 5 工厂）；而 `BlockBuilderJS` 已自发长出 `noItem()` / `item(Consumer)` / 预创建 `itemBuilder` 的连带注册雏形（96/99/125 行）。R1（ADR-0004）已锁定生命周期三阶段与 after-all 框架，本 ADR 定 Builder 侧：

1. **public field 约定（全项目 Builder）**：数据属性 public field（脚本端 `builder.maxStackSize = 16` 直接赋值）；**禁止 `return this` 链式 setter**；动作/派生方法保留但返回 `void`（`noItem()`、`requiresTool()` 等"动词"）。
2. **per-type Builder 类保留为类型内容**：通用机制吸收全部管道（分发、注册、工厂调用）；构造统一经 `registry_types` EP 注册的工厂（`ItemBuilderJS::new`），工厂命名分裂消失；`TaggableBuilder` 等成为基类层级接口。**新增注册表类型 = 1 个 Builder 类 + 清单 1 行**（成功判据①兑现）。
3. **连带注册 = 回调式三件套**：`handleAdditionalObjects(AdditionalObjectRegistry)` 单方法回调，after-all 后置阶段统一执行（KubeJS `createAdditionalObjects` 验证多年的同款）；三件套 = 预创建子 builder（构造期即建）+ `noXxx()` 置 null 抑制 + `RegistryObjectBuilder implements Supplier<T>`（跨注册表互引全走 Supplier 懒解析）。
4. **默认连带规则随 builder 类（内容层自治）**：`BlockBuilderJS` 默认预创建 BlockItem 子 builder（现状延续）、`FluidBuilder` 四子 builder（fluidType/flowing/block/bucket）；第三方 builder 自治决定自己的默认；引擎只提供框架。

## Considered Options

- `additionalBuilders()` 直返集合（PR #37 方案一）：弃——拿不到"注册目标"抽象、无法条件派生（`noBucket()` 时不构造 bucket builder）。
- 引擎内置全局默认规则表（key → 派生描述）：弃——默认规则是内容不是框架，builder 类自持最可发现。

## Consequences

- 15 个存量 BuilderJS 需迁移（链式 setter → public field，机械改写）；EventJS 侧 13 分支 if-else 与工厂分裂随 ADR-0004 一并消失。
- **脚本端既有 `.hardness(2)` 链式写法受影响**——是否保留链式薄包装作为兼容层、或随迁移改写脚本，归 R3 裁定（脚本 API 稳定约束的适用点，R3 决定兼容映射的深度）。
- after-all 回调的执行时机与平台事件序的关系遵循 ADR-0004 决策 2（不做引擎内排序）。
