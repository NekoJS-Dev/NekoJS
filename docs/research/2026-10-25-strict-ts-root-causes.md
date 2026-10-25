# 完整严格 TS 根因分类表（基线 1609）

绑定 [c3ee4097 五节点隔离安装基线](../architecture-refactor/evidence/2026-10-25-strict-ts-baseline-c3ee4097/README.md)：
十入口 1609 条、逐入口与错误码分布同 v3 修正值、五节点声明树与 3ccea55a 逐字节一致。分类输入为
`build/tsbaseline-c3ee4097/ide/*-tsc.txt` 完整诊断；"真实签名"列经 patched server jar `javap` 或 JDK
文档核对，非推断。TS2416/TS2430 顶层错误按 elaboration 链追到叶子后归入对应根因族。

## 总表（族 × 计数 × 节点聚类 × 叶因）

节点列 = 该族在五节点 server 入口的出现量（startup 通常 +1，为脚本目录自生噪声）。
**2026-10-25 复核修正**：全部计数改为 [`classify_diagnostics.py`](../../bench/tsbaseline/classify_diagnostics.py)
对 1609 条原始诊断的互斥归类结果（逐条映射见
[JSON](2026-10-25-strict-ts-root-cause-mapping-c3ee4097.json)），总和精确闭合为 1609。
原表逐项相加为 1613，差源有三：F6 的 `+16` 与 F9 对 `CraftingRecipe/SmithingRecipe`
TS2430 的重复计数（现按叶因统一归 F6）；F9 其余行按叶因解散（`EnchantmentEntityEffect`
codec 叶归 F5、`HolderLookup$Provider`/`$RegistryAccess` Optional 通配叶归 F1——即下文
F1 实施结果的第 1 条更正、`SectionPos↔Vec3i` 归 F13），F9 不再作为独立互斥族；
F1 的 `28×5` 混用了双入口合计（现全部按 server 入口列示）。各族的码列也随之合并为
该族实际包含的全部错误码。

