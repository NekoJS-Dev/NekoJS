# Python Bean/member collision repair — partial acceptance

基线 `9b2c5d29`。本次只修改 PythonClassRenderer 和新增回归测试；源码补丁 SHA256 为 `f279a32d04d1c823739a597e85466d6d5ba949f4ae6ae8b65b12556f438e267b`。五节点安装制品由这个未提交补丁构建，不能标成旧提交制品或套用旧 SHA。

## 修改与契约

当合成 Bean 属性与可见真实方法或字段同名时，Python stub 保留实际 getter/setter 调用入口，避免同名 `@property` 遮盖成员。无冲突的 Bean 属性维持原形状；隐藏、重命名成员及重载 setter 都经过边界检查。重载检测与渲染使用同一成员选择规则，避免遗漏 `typing.overload`。

这是 Python 声明表示的明确例外：冲突位置使用真实 accessor 方法，未伪造别名。共享 IR、TypeScript 生产器、运行期 Script API、完整 manual 生产器、HostAccess/ClassFilter 和持久化/网络契约均未修改。真实 Graal HostAccess 测试确认原 getter/setter、方法、字段与静态工厂仍可调用。

## 已通过

- 既有源码先运行真实 RED：两个初始用例中声明冲突用例失败、实际 HostAccess 调用通过。修复后同一反馈环 GREEN；补充 String/int setter 重载和隐藏/重命名边界后，三个回归及既有 Python renderer/backend integration 聚焦测试通过。
- 精确新官方 JAR 在五个专属服务器安装、生成 Probe、完成实际注册与重载。NeoForge26.1.2/26.2 验证14项注册检查、嵌套 fingerprint、FluidType 身份、方块/流体放置及重载前后 MobEffect；Fabric26.1.2/26.2 验证支持的7项子集、放置及重载。全部服务器正常 RCON 关闭，进程退出码直接观测为0。
- 全部1590份 Python stub AST 通过：legacy317、NF26.1.2 377、NF26.2 344、Fabric26.1.2 293、Fabric26.2 259。legacy 的8类不可用类型仍明确发出 NEKO-4029；其声明闭包状态是 **PARTIAL**。
- legacy 全量 Pyright 同节点、同 fixture 比较129→79：按文件/规则/消息归一化后移除50条诊断、新增0条；重复声明58→8。行号移动不算诊断变化。
- 原始证据归档逐项 SHA/readback 与凭据扫描通过，排除了服务器属性、world、凭据、账户文件、二进制制品及原始 JFR。

## 失败与限制

最终源码两次普通矩阵均 FAIL。第一次有2个目录移动失败；第二次保留791份 XML、4595测试、271跳过、1失败、0错误，失败是 ServerPackCacheTest 在预期注入删除失败之前的真实目录 `Files.move` 抛 AccessDeniedException。不得用先前源码矩阵 PASS 替代最终结果，也不得通过重跑聚焦测试覆盖失败证据。

聚焦28个 pack/client 测试重复通过；独立旧提交0d06a6fb完整 common 测试也通过。这些通过原本不足以证明失败原因；后续专属旧提交工作树实际替换重复100次复现2次相同 AccessDeniedException，详见 [文件系统后续诊断](FILESYSTEM-DIAGNOSIS.md)。同类症状在旧源码存在，但具体占用者/操作系统原因及修复仍未确定。未改持久化实现、加重试、跳过测试或弱化门禁。

全量 IDE 检查仍全部 FAIL：

| 实机节点 | Python stub | Pyright错误 / exit | 严格TSC server/startup诊断 / exit |
|---|---:|---|---|
| legacy1.21.1 server |317|79 / 1|2785 / 2786，2|
| NeoForge26.1.2 |377|88 / 1|3552 / 3553，2|
| NeoForge26.2 |344|87 / 1|2927 / 2928，2|
| Fabric26.1.2 |293|88 / 1|673 / 673，2|
| Fabric26.2 |259|88 / 1|630 / 630，2|

Pyright1.1.414明确枚举全部实际 stub，沿用默认 basic/extraPaths；TypeScript5.8.3继承实际默认 editor includes，只设置 skipLibCheck=false/noEmit=true。生成输出未手改。Fabric TSC 含语法级联，不能将其条数直接当作独立语义问题数。TS共享/package/manual文件字节相同，少量 side event/binding文件仅顺序差异且逐行多重集合相同；不宣称完整输出树字节一致或把TS诊断数量变化归因于Python修复。

独立 Standards/Spec 审查任务因服务账户用量限制在返回审查前失败，状态 **NOT COMPLETED**；[责任代理手工审查](REVIEW.md)单列，不能套用旧 Probe 修复的独立审查结论。当前补丁正式性能、客户端第一帧/视觉、实际 in-flight configuration/pre-ACK disconnect、发布制品选择与发布/回滚人类结论均未完成。完整票据和发布验收仍未通过，自动续跑保持暂停，HTTPS凭据恢复前不推送。

## 重现与证据

```powershell
./gradlew.bat :common:test --tests '*PythonBeanMemberCollisionTest' --tests '*PythonRendererTest' --tests '*PythonProbeBackendIntegrationTest' --offline --console=plain
./gradlew.bat :common:check :common-api-processor:test :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build guardLint --offline --console=plain
./gradlew.bat :common:test --tests '*ServerPackCacheTest' --tests '*PackSyncClientTest' --offline --console=plain
```

安装/证明/全IDE具体命令与脚本副本位于归档 helpers，完整 configs/logs/XML/output 位于 [原始证据包](python-member-evidence.zip)。归档3527项、8521889字节、SHA256 `53aec1ed8d02d172c3e4d8d62c4256b392fc8e85e086eaa301e6f9f799a43699`，已固定不重写。精确新JAR SHA、正常退出及逐项索引见 [manifest](manifest.json)，失败矩阵见 [测试摘要](python-member-final-test-summary.json)，完整IDE见 [诊断摘要](python-member-ide-summary.json)。性能历史见 [精确085验收](../ticket37-0856-performance/README.md)，不能认证本补丁。
