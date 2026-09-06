# Fabric 移植状态与缺口台账（26.1.2-fabric）

> 状态：living document——本文档是 fabric 移植的缺口台账与批次记录。
> 勘察事实源：2026-09-01/02 两轮只读勘察（笔记存 `D:\mcmodDemo\NekoJS\.agent-notes\fabric-port-audit\`，
> 含事件矩阵 90+ 总线逐条对照、AW↔AT 对照表、TODO(fabric) 63 处全归类）。
> 关联 spec：[fabric-node-expansion.md](fabric-node-expansion.md)（版本图扩展，其第 0 步上移
> 会消化本文"节点孪生"小节的债）。

## 本轮已补全（2026-09-02）

1. **惰性 mixin 全量激活**（7 个编译进 jar 却无配置引用的共享 mixin）：
   `nekojs-fabric-shared.mixins.json`（MinecraftServerMixin / RecipeManagerMixin /
   ReloadableServerResourcesMixin / ResultSlotMixin / inject.MixinLevel / MixinPlayer /
   MixinMutableComponent）+ `nekojs-fabric-dynamic.mixins.json`（client:
   RegistryDataCollectorMixin），fabric.mod.json 模板挂载。
   Level/Player/Server/MutableComponent 四个扩展接口（`neko$*` 脚本面）由此生效。
2. **`server` 段挪公共 `mixins` 段**（存量 bug）：sponge-mixin 的 `server` 数组按物理端
   二选一，单人（物理客户端）下 ServerLevelMixin/ServerGamePacketListenerMixin/
   ServerCommonPacketListenerAccessor 原本不应用 → joinLevel/rightClicked 单人失效、
   FabricPackSync 对未 mixin listener 强转有 CCE 风险。
3. **配方脚本链路端到端打通**（本轮主体）：
   - 共享树连坐守卫去除（零 loader 依赖，纯"配方面未移植"连坐）：
     RecipeEntryJS / RecipeFilter / NekoRecipeNamespaces / RecipeRegistryProxy /
     RecipeJsonValueAdapter / IngredientAdapter / RecipeFilterAdapter /
     IngredientFactory / MinecraftRecipeHandler / GoalEvents / GoalRegisterEventJS /
     NekoErrorUIHelper（getErrorComponent 参数化，不再绑 NekoJSMod）
   - 流体面收进行内 `//? if neoforge` 守卫（fabric 无流体配方体系，与 FluidBuilder
     同判豁免）：RecipeEventJS 序列化×3、RecipeJsonBuilder 工厂×3、
     RecipeJsonValueConverter 的 fluid/sized case、RecipeEventSchemaHost 的
     FLUID_* case（fabric 求值补 default 抛 UnsupportedOperationException）
   - RecipeManagerMixin 共享树版从整文件 `neoforge && >=26` 改 `>=26` + 行内 loader
     分支（条件配方过滤 / server 与 runtime root 获取）；neoforge 求值产物逐行不变
   - IngredientResolver fabric 等价：wildcard/not/compound/intersection 用 vanilla
     展开集合运算（ingredientOfHolders 本就拍平成 Item[]，无功能损失）；
     fromStack 降级为纯物品形态（丢数据组件，随 ItemStackExtension 批次补）
   - IngredientJS 的 and/intersect/except/subtract/asStack/withCount 收进 neoforge
     守卫（数组语法 `["a","b"]` 在 fabric 仍覆盖 or 语义）
   - isCustom()（NeoForge patch）分支化：fabric 全部走 values 检查
   - fabric AW 补配方面 6 条（ShapedRecipe/ShapelessRecipe/SingleItemRecipe 的 result、
     ShapelessRecipe.ingredients、Ingredient.values）；AW 是按需补，非 AT 全量镜像
   - fabric 节点孪生 ServerEvents（同名组 "ServerEvents" 经 EventGroupRegistry 合并，
     补 recipes/afterRecipes 两条总线）
   - FabricCorePlugin 注册：Ingredient/RecipeSchema binding、minecraft 配方命名空间、
     ItemStack/Ingredient/RecipeFilter/RecipeJsonValue 四 adapter
   - FabricNekoJSCommands 的 reload server 补配方热重载（applyRecipeScripts）
4. **fireAfterInit 补调**（onInitialize 尾部，FabricRegistryAdapter 抽干后——与 NeoForge
   侧 FMLLoadComplete 的 onLoadComplete 后同位次）。
