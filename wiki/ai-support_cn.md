<!-- wiki-page: ai-support; locale: cn -->

> **中文** · [English](ai-support_us)

<a id="wiki-section-1"></a>
# AI 辅助脚本编写

NekoJS 默认导出可离线阅读的英文 AI 文档包，同时支持**游戏运行时的内置 MCP**和**读取离线导出的独立 MCP stdio 辅助进程**，两者均提供只读编写查询。内置 MCP 另有独立启用的命令与完整 SERVER/CLIENT/TEST reload 工具，默认全部关闭。它不是 AI 模型或聊天服务，也不会自动修改 AI 客户端配置。

推荐流程：找到当前文档包 → 成功生成 TypeScript Probe 快照 → 按需选择游戏内 MCP 或独立 stdio → 查询精确签名 → 在游戏内验证脚本。调用形态见[脚本 API 约定](script-api-style_cn)；客户端 HUD/world 绘制与后处理见[专门说明](client-rendering_cn)，不要把其他项目的对象配置示例直接当成 NekoJS 的新重载。随模组发布的 handbook、Skill、AGENTS 模板和示例是 AI 使用的规范资料；Wiki 是面向用户的说明，不替代实例内文档。

<a id="wiki-section-2"></a>
## 离线入口与文件

以下路径均从游戏目录开始，按默认 `[output].directory = ".github/neko_ai"` 展示。先读 `nekojs/config/ai-location.json`，若自定义目录，以下 AI 根目录应替换为其中的 `directory`。

| 路径 | 用途 |
|---|---|
| `nekojs/config/ai.toml` | 输出、文档、查询、连接与控制配置 |
| `nekojs/config/ai-location.json` | 纯定位数据，默认为 `{"schemaVersion":1,"directory":".github/neko_ai"}` |
| `.github/neko_ai/current.json` | 当前文档包指针；其中 `bundle`、`manifest` 相对于 `.github/neko_ai/` |
| `.github/neko_ai/bundled/<bundleId>/` | 不可原地修改的版本化英文文档包 |
| `.github/neko_ai/environment.json` | 环境信息与 `apiSnapshotDirectory` |
| `.github/neko_ai/connection.json` | 本实例端点、监听状态、协议版本与 token 文件位置；不含 token |
| `nekojs/config/ai.token` | 开启监听时才创建的本机 bearer 凭据 |
| `.github/neko_ai/snapshots/current.json` | 所选 AI 根目录下的结构化快照指针 |

文档导出不扫描类，也不开启端口。所选 AI 根目录包含 `bundled/`、`current.json`、`environment.json`、`connection.json`、`snapshots/`、`agents/`、`AGENTS.md` 和 `README.md`；缺失的入口文件在这里创建，不放在 `nekojs/` 或工作区根目录。已知且未修改的生成角色路由可安全刷新，已编辑路由和用户文件保留。同一 ID 下修改过的文档包会拒绝覆盖。切换目录不会盲目搬移或删除旧文件，关闭导出也不会删除它们。

`output.directory` 不接受绝对路径、路径穿越或逃离游戏目录的链接。配置与私密凭据仍在 `nekojs/config/`；声明、补全片段与编辑器配置仍使用原有 Probe 位置。AI 根目录的 `agents/` 不会自动被 VS Code 发现；可选 `[output].editor_agents = true` 才写标准 `.github/agents/` 桥接，默认 false。这里的 `AGENTS.md` 也不是自动生效的工作区指令；请显式配置客户端读取，或将选定指引合入自己的指令文件。

<a id="wiki-section-3"></a>
## 默认配置

`nekojs/config/ai.toml` 的默认值如下（省略生成文件中的英文注释）：

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

