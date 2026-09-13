# 12: TS/JSX/TSX 编译、source map 与执行行为路径

**What to build:** 脚本作者写入 .ts/.jsx/.tsx 后，类型擦除、JSX 结构、runtime 注入、模块身份、缓存失效和 Graal 执行结果保持既有语义；语法或转换错误在准备阶段带原始 TS/JSX/TSX source location，运行时错误也能回映射。声明与语言能力归属不因转译丢失。

**Blocked by:** [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md)

**Status:** closed

**Assignee:** dsh-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 扩展 TypeScript erasure、JSX element/fragment、automatic runtime 和 TSX corpus，覆盖固定源码到可执行输出的行为与 source-map 映射。, 确保 requested mode、扩展名和 language id 共同决定 TS/JSX/TSX prepare 结果，Resolution/Cache 不复制类型擦除或 JSX 转换语义。, 为类型语法、 JSX 结构和 TSX 组合错误保留原始行列、文件和准备阶段，生成 JS 后的运行时异常也能回映射。, 将 language id、mode、内容、依赖图和 source map 纳入 cache invalidation，避免 TS/TSX 改动命中旧 JS 产物。, 保持纯 Java 自研实现；不引入非纯 Java 转译器，不因候选库评估阻塞行为票。, 收缩 gate：TS/JSX/TSX 旧并行转换或装载旁路只有在替代 behavior、source-map/declaration、trace 通过且无调用者后移除；随本票完成收缩，不推迟 final release，也不删除公开语言。

## Acceptance criteria

- [x] .ts、.jsx、.tsx representative corpus 均产生正确 language id、module mode、可执行 code/IR 和非空可用 source map。
- [x] TypeScript 类型擦除不改变运行时值、控制流和导出形状；corpus 固定有意承诺的行为而非私有 AST 布局。
- [x] JSX/TSX 的 element、fragment 和 automatic runtime 行为与既有 golden/执行测试一致。
- [x] 准备期语法/转换错误携带原始 TS/JSX/TSX 文件、行列和阶段；不会被后续 JS 位置覆盖。
- [x] 执行期异常经 source map 回到原始 TS/JSX/TSX 位置，并保留模块身份。
- [x] 源码、mode、language id 或依赖变化使 cache key/revision 失效，未变化输入可观察命中。
- [x] TS/JSX/TSX 与 JS/CJS/ESM 混合 import 的身份和错误归属可追踪。
- [x] 不新增外部转译依赖、公共 parser SPI、Gradle project 或第二套 TS 管线。
- [x] 随实现交付 .ts、.jsx、.tsx 的最小可运行示例与必要迁移材料；示例只使用已通过 gate 的语法、runtime 和模块能力。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [语言模块管线规格](../specs/06-language-module-pipeline.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md): TS/JSX/TSX 必须消费同一个不可变 prepared module、模块身份、cache key 和错误阶段模型；否则会重新形成转译语言私有管线。

## Scope and coordination

- **Rationale:** TS/JSX/TSX 的风险在行为、source map 和缓存，而不是 parser 接口；该票从作者源文件到执行结果和诊断完整闭合。
- **Coordination:**
  - MANAGED_SURFACE: TS declaration 的规范源与显式 golden 更新由 managed surface 票约束。
  - DIAGNOSTICS: 错误字段、workspace 展示和用户报告消费本票的 source-map/阶段结果。
  - PERF_BASELINE: TS/TSX corpus 执行时间只作对照记录，不擅自设定阈值。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## Closure record（2026-09-13）

- 执行者：dsh-agent。分支 `ticket-12-language-ts`（基于 `integration/tickets-08-11`，含已关闭的工单 11）。
  实施区间 `f3b266d4..本提交`（3 个语义化 commit：缺陷 A+B 先红后绿 → corpus/阶段/source map/示例证据 → 票据与报告）。
