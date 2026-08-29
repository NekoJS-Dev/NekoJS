# 09: Fabric 实体扩展机制 + pdata

**What to build:** fabric 上的实体扩展面：`EntityExtension` / `LivingEntityExtension` 去 neoforge 守卫
所需的两块地基 —— ①实体持久化数据存储（NeoForge 的 `Entity#getPersistentData()` 等价物）；
②扩展接口注入。地基就位后 `player.pdata()` / `entity.neko$*` 在 fabric 可用，
`PDataSyncService`（发送面已在票 05 中立化）随之接通。

**Blocked by:** 05（play 阶段通道底座已就位）。

**Status:** done（按"不再依赖实机"标准：持久化/同步语义由单测覆盖，编译 + 测试 + lint 绿）

- [x] fabric 实体持久化数据存储（`NekoEntityPDataMixin` 加字段 + `addAdditionalSaveData` /
      `readAdditionalSaveData` 钩子随实体存档读写）
- [x] `EntityExtension` 去整文件守卫（版本守卫保留），存取改走中立 `EntityPDataStore` 桥，
      fabric 经 `MixinEntity` / `MixinLivingEntity` 注入扩展接口
- [x] `PDataSyncPacket` fabric 注册 + 客户端 receiver + `flush`（END_SERVER_TICK）/
      `onEntityRemoved`（AFTER_ENTITY_CHANGE_LEVEL）钩子
- [x] 同步去重/清除语义单测（`PDataSyncAcceptTest`：revision 单调、空包清除、断线全清、
      幂等重发）+ 存取契约单测（`EntityPDataStoreTest`：getter 拷贝防御、空写移除、install 覆盖）
- [x] 四节点编译 + 测试 + guardLint 绿

## 落地记录

- **持久化存储**：vanilla `Entity` 无 `getPersistentData()`（NeoForge patch 加的，javap 验证）。
  fabric 用 `NekoEntityPDataMixin`：`@Unique` 字段 + `addAdditionalSaveData/readAdditionalSaveData`
  TAIL 注入。26.x 的存档走 `ValueOutput/ValueInput`（非 NBT 直接树），NBT 用
  `output.store(key, CompoundTag.CODEC, tag)` / `input.read(key, CompoundTag.CODEC)` 存取。
  **存档格式与 NeoForge 兼容**：外层 `Neo.JSPersistentData` 容器键 + 内层 `NekoJSPersistentData`
  子键——同一存档跨加载器迁移 pdata 不丢。
- **duck 接口 `NekoEntityPData`**（共享树）：fabric mixin 实现到 Entity 上，桥实现经它
  `((NekoEntityPData) entity).neko$getPDataRoot()` 取容器。
- **`EntityPDataStore`（中立桥，票 09 的关键抽象）**：`neko$pdata()` 的读写不再直接摸
  `getPersistentData()`，改走 `install(Access)` 装配的桥；键为**实体 id**（同步/镜像层本来就按
  id 记账，测试无需构造真实实体）。NeoForge 在 `NekoJSMod` 构造器装 `getPersistentData()` 版本
  （`getCompound` 的 Optional/非 Optional 版本差异用 `//? if >=26` 守卫），fabric 在
  `FabricPDataSync` 装 duck 接口版本（id→实体经全维度查表，仅服务器线程）。未装配时 fallback
  内存态存取（测试环境兜底，不随存档持久化）。
- **接口注入**：`MixinEntity`/`MixinLivingEntity`（`@Mixin(Entity.class)` + `implements`）——
  等价 NeoForge 的 `nekojs.interface_injection.json`；default 方法全在接口上，mixin 只是挂名。
- **版本差异速记**：26.x `CompoundTag.getCompound/getInt` 返回 Optional（`getCompoundOrEmpty`
  便利形态在）；1.21.1 返回裸值。共享树涉及 NBT 读取的地方都需要 `//? if >=26` 守卫。

## 测试布局的教训

- `PersistentDataJSTest` 从 src/test 去守卫时先移到了 common——**错**：`:common` 是
  MC-agnostic 的（`checkCommonIsolation` 禁 `net.minecraft` import），测试用了 NBT 类即红。
  已移回 src/test（共享测试树，四节点各编译一份）。
- 测试构造 `EntityType.PIG` 会触发 NeoForge `AttachmentHolder` 的静态初始化
  （`FMLLoader.getCurrent` → "There is no current FML Loader"）——裸 JUnit 不能碰注册表对象，
  Entity 桩也不可行（Entity 构造器必需 EntityType）。**这就是 `EntityPDataStore` 键形选
  `int entityId` 的原因**：同步语义测试完全不需要实体实例。
- 共享测试树对 26 系专属 API 的测试要加 `//? if >=26`（1.21.1 的 CompoundTag API 不同会编译红）；
  fabric 节点的 testImplementation（junit）此前缺失（共享树从未给 fabric 配过测试），
  在 fabric.gradle.kts 补上。
