# 票 08 证据报告：真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活

日期：2026-09-22
Worktree：`D:/mcmodDemo/NekoJS-mult-t08`（分支 `ticket-08-plugin-addon`，基线 `7768e02d`）
执行者：zed-flash-08（GLM-5.3 subagent worktree）

## 1. 范围

用真实外部 addon fixture 证明 Java 插件能通过 loader discovery 与 jar 依赖进入 Plugin Runtime，
沿既有 Point/Contributor/Hook 贡献，bootstrap 后经 Extension Handle 与脚本 binding 消费同一
冻结产物；普通 reload 不重新 bootstrap/freeze 插件，generation session token 失效。只补真实
消费链与边界修正，不新增生产 Point、不造第二套插件框架。

## 2. 改动清单（What changed）

### 2.1 test-only 外部 addon fixture 制品（AC1）

| 文件 | 作用 |
|---|---|
| `common/src/addonFixture/java/com/example/addon/*` | fixture 插件：主插件（priority 1200，provider + hook 双通道）、次插件（priority 400，纯 Contributor）、clientOnly 插件、requiredMods 缺失插件、脚本可见 surface、自定义点 `exampleaddon:greetings`（`dependsOnId("nekojs:bindings")`，Sealable 累积器） |
| `common/src/addonFixture/resources/META-INF/neoforge.mods.toml` | NeoForge 生产格式 metadata（modId `exampleaddon`） |
| `common/src/addonFixture/resources/fabric.mod.json` | Fabric metadata（`entrypoints.nekojs`） |
| `common/build.gradle` | `addonFixture` source set：编译 classpath **只有** common main 输出（结构上排除 test seam）；`externalAddonJar` 制品任务；`verifyAddonFixtureDependencySurface` 门禁（挂 `check`）；test 任务注入 jar 路径 |
| `buildSrc/.../nekojs.{neoforge,fabric}-node.gradle.kts` | 五个节点 test 任务同样依赖 fixture jar 并注入路径 |
| `stonecutter.gradle.kts` | `verifyExternalAddonIsolation`（挂 `sandboxCheck`）：正向校验 fixture jar 带两类 loader metadata；反向断言五个生产节点 jar 均不含 fixture 类/资源/metadata 痕迹（数量硬断言 = 5） |

fixture 的依赖面 = common main 输出（api.* + core.plugin 提供者 API），是未来第三方针对发布
fat jar 编译面的**子集**——"依赖面与第三方一致，不 import 生产内部测试 seam"由编译期
classpath 结构保证，`verifyAddonFixtureDependencySurface` 持续守护。

### 2.2 addon 定位失败输出（AC9，本票唯一生产行为改动）

`NekoPluginBootstrap`：provider 注册经 `registry.scopedTo(ownerLabel)` 收到带 addon 定位的
注册视图；`ExtensionRegistry` 记录 pointId → 注册方标签。四类失败（重复 id、freeze 后迟到
注册、未知依赖、依赖环）报错均点名肇事插件（owned bootstrap 用 owner id，plain 重载用类名）。
英文异常消息；未新增问题上报型日志，故未引入 NEKO- 码（见 §7）。

### 2.3 legacy 删除（AC14）

- 删除 `NekoPluginRuntime.bootstrap(List<NekoJSPlugin>, ...)`（@Deprecated，唯一调用方是
  `NekoSandboxFactoryResourceTest`；两 loader 生产路径自票 05/06 起全部走 `bootstrapOwned`，
  外部 fixture 也不需要嵌入入口——按票内条件删除）。`NekoSandboxFactoryResourceTest` 移植到
  owned 路径（新增共享 fixture `CoreContractPreviews.emptyPortablePreview()`）。
- `NekoJSBasePluginManager` 保留：两 loader 均有生产调用方（发现 → 注册缝）。
- 本分支不存在 loader 私有 Point registry 旁路（检索 `src/`、`versions/*/src`、`src/fabric`
  无第二注册面；`NekoRegistryPointsPlugin` 走标准 provider 注册）。

### 2.4 测试（新增 14 例）

- `common/src/test/.../Ticket08ExternalAddonChainTest`（11 例）：jar + fabric.mod.json 双形态
  discovery（经生产缝 `NekoJSBasePluginManager.registerClass`，含 owner identity=jar、
  clientOnly/requiredMods 过滤、priority 排序、同输入幂等）；贡献（hook 与显式 Contributor
  等价、dependsOn 拓扑、initializer 读冻结 bindings 产物）；Handle/result/binding 三口径同一
 冻结对象（`assertSame`）；Handle finish 前拒绝读取与 skipped 点永不发布；Sealable 累积器
  finish 后写入拒绝 + 非 Sealable 累积器迟到写入不影响已发布产物；reload 存活（init/
  registration 恰一次、Handle/Point 结果跨 reload 同一对象、两代脚本读同一 binding 实例、
  `closeCalls=0`）；旧 generation token 失效（`activeGenerationOf=-1`、域查询显式
  `generation-not-active`）；四类 addon 定位失败输出。
