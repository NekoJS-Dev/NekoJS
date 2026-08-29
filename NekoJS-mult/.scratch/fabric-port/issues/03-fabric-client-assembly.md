# 03: Fabric 客户端装配：CLIENT 脚本 + 基础客户端事件

**What to build:** fabric 客户端入口（`ClientModInitializer`，fabric.mod.json `client` entrypoint）：CLIENT 脚本发现与加载（对齐 NeoForge 侧 NekoJSClient 时机）、`ClientEvents` 组的 tick 子集经 fabric `ClientTickEvents`。客户端脚本在 fabric 真机（runClient）可执行并响应 tick。

**Blocked by:** None (can start immediately).

**Status:** done

- [x] fabric.mod.json client entrypoint 注册，CLIENT 脚本加载
- [x] `ClientEvents.tickPost` 在 fabric 客户端触发
- [x] runClient 冒烟（或至少客户端脚本加载日志 + tick 验证）—— latest.log 11:44: `[client] FABRIC-SMOKE-CLIENT: client script loaded` + `tick #1~#3`（CLIENT_STARTED 加载、END_CLIENT_TICK 驱动 + flushClientNodeTimers）
- [x] 四节点编译 + 测试 + guardLint 绿
