<!-- wiki-page: ai-support; locale: cn -->

> **中文** · [English](ai-support_us)

<a id="wiki-section-1"></a>
# AI 辅助脚本编写

直接让 Claude Code、Cursor、GitHub Copilot 写整合包脚本时，AI 往往会瞎编不存在的 API，或者把别的版本的写法张冠李戴。

NekoJS 通过两套机制解决这个问题：

1. **自动导出参考文档（默认开启）**：启动游戏时，把当前版本的官方手册、标准示例和 AI 规则导出到游戏目录（默认在 `.github/neko_ai/`），让 AI 直接阅读。
2. **本地 MCP 查询与控制服务（默认关闭）**：让 AI 直接查询**你当前整合包里真实存在的事件、方法和参数类型**，甚至在你的允许下帮你查脚本报错、自动执行 `reload`。

> NekoJS 本身不包含大模型，也不会偷改你电脑上的编辑器配置——你需要手动把导出的规则或 MCP 地址配进自己的 AI 工具里。

<a id="wiki-section-2"></a>
## 1. 三步接入流程

想让 AI 准确写出能跑的脚本，按这三步走：

1. **生成当前实例的 API 快照**：进游戏执行一次 `/nekojs probe`（每次新装模组或改完启动注册脚本后，都要重新执行一次；普通的 `/nekojs reload` 不会刷新快照）。
2. **把规则文件喂给 AI**：在 AI 工具里引用 `.github/neko_ai/AGENTS.md`，或者开启配置让编辑器自动识别。
3. **（推荐）连上 MCP 服务**：任选下文“游戏内直连”或“离线独立程序”一种方式，让 AI 能按需检索 API 和报错。

<a id="wiki-section-3"></a>
## 2. 导出的文档与目录结构

启动游戏后，游戏根目录下会自动生成以下与 AI 相关的文件：

| 路径 | 用途说明 |
|---|---|
| `nekojs/config/ai.toml` | AI 功能总配置文件（修改后需**重启游戏**生效） |
| `nekojs/config/ai.token` | 游戏内 HTTP/MCP 服务的访问令牌（仅开启服务后生成，相当于密码） |
| `.github/neko_ai/AGENTS.md` | 给 AI 看的总入口说明（告诉 AI 怎么写 NekoJS 脚本、去哪查资料） |
| `.github/neko_ai/current.json` | 指向当前生效文档包的索引（内部使用相对路径） |
| `.github/neko_ai/bundled/<bundleId>/` | 官方手册、15 个标准可运行示例与 Skill 本体（**请勿手动修改**） |
| `.github/neko_ai/environment.json` | 当前游戏版本、加载器等环境元数据 |
| `.github/neko_ai/connection.json` | 游戏内 MCP 服务的监听状态与地址（不含密钥） |
| `.github/neko_ai/snapshots/` | 执行 `/nekojs probe` 后生成的当前整合包 API 快照 |

**使用提示：**
- **让编辑器自动识别 Agent 配置**：默认导出的 `AGENTS.md` 和 `agents/` 放在 `.github/neko_ai/` 下，AI 工具不一定会自动扫描。如果你用 VS Code / Copilot，可以在 `ai.toml` 里设 `[output].editor_agents = true`，把规则同步输出到标准的 `.github/agents/`。
- **不要手改 `bundled/` 目录**：如果里面的内置文档被改动，NekoJS 更新时会拒绝覆盖并报错；万一改乱了，直接删掉旧包重启游戏即可重新导出。
- **内置示例怎么用**：文档包里的 `handbook/scripting-examples.md` 和 `examples/` 提供了 15 个覆盖登录欢迎、改配方、标签、注册物品、JSX 界面、断言测试等场景的标准脚本。想体验的话，挑一个复制到测试存档对应的 `nekojs/<类型>_scripts/authoring-examples/` 下试跑即可（一次建议只试一个场景）。

<a id="wiki-section-4"></a>
## 3. 连接 MCP 查询服务（二选一）

连上 MCP 后，AI 会多出 `nekojs_context`、`nekojs_resolve`、`nekojs_members`、`nekojs_query`、`nekojs_batch` 等只读检索工具，写代码前能自己查清每个方法的参数重载。你可以根据习惯选一种连接方式：

<a id="wiki-section-5"></a>
### 方式一：游戏运行时直连（推荐边开游戏边写脚本）

直接连接游戏内置的 MCP 服务，无需额外部署程序，还能读取实时报错和触发重载。

1. 打开 `nekojs/config/ai.toml`，把 `[mcp]` 下的 `enabled` 改为 `true`：
   ```toml
   [mcp]
   enabled = true
   ```
2. **重启游戏**并保持运行。打开 `.github/neko_ai/connection.json`，确认 `"listening": true`，记下 `mcp` 地址（默认为 `http://127.0.0.1:8767/mcp`）。
3. 打开 `nekojs/config/ai.token`，复制里面的密钥字符串。
4. 在你的 AI 工具中添加 **Streamable HTTP** 类型的 MCP 服务器：