| 族 | 码 | 总数 | 节点分布（server 入口） | 来源 Java 关系 | 叶因机制 |
|---|---|---:|---|---|---|
| F1 Constable→Optional | TS2416+TS2430 | 156 | 1.21.1=14，26.x=16 | `Constable.describeConstable(): Optional<? extends ConstantDesc>`；14 个实现类返回各自 `Optional<具体Desc>`；另 `HolderLookup$Provider`/`RegistryAccess` 的 TS2430 叶因为 `RegistryOps.lookup` 两个 `Optional<? extends …>` 重载 | 通配符上界压平成 `$Optional<$ConstantDesc>`；`$Optional<T>` 不变泛型 → 实现返回不可赋给接口 |
| F2 java.time chrono 互环 | TS2416+TS2344 | 330 | 33×5 均匀 | `Chronology.dateNow(): ChronoLocalDate` ↔ `ChronoLocalDate.getChronology(): Chronology`；`IsoChronology`/`LocalDate`/`Period` 协变覆写 | `$LocalDate`↔`$ChronoLocalDate`、`$IsoChronology`↔`$Chronology` 互环协变覆写 + 重载集合两两比较噪声（`$ZoneId` vs `$Clock` 是比较器伪象） |
| F3 实体不变容器/重载序 | TS2416 | 52 | 1.21.1=2，26.x=6 | `EntityGetter.players(): List<? extends Player>` vs `ServerLevel.players(): List<ServerPlayer>`；`ServerPlayer.teleport` 3 参 void + 1 参返回覆写 `$Player.teleport`；`ServerPlayer.level` | List 压平同 F1；teleport/level 另含重载集合 arity 混排（"Expected 3 or more, but got 1"） |
| F4 fabric 泛型方法投影不一致 | TS2416 | 80 | 仅 fabric 20+20 | fabric `AttachmentTarget.<A>getAttached(AttachmentType<A>): A` vs `Entity`/`BlockEntity`/`ChunkAccess`/`Level`/`ServerLevel`/`LevelChunk` 实现渲染为 `unknown` 返回 | 同一泛型方法：接口侧保留 `<A>…: A`，类侧擦成 `unknown`，两侧投影不一致 |
| F5 codec/getType 不变通配 | TS2416+TS2344+TS2430 | 160 | 16×5 均匀 | `PositionSource.getType(): PositionSourceType<? extends PositionSource>`（javap 核对）、`EnchantmentLocationBasedEffect.codec()`、`LevelBasedValue` codec、`PoolAliasBinding` codec 族 | 压平后 `$PositionSourceType<$PositionSource>` 不变，经 `codec().flatComapMap` 的 `$Function_` 逆变显形 |
| F6 Recipe 约束不变 | TS2344+TS2416+TS2430 | 488 | 1.21.1 无（API 不存在），26.x=60-62 | `SelectableRecipe$SingleInputSet<T extends Recipe<?>>`（javap 核对）+ `SingleItemRecipe implements Recipe<SingleRecipeInput>`；`getSerializer/serializer` 覆写与 `CraftingRecipe`/`SmithingRecipe` TS2430 同叶 | 约束 `Recipe<?>` 压成 `$Recipe<any>`，而 `$Recipe.getSerializer(): $RecipeSerializer<$Recipe<T>>` 不变自指 → `$StonecutterRecipe` 不满足 `$Recipe<any>` |
| F7 ConsumeEffect 约束 | TS2344+TS2416 | 160 | 26.x=20×4 | `ConsumeEffect.getType(): ConsumeEffectType<?>`，实现返回 `ConsumeEffectType<自身>`（含 40 条 `getType` TS2416，同叶） | 同 F5 机制（codec flatComapMap 逆变叶） |
| F8 Entity/EntityAccess Stream | TS2344+TS2416 | 32 | neoforge 4×3，fabric 2×2 | `Entity.getSelfAndPassengers(): Stream<Entity>` 覆写 `EntityAccess` 的 `Stream<? extends EntityAccess>`（含反向 `getPassengersAndSelf` 与 `$Entity` 约束） | elaboration 实测链条 `$Stream<$Entity>`→`findAny().or`→`$Supplier_<$Optional<…>>`→`get().filter`→`$Predicate_` 参数逆变拒绝 |
| F10 客户端类省略残留引用 | TS2724/TS2552/TS2304 | 46 | 仅 1.21.1=23 | NEKO-4029 在 DEDICATED_SERVER 省略 `$ClientGamePacketListener`/`$ClientCommonPacketListener`/`$RecipeBookMenu`，但其它声明仍引用 | 省略类与依赖闭包的边界不一致（连接 2026-10-24-unavailable-declaration-boundaries 的既有边界） |
| F11 nekojs core 模块缺失 | TS2307 | 31 | 全节点 2-3 | `java:com/tkisor/nekojs/core/villager`、`core/modification`、`@side-only/server/events/recipes` 模块未被生成 | 声明引用了未生成的自有模块（闭包/可达性边界待查） |
| F12 neoforge 客户端扩展模块缺失 | TS2307 | 8 | 仅 1.21.1=4 | `java:net/neoforged/neoforge/client/extensions/common` | 同 F10 边界 |
| F13 取器/方法同名冲突 | TS2426+TS2416+TS2430 | 36 | 1.21.1=8，26.x=1-4 | 基类 bean 取器 `get level` vs 子类方法 `level()`（BlockEntity/ShelfBlockEntity、Vec3i/SectionPos x/y/z/offset 及其 TS2430） | 同一 Java 成员名在两级分别投影为取器与方法，TS 同名空间冲突 |
| F14 fabric NekoEntityPData 缺成员 | TS2420 | 4 | 仅 fabric 26.x=1+1 | 接口要求 `neko$getPDataRoot`，fabric `$Entity` 未含 | 接口注入成员在 fabric 渲染路径缺失（连接 prior 轮 fabric_enumCycle 同域） |
| 尾项 | 各 | 26 | 分散 | `$BlastFurnaceMenu` 等 4×TS2344（1.21.1 容器菜单）、`getOrCreate`/`getOrThrow`/`leashHolder`/`fabric_enumCycle`/`get` 各 2-4 | 多为上述族的孤立实例，修复验证时逐条归并（分类器按已知签名显式枚举；未匹配任何规则的诊断落入 UNMAPPED 并使分类器非零退出，不静默吸收） |

## 叶因归纳（共享机制层）

