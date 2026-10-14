# 脚本包 A–E 与内容模组 JAR：最终交付记录

日期：2026-10-14。分支：`stonecutter`。基线 HEAD：`fe89c770d2d47efe1d3f72421328565eaf7dd889`。验证时为本地未提交工作区；验证输入按下述 SHA-256 绑定，提交与推送按维护者后续授权执行。

按维护者授权完成 [交接任务](2026-10-14-script-pack-work-handoff.md) A–E，并加入内容模组 JAR 生成器。实现未使用子代理；早期只读探索结果作为参考。本文记录工程实现与实际验证结果，不代签维护者验收。完整契约见 [实施记录](2026-10-14-script-pack-implementation.md)，作者用法见 [脚本包中文文档](../../wiki/script-packs_cn.md)。

## 最终行为与范围

| 阶段 | 最终行为 | 主要证据 |
|---|---|---|
| A | 来源限定选择器；启用意图、发现、入口结果和活动实例分离；保存失败不发布新意图；WORLD 刷新不复活旧会话；远端/MOD 只读 | `ScriptPackOperationsTest` 7 项，WORLD 相对路径测试，命令和远端测试 |
| B | `formatVersion: 1` 严格清单、四类显式入口、包内 import-only 库、受限 SemVer/环境依赖、确定依赖排序及阻止原因；schema 与 parser 共源 | `VersionedPackTest` 8 项，旧包测试，生成 schema，实际模板和 CLI |
| C | 每个版本化包/类型/来源实例有独立 Context 与 Node runtime；包私有 global、root shared；有界 JSON 能力桥、等待/失效/恢复及旧句柄失效 | `PackActivationTest` 16 项，root 7 项，timer 5 项；实际 WORLD 挂载与网络断线 |
| D | 提供者及必要消费者闭包联合准备、预检和提交；失败保留健康旧集合；无关实例保留；整表领域明确扩大集合；撤销抢占与贡献撤回 | 真实 Graal 闭包、共享写冲突、关闭/撤销抢占、世界 A/B/A、整表重收集；CLIENT render 生命周期测试 |
| E | 创建/校验/可重复归档/事务安装/升级恢复；授权同步接入；五节点内容 JAR 生成、已加载模组发现、原生 assets/data | `PackToolsTest` 4 项、`PackSyncClientTest` 24 项，实际 PowerShell 工具、五节点真实 loader 与客户端 |

旧无版本清单、平铺脚本、文件 priority/after 和旧共享 Context/global 保留兼容语义。新包显式选择加入；独立入口不递归执行库文件。跨包服务使用 `Pack.provide/require`，不能传递任意 guest/host 对象。新增 Script API 为 `Pack.selector`、`Pack.activation`、`provide`、`require`，公共合同在 `api.pack.PackAccess`，模板携带声明。运行实现与协调器仍在引擎内部；没有新增 Plugin Hook/Point 通道。

`NekoRuntimeRoot` 仍是唯一运行时 owner，普通 reload 不重建 Plugin Runtime。消费者先停止，再释放提供者。监听器、timer、绑定、Context、模块及领域清理分别尝试，异常保留原因，联合操作单独返回 `cleanupFailures`。回调中关闭 root 等待最外层 guest 返回；延迟失败记 `NEKO-1017`。计时器拒绝关闭后的注册/迟到入队，线程退出等待上限 2 秒，超时或中断报告未完成清理。

世界/连接结束或提供者失效后，整表领域重收集存活订阅而不重跑入口；失败尝试空计划，失败撤回另行报告。CLIENT render 按实际 Context 回收。STARTUP 初次加载后，版本化包变更或热加入均要求重启；旧 STARTUP 的非事务重载契约保留。任意 Java/世界/文件副作用及嵌套对象修改仍不在受管事务回滚承诺内。

本机启用状态文件、签名和 sync wire 格式保持。无签名远端仍同时需要客户端策略与服务器信任，并获得独立的无签名授权凭证；没有 local-trusted 回退或伪造签名 key。MOD 包通过 loader 发现，在内容哈希目录物化，管理来源只读；JAR 原生 data 不再次挂载脚本物化目录。MOD 不进入 GLOBAL/WORLD 同步 wire，按所需运行侧部署。

