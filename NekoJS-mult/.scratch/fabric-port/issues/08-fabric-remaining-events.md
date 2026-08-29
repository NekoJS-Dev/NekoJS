# 08: Fabric 余量事件：damagePre/Post + ItemEvents 子集

**What to build:** 按已确立的「中立 payload + fabric 桥 + 同名组」模式批量接通：`EntityEvents.damagePre/damagePost`（fabric `ServerLivingEntityEvents` 的 ALLOW_DAMAGE/AFTER_DAMAGE）、`ItemEvents.rightClicked` 子集。低优先级，模式已成熟。

**Blocked by:** None.

**Status:** done（rightClicked 代码级验收，见下）

- [x] damagePre/damagePost 在 fabric 真机触发（僵尸高空摔落，`DMG-SMOKE: pre amount=1` / `post amount=1` 多轮触发）
- [x] ItemEvents 子集至少一个事件接线（rightClicked；实机点击未遂，改代码级验收——自动化输入无法穿透 MC 的鼠标捕获，详见下）
- [x] 四节点编译 + 测试 + guardLint 绿

## 落地记录

- **LivingDamageEventJS**（中立 payload，`wrapper/event/entity/`，entity/source/amount 对齐契约承诺集）。
  fabric 桥挂 `ServerLivingEntityEvents.ALLOW_DAMAGE`（可取消：监听器返回 true → ALLOW_DAMAGE
  返回 false 免除伤害）与 `AFTER_DAMAGE`。**能力差异**：NeoForge 的 Pre 可改写伤害量
  （setNewDamageAmount），fabric 只能整体放行/拒绝——javadoc 与票面均记录。
- **可取消总线的 fabric 侧建法**：`EventGroup.server(...)` 的默认可取消性走
  `eventCancellability` → 外部谓词（NeoForge 侧 `NeoForgeRuntimeBootstrap` 装的
  `ICancellableEvent.class::isAssignableFrom`），fabric 上恒 false。需要可取消的中立载荷总线
  用 `EventGroup.add(name, ScriptType.SERVER, EventBusJS.of(type, true, dispatchKey))` 显式建。
- **ItemRightClickEventJS**（中立 payload，player/itemStack/hand/level）+ **rightClicked**：
  fabric-api 的 `UseItemCallback` 是**纯客户端事件**（挂在客户端交互链上），喂不了 SERVER 总线
  ——第一版接它并 `isClientSide` 过滤，结果永不投递。改走服务端等价入口
  `ServerGamePacketListenerImpl#handleUseItem`（客户端 UseItem 包的服务端处理），
  新增 `ServerGamePacketListenerMixin`（HEAD 注入、可 cancel，脚本 return true 跳过原版使用），
  dispatch 键 `Item`（按物品 id 字符串分发，与 NeoForge 一致）。

## rightClicked 为何代码级验收

damagePre/Post 已在真机验证（同一套 payload/桥/dispatch 机制）。rightClicked 的服务端入口
（mixin + handleUseItem HEAD 注入）与 02 票真机验证过的 ServerLevelMixin 同机制、同加载链
（`Preparing nekojs-fabric.mixins.json (3)` 确认 3 个 mixin 都进了配置）。未做实机点击的原因：
自动化鼠标输入（`mouse_event` 绝对/相对、坐标换算、前台激活、双击激活+捕获）都无法穿透 MC 的
原始鼠标捕获变成游戏内 Use 动作——键盘路径（聊天框 T + 文本 + 回车）验证可达，但右键无键盘
等价物。首次真人进服右键即是最终验收；机制层面（mixin 注入点 + 包处理路径）已由代码审查覆盖。

## 模式沉淀（后续余量事件照抄）

1. 中立 payload 放共享树（无守卫），成员名对齐契约 getter；
2. fabric 桥：`EventGroup.of("同名组")` + `EventBusJS`（dispatch 键用 vanilla 类型），
   可取消总线用 `EventBusJS.of(type, true, dispatch)` 显式建；
3. 平台事件面：NeoForge 直传原生事件（`EventBusForgeBridge`），fabric 转换为中立 payload 后 post；
4. fabric-api 的玩家交互事件全是客户端的——服务端语义一律走 mixin 钩 vanilla 包处理方法。
