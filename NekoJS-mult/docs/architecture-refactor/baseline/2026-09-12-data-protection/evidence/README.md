# 工单 03 回读证据（2026-09-12，worktree `D:/mcmodDemo/NekoJS-datafix` @ bench/datafix 首个提交）

采集环境：隔离 worktree `../NekoJS-datafix`（detached @ `39fd5aa9` 后续小修见 git log），节点 `26.1.2`，
run 目录 `versions/26.1.2/run`，RCON 25872 / server 25871。原始未裁剪输出（各 4.3 MB 的
`s1/s2-stdout.log`）留在 worktree `bench/datafix/out/`，不入库；本目录保存 hash 快照、RCON 回执与日志摘录。

- `snapshots/`：受保护数据集（config×3、GLOBAL pack 全树、脚本树、world pack 全树、README、根 jsconfig）
  的 `SHA256|size|mtime|relpath` 快照。对比结论见 `../REPORT.md` 场景矩阵。
- `rcon-*.log`：每次 RCON 调用的回执（原为 PS 5.1 Tee-Object 的 UTF-16LE，已无损转 UTF-8 便于审阅）。
- `rcon-summon-*.log`：`forceload add 0 0` + `summon` 盔甲架（pdata 写入触发）回执。
- `rcon-stop-*.log`：RCON stop 回执。
- `log-excerpts-session1.txt`：会话 1——启动 fixture 生效、场景 1 普通 reload、场景 2 失败 reload、恢复。
- `log-excerpts-session2-worldpack-defect.txt`：会话 2——重启、WORLD pack 激活触发的事务 reload 失败、恢复、监听器存活。
- `log-excerpts-worldpack-iae-stack.txt`：`DefaultErrorTracker.record` 的 `IllegalArgumentException: 'other'
  is different type of Path` 完整调用栈（激活期 + 手动 reload 两次）。
- `data-get-pdata-console-captures.txt`：RCON `data get entity` 读取 `NekoJSPersistentData` 的控制台捕获
  （普通 reload 后 / 失败 reload 后 / 停服重启后，值均不变）。
- `find-*.log`：启动期 marker 全量 grep（含自动物化的示例脚本）。

时间线（关键 marker）：
`19:47:38` pdata 写入 → `19:48:43` 普通 reload（0 文件变化）→ `19:49:03` 失败 reload（0 文件变化、旧环境存活）
→ `19:51:22` stop → `19:51:49` 重启 + WORLD pack 缺陷暴露 → `19:54:09` 移除 world pack 后恢复 reload
→ `19:55:11` 终态 stop（22/22 文件不变）。