5. **GoalEvents 接活**：postRegister 在 STARTUP 脚本加载后调用（镜像 NekoJSMod），
   消费端（FabricEntityEventBindings.postJoinLevel → GoalRegistry）与 AW 早已就绪，
   原状态是"消费钩子在跑、注册面死"的残钩。
6. fabric.mod.json 补 `fabric-api: *` depends（callback 大量使用，缺声明在无 fabric-api
   环境会硬崩）；4 处过时注释修正（NekoJSFabricMod javadoc+死 import、FabricPlayNetwork、
   fabric-node.gradle.kts ×2）；guardLint 规则 7 升级为守卫深度感知（行内守卫内的 loader
   import 在对侧求值时整段消失，与整文件守卫同理豁免——配方面的流体分支因此合法）。

### 验证

- 四节点 compileJava 全绿（26.1.2 / 26.2.0 / 1.21.1 / 26.1.2-fabric）+ guardLint
  零问题（守卫块 251）。
- runClient 冒烟：mixin 全量应用零错误（defaultRequire=1 下注入失败即崩）。
- runServer 冒烟：配方脚本执行 + /nekojs reload server 热重载（见提交说明）。

## 节点孪生（待上移/合并）

- `fabric/event/FabricServerEventBindings`、`FabricNekoJSCommands`：fabric 桥，节点目录
  独有（expansion spec 第 0 步上移共享树后全节点共用）。
- `bindings/event/ServerEvents`（fabric 孪生）：共享树版是 NeoForge 原生事件签名整文件
  守卫，孪生只含 recipes/afterRecipes；后续把共享树版事件载荷中立化后合并回单副本。

7. **网络自定义通道整链打通**（2026-09-02 第二批）：NekoScriptPayload 双向注册
   （PayloadTypeRegistry serverbound/clientbound + ServerPlayNetworking/
   ClientPlayNetworking receiver，切主线程后走中立投递）；共享树 NetworkJS 的
   S2C 两法改走 PlayPacketDispatchers（中立发送面）、C2S 行内 loader 分支；
   NetworkMessageHandler 拆出中立 post 核心（NeoForge 的 IPayloadContext 包装收进
   行内守卫）；FabricCorePlugin 注册 NetworkEvents 组与 "Network" 绑定——脚本
   `Network.sendToServer/sendToPlayer/sendToAll` 与 `NetworkEvents.server/client`
   在 fabric 可用；FabricPlatform 点亮 NETWORK_CUSTOM_CHANNEL 位。
8. **FabricCatalogPlatformProvider**（fabric 的 probe/workspace 目录数据源）：
   host 扩展 7/9（ItemStack/BlockState 两接口待其扩展批次）、registry 类型×10、
   配方命名空间（复用去守卫后的 NekoRecipeNamespaces）、snippets、modIds；
   NekoJSFabricMod static 块接线 setPlatformProvider——fabric 的 typings 从此
   含 host 扩展方法（runServer 实测 `nekojs probe` 生成 301 文件，
   addXpLevels/spawnLightning 出现在产物 d.ts 中）。

## P0 剩余（破坏活面）

（无——原 P0 两项已分别在上面第 7、8 条完成。）

9. **P1 事件面与基础绑定**（2026-09-02 第三批）：
   - LevelEvents 子集（loaded/unloaded ← ServerLevelEvents；tickPre/tickPost ←
     START/END_LEVEL_TICK）；PlayerEvents 扩四总线（tickPre/tickPost ← 服务端 tick
     首尾遍历、cloned ← ServerPlayerEvents.COPY_FROM、respawned ← AFTER_RESPAWN；
     注意此版 fabric-api 无 AFTER_CHANGE_DIMENSION，changedDimension 需 mixin）；
     ItemEvents.tooltip ← ItemTooltipCallback（lines 可变列表 mutate 即生效，不可取消）；
     CommandEvents.register ← CommandRegistrationCallback。中立 payload ×6 落共享树
     （LevelEventJS/PlayerTickEventJS/PlayerRespawnEventJS/PlayerCloneEventJS/
     ItemTooltipEventJS/CommandRegistryEventJS），节点孪生接口 ×3
   - **基础静态绑定面补齐**（勘察矩阵漏项，冒烟暴露）：FabricCorePlugin 补 24 个
     纯 vanilla/中立绑定——ItemStack/Items/Item/Block/Blocks/BlockPos/Direction/
     Vec3/AABB/MutableComponent/Component/DyeColor/SoundEvents/ParticleTypes/
     EntityType/CompoundTag/Identifier/MobEffects/MobEffectInstance/DamageTypes/
     TriState/Color/UUID/StringUtils/Time/Utils/global/Test。此前 fabric 脚本连
     `ItemStack`/`Component` 都是 unknown identifier。

