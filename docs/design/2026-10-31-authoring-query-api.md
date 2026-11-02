# NekoJS 精确作者 API 查询设计

日期：2026-10-31

状态：设计提案，尚未实现。本文供维护者审阅，使用中文；未来 API schema、工具描述、Skill 和内嵌使用说明使用英文。

关联：[内嵌 AI 支持总方案](2026-10-31-ai-support-plan.md)。本设计定义共享查询核心，普通 HTTP、可选 CLI 与 MCP 使用同一套语义；不要求采用 Elasticsearch、OData 或 GraphQL 服务。

## 1. 设计结论

使用 **明确的环境范围 + 结构化条件树 + 类型化字段目录 + 返回字段选择 + 固定快照游标**。

为 AI 提供两级入口：简单需求使用参数明确的 `resolve` / `members`；复杂需求使用 JSON `query`。简单入口编译成同一个查询模型，避免实现两套过滤逻辑。

第一版不开放 SQL、不执行 JavaScript 表达式、不要求 AI 拼接 OData/filter 字符串，也不运行任意 Java 反射。搜索负责发现候选；精确查询负责在已知范围内选取满足合同的记录。二者的匹配方式明确区分。

## 2. 成熟参考：借鉴什么，保留哪些差异

| 官方参考 | 可借鉴的设计 | 不直接照搬的部分 |
|---|---|---|
| Google AIP-160 Filtering | 字段化条件、类型检查和过滤参数验证 | 其字符串语法含搜索倾向，OR 优先级也不适合作为本方案默认；使用显式 JSON 树 |
| Google AIP-158 Pagination | 有界页大小、opaque page token、翻页参数一致性 | 本项目额外绑定声明 snapshot，防止重载或更新后跨页混合环境 |
| Microsoft Graph 查询参数 | 过滤、字段选择和关系展开分别表达 | 不实现完整 OData；第一版用显式后续查询代替无限关系展开 |
| Elasticsearch bool query | `must`/`filter`/`must_not`/`should` 展示布尔组合与评分的分工 | 本项目 `all`/`any`/`not` 更直接；过滤不隐含模糊相关性评分 |
| Elasticsearch nested query | 同一数组元素内组合多个条件，避免扁平化误匹配 | 不引入其存储引擎；采用独立重载记录及参数 `some` 约束 |
| JSON Schema | 类型化请求、枚举、嵌套结构和未知字段校验 | schema 通过不等于 API 可用或脚本能运行，仍需语义校验 |

这些参考证明相关设计已有成熟使用方式，不证明本项目已实现或应直接依赖相应产品。精确语义由下文冻结的本地合同定义。

## 3. API 入口

拟议普通 HTTP 路径如下，尚未实现：

| 方法和路径 | 用途 |
|---|---|
| `GET /api/v1/authoring/capabilities` | 支持的查询资源、字段、运算符、预算和 schema 版本 |
| `GET /api/v1/authoring/schema` | 请求/响应 JSON Schema 与字段说明 |
| `GET /api/v1/authoring/context` | 当前实例、快照、语言、Script Type 与完整性 |
| `POST /api/v1/authoring/resolve` | 精确解析 Binding、类或成员名；歧义返回候选 |
| `POST /api/v1/authoring/members` | 简单成员查询，参数式入口 |
| `POST /api/v1/authoring/query` | 多条件结构化查询，复杂入口 |
| `POST /api/v1/authoring/batch` | 同一快照内的有限批量只读查询 |

POST 用于承载复杂 JSON 查询，不执行脚本或修改游戏状态。查询资源与操作权限仍需鉴权，参数合法也不能越过权限。

MCP 对应 `nekojs_resolve`、`nekojs_members`、`nekojs_query` 等薄适配；CLI 将命令参数转换为同一个请求。由同一服务提供 HTTP 与可选 MCP，不建设两份索引。

原有发现式 `search` 可以保留，但不与精确 `resolve` 混用。简单查询已经满足时，AI 无需学习完整条件树。

## 4. 范围先于条件

所有复杂查询带 `scope`，限定环境和解释上下文：

```json
{
  "scope": {
    "environmentId": "<context返回的实例ID>",
    "snapshotId": "<context返回的快照ID>",
    "scriptType": "SERVER",
    "language": "typescript",
    "ownerSymbolId": "<resolve返回的Binding或类ID>",
    "memberView": "effective"
  }
}
```

