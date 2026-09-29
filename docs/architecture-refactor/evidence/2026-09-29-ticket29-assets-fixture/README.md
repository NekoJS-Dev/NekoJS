# Ticket 29 证据收口 review pack：Assets 回读 fixture 与按节点能力表（2026-09-29）

票 29（Assets/Lang 资源生成与回读收口，in-review）在本轮前有两项 AC 缺口（见票据 29
REPORT §3/§5 与 `evidence/2026-09-29-inreview-digest`）：

- **AC3 缺口**：blockState/blockModel/itemModel/texture 的输出没有「确定 contract/golden
  **或回读 fixture**」——既有测试验证行为，但没有一个 fixture 把**产物文件读回**并断言
  确定性内容；
- **AC8 缺口**：未对 fabric 跑 Assets/Lang 的 source trace；`Assets` 绑定带 `>=26` 守卫
  ⇒ 1.21.1 无该绑定，这一既存差异未被正式记录为 capability 条目。

本 pack 以**零生产改动**补上这两块：新增 2 个测试文件（共 10 tests）+ 票据 baseline
REPORT 追加 §8 能力表。**全部内容停留在 `ticket-29-assets-fixture` 分支，不合并不推送；
AC 勾选与票据注记由 main session 在维护者批准后进行。**

- 分支：`ticket-29-assets-fixture`（基于 mult@`225344f8`）
- worktree：`D:/mcmodDemo/NekoJS-mult-t29fx`
- 执行者：ticket-29 证据收口 subagent（GLM-5.3）

## 1. 交付物

| 文件 | 性质 | 内容 |
|---|---|---|
| `common/src/test/java/com/tkisor/nekojs/wrapper/Ticket29AssetsReadbackFixtureTest.java` | 新测试（4 tests，common 零 MC） | AC3 回读 fixture（见 §2） |
| `src/test/java/com/tkisor/nekojs/core/Ticket29AssetsLangCapabilityTraceTest.java` | 新测试（5 tests，共享树，`neoforge`+`>=26` 守卫） | AC8 source trace + golden 回读 + 26.x 运行时腿（见 §3） |
| `versions/1.21.1/src/test/java/com/tkisor/nekojs/core/Ticket29AssetsAbsentOn1211Test.java` | 新测试（1 test，1.21.1 节点本地树） | AC8 的 1.21.1 运行时腿：绑定缺席 + 两事件仍在（见 §3） |
| `docs/architecture-refactor/baseline/2026-09-21-assets-lang/REPORT.md` | baseline 追加 §8 | 五节点 assets/lang 能力表（supported/unavailable + 单元格证据 + not verified），**不是**全局矩阵（票 31/32 域不变） |

生产代码、票据文件（`29-assets.md`）、golden 与基线产物：**零改动**（见 §5 diff 清单）。

## 2. AC3 回读 fixture（`Ticket29AssetsReadbackFixtureTest`）

**做法**：镜像 `DataGeneratorJsJsonRealEngineTest` 的真 GraalJS harness（同一
`NekoSharedHostAccess` + `ClassFilter` + interop options），把 `AssetGeneratorJS` 以**生产
绑定名 `Assets`** 暴露进 Context——四个成员的第二个参数是 `graal...Value` 型，脚本对象必须
原样穿过 polyglot 转换边界（与生产脚本同一入口）。每个用例先从 JS 驱动写入，再**从磁盘读回
产物文件**断言确定性内容：

- `blockStateFormsReadBackAsDeterministicJson`：字符串简写（断言**逐字节**的落盘 JSON
  `{"variants":{"":{"model":"mymod:block/my_block"}}}`）、variants 对象 + 默认 namespace
  补全（`plain_block` → `minecraft/blockstates/plain_block.json`，成员集与嵌套结构全钉）、
  multipart 数组（嵌套 `apply` 对象保留）、JSON 字符串输入（与对象输入同构）；
- `blockModelReadBackPinsTextureShorthandCompletionAndSubdirectories`：子目录 id、成员集
  `{parent,textures}`、贴图简写四态（裸简写补全 / 已带命名空间 / 已带目录 / 完整引用原样），
  键序保持脚本插入序；
- `itemModelReadBackPinsItemKindCompletion`：`layer0` 简写 → `<ns>:item/<value>`；
- `placeholderTextureReadBackPinsDeterministicPngIdentity`：三种调用形态（显式 `block/`
  路径 / 默认 `block/` / 显式 kind `item`）各自落盘后，从原始字节钉占位 PNG 的**确切身份**。

