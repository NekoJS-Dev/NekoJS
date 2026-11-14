# Issue #69：26.1.2 NeoForge 游戏内验收

## 环境与启动

- Minecraft 26.1.2，NeoForge 26.1.2.71，GraalMC 25.5.0，NekoJS 1.1.0-preview3，Zulu Java 25。
- 使用当前源码的 Gradle runClient，不是另一个启动器或旧 NekoJS jar；启动日志明确加载本工作区 node/common 的 classes/resources。
- [独立启动配置](issue-69-client.init.gradle) 将目录隔离到 build/issue69-live-26.1.2-neoforge，不改原有 versions 运行目录或存档。
- 实际配置 scriptMemberValidation=true；保留默认授权和沙箱，没有通过关闭检查让正例通过。
- 第二次客户端仍运行，不能将运行中的 runClient 记成已经退出的 Gradle BUILD SUCCESSFUL。

~~~powershell
./gradlew.bat --no-daemon --init-script plan/issue-69-client.init.gradle :26.1.2:runClient --console=plain
# Optional: load an existing world from this isolated directory.
./gradlew.bat --no-daemon --init-script plan/issue-69-client.init.gradle -Pissue69World=新的世界 :26.1.2:runClient --console=plain
~~~

自动化桥接仅用于本测试目录，使用[指定版本 0.4.2 的 NeoForge 资产](https://github.com/langyo/minecraft-mod-mcp/releases/download/v0.4.2/minecraft-mcp-26.1.2-neoforge-v0.4.2.jar)。GitHub API 限流后改为该公开固定 release 下载，没有改启动目标版本。桥接工具的版本字段为 unknown，所以版本以 FML 日志为准。

## 聚焦正例：已执行通过

[复用脚本与说明](game-tests/issue-69/README.md) 使用不依赖 Minecraft 物品组件绑定的 Text API；JS/TS 自检结果，Python 打印实际值供外部核对。正例部署到 CLIENT 与 SERVER，均不修改世界、玩家或持久状态。

原始证据是本地运行日志 `build/issue69-live-26.1.2-neoforge/logs/latest.log`（`.gitignore` 的 `*.log` 规则不纳入版本库）；关键行摘录如下，时间 2026-11-14 Asia/Shanghai：

~~~text
[23:32:57.269] [Server thread/INFO] [NekoJS/]: [server] ISSUE69_JS_PASS line=end=block textEmpty=false
[23:32:57.270] [Server thread/INFO] [NekoJS/]: [server] ISSUE69_PY_RESULT 7
[23:32:57.270] [Server thread/INFO] [NekoJS/]: [server] ISSUE69_PY_TEXT_EMPTY False
[23:32:57.278] [Server thread/INFO] [NekoJS/]: [server] ISSUE69_TS_PASS textEmpty=false
[23:32:57.301] [CHAT] NekoJS server reload committed: generation=2 phase=COMMIT
~~~

| 阶段 | 实际结果与证据 |
| --- | --- |
| CLIENT 初载，23:31:59 | JS 三种注释链 PASS，TS PASS，Python result=7、textEmpty=False 并执行到末尾；日志 60–68 行 |
| CLIENT 资源重载，23:32:04 | 同样结果再次出现；日志 118–127 行及 162 行 reload finished |
| SERVER 世界数据加载，23:32:05 | 三种语言均得到预期结果；日志 163–175 行 |
| CLIENT 后续重载，23:32:43 | 三种语言再次得到预期结果；日志 335–349 行 |
| SERVER 后续重载，23:32:57 | 三种语言再次得到预期结果；日志 350–365 行，明确 generation=2 phase=COMMIT |

该正例日志中未出现 script-diagnostic，未出现 issue69TypeOnlyMethod、issue69_python_entry 的未知标识符诊断。重复执行证明实际重载，不单凭输出宣称缓存命中次数；缓存计数由 common 回归测试验证。

## 故意拼错成员的负例

模板为 [默认禁用负例](game-tests/issue-69/issue69-negative.js.disabled)，部署副本暂放 SERVER。不可达分支里的 Item.issue69MissingMember 应产生静态诊断，同时运行到 ISSUE69_NEGATIVE_EXECUTED，不应真的调用该成员。

已执行通过：23:36:36 的 SERVER 重载产生 Item.issue69MissingMember 静态诊断，位置为第 4 行第 10 列；随后输出 ISSUE69_NEGATIVE_EXECUTED 并提交 generation=3 phase=COMMIT。两阶段报告复用同一诊断 ID，游戏聊天显示 1 条唯一错误，未出现不可达调用的执行异常。原始证据同样在本地 latest.log（不纳入版本库），摘录：

~~~text
[23:36:36.113] [server] Script binding-preflight chain-member issue69MissingMember callback exception:
   原因: no member 'issue69MissingMember' on ItemJS/Item
 > 4 |     Item.issue69MissingMember();
[23:36:36.114] [server] ISSUE69_NEGATIVE_LOAD
[23:36:36.120] [server] ISSUE69_NEGATIVE_EXECUTED unreachable-call-not-run
[23:36:36.152] [CHAT] [NekoJS] 警告：引擎目前存在 1 个脚本运行错误。
[23:36:36.153] [CHAT] NekoJS server reload committed: generation=3 phase=COMMIT
~~~

负例部署副本已核对与模板内容相同后删除；等待最后一次 SERVER 重载确认旧错误消失。桥接 execute_command 返回 no command method found，自动聊天构造失败；type_text 明确返回 not in control mode，故由用户在普通聊天里协助执行，没有绕过控制模式权限。

## 实际发现的独立问题与限制

1. 最初 Item.of 用例在 CLIENT 初始化和 SERVER 世界数据加载的候选阶段均抛 Components not bound yet。不能说换到 SERVER 就自动解决。原始脚本保留为默认禁用探针；该 ItemStack 生命周期/组件绑定问题未在本轮修复。
2. 原 Python assert 会生成异常类数组前导，出现未知 forEach 静态误报。保留原探针，不将其算作通过，也没有把 forEach 加入合法全局来屏蔽。聚焦 Python 用例不触发该独立辅助路径。
3. 第一次客户端运行以 -1073741819 / 0xC0000005 退出，终端已观察到原生进程失败；没有足够证据归因给 #69、JVM 或桥接截图。未取得成功截图。
4. 桥接模组资源包还有 min_format/max_format 元数据警告；不把该警告或界面自动化兼容性问题伪装成 #69 脚本通过证据。

首轮错误证据为本地 latest.log 的历史副本（体积较大、含运行时路径，按 *.log 规则不纳入版本库）；其现象已在上文逐条摘录，可复现命令与失败栈见第 1、2 条的 stub 与输出记录。聚焦脚本通过不等于上述 ItemStack/assert/原生崩溃问题已解决，更不等于全部游戏功能或所有平台通过。

## 辅助检查

JS 的 node --check 通过；对 TS 尝试 node --check（包括 strip-types 标志）仍在 interface 处失败，未计作通过，也未把命令链最后退出 0 当成 TS 检查通过。TS 的有效验证证据来自真实游戏中 NekoJS 自己的前端、运行结果和重载记录，不用另一个解析入口替代。git diff --check 通过；本轮没有追加生产修改。

## 范围与人工结论

本轮只新增验收脚本、独立 Gradle 初始化配置和证据；未改生产实现、延期功能或已有配置合同。真实维护者最终接受结论仍未取得；负例及清理确认另行补记。
