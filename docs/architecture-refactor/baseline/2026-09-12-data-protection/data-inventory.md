# 2026-09-12 数据保护盘点（工单 03，核心交付物 1）

工单：[03: 持久化与用户编辑数据保护基线](../../implementation-tickets/03-data-protection.md)。
spec 依据：[运行时生命周期与数据保护规格](../../specs/05-runtime-lifecycle-and-data.md)。
盘点方法：**实读源码**（revision `d0aa6e0d`，master），不猜测；每行结论附 `file:line` 引用。
路径凡未特别说明均为相对 `<gamedir>`（run 目录，专用服务器 = `versions/<node>/run`）。

本文件是"事实清单"：记录**当前实现**的路径、格式、key、wire id、默认启用规则、可再生性与既有
保护机制（版本标记/原子写/备份）。是否满足工单口径的判定见同目录 [REPORT.md](REPORT.md)。

## 0. 总览表

| # | 数据类 | owner（代码锚点） | 实际路径 | 格式 | 可再生性 | 既有保护机制 | 无版本标记？ |
|---|---|---|---|---|---|---|
| 1 | 引擎 config | NekoJSPaths.java:70-79 + NightConfig 装载（NekoJSMod.java:87-89） | `nekojs/config/engine.toml`（旧位置 `<gamedir>/config/nekojs-engine.toml` 只读回退） | TOML（NightConfig） | 不可再生（用户可编辑）；损坏回退默认值 | 损坏不覆盖（回退内存默认）；沙箱永久拒写 `nekojs/config/`；**无原子写** | 无 |
| 2 | probe config | NekoJSPaths.java:70-79 + probe config 装载（同上） | `nekojs/config/probe.toml` | TOML（NightConfig autosave） | 不可再生（用户可编辑）；损坏回退默认 | 同上（SandboxPolicy 保护） | 无 |
| 3 | world pack | ScriptPackRegistry.java:27,89-94（WORLD scope） | `<world>/nekojs_packs/<pack>/` | 目录 + `manifest.json` | **不可再生**（用户数据） | 损坏 manifest 跳过不删；状态文件损坏回退默认 | 无 |
| 4 | GLOBAL pack | ScriptPackRegistry.java:26 + NekoJSPaths.java:82（GLOBAL scope） | `nekojs/packs/<pack>/` | 同上 | **不可再生**（用户数据） | 同上 | 无 |
| 5 | SERVER_CACHE pack | ScriptPackRegistry.java:108-113 + PackSyncClient.java:206-221 | `nekojs/server_packs/<sha256(地址)>/<syncId>/` | 同上（manifest+内容文件） | **可重建**：源 = 远端服务器 + 信任判定成立 | 落盘后重扫重算哈希自检；删旧重建（同 syncId 内） | 无（哈希非版本） |
| 6 | pack 启用状态 | ScriptPackRegistry.java:136-141,179-181 | 每包 `<pack>/.neko_pack.state.json` | JSON（pretty） | 不可再生（用户决策记录） | 损坏回退 manifest 默认；写失败 WARN 不抛；**无原子写** | 无 |
| 7 | trust-store | PackSyncTrustStore.java:140-153 | `nekojs/config/trusted-servers.json` | JSON（Gson） | 不可再生（信任决策） | 损坏→空存储（WARN）；**temp+ATOMIC_MOVE 原子替换**（唯一有原子写的盘上数据） | 无 |
| 8 | 实体/玩家 pdata | EntityPDataStore.java:19-60 + NekoEntityPDataMixin.java:28 | 实体持久化容器子键 `NekoJSPersistentData`（NeoForge=`Entity#getPersistentData()`；Fabric=mixin 字段，存档键 `NeoForgeData`） | NBT CompoundTag | **不可再生**（脚本/玩家数据） | 随实体存档读写；跨 loader 存档格式兼容（Fabric 写 `NeoForgeData` 兼容键） | 无 schema/version |
| 9 | PData wire | PDataSyncPacket.java:12-20 + PDataSyncService.java:22-30 + NekoJSNetwork.java:43-45 | payload id `nekojs:pdata_sync`，registrar 协议 `"1"` | VAR_INT entityId + VAR_INT revision + COMPOUND_TAG | wire（非落盘） | revision 去重；>32768 字符跳过并 WARN | — |
| 10 | 用户编辑 workspace | FileEditorConfigContributor.java:44-152 + NekoJSPaths.java:77-79 | `nekojs/{startup,server,client,test}_scripts/jsconfig.json`、`<pack>/<type>_scripts/jsconfig.json`、`.neko_probe/jsconfig.json`、`nekojs/README.txt` | JSON / 文本 | 半可再生（生成骨架可重建，**用户编辑不可再生**） | 全部 **only-if-missing** 写入（存在即不覆盖） | 无 |
| 11 | snippets | catalog 布局（FileEditorConfigContributor 同族） | `layout.snippetsPath()`（catalog 输出布局决定） | JSON | 可再生（纯生成物） | **无条件覆盖写** | 无 |
| 12 | 编辑器配置合并面 | FileEditorConfigContributor.java:44-152（merge 面） | `<gamedir>/jsconfig.json`、`pyrightconfig.json`、`.vscode/settings.json` 等 | JSON | 不可再生（用户可编辑） | **merge 语义**（读-改-写，保留未知键/未知路径） | 无 |
| 13 | probe 输出 | ProbeCoordinator（probe 命令）+ commitInPlace | `.neko_probe/{typescript,python,...}/` | .d.ts / .pyi | **可再生**（源 = 运行中的 registry/catalog，有命令 `/nekojs probe`） | commitInPlace：同内容跳过、陈旧删除；`@manual` 等生成目录随每次 probe 重写 | 无 |
| 14 | module cache | NekoModulePipelineCache.java:28-80 | 进程内存（`NekoModulePipelineCache.PREPARED_CACHE`） | — | 可再生（源 = 脚本文件本身） | 纯内存，按路径+mtime/size stamp 失效；**无盘上 module cache** | — |
| 15 | 历史脚本日志 | NekoJSLoggers.java:95-108 | `logs/nekojs/<name>.log` + `logs/nekojs/old/<name>.log` | log4j2 文本 | 不可再生（历史诊断） | 创建 logger 时把旧文件 move 到 `old/`（REPLACE_EXISTING，**单代备份**）；JVM shutdown hook 冲刷 | 无 |
| 16 | 脚本 generateData 落盘 | generateData 挂载（NekoJSMod.java:138 一族） | `nekojs/data/`（合成 datapack 挂载）+ 包内 `data/` | JSON（datapack 结构） | 不可再生（脚本产出，脚本可重跑） | 内容签名未变化时挂载零开销 | 无 |
| 17 | NekoJS 内建资源包 | NekoJSPackLoader.java（PackSelectionConfig） | `<gamedir>/nekojs/` 整根（`NekoJSPackLoader`） | MC resource/data pack | 派生（读脚本根） | `PackSelectionConfig(true, TOP, false)` 固定启用 | — |

