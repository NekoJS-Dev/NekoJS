# 移除 AI 定位 JSON

## 目标与实现

用户要求删除冗余的 `nekojs/config/ai-location.json`。取消运行时生成及离线读取，以 `ai.toml` 的 `[output].directory` 作为唯一的持久目录配置。

- 显式 `--ai-dir` 优先；否则只读已有 ai.toml 的目录字段。配置缺少字段时选择默认目录。
- ai.toml 缺失时保留默认导出、旧导出的既有发现顺序；配置存在但无效、越界或选中导出缺失时拒绝，不静默换根。
- 离线读取配置上限为 64 KiB，不创建配置、token 或监听器，不读取 token，不执行游戏操作。已有会话固定目录；修改目录后重启辅助进程。
- 运行时与离线工具共享输出字段校验，使用已有 NightConfig 3.8.3 TOML 解析器。独立 stdio JAR 新增打包现有解析库，保留多版本 JAR 元数据；没有升级依赖版本。按 [v3.8.3 上游许可](https://github.com/TheElectronWill/night-config/blob/v3.8.3/LICENSE) 保留 LGPL-3.0 文本、版本和来源说明。
- 旧 ai-location.json 不再具有含义。当前指定工作区的旧文件在验证后删除；不为自动清理旧文件增加新的运行时迁移逻辑。
- 更新英文内嵌手册、AGENTS/Skill 模板、CLI 帮助及双语 wiki。已有用户维护的指令文件继续遵循不覆盖规则，新版模板保存在版本化 bundle 中供比较。

## 验证结果

Gradle daemon 使用 Zulu 25，common 测试仍使用 Java 21。没有刷新 golden。

| 检查 | 结果 |
|---|---|
| Authoring 聚焦验证 | 原有五组共 59 项通过；后续补充的错误 output 表用例由最终完整检查覆盖 |
| `:common:check guardLint` | 2750 项，零失败／错误，4 跳过；隔离、插件消费者与源码门禁通过 |
| 五节点 `build` 与 platform gate | 1.21.1 为 476 项；两个 NeoForge 26.x 各 844 项；两个 Fabric 各 441 项；零失败／错误，分别跳过 33／85／85／39／39 项 |
| `npm.cmd run test:probe-native-jsx` | 两节点类型消费者通过 |
| `python tools/nekojs-ci-gates.py all` | 四类门禁零失败 |
| Wiki 和文档链接 | 30 个双语主题零问题；8 项工具测试通过；74 个内嵌本地链接通过 |
| JAR 审计 | 8 个 JAR 各 36 个 AI payload 与源及哈希一致，最终目录读取器字节码一致；独立工具包含解析器及许可资源，排除运行时配置初始化／HTTP 类 |
| 当前工作区 | 指定 26.1.2 run 目录的旧 ai-location.json 已删除；生产类已没有该文件的生成或读取引用 |
| 差异检查 | `git diff --check` 通过；保留此前 Probe、HUD 和 MixinJS 方案的工作区修改 |

初次完整检查中，新增 `output = 'not a table'` 负例发现 NightConfig 的嵌套字段读取会回退默认值。已在共享字段读取处加入 output 表类型验证，最终完整检查通过；没有删除负例或放宽检查。第一次日志保存在 `common/build/remove-ai-locator-first-build.log`。

最终日志：`common/build/remove-ai-locator-common.log`、`remove-ai-locator-build.log`、`remove-ai-locator-ci.log`。测试计数及产物哈希记录在 `common/build/remove-ai-locator-artifacts.json`。独立工具的真实进程测试覆盖默认、自定义及覆盖目录、无游戏 classpath、纯 MCP stdout、无文件写入、无 token 转发和无效目录拒绝。

未重跑完整 Probe 类型链（本轮未更改声明生成契约）；未执行游戏实机或 MCP 客户端人工验收。运行中的旧客户端及独立辅助进程需使用新构建重启；已有用户维护的 AGENTS 文件不会被强行覆盖。