- 文档默认开启；游戏内 HTTP 和 HTTP MCP 默认关闭（不影响独立 stdio），`api.enabled = true` 本身不会打开端口。
- 仅使用游戏内可选传输时，按需将 `[http].enabled` 或 `[mcp].enabled` 设为 `true`；两者共用 `[mcp].port`。
- host 只接受字面值 `127.0.0.1`，不支持 `0.0.0.0`、远程绑定或 IPv6。端口必须为 1–65535 的整数，布尔设置不能写成字符串。
- `documentation.agents` 只接受 `create-if-missing` 或 `off`。
- `api.enabled = false` 禁用 resolve/members/query/batch，但不禁用 context、capabilities、schema 或文档。
- **修改后重启游戏进程**；普通脚本 reload 不重建根运行时的监听器。配置错误、端口冲突等会使 AI 支持初始化失败（NEKO-1019），不会因此终止整个游戏。

<a id="wiki-section-4"></a>
## 生成并发现快照

先调用 `context`、`capabilities` 和 `schema`。context 返回 `environmentId`、`snapshotId` 和 `indexState`；索引可能为 `not-ready`、`ready`、`disabled` 或 `unavailable`。

导出文档不等于生成 API 索引。需要在游戏内成功运行 **TypeScript Probe**（具体命令见已安装版本的 `/nekojs probe` 帮助与 [Probe 类型生成](probe-type-generation_cn)）。普通脚本 reload 不重新生成索引。

Probe 先写不可变、按摘要命名的 JSON 文件，再原子切换 `current.json`。查询运行时在 context/API 请求时检查文件摘要、环境和快照 ID，验证后才加载；发布失败不替换之前已验证的快照。不要手改索引，也不要混用其他实例的快照。结构化 AI 快照固定在所选 AI 根目录的 `snapshots/`，由 `environment.apiSnapshotDirectory` 报告；更改 Probe 声明 baseDir 不会搬移该快照。

<a id="wiki-section-5"></a>
## 游戏内 MCP：游戏运行时连接

同时支持**游戏内 MCP**与**独立 stdio**。游戏已运行时，可直接使用内置服务，无需另外安装或启动辅助 JAR：

1. 在 `nekojs/config/ai.toml` 的 `[mcp]` 下设置 `enabled = true`（默认 false），保留 `host = "127.0.0.1"` 和 `port = 8767`。`[http].enabled` 可保持 false；它控制普通 JSON 路由，不是 MCP。
2. 完整重启游戏进程并保持运行。读取 `.github/neko_ai/connection.json`：`listening` 应为 true，`mcp` 通常为 `http://127.0.0.1:8767/mcp`；修改端口后以实际描述文件 URL 为准。
3. 在 AI 客户端新增支持 Streamable HTTP/JSON 响应的 MCP 连接，填入该 URL 和 `Authorization: Bearer <token>`。在本机把 `<token>` 替换为 `nekojs/config/ai.token` 内容；保密保存，描述文件本身不会包含 token。

客户端只需要 **URL + bearer 凭据**，不需要 Java 命令、JAR 路径或 `--game-dir`。支持 `mcpServers` 对象以及 `url`、`headers` 的客户端可参考：

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

具体设置结构取决于客户端，也可能通过连接对话框或显式传输设置完成。客户端自动处理 **2025-11-25** 初始化及会话头；后面的手写 JSON-RPC 仅供自定义客户端或调试，不是用户连接的必经步骤。确认工具已列出后，先调用 `nekojs_context`、`nekojs_capabilities`、`nekojs_schema`；API 查询仍需成功生成的 TypeScript Probe 快照。

该方式需显式开启，不是自动发现或零配置；没有 GUI 开关、自动修改客户端配置或自动连接。游戏关闭后内置服务结束；需要离线导出时使用下面的独立模式。

<a id="wiki-section-6"></a>
## 另一模式：独立 MCP stdio 读取离线导出

使用 **Java 21 或更新版本**及对应发布版本的独立 `nekojs-1.1.0-preview3-authoring-stdio.jar`（构建输出为 `common/build/libs/nekojs-1.1.0-preview3-authoring-stdio.jar`；构建任务 `:common:authoringStdioJar`，也包含在 `assemble` 中）。它不是模组 JAR 或游戏启动器。先启动一次游戏导出环境，再成功运行 TypeScript Probe 供 API 查询；之后即使游戏关闭，也能读取最后一次导出。

