# Ticket 15 实施报告：启动期注册、typed Builder 与连带注册垂直收口

> 工单：`docs/architecture-refactor/implementation-tickets/15-registry-startup.md`。
> 权威 spec：`08-ported-features-event-surface.md`（Builder 单一写入语义、连带注册、
> Dynamic Registry 分离）、`04-public-contract-and-plugin-model.md`（契约反射派生、
> legacy 观察）、`07-validation-and-migration.md`（golden/五节点 gate）。
> 基线 commit：`eba89230`（认领点）。本报告 commit 区间：`5ac74024..本提交`。
> 结论先行（票面 14 条 AC 口径）：13 条满足；**AC13（旧路径删除）不勾选**——维护者
> sign-off 是发布门禁，本票只交付替代 parity、消费者清单与迁移表（§6）。

## 0. Commit 清单

| Commit | 内容 | AC |
|---|---|---|
| `5ac74024` | `feat(registry): ticket 15 startup registry runtime vertical closure`——StartupRegistryRuntime、BuilderSurface/RegistryBuilderContract/RegistryBuilderSurfaces、契约条目与 TS/Py 渲染器、builder setter 化（共享树 + 1.21.1 六文件）、两 Adapter 薄接线与 epoch | AC1/2/3/5/7/8/9/12 |
| `a0e7d410` | `test(registry): ticket 15 end-to-end, epoch negatives, parity, golden, example fixtures`（+5 suites / +27 tests，两份 golden） | AC2/3/4/10/14 |
| `99896569` | `docs(baseline): ticket 15 registry startup report + examples + migration`（含 REGENERATE.md §1 登记行） | AC6/11/13 材料 |
| 审查整改提交 | `fix(registry): ticket 15 review findings F1-F8`（重载收集、指纹规范化、golden 字节级 gate 与再生成、fabric 错误面等，见 §10） | AC3/4/8/10 补强 |

## 1. 现状测绘（实施前形态）

- **入口**：`RegistryEvents` 事件组（唯一 `REGISTER` 总线，`NekoRegistryPointsPlugin.registerEvents` 注册）；`RegistryEventJS`（ProxyObject）提供糖方法/命名类型/`custom`/裸 `register` 四种入口。本票前 `RegistryEvents.REGISTER.post` 由两个 Adapter 各自直调。
- **攒/抽干**：`RegistryRepository`（先攒后建、duplicate/additional fail-fast、逐注册表 drain）语义已在；两个 Adapter 各自持有 static `REPOSITORY`/`PASSED`/`collected`——平台逻辑与收集生命周期耦合。
- **Builder**：ADR-0005 public field 形态；Graal 对宿主对象的 property 写直接落字段（characterization 证实**不会**落 setter），即 property 写入绕过校验/归一化。
- **声明**：`NekoRegistryDeclarations` 手写 manual declaration（legacy 观察）+ `TypeDocCatalogEntry.binding(STARTUP,"RegistryEvents")`。
- **Dynamic Registry**：`DynamicRegistryJS`（服务器运行期）独立生命周期，`DynamicRegisterMode` 拒绝运行期创建仅启动期对象。

## 2. 逐 AC 判定与证据指针