10. **注册 builder 六件套**（2026-09-02 第四批）：Item/Block/EntityType/Enchantment/
    CreativeTab/ParticleType 去守卫（builder 本体零 loader 依赖，纯连坐守卫），
    FabricRegistryAdapter 收尾挂两个平台钩子——实体属性（FabricDefaultAttributeRegistry，
    消费同一 drainPendingAttributes）与 groupTab 追加（CreativeModeTabEvents.
    modifyOutputEvent 按标签页分组，消费同一 GROUP_ASSIGNMENTS）。NeoForge patch
    差异三处行内处理：ItemBuilder.getBurnTime（fabric 无此 override，燃料面留待
    FabricFuelRegistry 批次）、EntityTypeBuilder.setShouldReceiveVelocityUpdates
    （fabric 无对应设置点）、CreativeModeTab.builder() 无参重载（fabric 走
    FabricCreativeModeTab.builder()）；AW 补 SimpleParticleType 构造。
    **平台事实（26.x）**：附魔是 vanilla 数据驱动注册表（BuiltInRegistries 无常量），
    代码注册在**两个加载器上都不可行**（NeoForge 26 实测同报 "RegisterEvent never
    fired"）；EnchantmentBuilder 条目保留（1.21.1 孪生可用），26.x 附魔走数据包面。
    CreativeTabBuilder 顺手修了 eager ItemStack 快照（fabric mod init 早于组件绑定
    必炸，改为懒回调解析——两侧通用）。
    已知笔误（共享树，两侧同在）：CreativeTabBuilder.icon 是字段，javadoc/示例里的
    `b.icon('...')` 方法调用写法不可用（应 `b.icon = '...'`）。

11. **P1 收尾批**（2026-09-02 第五批）：
    - 物品燃料面：ItemBuilder.burnTime 记账（FUEL_ASSIGNMENTS），FabricRegistryAdapter
      经 FuelValueEvents.BUILD 灌表（每次燃料表构建重放，幂等）
    - client 类绑定五项（Minecraft/Screen/Window/KeyMapping/InputConstants，
      scriptType()==CLIENT 条件注册）
    - KeyBindEvents fabric 孪生（register/pressed/released/tick；复用共享树 KeyBindIds）。
      两个 26.x 事实：fabric-api 的 KeyMappingHelper 在 Options 构建后撞 GameOptions
      二次初始化守卫（CLIENT 脚本时机必然晚于 Options）——孪生改为直接扩
      options.keyMappings 数组（AW mutable；NeoForge 版同款替换由 NeoForge patch
      去 final 支撑）；pressed/released/tick 的边沿检测挂 ClientTickEvents
    - 测试树放行三个中立适配器测试（BlockPos/Vec3/Holder）+ **修出 fabric 节点测试
      从未真正跑过的存量问题**：fabric-node.gradle.kts 缺 useJUnitPlatform()（Gradle 9
      默认框架非 Platform，jupiter 测试静默发现不了，failOnNoDiscoveredTests=false
      掩盖了这一点）——补上后 6 类 26 用例全绿（三个放行测试 + pdata 三件套存量）
    - common-api-processor 接线**不做**：processor 平台值只有 nf/cr 两态，fabric 是
      第三平台需先扩 processor 的平台模型（fabric 上大量 spec 未实现，接上会误报红）

12. **BlockEvents.rightClicked / leftClicked**（2026-09-02 第六批）：两个中立 payload
    （BlockRightClickEventJS / BlockLeftClickEventJS）落共享树，FabricBlockEventBindings
    接 UseBlockCallback / AttackBlockCallback（均未弃用；双端回调过滤客户端实例，SERVER
    总线语义与 NeoForge 侧一致）。可取消：脚本 return true → fabric 回调 SUCCESS。
    **26.x 平台事实：PlayerBlockPlaceEvents 已从 fabric-api 删除**——placed /
    entityPlaced / entityMultiPlaced 无现成回调，留 mixin 批次。语义差异：fabric 的
    AttackBlockCallback 只在生存模式触发（NeoForge 侧全模式），记入载荷 javadoc。