让 AI 客户端启动**独立进程**，不是接管游戏标准输入。以下示例合并到使用 `mcpServers` 对象的客户端设置中；不同客户端的设置文件位置可能不同。

```sh
java -jar /absolute/path/nekojs-1.1.0-preview3-authoring-stdio.jar --game-dir /absolute/game/directory
```

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

替换两个路径。`--game-dir` 是**包含 nekojs/ 的游戏目录**，不是 nekojs 本身、存档、JAR 目录或本仓库。推荐绝对路径和独立参数数组；含空格的路径保持为一个数组元素。Windows 可写成 `D:/Games/Instance`，避免 JSON 反斜杠转义。客户端 PATH 找不到 Java 时，改为 Java 21+ 可执行文件的绝对路径。NekoJS 不会自动安装或修改客户端设置。

无需监听器、token 或修改 `ai.toml` 的连接设置。客户端完成 **2025-11-25** 初始化并发送 `notifications/initialized` 后，先调用 `nekojs_context`、`nekojs_capabilities` 和 `nekojs_schema`。stdio 提供相同七个工具及列出的文档资源；先 `resources/list` 再 `resources/read`，检查 `structuredContent`、`isError` 和 coverage。没有 HTTP 会话头或 DELETE；结束时停止客户端持有的辅助进程（EOF 退出码为 0）。

辅助进程要求 `--game-dir` 恰好一次，可选 `--ai-dir <游戏相对目录>` 一次，顺序不限。发现优先级：显式覆盖 → 严格纯 JSON 的 `nekojs/config/ai-location.json`（最多 4096 字节）→ 存在时的默认 `.github/neko_ai/environment.json` → 仅在定位文件与默认描述都不存在时使用旧 `nekojs/ai/environment.json`。选中的导出无效、缺失或越界时拒绝，不静默回退。新版 `apiSnapshotDirectory` 必须是所选根目录的 `snapshots/`。读取导出的 `apiEnabled`，**不解析 ai.toml**，不创建配置/token/监听器或写游戏数据。API 配置变化后须重启游戏重新导出；每次 context/API 请求验证变化的环境、指针与快照，环境标识变化须重启辅助进程。文档来自辅助 JAR 自带的已验证英文包（`context.documentation.bundleId`）。离线结果只描述**最后导出**，不代表实时模组或世界状态。

<a id="wiki-section-7"></a>
### 启动与离线限制

`--help` 向 stderr 输出用法并以 0 退出；参数、目录或环境导出缺失/无效时退出码为 2。`--game-dir <directory>` 必须且只能提供一次；相对路径按客户端工作目录解析。`not-ready` 需成功 Probe；元数据/schema 和文档不需要快照。`disabled` / `INDEX_DISABLED` 需启用 API 并重启游戏重新导出。`unavailable` / `INDEX_UNAVAILABLE` 需检查描述文件/快照，或在环境变化后重启辅助进程，而不是静默回退到旧数据。

stdio 使用 UTF-8、逐行 JSON-RPC，每行一个对象，不接受批次数组。输入/输出帧上限为 64 KiB；输入超限会关闭连接，输出超限需减少字段/分页。stdout 只用于协议，诊断写入 stderr；不可合并两者或插入 shell 欢迎文字。辅助进程不开端口、不创建 token、不写游戏数据，也不能限制 AI 客户端自身文件工具的权限。

<a id="wiki-section-8"></a>
## 进阶：可选 HTTP 与 curl

重启后读取连接描述文件，使用其 `http` 或 `mcp` 地址及 `listening` 状态；禁用的端点为 null。`tokenFile` 相对于游戏目录（`tokenFileBase: "game-directory"`），不是描述文件目录。