| AC | 判定 | 证据 |
|---|---|---|
| AC1 唯一 Interface 进入 + 首 pass 前恰好收集一次 + 四种入口到达 Runtime 请求 | **满足** | 生产唯一 post 点收口到 `StartupRegistryRuntime.collectOnce`（grep 全仓 `RegistryEvents.REGISTER.post` 仅此一处生产调用）；`RegistryEventAdapter.onRegister`/`FabricRegistryAdapter.onInitialize` 首 pass 前调用。E2E `successPathCarriesDefinitionRegistryNodeAndFingerprint`（糖/命名/custom/supplier 四入口到达对应注册表请求）+ `collectionHappensExactlyOnceBeforeTheFirstPass`（二次 collectOnce no-op、同注册表二次 drain 为空） |
| AC2 duplicate/additional/default/类型冲突/drain 顺序保留既有语义；失败不留进程级暂存 | **满足** | E2E：`sameBatchDuplicateFailsFastAndKeepsTheFirstEntry`（错误带 first/second 来源）、`typeConflictsAreDiagnosedAtCollection`（未知类型/跨注册表歧义/未知注册表，失败不入库）、`coRegistrationDeliversInTargetPassAndSuppressionOmitsIt`、`coRegistrationTargetingAnAlreadyPassedRegistryIsReportedNotRegistered`（additional-target，不注册不残留）、`additionalCollidingWithAnUnrelatedMainEntryIsRejectedAtCollection`（additional 与来源外主对象冲突 fail-fast，先到主对象保留）、`platformRegisterFailureIsIsolatedPerEntryAndObservable`（按条隔离、无未交付残留）。epoch：`StartupRegistryEpochNegativesTest.failedEpochStagingDoesNotPolluteTheNextBoot` + 两侧 Adapter `beginBoot`（mod 构造期/onInitialize 初丢弃并诊断上一轮残留） |
| AC3 setter 与 property 同一 setter/校验/归一化/fingerprint/收集路径；例外清单；不依赖 Graal 天然 Bean | **满足（审查 F2 补强）** | `BuilderSurface`（ProxyObject putMember seam）两种写法转发同一 `Method`；`BuilderSetterPropertyParityTest`（真实 GraalJS）：`propertyAssignmentAndExplicitSetterProduceSameNormalizedStateAndFingerprint`（含 rarity 小写归一、groupTab 空白归一）、`invalidValuesFailWithTheSameMemberNamedErrorFromBothWriteStyles`、`finalIdentityFieldIsReadOnly`、`nestedSubBuilderConfigGoesThroughTheSameSurface`（子 builder 同面、指纹一致）、`nullItemPropertyAndNoItemMethodAreTheSameWrite`（`b.item = null` ≡ `noItem()`；非 null 赋值在 coerce 期拒绝、错误带成员名——审查 F2 修正断言到真实错误面）。**对象值属性指纹规范化**（审查 F2）：builder 值属性（如 `item` 子 builder）按子指纹递归展开（类型简名 + id + 全部可写读数，环以 cycle 截断），不再内嵌 identityHashCode——由 `objectValuedPropertyFingerprintIsStableAcrossInstancesAndDistinguishesSuppression`（合成 builder，无 vanilla 依赖，<b>全节点真跑</b>）钉住：跨实例同声明同指纹、抑制/存在可区分、子配置变化反映进父指纹。真实 BlockBuilder 嵌套面两用例仍 vanilla-gated（裸 JUnit 恒 skip、1.21.1 被守卫剥离）——机制面已由合成用例全节点覆盖，真实 builder 面归 G1。Graal 天然 Bean 行为被 characterization 证伪（宿主对象 property 写不落 setter，§7 P1），采用 spec 08 预授权的 ProxyObject seam |
| AC4 parity 由 GraalJS runtime contract test 固定 + TS/Py 声明一致成员语义 + 迁移表 | **满足** | runtime contract = parity 测试（上行）；声明一致 = `RegistryBuilderDeclarationParityTest`（common，4 用例：同条目列表两侧 builder 序/成员名序列逐条相同、可写/只读标注一致、逐字节稳定）+ `RegistryBuilderSurfaceGoldenTest.pythonDeclarationRendersTheSameMemberSemanticsFromTheSameEntries`；同名方法重载双形态由 `potionEffectOverloadsAreBothCallableFromScript`（真实 Graal，PotionBuilder 3 参/5 参 `effect` 都可达、错误参数个数仍可诊断）与 `contractCollectsRealOverloadLists`（契约层重载列表真实收集、顺序确定）钉住（审查 F1）；迁移表 MIGRATION.md §2（旧 property 写法继续有效、显式 setter 不引入第二语义、final id 例外） |
| AC5 custom/register(Supplier) 保持可用、唯一 epoch、Supplier 校验、不承诺副作用指纹/回滚、不开 Dynamic Registry | **满足** | `custom`/`register` 入口保留且走同一仓库/epoch（E2E 成功路径）；`bareSupplierReturnValueAndActualTypeAreValidatedAtDrain`（返回 null / 实际类型不匹配 → 带定义/注册表/节点的错误，条目不残留）；重复 ID 收集期 fail-fast（AC2 用例）；`arbitrarySupplierMutableStateIsForwardedNotSnapshot`（不误称深不可变、不承诺副作用指纹）；具名类型仍经 `registry_types` 工厂、裸 Supplier 匿名包装不套具名工厂（`RegistryEventJS` 实现）；Dynamic Registry 未动（AC12） |
| AC6 最小可运行示例 + 迁移材料，只用已 gate 能力 | **满足（连带注册示例为注释段 + 通道级测试承载）** | `examples/registry-startup.js`（五入口 + 连带注册说明段，与测试 STARTUP_EXAMPLE 逐行一致）+ `RegistryStartupMinimalExampleTest`（真实 GraalJS 生产序列跑通、双形状 sink、fully drained）；`MIGRATION.md`（入口能力表 + 8 条迁移要点 + tier 归属 + 已知边界）。真实 Block→BlockItem 全链路需 vanilla/FML（G1） |
| AC7 公开成员/校验/错误由契约反射生成；已验证类型只按裁定连带；不扩大类型/不复制 catalog | **满足** | `RegistryBuilderContract`（反射成员目录：可写/只读/方法 + ENGINE_SEAM 排除）→ `BuilderSurface`（运行时成员与错误：未知成员写拒绝带成员目录、未知读 undefined）→ `RegistryBuilderSurfaces.derive`（结构化条目）。类型清单未扩大（13 内置类型不变）；连带只保留已裁定面（Block→BlockItem 预创建/`noItem()` 抑制、Fluid 四连带——FluidBuilder 逻辑未改，只 setter 化） |
| AC8 MC/loader 只在 Adapter；common 契约零 MC 类型；五节点差异逐项记录 | **满足** | 对象创建/pass 接线只在 `RegistryEventAdapter`（NeoForge 逐 pass）与 `FabricRegistryAdapter`（单批直注）。<b>口径澄清（审查 F6）</b>：`StartupRegistryRuntime` 住版本树共享 MC-facing 层，import 了 MC 类型<b>令牌</b>（`Registry`/`Registries`/`Item`/`Block`/`Fluid`，用于 Supplier 实际类型校验与注册表键表达）——它不创建 MC/loader 对象（对象创建只在 Adapter）、不进 common；不要以「Runtime 零 MC 类型」推演边界。common 新增类型（`RegistryBuilderSurfaceEntry`、两渲染器）零 MC/loader/Graal 之外的 import（guardLint L1/L2 通过：255 守卫块/403 文件/0 警告）；差异表 §3 |
| AC9 runtime member/TS/Py declaration/Probe/contract-golden 同一契约输入派生；legacy 只观察；测试不改写 golden | **满足** | 同一输入 = builder 类 → `RegistryBuilderContract` 反射：`BuilderSurface`（runtime member）、`RegistryBuilderSurfaceEntry` → `RegistryBuilderTsRenderer`（probe `@registry-builders/index.d.ts`）/`RegistryBuilderPyRenderer`（`_registry_builders/__init__.pyi`）、`RegistryBuilderSurfaceGoldenTest` + golden（渲染器即后端生产渲染器）。`NekoRegistryDeclarations` 手写面保留为 legacy 迁移观察（§6）。golden 测试只读；再生成走手动路径 + §4 留痕（root 树无 regen 开关，query 域同款，已在 managed-surface REGENERATE.md §1 登记） |
| AC10 成功/失败/重复/类型冲突/连带缺失/drain 失败从 Interface 贯穿到 Adapter 可观察结果，含定义/注册表/节点/错误来源，不读私有字段 | **满足** | E2E 9 用例全部经生产同款 `EventGroupJS` 绑定 + 真实 GraalJS 监听进入，结果读 `DrainResult`（`RegistrationRecord`：definition/registry/node/origin/fingerprint；`ErrorRecord`：+source 与 message）与 sink 记录，无一处读 `RegistryRepository` 内部结构（`undrainedLiveView` 是公开防御性副本）。fabric 未知注册表条目也走同一错误面（审查 F7：no-op sink 改为抛错 → 每条进 errors（source=platform-register），不再冒充已注册） |
| AC11 五节点 source trace/artifact/最小 smoke；差异显式；不自动补 Fabric parity；experimental 不作 primary | **满足（26.2.0/26.2.0-fabric 全量 build 留主会话，定向证据已备）** | §3 差异表（source trace 维度逐节点）；最小 runtime smoke = 示例测试（GraalJS 端到端）+ golden 测试跨节点（26.1.2 全量、26.2.0 定向、1.21.1 全量、26.1.2-fabric 定向 + compileJava）；不补 Fabric parity（fluid 显式 unavailable，`event.fluid` 得 unknown registry 可诊断错误）；primary 回归只在 26.1.2 |
| AC12 Dynamic Registry 不指向启动 drain、运行期不改 live registry；两生命周期分离 | **满足** | `DynamicRegistryJS`/`DynamicRegisterMode` 未动（运行期拒绝创建仅启动期对象，错误信息指回 `RegistryEvents`）；启动路径只在合法 pass 经 sink 注册，无 live registry 直改；契约/测试/迁移表三处分离口径一致（MIGRATION.md 头注、`StartupRegistryRuntime` javadoc、E2E 不触碰 dynamic 面） |
| AC13 旧类型化入口/手写 declaration/重复 catalog/不受测 wrapper 的删除 | **不勾选（维护者删除门禁）** | 本票交付删除前提材料：替代 declaration parity（AC9 链路 + golden）、旧 route 消费者清单（§6）、迁移表覆盖公开写法（MIGRATION.md）。`NekoRegistryDeclarations` 等旧面**全部保留**；builder public field→setter 属 AC3 裁定的单一写入语义转换（脚本公开写法不变，见 §6 注），非本 gate 对象 |
| AC14 PR37 负例：callback 抛错不留半成品；finish 后容器变化不改快照；live view 不冒充冻结；Supplier 可变状态不称深不可变 | **满足** | `StartupRegistryEpochNegativesTest`：`throwingConfigCallbackLeavesNoDrainableHalfFinishedEntry`（先配置后入库）、`postDrainCollectionChangesDoNotAlterThePublishedSnapshot`（快照不可变、后到声明进下一轮）、`liveViewIsADefensiveCopyNotAFrozenResult`、`failedEpochStagingDoesNotPolluteTheNextBoot`、`arbitrarySupplierMutableStateIsForwardedNotSnapshot` |