- environment 包含 MC、加载器、插件和模组身份。不能把另一环境的字段条件当作当前实例可用性证明。
- snapshot 在首个请求确定并在后续固定；只在 context 或首请求中将“当前”解析成具体 ID，翻页不重新解析。
- `ownerSymbolId` 指明确的 Binding、类、Builder 或事件对象。`Item` Binding 与 Java `Item` 类是不同符号。
- `memberView: declared` 只查该所有者自己声明的成员；`effective` 查脚本实际可见成员，完成继承、覆盖和泛型替换。
- 省略 owner 只允许在端点声明支持的全局索引范围查询；必须有明确的结果资源与预算。
- MC/loader 等身份与 scope 冲突时返回范围错误，不通过“零结果”掩盖，也不自动换环境。

跨版本查询应显式选择两个可用快照，由比较接口处理；不能在一份快照中对版本字段筛选出不存在的历史 API。

## 5. 查询记录与精度

### 5.1 一个方法重载就是一条记录

`from: members` 中每条可调用成员记录对应一个重载。字段/属性各自独立，不把多个重载的参数和返回值合并成一条大对象。

例如存在：

```text
convert(String) -> ItemStack
convert(Number) -> String
```

查询“接受 String 且返回 String”必须不命中。若把参数和返回值按方法组分别汇总，就可能产生假结果。

界面需要合并重载时，在过滤后按 `callableGroupId` 展示，并保留 `matchedOverloadIds`。分组不是第一版的必要能力，也不能把未匹配重载冒充匹配结果。

### 5.2 按作者面查询

每条记录区分：

- 脚本名、必要的显式选择器、Java 来源名（可靠时）。
- 当前 owner 与实际声明者。
- 字段、Bean Property、方法、构造器、Binding 等种类。
- 实际作者面参数/返回类型及其输入别名，与 Java host 类型的差异。
- 固定参数数量与允许调用的最少/最多参数；可变参数不能只按数组长度判断。
- 适用语言、Script Type、公开性、弃用、覆盖完整性。
- 文档、示例和验证证据。没有这些信息时保留未知，不虚构说明。

`effective` 视图的成员 ID 必须绑定其 owner 与类型实例化上下文。`Box<String>.get()` 不应复用一条没有上下文的 `Box<T>.get()` 记录来宣称返回值确定。

### 5.3 按资源公开可查询字段目录

字段不是一个任意字符串袋子。schema/capabilities 应按资源列出支持的 field、数据类型、运算符、是否可能未知、计算成本及例子。候选目录如下，正式字段以实现审计后冻结的 schema 为准：

| 资源或范围 | 候选字段 | 能表达的精确条件 |
|---|---|---|
| symbols | kind、name、qualifiedName、package、公开导出状态 | 只查某包前缀内、名字精确相同的公开类，而不是同名 Binding |
| members | name、kind、isStatic、declaringType.id、deprecated | 查指定类型自己声明或继承来的静态方法，排除已知弃用项 |
| properties/fields | readable、writable、type.id | 只查可写数字属性，不把只读 Bean 当 setter 使用 |
| callables | callArity、参数记录、returnType、类型参数 | 查第 N 个参数、返回类型、可选性和数量同时满足的重载 |
| events | group、name、Event Object 类型、已知取消语义 | 查某组中当前 Script Type 可订阅且允许取消的事件 |
| inputAdapters | 适配器 ID、已声明输入形状、优先级 | 查接受指定转换合同的参数，而非只看 Java 类型名字 |
| provenance | 可靠的插件/模组来源、声明来源、验证级别 | 限定某贡献来源，或只取有运行证明的示例 |

Java 可见性、原生静态方法、脚本 Binding 的实例方法暴露、JS 属性访问是不同概念，字段文档需说明采用哪个视图。来源或验证状态未知时保留 unknown，不根据包名前缀猜模组，也不把缺失证明当成验证失败。

数组字段继续通过 some 访问，例如 inputAdapters 内的 id；不增加一条会扁平化数组关联的快捷路径。

## 6. 参数式简单查询

先通过 resolve 精确找到符号，再查询成员：

```json
{
  "scope": {
    "environmentId": "<environmentId>",
    "snapshotId": "<snapshotId>",
    "scriptType": "SERVER",
    "language": "typescript"
  },
  "kind": "binding",
  "name": "Item",
  "match": "exact"
}
```

简名命中多个符号时返回候选列表，不任意选择。字段名、符号名默认区分大小写；是否允许不区分大小写的发现搜索由单独参数决定。

`members` 简单入口示例：