13. **事件 mixin 面收网（Player/Item/Entity/Block/Level）**（2026-09-05 第七批）：
    18 个新 mixin + 5 个 Fabric*BindingsV2 总线类 + 16 个共享树中立载荷 + 3 个孪生接口
    （PlayerEvents×10 总线 / ItemEvents×6 / EntityEvents×5）+ BlockEvents 3 / LevelEvents 5：
    - PlayerEvents：containerOpened/containerClosed（ServerPlayerContainerMixin：openMenu/
      openHorseInventory/openNautilusInventory 用 `@At("RETURN")` + `containerMenu` 读取
      ——openMenu 里新菜单是局部变量，INVOKE 注入取不到；doCloseContainer 用 HEAD，
      removed/putfield 之前旧菜单仍在）、crafted/smelted（ResultSlot 两混入 @At HEAD，
      26.x 无 ServerPlayerRecipes，smelted 数据在 FurnaceResultSlot.checkTakeAchievements）、
      destroyed（ItemStack.applyDamage：HEAD 快照 isBroken + RETURN 比较，避免
      INVOKE shrink 扫描降级）、advancement（PlayerAdvancements.lambda$award$0 HEAD——
      "刚完成且有 display"语义，与 NeoForge 一致）、entityInteract（并入
      MixinServerGamePacketListenerItemInteract——见下方工程经验 7）、changedDimension
      （ServerPlayer.teleport(TeleportTransition) HEAD/TAIL，26.x 无 changeDimension）。
    - ItemEvents：canPickUp+pickedUpPre（同一 handler 双 dispatch）、pickedUp
      （playerTouch TAIL + isRemoved 过滤成功拾取）、dropped（ServerPlayer.drop(ItemStack,ZZ)
      汇聚点 HEAD，isDeadOrDying 排除死亡掉落）、foodEaten（completeUsingItem TAIL）、
      entityInteracted（handleInteract INVOKE interactOn）。
    - EntityEvents：drops（dropAllDeathLoot HEAD）、finalizeSpawn（TAIL +
      CIR&lt;SpawnGroupData&gt;；EntitySpawnReason 26.x 才有，载荷整文件 `//? if >=26` 守护）、
      tickPre/tickPost（MixinEntityTick HEAD/TAIL）、leaveLevel（ENTITY_UNLOAD 惰性注册）。
    - BlockEvents：portalSpawn（PortalShape.createPortalBlocks HEAD，@Shadow axis/bottomLeft
      构造载荷；框架生成不经 PortalShape 的路径不覆盖——载明接线清单）、neighborNotify
      （NeighborUpdater.executeUpdate HEAD——26.x 红石通知网络唯一汇点，接口**静态**方法，
      mixin 类必须是 interface 且 handler private static；高频通道，无监听器短路）、
      farmlandTrample（fallOn 内 getBbWidth 调用点 INVOKE，全套判定之后，消除"体型
      过小多触发"偏差已记入 javadoc）。
    - LevelEvents：saved（ServerLevel.save TAIL）、explosionStart/detonate 及
      before/after 总线（Explosion.explode()I HEAD/TAIL + CIR&lt;Integer&gt;，
      前/后总线由同一 handler 按 hasListeners 分发）。
    **mixin 注入工程经验（本批实测，后续批次直接照抄）**：① `@At("INVOKE")` target
    在本 fork（sponge-mixin 基 0.8.7）下必须用单 L 形式 `Lowner;name(desc)ret`，
    `LL` 形式全部扫 0（owner 解析出多余 L 前缀，与 CP owner 永不匹配）；② INVOKE
    handler 的实参只能取宿主方法参（前缀）+ CallbackInfo/CIR，被调方法实参取不到——
    需要就用 TAIL/RETURN + @Shadow 字段或重新解析（如 packet.entityId()）；③ 目标
    方法非 void 必须用 CallbackInfoReturnable（否则 "CallbackInfoReturnable is
    required!"）；④ `method = "name"` 不带描述符会解析到继承链同名方法
    （fallOn 误中 Block.fallOn → 扫 0），一律写全描述符；⑤ CP owner ≠ 声明类的
    INVOKE 目标扫 0（transferState：CP 是静态类型 InventoryMenu、声明在
    AbstractContainerMenu）；⑥ 接口静态方法目标：mixin 类声明为 interface +
    handler private static；⑦ 同一注入点两个孪生 @Inject 出现"一个扫 0 一个通过"
    的怪癖（解释未定论），处理方式：合并进单一 handler 一次 dispatch
    （entityInteract + entityInteracted 已如此合并）。
    冒烟：runServer 启动 `Done (0.943s)` 零注入失败、78s 后正常停止；四节点
    （common/26.1.2/26.1.2-fabric/26.2.0）编译 + guardLint（247 守卫块 0 警告）全绿。
    遗留：ServerPlayer/ServerGamePacketListenerImpl 的混入在玩家连接时才类加载
    （headless 冒烟覆盖不到），defaultRequire=1 保证失败必崩不静默。Explosion radius
    AW public-f 未做（爆破半径修饰留待后续）。