每个活动的版本化包/类型实例拥有一个 Context、模块会话与 Node runtime，候选期间旧、新资源并存。记录了资源粒度，未进行 CPU/内存性能优劣比较。

## 最新源码的自动验证

最终矩阵日志：[`build/script-pack-final-matrix-12.log`](../../build/script-pack-final-matrix-12.log)。结果 **PASS**，`BUILD SUCCESSFUL in 3m 9s`，104 个任务（34 执行、70 up-to-date）。此前 matrix-10/11 均通过；matrix-11 加入各类型清理失败隔离，matrix-12 包含最终补齐的内部诊断编号。

```powershell
.\gradlew.bat :common:check guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
python tools/nekojs-ci-gates.py all --json
npm run test:probe-types
node scripts/wiki-docs.mjs check --strict --structure-only
git diff --check
```

| 检查 | 结果 | 记录 |
|---|---|---|
| common 完整 check（含隔离）及 guardLint | PASS | 最终矩阵；未放宽门禁 |
| 三个 NeoForge、两个 Fabric 节点 build/test/制品校验 | PASS | 最终矩阵 |
| CI 子集、processor 边界、声明一致性、节点报告四个检查 | PASS | `build/script-pack-ci-gates-final-12.json` |
| Probe TypeScript 声明检查 | PASS | `build/script-pack-probe-types-final-12.log` |
| 清单 schema 重生成与 canonical 字节一致性 | PASS | `build/script-pack-schema-final.log`；额外临时输出比较完全一致 |
| Wiki 结构、双语配对及链接 | PASS | `build/script-pack-wiki-structure-final-12.log`，26 主题/0 问题 |
| Wiki 全量 strict | FAIL（历史基线） | `build/script-pack-wiki-strict-final-12.log`，六个未修改主题/12 条 |
| 最终 diff 空白检查及源码绑定复核 | PASS | `build/script-pack-diff-check-final.log`；源码 hash 无漂移 |

以下统计汇总各目录全部 `test-results` XML，包括 isolation/平台 gate 套件；不是单一 `test` 任务的 stdout 数量。跳过项保留原状，不把跳过算作通过。共声明 4906 个测试，0 failure、0 error、272 skipped。

| 节点 | 套件 | 声明测试 | failure/error | skipped |
|---|---:|---:|---:|---:|
| common | 304 | 2311 | 0/0 | 5 |
| NeoForge 1.21.1 | 101 | 445 | 0/0 | 31 |
| NeoForge 26.1.2 | 136 | 681 | 0/0 | 81 |
| NeoForge 26.2.0 | 136 | 681 | 0/0 | 81 |
| Fabric 26.1.2 | 82 | 394 | 0/0 | 37 |
| Fabric 26.2.0 | 82 | 394 | 0/0 | 37 |

精确绑定：[`build/script-pack-verification-final-12/binding.json`](../../build/script-pack-verification-final-12/binding.json) 保存基线 HEAD、82 个本轮源码/测试/构建输入的 SHA-256、6 个最终制品 SHA-256及统计；同目录保留 XML 副本。原始日志、绑定、XML、截图和测试世界在本机 build 目录，不随源码提交；本文记录其结果与复现入口。两份既有声明 golden 不计入本轮所有权。原 matrix-8 绑定和原始失败日志另存保留，未覆盖成“全部通过”。本轮没有改 canonical 声明输出掩盖测试失败；schema 由 `:common:generatePackSchema`，wiki 由既有生成器生成。

## 实际作者工具与游戏验证

工具入口：[`tools/nekojs-pack.ps1`](../../tools/nekojs-pack.ps1)，核心 `PackTools`，游戏命令经 root 作者操作入口。实际 create、validate、archive、install、jar 均成功。归档和 JAR 测试检验可重复字节、原清单/签名、hash、内容范围、恶意路径/数量/体量限制、替换失败恢复及旧启用意图；只通过 ZIP 检查没有被记成 loader 成功。

