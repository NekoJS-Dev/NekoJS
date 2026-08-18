# NekoJS 工程规范（Development Spec）

> **文档地位**：本规范是本仓库的长期开发约束，所有代码变更（新功能、修复、重构、构建调整）都必须遵守。它与 `docs/ROADMAP.md`（方向与路线图）互补：ROADMAP 回答"做什么"，本文档回答"怎么做、不许怎么做、改了要跑什么"。
>
> **标记约定**：
> - **[必须]** —— 硬性约束，违反会导致 CI 失败或代码评审不通过；
> - **[惯例]** —— 推荐做法，长期维持的一致性约定；
> - **[现状]** —— 当前事实记录（版本号、警告清单等会随演进变化，维护代码时顺手更新，不必单独开任务）。
>
> **维护**：本文档随架构演进更新。修改规范本身时，必须同步修改对应的代码、测试与门禁，保证规范描述的每个不变量都有代码或测试支撑，不允许出现"规范写了但代码没实现"的条文。

---

## 1. 模块架构与依赖边界

### 1.1 模块职责

| 模块 | 角色 | 关键约束 |
|---|---|---|
| `common-api` | API 契约层：纯类型、注解、契约记录，零运行时依赖 | 编译依赖白名单仅 `org.jetbrains:annotations` |
| `common-api-processor` | 编译期注解处理器（`SpecCoverageProcessor`） | 仅 `compileOnly project(':common-api')` |
| `common` | 共享运行时：脚本引擎（GraalJS）、事件框架、插件 SPI、probe | `api project(':common-api')` + `api(libs.graal)`；禁止任何 MC/loader import |
| `platforms:neoforge-26.1 / 26.2 / 1.21.1 / cleanroom-1.12.2` | 平台适配层：事件组、绑定、适配器、mixin、平台特有实现 | `implementation project(':common')` |

> **[现状]** 注意一个容易混淆的点：`NekoJSPlugin`、`ScriptType`、`EventGroup` 等"api 面"类位于 **common**（`com.tkisor.nekojs.api.*` 包在 common 里也有一部分），并非都在 `common-api`。判断归属不看包名，看编译依赖：能被平台直接引用且不含 MC 类型的公共设施放 common；被 common 与平台共享、必须零依赖的纯契约放 common-api。

### 1.2 依赖方向

**[必须]** 依赖只能沿单向链：`common-api ← common ← platforms`，任何反向或跨层依赖（平台→common-api 之外的 common 类是被允许的，但 common → 平台、common-api → common 均禁止）。新增模块时先画依赖图，违反方向的代码会被隔离门禁直接拦下。

### 1.3 隔离边界（编译期强制）

两个 Gradle 校验任务挂在各自模块的 `check` 上，任何违规直接构建失败：

- **`checkApiBoundaries`**（`common-api/build.gradle`）：逐行扫描 import，禁止 `graal/org.graalvm/net.minecraft/net.minecraftforge/net.neoforged` 前缀，禁止引用 `com.tkisor.nekojs.*` 中非 `api.` 子包的任何类型；同时校验编译 classpath 白名单（只允许 `org.jetbrains:annotations`）。
  **[必须]** `common-api` 新增任何编译依赖，必须同步加入 `allowedCompileDependencies` 白名单并说明理由。
- **`checkCommonIsolation`**（`common/build.gradle`）：禁止 common 出现 `net.minecraft`、`net.minecraftforge`、`net.neoforged` import。
  **[必须]** common 必须保持 MC/loader 无关（Graal 是唯一例外，common 独占引擎）。平台特有代码**禁止**下沉到 common——被门禁拦下是特性而非缺陷，平台差异请留在平台模块或经 SPI 注入。

### 1.4 契约体系

NekoJS 有两条契约通道，方向不同：

**编译期（SpecCoverageProcessor，`common-api-processor`）**
- Spec 接口放在 `com.tkisor.nekojs.api.spec` / `.spec.inject`，标注 `@PlatformAvailability`（`Scope.ALL / NF_ONLY / CR_ONLY`）。
- **[必须]** 每个 Spec 接口的 `neko$` 方法必须被平台实现接口**显式覆盖**（Spec 的哨兵 default 不算覆盖），处理器对签名做参数个数+类型全等校验。
- **[必须]** 平台模块编译必须传 `-Anekojs.platform=nf|cr`（nf = NeoForge 系列，cr = Cleanroom），处理器据此强制 `Scope` 要求的平台实现存在；不传选项则范围校验静默失效——这是配置错误，不是可选项。
- `Scope.ALL` 仅用于"所有平台都能原生或 mixin 实现"的接口。

