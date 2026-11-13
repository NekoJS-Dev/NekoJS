<!-- wiki-page: ai-support-protocol; locale: cn -->

> **中文** · [English](ai-support-protocol_us)

<a id="wiki-section-1"></a>
# AI 查询协议与底层接口参考

本页面向**第三方工具作者、IDE 插件开发者或需要手动调试 MCP / HTTP 报文的开发者**。如果你只是想用 Cursor、Claude Code、Copilot 辅助写整合包脚本，直接看 [AI 辅助脚本编写](ai-support_cn) 即可，主流 AI 客户端会自动构造以下全部请求。

<a id="wiki-section-2"></a>
## 1. API 检索工具与请求载荷

无论通过游戏内 MCP、离线 `authoring-stdio.jar` 还是纯 HTTP 接口，底层都共享同一套只读检索模型。客户端通常先调用 `nekojs_context`、`nekojs_capabilities`、`nekojs_schema` 获取当前实例的 `environmentId`、`snapshotId` 与可查询字段定义，再调用以下四个核心检索工具：

| 工具名 | HTTP 端点 | 用途 |
|---|---|---|
| `nekojs_resolve` | `POST /resolve` | 按名称精确或模糊查找全局符号（如绑定 `Item` 或类） |
| `nekojs_members` | `POST /members` | 根据 `ownerSymbolId` 列出某个符号下的方法、属性及各重载签名 |
| `nekojs_query` | `POST /query` | 使用组合条件树（`all` / `any` / `not` / `some`）筛选符号、成员或事件 |
| `nekojs_batch` | `POST /batch` | 在同一个 `scope` 下一次性批量提交 1～10 个子查询 |

<a id="wiki-section-3"></a>
### 典型调用链：`resolve` → `members`

第一步，用 `resolve` 查找名为 `Item` 的绑定（注意同名绑定和类可能同时存在，需根据 `kind` 区分；`scriptType` 必须大写，`language` 目前固定为 `"typescript"`）：

```json
{
  "scope": {
    "environmentId": "<从 nekojs_context 获取>",
    "snapshotId": "<从 nekojs_context 获取>",
    "scriptType": "SERVER",
    "language": "typescript",
    "memberView": "declared"
  },
  "name": "Item",
  "kind": "binding",
  "match": "exact",
  "select": ["symbolId", "name", "kind", "coverage", "source"],
  "page": { "size": 20 }
}
```

第二步，取出目标结果的 `symbolId`（注意不能直接填名字字符串），填入 `scope.ownerSymbolId` 查询它接收 1 个参数的 `of` 方法重载（`arity` 会自动计入可选参数与变长参数，每个重载独立返回一行）：

```json
{
  "scope": {
    "environmentId": "<environmentId>",
    "snapshotId": "<snapshotId>",
    "scriptType": "SERVER",
    "language": "typescript",
    "memberView": "declared",
    "ownerSymbolId": "<上一步拿到的 symbolId>"
  },
  "name": "of",
  "kind": "method",
  "arity": { "min": 1, "max": 1 },
  "select": ["symbolId", "name", "parameters", "returnType", "callArity", "coverage"],
  "page": { "size": 20 }
}
```

<a id="wiki-section-4"></a>
### 高级条件筛选：`query` 与 `batch`

`query` 支持对 `symbols`、`members`、`events`、`inputAdapters`、`declarations` 五张逻辑表做结构化过滤（具体支持的字段和操作符以 `nekojs_schema` 返回值为准，写错字段名会直接报错拒绝）。当需要限定**同一个参数对象**同时满足多个条件时，使用 `some`：

```json
{
  "scope": {
    "environmentId": "<environmentId>",
    "snapshotId": "<snapshotId>",
    "scriptType": "SERVER",
    "language": "typescript",
    "memberView": "declared",
    "ownerSymbolId": "<ownerSymbolId>"
  },
  "from": "members",
  "where": {
    "all": [
      { "field": "name", "op": "eq", "value": "of" },
      {
        "some": {
          "path": "parameters",
          "where": {
            "all": [
              { "field": "position", "op": "eq", "value": 0 },
              { "field": "type.display", "op": "eq", "value": "string" }
            ]
          }
        }
      }
    ]
  },
  "select": ["symbolId", "name", "parameters", "returnType", "coverage"],
  "page": { "size": 20 }
}
```

如果需要一次执行多条查询，使用 `batch` 共享外层 `scope`（返回后需逐条检查每个子结果的 `status`）：

```json
{
  "scope": {
    "environmentId": "<environmentId>",
    "snapshotId": "<snapshotId>",
    "scriptType": "SERVER",
    "language": "typescript",
    "memberView": "declared"
  },
  "queries": [
    { "from": "symbols", "where": { "field": "name", "op": "eq", "value": "Item" }, "select": ["symbolId", "name", "coverage"] },
    { "from": "events", "where": { "field": "group", "op": "eq", "value": "ServerEvents" }, "select": ["symbolId", "name", "group", "coverage"] }
  ]
}
```

