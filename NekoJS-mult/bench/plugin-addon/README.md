# bench/plugin-addon — ticket 08 真实外部 addon 端到端证据

证明 Java 插件能通过 **真实 loader discovery**（不是内部实例化）和 fat jar 依赖进入 Plugin
Runtime，沿既有 Point/Contributor/Hook 贡献，bootstrap 后由脚本 binding / node module 消费；
普通 reload 不重新 bootstrap/freeze 插件，generation session object 失效。

## 组成

| 路径 | 作用 |
|---|---|
| `fixture/` | test-only 外部 addon 源（`com.example.nekojs08`），只依赖平台 fat jar + slf4j |
| `build-addon.ps1` | 用 javac+jar 把 fixture 打成两个 loader 形态的独立 jar（**不新增 Gradle 项目**） |
| `fixtures/nekojs/` | 消费脚本（STARTUP 读 binding，SERVER 读 binding + node module） |
| `run-addon-smoke.ps1` | 部署 → 启动 → `nekojs reload server` → 计数 → 判定，输出到 `out/` |
| `verify-jars.py` | 校验五个生产 jar 都不含 addon 的类/资源/metadata（AC1） |
| `MINIMAL-ADDON.md` | 最小可运行外部 addon 示例 + 插件作者迁移材料（AC7） |
| `DEAD-CODE-AUDIT.md` | AC15 的三个旁路目标逐条取证（删了哪个、为什么保留哪个） |

## 复现

```powershell
# 1) 先产出平台 fat jar（addon 的编译依赖）
.\gradlew.bat :26.1.2:jar :26.1.2-fabric:jar

# 2) 打 addon 制品
powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/build-addon.ps1

# 3) 真实 loader discovery + reload 存活烟测（各节点）
powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/run-addon-smoke.ps1 -Node 26.1.2
powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/run-addon-smoke.ps1 -Node 26.1.2-fabric

# 4) 生产 jar 打包隔离
python bench/plugin-addon/verify-jars.py --json <out.json>
```

## 覆盖形态（诚实记录）

- **fat jar**：已覆盖 —— addon 是独立 jar，放进 `run/mods`（NeoForge）/ `run-server/mods`（Fabric）
  后由 loader 从 mods 目录发现。这是真实目标发布形态。
- **classpath**：**未覆盖**。`build-addon.ps1 -Skipped` 是显式的开关，用于把 classpath 形态明确标记
  为未验证，避免误报。

## 判定口径

`out/<UTC stamp>-<node>/checks.json` 的每一项都是 grep 计数，不是人工声明：

| check | 含义 |
|---|---|
| `server_started` | 服务器到达 `Done (...)` |
| `addon_instances_are_deduped` | NekoJS 只发现一个实例（`instance=1`），且总实例数与 loader 形态吻合 |
| `addon_bootstrap_once` | reload 前后 `binding-registered` 计数不变 —— 插件只在进程启动时 bootstrap/freeze 一次 |
| `addon_init_once` | reload 前后 `initStartup` 计数不变 |
| `binding_consumed_by_script` | STARTUP 与 SERVER 脚本都成功读到了冻结 binding 产物 |
| `node_module_consumed_by_script` | 脚本 `require('<modid>:<name>')` 拿到插件注册的 node module |
| `binding_alive_after_reload` / `module_alive_after_reload` | reload 后两者仍可用（进程级产物身份保持） |
| `no_script_errors` | per-type 脚本日志无 `[ERROR]` |
| `clean_exit` | 经 RCON 正常停服 |

Loader 差异（已实测，不掩盖）：NeoForge 的 FML 注解扫描只实例化 addon 类一次；Fabric loader 在
pre-launch 探测时会额外构造一个一次性入口实例（脚本把 `instance=1` 归给 NekoJS 真正发现的那个），
所以 Fabric 的 `addon_discovered` 总数是 2。两项都在 `counts.json` 里留档。
