# 公开迁移与删除核对（2026-10-07）

这是当前源码消费者核对与技术交接，不是维护者批准。票 39 AC14 确认不扩大到以下项目；本轮不删除尚有消费者或缺少完整替代声明的代码。脚本、Plugin API、持久化和 wire 变化分开记录。

| 票/旧 public symbol | 当前状态 | 新写法/替代路径 | 数据与行为影响 | 确认状态 |
|---|---|---|---|---|
| 15 Builder 的 public 可写字段 | 已改为 accessor；脚本 property 继续有效 | `RegistryEvents.register(event => event.item(id, build => { build.maxStackSize = 16 }))`；Java 使用 getter/setter | 同一 setter 校验/规范化/指纹；Java field 访问是既有 breaking；不改变保存格式 | 历史改动已列迁移；本轮未新增删除批准 |
| 15 `nekojs.registry` manual declaration | 仍有 `NekoRegistryPointsPlugin` 生产消费者和 TS backend 输出消费者 | structured builder surface 只替代 builder 成员；**尚未完整替代** RegistryEvent 默认/命名 sugar、custom、Supplier、RegistrySugar 和短 Potion.effect 声明 | 现在删除会丢作者声明能力；保留而非假称无人调用 | 暂不申请删除，先补完整同源 replacement |
| 15 `NekoScriptCatalogSnapshot` 14-arg public ctor | 多个测试仍调用，替代 ctor 已有 builder surface 参数 | 使用 15-arg ctor 并显式传 builder surface 列表 | Plugin Java 签名变化；无保存/wire 改动 | 暂保留；迁移全部消费者后另列确认 |
| 16 `DynamicRegistry` binding 六个 entry overload；旧 fluent builder；`DynamicEntryHandle` 返回契约 | 仍在，wiki 仍有旧例；binding close 是现有 beginServerReload 消费者 | `DynamicRegistryEvents.dynamicRegistry(event => event.item(id, build => { build.maxStackSize = 16 }))`，soundEvent/mobEffect 同理；使用定义 id 与提交后 query/diagnostics，不承诺同步返回 handle | candidate 收集 inert；成功 commit 后异步 activation；冲突/过期失败明确；不能把旧立即 handle 假写成完全等价 | 待精确维护者确认；删除前迁移 close 生命周期和文档 |
| 22 NeoForge 26.x `VillagerTrades.add/pendingCount` | canonical 仍在；**1.21.1 已不在**，只余 query/describe | `ServerEvents.tradeDeclaration(event => event.add(tradeSet, config))`、`tradeReload`、`VillagerTrades.query()/describe()` | 整批拒绝 vs 旧部分跳过；commit 后写入；遗漏项 unrestored，显式 obsolete 才 retire；同 trade set 旧新不可混用 | 剩余 26.x Script 删除 + 已发生 legacy 删除分别待确认 |
| 22 两节点旧 `VillagerTradeManager` public nested types/静态 methods/旧 listener-command hooks | 仍有旧 lifecycle 消费者；新 DomainOwner 不调用旧 Manager | root-owned collector + 节点 Adapter + generation query | 不删除新 Adapter surgery；不得删除 `VillagerTradesPlugin` binding producer，它还提供 query/describe | 待精确确认，先迁移消费者与 wiki |
| 28 `PostEffects.register/unregister/has`、Manager 旧 writers、binding close 清屏 override | **已删除**；尚缺知情追认，不是待 blanket 删除 | `ClientEvents.postEffects(event => event.register/unregister(...))`；`hasDefinition` 读 active generation；runtime set/clear/toggle/current 保留 | 候选失败保留旧 active；binding teardown 不清屏；资源 reload 的 vanilla renderer 行为另验；legacy resources-only，不承诺 inline chain | 待追认这些确切 public/行为变化；不表示全视觉验收 |

## 不属于删除清单

- DynamicRegistries / RegistrySurgery / DynamicRegistrySet / bookkeeping / RegistryDataCollectorMixin / enabled gate / debug commands：新 activation/rollback 和客户端 component replay 使用它们。
- RegistryEvents binding、custom/Supplier advanced entry、TypeDoc 和有效 builder 对象生命周期。
- VillagerTrades query/describe binding producer、域 collector、新 Adapter。
- PostEffectManager 的 active definition、parse/install generation、runtime shader source、cache lifecycle 和 set/clear/toggle/current/query。
- 任意用户数据、world、pack、trust-store；网络协议 schema、payload id 与已有 frozen wire gate。

## 技术验证与仍开窗口

新 PostEffects 双语言生产 parity、两 Fabric 的明确失败断言和旧 callback golden；26.2 官方制品 native resource inversion/clear/F3+T、声明资源 id override 实际 blur；26.2 官方交易报价；Fabric 官方制品 declaration-only preflight 明确拒绝，均由同目录证据承载。

15 的真实连带注册/嵌套 builder/probe、结构化短重载声明；16 MobEffect activation/跨进程 sync/部分类型精度；22 legacy 静态池实机替换和交易 retirement/reload；28 多节点首帧/任意内联 id 激活限制仍不外推。历史 closed header 不代表这些 AC 已满足。

## 后续必须由维护者给出的结论

- A16：只批准旧 Script facade/六 overload/fluent builders/immediate handle 迁移，明确保留共享 backend/gate/mixin；以生命周期迁移和域验证完成为删除前提。
- A22：批准剩余 26.x add/pendingCount 与旧 Manager 指定符号/hooks 迁移，追认 legacy 已移除的 Script methods；保留 query producer，接受表内 whole-batch/unrestored/retired 语义。
- A28：追认表内已删除声明 methods/writers 和 reload-no-clear 变化；保留 runtime/query，并接受 legacy resources-only 限制。
- A15 先不批准不完整替代的手写 event declaration 删除；public ctor 若选删除须先迁移全部调用者。

这些结论不等于正式 1.2.0 发布、版本切换、性能豁免或数据回滚策略批准。
