# Callable Node 模块的成员与构造参数声明修复

实际 assert.ts 重复定义 NekoSyncFn；其模块值是函数，成员通过 Object.assign 附加，旧提取器只支持对象字面量变量，遗漏 AssertionError 等命名成员。现在删除重复的私有类型别名，从实际函数的单个 literal source Object.assign 中提取成员，复用原类/函数/对象渲染。类参数默认值从 ambient 签名移除，尾部默认参数成为可选参数，位于必填参数前的默认参数保留必填位置并允许 undefined。无默认值的签名逐字保留；字符串、注释及其他目标的赋值不产生虚假导出。

改动限于 canonical NodeModuleTypeDocs、assert.ts 的重复类型别名和两项行为回归。运行时断言逻辑、HostAccess/ClassFilter、权限、公开 Java/Plugin API、wire 和持久格式不变，无 Minecraft/loader 依赖进入 common。提取器仍是既有简化声明扫描器；本修复验收命名成员与类默认参数，default callable export、其他模块签名/支持范围和完整 IDE 保留为后续任务。

源码基点 `6d10f0ece24a67ed6684577acc181bb54794d395`，生产补丁 SHA256 `2262c610aa2ffabd7e63a6f5ade42cf6d27d4a3440e70821f4bce8e6e3ae31c7`。包内保存精确源码/test/patch、五 JAR SHA 和全部实际编译类绑定；归档时还逐个验证 JAR 中 assert.ts 与当前 canonical 源码完全一致。

| 实际生产器阶段 | Java 直接退出 | 严格 caller | 诊断 |
|---|---:|---:|---|
| 原始 RED | 1 | 2 | 16：8 duplicate、4 missing class、4 missing export |
| 只删除重复别名 | 1 | 2 | 8：missing class/export |
| 再提取实际附加成员 | 1 | 2 | 4：TS2371 ambient 参数不能有 initializer |
| 最终 GREEN | 0 | 0 | 0 |

三个 RED Java XML/生产器输出/直接退出均冻结。最终 caller 对四个实际模块别名创建 AssertionError，支持无参和 options，检查 Error/字段类型及 throws/rejects；错误 message 类型和 callback 被拒绝。另一个实际提取器 fixture 覆盖 default-before-required、嵌套对象/字符串逗号和 generic initializer；合法 caller 通过，缺必填参数和错误 Map 类型被拒绝。四项非法 caller 均没有 any 或 skipLibCheck 掩盖。首次 callable CLI 使用错误 types 参数的配置失败保留，不作为缺陷 RED；有效配置使用 types=[]。

实际 Node 模块运行时回归 PASS，既有 golden 原样通过。最终完整矩阵 PASS：4810 tests、271 skipped、0 failures/errors、810 XML；109 tasks，3m2s。首轮矩阵也通过，原始两轮日志均保留。验证命令：

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm.cmd run test:probe-types
python tools/nekojs-ci-gates.py all --out build/ticket37-node-assert-repair/platform-gates.json
```

五个新官方 JAR 在新专属 world 实际生成 Probe。注册、放置、重载、NeoForge fluid identity、26.1.2 effect 重载前后实际施加 PASS；1600份 Python stub 全部 AST PASS。五服正常 RCON stop、直接退出0，十端口可绑定；每服完整 manual 中的 assert 声明与严格 caller 的实际生产器输出逐字一致。没有手改生成输出、提交密码/world/raw JFR 或停止用户进程。

| 节点 | 严格 TS server/startup | Pyright errors | 结果 |
|---|---:|---:|---|
| NeoForge 1.21.1 | 632 / 633 | 7 | FAIL |
| NeoForge 26.1.2 | 789 / 790 | 7 | FAIL |
| NeoForge 26.2.0 | 684 / 685 | 7 | FAIL |
| Fabric 26.1.2 | 1195 / 1196 | 13 | FAIL |
| Fabric 26.2.0 | 951 / 952 | 13 | FAIL |

保留默认 editor includes，只设置 skipLibCheck=false/noEmit=true。与 [SAM 实机](../ticket37-sam-module-import-repair/README.md)比较 file/code/首行消息多重集，忽略位置/安装根：每项目恰好移除8次 NekoSyncFn TS2300 和4次 AssertionError TS2304，十项目合计120条，零新增；多行说明未比较。TSC 直接退出2、Pyright1，外层收集器0仅表示收集完成。其他真实错误未删改。

自审确认引用/类渲染复用既有机制，新增辅助方法只处理实际 callable module 和 class ambient 参数，默认参数的必填/可选位置由严格正负 caller 验证。不更新 golden、不放宽 gate、不新增依赖，无独立审查或真实维护者结论。[不可变证据](node-assert-evidence.zip)：4340 entries、7847790 bytes，SHA256 `35203739f3390f862606af9da467fc9f45e490ebd2ca8562c915d9b9910c9f1e`。[清单](manifest.json)、[结果](runtime-summary.json)、[独立验证器](verify.py)核验全部 hash、矩阵 XML、分步 RED/GREEN、正负 caller、实际生成内容/退出/配置和精确诊断增减。

新补丁正式性能 NOT RUN；精确63d538dc重载372.2/366.8ms FAIL285.3仍为最近正式记录，旧 d109 PASS不能继承。完整 IDE、Pyright、性能、其他当前多人/下载/首像素/客户端节点/领域/磁盘、真实维护者及发布回滚验收未完成。自动续跑暂停、权限字段问题未解决；A22/A28与发布授权边界不变，整体 NOT COMPLETE。