```json
{
  "scope": {
    "environmentId": "<environmentId>",
    "snapshotId": "<snapshotId>",
    "scriptType": "SERVER",
    "language": "typescript",
    "ownerSymbolId": "<Item Binding的symbolId>",
    "memberView": "effective"
  },
  "kind": "method",
  "name": "of",
  "match": "exact",
  "arity": {"min": 1, "max": 2},
  "select": ["symbolId", "signature", "parameters", "returnType", "inputAdapters"],
  "page": {"size": 10}
}
```

简单入口的 arity 条件定义为：该重载允许的调用参数数量与请求闭区间存在交集，而不是静态形参数量必须同时等于 1 和 2。检查某次恰好传入 N 个参数的调用时使用 `min == max == N`；这仍允许一个能接收其他数量的可选/可变参数方法命中。若要求重载只能接受 N 个参数，应同时精确匹配记录的调用上下界；需要查询声明形参长度时使用另一个显式字段。

## 7. 复杂查询：结构化 JSON 条件树

### 7.1 组合规则

```text
Predicate := all([...]) | any([...]) | not(Predicate)
           | comparison(field, op, value)
           | some(arrayPath, Predicate)
```

JSON 每个节点只能选择一种结构，不允许 `all` 和比较字段混写。`all`/`any` 至少包含一项。值保持 JSON 原类型，不把布尔、数字和字符串互相猜测转换。

第一版支持：

| 运算符 | 适用字段 | 含义 |
|---|---|---|
| `eq` / `ne` | 枚举、标识符、布尔、标量 | 精确相等/不等，不隐含模糊搜索 |
| `in` / `notIn` | 相同类型的标量集合 | 有界集合匹配 |
| `lt` / `lte` / `gt` / `gte` | 数字 | 数值范围 |
| `prefix` | 已声明支持的字符串字段 | 名称或限定名的字面前缀 |
| `hasValue` | 可空字段 | 字段有已知非空值；不同于字段信息未知 |

不默认支持正则、任意函数、SQL 或递归表达式。复杂类型关系是独立能力，不能偷偷用字符串 contains 代替。

### 7.2 同一参数内关联多个条件

如下请求表示：查 Item 的某个 `of` 重载，它同时满足返回类型条件、允许一个参数，并且第 0 个参数具有指定类型。占位 ID 均需先查询获得。

```json
{
  "scope": {
    "environmentId": "<environmentId>",
    "snapshotId": "<snapshotId>",
    "scriptType": "SERVER",
    "language": "typescript",
    "ownerSymbolId": "<Item Binding的symbolId>",
    "memberView": "effective"
  },
  "from": "members",
  "where": {
    "all": [
      {"field": "kind", "op": "eq", "value": "method"},
      {"field": "name", "op": "eq", "value": "of"},
      {"field": "callArity.min", "op": "lte", "value": 1},
      {"field": "callArity.max", "op": "gte", "value": 1},
      {"field": "returnType.id", "op": "eq", "value": "<ItemStack作者面类型ID>"},
      {
        "some": {
          "path": "parameters",
          "where": {
            "all": [
              {"field": "position", "op": "eq", "value": 0},
              {"field": "type.id", "op": "eq", "value": "<目标参数作者面类型ID>"}
            ]
          }
        }
      }
    ]
  },
  "select": ["symbolId", "signature", "parameters", "returnType", "inputAdapters", "provenance"],
  "orderBy": [{"field": "symbolId", "direction": "asc"}],
  "page": {"size": 10},
  "explain": true
}
```

`some` 的内部字段只指当前参数元素，不能跳到其他参数或父记录；数组不能通过隐式扁平字段比较绕过该规则。需要两个不同位置分别满足条件时，写两个独立 `some`。

特别区分：`some(parameters, not(type == X))` 表示存在一个不是 X 的参数；`not(some(parameters, type == X))` 表示没有任何参数是 X。零参数方法对后者成立、对前者不成立。

可变参数的最大调用数量采用专用 `arityUpperBound` 类型：有限值为 `{"kind":"finite","value":N}`，无上界为 `{"kind":"unbounded"}`，不用 JSON Infinity 或 null。此类型的比较运算以非负整数为右值：unbounded 大于任意有限值，有限值按 value 比较；这是字段目录中显式声明的专用语义，不是任意对象到数字的隐式转换。未知上界另记为 unknown，不能当作 unbounded。简单入口把调用数量交集编译成上述比较，响应 schema 同时公开该字段的表示与运算符合同。

### 7.3 精确查找与模糊发现分开

