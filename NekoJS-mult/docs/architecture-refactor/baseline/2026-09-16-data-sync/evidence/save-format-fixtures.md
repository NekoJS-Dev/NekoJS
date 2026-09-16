# 票 18：存档兼容 fixture 清单

全部 fixture 在 `versions/26.1.2-fabric/src/test/java/com/tkisor/nekojs/fabric/mixin/NekoEntityPDataSaveShapeTest.java`
（26.2.0-fabric 同体一份），驱动**生产 mixin 的真实方法体**（反射调 `neko$loadPData`/`neko$savePData`
@Inject 处理器，经 vanilla `TagValueInput`/`TagValueOutput`，非逻辑复制品）。

| # | fixture | 形状 | 断言 |
|---|---|---|---|
| 1 | 旧 NeoForge 存档 literal NBT | `{id:"minecraft:armor_stand", Health:20.0d, NeoForgeData:{NekoJSPersistentData:{mana:303,name:"neko",seen:1700000000000L}, SomeOtherModsKey:7}}` | fabric mixin load 回读出全部 3 个 pdata 键、恰好 3 项（其它 mod 的键不泄漏进 pdata）；`oldNeoForgeSaveReadsBackOnFabric` |
| 2 | vanilla-only 存档 | `{id:"minecraft:cow", Health:10.0d}`（无 NeoForgeData） | 回读为空 pdata；`vanillaOnlySaveReadsBackEmpty` |
| 3 | fabric 写出形状 | root `{mana:303}` → save | `buildResult()` = `{NeoForgeData:{NekoJSPersistentData:{mana:303}}}`——容器键 `NeoForgeData` 与 NeoForge patched `Entity#addAdditionalSaveData` 的 `storeNullable("NeoForgeData",...))` 同形，payload 在 `NekoJSPersistentData` 子键、且 fabric 侧只写自有子键（container 恰 1 键）；`saveWritesTheCrossLoaderCompatibleShape` |
| 4 | 空 pdata 写出 | root = 空 tag | 不写 `NeoForgeData` 键；`emptyPDataWritesNoContainerKey` |
| 5 | 全新实体（root 字段 null） | 未 load 过 | 同上不写键；`nullRootWritesNoContainerKey` |
| 6 | 写读往返 | — | fabric 写出 → 回读逐项相等（幂等）；`saveLoadRoundTripIsStable` |
| 7 | 防御拷贝 | — | 写出后改源头 tag 不影响已存内容；`savedShapeIsIndependentOfTheSourceTag` |

key 常量钉住：`EntityExtension.NEKO_PDATA_KEY = "NekoJSPersistentData"`（公开常量，被上述断言消费）；
fabric mixin 容器键 `NEKO$CONTAINER_KEY = "NeoForgeData"` 经行为断言（fixture 3/4/5 的写出形状）钉住。

NeoForge 侧回读：容器即平台 API `Entity#getPersistentData()`（NeoForge patched Entity 序列化时挂
`NeoForgeData` 键下的同一 Compound），NekoJS 读路径语义（`getCompound(NEKO_PDATA_KEY).copy()` 读出、
空 tag 移除子键、写拷贝防御）由共享树 `EntityPDataStoreTest`（既有）+ `PDataJoinWindowResolutionTraceTest.bothLoaderFacesKeepCopySemanticsAndEmptyTagRemoval`（新增 trace）钉住。

wire fixture（AC6）不重复造：引用票 17 `PayloadWireFormatGoldenTest`——`PDATA_SYNC_HEX = "d209090a0300046d616e61000000070800046e616d6500046e656b6f00"`（varint entityId=1234 + varint revision=9 + NBT）、`CLIENT_DATA_SYNC_HEX = "086875642f6d616e61077b2276223a377d"`（UTF8 key + UTF8 json），五节点同 hex + 等价解码对照。本票零 wire 改动。

真实存档目录/真实 Entity 的平台回读（`data get entity ... NeoForgeData` 通道）：03 号票真机已证
（`2026-09-12-data-protection/evidence/data-get-pdata-console-captures.txt`：pdata 跨停服重启持久、
NBT 读回同值）；本票 characterization 引用，未重跑真机。
