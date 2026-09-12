# Shakedown / 作废样本记录（工单 02）

本目录保存采样过程中**未进入正式统计**的运行，按工单要求保留、不删除，并逐条记录作废原因。
正式样本在 `../formal/`；统计与结论见 `../REPORT.md`。

harness 的缺陷在这批运行里被发现并修复（每条的修复都进了 `bench/perf/` 的最终版本）。
这些运行对应的 session stdout 全文与正式样本一样已 gzip 入库（各自的 `full-logs/`），
另有按关键行过滤的摘要 `*-excerpt.log` 便于快速复核；留档规模见 REPORT.md §7。
（第一遍正式采样被 harness 修订取代，见 `../superseded/README.md`，不在本目录。）

| 运行目录 | 模式 | 结果 | 作废原因 | 修复 |
|---|---|---|---|---|
| `20260912T080749Z-startup` | startup | 1 成功 + 6 失败 | 停服后未等 `world/session.lock` 释放，第 2–7 个会话在 `DirectoryLock.create` 竞争失败（`FatalStartupException`） | 加 `Wait-WorldLockRelease`（`sample.ps1`） |
| `20260912T082216Z-startup` | startup | 7 个会话全部中止 | 纯 PowerShell Source-RCON 封帧被 vanilla RCON 线程在 auth 阶段重置连接 → 只能 taskkill，服务器 JVM 成孤儿并占锁 | 改走 `bench/perf/rcon.py`（python 客户端实测通过） |
| `20260912T083334Z-startup` | startup | 7 样本，数据集未加载 | `Copy-Item -Recurse` 把 fixtures 嵌套成 `run/nekojs/nekojs/...`，发现数只有 mod 脚手架 1 个 → 量的不是声明的负载 | deploy 改先建空目录并复制内容 |
| `20260912T083635Z-bench` | bench | `tick_rows=0`、CSV 全 missing | 同上嵌套数据集；且空服务器 60s 自动暂停 tick | 同上 + `pause-when-empty-seconds=-1` |
| `20260912T085524Z`–`20260912T090315Z` (4×bench、2×startup) | bench/startup | 空目录 | 分离启动进程与旧句柄互踩 / 半删除目录复制失败（`CopyContainerItemToLeafError`：fixtures 加入 `src/` 嵌套目录后 `Copy-Item` 通配+`-Recurse` 的 PS 5.1 缺陷） | deploy 改 `robocopy /E` + 删净等待 |
| `20260912T091125Z-startup` | startup | 7 样本 | 数据集已加载，但 s06/s08 fixture 报错（`b.title(...)`/`b.icon(...)` 当方法调用、`.slice` 触发 binding-preflight、`add` 传两参） | 修 fixture（见 REPORT.md §5） |
| `20260912T091503Z-bench` | bench | tick 1199 行；adapter missing | `adapter-bench.js` 在脚本 load 阶段执行，早于 registry 组件绑定 → `Components not bound yet` | 触发点改 `ServerEvents.started` |
| `20260912T092419Z-bench` | bench | 3 轮完整 | 数据集仍含 s06 builder 错误（reload 报 `1 error(s) remain`） | 修 s06（字段赋值 + `add` 单参） |
| `20260912T093012Z-reload` | reload | 5 样本但 `marker_ms=null`，会话 16m31s | 完成 marker 用了中文/错误模式串；PS 5.1 无 BOM 时按 ANSI 解码 `.ps1`，中文注释吞换行、marker 恒不匹配 | 改 ASCII 代理 marker + UTF-8 显式读日志 + 加 BOM |
| `20260912T095720Z` / `T102000Z` / `T102133Z-reload` | reload | 中止/`reload` resp 计数含错 | 进程被外部终止 / 数据集未修完 | 见上 |
| `20260912T102351Z-startup` | startup | 1 样本（零错误核验） | 单样本核验轮，不构成 5 样本口径 | 无需修复 |
| `20260912T103458Z-probe` | probe | 无样本 | probe 完成 marker 模式串错误（实际格式 `Probe [typescript] generated N files in Mms`） | 更正模式串 + 每样本清 `.neko_probe` |

作废运行的 `samples.jsonl` / CSV / 摘要日志均按原样保留，不做修饰。