---

## 1. config（引擎配置 engine.toml / probe 配置 probe.toml）

### 1.1 engine.toml

- **路径**：规范位置 `<gamedir>/nekojs/config/engine.toml`（`common/src/main/java/com/tkisor/nekojs/core/fs/NekoJSPaths.java:77`）；
  旧位置 `<gamedir>/config/nekojs-engine.toml` 仅作**只读迁移回退**（`NekoJSPaths.java:78,99-100`）。
- **读写类**：`SandboxConfigLoader.load(Path, boolean)`（`common/.../core/config/SandboxConfigLoader.java:19-95`），
  NightConfig `CommentedFileConfig`，writable 时 `sync().autosave()`（`:22-24`）；入口 `ClassFilter.loadEngineConfig()`
  （`common/.../core/fs/ClassFilter.java:137-166`，启动期由 `WorkspaceGenerator.setupWorkspace()` 触发，`NekoJSMod.java:138`）。
- **默认值来源**：`setupConfigEntry` 对缺失键补默认值 + 注释（`SandboxConfigLoader.java:29-73`；如 `allowThreads=false`、
  `allowReflection=false`、`enableEsmAuthoring=true`、`scriptEvaluationTimeoutSeconds=30`、`packSync.mode=off`、
  `dynamicRegistry.enabled=false` 等）。**默认键补写是写盘行为**：首次加载会把缺失键与注释写回文件。
