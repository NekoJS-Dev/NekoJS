# 严格 TS 基线驱动（票 37 / 2026-10-25 交接）

固定口径的五节点隔离安装 → 默认声明生成 → 十入口严格 tsc 检查的可复用驱动。
入口 `run.py`，三个子命令按顺序执行；产物全部落在 `--target` 目录，供证据归档。

```bash
# 0) 前置：五节点 jar 已构建（versions/<node>/build/libs/），node_modules 已安装
./gradlew :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm install

# 1) 准备隔离安装（复制加载器平台 + 铺当前 jar + 写 eula/properties/manifest）
python bench/tsbaseline/run.py prepare --target build/tsbaseline-<tag> --revision <git-sha>

# 2) 逐节点启动已安装服务端，RCON 触发 nekojs probe typescript，干净停服
python bench/tsbaseline/run.py run --target build/tsbaseline-<tag>

# 3) 十入口严格检查（strict 来自生成的 jsconfig，覆盖 skipLibCheck=false / noEmit）
python bench/tsbaseline/run.py check --target build/tsbaseline-<tag>
```

## 固定口径

- **安装隔离**：`prepare` 只从 `--platform-source`（默认 v3 安装目录）复制加载器平台
  （NeoForge `libraries/`；Fabric 另加 `fabric-server-launch.jar`）。平台由加载器版本钉死，
  与仓库源码无关；graal/fabric-api 属 curse.maven 钉版外部制品（8762962/8762963），同样
  从 platform-source 复制并记录 SHA256。其余一切（config、`nekojs/`、`.neko_probe/`、world）
  由**当前** jar 首次启动全新生成，验收脚本噪声为零。
- **端口**：`--base-port`（默认 34500）起，每节点占 10：server=base+i*10，rcon=+1；
  rcon 密码由 revision+node 派生并记录在 `preparation-manifest.json`。
- **JDK**：1.21.1 用 jdk-21.0.10，四个 26.x 节点用 jdk-25.0.2（沿用 v3 实测记录）；
  路径硬编码在 `run.py` 的 NODES 表，换机改表。
- **jar 选择**：1.21.1 取 `nekojs-neoforge-1.21.1-*-server.jar`（专用服务端变体），
  其余节点取对应 `nekojs-{neoforge|fabric}-<ver>-*.jar` 最新构建产物。
- **生成**：RCON `nekojs probe typescript`，等待 `Probe generated: N files` 完成行；
  停服走 RCON `stop`，等 exit 0 与 `world/session.lock` 释放。
- **进程回收**：启动即写 `<node>/server-process.json`（pid、微秒精度创建时刻、完整命令行、
  启动器特征）。`run` 的成功、探针失败与驱动异常共用同一条回收路径：先 RCON `stop`，有界等待
  （stop 未送达时 15 秒，送达后 `--stop-timeout`），超时才 kill 自己启动的子进程；失败也先落盘
  `run-summary.json`（含 `failure` 原因）再以 `SystemExit ... from 原异常` 抛出。宿主机上绝不
  按通用进程名匹配。
- **孤立进程清理**：`kill-servers.ps1 -Target <安装根>` 只处理该目录下 `server-process.json`
  记录过的进程，且需精确身份验证（PID 存活、**完整命令行全等**、**微秒创建时刻全等**——同
  PID 复用必产生新创建时刻，窗口关闭；未采集到身份的记录一律 SKIP）。正常停服由 `run`
  负责；脚本只是驱动已死时的兜底，不再接受无参全量匹配。
- **检查**：每节点 × {server, startup} 两个入口，tsconfig `extends` 生成的
  `nekojs/<side>_scripts/jsconfig.json`（strict=true 由其携带），仅覆盖
  `skipLibCheck=false`、`noEmit=true`；tsc 用仓库 `node_modules` 的 TypeScript，
  版本写入 summary。运行分三类：`clean`（exit 0 零诊断）、`diagnostics`
  （exit 1/2 且解析出 TSxxxx——基线状态）、`invalid`（非零退出且零条诊断，如
  TypeScript 损坏 / 找不到模块 / 配置缺失）。`invalid` 显式失败并停止汇总，
  绝不折算成"零诊断"；三者与每入口计数一并写入 `ide-check-summary.json`。

## 产物

| 文件 | 内容 |
|---|---|
| `preparation-manifest.json` | revision、平台来源、每节点 jar 名+SHA256、端口、rcon 密码 |
| `<node>/server-process.json` | 该节点服务端 pid、微秒创建时刻、完整命令行（kill 脚本的精确身份凭据） |
| `run-summary.json` | 每节点 pid、probe 响应/文件数、停服路径（stopSent/exit/killed）、失败原因（failure）、session.lock 释放 |
| `ide-check-summary.json` | 十入口退出码、runState（clean/diagnostics/invalid）、按 TSxxxx 分组的诊断计数、tsc 版本、命令行 |
| `ide/<node>-<side>-tsc.txt` | 每入口完整诊断原文（根因分类的原始输入） |
| `ide/<node>-<side>-tsconfig.json` | 实际执行的 tsconfig（绝对路径绑定该次安装） |

## 与既有计数的边界

v3（3ccea55a）的 1609 = 1629 首轮实测 − 20 条验收脚本自生诊断，且 v3 的
`server_scripts/` 铺有验收脚本。本驱动是**无脚本的默认安装**，基线数值必须重采后
单独报告，不与 1609 / 1629 / 1621（旧原型）混用；Python stub 的 1609 份是另一指标。
