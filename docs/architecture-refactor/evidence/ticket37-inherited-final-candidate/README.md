# 继承声明修复：第三候选，尚未接受

本包固定基线 `e48ea68cafbde0007ac7271d99615c146199853b` 加生产源码补丁 `03ccdf46073db0263c5c91d0b6e762adfe0787929f2489d42c464134bd2f743c`。候选名称中的 final 仅表示本轮审查后的构建，**不表示整体验收完成**。源码、制品、测试及实机输出不能套用前两个候选的 SHA。

本轮修复继承方法的泛型身份、边界和依赖，保留普通宿主容器的类型参数；保留真实 setter、静态重载和 Bean 读取/字段写入的不同类型。Python 同名静态/实例成员使用私有 callable Protocol 与 metaclass 表达。复审另外发现并修复跨包 helper 与消费者公开类型重名，以及字段投影忽略既有 getter override 两个问题；两条回归真实 RED 后 GREEN。没有修改 HostAccess/ClassFilter、运行时所有权、manual 生产器或网络协议。

Java SAM 的 `apply/call` 能与 JavaScript Function 的成员结构相匹配，导致错误返回类型绕过回调分支。生成的宿主分支现在排除 Function 的构造器符号，合法回调与真实 Java 宿主实例仍通过。实际 Graal 检查沿用生产安全设置及 ECMAScript latest。最初 ES5 测试环境中的 Symbol 失败、修正后的检查和严格 caller 的负面返回用例均保留。

| 检查 | 结果 |
|---|---|
| 串行五节点矩阵、common 隔离、processor、guard | PASS：800 XML，4708 测试，271 跳过，零失败；106 任务，2m9s |
| 两个 npm Probe 项目、严格正/负 caller | PASS |
| 平台门禁 | PASS |
| 五份精确 JAR 的注册、生成、放置和重载 | 可用子集 PASS；1.21.1 仍有已记录的客户端反射遗漏，整体 PARTIAL |
| 两个 26.x NeoForge 的效果施加、重载后重新施加 | PASS；Fabric 只验证其支持的子集 |
| 实机 Python AST / TS 解析 | PASS：1600 / 1675 文件，零语法诊断 |
| 全部默认编辑器范围严格 TSC | FAIL：所有节点退出 2；只设置 skipLibCheck=false/noEmit，没有缩小 includes |
| 全部实机 Python stub 的项目 Pyright | FAIL：所有节点退出 1 |
| 新源码正式启动/重载性能、客户端/视觉 | NOT RUN；旧性能和 native 客户端失败仍有效 |
| 当前 golden 的真实维护者接受结论 | NOT RECORDED；以前接受的 20 份 golden 不覆盖本轮 |

| 节点 | TSC SERVER / STARTUP | Pyright 错误 |
|---|---:|---:|
| 1.21.1 | 776 / 777 | 21 |
| 26.1.2 | 950 / 951 | 18 |
| 26.2.0 | 831 / 832 | 18 |
| 26.1.2-fabric | 1307 / 1308 | 23 |
| 26.2.0-fabric | 1047 / 1048 | 23 |

相较 f469 候选，SERVER 每节点减少 444/499/317/475/283 条诊断，同时新增 11/11/12/16/18 条诊断身份。前三节点 Python 各减少一条 TagEntry 静态/实例重名诊断，无新增；两个 Fabric 不变。生成文件集合与 f469 相同，没有删文件。不能由净下降推断接受：新增诊断包括构造器导入暴露的未收集包/类型、primitive Comparable 约束、enum/recipe 结构不满足约束，以及 ServerPlayer.level 的返回类型沿 ServerLevel.noSave 字段/方法冲突传播。原始详情见 comparison-f469.json；另保留与 7e98 和首个失败候选的完整比较。

本轮 canonical regenerate 改变 11 个 golden 包文件，其中新增 Annotation 包；现有公开类型名称保留。7 个新类型、36 条旧成员签名差异和 84 条成员增加均有 AST 审计，另外记录 52 条 alias 变化与逐文件 import 变化。签名/alias 的泛型约束和回调宿主分支变化是有意修复，不是只删未用 import，也未获以前的基线批准。详见 golden-audit.json。

两个独立复审的结论保存在 REVIEW.md。Python 跨包生成 fixture 按当前项目 basic 模式检查 5 份文件，零诊断；额外 strict 模式仍有 10 条未使用导入/跨包私有 helper 诊断。第一次额外 harness 错用了绝对 include，被 Pyright 忽略而检查到辅助脚本；该运行及配置保留，但不是产品检查。错误的 TS 配置路径尝试也保留，正确路径的严格 caller 已通过。没有消除或忽略这些失败记录。

复现命令：

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
node node_modules/typescript/bin/tsc -p build/ticket37-config-sync-repair/inherited-bean-tsconfig.json --pretty false
```

实机脚本、实际 compiler 配置、stdout/stderr、完整生成树、XML、源码及基线文件均在 inherited-final-candidate.zip。ZIP：3766 条目、9421849 字节；SHA-256 `2b04ba11729727171df6d8de17f080b8e6761bea7dc84bb7b3f27302bd2f37b1`；矩阵 XML ZIP SHA-256 `cc61f879fdda9b8c4d8e8b6cb6fea676491f576707546fb3a9d6eae08fe29c34`。制品逐项 SHA 见 manifest.json。压缩包已逐项读取核验，并扫描实际专属 RCON 密码；不含凭据/server.properties、world、账户、库/JAR 或原始 JFR。

五个服务器 PID 23492/24188/8136/24264/11340 全部由 RCON 正常关闭并退出 0，十个专属端口已核验空闲。没有强停用户进程。本轮证据收集进程结束，自动续跑保持暂停。票据 15/16/22/28/34/37 整体验收、性能、其余客户端窗口和真实维护者结论仍未完成；没有发布授权。
