# 活动版本源码覆写修复（2026-10-31）

## 问题与判定

用户在 IDEA 切到活动 1.21.1 后执行所有节点的 classes/testClasses，:1.21.1:compileJava 报大量 duplicate class。同名实现同时来自仓库 src/main/java 与 versions/1.21.1/src/main/java。主仓库位于 D:/mcmodDemo/NekoJS-mult，存在用户切版本后的大量未提交源码变化；诊断和活动版本矩阵在独立的 capture-views 工作树进行。

已在当前源码 ac077e92 上直接复现 compileJava 失败（build/active-1211-compile-red.txt）。随后用真实 JavaCompile/ProcessResources 输入建立较快的检查：修复前 62 个覆写位置全部出现共享副本，检查退出1；不是只靠 IDEA 缓存或日志猜测。

核对 Stonecutter 0.9.7 自带源码：createProcessingTasks 的 stonecutterGenerate 按节点本地文件是否存在排除共享文件；configureSource 对活动节点直接挂共享目录，绕过了该生成过滤。因此非活动版本能通过，不表示活动版本没有重复输入。IDE 注入和残留生成文件不是本轮复现的必要条件。

## 修改

- 新增共享的 nekojs.node-sources convention，两个加载器均应用。对每个 source set 的 Java/resources 输入，若节点本地有相同相对路径，只排除本项目拥有的借入共享树、Stonecutter 生成树或 Fabric raw root 的对应文件。保留节点文件，其他路径继续按已有规则处理。
- 过滤位于 SourceDirectorySet，编译、资源处理及其他源集消费者共用，不只给某个 compileJava 任务加临时排除。没有改变 Java 类、Script/Plugin API、MC 行为或 ICU 依赖。
- switchVersion 原来只替换控制器的 active 行，未调用源码转换。现委托现有 stonecutterSwitchTo<node> 任务，使共享源码转换与活动声明同步；不重新实现 Stonecutter 处理器。IDE 切换后的 Gradle 同步仍然需要正常完成。
- 新增 verify-node-source-selection.init.gradle 检查真实编译/资源输入中的覆写赢家；verify-active-node-sources.py 逐个活动版本执行跨节点检查，并可带 --compile 重放 classes/testClasses。

## 验证计划和已执行结果

第一轮没有额外夹具的 5 活动版本 × 5 目标节点编译已全部通过，共25组 main/test 编译。

第二轮额外在共享树、每个节点 main/test 和 Fabric raw root 中创建同相对路径 Java/资源探针，检查节点文件独占输入；驱动使用排他创建，绝不覆盖既有文件，finally 删除自己创建的文件并恢复原活动版本。原始操作异常保留；恢复失败作为附注或独立错误报告，不覆盖原始异常。

```powershell
python tools/verify-active-node-sources.py --compile --target build/active-source-matrix-with-resources
```

该驱动会转换共享源码，应在隔离工作树中执行，不能拿用户正在编辑的主仓库作为随意切换的测试目录。本次不会对主仓库执行活动版本矩阵。

| 检查 | 实际结果 |
|---|---|
| 第一轮5活动×5目标，main/test编译 | 25组全部PASS |
| 第二轮增加Java/资源探针，5活动×5目标并编译 | 25组全部PASS；410条输入选择检查，0失败 |
| 最终CI同款选择矩阵 | 5活动×5目标全部PASS，410条检查、0失败；结束后共享src无Git差异，活动版本恢复26.1.2 |
| 驱动成功恢复、双失败保留原异常、既有文件保护 | 3项PASS；旧恢复实现实际退出1，修复后退出0；空目录遗漏的追加断言也先红后绿 |
| `:common:check`、五节点 `build`、`guardLint --offline --console=plain` | PASS，6m59s；104 tasks：59 executed / 14 from cache / 31 up-to-date |
| 主仓库IDEA原classes/testClasses任务，另加五节点输入检查 | PASS，54s；75 tasks；活动1.21.1保持不变，原src工作区diff摘要前后一致 |
| 最终重新打包及探针检查 | 打包PASS，26s；6份普通/server制品均无探针class、资源文件或空探针目录 |
| 五节点source-roots及平台汇总门禁 | PASS；五节点exit 0，平台汇总exit 0 |

常规构建日志：build/active-source-final-build.txt。编译矩阵：build/active-source-matrix 与 build/active-source-matrix-with-resources。最终CI同款输入矩阵：build/active-source-ci-final。主仓库重放：D:/mcmodDemo/NekoJS-mult/build/active-source-idea-replay-20261031.txt。

驱动从 versions/*/gradle.properties 发现节点。测试开始前保存共享源码原字节，恢复活动版本后还原Stonecutter往返切换产生的守卫注释格式变化；清除自己创建的文件及空目录，不递归删除目录、不覆盖既有探针路径。CI在已有节点构建后运行这套检查，避免只覆盖默认活动版本。

产物复核曾发现空探针目录残留，没有发现探针class或资源文件。补上创建目录跟踪及对应红绿回归后，重新清理、打包并检查六份制品，结果均为零残留。没有弱化检查来忽略目录。

没有运行游戏客户端或专用服务器，不把构建问题的通过外推为模组实机验收。

切换任务依赖查询另有独立回归：未提供 `-Pnode` 时，旧实现读取缺失属性导致模型检查失败；改为允许模型读取空依赖，仅实际执行切换时要求目标。回归任务 `verifySwitchTaskModel` 已接入矩阵。此项只改变构建任务的配置行为，不改变脚本或运行时合同。旧实现模型检查实际失败，修复后通过（22s）；最终主仓库原IDEA任务连同模型检查再次通过（12s，69 tasks，68项up-to-date）。日志为 build/active-source-switch-model-red.txt、build/active-source-switch-model-green.txt 和主仓库 build/active-source-idea-final-20261031.txt。

## 交付约束

原工作树活动版本为26.1.2，矩阵结束后恢复它，检查自动转换没有残留到最终提交。主仓库仅同步这项构建修复，保留其活动1.21.1、现有源码编辑和未跟踪数据。不会混入此前命名提交或票37实验，不 push、不发PR。
