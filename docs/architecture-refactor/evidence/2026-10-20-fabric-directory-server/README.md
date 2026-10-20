# Fabric 目录包的原生服务端验收

Fabric 26.1.2 / 26.2 两个真实服务端均通过下列场景，正常 RCON 停服，launcher exit 0。主代理编写并执行验收；没有生产源码修改，没有客户端渲染结论，也没有代签整体维护者接受。

| 场景 | 两节点实际观察 |
| --- | --- |
| 初次目录发现 | 原版自动加载 `nekojs:directory`；GLOBAL / WORLD 脚本进入各自 Context |
| 初次 WORLD 配方 | recipes 回调读到 WORLD data，加入脚本配方；首次 started 时两个配方均存在于原生 RecipeManager |
| 直接覆盖与独有资源 | 根/GLOBAL/WORLD 函数值为 1/2/3；同名 winner 为 WORLD 的 30 |
| 资源栈合并 | 根 stone、GLOBAL dirt、WORLD cobblestone 均属于同一原生 block tag |
| 原版 `/reload` | 修改 WORLD 函数后为 31；tag 改成 andesite，stone/dirt 保留，cobblestone 消失 |
| 排除 | 禁用包、NeoForge-only 包的函数均为 Unknown function，入口未执行 |
| WORLD 禁用与恢复 | 禁用时同名资源回退 GLOBAL 的 20，WORLD 独有函数消失、andesite 撤回；恢复后回到 31、独有函数和 andesite 恢复 |
| 脚本事务 | 每节点三次 `nekojs reload` 均 COMMIT / no errors；每节点三次原版资源重载 |

脚本重载与数据资源重载采用各自真实边界：禁用时先撤销脚本监听再撤回 data；恢复时先恢复 data 再安装依赖它的脚本。没有将两套机制包装成跨系统原子事务。首次 started 检查的是原生配方存在性，没有把它扩大为实际熔炼产出或客户端配方同步验收。

源码绑定 `acdad763a1133cef475eecf5983e9c1a42da1436`；其生产源与已测 `26844b7e` 没有差异。每节点 1569 个实际生产类与封存 JAR 全部一致，前后 class / JAR 摘要保持。这里运行的是 Loom source runServer；封存 JAR 用于产物绑定，未宣称独立安装 JAR 启动。

26.1.2 保留 91 条命令观察，26.2 保留 88 条；差异来自首次区块加载轮询次数，四次 winner 观察均为 30 → 31 → 20 → 31，没有删除失败或首值。`verify.py` 可只读复核全部归档摘要、实际 JAR 中的类、fixture、原生命令响应、配方日志和正常退出。

## 保留的准备失败

1. 首次 init script 同时应用到 buildSrc，查找不存在的节点项目后退出；没有进入游戏。后续使用 findProject 并忽略不含该节点的构建。
2. 第二次已通过启动、初次配方和基础资源观察，但区块就绪检查错误地要求 `say` 在 RCON 返回文本中回显。实际日志有该广播、RCON 返回为空，观察器等到超时后正常停服。改用原生条件写 scoreboard 并查询实际值，未削弱区块加载或资源断言。

两次失败的控制器、结果、日志和可用原始观察均保留；通过的是修正后的第三版控制器。隔离测试仅监听回环地址；归档不含 server.properties、随机 RCON 密码、世界存档或环境变量。

## 未完成边界

客户端初次模型、`parentModel` / `texture` 实际加载与渲染、语言 resource stack、F3+T、Fabric A→B→A，以及远端资源授权/拒绝/断线撤回仍待客户端验收。已有 common / 节点行为测试和 NeoForge 实机证据不能替代这些 Fabric 场景。全游戏 1/4/8 包内存预算与长期稳定性、整体维护者接受也仍未完成。

复核：`python -X utf8 docs/architecture-refactor/evidence/2026-10-20-fabric-directory-server/verify.py`，预期 evidenceVerification=PASS，clientAssetsAccepted=false。
