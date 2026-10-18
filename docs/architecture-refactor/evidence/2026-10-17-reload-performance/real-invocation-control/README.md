# 2026-10-18 游戏内原调用与直接调用对照

当前正式重载仍为 **466.4 / 469.4 ms，FAIL**。本轮只控制调用方式，未改生产源码、编译类、原 RCON 客户端、固定 fixture 或原正式成绩。1625 个 main 类在运行前后均与 `6e5bb8f0` 正式测量一致。

一个正常游戏会话内，原 PowerShell `Invoke-Rcon` 与同一可执行文件的直接 .NET Process 调用交替二十对，每对交换先后，每条间隔三秒，四十条及首值全部保留。两臂均发送完整 `nekojs reload`，等待完整 RCON 返回后按原方式每 100 ms 轮询转发 stdout marker；所有重载均 COMMIT、无脚本错误，最终正常 RCON 停服，无强杀或超时。

| 调用 | RCON 均值（ms） | marker 均值（ms） |
|---|---:|---:|
| 原 PowerShell native | 265.54 | 352.37 |
| 直接 .NET Process | 251.00 | 330.46 |

配对 RTT 差值有正有负，范围约 -84.53 至 142.02 ms。首轮与执行顺序、JIT、游戏调度及主机波动混在一起，不能把 14.55 ms 的均值差当作已证实的调用实现收益。两臂 marker 均值仍超过 285.3 ms；结合空端点控制，不支持优先改造调用器来解决主要回归。该长会话诊断也不能替代原五样本正式组。

归档包含完整四十条观测、游戏 stdout/stderr、控制器、会话终止结果及正式类摘要绑定。文本凭据匹配按 `redactions.json` 脱敏，未归档凭据配置文件。

```powershell
python docs/architecture-refactor/evidence/2026-10-17-reload-performance/real-invocation-control/verify.py
```

完整性和独立重算 PASS。`evidence.zip` 含 18 项、314418 bytes，SHA-256 为 `a5fdf40244ea10baee8a20a37a7cdcb8c5a2a8756eb25b4e557f07c59546adbb`。