- **废弃键清理**：`removeConfigEntry` 加载时删除 `prependRequirePatch`/`useNekoScriptLoader`/`useNativeEsmLoader`（`:41-43,125-130`）。
- **损坏语义**：解析抛错 → WARN + `SandboxConfig.defaultConfig()`（内存），**不覆盖原文件**（`:91-94`）。
- **旧位置回退**：规范位置缺失而旧位置存在 → 只读加载旧文件 + WARN 提示手动迁移，**不自动搬移**（`ClassFilter.java:141-154`）。
- **脚本写保护**：`nekojs/config/` 整目录与旧位置对脚本**永久拒写拒删**（`common/.../core/fs/SandboxPolicy.java:34-40,52-70`）。
- **版本标记**：无 schema/version 键；**原子替换**：无（autosave 直接写）。

### 1.2 probe.toml

- **路径**：`<gamedir>/nekojs/config/probe.toml`（`NekoJSPaths.java:79`）。
- **读写类**：`ProbeConfigLoader`（`common/.../probe/ProbeConfigLoader.java:16,24`，CommentedFileConfig + autosave；
  缓存为 mtime+size 双因子，`ProbeConfigService.java:11`）。启动期缺失自动落盘默认（`ProbeCoordinator.ensureConfigFile()`，
  `ProbeCoordinator.java:112-114`）。
- **损坏语义**：异常回退 `ProbeConfig.defaultConfig()`（`ProbeConfigLoader.java:16` javadoc）。
- **版本标记/原子写**：无（同 engine.toml）。

## 2. world（存档侧）

- **WORLD pack 路径**：`<world>/nekojs_packs/<pack>/`（`ScriptPackRegistry.WORLD_PACKS_DIR="nekojs_packs"`，
  `common/.../core/pack/ScriptPackRegistry.java:27`；激活入口 `activateWorldPacks(worldDir)` `:89-94`，扫 `worldDir.resolve("nekojs_packs")`）。
- **激活时机（NeoForge 26.x）**：`ServerAboutToStartEvent` → `ServerEventListener.activateWorldPacks(server)`，
  world dir = `server.getWorldPath(LevelResource.ROOT)`（`src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java:57-73,94-108`）；
  激活出非空集合时补一次完整 SERVER reload（`:101-107`）。`ServerStoppedEvent` → `deactivateWorldPacks()` + 按包前缀清 listener（`:115-128`）。
  1.21.1 节点有同名实现（`versions/1.21.1/src/main/java/.../listener/ServerEventListener.java:58,95-97`）。
- **世界包挂载 datapack**：启用包内 `data/` 目录用 `ScriptPackDataManager` 合成 server datapack 挂载，内容签名未变化零开销
  （`ServerEventListener.java:75-91`）。
- **level data 侧**：mod 不写自有 level 文件；存档接触点只有 (a) `<world>/nekojs_packs/` 读扫描，(b) 实体存档内的
  `NekoJSPersistentData` 子键（§4），(c) Fabric 存档键 `NeoForgeData`（§4）。
- **Fabric WORLD 现状差异**：单列见 §9。

## 3. 脚本与 pack（GLOBAL / WORLD / SERVER_CACHE）

### 3.1 目录与扫描

