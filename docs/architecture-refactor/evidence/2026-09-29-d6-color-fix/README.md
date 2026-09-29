# 2026-09-29 缺陷 D6 修复包（脚本侧 ARGB 颜色字面量渲染为白色）

分支 `ticket-26-color-fix`（基线 mult@60e113a3），等维护者裁定后合并。
缺陷来源：票 26 真机会话记录（`../2026-09-29-ticket26-realmachine/README.md` §新发现缺陷 D6）。

## 结论一句话

缺陷机制与怀疑一致并已用真引擎探针证实：JS 无符号颜色字面量（≥ 2³¹）传入 Java
`int` 参数时，GraalJS 宿主互转**饱和**为 `Integer.MAX_VALUE`（0x7FFFFFFF = 半透明白）
而非按位回绕；修复 = 全部脚本面颜色参数改收 `Number`，经 `UiColor.argbBits` 按
uint32 读位（低 32 位即 ARGB 位），Java 侧传负 int 位形完全不变。

## 诊断（真引擎探针证据）

探针测试：`common/src/test/java/com/tkisor/nekojs/api/ui/ScriptColorBoundaryRealEngineTest.java`
——按 `DataGeneratorJsJsonRealEngineTest` 同款装配（`NekoSharedHostAccess` + `ClassFilter`
+ 生产互操作选项，含 `js.nashorn-compat`），把只含一个 `int color(int)` 方法的宿主探针
投给真实 GraalJS Context，从 JS 侧调用。

红阶段（修复前实跑，transcript 见 `probe-red-common.txt`）：

| JS 字面量 | Java `int` 参数收到 | 期望（位回绕） |
|---|---|---|
| `0xFFFFFF00`（黄） | `2147483647`（0x7FFFFFFF，半透明白） | `-256` |
| `4294967040`（同值十进制） | `2147483647` | `-256` |
| `-256`（位运算结果形态） | `-256`（不受影响） | `-256` |

即：真机观察到的「黄→白、绿→白」正是 0x7FFFFFFF（50% 透明白）叠在任何底色上的观感。
机制层：Truffle/GraalJS 的 number→int 宿主转换按 Java `(int) double` 强转语义**饱和**，
不是 ECMAScript ToInt32 的回绕语义。JVM 单测直接传 Java int（负数），从未跨过这条边界，
故从未暴露。

## 修复（最窄正确缝）

1. **归一化助手**：`common` 的 `com.tkisor.nekojs.api.ui.UiColor#argbBits(Number)`
   —— 整数且在 [int32min, uint32max] 区间按 `(int)(long) raw` 读低 32 位；
   null / 小数 / 超范围抛英文 `IllegalArgumentException`（对齐 DataGeneratorJS 的
   「垃圾输入响亮失败」先例；修复前超范围是静默饱和成白色，更糟）。
   `UiColor.parse` 的 Number 分支改与它共享同一条区间判据（单一事实源）。
2. **脚本面参数改 `Number`**：饱和发生在「参数声明类型」这一步，Java 代码无法在
   收到 0x7FFFFFFF 之后还原，所以缝必须在参数类型上。JS 侧写法零变化（传
   `0xFFFFFF00` 或 `-256` 都对）；Java 侧调用者传 int 字面量自动装箱，负数位形往返不变。

### 逐面改动清单

| 文件 | 改动的颜色参数方法 |
|---|---|
| `src/.../wrapper/client/PainterJS.java`（26.x） | `color` `text` `centerText` `rect` `outline` `gradient` `gradientH` |
| `src/.../client/render/HudRenderContextJS.java`（26.x） | `color` `text` `drawText` `centerText` `rect` `fillRect` `outline` `gradient` |
| `src/.../client/render/WorldRenderContextJS.java`（26.x） | `line`（6/7 参两形态）`box` |
| `versions/1.21.1/src/.../wrapper/client/PainterJS.java` | 同 26.x PainterJS |
| `versions/1.21.1/src/.../client/render/HudRenderContextJS.java` | 同 26.x HudRenderContextJS |
| `versions/1.21.1/src/.../client/render/WorldRenderContextJS.java` | 同 26.x WorldRenderContextJS |