- `src/test/.../Ticket08LoaderDiscoveryTest`（3 例，共享树，五节点各跑一遍）：注解/mod
  metadata 扫描形态 + fabric.mod.json entrypoint 形态 + 干净重复执行一致性 + fixture 不在
  节点类路径。

### 2.5 示例与迁移材料（AC7）

- `examples/external-addon/`：最小可运行外部 addon（插件类 + 两份 loader metadata + README
  公开构建说明）。已用 JDK 21 `javac -cp common/build/classes/java/main` 独立编译验证——
  只用公开依赖与入口。
- `MIGRATION.md`：插件作者迁移材料（两种 loader 发现输入、唯一 Point 生命周期、三层冻结
  边界、reload 语义、addon 定位失败输出、从嵌入式注册迁移的检查单）。

## 3. red → green 证据（核心生命周期断言）

TDD 顺序：先写 `Ticket08ExternalAddonChainTest`（含尚未实现的 addon 定位断言），跑
`:common:test --tests ...Ticket08ExternalAddonChainTest`：

- **RED**（2026-09-22，`command-output/07-red-green-chain.txt` 摘录）：
  `11 tests completed, 7 failed`——4 个 addon 定位断言失败（报错无 owner 名：
  `Plugin extension point 't08:dup' is already registered == expected: <true> but was: <false>`
  等），另有 3 个 harness 修正（合同集、属性注册表、脚本绑定名）逐一转绿。
- **GREEN**：实现 `scopedTo`/registrants 归因后 `11 tests completed, 0 failed`；
  `:common:check` 全绿（1774 tests, 0 failures——含既有 `NekoPluginExtensionProviderTest`
  与 `PluginHookPairingTest`，报错形态变化未破坏既有断言）。

reload 存活断言本身在实现前后都是绿的——本票**没有**为此改生产代码（这正是票的价值：既有
边界已对，缺的是真实外部证据）；过程中发现并修正的三处均为测试 harness 侧（见 §8 审查记录）。

## 4. 验证命令与结果（全部在 worktree 根执行）

| 命令 | 结果 |
|---|---|
| `./gradlew.bat :common:check --console=plain` | **通过**（含 `checkCommonIsolation`、`verifyAddonFixtureDependencySurface`、`PluginHookPairingTest`；1774 tests / 0 failures） |
| `./gradlew.bat guardLint --console=plain` | **通过**（守卫块 294，扫描 442 文件，0 豁免 0 警告） |
| `./gradlew.bat :26.1.2:test --tests com.tkisor.nekojs.core.Ticket08LoaderDiscoveryTest` | **通过**（3/3；重复第二次执行同样通过——干净 run/管理器状态 + 同一 jar 输入结果一致） |
| `./gradlew.bat :26.1.2-fabric:test --tests com.tkisor.nekojs.core.Ticket08LoaderDiscoveryTest` | **通过**（3/3；重复第二次执行同样通过） |
| `./gradlew.bat verifyExternalAddonIsolation --console=plain` | **通过**（五个生产 jar 全部构建并断言不含 fixture 内容；fixture jar 元数据齐全） |
| `./gradlew.bat :26.1.2:runServer`（mods 目录放 fixture jar + startup 冒烟脚本） | **通过**——FML `Found valid mod file ... {exampleaddon}`，真实注解发现注册主/次插件（clientOnly 与 requiredMods 缺失者被过滤），冒烟脚本打印 `T08-ADDON-SMOKE: startup binding ok, marker=exampleaddon-frozen-product init=1`，服务器 `Done (4.300s)`（`command-output/02`） |
| `./gradlew.bat :26.1.2-fabric:runServer`（mods 目录放 fixture jar + 冒烟脚本） | **通过**——FabricLoader 加载 `exampleaddon 1.0.0`，`nekojs` entrypoint 真实发现，同一冒烟标记 + `bootstrap done`（`command-output/01`） |
| `javac -cp common/build/classes/java/main .../examples/external-addon/*.java`（JDK 21） | **通过**（示例只用公开面编译） |

**未跑/不适用**（如实记录）：