所有游戏运行使用 `build/` 下独立测试目录。下列较早运行是当时源码的真实证据，不能被当作最后 timer/关闭变更的全节点复测；最后一行在最终矩阵之后，以未再改动的源码补验 STARTUP、MOD 入口与原生 data。最终 native 绑定记录 fixture JAR、源码/制品及运行输入摘要。

| 环境/路径 | 结果与实际观察 | 日志 |
|---|---|---|
| NeoForge 1.21.1 专用服务器 | PASS；lowcodefml 内容 JAR 被加载，MOD 入口、服务器 Done、原生 DATA true，正常停服 | `build/script-pack-real-mc-neo121-server-fixed.log` |
| NeoForge 26.1.2 GameTest | PASS；内容 JAR 被加载，MOD 入口、DATA true，`All 1 required tests passed` | `build/script-pack-real-mc-neo261-data.log` |
| NeoForge 26.2 专用服务器 | PASS；MOD 入口、Done、DATA true，正常停服 | `build/script-pack-real-mc-neo262-fixed.log` |
| Fabric 26.1.2 专用服务器 | PASS；MOD 入口、Done、DATA true，包查询/只读拒绝/重载，正常停服 | `build/script-pack-real-mc-fabric261-data-run.log`、对应 commands/stop 日志 |
| Fabric 26.2 专用服务器 | PASS；MOD 入口、Done、DATA true、MOD 包重载，正常停服 | `build/script-pack-real-mc-fabric262-final.log` |
| NeoForge 26.2 客户端 | PASS；CLIENT MOD 入口，原生 ASSET true，真正游戏画面显示中文包状态/版本/活动/入口结果 | `build/script-pack-real-mc-client262-final.log`、下方截图 |
| 独立进程客户端与服务器 | PASS；配置阶段激活授权 `SERVER_CACHE:packs:remotesmoke`；服务器随后 login/join；断线撤回远端包并保留本地 MOD | 同一客户端日志及 NeoForge 26.2 服务端日志 |
| NeoForge 26.2 WORLD 来源 | PASS；真实平台挂载 `WORLD:worldsmoke`，私有 count=1、SERVER tick 回调，正常停服 | `build/script-pack-real-mc-world262-final.log` |
| **最终源码 NeoForge 26.1.2 GameTest** | **PASS**；STARTUP MOD 入口一次、SERVER MOD 入口、DATA true、必需测试 1/1，正常关闭 | **`build/script-pack-real-mc-neo261-startup-final-12.log`** |

最后一次运行命令：

```powershell
.\tools\nekojs-pack.ps1 -Action jar -InputPath build/script-pack-native-final/packboot -OutputPath build/script-pack-native-final/clipack-neoforge-261-startup-final-12.jar -Loader neoforge -Minecraft 26.1.2
Copy-Item -LiteralPath build/script-pack-native-final/clipack-neoforge-261-startup-final-12.jar -Destination build/script-pack-native-final/neo261-startup/mods/clipack.jar
.\gradlew.bat :26.1.2:runGameTestServer --init-script build/script-pack-runtime.init.gradle '-PpackNode=26.1.2' '-PpackGame=build/script-pack-native-final/neo261-startup' --console=plain
```

结果 `BUILD SUCCESSFUL in 26s`、24 任务（1 执行、23 up-to-date）。已执行必需 GameTest 成功断言，未只按进程退出码判定。精确输入与日志绑定见 [`native-binding.json`](../../build/script-pack-verification-final-12/native-binding.json)，其中也明确标记了较早 native 运行未捕获最终运行时 class hash 的限制。

![真实客户端中文包状态](../../build/script-pack-native-final/client262-status.png)

加载器提示页由用户点击继续；用户实际完成本地连接。最初信任未匹配被正确拒绝，补齐本地 fixture 的当前 hostname 信任后才激活；没有改动服务器地址归一化合同。MCP 在开发客户端返回 `no screen/no player`，截图可用，所以必要 UI 操作由用户完成。独立测试客户端的 MCP 辅助 HTTP 线程在 FML 关闭后仍存活；正常关闭请求后，仅终止已核实归属的辅助客户端 JVM。该结束不能当作 NekoJS 正常客户端完整退出证据；记录在 `build/script-pack-native-final/client262-cleanup.log`。用户其他进程未停止。

