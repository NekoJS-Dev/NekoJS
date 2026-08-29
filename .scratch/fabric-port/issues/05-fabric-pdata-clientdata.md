# 05: Fabric pdata / clientData 推送

**What to build:** 依托 04 的网络通道实现 `PDataSyncService` / `ClientDataSyncJS` 的 fabric 面：服务器按需推送、客户端只读视图（common 的绑定已就绪，只差平台网络端点）。脚本 `player.pdata()` / `clientData` 在 fabric 可用。

**Blocked by:** 04。

**Status:** ready-for-agent

- [ ] pdata 服务器→客户端同步在 fabric 真机可验证
- [ ] clientData 只读视图脚本可访问
- [ ] 四节点编译 + 测试 + guardLint 绿
