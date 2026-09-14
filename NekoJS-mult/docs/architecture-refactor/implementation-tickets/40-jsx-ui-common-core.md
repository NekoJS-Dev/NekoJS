# 40: JSX UI common core、公开契约与 Fake Host Proof

**What to build:** 在 common 中交付不依赖 Minecraft/loader 的 JSX UI 核心路径：automatic runtime 产生不可变 VNode，函数组件与 Fragment 展开为候选树，signal/store 依赖触发了受影响 root reconcile，keyed reconciler 通过公开 host Adapter contract 创建、更新、排序和释放 fake host node。同一票冻结最小 UI facade、primitive props、事件对象、signal/store、root handle、布局约束和诊断字段的 managed contract，并让 TypeScript JSX declaration / Probe 输出与 fake runtime 语义一致。

**Blocked by:**
- [05: 单 owner 预整理：闭合两个 loader 的运行生命周期入口](05-runtime-root.md)
- [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)
- [12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md)

**Status:** closed

**Assignee:** dsh-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [x] `jsx` / `jsxs` / `Fragment` 到 VNode、函数组件、条件、数组、spread、key 和 children 的语义有公开 contract fixture；VNode 不持有 Minecraft、loader、`GuiGraphics`、Font、Screen、GL 或长期可变 host 对象。
- [x] 初始 primitive 集合 `screen`、`panel`、`row`、`column`、`stack`、`scroll`、`label`、`button`、`input`、`image`、`spacer` 有唯一登记表；未知 primitive、非法 props、非法布局值和重复 key 显式失败。
- [x] signal/store 的读取、写入、批量失效、依赖重建、disposed root 写入和 owner-thread 拒绝/排队语义可从公开调用者 Interface 观察，不检查私有依赖集合。
- [x] reconciler 的候选 render、结构/属性/能力校验、原子提交、keyed diff、事件替换、子节点排序、布局失效和幂等 cleanup 均可由 fake host 观察；失败更新保留最后一次有效树。
- [x] render、component、layout、event、host update 阶段错误可区分并进入统一诊断 seam；单个 event 错误不影响其它控件，render/layout 失败不提交半成品树。
- [x] UI facade、primitive、props、事件对象、signal/store 和 capability 进入 09 的 NormativeApiContract 派生链；TypeScript JSX declaration 与 Probe 输出同源生成，不存在声明有而 runtime 无、或 runtime 有而声明缺失的能力。
- [x] common Module 不导入 Minecraft/loader，不新增 Gradle 项目、API jar、第二 runtime owner、第二事件 bus 或独立声明事实源。
- [x] close/dispose、重复 close、候选失败清理和最后有效树保留都有 fake Adapter 测试；cleanup 不推迟到后续真实客户端票。

## Dependency rationale

