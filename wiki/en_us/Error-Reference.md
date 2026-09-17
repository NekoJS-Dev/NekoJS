<!--
  This page is not a translation of an existing wiki page. It is a new
  reference, generated from the message sites in the source.
  See docs/translation-guide.md for conventions.
-->

> **English** · no Chinese counterpart yet
>
> | | |
> |---|---|
> | Page type | New reference, not a translation |
> | Last updated | 2026-09-16 |

# Error and log message reference

NekoJS writes its log and exception messages in Chinese. Messages that report a
problem now carry a stable code and a short English summary, so a log line can
be looked up here without copying Chinese text:

```
[NEKO-2002] 脚本语句累计数达到 scriptStatementLimit（50000000），关闭对应脚本环境；… — script statement limit exceeded
```

The code never changes, even if the wording does. Search this page for the code.


## Script loading and reload

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-1001` | failed to scan script directory | 扫描脚本目录失败: {} |
| `NEKO-1002` | failed to create workspace directory | 无法初始化环境目录 [{}]: {} |
| `NEKO-1003` | script execution failed | 脚本执行失败: {}\\n{} |
| `NEKO-1004` | problem in after dependency order | {} 脚本 after 依赖排序存在问题：{} |
| `NEKO-1005` | script unreadable, skipped | 无法读取脚本 {}，已跳过：{} |
| `NEKO-1006` | startup reload is not transactional | {} 脚本重载为非事务式语义（STARTUP 涉及物品/方块/实体等不可逆注册，无法安全回滚）；若重载期间脚本出错，已注册内容不会回退。 |
| `NEKO-1007` | candidate context creation failed | {} 候选环境创建失败，保留旧 Context（listener/binding 已清，需再次 reload 恢复） |
| `NEKO-1008` | transactional script reload failed | {} 脚本事务重载失败，已保留旧 Context；listener/binding 状态需再次 reload 恢复 |
| `NEKO-1009` | error closing old Node runtime | 关闭旧 Node runtime 时发生异常 |
| `NEKO-1010` | error closing old context | 关闭旧上下文时发生异常 |
| `NEKO-1011` | client script loading failed | [client] CLIENT 脚本加载失败 |
| `NEKO-1012` | initial resource refresh failed | [client] 初始资源刷新失败 |

## Sandbox and resource limits

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-2001` | script context hit resource limits | 脚本环境 {} 触发 ResourceLimits（失控看门狗 {}s / 语句上限 {}），Graal 已关闭该 Context；当前求值被中止，下一次取用时会自动重建（/nek… |
| `NEKO-2002` | script statement limit exceeded | 脚本语句累计数达到 scriptStatementLimit（{}），关闭对应脚本环境；当前求值被中止，下一次取用时会自动重建 Context（/nekojs reload 亦可手… |
| `NEKO-2003` | runaway script loop detected | 脚本同步执行持续超过 scriptRunawayTimeoutSeconds（{}s）未让出，判定为失控循环，关闭对应脚本环境；当前求值被中止，下一次取用时会自动重建 Contex… |
| `NEKO-2004` | script output line limit reached | 脚本输出行数超过 {} 行上限，后续输出将被丢弃（防止日志无限增长） |
| `NEKO-2005` | script evaluation timed out | 脚本求值超时（超过  秒，可在 nekojs/config/engine.toml 中调整 scriptEvaluationTimeoutSeconds）：入口脚本的顶层 awai… |

## Script sync limits

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-3001` | script file too large | 脚本文件过大:  ( bytes, 最大 ) |
| `NEKO-3002` | too many script files | 脚本数量超过限制:  (最大 ) |
| `NEKO-3003` | total script size too large | 脚本总大小超过限制:  bytes (最大 ) |
| `NEKO-3004` | script content too large | 脚本内容超过限制 |

## Registration and bindings

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-4001` | duplicate binding name ignored | 同名绑定 '{}' 已注册，后者被忽略（首胜），被拒绝绑定的 valueType: {} |
| `NEKO-4002` | goal target must be a LivingEntity | 目标类型必须是 LivingEntity:  |
| `NEKO-4003` | cannot infer target class from EntityType | 无法从 EntityType 推断目标类（NeoForge 不暴露实体类），请传实体 id 字符串或 Java 类:  |
| `NEKO-4004` | unknown target entity | 未知目标实体（无内置映射，可用 Java.type(...) 传类）:  |
| `NEKO-4005` | could not resolve goal target | 无法解析目标:  |
| `NEKO-4006` | unknown block entity type | 未知方块实体类型:  |
| `NEKO-4007` | unknown capability | 未知 capability（支持 item/energy/fluid）:  |

## Recipes and data

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-5001` | could not convert value to JSON | 无法转换为 JSON:  |
| `NEKO-5002` | JSON must be an object | JSON 必须是对象，得到:  |
| `NEKO-5003` | unknown recipe filter key | RecipeFilterAdapter: unknown key '...'（未知键），仅接受文档中列出的过滤键，防止条件被静默丢弃 (silent data loss) |

## Messages without a code

Routine progress messages carry an English summary but no code, since
there is nothing to look up when they appear.

| English summary | Chinese message |
|---|---|
| loading client scripts | [client] 正在加载 CLIENT 脚本... |
| no scripts to load | 没有需要加载的 {} 脚本。 |
| registering event groups | 正在为 {} 注册 {} 个事件组... |
| reloading one script file | 正在重载 {} 脚本文件 {}，受影响入口 {} 个... |
| reloading scripts | 正在重载 {} 脚本... |
| running test scripts | 正在运行 TEST 脚本... |
| script file reload finished | {} 脚本文件 {} 重载完毕。 |
| script reload finished | {} 脚本重载完毕。 |
| scripts discovered | 发现了 {} 个 {} 脚本。 |
| startup file reload falls back to full reload | 正在重载 STARTUP 脚本文件 {}：STARTUP 注册不可逆，退化为完整 STARTUP 重载。 |
| test scripts finished | TEST 脚本运行完毕。 |
| workspace entry point created | 已初始化环境入口: {} |

## Coverage

Generated from the `logger` and exception call sites in `common/` and
`platforms/`. Messages built from variables, and the labels used in the in-game
error panel, are not listed here. If you meet a Chinese message that is not
here, please open an issue with the text and it will be added.
