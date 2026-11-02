# NekoJS 内嵌 AI 支持方案

日期：2026-10-31

状态：修订提案，尚未实施；本版按用户要求取代上一版的外部文档和独立 MCP 优先路线。

设计基线：`cd54238d`。本文用于维护者审阅，使用中文；随模组发布、供 AI 参考的手册、Skill、模板、示例说明及 API/MCP 资料统一使用英文。

## 1. 产品决定

**把 AI 编写脚本需要的知识和查询能力随 NekoJS 发布。** 用户安装模组后即可获得本地文档、示例、Skill 和短 `AGENTS.md`；需要在线查询时，在 NekoJS 配置中开启由模组管理的 MCP 服务。

采用四层配合：

| 层 | 职责 | 是否需要运行游戏 |
|---|---|---|
| 随 JAR 发布的 Markdown 与示例 | 解释写法、转换、约束和常见任务 | 导出后不需要 |
| 预设 `AGENTS.md` 与 Skill | 引导 AI 选择环境、阅读相关文档、查询和验证 | 不需要 |
| 当前实例 API 索引 | 查 Binding、类、方法、字段、事件、Builder 和注册表 | 已生成快照可离线读；更新需运行 |
| NekoJS 内置可选 MCP | 把同一文档和索引交给 AI 工具，逐步增加诊断和受控操作 | 需要 |

wiki 退为面向人的浏览渠道，可以由同源文档同步；**运行和 AI 编写均不依赖 wiki 是否更新或网络是否可用**。

本方案面向 AI 帮作者编写、理解和修复整合包脚本。游戏内 NPC 推理、模型服务调用和自动玩游戏另立产品范围，不随这里的 MCP 自动引入。

## 2. 文档如何随模组发布

### 2.1 静态写法内嵌，实例 API 自动生成

不要手写几十万行类方法 Markdown。分清两种资料：

- **内嵌手册**：人维护简洁 Markdown，解释事件订阅、Bean Property、自动转换、Builder、脚本包、重载、资源和 UI；例子由测试执行。这是写法和经验的权威来源。
- **实例 API 参考**：由当前插件、Catalog、Probe 和最终作者面投影生成。返回本实例实际存在的符号和签名，这是“有哪些类和方法”的权威来源。

手册随 JAR 版本变化，API 索引随实例的版本、加载器、模组、插件和配置变化。两者都带来源和内容身份，不拿一个版本的手册替另一个版本背书。

**语言范围**：内嵌手册正文、Markdown 参考、Skill 的描述与指令、AGENTS 模板、示例解释、生成的 API 描述和 MCP 工具/资源文档均为英文。只维护一套英文 AI 手册，不设置中文手册分支。代码、标识符、协议值和必要夹具数据保留精确值；这不改变玩家界面的本地化机制，也不要求维护者审阅方案使用英文。

### 2.2 建议源码布局

以下均为待新增路径：

```text
common/src/main/resources/nekojs/authoring/
├── manifest.json
├── handbook/
│   ├── index.md
│   ├── script-types.md
│   ├── events.md
│   ├── bindings-and-adapters.md
│   ├── recipes-and-tags.md
│   ├── registry-builders.md
│   ├── script-packs.md
│   ├── jsx-ui.md
│   ├── diagnostics.md
│   └── validation.md
├── examples/                     # 由 canonical 可执行夹具提取
├── skills/nekojs-scripting/
│   ├── SKILL.md
│   └── references/               # 指向手册/索引，避免复制规则
└── templates/AGENTS.md
```

这些 Markdown 是产品资源，不需要再依赖用户检出源码仓库。构建时生成文件清单和摘要；示例从测试夹具提取，或验证资源与夹具字节一致，不另维护一份容易漂移的代码。

文档内容可由共用机制按 profile 筛选；NeoForge/Fabric 差异依据实际能力元数据标注，不把版本判断散进每篇手册。

### 2.3 游戏目录的使用体验

