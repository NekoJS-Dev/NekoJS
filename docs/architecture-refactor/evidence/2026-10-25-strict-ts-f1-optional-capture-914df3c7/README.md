# F1 Constable→Optional 通配族修复（914df3c7）

本页绑定 `914df3c7`（fix: render capture-safe views for wildcard Optional output sites），
[根因表](../../../../research/2026-10-25-strict-ts-root-causes.md) F1 族的先红后绿实现与十入口实测增减。
对照基线为 [c3ee4097 十入口 1609](../2026-10-25-strict-ts-baseline-c3ee4097/README.md)。

## 机制

真实 Java 形态 `Optional<? extends Bound>`（如 `Constable.describeConstable(): Optional<? extends ConstantDesc>`）
此前被压平为不变泛型 `$Optional<$Bound>`，14 个实现类的窄返回全部 TS2416。修复在 canonical 渲染器
`TypeScriptClassRenderer.renderSlot` 的**输出位**（`!input && !slot.overridden && sourceType != null`，
编辑过的槽仍以 IR 为准、输入位 SAM 别名路径不变）按源形态触发内联捕获视图：

```ts
{ isPresent(): boolean; isEmpty(): boolean; get(): <Bound>;
  stream(): { count(): number; findAny(): { get(): <Bound>; ... }; findFirst(): { ... } } }
```

只暴露 Java 捕获语义下可命名的成员：上界读取、存在性检查、一层递归的 stream 终止投影
（内联类型无法自指故截断一层）；or/filter/map/orElse 等捕获变量入参成员无健全参数类型，
保留在具体实现视图上可用。错误元素仍被 `get(): <Bound>` 拒绝。

## 先红后绿与正负消费

- **红**（临时摘除钩子重跑）：Java 断言失败于 `interface return renders the capture view`（当时输出
  `describeConstable(): $Optional<$ProbeWildcardOptionalCaptureTest$ConstDesc>`），严格 tsc 8 错 exit2，
  含 fixture 自身 TS2416/TS2322、真实 `$Class/$Enum/$MethodHandle/$MethodType/$VarHandle` 对 `$Constable`
  的 TS2416（微型复现 F1）与 TS2578。
- **绿**：`ProbeWildcardOptionalCaptureTest` 通过；`tsc -p common/build/probe-ts-wildcard-optional` exit0
  （`npm run test:probe-wildcard-optional`，已入 `test:probe-types` 链）。正例：实现可赋给接口、接口结果
  保留上界（`get(): ConstDesc`）、窄成员不可达（负例）、`stream().findAny().get()` 读上界、截断层不可再
  入 `stream()`（负例）、具体视图保留窄结果、`or(good)/filter(good)` 合法 SAM 输入可用。负例：
  `or()` 拒绝不相关 Supplier 载荷（经 `$Optional` 不变链）、`filter()` 拒绝错误回调结果类型（可调用分支
  对宿主元素的有意宽容见 TypeAliasRegistry 契约，测试内注明）。
- 组门槛说明：fixture 闭包经 `Thread.sleep($Duration)`/`Date.from($Instant)` 触达 java.time，其 chrono
  族（F2，330 条）与本族无关且保留在十入口基线计数；故本组 tsconfig 以 paths 将 `java:java/time` 解析
  到仅含两个被引用名字的不透明名义类，不使用 any/空 stub/skipLibCheck。
- golden：`legacy-tree` 仅一行变化——`$Constable.describeConstable` 换为视图（`:common:regenerateGoldens`
  官方流程再生，git diff 人工审查）。

## 十入口实测增减（[ide-check-summary.json](ide-check-summary.json)）

十入口 1609 → **1503（−106）**，逐入口 −9/−11，**零新增、零其它族回归**：

| 族 | 基线 | 修复后 | Δ | 说明 |
|---|---:|---:|---:|---|
| F1 Constable/Optional | 140 | 50 | **−90** | 14 实现类中 9 个转绿（Boolean/Byte/Character/Class/Enum/Short/MethodHandle/MethodType/VarHandle） |
| F9 interface extends | 50 | 34 | **−16** | 连带清除：`$HolderLookup$Provider`/`$RegistryAccess` 各 8，实际叶因同为 Optional 通配（`RegistryOps.lookup` 两个重载，根因表 F9 归类据此更正） |
| 其余 13 族 | 1419 | 1419 | 0 | 无变化 |

真实声明树的全部差异（五节点并集，仅 5 文件、每处一行）：`java/lang/constant` 的 `$Constable`、
`net/minecraft/core` 的 `RegistryOps.lookup` ×2、`com/mojang/datafixers/functions` 的 `rewrite`、
1.21.1 `net/minecraft/core/component` 的 `DataComponentGetter.get`（上界为类型变量 `T` 的边界情形，视图
正确渲染 `get(): T`）。

## 可表达性边界（剩余 50 = 5×10）

`$Double/$Float/$Integer/$Long/$String` 的 `describeConstable` 仍 TS2416：真实返回 `Optional<Double>` 等
被值投影为 `$Optional<number>`/`$Optional<string>`，原始投影不结构满足 `$ConstantDesc` host 接口。
此前只读调查（reload-declaration-findings §Optional）已预告该五类边界；修复它们需要单独证明的值投影
变更，不属于本族本次范围，不以 any/联合放宽掩盖。

## 验证

- `:common:check`（含隔离检查与 2463 项测试，golden 再生后）BUILD SUCCESSFUL。
- `npm run test:probe-types` 全链 exit0（新增 `test:probe-wildcard-optional` 组）。
- 五节点构建 + [驱动](../../../../bench/tsbaseline/README.md) 隔离安装重采：probe 394/448/430/540/519 文件、
  exit 全 0、无强杀（[run-summary.json](run-summary.json)、[preparation-manifest-trimmed.json](preparation-manifest-trimmed.json)）。
- 完整严格 TS 仍 FAIL（1503），全部历史 FAIL 记录保留；本页不宣称全绿。
