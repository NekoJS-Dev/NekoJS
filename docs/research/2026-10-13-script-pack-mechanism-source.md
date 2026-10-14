# NekoJS 脚本包机制源码盘点

日期：2026-10-13。
核对基线：`fe89c770d2d47efe1d3f72421328565eaf7dd889`（`stonecutter`）。
范围：盘点现有脚本包的发现、执行、信任、同步、生命周期与测试证据，为后续改进范围选择提供事实基础。此文只作源码研究，不代表实现提案已落地。

## 结论摘要

- 当前“脚本包”是由 `manifest.json` 标识、从目录中发现的脚本/内容集合。普通脚本目录 `nekojs/{startup,server,client,test}_scripts` 仍是独立的平铺脚本入口；没有隐式的“默认包”。
- 包之间有扫描和执行顺序、来源 ID、世界激活/反激活、服务器缓存和同步签名；但每种 ScriptType 的活跃环境仍由同一个 ScriptManager/Context 执行，完整重载另行准备候选环境。包本身不是隔离的 runtime、模块图或权限单元。
- manifest 当前只是宽松解析的元数据集合。它没有当前生效的 schema version、semver 约束、包依赖图、包级权限或显式入口字段。脚本文件的 `after`/`priority` 是另一套**文件级**排序机制。
- `SERVER_CACHE` 远端包有本地文件缓存、签名/哈希/服务器信任、暂存和补偿回滚；这是包分发事务，不是动态 registry 的多参与方 PREPARE/ACK/COMMIT 协议。普通包同步没有应用层客户端执行 ACK。
- 已有代码提供启动/环境级加载、候选 generation reload，以及非 STARTUP 文件 reload 的失效后重跑。最后一项在活动 Context 内直接操作，不是新 generation 候选提交；STARTUP 定向文件 reload 会退化为完整 STARTUP reload。
- `docs/research/cordis-v4-reassessment.md` 记录的 G1 动态 capabilities、G2 scoped instances、G3 resource ownership、G4 local reload 是维护者确认的需求方向/研究提案；它们不是本次盘点到的当前 runtime 能力。后续设计可把本报告的现有边界与这四项逐一映射，避免复用提案字段或名称时误称为现状。

## 1. 术语与目录边界

### 脚本包、Minecraft 资源包、模块

此处的 ScriptPack 是 NekoJS 发现/执行的目录实体，包含 `manifest.json`、一个或多个类型脚本目录及可选的 `assets/`、`data/`。它不是 Minecraft 客户端资源包格式本身。NeoForge 有 NekoJS 总根目录的资源/数据 pack 挂载器；另一方面，ScriptPackDataManager 会为每个启用脚本包单独挂载其 `data/` 内容。现有资源路径解析器读取的是全局 `nekojs/assets/...`；源码注释明确将客户端逐包 `assets/` 挂载列为范围外。因此不要把“可同步/签名的 pack 内容”理解为“这些内容都作为客户端 Minecraft 资源包被挂载”。

JS/TS 模块是脚本间的 import/require 目标，沿模块解析器的文件路径和 shared `node_modules` 查找；它不是独立的 NekoJS ScriptPack API。当前没有“每个包自己的模块解析器”证据。

