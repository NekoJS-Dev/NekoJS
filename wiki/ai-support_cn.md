<!-- wiki-page: ai-support; locale: cn -->

> **中文** · [English](ai-support_us)

<a id="wiki-section-1"></a>
# AI 辅助脚本编写

NekoJS 能让 Claude、Copilot、Cursor 这类 AI 编程工具更准确地帮你写脚本。它做两件事：

- **导出文档**：把 NekoJS 的英文手册、示例和 AI 指令文件放进游戏目录，AI 可以直接读。默认开启。
- **提供查询服务（MCP）**：AI 可以查到你这个实例里真实存在的事件、方法和参数，而不是凭记忆猜。默认关闭。

NekoJS 本身不是 AI 模型，也不会自动修改你的 AI 工具配置。

推荐流程：

1. 让 AI 读导出的文档。
2. 在游戏里执行 `/nekojs probe`，生成 API 快照。
3. 选一种方式把 AI 工具连上查询服务。
4. AI 写完脚本后，你在游戏里 reload 并确认效果。

<a id="wiki-section-2"></a>
## 导出的文件

文档默认导出到游戏目录下的 `.github/neko_ai/`。常用文件：

| 路径 | 内容 |
|---|---|
| `nekojs/config/ai.toml` | AI 功能的配置 |
| `.github/neko_ai/current.json` | 指向当前文档包；里面的路径相对于 `.github/neko_ai/` |
| `.github/neko_ai/bundled/<bundleId>/` | 文档包本体：手册、示例、Skill。不要手动修改 |
| `.github/neko_ai/AGENTS.md` | 给 AI 的入口说明 |
| `.github/neko_ai/environment.json` | 当前实例的环境信息 |
| `.github/neko_ai/connection.json` | 查询服务的地址和状态（不含密钥） |
| `.github/neko_ai/snapshots/` | `/nekojs probe` 生成的 API 快照 |
| `nekojs/config/ai.token` | 查询服务的访问密钥，开启服务后才会生成 |

几点说明：

- `AGENTS.md` 不会被 AI 工具自动读取。你需要在 AI 工具里指定它，或者把需要的内容合并进你自己的指令文件。
- `agents/` 目录也不会被 VS Code 自动发现。想写到标准的 `.github/agents/`，在配置里设 `[output].editor_agents = true`。
- 想换导出目录，改 `[output].directory`。它必须是游戏目录内的相对路径。换目录不会移动或删除旧文件。
- 文档包被修改过的话，NekoJS 不会覆盖它，而是报错。需要更新时删掉旧包让它重新导出。

<a id="wiki-section-3"></a>
## 生成 API 快照

文档只是说明书。想让 AI 查到**你这个实例**里具体有哪些方法，需要先在游戏里执行：

```text
/nekojs probe
```

它会生成代码补全用的类型声明，同时生成 AI 查询用的快照。加了模组或改了注册内容之后，再执行一次。普通的 `/nekojs reload` 不会刷新快照。详见 [Probe 类型生成](probe-type-generation_cn)。

<a id="wiki-section-4"></a>
## 方式一：游戏运行时连接

游戏开着的时候，直接连游戏内置的查询服务，不用另外安装东西。

1. 打开 `nekojs/config/ai.toml`，在 `[mcp]` 下设置 `enabled = true`。
2. **重启游戏**并保持运行。打开 `.github/neko_ai/connection.json`，确认 `listening` 是 `true`，`mcp` 一般是 `http://127.0.0.1:8767/mcp`。
3. 在 AI 工具里添加一个 MCP 服务器，类型选 Streamable HTTP，填入上面的地址，再加一个请求头 `Authorization: Bearer <密钥>`。密钥是 `nekojs/config/ai.token` 文件的内容。

如果你的 AI 工具用 `mcpServers` 配置文件，可以这样写：

```json
{
  "mcpServers": {
    "nekojs": {
      "url": "http://127.0.0.1:8767/mcp",
      "headers": {"Authorization": "Bearer <token>"}
    }
  }
}
```