**范围外遵守**：未扩大开放 registry 类型；未动服务器运行期 Dynamic Registry 语义；未改事件总线/Plugin Runtime 既有机制（只消费）；无性能阈值设定。

## 3. 五节点差异表

| 维度 | 26.1.2（primary） | 26.2.0（secondary） | 1.21.1（experimental） | 26.1.2-fabric（experimental） | 26.2.0-fabric（experimental） |
|---|---|---|---|---|---|
| 源编译形态 | 直编共享 `src/` | stonecutter 预处理副本（同源） | 预处理副本 + `versions/1.21.1/src` 六 builder 整文件 override + `mc_legacy_api` 替换（`.identifier()`→`.location()`） | 预处理副本 + `src/fabric` raw loader root | 同左 |
| 收集/抽干接线 | `RegistryEventAdapter`（`//? if neoforge`）：逐 `RegisterEvent` pass `drainFor`，Supplier 延迟执行 | 同 26.1.2 | 同（守卫替换后编译，全量 build 通过） | `FabricRegistryAdapter.onInitialize` 单批：先 `collectOnce` 再逐注册表 `drainFor` 直注 vanilla registry（supplier 立即执行） | 同左 |
| epoch `beginBoot` | `NekoJSMod` 构造期 | 同 | 同 | `NekoJSFabricMod` onInitialize 初 | 同 |
| FluidBuilder（`minecraft:fluid`） | 有（注册点行内 neoforge 守卫） | 有 | 有（1.21.1 override 文件） | **无**（整文件 neoforge 守卫剥离；`event.fluid` → unknown registry 可诊断错误，显式拒绝不静默 no-op） | 无 |
| typed Builder golden | `startup-builders.d.ts`（12 类型，161 行，审查 F1 再生成） | 同一份（定向测试通过——预处理同源） | `startup-builders-1.21.1.d.ts`（152 行；**真实成员差异**：六个版本化 builder 无 `tag`、PaintingVariant 无 `author/title`。审查 F1 修正：初版 golden 里的 Potion `effect` 重载形状差异是<b>收集伪影</b>（getMethods 顺序丢重载），修复后两侧统一冻结确定性 5 参形态） | 26.x golden 对 fabric 成立（fluid 条目本就不进 golden；fluid 用例被守卫剥离，golden 套件 3 用例） | 同左 |
| 节点标签（错误/诊断内） | `neoforge:<mc 版本>`（运行期取 `Platform`） | 同 | 同 | `fabric:<mc 版本>` | 同 |
| 本票验证状态 | 全量 build + `:common:check` + guardLint 通过 | 定向 golden 通过；全量 build 主会话统一 | 全量 build 通过（125 tests） | `compileJava` + 定向 golden 通过；全量 build 主会话 | 主会话统一 |
| capability 口径 | 启动注册 supported（证据如上） | supported（预处理同源 + 定向 golden） | supported（成员面差异显式冻结于独立 golden） | 启动注册 supported（单批直注形状）；fluid 类型 unavailable | 同左 |