世界 A/B/A 同 ID 私有状态重建、旧能力句柄、关闭抢占及领域撤回由真实 Graal 测试覆盖；没有宣称手动在客户端切换了两个世界。签名/key pin、同桶候选失败恢复和授权撤销有自动回归证据；实机跨进程传输 fixture 为明确授权的无签名包，未宣称全部签名模式实机复测。

## 失败保留与修复

- matrix-3：曾重复启动同目录测试，报告写入冲突；此运行无效，不作为源码成功/失败证据。其后验证串行。
- cleanup focused：最初使用不存在的测试 lookup，后改为真实 `ScriptManager.from`；重复关闭与 Engine 宿主配置污染暴露后，修复关闭幂等边界，新 fixture 使用独立 Engine。
- matrix-5：已取消/耗尽 Context 的 close 重抛终止异常遮盖原失败；现仅识别 Graal 的取消/资源耗尽通知，其他关闭异常仍传播，原回归恢复。未改变健康 Context 的关闭异常契约。
- matrix-6：既有 loader discovery fixture 复用前一个客户端 Platform，服务器过滤断言受测试顺序污染；fixture 显式安装 server Platform 并恢复前值，原断言保留。
- 首次 NeoForge 1.21.1 GameTest：`No test functions were given`，虽 Gradle success 也不计有效 GameTest。随后使用专用服务器验证。首次 1.21.1 fixture 用了现代 `Identifier`，改为该节点的 ResourceLocation API；只改测试 fixture。
- 首次 MOD data 实机运行触发过早的重复目录资源 reload，NeoForge datapack 同步访问尚未创建的 overworld。修复 MOD 不再挂载物化 data，五节点 `ScriptPackModDataMountTest` 验证 GLOBAL/WORLD 仍挂载；专用服务器与最终 GameTest 已复验原生 data。
- 第一次连接信任身份不匹配按合同拒绝；修正隔离 fixture 的 hostname 信任后重连成功。没有放宽信任检查。
- Windows Perflib 性能计数器、JDK/native-access/Unsafe、旧 logoFile 以及 Gradle 弃用警告保留；不将系统或依赖警告涂改成无警告运行。
- Wiki 六个历史主题的 12 条问题经 `build/script-pack-wiki-baseline-audit-final.log` 逐篇证明 `baseline_stale=true body_unchanged=true`。新增/本轮维护主题已更新其实际 digest；人类审核不由 digest 代替。

## 最终审查与剩余边界

已检查完整 tracked diff 及本轮 untracked Java/API/测试/模板/schema/工具，包含调用者、节点对应实现、语言、owner、公共接口和派生文件。公共 common 没有新增 Minecraft/loader 依赖；node business 接入沿用现有 platform 分支。没有放宽断言、隔离或 golden 门禁。既有 golden、研究/native 图片和工具目录保留，未批量暂存、清理或提交。测试密码/world/trust 原始内容仅留隔离 build 目录，未复制到交付正文。

**未执行/仍待实际结论：** 维护者整体接受与新双语主题的人类翻译审核；最终源码在所有 OS/JDK/CI 环境的运行；全部签名模式跨进程实机复测；客户端双世界手动切换及所有节点 CLIENT 图形验收；CPU/内存性能比较。现有原始证据不补签这些结论。

**范围外：** VS Code/语言服务器/编辑器扩展、在线市场、自动依赖下载、Forge 节点、完整 Fabric 目录包资源桥、任意宿主副作用回滚。内容 JAR 依赖 NekoJS，JS 没有编译成独立 Java 模组；当前支持三 NeoForge、两 Fabric 目标。

维护者接受记录：目前只收到加载器继续/本地连接操作完成的回复，尚无整体验收结论。工程实现、自动门禁和上述本地运行证据已交付。
