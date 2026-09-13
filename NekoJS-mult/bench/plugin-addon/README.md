# bench/plugin-addon — ticket 08 真实外部 addon 端到端证据

证明 Java 插件能通过 **真实 loader discovery**（不是内部实例化）和 fat jar 依赖进入 Plugin
Runtime，沿既有 Point/Contributor/Hook 贡献，bootstrap 后由脚本 binding / node module 消费；
普通 reload 不重新 bootstrap/freeze 插件，generation session object 失效。

## 组成

| 路径 | 作用 |
|---|---|
| `fixture/` | test-only 外部 addon 源（`com.example.nekojs08`），只依赖平台 fat jar + slf4j；含两个 AC6 控制类 |
| `build-addon.ps1` | 用 javac+jar 把 fixture 打成两个 loader 形态的独立 jar（**不新增 Gradle 项目**） |
| `fixtures/nekojs/` | 消费脚本（STARTUP 读 binding + 过滤断言，SERVER 读 binding + node module） |
| `run-addon-smoke.ps1` | 干净 run/mods → 多轮启动 → `nekojs reload server` → 计数 → 轮间一致性 → 判定，输出到 `out/` |
| `verify-jars.py` | 校验五个生产 jar 都不含 addon 的类/资源/metadata（AC1） |
| `MINIMAL-ADDON.md` | 最小可运行外部 addon 示例 + 插件作者迁移材料（AC7） |
| `DEAD-CODE-AUDIT.md` | AC14 的三个旁路目标逐条取证（删了哪个、收窄了哪个、为什么保留哪个） |

## 复现

```powershell
# 1) 先产出平台 fat jar（addon 的编译依赖）
.\gradlew.bat :26.1.2:jar :26.1.2-fabric:jar

# 2) 打 addon 制品
powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/build-addon.ps1

# 3) 真实 loader discovery + reload 存活 + 重复一致（默认 2 轮）
powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/run-addon-smoke.ps1 -Node 26.1.2
powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/run-addon-smoke.ps1 -Node 26.1.2-fabric

# 3b) 显式指定轮数（多轮 = 更强的重复性证据）
powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/run-addon-smoke.ps1 -Node 26.1.2 -Rounds 3

# 4) 生产 jar 打包隔离
python bench/plugin-addon/verify-jars.py --json <out.json>
```

## 覆盖形态（诚实记录）

- **fat jar**：已覆盖 —— addon 是独立 jar，放进 `run/mods`（NeoForge）/ `run-server/mods`（Fabric）
  后由 loader 从 mods 目录发现。这是真实目标发布形态。
- **classpath**：**未覆盖**。这里**没有**专门开关来「声明」这件事（早期版本有一个只打印一行就
  退出的 `-Skipped` 空开关，因零调用者已删除——空开关本身不是证据）。要覆盖该形态，需手动把
  fixture 的 classes 目录加进 dev run 的额外 classpath，再由 loader 从 classpath 发现。

## 重复性（AC2）

`run-addon-smoke.ps1` 默认 `-Rounds 2`。每轮都从**干净 run/mods** 开始：清 `run/nekojs`、清
`run/logs/nekojs`、删掉上次装入的 `nekojs08addon-*.jar`，重新铺 fixture 与 jar，然后起一个新的
服务器进程。轮次跑完后逐字段比较，结果写 `consistency.json`；任一轮有失败 check 或轮间不一致
都会让整次运行抛错。

`env.txt` 记录采样时的真实 `git head`、工作树是否 dirty、轮数、run dir 与 addon jar 的 SHA-256，
这样归档的证据能自证「跑的是哪份代码、哪份制品」。

## 判定口径

`out/<UTC stamp>-<node>/round-N-checks.json` 的每一项都是 grep 计数，不是人工声明：

| check | 含义 |
|---|---|
| `server_started` | 服务器到达 `Done (...)` |
| `addon_instances_are_deduped` | NekoJS 只发现一个实例（`instance=1`），且总构造次数与 loader 形态吻合 |
| `addon_bootstrap_once` | `binding-registered` 计数稳定且 `initStartup` 只发生一次 —— 插件只在进程启动时 bootstrap/freeze 一次 |
| `binding_consumed_by_script` | STARTUP 与 SERVER 脚本都成功读到了冻结 binding 产物 |
| `node_module_consumed_by_script` | 脚本 `require('<modid>:<name>')` 拿到插件注册的 node module |
| `binding_alive_after_reload` / `module_alive_after_reload` | reload 后两者仍可用（进程级产物身份保持） |
| `addon_required_mods_filter_holds` / `addon_client_only_filter_holds` | 两个控制类被过滤（脚本侧确认绑定缺席 + LEAKED 日志为 0） |
| `no_script_errors` | per-type 脚本日志无 `[ERROR]` |
| `clean_exit` | 经 RCON 正常停服 |

Loader 差异（已实测，不掩盖）：NeoForge 的 FML 注解扫描只实例化 addon 类一次；Fabric loader 在
pre-launch 探测时会额外构造一个一次性入口实例（脚本把 `instance=1` 归给 NekoJS 真正发现的那个），
所以 Fabric 的 `discovered` 总数是 2，NeoForge 是 1。两个数字都在 `round-N-counts.json` 里留档，
并由 `addon_instances_are_deduped` 同时断言。

## 输出与入库约定

`out/` 是每次会话的原始采样（含 server 日志），**不入库**（见 `.gitignore`）。
证据以 curated 形式归档到 `docs/architecture-refactor/baseline/2026-09-13-plugin-addon/evidence/`。
