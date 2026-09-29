# 2026-09-29 浮点收窄热修包（脚本 float 属性写毒化整轮 reload）

分支 `hotfix-float-coercion`（基线 mult@ca52f90f），等维护者裁定后合并。
缺陷来源：票 36 维护者试做会话（2026-09-29，author-task 04 item/block modification）——
真机日志 `versions/26.1.2/run/logs/2026-09-29-1.log.gz`（主检出，只读）。

## 结论一句话

试做脚本写 `block.friction = 0.9` / `block.jumpFactor = 1.1`（以及任何不能在
double↔float 间精确往返的脚本数值）时，`ModificationViewSurface.coerce` 用
`Value.asFloat()` 装配参数，引擎的「无损」检查把**合法值**以原始
`PolyglotException`（`Invalid or lossy primitive coercion`）拒绝；异常沿
DOMAIN_PLAN 收集冒泡，把**整轮** server reload 打成
`server candidate domain collection failed for 'item-block-modification'`。
与 D6（int 饱和）、D-B（PolyglotMap 序列化）同类：JS 数值在脚本面缝上遇见窄 Java
原始类型。修复＝三个 ProxyObject surface（modification / 启动期 registry builder /
dynamic builder）的数值装配统一走新助手 `ScriptNumberCoercion`：按 double 读一次、
显式收窄（float＝Java 强转语义；int/long＝整数且在界内），拒绝一律是带成员名的
英文 IAE——D6「脚本面数值参数显式归一」策略的同一收口。

## 触发与根因（真机证据）

试做脚本 `author-tasks/04-item-block-modification.js` 第 73 行起：

```js
event.modify('minecraft:oak_fence', block => {
  block.friction = 0.9        // ← 触发行（服务器日志脚本 t36-04 第 73 行）
  block.jumpFactor = 1.1
})
```

真机栈（截取）：`BlockModificationEventJS.modify` → 裸视图自身 ProxyObject
`putMember` → `ModificationViewSurface.coerce:211 value.asFloat()` →
`PolyglotException: Cannot convert '0.9'(language: Java, type: java.lang.Double) to Java
type 'float' using Value.asFloat(): Invalid or lossy primitive coercion` →
`ScriptManager.reloadScriptsTransactional` 包成 `phase=DOMAIN_PLAN
domain=domain-collect:item-block-modification` 的整轮失败。

机制：JS 数值是 double；Graal `Value.asFloat()/asInt()/asLong()` 额外要求**精确往返**。
`0.9` 的 float 表示 ≠ double 0.9，故被拒；`2.0`、`0.5`、`16` 恰可精确表示，故
`hardness = 2.0` 一直正常——这就是为什么只有 friction/jumpFactor（以及任意
「非二进整分数」浮点值）在野外爆炸。

## 审计范围与处置（逐缝）

| 缝 | 文件 | 暴露面 | 处置 |
|---|---|---|---|
| modification 域值装配 | `src/main/java/.../wrapper/event/server/ModificationViewSurface.java`（26.x 与 1.21.1 共享编译，无 MC 类型） | `BlockModificationJS`（friction/jumpFactor/hardness/resistance float、lightLevel int）、`ItemModificationJS`（maxStackSize/maxDamage int；attackDamage/attackSpeed double 不受影响）、1.21.1 孪生 | **修复（触发缝）** |
| 启动期 registry builder 值装配 | `src/main/java/.../wrapper/registry/gen/BuilderSurface.java`（同缝，1.21.1 经替换共享） | `BlockBuilder` hardness/resistance(float)、`EntityTypeBuilder` width/height(float)、`FluidBuilder` explosionResistance(float)、各 int 面（Enchantment/Item/Painting/MobEffect…） | **修复（同类缺陷，姊妹域）** |
| dynamic builder 值装配 | `common/src/main/java/.../core/dynamic/plan/DynamicBuilderSurface.java` | `DynamicSoundEventBuilder.setFixedRange(Float)`（float）、`DynamicItemBuilder.setMaxStackSize(int)`、`DynamicMobEffectBuilder.setColor(int)` | **修复（同类缺陷）** |
| food/tool 对象字面量解析 | `ItemModificationComponents.intOption/floatOption` | map 路径走 `Number.intValue()/floatValue()`，不经 `Value.asFloat`，无引擎异常 | 不改（票 39 既定宽松语义：`nutrition: 4.7` 静默截断为 4；无崩溃面） |
| MobEffectBuilder 颜色 | `setColor(int)` | 无 float 字段；24 位 RGB 合法值全在 int 正区间（D6 审计已裁定不放开 uint32） | 除共享 coerce 修复外无改动 |
| 类型适配器 | `SizedIngredientAdapter` / `ItemStackAdapter` / `FluidResolver` / `CompoundTagAdapter` / `RecipeJsonValueConverter` / `JsxHostAdapter` / `RecipeEventSchemaHost` | 全部 `fitsInInt()` 先行守卫 + `ValueConversionException`（本家族既有惯例） | 不改 |
| BlockPosAdapter | `{x,y,z}` / 数组坐标 `asInt()` 无守卫 | 小数坐标得原始 lossy 异常（非 legible） | 审计记录，**不改**：int-only、无 float 面、与本热修的「合法 float 被拒」缺陷不同类（拒绝的是本就非法的输入，只是报错不雅）；属适配器家族的既有惯例缺口 |
| double 面 | 三个 surface 的 double 分支 | `asDouble()` 对 JS 数值无损（JS 数值即 double） | 不改（NaN/Infinity 经 double 面仍可写入，见「遗留」） |