```json
{
  "mcpServers": {
    "nekojs": {
      "url": "http://127.0.0.1:8767/mcp",
      "headers": {
        "Authorization": "Bearer 替换成ai.token里的内容"
      }
    }
  }
}
```

> 连上后在 AI 工具的 MCP 列表里看到 `nekojs_context` 等工具即代表成功。关闭游戏后该服务会随之停止。

<a id="wiki-section-6"></a>
### 方式二：游戏关闭时离线使用（独立 stdio 程序）

不想一直开着沉重的整合包客户端？你可以用独立的 `authoring-stdio` 辅助包直接读取上次 `/nekojs probe` 留下的快照。它由 AI 工具按需拉起，不占端口，也不需要配密钥。

**前置条件：**
- 电脑上装有 **Java 21 或更高版本**。
- 下载与当前 NekoJS 版本一致的 `nekojs-<版本>-authoring-stdio.jar`（注意：**这不是模组**，千万别放进 `mods/` 文件夹！如果是从源码构建，运行 `:common:authoringStdioJar` 后在 `common/build/libs/` 下获取）。
- 之前至少启动过一次游戏，并成功跑过一次 `/nekojs probe`。

在 AI 工具的 `mcpServers` 里填入以下配置（把两处路径换成你的实际绝对路径）：

```json
{
  "mcpServers": {
    "nekojs": {
      "command": "java",
      "args": [
        "-jar",
        "D:/Tools/nekojs-1.1.0-preview3-authoring-stdio.jar",
        "--game-dir",
        "D:/Games/MyModpackInstance"
      ]
    }
  }
}
```

**避坑细节：**
- `--game-dir` 填的是**包含 `nekojs/` 文件夹的那个游戏根目录**，既不是 `nekojs/` 本身，也不是 `saves/` 存档目录。Windows 路径建议统一用正斜杠 `/`，省去转义麻烦。
- 如果你的 AI 工具找不到 `java` 命令，把 `"command"` 改成 Java 21 的 `java.exe` 完整绝对路径。
- 离线模式读的是**上一次导出时的静态快照**。如果你后来加了新模组、改了启动注册脚本，记得进游戏重跑一次 `/nekojs probe`；如果改过 `ai.toml` 的导出目录（或手动传了 `--ai-dir <相对路径>`），重启一下 AI 工具里的 MCP 连接即可。

<a id="wiki-live-results"></a>
<a id="wiki-section-7"></a>
## 4. 让 AI 读取实时报错与执行重载

当使用**方式一（游戏内直连 MCP）**时，除了查 API，AI 还能直接查看游戏里的脚本报错，甚至帮你触发 `reload` 验证代码（离线 stdio 模式不支持以下功能）。

<a id="wiki-section-8"></a>
### 查看实时脚本报错（默认可用）

只要连上了游戏内 MCP，无需额外开关，AI 就能使用两个只读诊断工具：
- `nekojs_errors`：直接拉取当前游戏里的脚本报错列表（精确到报错文件、行列号与错误摘要）。写完脚本后你可以直接对 AI 说“帮我看看游戏里有没有报错”。
- `nekojs_operation_status`：查询某次重载或命令操作的执行进展。
- 此外，`nekojs_context` 返回的 `testRunReport` 字段里也会附带最近一次 `/nekojs test` 的断言通过情况（若没有任何断言执行，`overallPassed` 为 `null`，不视为通过）。

<a id="wiki-live-controls"></a>
<a id="wiki-section-9"></a>
### 开启实时控制：让 AI 自动 reload 或跑命令（默认关闭）

如果你希望 AI 改完文件后自己执行 `/nekojs reload` 或指定的测试命令，可以在 `nekojs/config/ai.toml` 的 `[control]` 段按需开启：

> ⚠️ **安全提醒**：AI 触发的命令和重载会真实改动你当前打开的存档！建议只在**测试存档**或提前备份后开启。

```toml
[mcp]
enabled = true

[control]
reload = true                     # 允许 AI 重载 server / client / test 脚本
commands = true                   # 允许 AI 执行白名单内的服务端命令
command_allowlist = ["list"]      # 命令白名单（按第一个词精确匹配）
```

修改后**重启游戏**，AI 工具里就会多出 `nekojs_reload_scripts` 和 `nekojs_execute_game_command` 两个操作工具。使用时请留意以下边界：

- **重载限制**：需要已进入单人世界或服务端正在运行（`client` 重载还要求有客户端环境）。不支持重载 `startup` 启动脚本（必须重启游戏），也不支持单文件重载。`server` 重载会自动重刷配方，但如果你改了原版数据包标签（Tag），仍需手动跑一次原版 `/reload`。
- **命令白名单规则**：按命令开头的第一个词精确匹配（例如 `list` 和 `minecraft:list` 视为两个不同的词），以 **OP 权限等级 2** 执行（无玩家实体上下文，依赖“当前玩家位置/视角”的命令会失败）。**注意：白名单不是安全沙箱**——如果你把 `execute` 放进白名单，就等于允许 AI 借它执行任意子命令！请只放真正需要的安全命令，用完及时关掉。
- **操作串行与超时**：同一时刻只允许跑一个控制操作。超时参数 `timeoutMs`（1～8000 毫秒，默认 5000）超时**不代表操作没跑**，可能只是游戏还在卡顿加载；此时不要盲目重试，应先通过 `nekojs_operation_status` 或进游戏确认结果。

