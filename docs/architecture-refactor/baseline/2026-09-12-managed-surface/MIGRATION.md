# Managed Surface API 变化要点与迁移要点（ticket 09 / AC7）

> 本票（09-managed-surface）把 managed Script API 的规范源收口为
> `NormativeApiContract`（`CoreManagedApiBootstrap.buildContract` 纯反射合成），
> manifest、Probe、TypeScript declaration 与 Python declaration 均为派生或观察面。
> 本文件记录脚本作者需要知道的**变化要点**与**迁移要点**；公开 breaking 的最终
> 迁移表以 1.2.0 release handoff（W10 / spec 07）为准。

## 1. 本票交付的变化要点（脚本作者视角）

### 1.1 事实源与派生关系（行为不变，可预期性增强）

- 规范源：`NormativeApiContract` 由 facade（ID/Platform/Text/JsonIO/NBT/Registry/Performance）、
  数据类型（TextValue/NekoId/JsonValue/NbtValue/NbtEntry/RegistryView/ModInfo/PerfTimer）与
  事件注册类（ScriptEventRegistrationEvent）**反射合成**——没有第二份手写契约 JSON。
- 派生面（只读，不反向决定运行时行为）：
  - **manifest**（`api-manifest-core.json` golden）：符号 id + 签名 callKey，字典序确定；
  - **TS declaration**（managed 部分，`ManagedApiDeclarationGenerator`）：`const X: $X` +
    `interface $Owner`，owner 归属与契约符号一一对应；
  - **Python declaration**（`.pyi`，经 `TypeReflector` IR）：runtime 成员（方法/getter→
    `@property`/静态→`@staticmethod`）按包路径归属 module。
- 确定性：同一契约输入重复生成 manifest/Probe/TS/Py 产物逐字节稳定
  （`ManagedSurfaceDerivationDeterminismTest`、`PythonDeclarationDeterminismParityTest`）。

### 1.2 能力结论显式化（新基础设施，行为不变）

- 契约能力支持三态 `supported` / `partial` / `unavailable`，可携带 loader / loader 版本 /
  运行上下文（dist、requiredMods）条件（`ContractCapability.status` + `EnvironmentScope conditions`）。
- 条件在 surface 解析时真实生效：条件不满足 → 能力不激活，且在解析结果中显式记录
  `UNAVAILABLE`；声明 `unavailable` 的能力永不激活（`DECLARED_UNAVAILABLE`）。
  不允许静默 no-op。
- 本票未给 portable-core 契约声明任何新能力（`capabilities` 仍为空）；后续事件/查询/UI 票
  按 W5 的规范源与派生流程追加。

### 1.3 legacy 观察面（迁移可见性 ≠ stable 承诺）

- `NekoScriptCatalog` / `LegacySurfaceAdapter` 继续承担迁移观察：legacy 符号进入
  `legacySurface`（占位签名，观察 kind），**不进入 managed surface、不获得 stable 身份、
  不能覆盖同名 managed symbol**（resolver 层 `LEGACY_NAME_COLLISION` 显式失败）。

### 1.4 高级 Java 面不变

- `java:`（ESM 协议，`NekoModuleResolver`）、`Java.type`、`Java.loadClass`（= Java.type 别名）、
  Graal interop（List/Map 映射、Number targetTypeMapping）与当前 HostAccess（ALL + adapter
  targetTypeMapping）/ ClassFilter 形态保持既有行为，本票未收紧
  （`AdvancedJavaInteropSmokeTest`）。

## 2. 迁移要点

### 2.1 脚本作者

- 现有 managed 全局（ID/Platform/Text/JsonIO/NBT/Registry/Performance）的成员、签名与
  语义**零变化**；golden（manifest / TS declaration）无 diff 即证明。
- Builder 写法：
  - 显式 setter（`putString/putInt/...`）与类型化 put（`put(key, NbtValue)`）是**同一写入
    语义**（同一校验/规范化路径）；最终 `build()` 产物为不可变快照（final identity）。
  - Graal 对 host object 的天然 JavaBean property assignment 行为**不作为承诺**——请使用
    显式 setter（见 `ManagedBuilderWriteSemanticsTest`）。
- 最小示例见 [minimal-example.js](minimal-example.js)； regenerate 流程见
  [REGENERATE.md](REGENERATE.md)。

### 2.2 维护者 / 插件作者

- 新增/修改 managed 表面：只改 facade / 数据类型 / 事件注册类（反射输入），派生产物经
  `./gradlew :common:regenerateGoldens` 显式再生成并按 REGENERATE.md 审阅。
- 能力声明新增：`ContractCapability(id, versionRange, status, conditions, docs)`，provider
  走既有 `registerCapabilityProvider`；CORE_ONLY 接受核内 owner（`nekojs-core`/`nekojs`）。
- 已知不一致（非本票引入，未在本票修改）：`NBT.compound()` 虽被契约/声明承诺，但经契约
  invoker 返回 host object 会触发 `NATIVE_TYPE_LEAK`（`ApiValueMarshaller` 的 raw return
  守卫）。处理归属见 REPORT「问题与处理」。

### 2.3 不在本票范围（不做迁移承诺）

- 事件面、registry、JSX UI（票 40）、Villager Trades / Dynamic Registry（W6/W7）的 API 定稿；
- `1.2.0` 公开 breaking 清单与最终迁移表（W10）；
- Fabric declaration parity 与 processor 延期 gate（W9 接线）。