<a id="wiki-section-5"></a>
### 配额、分页与覆盖率契约

- **分页游标**：默认每页 20 条，最大 100 条。翻页时将响应中的 `page.nextCursor` 原样填入下一次请求的 `page.cursor`，其余过滤条件保持不变（游标闲置 15 分钟后过期）。
- **体积与字段限制**：`select` 最多选 32 个字段；`batch` 单次允许 1～10 条子查询；单次请求体与响应体上限均为 **64 KiB**。
- **能力边界**：仅提供只读索引查询（目前仅索引 TypeScript 视图，不含 Python 成员视图），不提供任意代码执行、文件写入或任意 Java 反射。
- **覆盖率（`coverage`）判定**：HTTP 200 或调用成功不代表索引 100% 完整。手写 `.d.ts` 声明、部分继承链与特殊事件签名可能未被完全索引；当 `coverage` 指示不完整时，空结果不代表该 API 不存在，客户端应回退读取磁盘上的 `.d.ts` 文件。

<a id="wiki-section-6"></a>
## 2. 离线 `authoring-stdio.jar` 进程契约

自行集成离线 stdio 进程时，注意以下进程级约定：

- **标准流隔离**：`stdout` 专用于 JSON-RPC 协议帧输出，切勿在包装脚本中向 `stdout` 打印任何额外日志。
- **启动参数与退出码**：默认读取 `--game-dir` 下 `nekojs/config/ai.toml` 中的 `[output].directory` 定位快照；如需覆盖可显式传 `--ai-dir <游戏目录内相对路径>`（传 `--help` 打印用法）。若参数非法或找不到导出目录，进程会以 **退出码 2** 终止并将原因写入 `stderr`。

<a id="wiki-section-7"></a>
## 3. 纯 HTTP JSON 接口

在 `nekojs/config/ai.toml` 中设 `[http].enabled = true` 并重启游戏后，可通过普通 HTTP 客户端直接访问 `/api/v1/authoring/*`（与 MCP 共用 `[mcp].port`，所有请求均须携带 `Authorization: Bearer <ai.token>`，不支持实时控制操作）：

| 请求方式 | 路径（相对于 `/api/v1/authoring/`） |
|---|---|
| `GET` | `context`、`capabilities`、`schema` |
| `POST`（`Content-Type: application/json`） | `resolve`、`members`、`query`、`batch`、`errors`、`operation_status` |

> 所有筛选参数必须放在 POST 的 JSON 请求体中，服务端不解析 URL Query 参数。

```sh
TOKEN="$(tr -d '\r\n' < nekojs/config/ai.token)"
BASE='http://127.0.0.1:8767/api/v1/authoring'

curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/context"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/capabilities"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" "$BASE/schema"
curl --fail-with-body -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' --data-binary @request.json "$BASE/resolve"

unset TOKEN
```

<a id="wiki-section-8"></a>
## 4. 手动调试 Streamable HTTP MCP 握手

游戏内 MCP 端点遵循 **MCP 2025-11-25** 规范（Streamable HTTP 纯 JSON 响应模式；不支持旧版 SSE、不支持 `GET /mcp` 事件流——会返回 `405`，也不支持 `prompts` 与资源订阅）。手动调试流程如下：

1. **发送 `initialize`**：向 `http://127.0.0.1:8767/mcp` 发送 `POST`，请求头带上 `Authorization: Bearer <密钥>`、`Content-Type: application/json` 及 `Accept: application/json, text/event-stream`：
   ```json
   {
     "jsonrpc": "2.0",
     "id": 1,
     "method": "initialize",
     "params": {
       "protocolVersion": "2025-11-25",
       "capabilities": {},
       "clientInfo": { "name": "local-authoring-client", "version": "1" }
     }
   }
   ```
2. **保存会话头**：记录响应头返回的 `Mcp-Session-Id`。后续每一个请求都必须同时携带 `Mcp-Session-Id` 和 `MCP-Protocol-Version: 2025-11-25`。
3. **确认初始化完成**：发送 `{"jsonrpc":"2.0","method":"notifications/initialized"}`，收到 `HTTP 202` 后即可调用工具或读取 `resources/list` 中列出的文档资源：
   ```json
   {
     "jsonrpc": "2.0",
     "id": 2,
     "method": "tools/call",
     "params": { "name": "nekojs_context", "arguments": {} }
   }
   ```
   > 判断工具执行结果请检查响应载荷中的 `structuredContent` 与 `isError`，不要只看外层 HTTP 状态码。
4. **关闭会话**：调试完毕后向 `/mcp` 发送 `DELETE` 释放会话（服务端最多同时保留 32 个会话，闲置 30 分钟自动过期）。

<!-- wiki-nav -->

---

[上一篇: JSX 进阶控件与工作区](jsx-client-ui-advanced_cn) · [目录](Home)