**运行时（契约反射）**
- 契约真相源是 **Java 方法签名**（`ContractReflector`）：facade 方法 → `global:`/`member:` 符号；`@ContractReceiver` / `@Remap` 注解声明契约名与 JS 名。
- 事件契约由 `EventContractReflector` 从运行时 `EventGroup` 反射派生：payload 字段 = 事件类的 public 零参 getter，mixin 注入字段统一用 `neko$` 前缀规避（`neko$` 方法永不进入 JS API 表面）。
- 运行时强制（构造期抛异常）：`ContractEvent` BY_ID 必须带 `dispatchKeyType`、PLAIN 不得带；PORTABLE 字段必须带 `portType`、NATIVE 不得带；`VerifiedContractSet` 拒绝重复身份。

### 1.5 插件 SPI 与生命周期

- 插件 = `implements NekoJSPlugin` + `@RegisterNekoJSPlugin(clientOnly / requiredMods / priority)`。`priority` 默认 1000，数值大先加载；`CORE_PRIORITY = Integer.MAX_VALUE` 保留给核心插件。
- 平台加载器只做**发现**；过滤/排序/实例化统一在 common 的 `NekoJSBasePluginManager`。
- **[必须]** 绑定名、事件组名、配方命名空间是全局唯一 ID，重复注册直接抛异常；所有注册必须在 bootstrap 冻结（`freezeState`）之前完成，冻结后注册抛 `IllegalStateException`。

---

## 2. 构建与编译卫生

### 2.1 环境要求

- Gradle wrapper 9.6.0 需要 JVM 17+。**本机 PATH 默认是 JDK 8**，任何 `gradlew` 之前必须先设置：
  ```powershell
  $env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
  ```
- `org.gradle.configuration-cache=false` 是**刻意**的（ModDevGradle 配置期读环境 + 跨项目任务引用），不要试图打开。

### 2.2 构建命令速查

| 目的 | 命令 |
|---|---|
| 核心检查（含隔离门禁） | `.\gradlew.bat :common-api:check :common:check :common-api-processor:test` |
| 平台完整构建 + 产物校验 | `.\gradlew.bat :platforms:neoforge-26.1:build`（check 内含 verifyRuntimeArtifact / verifyDevModSourceSets） |
| NBT 冒烟 | `.\gradlew.bat nbtSmokeTest` |
| 再生成 golden | `.\gradlew.bat :common:regenerateGoldens`（**生成后必须 review diff 再提交**） |
| 平台漂移检测 | `bash scripts/check-platform-drift`（dashboard 模式，恒 exit 0） |
| Cleanroom 单独构建 | `.\gradlew.bat :platforms:cleanroom-1.12.2:build`（remapJar → verifyRuntimeArtifact） |

### 2.3 工具链与字节码

| 模块 | toolchain | 目标字节码 | 说明 |
|---|---|---|---|
| common-api / common-api-processor / common | 21 | 21 | |
| neoforge-1.21.1 | 21 | 21 | |
| neoforge-26.1 / 26.2 | 25 | 25 | 共享 `gradle/neoforge-26-shared.gradle` |
| cleanroom-1.12.2 | 25（编译） | **21**（分发） | **[必须]** cleanroom 分发字节码锁 Java 21（官方仅支持到 21，25 字节码会让 17/21 用户 UnsupportedClassVersionError）；测试 JVM 用 25 launcher |

### 2.4 编译卫生（0 警告策略）

**根配置**：所有模块 `-Xlint:all` + `-parameters` + UTF-8（根 `build.gradle`）。`-parameters` 是硬需求：probe 的 TypeReflector / ContractReflector 靠真实参数名生成声明与契约，缺失时退化为 `arg0/arg1`。

**[必须]** 任何模块不得新增 javac 警告；新代码按以下类别惯例处理：

