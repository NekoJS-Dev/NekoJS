# 证据：JSX UI common core（票 40）

> 执行者：dsh-agent。分支 `ticket-40-jsx-ui-common-core`。
> 本目录只记录**已实际执行**的验证；未执行项在「未验证项」明确列出。
> 本文件在 code-review 修订后按**实测值**重写（旧版曾出现自相矛盾的数字）。

## 1. 验证命令与结果（修订后）

| 命令 | 结果 |
|---|---|
| `:common:test --rerun-tasks --no-build-cache` | BUILD SUCCESSFUL；**1602 tests, 0 failures, 5 skipped** |
| `:common:check` | BUILD SUCCESSFUL |
| `guardLint` | BUILD SUCCESSFUL；守卫块 248 / 扫描 331 文件 / 违规 0 / 警告 0（与改前同） |
| `checkCommonIsolation`（:common:check 依赖） | 通过（common 零 MC/loader import） |

`--rerun-tasks --no-build-cache` 为全量真跑。

## 2. 测试清单（10 个类，85 个 @Test，实测）

| 测试类 | @Test | 覆盖 AC |
|---|---|---|
| `core/ui/UiPrimitiveRegistryTest` | 11 | AC2 + 三方一致性 |
| `core/ui/UiVNodeContractTest` | 12 | AC1 |
| `core/ui/UiRootReconcileTest` | 15 | AC4、AC8 |
| `core/ui/UiStateOwnerTest` | 8 | AC3 |
| `core/ui/UiDiagnosticsAndGenerationTest` | 13 | AC5、AC8 |
| `core/ui/UiContractSemanticsTest` | 6 | AC2、AC3、AC8（code-review 修订新增） |
| `core/ui/UiContractChainTest` | 10 | AC6、AC7 |
| `core/ui/UiJsxRuntimeEndToEndTest` | 9 | AC1（真实 runtime 端到端） |
| `core/ui/UiDeclarationEvidenceTest` | 1 | AC6（TypeDocsRegister 可达性） |
| `core/ui/UiTestFixture` | 0 | 测试夹具 |
| **合计** | **85** | |

## 3. 冻结面（golden）变更——需维护者审阅

**变更文件**：`common/src/test/resources/nekojs/golden/api-manifest-core.json`

### 3.1 旧/新 diff（相对票 40 之前的冻结基线）

票 40 首次交付时相对基线为 **216 行纯新增、0 删除**；code-review 修订后再次变更两行：

```diff
-      "id": "member:UiProp.isLayout",
+      "id": "member:UiProp.jsName",
-      "id": "member:UiProp.jsName",
+      "id": "member:UiProp.kind",
```

即：撤下 `UiProp.isLayout()`（零消费者的推测性公开 API），新增 `UiProp.kind()`
（取值种类唯一事实源）。第二次变更净增 1 个符号（`kind` 进、`isLayout` 出，排序位移）。

### 3.2 数字（实测）

| 指标 | 基线（票 40 前） | 票 40 首次交付 | code-review 修订后 |
|---|---|---|---|
| JSON 符号总数 | 148 | 184 | **184** |
| 其中 UI 符号 | 0 | 36 | **36** |
| capabilities 条目 | 0 | 0 | **0** |
| modules 条目 | 0 | 0 | **0** |
| golden 文件字节 | 16514 | 20035 | **20035（含排序位移）** |

UI 符号构成：`global:Ui` 1 个；`member:Ui.*` 8 个（facade 的脚本可见成员）；
`member:UiProp.*` 4 个（`jsName` / `category` / `kind` / `isEvent`）；
`member:UiSignal.*` 3 个；`member:UiStore.*` 5 个；`member:UiEvent.*` 15 个（record 访问器）。

### 3.3 变更原因

AC6 要求 UI facade / primitive / props / 事件对象 / signal-store 进入 09 的
`NormativeApiContract` 派生链。golden 是派生观测物
（`ApiManifestGoldenTest` 的输入就是 `contract.symbols()`），契约输入增加必然反映到 golden。

