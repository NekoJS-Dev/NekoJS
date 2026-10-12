# 当前 d109：真实双客户端重载与原生像素验收

两位真实 NeoForge26.2 客户端并发运行，完成资源重载前后清晰/模糊切换、非法 CLIENT 候选失败保留及恢复，服务器普通重载收到两位真实客户端的 ACK 和激活报告并提交动态 generation3。三份实际官方 JAR 均为 d109 的精确制品，1592个编译类分别与当前 common/node build 输出一致。所有客户端和服务器正常退出0。

本轮绕开[已复现的跨 GLFW 进程激活故障](../ticket37-native-focus-d109/README.md)，没有修复或替换 GLFW/Sky，也没有加载会挂住退出的 HTTP bridge。客户端自己的 tick owner 调用原生 `Screenshot.grab(minecraft, false)`，没有跨线程 guest Consumer；资源重载调用实际 `Minecraft.reloadResourcePacks()`。这是游戏 API 语义验收，不是 Windows 输入替代实现；本轮没有跨客户端 Sky 操作，也没有声称实际按下 F3+T。

## 精确环境与结果

- 源码 `d10912ebbf24d9d5df75a908dc7a5529da09979a`；checkout `9d4280b74f3896137c408110e48c4002f2d1f703`，后续差异仅文档。
- NeoForge `26.2.0.75`、Oracle JDK `25.0.2`。官方 `nekojs-neoforge-26.2.0-1.1.0-preview3.jar` SHA256 `fde6c16ba1b6fbca43dfc87aff4bf9709c1925bcb2d72c307f2e73346922f092`。
- 专属 `build/ticket37-semantic-pair-d109`，新世界与两个合成离线账号，127.0.0.1:25985/RCON25986，沿用既有权限配置。不会操作用户游戏、world或未知进程。
- 两位客户端均真实 configuration catch-up generation2 后入服。普通服务器重载先实际观察到 AWAITING_ACK/一个 ACK，随后 COMMITTED/两 ACK/两 `activation-report=activated`。这没有用模拟网络回复、修改 wire 或略过等待来产生通过结果。
- 当前 CLIENT post-effect 首 HUD声明 generations1/2/3/4/5 均存在；此计数不等同于 ScriptManager 候选编号，也不等同于动态注册 generation。
- 六组实机清晰/模糊画面对照，分别为 A/B 的声明 generations3、4、5。原图854×480，固定墙体区域 y180/x170..519 相邻 RGB最大变化：A清晰594/594/547，对应模糊19/19/19；B清晰473，对应模糊15。该数值用于验证已经逐张观察的真实边缘变化，没有替换总体视觉门禁。
- 两端均写入非法专属 CLIENT fixture 后实际资源重载失败，`NEKO-1008` 留在原始日志；截图仍为原来的 blur、声明 generation4/active=true。只移走该专属 fixture，下一次资源重载恢复至声明 generation5，清晰/模糊切换再次有实际像素证据。
- 普通服务器重载后真实 `give` 在两端都识别并给予 `ticket37_b7:proof_item`；客户端截图阶段没有覆盖该物品的模型验收。
- A tick-owner stop0.703秒、B0.797秒，均直接0；服务器 RCON/direct0。没有强制清理，本轮没有 hs_err 或 Graal 多线程访问诊断。两个端口释放。

## 保留的失败与限制

最初 A generation1 的两张原生截图带暂停菜单及 Minecraft 内置菜单模糊，**不能**标为 post-effect清晰/模糊通过；B最初画面对照正常。A的错误截图和原始 fixture全部保留。测试 fixture在活动 tick 上仅对这两个专属实例设置 `pauseOnLostFocus=false` 并调用既有 `McClientCompat.showScreen(null)` 退出菜单，generation3重新观察后才计入有效对照。没有在候选准备阶段改用户设置，也没有更改用户 profile或安全选项。

最初一条临时 shell中的 generation-await 检查因引号错误而 SyntaxError，没有取得结论；随后新独立 helper按原始 JSON HUD记录等待，generation4/5两端实际通过，generation3由完整日志与对应截图状态验证。没有用失败等待来声称结果。fixture使用十个客户端 tick 后截图，不是第一像素帧验收。

缺失旧 STARTUP fixture 的模型/声音资源警告原样保留，本轮对照墙使用内建方块。服务器 reload命令响应的 ScriptManager generation2，动态事务实际 generation3；两种计数不能混用。现有脚本/API/资源契约、HostAccess/ClassFilter、manual生产器没有改变。

## 原始证据

[运行结果](runtime-summary.json)、[清单](manifest.json)、[完整日志/fixture/18张原生截图](semantic-pair-evidence.zip)、[独立验证器](verify.py)。压缩包60条、3699678字节，SHA256 `a4bc2b6d42333f156c27a2f9376ac0a7e39232ca37df93aaa0c9de32974d6b28`。独立验证器重读全部 entry/hash、原始 ACK/状态、正常退出和PNG固定区域数值；维护者可以查看原图，不需要把代理观察当作自己的人工验收。

A资源重载后的[清晰画面](a-clear.png)/[模糊画面](a-vanilla.png)，B资源重载后的[清晰画面](b-clear.png)/[模糊画面](b-vanilla.png)。没有裁剪或手工改图。完整原图及最初失败图都在压缩包。

```powershell
# Already executed with the bundled Python; helpers are frozen in the ZIP.
python build/ticket37-config-sync-repair/prepare-semantic-pair-d109.py
python build/ticket37-config-sync-repair/run-semantic-pair-d109.py server
python build/ticket37-config-sync-repair/run-semantic-pair-d109.py A
python build/ticket37-config-sync-repair/run-semantic-pair-d109.py B
python build/ticket37-config-sync-repair/step-semantic-pair-d109.py A clear-corrected
python build/ticket37-config-sync-repair/await-semantic-generation.py 4
python docs/architecture-refactor/evidence/ticket37-semantic-pair-d109/verify.py
```

准备器和runner都拒绝覆盖旧目录/所有权记录；重新执行必须新建独占 fixture。RCON密码、world、启动参数、制品、原生库和无关桌面不入库。

## 尚未完成

当前双端 catch-up/ACK/commit及有限视觉 **PASS**；最初 A菜单画面 **FAIL**，后续修正只解决测试 fixture的失焦暂停。当前并发 abort、下载中途入服、第一像素、其他节点完整客户端领域、磁盘失败、正式性能及全量 IDE仍需各自验收，不能由本轮局部通过推出整体通过。维护者实际结论未记录，发布未授权；自动续跑暂停与工具权限字段问题仍未解决。整体未完成，技术工作继续。