| 警告类别 | 处理惯例 | 反例（禁止） |
|---|---|---|
| `serial` | 异常/可序列化类加 `serialVersionUID = 1L`；载荷字段 `@SuppressWarnings("serial")` 且注释说明"运行时只读、不序列化、保留字段避免静默丢数据" | 裸字段无注释 |
| `unchecked` / `rawtypes` | 方法级 `@SuppressWarnings({"unchecked","rawtypes"})` 精准压制，附一句为什么安全 | 类级大范围压制 |
| `this-escape` | **重构优先**：静态工厂（构造后初始化）、惰性字段、构造器内联替代委托 | `@SuppressWarnings("this-escape")` 掩盖 |
| `deprecation` / `removal` | 能换 API 就换；过渡期桥接 `@SuppressWarnings("deprecation"|"removal")` + 注释说明过渡理由 | 无注释压制 |
| `processing` | **[必须]** 仅以下 4 处允许 `-Xlint:-processing`（且必须保留中文注释说明理由）：common、`gradle/neoforge-26-shared.gradle`、`platforms/neoforge-1.21.1/build.gradle`、`platforms/cleanroom-1.12.2/build.gradle`。理由：`@RegisterNekoJSPlugin` / Forge / Mixin 注解都是运行时元数据（反射读取），不需要注解处理器。**其他模块不得关闭该 lint** | 新模块随手加 |

**[现状]** 当前警告状态（2026-08 实测，`--rerun-tasks` 全量重编译）：**全部模块 0 警告**（common-api / common / common-api-processor / cleanroom-1.12.2 / neoforge-1.21.1 / 26.1 / 26.2）。neoforge 平台的 86 条遗留警告已于 2026-08 批次清零（换新 API / 共享树修复 / 注释化抑制），详见 ROADMAP「工程卫生收尾」。

> **[惯例]** 警告只在 javac 真实重跑时可见（UP-TO-DATE / FROM-CACHE 不重放）。验证警告状态用 `--rerun-tasks`，不要在增量构建里数警告。

### 2.5 平台构建机制

- **三层源码组织**：① `platforms/neoforge-shared/`（1.21.1 与 26.x 字节级相同的文件）；② `platforms/neoforge-26-shared/`（26.1/26.2 共享，**1.21.1 的测试也共用**）；③ 各平台本地层（26.1/26.2 仅各 4 个差异文件）。
  **[惯例]** 平台差异文件保持最小化；跨版本机械改名（如 `ResourceLocation`↔`Identifier`）由 `scripts/check-platform-drift` 归一化后对比，新差异文件要能通过 drift 审视。
- `gradle/neoforge-common.gradle`：三平台字节级相同的公共块（jar 内嵌 common/common-api、verifyRuntimeArtifact 挂接、unifiedPublishing）。
- `gradle/verify-runtime-artifact.gradle`：解包分发 jar，断言必需类条目（common/common-api 全量输出 + 平台 NBT codec + 嵌入依赖的 class）；NeoForge 追加拒绝内嵌 graal/truffle 类与 `module-info`。
- Cleanroom 特有：`contain` 配置把 Graal/night-config 打进 jar（manifest `ContainedDeps`/`MixinConfigs`/`FMLAT`），unimined remap + mixinRemap，AT 文件经 processResources 进 `META-INF`；blossom 模板注入 mod 元数据（NeoForge 用 `generateModMetadata`，common 用 resources filter）。

---

## 3. Probe 子系统契约

### 3.1 数据流

```
EventGroupRegistry / BindingRegistry / adapters / recipeNamespaces
        │
        ▼
NekoScriptCatalog.snapshot(runtime) ──► NekoScriptCatalogSnapshot
        │                                    (bindings/events/adapters/registryTypes/...)
        ▼
ProbeCoordinator.runProbe
  ├─ collectClasses：BFS（种子 = 事件类型 + dispatchKey + 绑定 + extraDocTypes，深度 ≤ maxDepth=5）
  ├─ buildAndMutateIr：并行反射为 TypeDecl IR（assign_type 先于 modify_type 应用）
  └─ 按 ProbeBackendRegistry 分发（(languageId, name) 二维，锁定后冲突 fail-fast）
        ▼
TypeScriptProbeBackend ──► .neko_probe/typescript/（@package / @side-only / @special / @manual / @nekojs/managed）
PythonProbeBackend    ──► .neko_probe/python/（nekojs/{__init__.pyi, py.typed, _java, _events}）
        ▼
各自 staging 原子替换（成功才覆盖 outputDir，失败保留旧声明）+ 编辑器配置注入
```