- 交付物：
  - 源码（全部在 `common`）：`NekoTypeScriptCompiler`（fail closed 出口 + 只读谓词预扫描 + `jsxFrontend` 模式；
    删除零调用者的 `erasePreservingSourceMap`）、`NekoJsxCompiler`（`compileTsx` 改用 JSX frontend 擦除入口）、
    `NekoJsxLexer` / `NekoJsxTokenStream`（language id 取插件 id，缺陷 A）、
    `NekoNativeEsmSourceRewriter`（把 prepared module 的 source map 登记到 ESM virtual module id，补 AC5 缺口）。
  - 证据：[`baseline/2026-09-13-language-ts/REPORT.md`](baseline/2026-09-13-language-ts/REPORT.md)
    （物理落点、缺陷裁决、fail-closed 边界、逐 AC 判定、收缩 gate、遗留）；
    [`MIGRATION.md`](baseline/2026-09-13-language-ts/MIGRATION.md)（迁移对照表）；
    [`minimal-example.ts`](baseline/2026-09-13-language-ts/minimal-example.ts) /
    [`.jsx`](baseline/2026-09-13-language-ts/minimal-example.jsx) /
    [`.tsx`](baseline/2026-09-13-language-ts/minimal-example.tsx) /
    [`-entry.mjs`](baseline/2026-09-13-language-ts/minimal-example-entry.mjs)。
  - 测试（新增 5 个类 / 29 个用例）：`NekoTypeScriptErasureFailClosedTest`（12）、
    `NekoLanguageIdentityInvariantTest`（3）、`NekoLanguagePipelineCorpusTest`（4）、
    `NekoTranspiledLanguagePhaseCorpusTest`（6）、`NekoTranspiledMinimalExampleTest`（4）。
    全部使用 `@AfterEach` + `deleteFilesAndEmptyParents` 真删文件与变空父目录（沿用工单 11 的清理纪律）。
- **缺陷 A 裁决：`.tsx` 的 language id 归 `jsx`。** 同一文件此前 prepared module 报 `jsx`、IR 报 `tsx`。
  理由：`NekoJsxLanguagePlugin` 同时拥有 `.jsx` 与 `.tsx` 且 `id() = "jsx"`；TSX 是同一 JSX frontend 的
  「JSX lowering + TS 擦除」组合形态；`getLanguage`/`replaceLanguage` 按 extension 与 id 匹配；
  `identityOf` 必须在编译前算出与产物同源的 id；`NekoCommonBuiltinPluginTest` 与 `NekoJsxTokenStream` 的默认值
  本来都是 `jsx`；tsc 也不按扩展名分裂 JSX 语言身份。影响调用点：`NekoJsxLexer`（去写死 `"tsx"`）、
  `NekoJsxTokenStream`（默认值取插件 id）、`NekoIRProgram.languageId`（`.tsx` 现为 `jsx`）。
  `.ts`/`.jsx`/module mode/产物/source map/JSX runtime **均未变**。深层不变量（prepared == registry == IR）
  由 `NekoLanguageIdentityInvariantTest` 对 `.js/.mjs/.cjs/.jsx/.ts/.tsx` 全体钉住。
- **缺陷 B fail-closed 边界（实际只覆盖 2 类 / 5 种，不是「所有未终止类型构造」）：** 未闭合的 `interface` 体、
  `type` 对象体、`export interface` 体、`declare class` 体，以及**类型标注**里的泛型参数列表未闭合
  （`const x: Array<string = SIDE_EFFECT();`）——这 5 种在**准备阶段**抛带原始文件 + 行列的
  `IllegalArgumentException`（消息以 `Unterminated TypeScript type construct in <file> at <line>:<col>` 开头，
  只陈述事实、不含修复指引）。**仍静默通过、本票未覆盖**：类/函数**声明头**的泛型参数列表未闭合
  （`class C<T {`、`function f<T {`）、泛型调用/泛型箭头未闭合（`foo<Bar(1);`、`const f = <T, (x: T) => x;`）、
  `implements` 子句未闭合（`class C implements I {`）。详见「code-review 修订」第 1 条。
  **仍然合法**：`interface Foo { bar: string }`、`type T = string`、`type T = { a: string }`、`function id<T>(x: T): T`、
  `const id = <T,>(x: T) => x`、`const m: Map<string, number> = ...`、跨行泛型标注、文件末尾的 `let x: number`、
  `abstract class` 成员 elide —— 全部继续擦除成功并可执行（7 条边界断言）。实现上先跑一遍只读谓词预扫描，
  保证「一旦会报告未终止，就在任何擦除动作之前抛错」，避免谓词与拆解器结果撕裂。
