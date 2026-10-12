# 回调参数宿主类型的模块 import 修复

实际 Java Executor 参数会生成 `$Executor_<$Runnable>`，旧 import 收集只看到 Executor，遗漏 Runnable。IndexFileGenerator 现在通过既有 TypeAliasRegistry.withReferences 观察实际类声明和函数接口别名的渲染引用，补齐跨包宿主类型。仅收集实际 `$` 类型引用，保留 string/number 映射，避免为 Java 包装类型添加无用 import；不增加第二套签名展开规则。运行时、安全配置、公开方法、类扫描范围和 manual 生产器不变。

源码基点 da6bad60385d8685b34e58af02c92d3da8d293b5，生产补丁 SHA256 `58528bf75199666cc5996901ea83631697d7c7c66f4e08c58888427d99700f6f`。包内保存精确源码、测试、patch 和五个新官方 JAR 的逐项编译类绑定，不能套用旧制品清单。

真实 RED Java 退出1、失败 XML 已冻结，包含生产器输出。严格 caller RED 退出2/TS2304 缺 `$Runnable`，GREEN 退出0；两个非法 callback 分别被 TS2345/TS2339 拒绝。RED 使用失败 XML 中实际捕获的用户模块，两个陪同模块来自 GREEN 生产器，证明该捕获模块的缺失符号，不能称完整旧三模块输出验收。首次错误的 caller moduleResolution 配置及失败仍保留，不作为缺失 Runnable 的 RED。最终实际三模块输出与 caller 所用 GREEN 逐字相等。

首轮修复曾引入未使用 `$String` import，导致两个既有 golden 检查失败；原日志和退出1保留。改为只收集实际 `$` 引用并新增 string/number 回归后，既有 golden 原样通过。第二轮完整矩阵出现包同步审批断言失败，原失败 XML 已冻结，根因未确认；[阶段守卫](../ticket37-pack-reload-stage-guard/README.md)单独加强测试。没有改生产信任逻辑，没有删除失败或更新 golden。

最终完整矩阵 PASS：4808 tests、271 skipped、0 failures/errors、810 XML；109 tasks，2m43s。下列命令、原始日志及 XML 均归档：

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm.cmd run test:probe-types
python tools/nekojs-ci-gates.py all --out build/ticket37-sam-module-import-repair/platform-gates.json
```

五个新 JAR 在专属服务器实际生成 Probe；注册、放置、重载、NeoForge fluid identity、26.1.2 effect 重载前后实际施加 PASS。1600 份 Python stub 全部 AST PASS。五服正常 RCON stop、直接退出0，十个端口已核验可绑定。没有手改生成输出、提交密码/world/raw JFR 或操作用户进程。

| 节点 | 严格 TS server/startup | Pyright errors | 结果 |
|---|---:|---:|---|
| NeoForge 1.21.1 | 644 / 645 | 7 | FAIL |
| NeoForge 26.1.2 | 801 / 802 | 7 | FAIL |
| NeoForge 26.2.0 | 696 / 697 | 7 | FAIL |
| Fabric 26.1.2 | 1207 / 1208 | 13 | FAIL |
| Fabric 26.2.0 | 963 / 964 | 13 | FAIL |

保留实际默认 editor includes，仅验收设置 skipLibCheck=false/noEmit=true。与上次 manual-item 实机比较 file/code/首行消息的多重集，忽略位置/安装根：十项目合计移除1002条诊断，零新增。每侧减少109/126/116/83/67条；未比较多行说明，不把所有剩余错误归因于此次缺陷。TSC 直接退出2、Pyright1；收集器退出0只表示收集完成。

自审确认生产改动只在 canonical 模块生成器，复用既有引用命名机制，线程内收集并写入原 import cache；测试覆盖 contextual Runnable 与 primitive 映射。没有依赖、权限、公开删除、golden 或 gate 变更。额外引用观察和函数别名渲染的性能尚未正式验收，不宣称零成本。没有独立审查或真实维护者结论。

[不可变证据](sam-module-evidence.zip)：4312 entries、7825634 bytes，SHA256 `c44316c35649faa4c57f25c8fa7b202721ef559194011cc7cb2b6e5c2222104f`。[清单](manifest.json)、[结果](runtime-summary.json)、[独立验证器](verify.py)检查完整 hash、真实 RED/GREEN、最终全部矩阵 XML、实际退出、默认配置和诊断增减。

新补丁正式性能 NOT RUN；最近精确63d538dc[重载正式性能](../ticket37-manual-item-performance/README.md)372.2/366.8ms FAIL285.3仍保留，旧 d109 PASS不能继承。完整 IDE、Pyright、性能、当前其他多人/下载/首像素/客户端节点/领域/磁盘和真实维护者及发布回滚验收未完成。自动续跑暂停、权限字段问题未解决，A22/A28 和发布授权边界不变；整体 NOT COMPLETE。