14. **放置/流体/tick 簇 + inventoryChanged**（2026-09-05 第八批；spec 见
    `fabric-port-block-player-events-spec.md`，评审后按 javap/补丁源码实证修订）：
    - **placed / entityPlaced**：26.x 重大事实——NF 补丁源码里
      `BlockEvent.EntityPlaceEvent` 的唯一 post 点是末影人放置
      （`EndermanLeaveBlockGoal` 内 `EventHooks.onBlockPlace`），BlockItem/
      FallingBlock/Wither 均无挂点；`onMultiBlockPlace` 零调用（**entityMultiPlaced
      在 NF 26.x 是死事件**，fabric 不实现）。fabric 挂 `canPlaceBlock` HEAD
      （上下文全在声明参数，避免 INVOKE+LocalCapture 脆弱性），取消 =
      `setReturnValue(false)` → tick 的 && 短路，与 NF 取消语义一致（保留手中方块）。
      一次放置双总线投递（与 NF 双绑定同构）。分发键差异：NF 取快照（脚下方块）、
      fabric 取放置物——载荷 javadoc 与台账双记录。
    - **fluidPlaced**：NF 26.1.2 只挂 LavaFluid 三处（spreadTo 成石 + 火焰蔓延×2），
      不覆盖黑曜石/圆石/玄武岩（`LiquidBlock#shouldSpreadLiquid`）。fabric 覆盖
      LiquidBlock 两处 INVOKE（ordinal 0 = isSource?obsidian:cobble、ordinal 1 = basalt，
      取消 = `setReturnValue(true)` 流体继续流动）+ LavaFluid.spreadTo INVOKE
      （取消 = 整方法早退；NF 是参数换旧状态，净效果一致）——fabric 是 NF 超集。
    - **randomTick / blockEntityTick**：派发路径孪生决策落地——NF 侧不动（原生
      Event 子类走 NF 总线），fabric mixin 直投中立总线（`BlockBehaviour#randomTick`
      与 `LevelChunk$BoundTickingBlockEntity#tick` HEAD，与 NF 同名 mixin 逐行孪生，
      高频无监听器短路）。
    - **inventoryChanged**：共享树 `InventoryChangeListener` 本就中立——去整文件
      `//? if neoforge` 守卫即复用；fabric 挂载用 fabric-api 现成回调
      （`ServerPlayConnectionEvents.JOIN` ≙ PlayerLoggedInEvent、
      `ServerPlayerEvents.COPY_FROM` ≙ PlayerEvent.Clone，`registerLifecycle()`
      接进 NekoJSFabricMod），零 mixin。孪生 PlayerEvents 补 INVENTORY_CHANGED 总线。
    - 新载荷 ×4（BlockPlaced/BlockFluidPlaced/BlockRandomTick/BlockEntityTickEventJS）、
      mixin ×5、mixins.json +5、V2 总线 +5。
    - **顺带修复**：1.21.1 节点自第 13 批起整树编译红（`BlockNeighborNotifyEventJS`
      引用 26.x-only 的 `net.minecraft.world.level.redstone.Orientation`）——补
      `//? if >=26` 文件守卫后恢复绿。**教训**：共享树载荷的 import 也要做版本
      可用性检查（guardLint 只查 loader 泄漏，不查版本可用类型）。
    验证：runServer `Done (1.043s)` 零注入失败（LiquidBlock/LavaFluid/BlockBehaviour/
    BoundTicking 四 mixin 启动期即织入验证）；五节点编译全绿 + guardLint 320 文件
    0 警告。