| scope | 目录 | 激活/卸载 | 启用判定 |
|---|---|---|---|
| GLOBAL | `<gamedir>/nekojs/packs/<id>/`（`GLOBAL_PACKS_DIR="packs"`，`NekoJSPaths.java:82` + `ScriptPackRegistry.java:26`） | 进程内首次访问懒扫描一次；reload 前显式 `refreshGlobalPacks()`（`ScriptPackRegistry.java:31-34,64-77`） | `.neko_pack.state.json` > manifest `enabled`（默认 true） |
| WORLD | `<world>/nekojs_packs/<id>/`（`ScriptPackRegistry.java:27,89-94`） | 服务器 aboutToStart 激活 / stopped 卸载（§2） | 同上 |
| SERVER_CACHE | `<gamedir>/nekojs/server_packs/<bucket>/<syncId>/`（`NekoJSPaths.java:83` + `ScriptPackRegistry.java:108-113`） | 客户端验签+信任通过后激活；断线/空清单卸载（`PackSyncClient.java:87-108,206-221`） | **强制启用**（`scanForceEnabled`，状态文件与 manifest 默认均不适用，`ScriptPackRegistry.java:136-141,179-181`） |

- **扫描规则**：只认含 `manifest.json` 的子目录；损坏 manifest WARN 并跳过；同 scope 重复 id 后者跳过；按 id 字母序
  （`ScriptPackRegistry.java:141-177`）。加载顺序 GLOBAL → WORLD → SERVER_CACHE（`:42-48`）。
- **包内布局**：`<pack>/{startup,server,client,test}_scripts/`（`ScriptPack.java:22-24`）+ `assets/` + `data/`
  （哈希范围 `PackHasher.CONTENT_DIRS`，`common/.../core/pack/sync/PackHasher.java:30-33`）。
- **ScriptId 前缀**：GLOBAL=`packs/<id>/`，WORLD=`worldpacks/<id>/`；完整 `nekojs:<type>/<前缀段><相对路径>`
  （`ScriptPack.java:32-39`），是世界卸载时按前缀反注册监听器的定位键。

### 3.2 manifest key（`manifest.json`）

- 解析模型：`ScriptPackManifest`（`common/.../core/pack/ScriptPackManifest.java:17-73`）。键：
  `id`（缺省=目录名 sanitize）、`name`（缺省=id）、`version`（缺省 "unknown"）、`description`、`authors`、
  `enabled`（缺省 **true**）、`clientSync`（缺省 **true**）、`signature`（透传给包分发）、`config`（透传）；
  未知键保留在 `raw()`。文件缺失 → 不是包（返回 null）；损坏 → WARN + 跳过（`:42-58`）。
- 目录名/id 规则：`sanitizeId` 小写 `[a-z0-9_-]`，其余替换 `_`（`:33-36`）。

### 3.3 启用状态文件（`.neko_pack.state.json`）

- 路径：每包目录内（`ScriptPackState.FILE_NAME`，`common/.../core/pack/ScriptPackState.java:17`）；优先级高于 manifest `enabled`。
- 读取：损坏/类型不符 → null → 回退 manifest 默认（`:20-39`）。写入：pretty JSON `{"enabled": bool}`，**写失败仅 WARN**
  （`:41-50`）。**无原子写、无版本标记**。

### 3.4 PackSync 分发格式（SERVER_CACHE 来源）

- **引擎开关**：`engine.toml packSync.mode`（off 默认 / hashOnly / all，`SandboxConfigLoader.java:66-70`；`PackSyncServer.java:28`）。
- **payload**：`nekojs:pack_hashes`（`PackHashListPayload.java:49-50`）与 `nekojs:pack_bundle`（`PackBundlePayload.java:78-79`），
  走 configuration 阶段（`NekoJSNetwork.java:43-45`）。
- **传输单元**：`SyncedPack(syncId, scopeName, hash, manifestJson, files)`；syncId = `<scope段>:<包id>`，目录编码
  `:` → `_`（`SyncedPack.java:24-26`）。
