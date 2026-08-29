# 04: Fabric 网络 v1：ScriptSync 脚本同步通道

**What to build:** fabric networking（payload 类型注册 + `ServerPlayNetworking`/`ClientPlayNetworking`）实现服务器→客户端脚本内容同步（ScriptSyncService 的推送面）。单人/多人场景下客户端拿到服务器同步的脚本。为 pdata/clientData 提供通道底座。

**Blocked by:** 03（客户端入口是接收端）。

**Status:** ready-for-agent

- [ ] fabric 侧 payload 注册与编解码
- [ ] 服务器→客户端脚本内容同步真机验证
- [ ] 四节点编译 + 测试 + guardLint 绿