15. **ServerEvents 资源/生命周期面收口**（2026-09-05 第九批；spec 见
    `fabric-port-server-events-spec.md`）：
    - **lootTableLoad**：fabric-loot-api-v3 `LootTableEvents.MODIFY`（ResourceKey,
      LootTable.Builder, LootTableSource, registries）→ 中立载荷
      `LootTableLoadEventJS`。**语义差异**：NF 是整表 get/set + 可取消；fabric 是
      builder 原地修改、不可取消（`event.table.addPool(...)` 风格）。26.x 字节码
      事实：`ResourceKey.location()` 已改名 `identifier()`。
    - **datapackSync**：`ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS(player,
      hasJoinedBefore)` → `DatapackSyncEventJS`（player=null = reload 后全体玩家，
      与 NF OnDatapackSyncEvent 语义一致）。
    - **tagsUpdated**：`CommonLifecycleEvents.TAGS_LOADED(registries, updated)` →
      `TagUpdatedEventJS`——fabric impl 经 ReloadableServerResourcesMixin 在服务端
      资源装载完成时触发（时机与 NF 对齐）；NF 的 UpdateCause 无 fabric 对应，
      载荷以 shouldUpdateStaticData 承载 updated 布尔。
    - **Item/Block MODIFICATION 重放**：四个 modification 类本就 vanilla-only
      （DataComponentMap/Holder.bindComponents/BlockBehaviour.Properties），去
      `//? if neoforge` 守卫（保留 `>=26`，1.21.1 走自己的副本）即复用；总线声明
      `BlockEvents.MODIFICATION` **上移共享层 BlockEvents.java**（`//? if >=26` 块内
      ——1.21.1 无此总线），NF `NeoForgeBlockEvents` 删本地声明，脚本面不变。
      fabric 触发时机与 NF 同位次：SERVER_STARTING（ABOUT_TO_START post 后）fire×2
      + `/nekojs reload server`（FabricNekoJSCommands reloadType SERVER 分支，
      配方重放之后）fire×2。快照恢复模型保证幂等。
    - 三载荷零 mixin、零 mixin 配置变更。全部守卫块调整经五节点编译验证。
    验证：runServer `Done (0.979s)` 零失败；五节点编译 + guardLint 323 文件 0 警告。
    **P1 事件面至此真正零缺口**（原清单漏项 modification 已补记）。

16. **inject 扩展链激活 + 债务清点**（2026-09-06 第十批）：
    - **发现并修复 26.x 存量缺口（双平台）**：P2-P4 重设计（50029bd2）把
      inject.MixinBlock/BlockState/Item/Entity/LivingEntity 五个 mixin 全部死在
      `//? if <26` 分支里，resources-modern 也没登记——26.x NF 侧
      `HostExtensionSource` 九源照常宣称，但 Item/Block/BlockState/Entity/LivingEntity
      的 `neko$*` 脚本面在 26.x 上**静默失效**。修复：五 mixin 去 `<26` 包裹
      （空 `implements` 实现，两端通用）+ resources-modern 登记 +5。
    - **fabric 侧**：MixinItemStack 去守卫 + nekojs-fabric-shared.mixins.json +6
      （五复活 + ItemStack）+ FabricCatalogPlatformProvider 补齐九源（原缺
      ItemStack/BlockState 两源）。verbose 冒烟确认 9/9 inject mixin 全部织入。
    - **ItemStackExtension 中立化三处**（原整文件 neoforge 守卫的原因）：
      enchantById / hasEnchantment(id,level) 的动态附魔注册表服务端访问
      （NF ServerLifecycleHooks ↔ fabric FabricServerEventBindings.currentServer）、
      componentIngredient 的组件匹配成分（NF DataComponentIngredient ↔ fabric
      DefaultCustomIngredients.components，fabric 无 strict 区分）。行内 loader
      守卫落地。**26.x API 事实**：`ItemStack#getEnchantmentLevel` 已移除，改经
      `getEnchantments().getLevel()`。
    - **守卫新教训（active 节点语义）**：active 节点直接编译共享树磁盘——守卫对
      active 侧是惰性注释，**禁用分支必须在磁盘上 `/* */` 包裹**；stonecutter 生成
      侧对 active 分支剥包裹、对禁用分支加包裹；分支内容行首不能是 `//`（会被
      生成器吞掉）。
    - **common-api 债务（memory 修订）**：ApiContractViolation 已删（确认零引用）；
      NullJsValueView / ConversionContext **不删**——memory 记载过时，二者现被
      NewAdapterBridge/LegacyAdapterBridge 与 JsTypeAdapter 接口承载。
    - **Explosion radius AW 作废**：26.x `Explosion` 已接口化、`radius()` 只读
      访问器、无字段、脚本面无 setRadius——两平台语义一致，无 AW 可做（batch 13
      遗留项销账）。AT 第 112 行字段条目为 1.21.1 时代遗留，26.x 不生效。
    验证：五节点编译 + guardLint 全绿；verbose 冒烟 `Done` 零注入失败、9/9 inject
    织入；common 测试绿。