## 4. golden 变更留痕（REGENERATE.md §3 格式）

**新增两个 golden（本票零改动既有 golden）：`src/test/resources/golden/registry/startup-builders.d.ts`（26.x，161 行）与 `startup-builders-1.21.1.d.ts`（1.21.1，152 行）**

- **原因**：AC9 要求 contract/golden 与 runtime member、TS/Python declaration 由同一契约输入派生。输入 = 生产 `registry_types` 同款 builder 清单（13 类型；fluid 为 NeoForge 面不入 golden）→ `RegistryBuilderContract` 反射 → `RegistryBuilderSurfaces.derive` → `RegistryBuilderTsRenderer`（probe TS 后端生产渲染器）。
- **旧新 diff**：均为新文件（旧值：不存在）。生成方式 = 临时 scratch（已删除）以生产同款 fixture 驱动渲染写 `build/tmp`，人工提升为 golden（root 树手动路径，query 域 ticket 25 同款；REGENERATE.md §1 已加登记行并更新尾注）。
- **影响**：只被 `RegistryBuilderSurfaceGoldenTest` 消费；`api-manifest-core.json`、probe-ts fixture、legacy probe golden 零变化（`:common:check` 通过即证）。两份 golden 的差异即 1.21.1 成员面真实差异（§3）。
- **审阅记录**：owner 自查（zcode-agent，2026-09-15）——逐行核对成员来自真实 builder 反射（含修复 varargs 渲染缺陷后重生成）；**缺维护者审阅**（ticket 14 G6 同款如实标注）。
- 过程修正：初版渲染有 varargs 缺陷（`tag(tags...args: string[])` 非法 TS）——修 `RegistryBuilderSurfaces`（TS `...name: T[]` / Py `*name: T`）后重新生成 golden，缺陷修复与 golden 同批提交。
- **审查 F1 再生成（2026-09-15）**：契约重载收集修复（单 `Method` 收集丢同名重载）后，两份 golden 重新生成并逐节点复核。旧新 diff：**仅 26.x 的 Potion `effect` 签名行一处**——3 参形态改为确定性 5 参形态 （`effect(effect: any, durationTicks: number, amplifier: number, ambient: boolean, visible: boolean): void`），行数 161→161；1.21.1 golden 逐字节零变化（它此前碰巧冻到 5 参侧）。原因：重载列表真实收集后按参数最多者确定性排序，两节点从此冻结同一形态；修复前 26.x 丢 5 参（脚本不可达）、1.21.1 丢 3 参（javadoc 主推写法抛错）。影响：仅 golden 契约面一致化；`RegistryBuilderSurfaceGoldenTest` 四节点（26.1.2/26.2.0/1.21.1/26.1.2-fabric）定向全部通过。

