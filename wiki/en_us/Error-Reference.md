<!--
  This page is not a translation of an existing wiki page. It is a new
  reference for the messages on this branch, ported from PR 58.
  See docs/translation-guide.md for conventions.
-->

> **English** · no Chinese counterpart yet
>
> | | |
> |---|---|
> | Page type | New reference, not a translation |
> | Last updated | 2026-09-21 |
> | Source | Port of [#58](https://github.com/NekoJS-Dev/NekoJS/pull/58), option 1 |

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

## Sandbox and resource limits

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-2001` | script context hit resource limits | 脚本环境触发 ResourceLimits |
| `NEKO-2002` | script statement limit exceeded | 脚本语句累计数达到 scriptStatementLimit |
| `NEKO-2003` | runaway script loop detected | 脚本同步执行持续超过 scriptRunawayTimeoutSeconds |
| `NEKO-2004` | script output line limit reached | 脚本输出行数超过行上限 |
| `NEKO-2005` | script evaluation timed out | 脚本求值超时 |

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

## Recipes and data

| Code | English summary | Chinese message |
|---|---|---|
| `NEKO-5001` | could not convert value to JSON | 无法转换为 JSON |
| `NEKO-5002` | JSON must be an object | JSON 必须是对象 |
| `NEKO-5003` | unknown recipe filter key | RecipeFilterAdapter: unknown key |

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