```text
<game>/nekojs/
├── AGENTS.md                         # 短入口，默认仅在不存在时创建
├── ai/
│   ├── README.md                     # 人类接入说明
│   ├── environment.json              # 当前实例身份与可用性
│   ├── bundled/<bundleId>/
│   │   ├── handbook/
│   │   ├── examples/
│   │   ├── skills/nekojs-scripting/SKILL.md
│   │   └── AGENTS.template.md
│   ├── current.json                  # 指向当前文档包和 API 快照
│   └── connection.json              # MCP 开启时生成；只记连接信息，不含令牌
├── config/ai.toml
└── startup_scripts/、server_scripts/、client_scripts/、test_scripts/
```

API 索引继续位于配置指定的 Probe 工作目录，例如 `.neko_probe/authoring/`；`nekojs/ai/current.json` 提供经过校验的相对位置，避免复制整棵声明树。

模组初始化时只导出小型内嵌资源，不因此触发全量反射或 Probe。实例索引在相关注册和声明生成完成后更新；在此之前，AI 能阅读写法，查询工具明确返回 `index-not-ready`。

版本目录不可变，更新原子切换指针。用户改动的文件保留；根 `AGENTS.md` 已存在时生成旁置模板和差异提示，而不是覆盖。只清理工具拥有且未被固定的旧版本。

### 2.4 每次版本更新前必须同步手册

仓库 `AGENTS.md` 已加入发布规则；每次版本更新必须完成：

1. 审查上一版本以来的生产代码变化，覆盖 API、默认值、配置、平台支持、转换、生命周期、错误和限制。
2. 在同一版本交付中更新所有受影响的内嵌手册、示例、Skill 引用及 AGENTS 模板，修改 canonical 来源。
3. 为目标版本更新文档包版本元数据。即使审查后正文无需变化，也必须记录审查范围及无需修改的原因，而不是随意改几句话制造更新记录。
4. 按现有流程再生派生产物，验证链接、可执行示例、API/profile 一致性、资源摘要及各受影响发布 JAR 中的文档包含情况。
5. 记录验证命令、结果和剩余缺口。文档缺失或必需检查失败时，阻止完成版本更新或发布制品。

推荐顺序：准备版本变更 → 同步并验证资料 → 完成版本更新 → 发布。代码构建通过不能替代这项审查，wiki 更新也不能替代内嵌资料更新。

这是当前已写入仓库的协作规则；文档包、manifest 及自动化 CI 门禁仍需在实施阶段建立，不宣称它们已经运行。

## 3. AGENTS.md 与 Skill 各做什么

### 3.1 推荐两者都提供

`AGENTS.md` 是入口，Skill 是工作流程，Markdown 手册是参考资料，实例索引是可查询事实。它们相互引用，不把同一套规则复制四次。

推荐用户把 `nekojs/` 作为 AI 工作区打开；不同客户端对 AGENTS 的发现规则需在接入样例中明确，不能假定打开游戏根目录就会自动读取所有子目录规则。

默认在 `nekojs/` 创建约 40–80 行的作者版 `AGENTS.md`。它只约束这个脚本工作区，不修改整个游戏目录的其他项目，也不复制 NekoJS 源码仓库的构建规则。

拟议内容结构：

1. 写脚本前读取 `ai/environment.json`，确认 Minecraft、加载器、语言和 Script Type。
2. 编写/修改脚本时读取随包提供的 `nekojs-scripting/SKILL.md`。
3. 处理事件读 events；处理物品字符串读 bindings-and-adapters；注册读 registry-builders；UI 读 jsx-ui。
4. 名称和方法不确定时查询当前 API 索引；索引不可用时使用已有声明，并标记未确认项。
5. 修改后按 validation 手册验证，报告已执行、失败和未执行的部分。
6. 脚本仅使用该环境已证实的 API，不因示例相似而假定 KubeJS/浏览器写法可用。

这里描述的是尚未生成的**整合包作者模板**，不要求整合包作者维护 NekoJS 模组自身的发布资料。源码仓库 `AGENTS.md` 已另外加入上述版本更新审查规则，两者作用范围不同。

### 3.2 Skill 的最小工作流

