<!-- wiki-page: error-reference; locale: cn -->

> **中文** · [English](error-reference_us)

<a id="wiki-section-1"></a>
# 错误与日志参考

NekoJS 报告问题时，消息前面会带一个 `NEKO-xxxx` 错误码。把错误码复制下来，在本页搜索，就能查到它的意思。

<a id="wiki-section-2"></a>
## 怎么找到错误

1. 执行 `/nekojs error` 看有没有报错，或执行 `/nekojs view_all_errors` 查看完整列表。
2. 打开 `logs/nekojs/<类型>.log`，例如 `logs/nekojs/server.log`。
3. 找到以 `[NEKO-xxxx]` 开头的那一行，在本页查这个错误码。

报告问题时，请附上错误码、完整消息、脚本类型、文件名和行号。

<a id="wiki-section-3"></a>
## 消息格式

消息由错误码、中文或英文说明和英文摘要组成。英文摘要放在 `—` 后面：

```text
[NEKO-2004] 脚本输出行数超过 … 行上限，后续输出将被丢弃（防止日志无限增长） — script output line limit reached
```

消息末尾带具体值时，值写在摘要后面：

```text
[NEKO-4005] 无法解析目标 — could not resolve goal target: minecraft:zombie
```

错误码不会因为措辞调整而改变，查询时以错误码为准。