## 5. 测试结果

| 套件 | 结果 | 数字 |
|---|---|---|
| `:common:check` + `:common-api-processor:test` | 通过 | 201 suites / 1478 tests / 0 failures / 4 skipped（+1 suite / +4 tests：DeclarationParity） |
| `guardLint` | 通过 | 守卫块 255 / 扫描 403 文件 / 豁免 0 / 警告 0 |
| `:26.1.2:build` | 通过 | 45 suites / 209 tests / 0 failures / 36 skipped（+5 suites / +30 tests；审查 F1/F2 再 +3 parity 用例） |
| `:1.21.1:build` | 通过 | 33 suites / 128 tests / 0 failures / 0 skipped |
| `:26.2.0:test`（golden 定向） | 通过 | golden 套件 4 用例 |
| `:26.1.2-fabric`：`compileJava` + golden 定向 | 通过 | golden 套件 3 用例（fluid 用例守卫剥离） |

## 6. 旧路径与删除 gate（AC13 输入；本轮零删除）

| 旧路径 | 现状消费者 | 替代物状态 | 处置 |
|---|---|---|---|
| `NekoRegistryDeclarations` 手写 manual declaration（逐类型字符串） | probe manual declaration 渲染（legacySurface 迁移观察链） | 结构化 `RegistryBuilderSurfaceEntry` 派生 + golden 已通过（§4） | **保留**；删除需维护者 sign-off（本表 + MIGRATION 即移交材料） |
| `TypeDocCatalogEntry.binding(STARTUP, "RegistryEvents")` | `NekoJSCorePlugin.registerTypeDocs`（文档/补全） | 契约派生面不覆盖 binding 文档 | **保留**（合法 tier，ticket 14 既有裁定） |
| Builder public field 的 Java 直访（ADR-0005 形态） | 仓内消费者已全部随本票转 getter/setter（编译通过 = 无残留）；脚本面成员名不变（`b.maxStackSize = 16` 经 `BuilderSurface` 继续可用） | setter 单一路径 + parity fixture + golden | 已按 AC3 转换。**注**：这不是 AC13 gate 的「旧类型化启动入口删除」——脚本公开写法（property 形态）原样保留且语义收紧（写入过校验/归一化），Java 侧字段访问属活跃开发期内部 breaking（迁移表 §2.6） |
| Adapter 内 static `REPOSITORY`/`PASSED`/`collected` 平台暂存 | 已被 `StartupRegistryRuntime`（adapter 持实例字段 + epoch）取代 | 语义收口不重写：duplicate/additional/drain 语义由 E2E 钉住 | 已替换（本票垂直收口本体） |
| PR37 单注册表 skeleton 的 `register(id, Supplier)` 历史形状 | 无（已被统一事件签名取代；票文明确不覆盖当前签名） | `RegistryEvents.register` 事件面 | 历史形状，无需动作 |