以下示例适用于 POSIX shell、curl 和默认 HTTP 地址；在游戏目录执行，其他 shell 使用等价的私密 token 读取方式。先启用 HTTP，并按发现结果调整端口：

```sh
TOKEN="$(tr -d '\r\n' < nekojs/config/ai.token)"
BASE='http://127.0.0.1:8767/api/v1/authoring'
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/context"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/capabilities"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/schema"
# Save the resolve request below as request.json and replace the discovered IDs.
curl --fail-with-body -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' --data-binary @request.json "$BASE/resolve"
unset TOKEN
```

所有路由都要求 `Authorization: Bearer <token>`。统一前缀为 `/api/v1/authoring/`：

| 方法 | 操作 |
|---|---|
| GET | context、capabilities、schema |
| POST JSON | resolve、members、query、batch |

不接受 URL 查询参数，scope 和过滤条件放在 JSON 请求体。HTTP 成功也不代表查询覆盖完整，必须检查结果内容。

<a id="wiki-section-9"></a>
## 进阶参考：手写 HTTP MCP 请求

以下仅供调试或自定义客户端使用，普通客户端会自动处理这些交换。需要支持 **Streamable HTTP 的 JSON 响应模式**、bearer 请求头和 **2025-11-25** 协议。地址使用连接文件中的 `mcp`（默认 `http://127.0.0.1:8767/mcp`），不是普通 HTTP API 前缀。

1. 向该地址 POST JSON-RPC initialize，携带 `Authorization: Bearer <token>`、`Content-Type: application/json`、`Accept: application/json, text/event-stream`：

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"local-authoring-client","version":"1"}}}
```

2. 读取响应中的协议版本和 `Mcp-Session-Id` 响应头。后续 POST 保留 bearer/Accept/Content-Type，并增加该会话头及 `MCP-Protocol-Version: 2025-11-25`。
3. POST 不带 id 的 `{"jsonrpc":"2.0","method":"notifications/initialized"}`；成功为无响应体的 HTTP 202。随后调用 tools/list、resources/list，先查询 context：

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"nekojs_context","arguments":{}}}
```

4. 依次读取 `nekojs_capabilities`、`nekojs_schema`，再调用 `nekojs_resolve`、`nekojs_members`、`nekojs_query`、`nekojs_batch`。参数与同名 HTTP 操作一致。检查 `structuredContent` 和 `isError`，不只看 HTTP 状态。
5. resources/read 只能读取 resources/list 列出的 `nekojs://docs/<bundleId>/<path>` 文档 URI，不可读取任意文件。完成后向 MCP 地址 DELETE，携带 bearer、会话和协议头。最多 32 个会话，闲置 30 分钟过期；过期或进程重启后重新初始化。

stdio 由上面的独立辅助进程提供，不是游戏监听器的传输。**不支持**旧式 SSE 端点、GET 事件流（GET /mcp 返回 405）、prompts、资源订阅或服务端主动请求。Accept 中包含 text/event-stream 是传输要求，不表示服务会推送 SSE。客户端若强制要求这些能力，请改用文件或普通 HTTP 工作流。

<a id="wiki-section-10"></a>
## 查询示例

每次 scope 都要指定发现的 environmentId、scriptType 和 language；scriptType 使用大写 STARTUP/SERVER/CLIENT/TEST，目前 language 仅为 typescript，memberView 仅为 declared。发现后显式固定 snapshotId；仅初次选择当前快照时可以省略。下列尖括号是占位符，发送前必须替换；示例名称不保证每个实例都提供。

<a id="wiki-section-11"></a>
### 先 resolve，再 members

POST resolve，或将相同对象传给 nekojs_resolve：

```json
{
  "scope":{"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared"},
  "name":"Item","kind":"binding","match":"exact",
  "select":["symbolId","name","kind","coverage","source"],"page":{"size":20}
}
```

检查所有候选，绑定与类可能同名。取所选结果的 **symbolId** 放进 **scope.ownerSymbolId**，不是放名称。exact 为默认匹配，prefix 需要显式选择。POST members：