先提供一个 `nekojs-scripting` Skill，不立即拆出十几个 Skill。其触发说明覆盖编写脚本、修复报错、迁移 KubeJS 和编写 JSX UI；分支参考资料按需读取。

流程：**确认环境 → 查询调用合同 → 使用带证明的最小示例 → 修改 → 分层验证 → 交付结果**。

每步有完成标准，例如“确定事件与 Event Object 的来源、Script Type 和参数合同”，而不是笼统要求“熟悉文档”。约 100–150 行主文，细节放 references 和手册。

Skill 是可直接阅读的文件；各 AI 客户端是否自动发现及如何安装不一致。默认只导出到 NekoJS 自有目录，由 `AGENTS.md` 指引读取；可选生成客户端配置样例，不擅自安装到用户全局 skills 目录。客户端不识别 Skill 时，读取同一 Markdown 也能完成流程。

### 3.3 如何防止手册也变成慢更新的 wiki

- 修改公开脚本写法时，相关示例测试和内嵌手册属于同一交付范围。
- 示例记录适用 profile、前置模组、验证级别及实际断言。
- CI 检查所有引用存在、示例通过、AI 资料使用英文、资源已进入正式 JAR。
- 从符号链接提及的方法必须在对应 profile 索引中解析，防止示例引用已删除名称。
- API 名称、签名和成员列表由程序生成；手册只维护用途、限制和任务流程。
- 第三方插件可通过既有 Contributor/Plugin Hook 模型贡献自己的作者文档；注册新通道时补对应 Point、facade 和配对测试。插件文档保留来源，不赋予其改变工具权限的能力。

## 4. 类、方法和事件到底怎么查

精确范围、多条件、参数关联、字段裁剪和快照分页的详细合同见 [精确作者 API 查询设计](2026-10-31-authoring-query-api.md)。采用共享查询核心：普通 HTTP、可选 CLI 与 MCP 复用同一语义；MCP 是接入层，不决定查询能力。

### 4.1 对用户呈现的查询入口

下面按作者任务列出只读查询能力；专项方案进一步定义 resolve、members 简单入口与结构化 query。最终路由和 MCP 工具清单在原型中统一冻结，文件导出与拟议游戏命令也复用同一查询模块：

| 拟议工具 | 用途 |
|---|---|
| `nekojs_context` | 当前环境、文档版本、索引状态、支持能力 |
| `nekojs_search` | 按名称/关键词搜索 Binding、事件、类、方法、Builder 和文档 |
| `nekojs_describe` | 按 symbol ID 返回类型或成员详情 |
| `nekojs_members` | 分页查看类的构造器、方法、字段、属性、继承成员和重载 |
| `nekojs_registry` | 查物品/流体/标签等 ID，不输出整份巨大字面量 union |
| `nekojs_examples` | 按意图、Script Type、语言和 profile 取已验证示例 |

示例调用，全部为拟议接口，尚未实现：

```json
{"tool":"nekojs_search","arguments":{"query":"Item","kind":"binding","scriptType":"SERVER"}}
```

搜索返回当前快照内真实的 symbol ID，再据此查询，而不是让 AI 自己猜 ID：

```json
{"tool":"nekojs_members","arguments":{"symbolId":"<搜索返回的ID>","name":"of","includeInherited":true}}
```

查询 Java 类时允许简名和完整限定名；简名歧义返回候选，不随机选类。查询结果附 `java:` 导入路径，仅在当前运行环境允许且有对应声明时提供。

默认优先脚本公开入口，例如先找到 Binding `Item`，再查看 `of`，而不是只给用户 Minecraft 的 `Item` 类。API 查询受现有扫描、隐藏、排除与类访问规则约束，不通过反射开放任意 classpath。

### 4.2 类与方法详情必须包含什么