## 7. 问题与处理

1. **Graal 宿主对象 property 写不落 setter**（设计过程 characterization）：public-field 形态下 `b.maxStackSize = 16` 直写字段、绕过一切校验；私有化后宿主对象 property 写亦不会映射 setter。处理：`BuilderSurface` ProxyObject putMember seam（spec 08 Implementation Decisions 预授权回退路径），两种写法转发同一 `Method`；characterization 结论固化进 parity 测试 javadoc（探测用 ScratchProbeTest 已删除，不入库）。
2. **`IPluginRuntime` 新增抽象方法破坏 15 个 common 测试 stub**：改为 default `List.of()`（与 `TypeDocsRegister.registerRegistryBuilderSurface` 同款处理；真实产物由 `NekoPluginRuntime` 合并提供，两个根树测试 stub 的显式覆盖保留）。
3. **单一 golden 在 1.21.1 不成立**：六个版本化 builder 的成员面有真实差异（无 `TaggableBuilder`、painting 无 author/title）。处理：按版本各冻一份 golden，守卫选择；差异进 §3 表——这是显式记录而非掩盖。**审查 F1 更正**：初版此处与 §3 把 Potion `effect` 重载形状也列为差异——那是契约收集伪影（`Map.put` 同名覆盖只留一个重载，两节点各自碰巧冻到不同形态），修复后差异面只剩 tag/painting 两项（§10-1）。
4. **varargs 参数渲染缺陷**：`tag(tags...args: string[])` 是非法 TS。修复 `RegistryBuilderSurfaces`（TS `...name: T[]`、Py `*name: T`）并重新生成 golden（§4）。
5. **共享测试树跨节点编译**（上轮 KeyBindEvents 教训）：`VanillaRegistryProbe` 是 `>=26` 文件 → parity 两个 BlockBuilder 用例加 `//? if >=26` 守卫；测试树 `.identifier()` 用法加 `//~ mc_legacy_api` 标记（2 个测试文件）；guard else 分支须 `/* */` 包裹 inactive 代码（`EntityExtension` 先例）。
6. **示例测试 ITEM 立即执行需 FML**（裸 JUnit 无 loader）：sink 改双形状——ITEM 按 NeoForge `RegisterEvent` 延迟形状只记录 Supplier，其余立即执行（fabric 直注形状）；两种形状消费同一 `validatedSupplier` 包装。
7. **示例的指纹 parity 断言初版写法错误**：指纹含 id，两条不同 id 的声明本就不相等；改为「脚本 property 写入条目 vs Java 显式 setter 同 id twin」比对（更准确表达 parity 语义）。
8. **工单文件只读**：未改 `15-registry-startup.md` 的 Status/AC 勾选（关票归主会话）。

## 8. 遗留缺口与建议 owner

| # | 事项 | 状态 | 建议 owner |
|---|---|---|---|
| G1 | 真实 Block→BlockItem / Fluid 四连带的 vanilla 内运行证据，含真实 builder 的嵌套 surface/指纹面（parity 两个 BlockBuilder 用例 vanilla-gated 恒 skip；机制面已由合成用例全节点覆盖，通道级 E2E 亦在） | not-verified（通道级 + 合成机制面已验证；真实 builder 面未验证） | 主会话可用 minecraft-mod-mcp 做 in-game smoke（AGENTS.md MCP 通道），或接 ModDev `unitTest` 后由守卫用例自动真跑 |
| G2 | probe 输出 `@registry-builders/index.d.ts` / `_registry_builders` 的真机 `/nekojs probe` 证据（单测验证渲染器与 golden，未跑游戏内 probe） | not-verified | 随 G1 一并 |
| G3 | `:26.2.0:build` / `:26.2.0-fabric:build` 全量未跑（定向 golden + fabric compileJava 已过） | 本票未验证 | 主会话合并后五节点统一 build（既定流程） |
| G4 | golden 审阅缺维护者确认（§4；ticket 14 G6 同款） | owner 自查完成 | 主会话维护者审阅 |
| G5 | AC13 删除 gate：`NekoRegistryDeclarations` 等旧面删除需维护者 sign-off | 材料已备（§6 + MIGRATION） | 主会话维护者裁定 |
| G6 | root 树 golden 无 regenerate 开关（query 域既有后续债，本票沿用并在 REGENERATE.md §1 尾注扩展登记） | 已登记 | managed-surface owner（后续票统一补开关） |
| G7 | `NekoScriptCatalogSnapshot` 保留旧形状兼容构造器（本票真实构造点已全传新参数） | 临时兼容 | 后续清理票（无消费者后删） |

