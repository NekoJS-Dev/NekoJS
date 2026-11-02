# NekoJS AI 脚本编写手册（中文审阅版）

> 本文用于人工阅读和审阅，是基础手册 13 个章节的中文对照译本，不是内容摘要。后续补充的 [脚本编写实践篇](<2026-10-31-scripting-examples-zh-cn.md>) 提供 12 个场景、14 个完整脚本文件及运行和清理说明。
>
> 对应版本：**1.1.0-preview3**；翻译日期：**2026-10-31**。
>
> 本文不进入模组的 AI 文档包，不替换英文规范来源，也不改变导出、配置或运行行为。各章均附英文原文链接。命令、API 名称、协议字段、配置值和代码示例保留原样；括号中的中文仅解释术语。示例中的名称不代表所有实例都提供这些 API。

## 目录

1. [手册入口与使用顺序](#index)
2. [脚本类型](#script-types)
3. [事件](#events)
4. [Binding 与输入适配器](#bindings-and-adapters)
5. [配方与标签](#recipes-and-tags)
6. [注册与 Builder](#registry-builders)
7. [脚本包](#script-packs)
8. [JSX 客户端 UI](#jsx-ui)
9. [诊断](#diagnostics)
10. [验证](#validation)
11. [MCP 连接：模组内与独立模式](#connections)
12. [通过 stdio 连接 AI 客户端](#stdio)
13. [只读脚本编写查询](#query-api)

<a id="index"></a>
## 1. 手册入口与使用顺序

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/index.md>)

请将安装在当前实例中的文档包与**同一实例**的声明文件配套使用。Wiki 仅作为补充；编写脚本不要求联网。

1. 从游戏目录读取 `nekojs/ai/environment.json` 和 `nekojs/ai/current.json`，或调用脚本编写服务的 `context` 操作。通过这些发现文件确定当前文档包和索引的位置。
2. 若要在游戏运行时使用 MCP 工具，开启[模组内 MCP](#connections)，在客户端配置 URL 和 bearer token；不需要辅助 JAR 或游戏目录参数。若要访问离线快照，或希望由客户端持有独立进程，使用[独立 stdio 辅助程序](#stdio)。
3. 选择脚本类型，并阅读[脚本类型](#script-types)。
4. 根据任务阅读下面的对应章节；使用不熟悉的成员前，先查询精确签名。
5. 按照[验证](#validation)进行检查，并如实报告实际完成的验证。

| 任务 | 参考 |
| --- | --- |
| 订阅事件 | [事件](#events) |
| 调用辅助方法或传入物品字符串 | [Binding 与输入适配器](#bindings-and-adapters) |
| 修改配方或标签 | [配方与标签](#recipes-and-tags) |
| 注册内容 | [注册与 Builder](#registry-builders) |
| 在脚本包中工作 | [脚本包](#script-packs) |
| 创建客户端 UI | [JSX 客户端 UI](#jsx-ui) |
| 排查错误 | [诊断](#diagnostics) |
| 查找类和成员 | [只读脚本编写查询](#query-api) |
| 连接正在运行的游戏 | [模组内 MCP 设置](#connections) |
| 通过 MCP 读取离线快照 | [独立 stdio](#stdio) |

首版文档包介绍通用的脚本编写流程。结构化 API 覆盖范围明确是不完整的；没有成员索引时，仍可读取声明文本。它不是 Minecraft 或所有模组 API 的完整目录。

<a id="script-types"></a>
## 2. 脚本类型

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/script-types.md>)

| nekojs 下的目录 | Script Type（脚本类型） | 加载与验证 |
| --- | --- | --- |
| startup_scripts | STARTUP | 在进程启动时运行；注册内容变更后需要重启游戏。 |
| server_scripts | SERVER | 随服务器／世界加载；使用 `/nekojs reload server`。 |
| client_scripts | CLIENT | 仅限客户端 API；在集成客户端中使用 `/nekojs reload client`。 |
| test_scripts | TEST | 显式执行的测试；使用 `/nekojs test`。 |

对应的编译器支持 JavaScript、可擦除类型的 TypeScript、JSX/TSX、CommonJS 和 Python 子集。TypeScript 类型检查与运行时加载是两个独立步骤。这里的 Python 不是 CPython；请查阅生成的 Python stub，并确认所用语法受到支持。

`global` 用于同一 Script Type 内的内存共享，并在普通 reload 之间保留。`shared` 用于同一运行时内不同 Script Type 之间的内存共享。二者都不提供网络同步或存档持久化。

事务式候选加载失败时，会保留当前生效的脚本代次。任意游戏、Java、网络或文件副作用不在受管理的回滚保证之内。不能用 `/nekojs reload startup` 重新执行启动脚本。

<a id="events"></a>
## 3. 事件

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/events.md>)

通过 Event Group（事件组）订阅事件：`ServerEvents.tickPost(event => { ... })`。回调接收 Event Object（事件对象）；事件组本身是用于订阅的命名空间。

订阅之前，先在当前 Script Type 中解析事件组和事件。查看最终声明，确认回调参数、选择器和事件载荷。其他模组或脚本引擎中存在同名事件，不能证明当前实例也有该事件。

取消行为由具体事件决定。对于 NekoJS 的可取消监听器契约，返回 `true` 表示取消；不要臆造 `event.cancel()`。元数据中的可取消标记，不意味着每个事件对象都有这个方法。

保持回调工作量有界。通用辅助操作应使用已有 Binding，而不是把操作附加到事件对象上。

<a id="bindings-and-adapters"></a>
## 4. Binding 与输入适配器

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/bindings-and-adapters.md>)

Binding 是暴露给脚本的命名值，例如 `Item`。解析 Binding 时，应与短名称相同的 Java 类区分开。一个 Binding 可以同时组合辅助对象的实例方法和另一个类型的静态成员。

在当前受支持节点上，使用 `Item.of("minecraft:iron_sword[damage=2]")` 表示带组件的物品字符串。复制 `/nekojs hand` 的输出时，完整保留方括号后缀。旧的 `{...}` NBT 语法是另一种格式。解析具体组件值需要运行实例的注册表和 codec。

适配器允许特定 Java 输入接收额外的脚本数据形式。请检查参数实际发出的输入别名及适配器文档。某个 ItemStack 适配器接受字符串，不代表所有 String 参数都是物品输入，也不代表任意字符串都是合法物品值。

对暴露的 getter 使用可读 Bean Property（Bean 属性），对暴露的 setter 使用可写属性。只有 getter 时，不能据此进行赋值。带参数的操作仍然使用方法。字段与方法冲突时，只使用最终声明中给出的精确选择器拼写；某些投影方法会被有意省略。

查询中的 Type ID 标识某一模块中实际发出的类型拼写。本版本不证明不同别名之间的可赋值关系或语义等价性。

<a id="recipes-and-tags"></a>
## 5. 配方与标签

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/recipes-and-tags.md>)

配方脚本通常属于 SERVER。使用具体操作前，先解析 `ServerEvents.recipes` 并检查它的事件对象。优先采用当前实例中 `event.recipes.<namespace>.<type>` 的签名，而不是其他整合包的示例。

配方过滤器是数据对象。`id`、`type`、`input`、`output`、`and`、`or`、`not` 等键具有配方领域的含义；它们与脚本编写查询语言中的 `all`、`any`、`some` 谓词无关。

物品标签（`#namespace:path`）不是物品堆栈。请确认哪种适配器接受它。标签、配方 schema、资源格式和 reload 支持可能随版本与加载器变化。

服务器 reload 成功后，在实际运行的世界中验证配方变更。编辑器验证不能证明配方 codec 接受这些数据，也不能证明配方查看器已刷新。

<a id="registry-builders"></a>
## 6. 注册与 Builder

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/registry-builders.md>)

在 STARTUP 脚本中，通过实例公布的注册事件组和 Builder（构建器）声明注册内容。必需的标识参数放在配置回调之前：`event.create(arg1, build => { ... })` 只是调用形状示例，并不保证存在某个具体注册入口。

使用当前声明实际暴露的便捷入口或通用注册表契约。Builder 配置放在最后。对受支持的 Bean 属性进行赋值；动作和复合配置使用方法。

注册内容变更需要完整重启进程。注册项、Builder 类型、资源和 Co-registration（伴随注册）行为会因平台而异。不要推断 NeoForge 的流体 Builder 或 1.21.1 的附魔 Builder 在 Fabric 或 26.x 上也存在。

不要通过再创建一个标识符来绕过重复注册错误。应排查原始定义与加载顺序。

<a id="script-packs"></a>
## 7. 脚本包

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/script-packs.md>)

版本化脚本包拥有自己的 manifest、入口、Script Type 和模块上下文。修改前先检查实际 manifest；平铺的 `nekojs/*_scripts` 目录不能代替包内入口布局。

每个包有独立上下文。`global` 和 `shared` 遵循运行时的明确契约；导入和文件路径必须从包允许的来源解析。不要假设一个包的顶层 JavaScript 变量可以被另一个包访问。

已签名的脚本包和下载到服务器缓存中的脚本包，是不可变的脚本编写输入。请在受维护的源脚本包中修改，完成验证，并遵循正常打包／签名流程。如果编辑器元数据在签名前已经被包含，它也是签名内容的一部分。

使用已有的 `/nekojs packs` 命令检查安装布局。在建议操作前，阅读已安装版本的命令帮助，确认精确子命令；API 声明查询不描述命令树。

<a id="jsx-ui"></a>
## 8. JSX 客户端 UI

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/jsx-ui.md>)

UI 脚本属于 CLIENT。编写 JSX 前，确定当前配置的 JSX 模式，并查询当前 Ui Binding、组件、props 和生命周期声明。支持 JSX 不意味着支持浏览器 DOM API 或 Web 组件库。

遵守界面所有权、状态生命周期和回调清理要求。类型正确的元素树，不能证明渲染、焦点、布局或交互行为正确。这些结论需要实际客户端验收。

结构化索引不完整时，阅读匹配的 `@manual` UI 声明和关联源码位置的诊断信息。明确说明平台上不可用的能力。

<a id="diagnostics"></a>
## 9. 诊断

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/diagnostics.md>)

从真实故障、脚本路径、行号、Script Type、环境和稳定的 NEKO 诊断码开始排查。使用 `/nekojs view_all_errors` 和 `logs/nekojs` 下的对应输出。调查时保留原始原因。

区分解析／转译失败、声明错误、运行时异常、转换失败和特定世界中的验证问题。查询覆盖不完整时，脚本编写查询返回空结果，不能证明成员不存在。

本版本只暴露只读的脚本编写资料。它不授予访问任意日志、文件、客体脚本求值、reload、世界命令或检查能力的权限。请通过脚本作者的正常工作流程取得相关诊断文本。

<a id="validation"></a>
## 10. 验证

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/validation.md>)

1. **检查环境、Script Type，以及每个新使用 API 的来源。**完成标准：符号在当前声明中可以解析，或被明确报告为尚未确认。
2. **如果工作区有类型检查器，运行它。**完成标准：记录命令和退出状态；仅完成转译不等于严格类型检查。
3. **运行最小适用的 NekoJS 检查。**可以是服务器／客户端 reload、显式测试脚本，或启动注册所需的进程重启。完成标准：检查结果和原始错误输出。
4. **需要时，在真实世界／客户端中验证游戏特定效果。**完成标准：记录观察到的行为、所在节点，以及尚缺少的验收。
5. **交付修改后的脚本、受影响的行为、通过／失败／未运行的检查，以及剩余缺口。**

不要削弱严格检查、编造成功的游戏验收，也不要把成功生成 `.d.ts` 文件当成类型检查无误。随包示例的元数据说明了证据等级；隔离 mock 不能证明示例在 Minecraft 中执行过。

<a id="connections"></a>
## 11. MCP 连接：模组内与独立模式

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/connections.md>)

NekoJS 支持两种 MCP 模式，提供相同的只读脚本编写工具：**游戏运行时使用模组内 MCP 服务**；或使用独立的 [stdio 辅助程序](#stdio)读取最后一次导出的快照，包括游戏已关闭的情况。两种模式都不会自动配置或连接你的 AI 客户端。

### 11.1 模组内 MCP：游戏运行时连接

1. 在 `nekojs/config/ai.toml` 的 `[mcp]` 下设置 `enabled = true`（默认 false）。除非本机需要另一个端口，否则保留 `host = "127.0.0.1"` 和 `port = 8767`。`[http].enabled` 可以保持 false：它控制普通 JSON 路由，不是 MCP 端点。
2. 完整重启游戏进程。检查 `nekojs/ai/connection.json`：`listening` 必须为 true，`mcp` 必须包含端点，通常为 `http://127.0.0.1:8767/mcp`。连接期间保持游戏运行。
3. 在 AI 客户端添加 Streamable HTTP 服务器，使用该 URL，并配置 `Authorization: Bearer <token>` 请求头。在本机将 `<token>` 替换为 `nekojs/config/ai.token` 的内容；不要提交或分享该凭据。连接描述文件只提供 token 文件位置，不提供凭据本身。

客户端需要的是 **URL＋bearer 凭据**，不是 Java 命令、独立 JAR 路径或 `--game-dir` 参数。对于支持在 `mcpServers` 对象中使用 `url` 和 `headers` 的客户端，配置形状如下：

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

具体配置格式取决于客户端；部分客户端使用连接对话框或要求显式设置 HTTP 传输。选择支持 Streamable HTTP／JSON 响应及 **2025-11-25** 协议的客户端。普通 MCP 客户端会自行完成初始化、会话头和关闭流程；后面的原始 JSON-RPC 示例仅供调试或自定义客户端使用。

4. 确认工具已经列出，再调用 `nekojs_context`、`nekojs_capabilities` 和 `nekojs_schema`。API 查询需要一次成功的 **TypeScript Probe** 快照；仅开启 MCP 不会生成快照。使用签名前，检查 coverage 和 `isError`。

目前没有自动发现、GUI 启用开关或自动客户端配置。禁用的端点为 null；显式开启后仍然需要认证。如果需要离线访问或偏好由客户端持有独立进程，使用[独立 stdio](#stdio)；只有这种模式需要 JAR 和游戏目录路径。

### 11.2 从离线文档开始

文档导出默认开启，不扫描类，也不开端口。在游戏目录读取 `nekojs/ai/current.json`：其中的 `bundle` 和 `manifest` 路径相对于 `nekojs/ai/`。从当前文档包读取手册、Skill 和示例。读取 `nekojs/ai/environment.json`，了解实例和已配置的 API 快照目录。不要假设从其他实例复制来的文档包或快照适用于当前实例。

导出内容位于 `nekojs/ai/bundled/<bundleId>/`。缺失的 `nekojs/AGENTS.md` 和 `nekojs/ai/README.md` 会被创建；已有用户文件会保留。不要原地修改导出的文档包：同一 ID 的导出内容被改动后，系统会拒绝覆盖。关闭文档导出不会删除旧的导出内容。

### 11.3 配置参考：生成的默认值

`nekojs/config/ai.toml` 生成的默认内容如下。为便于与原文对照，示例中的英文注释也保持原样。

```toml
# English authoring documentation is exported without scanning classes or opening a port.
[documentation]
enabled = true
agents = "create-if-missing"

[api]
enabled = true

# Both protocols share one loopback listener and the same read-only query core.
[http]
enabled = false

[mcp]
enabled = false
host = "127.0.0.1"
port = 8767
```

开启普通 JSON 路由，设置 `[http].enabled = true`；开启 MCP，设置 `[mcp].enabled = true`；也可以同时开启。二者共用 `[mcp]` 下的端口。host 仅接受字面 IPv4 回环地址。端口必须是 1–65535 的整数；布尔值必须是 TOML 布尔值。`documentation.agents` 只接受 `create-if-missing` 或 `off`。`api.enabled = false` 会禁用 resolve／members／query／batch，不会禁用 context、capabilities、schema 或内嵌文档。

修改配置后重启游戏进程。普通脚本 reload 不会重新创建由根运行时持有的监听器。无效配置或端口冲突会导致脚本编写服务初始化被拒绝（NEKO-1019），而不是终止整个游戏。

### 11.4 进阶：普通 HTTP

重启后读取 `nekojs/ai/connection.json`。使用其中的 `http` 或 `mcp` 端点及 `listening` 状态；禁用的端点为 null。`tokenFile` 相对于 `nekojs/`，不是相对于描述文件所在目录。描述文件从不包含 token 本身。下面的 POSIX shell 示例假设使用默认 HTTP 端点、当前目录为游戏目录，并且已安装 curl；其他 shell 应采用等价的私密 token 读取方式。

```sh
TOKEN="$(tr -d '\r\n' < nekojs/config/ai.token)"
BASE='http://127.0.0.1:8767/api/v1/authoring'
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/context"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/capabilities"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/schema"
# Save a request from query-api.md as request.json and replace its discovered IDs.
curl --fail-with-body -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' --data-binary @request.json "$BASE/resolve"
unset TOKEN
```

GET 操作为 `context`、`capabilities`、`schema`；POST JSON 操作为 `resolve`、`members`、`query`、`batch`，均位于 `/api/v1/authoring/` 下。每个路由都要求 bearer 认证。URL 查询参数会被拒绝；范围与过滤条件放在 JSON 请求体中。解释结果前先阅读[查询请求](#query-api)。

### 11.5 进阶参考：手动通过 HTTP 使用 MCP

配置支持 **Streamable HTTP JSON 响应模式**、bearer 请求头及 **2025-11-25** 协议的 MCP 客户端。使用描述文件中的 `mcp` URL（默认 `http://127.0.0.1:8767/mcp`），不是普通 HTTP API 的基础地址。系统不会安装或修改全局 AI 客户端配置。

1. POST JSON-RPC `initialize` 请求，带上 `Content-Type: application/json`、`Accept: application/json, text/event-stream` 和 `Authorization: Bearer <token>`。例如：

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"local-authoring-client","version":"1"}}}
```

2. 读取返回的 `protocolVersion` 和响应头 `Mcp-Session-Id`。后续 POST 使用该会话、`MCP-Protocol-Version: 2025-11-25`，以及相同的 bearer／Accept／Content-Type 请求头。
3. POST 不带 ID 的 `{"jsonrpc":"2.0","method":"notifications/initialized"}`；成功时返回 HTTP 202，响应体为空。然后使用 `tools/list`、`resources/list` 和 `tools/call`。先从下面的调用开始：

```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"nekojs_context","arguments":{}}}
```

4. 读取 `nekojs_capabilities` 和 `nekojs_schema`，再调用 `nekojs_resolve`、`nekojs_members`、`nekojs_query` 或 `nekojs_batch`；参数对象与 HTTP 相同。检查 `structuredContent` 和 `isError`，而不只是看 HTTP 成功。`resources/read` 只接受 `resources/list` 返回的 URI（`nekojs://docs/<bundleId>/<path>`），不接受任意文件路径。
5. 完成后向 MCP URL 发送 DELETE，带上 bearer、会话和协议请求头。会话闲置 30 分钟后过期，最多同时保留 32 个会话。过期或重启后重新初始化。

stdio 由单独的[独立辅助程序](#stdio)提供，不是这个游戏内端点。没有旧式 SSE 端点、GET 事件流（GET /mcp 返回 405）、prompts、资源订阅或服务端主动操作。Accept 中的 event-stream 标记是传输要求，不承诺流式响应。若客户端强制要求上述不支持的功能，请使用文件或普通 HTTP。

### 11.6 HTTP 安全与预算

随机 bearer token 仅在开启监听器时创建，位置为 `nekojs/config/ai.token`，使用仅所有者可访问的 POSIX 权限或 Windows 所有者 ACL。不要将其放入版本控制、提示词、截图或共享日志。本机 bearer 认证不是 OAuth、TLS 或远程访问控制。不要转发端口，也不要通过公共代理暴露；浏览器 Origin 和 Host 校验不能替代凭据保密。能够读取 token 文件的受信任本地进程可以通过认证。

只接受 IPv4 回环对端。Host 必须匹配 localhost 或 127.0.0.1 及监听端口；如果提供 Origin，它必须匹配本地 HTTP origin。请求／响应上限为 64 KiB，同时最多进行两个交换，每个交换期限为 10 秒。响应超出预算时，减少字段或缩小页；较大的声明请在本地读取。关闭时会释放监听器、会话和查询缓存，并将所属连接描述文件标为离线。没有任何路由会执行脚本、写入脚本、触发 Probe 或 reload 游戏。

<a id="stdio"></a>
## 12. 通过 stdio 连接 AI 客户端

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/stdio.md>)

需要离线访问，或偏好由客户端持有独立进程时，使用独立 MCP stdio JAR。对于正在运行的游戏，[模组内 MCP](#connections)只需要显式开启端点，并在客户端配置 URL 和 bearer 凭据；不需要辅助 JAR 或游戏目录参数。两种模式都受支持。独立辅助程序在单独的 Java 进程中运行，读取所选游戏实例导出的脚本编写环境和 API 快照，游戏关闭时也能工作。它不接管游戏的 stdin，也不要求运行 HTTP 监听器。

### 12.1 前提条件

1. 辅助进程使用 **Java 21 或更新版本**。某个 Minecraft 版本可能要求游戏本身使用更新的 Java；这不改变辅助程序最低 Java 21 的要求。
2. 从匹配的 NekoJS 发布／构建中获取 [独立辅助 JAR](<../../common/build/libs/nekojs-1.1.0-preview3-authoring-stdio.jar>)。保持辅助程序和游戏模组版本一致。这是独立脚本编写产物，不是普通模组 JAR，也不是游戏启动器。源码构建中，`:common:authoringStdioJar` 生成[独立 JAR](<../../common/build/libs/nekojs-1.1.0-preview3-authoring-stdio.jar>)；`assemble` 包含该任务。
3. 至少启动一次安装了 NekoJS 的游戏，以导出 `nekojs/ai/environment.json`，并成功完成一次 **TypeScript Probe**，发布 API 快照。之后可以关闭游戏。仅导出手册不会创建 API 索引。

### 12.2 客户端配置

启动命令：

```sh
java -jar /absolute/path/nekojs-1.1.0-preview3-authoring-stdio.jar --game-dir /absolute/game/directory
```

向 MCP 客户端提供命令和分开的参数数组，不是 HTTP URL。对于采用 `mcpServers` 配置的客户端，将该条目合并到已有设置中；具体设置文件取决于客户端。

```json
{
  "mcpServers": {
    "nekojs": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/nekojs-1.1.0-preview3-authoring-stdio.jar",
        "--game-dir",
        "/absolute/game/directory"
      ]
    }
  }
}
```

替换两个示例路径。`--game-dir` 是**包含 nekojs/ 的游戏目录**，不是 nekojs 目录、世界存档、JAR 所在目录或本仓库。使用绝对路径，可以避免启动行为依赖 AI 客户端的工作目录。如果 Java 不在客户端 PATH 中，将 `command` 改为 Java 可执行文件的绝对路径。Windows 下可以使用 `D:/Minecraft/instance` 这样的 JSON 路径，避免反斜杠转义。含空格的路径仍然作为参数数组中的一个元素。

AI 客户端启动并持有这个辅助进程。开启 stdio 不需要修改 `ai.toml`，也没有需要配置的 token、URL、端口转发或 HTTP 会话头。NekoJS 不会自动安装客户端配置。

### 12.3 编写脚本前验证发现结果

1. 让客户端使用 **2025-11-25** 协议完成 MCP 初始化，并发送 `notifications/initialized`。与 HTTP 不同，stdio 只有进程内的生命周期，不使用 `Mcp-Session-Id` 请求头，也没有 DELETE 请求。
2. 调用 `nekojs_context`。确认环境符合预期、快照已就绪；导出内容缺失或无效时，应报告问题，不要猜测签名。
3. 调用 `nekojs_capabilities` 和 `nekojs_schema`。然后使用[查询请求](#query-api)中的参数对象调用 `nekojs_resolve`、`nekojs_members`、`nekojs_query` 或 `nekojs_batch`。检查 `structuredContent`、`isError` 和 coverage。
4. 只通过 `resources/read` 读取 `resources/list` 返回的资源。这些是已编目的内嵌文档，不是任意文件访问。完成后通过客户端停止辅助程序；EOF 会结束 stdio 连接。

辅助程序读取 `nekojs/ai/environment.json`，确定环境和已配置的 `apiSnapshotDirectory`，包括自定义 Probe 基目录。它遵循导出的 `apiEnabled` 标记，**不解析 ai.toml**。修改 API 配置后，重启游戏以导出新状态。每次 context／API 请求都会检查环境和当前指针；指针变化后，先校验摘要、环境和快照，再发布到查询会话。辅助程序不会运行 Probe、重新扫描游戏类、启动 Minecraft 或重新生成导出。修改模组、加载器，或修改到需要新声明的脚本后，重新运行游戏并成功执行 TypeScript Probe。游戏已停止时，元数据描述的是**最后一次导出**，不是当前已安装模组的实时清单。查询成功不等于完成游戏内验证。

辅助程序提供自己携带且已验证的英文文档包，不提供游戏导出文档目录下的任意文件。`context.documentation.bundleId` 标识辅助程序的文档包。保持发布版本一致；不要因为离线查询成功，就断言游戏当前加载了相同的模组。

### 12.4 启动问题与索引状态诊断

- `--help` 向 stderr 输出用法，并以 0 退出。`--game-dir <directory>` 必须提供且仅提供一次；相对路径基于客户端工作目录解析。参数无效，或环境导出缺失／无效时，向 stderr 输出诊断并以 2 退出；正常 EOF 以 0 退出。
- `not-ready`：在游戏中成功执行 TypeScript Probe。没有 API 快照时，仍可使用元数据／schema 和内嵌文档。
- `disabled`／`INDEX_DISABLED`：开启 API 并重启游戏，重新导出开启状态。不要求开启 HTTP 或 MCP 监听器。
- `unavailable`／`INDEX_UNAVAILABLE`：检查导出描述文件、快照路径和摘要；必要时重新生成 Probe。环境标识变化后，需要重启辅助程序。当前导出无效时，不会静默地把旧快照当成健康数据继续提供。

### 12.5 传输与安全

stdio 在 stdin／stdout 上使用 UTF-8、换行分隔的 JSON-RPC，每行一个对象，不接受 JSON-RPC 批次数组。请求和响应上限为 64 KiB。输入超限会关闭连接；响应超限时，应减少字段／页大小，或在本地访问声明。stdout 只用于协议，诊断写入 stderr。通过 MCP 客户端启动它，而不是通过游戏控制台；不要把 stderr 合并到 stdout，也不要插入 shell 欢迎文字。辅助程序不打开监听器、不创建 token、不写入游戏文件。确保游戏目录和辅助产物可信：辅助程序使用启动它的用户权限读取本地文件。这个只读脚本编写服务不是 AI 客户端独立文件系统工具的沙箱。

stdio 与[模组内 HTTP MCP](#connections)暴露相同的七个只读工具和内嵌文档资源。二者都不能执行脚本、写入脚本、触发 Probe、reload 游戏、提供 prompts 或订阅资源。没有 MCP 客户端时，可以直接使用本地文件。

<a id="query-api"></a>
## 13. 只读脚本编写查询

[英文原文](<../../common/src/main/resources/nekojs/authoring/handbook/query-api.md>)

### 13.1 准备匹配的快照

先读取 `context`、`capabilities` 和 `schema`。context 报告 `environmentId`、`snapshotId` 和 `indexState`。索引状态包括 `not-ready`、`ready`、`disabled`、`unavailable`。导出手册不会构建 API 索引。需要在游戏中成功运行 **TypeScript Probe**，具体用法查看已安装版本的 `/nekojs probe` 帮助；普通脚本 reload 不会重新生成索引。

默认指针为游戏目录下的 `.neko_probe/authoring/current.json`。使用自定义 Probe 基目录时，`nekojs/ai/environment.json` 中的 `apiSnapshotDirectory` 记录实际位置。Probe 先发布不可变、以摘要命名的 JSON 文件，再原子切换指针。运行时或独立 stdio 辅助程序在 context／API 请求时校验摘要、快照 ID 和环境，再加载。辅助程序可以在游戏关闭时读取最后成功导出的内容；它不会运行 Probe 或检查实时游戏状态。发布失败不会替换之前已验证的快照。不要手动编辑索引文件。

每个请求的 scope 都需要已发现的 `environmentId`、大写 `scriptType`（`STARTUP`、`SERVER`、`CLIENT` 或 `TEST`）和 `language: "typescript"`。仅支持 `memberView: "declared"`。发现 `snapshotId` 后，应显式固定它；只有想选择当前快照时，才可以省略。发送请求前，替换下面尖括号占位符。示例名称不保证每个实例都导出。

### 13.2 先解析，再检查成员

向 `resolve` POST，或调用 `nekojs_resolve`：

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared"},
  "name":"Item", "kind":"binding", "match":"exact",
  "select":["symbolId","name","kind","coverage","source"], "page":{"size":20}
}
```

检查所有候选项：类名和 Binding 名可能存在歧义。将选中结果的 `symbolId` 复制到 `members` 请求的 `scope.ownerSymbolId` 中。不要拿名称代替 ID。默认精确匹配；`prefix` 是需要显式指定的选项。向 `members` POST：

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared","ownerSymbolId":"<ownerSymbolId>"},
  "name":"of", "kind":"method", "arity":{"min":1,"max":1},
  "select":["symbolId","name","parameters","returnType","callArity","coverage"], "page":{"size":20}
}
```

arity 根据可接受实参数量区间是否重叠进行过滤，包括可选参数／可变参数行为；它不是原始形参数量过滤器。每个可调用记录都是一个实际发出的重载，不是合并的方法组。

### 13.3 在同一重载内关联参数

向 `query` POST。这个请求查找一个 `of` 重载，要求**同一个第一参数**的显示类型为 `string`。它不会拼接不同参数或不同重载的信息：

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared","ownerSymbolId":"<ownerSymbolId>"},
  "from":"members",
  "where":{"all":[
    {"field":"name","op":"eq","value":"of"},
    {"some":{"path":"parameters","where":{"all":[
      {"field":"position","op":"eq","value":0},
      {"field":"type.display","op":"eq","value":"string"}
    ]}}}
  ]},
  "select":["symbolId","name","parameters","returnType","coverage"], "page":{"size":20}
}
```

资源包括 `symbols`、`members`、`events`、`inputAdapters` 和 `declarations`。布尔组为 `all`、`any` 和 `not`；关联数组使用 `some`。先发现各资源特有的字段、运算符、可选返回字段和请求结构 schema，不要猜测。查询 declarations 时，选择 `path`、`sha256` 和 `text`；声明文本超过传输预算时，读取本地生成的声明文件。Type ID／显示字符串采取保守标识方式，不是跨模块或别名的可赋值性判定器。

### 13.4 阅读覆盖信息并安全续页

检查 `items`、`coverage.state`、`coverage.scope`、`coverage.unresolvedRecords`、`warnings` 和 `page`。即使位于 `not` 下，缺失元数据仍然是未知。精确过滤不会静默变成模糊过滤。覆盖不完整时，即使最后一页为空，也**不能**证明 API 不存在。

声明记录包含已实际展开输出的声明投影，不是将继承关系展平并完成泛型实例化的视图。手写声明、字面量重载和 capture view（捕获视图）可能只存在于声明文本中。Binding 的继承／冲突解析、事件回调签名和适配器别名尚未完整建立索引：请查阅它们的声明。不支持 Python 成员、`effective` 视图或 `assignableTo`。

翻页时，将 `page.cursor` 设为返回的 `page.nextCursor`，并保持操作、scope、过滤条件、字段选择和排序不变。页大小可以调整。只有在检查 `page.endOfResults` 和 `page.stopReason` 后，才判断是否结束。运行时保留最近三个已加载快照；游标在 15 分钟后或关闭时失效。遇到 `SNAPSHOT_EXPIRED`，重新读取 context 并重启查询，不要把其他快照的结果追加进来。

### 13.5 批量查询与预算

向 `batch` POST。子项是 query 对象，不是任意操作；它们共享外层 scope 和固定快照，不能覆盖 scope：

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared"},
  "queries":[
    {"from":"symbols","where":{"field":"name","op":"eq","value":"Item"},"select":["symbolId","name","coverage"]},
    {"from":"events","where":{"field":"group","op":"eq","value":"ServerEvents"},"select":["symbolId","name","group","coverage"]}
  ]
}
```

分别检查每个 `results` 项的 `status`，以及 `result` 或 `error`。限制为：批量大小 1–10，页大小默认 20／最大 100，最多 32 个返回字段、三个排序键、32 个谓词节点、深度 4，每个 `in`／`notIn` 最多 100 个值，HTTP 请求体／响应上限 64 KiB。stdio 的每个 JSON-RPC 输入／输出帧也限制为 64 KiB；应缩小字段选择／页大小，为 MCP 信封预留空间。未知字段、错误类型和不支持的运算符都会报错，不会被忽略。

`INDEX_NOT_READY` 表示需要成功的 TypeScript Probe；`INDEX_DISABLED` 表示需要开启 API 并重启游戏，导出变化后的状态；`INDEX_UNAVAILABLE` 表示需要检查本地快照／配置和日志。`SCOPE_CONFLICT` 表示应重新发现当前实例的环境。`RESPONSE_TOO_LARGE` 表示应减少字段／页大小，或读取本地文件。不要通过假定签名来绕过错误。游戏运行时使用[模组内 MCP](#connections)，访问离线导出快照时使用[独立 stdio 辅助程序](#stdio)。二者暴露相同查询参数；普通 JSON HTTP 是额外的进阶选项。本服务不能执行 JavaScript、反射任意 Java、写入脚本或 reload 某个脚本代次。
