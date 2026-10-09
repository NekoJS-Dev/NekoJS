# Directory move failure — unresolved recovery policy

2026-10-09，专属工作树 config-sync-validation，固定旧提交0d06a6fb。ServerPackCache.java在该提交与当前HEAD的Git字节完全相同。诊断源码只添加到此工作树的 test 目录，没有修改生产代码、测试门禁或用户环境。

## 反馈环

```powershell
./gradlew.bat :common:test --tests '*ServerPackCacheMoveDiagnosticTest' --offline --console=plain
```

归档含每组实际执行的临时源码副本、日志、XML和直接退出记录；同名临时测试按顺序替换，一次只运行一组。没有与主工作区构建或游戏并行运行。最初复制既有 physicalRestore fixture，只重复100次，先执行实际 replace，再验证注入删除失败仍向调用者传播并保留 staging。它在2次replace处抛AccessDeniedException：备份旧目录1次、安装新目录1次。尚未到达预期注入失败，符合主矩阵症状。

| 诊断输入 | 次数 | 初始移动失败 | 退出 |
|---|---:|---:|---:|
| 旧提交实际PhysicalReplacement路径 |100|2|1|
| 纯Files.move、单文件目录 |100|0|0|
| 同单文件输入扩大次数 |1000|0|0|
| 纯Files.move、嵌套脚本/assets文件目录 |1000|4|1|
| 嵌套目录+失败状态/延迟探针 |300|0|0|
| 同状态/延迟探针扩大次数 |1000|20|1|

纯Files.move用例只创建目录、写文件、移动旧目录到backup、移动新目录到原位置并核对文件；不调用任何NekoJS pack/runtime代码。单文件输入未复现不能证明其永远安全；嵌套输入足以复现，但没有宣称每个文件都是必要条件或已识别最小因果输入。

## 假设与观测

探针前按顺序记录三项可证伪假设：短暂外部占用会使同路径稍后成功；JDK句柄释放问题会影响关闭后即时移动；固定权限问题会持续拒绝同路径。探针记录 source/target存在状态及DOS只读属性，然后仅在首次AccessDeniedException后尝试1/10/100ms延迟移动。此操作是临时诊断，未定义或实现生产重试策略。

1000次探针捕获20次首次失败（backup3次、install17次），均 sourceExists=true、targetExists=false、sourceReadonly=false；20次均在额外1ms后成功，原测试仍重新抛出并保留首次异常，因此整组退出1。没有删首次失败、把恢复样本改成普通PASS或改失败计数。此观测不符合这些样本中的持续权限拒绝，支持瞬时占用/句柄时序；不能区分外部进程与JDK/操作系统因素，也不能据此断言杀毒软件是原因。

## 边界与剩余工作

该症状在旧提交真实路径复现，因此并非只能在新Python成员选择代码下观察。主工作区最终普通矩阵仍为FAIL；旧提交完整common单次PASS、聚焦28测试PASS和本诊断均分别保留，不相互替代。未来若定义恢复策略，必须证明失败保留、严格rollback、路径校验、持久化数据及线程/延迟预算仍满足契约；当前未添加重试、atomic-move覆盖、copy/delete回退、测试排除或权限升级。

全部自有临时测试源码在归档/readback后按精确路径移除；工作树恢复clean。生产源码无DEBUG探针。专用归档 [filesystem-diagnostic-evidence.zip](filesystem-diagnostic-evidence.zip)，SHA256 `bed5af78869333a2de3abe8f97870382f73c875ee71399564f949b09ed0f4ae7`；逐项SHA及测试统计见 [manifest](filesystem-diagnostic-manifest.json)。此包补充已固定的Python验收包，没有重写旧证据。