- **哈希**：SHA-256(manifest 原始字节 + 0x00 + 按 排序相对路径 的 `路径 + 0x00 + 内容 + 0x00` 连接)（`PackHasher.java:14-21`）；
  状态文件不参与哈希。
- **落盘纪律**：删旧目录再重建 + 相对路径穿越校验（绝对/`..` 拒绝），写后从盘重扫重算哈希对照（完整性自检）
  （`ServerPackCache.java:16-21,38-57,60-80`；`PackSyncClient.java:158-198`）。
- **信任判定**：bucket = sha256(地址 trim+小写)；未信任 → 断连 + 提示 `/nekojs trust`；激活后 pinning 签名公钥
  （`PackSyncClient.java:185-215`）。断线/空清单/hashOnly 客户端 → 卸载激活集，**缓存文件保留**（`:206-221`）。
  `mode=hashOnly` 语义文档化于 `PackSyncClient.java:27-28`（清空激活集、永不执行）。
- **体量上限**：64 包/bundle、4096 文件/包、8 MiB/文件、256 KiB/manifest、64 MiB/bundle（`PackSyncClient.java:33-37`）。

## 4. 实体/玩家 pdata

- **存储**：实体持久化容器中 NekoJS 子键 **`NekoJSPersistentData`**（`src/main/java/com/tkisor/nekojs/api/inject/EntityExtension.java:17`）。
  - NeoForge：`Entity#getPersistentData()`（平台 API），安装点 `NekoJSMod.java:87-89,128`。
  - Fabric：`NekoEntityPDataMixin` 加 `neko$pdataTag` 字段随实体存档读写；**存档键 `NeoForgeData` 下挂
    `NekoJSPersistentData`，与 NeoForge 侧存档格式兼容**（`versions/26.1.2-fabric/src/main/java/.../mixin/NekoEntityPDataMixin.java:28,50-68`；
    仅在非空时写出 `:54-58`）。空 tag 写回 = 移除子键（`EntityPDataStore.java:19-24`）。
  - 未装配兜底：按实体 id 的内存 Map（测试/专用服务器早期），不随存档持久化（`EntityPDataStore.java:26-60`）。
- **key 空间**：脚本任意字符串 key（PersistentDataJS putXxx/getXxx：byte/short/int/long/float/double/string/boolean/
  byteArray/intArray/longArray/compound），`edit()` 批量事务，`replaceTag`/`merge`/`clear`（`src/main/java/.../wrapper/pdata/PersistentDataJS.java:49-126`）。
  客户端 mirror 为 readOnly（写抛 `UnsupportedOperationException`，`:150-156`）。
- **wire**：`nekojs:pdata_sync`，`PDataSyncPacket(entityId VAR_INT, revision VAR_INT, data COMPOUND_TAG)`
  （`src/main/java/.../network/PDataSyncPacket.java:12-20`），注册于 NeoForge `registrar("1")`
  （`NekoJSNetwork.java:23,32`；fabric 侧 `PayloadTypeRegistry` 注册同名 payload，`versions/26.1.2-fabric/.../FabricPlayNetwork.java:44-52`）。
- **同步语义**：脏标记 + 每 tick 最多 256 个 flush；单包 tag 字符串 >32768 字符跳过并 WARN；entity 移除时 revision+1 发空包
  清客户端 mirror；客户端按 revision 去重（`PDataSyncService.java:22-30,47-72,90-100,108-117`）。
- **玩家 pdata**：玩家即 `Entity`，同一 `NekoJSPersistentData` 子键路径（PlayerEvents 侧脚本同一 API）。
- **版本标记**：无 schema/version；备份：无（随存档，依赖 MC 存档机制）。

## 5. trust-store

- **路径**：`<gamedir>/nekojs/config/trusted-servers.json`（`PackSyncTrustStore.java:36,46-58`；在 SandboxPolicy 保护的
  `nekojs/config/` 内，脚本不可写）。