AI 查询服务返回的 `CONTROL_DISABLED`、`INDEX_UNAVAILABLE` 等返回码是另一套，见 [AI 辅助脚本编写](ai-support_cn#wiki-live-controls)。

<a id="wiki-section-4"></a>
## 脚本加载与重载

| 错误码 | 英文摘要 | 说明 |
|---|---|---|
| `NEKO-1001` | failed to scan script directory | 扫描脚本目录失败 |
| `NEKO-1002` | failed to create workspace directory | 无法初始化环境目录 |
| `NEKO-1003` | script execution failed | 脚本执行失败 |
| `NEKO-1004` | problem in after dependency order | 脚本 after 依赖排序存在问题 |
| `NEKO-1005` | script unreadable, skipped | 无法读取脚本，已跳过 |
| `NEKO-1006` | startup reload is not transactional | 脚本重载为非事务式语义 |
| `NEKO-1008` | transactional script reload failed | 脚本事务重载失败，候选 generation 已关闭，active 环境保持不变 |
| `NEKO-1013` | script pack state update or temporary cleanup failed | 脚本包状态保存或暂存文件清理失败 |
| `NEKO-1014` | script pack state unreadable; discovery uses manifest default | 脚本包状态不可读取，发现时使用清单默认值 |
| `NEKO-1016` | versioned pack descriptor or entry validation rejected | 新版脚本包描述或入口校验被拒绝 |
| `NEKO-1017` | package activation, capability, operation or resource release rejected/failed | 包激活、能力调用、操作或资源释放被拒绝或失败 |
| `NEKO-1018` | package author tool or embedded content admission failed | 脚本包的创建、校验、打包、安装、导出 JAR，或从模组 JAR 载入内容失败 |
| `NEKO-1019` | Authoring bundle, configuration, snapshot or local service setup failed | AI 文档包、配置、API 快照或本地服务初始化失败；游戏照常运行 |
| `NEKO-1020` | admitted authoring owner callback failed | AI 实时操作的回调执行失败，不会自动重试 |
| `NEKO-1021` | authoring command rejected inside an active command context | 已有命令正在执行，AI 命令被拒绝，避免它排队后延迟执行 |
| `NEKO-1022` | authoring SERVER committed but postprocessing failed | SERVER 脚本已生效，但配方、脚本包或资源的后续处理失败 |
| `NEKO-1023` | authoring live control requires server owner thread | 实时 AI 操作没有在正确的线程上执行，被拒绝 |
| `NEKO-1024` | authoring live server runtime is no longer current | 服务器或客户端已停止或更换，旧操作不会在新世界里继续执行 |
| `NEKO-1025` | script property rejected; previous value or default retained | 脚本开头的属性注释无效；沿用之前的有效值或默认值。警告里有文件、行号和原因 |

<a id="wiki-section-5"></a>
## 日志输出上限

| 错误码 | 英文摘要 | 说明 |
|---|---|---|
| `NEKO-2004` | script output line limit reached | 脚本输出行数超过行上限 |

脚本的标准输出和错误输出各自最多写入 100,000 行日志，超过后的输出会被丢弃，并只警告一次。这只限制日志，不限制脚本运行。

NekoJS 不会因为脚本运行太久而中断它。死循环或永远不会完成的 `await` 会让游戏卡住，见 [脚本基础](script-basics_cn) 的“小心死循环”。

<a id="wiki-section-6"></a>
## 数据同步

| 错误码 | 英文摘要 | 说明 |
|---|---|---|
| `NEKO-3010` | persistent data sync skipped because the payload exceeds the limit | 玩家持久化数据超过大小上限，跳过同步 |
| `NEKO-3011` | server Item/Block modification sync rejected | 客户端拒绝了服务端的物品/方块修改同步 |
| `NEKO-3012` | dynamic registry message delivery failed | 动态注册表消息发送失败 |
| `NEKO-3013` | dynamic registry configuration failed; connection disconnected | 动态注册表配置同步失败，连接已断开 |
| `NEKO-3014` | malformed dynamic registry message or reply dropped | 无效动态注册表消息或回执已丢弃 |
| `NEKO-3015` | dynamic registry sync reply could not be sent | 动态注册表同步回执发送失败 |

<a id="wiki-section-7"></a>
## 注册与绑定

| 错误码 | 英文摘要 | 说明 |
|---|---|---|
| `NEKO-4001` | duplicate binding name ignored | 同名绑定已注册，后者被忽略（首胜） |
| `NEKO-4002` | goal target must be a LivingEntity | 目标类型必须是 LivingEntity |
| `NEKO-4003` | cannot infer target class from EntityType | 无法从 EntityType 推断目标类 |
| `NEKO-4004` | unknown target entity | 未知目标实体 |
| `NEKO-4005` | could not resolve goal target | 无法解析目标 |
| `NEKO-4006` | unknown block entity type | 未知方块实体类型（注册方块实体能力时找不到目标） |
| `NEKO-4007` | unknown capability | 未知能力类型（支持 item / energy / fluid） |
| `NEKO-4008` | invalid entity factory or entity class constructor | 实体 factory 无效，或实体类缺少 (EntityType, Level) 构造器 |
| `NEKO-4009` | capability provider execution failed | 能力 provider 执行时出错 |
| `NEKO-4010` | capability provider returned an incompatible non-null value | 能力 provider 返回了类型不兼容的值 |
| `NEKO-4011` | capability batch is no longer collecting or its native commit failed | 能力注册阶段已经结束，或原生提交失败 |
| `NEKO-4012` | capability factory argument is negative | 能力工厂的参数不能为负数 |
| `NEKO-4013` | client persistent data mirror is read-only | 客户端持久化数据镜像只读 |
| `NEKO-4014` | duplicate capability provider | 同一目标重复注册了同一种能力 provider |
| `NEKO-4017` | invalid persistent native goal factory or goal constructor | 持久化的原生 goal factory 或构造器无效 |
| `NEKO-4019` | invalid or unknown capability target | 能力目标无效或不存在 |
| `NEKO-4020` | invalid native capability/provider/context input | 原生能力、provider 或 context 的参数无效 |
| `NEKO-4024` | saved capability storage is missing, invalid, or outside its bounds | 存档中的能力数据缺失、无效或超出范围；保留当前数据 |
| `NEKO-4025` | invalid entity renderer, texture, shadow, or native renderer constructor | 实体渲染器、纹理、阴影或原生渲染器构造器无效 |
| `NEKO-4026` | texture dimensions must be positive | 纹理尺寸必须为正数 |
| `NEKO-4027` | storage persistence attempted during an uncommitted transfer transaction | 物品转移事务还没提交时，不能加载或保存存储 |
| `NEKO-4028` | Fabric registry drain did not consume a selected pending registry | Fabric 注册阶段结束时还有没处理完的待注册内容 |
| `NEKO-4029` | Probe type reflection is incomplete; declarations may be omitted | Probe 类型反射不完整，部分声明可能缺失 |
| `NEKO-4030` | Localization binding rejected invalid text or format arguments | 本地化 Binding 拒绝无效文本或格式参数 |
| `NEKO-4031` | Authored class declaration registration rejected | 手写类声明注册被拒绝 |
| `NEKO-4032` | Conflicting authored class declarations rejected at the same priority | 同优先级的手写类声明冲突，注册被拒绝 |
| `NEKO-4033` | Authored class declaration dependency is unavailable, excluded, or hidden | 手写类声明依赖不可用、被排除或被隐藏 |
| `NEKO-4034` | item component, model, texture or identifier configuration rejected | 物品组件、模型、纹理或标识符配置被拒绝 |
| `NEKO-4035` | item definition modification rejected after startup collection | 启动脚本收集完成后物品定义已冻结，之后的修改被拒绝 |
| `NEKO-4036` | item component type or codec value rejected with item, component and node context | 物品组件类型或值无效；消息里有物品、组件和具体位置 |
| `NEKO-4038` | Probe predeclaration failed; the selected class is omitted from generated TypeScript | Probe 预声明失败，选定类未进入生成的 TypeScript |
| `NEKO-4039` | Probe declaration task failed; some generated TypeScript declarations may be absent | Probe 声明任务失败，生成的 TypeScript 可能缺少部分声明 |
| `NEKO-4040` | Activation claim publication rejected: unsupported registry | 激活时遇到不支持的注册表（内部错误，请报告） |
| `NEKO-4041` | Villager trade replay failed after resource reload; committed snapshot is unavailable | 资源重载后重新应用村民交易失败，交易暂时不可用 |
| `NEKO-4042` | Dynamic registry rollback failed; the registry may retain partial writes | 动态注册回滚失败，注册表里可能留有部分写入 |
| `NEKO-4043` | Villager trade batch rejected: generated ID collision | 生成的交易 ID 与已有条目或另一组交易冲突，写入前被拒绝 |
| `NEKO-4044` | Villager trade checkpoint read/restore failed | 村民交易快照读取或恢复失败 |
| `NEKO-4045` | Unknown profession | 交易引用了不存在的职业，这批交易全部被拒绝 |
| `NEKO-4046` | Villager trade pool replacement failed | 替换静态交易池失败 |
| `NEKO-4047` | TypeScript Bean alias exclusion registration rejected | TypeScript Bean 属性排除条目无效 |
| `NEKO-4048` | Probe TypeScript dependency closure incomplete | 必需的类型被排除、隐藏或无法反射，详见 `@special/dependencies.json`。生成成功不代表 IDE 检查完整 |
| `NEKO-4050` | Dynamic item fire resistance value rejected before activation | 动态物品的防火属性必须是 `true` 或 `false` |
| `NEKO-4051` | Manual declaration type reference rejected | 手写声明的类型引用别名无效，或同名别名的目标类型、输入模式冲突 |
| `NEKO-4052` | Registry type catalog rejected conflicting sources or symbols | 注册表类型目录存在同名异来源，或与标签/命名空间类型名冲突 |
| `NEKO-4053` | Bean method projection rejected | Bean 方法投影的来源、无参签名或收集器无效 |
| `NEKO-4054` | Item component string conversion failed | 物品组件字符串无法解析或序列化 |
| `NEKO-4055` | Authoring query or transport request rejected | AI 查询或请求被拒绝；响应中的返回码和 JSON 路径说明了具体原因 |
| `NEKO-4056` | Resource ID conversion rejected | 资源 ID 格式不对，或不是真实存在的原生 ID；旧的 `ID.of` 解析规则不变 |
| `NEKO-4057` | API module publication or access rejected | 检查模组归属、暴露目标、导出名、公共类型、事件白名单和当前脚本类型 |
| `NEKO-4060` | Script options rejected | 配置对象里有未知字段、类型错误或超出范围的值，注册被拒绝 |
| `NEKO-4061` | Post-effect builder rejected | 后处理 builder 已在回调结束后关闭，或配置无效；这次声明不生效 |
| `NEKO-4062` | Render registration rejected | 渲染注册的参数、回调或元数据无效 |
| `NEKO-4063` | Script input declaration rejected | 脚本输入形状的 provider 无法创建、返回了无效形状，或缺少适配目标 |
| `NEKO-4064` | Script output declaration rejected | 方法声明的 TypeScript 返回类型为空 |
| `NEKO-4065` | Script output view rejected | 输出视图必须是真正实现了的、公开的非泛型接口 |
| `NEKO-4066` | Probe declaration retained with incomplete public members | 缺少可选的公共签名，无法反射完整的方法列表。其他成员仍保留；详见 `incompleteMembers` |
| `NEKO-4090` | Mixin preparation/restart required or declaration/library rejected | 先检查引擎配置 `[mixinjs] enabled`（默认关闭），修改后完整重启；准备成功后也要完整重启。其他情况检查缓存、加载器是否接受、目标环境、方法描述符和选项。见 [MixinJS 故障排查](mixinjs_cn#wiki-mixinjs-troubleshooting) |
| `NEKO-4091` | Mixin callback authority or bridge ownership rejected | 使用本地可信的候选和回调文件；其他运行中的实例不能接管桥接 |
| `NEKO-4092` | Native hook bypassed or callback decision discarded | 检查线程、同步返回值、控制对象的有效期和类型转换 |

<a id="wiki-section-8"></a>
## 配方、战利品表与数据

| 错误码 | 英文摘要 | 说明 |
|---|---|---|
| `NEKO-2301` | Loot table JSON replacement unavailable; keeping the previous table | 当前环境缺少注册表上下文，无法替换战利品表 JSON；保留原来的战利品表 |
| `NEKO-5001` | could not convert value to JSON | 无法转换为 JSON |
| `NEKO-5002` | JSON must be an object | JSON 必须是对象 |
| `NEKO-5003` | unknown recipe filter key | RecipeFilterAdapter: unknown key |
| `NEKO-5200` | selected recipe method threw a checked exception; no overload or schema fallback | 所选配方方法抛出受检异常；保留原因，不再尝试其他重载 |
| `NEKO-5201` | selected recipe method is inaccessible; invocation rejected | 已选配方方法不可访问，调用被拒绝 |
| `NEKO-5202` | native type adapter acceptance or conversion failed; original cause preserved, not an overload mismatch | 原生类型适配器判断或转换失败；保留原始原因，不算作重载不匹配 |
| `NEKO-5210` | invalid advanced recipe definition or inconsistent field/alias/function metadata | 高级配方定义无效，或字段、别名、函数信息不一致。修正定义，避免含糊或冲突的字段映射 |
| `NEKO-5211` | known recipe profile is explicitly unsupported; no handler/raw fallback | 这个配方模型不受支持，不会退回成通用配方。查看来源和拒绝原因 |
| `NEKO-5212` | multiple named arguments target the same canonical field | 多个命名参数指向同一个字段。字段名和别名只能用一个 |
| `NEKO-5213` | required schema field is missing | 缺少必需的配方字段。按模型的构造参数补齐 |
| `NEKO-5214` | no schema constructor accepts the supplied arguments | 没有配方构造器接受当前参数。按模型声明的参数顺序和数量传参 |
| `NEKO-5215` | recipe field encoder returned no JSON value | 配方字段编码器没有返回 JSON 值。检查提供该模型的插件 |
| `NEKO-5216` | declarative schema function received arguments | 声明式配方函数不接受参数 |
| `NEKO-5350` | host does not support native builder sessions | 宿主不支持原生构建器会话 |
| `NEKO-5352` | host requires a native JSON builder | 宿主需要原生 JSON 构建器 |
| `NEKO-5353` | host has no borrowed registry access | 宿主缺少借用的注册表访问 |

<a id="wiki-section-9"></a>
## JSX UI：外观与资源

| 错误码 | 含义 |
|---|---|
| `NEKO-6001` | UI 颜色格式不对。支持 ARGB/RGB 整数、`#RGB`/`#RRGGBB`/`#AARRGGBB` 和 CSS 基础颜色名 |
| `NEKO-6002` | UI 外观属性超出范围或类型错误（`opacity`、`fontSize`、`borderWidth`、`radius`、`fit`、`crop`） |
| `NEKO-6003` | UI 资源 id 格式不对，必须是小写的 `namespace:path`，不能含 `..` |
| `NEKO-6004` | 找不到这个 UI 资源 id 对应的文件 |
| `NEKO-6005` | 读取或上传 UI 资源失败，包括文件超过大小上限 |
| `NEKO-6006` | UI 纹理或裁剪尺寸无效或过大 |
| `NEKO-6007` | PNG 资源解码失败 |
| `NEKO-6008` | 已有的资源文件属于外部或被修改过，跳过自动生成这一组资源 |
| `NEKO-6009` | 自动生成物品资源失败；发布阶段失败时会尝试回滚 |
| `NEKO-6010` | 物品资源归属索引读取或校验失败；不生成资源，也不替换索引 |
| `NEKO-6011` | 目录包的客户端资源重载失败；查看异常原因和原版资源是否恢复 |
| `NEKO-6012` | UI 中物品的配置无效（资源 id、空气物品、数量或提示属性）；更新失败时保留当前界面 |

<a id="wiki-section-10"></a>
## JSX UI：运行时

| 错误码 | 含义 |
|---|---|
| `NEKO-7001` | UI 操作被拒绝：界面属于已被 reload 替换或已关闭的脚本（句柄已过期） |
| `NEKO-7002` | UI 根节点的生命周期状态转换不合法 |
| `NEKO-7003` | 在 CLIENT 脚本之外创建了 JSX 宿主 |
| `NEKO-7004` | 在客户端线程之外直接修改 UI，没有先排队 |
| `NEKO-7005` | reload 清理时释放 UI 根节点失败；清理继续进行 |
| `NEKO-7006` | 延迟的 UI 操作被丢弃：它所属的脚本没有生效或已被替换 |
| `NEKO-7007` | JSX UI 在渲染、布局、事件或宿主阶段出错，包括剪贴板读写失败 |
| `NEKO-7008` | 控件外观里的颜色或状态字段无效 |
| `NEKO-7011` | 滑块配置无效：范围或值不是有限数、步长不是正数，或值超出范围 |
| `NEKO-7012` | 对话框属性无效：id 缺失或为空、标题不是字符串、关闭开关不是布尔值。也可能以 `NEKO-7007` 报告 |
| `NEKO-7013` | Workspace 的配置或 ID 无效，或在发布阶段修改了控制器。也可能以 `NEKO-7007` 报告 |
| `NEKO-7014` | 挂载区域的边界无效，或对独占 Screen 调用了区域专用操作，或使用了其他根句柄/布局根 ID |
| `NEKO-7015` | 内置诊断界面不可用、已关闭，或加载、绘制、事件处理失败。NeoForge 26.x 上失败的面板只会请求一次原生界面回退 |
| `NEKO-8001` | UI 检查器读到的布局快照格式不匹配（版本不一致）；保留最后一帧有效结果 |

<a id="wiki-section-11"></a>
## NekuJS 附加组件

下面的错误码来自 NekuJS 附加组件，只在安装了它、并使用为 KubeJS 编写的附属模组（例如配方 schema）时出现。消息里会写明出错的资源和 JSON 路径，按它修改对应的定义。被识别但无法支持的配方，在 Probe 生成的声明里显示为 `never`，调用时会被拒绝。

| 错误码 | 英文摘要 | 说明 |
|---|---|---|
| `NEKO-5190` | recipe schema source lacks an expected resource | 配方 schema 来源缺少预期资源 |
| `NEKO-5191` | imported crushing schema profile differs from the explicitly supported resource shape | 导入的粉碎配方 schema 与明确支持的资源形状不同 |
| `NEKO-5192` | recipe schema source parsing or validation failed | 配方 schema 来源解析或校验失败 |
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
| `NEKO-5360` | expected a recipe number | 需要配方数值 |
| `NEKO-5361` | expected a recipe boolean | 需要配方布尔值 |
| `NEKO-5362` | tag belongs to a different registry | 标签属于其他注册表 |
| `NEKO-5363` | expected a tag identifier | 需要标签标识符 |
| `NEKO-5364` | resource key belongs to a different registry | 资源键属于其他注册表 |
| `NEKO-5365` | expected a resource identifier | 需要资源标识符 |
| `NEKO-5366` | wrapped list size is outside bounds | 转换后的列表长度越界 |
| `NEKO-5367` | structured conversion requires the caller active Graal context | 结构化转换需要调用方已进入的 Graal 上下文 |

NekuJS 还会用到 `NEKO-4190`–`NEKO-4287` 和 `NEKO-5280`–`NEKO-5305`，这些消息都是自带说明的英文。

<a id="wiki-section-12"></a>
## 已停用的错误码

旧日志里可能还会看到下面这些错误码。它们不会再出现，也不会被挪作他用。

| 错误码 | 情况 |
|---|---|
| `NEKO-1007` | 旧的重载失败消息，已被 `NEKO-1008` 取代 |
| `NEKO-1009`、`NEKO-1010` | 关闭旧运行时/旧上下文的消息，当前代码不再输出 |
| `NEKO-1011`、`NEKO-1012` | 旧的客户端加载日志，未使用 |
| `NEKO-2001`、`NEKO-2002`、`NEKO-2003`、`NEKO-2005` | 旧的执行时间/语句数限制，相关功能已移除 |
| `NEKO-3001`–`NEKO-3004` | 旧的脚本同步大小限制，未使用 |
| `NEKO-4018` | ItemStack persistent data is unavailable，当前代码不再输出 |
| `NEKO-4037` | Fabric 自动物品资源的旧限制，已解除 |

<a id="wiki-section-13"></a>
## 不带错误码的消息

常规进度消息只有英文摘要，没有错误码。

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
