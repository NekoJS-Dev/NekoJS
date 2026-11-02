# 信息命令实机冒烟

`python bench/commands/run_smoke.py` 运行 NeoForge 26.1.2 的隔离开发服务端，验证真实注册表和命令树。需要本项目现有 Gradle/JDK 25 及已缓存依赖；使用固定测试端口 25943。

测试通过专用 `informationSmoke` source set 加载 Java 断言，不加入生产 `main` 或 JAR。游戏目录是 `versions/26.1.2/build/information-command-smoke/game`；脚本只配置该专用目录，不修改普通 run 或用户世界。测试结束调用服务器正常停服、保存世界；不会强杀进程。

检查覆盖：

- 三条玩家命令拒绝控制台；服务端不注册 browse。
- 注册表/标签补全，标签点击命令，未知注册表/标签失败。
- ItemStack.CODEC 真正往返，保留数量、带引号/反斜杠/中文的名字、损伤和移除组件。
- 使用无网络连接的测试玩家，捕获实际命令聊天输出，验证 hand、hotbar、inventory 的复制数据、槽位边界、顺序、重复项、装备/副手排除及空背包。
- 方块/物品标签和水桶流体查询；检查手持栈未变化。

驱动启动前移除旧报告，只接受本轮生成且 failure=null 的报告和至少44条完成断言。Gradle 退出0不能单独算通过；断言失败后服务端仍正常停服，驱动必须返回非零。

原始日志：`build/information-commands-runtime.txt`。结构化报告：测试游戏目录的 `information-command-smoke.json`。第一次实测发现未知注册表错误提示向现代 Component 传入 Identifier 对象导致异常；修复为字符串后继续测试，保留 `build/information-commands-runtime-red.*` 作为本地红态记录。

此测试不覆盖客户端实际点击、系统剪贴板、hover 绘制或文件管理器弹出。聊天组件的点击/悬停事件与参数解析另由五节点 `InformationCommandsTest` 检查。普通 JVM 无完整物品注册表时，`InformationItemExportTest` 会明确跳过；其数据保真合同由这里的加载器实测补齐。


组件字符串补充验收：默认 `python bench/commands/run_smoke.py` 验证 26.1.2；`python bench/commands/run_smoke.py --node 1.21.1` 使用独立旧节点夹具和游戏目录，顺序执行以复用固定端口。包含通过 JavaScript 的 `Item.of`、ItemStack 字符串/对象适配器、数量覆盖、名称转义、损伤、组件删除、依赖动态附魔注册表的组件往返，以及未知/重复/错误组件、无效数量、未闭合括号和多余尾部的拒绝。复制数组的元素也逐一通过工厂重建。客户端的实际剪贴板操作仍不在该服务端测试范围内。
