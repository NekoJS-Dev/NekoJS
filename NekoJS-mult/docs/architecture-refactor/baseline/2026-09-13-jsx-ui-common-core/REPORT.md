# 证据：JSX UI common core（票 40）

> 执行者：dsh-agent。分支 `ticket-40-jsx-ui-common-core`。
> 本目录只记录**已实际执行**的验证；未执行项在「未验证项」明确列出。

## 1. 验证命令与结果

| 命令 | 结果 |
|---|---|
| `:common:test --rerun-tasks --no-build-cache` | BUILD SUCCESSFUL；**1593 tests, 0 failures, 5 skipped** |
| `:common:check` | BUILD SUCCESSFUL |
| `guardLint` | BUILD SUCCESSFUL；守卫块 248 / 扫描 331 文件 / 违规 0 / 警告 0（改前同为 248/331） |
| `checkCommonIsolation`（:common:check 依赖） | 通过（common 零 MC/loader import） |

`--rerun-tasks --no-build-cache` 为全量真跑；单独验证 `ScriptReloadGenerationTest`
（票 11 记录的共享 gameDir 污染形状）在完整全量跑法下通过。

## 2. 新增/修改测试清单（8 个测试类，52 个测试）

| 测试类 | 覆盖 AC |
|---|---|
| `core/ui/UiPrimitiveRegistryTest`（11） | AC2 |
| `core/ui/UiVNodeContractTest`（12） | AC1 |
| `core/ui/UiRootReconcileTest`（15） | AC4、AC8 |
| `core/ui/UiStateOwnerTest`（8） | AC3 |
| `core/ui/UiDiagnosticsAndGenerationTest`（13） | AC5、AC8 |
| `core/ui/UiContractChainTest`（10） | AC6、AC7 |
| `core/ui/UiJsxRuntimeEndToEndTest`（7） | AC1（真实 runtime 端到端） |
| `core/ui/UiTestFixture`（夹具） | — |

## 3. 冻结面（golden）变更：需维护者审阅

**变更文件**：`common/src/test/resources/nekojs/golden/api-manifest-core.json`

**旧/新 diff 摘要（`git diff --stat`）：**

    .../resources/nekojs/golden/api-manifest-core.json | 216 +++++++++++++++++++++
    1 file changed, 216 insertions(+)

- **纯新增，0 删除**：`git diff -U0` 的删除侧只有 `--- a/...` 头。
- 新增 36 个符号（116 → 152... 见下）、0 个 capability 条目、0 个 module 条目：

| 类别 | 新增符号 |
|---|---|
| facade | `global:Ui`、`member:Ui.create`、`member:Ui.open`、`member:Ui.roots`、`member:Ui.dispose`、`member:Ui.generation`、`member:Ui.createStateOwner`、`member:Ui.invalidateGeneration`、`member:Ui.closeGeneration` |
| UiProp | `jsName` / `category` / `isEvent` / `isLayout` |
| UiSignal | `get` / `set` / `update` |
| UiStore | `read` / `write` / `merge` / `field` / `snapshot` |
| UiEvent | 15 个 record 访问器 |

JSON 符号总数：**148 → 184**。

**变更原因**：AC6 要求 UI facade / primitive / props / 事件对象 / signal-store 进入 09 的
`NormativeApiContract` 派生链。golden 是派生观测物，契约输入增加必然反映到 golden。

**未做的事**：本票**未**修改任何 capability 条目（`capabilities: []` 保持为空）——
UI capability 已声明在契约的 capabilities 列表，但 golden 的 `buildManifest() 只传
`contract.symbols()` 与空 capability 集合（既有测试形状），故 golden 无 capability 行。

**需维护者审阅**：按票据硬性约束 5，golden 更新必须有旧新 diff 与审阅记录。此处仅做
`-Dnekojs.golden.regenerate=true` 再生成，diff 为纯新增；未做语义删除或改写。

## 4. 同步修改的既有契约 owner 测试

| 文件 | 变更 |
|---|---|
| `core/api/CoreManagedApiBootstrapTest` | 109 → 118（contributions）、7 → 8（globalImplementations），并断言 `UI_GLOBAL` |
| `core/api/NormativeApiContractOwnerTest` | 测试侧重放的反射输入清单加入 UI facade + 4 个数据类型（与 `buildContract` 逐元素对齐） |

这两个测试是 09 的「契约输入 == 反射集合」双记账守卫；输入集合变化必须同步，否则测试变红。

## 5. 发现并修掉的既有缺陷（本票暴露）

1. **`NekoTypeScriptCompiler` 擦除器误删三元表达式裸标识符 else 分支。**
   实测：`type === FRAGMENT ? 'Fragment' : type` 被擦成
   `type === FRAGMENT ? 'Fragment' :`（else 分支连同后续逻辑丢失，语法错误）。
   本票以 if/return 等价写法规避，**未修改擦除器**（超出本票范围，属票 12 的语言管线域）。
   证据：`UiJsxRuntimeEndToEndTest.runtimeBindings` 的擦除断言 + 端到端求值。

2. **`Value.asProxyObject()` 对普通 JS 函数抛 `ClassCastException`。**
   本票 `ScriptUiVNodeFactory.componentNameOf` 初版用它取组件标识，实测 JS 函数
   （非 Graal host proxy）直接抛错；改为读函数 `name` 属性并退回稳定序号。

## 6. 未验证项

- **真实 Minecraft 客户端**：本票范围内**未**做任何真实客户端尝试，**未**使用 Minecraft MCP，
  也**未**运行 `runGameTestServer`。真实 Screen/输入/绘制接线归票 41（NeoForge 26.2 Adapter）。
  本票全部证据来自 fake host Adapter。
- **性能基线**：未测。规格要求 P0 baseline 在独立工作项，本票未设定也未测任何阈值。
- **六档 Viewport Profile / 网页转换**：属规格 51-70 的后续范围，本票未实现。
- **gate 布局收敛**：`UiLayoutEngine` 实现 measure/arrange 与 row/column/stack/scroll
  的主轴、交叉轴对齐、gap/padding/margin/min/max/fill/percent；**未**实现 scroll 内容裁剪与偏移的
  完整语义（`scrollX/scrollY` 属性已登记但在布局引擎中被当作普通 stack 处理）。