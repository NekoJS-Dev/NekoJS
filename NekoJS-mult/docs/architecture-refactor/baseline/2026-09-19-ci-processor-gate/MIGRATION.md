# 票据 33 迁移材料：CI 用途子集与 Fabric processor 延期替代 gate（2026-09-19）

> 范围：W9 构建约定与 CI 收口。**脚本作者零迁移**；维护者/CI 侧新增可运行 gate，
> 未改变任何公开脚本语义、支持矩阵、节点身份或制品命名。

## 1. 脚本作者与插件作者：无须迁移

本票不改 runtime、不改公开契约、不更新任何既有 golden。受影响面只有构建/CI：
新增只读 gate 与两个 Gradle 任务（`platformGateTest`、各节点 `check` 的新依赖边），
以及 `guardLint` 模块边界规则的一处"去假门禁"修订（见 §3）。

## 2. Fabric `common-api-processor`：1.2.0 明确延期（AC1）

### 2.1 延期结论与事实源

| 项 | 事实 |
|---|---|
| 处理器接受平台 option | `nf26` / `nf121` / `cr`（`SpecCoverageProcessor.PLATFORM_OPTION` 的词表） |
| Fabric 是否挂处理器 | **否**。两个 Fabric 节点 convention 无 `annotationProcessor(project(":common-api-processor"))` |
| Fabric 是否传平台 option | **否**。fabric convention 不出现 `-Anekojs.platform=` |
| Fabric 节点是否宣称 option | **否**。`versions/*-fabric/gradle.properties` 没有 `deps.platform_tag` |
| NeoForge 接线 | **保持**。三节点均 `wired=true` + 传 option，值 `nf121` / `nf26` 均在词表内 |

**这不是**「NeoForge processor 等价」，也**不是**「临时接通」：延期是工作状态，
不是第四种 capability 值，也不改变支持矩阵。

### 2.2 未覆盖范围（延期必须公开说明的部分）

Fabric 节点上，处理器原本会强制的两类编译期结论**没有**等价物：

1. **method 覆盖**：spec 的每个 `neko$xxx` 是否被平台实现面自行声明（而非继承哨兵 default）；
2. **平台范围**：`@PlatformAvailability` scope 要求的 spec 是否有实现面直接 extends。

延期的替代覆盖由 gate 补足（§2.3），**但替代物不是 processor**：它跑在编译产物上、
在测试期而非编译期、且没有 IDE 红线。三者关系必须如实描述：

| | NeoForge（processor） | Fabric（延期 + 替代 gate） |
|---|---|---|
| 时机 | 编译期 | 测试期（`:check`） |
| 失败形态 | javac ERROR / IDE 红线 | 测试失败 + 指名诊断 |
| method 覆盖 | ✔ | ✔（`PlatformSpecContractGateTest`） |
| 平台范围强制 | ✔（`-Anekojs.platform`） | ✔ 但平台轴来自节点 loader（非 option） |
| contract/event、declaration | 无（processor 不管） | ✔ 独立 gate |

### 2.3 替代覆盖 gate（AC2）

三类非 processor gate 的 owner / 输入 / 逐项输出 / 失败诊断：

| gate | owner | 输入 | 逐项输出 | 失败诊断（可定位） |
|---|---|---|---|---|
| contract/spec | Managed Surface/Probe owner 定义规范；build convention owner 接线 | 节点 classpath 上编译出的 `api.spec.inject.*`（scope + `neko$` 方法）+ `api.inject.*Extension` 实现面 | `build/nekojs-gates/spec-coverage-<node>.json`；只读基线 `spec-coverage-<loader>.txt` | `missing-contract`（spec 无实现面）、`missing-method`（`spec.neko$xxx` 未自行声明） |
| event/surface | 同上（coverage ledger + contract fixture） | 节点插件发现输入（neoforge 注解扫描 / fabric `BUILTIN_PLUGINS`）的 `registerEvents` / `registerClientEvents` 钩子 | `event-surface-<node>.json`；跨节点基线 `event-surface-domains.txt` | `missing-binding`（domain/bus 缺失）、`undocumented-domain`、`member-drift`、`undeclared-evidence` |
| declaration | Managed Surface/Probe owner（declaration fixture） | `CoreManagedApiBootstrap` 反射的规范契约 → `ManagedApiDeclarationGenerator` 产物 | `common/build/nekojs-gates/declaration-parity.json`；基线 `declaration-parity.txt` | `missing-member` / `extra-member` / `missing-type` / `owner-missing` / `type-fallback` |

### 2.4 接入方式（未来域票）

后续域票提交其真实 fixture 并通过同一 gate，**不需要**改 gate 本体：

1. 新 domain：在节点的 `registerEvents`/`registerClientEvents` 里登记 → `platformGateTest`
   自动枚举到 → 更新跨节点基线 `event-surface-domains.txt` 并在 REPORT 留记录；
