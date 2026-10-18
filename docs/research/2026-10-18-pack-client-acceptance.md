# 脚本包真实客户端验收：隔离输入与执行记录

状态：**准备通过，真实 A→B→A 与签名网络验收尚未完成。** 本记录接续[优先级工作](2026-10-17-priority-closeout.md)，实现与执行由主代理负责，Luna 仅只读调查。

## 测试范围与输入

- NeoForge 26.2，主工作树源码；`:26.2.0:build --console=plain` 通过，34 项任务，3 执行、2 缓存、29 up-to-date。
- 客户端使用 `build/priority-closeout/client-world-acceptance/game`，沿用仓库 `script-pack-runtime.init.gradle` 的独立 gameDirectory。用户 `.minecraft`、其他 mod 和存档未修改。
- 从已属于本任务的旧 NeoForge 26.2 服务器 world 复制两个隔离存档，分别命名 Neko Pack Acceptance A/B；不复制 session.lock 或旧世界包。两者使用相同 `sessionprobe/sessionconsumer` 包 ID、不同世界标记，每包独立 SERVER/CLIENT entries，`clientSync=false`。
- probe 保留 4096 项私有负载，输出世界/类型/activation/count、tick 和 timer；consumer 验证 capability 返回正确世界和首次私有计数。真实切换时记录同一客户端 PID、各阶段画面/日志及退出后的旧回调；现有 common 旧句柄失效测试单独保留，不冒充 GUI 切换证据。
- 独立服务端使用同级 `server` 目录及 loopback 25873/25874，离线测试；RCON 凭据随机生成，只保留在本地 server.properties，不进入命令行或交付。客户端和服务端均设 `[packSync] mode="all", allowUnsigned=false`，尚未授予来源信任。
- `signedacceptance` 为 Ed25519 签名 GLOBAL 包，syncId 为 `packs:signedacceptance`，稳定测试 keyId 为 `acceptance-local-20261018`。一次性 Java helper 调用当前 PackSigner / canonical payload；私钥存于包目录以外的 `server-only-signing`，不分发、不提交。已验证有效签名、改变 payload 拒绝、改变 scope 拒绝；这只是准备检查，不是网络通过。

五个包全部通过当前 PackTools.validate，九份 JS 全部通过 Node 语法检查。首次 fixture 错用大写 capability 名 `worldStamp` 被既有小写标识符规则拒绝；保留失败输入和日志，改为 `worldstamp` 后通过。没有修改解析规则。第一次客户端 init 在 buildSrc 项目查找不存在节点时失败；补上与现有 init 一致的 null 项目保护，原失败日志保留。

## 测试依赖的启动问题

第一次实际客户端 PID 20760 停在 GraalMC 的 `logoFile` 警告页；截图与日志保留。用户报告 mod 有问题后，核对得到：