## 9. 需要主会话审查的重点

1. **public field → setter 的脚本面判定**：成员名与可写性不变，但 property 写入现在过校验/归一化（语义收紧，spec 04/08 裁定）；确认接受该收紧面与迁移表口径（§6 第三行注）。
2. **两份 golden 的成员面**：尤其 1.21.1 差异独立成文件的处置（§3/§4），以及 varargs 修复后的签名形状。
3. **`IPluginRuntime.registryBuilderSurfaces` 的 default 方法取舍**（§7 P2）。
4. **AC11 的验证口径**：fabric 只有定向证据 + compileJava，全量留主会话（G3）。

## 10. 双轴审查整改（2026-09-15，随审查修复提交）

| # | finding | 整改 |
|---|---|---|
| F1 | 契约重载收集丢方法：`rest` 单 `Method` 收集按 getMethods 顺序同名覆盖，`deterministicOverload`/`resolveOverload` 因此恒单候选；Potion `effect` 每节点各有一形态脚本不可达（26.x 丢 5 参、1.21.1 丢 javadoc 主推的 3 参），两份 golden 冻结到不同形态是**收集伪影** | `rest` 改 `Map<String, List<Method>>`，真实重载列表进 `Member.overloads()`（参数最多者在前、签名串稳定排序——契约/声明/golden 派生确定，脚本按实参个数解析不受此序影响）；两份 golden 走再生成路径重出（旧新 diff 见 §4：仅 26.x potion 一行变化，1.21.1 零变化）；`RegistryBuilderSurfaces` 失实注释改为「声明面每名冻一个确定性签名、脚本侧按实参解析」；新增 `potionEffectOverloadsAreBothCallableFromScript`（真 Graal 双形态跑通 + 错误参数个数可诊断）与 `contractCollectsRealOverloadLists`（契约层重载列表）；§3 potion 行与 §7 P3 表述更正 |
| F2 | `normalizedPropertyReadings` 对对象值可写属性用 `String.valueOf`——嵌子 builder 内嵌 `@identityHashCode`，同声明跨实例/跨启动指纹漂移 | builder 值改为**子指纹**规范化：`builder[类型简名(id)全部可写读数]` 递归展开（环以 cycle 截断；null=抑制 与存在天然可区分）；`nullItemPropertyAndNoItemMethodAreTheSameWrite` 的错误预期修正到真实错误面（coerce 期 "item ... cannot be converted"，`setItem` 的 "only accepts null" 是 Java 直调防御）；新增 `objectValuedPropertyFingerprintIsStableAcrossInstancesAndDistinguishesSuppression`（**合成 builder、无 vanilla 依赖、全节点真跑**）——真实 BlockBuilder 嵌套面两用例仍 vanilla-gated（AC3/§5 如实标注，归 G1） |
| F3 | `builderImplementsSupplierSoDrainCanLazilyBuild` 同引用自比恒真 | 改为实质断言：`assertSame(builder, BuilderSurface.unwrap(BuilderSurface.of(builder)))`（surface 不改 Java 身份）+ 计数 builder 证明 `get()` 恰构建一次并缓存 |
| F4 | golden 比较只做 normalize，放过空白/注释漂移 | 改字节级 gate（行尾统一 LF 容忍 checkout autocrlf）；不一致时 fail 并给出首差异行 + 「仅空白/行尾」或「内容级差异」结论（内容级再列 normalize 比对，便于定位） |
| F5 | 4 处 `ScriptContextRegistry.unbind` 不在 finally——断言失败泄漏静态绑定 | E2E `collect()`/裸 supplier 用例、负例 2 用例、示例 `runExample()` 全部改 `try { ... } finally { unbind }` |
| F6 | 「平台无关 Runtime」口径失实：`StartupRegistryRuntime` import MC 类型令牌 | `RegistryEventAdapter` javadoc 与 REPORT AC8 更正：Runtime 住版本树共享 MC-facing 层，持有 MC 类型**令牌**（Supplier 校验/键表达），不创建 MC/loader 对象；common 零 MC 不变 |
| F7 | fabric 未知注册表 no-op sink 把条目记进 `registered`——冒充已注册 | sink 抛错 → 每条进 `DrainResult.errors`（source=platform-register，含定义/注册表/节点），错误统一经既有 logger 输出；条目仍被 drain（无残留） |
| F8 | 1.21.1 `PaintingVariantBuilder` javadoc 示例用了本节点没有的 `b.title` | 改 width/height 示例并注明 26.x 成员差异指向 golden 差异表 |
| F9 | `registryBuilderSurfaces` default 空面 | 无需动作（§9 风险点 3 保留） |