- 类的完整名称、脚本导出名、适用 Script Type、语言、可用来源和继承关系。
- 实际作者面签名：参数名、必选/可选、返回值、泛型、重载与构造器。
- 每个成员的声明者；查询继承成员时正确应用实际类型参数，不把父类未替换的 `T` 当最终结果。
- 字段和 Bean Property 的读/写能力；真正需要显式选择器时返回准确拼写。
- 参数的输入别名和 AdapterInputShape，例如 ItemStack 接受的字符串/对象写法。
- 弃用、限制、阶段、副作用等已有可靠元数据；没有的说明标为缺失，不生成看似权威的注释。
- 原始声明/投影来源、文档链接、示例证明、snapshot 和完整性状态。

例如搜索 `Item.of` 后，应能看到 `of(String)` 的实际签名，并关联 `ItemStackAdapter` 的输入语法，得到 `Item.of('minecraft:iron_sword[damage=2]')` 的受测示例。不能把适配器支持的对象写法一概宣称为所有 `Item.of` 重载都接受。

默认每次只返回 10 个搜索命中或 30 个成员；通过 cursor 继续。字段、方法、构造器、继承和文档可分 section 获取。

### 4.3 查询数据怎么生成，避免另一套漂移实现

当前 Catalog + 原始反射 IR 仍不等于最终作者面。TS 的泛型投影、捕获视图、Bean 处理、显式选择器以及手写声明都必须算在内。

内置 JVM 查询采用**与 Probe 最终投影同源的只读查询模型**：

1. 保留 TypeReflector → IR → 作者面投影 → 语言渲染的已有路径。
2. 在名称、类型替换、输入别名等最终决定作出的同一位置输出查询记录；TS 声明渲染和 API 记录消费同一份已解析结果，不各实现一套投影规则。
3. Binding、事件、Builder、适配器等特殊生成器同样提供其最终结果；不是直接序列化几份 Catalog 就宣称全覆盖。
4. 内嵌手写声明可在构建时用 TypeScript Compiler API 解析为配套索引，随同资源发布并校验摘要。
5. 对第三方动态提交的任意 `.d.ts`，第一版保留原始声明和文档检索，标记结构化索引覆盖不完整；后续支持插件贡献结构化记录，或提供可选离线 AST 索引工具。不得用正则冒充完整 TS 解析器。
6. 对 `.pyi` 建独立覆盖状态，不能拿 TS 签名当 Python 签名。

原离线方案的 TypeScript AST 索引可作为开发期原型和对照检查，但**用户启用内置 MCP 不需要安装 Node/npm，也不在游戏进程里启动 TypeScript 编译服务**。这会要求审计现有渲染接口、少量提取共用投影结果，须纳入实现工作量。

### 4.4 没开启 MCP 时

- 内嵌手册、Skill、`AGENTS.md` 和最近 API 快照照常可读。
- 输出轻量 `index.md` 和分片 JSON，并提供按 symbol ID 对应的声明来源。
- 拟议 `/nekojs api search <name>`、`/nekojs api describe <symbolId>` 使用同一个查询模块；对外部 AI，可先让用户导出对应条目。
- 类 Markdown 详情按需导出，不在首次启动时写出几十万个文件。

因此 MCP 是方便查询的接入方式，不是获得文档和 API 信息的前置条件。

## 5. MCP 由 NekoJS 启动，配置中选择开启

### 5.1 推荐默认值

**文档导出默认开启，MCP 默认关闭，运行控制默认关闭。** 用户打开 MCP 配置后，游戏生命周期负责服务启动与关闭；关闭配置不会删除已导出的文档。

建议独立配置 `<game>/nekojs/config/ai.toml`，与 Probe 的扫描开关分开。AI 资料固定为英文，不提供手册语言选择。以下键是设计草案，不是现有配置：

```toml
[documentation]
enabled = true
agents = "create-if-missing"
skills = "export-only"

[api]
enabled = true
refresh = "after-probe"

[mcp]
enabled = false
transport = "streamable-http"
host = "127.0.0.1"
port = 8767
readOnly = true

[mcp.authentication]
mode = "local-token"
tokenFile = "nekojs/config/ai-mcp.token"

[mcp.runtime]
diagnostics = false
inspect = false
reload = false
```

`[api].enabled` 表示生成/提供作者 API 索引，不扩大 Probe 扫描白名单。配置开启但索引尚未生成时，服务能提供手册与状态，并报告 API 未就绪，不能暗中全量扫描。

