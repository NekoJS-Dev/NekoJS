<!-- wiki-page: error-reference; locale: us -->

> **English** · [中文](error-reference_cn)

<a id="wiki-section-1"></a>
# Error and log message reference

Messages that report a problem carry a stable code and a short English summary.
The Chinese text is unchanged apart from the code prefix and the summary.
The code is the lookup key and does not change when the wording does.

```
[NEKO-2002] 脚本语句累计数达到 scriptStatementLimit（50000000），… — script statement limit exceeded
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

<a id="wiki-section-3"></a>
## Sandbox and resource limits

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-2001` | script context hit resource limits | 脚本环境触发 ResourceLimits |
| `NEKO-2002` | script statement limit exceeded | 脚本语句累计数达到 scriptStatementLimit |
| `NEKO-2003` | runaway script loop detected | 脚本同步执行持续超过 scriptRunawayTimeoutSeconds |
| `NEKO-2004` | script output line limit reached | 脚本输出行数超过行上限 |
| `NEKO-2005` | script evaluation timed out | 脚本求值超时 |

<a id="wiki-section-4"></a>
## Data synchronization

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-3010` | persistent data sync skipped because the payload exceeds the limit | PData 同步因数据超过上限而跳过 |
| `NEKO-3011` | server Item/Block modification sync rejected | 服务端 Item/Block modification 同步被客户端拒绝 |

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

<a id="wiki-section-6"></a>
## Recipes and data

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-5001` | could not convert value to JSON | 无法转换为 JSON |
| `NEKO-5002` | JSON must be an object | JSON 必须是对象 |
| `NEKO-5003` | unknown recipe filter key | RecipeFilterAdapter: unknown key |

<a id="wiki-section-7"></a>
## JSX UI visual and resource contract

| Code | Meaning |
|---|---|
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
| NEKO-7007 | JSX UI phase failure (render/layout/event/host) recorded into the diagnostics chain. |

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

[Previous: Commands](commands_us) · [Contents](Home) · [Next: Recipe system](recipe-system_us)