### 3.2 事件目录不变量（防重复声明，历史 bug 教训）

- **[必须]** 每个 bus 在事件目录中**恰好一条** `EventCatalogEntry`，且用 `holder.scriptType()` 的**规范标签**（`NekoScriptCatalog.events(runtime)` + `firstApplicableBus`）。
- 历史事故（2026-08）：旧实现按 ScriptType 逐类型收集，同一条 SERVER bus 被打上 STARTUP/SERVER/TEST 多标签重复入目录；side 过滤（`SERVER.test(STARTUP)==true`）把多条同总线条目放进同一个 side 文件，导致 `.pyi`/`.d.ts` 出现重复方法声明（Pylance `reportRedeclaration`「方法声明被同名声明遮盖」）。回归测试：`NekoScriptCatalogEventsTest`。
- side 过滤语义：`entry.scriptType().test(side)`；STARTUP 接受 server+client 事件、SERVER 只收 server、CLIENT 只收 client、TEST 收 server（`ScriptType.test` 定义，勿改）。

### 3.3 Python 输出契约

- `_events/<side>/__init__.pyi`：每个事件组一个 `<Group>Type` 类，每个事件一个 `@staticmethod`。
- **[必须]** dispatch 型事件的两个签名（`handler` 版与 `extra, handler` 版）**都必须标注 `@overload`**，`typing` import 无条件包含 `overload`（`PythonEventRenderer`）。裸同名 def 在 .pyi 里会被 Pylance 报「方法声明被同名声明遮盖」。
- **[必须]** `_java` 类桩里 Java 重载（同名不同参数的方法/构造器）**每个签名都必须标注 `@overload`**，`typing` import 按需包含 `overload`（`PythonClassRenderer`，静态方法顺序 `@staticmethod` + `@overload`）。
- **[必须]** 方法名与运行时 JS 语义一致（`TypeReflector`）：`@RemapByPrefix`/`@Remap` 重映射进 `renameTo`（如 `neko$data` → `data`、`neko$getId` → getter `id`）；`@HideFromJS` 方法不进 IR；**类与接口收集都过滤 synthetic/bridge 方法**（javac 协变覆盖产生的同参不同返回 bridge 在 JS/Python 侧无意义且构成冗余重载）。
- **[必须]** 类级 `@RemapByPrefix` 查找含**接口继承**（`JavaMemberIndex`）：mixin/interface-injection 注入的方法反射自宿主类（declaringClass 无注解），注解在注入接口上——probe 与运行时 Graal（接口方法路径）必须一致命中，否则声明（`neko$data`）与运行时 JS 名（`data`）脱节。TS 与 Python 后端共用同一 IR，同时受益。
- 隐藏类（`@ClassEditor.hide`）必须从 `availableFqns` 剔除；其他模块引用它时不得 import（防悬空），渲染降级为 `Any`。
- `Literal` 仅按需 import；绑定名 == import 名时不写自引用别名；`RegistryValue` 形状 <512 条目 → 排序后的 `Literal[...]`，≥512 → `str` + 行尾注释标注注册表名。
- 输出必须含 `py.typed`（PEP 561）、祖先包逐层 `__init__.pyi`、`nekojs/__init__.pyi` 的全局绑定 + `__all__`。

### 3.4 TypeScript 输出契约

- `@side-only/<side>/events/index.d.ts`：namespace + function 声明；`java:` 路径 import + `$` 前缀；import 与符号**字典序**（跨 JVM 运行抖动防护）。
- `@manual/globals.d.ts` 承载脚本侧全局声明；适配器输入别名 `$Foo_` 注册进 `TypeAliasRegistry`（每次运行清空，防陈旧）。

### 3.5 确定性与原子性

- **[必须]** 所有生成路径保持确定性排序：collectClasses 结果按 FQN 字典序、TypeReflector 成员排序、import 字典序——生成产物必须可复现，不得依赖反射/HashMap 的偶然顺序。
- 输出采用 staging 原子替换：失败保留旧声明；入口需能恢复崩溃残留的 staging/backup 中间态。

### 3.6 修改规则