1. GraalMC 25.1.3.7 的 `neoforge.mods.toml` 仍使用 `logoFile="icon.png"`。[NeoForge 官方说明](https://github.com/neoforged/Documentation/issues/372)确认 26.2 开发环境会因此显示加载警告。
2. MCP 0.4.2 的 pack.mcmeta 仅有 pack_format 88，没有 min_format/max_format；实际 ResourcePackLoader 记录解析失败并使用 fallback。

只在 owned 测试副本修正这两个元数据：GraalMC 改 iconFile，MCP 补 min/max 88。GraalMC 的 10224 个 class、MCP 的 34 个 class 字节完全相同；原 cache/项目依赖文件未变。客户端 init 只在该次运行的 runtimeClasspath 排除原 GraalMC artifact，换为测试副本；compile 输入不变。

| 依赖 | 原 jar SHA-256 | 测试副本 SHA-256 | 唯一修改 entry |
| --- | --- | --- | --- |
| GraalMC | `d48e2cae5988175ca2a7593e3403c64ded6503649a50e6e368b9cf6d73780a22` | `ad0b9c65176e26815a5c1f6b1f8338a0043210c1ced4a8eae9e6a54018e4ae8a` | META-INF/neoforge.mods.toml |
| MCP | `2e2caf30f27d2e76ff68bbfc6b9c00aad21970015a3a0c574eefc2efd9e5bc1c` | `53a32aa3dd5c94edbf5f3d2a29bee057ed9fb1a169f8508586a4643158001b71` | pack.mcmeta |

核实创建时间后仅终止 owned 首轮客户端，发生于进世界前；此为强制结束的启动准备会话，不能记作正常退出或资源释放通过。修正后客户端 PID 11620 已越过加载警告进入 Minecraft accessibility onboarding 画面，上述两项元数据警告消失。截图可用，但 MCP 的 get_screen_buttons 仍返回 `no screen`、get_player_info 为 `{name:null}`；控制接口问题还在调查，尚未操作切世界。MCP 的人工激活约束保留，未通过 enter_control_mode 绕过。

服务端 PID 18604 已到 Done，RCON 查询确认发现一份启用的 GLOBAL:signedacceptance、在线玩家为 0。服务端发现包不等于客户端已收到、验签或执行。

## MCP 26.2 屏幕兼容与首次进入世界

元数据修正后进一步定位到 MCP 的屏幕访问仍沿用旧版 Minecraft 根对象字段/方法。26.2 将当前 screen 和 setScreen 移到 `Minecraft.gui`；旧反射会漏掉 onboarding，并在进入世界后把 LocalPlayer 当作 screen。已在 owned MCP 测试副本添加窄适配，未修改 NekoJS 的依赖声明、全局缓存或人工接管边界：

- getter 在确认 Gui API 存在时以 `gui.screen()` 为准，包含合法的 null；旧版本继续原 fallback。
- closeScreen、openPauseMenu 和手动 transfer overlay 的现有 setter 查找/调用指向 Gui owner，原分支和控制模式判定保留。
- v3 副本 SHA-256 为 `920692b6702c0ac5882bc446db4c89f1840ee32cc06004ea536e86fe04ffd97f`；相对原 jar 只修改 ReflectionCache、PlayerWorldHelper、ControlModeHelper 三个 class，另增 McpGuiLookup。上表的 MCP SHA 仅描述此前元数据副本，不能代表当前 v3。
- 原实现对照、现代 Gui 非空/空值、旧根字段、原 null 输入异常、真实 PlayerWorldHelper 在假 Gui owner 上关闭屏幕的检查均通过 `-Xverify:all`。早期 v2 在真实世界把 LocalPlayer 当作 screen 的失败和第一次错误预期 null 输入不抛异常的测试失败均保留。

PID 22036、29460 均自动进入隔离世界 A。v3 的只读接口在无菜单时正确返回 `no screen`，并能读取玩家位置、维度、血量；世界元信息仍有 unknown/默认值，未声称整个 MCP 已兼容。PID 29460 从 20:36:17 运行，A 的 SERVER probe/consumer 均仅首次执行且 count=1，CLIENT entry 未出现。人工接管尚未观察到激活，因此没有驱动 A→B→A 或网络连接。

源路径确认：初始 CLIENT load/资源 reload 早于 WORLD 包发布；server 启动激活世界包只重载 SERVER，客户端没有登录后的重载接线。两个 NeoForge 实现的 server-stop 注释声称“下次进世界整体重载”，但原客户端未实现该动作。这是此次真实输入发现的产品缺口。Fabric 当前未挂载 WORLD 包，不能把它记录为同一自动切换已实现。

为修复准备重启：独立签名服务端通过 RCON 正常 stop；核实 PID 29460 创建时间后仅终止该 owned 客户端。后者是强制结束的兼容准备会话，不能作为正常退出或资源释放通过。前述失败/准备日志保留为 client-launch-1 至 5（首轮文件名为 client-launch.log）。

回归接线检查在未修复源码上运行一次，精确失败于缺少 LoggingIn listener，原 XML/log 保留。修复在共享 26.x 和 1.21.1 的 NeoForge CLIENT 注册处监听 LoggingIn：仅 memory connection 且 WORLD 包非空时调用现有事务 CLIENT reload；远程 configuration-phase 激活保持原路径。失败使用现有 NEKO-1017 并写入错误跟踪器。验证结果在下方继续记录；接线测试不能代替实际 CLIENT entry 证据。

修复后 `guardLint :1.21.1:build :26.1.2:build :26.2.0:build --console=plain` 通过（3m14s，67 tasks），四项 `tools/nekojs-ci-gates.py` 的 subsets / processor / declaration / nodes 均 pass。1625 个实际 main 类相对上一轮仅 NekoJSClient.class 改变，common 生产类不变。新客户端 PID 9932 的 client-launch-6.log 记录 20:52:09 Render thread 自动执行 A 的 CLIENT probe，activation=3/count=1，20:52:10 consumer 确认 world=A/count=1；SERVER probe/consumer 也已执行。未使用显式 CLIENT reload 命令，这是首次实际自动加载证据。

为检查手动接管入口，通过允许的 pause_game 打开暂停菜单；返回 setScreen(PauseScreen)，截图及只读 widget 枚举确认当前 screen 正确，但菜单没有 MCP 接管按钮。界面枚举的 label 仍为空，也未假设 widget id 可用。此问题继续在 MCP 测试副本调查；没有再次要求用户点击不存在的按钮，也没有调用 enter_control_mode 绕过。旧接管提示失效时用户回复“没有mc啊”，因为原测试进程已为上述产品修复而结束；已解释并在新客户端截图确认后更新状态。

该轮还有一项独立环境限制：JEI 30.1.0.12 的 NeoForgeGuiPlugin 引用已不存在的 ContainerScreenEvent.Render.Foreground，实际日志由 JEI PluginCaller 捕获 NoClassDefFoundError。游戏仍进入世界，未将该可选集成的失败记作通过，亦未把它误归因于本轮 NekoJS 登录接线。

## 待执行断言

先解决客户端控制问题并记录人工激活，再在同一进程完成 A→B→A。分别记录 SERVER/CLIENT 是否自动加载；如果需要显式 CLIENT reload，注明操作，不能写成自动切换通过。验证来源和私有计数重建、旧事件/timer 撤回与资源窗口。

随后连接上述独立服务器：首次不信任应拒绝；按实际拒绝身份仅授权本轮 loopback 来源；允许有效签名且 pin 公钥、CLIENT entry/tick/timer 执行；断线撤回后回调停止。保持签名而篡改内容必须拒绝，换同 keyId 的另一公钥必须被既有 pin 拒绝；恢复原签名和 key 后重连。原 key、有效包与失败日志均保留。真正到达网络事件和客户端日志之前，各项均为未运行。

真实图形行为、完整游戏/GraalMC 内存、维护者接受结论仍独立记录。此次 NMT 在测试客户端开启，不用于重载性能评分；显式 HighQoS 的通过也没有撤销默认性能 FAIL。
