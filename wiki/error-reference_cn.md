<!-- wiki-page: error-reference; locale: cn -->

> **中文** · [English](error-reference_us)

<a id="wiki-section-1"></a>
# 错误与日志消息参考

报告问题的消息带有稳定的错误码和简短英文摘要。
除了错误码前缀和英文摘要，中文文本保持不变。
错误码是查询键，不会随着措辞变化而改变。

本页查询的是 NekoJS 开发诊断编号，不是所有 Java、模组或网络异常的完整清单。AI HTTP/MCP 的 `CONTROL_DISABLED`、`INDEX_UNAVAILABLE` 等结构化机器码及操作状态见[AI 支持](ai-support_cn#wiki-live-controls)；它们与日志中的 `NEKO-xxxx` 不是同一套编号。报告问题时同时提供 Script Type、源文件/行号、阶段与实际是否提交，不要仅凭一条“已排队”消息判断 reload 成功。

入口求值失败会把该入口标为禁用并记录原因，不能泛化成“任意回调异常都会禁用整个运行时”。事件/定时器回调报告有各自的归属。被宿主取消或资源耗尽终止的 Context 会使所属代失效；恢复需要显式完整 reload，不能靠单文件重跑。

```
[NEKO-2004] 脚本输出行数超过 … 行上限，后续输出将被丢弃（防止日志无限增长） — script output line limit reached
```

消息末尾包含具体值时，摘要放在该值之前：

```
[NEKO-4006] 未知方块实体类型 — unknown block entity type: minecraft:chest
```

#58 中在此分支没有调用位置的错误码不使用：
`NEKO-1007`（旧候选上下文失败已被 generation reload 路径替代）、
`NEKO-1011` / `NEKO-1012`（Cleanroom 客户端加载日志）和 `NEKO-3001`–`NEKO-3004`（脚本同步大小限制）。

<a id="wiki-section-2"></a>
## 脚本加载与重载

| 错误码 | 英文摘要 | 中文消息 |
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
| `NEKO-1025` | script property rejected; previous value or default retained | 脚本属性解析失败；保留最近有效值或默认值，警告包含文件、实际行号和原因 |

`NEKO-2001`、`NEKO-2002`、`NEKO-2003`、`NEKO-2005` 已作为普通执行预算诊断退役。原有资源/语句上限、失控 watchdog、求值超时调用点已移除；旧日志仍可能包含这些码。它们保留为历史引用，不是可重新启用的设置，也不应复用给其他含义。`NEKO-2004` 仍有效：它限制输出日志行数，不限制脚本执行。

普通执行没有替代性的自动截止时间或 watchdog。未完成的顶层 await 可以持续等待，无限循环可能阻塞所属线程/进程。HTTP/authoring 请求等待上限、生命周期取消/关闭，以及离线 Mixin 声明专用沙盒固定的 100,000 条语句限制，都是独立且继续有效的机制。

结构化诊断的 `phase: WATCHDOG` 与 owner `Runaway Watchdog` 为兼容保留历史线上标签，用于一般的 Polyglot 取消、中断或资源耗尽上下文。看到这些值**不能**证明安装了自动 watchdog，也不能证明脚本触及执行预算；应检查实际原因与生命周期。

<a id="wiki-section-3"></a>
## 沙盒与资源限制

| 错误码 | 英文摘要 | 中文消息 |
|---|---|---|
| `NEKO-2004` | script output line limit reached | 脚本输出行数超过行上限 |

<a id="wiki-section-4"></a>
## 数据同步

| 错误码 | 英文摘要 | 中文消息 |
|---|---|---|
| `NEKO-3010` | persistent data sync skipped because the payload exceeds the limit | PData 同步因数据超过上限而跳过 |
| `NEKO-3011` | server Item/Block modification sync rejected | 服务端 Item/Block modification 同步被客户端拒绝 |
| `NEKO-3012` | dynamic registry message delivery failed | 动态注册表消息发送失败 |
| `NEKO-3013` | dynamic registry configuration failed; connection disconnected | 动态注册表配置同步失败，连接已断开 |
| `NEKO-3014` | malformed dynamic registry message or reply dropped | 无效动态注册表消息或回执已丢弃 |
| `NEKO-3015` | dynamic registry sync reply could not be sent | 动态注册表同步回执发送失败 |

<a id="wiki-section-5"></a>
## 注册与绑定

| 错误码 | 英文摘要 | 中文消息 |
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
| `NEKO-4048` | Probe TypeScript dependency closure incomplete | 必需类型被排除、隐藏或无法反射；详见 `@special/dependencies.json`。生成文件不代表完整 IDE 检查通过。 |
| `NEKO-4050` | Dynamic item fire resistance value rejected before activation | 动态物品火抗值不是规范的 `true` / `false`，激活前拒绝 |
| `NEKO-4051` | Manual declaration type reference rejected | 手写声明的类型引用别名无效，或同名别名的目标类型、输入模式冲突 |
| `NEKO-4052` | Registry type catalog rejected conflicting sources or symbols | 注册表类型目录存在同名异来源，或与标签/命名空间类型名冲突 |
| `NEKO-4053` | Bean method projection rejected | Bean方法投影的真实来源、零参签名或收集器无效 |
| `NEKO-4054` | Item component string conversion failed | 物品组件字符串无法解析或序列化 |
| `NEKO-4055` | Authoring query or transport request rejected | AI 查询或传输请求被拒绝；响应的机器码和 JSON 路径提供具体原因 |
| `NEKO-4056` | Resource ID conversion rejected | 严格资源 ID 格式或真实原生 ID 不符合要求；旧 `ID.of` 解析规则不变。 |
| `NEKO-4057` | API module publication or access rejected | 检查模组 owner、暴露目标、导出名、公共类型、事件白名单及当前脚本环境。 |
| `NEKO-4060` | Script options rejected | 配置包含未知字段、错误类型或超出范围的值，注册被拒绝 |
| `NEKO-4061` | Post-effect builder rejected | 后处理 builder 已封存或配置无效，候选声明不予发布 |
| `NEKO-4062` | Render registration rejected | 渲染注册的参数、回调或声明元数据无效 |
| `NEKO-4063` | Script input declaration rejected | 脚本输入形状 provider 无法构造、返回无效形状，或缺少适配器目标 |
| `NEKO-4064` | Script output declaration rejected | 方法显式声明的 TypeScript 返回类型表达式为空 |
| `NEKO-4065` | Script output view rejected | 输出视图必须是实际实现的公开非泛型接口；原生输入契约不变。 |
| `NEKO-4066` | Probe declaration retained with incomplete public members | 可选公共签名缺失导致原生方法集无法反射。保留独立成员；详见 `incompleteMembers`，依赖闭包仍不完整。 |
| `NEKO-4090` | Mixin 准备后需重启，或声明/库被拒绝 | 先检查默认关闭的引擎配置 `[mixinjs] enabled`，修改后完整重启；准备成功后也需完整重启。其他情况检查缓存选择和归属、加载器实际接纳、精确环境、方法描述符和支持的选项。参见 [MixinJS 排查指南](mixinjs_cn#wiki-section-9)。 |
| `NEKO-4091` | Mixin 回调授权或桥接所有权被拒绝 | 使用本地可信候选及回调文件；其他活动根不能替换桥接租约。 |
| `NEKO-4092` | 原生钩子跳过或回调决策被丢弃 | 检查线程、同步返回、控制对象有效期和严格 ABI 转换。 |


<a id="wiki-section-6"></a>
## 配方与数据

`NEKO-5190`–`NEKO-5192` 属于明确限定资源形状的粉碎配方导入辅助器；此处不匹配，不能推断同一 addon 的所有 schema 或通用原生 schema 编译器都不受支持。

`NEKO-5240`–`NEKO-5279` 下列条目来自 NekuJS 的配方 schema 导入链路，而不是对所有 KubeJS 附属的兼容承诺。已识别但无法兑现的定义会在 Probe 中显示为 `never`，对应运行时构造被拒绝；先修复或补齐诊断指向的语义，不要修改生成声明来绕过它。

| 错误码 | 英文摘要 | 中文消息 |
|---|---|---|
| `NEKO-5001` | could not convert value to JSON | 无法转换为 JSON |
| `NEKO-5002` | JSON must be an object | JSON 必须是对象 |
| `NEKO-5003` | unknown recipe filter key | RecipeFilterAdapter: unknown key |
| `NEKO-5190` | recipe schema source lacks an expected resource | 配方 schema 来源缺少预期资源 |
| `NEKO-5191` | imported crushing schema profile differs from the explicitly supported resource shape | 导入的粉碎配方 schema 与明确支持的资源形状不同 |
| `NEKO-5192` | recipe schema source parsing or validation failed | 配方 schema 来源解析或校验失败 |
| `NEKO-5200` | selected recipe method threw a checked exception; no overload or schema fallback | 已选配方方法抛出受检异常，保留原因，不再尝试重载或 schema 回退 |
| `NEKO-5201` | selected recipe method is inaccessible; invocation rejected | 已选配方方法不可访问，调用被拒绝 |
| `NEKO-5202` | native type adapter acceptance or conversion failed; original cause preserved, not an overload mismatch | 原生类型适配器判定或转换失败，保留原始原因，不作为重载不匹配处理 |
| `NEKO-5210` | 高级配方定义或字段、别名、函数元数据不一致 | 修正原生配方定义，避免含糊或冲突的字段映射。 |
| `NEKO-5211` | 已知配方模型不受支持，不回退到通用配方 | 查看来源和拒绝原因；不要将未实现的语义当作通用双字段配方使用。 |
| `NEKO-5212` | 多个命名参数指向同一个字段 | 字段名与别名只能选择一个。 |
| `NEKO-5213` | 缺少必需的配方字段 | 按模型的构造器或命名参数补充字段。 |
| `NEKO-5214` | 没有配方构造器接受当前参数 | 使用该模型声明的参数顺序和数量。 |
| `NEKO-5215` | 配方字段编码器未返回 JSON 值 | 检查提供该模型的插件编码器。 |
| `NEKO-5216` | 声明式配方函数不接受参数 | 无参数函数只设置模型预先声明的值。 |

| `NEKO-5240` | Invalid schema value, shape, resource ID, field role or missing property | 字段值、结构、资源 ID、角色或必需属性无效 |
| `NEKO-5241` | Duplicate schema resource ID | 多个资源声明同一个 schema ID |
| `NEKO-5242` | Missing or unsupported parent schema | 父 schema 缺失或继承语义不受支持；检查来源文件与 $.parent |
| `NEKO-5243` | Schema parent cycle | 父 schema 形成继承环 |
| `NEKO-5244` | Invalid key ordering, duplicate key or incompatible requiredness | 字段顺序、重复字段或继承后的必需性不兼容 |
| `NEKO-5245` | Unknown schema key | 构造器、函数或常量引用不存在的 schema 字段 |
| `NEKO-5246` | Unsupported component or missing semantic codec | 字段组件无法解析，或没有真实语义编码器 |
| `NEKO-5247` | Invalid component constant or default-writing policy | 常量、默认值编码失败，或 default_optional 与 always_write 冲突 |
| `NEKO-5248` | Duplicate constructor key or arity | 构造器包含重复字段，或存在重复参数个数 |
| `NEKO-5249` | Ambiguous field alias or function/setter conflict | 字段别名含糊，或函数名与字段 setter 冲突 |
| `NEKO-5250` | Unsupported custom factory, processor, mapping or function behavior | 自定义配方工厂、处理器、映射或非声明式函数尚不支持 |
| `NEKO-5251` | Abstract schema dependency is not callable | 抽象 schema 仅供继承，不能直接构造配方 |
| `NEKO-5279` | Recipe schema resource read failed or root is not an object | schema 资源无法读取，或 JSON 根节点不是对象 |

| `NEKO-5306` | String component requires a string value | 字符串组件要求字符串值 |
| `NEKO-5307` | Pattern map requires nonempty single-character non-space keys | 图案映射必须非空，且键是单个非空格字符 |
| `NEKO-5308` | Crafting category input type is invalid | 合成分类输入类型无效；使用原生枚举或小写序列化值 |
| `NEKO-5309` | Native shaped preparation failed | 原生有序合成准备失败：检查图案、原料键、空原料或不支持的镜像／收缩控制 |

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
## JSX UI 视觉与资源契约

| 错误码 | 含义 |
|---|---|
| `NEKO-6008` | 自动资源组因已有文件属于外部或被修改而跳过。 |
| `NEKO-6009` | 自动物品资源生成失败；发布阶段失败会尝试回滚，还原失败作为 suppressed cause 保留。 |
| `NEKO-6010` | 物品资源所有权索引读取或校验失败；拒绝生成且不替换索引。 |
| `NEKO-6011` | 目录包客户端资源重载失败；查看异常原因及原版资源恢复结果。 |
| `NEKO-6012` | 原生 UI 宿主拒绝无效或未知的只读物品配置（资源 id、非空气物品、数量或提示属性）；候选更新失败时保留已激活界面。 |

| NEKO-6001 | UI 颜色属性不是受控颜色格式（ARGB/RGB 整数、`#RGB`/`#RRGGBB`/`#AARRGGBB`、CSS 基础命名色）。 |
| NEKO-6002 | UI 视觉属性值超出范围或类型错误（`opacity`、`fontSize`、`borderWidth`、`radius`、`fit`、`crop`）。 |
| NEKO-6003 | UI 资源标识不符合受控 id 语法（小写 `namespace:path`，不能含 `..`）。 |
| NEKO-6004 | 受控 UI 资源 id 无法在资源根目录中解析到资源。 |
| NEKO-6005 | 读取或上传已解析的 UI 资源失败，包括编码输入超过支持的大小上限。 |
| NEKO-6006 | UI 纹理或裁剪尺寸无效，或超过支持的尺寸范围。 |
| NEKO-6007 | 将已解析的 PNG 资源解码为可用 UI 图像失败。 |

<a id="wiki-section-8"></a>
## JSX UI 运行时生命周期

| 错误码 | 含义 |
|---|---|
| NEKO-7001 | UI 操作被拒绝：root 的 generation 已被替换或关闭（过期 handle）。 |
| NEKO-7002 | UI root 生命周期状态转换不合法（candidate/active/closing/closed）。 |
| NEKO-7003 | JSX host adapter 在受管 CLIENT 脚本上下文之外创建。 |
| NEKO-7004 | 未显式排队就尝试在客户端 owner thread 之外修改 UI。 |
| NEKO-7005 | generation 清理期间释放 UI root 失败；清理继续执行。 |
| NEKO-7006 | 延迟 UI 操作被丢弃：其 generation 从未提交或已被替换。 |
| NEKO-7007 | JSX UI 阶段失败（render/layout/event/host）已记录到诊断链路；包括宿主剪贴板读写失败。 |
| NEKO-7008 | 原生宿主拒绝不符合受控颜色/状态字段规则的控件 appearance。 |
| NEKO-7011 | 原生宿主拒绝无效滑块配置（非有限范围/值、非正 step 或超出范围）。 |
| NEKO-7012 | 原生宿主拒绝无效弹窗属性（缺失或空白 id、非字符串标题、非布尔关闭开关）；公共组件或布局校验也可能通过 `NEKO-7007` 记录阶段失败。 |
| NEKO-7013 | Workspace 编写契约拒绝无效配置／ID 或 root 发布阶段修改控制器；构造／控制器调用可直接抛出异常，UI 阶段也可能用 `NEKO-7007` 包装其原因。 |
| NEKO-7014 | managed-mount 编写契约拒绝非正／不可表示的 GUI 区域边界、独占 Screen 的区域专用操作或其它 root handle／layout root ID。构造、绑定或发布前校验可直接拒绝；同 handle 绑定幂等。不是新增 HTTP/MCP wire error。 |
| NEKO-7015 | 内置诊断 UI 会话不可用、已关闭，或加载／绘制／事件分发失败。支持的 NeoForge 26.x 客户端中，当前失败的仪表盘只请求一次原生回退；关闭／停机及晚到更新不会使其重新弹出。不是新增公共 Script API 或网络包状态。 |

<a id="wiki-section-9"></a>
## JSX UI 检查器

| 错误码 | 含义 |
|---|---|
| NEKO-8001 | 运行时布局快照不符合检查器读取契约（runtime/host 版本不一致）；保留最后一帧有效结果。 |

<a id="wiki-section-10"></a>
## 不带错误码的消息

常规进度消息带有英文摘要，但不带错误码。

| 英文摘要 | 中文消息 |
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

[上一篇: 命令](commands_cn) · [目录](Home) · [下一篇: 版本化脚本包](script-packs_cn)