### 3.4 审阅记录（规格 149）

- **旧符号数**：148 → **新符号数**：184（净 +36）。
- **纯新增结论**：首次交付 216 行插入 / 0 行删除，删除侧只有 `+++`/`---` 文件头；
  修订轮的两行 diff 是「撤下 isLayout + 新增 kind」的成对替换，均为 UI 符号。
- **是否有语义删除**：无。未修改任何既有符号的签名，未改动非 UI 符号。
- **capability/module**：golden 中仍为 `[]`（既有 `buildManifest()` 形状只传 `contract.symbols()`）。
  UI capability 已声明在**契约**的 `capabilities` 列表，由 `UiContractChainTest` 覆盖，
  不经过该 golden。
- **契约 version**：仍为 `schemaVersion = 2`，**未递进**。理由：schemaVersion 描述
  `NormativeApiContract` 的**文档 schema**（字段结构），本次只增删 symbol 条目、未改任何
  字段结构；API 语义版本由 `ApiContractIdentity.version`（`api-runtime.properties` 的
  `0.12.0`）承载，其推进属 1.2.0 clean cutover 的发布决定，不由单张 feature 票改动。
- **维护者待审标记**：⚠ 本 golden 变更**未被人工审阅**，仅由 agent 以
  `-Dnekojs.golden.regenerate=true` 再生成并记录 diff。请维护者核对上述 diff 后再接受。

### 3.5 同步修改的既有契约 owner 测试

| 文件 | 变更 |
|---|---|
| `core/api/CoreManagedApiBootstrapTest` | 109 → 118（contributions）、7 → 8（globalImplementations），断言 `UI_GLOBAL` |
| `core/api/NormativeApiContractOwnerTest` | 测试侧重放的反射输入清单加入 UI facade + 4 个数据类型 |

这两个测试是 09 的「契约输入 == 反射集合」双记账守卫；输入集合变化必须同步，否则测试变红。

## 4. code-review 修订（第 1/2 项为「契约说谎」修复）

### 4.1 契约说谎 A：`VISIBLE` / `FIT` / `SCROLL_*` 无实现

**判定与处置**：
- `VISIBLE`：**实现语义**。`UiReconciler.expandOne` 在候选择阶段剔除 `visible=false` 的节点，
  使其不创建 host node、不参与布局/绘制/已提交树。
- `FIT`：**从冻结契约撤下**（`UiProp.FIT` 删除）。图片适配语义依赖真实纹理能力，属票 41 范围。
- `SCROLL_X` / `SCROLL_Y` / `SCROLLABLE`：**从冻结契约撤下**（`UiProp` 中删除）。`scroll`
  primitive 保留（内容裁剪/偏移语义归后续 UI 增强票），但不再承诺未生效的滚动属性。

### 4.2 契约说谎 B：`invalidate()` 承诺停止接受新事件但实现不拒

**处置**：新增 `UiFailure.ROOT_INVALIDATED` 与 `UiRoot.requireEventAccepting`；
`dispatchEvent` 现在对 `INVALIDATED` root 显式失败（与 `invalidateAndReconcile` 的既有拒绝一致）。
`paint()` 仍允许（保留最后一次有效树供显示，符合规格 127「停止接受新事件」而非「停止显示」）。

### 4.3 AC3 线程语义覆盖补齐

新增 `UiContractSemanticsTest`：非 owner thread 的 `dispatchEvent`、`open`、`store.write`、
`store.merge` 均断言 `OFF_OWNER_THREAD` 且不改变状态。

### 4.4 死代码与推测性公开 API 清理