17. **Fabric 分发 jar 与发布隔离门禁**（2026-09-06 发布收口首项）：
    `verifyFabricRuntimeArtifact` 直接读取最终 fat jar，断言 `fabric.mod.json`、access
    widener、三份 Fabric mixin 配置、Fabric 主/客户端入口与 common runtime 入口以及已测
    Fabric API 最低版本均存在，同时拒绝 NeoForge AT/mods.toml/mixin 配置、路径或类名中
    含 `NeoForge`/`neoforged` 的 loader-specific class 与 NeoForge service。Fabric source
    set 同时排除未被外层守卫包裹的 `NeoForge*.java`，避免门禁只报错而无法生成合规制品。
    任务接入 Fabric `check`，因此节点 `build` 自动执行；嵌套仓库 CI 在 release 时也会排除
    `nekojs-fabric-*`，避免尚未通过运行时 smoke 的 jar 被 GitHub Release 意外公开。
    CI 新增版本库中的 Fabric server smoke fixture，启动后要求 startup/server-started
    markers，并拒绝常见 Mixin/启动失败。

## P1 剩余（功能面）

- BlockEvents：fluidPlaced 的 LavaFluid.randomTick 火焰蔓延两处（NF 有、fabric 无，
  无脚本价值）。placed/entityPlaced/fluidPlaced/randomTick/blockEntityTick/portalSpawn/
  neighborNotify/farmlandTrample/modification 已在第 13-15 批完成；entityMultiPlaced
  双平台死事件（NF 零调用点）。
- PlayerEvents / ServerEvents（lootTableLoad/datapackSync/tagsUpdated）/ Item·Block
  modification：全部完成（第 13-15 批）。
- LevelEvents：saved/爆炸系已在第 13 批完成（Explosion radius 修饰 AW 未做）。
- ItemEvents：mixin 面已在第 13 批完成；剩 category/food 配方面（其余 P2）。
- FluidBuilder（需 fabric 流体重设计）。
- common-api-processor 的 fabric 平台支持（processor 端先扩平台模型，见上）。

## P2（大块移植，按需排期）

脚本编辑器同步 8 包 + 工作区 GUI（NekoWorkspaceScreen/NekoCodeEditor 需 AW：MultiLineEditBox
等）；配方面余量（ItemModification/BlockModification 重放、tagsUpdated/lootTableLoad/
datapackSync、generateData）；ItemStackExtension+MixinItemStack 链（ItemStack 脚本扩展面）；
BlockStateExtension（hasTag）；TagLoaderMixin（ServerEvents.tags）；后处理三件套
（ShaderManagerMixin/PostEffectManager）；VillagerTrades（fabric 无 RegisterVillagerTradesEvent
等价，需 mixin 或重写）；JEI；BlockModelGenerator（fabric 无 datagen 事件，需自建管线）。

## B 类豁免（NeoForge 专属概念，移植=重新设计）

CapabilityEvents（fabric 无 capability API，DataComponent 方案另议）；
BlockEvents.toolModification；generateData/generateAssets/lang（datagen 事件模型）；
流体配方面（FluidResolver/FluidIngredientJS/SizedIngredientJS/FluidJS——fabric 无流体体系）。

## 语义差异记录（非缺口）

- fabric 的 ServerEvents.starting/aboutToStart 都在 SERVER_STARTING（世界装载前）触发
  （NeoForge 的 Starting 在世界装载后）——脚本请到 started 再访问玩家列表。
- damagePre 只能取消不能改伤害量（fabric ALLOW_DAMAGE 语义）。
- CLIENT 脚本加载在 CLIENT_STARTED（晚于 NeoForge 的构造期）。
- fabric 无条件配方系统：neoforge:conditions 配方在 fabric 全量进工作集。