```json
{
  "scope":{"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared","ownerSymbolId":"<ownerSymbolId>"},
  "name":"of","kind":"method","arity":{"min":1,"max":1},
  "select":["symbolId","name","parameters","returnType","callArity","coverage"],"page":{"size":20}
}
```

arity 判断可接受调用实参数量区间是否重叠，包含可选参数与变长参数语义，不是原始参数数量。每个可调用记录对应一个实际发射的重载，不把多个重载混成一组。

<a id="wiki-section-12"></a>
### 关联同一参数与同一重载

POST query：该请求查找名为 of 且**第一个参数本身**的显示类型为 string 的重载，不从不同参数或重载拼凑条件：

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

资源包括 symbols、members、events、inputAdapters、declarations。布尔组合使用 all/any/not，参数关联使用 some。字段、操作符、select 与结构化请求约束以 capabilities/schema 为准。声明原文可选择 path、sha256、text；过大的文本请直接读取本地生成的声明文件。类型 ID/显示文本是保守元数据，不提供跨模块或别名的可赋值关系推断。

<a id="wiki-section-13"></a>
### Batch

POST batch：子项是 query 对象，不是任意操作，共用外层 scope/快照，不能各自覆盖 scope：

```json
{
  "scope":{"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared"},
  "queries":[
    {"from":"symbols","where":{"field":"name","op":"eq","value":"Item"},"select":["symbolId","name","coverage"]},
    {"from":"events","where":{"field":"group","op":"eq","value":"ServerEvents"},"select":["symbolId","name","group","coverage"]}
  ]
}
```

逐一检查 results 内各项的 status，以及 result 或 error，不因其他子项成功就忽略失败。

<a id="wiki-section-14"></a>
## 覆盖、分页与限制

- 检查 items、coverage.state/scope/unresolvedRecords、warnings 和 page。元数据缺失为 unknown，即使取反也不是 true；精确查询不自动退化成模糊查询。**覆盖不完整时，最后一页为空也不能证明 API 不存在。**
- declared 包括实际发射的声明与已物化投影，不会展开所有继承泛型。手写声明、字面量重载、capture 视图可能只有声明文本；绑定继承与冲突、事件回调签名、适配器别名也未完整索引。应沿来源读取声明。
- 不支持 Python 成员、effective 继承视图、assignableTo、任意 Java 反射、SQL、脚本求值、写脚本、运行 Probe 或触发 reload。
- 下一页将返回的 page.nextCursor 放入 page.cursor，保持操作、scope、过滤、select、排序不变；可调整页大小。检查 page.endOfResults 与 page.stopReason。内存仅保留最近加载的三个快照，游标在 15 分钟或进程关闭后失效；SNAPSHOT_EXPIRED 后刷新 context 并重查，不拼接不同快照的结果。
- 每页默认 20、最多 100 条；select 最多 32 项、排序最多 3 项；谓词最多 32 节点/4 层，in/notIn 最多 100 值；batch 为 1–10 项。不认识的字段、操作符或错误类型会报错而非忽略。
- HTTP 请求体/响应最多 64 KiB、并发交换最多 2 个、单次交换期限 10 秒。缩小页或 select；过大声明使用文件方式阅读。

| 查询错误 | 下一步 |
|---|---|
| INDEX_NOT_READY | 成功运行 TypeScript Probe |
| INDEX_DISABLED | 检查 api.enabled 并重启 |
| INDEX_UNAVAILABLE | 检查快照、配置与日志 |
| SCOPE_CONFLICT | 重新发现当前实例环境 |
| SNAPSHOT_EXPIRED | 刷新 context，重新查询 |
| RESPONSE_TOO_LARGE | 减少字段/页大小，或读取本地文件 |

<a id="wiki-section-15"></a>
## 实用脚本：选择一个完整场景