开启后拟议行为：

1. 校验配置与本机监听地址，加载或安全创建实例令牌。
2. NekoJS 初始化一次 MCP endpoint，后续世界切换与普通脚本重载不重复创建。
3. 写 `connection.json` 和客户端接入样例，显示当前地址与环境身份；日志不打印令牌。
4. 注册 tools/resources，API 查询读取冻结快照；没有世界时仍可提供手册及已生成快照。
5. 世界退出后撤销在线状态，历史快照仍能读取；进程退出时停止监听、取消任务和释放线程。

建议增加 `/nekojs ai status`、`export`、`refresh`、`config-reload` 管理入口。它们是提案：`refresh` 可显式触发受控 Probe/索引更新，`config-reload` 必须正确启动或关闭服务，不能留下旧端口。

### 5.2 为什么内置服务用 HTTP，不接管游戏 stdin/stdout

标准 stdio 是 AI 客户端启动服务子进程；Minecraft 自己启动的 MCP 服务更适合 Streamable HTTP。游戏 stdout 已承载日志，不能与 MCP stdio 消息混流。

优先实现正式 MCP Streamable HTTP 协议，包含协商、tools/resources、错误处理和适用的会话生命周期；不能只做几个普通 JSON HTTP 接口就称为 MCP。

本地服务固定默认端口，允许用户更改。端口冲突时单独报告 MCP 启动失败，保持游戏正常运行，不自动跳到另一个端口导致客户端连错实例。多实例使用不同端口，并校验 instance ID。可选随机端口需要配套发现流程，不作为第一版默认。

### 5.3 接入 AI 客户端的实际限制

NekoJS 可以启动服务并生成连接样例，但不能保证所有客户端都会自动发现、连接或支持相同认证方式。用户仍需要在所用客户端添加一次连接；不修改用户全局配置。

本机固定令牌是本产品私有接入方案，**不是完整的远端 OAuth 授权方案**，需要验证目标客户端能携带凭据。第一版仅支持明确验证过的本机客户端；不兼容 HTTP/认证的客户端可以继续读文件，后续可选提供由客户端启动的薄 stdio 桥接器。桥接器不再创建第二套业务索引。

“配置开启即开始监听”和“所有 AI 软件零配置可用”是两件事，交付时分别说明。

### 5.4 网络和打包约束

- 第一版只允许 loopback 地址；校验 Origin/Host，认证失败拒绝请求，限制请求大小、并发与超时。
- 令牌保存在受限文件中，连接描述与示例不内嵌明文令牌；轮换提供显式入口。
- 关闭后不监听端口、不运行索引后台轮询任务，文档仍可使用。
- JVM 内采用成熟 MCP 协议实现或经过规范测试的适配层；SDK 选择必须先验证支持的 JDK、加载器和依赖体积。
- 不为此引入模型 SDK、Node 运行时、浏览器或大规模网络框架。对新增依赖检查 JAR 嵌套、重复库、许可证和包体增量，覆盖 NeoForge 1.21.1、26.x 与 Fabric。

## 6. 内置 MCP 提供哪些内容

### 6.1 Resources 与 Tools 分工

Resources 用于读取文档和冻结快照；Tools 用于带筛选条件的查询与明确的操作。拟议资源地址：

```text
nekojs://handbook/events
nekojs://handbook/bindings-and-adapters
nekojs://skills/nekojs-scripting
nekojs://environment/current
nekojs://api/<snapshotId>/<symbolId>
```

具体 URI 编码、语言参数和分页规则在原型中冻结；这些资源都只映射 NekoJS 自有文档和允许导出的事实，不映射任意本机文件。

首版仅开放已冻结的只读查询工具，具体清单与专项查询 API 合同对齐。诊断、UI/物品快照、验证和重载按后续阶段追加，能力列表反映实际配置，不仅在说明里写“不可用”却仍允许调用。

### 6.2 性能与线程