- `:26.2.0`、`:1.21.1`、`:26.2.0-fabric` 的单节点 discovery 测试未逐个跑（共享树测试五节点
  同构，本票在 primary NeoForge 26.1.2 与 primary Fabric 26.1.2-fabric 上验证；isolation 门禁
  覆盖全部五节点 jar 构建）。`gameTestServer` 未跑（`runServer` 已给出真实 loader 证据）。
- Fabric `verifyFabricRuntimeArtifact`/`platformGateTest` 未单独跑（未改动相关面；
  `:26.1.2-fabric:test` 通过）。
- `sandboxCheck` 聚合未整体跑（其组成部分 guardLint + `:common:check` + 五节点
  `verifyExternalAddonIsolation` 各自通过）。

## 5. 缺口与 owner（Gaps）

1. **AC2 的"至少一个 Fabric 节点通过真实 fabric.mod.json entrypoint discovery"**：已由
   26.1.2-fabric 的 JUnit 形态测试 + 真实 `runServer` 双重覆盖；26.2.0-fabric 未单独跑
   （同构共享树，owner：后续例行 CI）。
2. **AC13 的"loader 启动/烟测路径"**：NeoForge 26.1.2 与 Fabric 26.1.2-fabric 的
   `runServer` 已覆盖；其余三节点的 loader 启动路径未跑（owner：CI / 票 34 例行 smoke）。
3. **classloader 泄漏面**：fixture 测试每次开新 `URLClassLoader` 且测试内关闭（try-with-
   resources）；jar 文件句柄由 `JarFile.use` 管理。未做多轮长驻类加载器泄漏剖析（超出本票）。
4. **NEKO- 码**：本票未新增问题上报型日志（新失败输出全部是开发者面异常消息），故未动
   `wiki/en_us/Error-Reference.md`——该页当前仅存在于维护者 `stonecutter` 分支（2fec8ada），
   本分支无此文件也无既有码表；为避免编号冲突刻意不引入新码。若维护者合流后要求异常也带
   码，另开小票（owner：维护者裁定）。
5. **docs/agents/coding.md 在本分支不存在**（AGENTS.md 引用但文件缺失）——NEKO- 编号区域
   规则无法本地核对，按第 4 条处理。

## 6. 契约影响

- 公开契约无 breaking 变化：`NekoPluginBootstrap.bootstrap(plugins, props)`（包内 seam）、
  `bootstrapOwned` 签名不变；`NekoJSPlugin`/Point/Handle API 不变。
- 删除项：`NekoPluginRuntime.bootstrap(List<NekoJSPlugin>, ...)` —— @Deprecated、无生产调用
  方、javadoc 明示"API freeze 后计划移除"；属票内授权的 legacy 删除（AC14）。
- 异常消息文本变化（addon 定位增强）：按 AGENTS.md 属开发者面异常消息（英文、无修复指引
  要求）；既有测试的 `contains` 断言全部保持绿。
- 无 golden/基线文件变化。

## 7. 日志与错误码说明

本票生产代码零新增日志语句；`NekoJSBasePluginManager` 既有 debug/error 日志未动。新增失败
输出为 `IllegalStateException`/`IllegalArgumentException` 异常消息（英文、带 addon 定位），
与仓内既有同类异常一致，不带 NEKO- 码（见 §5.4）。

## 8. 审查记录（过程中的修正）

1. `NekoPluginBootstrap.collect` 签名扩展（加 ownerLabels 参数）：包内可见 seam，两个调用方
   （plain/owned bootstrap）各自提供标签函数；`NekoBuiltinPointsPlugin` 注册视图标签为
   `nekojs:builtin-points`。
2. 测试 harness 三处自我修正（非生产缺陷）：空 `VerifiedContractSet` 会被
   `FrozenApiRegistrySet` 拒绝（补 `CoreContractPreviews`）；`ScriptManager` 需要已注册的
   文件头属性（AFTER/MODLOADED/DISABLE/PRIORITY）；冒烟脚本绑定名与注册名不一致
   （`ChainRecorder`）。
3. 冒烟 run 目录用后即删（`versions/*/run*`）；测试对共享 server_scripts 目录做"停靠-恢复"
   隔离，避免与其它测试类的脚本互扰。

## 9. 复现

```bash
cd /d/mcmodDemo/NekoJS-mult-t08
./gradlew.bat :common:check --console=plain
./gradlew.bat :26.1.2:test --tests "com.tkisor.nekojs.core.Ticket08LoaderDiscoveryTest" --console=plain
./gradlew.bat :26.1.2-fabric:test --tests "com.tkisor.nekojs.core.Ticket08LoaderDiscoveryTest" --console=plain
./gradlew.bat guardLint verifyExternalAddonIsolation --console=plain
# 真实 loader 冒烟（详见 command-output/01、02 的准备步骤）
```
