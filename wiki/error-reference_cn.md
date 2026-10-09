<!-- wiki-page: error-reference; locale: cn -->

> **中文** · [English](error-reference_us)

<a id="wiki-section-1"></a>
# 错误与日志消息参考

报告问题的消息带有稳定的错误码和简短英文摘要。
除了错误码前缀和英文摘要，中文文本保持不变。
错误码是查询键，不会随着措辞变化而改变。

```
[NEKO-2002] 脚本语句累计数达到 scriptStatementLimit（50000000），… — script statement limit exceeded
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

<a id="wiki-section-3"></a>
## 沙盒与资源限制

| 错误码 | 英文摘要 | 中文消息 |
|---|---|---|
| `NEKO-2001` | script context hit resource limits | 脚本环境触发 ResourceLimits |
| `NEKO-2002` | script statement limit exceeded | 脚本语句累计数达到 scriptStatementLimit |
| `NEKO-2003` | runaway script loop detected | 脚本同步执行持续超过 scriptRunawayTimeoutSeconds |
| `NEKO-2004` | script output line limit reached | 脚本输出行数超过行上限 |
| `NEKO-2005` | script evaluation timed out | 脚本求值超时 |

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
| `NEKO-4031` | Authored class declaration registration rejected | 手写类声明注册被拒绝 |
| `NEKO-4032` | Conflicting authored class declarations rejected at the same priority | 同优先级的手写类声明冲突，注册被拒绝 |
| `NEKO-4033` | Authored class declaration dependency is unavailable, excluded, or hidden | 手写类声明依赖不可用、被排除或被隐藏 |

<a id="wiki-section-6"></a>
## 配方与数据

| 错误码 | 英文摘要 | 中文消息 |
|---|---|---|
| `NEKO-5001` | could not convert value to JSON | 无法转换为 JSON |
| `NEKO-5002` | JSON must be an object | JSON 必须是对象 |
| `NEKO-5003` | unknown recipe filter key | RecipeFilterAdapter: unknown key |

<a id="wiki-section-7"></a>
## JSX UI 视觉与资源契约

| 错误码 | 含义 |
|---|---|
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
| NEKO-7007 | JSX UI 阶段失败（render/layout/event/host）已记录到诊断链路。 |

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

[上一篇: 命令](commands_cn) · [目录](Home) · [下一篇: 配方系统](recipe-system_cn)