传输层与索引生命周期由根运行时持有一次；世界/Script generation 数据只以冻结 DTO 进入查询快照。MCP 请求线程不直接调用 Graal Context、访问活玩家背包或修改注册表。

普通查询从不可变索引返回，不占 Tick；需要活数据的后续请求送到既有 owner thread，再回传快照。切世界、脚本重载、插件变化后标记相关快照过期，不保留旧世界引用。

知识包发布采用内容摘要与原子指针；不能把现有 Probe 的逐文件同步当跨文件事务。生成失败保留旧快照，同时报告新一轮失败。索引不自动绑定到每次普通脚本重载。

## 7. 环境与查询结果可信度

每个结果关联实际 NekoJS 制品身份、MC/加载器、插件与模组集合、Script Type、语言、Probe 配置、snapshot 和完整性。Git HEAD、目录名和声明生成时间都不能单独证明实际加载环境。

结果区分：`ok`、`not-found`、`unsupported`、`incomplete`、`stale`、`conflict`、`failed`。只有覆盖范围完整时，未找到才可解释成不存在。没有世界时显示离线快照身份，不宣称与在线世界一致。

隐藏、排除、权限和声明近似继续生效；查询结果不得宣称比原脚本引擎更强的转换或类型保证。文档存在、类型通过和运行通过分别记录。

## 8. 验证与后续运行闭环

验证分层保留：环境/包结构 → 语言语法和模块 → 严格类型/合同 → 隔离运行 → 指定实例验收。

转译成功不是严格类型通过；严格类型通过不是运行成功。不能打开 `skipLibCheck` 或把类型改成 any 来制造通过。声明图存在基线错误时，报告其范围和对本次脚本检查的影响，不能简单减去条数。

`validate` 默认不执行顶层代码。真正执行使用显式选择的隔离环境；开放实时重载时仍按原有 owner thread、generation 和失败保留合同，校验目标环境与文件摘要。

普通脚本可能产生文件、Java、世界和外部服务副作用，不存在通用无副作用 dry-run。AI 自动修复不应通过反复重载生产世界试错；开启只读 MCP 不包含这种权限。

结构化诊断复用现有 `ScriptDiagnosticRecord`，只导出与请求关联的数据；UI 调整复用已有 Inspector 合同。`hand` 的 JSON/字符串由相同领域实现导出，不反向解析聊天文本。

## 9. 实施计划：按本轮要求重新排序

| 阶段 | 实际交付 | 验收条件 |
|---|---|---|
| P0：最小纵向原型 | 内嵌两篇写法文档与物品示例、AGENTS/Skill 模板，`Item` Binding 和其方法的同源 API 记录；本机 MCP 查询原型 | 从干净实例导出文档；配置开启后可完成 context → search → members → 示例读取；不要求先完成全部类索引 |
| P1：随版本文档包 | 完整手册目录、首批 10 个受测任务、用户文件保护、英文资料检查、版本元数据与打包验证 | 不访问 wiki；无 MCP 也能按 Skill 完成任务；升级不覆盖用户 AGENTS 与自改文件；每次版本交付记录手册审查结果 |
| P2：完整公开 API 查询 | 共享投影模型、事件/Binding/Builder/适配器/类方法、分页继承查询、手写声明覆盖报告 | 与最终声明逐项对照；五节点验证；所有不完整来源明确记录，无任意反射开放 |
| P3：可发布内置 MCP | 配置加载、协议实现、认证与 loopback、状态和接入样例、生命周期与依赖审计 | 开/关/端口冲突/多实例/切世界/重载/退出/错误认证均有测试；无需 Node/npm 和模型密钥 |
| P4：验证和诊断 | 分层验证、诊断筛选、20 个任务、Python 独立覆盖记录 | 不执行的检查不标为通过；完整声明问题如实暴露；诊断关联目标操作与 generation |
| P5：可选运行操作 | 显式开启的 Inspector/物品快照与受控重载 | 权限、并发修改、超时、断线、重复操作和失败保留均有真实加载器证据 |

P0 是验证整个使用体验的小闭环，不是把全项目重构完才让 AI 试用。P3 的正式服务需要 P2 的覆盖与可信度支持；P0 的原型必须标注仅含少量符号，不能宣传为完整查询。