| 项 | 处置 |
|---|---|
| `UiReconciler.Candidate.order`（从未赋值） | 删除，改为在展开时赋值的 `keylessSlot` |
| keyless 序号在 `identity()` 与 `diffChildren` 两处重算 | 收敛到 `Candidate.identity(...)` 单一实现 |
| `UiPrimitiveRegistry.validateUniqueKeys` + `KeyedTag`（仅测试调用） | 删除；重复 key 由 reconciler 唯一实现 |
| `UiHostAdapter.measure`（零调用者） | 删除（布局引擎自持 measure） |
| `FakeUiHostAdapter.textMeasurer()` / `measurements()` | 删除 |
| `UiRoot.commitCount()` | 删除 |
| `Ui.propValueKinds()` / `Ui.capabilityIds()` / `propNames()` / `eventNames()` / `eventTypeNames()` | 删除（只保留 `primitives()`） |
| `UiProp.isLayout()` | 删除（零消费者） |
| `UiStateOwner.hasPendingInvalidation()` | 删除（测试改用可观察的 reconcile 次数） |
| `UiSpec.EVENT_GROUP` / `RESOURCE_TYPE` / `EVENT_HANDLER_TYPE` | 删除（不预留未接入的常量） |

### 4.5 `UiJsxDeclarationGenerator` 接入 Probe 派生链

**处置：不降级**。新增 `UiJsxDeclarationGenerator.register(TypeDocsRegister)`，由生产入口
`NekoCommonBuiltinPlugin.registerNodeTypeDocs` 调用——声明与 node 模块声明同源进入既有
probe/workspace 派生链，**未新增 Point / catalog / 声明 DSL**。
测试 `UiDeclarationEvidenceTest` 经该入口断言可达，并落盘到 `@TempDir`（不再写生产目录）。

### 4.6 其它建议项

- **属性语义三处平行**：已把取值种类挂到 `UiProp.kind()`（新 `UiValueKind`），取值校验与
  TS 类型都从它派生；`UiJsxDeclarationGenerator` 不再持有「属性名 → 类型」表。
  三方一致性由 `UiPrimitiveRegistryTest` 与 `UiContractChainTest` 钉住。
- **错误消息带修复指引**：`ScriptUiVNodeFactory` 的 `"; use a component tag instead"` 已删。
- **`IPlatform` 末尾换行**：已补回。
- **重复 key 报错文案重复**：收敛到 `UiReconciler.duplicateKey(...)` 单一方法。
- **api/ui Javadoc 指名 core 实现类**：已改为描述契约语义（api/ui 下 `grep core.ui` 为 0）。
- **`props.string(UiProp.X.jsName())` 字符串键双写**：新增 `has/raw/string/number/bool` 的
  `UiProp` 重载并全部改用。

## 5. 本票暴露并修复的既有缺陷

**`Value.asProxyObject()` 对普通 JS 函数抛 `ClassCastException`**（Graal API 既有语义）。
本票 `ScriptUiVNodeFactory.componentNameOf` 初版用它取组件标识，实测抛错；改为读函数
`name` 属性并退回稳定序号。**已在本票内修复**。

## 6. 未复现的申报（如实记录）

我在首轮报告中申报「`NekoTypeScriptCompiler` 擦除器误删三元表达式裸标识符 else 分支」，
并在 `jsx-runtime.ts` 中以 if/return 写法规避。主 agent 用 15 种三元形态在 t40 HEAD 与票 12
之前的基线 `4e258d3a` 上各测一遍，**两处均未复现**。我接受该复核结论：**撤回申报，不计为缺陷、
不开新票**。`jsx-runtime.ts` 中的 if/return 写法作为等价实现保留（避免依赖未证实的行为）。

## 7. 未验证项

- **真实 Minecraft 客户端**：本票范围内**未**做任何真实客户端尝试，**未**使用 Minecraft MCP，
  也**未**运行 `runGameTestServer`。真实 Screen/输入/绘制接线归票 41。全部证据来自 fake host Adapter。
- **性能基线**：未测（独立 P0 工作项，阈值其后决定）。
- **六档 Viewport Profile / 网页转换**：属规格 51-70 后续范围，未实现。
- **scroll 完整裁剪/偏移**、**`justify: space-between/around` 分布**：未实现，相关属性已从
  冻结契约撤下（`scrollX`/`scrollY`/`scrollable`）。`scroll` primitive 与 `justify` 的
  center/end 位移已实现。
- **`inspection.paintOperations`** 只在显式 `paint()` 调用后更新。
