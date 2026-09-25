# 45: UI Inspector、布局测量与截图差异基线

**What to build:** 提供 JSX UI 的公开 Inspector contract 与 fake/NeoForge 双输出：当前 profile、逻辑视口、安全区域、节点树、矩形、裁剪、最终解析样式、溢出、资源状态、事件绑定摘要和 layout diagnostics；支持采集 actual 截图并与参考图/参考尺寸输出差异报告。Inspector 是 runtime 事实工具，不依赖 AI 文档、网页转换映射或自动转换器。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)
- [43: 六档 Viewport Profile 与响应式布局系统](43-jsx-ui-viewport-profiles.md)
- [44: 文本测量、视觉样式、图片与受控资源解析](44-jsx-ui-text-visual-assets.md)

**Status:** in-review

**Assignee:** workbuddy-kimi-45（main-session agent；mult worktree）

**Claim record:** 2026-09-25 由 workbuddy 主会话 agent 在 mult 分支认领。Blocker 偏差：40/41/43/44 当前为 in-review（未 closed），照票 46/42/44 先例——阻塞票已交付待审、且用户直接指令"继续来一张"构成源码实施的另行授权，遂认领。预计改动范围：Inspector contract、fake/NeoForge 双输出、测量 golden、截图差异基线相关文件（实现主体已在 1136f30a 批次落地，本票负责验证闭环与缺口修复）。

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [x] fake Adapter 与 NeoForge 26.2 均可输出同构 Inspector record；字段来自公开 host contract，不暴露 Java 对象身份、原生 widget、`GuiGraphics` 或内部 reconciler 结构。【evidence: `InspectorSnapshots.read` 同时接受 guest `Value` 与 host `Map/List` 数据并走同一实现；`Ticket45InspectorOutputTest.hostObjectPathProducesTheIsomorphicRecord` 钉住双路径结构全等；record 全为冻结拷贝（`List.copyOf`/unmodifiableMap），`GuestReader.plain()` 物化嵌套数据；`Ticket45InspectorSmokeTest.adapterPublishesThePublicInspectorContract` 钉住 `JsxHostAdapter implements UiInspector`】
- [x] Inspector 能定位节点、最终 props、resolved style、profile、矩形、裁剪、滚动偏移、焦点、事件绑定摘要、资源引用和错误阶段。【evidence: `InspectorNode`（id/type/key/style=最终 resolved props/rect/clip/overflow/scrollOffset/focused/bindings/resources）+ `InspectorSnapshot`（viewport.profile、diagnostics、`PhaseError(phase,rootId,message)`）；`decoratedRecordsCarryBindingsFocusResourcesAndErrorPhase` 逐项钉住 id 定位、bindings=[blur,click]（runtime 侧 `.sort()` 排序，jsx-runtime.ts:692）、focused、RESOLVED 资源、scrollOffset=2、error phase=layout】
- [x] 六个 Viewport Profile 都能生成稳定测量输出；输出可作为 golden，普通测试只读，更新必须走旧新 diff 与维护者审阅。【evidence: `sixProfileDecoratedOutputMatchesTheGoldenFile` 对 `six-profiles-golden.txt` 做全串精确比对（六档 profile 各一段，含 diagnostics/errors/screenshot/节点树），断言消息明示 golden 只读、有意变更须审阅旧新 diff；profile 值来自票 43 的公开 `resolveViewport` 规则】
- [x] actual 截图或等价像素 evidence 只作为辅助，必须与公开测量/行为断言配合，不把截图对象或私有渲染缓冲作为脚本 API。【evidence: `InspectorScreenshot` 仅 source/width/height 元数据，无像素/缓冲；`screenshotAndReferenceImageStayProvenanceNotEntries` 钉住截图与参考图从不产生差异条目；脚本 API 无截图对象暴露。限制：真实像素采集未接线（须 live client），契约文档与 Javadoc 均已如实标注】
- [x] 差异报告能指出偏差最大的节点/属性，并保留输入、环境、profile、参考图和实际输出来源。【evidence: `SnapshotDiff` 条目按 deviation 降序（结构断裂 MAX_VALUE 居首），`largest()` 给出 nodePath+field；provenance 含 referenceSource/actualSource、双方 profile 与 viewport、referenceImage、actualScreenshot；`largestDeviationIsRankedFirstAndNamesNodeAndField`、`structuralBreakOutranksAnyMeasurementDrift`、`controlledFieldChangesProducePreciseEntries` 钉住】
- [x] 45 的实现与验收不读取、不依赖 46/47 的 AI 文档或网页转换规则；后续 AI 工作流只消费 45 输出。【evidence: ticket-45 全部源码/测试/golden/fixture grep 无 46/47 产物引用；`ui-inspector-contract.md` 明示 "depends on no AI authoring documentation and no web conversion rules"】