名称 `eq` 和类型 ID 比较永远精确。发现式 `search` 可以显式请求前缀/文本相关性，但不得因精确查询零命中而自动开启纠错或替换类型。

如果需要按说明搜索，可在 query 的独立 text 部分声明 query、字段与模式；只在过滤后候选内评分，返回 `matchMode` 和排序依据。第一版可不实现此组合，直接使用 search → query 两步流程。

## 8. 类型与适配器查询

单纯 `returnType.name == "ItemStack"` 不足以处理同名类、泛型和脚本投影。建议 type reference 至少保留稳定快照内 ID、显示文本、种类、泛型参数和可取得的 Java 来源。

| 能力 | 第一版建议 | 限制 |
|---|---|---|
| 精确作者面类型 ID | 支持 | ID 来自查询，不由 AI 拼接 |
| 原始 Java 类型精确匹配 | 有来源时支持独立字段 | 不替代作者面类型 |
| 输入 Adapter ID / 已声明形状 | 支持 | 证明合同，不证明任意具体值能被当前世界解析 |
| 对象可赋值性 | 延后或显式声明不可用 | 需语言类型检查器，不能用父类名字符串推断全部情况 |
| 接受某个具体物品组件字符串 | 独立受控验证入口 | 可能依赖在线注册表；离线只能做语法/已知形状检查 |
| 任意泛型/通配符关系查询 | 延后 | 捕获、方差、递归与代理转换不能简化成名字匹配 |

例如“哪些方法接收字符串形式 ItemStack”可先按 `inputAdapters` 中的 ItemStack 适配器过滤，返回其真实形状与优先级。它不等价于“所有字符串都能成功转换”，也不代表所有 String 参数都是 ItemStack 输入。

## 9. 字段裁剪、关系与批量查询

`select` 只影响返回内容，不影响过滤范围、权限或匹配结果。用户选择很少字段时，仍始终返回身份、覆盖状态、游标等结果 envelope。

默认不展开整张类型图。成员记录返回相关 type IDs，AI 用 batch 取需要的类型详情。第一版 batch 限 10 个查询、固定同一 snapshot，每个子结果独立标记成功/失败；一个失败不让其他结果伪装成功或全部丢失。

后续若增加 `expand`，显式限制深度、总节点和字节数，并保留 continuation。查询响应不得因关系展开绕过权限或返回大小预算。

## 10. 响应、完整性与可解释性

响应外层建议包含：

```json
{
  "schemaVersion": "1",
  "requestId": "<requestId>",
  "environmentId": "<environmentId>",
  "snapshotId": "<snapshotId>",
  "status": "ok",
  "coverage": {"state": "complete", "scope": "public-authoring-surface"},
  "items": [],
  "page": {"nextCursor": null, "endOfResults": true, "stopReason": null},
  "warnings": []
}
```

这是“完整公开查询范围内确实零匹配”的合法示例，不代表能查询被隐藏的全部 Java 类。

- 扫描或结构化记录不完整时返回 `incomplete`，说明缺失来源和受影响能力；不以空数组证明不存在。
- 元数据未知不等于 false、null 或不匹配。若影响查询结论，返回未决状态与可取得的未决数量，数量不可知时不伪造 0。
- not/不等比较也不能把未知值变成确定满足条件；未知的相关记录需要披露。
- 不泄露无权限查看的隐藏符号、其计数或存在性。
- API 结果区分公开范围完整性与分页是否结束；第一页没显示某个重载不代表它不存在。

条件使用 true/false/unknown 三值：all 有 false 则 false，否则有 unknown 则 unknown；any 有 true 则 true，否则有 unknown 则 unknown；not 保留 unknown。some 有 true 元素则 true，完整数组全部为 false 则 false；无 true 且存在 unknown 元素或未采集元素时为 unknown。items 只收确定匹配；未决记录通过独立完整性信息披露，不混入精确结果。`hasValue` 遇到已知 null 为 false、已知非空为 true、未知仍为 unknown。

`explain` 返回解析后的 owner、有效 scope、实际使用字段、支持状态及相关告警。对于零结果，可指出范围冲突、索引缺口或条件过严的已知事实；不自动放宽条件，不泄露原始内部存储计划，也不编造最接近的 API。

## 11. 分页、排序和查询预算

