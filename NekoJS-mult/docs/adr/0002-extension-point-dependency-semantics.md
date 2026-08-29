# 扩展点依赖语义：双轨声明 + 拓扑排序 fail-fast

E1（ADR-0001）已锁定"顺序 = 注册序 + 显式依赖声明、无插队 API、内置先行固定相位"。本 ADR 定依赖的**具体表达与执行模型**：两类依赖，一种机制。

1. **时序依赖** = builder 上 `dependsOn(POINT...)`：仅需对方先完成、不读产物（Spring depends-on 区分两类依赖的先例）。freeze 后对全部 `dependsOn` 边做 Kahn 拓扑排序；发现环 → **fail-fast，报错打印完整环路径**（`a → b → c → a`），不提供任何运行期"打破环"手段（环是配置错误，自动打破只会把错误藏进不确定顺序；OSGi SCR fail-fast 先例）。
2. **数据依赖** = initializer / collector 里 `context.result(POINT)`：**免声明**。拓扑序保证被引用点必然已 finish；运行期发现未 finish（漏 `dependsOn` 或图被改坏）立即抛 `IllegalStateException`，报错附修复指引（"declare dependsOn(...)"）——漏声明不可能静默出错。
3. **同层顺序**：拓扑同层（互不依赖的点）按注册序执行，仅作稳定 tiebreaker；文档明确"不要依赖同层顺序"，跨同层正确性必须靠 `dependsOn`。
4. **`dependsOn` 指向未注册 id** → freeze 时报错（拼错早爆，优于运行期谜语）。
5. **被环境跳过的依赖点**（如专用服务器上的 client-only 点）不阻塞依赖方执行；产物缺席由使用处 `result()`（null）/ `resultOrThrow()`（抛错）表达。
6. **依赖粒度只到点级**（整点先完成），不提供增量 / 部分产物依赖。

## Considered Options

- 全显式（数据依赖也必须 `dependsOn`）：弃——"既 dependsOn 又 result 同一个点"是常态写法，重复声明是纯噪音。
- "引用即依赖、自动记边"（RE2 备选）：弃——需要引擎在运行期簿记查询历史，且发现违序时已无法事后改序；运行期抛错 + 修复指引以更少的机制达到同等安全。

## Consequences

- 拓扑排序 + 环检测成为 bootstrap freeze 阶段的一部分，在任何收集开始前执行。
- PR #37 痛点②（`registry_object_types` 依赖 `registry_infos`）的标准写法：`dependsOn(RegistryInfosPoint.POINT)` + initializer 里 `ctx.result(RegistryInfosPoint.POINT)`。
- 内置先行相位是图的根部天然约束，无需特殊排序代码。
