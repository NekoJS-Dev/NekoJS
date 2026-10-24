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
- **检查**：每节点 × {server, startup} 两个入口，tsconfig `extends` 生成的
  `nekojs/<side>_scripts/jsconfig.json`（strict=true 由其携带），仅覆盖
  `skipLibCheck=false`、`noEmit=true`；tsc 用仓库 `node_modules` 的 TypeScript，
  版本写入 summary。退出码 2 = 有诊断（预期基线状态），不是驱动失败。

## 产物

| 文件 | 内容 |
|---|---|
| `preparation-manifest.json` | revision、平台来源、每节点 jar 名+SHA256、端口、rcon 密码 |
| `run-summary.json` | 每节点 pid、probe 响应/文件数、停服 exit、session.lock 释放 |
| `ide-check-summary.json` | 十入口退出码、按 TSxxxx 分组的诊断计数、tsc 版本、命令行 |
| `ide/<node>-<side>-tsc.txt` | 每入口完整诊断原文（根因分类的原始输入） |
| `ide/<node>-<side>-tsconfig.json` | 实际执行的 tsconfig（绝对路径绑定该次安装） |

## 与既有计数的边界

v3（3ccea55a）的 1609 = 1629 首轮实测 − 20 条验收脚本自生诊断，且 v3 的
`server_scripts/` 铺有验收脚本。本驱动是**无脚本的默认安装**，基线数值必须重采后
单独报告，不与 1609 / 1629 / 1621（旧原型）混用；Python stub 的 1609 份是另一指标。