不要只停留在连接示例。先从 `.github/neko_ai/current.json` 找到当前包，再读包内 `handbook/scripting-examples.md`：其中有 12 个场景、全部 14 个源文件的完整代码、目标路径、触发方式、预期观察、清理步骤与平台条件。`examples/metadata.json` 记录安装文件组和验证级别。下表路径均相对于包内 `examples/`。

使用可丢弃实例/世界，备份现有脚本，每次只安装所选场景的一份。普通 SERVER 文件放入 `nekojs/server_scripts/authoring-examples/`；STARTUP、CLIENT、TEST 分别放入对应的 `startup_scripts`、`client_scripts`、`test_scripts` 下同名子目录。CommonJS 两文件放入 SERVER 的 `authoring-examples/modules/`，保留 `lib/` 子目录；状态场景两文件分属 STARTUP 与 SERVER。两种 MCP 模式都不会安装文件或执行游戏命令。

| 场景 | 包内文件 | 触发方式 / 条件 | 预期观察 |
|---|---|---|---|
| 登录欢迎 | `server/player-login.js` | SERVER reload 后重新登录 | 新登录玩家收到消息 |
| 配方 | `server/recipes.js` | 整个 SERVER reload，检查配方后处理 | 替换木棍配方，添加燧石与玻璃配方 |
| 物品标签 | `server/item-tags.js` | NeoForge：SERVER reload 后执行原版 `/reload` | 物品 stones 标签含石头，不含花岗岩 |
| 方块标签 | `server/block-tags.js` | NeoForge：SERVER reload 后执行原版 `/reload` | 独立的方块 stones 标签含石头 |
| 启动注册 | `startup/registry.js` | 完整重启进程，客户端/服务端定义一致 | 红宝石物品和方块；方块物品自动创建 |
| 物品组件 | `server/item-components.js` | SERVER reload，检查日志 | 修改内存中的物品堆，不发放到背包 |
| 状态与定时器 | `startup/shared-config.js` + `server/state-and-timer.js` | 先重启一次，再 SERVER reload | 共享问候语、类型内递增计数、延迟日志 |
| 客户端 UI | `client/counter.tsx` | 仅 NeoForge 26.x，CLIENT reload | 计数界面打开，按钮递增文本 |
| CommonJS | `modules/hello.cjs` + `modules/lib/message.cjs` | SERVER reload，保留相对路径 | 输出 `Hello, NekoJS!` |
| 断言 | `test/assertions.js` | `/nekojs test` | 三个断言通过 |
| 日志热身 | `greeting.js` | SERVER reload | 一条示例加载日志 |
| 事件热身 | `event-subscription.js` | SERVER reload | 观察配方事件，不修改配方 |

<a id="wiki-section-16"></a>
### 第一个游戏脚本：登录欢迎

将完整代码放入 `nekojs/server_scripts/authoring-examples/player-login.js`，执行 `/nekojs reload server` 后重新连接：

```js
// Place in server_scripts. The callback runs for future logins, not existing players.
PlayerEvents.loggedIn(event => {
    event.player.sendMessage("Welcome to NekoJS!");
});
```

之后登录的玩家收到 `Welcome to NekoJS!`；已经在线的玩家不会补发。删除复制的文件后 reload SERVER，即可取消后续欢迎。事件组订阅与 `event.player` Bean 属性是实际写法；玩家句柄只在回调内使用。

<a id="wiki-section-17"></a>
### 配方示例与验收

只在可丢弃世界中复制到 `nekojs/server_scripts/authoring-examples/recipes.js`：

