# 启动期注册迁移材料（ticket 15）

> 面向脚本作者的启动期 `RegistryEvents.register` 使用与迁移要点。服务器运行期
> Dynamic Registry（`DynamicRegistryJS` / 规划中的 `ServerEvents.dynamicRegistry` 面）
> 是**独立生命周期**，与本文的启动期入口保持分离（spec 08）。

## 1. 入口与能力（当前快照）

| 能力 | 写法 | 说明 |
|---|---|---|
| default 类型糖方法 | `event.item('mymod:ruby', b => { ... })` | 免类型名；糖方法名由注册表键 snake_case → lowerCamelCase 派生 |
| 命名类型 | `event.soundEvent('mymod:ping', 'basic', b => { ... })` | 显式类型名；未知类型在收集期拒绝并列出 known types |
| custom | `event.custom('mymod:art', 'art', b => { ... })` | 按全局唯一类型名解析注册表；跨注册表同名类型会被拒绝并提示改用命名形态 |
| 裸 Supplier | `event.register('minecraft:villager_type', 'mymod:raw', () => value)` | 高级入口；Runtime 在合法启动 pass 校验返回值非空、实际类型与重复 ID |
| setter/property parity | `b.maxStackSize = 16` 与 `b.setMaxStackSize(16)` | 同一个 setter，同一校验/规范化/definition fingerprint |
| 连带注册 | `event.block(...)` 默认带 BlockItem；`b.noItem()` / `b.item = null` 抑制 | 连带条目在目标注册表自己的 pass 投递；目标 pass 已过则记 `additional-target` 错误且不注册 |

## 2. 迁移要点（1.2.0）

1. **property 写法继续有效**：`b.maxStackSize = 16` 不需要改写。变化在于 property
   写入现在与显式 setter 走**同一条路径**——越界值（如 `maxStackSize = 200`）在写入期
   即抛带成员名的错误，而不是留到 MC 注册冻结期；`rarity = 'EPIC'` 与 `'epic'` 归一化
   相同。这是语义收紧（spec 04/08 裁定），不是 API 破坏。
2. **显式 setter 不引入第二语义**：两种写法调用同一个 `Method`，错误信息同源
   （`BuilderSetterPropertyParityTest` 固定）。
3. **final identity 字段例外**：`b.id` 是只读成员（final identity），写入会被拒绝并
   列出可写成员目录；这是 AC 声明的例外清单。
4. **未知成员**：读取返回 `undefined`（JS 语义）；写入被拒绝并带成员目录
   （`has no member 'nope'; known: [...]`）。
5. **重复/冲突错误增强**：同批同 id 重复仍是收集期 fail-fast，错误信息现在带
   「first from X, second from Y」来源与节点上下文。
6. **Java 侧消费者**（插件直接读 builder public 字段的代码）必须改用
   getter/setter——public 字段已转私有（活跃开发期允许 breaking；脚本面无感）。
7. **失败不再污染下一轮启动**：每轮游戏启动一个注册 epoch；上一轮未交付的暂存会被
   丢弃并记诊断日志（`[registry-startup] stale staging ... discarded`）。
8. **Dynamic Registry 分离**：运行期动态注册不经 `RegistryEvents.register`；
   `DynamicRegisterMode` 对「运行期创建只能走启动期的对象」给出可诊断错误。

## 3. 声明面（tier 归属）

| 面 | 来源 | tier |
|---|---|---|
| typed Builder 结构化声明（TS `@registry-builders/index.d.ts` / Python `_registry_builders/__init__.pyi`） | builder 类契约反射（`RegistryBuilderContract`）→ `RegistryBuilderSurfaceEntry` → probe 后端渲染 | managed 派生（contract/golden：`RegistryBuilderSurfaceGoldenTest` + `startup-builders*.d.ts`） |
| 手写 manual declaration（`NekoRegistryDeclarations`） | 逐类型手写字符串 | **legacy 迁移观察**（保留，删除走维护者 gate + parity 证据） |
| `TypeDocCatalogEntry.binding(STARTUP, "RegistryEvents", ...)` | 文档/补全 | legacy 观察（ticket 14 既有） |

## 4. 已知边界（不在本票解决）

- 裸 Supplier 不承诺任意副作用的指纹/回滚；指纹只覆盖可表达的启动声明
  （typed builder 的规范化成员读数）。
- `getMemberKeys`/`Object.keys(b)` 对 ProxyObject 只列成员名，不含宿主类继承面。
- Fabric 侧流体类型（`minecraft:fluid`）不注册（流体体系是 NeoForge 面），
  `event.fluid(...)` 在 Fabric 节点得到 unknown registry 错误——显式拒绝，不静默 no-op。
- 1.21.1 的六个版本化 builder 成员面与 26.x 有真实差异（golden 按版本各冻一份，
  见 REPORT 五节点差异表）。
