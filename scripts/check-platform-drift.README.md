# check-platform-drift

平台副本漂移检测脚本（dashboard 模式）。在 Git Bash 中从仓库根目录运行：

```bash
scripts/check-platform-drift
# 或
bash scripts/check-platform-drift
```

## 用途

多个平台模块（`neoforge-1.21.1`、`neoforge-26-shared`、`neoforge-26.1`、
`neoforge-26.2`、`cleanroom-1.12.2`）之间存在按相对路径成对的"共享副本"文件。
副本之间容易产生无意的漂移。本脚本对每一对目录做 **归一化后 diff**，输出统计，
用于人工巡检或后续 CI 门禁。

比较的目录对：

| 对 | 说明 |
| --- | --- |
| `neoforge-1.21.1/src/main/java` ↔ `neoforge-26-shared/src/main/java` | 主对：1.21.1 与 26.x 的共享副本 |
| `neoforge-26.1/src/main/java` ↔ `neoforge-26.2/src/main/java` | 26.x 两个版本专属层 |
| `cleanroom-1.12.2/src/main/java` ↔ `neoforge-26-shared/src/main/java` | 仅参考信息（cleanroom 是独立实现，同路径文件多为同源重写） |

## 归一化规则

先去掉 `\r`（Windows CRLF），再把已知的"版本机械改名"替换成统一记号，两侧应用
同一套 sed 规则（双向收敛），以降低纯改名带来的噪音：

- `ResourceLocation` → `Identifier`（含 import 行）
- `.location()` → `.identifier()`
- `.hasPermission(2)` / `.hasPermissions(2)` / `Commands.LEVEL_GAMEMASTERS.check` → 权限记号（惯用法形状不同，只能部分归一化，残留属于真实差异）
- `.displayClientMessage(` → `.sendSystemMessage(`
- `.listRegistries()` → `.listRegistryKeys()`
- 26.1 → 26.2 的机械改名：`EntityType.` → `EntityTypes.`、`getApiDescription()` → `getBackendDescription()`、`.getToastManager()` → `.gui.toastManager()`、`.screen instanceof` → `.gui.screen() instanceof`、`.setScreen(` → `.gui.setScreen(`

规则刻意保持 **小而保守**：目标是减少噪音，不是把真实语义差异抹平。新增规则前
先确认两侧样本确实是纯机械改名。

## 输出

- 每对：参与比较的文件数、identical-after-normalize 数、drifted 数、单侧独有文件数；
- drifted 文件列表（按归一化 diff 行数降序，默认前 15 条）。

## 退出码

**永远为 0**（dashboard 模式）。是否作为 CI 门禁、阈值如何，由上层另行决定。

## 基线变更记录

- **2026-08-25（W5 步骤 3）**：28 个「仅机械改名」文件（13 个 network 包、7 个
  RegistryEventJS、builder 等，~1,600 行 ×2）不再手维护 1.21.1 副本——
  `platforms/neoforge-1.21.1` 的 `generateLegacySharedSources` 任务从 26-shared master
  按 `scripts/gen-1211-rules.properties`（本脚本主对归一化规则的反向）生成到
  build/generated。生成产物已验证与删除前的手维护副本逐字节一致。
  **drifted 计数不变（113）**——这批文件原本就在 identical-after-normalize 桶里；
  变化是 compared 141→113、identical 28→0、only-in-B +28（master 单侧存在）。
  master 用了 1.21.1 没有的 API 时由 1.21.1 编译失败兜底（不是静默漂移）。
- **2026-08-25（W5 步骤 1+2）**：`111/0/73 → 113/0/69`。
  - 9 个字节相同文件上提 neoforge-shared（不影响 drifted）；
  - 26.1/26.2 的 4 个版本专属文件收进 26-shared + per-version compat
    （`platform/compat/McVersionCompat` / `McClientCompat`，ServiceLoader 提供）——
    26.1↔26.2 比较对归零（was 4 文件 / 1,127 行双份编译）；
  - cleanroom 对 73→69（4 个搬走文件退出比较）；
  - **主对 111→113 是度量口径扩大而非新增分歧**：LevelExtension 与 NekoJSNetwork
    从 26.1/26.2 层搬进 26-shared 后，与 1.21.1 独立副本开始配对——这两对的分歧
    一直存在（1.21.1 时间/天气与网络注册 API 结构不同），此前不在任何比较对里。
    现在被追踪，后续收敛可棘轮下降。

依赖：bash + coreutils（sed / diff / find / comm / sort），无其他外部依赖，
可在 Windows Git Bash 下直接运行。