不同 AI 工具的配置方式不一样，有的是在设置界面里填。连上之后，工具列表里应该能看到 `nekojs_context` 等工具。游戏关闭后服务也随之停止。

<a id="wiki-section-5"></a>
## 方式二：游戏关闭时也能用

独立的 stdio 辅助程序读取游戏上次导出的文档和快照，游戏关着也能用。它由 AI 工具自己启动，不需要开端口，也不需要密钥。

准备：

- **Java 21 或更高版本**。
- 和你的 NekoJS 版本对应的 `nekojs-1.1.0-preview3-authoring-stdio.jar`。它不是模组，不要放进 `mods/`。从源码构建时由 `:common:authoringStdioJar` 生成，输出到 `common/build/libs/`。
- 至少启动过一次游戏，并成功执行过 `/nekojs probe`。

启动命令：

```sh
java -jar /absolute/path/nekojs-1.1.0-preview3-authoring-stdio.jar --game-dir /absolute/game/directory
```

在 AI 工具的 `mcpServers` 配置里这样写：

```json
{
  "mcpServers": {
    "nekojs": {
      "command": "java",
      "args": ["-jar", "/absolute/path/nekojs-1.1.0-preview3-authoring-stdio.jar", "--game-dir", "/absolute/game/directory"]
    }
  }
}
```

替换两个路径：

- `--game-dir` 是**包含 `nekojs/` 文件夹的游戏目录**，不是 `nekojs/` 本身，也不是存档目录。
- 建议都用绝对路径。Windows 上可以写成 `D:/Games/Instance`，省得在 JSON 里转义反斜杠。
- AI 工具找不到 `java` 时，把 `command` 换成 Java 21 的完整路径。

它查到的是**上次导出时**的状态。改了模组或脚本后，要重新进游戏执行 `/nekojs probe`；如果改了 `ai.toml` 的导出目录或游戏环境变了，还要重启这个辅助程序。

<a id="wiki-section-6"></a>
### 连不上时

- `--help` 会打印用法。参数错误、找不到导出文件时，程序以退出码 2 退出，原因写在 stderr。
- 默认按 `ai.toml` 的 `[output].directory` 找导出目录。需要手动指定时加 `--ai-dir <游戏目录内的相对路径>`。
- 查询提示 `not-ready`：还没成功执行过 `/nekojs probe`。
- 提示 `disabled`：`ai.toml` 里 `[api].enabled` 是 `false`。改回 `true`，重启游戏重新导出。
- 提示 `unavailable`：快照文件有问题，或游戏环境变了。重新 probe，再重启辅助程序。
- 辅助程序的标准输出只用于通信。不要在启动脚本里往 stdout 打印任何东西。

<a id="wiki-section-7"></a>
## 配置参考

`nekojs/config/ai.toml` 的默认内容：

```toml
[output]
directory = ".github/neko_ai"
editor_agents = false

[documentation]
enabled = true
agents = "create-if-missing"

[api]
enabled = true

[http]
enabled = false

[mcp]
enabled = false
host = "127.0.0.1"
port = 8767

[control]
commands = false
reload = false
command_allowlist = []
```