**占位 PNG 身份的钉法（及为何不钉整文件哈希）**：8 字节 PNG 魔数逐字节相等；chunk 顺序恰为
IHDR→IDAT→IEND 且每个 chunk 的 CRC32 复算通过；IHDR 13 字节全钉（16×16、bit depth 8、
color type 2 truecolor RGB、压缩/滤波/隔行全 0）；IDAT 解压后与期望扫描线数组**逐字节相等**
（16 行 ×（1 滤波字节 0x00 + 16×3 洋红 FF 00 FF）＝784 字节）并以 **SHA-256 常量**
`e0d80d…e2bd5a` 钉像素负载内容；同一运行内两次独立写入的占位 PNG **逐字节相等**（每运行
确定性）。**不**钉整文件哈希的原因：IDAT 是 `java.util.zip.Deflater`（native zlib）的压缩
输出，字节形态在同一 JVM 构建内稳定、但不是跨 JVM/zlib 构建的契约——语义上承重的部分
（魔数/IHDR/解压像素负载/chunk CRC/写入间确定性）全部与 zlib 无关地钉死，fixture 因此在
不同 JDK 构建间稳定。golden 文件刻意不引入：回读断言钉住同等确定性，且少一份需要同步的
第二产物。

## 3. AC8 fabric/1.21.1 能力 source trace

**共享树 `Ticket29AssetsLangCapabilityTraceTest`**（`//? if neoforge` + `//? if >=26`，
Ticket27 surface-fixture 同形；26.1.2/26.2.0 执行）：

1. **golden 回读**（只读，普通测试不更新 golden）：五节点 `ClientEvents` 行——NeoForge
   三节点 `generateAssets`/`lang` 各恰一次；fabric 两节点均无（documented unavailable, no
   silent parity）；并断言行覆盖恰为已知五节点集合（节点清单变化须回头改能力表）；
2. **守卫栈 trace**：对 `NekoJSCorePlugin.java` 做与 stonecutter 同形的守卫栈扫描，
   `new AssetGeneratorJS()` 注册行的活跃守卫栈必须含 `neoforge` 与 `>=26`（⇒ 1.21.1
   求值移除、fabric 树不含该类）；`ClientEvents.java` 的两个 `GROUP.client(...)` 声明行
   必须在文件级 `neoforge` 守卫内（⇒ fabric 求值树整组缺席）；
3. **fabric 源树扫描**：`src/fabric/java` + 两个 fabric 版本树中
   `new AssetGeneratorJS(`/`register("Assets"` 零命中；`FabricClientEventBindings` 的
   bus 声明集合恰为 `{tickPre,tickPost,tick}`（无 assets/lang 桥）；
4. **26.x 运行时腿**：生产 `registerBinding` 路径解析到 `Assets`（valueType 精确
   `AssetGeneratorJS`）+ 两总线 eventName/scriptType。

**1.21.1 节点本地 `Ticket29AssetsAbsentOn1211Test`**（节点本地树 ⇒ 只在该节点执行）：
同一条生产 `registerBinding` 路径上 `viewRegistered().get("Assets")` 为 **null**，而
`ClientEvents.GENERATE_ASSETS/LANG` 仍解析——「1.21.1：事件在、typed binding 缺席」由
guard 推断升级为**运行时断言**。

> 工程注记：1.21.1 腿必须放节点本地树。共享树的 active 节点（26.1.2）**直编不预处理**，
> 内层版本守卫在 active 节点是惰性注释——`<26` 分支若写在共享树必须整体块注释
> （`PostEffectDeclarationLifecycleTest#chainJson` 形态），而它嵌在文件级 `neoforge`
> 守卫内会让 fabric 预处理面对嵌套块注释；节点本地树（`versions/1.21.1/src/test`，
> `Ticket44ResourceIdParityTest` 先例）是干净落点。另：字符串字面量/注释里的 `//?}`
> 序列会被 stonecutter 词法当成真守卫标记（本轮真实踩到，`stonecutterPrepareTest`
> 报 Mismatched input），fixture 里守卫标记用 `"/" + "/?"` 拼接构造。

**能力表**（五节点 × 三能力域，含上述证据引用与 not verified 清单）追加在
`baseline/2026-09-21-assets-lang/REPORT.md` **§8**，摘要：

| 能力域 | 1.21.1 | 26.1.2 | 26.2.0 | 26.1.2-fabric | 26.2.0-fabric |
|---|---|---|---|---|---|
| `ClientEvents.generateAssets` + plugin generate-assets 聚合 | supported | supported | supported | unavailable（显式） | unavailable（显式） |
| `ClientEvents.lang` + `generatedLangs()` 聚合 | supported | supported | supported | unavailable（显式） | unavailable（显式） |
| `Assets` typed binding | **unavailable（显式）** | supported | supported | unavailable（显式） | unavailable（显式） |

