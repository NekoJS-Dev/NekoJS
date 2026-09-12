# 场景 7：切换世界（world switch，2026-09-12 补跑）

工单 03 验收 2 的"切换世界"场景。专用服务器（`26.1.2` dev runServer）上以**换存档目录**方式构造：
world-A（含 36 文件、无 WORLD pack——WORLD pack 激活缺陷见 REPORT §3-1，另行单测）→ stop →
把 `world/` 改名为 `world-a-archived` → 以全新 world-B 启动 → 验证 → stop → 回验 world-A。

执行序（run-datafix.ps1 step）：

```text
deploy → start(-Session wA) → wait-done → snapshot(-Name s7-before-switch) → stop
mv versions/26.1.2/run/world → world-a-archived
start(-Session wB) → wait-done → rcon 'nekojs packs' → snapshot(-Name s7-after-switch)
compare-snapshot(s7-before-switch vs s7-after-switch) → stop
```

## 观察结果

1. **受保护数据跨世界切换原样**：`compare-snapshot` = `DIFF-SUMMARY changed=0 added=0 removed=0 same=20`
   ——config（engine.toml/probe.toml/trusted-servers.json）、GLOBAL pack、脚本树、jsconfig 等全部逐字节不变。
2. **GLOBAL pack 在新世界照常生效**：world-B 启动日志 `DATAFIX-PACK demo-pack startup/server script load v1`；
   RCON `nekojs packs` 恰好 1 条：`[x] GLOBAL:demo-pack v1.0.0`（`rcon-20260912-*.log`）。
3. **WORLD scope 不跨世界泄漏**：world-A 无 WORLD pack 部署（见上），world-B 亦无 `nekojs_packs`，
   pack 清单无 WORLD 条目；WORLD 目录绑定行为（`ScriptPackRegistry.java:27,89-94`）与盘点一致。
4. **归档的 world-A 未被触碰**：36 个文件 SHA-256 逐字节比对 world-B 会话前后完全一致
   （`snapshots/s7-worldA-tree-before.json` 为切换前基线；比对在 world-B stop 后执行）。

## 结论

- 验收 2 的"切换世界"在此口径下 **pass**：config、pack（GLOBAL）、trust、脚本树原样可读，
  world 作用域数据不泄漏、不丢失。
- 口径限制：本场景验证的是"换存档目录"这一专用服务器可达的切换形态；**单人客户端的退出重进/
  多维世界切换（Level 事件路径）仍属客户端会话范畴**，沿列入 REPORT §5（owner：维护者/mcp 会话）。
- 附带观察：world-A 首次启动即触发 REPORT §3-1 的 WORLD pack 缺陷（该轮未部署 WORLD pack，
  缺陷复现证据在 `log-excerpts-session2-worldpack-defect.txt`，本场景不依赖它）。

原始会话日志：`full-logs/wA-*.log.gz`、`wB-*.log.gz`（wA 为 02 模式修正后首启，含 daemon 冷启动段）。
