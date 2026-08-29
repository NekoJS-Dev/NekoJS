# 06: ScriptEvents 自定义事件中立化（双平台）

**What to build:** 自定义脚本事件注册面（`ScriptEvents.server(ev => ev.register('组','名','net.neoforged...类名'))`）目前以 NeoForge 事件类名驱动、整文件 neoforge 守卫。中立化重设计：中立事件源描述（平台无关的注册参数）+ 双端解析（NeoForge 挂原生 bus、fabric 挂 fabric 事件或中立 dispatch）。**设计先行**：API 形态若拿不准先向用户摆方案（可能需要一轮 grilling）。

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] 中立 API 形态确定（与用户确认）
- [ ] NeoForge 侧迁移到中立形态（脚本写法尽量不破或一次性切换）
- [ ] fabric 侧最小子集可用
- [ ] wiki《事件扩展》同步 + 四节点编译 + 测试 + guardLint 绿