- 顺带修复（同一 root cause，被既有测试当场抓住）：`.tsx` 的泛型组件 `<Foo<number>/>` 经 JSX lowering 后，
  擦除器把它当 TS 泛型实参擦掉，组件运行时值从 `Foo` 变成字符串 → `jsxFrontend` 模式下只承认
  「泛型调用 `foo<T>(`」与「泛型箭头 `<T>(` / `<T>=`」两种后续形态。
- 收缩 gate（Work item 6）：**不存在** TS/JSX/TSX 的旧并行转换或装载旁路。生产擦除入口为 `eraseTypescript` /
  `eraseDetailed` / `eraseJsxFrontendPreservingSourceMap`；JSX 转换唯一调用者是 `NekoJsxLexer`；
  `src/`、`versions/`、`common-api-processor/` 零引用。本票只删除了零调用者的 `erasePreservingSourceMap`，
  没有为凑票据删除无辜代码。
- 行为保持：语言范围、扩展名、module mode、JSX runtime 选择、装饰器报错语义全部不变；工单 11 的
  `NekoPreparedModule`/`NekoModulePreparation`/cache key/错误阶段模型只消费未重定义。
  **既有 golden 零变更**（无需维护者审阅的旧/新 diff）。
- 验收判定（据实）：**AC1–AC9 全部 pass**。逐条证据见 REPORT §8。
  本票另修复一个**真实缺口**（不在原票据缺陷清单）：经 ESM import 链执行的转译模块，其 source map 此前未登记到
  ESM virtual module id，执行期错误会在查表时落空而丢失原始文件/行列（AC5 字面要求）。
- 门禁（全量真跑，首次关闭时）：`:common:test --rerun-tasks --no-build-cache` → **207 suites / 1515 tests / 0 failures /
  0 errors / 5 skipped**；`:common:check` pass；`guardLint` pass（248 守卫块 / 331 文件 / 0 问题）。
  修订后为 207 suites / 1516 tests（新增 1 例边界测试），见文末 code-review 修订。
- 未验证项与 owner：① 真实 Minecraft 客户端/服务端 smoke（含 `/nekojs reload`）**not-verified**——本票改动全在
  `common`，未启动真实客户端；owner 维护者 / 票 19 / 票 34；② 节点 `:26.1.2:compileJava` 未单独执行——
  改动不触碰平台面，owner 维护者。
- 遗留（**先区分两类后果**：旧缺陷是静默**删除**代码、永不失败，已修；下列遗留是静默**保留**代码、
  在执行期以硬 `SyntaxError` **可见地失败**，不丢代码、严重度低得多）：① TS 擦除器整体容错哲学仍不统一——
  声明头泛型参数列表未闭合（`class C<T {`、`function f<T {`）、泛型调用/泛型箭头未闭合（`foo<Bar(1);`、
  `const f = <T, (x: T) => x;`）、`implements` 子句未闭合（`class C implements I {`）、`satisfies` 后跟对象类型、
  类内 index signature、`import A = require(...)` 仍静默产出无法解析的 JS；是否 fail closed 应由后续语言票结合
  作者反馈评估。owner：后续语言票 / 维护者；② 纯 JS 语法错误的宽松度（`const = = 1;`、未终止字符串、
  孤立 `}`、未闭合函数体）在 `.js` 与 `.ts` 完全相同，来源是 ESM parser 而非 TS 擦除，**不是本票引入的**，
  本票未改动 ESM parser 的通用容错（工单 11 已验收该层）；③ `.jsx` 下泛型组件语法的报错质量可改进。
