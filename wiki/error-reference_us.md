<!-- wiki-page: error-reference; locale: us -->

> **English** · [中文](error-reference_cn)

<a id="wiki-section-1"></a>
# Error and log reference

When NekoJS reports a problem, the message starts with a `NEKO-xxxx` code. Copy the code and search this page for it.

<a id="wiki-section-2"></a>
## Finding errors

1. Run `/nekojs error` to see whether there are errors, or `/nekojs view_all_errors` for the full list.
2. Open `logs/nekojs/<type>.log`, for example `logs/nekojs/server.log`.
3. Find the line starting with `[NEKO-xxxx]` and look up that code here.

When you report a problem, include the code, the full message, the script type, the file name and the line number.

<a id="wiki-section-3"></a>
## Message format

A message has the code, a Chinese or English description, and an English summary after `—`:

```text
[NEKO-2004] 脚本输出行数超过 … 行上限，后续输出将被丢弃（防止日志无限增长） — script output line limit reached
```

When a message ends with a specific value, the value comes after the summary:

```text
[NEKO-4005] 无法解析目标 — could not resolve goal target: minecraft:zombie
```

Codes do not change when the wording changes, so always search by code.

Codes such as `CONTROL_DISABLED` and `INDEX_UNAVAILABLE` returned by the AI query service are a separate set; see [AI-assisted authoring](ai-support_us#wiki-live-controls).

<a id="wiki-section-4"></a>
## Script loading and reload

| Code | English summary | Details |
|---|---|---|
| `NEKO-1001` | failed to scan script directory | Chinese log text: 扫描脚本目录失败 |
| `NEKO-1002` | failed to create workspace directory | Chinese log text: 无法初始化环境目录 |
| `NEKO-1003` | script execution failed | Chinese log text: 脚本执行失败 |
| `NEKO-1004` | problem in after dependency order | Chinese log text: 脚本 after 依赖排序存在问题 |
| `NEKO-1005` | script unreadable, skipped | Chinese log text: 无法读取脚本，已跳过 |
| `NEKO-1006` | startup reload is not transactional | STARTUP registers items, blocks and entities that cannot be safely rolled back. Chinese log text: 脚本重载为非事务式语义 |
| `NEKO-1008` | transactional script reload failed | The failed reload is discarded; the previous scripts stay in use. Chinese log text: 脚本事务重载失败，候选 generation 已关闭，active 环境保持不变 |
| `NEKO-1013` | script pack state update or temporary cleanup failed | — |
| `NEKO-1014` | script pack state unreadable; discovery uses manifest default | — |
| `NEKO-1016` | versioned pack descriptor or entry validation rejected | — |
| `NEKO-1017` | package activation, capability, operation or resource release rejected/failed | — |
| `NEKO-1018` | package author tool or embedded content admission failed | — |
| `NEKO-1019` | Authoring bundle, configuration, snapshot or local service setup failed | The game itself keeps running |
| `NEKO-1020` | admitted authoring owner callback failed | Not retried automatically |
| `NEKO-1021` | authoring command rejected inside an active command context | Rejected so it does not run late after queueing |
| `NEKO-1022` | authoring SERVER committed but postprocessing failed | — |
| `NEKO-1023` | authoring live control requires server owner thread | The live AI action did not run on the right thread |
| `NEKO-1024` | authoring live server runtime is no longer current | The server or client stopped or changed; the old action does not continue in the new world |
| `NEKO-1025` | script property rejected; previous value or default retained | The warning gives the file, line and reason |

<a id="wiki-section-5"></a>
## Log output limit

| Code | English summary | Details |
|---|---|---|
| `NEKO-2004` | script output line limit reached | Chinese log text: 脚本输出行数超过行上限 |

Script standard output and error output each log at most 100,000 lines; anything after that is dropped, with a single warning. This limits the log only, not script execution.

NekoJS does not stop a script for running too long. An infinite loop or an `await` that never finishes freezes the game; see "Watch out for infinite loops" in [Script basics](script-basics_us).

<a id="wiki-section-6"></a>
## Data synchronization

| Code | English summary | Details |
|---|---|---|
| `NEKO-3010` | persistent data sync skipped because the payload exceeds the limit | — |
| `NEKO-3011` | server Item/Block modification sync rejected | — |
| `NEKO-3012` | dynamic registry message delivery failed | — |
| `NEKO-3013` | dynamic registry configuration failed; connection disconnected | — |
| `NEKO-3014` | malformed dynamic registry message or reply dropped | — |
| `NEKO-3015` | dynamic registry sync reply could not be sent | — |

<a id="wiki-section-7"></a>
## Registration and bindings

| Code | English summary | Details |
|---|---|---|
| `NEKO-4001` | duplicate binding name ignored | The first registration wins. Chinese log text: 同名绑定已注册，后者被忽略（首胜） |
| `NEKO-4002` | goal target must be a LivingEntity | Chinese log text: 目标类型必须是 LivingEntity |
| `NEKO-4003` | cannot infer target class from EntityType | Chinese log text: 无法从 EntityType 推断目标类 |
| `NEKO-4004` | unknown target entity | Chinese log text: 未知目标实体 |
| `NEKO-4005` | could not resolve goal target | Chinese log text: 无法解析目标 |
| `NEKO-4006` | unknown block entity type | Raised when a block entity capability target cannot be found |
| `NEKO-4007` | unknown capability | Supported: item, energy, fluid |
| `NEKO-4008` | invalid entity factory or entity class constructor | The entity class needs an (EntityType, Level) constructor |
| `NEKO-4009` | capability provider execution failed | — |
| `NEKO-4010` | capability provider returned an incompatible non-null value | — |
| `NEKO-4011` | capability batch is no longer collecting or its native commit failed | — |
| `NEKO-4012` | capability factory argument is negative | — |
| `NEKO-4013` | client persistent data mirror is read-only | — |
| `NEKO-4014` | duplicate capability provider | — |
| `NEKO-4017` | invalid persistent native goal factory or goal constructor | — |
| `NEKO-4019` | invalid or unknown capability target | — |
| `NEKO-4020` | invalid native capability/provider/context input | — |
| `NEKO-4024` | saved capability storage is missing, invalid, or outside its bounds | The current data is kept |
| `NEKO-4025` | invalid entity renderer, texture, shadow, or native renderer constructor | — |
| `NEKO-4026` | texture dimensions must be positive | — |
| `NEKO-4027` | storage persistence attempted during an uncommitted transfer transaction | — |
| `NEKO-4028` | Fabric registry drain did not consume a selected pending registry | — |
| `NEKO-4029` | Probe type reflection is incomplete; declarations may be omitted | — |
| `NEKO-4030` | Localization binding rejected invalid text or format arguments | — |
| `NEKO-4031` | Authored class declaration registration rejected | — |
| `NEKO-4032` | Conflicting authored class declarations rejected at the same priority | — |
| `NEKO-4033` | Authored class declaration dependency is unavailable, excluded, or hidden | — |
| `NEKO-4034` | item component, model, texture or identifier configuration rejected | — |
| `NEKO-4035` | item definition modification rejected after startup collection | — |
| `NEKO-4036` | item component type or codec value rejected with item, component and node context | — |
| `NEKO-4038` | Probe predeclaration failed; the selected class is omitted from generated TypeScript | — |
| `NEKO-4039` | Probe declaration task failed; some generated TypeScript declarations may be absent | — |
| `NEKO-4040` | Activation claim publication rejected: unsupported registry | An internal error; please report it |
| `NEKO-4041` | Villager trade replay failed after resource reload; committed snapshot is unavailable | — |
| `NEKO-4042` | Dynamic registry rollback failed; the registry may retain partial writes | — |
| `NEKO-4043` | Villager trade batch rejected: generated ID collision | Rejected before anything is written |
| `NEKO-4044` | Villager trade checkpoint read/restore failed | — |
| `NEKO-4045` | Unknown profession | The whole batch of trades is rejected |
| `NEKO-4046` | Villager trade pool replacement failed | — |
| `NEKO-4047` | TypeScript Bean alias exclusion registration rejected | — |
| `NEKO-4048` | Probe TypeScript dependency closure incomplete | Required types are excluded, hidden or unavailable; see `@special/dependencies.json`. A successful generation does not mean a complete IDE check |
| `NEKO-4050` | Dynamic item fire resistance value rejected before activation | — |
| `NEKO-4051` | Manual declaration type reference rejected | — |
| `NEKO-4052` | Registry type catalog rejected conflicting sources or symbols | — |
| `NEKO-4053` | Bean method projection rejected | — |
| `NEKO-4054` | Item component string conversion failed | — |
| `NEKO-4055` | Authoring query or transport request rejected | The response code and JSON path give the exact reason |
| `NEKO-4056` | Resource ID conversion rejected | Strict resource syntax or a real native ID is required; legacy `ID.of` parsing is unchanged |
| `NEKO-4057` | API module publication or access rejected | Check the mod owner, exposure target, export name, public type, event allowlist and script type |
| `NEKO-4060` | Script options rejected | Unknown fields, wrong types or out-of-range values reject the registration |
| `NEKO-4061` | Post-effect builder rejected | The builder was used after its callback ended, or its settings are invalid; the declaration does not apply |
| `NEKO-4062` | Render registration rejected | Invalid arguments, callback or metadata |
| `NEKO-4063` | Script input declaration rejected | The input-shape provider cannot be created, returns an invalid shape, or needs a missing adapter target |
| `NEKO-4064` | Script output declaration rejected | A method's declared TypeScript return type is blank |
| `NEKO-4065` | Script output view rejected | The view must be an implemented public non-generic interface |
| `NEKO-4066` | Probe declaration retained with incomplete public members | A missing optional public signature prevents reflecting the full method list. Other members stay; see `incompleteMembers` |
| `NEKO-4090` | Mixin preparation/restart required or declaration/library rejected | First check `[mixinjs] enabled` in the engine config (off by default) and fully restart after changing it; also restart after a successful preparation. Otherwise check the cache, loader acceptance, target environment, method descriptor and options. See [MixinJS troubleshooting](mixinjs_us#wiki-mixinjs-troubleshooting) |
| `NEKO-4091` | Mixin callback authority or bridge ownership rejected | Use a locally trusted candidate and callback file; another running instance cannot take over the bridge |
| `NEKO-4092` | Native hook bypassed or callback decision discarded | Check the thread, synchronous return value, control object lifetime and type conversion |

<a id="wiki-section-8"></a>
## Recipes, loot tables and data

| Code | English summary | Details |
|---|---|---|
| `NEKO-2301` | Loot table JSON replacement unavailable; keeping the previous table | — |
| `NEKO-5001` | could not convert value to JSON | Chinese log text: 无法转换为 JSON |
| `NEKO-5002` | JSON must be an object | Chinese log text: JSON 必须是对象 |
| `NEKO-5003` | unknown recipe filter key | — |
| `NEKO-5200` | selected recipe method threw a checked exception; no overload or schema fallback | — |
| `NEKO-5201` | selected recipe method is inaccessible; invocation rejected | — |
| `NEKO-5202` | native type adapter acceptance or conversion failed; original cause preserved, not an overload mismatch | — |
| `NEKO-5210` | invalid advanced recipe definition or inconsistent field/alias/function metadata | Fix the definition so that field mappings are not ambiguous or conflicting |
| `NEKO-5211` | known recipe profile is explicitly unsupported; no handler/raw fallback | Check the source and the reason; it does not fall back to a generic recipe |
| `NEKO-5212` | multiple named arguments target the same canonical field | Use either the field name or an alias, not both |
| `NEKO-5213` | required schema field is missing | Add the field using the model's constructor arguments |
| `NEKO-5214` | no schema constructor accepts the supplied arguments | Pass arguments in the order and number the model declares |
| `NEKO-5215` | recipe field encoder returned no JSON value | Check the plugin that provides the model |
| `NEKO-5216` | declarative schema function received arguments | A declarative function takes no arguments |
| `NEKO-5350` | host does not support native builder sessions | — |
| `NEKO-5352` | host requires a native JSON builder | — |
| `NEKO-5353` | host has no borrowed registry access | — |

<a id="wiki-section-9"></a>
## JSX UI: appearance and resources

| Code | Meaning |
|---|---|
| `NEKO-6001` | Bad UI color format. Supported: ARGB/RGB integers, `#RGB`/`#RRGGBB`/`#AARRGGBB` and CSS basic color names |
| `NEKO-6002` | A UI appearance value is out of range or the wrong type (`opacity`, `fontSize`, `borderWidth`, `radius`, `fit`, `crop`) |
| `NEKO-6003` | Bad UI resource id; it must be lowercase `namespace:path` without `..` |
| `NEKO-6004` | No file found for this UI resource id |
| `NEKO-6005` | Reading or uploading a UI resource failed, including a file over the size limit |
| `NEKO-6006` | A UI texture or crop size is invalid or too large |
| `NEKO-6007` | A PNG resource could not be decoded |
| `NEKO-6008` | Automatic item resources skipped because an existing file is external or was modified |
| `NEKO-6009` | Automatic item resource generation failed; a failure while publishing attempts a rollback |
| `NEKO-6010` | The item resource ownership index cannot be read or validated; nothing is generated and the index is not replaced |
| `NEKO-6011` | Directory pack client resource reload failed; check the cause and whether vanilla resources recovered |
| `NEKO-6012` | Invalid item settings in UI (resource id, air item, count or tooltip); a failed update keeps the current screen |

<a id="wiki-section-10"></a>
## JSX UI: runtime

| Code | Meaning |
|---|---|
| `NEKO-7001` | UI action rejected: the screen belongs to scripts that were replaced by a reload or closed (stale handle) |
| `NEKO-7002` | Invalid lifecycle transition of a UI root |
| `NEKO-7003` | A JSX host was created outside CLIENT scripts |
| `NEKO-7004` | UI changed directly off the client thread without queueing |
| `NEKO-7005` | Releasing a UI root failed during reload cleanup; cleanup continues |
| `NEKO-7006` | Delayed UI work dropped: its scripts never took effect or were replaced |
| `NEKO-7007` | JSX UI failed while rendering, laying out, handling events or in the host, including clipboard failures |
| `NEKO-7008` | Invalid color or state fields in a control appearance |
| `NEKO-7011` | Invalid slider: range or value not finite, step not positive, or value out of range |
| `NEKO-7012` | Invalid dialog props: missing or blank id, non-string title or non-boolean close flags. May also be reported as `NEKO-7007` |
| `NEKO-7013` | Invalid Workspace settings or IDs, or the controller was changed while publishing. May also be reported as `NEKO-7007` |
| `NEKO-7014` | Invalid mount region bounds, a region-only call on a full-Screen mount, or another root handle/layout root ID |
| `NEKO-7015` | A built-in diagnostic screen is unavailable, closed, or failed to load, draw or handle events. On NeoForge 26.x a failed panel requests the native fallback once |
| `NEKO-8001` | The UI inspector read a layout snapshot in a mismatched format (version skew); the last good frame is kept |

<a id="wiki-section-11"></a>
## NekuJS addon

The codes below come from the NekuJS addon. They appear only when it is installed and you use mods written for KubeJS (for example their recipe schemas). The message names the resource and JSON path that failed; fix that definition. A recipe that is recognized but cannot be supported is declared as `never` by Probe and rejected when called.

| Code | English summary | Details |
|---|---|---|
| `NEKO-5190` | recipe schema source lacks an expected resource | — |
| `NEKO-5191` | imported crushing schema profile differs from the explicitly supported resource shape | — |
| `NEKO-5192` | recipe schema source parsing or validation failed | — |
| `NEKO-5240` | Invalid schema value, shape, resource ID, field role or missing property | — |
| `NEKO-5241` | Duplicate schema resource ID | — |
| `NEKO-5242` | Missing or unsupported parent schema | — |
| `NEKO-5243` | Schema parent cycle | — |
| `NEKO-5244` | Invalid key ordering, duplicate key or incompatible requiredness | — |
| `NEKO-5245` | Unknown schema key | — |
| `NEKO-5246` | Unsupported component or missing semantic codec | — |
| `NEKO-5247` | Invalid component constant or default-writing policy | — |
| `NEKO-5248` | Duplicate constructor key or arity | — |
| `NEKO-5249` | Ambiguous field alias or function/setter conflict | — |
| `NEKO-5250` | Unsupported custom factory, processor, mapping or function behavior | — |
| `NEKO-5251` | Abstract schema dependency is not callable | — |
| `NEKO-5279` | Recipe schema resource read failed or root is not an object | — |
| `NEKO-5306` | String component requires a string value | — |
| `NEKO-5307` | Pattern map requires nonempty single-character non-space keys | — |
| `NEKO-5308` | Crafting category input type is invalid | — |
| `NEKO-5309` | Native shaped preparation failed | — |
| `NEKO-5320` | duplicate programmatic schema | — |
| `NEKO-5321` | duplicate recipe factory | — |
| `NEKO-5322` | duplicate schema key | — |
| `NEKO-5323` | required key follows optional key | — |
| `NEKO-5324` | null default cannot always be written | — |
| `NEKO-5325` | invalid list bounds | — |
| `NEKO-5326` | programmatic list spreading is unsupported | — |
| `NEKO-5327` | override references an undeclared key | — |
| `NEKO-5328` | unknown schema key | — |
| `NEKO-5329` | decoded list size is outside bounds | — |
| `NEKO-5330` | recipe serializer is not registered | — |
| `NEKO-5331` | legacy target is already attached | — |
| `NEKO-5332` | native recipe field decode failed | — |
| `NEKO-5333` | native recipe field encode failed | — |
| `NEKO-5334` | key belongs to another schema | — |
| `NEKO-5335` | legacy target has no native builder session | — |
| `NEKO-5336` | original component rejected a value | — |
| `NEKO-5337` | duplicate field alias | — |
| `NEKO-5338` | duplicate field function | — |
| `NEKO-5339` | factory returned an incompatible fluent target | — |
| `NEKO-5340` | programmatic default encode failed | — |
| `NEKO-5341` | unknown native component mapping | — |
| `NEKO-5342` | programmatic component encode failed | — |
| `NEKO-5343` | component requires an array | — |
| `NEKO-5344` | recipe number is not an exact safe integer or is out of bounds | — |
| `NEKO-5345` | component requires its original typed host value | — |
| `NEKO-5346` | programmatic component decode failed | — |
| `NEKO-5347` | forced programmatic constructor overrides are unsupported | — |
| `NEKO-5360` | expected a recipe number | — |
| `NEKO-5361` | expected a recipe boolean | — |
| `NEKO-5362` | tag belongs to a different registry | — |
| `NEKO-5363` | expected a tag identifier | — |
| `NEKO-5364` | resource key belongs to a different registry | — |
| `NEKO-5365` | expected a resource identifier | — |
| `NEKO-5366` | wrapped list size is outside bounds | — |
| `NEKO-5367` | structured conversion requires the caller active Graal context | — |

NekuJS also uses `NEKO-4190`–`NEKO-4287` and `NEKO-5280`–`NEKO-5305`; those messages are self-explanatory English.

<a id="wiki-section-12"></a>
## Retired codes

Old logs may still contain these codes. They are no longer emitted and will not be reused.

| Code | Status |
|---|---|
| `NEKO-1007` | Old reload failure message, replaced by `NEKO-1008` |
| `NEKO-1009`, `NEKO-1010` | Messages about closing an old runtime/context; no longer emitted |
| `NEKO-1011`, `NEKO-1012` | Old client load logs; unused |
| `NEKO-2001`, `NEKO-2002`, `NEKO-2003`, `NEKO-2005` | Old execution time/statement limits; the feature was removed |
| `NEKO-3001`–`NEKO-3004` | Old script sync size limits; unused |
| `NEKO-4018` | ItemStack persistent data is unavailable; no longer emitted |
| `NEKO-4037` | An old limit on Fabric automatic item resources; lifted |

<a id="wiki-section-13"></a>
## Messages without a code

Routine progress messages have an English summary but no code.

| English summary | Meaning |
|---|---|
| no scripts to load | No scripts of this type were found |
| registering event groups | Event groups are being registered for a script type |
| reloading one script file | A single-file reload started |
| reloading scripts | A reload started |
| running test scripts | `/nekojs test` started |
| script file reload finished | A single-file reload finished |
| script reload finished | A reload finished |
| scripts discovered | Scripts were found; the count is in the message |
| startup file reload falls back to full reload | Startup scripts cannot reload one file, so all startup scripts reload |
| test scripts finished | `/nekojs test` finished |
| workspace entry point created | A default example script was created in an empty script folder |

<!-- wiki-nav -->

---

[Previous: Commands](commands_us) · [Contents](Home) · [Next: Versioned script packs](script-packs_us)