- **格式**：`{"trustedServers": {"<sha256(地址)>": {serverAddress, trustedAt}}, "trustedKeys": {"<keyId>": {publicKey, fingerprint, serverAddress, trustedAt}}}`
  （`PackSyncTrustStore.java:18-29,75-117`）。v1 语义：信任服务器即信任其当前签名密钥（key pinning，`PackSignatureVerifier` 拒绝换钥）。
- **损坏降级**：读失败（损坏 JSON）→ WARN + **按空存储处理**（`readRoot` `:129-138`）——既有测试
  `PackSyncTrustStoreTest.corruptFileDegradesToEmptyStore` 覆盖；跨 reload 保留由 `serverTrustPersistsAcrossReload` 覆盖。
- **原子写**：**temp + `ATOMIC_MOVE`（不支持时降级 REPLACE_EXISTING move）**——本盘点中唯一使用原子替换的盘上数据
  （`writeRoot` `:140-153`）。写失败 WARN 不抛。
- **版本标记**：无。

## 6. 用户编辑 workspace / declaration

- **脚本目录 jsconfig.json**：`<gamedir>/nekojs/{startup,server,client,test}_scripts/jsconfig.json` 与
  `<pack>/<type>_scripts/jsconfig.json`（`WorkspaceGenerator.java:61-108`）。写规则 **only-if-missing**（`:100-107`）——
  用户编辑不会被生成物覆盖；插件可经 `modifyWorkspaceConfig` 改模型（`:91-98`）。
- **`.neko_probe/jsconfig.json`**：only-if-missing（`WorkspaceGenerator.java:183-190`）。
- **`nekojs/README.txt`**：only-if-missing（`:41-58`）。
- **snippets 文件**：`createSnippets()` **无条件覆盖写**（`:206-221`）——纯生成物，路径由 catalog `outputLayout().snippetsPath()` 决定。
- **编辑器配置合并面**：probe 运行后向 `<gamedir>/jsconfig.json`（paths/include/typeRoots/typeAcquisition）、
  `pyrightconfig.json`（extraPaths）、`.vscode/settings.json`（python.extraPaths）做 **读-改-写合并**，保留用户键
  （`common/.../probe/FileEditorConfigContributor.java:44-152`）。reset 入口 `/nekojs probe reset_config` 逐 backend
  删除其整体拥有的文件后由 `createWorkspaceConfigs()` 重建（`ProbeCoordinator.java:139-152`）。
- **probe 生成物（对照）**：`.neko_probe/{typescript,python}/...` 每次全量重算 + `commitInPlace`（同内容跳过、陈旧删除、
  空目录修剪）（`common/.../probe/ProbeOutputCommitter.java:85-155`）；`@manual/*` 为每次 probe 重新渲染的生成物
  （`TypeScriptProbeBackend.java:171-177,286-298`）。README.txt 明示 .d.ts 勿手改（`WorkspaceGenerator.java:51`）。
- **版本标记**：无；判定"是否可能被覆盖"：jsconfig/README=不会（only-if-missing）；编辑器合并面=不会（merge 保留未知键）；
  `.neko_probe` 内生成物=会（设计如此，属可再生层）；snippets=会（无条件覆盖）。

## 7. 历史日志

- **路径**：`<gamedir>/logs/nekojs/<name>.log`（脚本 logger，`common/.../core/log/NekoJSLoggers.java:95-96`）。
- **轮转/保留**：每次 JVM 进程内首次创建某 logger 时，若同名日志已存在 → move 到 `logs/nekojs/old/<name>.log`
  （`REPLACE_EXISTING`，**仅一代备份**，同进程重启第二次运行会覆盖上一代）（`NekoJSLoggers.java:99-108`）。
  无按大小/时间的 RollingFile；主 `logs/latest.log` 由 vanilla 管理，mod 不触碰。