- [ScriptPack 模型](../../common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPack.java#L22-L38)、[类型脚本发现](../../common/src/main/java/com/tkisor/nekojs/core/ScriptLocator.java#L20-L27)
- [NeoForge NekoJS 资源根挂载](../../src/main/java/com/tkisor/nekojs/resource/NekoJSPackLoader.java#L27-L62)、[逐包 data 挂载](../../src/main/java/com/tkisor/nekojs/resource/ScriptPackDataManager.java#L31-L45)、[全局磁盘 UI 资源路径](../../common/src/main/java/com/tkisor/nekojs/api/ui/DiskPackUiResourceResolver.java#L6-L14)

### 根目录、发现规则和默认入口

`NekoJSPaths` 定义游戏根下 `nekojs/`，其中 `packs/` 是 GLOBAL 根，`server_packs/` 是远端缓存根。WORLD 包根是当前世界目录下的 `nekojs_packs/`。注册表只列举根目录**直接子目录**，子目录中必须存在可解析的 JSON 对象 `manifest.json` 才被当作 pack；不存在或损坏的 manifest 会跳过。它不会递归寻找嵌套 pack。

普通 `<gameDir>/nekojs/{type}_scripts/` 是不带 manifest 的平铺目录，由 ScriptLocator 在所有启用 pack 后继续扫描。它也不是“隐式 default pack”：没有 pack id、独立 enabled 状态或 pack scope。注册表第一次被访问时懒扫描 GLOBAL 一次，随后由显式 refresh 更新；WORLD 随世界生命周期扫描/卸载。SERVER_CACHE 仅在同步 bundle 处理时以选定 sync id 激活，停用时保留磁盘缓存。

- [游戏目录路径](../../common/src/main/java/com/tkisor/nekojs/core/fs/NekoJSPaths.java#L65-L107)
- [pack 根和 enabled 顺序](../../common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackRegistry.java#L30-L50)、[GLOBAL refresh 与 WORLD 生命周期](../../common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackRegistry.java#L68-L110)、[SERVER_CACHE 激活/停用](../../common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackRegistry.java#L116-L145)、[直接子目录扫描、ID 与 enabled 判定](../../common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackRegistry.java#L168-L204)
- [scope 根语义](../../common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackScope.java#L8-L30)

启用包按 GLOBAL → WORLD → SERVER_CACHE 拼接；每个 scope 内按**有效 id**排序。每个 scope 单独查重复 id，目录遍历和有效 id 都排序/规范化。Manifest 解析后，注册表还会再次 sanitize `manifest.id()` 并在变更时警告，`manifest.raw()` 则保留原始 JSON。需要设计签名或迁移契约时，应明确目录名、manifest 原始 id、有效 id、scope 和 sync id 的规范化关系；这里仅记录行为，不推断其形成漏洞。

### ScriptType、脚本目录与文件次序

ScriptType 为 STARTUP、SERVER、CLIENT、TEST，对应 `startup_scripts/`、`server_scripts/`、`client_scripts/`、`test_scripts/`。四类都可被执行/指定；自动加载集合是 STARTUP、SERVER、CLIENT，不含 TEST。pack 内按注册表顺序先于平铺根目录发现；目录内按路径排序。扫描接受当前 file policy/compiler registry 提供的扩展，且路径含 `node_modules` 的文件被排除。包脚本的 ScriptId path 按来源带 `packs/<id>/`、`worldpacks/<id>/` 或 `serverpacks/<id>/` 前缀，容器还保留 pack id 和 scope，便于清理该来源注册的监听器。

随后脚本属性的排序器按 priority 降序，并对同 priority 条目的 `after` 引用作稳定拓扑排序；缺失引用和环有回退行为。这是**脚本入口**排序，不是 manifest 声明的 pack 依赖。当前清单模型没有依赖列表，也没有按依赖图解析包或定义依赖失效传播。

- [ScriptType 与自动加载集合](../../common/src/main/java/com/tkisor/nekojs/api/ScriptType.java#L7-L92)
- [pack 优先于平铺根的发现顺序与脚本容器 ID](../../common/src/main/java/com/tkisor/nekojs/core/ScriptLocator.java#L20-L68)、[扩展过滤和路径排序](../../common/src/main/java/com/tkisor/nekojs/core/ScriptLocator.java#L99-L116)
- [文件级优先级和 after 排序](../../common/src/main/java/com/tkisor/nekojs/script/ScriptLoadOrderSorter.java#L84-L118)、[排序元数据语义](../../common/src/main/java/com/tkisor/nekojs/script/ScriptLoadOrderSorter.java#L284-L291)

### Manifest 字段、默认值与验证

当前模型读取 `id`、`name`、`version`、`description`、`authors`、`enabled`、`clientSync`、`signature`、`config`。`id` 缺失时用清洗后的目录名；注册表还会清洗显式 id。name 默认清单解析时的原始/回退 id，不会随注册表再次清洗有效 id 而更新；version 默认 `unknown`，description 默认空串，authors 默认空列表，enabled 默认 true，clientSync 默认 true。signature/config 只有 JSON 对象时保留；未知键随 `raw` 保留。损坏 JSON、非对象根或无法读取会告警并跳过。类型不符合字段预期时采用默认值，而非 schema 错误。

`version` 是未解析的字符串；当前这条机制没有可见的 semver 约束、manifest schema version 或依赖字段语义。`config` 保留为 JSON 对象，但没有找到 pack runtime 对它的读取/配置能力消费路径。`enabled` 写在 manifest 中，另有同目录 `.neko_pack.state.json` 覆盖本机启用值；若无 state 则采用 manifest 默认值。SERVER_CACHE 强制启用，不受 manifest/state 控制。

- [manifest 数据模型和宽松默认值](../../common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackManifest.java#L17-L72)、[字段类型解析](../../common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackManifest.java#L75-L100)
- [state 覆盖及写入实现](../../common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackState.java#L17-L48)、[扫描时有效 id、重复和默认启用](../../common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackRegistry.java#L181-L202)

### Module 解析范围

相对导入基于发起模块路径；bare specifier 使用共享的 `<gameDir>/nekojs/node_modules` 根和当前 file policy 的扩展/`index` 候选，并限制 bare 路径留在该 node_modules 根内。通用解析路径通过 real path/symlink 检查，限制在 gameDir 内。没有包根级的相对 import containment 检查；据此可推断同一环境中的文件相对导入可能越过 pack 目录边界，但这需要结合真实脚本布局和调用语义确认，不能推成已经发生的跨包访问事故。

源码所示 bare resolver 直接用 specifier 拼接共享 node_modules 路径并尝试扩展名/index 文件。此处没有根据 Node `package.json` 的 main/exports 做解析的实现证据。JS loader 对模块请求的更高层委派行为未在本次盘点中完整核实，因此不对完整 npm 兼容面作结论。

- [bare 模块路径](../../common/src/main/java/com/tkisor/nekojs/core/module/NekoModuleResolver.java#L92-L107)、[扩展和 index 候选](../../common/src/main/java/com/tkisor/nekojs/core/module/NekoModuleResolver.java#L120-L157)、[gameDir/node_modules containment](../../common/src/main/java/com/tkisor/nekojs/core/module/NekoModuleResolver.java#L160-L179)、[相对路径计算](../../common/src/main/java/com/tkisor/nekojs/core/module/NekoModuleResolver.java#L182-L195)

## 2. 执行、隔离、权限和资源所有权

### Pack 不是 runtime 隔离边界

`NekoRuntimeRoot` 是当前 runtime owner，持有各 ScriptType 的 ScriptManager 和 root stores。每个 ScriptType 有一个 manager/environment/context；同类型多个 pack 的入口在这个 Context 中加载。因此包当前是发现/归属/同步单位，并不自动获得独立 Context、global namespace、模块缓存或资源清理域。ScriptId 的 pack 前缀支持按包清监听器等注册，但这不等同于包内所有宿主资源均由独立 owner 托管。

`GenerationGlobals` 提供按 ScriptType 私有的 `global` 和显式跨类型 `shared`，视图绑定于 generation。Full reload 可用候选 generation 切换；根对象/plugin runtime 等 root-owned 资源不是每次普通 reload 都重建。不要把每个包解释成已有的 generation/runtime。

- [runtime root 和类型 manager 所有权](../../common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java#L48-L64)、[reload candidate/commit 边界](../../common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java#L201-L252)
- [generation globals 分层](../../common/src/main/java/com/tkisor/nekojs/core/state/GenerationGlobals.java#L9-L82)、[generation 关闭行为](../../common/src/main/java/com/tkisor/nekojs/core/state/GenerationGlobals.java#L192-L202)
- [容器保存来源 pack/scope](../../common/src/main/java/com/tkisor/nekojs/script/ScriptContainer.java#L17-L50)

### 文件权限与远端信任

Sandbox 是 ScriptType Context 级配置，不读取 pack manifest 的 capabilities。文件系统 policy 允许读取 gameDir；写入受全局 `allowFsWriteOutsideNekojs` 控制，默认限制在 `nekojs/`，并保护 `nekojs/config/` 与旧 engine config。Graal 文件访问、Node FS 都经过策略。`NekoSandboxFactory` 还约束 Java host-class lookup、线程、process 等能力；这些是 Context 策略，不是包级 ACL。故应准确表述为“存在环境级沙箱边界”，不能据此宣称每个 pack 相互隔离或某个 host object 的完整能力都被拒绝。

本机普通包没有经过远端签名验证的门槛。远端同步包则额外标为 remote source；模块信任上下文根据已验证/授权文件路径判决远端来源权限。签名只保护内容真实性/来源 pinning，不提供独立沙箱。远端资源被撤销授权后，缓存文件可留在磁盘，但不应因其位于本地而自动变成本机可信包。

- [Graal Context 能力设置](../../common/src/main/java/com/tkisor/nekojs/core/NekoSandboxFactory.java#L121-L171)、[文件读写边界](../../common/src/main/java/com/tkisor/nekojs/core/fs/SandboxPolicy.java#L20-L80)
- [remote-source 信任上下文](../../common/src/main/java/com/tkisor/nekojs/core/module/NekoRuntimeTrustContext.java#L18-L67)

### Reload 单位和异常边界

普通首次加载发现所有启用包/平铺入口。完整非 STARTUP 环境 reload 走 candidate generation，成功才替换当前 generation；STARTUP 包含可能不可逆的注册，reset+load 不具有同样的回滚承诺。非 STARTUP 文件 reload 先找受影响入口（包括依赖图），在活动 Context 中失效模块并重跑这些入口；失败时 finally 清理尚未重跑入口的旧副作用，恢复需要再次 reload。它不是 candidate generation 上的局部提交。STARTUP 文件 reload 如果目标已加载，则退化为完整 STARTUP reload。

当前代码因此包含多种 reload 语义：首次/STARTUP 非事务加载、环境级候选切换、文件级活动 Context 失效重跑。若后续做包局部 reload，契约需要明确资源登记、依赖闭包、失败时旧副作用能否回滚、与活动 generation 的交互；不能仅把 “package” 作为现成事务单位。

- [ScriptManager 初始化和完整 reload](../../common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java#L602-L719)、[STARTUP reload 明确标为非事务](../../common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java#L760-L772)
- [文件 reload active-context 失效/重跑/清理](../../common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java#L1257-L1338)、[STARTUP fallback](../../common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java#L231-L249)

## 3. 签名、内容边界、工作区文件

### 哈希和签名覆盖范围

`PackHasher` 对 manifest 原始文本和六类内容目录中按相对路径排序的文件计算 hash：四个 script type 目录、`assets/`、`data/`。根目录 `.neko_pack.state.json` 不在这些内容目录中，因而本地 enabled state 不是此 hash 的覆盖对象；不要推断切换启用状态会使签名失效。`node_modules` 路径被过滤。签名 payload 绑定域分隔符、syncId、scope、去掉 signature 的规范 manifest，以及相对路径/字节内容；算法限定 Ed25519。

- [哈希目录、过滤与 manifest 输入](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackHasher.java#L18-L72)、[有序内容摘要](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackHasher.java#L72-L95)
- [签名载荷绑定字段](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSignaturePayload.java#L13-L54)、[验证与 allowUnsigned 判定](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSignatureVerifier.java#L36-L121)

### Workspace Generator 的写入约束

workspace generator 对启用的本机 unsigned pack，为**已有类型脚本目录**创建缺失的 `jsconfig.json`；不覆盖既有文件。若 manifest 存在任意 signature 对象，或 scope 为 SERVER_CACHE，则跳过 pack workspace 写入，避免签名内容/接收缓存被生成器改写。因为这些文件位于脚本内容目录内，pack-local IDE 配置一旦纳入签名前准备会成为哈希内容；生成器自身明确要求先纳入再签名。此保护依据的是 signature block 是否存在，是保守的禁止写入标记，不等于本地签名真伪验证。

- [启用包 workspace 生成与签名/cache 跳过](../../common/src/main/java/com/tkisor/nekojs/script/WorkspaceGenerator.java#L73-L117)
- [保留用户编辑配置的测试](../../common/src/test/java/com/tkisor/nekojs/core/plugin/WorkspaceGeneratorModifyConfigHookTest.java#L119-L190)、[已有配置不覆盖测试](../../common/src/test/java/com/tkisor/nekojs/script/WorkspaceGeneratorPreserveTest.java#L31-L57)

## 4. 服务器同步和缓存生命周期

### 服务端打包与客户端安装

服务端 gather 已启用的 GLOBAL + WORLD 包，排除 SERVER_CACHE，并仅选 `clientSync=true` 的包。`off` 停止同步；`hashOnly` 发送清单 hash、客户端不执行并会清掉/停用当前远端包；`all` 走 bundle。代码限制单次传输的包数、文件数、单文件大小、manifest 大小和总字节数。

配置阶段先发 hash list，再按 mode 发 bundle。Fabric 和 NeoForge 各自的接收器调用共同的 PackSyncClient 核心管线，平台接收器使用同一主线程 latch 协议等待落盘、验证及执行结束；拒绝时返回断开原因。包协议本身没有观察到 server-side 应用 ACK/双端 prepare-commit 控制消息。公开的客户端文件缓存不能据此描述成有增量复用协议：当前入口下服务器按所选包发 bundle，并由客户端处理 replacement。

客户端先处理地址 bucket 与预期 hash。切换连接时撤销旧 source 信任并卸载旧 active set；同 bucket 替换可在新 bundle 校验期间保留旧集。bundle 在 bucket 下 staging、解析 manifest、校验签名和内容、检查服务器信任、再次校验落盘缓存，形成 remote source 权限；随后备份/替换物理缓存、激活选中 SERVER_CACHE、授权 runtime source 并触发 CLIENT reload。失败尝试恢复旧文件和旧 runtime trust；断线停用 SERVER_CACHE 并撤销 trust，缓存字节保留。staging 在结束清理。具体可靠性由单元测试覆盖，但本次没有执行这些测试。

- [服务端范围、mode 与包上限](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncServer.java#L28-L73)、[hash 的 scope/id](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncServer.java#L70-L73)
- [配置阶段 hash/bundle 顺序](../../src/main/java/com/tkisor/nekojs/network/PackSyncConfigurationTask.java#L17-L58)、[NeoForge 主线程 handler](../../src/main/java/com/tkisor/nekojs/network/PackSyncMessageHandler.java#L12-L66)、[Fabric 接入和接收器](../../src/fabric/java/com/tkisor/nekojs/fabric/FabricPackSync.java#L31-L148)
- [客户端 hash-list / 切换连接行为](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncClient.java#L98-L175)、[bundle 验证、暂存、替换和 reload](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncClient.java#L179-L375)、[断线/清理](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncClient.java#L382-L420)、[带内容 unsigned source 条件](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncClient.java#L645-L661)
- [缓存路径及文件路径约束](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/ServerPackCache.java#L52-L80)、[暂存和文件落盘](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/ServerPackCache.java#L97-L137)、[目录替换与清理](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/ServerPackCache.java#L188-L250)

客户端配置 `allowUnsigned` 有两段不同门槛：签名验证器会按开关接受无签名 bundle；后续 remote-source 组装却在“无 `keyId` 且文件非空”时返回失败。因此不能把它总结为任意无签名内容一定可执行。此为源码组合可见的行为/潜在配置一致性缺口，本次没有通过运行时或端到端新增案例确认其用户表现；空文件包也不触发同一拒绝条件。

信任状态放在 `nekojs/config/trusted-servers.json`，服务器地址 trim+小写后 hash 成 bucket；显式 trust 命令记录服务器。成功接收时可按 keyId pin pack 公钥；同一个已经 pin 的 keyId 携带不同公钥会被拒绝。公钥索引以 keyId 为键，服务器信任以地址 bucket 为键，不能扩写成“每个服务器只允许一个固定公钥，任何新 keyId 都自动拒绝”。缓存目录按地址 bucket 隔离；SERVER_CACHE 的 force-enabled 不受用户 state 影响。范围签名和远端 trust 解决内容授权/一致性，不给 pack 进程隔离，也不是签名者可执行代码的安全沙箱。

- [地址桶、显式信任和 key pin 说明](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncTrustStore.java#L15-L29)、[bucket 与信任写入](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncTrustStore.java#L65-L116)
- [客户端配置开关访问点](../../common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncClient.java#L720-L738)

### 与动态 Registry 交易协议区分

代码中另有动态 registry contribution 同步：NeoForge 26.x 路径包含服务端 owner、参与者准备、客户端 ACK、受控 commit、失败/离开时 abort、晚加入 STATE_SYNC/catch-up 等交易步骤。协调器说明它不提供跨进程原子性。这套交易管理动态贡献状态，不分发 pack 文件，也不替换上面的 hash-list/bundle cache lifecycle。不要仅因名字里有 prepare/commit/ACK 就把它记为脚本包协议。

- [Dynamic Registry wire message 种类](../../src/main/java/com/tkisor/nekojs/dynamic/DynamicRegistrySyncWire.java#L47-L151)、[NeoForge 传输和 ACK/catch-up 路径](../../src/main/java/com/tkisor/nekojs/dynamic/NeoForgeDynamicSyncTransport.java#L20-L197)
- [跨参与者事务协调器](../../common/src/main/java/com/tkisor/nekojs/core/dynamic/txn/DynamicRegistryTransactionCoordinator.java#L16-L250)、[客户端参与者阶段](../../common/src/main/java/com/tkisor/nekojs/core/dynamic/txn/DynamicRegistryClientParticipant.java#L12-L184)

## 5. 插件扩展、命令、配置和诊断

Pack registry/locator 是核心路径；这次对 `ScriptPack` 在 Plugin Point/Contributor 注册路径的源码搜索没有发现注册点。插件系统贡献编译器、Bindings、模块等能力，编译器注册表影响合法脚本扩展；插件 runtime 在通常 reload 中由 root owner 保持。把“让插件提供一个 pack”作为新机制时，需要单独定义 contributor 的来源、刷新/移除和异常所有权语义，现有 Point 模型并未自动做这件事。

用户可通过 `/nekojs packs`、`/nekojs packs enable|disable <id>` 管理本机/世界包，以及 `/nekojs trust <address>` 决定服务器信任；`engine.toml` 的 `packSync` 配置有 `off`、`hashOnly`、`all` 以及 `allowUnsigned`。命令与同步日志提供状态/失败信息，没有发现完整的 pack 管理 GUI。状态写盘错误在 pack state 写入路径有吞错而命令层仍可能报告成功的问题，属于源码可见的结果可观测性缺口；没有据此断言发生过用户故障。ID 命令仅按有效 id 寻找时，跨 GLOBAL/WORLD 同名会有选择歧义，需明确命令契约是否包含 scope。

- [命令入口](../../src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java#L244-L292)、[状态文件保存](../../common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackState.java#L17-L48)
- [packSync 配置项](../../common/src/main/java/com/tkisor/nekojs/core/config/SandboxConfig.java#L23-L58)
- [编译器/文件 policy 入口](../../common/src/main/java/com/tkisor/nekojs/core/ScriptFilePolicy.java#L1-L33)

## 6. 现有测试和证据级别

下表只说明仓库已有测试源码，不表示本次执行或通过。测试文件检查可覆盖的主要契约如下：

| 区域 | 已有测试源码覆盖 | 代表性来源 |
|---|---|---|
| manifest 默认值/坏文件、state override、scope 激活、id 清洗/去重/排序 | 单元测试 | [ScriptPackRegistryTest](../../common/src/test/java/com/tkisor/nekojs/core/pack/ScriptPackRegistryTest.java#L33-L145) |
| pack/平铺发现与来源 ID | 单元测试 | [ScriptLocatorPackTest](../../common/src/test/java/com/tkisor/nekojs/core/pack/ScriptLocatorPackTest.java#L25-L104) |
| 文件级排序约束与环行为 | 单元测试 | [ScriptLoadOrderSorterTest](../../common/src/test/java/com/tkisor/nekojs/script/ScriptLoadOrderSorterTest.java#L95-L258) |
| hash/signature、缓存路径、远端 trust、断线/reload失败/替换回滚 | 单元测试 | [PackSignatureVerifierTest](../../common/src/test/java/com/tkisor/nekojs/core/pack/sync/PackSignatureVerifierTest.java#L30-L123)、[ServerPackCacheTest](../../common/src/test/java/com/tkisor/nekojs/core/pack/sync/ServerPackCacheTest.java#L33-L128)、[PackSyncClientTest](../../common/src/test/java/com/tkisor/nekojs/core/pack/sync/PackSyncClientTest.java#L85-L711) |
| signed/cache 工作区不可改写、用户编辑配置保留 | 单元测试 | [WorkspaceGenerator signed/cache 用例](../../common/src/test/java/com/tkisor/nekojs/core/plugin/WorkspaceGeneratorModifyConfigHookTest.java#L119-L190)、[WorkspaceGeneratorPreserveTest](../../common/src/test/java/com/tkisor/nekojs/script/WorkspaceGeneratorPreserveTest.java#L31-L57) |
| 动态 Registry participant catch-up / 交易阶段 | 单元测试 | [DynamicConfigurationParticipantsTest](../../src/test/java/com/tkisor/nekojs/dynamic/DynamicConfigurationParticipantsTest.java#L1-L160)、[DynamicRegistryClientParticipantTest](../../common/src/test/java/com/tkisor/nekojs/core/dynamic/txn/DynamicRegistryClientParticipantTest.java#L1-L184) |

可能有更多覆盖或平台集成 fixture；上表不是测试总清单。此轮没有运行测试、构建、游戏或服务器。仓库现有 ticket 37 signed-workspace repair 证据目录是历史记录，不能代替当前 HEAD 的复验；本报告以当前源码行为为准，workspace generator 的当前保护已用现有源代码和测试实现交叉确认。

## 7. 可供后续选范围的事实缺口与设计选项

以下是基于源码边界给出的候选方向，不是批准的实施方案，也不是当前属性/字段的名称。

| 现有边界 / 候选方向 | 要先定清的契约 | 迁移与兼容问题 | 有意义的验证 |
|---|---|---|---|
| 所有同 ScriptType 的 pack 共用 Context/global/module cache；可评估 scoped runtime instance | scope 是包、依赖闭包还是其他单元；可见性、跨单元通信、reload 作用域、相同脚本/模块 ID | 保留当前同 Context 行为作为兼容默认或明确迁移；定义原有 global/module singleton 的转移 | 两个包互访/隔离、模块共享策略、generation 切换、异常期间旧实例可用性 |
| manifest 没有实际依赖图；`after` 是脚本入口级排序 | 包依赖字段及版本/范围语法、顺序稳定性、环/缺失依赖策略、依赖禁用/缺失时的激活规则 | 旧 manifest 缺字段等价“无依赖”；未知键目前透传，应为新字段设版本/兼容规则 | 依赖 DAG、循环、范围不匹配、跨 scope、重复有效 ID、内容新增后依赖闭包重载 |
| 权限策略是 Context/环境级 | 是否允许声明能力，谁授权，签名/信任/权限三者关系，默认拒绝还是继承全局配置 | 现有本机/远端包不得静默扩权；旧包兼容策略和用户迁移提示 | 不同能力矩阵下 read/write/host APIs/remote source 的正反例 |
| listener 等部分注册有来源清理；没有已确认的逐包通用资源 owner | 资源注册/释放 API、所有者、reload失败回滚、宿主资源不可逆操作边界 | 与现有 event ownership/generation cleanup 协同，不重复释放 root/plugin 资源 | 每类副作用的生命周期测试、部分失败 cleanup、重复 reload/卸载幂等 |
| 文件 reload 在活动 Context 内失效/重跑；不是包级 candidate reload | 局部候选如何复制/隔离状态，依赖重跑闭包，commit 时资源交换语义 | 保持现有 file reload 行为、失败语义和 STARTUP restart 限制，除非明确宣布迁移 | 修改包入口、共享模块、跨包依赖时的成功/失败/副作用回滚；STARTUP 不可逆边界 |
| `allowUnsigned` 验签允许与有内容 remote source 授权条件并不一致 | `allowUnsigned` 是否只表达完整允许执行，或允许但降权；UI/日志/断开行为 | 不改变既有安全默认；测试明确带内容和空包两条路径 | 有/无 keyId、非空/空内容、mode 与信任组合的端到端测试 |
| ID 会 sanitize，但 raw manifest 保存原始 id；状态/命令按 id 可能碰到跨 scope 重名 | 唯一性是否按全局还是 scope，manifest/wire/文件夹/命令的 canonical id | 防止已发布签名内容因规范化改变而失效；旧目录和状态文件映射规则 | 不同 raw id 收敛到相同有效 id、GLOBAL/WORLD 同名、同步 ID 稳定性 |

与 Cordis reassessment 的对应：G1 动态 capabilities 对应能力提供者、消费者及其激活依赖，目前 manifest 没有表达这些关系；能力可用性与沙箱授权分别设计，不能用 ACL 代替动态依赖。G2 scoped instances 对应“包不是独立运行实例”；G3 resource ownership 对应“部分监听按来源清理但不存在通用包资源 owner 的证据”；G4 local reload 对应“当前活动 Context 的文件 reload 与候选 generation 的差异”。这四点可用于挑选改进边界，但所有 runtime-unit 或 manifest 字段仍需单独设计和验证。

- [Cordis reassessment 需求/方案](./cordis-v4-reassessment.md)

## 未在本轮确认

- 未执行任何测试/构建/实际游戏服务器，无法确认当前生产运行路径、性能或故障率。
- 没有完整检查 JS loader 对 `package.json` 字段和所有模块格式的委派行为；本文只描述 `NekoModuleResolver` 明确实现。
- 没有逐平台复核每个 Fabric/NeoForge 版本入口；同步行为以共同 pack sync 实现和本次读取的平台接线为限。
- 未将资源注册的每种类型逐项追踪到 owner/清理实现；“未发现通用包 owner”是范围内源码搜索结论，不等同于每种资源都泄漏。
