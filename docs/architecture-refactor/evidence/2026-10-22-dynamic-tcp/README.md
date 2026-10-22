# Dynamic Registry：两个现代 NeoForge 节点的真实 TCP 验收

## 结果与范围

冻结安装候选 `d91ca974ba88fe3507281b47a3bdb574322c5c9d`：26.1.2 与 26.2.0 各八阶段通过；各有独立服务器、客户端 A、关闭 gate 的客户端 B、重启后开启 gate 的 B 四个进程，合计八个正常退出 0，无强制终止。每个进程核对 ModList 路径、CodeSource、class resource 和安装 JAR class SHA；制品与 class 全量摘要见 archive 中 artifact-manifest。

26.1.2 客户端/服务器均为 NeoForge 26.1.2.71；26.2 客户端使用缓存的 26.2.0.57、服务器为 26.2.0.75。客户端通过独立 ModDev 2.0.140 启动器运行，唯一 source mod 是验收 helper，NekoJS 来自生产 JAR；这不等于外部官方安装器或人工多人体验验收。26.2 仅隐藏供应商开发图标警告，未修改 gate 或生产权限。

## 八阶段

1. 空 ledger，A 开启 gate、B 关闭 gate，均进入 PLAY。
2. B 对真实 PREPARE 返回 NACK，整批中止，服务器及两个客户端无新物品。
3. B 正常退出；A 经真实 PREPARE/ACK/COMMIT 激活 seed 物品。
4. B 开启 gate 后重启，通过配置阶段 STATE_SYNC 补齐 seed。
5. 新增 dual 物品，两个不同 participant 的 ACK 均被观察到；三侧物品实际存在，maxStackSize=16、rarity=RARE。
6. helper 在 A owner thread 有限延迟 12.5 秒，服务器按原 10 秒门槛超时；迟到的真实 ACK 记录为 late-ack-outside-transaction，新物品未激活。
7. 准备中通过真实服务器 kick 断开 A，批次按 participant-disconnected 中止。
8. A 使用真实 ConnectScreen 重连；仅同步成功激活 generation 4，失败 ledger 已到 6；三侧均无 timeout/disconnect 物品。

未直接调用 ACK/网络 handler，未反射私有 participant，也未新增生产测试接口。观察器只读取现有公开状态和真实注册表；延迟、kick、脚本与 owner thread 控制均属于隔离夹具动作。`containsKey` 与实际注册 key 均核对，避免 AIR fallback 假阳性。

## 失败尝试与复核

261 v1 使用错误大写 reload 参数，未发布 ledger；v2 的 VM argfile 路径转移不完整，被安装来源断言正确拒绝，失败 bootstrap 未退出，外部精确强制清理其独有 PID 7488。两次均 NOT GREEN，完整失败记录与 external-cleanup.json 保留。v3 修正夹具后从全新目录/世界完成八阶段；未放宽来源或生产检查。262 首轮完成全部八阶段。

可执行 `python docs/architecture-refactor/evidence/2026-10-22-dynamic-tcp/verify.py` 离线核对 archive 摘要、安装 class 绑定、16 个阶段、八次正常退出以及失败尝试。原运行器还逐字节读取本地实际 JAR 验证绑定；离线包不包含 JAR、world、依赖、启动参数文件或 server.properties。

## 剩余边界

后续 `ad4d3750` 只验证了 common 源码与严格定向类型，尚未统一重建并复测最终候选。本文不覆盖 COMMIT 后激活失败的降级、多种动态注册类型穷举、Fabric 动态激活、完整 TSC、最终性能与制品/数据分离回退，也不代签维护者发布结论。261 acceptance-review 的 remaining 字段是当时快照，其中 262 runtime 已由本包后续结果补齐。
