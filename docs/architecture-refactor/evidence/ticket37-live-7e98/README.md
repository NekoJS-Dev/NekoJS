# 精确 7e98 实机多人窗口与客户端视觉

源码 `7e98fd15175d15c8dad062bf40e2a284e04eaf51`。服务器与 A/B/C/D/E 均安装官方 NeoForge26.2 JAR，SHA256 `9acfb41ec223240054d852f04fc6f892c1be62f64dea1af71008c417fa70bd93`，NeoForge26.2.0.75/JDK25.0.2。新专属世界、离线合成身份、相同 STARTUP fixture；客户端没有动态 ID 预注册。生产源码、HostAccess/ClassFilter、路径门禁、协议、性能门槛均未改。

## 多人 PASS

- A/B/C 实际配置 catch-up 第2代后登录；加入观察 fixture 的普通重载第3代正常提交。
- 第4代新增 `ticket37_live:window_item`。本机 TCP 代理按原有动态消息识别 PREPARE，只延迟原始帧，不改字节、不生成回复。C 的 PREPARE 暂缓时释放 D 的连接握手；D 在配置阶段收到第4代 PREPARE，服务器记录 `joined-mid-transaction`。
- 代理在把 D 的 PREPARE 交付给客户端前断开其真实连接。服务器记录 `participant disconnected before acking the prepare`，第4代整批 ABORT，没有 COMMIT，activatedGeneration 保持3。实际 give 报 Unknown item。
- 第5代正常恢复，A/B/C 三个真实 ACK、三个 activation-report；实际 give 成功。
- 第6代再次暂缓 C 的 PREPARE，让 E 在事务等待 ACK 时进入配置阶段。E 收到 PREPARE/COMMIT；四个真实 ACK、四个 activation-report，配置任务释放后 E 实际登录并收到物品。第4/6代分别覆盖失败和成功窗口。

脚本 generation 与动态事务 generation 不同。例如第4代动态事务中止时，RCON 的脚本 reload 仍报告脚本 generation3 提交；不能仅依据命令响应判定动态事务成功。原始代理记录、服务器完整观察、RCON 命令和两份 verdict 均在固定 ZIP。

这里闭合的是 NeoForge26.2 的实际配置阶段并发加入及 PREPARE 交付前断线窗口；没有新增包下载中重载的独立结论，也不外推其他节点。

## 视觉与生命周期 PASS / PARTIAL

- computer-use 最初针对 A 返回无关应用画面；刷新并激活正确窗口后恢复。无关画面不归档为游戏证据。
- A 实际世界可见，游戏内 F2 保存 [world.png](world.png)。fixture 没有提供物品模型，日志和画面保留缺失模型现象，不把缺失纹理算作资源呈现通过。
- 给 A 添加现有资源 ID `minecraft:invert` 的声明 blur override 后，真实 F3+T 从声明 generation1 升到2。新 generation 的第一个 HUD 回调读到 `declared=true/current=minecraft:invert/active=true`，后续原生世界 [blur.png](blur.png) 确认模糊效果。
- 原生产 binding clear 返回 true，实际回读 `current=null/active=false`；[cleared.png](cleared.png) 显示清晰世界。
- 故意抛错的 CLIENT 候选记录 RELOAD_CANCEL；旧声明 generation2 保持，旧 callback 仍可重新激活并渲染，见 [retained-after-rejection.png](retained-after-rejection.png)。移除专属失败 fixture 后，下一次 F3+T 成功安装 generation3，第一个 HUD 回调再次看到正确 active 状态。
- E 的登录回调 set 返回 true，但首次 HUD 回读 `current=null/active=false`；初始登录声明已安装，但登录期间运行操作的时序仍未解释。这个观察不改为 PASS。

第一个 HUD 回调是实际渲染提取阶段的状态观察；F2 是之后的像素图，不能拼成首次像素帧抓取。首次像素帧、任意新 inline ID 和其他节点的客户端视觉仍 NOT VERIFIED/NOT RUN。

## 原生失败与退出

D/E 分别发生 `EXCEPTION_ACCESS_VIOLATION`，原生 frame 均为 `glfw.dll+0x10fa1`，实际进程退出1。窗口激活失败后按 computer-use 规则刷新一次，窗口已消失；没有继续向失效窗口输入。崩溃完整原因未建立，不能归因于模组、驱动或自动化工具，不能称为正常退出。原始 stdout/stderr 和退出记录归档；包含机器细节的原始 hs_err 留在本地，只归档其 SHA。

服务器经 RCON stop 正常退出0；A/B/C 用实际窗口 Alt+F4 正常退出0。三个代理均已返回，四个专属端口可重新绑定，没有强制结束进程或干预用户进程。完整客户端验收仍 FAIL，部分协议/视觉 PASS 不替代它。

## 证据与复现

[runtime-summary.json](runtime-summary.json) 和 [manifest.json](manifest.json) 绑定 [live-evidence.zip](live-evidence.zip)：116项、941380字节，SHA256 `ebf59e9f6f8c835ce97757a74f5e350fcf101ad7bcff2fb0eac9aec7d4ab3267`。包含准备/启动/代理/证明/归档脚本、fixture、实际日志、直接退出、同步观察和四张游戏截图。逐项hash、ZIP readback及精确RCON凭据扫描 PASS。排除 server.properties、world、账户/launcher参数、binary/library/assets、原始 hs_err。保存图像是新测试目录的游戏内截图，与此前八张未跟踪 native 图片无关。

```powershell
python build/ticket37-config-sync-repair/prepare-live-7e98.py
python build/ticket37-config-sync-repair/run-live-7e98.py server
python build/ticket37-config-sync-repair/live-proxy-7e98.py C 25973
python build/ticket37-config-sync-repair/live-proxy-7e98.py D 25974
python build/ticket37-config-sync-repair/run-live-7e98.py A
python build/ticket37-config-sync-repair/run-live-7e98.py B
python build/ticket37-config-sync-repair/run-live-7e98.py C
python build/ticket37-config-sync-repair/run-live-7e98.py D
python build/ticket37-config-sync-repair/prove-live-window-7e98.py
```

E 的成功窗口、后续视觉和退出顺序见 ZIP 脚本/日志与本文；固定目录有防覆写检查，重放应使用新专属路径与新的门闩文件，不能覆盖本轮证据。GUI 操作由 computer-use 完成，未使用另一套 Windows UI 自动化。

## 代理审查与剩余交付

维护者在2026-10-10主会话指示「那你继续，全都由你来做」，授权代理继续技术验收与交付整理。这是实际工作授权，不伪造维护者对所有结果的通过结论。

代理已核对原始4/6代因果顺序、真实participant/ACK/activation、失败后不存在/恢复后存在、声明代数/画面/退出/制品SHA；认定上述有限范围 PASS。此前7份接口静态成员 golden 的 AST 审查与独立Standards/Spec结论继续有效，代理技术结论为可接受；实际人类对该7份golden的逐项结论尚未出现。

整体交付判定仍 NO-GO：完整TS/Pyright失败、新源码正式性能未跑、文件系统间歇失败、原生客户端崩溃及跨节点视觉/首帧、fireResistant、剩余领域与最终发布/回滚结论均未闭合。A22/A28的精确历史授权边界保持，不删除新公开接口，不发布制品，不创建issue。自动续跑仍PAUSED。
