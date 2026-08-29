# 02: Fabric mixin 通道 + EntityEvents.joinLevel

**What to build:** fabric 节点接入 mixin 基建（fabric.mod.json 增 `mixins` 键 + mixin 配置 JSON + fabric.gradle.kts 停止排除该配置），写「实体加入世界」钩子（EntitySectionManager/LevelCallback 级 addEntity），post 已有的 `EntityJoinLevelEventJS` 中立 payload 到按实体类型 dispatch 的 `EntityEvents.joinLevel` 总线（fabric-api 无此等价事件——ENTITY_LOAD 仅覆盖存储装载，故须自建）。

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] fabric 节点 mixin 基建生效（配置被 loader 加载、钩子类被应用）
- [ ] 脚本 `EntityEvents.joinLevel('minecraft:zombie', ...)` 在 fabric 真机触发
- [ ] 冒烟：forceload + `PersistenceRequired:1b` 僵尸 summon → joinLevel 回调输出（方法论见 MIGRATION-ROADMAP P4-d 踩坑记录）
- [ ] 四节点编译 + common:test + guardLint 绿