- **改完要重启游戏**，reload 不会让配置生效。
- `[mcp].enabled` 打开游戏内 MCP，`[http].enabled` 打开普通 JSON 接口。两者共用 `[mcp].port`。独立 stdio 不受这两项影响。
- `host` 只能是 `127.0.0.1`，只允许本机访问。端口是 1–65535 的整数。
- `documentation.agents` 只能是 `create-if-missing` 或 `off`。
- `[api].enabled = false` 会关闭 API 查询，但文档仍然可读。
- `[control]` 是实时操作的开关，见下文 [实时操作](#wiki-live-controls)。
- 配置写错或端口被占用时，AI 功能不会启动，日志里有 `NEKO-1019`。游戏本身照常运行。

<a id="wiki-section-8"></a>
## AI 能查什么

连上之后，AI 一般先调用 `nekojs_context`、`nekojs_capabilities` 和 `nekojs_schema` 了解环境，再用下面四个工具查 API：

| 工具 | 用途 |
|---|---|
| `nekojs_resolve` | 按名字找符号，例如 `Item` |
| `nekojs_members` | 列出某个符号的方法和属性 |
| `nekojs_query` | 按条件筛选，例如“第一个参数是 string 的 `of` 重载” |
| `nekojs_batch` | 一次发送最多 10 个查询 |

这七个工具在两种方式下都可用。查询只读，不会执行脚本、修改文件或 reload。

下面的请求示例主要给想了解细节或自己写客户端的人看。普通 AI 工具会自己构造这些请求。

<a id="wiki-section-9"></a>
### resolve 和 members

先用 resolve 按名字找到符号：

```json
{
  "scope":{"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared"},
  "name":"Item","kind":"binding","match":"exact",
  "select":["symbolId","name","kind","coverage","source"],"page":{"size":20}
}
```

`environmentId` 和 `snapshotId` 从 `nekojs_context` 的结果里拿。`scriptType` 用大写，`language` 目前只能是 `typescript`。同名的绑定和类可能同时出现，要看清楚选哪个。

再把选中结果的 `symbolId`（不是名字）填进 `ownerSymbolId`，查它的成员：

```json
{
  "scope":{"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared","ownerSymbolId":"<ownerSymbolId>"},
  "name":"of","kind":"method","arity":{"min":1,"max":1},
  "select":["symbolId","name","parameters","returnType","callArity","coverage"],"page":{"size":20}
}
```

`arity` 按“能接受几个参数”匹配，会考虑可选参数和变长参数。每个重载单独返回一条。

<a id="wiki-section-10"></a>
### query

查找名为 `of`、且**第一个参数**类型是 `string` 的重载：

```json
{
  "scope":{"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared","ownerSymbolId":"<ownerSymbolId>"},
  "from":"members",
  "where":{"all":[
    {"field":"name","op":"eq","value":"of"},
    {"some":{"path":"parameters","where":{"all":[
      {"field":"position","op":"eq","value":0},
      {"field":"type.display","op":"eq","value":"string"}
    ]}}}
  ]},
  "select":["symbolId","name","parameters","returnType","coverage"],"page":{"size":20}
}
```

`from` 可以是 `symbols`、`members`、`events`、`inputAdapters`、`declarations`。条件用 `all`/`any`/`not` 组合，`some` 用来要求“同一个参数”同时满足几个条件。可用的字段和操作符以 `nekojs_schema` 返回的为准。

<a id="wiki-section-11"></a>
### batch

一次发多个查询，共用外层的 scope：

```json
{
  "scope":{"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared"},
  "queries":[
    {"from":"symbols","where":{"field":"name","op":"eq","value":"Item"},"select":["symbolId","name","coverage"]},
    {"from":"events","where":{"field":"group","op":"eq","value":"ServerEvents"},"select":["symbolId","name","group","coverage"]}
  ]
}
```

每个子查询有自己的 `status`，要逐个检查。

<a id="wiki-section-12"></a>
### 看懂查询结果

- **查不到不等于不存在。** 结果里的 `coverage` 不完整时，空结果不能说明 API 不存在。有些手写声明、继承关系和事件签名没有完整索引，这时直接去读生成的声明文件。
- **分页**：把返回的 `page.nextCursor` 填进下一次请求的 `page.cursor`，其他条件保持不变。游标 15 分钟后过期。
- **上限**：每页默认 20 条、最多 100 条；`select` 最多 32 个字段；batch 1–10 个查询；请求和响应各最多 64 KiB。超了就少选字段、减小页数。
- 写错字段名或操作符会直接报错，不会被忽略。
- 不支持 Python 成员、执行脚本、写文件或任意 Java 反射。

| 查询错误 | 怎么办 |
|---|---|
| `INDEX_NOT_READY` | 在游戏里成功执行一次 `/nekojs probe` |
| `INDEX_DISABLED` | 把 `[api].enabled` 改为 `true`，重启游戏 |
| `INDEX_UNAVAILABLE` | 检查快照文件、配置和日志 |
| `SCOPE_CONFLICT` | 重新调用 `nekojs_context`，用当前实例的 ID |
| `SNAPSHOT_EXPIRED` | 重新调用 `nekojs_context`，从头再查 |
| `RESPONSE_TOO_LARGE` | 减少字段或页大小，或者直接读本地文件 |

<a id="wiki-section-13"></a>
## 示例脚本

文档包里的 `handbook/scripting-examples.md` 有 15 个完整场景：每个都给出完整代码、放在哪里、怎么触发、应该看到什么、怎么清理。文件在包内的 `examples/` 目录。

用测试世界试，一次只装一个场景。服务端示例放进 `nekojs/server_scripts/authoring-examples/`，其他类型放进对应脚本目录下的同名子目录。

| 场景 | 包内文件 | 怎么触发 | 应该看到 |
|---|---|---|---|
| 登录欢迎 | `server/player-login.js` | reload 后重新登录 | 登录时收到欢迎消息 |
| 配方 | `server/recipes.js` | `/nekojs reload` | 木棍配方被替换，新增燧石和玻璃配方 |
| 物品标签 | `server/item-tags.js` | NeoForge：`/nekojs reload` 后再执行原版 `/reload` | 物品标签 stones 含石头、不含花岗岩 |
| 方块标签 | `server/block-tags.js` | NeoForge：`/nekojs reload` 后再执行原版 `/reload` | 方块标签 stones 含石头 |
| 注册新内容 | `startup/registry.js` | 重启游戏 | 红宝石物品和方块 |
| 物品组件 | `server/item-components.js` | `/nekojs reload`，看日志 | 修改一个内存里的物品，不发给玩家 |
| 共享状态与定时器 | `startup/shared-config.js` + `server/state-and-timer.js` | 先重启，再 `/nekojs reload` | 共享的问候语、递增的计数、延迟日志 |
| 客户端 UI | `client/counter.tsx` | 仅 NeoForge 26.x，`/nekojs reload client` | 计数界面，按按钮数字增加 |
| 控件 | `client/controls.tsx` | 仅 NeoForge 26.x，`/nekojs reload client` | 开关、滑块、选择器和输入框 |
| 表单与对话框 | `client/forms-and-dialogs.tsx` | 仅 NeoForge 26.x，`/nekojs reload client` | 输入校验、确认对话框、只读预览 |
| 多窗口 | `client/workspace.tsx` | 仅 NeoForge 26.x，`/nekojs reload client` | Notes/Tools/Preview 三个窗口，可最小化 |
| CommonJS | `modules/hello.cjs` + `modules/lib/message.cjs` | `/nekojs reload`，保持相对路径 | 日志输出 `Hello, NekoJS!` |
| 断言测试 | `test/assertions.js` | `/nekojs test` | 三个断言通过 |
| 日志 | `greeting.js` | `/nekojs reload` | 一条加载日志 |
| 事件订阅 | `event-subscription.js` | `/nekojs reload` | 观察配方事件，不修改配方 |

<a id="wiki-section-14"></a>
### 第一个示例：登录欢迎

把下面的代码放进 `nekojs/server_scripts/authoring-examples/player-login.js`，执行 `/nekojs reload`，然后退出重新登录：

```javascript
// Place in server_scripts. The callback runs for future logins, not existing players.
PlayerEvents.loggedIn(event => {
    event.entity.sendMessage("Welcome to NekoJS!");
});
```

登录时会收到 `Welcome to NekoJS!`，已经在线的玩家不会收到。**Fabric** 上要把 `event.entity` 换成 `event.player`。

<a id="wiki-section-15"></a>
### 配方示例

这个示例会替换原版木棍配方，请在测试世界里用。放进 `nekojs/server_scripts/authoring-examples/recipes.js`：

```javascript
// Use a disposable world: this replaces the vanilla stick recipe.
ServerEvents.recipes(event => {
    event.remove({ id: "minecraft:stick" });
    event.shaped(Item.of("minecraft:stick", 4), ["P", "P"], {
        P: Ingredient.of("minecraft:oak_planks")
    }).id("nekojs_example:sticks");
    event.shapeless(Item.of("minecraft:flint"), [
        Ingredient.of("minecraft:gravel"),
        Ingredient.of("minecraft:gravel")
    ]).id("nekojs_example:flint");
    event.smelting(Item.of("minecraft:glass"), Ingredient.of("minecraft:sand"), 0.1, 200)
        .id("nekojs_example:glass");
});
```

执行 `/nekojs reload` 后：两块竖放的橡木木板合成 4 根木棍；两块沙砾无序合成 1 个燧石；熔炉把沙子烧成玻璃。删掉文件再 reload，原版木棍配方就回来了。

<a id="wiki-live-results"></a>
<a id="wiki-section-16"></a>
## 查看实时运行结果

使用游戏内 MCP 时，除了上面七个工具，还有两个只读工具。它们不需要 API 快照，也不需要开启实时操作。独立 stdio 没有这两个工具。

| 工具 | 参数示例 | 返回什么 |
|---|---|---|
| `nekojs_errors` | `{"scriptType":"SERVER","limit":25}` | 当前的脚本错误列表：错误 ID、脚本类型、文件、行列号和简短消息。两个参数都可省略，最多 100 条 |
| `nekojs_operation_status` | `{"operationId":"<完整 UUID>"}` | 某个实时操作的后续状态，只查询，不会重新执行 |

开启了 `[http]` 时，也可以 POST 到 `errors` 和 `operation_status` 这两个 HTTP 接口。

另外，`nekojs_context` 结果里的 `testRunReport` 是最近一次 `/nekojs test` 的结果，包括 `passed`、`failed` 和 `overallPassed`。一个断言都没有执行时，`overallPassed` 为 `null`，不算通过。

<a id="wiki-live-controls"></a>
<a id="wiki-section-17"></a>
## 实时操作（默认关闭）

开启后，AI 可以通过游戏内 MCP 执行你允许的服务器命令，或 reload 脚本。只有游戏内 MCP 有这个功能，独立 stdio 和普通 HTTP 接口都没有。

> AI 执行的命令和 reload 会真实改变你的世界。请先备份脚本和存档，最好在测试世界里用，并且每次都明确知道 AI 要做什么。

开启步骤：

1. 在 `ai.toml` 中开启 `[mcp].enabled`。
2. 在 `[control]` 里只打开需要的功能：
   - 允许执行命令：`commands = true`，并在 `command_allowlist` 里列出允许的命令，例如 `["list"]`。
   - 允许 reload：`reload = true`。
3. 重启游戏，按“方式一”连接。AI 工具里会多出下面的工具。

| 工具 | 参数示例 | 作用 |
|---|---|---|
| `nekojs_execute_game_command` | `{"command":"list","timeoutMs":5000}` | 执行一条在允许列表里的服务器命令 |
| `nekojs_reload_scripts` | `{"type":"server","timeoutMs":5000}` | 完整 reload 一种脚本：`server`、`client` 或 `test` |

**命令**：

- 允许列表按命令的第一个词精确匹配。`list` 和 `minecraft:list` 算两个不同的命令。
- 命令以权限等级 2 执行，不是以玩家身份执行。需要玩家身份或更高权限的命令会失败。
- **允许列表不是沙箱。** 允许了 `execute` 这类命令，就等于允许了它能做的所有事。只放你确实需要的命令。
- 需要服务器正在运行（单人世界也可以）。

**reload**：

- `server` 和 `test` 需要服务器正在运行；`client` 需要客户端，专用服务器不能 reload client。
- 不支持 `startup`（需要重启游戏），也不支持只 reload 单个文件。
- `server` reload 包含配方的重新应用。标签仍需要再执行一次原版 `/reload`。
- 结果里分别看 `committed`（脚本是否生效）和 `postprocessingSucceeded`（配方等后续处理是否成功）。

**超时**：`timeoutMs` 是 1–8000 的整数，默认 5000。超时**不代表操作没有执行**，它可能仍在进行或已经完成。不要直接重试，先用 `nekojs_operation_status` 查询，或者在游戏里确认实际状态。同一时间只能有一个操作在进行。

常见的拒绝原因：

| 返回码 | 原因 |
|---|---|
| `CONTROL_DISABLED` | `[control]` 里没有开启这个功能 |
| `COMMAND_NOT_ALLOWED` | 命令不在允许列表里 |
| `CONTROL_BUSY` | 已有另一个操作在进行 |
| `LIVE_RUNTIME_REQUIRED` | 没有正在运行的服务器；独立 stdio 也总是返回这个 |
| `RELOAD_UNAVAILABLE` | 没有可用的客户端，无法 reload client |
| `RESTART_REQUIRED` | 请求 reload startup，需要重启游戏 |
| `UNSUPPORTED_RELOAD` | 不支持的 reload 类型 |
| `STALE_RUNTIME` | 世界已关闭或切换，旧操作不再执行 |
| `RELOAD_POSTPROCESSING_FAILED` | 脚本已经生效，但配方等后续处理失败。这不是回滚 |
| `TEST_ASSERTIONS_FAILED` | 测试脚本执行了，但有断言失败 |

用完后，把不需要的开关关掉并重启游戏。文档包里的 `handbook/live-controls.md` 有更详细的说明。

<a id="wiki-section-18"></a>
## 进阶：普通 HTTP 接口

开启 `[http].enabled` 并重启后，可以用 curl 之类的工具直接请求。所有接口都在 `/api/v1/authoring/` 下，都要带 `Authorization: Bearer <密钥>`。下面的例子在游戏目录下的 POSIX shell 里执行：

```sh
TOKEN="$(tr -d '\r\n' < nekojs/config/ai.token)"
BASE='http://127.0.0.1:8767/api/v1/authoring'
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/context"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/capabilities"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/schema"
# Save the resolve request above as request.json and replace the discovered IDs.
curl --fail-with-body -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' --data-binary @request.json "$BASE/resolve"
unset TOKEN
```

| 方法 | 接口 |
|---|---|
| GET | `context`、`capabilities`、`schema` |
| POST JSON | `resolve`、`members`、`query`、`batch`、`errors`、`operation_status` |

不接受 URL 查询参数，条件都放在 JSON 请求体里。HTTP 返回成功不代表查询结果完整，仍要看 `coverage`。

<a id="wiki-section-19"></a>
## 进阶：手写 MCP 请求

只有自己写客户端或调试时才需要。服务使用 Streamable HTTP 的 JSON 响应模式，协议版本 **2025-11-25**。

1. 向 `mcp` 地址 POST initialize，带上 `Authorization: Bearer <密钥>`、`Content-Type: application/json` 和 `Accept: application/json, text/event-stream`：

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"local-authoring-client","version":"1"}}}
```

2. 记下响应头里的 `Mcp-Session-Id`。之后每个请求都带上它和 `MCP-Protocol-Version: 2025-11-25`。
3. POST `{"jsonrpc":"2.0","method":"notifications/initialized"}`，成功时返回 HTTP 202。然后就可以调用工具了：

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"nekojs_context","arguments":{}}}
```

4. 结果看 `structuredContent` 和 `isError`，不要只看 HTTP 状态码。`resources/read` 只能读 `resources/list` 列出的文档。
5. 用完后对 `mcp` 地址发 DELETE。会话闲置 30 分钟过期，最多同时 32 个。

不支持旧式 SSE、GET 事件流（GET /mcp 返回 405）、prompts 和资源订阅。

<a id="wiki-section-20"></a>
## 安全

- 查询服务只监听本机 `127.0.0.1`。不要做端口转发，也不要放到公网代理后面。
- `ai.token` 相当于密码。不要提交到 Git，不要贴进聊天、截图或日志。本机上能读到这个文件的程序都能访问服务。
- 没有 TLS 和远程授权，只适合本机使用。
- AI 查到的信息只能帮助写代码，不能证明脚本在游戏里一定正确。最终效果要你在游戏里确认。

相关页面：[脚本基础](script-basics_cn)、[TypeScript 与 JSX](typescript-and-jsx_cn)、[命令](commands_cn)、[错误与日志参考](error-reference_cn)。

<!-- wiki-nav -->

---

[上一篇: 事件参考](event-reference_cn) · [目录](Home) · [下一篇: 命令](commands_cn)