- **[必须]** 修改任何渲染器/目录逻辑，必须同步修改对应集成测试（`PythonProbeBackendIntegrationTest` 内联断言 / golden）。
- **[必须]** 涉及 golden 的输出变化，跑 `.\gradlew.bat :common:regenerateGoldens` 后**人工 review diff**，禁止无审阅直接提交。
- 未编辑 IR 的渲染路径必须与旧路径**逐字节一致**（`TypeScriptNoopIrGoldenTest` 守护）；共享 IR 编辑必须标记 `mutated`，否则编辑器状态机失效。

---

## 4. 测试与质量门禁

### 4.1 测试布局与约定

| 位置 | 框架 | 说明 |
|---|---|---|
| common-api / common / common-api-processor | JUnit 6（BOM 6.0.0） | 主战场在 common（约 120 个测试类，按被测包镜像） |
| neoforge 平台（含 26-shared 测试） | JUnit **5.14.3 legacy** | 与 common 的 5/6 分歧是迁移期刻意状态，勿"顺手统一" |
| cleanroom | JUnit 6.0.3 | |

- **[惯例]** 涉及平台单例的测试先 `TestPlatformInit.ensureInitialized()`（反射注入 `TestIPlatform`，gameDir 为临时目录）；需要假运行时时用 `StubPluginRuntime implements IPluginRuntime`。
- 测试命名：后端/生成器用 `generate_行为描述`；回归用行为句（`sameSourceReregistrationReplacesDefinitionWithoutThrowing`）；一般 `xxxTest`。注释与断言消息用中文。
- 文件操作用 `@TempDir`；**持文件句柄的场景禁用 @TempDir**（并发测试会炸）。
- **[现状]** common 独立测试必须在 `testRuntimeOnly` 注入 slf4j/log4j（api+core）/icu4j/night-config（core+toml）——游戏内由 MC/平台提供，独立 JVM 缺类即 NoClassDefFoundError。

### 4.2 特殊测试任务

| 任务 | 作用 | 触发 |
|---|---|---|
| `nbtSmokeTest`（根聚合） | 真实 codec 写压缩 NBT → 重建 facade 模拟重启读取（缺失/路径穿越/损坏/超限）；`@Tag("nbt-smoke")` | `.\gradlew.bat nbtSmokeTest` |
| `:common:regenerateGoldens` | `nekojs.golden.regenerate=true` + 过滤 `com.tkisor.nekojs.probe.*`，镜像写回 `src/test/resources` 后 assumption 跳过 | 手动，生成后 review diff |
| `check-platform-drift` | 三对平台目录归一化 diff（dashboard 模式，恒 exit 0，门禁由上层定） | `bash scripts/check-platform-drift` |
| `verifyRuntimeArtifact` | 分发 jar 必需类条目完整性（挂各平台 `check`） | 随 `build` |

### 4.3 门禁链

```
checkApiBoundaries ─┐
                    ├─► :common-api:check / :common:check ──► 平台 :check（含 verifyRuntimeArtifact / verifyDevModSourceSets）
checkCommonIsolation┘
:common-api-processor:test  ──► 不在 :check 内，CI 显式跑
```

CI（`.github/workflows/ci-build.yml`，push/PR main+master）：JDK 25 跑 `:common-api:check :common:check :common-api-processor:test` → `nbtSmokeTest -x :common:nbtSmokeTest` → `npm run test:probe-types`（tsc 校验 probe-ts）→ 动态发现平台跑 compileJava/test/build → 收集 jar；另有 **JDK 21 job** 复跑 common 检查抓 toolchain 漂移。

---

## 5. 变更流程

### 5.1 改动影响矩阵（改了哪里，至少跑什么）

| 改动 | 必须验证 |
|---|---|
| `common-api` 任何改动 | `:common-api:check` + `:common:check`（指纹强制重编译 common）+ 全量 `:common:test` |
| `common` 逻辑改动 | `:common:check` + 全量 `:common:test` |
| probe 渲染器/目录 | `:common:test`（含 PythonProbeBackendIntegrationTest / NekoScriptCatalogEventsTest）；golden 变化走 regenerateGoldens + review |
| 事件组/绑定/适配器 | `:common:test` + 对应平台 `compileJava`（SpecCoverageProcessor 范围校验生效） |
| 平台模块改动 | 该平台 `build`（含 verifyRuntimeArtifact）；共享树文件（neoforge-shared / neoforge-26-shared）改一处跑全部共享平台 |
| 构建脚本/依赖版本 | `--rerun-tasks` 全量编译确认 0 新增警告 + 门禁任务 |
| cleanroom 字节码/打包 | `:platforms:cleanroom-1.12.2:build`（remap 产物校验） |