- 游标绑定 schema、environment、snapshot、scope、条件、select 和排序的规范化摘要，以及过期时间。服务端签名或保存，客户端不可自行修改。
- 后续页除游标和允许调整的页大小外必须保持请求一致；参数变化拒绝，不从第一页重来。
- 每次排序末尾追加唯一记录 ID，保证同值稳定。精确查询默认按稳定 ID 排序；文本搜索使用显式评分再以 ID 打破平局。
- snapshot 被清理时返回 `SNAPSHOT_EXPIRED`，要求重新获取上下文；不能自动换到新世界或最新快照继续翻页。
- 默认不计算昂贵的全量 total。需要计数时单独请求，并声明精确/估算/未知；不能把当前页大小作为总数。

原型预算建议：页大小默认 20、最大 100；最多 32 个条件节点、4 层条件树、in 集合最多 100 项、batch 最多 10 项、单次响应最多 64 KiB。它们需按真实树校准，不是已经通过测试的性能承诺。

索引和查询在冻结数据上执行，不在 Tick 反射。超时或预算中止必须返回明确原因；如支持部分结果，`endOfResults` 不能为 true，后续不能将其宣传为完整查询。

## 12. 错误处理与 schema 发现

无效字段、运算符、值类型、选择字段、排序字段、预算或不支持的类型关系均明确报错，不能忽略。错误带英文 code/message、JSON pointer 和可修正选项。

例如：

```json
{
  "code": "UNSUPPORTED_QUERY_OPERATOR",
  "message": "Operator 'assignableTo' is not supported for this field.",
  "path": "/where/op",
  "supportedOperators": ["eq", "ne", "in", "notIn"]
}
```

拟议 HTTP 映射：schema/语法错误 400，合法结构但不支持的语义 422，快照/范围冲突 409，过期快照 410，限流 429，依赖或索引暂不可用 503。认证和权限遵循既定接入合同。传输错误与业务完整性状态分开，不能因 HTTP 200 就当查询证明完整。

schema 需要按 `from` 限定字段和操作列表；使用判别联合与未知属性拒绝，JSON 解析层还需处理重复键。schema 合法后继续验证字段权限、type IDs 所属快照、owner 归属和操作预算。

capabilities 提供工具自己可读的字段目录、例子及限制；AI 先发现能力，再构造复杂查询。API 文档和客户端 Skill 中给出小请求模板，常见任务优先使用简单端点。

## 13. 实现边界与验收

共享核心流程：请求校验 → scope 解析 → 条件编译 → 索引候选 → 同记录过滤 → 稳定排序/分页 → 选字段 → 结果与来源。

不引入数据库只是为了拥有 query DSL。第一版可用按 owner、kind、name、type ID 建的不可变内存索引；profiling 证明需要后再评估持久化索引。HTTP/MCP/CLI 不各自实现语义。

第一版优先：精确 resolve、members 简单入口、标量过滤、all/any/not、参数 some、字段选择、快照游标、覆盖状态。随后再做 batch 与 explain；可赋值性、深层 expand、任意泛型和复杂聚合不挤进首版。

必须覆盖的测试：

1. 同名 Binding 与类、同名不同包类不会静默选错。
2. 不同重载的条件不能拼成假匹配；不同参数位置/类型也不能跨元素拼接。
3. some(not) 与 not(some) 含零参数情况语义正确。
4. declared/effective、覆盖成员和泛型替换与最终作者面一致。
5. Optional/varargs 的调用数量与声明形参数量区分正确。
6. 精确查询零命中不模糊回退；未知字段/未知运算符不被忽略。
7. partial/unknown/hidden 的结果不会证明错误的“不存在”。
8. 翻页遇到相同排序值、参数变化、快照更新或清理均正确处理。
9. select、batch 和未来 expand 不能绕过权限和预算。
10. 同一请求经 HTTP、MCP 和可选 CLI 得到相同结果与覆盖状态。

以上均是待实现验收，不是当前测试结果。本轮只写设计，不创建查询 endpoint 或新增依赖。

## 14. 一手参考

以下链接是本设计核查的官方资料；本文采用思想而非宣称完整兼容对应规范：

```text
Google AIP-160 — Filtering
https://google.aip.dev/160
Google AIP-158 — Pagination
https://google.aip.dev/158
Microsoft Graph — Query parameters
https://learn.microsoft.com/en-us/graph/query-parameters
Elasticsearch — Boolean query
https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-bool-query
Elasticsearch — Nested query
https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-nested-query
JSON Schema — Objects
https://json-schema.org/understanding-json-schema/reference/object
```

补充核查见 [一手参考核查](../research/2026-10-31-query-api-references.md)。正式实施前冻结英文 schema、字段目录、错误码及简单端点到查询树的映射，并用真实五节点声明做准确性对照。