<a id="wiki-section-10"></a>
## 5. 配置文件与常见状态码速查

<a id="wiki-section-11"></a>
### `nekojs/config/ai.toml` 完整默认配置

```toml
[output]
directory = ".github/neko_ai"      # 导出目录（必须是游戏目录内的相对路径；改路径不会自动清理旧目录）
editor_agents = false             # 设为 true 时额外向 .github/agents/ 导出编辑器规则

[documentation]
enabled = true                    # 是否导出手册、示例与 Skill
agents = "create-if-missing"      # 生成 AGENTS.md 的策略："create-if-missing" 或 "off"

[api]
enabled = true                    # 是否在 /nekojs probe 时生成供 AI 检索的 API 快照

[http]
enabled = false                   # 是否开启纯 HTTP JSON 接口（供自定义脚本/工具调用，与 MCP 共用端口）

[mcp]
enabled = false                   # 是否开启游戏内 MCP 服务
host = "127.0.0.1"                # 固定仅限本机回环地址，不可改为公网监听
port = 8767                       # 监听端口（1–65535；若端口被占用或配置写错，日志会报 NEKO-1019，不影响正常游玩）

[control]
commands = false
reload = false
command_allowlist = []
```

<a id="wiki-section-12"></a>
### 常见报错与拒绝码排查

| 状态码 / 提示 | 含义与原因 | 解决办法 |
|---|---|---|
| `INDEX_NOT_READY` / `not-ready` | 还没有生成过 API 快照 | 进游戏成功执行一次 `/nekojs probe` |
| `INDEX_DISABLED` / `disabled` | 关闭了 API 快照功能 | 把 `ai.toml` 里的 `[api].enabled` 改回 `true`，重启游戏并重新 probe |
| `INDEX_UNAVAILABLE` / `unavailable` | 快照文件损坏，或游戏环境已变更 | 重新进游戏跑一次 `/nekojs probe`（若用离线 stdio，再重启该辅助程序） |
| `SCOPE_CONFLICT` / `SNAPSHOT_EXPIRED` | AI 缓存了旧的实例 ID 或过期快照 | 让 AI 重新调用一次 `nekojs_context` 刷新上下文 |
| `RESPONSE_TOO_LARGE` | 单次查询结果超过 64 KiB 上限 | 缩小查询范围，或让 AI 直接读取本地生成的 `.d.ts` 声明文件 |
| `CONTROL_DISABLED` | 未开启对应的实时控制开关 | 检查 `ai.toml` 中 `[control]` 下的 `reload` 或 `commands`，修改后重启游戏 |
| `COMMAND_NOT_ALLOWED` | 请求执行的命令不在白名单内 | 将需要的命令首词加入 `command_allowlist` 并重启游戏 |
| `CONTROL_BUSY` | 上一个实时操作还没跑完 | 稍等片刻，或用 `nekojs_operation_status` 查看进度 |
| `LIVE_RUNTIME_REQUIRED` | 当前没有正在运行的世界/服务端 | 先进入存档世界（注：离线 stdio 模式永远返回此码） |
| `RELOAD_UNAVAILABLE` / `RESTART_REQUIRED` | 专用服尝试重载客户端，或尝试重载 `startup` | `client` 只能在客户端重载；`startup` 脚本改动必须重启游戏 |
| `RELOAD_POSTPROCESSING_FAILED` | 脚本本身已生效，但配方等后处理出错 | 注意此时新脚本已提交（未回滚），通过 `/nekojs error` 检查具体的配方错误 |
| `TEST_ASSERTIONS_FAILED` | `/nekojs test` 已跑完，但有测试断言没过 | 查看测试报告修复未通过的断言 |

> **查不到某个方法？** 部分手写声明、复杂继承成员可能未被快照完全索引（返回结果的 `coverage` 会标记未完全覆盖）。因此**检索为空不绝对等于 API 不存在**，遇到这种情况可以让 AI 直接去翻脚本目录下的 `.d.ts` 类型声明文件。

<a id="wiki-section-13"></a>
## 6. 安全须知与协议开发参考

- **严禁暴露到公网**：服务仅设计给本机 `127.0.0.1` 使用，没有 TLS 加密与多用户鉴权，千万不要用内网穿透、端口转发或反向代理把它暴露出去。
- **保管好 `ai.token`**：任何能读到 `nekojs/config/ai.token` 的本机程序都能调用你的 MCP 接口。请把它加入 `.gitignore`，切勿发进群聊、截图或贴进公开仓库。
- **二次开发参考**：如果你正在自己编写对接 NekoJS 的第三方编辑器插件、CLI 工具或调试 MCP/HTTP 底层报文，请查阅专门面向工具开发者的 **[AI 查询协议与底层接口参考](ai-support-protocol_cn)**。

<!-- wiki-nav -->

---

[上一篇: 事件参考](event-reference_cn) · [目录](Home) · [下一篇: 命令](commands_cn)