### 5.2 提交规范

- **[惯例]** 提交信息格式：`<type>(<scope>): <中文描述>`，如 `fix(cleanroom): resolve all compileJava warnings under -Xlint:all`、`fix(probe): force Pylance when language server is unset`。scope 用模块/子系统名（common-api、common、probe、cleanroom、neoforge-26.x…）。
- 逻辑独立的改动拆成独立提交（如：警告修复 / 目录去重 / 渲染器契约，各一提交）；同一提交内不要混入无关格式化。
- 本机工作树行尾为 CRLF（`core.autocrlf`），仓库内以 LF 存储（`.gitattributes` 强制）；提交前确认无混合行尾。

---

## 附录 A：neoforge 平台遗留警告清单（[现状]，已清零）

2026-08 批次已全部清零（`--rerun-tasks` 实测 0 警告）。此附录保留原清单作历史记录，勿再引用：

- **neoforge-1.21.1（原 20 条）**：deprecation ×8（`ItemStackExtension`、`FoodBuilderJS`、`ClientBlockRenderTypes`、`RegistryEventListener`、`NekoJSPathPackResources`、`IngredientResolver`）；this-escape ×7（`RecipeEventJS`、`NekoCodeEditor`、`NekoErrorDashboardScreen`、共享 `FluidIngredientJS`）；serial ×2（`NeoForgeNbtBinaryCodec.LimitedOutputException`、共享 `RecipeEventSchemaHost`）；unchecked ×1（`GoalRegistry`）；rawtypes ×1（`TagKeyAdapter`）；cast ×1（共享 `ScriptEventsJS`）。
- **neoforge-26.1 / 26.2（原各 33 条，两份完全相同）**：deprecation ×15（`Ingredient.items()` 族 5 处、`builtInRegistryHolder()` 族 6 处、`LivingEntityExtension`、`ItemAdapter`、`NekoJSPathPackResources` ×2）；this-escape ×9；unchecked ×4（含 `NeoForgeRegistryQueryService` 2 处）；cast ×2（`ScriptEventsJS`、`NeoForgeRegistryQueryService`）；serial ×2；rawtypes ×1。

清理要点（如需重做/迁移到新 MC 版本时参考）：`Ingredient.items()` 与 `builtInRegistryHolder()` 无等价非废弃 API（custom ingredient 展开语义），保守用 `@SuppressWarnings` 而非替换；`ItemStack.getEnchantments()` → `getEnchantmentLevel(Holder)`；`Item.byBlock` → `Block.asItem()`；`FoodProperties.Builder.effect(MobEffectInstance,float)` → Supplier 重载；`SpawnEggItem` 构造器 → `DeferredSpawnEggItem`；`SharedConstants.*_PACK_FORMAT` → `WorldVersion.packVersion(PackType).major()`（26.x）/ `getPackVersion(PackType)`（1.21.1）。

## 附录 B：关键文件索引

| 关注点 | 文件 |
|---|---|
| 全局编译参数 | 根 `build.gradle` |
| 版本目录（含刻意排除项注释） | `gradle/libs.versions.toml` |
| 隔离门禁 | `common-api/build.gradle`（checkApiBoundaries）、`common/build.gradle`（checkCommonIsolation、指纹） |
| 契约处理器 | `common-api-processor/.../SpecCoverageProcessor.java` |
| 事件目录 | `common/.../api/catalog/NekoScriptCatalog.java` |
| Python 事件渲染 | `common/.../probe/backend/python/PythonEventRenderer.java` |
| probe 编排 | `common/.../probe/ProbeCoordinator.java`、`ProbeBackendRegistry.java` |
| 平台共享构建 | `gradle/neoforge-common.gradle`、`gradle/neoforge-26-shared.gradle`、`gradle/verify-runtime-artifact.gradle` |
| 产物校验 | `scripts/check-platform-drift` |
| CI | `.github/workflows/ci-build.yml` |