- 说明：本票不删除任何公开语言，未新增转译依赖 / 公共 parser SPI / Gradle project，未新建第二套 TS 管线。
### code-review 修订（2026-09-13）

双轴 review（Standards 轴 + 主 agent 独立核验）后逐条处理。**AC1–AC9 判定不变：全部仍为 pass，Status 保持 closed。**
核验已确认不动的部分：缺陷 A 六扩展名 `prepared == registry == IR` 一致；缺陷 B 的 5 种形态确实抛错；
既有 golden 零变更；收缩 gate 属实；AC5/AC9 的测试确实真执行。

1. **fail-closed 边界表述夸大 —— 已如实降低（报告准确性问题，非实现问题）。**
   主 agent 实测反驳了 Closure record 原文声称覆盖的「未闭合的泛型参数列表」「未闭合的 `implements` 子句」；
   我复现确认以下形态**仍然静默通过**：`class C implements I< { … }`、`class C implements { … }`、
   `class C implements I {`（体未闭合）、`function f<T { … }`、`function f<T`（换行）、`foo<Bar(1);`、
   `class C<T { … }`、`const f = <T, (x: T) => x;`（复现输出见下）。
   **处理**：REPORT §3.2/§3.3、MIGRATION §1 与本 Closure record 的表述改为「实际覆盖 2 类 / 5 种」并逐条列出未覆盖形态；
   新增 `NekoTypeScriptErasureFailClosedTest.declarationHeaderAndImplementsFormsAreStillSilentAndKeepTheirCode`
   把「仍静默」钉成事实（断言不抛且代码未被删空），防止后续 reviewer 误以为已覆盖。
   复现命令：`:common:test --tests '…NekoTypeScriptErasureFailClosedTest'`（该用例即复现），
   实测输出：`class C implements I< { x = 1; }` → 静默、产物 `class C               { x = 1; }` 可解析；
   `function f<T { return 1; }` → 静默、产物无法解析。
2. **Standards 违规：错误消息带修复指引 —— 已删除。** `CONTEXT.md:8` 规定「报错保持普通形式，不带修复指引」，
   指引应由 wiki / `MIGRATION.md` 承载。`NekoTypeScriptCompiler.failClosedUnterminated` 的消息已去掉
   `Close the type before the following code, or remove the incomplete declaration.`，只保留
   「file + 行列 + 未终止事实 + NekoJS 不会静默擦除文件其余部分」。
   按边界要求**只改本票新增的该方法**，未触碰历史代码 `unsupported()`（其 hint 不在本票 diff 内，
   改动会扩大 diff 并影响既有断言）。既有断言（`assertLocatedAt`、`NekoTypeScriptCompilerTest`）保持绿。
3. **「仍静默通过」的形态 —— 已如实记录为遗留，并明确区分两类后果。** REPORT §10 现在用对照表区分：
   旧缺陷＝静默**删除**代码、永不失败（已修，风险高）；现遗留＝静默**保留**代码、执行期硬 `SyntaxError`
   可见地失败（不丢代码，风险低）。第二类不要求本票修（AC4 针对的是准备期错误带位置，这些形态的失败是可见的）。

**本轮附带发现并修复的一处死代码（并入第 1 条）：** `eraseImplements` 中为「`implements` 子句未闭合」新增的
fail-closed 分支是**不可达**的——`scanPredicates()` 只对 `:` 类型标注跑拆解，因此该分支置位的状态永远不会被消费。
它是第 1 条夸大表述的来源，已删除，`eraseImplements` 恢复为改动前的行为（`implements` 如实记为未覆盖）。
删除前后复现均确认无误报（`class C implements I` 后跟合法类型标注/断言/普通代码都不抛）。

**修订后门禁（全量真跑）**：`:common:test --rerun-tasks --no-build-cache` + `:common:check` + `guardLint`
同一次执行 → BUILD SUCCESSFUL；**207 suites / 1516 tests / 0 failures / 0 errors / 5 skipped**；
`guardLint` pass（248 守卫块 / 331 文件 / 0 问题）。