# 8922627e installed validation and SAM linkage recovery

基线为 `8922627e75f903fb8efbbbf7ceb89dd51b1f50d0`。本轮补齐社区实现后的完整五节点构建与真实安装验收，并修复其新引入的 legacy TypeScript 整体生成失败。旧 cb5 的性能和 IDE 记录保持历史身份，不认证这份新源码。

## 修改与契约

真实 1.21.1 专用服务器的 `nekojs probe typescript` 在 `FunctionalInterfaceAliasGenerator.prepare → FunctionalInterfaceResolver.singleAbstractMethod → Class.getMethods` 失败：接口可以加载，但加载器拒绝方法签名中的客户端类。旧的共享收集/IR 已按 NEKO-4029 报告这些不可用类型；新 SAM 扫描重新触碰它们，导致整个 backend 中止。

在 SAM 别名生成的逐接口边界恢复既有 RuntimeException/LinkageError 部分生成策略，并以 NEKO-4029 明确记录别名省略。签名依赖完整收集后才注册别名，避免半成品。OutOfMemoryError 等虚拟机错误仍向上传播。原 missing/rejected dependency class-loader fixture 增加接口形态和真实 coordinator 回归，两个检查 RED 失败后 GREEN；新增 fatal SAM 检查通过。没有新增公开 API、删除手动声明、改变运行期转换、放宽 HostAccess/ClassFilter 或手改生成输出；没有 golden 内容变更。

## PASS / PARTIAL

- 精确892原矩阵 PASS：106任务/1m1s，798XML、4678测试、271跳过、零失败/错误；common/processor复用同源码社区检查，五节点test任务均执行。原 npm managed/community strict fixtures PASS。
- 修复后完整矩阵 PASS：106任务/1m43s，798XML、4681测试、271跳过、零失败/错误。common检查含隔离门禁；五节点build/guardLint全部通过，没有排除Fabric测试。修复后 `npm.cmd run test:probe-types` PASS。
- 两轮各五个专属测试服均使用各自新官方JAR、新world和相同fixture；十服全部正常RCON关闭，直接观测退出0。制品SHA和fixture hash见 [manifest](manifest.json)，修复制品不能套用原892或cb5 SHA。
- 修复后五节点实际TS/Python生成、支持的注册检查、nested fingerprint、放置及重载完成。NeoForge26.x另验证FluidType身份、MobEffect重载前后实际施加；Fabric仍为支持子集。每轮1590份Python stub AST全通过（317/377/344/293/259）。
- 修复后334/391/360/307/275份TS声明只读解析，所有语法诊断为0。legacy仍明确报告不可用客户端类型，其完整类型闭包为PARTIAL，不能宣称完整覆盖。
- [独立审查](REVIEW.md)：Standards 0发现、Spec 0发现，源码审查与自动测试分开。完整证据归档逐项hash/readback及gzip/嵌套ZIP凭据扫描通过，排除RCON密码/world/账户/binaries/raw JFR。

## FAIL / BLOCKED / NOT RUN

原892 legacy TS生成 FAIL。其空TS项目随后退出0、诊断0，这是生成失败造成的空检查，明确标为BLOCKED，不能算严格TSC PASS。原始响应、堆栈和退出均保留在 [原始证据包](installed-evidence.zip)；[基线摘要](baseline-summary.json)给出此边界。

修复后实际默认editor includes、仅令skipLibCheck=false/noEmit=true的完整strictTSC仍FAIL；Pyright1.1.414显式枚举全部实际stub仍FAIL。IDE捕获脚本退出0只代表收集成功，各检查的退出码仍分别为2/1，见 [修复摘要](repair-summary.json)。其余四节点TS诊断数与原892相同；legacy恢复真实输出后才能重新评价，不能与空检查相减来声称退步。

| 节点 | 修复后严格TSC server/startup | Pyright错误 |
|---|---|---|
| 1.21.1 server | 2514 / 2515 | 79 |
| NeoForge26.1.2 | 3330 / 3331 | 88 |
| NeoForge26.2 | 2784 / 2785 | 87 |
| Fabric26.1.2 | 3230 / 3231 | 88 |
| Fabric26.2 | 2667 / 2668 | 88 |

Pyright五节点相对cb5的诊断身份移除0/新增0。本修复只恢复声明生成，没有解决其余语义错误。当前修复正式性能NOT RUN；历史cb5重载346.8ms FAIL保持。文件系统间歇AccessDenied、实际in-flight configuration/pre-ACK disconnect、fireResistant registry access、客户端第一帧/视觉、发布分类和真实维护者发布/回滚验收仍未完成。整体票据未完成，自动续跑仍PAUSED。

Git使用用户明确授权的computer-use点击现有Tki-sor账号和Continue，普通推送至stonecutter成功，远端核验为精确892；未输入/提取密码，没有增加授权范围。本轮源码修复的后续提交/推送状态另记，不能把登录成功当作其他验收通过。

## 复现

```powershell
./gradlew.bat :common:test --tests '*ProbeUnavailableTypeTest' --tests '*FunctionalInterfaceAliasGeneratorTest' --tests '*CommunityProbeTypeScriptFixtureTest' --offline --console=plain
./gradlew.bat :common:check :common-api-processor:test :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build guardLint --offline --console=plain
npm.cmd run test:probe-types
```

固定证据包6683项、13844332字节，SHA256 `523e675cf809e33b25a1b86b3ffde73ad210ccf6fc260615a0d8abb1da166890`；完整三文件补丁SHA256 `fc8fc437819493153faf6b18a0da3f07f57e2749c433dabb8f36e71e55487e4f`。制品、运行期、IDE的复现runner、两轮真实生成输出/config/日志、RED/GREEN XML、完整矩阵XML、修复前后源码字节和补丁都在固定证据包内。服务器属性和凭据不随包交付，重新实机运行必须先建立专属环境并通过既有路径/端口/进程所有权检查。
