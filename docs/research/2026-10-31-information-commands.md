# NekoJS 信息命令交付记录

## 范围与实施顺序

参考本机 D:/kubejs-2601，实现 /nekojs hand、list-tag、hotbar、inventory、browse。调研由两名 Luna xhigh 代理只读完成，产品实现由主代理负责。

1. 核对五条 KubeJS 命令的参数、权限、输出、复制交互与客户端边界。
2. 将现有 hand/inventory 简版替换为共享信息命令实现；NeoForge 1.21.1、26.x 与 Fabric 使用同一行为。
3. 对 Minecraft API 差异使用节点实现，不在业务代码增加版本判断；玩家提示加入中英文语言文件。
4. 增加参数、序列化和输出行为回归，执行五节点构建及相关门禁，记录实际通过与未执行项目。
5. 更新中英文命令文档，检查改动范围并本地提交；不发 PR、不推送。

## 调研结论

参考源是本机解压目录，不带 Git 元数据。本轮两名 Luna xhigh 分别调查 KubeJS 行为与 NekoJS 接入；均禁止改文件、构建或运行。主代理读取源码并以编译/运行校正报告，未把代理推测当成 API 事实。

- KubeJS `command/InformationCommands.java` 的 hand 展示物品、标签、命名空间、创造模式栏和方块/容器流体；hotbar/inventory 过滤空槽并整组复制。`command/KubeJSCommands.java` 的 list-tag 支持注册表/标签补全。其点击链接误用 list_tag；NekoJS 使用已注册的 list-tag。
- `command/KubeJSClientCommands.java` 的 browse 是客户端调用系统文件管理器打开 kubejs 根目录；NekoJS 对应本机 `NekoJSPaths.root()`，不是远程服务器目录。
- KubeJS 信息命令没有管理员门禁。NekoJS 保留既有服务端 `/nekojs` 的 op 2 门禁，避免放宽其他子命令；客户端 browse 无此限制。
- KubeJS 的组件物品字符串不是 NekoJS 当前输入合同。新增输出分为基础 ID/数量字符串和完整 ItemStack.CODEC JSON；后者包含组件修改/删除和自定义数据，但不承诺能经 1.21.1 的 Item.of 还原组件。创造栏复制原始 ID，不复制当前 NekoJS 不支持的 %tab 表达式。
- 1.21.1 与 26.x 的原生标识符参数、聊天点击/悬停构造、系统 Util 位置及 NeoForge 流体查询不同，由节点文件承接。Fabric 只读查询 FluidStorage.ITEM。服务端流不引用 BrowseCommand 或客户端类型。
- lookup / listTagIds 使用 HolderLookup.Provider 的跨版本合同；已通过五节点编译，不能仅因仓库没有其他调用就推断原版 API 不存在。

KubeJS 关键文件 SHA256：

- `command/InformationCommands.java`: `3e4c2ec67e8582777065190fa40092f404b968f3db84c00ce943915436fe8a93`
- `command/KubeJSCommands.java`: `f683be7b3b2a0c48000e1e82e125ac6c6f5d69076c118272e1f2092c5651921c`
- `command/KubeJSClientCommands.java`: `6adef01813f7e5792e9470f046b46335b37ca07735669d5f28412ea2b317ec2c`
- `core/ItemStackKJS.java`: `34d8ad70c7da00751d32a8735c39b2fb8e3d2b57ac336516c1bb9a1313935877`

## 验证结果