2. 新 spec：加 `@PlatformAvailability` + 实现面 → 自动进入 spec gate 比对；
3. 新契约成员：走 09 的显式 regenerate 流程 → declaration gate 基线随之更新（同样需留记录）。

## 3. `guardLint` 的过时 Graal 规则（AC7）

**修订前**：L1 规则把 `import org.graalvm.*` 列为 `com.tkisor.nekojs.api.*` 的违规。
**问题**：ADR-0007（2026-08-30 修订）已明确 `common`（含 `api.*`）**允许**使用 GraalJS，
"契约层零 Graal import"只作为历史理由保留、不再是当前约束。该规则因此早已空转
（实际扫描到的 Graal import 用的是 relocated 包名 `graal.graalvm.*`，原正则匹配不到）。

**修订后**：删除 `graalImport` 硬失败规则与规则 6 的 Graal 措辞；**保留** MC/Loader 隔离
（L1 `api.*` + L2 `common` + `checkCommonIsolation` + `ModulePipelineIsolationTest`）。
不新增任何依赖、不放宽 MC/loader 边界、不改支持矩阵。

> 这里选择"删规则 + 写明理由"而不是"改成匹配 relocated 名"：把一条与现行 ADR 相反的禁令
> 修成能生效的形态，会立刻把 `common` 合法使用 Graal 判成违规。去假门禁，不加真禁令。

## 4. CI 子集（AC4/AC5）

四类手写子集按**用途**分别对照 `settings.gradle.kts` 节点图，不使用单一等值检查：

| 子集 | 用途 | 覆盖节点 | intentional skip | 一致性检查 |
|---|---|---|---|---|
| NeoForge-only NBT | NeoForge 专属 portable binary NBT smoke | 1.21.1 / 26.1.2 / 26.2.0 | 两个 Fabric 节点（convention 不注册 `nbtSmokeTest`） | 三条 `nbtSmokeTest` 必须在 run 命令块；skip 节点平台必须 != neoforge |
| Fabric-only artifact/smoke | 制品 entrypoint / NeoForge 泄漏校验 + development runtime smoke | 26.1.2-fabric / 26.2.0-fabric | 三个 NeoForge 节点 | 两处制品路径 + 两个步骤名；**有意不复用** Gradle `verifyFabricRuntimeArtifact`（校验对象不同：本机产物 vs 下载回来的已上传 jar） |
| 全节点 build | compileJava → test → check | 五节点全等 | 无 | 手写列表必须与节点图逐个等值 |
| release/publish | 发布 NeoForge 三节点制品 | 1.21.1 / 26.1.2 / 26.2.0 | 两个 Fabric 节点（workflow 内注释保留的 CurseForge 块） | 三条 release 制品路径 + 两个步骤名 |

**manifest 只作派生快照**：本票的节点事实源是 `settings.gradle.kts` 的 Stonecutter DSL +
`versions/*/gradle.properties`；`docs/architecture-refactor/baseline/node-source-artifact-manifest.md`
不被 gate 读取，也不被更新为"第二事实源"。

## 5. 五节点报告（AC6）

`tools/nekojs-ci-gates.py nodes` 把五个节点的四类结果汇总进 `build/nekojs-gates-report.json`：
check（Test XML 统计）、artifact（名字 + 大小 + sha256）、source trace（探针实际读出的
srcDirs）、两个节点内 gate 报告。任一缺失记 `not verified` 并逐条列出——**不静默省略**，
尤其 Fabric artifact 与已声明能力 smoke。

Fabric 节点的 source trace 里可见 `../../src/fabric/java`、`../../src/fabric/resources`；
NeoForge 节点没有——这是 W8 源根所有权的运行时证据，不是文档声明。

## 6. 未做事项（非本票范围）

- 不改支持矩阵、不新增 Gradle project / API jar、不删 Stonecutter、不改节点身份/坐标/制品命名。
- 不在 1.2.0 接通 Fabric processor；不把延期写成 capability 值。
- 不把本报告的 gate 结果外推为未迁移域的验收（AC9）。
- 未跑真实 Ubuntu CI 全链路、未跑 Fabric runtime smoke（见 REPORT 的限制小节）。

## 7. Gate 自身证据边界

- contract/spec 与 event/surface gate 证明的是**注册面/声明面**存在且成员一致，
  不是 runtime smoke：不施加 `requiredMods`/`clientOnly` 运行期过滤，不执行平台原生回调。
- declaration gate 跑在 `:common:test`，不需要节点；它冻结的是 SERVER 脚本类型的
  managed global/member/type 成员集合与签名计数，不冻结渲染字节。
- `not-verified` 是唯一允许的"无证据"写法；写成 `unavailable`/`partial` 会被 gate 判失败。
