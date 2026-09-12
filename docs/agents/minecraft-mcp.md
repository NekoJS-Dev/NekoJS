# Minecraft MCP：在真实客户端里验证 NekoJS

`minecraft-mod-mcp` 让 Agent 驱动一个**真实运行的 Minecraft 客户端**：截图、点击、按键、执行斜杠命令、读玩家与世界状态。mixin / access-transformer 的加载路径、GUI 与聊天行为、`/nekojs reload` 的候选态切换这类只有游戏内才成立的证据，单测和 GameTest 补不齐，用这里补。

它**不替代**单测与 GameTest：能落成 JUnit 或 `runGameTestServer` 的验证仍然走那边；只有"必须在真客户端里看见"的才用 MCP。

## 两层，各管一半

| 层 | 在哪 | 谁负责 |
|----|------|--------|
| 桥接器（讲 MCP / stdio） | npm 包 `minecraft-mod-mcp`，已在 PATH 上 | 已装好 |
| 游戏内 mod（起 HTTP 服务） | 客户端 `mods/` 目录 | 桥接器启动时自动下载投放 |
| 装了 mod 的客户端 | 见下方配方 | **需要自己拉起** |

mod 自带的是一个普通 HTTP 服务（`/api/cmd`、`/api/screenshot`、`/api/status`…），**不讲 MCP 协议**。所以别把 `"type":"sse"` / `"url"` 直接指向它；只有 `npx -y minecraft-mod-mcp` 这个 stdio 桥接器讲 MCP。

端口不用猜：桥接器每次调用都扫 **9876→9000**，取第一个 `/api/status` 返回 `type:"minecraft-mod"` 的端口，并从那里读出 `version` / `loader` / `pid`。同时跑多个客户端时只会控制最先找到的那个。

## 关键路径

桥接器把游戏放在**独立的启动器目录**里，不碰 `%APPDATA%\.minecraft` 下的其它东西：

```text
%APPDATA%\.minecraft\                 # mcDir
└── mcp_launcher\                     # launcherDir（config.json 在这）
    ├── modjars\                      # 桥接器缓存的 minecraft-mcp-<ver>-<loader>.jar
    ├── server\                       # 独立服务端
    └── game\                         # gameDir —— 客户端实际的工作目录
        ├── mods\                     # ← 所有 mod 都放这里
        ├── nekojs\                   # ← NekoJS 的脚本目录（首次启动生成）
        └── logs\
```

`game_dir` 可以在 `%APPDATA%\.minecraft\mcp_launcher\config.json` 里改；改了以后下面的路径跟着改。

## 启动一个装了 NekoJS 的客户端

NekoJS 的 jar 里把 `graalmc` 声明成**硬依赖**（`versionRange = "[25.0.1,)"`），缺了 NeoForge 会直接停在缺依赖界面。所以要凑齐三个 mod：桥接器自己的、GraalMC、NekoJS。

**桥接器的那个不用手动放**：`launch` 时它会调用 `ensureModJar()` 把发行版 jar 投到同一个 `mods/` 里。

```powershell
$mc   = "$env:APPDATA\.minecraft"
$mods = "$mc\mcp_launcher\game\mods"
New-Item -ItemType Directory -Force -Path $mods | Out-Null

# 1) GraalMC —— NekoJS 的硬依赖，版本下限 25.1.3.7（见 gradle/libs.versions.toml 的 graal 注释）
#    curse 文件按加载器分构建：8762962 = NeoForge，8762963 = Fabric
$graal = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\curse.maven\graal-1504336" `
  -Recurse -Filter "graal-1504336-8762962.jar" | Select-Object -First 1
Copy-Item $graal.FullName $mods -Force

# 2) NekoJS 本体（构建你要验的那个节点）
.\gradlew.bat :1.21.1:build
Copy-Item versions\1.21.1\build\libs\nekojs-neoforge-1.21.1-*.jar $mods -Force

# 3) 启动（桥接器把自己那份 mod 也投进同一个 mods/，然后拉起客户端）
minecraft-mod-mcp launch 1.21.1 --loader neoforge
```

节点与 jar 的对应关系：

| 节点 | 加载器 | 产物 | 用哪个 GraalMC |
|------|--------|------|----------------|
| `1.21.1` / `26.1.2` / `26.2.0` | NeoForge | `versions/<节点>/build/libs/nekojs-neoforge-*.jar` | `8762962` |
| `26.1.2-fabric` / `26.2.0-fabric` | Fabric | `versions/<节点>/build/libs/nekojs-fabric-*.jar` | `8762963` |

**先建一个离线账号**（只做一次）：`minecraft-mod-mcp auth offline NekoDev`，或让 Agent 调 `create_offline_account`。

## 验证连上了

- MCP 工具：`ping` → 应返回 `pong`；`get_minecraft_status` → `connected: true` 并带 `version` / `loader` / `port`。
- 命令行：`minecraft-mod-mcp status`。

桥接器在 mod 起来之前发现不到东西是正常的——`launch_minecraft` 之后它会一直扫到找到为止。

## 常用动作

| 想做的事 | 工具 |
|----------|------|
| 看游戏现在长什么样 | `screenshot` / `screenshot_to_file` |
| 跑命令（`/nekojs reload`、`/nekojs test`…） | `execute_command` |
| 走 GUI、按键、切物品栏 | `click` / `press_key` / `scroll` / `open_chat` / `paste_text` |
| 读坐标、血量、维度、游戏模式 | `get_player_info` / `debug_fields` |
| 读种子、时间、天气、实体 | `get_world_info` |
| 端到端脚本验证 | 往 `game/nekojs/server_scripts/` 丢 fixture，再 `execute_command("/nekojs reload")`，最后截图或读日志确认 |

GUI 里的按钮可以用 `get_screen_buttons` / `enumerate_widgets` 拿坐标和 ID，比按 `click x y` 猜坐标稳。

## 坑

- **`--mod-jar` 不是"额外加一个 mod"，它替换桥接器的默认 jar。** 传给 `launch` 时，`ensureModJar()` 被整个跳过，`minecraft-mcp` 自己那份 mod 就不会被投放，客户端起来也连不上。要加 NekoJS / GraalMC，**提前放进 `game\mods\`**，`launch` 不要带 `--mod-jar`（那个参数是给"我想用自己构建的桥接器 mod"准备的）。
- **同名 jar 不会覆盖。** 投放逻辑是 `if (!existsSync(dest))`，所以重新构建 NekoJS 后同名 jar 不会刷新——先删掉 `game\mods\` 里的旧文件再启动。
- **先装版本再启动。** 首次用 `minecraft-mod-mcp install <版本> --loader <加载器>` 拉版本与资源（资源有几千个，慢）；或者直接 `launch`，让它自己装。
- **JDK 按版本挑。** 1.21.1 需要 Java 21，26.x 需要 Java 25；机器上都有（`minecraft-mod-mcp java` 可列）。启动器会自己找，找不到再传 `--java <路径>`。
- **GUI 需要显示器。** 无头 Linux 上用 `xvfb-run -a -s "-screen 0 1280x720x24" minecraft-mod-mcp launch …`；纯服务端不需要显示器。
- **别用 `just daemon` / `scripts/mc_vtty.py`。** 那是上游项目内部的旧脚手架，已经被桥接器取代。