1.21.1 孪生：`ModificationViewSurface`/`BuilderSurface` 为两节点共享编译（后者经
`mc_ids` 替换），`versions/1.21.1/.../registry/gen/` 自有的 `BlockBuilder`/
`EntityTypeBuilder`/`FluidBuilder` float 面经共享 coerce 一并修复；修复在新
registry-free 边界测试下于 1.21.1 节点实跑验证（green-1.21.1.txt）。

## 修复（最窄正确缝）

**为什么不是「参数改 `Number`」**（任务书的 D6 字面模式）：本域 setter 从不被
Graal 以宿主方法直调——脚本 property 写/显式 setter 都经 ProxyObject surface
**反射**调用，JS 值先由 `coerce` 装配成实参后才见到 Java 形参。缺陷缝是
`coerce` 的数值分支，不是形参声明；把 `setFriction(float)` 改成 `setFriction(Number)`
反而绕不进修复（会落入 `value.as(Number.class)` 分支）且要连动 Float 存储、
fingerprint 与 Adapter 面。故按 D6 的**实质**（边界处显式归一 + 英文拒绝）收口在
`coerce`，这是本任务书指令的一处实质例外，特此说明。

1. **归一助手**：`common` 新增 `com.tkisor.nekojs.core.bridge.ScriptNumberCoercion`
   —— `toInt/toLong/toFloat(Value, what)`：按 `asDouble()` 读一次；
   float＝有限数按 Java 强转语义收窄（超 float 范围拒绝，不静默成 Inf）；
   int/long＝整数且在界内（`Math.rint` + 界检查，long 上界用 `< 0x1.0p63`，
   对齐 `JsNumber.nativeNumber`）；非数字 / NaN / ±Inf / 越界一律带成员名英文 IAE。
2. **三个 surface 的数值分支**改调该助手（各 3 行替换）；double 分支不动。
   行为对合法值完全兼容：`16`、`16.0`、`2.0`、`0.5`、负数位形照旧；`0.9` 从
   「引擎异常」变为「正常收窄」；`2.5`→int 从「原始 lossy 异常」变为 legible IAE；
   float 面的 NaN/±Inf 从「静默通过（NaN 可穿透到 live 方块）」变为显式拒绝
   （顺手关掉一个真 NaN 毒化洞）。

### 逐文件改动

| 文件 | 改动 |
|---|---|
| `common/src/main/java/.../core/bridge/ScriptNumberCoercion.java` | 新增归一助手（纯 JVM + Graal，无 MC 依赖） |
| `src/main/java/.../wrapper/event/server/ModificationViewSurface.java` | coerce 数值分支改调助手（触发缝） |
| `src/main/java/.../wrapper/registry/gen/BuilderSurface.java` | 同上 |
| `common/src/main/java/.../core/dynamic/plan/DynamicBuilderSurface.java` | 同上 |
| `src/test/java/.../event/server/ModificationFloatCoercionBoundaryTest.java` | 新增：真 GraalJS 红绿测（见下） |
| `src/test/java/.../registry/BuilderFloatCoercionBoundaryTest.java` | 新增：真 BlockBuilder 经 BuilderSurface |
| `common/src/test/java/.../plan/DynamicBuilderNumberCoercionTest.java` | 新增：真 DynamicSoundEventBuilder |

## 测试证据（红→绿）

