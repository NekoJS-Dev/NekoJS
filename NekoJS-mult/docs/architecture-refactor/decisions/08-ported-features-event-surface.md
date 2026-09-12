# 搬运功能如何适配 NekoJS 事件面与运行时扩展？

Status: closed
Type: grilling
Mode: HITL
Parent: [NekoJS 架构重构路线图](../../architecture-refactor-map.md)
Assignee: 主 agent + 维护者共同裁决
Blocked by: [脚本表面与插件作者模型如何只有一个事实源？](04-public-contract-and-plugin-model.md)、[运行时所有权、reload 与数据保护的契约是什么？](05-runtime-lifecycle-and-data.md)、[怎样以可验证的阶段完成本次重构并作为新标准？](07-validation-and-migration.md)

## Question

哪些从其他 mod 搬来的静态 binding/manager 应事件化，哪些应保留为 Adapter/工具 binding？

搬运功能不能因为原实现存在静态入口就自动进入 Managed Surface 事件面，也不能把启动期注册、运行期注册、查询和动作混成一条注册路径。需要区分：

- 有明确生命周期、多个脚本/插件贡献、注册/reload/事务提交语义的公开面，哪些应进入 Managed Surface 事件面；
- 工厂、查询、运行时命令、发送动作等无上述事件语义的面，哪些继续作为 binding/Adapter；
- Villager Trades、Dynamic Registry、PostEffects 等具体搬运域分别落在哪条现有事件或运行时扩展路径，如何避免第二注册路径和重复事件。

证据入口：[实施交接单](../implementation-handoff.md)、[架构重构方案](../proposal.md)。本票只裁定归属与迁移方向，不冻结精确事件名、payload、`remove`/`replace` 语义或 Fabric parity；这些进入 W5-W7 的 contract/capability/golden 验证。

## Resolution

### 事件化边界

只有有明确生命周期、多个脚本/插件贡献、注册/reload/事务提交语义的面，才进入 Managed Surface 事件面。工厂、查询、运行时命令、发送动作继续作为 binding/Adapter；不造万能 Event Module、第二注册路径或无真实需求的 Point。

### Villager Trades

- Villager Trades 进入现有 `ServerEvents` 的数据/reload 子事件，本票只记录工作名，不冻结最终事件名。
- 第一版公开面保留 `add` 与稳定 query；`remove`、`replace`、`modify` 不在第一版公开，待冲突、顺序、回滚契约另行闭合。
- 实际 registry mutation 只放在版本/平台 Adapter。
- Fabric 当前记为 `unavailable`；1.21.1/26.x 能力按实际验证结果公开，不能静默 no-op。

### Dynamic Registry

- 用户要求保留 Dynamic Registry，并希望公开脚本面尽量使用事件。
- 采用“两层不重复”：普通 `RegistryEvents.register` 仍是启动期声明注册；Dynamic Registry 公开为服务器运行期动态注册事件，工作名为 `ServerEvents.dynamicRegistry`，本票不把它写成最终冻结 API。
- 底层仍由 Registry Runtime + platform/version Adapter 负责 claim/stale/cleanup/数值 ID/同步/registry surgery。
- 不把运行时注册伪装成启动期 `RegistryEvents`。
- Fabric/1.21.1 capability 显式记录。

### Dynamic Registry 运行时语义（补充）

- 公开面是服务器运行期的生命周期收集事件，可热重载；初次 server registry ready 与每次成功的 script/data reload 都会触发。事件只收集本批计划，不在任意脚本线程即时执行 registry mutation。
- 事件声明沿用 NekoJS typed builder，并复用现有 `RegistryEventJS`/`ItemBuilder` 等形状的声明方向；不使用通用 `{type: ..., ...}` catalog。精确 builder API 与开放 registry 类型尚未冻结。
- 一批计划收集完成后依次执行 preflight、同 key 定义 fingerprint/冲突检测、服务端 prepare、客户端 prepare/ack，最后原子 commit。任一步失败都整批不提交，并保留旧 active state。
- 脚本不再声明的已暴露项标记为 stale/retired；普通 reload 不物理删除。未来如需替换、更新或迁移，另开显式且可验证的 replace/update/migration 机制。
- 同 key 定义变化第一版按冲突失败，不静默覆盖。客户端同步失败不得产生半成功状态；不可完成同步的节点按 capability 处理。
- 本节仅补充 Dynamic Registry 已确认的运行时语义，不冻结最终事件名、payload、builder 方法、开放 registry 类型或多人同步协议。

### PostEffects

- `register` / `unregister` 这类声明/reload 面进入现有 `ClientEvents` 的客户端资源/reload 子事件。
- `set` / `clear` / `toggle` / `current` 等运行时操作保留 binding/Adapter。

### 复用与不重复

- Assets 复用已有 `ClientEvents.generateAssets`，不新增第二个 Assets 事件。
- EntitySelectors 保持 factory/query binding。
- recipe/loot/tags/JEI/capability/goal/render 等已有事件不重复造。

### 票范围

本票只裁定归属与迁移方向，不冻结精确事件名、payload、`remove`/`replace` 语义或 Fabric parity。这些进入 W5-W7 的 contract/capability/golden 验证。
### Typed Builder 与 JavaBean-style property（补充）

- Dynamic Registry 的脚本 Builder 必须沿用 NekoJS 的 typed Builder 形状，不使用通用对象 catalog。第一版只开放已完成运行期 Adapter、事务和同步验证的 registry 类型；当前普通 `RegistryEvents` 的 `ItemBuilder`、`BlockBuilder`、`FluidBuilder` 只能按可复用的配置形状和类型事实源借鉴，不能直接复用其启动期 `drain()` 或会污染全局状态的构建路径。
- 属性和显式 setter 允许同时存在，并且必须进入同一条校验、规范化、definition fingerprint 和事务收集路径。目标写法可同时支持：

  ```js
  event.item("nekojs:example", item => {
    item.setMaxStackSize(16)
    item.maxStackSize = 16
  })
  ```

  这里的 `maxStackSize` 是 JavaBean-style property：由私有状态、`getMaxStackSize()` 与 `setMaxStackSize(...)` 暴露；不得同时保留同名 public Java field，让脚本写入绕过校验、指纹和收集状态。setter 是否保持 fluent return 由最终 Builder Interface 冻结，但显式调用和 property assignment 必须产生相同的规范化结果。
- 当前 NekoJS 使用 `HostAccess.ALL`、public access 和 `js.nashorn-compat=true`；精确 GraalMC runtime 已用独立 characterization 验证 property assignment 能调用 setter。该行为仍需作为 runtime contract test 固定，GraalJS 坐标升级时重新验证；若未来某运行模式不保证 JavaBean property 映射，使用 NekoJS 已有 `ProxyObject` seam 显式把 `putMember("maxStackSize", ...)` 转发到同一 setter，而不是恢复 public field 旁路。
- Q6-Q8 的范围同时约束 Builder：第一版不自动扩展 Block/Fluid 等带连带对象、资源和同步风险的类型；定义 fingerprint 覆盖规范化后的 builder 输入及连带对象；多人 prepare/ack 协议完成前 capability 必须显式为 `partial`/`unavailable`。

本节只确定 Builder 的体验与单一写入语义，不冻结最终字段列表、方法名全集、开放 registry 类型或同步协议。