整改后全量验证：`:common:check` + `:common-api-processor:test`（201/1478/0/4skip）、`guardLint`（255/403/0）、`:26.1.2:build`（45/209/0/36skip）、`:1.21.1:build`（33/128/0/0）、26.2.0 golden 定向、26.1.2-fabric compileJava + golden 定向——全部通过（evidence/verification-commands.md 同步更新）。

## 11. 合并后 fix-forward（2026-09-17，启动期 P0，提交 `c2348513`）

**症状**：该 revision 起 fml **mod 构造期崩溃**，游戏起不来——`:26.1.2:runGameTestServer`
BUILD FAILED，crash-report 栈：
`NekoJSMod.initializeScripts → NekoRuntimeAssembly.assemble → NekoPluginBootstrap.bootstrapOwned
→ collect → collectPoint(:152) → NekoRegistryPointsPlugin.registerTypeDocs(:52) → registryTypes()(:97)
→ requireResult(:102)` 抛 `IllegalStateException: extension point 'nekojs:registry_types'
has not finished yet (bootstrap incomplete)`。

**根因**：本票新增的 `RegistryBuilderSurfaces.register(registry, registryTypes())` 在
`registerTypeDocs`（`nekojs:type_docs` 扩展点的 collector）里**急切读取后序点产物**——
`type_docs` 是内置点（`NekoBuiltinPointsPlugin` 最先注册，Kahn 同层按注册序第 5 个执行），
而 `registry_types` 由版本树插件在其后注册（且 `dependsOn(registry_infos)`），两者之间没有
排序边 → 数据依赖倒序（违反 ADR-0002 ②「读先序点产物」的模型）。本票实施与双轴审查的
五节点 build / 单测全绿但都不覆盖：**插件图只在真机 boot 时执行**。

**修复**（`common/.../core/plugin/`）：引擎补 ADR-0002 第三种依赖形态——**可选时序依赖**
（`NekoPluginExtensionPoint.Builder#dependsOnOptional/Id`）：仅当对方已注册时加入排序边
（对方缺席的 bootstrap 不建边、不早爆；在场时序保证与硬依赖等同，含环检测）。
`TypeDocsPoint.POINT` 据此声明 `.dependsOnOptionalId("nekojs:registry_types")`
（id 字面量：该点定义在版本树，common 不能引用其类型；common-only bootstrap 不含该插件）。

**回归钉**（`NekoPluginBootstrapV2Test` +4）：`lateProductReadWithoutDeclaredDependencyThrows`
（负例对照：同形状不声明依赖必抛）、`typeDocsPointIsOrderedAfterRegistryTypesWhenPresent`
（真实 TypeDocsPoint 正例，复用版本树插件的句柄急切读取形态）、
`optionalDependencyAbsentDoesNotFailFreeze` / `optionalDependencyPresentOrdersExecution`。

**验证**：`:26.1.2:runGameTestServer` BUILD SUCCESSFUL（日志：`plugin runtime bootstrapped once`、
`发现了 2 个 STARTUP 脚本`、`正在为 STARTUP 注册 13 个事件组`、STARTUP 示例脚本执行，零 NekoJS 报错）；
`:common:test` 绿、`guardLint` 0 警告。

**教训（并入后续票验证协议）**：① 改动插件图/启动面（Point 增删、collector 里的跨点读取、
插件发现与注册序）**必须补一次真机 boot**（`runGameTestServer`），build+unit 不覆盖；
② `NekoPluginExtensionPoint` 的"数据依赖"只能在 collector/initializer 里读**先序**点，
读后序点要么声明排序边（对方可能缺席时用可选形态）、要么改由后序点写入。