| 测试 | 内容 | 阶段 |
|---|---|---|
| 26.x+1.21.1 共享 `ModificationFloatCoercionBoundaryTest`（9 例，registry-free 替身视图） | **触发面**：`friction = 0.9`/`jumpFactor = 1.1`（property 写、显式 setter、生产 Consumer+裸视图三种到达形态）收窄为 0.9f/1.1f；int 面整数值照旧；NaN/±Inf/1e40/2.5/1e10 拒绝为带成员名的英文 IAE 且不再是原始 lossy 异常 | 先红（12 失败，含与真机日志逐字相同的 `Cannot convert '0.9'`）后绿 |
| 26.x+1.21.1 共享 `BuilderFloatCoercionBoundaryTest`（5 例，真 `BlockBuilder`） | 姊妹域：`hardness = 0.9` 收窄；`lightLevel = 7.0` 照旧；`2.5`/NaN legible 拒绝 | 先红后绿 |
| `common` `DynamicBuilderNumberCoercionTest`（4 例，真 `DynamicSoundEventBuilder`） | `fixedRange = 0.7` 收窄；null≡未设置语义不变；NaN legible 拒绝 | 先红（`Cannot convert '0.7'`）后绿 |

transcripts：`red-26.1.2.txt`、`red-common.txt`（红，修复前实跑）、`green-26.1.2.txt`、
`green-1.21.1.txt`、`green-common.txt`（绿，含命令与 JUnit 摘要）。

既有覆盖说明：E2E `Ticket39ModificationExamplesTest.blockModificationExampleCommitsThroughTheAdapter`
本身就含 `block.friction = 0.9`（与试做脚本同源），但它是 registry-gated（裸 JVM 无 FML
时按探针跳过——本轮 4/4 skipped），这正是缺陷漏到真机才暴露的原因；新边界测试
registry-free，任何环境都实跑钉住该缝。

## 失败粒度裁定（任务书第 3 问）

- **合法 float（0.9）**：修复前＝收集期原始引擎异常 → 整轮 DOMAIN_PLAN 失败
  （缺陷本体）；修复后＝正常收集、进入声明与 fingerprint，commit 后生效。已修复。
- **真非法值**（非数字 / 小数 int / NaN / 越界）：现在从**当次调用**抛带成员名的
  legible IAE；若发生在 reload 收集期，该异常按票 39/10 的**整批事务语义**仍会使
  候选失败（DOMAIN_PLAN，旧 active 继续服务、零部分应用）——**by-design**，由
  `common` `Ticket39DomainCollectionTest.collectionListenerErrorFailsTheCandidateAndKeepsOldActivePlan`
  钉住（`EventBusJS$PendingListener.executeForCollection` 的注释明确「异常向上传播，
  调用方让候选失败」）。即：粒度边界不是「单次调用失败」，而是「合法值不再触发、
  非法值 legible 且保持整批不提交」。
- **范围非法但可收窄**（如 friction 1.5）：收集期只记录，STATE_PLAN 联合预检整批
  拒绝（旧 active 继续）——票 39 既有语义，不变。

## 验证命令与结果（2026-09-29 实跑）

- `./gradlew :common:check` —— BUILD SUCCESSFUL（含隔离检查）
- `./gradlew :26.1.2:test` —— BUILD SUCCESSFUL；JUnit 汇总 tests=504 failures=0
  skipped=58（跳过全部为 registry-gated 用例，与本改动无关的既有形态）
- `./gradlew :1.21.1:test` —— BUILD SUCCESSFUL；tests=355 failures=0 skipped=14；
  新边界测试在 1.21.1 实跑 9+5 例全绿
- `./gradlew guardLint :26.2.0:compileTestJava :26.1.2-fabric:compileTestJava
  :26.2.0-fabric:compileTestJava` —— BUILD SUCCESSFUL（共享测试在其余节点可编译）
- 无 golden 改动（`RegistryBuilderSurfaceGoldenTest` 照常通过）。

## 遗留（审计发现，未在本包处理）

- double 面的 NaN/±Inf 仍可写入（如 `attackDamage = NaN` 会进属性组件）：非本缺陷
  类（double 是 JS 原生宽度），建议随下次 attackDamage/attackSpeed 语义票处理。
- `BlockPosAdapter` 坐标 `{x,y,z}` 的无守卫 `asInt()`（小数坐标原始 lossy 异常）：
  见上表处置理由，属适配器家族惯例缺口。

## 维护者请求（一句话）

请在修复后的构建上重跑票 36 试做任务 4（`04-item-block-modification.js` 原样重放，
`/nekojs reload server` 应成功、oak_fence friction 变 0.9）；通过即裁定关闭并合并
`hotfix-float-coercion`。