本轮只修改方案及仓库协作规则，不创建实际配置、监听端口、安装客户端 Skill 或替换现有 Agent 模板。

## 10. 第一批任务与评价标准

先选 20 个维护者能手工完成的任务：物品转换 4、配方/标签 5、事件 4、注册 Builder 3、客户端/JSX 4。10 个核心合同任务覆盖全部五节点；其余按实际支持矩阵分配。Python 单独建基线，平台不支持不能跳过后算通过。

固定模型、提示、环境、尝试次数和上下文预算，比较“现有声明/静态模板”与“内嵌文档/Skill/API 查询”。统计 API 臆造率、错误 Script Type/加载器调用、首次静态通过、规定次数内运行通过、读取量、耗时与人工干预次数。

每个后续版本均重复第 2.4 节的文档审查，不能先发布 API/行为变更，再承诺以后补手册。

索引耗时、峰值内存、包体增量与查询延迟单独记录。原型热查询 p95 500 ms、首次可用查询 10 秒作为待校准目标，不替代既有重载性能门槛，不让文档导出引起全量扫描。

## 11. 当前实现基础与改动位置

| 现有位置 | 拟议改动 |
|---|---|
| `common/.../probe/AgentTemplateGenerator.java` | 改为输出短入口/引用内嵌资料；加入用户文件保护，逐步统一旧三份静态模板 |
| `common/.../api/catalog/NekoScriptCatalogSnapshot.java` | 复用目录事实，必要时补可靠元数据；不直接把原始 Catalog 当最终调用合同 |
| `common/.../probe/ir/` 与语言渲染器 | 审计最终投影与生成器，提取共享结果供声明和 API 查询记录消费 |
| `common/.../core/error/ScriptDiagnosticRecord.java` | 后续只读诊断投影 |
| `common/.../core/lifecycle/NekoRuntimeRoot.java` | 集成根所有者管理的可选 MCP 生命周期；不在各 Script Context 创建服务器 |
| `src/...` 与节点 facade | MC/loader 数据采集、当前侧/世界连接、权限与加载器生命周期 |
| 新增 `resources/nekojs/authoring/` | Markdown、示例、Skill、AGENTS 模板及资源 manifest |
| 新增作者查询与传输模块 | 模块先以清晰包边界组织，是否拆独立 Gradle 子项目依据依赖审计决定 |

`common` 继续没有 Minecraft/loader 依赖。协议与文档不引入模型推理依赖。公开脚本 API、持久化数据和原运行时安全限制不因提供查询而放宽。

## 12. 与旧文档和技术参考的关系

本版取代上一版的“先独立 Node/TS 工具、优先 stdio、从 wiki 取用法”的默认路线。离线工具仍可保留为可选开发辅助，不是普通用户前置依赖。

`docs/nekojs-api-mcp.md` 保留为历史提案；其 AST 索引和上下文预算思路仍可复用，但不再代表当前部署决定。已有 JSX authoring contract 继续提供语义依据，产品资料通过打包/提取流程进入内嵌手册，不复制第二套 UI 规则。

已核对官方 MCP 资料：stdio 由客户端启动子进程且 stdout 仅用于协议；Streamable HTTP 支持独立 endpoint；本地 HTTP 需考虑 Origin、loopback 和认证。Tools 与 Resources 分别用于查询操作和上下文资源。参考地址：

```text
https://modelcontextprotocol.io/specification/2025-11-25/basic/transports
https://modelcontextprotocol.io/specification/2025-11-25/server/tools
https://modelcontextprotocol.io/specification/2025-11-25/server/resources
https://github.com/microsoft/TypeScript/wiki/Using-the-Compiler-API
```

这些是本次设计核对的版本，不宣称为最新协议。实施 P0 时选择并锁定兼容的 SDK/协议/JDK，验证目标客户端接入与依赖体积。待冻结项包括最终配置键、symbol ID/schema、索引覆盖规则、HTTP 接入客户端清单与插件文档贡献接口。