```js
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

执行整个 `/nekojs reload server` 并等待配方后处理完成，而非只确认脚本提交。预期：两块竖放的橡木木板通过 `nekojs_example:sticks` 合成四根木棍；两块沙砾通过 `nekojs_example:flint` 无序合成一个燧石；`nekojs_example:glass` 以 0.1 经验、配置时长 200 tick 将沙子烧成玻璃。旧的 `minecraft:stick` 配方被移除，原版玻璃配方可能仍存在。实际操作工作台/熔炉验收；删除文件并完整 reload SERVER 后检查基础配方恢复，资源或查看器状态过期时也要检查其 reload 结果。

标签需在已实现的 NeoForge 路径依次完成 SERVER reload 与原版 `/reload`；Fabric hook 尚未完整实现。注册内容必须重启进程并使用可丢弃存档，示例未提供资源或创造标签页条目。UI 仅适用 NeoForge 26.x 的 `ClientUI`，1.21.1/Fabric 应跳过。状态只在当前进程内共享，不是磁盘保存或客户端/服务端同步。组件示例只创建独立的内存物品堆，不修改背包。切换场景前按 cookbook 中该分支的步骤清理。

验证标签区分隔离契约检查、真实引擎执行、带平台夹具的真实引擎执行；这些都不是 Minecraft 世界内验收。配方、标签、注册、玩家回调与 UI 的实机结果必须单独记录。复制陌生调用前，检查当前实例声明与 profile。

<a id="wiki-live-controls"></a>
<a id="wiki-section-18"></a>
## 按需启用的实时 MCP 操作

授权前先读当前文档包的 `handbook/live-controls.md`。普通 HTTP 查询与 query batch 仍只读；只有**游戏内 MCP**可公布这两个工具。独立 stdio 没有实时宿主，即使游戏配置启用了控制，有效操作请求仍返回 `LIVE_RUNTIME_REQUIRED`（409）。

1. 备份脚本与存档，在可丢弃世界中操作，并获得用户对具体操作的明确授权。
2. 启用 `[mcp].enabled`，再在 `[control]` 中独立选择：窄范围命令测试可用 `commands = true` 与 `command_allowlist = ["list"]`；需要完整 SERVER、CLIENT 或 TEST reload 时才启用 `reload = true`。默认分别为 false、false、空 allowlist，不用的保持关闭。
3. 完整重启进程，再按上文 URL/bearer 流程连接。命令、SERVER 和 TEST 需要正在运行的专用/内置服务器；CLIENT 需要客户端进程中的活动运行时，不要求有服务器，专用服务器不能 reload CLIENT。刷新 `tools/list`，检查 `context.liveControls` 或 `capabilities.liveControls`；`liveControls.reloadTypes` 按 `server`、`client`、`test` 顺序列出当前可用且策略已启用的目标，关闭或无可用目标时为 `[]`。至少一个目标可用才公布 reload 工具；输入 schema 的三种枚举值不证明对应运行时可用。伪造隐藏工具调用不会绕过检查。描述文件的 `readOnly` 只说明配置策略，不证明目标 owner 当前可用。

| 工具 | 工具参数 | 效果与限制 |
|---|---|---|
| `nekojs_execute_game_command` | `{"command":"list","timeoutMs":5000}` | 一条精确命令根 allowlist 匹配的服务器命令，可带开头斜杠；最多 2048 字符，不接受控制/行分隔字符或首尾空白 |
| `nekojs_reload_scripts` | `{"type":"server","timeoutMs":5000}` | 在实际 owner 上 reload 一个完整 `server`、`client` 或 `test` 代次；仅 SERVER 含配方/脚本数据包后处理；不支持单文件/局部/`all`、原版 reload 或 Probe |

命令源固定为非玩家、权限等级 2（gamemaster）、无实体冒充，位置为主世界原点。要求玩家或更高权限的命令可能失败。allowlist 精确匹配根标记，不匹配前缀或过滤参数；`list` 与 `minecraft:list` 不同。最多 128 个根、每个最多 128 字符，仅小写字母、数字、`_`、`.`、`-` 与可选 `namespace:`。**allowlist 不是沙箱**：`execute` 或插件分派根可能允许强大嵌套操作。反馈仅属于本次请求，是最多 64 条/8192 字符的净化纯文本，不是全局日志或管理员广播。完成取实际命令结果；同步分派返回但没有结果回调时按失败处理，不视为成功。

reload 使用小写 `server`、`client` 或 `test`；例如将参数替换为 `{"type":"client","timeoutMs":5000}` 或 `{"type":"test","timeoutMs":5000}`。CLIENT 等待客户端 owner 的真实候选完成，不将排队视为成功；它没有服务端配方/资源后处理，专用服务器会以 `RELOAD_UNAVAILABLE` 拒绝。TEST 需要实时服务器 owner，只加载/reload TEST 脚本，不负责执行并认证断言套件通过。SERVER 保留配方/脚本数据包后处理。STARTUP 必须重启进程（`RESTART_REQUIRED`），单文件/局部、`all` 等其他不支持的类型返回 `UNSUPPORTED_RELOAD`。分别检查 `committed` 和 `postprocessingSucceeded`；`committed: true` 加 `RELOAD_POSTPROCESSING_FAILED` 表示候选已提交而后处理失败，不是回滚。CLIENT/TEST 的 `postprocessingSucceeded: true` 表示候选完成且没有服务端后处理，不证明 UI 行为或测试断言通过。即使候选回滚，也不能撤销任意外部副作用。tag 流程仍按示例指南执行额外原版 reload。

检查 MCP 的 `isError` 与 `structuredContent`。已接纳结果含 `operationId`、`operation`、`status`、`code`、`effects`、`retryable`、`output`、`outputTruncated` 和操作完成字段；reload 结果与审计元数据包含选定的小写 `type`。接纳前拒绝是错误对象。`timeoutMs` 为 1–8000 整数，默认 5000。排队中过期的任务不会随后执行（`effects: "none"`）；已开始后超时/中断可能是 `effects: "unknown"`，不等于回滚、保证取消或可以盲目重试。命令和所有 reload 目标共用一个活动操作槽位。在该操作的 owner 代次内，已领取操作占用槽位直到实际完成，即使客户端已超时；目标停止/代次变化可退役旧槽位，但不会使已开始的副作用变得已知或可安全重试。后续请求得到 `CONTROL_BUSY`。`retryable` 只表示任务未被领取，不自动授权重复操作。`CONTROL_DISABLED`、`COMMAND_NOT_ALLOWED`、`STALE_RUNTIME` 区分策略、allowlist 和代次拒绝。CLIENT 没有活动客户端运行时时返回 `RELOAD_UNAVAILABLE`；命令或 SERVER/TEST 没有实时服务器时返回 `LIVE_RUNTIME_REQUIRED`。没有公开状态轮询接口，超时响应也不会事后改写。

记录真实平台/配置、操作、观察结果与清理。源码/夹具测试不证明真实世界效果；**人工验收仍待维护者给出实际结论**。测试后关闭不用的控制并重启。

<a id="wiki-section-19"></a>
## 安全与验证

以下网络与 token 限制仅适用于可选的游戏内 HTTP，不适用于独立 stdio 辅助进程。监听只接受 IPv4 loopback。Host 必须匹配 localhost 或 127.0.0.1 及端口，若带 Origin 则必须匹配本机 HTTP origin。token 使用随机 bearer 凭据，并限制 POSIX 权限或 Windows owner ACL；能够读取 token 的受信任本机进程仍可认证。

不要转发端口、公开代理或把 token 放进版本库、提示词、截图、共享日志。这里没有 OAuth、TLS 或远程访问授权方案；浏览器 Host/Origin 校验不替代凭据保密。关闭进程会关闭监听/会话/查询缓存并将本实例连接描述标为离线。

查询只能辅助编写，不能验证游戏行为。检查生成声明、按脚本类型进行正常加载/reload/重启，并记录真正执行过的验证。详见 [脚本基础](script-basics_cn)、[TypeScript 与 JSX](typescript-and-jsx_cn)、[命令](commands_cn) 和 [错误与日志参考](error-reference_cn)（NEKO-1019、NEKO-4055）。

<!-- wiki-nav -->

---

[上一篇: 事件参考](event-reference_cn) · [目录](Home) · [下一篇: 命令](commands_cn)
