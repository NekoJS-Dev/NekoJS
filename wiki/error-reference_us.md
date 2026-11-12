<!-- wiki-page: error-reference; locale: us -->

> **English** · [中文](error-reference_cn)

<a id="wiki-section-1"></a>
# Error and log message reference

Messages that report a problem carry a stable code and a short English summary.
The Chinese text is unchanged apart from the code prefix and the summary.
The code is the lookup key and does not change when the wording does.

This page indexes NekoJS developer diagnostics, not every Java, mod or network exception. Structured HTTP/MCP machine codes such as `CONTROL_DISABLED` and `INDEX_UNAVAILABLE`, and operation states, are documented under [AI support](ai-support_us#wiki-live-controls); they are distinct from logged `NEKO-xxxx` identifiers. Include Script Type, source/line, phase and actual commit outcome in reports. A queueing message alone does not establish reload success.

```
[NEKO-2004] 脚本输出行数超过 … 行上限，后续输出将被丢弃（防止日志无限增长） — script output line limit reached
```

Where a message ends with a value, the summary comes before it:

```
[NEKO-4006] 未知方块实体类型 — unknown block entity type: minecraft:chest
```

Codes from #58 that have no call site on this branch are not used:
`NEKO-1007` (the old candidate-context failure was replaced by the generation reload path),
`NEKO-1011` / `NEKO-1012` (Cleanroom client load logs), and `NEKO-3001`–`NEKO-3004` (script sync size limits).

<a id="wiki-section-2"></a>
## Script loading and reload

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-1001` | failed to scan script directory | 扫描脚本目录失败 |
| `NEKO-1002` | failed to create workspace directory | 无法初始化环境目录 |
| `NEKO-1003` | script execution failed | 脚本执行失败 |
| `NEKO-1004` | problem in after dependency order | 脚本 after 依赖排序存在问题 |
| `NEKO-1005` | script unreadable, skipped | 无法读取脚本，已跳过 |
| `NEKO-1006` | startup reload is not transactional | 脚本重载为非事务式语义 |
| `NEKO-1008` | transactional script reload failed | 脚本事务重载失败，候选 generation 已关闭，active 环境保持不变 |
| `NEKO-1009` | error closing old Node runtime | 关闭旧 Node runtime 时发生异常 |
| `NEKO-1010` | error closing old context | 关闭旧上下文时发生异常 |
| `NEKO-1013` | script pack state update or temporary cleanup failed | 脚本包状态保存或暂存文件清理失败 |
| `NEKO-1014` | script pack state unreadable; discovery uses manifest default | 脚本包状态不可读取，发现时使用清单默认值 |
| `NEKO-1016` | versioned pack descriptor or entry validation rejected | 新版脚本包描述或入口校验被拒绝 |
| `NEKO-1017` | package activation, capability, operation or resource release rejected/failed | 包激活、能力、操作或资源释放被拒绝或失败 |
| `NEKO-1018` | package author tool or embedded content admission failed | 包创建、校验、归档、安装、JAR 或已加载模组内容接入失败 |
| `NEKO-1019` | Authoring bundle, configuration, snapshot or local service setup failed | AI 文档包、配置、声明快照或本地服务初始化失败；已有资料按具体错误保留 |
| `NEKO-1020` | admitted authoring owner callback failed | 已接纳的 AI 所有者线程回调异常；未自动重放 |
| `NEKO-1021` | authoring command rejected inside an active command context | 已有命令上下文时拒绝 AI 命令，避免排队后迟执行 |
| `NEKO-1022` | authoring SERVER committed but postprocessing failed | SERVER 脚本已提交，但配方、脚本包或资源后处理失败 |
| `NEKO-1023` | authoring live control requires server owner thread | 实时 AI 控制未在服务器所有者线程执行，操作被拒绝 |
| `NEKO-1024` | authoring live server runtime is no longer current | 实时服务器已停止或更换，旧操作不得在新世界继续执行 |

`NEKO-2001`, `NEKO-2002`, `NEKO-2003` and `NEKO-2005` are retired normal-execution budget diagnostics. Their former resource/statement-limit, runaway-watchdog and evaluation-timeout call sites were removed; old logs may still contain them. These identifiers remain historical references, not settings to enable or codes to reuse. `NEKO-2004` remains active: it caps emitted log lines, not script execution.

Normal execution has no replacement automatic deadline or watchdog. Unresolved top-level await may remain pending, and infinite loops can block the owner thread/process. HTTP/authoring request waits, lifecycle cancellation/shutdown and the private offline Mixin declaration sandbox's fixed 100,000-statement limit are separate and remain applicable.

Structured diagnostic `phase: WATCHDOG` and owner `Runaway Watchdog` retain their historical wire labels for generic cancelled, interrupted or resource-exhausted Polyglot contexts. Seeing those values does **not** establish that an automatic watchdog was installed or that a script hit an execution budget; inspect the actual cause and lifecycle.

<a id="wiki-section-3"></a>
## Sandbox and resource limits

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-2004` | script output line limit reached | 脚本输出行数超过行上限 |

<a id="wiki-section-4"></a>
## Data synchronization

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-3010` | persistent data sync skipped because the payload exceeds the limit | PData 同步因数据超过上限而跳过 |
| `NEKO-3011` | server Item/Block modification sync rejected | 服务端 Item/Block modification 同步被客户端拒绝 |
| `NEKO-3012` | dynamic registry message delivery failed | 动态注册表消息发送失败 |
| `NEKO-3013` | dynamic registry configuration failed; connection disconnected | 动态注册表配置同步失败，连接已断开 |
| `NEKO-3014` | malformed dynamic registry message or reply dropped | 无效动态注册表消息或回执已丢弃 |
| `NEKO-3015` | dynamic registry sync reply could not be sent | 动态注册表同步回执发送失败 |

<a id="wiki-section-5"></a>
## Registration and bindings

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-4001` | duplicate binding name ignored | 同名绑定已注册，后者被忽略（首胜） |
| `NEKO-4002` | goal target must be a LivingEntity | 目标类型必须是 LivingEntity |
| `NEKO-4003` | cannot infer target class from EntityType | 无法从 EntityType 推断目标类 |
| `NEKO-4004` | unknown target entity | 未知目标实体 |
| `NEKO-4005` | could not resolve goal target | 无法解析目标 |
| `NEKO-4006` | unknown block entity type | 未知方块实体类型 |
| `NEKO-4007` | unknown capability | 未知 capability（支持 item/energy/fluid） |
| `NEKO-4008` | invalid entity factory or entity class constructor | 实体 factory 无效或实体类缺少 (EntityType, Level) 构造器 |
| `NEKO-4009` | capability provider execution failed | capability provider 执行失败 |
| `NEKO-4010` | capability provider returned an incompatible non-null value | capability provider 返回不兼容的非空类型 |
| `NEKO-4011` | capability batch is no longer collecting or its native commit failed | capability 批次已结束收集或原生提交失败 |
| `NEKO-4012` | capability factory argument is negative | capability 工厂参数不能为负数 |
| `NEKO-4013` | client persistent data mirror is read-only | 客户端持久化数据镜像只读 |
| `NEKO-4014` | duplicate capability provider | capability provider 重复注册 |
| `NEKO-4017` | invalid persistent native goal factory or goal constructor | 持久原生 goal factory 或构造器无效 |
| `NEKO-4018` | ItemStack persistent data is unavailable | ItemStack 持久化数据不可用 |
| `NEKO-4019` | invalid or unknown capability target | capability 目标无效或未知 |
| `NEKO-4020` | invalid native capability/provider/context input | 原生 capability、provider 或 context 输入无效 |
| `NEKO-4024` | saved capability storage is missing, invalid, or outside its bounds | capability 存档字段缺失、无效或超出范围，保留当前数据 |
| `NEKO-4025` | invalid entity renderer, texture, shadow, or native renderer constructor | 实体 renderer、纹理、阴影或原生渲染器构造器无效 |
| `NEKO-4026` | texture dimensions must be positive | 纹理尺寸必须为正数 |
| `NEKO-4027` | storage persistence attempted during an uncommitted transfer transaction | 未提交传输事务期间不能加载或保存存储 |
| `NEKO-4028` | Fabric registry drain did not consume a selected pending registry | Fabric 注册表抽干未消费选定的待注册条目 |
| `NEKO-4029` | Probe type reflection is incomplete; declarations may be omitted | Probe 类型反射不完整，部分声明可能缺失 |
| `NEKO-4030` | Localization binding rejected invalid text or format arguments | 本地化 Binding 拒绝无效文本或格式参数 |
| `NEKO-4031` | Authored class declaration registration rejected | 手写类声明注册被拒绝 |
| `NEKO-4032` | Conflicting authored class declarations rejected at the same priority | 同优先级的手写类声明冲突，注册被拒绝 |
| `NEKO-4033` | Authored class declaration dependency is unavailable, excluded, or hidden | 手写类声明依赖不可用、被排除或被隐藏 |
| `NEKO-4034` | item component, model, texture or identifier configuration rejected | 物品组件、模型、纹理或标识符配置被拒绝 |
| `NEKO-4035` | item definition modification rejected after startup collection | STARTUP 收集后物品定义已冻结，修改被拒绝 |
| `NEKO-4036` | item component type or codec value rejected with item, component and node context | 物品组件类型或 codec 值被拒绝，消息包含物品、组件与节点上下文 |
| `NEKO-4037` | Retired: Fabric automatic item resources now use the directory resource bridge. | 已停用：Fabric 自动物品资源已接入目录资源桥。 |
| `NEKO-4038` | Probe predeclaration failed; the selected class is omitted from generated TypeScript | Probe 预声明失败，选定类未进入生成的 TypeScript |
| `NEKO-4039` | Probe declaration task failed; some generated TypeScript declarations may be absent | Probe 声明任务失败，生成的 TypeScript 可能缺少部分声明 |
| `NEKO-4040` | Activation claim publication rejected: unsupported registry | 激活认领发布遇到不支持的注册表，属于预检后的内部契约错误 |
| `NEKO-4041` | Villager trade replay failed after resource reload; committed snapshot is unavailable | 资源重载后交易重放失败，已提交快照暂不可用 |
| `NEKO-4042` | Dynamic registry rollback failed; the registry may retain partial writes | 动态注册回滚失败，未报告恢复成功 |
| `NEKO-4043` | Villager trade batch rejected: generated ID collision | 交易批次生成的 ID 与已有非本域条目或另一个交易集冲突，写入前拒绝 |
| `NEKO-4044` | Villager trade checkpoint read/restore failed | 交易注册表快照读取或恢复失败；恢复失败时查询不报告 active |
| `NEKO-4045` | Unknown profession | 交易批次引用未知职业，整个候选被拒绝 |
| `NEKO-4046` | Villager trade pool replacement failed | 静态交易池替换失败，不能报告成功 |
| `NEKO-4047` | TypeScript Bean alias exclusion registration rejected | TypeScript Bean 派生属性排除条目无效或收集器不支持 |
| `NEKO-4048` | Probe TypeScript dependency closure incomplete | Required types are excluded, hidden, or unavailable; inspect `@special/dependencies.json`. Generation does not certify a complete IDE check. |
| `NEKO-4050` | Dynamic item fire resistance value rejected before activation | 动态物品火抗值不是规范的 `true` / `false`，激活前拒绝 |
| `NEKO-4051` | Manual declaration type reference rejected | 手写声明的类型引用别名无效，或同名别名的目标类型、输入模式冲突 |
| `NEKO-4052` | Registry type catalog rejected conflicting sources or symbols | 注册表类型目录存在同名异来源，或与标签/命名空间类型名冲突 |
| `NEKO-4053` | Bean method projection rejected | Bean方法投影的真实来源、零参签名或收集器无效 |
| `NEKO-4054` | Item component string conversion failed | 物品组件字符串无法解析或序列化 |
| `NEKO-4055` | Authoring query or transport request rejected | AI 查询或传输请求被拒绝；响应的机器码和 JSON 路径提供具体原因 |
| `NEKO-4056` | Resource ID conversion rejected | Strict resource syntax or a real native ID is required; legacy `ID.of` parsing is unchanged. |
| `NEKO-4057` | API module publication or access rejected | Check the mod owner, exposure target, export name, public type, event allowlist, and selected script environment. |
| `NEKO-4060` | Script options rejected | Unknown fields, incorrect types, or out-of-range values reject registration |
| `NEKO-4061` | Post-effect builder rejected | A sealed or invalid builder rejects the candidate declaration |
| `NEKO-4062` | Render registration rejected | Invalid rendering arguments, callback, or declaration metadata reject registration |
| `NEKO-4063` | Script input declaration rejected | The input-shape provider cannot be constructed, returns an invalid shape, or requires a missing adapter target |
| `NEKO-4064` | Script output declaration rejected | A method's explicit TypeScript output expression is blank |
| `NEKO-4065` | Script output view rejected | The view must be an implemented public non-generic interface; native input contracts are unchanged. |
| `NEKO-4066` | Probe declaration retained with incomplete public members | A missing optional public signature prevents native method-set reflection. Independent members remain; inspect `incompleteMembers` and do not treat the closure as complete. |
| `NEKO-4090` | Mixin preparation/restart required or declaration/library rejected | Restart after successful preparation; otherwise check cache selection/ownership, actual loader admission, exact profile, descriptor and supported options. See [MixinJS troubleshooting](mixinjs_us#wiki-section-9). |
| `NEKO-4091` | Mixin callback authority or bridge ownership rejected | Use a locally trusted candidate and callback file; another live root cannot replace the bridge lease. |
| `NEKO-4092` | Native hook bypassed or callback decision discarded | Check thread, synchronous return, control lifetime and strict ABI conversion. |


<a id="wiki-section-6"></a>
## Recipes and data

The entries below in the `NEKO-5240`–`NEKO-5279` range belong to NekuJS recipe-schema import, not a promise of compatibility with every KubeJS addon. A recognized definition whose semantics cannot be honored is declared as `never` by Probe and rejected by the corresponding runtime constructor. Fix or implement the diagnosed semantics rather than bypassing the declaration.

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-5001` | could not convert value to JSON | 无法转换为 JSON |
| `NEKO-5002` | JSON must be an object | JSON 必须是对象 |
| `NEKO-5003` | unknown recipe filter key | RecipeFilterAdapter: unknown key |
| `NEKO-5200` | selected recipe method threw a checked exception; no overload or schema fallback | 已选配方方法抛出受检异常，保留原因，不再尝试重载或 schema 回退 |
| `NEKO-5201` | selected recipe method is inaccessible; invocation rejected | 已选配方方法不可访问，调用被拒绝 |
| `NEKO-5202` | native type adapter acceptance or conversion failed; original cause preserved, not an overload mismatch | 原生类型适配器判定或转换失败，保留原始原因，不作为重载不匹配处理 |
| `NEKO-5210` | invalid advanced recipe definition or inconsistent field/alias/function metadata | 高级配方定义或字段、别名、函数元数据不一致 |
| `NEKO-5211` | known recipe profile is explicitly unsupported; no handler/raw fallback | 已知配方模型不受支持，不回退到处理器或原始配方 |
| `NEKO-5212` | multiple named arguments target the same canonical field | 多个命名参数指向同一个字段 |
| `NEKO-5213` | required schema field is missing | 缺少必需的配方字段 |
| `NEKO-5214` | no schema constructor accepts the supplied arguments | 没有配方构造器接受当前参数 |
| `NEKO-5215` | recipe field encoder returned no JSON value | 配方字段编码器未返回 JSON 值 |
| `NEKO-5216` | declarative schema function received arguments | 声明式配方函数不接受参数 |

| `NEKO-5240` | Invalid schema value, shape, resource ID, field role or missing property | Invalid schema value, shape, resource ID, field role or missing property; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5241` | Duplicate schema resource ID | Duplicate schema resource ID; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5242` | Missing or unsupported parent schema | Missing or unsupported parent schema; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5243` | Schema parent cycle | Schema parent cycle; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5244` | Invalid key ordering, duplicate key or incompatible requiredness | Invalid key ordering, duplicate key or incompatible requiredness; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5245` | Unknown schema key | Unknown schema key; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5246` | Unsupported component or missing semantic codec | Unsupported component or missing semantic codec; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5247` | Invalid component constant or default-writing policy | Invalid component constant or default-writing policy; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5248` | Duplicate constructor key or arity | Duplicate constructor key or arity; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5249` | Ambiguous field alias or function/setter conflict | Ambiguous field alias or function/setter conflict; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5250` | Unsupported custom factory, processor, mapping or function behavior | Unsupported custom factory, processor, mapping or function behavior; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5251` | Abstract schema dependency is not callable | Abstract schema dependency is not callable; inspect the resource and JSON path in the diagnostic. |
| `NEKO-5279` | Recipe schema resource read failed or root is not an object | Recipe schema resource read failed or root is not an object; inspect the resource and JSON path in the diagnostic. |

| `NEKO-5306` | String component requires a string value | String component requires a string value |
| `NEKO-5307` | Pattern map requires nonempty single-character non-space keys | Pattern map requires nonempty single-character non-space keys |
| `NEKO-5308` | Crafting category input type is invalid | Crafting category input type is invalid |
| `NEKO-5309` | Native shaped preparation failed | Native shaped preparation failed |

| `NEKO-5320` | duplicate programmatic schema | 程序式配方模型重复 |
| `NEKO-5321` | duplicate recipe factory | 配方工厂重复 |
| `NEKO-5322` | duplicate schema key | 模型字段重复 |
| `NEKO-5323` | required key follows optional key | 必需字段位于可选字段之后 |
| `NEKO-5324` | null default cannot always be written | 空默认值不能始终写出 |
| `NEKO-5325` | invalid list bounds | 列表边界无效 |
| `NEKO-5326` | programmatic list spreading is unsupported | 尚不支持程序式列表展开 |
| `NEKO-5327` | override references an undeclared key | 覆盖引用了未声明字段 |
| `NEKO-5328` | unknown schema key | 模型字段不存在 |
| `NEKO-5329` | decoded list size is outside bounds | 解码后列表长度越界 |
| `NEKO-5330` | recipe serializer is not registered | 配方序列化器未注册 |
| `NEKO-5331` | legacy target is already attached | 兼容方法目标已绑定 |
| `NEKO-5332` | native recipe field decode failed | 原生配方字段解码失败 |
| `NEKO-5333` | native recipe field encode failed | 原生配方字段编码失败 |
| `NEKO-5334` | key belongs to another schema | 字段属于其他配方模型 |
| `NEKO-5335` | legacy target has no native builder session | 兼容方法目标尚未绑定原生构建器 |
| `NEKO-5336` | original component rejected a value | 原附属组件拒绝该值 |
| `NEKO-5337` | duplicate field alias | 字段别名重复 |
| `NEKO-5338` | duplicate field function | 字段方法重复 |
| `NEKO-5339` | factory returned an incompatible fluent target | 工厂返回了不兼容的方法目标 |
| `NEKO-5340` | programmatic default encode failed | 程序式默认值编码失败 |
| `NEKO-5341` | unknown native component mapping | 原生组件映射不存在 |
| `NEKO-5342` | programmatic component encode failed | 程序式组件编码失败 |
| `NEKO-5343` | component requires an array | 组件需要数组 |
| `NEKO-5344` | recipe number is not an exact safe integer or is out of bounds | 配方整数不精确、不安全或越界 |
| `NEKO-5345` | component requires its original typed host value | 组件需要原始 Java 类型对象 |
| `NEKO-5346` | programmatic component decode failed | 程序式组件解码失败 |
| `NEKO-5347` | forced programmatic constructor overrides are unsupported | 尚不支持程序式构造器强制覆盖 |
| `NEKO-5350` | host does not support native builder sessions | 宿主不支持原生构建器会话 |
| `NEKO-5352` | host requires a native JSON builder | 宿主需要原生 JSON 构建器 |
| `NEKO-5353` | host has no borrowed registry access | 宿主缺少借用的注册表访问 |
| `NEKO-5360` | expected a recipe number | 需要配方数值 |
| `NEKO-5361` | expected a recipe boolean | 需要配方布尔值 |
| `NEKO-5362` | tag belongs to a different registry | 标签属于其他注册表 |
| `NEKO-5363` | expected a tag identifier | 需要标签标识符 |
| `NEKO-5364` | resource key belongs to a different registry | 资源键属于其他注册表 |
| `NEKO-5365` | expected a resource identifier | 需要资源标识符 |
| `NEKO-5366` | wrapped list size is outside bounds | 转换后的列表长度越界 |
| `NEKO-5367` | structured conversion requires the caller active Graal context | 结构化转换需要调用方已进入的 Graal 上下文 |

<a id="wiki-section-7"></a>
## JSX UI visual and resource contract

| Code | Meaning |
|---|---|
| `NEKO-6008` | Automatic item resource group skipped because an existing file is externally owned or modified. |
| `NEKO-6009` | Automatic item asset generation failed; publication failures attempt rollback and attach restoration failures as suppressed causes. |
| `NEKO-6010` | Item asset ownership index cannot be read or validated; generation rejected without replacing it. |
| `NEKO-6011` | Directory pack client resource reload failed; inspect the cause and native resource recovery. |
| `NEKO-6012` | The native UI host rejected invalid or unknown read-only item configuration (resource id, non-air item, count or tooltip props); a failed candidate update retains the active UI. |

| NEKO-6001 | A UI color prop is not a controlled color form (ARGB/RGB int, `#RGB`/`#RRGGBB`/`#AARRGGBB`, CSS basic named color). |
| NEKO-6002 | A UI visual prop value is out of range or has the wrong type (`opacity`, `fontSize`, `borderWidth`, `radius`, `fit`, `crop`). |
| NEKO-6003 | A UI resource identifier violates the controlled id grammar (lowercase `namespace:path`, no `..`). |
| NEKO-6004 | A controlled UI resource id does not resolve in the resource roots. |
| NEKO-6005 | Reading or uploading a resolved UI resource failed, including encoded input over the supported size limit. |
| NEKO-6006 | A UI texture or crop size is invalid or exceeds the supported dimensions. |
| NEKO-6007 | Decoding a resolved PNG resource into a usable UI image failed. |

<a id="wiki-section-8"></a>
## JSX UI runtime lifecycle

| Code | Meaning |
|---|---|
| NEKO-7001 | UI operation rejected: the root's generation is superseded or closed (stale handle). |
| NEKO-7002 | Illegal UI root lifecycle transition (candidate/active/closing/closed). |
| NEKO-7003 | JSX host adapter created outside a managed CLIENT script context. |
| NEKO-7004 | UI mutation attempted off the client owner thread without explicit queueing. |
| NEKO-7005 | UI root release failed during generation teardown; teardown continued. |
| NEKO-7006 | Deferred UI work dropped: its generation never committed or was superseded. |
| NEKO-7007 | JSX UI phase failure (render/layout/event/host) recorded into the diagnostics chain, including host clipboard read/write failure. |
| NEKO-7008 | The native host rejected a control appearance outside the controlled color/state-field grammar. |
| NEKO-7011 | The native host rejected an invalid slider configuration (non-finite range/value, non-positive step or out-of-range value). |
| NEKO-7012 | The native host rejected invalid dialog props (missing/blank id, non-string title or non-boolean close flags). Common component/layout validation may instead report the phase through `NEKO-7007`. |
| NEKO-7013 | Workspace authoring rejects invalid configuration/IDs or controller mutation during root publication. Construction/controller calls can throw directly; a UI phase may also wrap the cause as `NEKO-7007`. |
| NEKO-7014 | Managed-mount authoring rejected non-positive/unrepresentable GUI region bounds, a region-only operation on an owned Screen mount, or another root handle/layout root ID. Construction, binding or pre-publication validation can reject directly; identical-handle binding is idempotent. It is not a new HTTP/MCP wire error. |
| NEKO-7015 | A bundled diagnostic UI session is unavailable, closed, or failed to load/render/dispatch. On supported NeoForge 26.x clients, an active failed dashboard requests the native fallback once; closing/shutdown and late updates do not reopen it. This is not a new public Script API or packet status. |

<a id="wiki-section-9"></a>
## JSX UI inspector

| Code | Meaning |
|---|---|
| NEKO-8001 | A runtime layout snapshot does not match the inspector read contract (runtime/host version skew); the last good frame is kept. |

<a id="wiki-section-10"></a>
## Messages without a code

Routine progress messages carry an English summary but no code.

| English summary | Chinese message |
|---|---|
| no scripts to load | 没有需要加载的脚本 |
| registering event groups | 正在为注册事件组 |
| reloading one script file | 正在重载脚本文件 |
| reloading scripts | 正在重载脚本 |
| running test scripts | 正在运行 TEST 脚本 |
| script file reload finished | 脚本文件重载完毕 |
| script reload finished | 脚本重载完毕 |
| scripts discovered | 发现了脚本 |
| startup file reload falls back to full reload | STARTUP 注册不可逆，退化为完整 STARTUP 重载 |
| test scripts finished | TEST 脚本运行完毕 |
| workspace entry point created | 已初始化环境入口 |

<!-- wiki-nav -->

---

[Previous: Commands](commands_us) · [Contents](Home) · [Next: Versioned script packs](script-packs_us)