- [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。

## Closure record

**执行者**：dsh-agent
**分支**：`ticket-40-jsx-ui-common-core`（基于 `integration/tickets-08-11-12`）
**Status**：closed（8/8 AC 通过）

### commit 区间

`<base 3cd907c3>`..<本轮 commit>`（见本票提交历史；按语义拆分：核心契约与登记表 / 引擎实现 / 测试与契约链 / 证据与关闭）

### 交付物

**公开 Interface（`common/src/main/java/com/tkisor/nekojs/api/ui/`，21 个类型）**

| 类别 | 成员 |
|---|---|
| UI facade | `UiFacade`（generation / createStateOwner / create / open / invalidateGeneration / closeGeneration / roots / dispose）、`Ui`（静态派生视图：primitives / propNames / eventNames / eventTypeNames / propValueKinds / capabilityIds）、`UiSpec` |
| root handle | `UiRootHandle`（rootId / generation / status / isActive / options / open / invalidate / invalidateAndReconcile / dispatchEvent / paint / dispose / inspection / committedTree / focusedPath）、`UiRootStatus`、`UiScreenOptions` |
| VNode | `UiVNode`（sealed）、`HostVNode`、`ComponentVNode`、`FragmentVNode`、`UiProps`、`UiVNodeFactory`、`UiScriptScreen`、`UiComponentRegistry` |
| primitive | `UiPrimitive`（screen/panel/row/column/stack/scroll/label/button/input/image/spacer）、`UiProp`（49 个属性，6 类）、`UiPropCategory` |
| props 规则 | `UiLayoutSpec`、`UiDimension`（auto/fill/pixels/percent）、`UiAlignment`、`UiAnchor` |
| 事件对象 | `UiEvent`（type/坐标/按键/修饰键/targetPath/rootId/generation/timestamp）、`UiEventType`（12 类） |
| signal/store | `UiSignal`（get/set/update）、`UiReadonlySignal`、`UiStore`（read/write/merge/field/snapshot）、`UiStateOwner`（createSignal/createStore/runTick/register/dispose） |
| host Adapter | `UiHostAdapter`（adapterId / primitives / create / update / reorder / bindEvents / dispose / measure / arrange / paint）、`UiHostNode`、`UiHostCreate`、`UiTextMeasurer`、`UiTextMeasure`、`UiSize`、`UiRect`、`UiHostInspection`、`UiResources` |
| 布局约束 | `UiLayoutSpec`（width/height/min/max/margin/padding/gap/align/justify/anchor/grow/visible） |
| 诊断字段 | `UiDiagnosticStage`（render / component / layout / event / host-update）、`UiDiagnostic`（stage/code/message/nodePath/rootId/generation/cause）、`UiDiagnosticSink`、`UiFailure`（12 个稳定错误码） |

**common 实现（`core/ui/`，14 个类）**：`UiPrimitiveRegistry`（唯一登记表）、`RootUiFacade`、`UnavailableUiFacade`、`UiRoot`、`UiReconciler`、`UiLayoutEngine`、`DefaultUiStateOwner`、`DefaultSignal`、`DefaultStore`、`ScriptUiVNodeFactory`、`ScriptUiScreen`、`FakeUiHostAdapter`、`UiJsxDeclarationGenerator`、`UiDiagnostics`/`UiNodePaths`

### 行为保持

- `nekojs/node/modules/jsx-runtime.ts` 导出形状不变（`jsx` / `jsxs` / `Fragment`，模块 id 不变）；
  元素新增 `kind` 字段、`type==Fragment` 时归一为字符串 `'Fragment'`、对象与 props/children 冻结。
  票 12 的 `NekoJsxCompilerTest` / `NekoCompilationPipelineJsxRuntimeTest` /
  `NekoTranspiledLanguagePhaseCorpusTest` 全绿（既有 golden 未改）。
- classic runtime、TS/TSX lowering、ESM/CJS、HostAccess 未改动。
- 未新增 Gradle project / API jar / 第二 runtime owner / 第二事件 bus。

### 逐 AC 判定

| AC | 判定 | 证据 |
|---|---|---|
| AC1 VNode 语义 + 不可变 + 无 host 对象 | **通过** | `UiVNodeContractTest`（字段 final/类型黑名单/拷贝不可变/非法构造）；`UiJsxRuntimeEndToEndTest` 真跑 `jsx-runtime.ts` 断言 `Object.isFrozen` 与改写拒绝；automatic lowering 端到端覆盖函数组件、条件、数组、**spread**、key、children、Fragment |
| AC2 唯一登记表 + 四类显式失败 | **通过** | `UiPrimitiveRegistryTest`（11 标签精确集合、未知 primitive/props/布局值/重复 key）；`UiRootReconcileTest.rootMustBeScreenPrimitive`、`failedCandidateKeepsLastValidTreeAvailable` |
| AC3 signal/store 公开可观察语义 | **通过** | `UiStateOwnerTest`（读取建立依赖、写入只失效受影响 root、同 tick 合并、依赖重建、disposed root 不再失效、非 owner thread 写入/runTick 显式 `OFF_OWNER_THREAD` 且不改变值）。未检查私有依赖集合 |
| AC4 reconciler 全部由 fake host 观察 | **通过** | `UiRootReconcileTest` 15 例：首次顺序、未变化子树不重建、keyed 重排/移除保留身份、事件替换与解绑、布局矩形/resize、paint 顺序与裁剪、adapter 失败归 HOST_UPDATE、原子提交、幂等 cleanup |
| AC5 五阶段可区分 + 单事件错误不扩散 | **通过** | `UiDiagnosticsAndGenerationTest`（render/component/layout/event/host-update 各自阶段与 code、诊断带 rootId+generation、单 event 失败后其它事件继续、render/布局失败不提交半成品） |
| AC6 进入 09 契约链 + 声明同源 | **通过** | `UiContractChainTest`（global:Ui 与 member:Ui.* 在契约、capability id 同源、冻结 surface/manifest 精确一致、runtime 无契约外 UI 符号、facade 成员零漂移、`ManagedApiDeclarationGenerator` 渲染 $Ui/$UiProp/$UiSignal/$UiEvent、JSX 声明与登记表逐 primitive/属性相等）。新增 `UiJsxDeclarationGenerator`（intrinsic elements + props + 事件 + signal/store + facade，全部迭代 Java 登记表） |
| AC7 common 零 MC/loader、无第二事实源 | **通过** | `guardLint` 0 违规（248 守卫/331 文件，与改前同）、`:common:check` 含 `checkCommonIsolation`；未新增 Gradle project/API jar/runtime owner/事件 bus；`Ui` 静态方法只返回派生列表，无 UI 专用 global singleton 或静态跨 generation registry |
| AC8 close/dispose/重复 close/候选失败清理 | **通过** | `UiDiagnosticsAndGenerationTest`（dispose 幂等每节点恰好释放一次、closeGeneration 幂等、跨 generation 不误关、candidate 校验失败零创建、facade dispose 后 create 显式失败）+ `UiRootReconcileTest.disposeIsIdempotentAndReleasesEveryHostNodeOnce` |

### code-review 遗留

1. **`NekoTypeScriptCompiler` 擦除器误删三元裸标识符 else 分支**（既有缺陷，本票暴露）。
   `cond ? 'a' : bareIdentifier` 被擦成 `cond ? 'a' :`，后续语句语法错误。
   本票以 if/return 等价写法规避，**未改擦除器**（属票 12 语言管线域）。建议单开一条修复票。
2. **`Value.asProxyObject()` 对普通 JS 函数抛 `ClassCastException`**（本票自身实现中已修，
   记录供其它调用点参考：`componentNameOf` 改读函数 `name` 属性）。
3. **`HostVNode`/`UiProps` 的 `List.copyOf` 拒绝 null**：条件渲染的 null 由
   `UiVNodeFactory`（真实脚本路径）与 `UiReconciler.collect`（防御性）过滤；Java 侧直接用
   `List.of(..., null, ...)` 会 NPE。属类型系统固有行为，已在测试中显式固化。
4. **scroll 的裁剪/偏移语义不完整**：`scrollX/scrollY` 已登记为受控属性，但 `UiLayoutEngine`
   目前把 `scroll` 按 stack 处理，未实现内容尺寸、裁剪区域与滚动偏移的完整语义。
5. **`UiLayoutEngine` 的 `justify: space-between/space-around`** 只实现 center/end 的位移，
   未实现 space-between/around 的分布间距。
6. **`inspection.paintOperations`** 只在显式 `paint()` 调用后更新。

### 冻结面（golden）变更——需维护者审阅

`common/src/test/resources/nekojs/golden/api-manifest-core.json`：**216 行插入、0 行删除**（纯新增）。
符号 148 → 184（新增 global:Ui、8 个 member:Ui.*、UiProp/UiSignal/UiStore/UiEvent 的 36 个访问器）。
原因：AC6 要求 UI 进入契约派生链。capabilities/modules 条目未变（既有 `buildManifest` 形状只取 symbols）。
同步修改的既有测试：`CoreManagedApiBootstrapTest`（109→118 / 7→8）、
`NormativeApiContractOwnerTest`（测试侧反射输入清单加 UI 输入）。
详见 [证据报告](../../baseline/2026-09-13-jsx-ui-common-core/REPORT.md)。

### 未验证项与 owner

| 未验证项 | owner |
|---|---|
| 真实 Minecraft 客户端 Screen / 输入 / 绘制 / resize（**本票未做任何真实客户端尝试，未用 MCP**） | 票 41（NeoForge 26.2 Adapter） |
| 性能 P0 基线（首次构建/增量/输入/持续绘制） | 独立 P0 工作项 |
| 六档 Viewport Profile 与网页转换（规格 51-70） | 后续转换票 |
| scroll 完整裁剪/偏移语义、space-between/around 分布 | 后续 UI 增强（见遗留 4/5） |
| 其它 MC 节点的 UI capability 等级 | 各节点 Adapter 完成后按真实证据裁定 |

### 证据

- [基线证据报告](../../baseline/2026-09-13-jsx-ui-common-core/REPORT.md)
- [生成的 JSX 声明](../../baseline/2026-09-13-jsx-ui-common-core/jsx-ui.generated.d.ts)
- [生成的 automatic runtime 模块声明](../../baseline/2026-09-13-jsx-ui-common-core/jsx-runtime.generated.d.ts)

**验证命令与结果**：`:common:test --rerun-tasks --no-build-cache` → 1596 tests / 0 failures / 5 skipped；
`:common:check` → SUCCESS；`guardLint` → 0 违规 0 警告。
