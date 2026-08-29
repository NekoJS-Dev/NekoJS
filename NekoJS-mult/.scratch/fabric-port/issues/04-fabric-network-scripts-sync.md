# 04: Fabric 网络 v1：ScriptSync 脚本同步通道

**What to build:** fabric networking（payload 类型注册 + `ServerPlayNetworking`/`ClientPlayNetworking`）实现服务器→客户端脚本内容同步（ScriptSyncService 的推送面）。单人/多人场景下客户端拿到服务器同步的脚本。为 pdata/clientData 提供通道底座。

**Blocked by:** 03（客户端入口是接收端）。

**Status:** done

- [x] fabric 侧 payload 注册与编解码（`PayloadTypeRegistry.clientboundConfiguration()`，复用共享树 PackHashListPayload/PackBundlePayload 的 StreamCodec，线格式与 NeoForge 侧一致）
- [x] 服务器→客户端脚本内容同步真机验证（dedicated server + runClient --quickPlayMultiplayer localhost）：
      服务器 `Pushed 1 script pack(s) to a configuring client`（Netty NIO IO 线程，配置任务内）→
      客户端落盘 `run/nekojs/server_packs/49960de5…/packs_fabric_sync_smoke/{manifest.json,client_scripts/}` →
      未信任时按提示断连（`Run /nekojs trust localhost … and reconnect`）→ 写信任后重连
      `Activated 1 remote script pack(s) from server localhost` + 远端包脚本 `FABRIC-SYNC-SMOKE: remote pack script executed on client`
- [x] 四节点编译 + 测试 + guardLint 绿

**踩坑记录**
- fabric 与 NeoForge 的差别只在入口：`ServerConfigurationConnectionEvents.CONFIGURE` 里
  `addTask(...)` 挂 `ConfigurationTask`（同 Type id `nekojs:pack_sync`），而不是把发送 defer 到
  `server.execute` —— 后者会跑到配置阶段之后，实测一个包都推不出去。
- 客户端 receiver 在 netty event loop 执行（fabric 明文契约），与 NeoForge 侧同构：
  `prepareMainThreadWork` → `client().execute(落盘/验签/信任/激活)` → `awaitMainThreadWork` 阻塞
  网络线程，保证注册表校验时远端脚本产物已就位。
- dev run 目录必须 server / client 分开（`loom.runs.named("server") { runDir("run-server") }`）：
  共用时两个进程互相覆盖 `logs/latest.log`（Windows 上还撞 `Files.move` 轮转异常），且客户端会把
  服务器的 `nekojs/packs/` 当成自己的本地 GLOBAL 包 —— 冒烟会假阳性（脚本在客户端跑起来了，
  但那是本地包不是同步来的）。判据改成 `server_packs/<bucket>/` 落盘 + `Activated … from server`。
- `/nekojs trust` 命令仍是 neoforge 面（命令注册未移植），fabric 冒烟直接写
  `nekojs/config/trusted-servers.json`（bucket = sha256(小写地址)）。命令移植归后续票。
- 首次 runClient 会弹无障碍引导挡住 quickPlay，`options.txt` 置 `onboardAccessibility:false` 即可。