## Delivery record (2026-09-25, workbuddy-kimi-45)

- Status 转 `in-review`（交付待维护者审阅，非签收）。实现主体随 1136f30a 批次落地；本轮完成认领、逐条 AC 验证、双轴 review（Standards + Spec 子代理）、修复与测试闭环。
- **Review 修复（本轮新改）**：
  1. `JsxHostAdapter.inspect()` 截图元数据改取 `lastSnapshot.viewport()`（原为实时 viewport——resize 后未 layout 时会给出与保留帧不一致的尺寸；Spec/Standards 双轴均指出）。
  2. `InspectorSnapshots.read` 统一把标量强转失败包装为 NEKO-8001（原 guest 侧 `PolyglotException`/`ClassCastException` 会绕过稳定码）；`HostReader.asBoolean` 改严格模式，与 guest 路径在 malformed 输入上保持同构。新增 `malformedHostScalarIsRejectedWithTheInspectorCode` 钉住。
  3. `SnapshotDiffer.compareFrame` 补比 `guiScale`/`designScale`（环境输入原漏比；contentWidth/Height 可推导，注释说明不比）。新增 `guiScaleAndDesignScaleDriftAreFrameEntries` 钉住。
  4. 移除 `Reader` 接口无消费者的 `asInt()`（Speculative Generality；`GuestReader.plain()` 保留私有实现）。
  5. 文档对齐：`ui-inspector-contract.md` 的 `neoforge-frame` 更正为实现的 `neoforge-viewport-meta`；"Viewport profile ownership (ticket 43 pending)" 一节与 `InspectorViewport` Javadoc 更新为现状（43 已交付 in-review，profile 值即来自其规则）。
- **记录不改的判断项**：① Spec 轴指出 differ 排名混合量纲（离散恒 1、结构 MAX_VALUE）——排名语义已被契约文档与测试钉住，改动属维护者决策，如实记录；② `read(String,String,Value)` 重载是 guest 主入口的便利签名，保留；③ `readBindings`/`readDiagnostics` 小重复，可接受。
- **诚实未勾/限制**：AC4 的真实像素采集未接线（无 live Minecraft client，同 41/42/43/44 口径）；NeoForge host 的 `inspect()` 全链路只能在真实客户端验证（Minecraft-free smoke 已钉 collect/decorate/diff 契约）。
- **验证**：焦点套件修复后 `:common:test`（SnapshotDifferTest 6、Ticket45InspectorOutputTest 5、NekoTypeScriptJsxRuntimeTest 13）与 `:26.2.0:test`/`:26.1.2:test`（Ticket45InspectorSmokeTest 各 3）全绿；全套无过滤器结果见下。
- 全套（修复后无过滤器）：`:common:test :26.1.2:test :26.2.0:test :1.21.1:test :26.1.2-fabric:compileJava :26.2.0-fabric:compileJava` → BUILD SUCCESSFUL；common 1930 tests / 4 skipped / 0 失败，26.1.2 与 26.2.0 各 438 / 58 / 0，1.21.1 313 / 14 / 0；两 fabric 节点编译通过（票 42/44 的守卫剥离口径）。

## Dependency rationale

- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [43: 六档 Viewport Profile 与响应式布局系统](43-jsx-ui-viewport-profiles.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [44: 文本测量、视觉样式、图片与受控资源解析](44-jsx-ui-text-visual-assets.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