- **冲刷**：1s 定时批量 + JVM shutdown hook（`:51-65`）；脚本日志折叠防刷屏（`CollapsingAppender` `:155-219`）。
- **版本标记/原子写**：无（append-only 文本）。

## 8. 可再生 cache

- **probe 输出**：`.neko_probe/`（`NekoJSPaths.java:70`）。重建入口 = `/nekojs probe`（`NekoJSCommands.java:493-514`）；
  源 = 运行中实例的 catalog/registry，**可重建有证据**（02 号票 probe 维度实测完整生成 330–780 ms）。
  增量快路径：不清输出时 `0 written / N unchanged`（`ProbeOutputCommitter.hasSameContent` `:169-176`）。
- **module cache**：**纯进程内存**（`NekoModulePipelineCache.PREPARED_CACHE`，key=路径，stamp=mtime+size 源快照；
  `common/.../core/module/NekoModulePipelineCache.java:28-80`）。无盘上形态，reload/重启自然重建；`clear()` 清内存（`:76-78`）。
  —— 盘上不存在"误删唯一数据"的风险面。
- **node_modules**：`nekojs/node_modules/`（`NekoJSPaths.java:71`，启动 ensureDir `:118`）——**用户/包作者安装的运行时
  依赖，不可再生**（来源是用户手动放置），不属于 cache。
- **远端 pack cache**：`nekojs/server_packs/`（§3.4）。重建前提：源服务器可再次连接 + 信任判定成立（bucket 受信 + keyId pinning
  一致）；不满足时不重建，已激活集合随断线卸载但文件保留（`PackSyncClient.java:206-221`）。
- **pack data 挂载**：`nekojs/data/`（`NekoJSPaths.java:81`）脚本 generateData 落盘 datapack；签名未变化零开销重挂
  （`ServerEventListener.java:75-91`）——派生可重跑，但内容是脚本产出，盘点归"用户数据"不默认清理。

## 9. Fabric WORLD 当前行为（现状差异，spec 要求记为现状而非 parity）

- **实测代码事实**：`26.1.2-fabric`/`26.2.0-fabric` 节点**从不调用** `ScriptPackRegistry.activateWorldPacks`——
  全 fabric 节点源码中 `nekojs_packs` 仅出现在 `/nekojs packs` 列表命令的提示文案里
  （`versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java:309`），
  生命周期只注册 `SERVER_STARTING/STOPPED` 维护 currentServer 引用（`FabricPlayNetwork.java:56-57`、
  `FabricPDataSync.java:43-44`）。
- **结论（现状，非 parity 目标）**：Fabric 专用服务器/客户端上，`<world>/nekojs_packs/` 内的世界包**不会被激活执行**
  （目录被用户放进存档也只是静态数据）；列表命令文案声称"looked in `<world>/nekojs_packs/`"与实际激活行为存在
  表述落差，属现状差异记录项，本票不改代码。对照：NeoForge 26.x/1.21.1 在 aboutToStart 激活、stopped 卸载（§2）。
- 其余数据面（GLOBAL pack、config、trust-store、pdata 存档格式）在 Fabric 节点与 NeoForge 同路径同格式；
  pdata 存档兼容性由 `NekoEntityPDataMixin` 显式保证（§4）。

## 10. 已有测试 prior art（只读引用）

- `common/src/test/java/.../core/pack/sync/PackSyncTrustStoreTest.java`：trust-store 跨 reload 保留、损坏文件降级空存储。
- `src/test/java/.../wrapper/pdata/PersistentDataJSTest.java`：pdata 读写、脏标记语义。
- `src/test/java/.../network/PDataSyncAcceptTest.java`（PData 同步接受语义）。
- `common/src/test/java/.../core/pack/sync/PackSyncClientTest.java`、`PackSignatureVerifierTest.java`：分发/验签。

（以上为既有测试，未在本票重跑改动；场景级运行时证据见 [REPORT.md](REPORT.md)。）