## 4. 验证（全部本 worktree 真跑；原始转录 `transcripts/`）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `gradlew guardLint --rerun --info` | BUILD SUCCESSFUL；`守卫块 339，扫描 482 个文件；超限豁免 0 个；警告 0 条` |
| 2 | `gradlew :common:check` | BUILD SUCCESSFUL in 1m（含 `checkCommonIsolation`）；XML 汇总 **1954 tests, 0 failures, 0 errors, 4 skipped**；`Ticket29AssetsReadbackFixtureTest` 4/0 |
| 3 | `gradlew :26.1.2:test --rerun` | BUILD SUCCESSFUL in 15s；XML 汇总 **474 tests, 0 failures, 0 errors, 58 skipped**；`Ticket29AssetsLangCapabilityTraceTest` 5/0 |
| 4 | `gradlew :1.21.1:test --rerun` | BUILD SUCCESSFUL in 17s；XML 汇总 **325 tests, 0 failures, 0 errors, 14 skipped**；`Ticket29AssetsAbsentOn1211Test` 1/0；共享树 trace fixture 在该节点被守卫求值移除（test-results 无其 XML＝预期缺席，非跳过） |
| 5 | `gradlew :26.1.2-fabric:test --rerun` | BUILD SUCCESSFUL in 19s；XML 汇总 **254 tests, 0 failures, 0 errors, 25 skipped**（fabric 腿：共享树含新 fixture 编译通过、零回归；trace fixture 在 fabric 亦为守卫求值缺席） |

XML 计数取自各节点 `build/test-results/test/*.xml` 的 JUnit 属性求和；`--rerun` 防止
UP-TO-DATE 假绿。红/绿自我修正记录：readback fixture 初版两处测试自身笔误（gson
`keySet()` 与 `List` 的 equals 不可交换、SHA-256 常量抄写掉字）先红后绿；trace fixture
初版把 `<26` 腿放共享树被 active 节点直编暴露、以及字符串字面量里的守卫标记触发
`stonecutterPrepareTest` 解析失败——三处均修正后全绿（教训已写进 §3 工程注记）。

## 5. diff 清单（本 pack 自身）

```
common/src/test/java/com/tkisor/nekojs/wrapper/Ticket29AssetsReadbackFixtureTest.java   （新增，4 tests）
src/test/java/com/tkisor/nekojs/core/Ticket29AssetsLangCapabilityTraceTest.java        （新增，5 tests）
versions/1.21.1/src/test/java/com/tkisor/nekojs/core/Ticket29AssetsAbsentOn1211Test.java（新增，1 test）
docs/architecture-refactor/baseline/2026-09-21-assets-lang/REPORT.md                   （追加 §8 能力表）
docs/architecture-refactor/evidence/2026-09-29-ticket29-assets-fixture/**              （本目录）
```

零生产改动；零 golden/基线变更；票据文件 `29-assets.md` 未动（勾选/注记待批准后由
main session 做）。

## 6. 合并后逐 AC 判定（供 main session 注记引用）

| AC | 合并本 pack 后的判定 | 依据 |
|---|---|---|
| AC3（blockState/blockModel/itemModel/texture … 确定 contract/golden 或回读 fixture） | **可勾选**（建议） | `Ticket29AssetsReadbackFixtureTest`：真引擎驱动 + 磁盘回读 + JSON 结构/逐字节序列化形式 + PNG 身份全钉（§2）；不为资源限额或 PNG 新造 policy 的约束未违反（零生产改动） |
| AC8（… supported/partial/unavailable 由真实 source trace 与 smoke 决定，不自动补 Fabric parity） | **部分满足 → 大幅收窄，建议随票 31/32 的 fabric smoke 后再勾**：source-trace + 运行时腿已交付且五节点能力表已记录（REPORT §8）；仍缺的是 fabric 侧**运行时 smoke**（当前 fabric 判定基于 golden 行＝真实节点求值产物 + 源树扫描） | §3 + REPORT §8.3 not verified 表 |

其余 AC 的判定不受本 pack 影响（AC6 真机 reload、AC7 dedicated-server 进程级、AC9
declaration 面、AC11 sign-off 等缺口与 owner 不变，见票据 29 REPORT §5）。

## 7. 维护者请求（一句话）

请裁决：是否接受以「回读 fixture（非 golden）+ source-trace/运行时能力钉 + baseline §8
能力表」的形态关闭票 29 的 AC3 缺口、并把 AC8 的 fabric 面收敛为「已记录 unavailable、
运行时 smoke 归票 31/32」——接受则本分支可合并，main session 随后注记票据。