- 五节点 InformationCommandsTest：10个方法通过，无跳过，覆盖权限保留、原生命名空间参数、完整/不完整命令、错误标识符、原样复制与正确标签链接。
- 五节点 InformationItemExportTest：10个方法因裸 JVM 缺少完整物品组件注册表而跳过，不作为组件通过证据。
- `python bench/commands/run_smoke.py`：NeoForge 26.1.2 隔离开发服务端27条断言通过，正常停服，failure=null。真实命令验证组件往返、删除组件、数量、损伤、转义名字、槽位范围、顺序/重复、空物品栏、方块标签与水桶流体。原始日志 build/information-commands-runtime.txt，结构化报告见下。
- 实测先发现现代 Component.translatable 不能接收 Identifier/Path 对象。未知注册表红态保留在 build/information-commands-runtime-red.*，产品错误分支改为传字符串后通过。另一次失败来自无网络测试玩家的消息发送，修正测试玩家消息捕获后通过，没有为测试修改产品消息路径。
- 最终命令 `.\gradlew.bat :common:check :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build guardLint --offline --console=plain`：PASS。第一轮3m6s；收敛创造栏复制为原始ID后最终候选1m42s，104 tasks（43 executed / 61 up-to-date）。日志 build/information-commands-final-candidate.txt。
- 最终候选再次运行 `python bench/commands/run_smoke.py`：PASS，27条断言，24s，正常停服。
- `python tools/nekojs-ci-gates.py all --out build/information-commands-platform-gates.json`：PASS，4类检查、0失败；五节点 source-roots 各 exit 0。
- 六份正式普通/server JAR 检查：均无 InformationCommandSmoke 或两份 Information 测试类。记录 build/information-commands-artifact-check.json；最终源文件与 build/information-commands-source-manifest.json 匹配。
- 中英文22个新增语言键及占位符数量一致；文档包含全部五条指令；git diff --check 通过。没有 golden 改动。
- 未执行真实客户端点击/hover/系统剪贴板与文件管理器弹出；未声称这些交互已获人工验收。1.21.1 和 Fabric 的真实服务端本轮未运行，使用五节点编译与单元检查覆盖其接线。

## 验证工具与产物边界

bench/commands 的 informationSmoke source set 只由显式 init script 启用，不进入生产 JAR。驱动先移除旧报告，不将 Gradle 退出0独自当成功。正式构建不使用该 init script。最终交付前检查普通及server制品无 InformationCommandSmoke 类。

结构化实测报告：

```json
{
  "checks": [
    "browse is not registered on the server",
    "hand rejects console",
    "inventory rejects console",
    "hotbar rejects console",
    "registry completion",
    "tag link spelling",
    "tag completion",
    "nekojs list-tag nekojs:missing fails",
    "nekojs list-tag minecraft:item nekojs:missing fails",
    "codec round trip with count, custom name, damage, and removal",
    "component removal retained",
    "hand includes complete copyable JSON",
    "hand includes supported count string",
    "hand does not mutate held stack",
    "hotbar bounds and empty-slot filtering",
    "hotbar retains components",
    "hotbar retains order",
    "hotbar retains duplicate stacks",
    "inventory bounds and empty-slot filtering",
    "inventory retains components",
    "inventory retains order",
    "inventory retains duplicate stacks",
    "hand item and block tags",
    "hand block ID",
    "hand fluid container",
    "empty hand message has no fake item export",
    "empty inventory arrays"
  ],
  "failure": null
}
```


## 2026-10-31：1.21.1 用户仓库交付修正

用户反馈 1.21.1 缺少指令。核对确认功能提交 `7860c953` 只存在于 C 盘工作树，用户实际使用的 `D:/mcmodDemo/NekoJS-mult` 尚无 `InformationCommands`，其 1.21.1 注册入口也未接入。因此该仓库旧构建不含新增指令；本次修正为同步功能并重建，未更改权限或新增另一套实现。

同步使用 Stonecutter 正式切换流程：先将 D 盘仓库从活动 1.21.1 切到 26.1.2，确认完整功能补丁 `git apply --check` 通过后应用，再切回 1.21.1。补丁未包含 lite 或其他任务。同步前保存 4,342 个已跟踪文件的内容摘要，同步后排除本功能 25 个文件逐一比较：其余文件内容全部一致，活动节点仍是 1.21.1。未重置、提交或暂存 D 盘仓库已有改动。

验证在 D 盘实际仓库运行：

- `gradlew.bat :1.21.1:build --offline --console=plain`：通过，49 秒；包括节点测试和普通/服务端产物验证。
- `InformationCommandsTest`：2 条通过，零失败；`InformationItemExportTest`：2 条明确跳过，不能算作游戏内数据导出验证。
- 重新生成的普通 JAR 与 `-server.jar` 均检查到四条服务端命令常量、实际注册类调用、1.21.1 原生参数适配、客户端 browse 入口及双语文案；测试辅助类未进入制品。
- 本次未重新启动 1.21.1 游戏客户端；剪贴板和文件管理器交互仍未实测。前四条命令继续需要管理员权限，browse 仍为客户端本机操作。已运行的游戏需要重启才会加载新代码。

本机日志和摘要保存在 C 盘工作树的 `build/command-sync/`（不入库）。可用产物位于 `D:/mcmodDemo/NekoJS-mult/versions/1.21.1/build/libs/`；用户应根据客户端/服务端使用对应 JAR。源功能提交仍为 `7860c953`，本次没有推送或创建 PR。