1. **通配符上界压平 × 不变泛型容器**（F1/F3部分/F5/F6/F7/F8，合计 1048）：真实签名
   `Container<? extends Bound>` 被 `TypeReflector.toRef`（ir/TypeReflector.java:626）与
   `JavaTypeScriptProjection.project`（backend/typescript/JavaTypeScriptProjection.java:24）压成
   `$Container<$Bound>`；TS 泛型无使用位置型变，`$Optional/$List/$Stream/$RecipeSerializer/$Codec` 均按
   结构测得不变，协变覆写在 TS 侧必然失败。诊断经 or/Supplier、filter/Predicate、flatComapMap/Function
   的参数逆变显形，属同一根因的不同 SAM 出口。
2. **投影一致性缺陷**（F4，80）：泛型方法 `<A>…: A` 接口侧/类侧投影路径不同。
3. **bean 投影形状不一致**（F13，16）：同名成员在继承两级产生取器 vs 方法两种形状。
4. **省略类/未生成模块的引用残留**（F10/F11/F12，85）：依赖闭包与省略边界不同步。
5. **重载集合混排**（F3 的 teleport/level 部分）：覆写重载集与基类重载集 arity 交错，TS 两两比较失败。

## 第一修复族（§3-4）：F1 Constable→Optional

选择依据：纯 JDK 类型（无 Minecraft 依赖，fixture 最小）、五节点均匀 28、诊断链已被既有只读调查
（2026-10-23 reload-declaration-findings §Optional 捕获与协变）完整刻画：九个 `Optional.or`/Supplier
出口（Boolean/Byte/Character/Class/Enum/Short/MethodHandle/MethodType/VarHandle）+五个
`Optional.filter`/Predicate 出口（Double/Float/Integer/Long/String，后五者为 number/string 与
ConstantDesc host interface 的投影差异，需单独核对）。

约束（沿用已批准边界）：不改共享 IR 语义；未经正负证明不引入全局 `out T`/any/never/成员删除；
已编辑 TypeSlot 以 IR 为准不从反射恢复；`$Optional.stream(): $Stream<CAP>` 的递归捕获与 SAM 输入
方向（`$Constable_<CallbackResult>`）必须保持可拒绝错误 Supplier/Predicate。

## 后续族的顺序建议

F4（fabric 泛型方法投影一致性，80 条，独立机制）→ F5+F7（codec/getType，与 F1 同表示可复用）→
F6 Recipe（约束位通配，量大但同机制）→ F2（互环，需单独的循环协变表示）→ F13/F9/F10-F12/F14
（独立小机制）。每族独立先红后绿 + 正负消费，不宣称一次修复多族。

## 2026-10-25 F1 实施结果（914df3c7）

F1 已按上述约束实施：canonical 渲染器输出位捕获视图，先红后绿与正负消费、十入口实测见
[独立证据页](../architecture-refactor/evidence/2026-10-25-strict-ts-f1-optional-capture-914df3c7/README.md)。
十入口 1609→1503（F1 −90、F9 连带 −16，零新增零回归）。两点据此更正/明确：

1. F9 的 `$HolderLookup$Provider`/`$RegistryAccess`（各 8）叶因实为 Optional 通配
   （`RegistryOps.lookup` 两个 `Optional<? extends …>` 重载），非 F5/F6 连带；表中该两行归入 F1 机制。
2. F1 剩余 50 = `$Double/$Float/$Integer/$Long/$String` ×10，为值投影（number/string 不结构满足
   host 接口）的可表达性边界，需单独证明的投影变更，不以联合/any 放宽。

## 2026-10-25 F1 视图重做为成员驱动表示（d075db12 + fab58a7d）

复核指出 914df3c7 的内联视图缺失合法公开成员（`orElseThrow`/Bean `present`/`filter`/递归
`stream()`），已改为按反射实例面生成的命名 `$OptionalView<B>`/`$StreamView<B>`：排除集
收敛为三条带 tsc 错误链证明的机械规则（嵌套捕获容器参数、多重载上界 SAM 组、不变容器返回，
`$Iterator` 白名单），被排除成员在具体实现上保留。fab58a7d 确切产物十入口重测仍为
1609→1503（F1 156→50、逐条移除 106/新增 0），设计与证明见
[证据页](../architecture-refactor/evidence/2026-10-25-strict-ts-f1v2-member-view-fab58a7d/README.md)。