审计过但不改（理由）：
- `DynamicRegistryJS.MobEffectBuilder.color(int)` / `MobEffectBuilder.setColor(int)`：
  药水效果色是 24 位 RGB（默认 0xFFFFFF），合法值全部落在 int 正区间，不经过 ≥ 2³¹
  字面量，不在本缺陷范围；如未来放开 ARGB 再议。
- `JsxHostAdapter` / `VisualStyleResolver`（JSX UI 路径）：颜色经 `UiColor.parse`
  （Number 分支本就按 uint32 读位），已是正确语义。
- Dashboard 内部 Canvas（`DashboardView`/`NekoErrorDashboardScreen`）：纯 Java 内部面，
  非脚本面。

## 测试证据（红→绿）

| 测试 | 内容 | 阶段 |
|---|---|---|
| `common` `ScriptColorBoundaryRealEngineTest` | 真引擎机制定版：`int` 参数饱和 characterization（钉住引擎行为，注明生产不得依赖）+ `Number` 缝位回绕绿测 | 先红（断言期望行为收到 2147483647）后绿 |
| `common` `UiColorTest`（新增 3 例） | `argbBits` 的 uint32 回绕 / int32 透传 / 英文拒绝消息 | 绿 |
| 26.x+1.21.1 共享 `wrapper/client/PainterJSScriptColorBoundaryTest` | **生产 PainterJS 全缝**：真 GraalJS Context 驱动测试缝构造的 PainterJS，录制用 `GuiGraphicsExtractor`/`GuiGraphics` 子类（`Unsafe.allocateInstance` 绕开需要活客户端的构造器）在图形汇点断言收到的 int；覆盖 text/rect/outline/gradient/color+currentColor、位运算负数透传、Java int 调用者兼容、坏输入英文报错；`//? if >=26` 守卫双分支（26.x `text`/`outline` vs 1.21.1 `drawString`/`hLine`/`vLine`） | 绿 |
| 共享 `client/render/Ticket26ColorParameterSurfaceTest` | Hud/World 两类无法无头构造（构造器解引用活 `Minecraft`），以反射钉住三类 painter 面的全部颜色参数签名：`Number` 形必须存在、`int` 形（D6 原形）必须不存在 | 绿 |

transcripts：`probe-red-common.txt`（红）、`green-common.txt`、`green-26.1.2.txt`、
`green-1.21.1.txt`（绿，含验证命令与结果摘要）。

## 验证命令与结果

- `./gradlew :common:check` —— common 被改（UiColor + 两测试）
- `./gradlew :26.1.2:test` —— 共享树全量（含既有 JVM 测试回归）
- `./gradlew :1.21.1:test` —— 双胞胎树全量
- `./gradlew guardLint` —— 新测试带版本守卫
- `./gradlew :26.2.0:compileTestJava :26.1.2-fabric:compileTestJava :26.2.0-fabric:compileTestJava`
  —— 共享测试在其余节点可编译

结果见各 green-*.txt 末行（全部 BUILD SUCCESSFUL）。

## 真机验证（留给维护者的一次性检查）

本修复包为无头证据；请下一轮真机会话重跑 1 分钟 HUD 颜色检查：dev 客户端载入
`ClientEvents.hud` + `hudRender` 各画一条 `0xFFFFFF00`（黄）与 `0xFF55FF55`（绿），
目视确认不再是白色（照抄 `../2026-09-29-ticket26-realmachine/t26_realmachine_smoke.js`
第 24-31 行即可）。

## 维护者请求（一句话）

请在真机重跑上述 1 分钟 HUD 颜色冒烟；通过即裁定 D6 关闭并合并 `ticket-26-color-fix`。
