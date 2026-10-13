# 签名包的工作区生成保持内容不变

此前真实配置分发已复现：只有一个脚本、缺少 jsconfig 的有效预签名包，服务端启动时被 WorkspaceGenerator 新增 `client_scripts/jsconfig.json`，从而在客户端被生产验签器拒绝。原始失败和单文件集合诊断见 [下载窗口证据](../ticket37-current-download-7d/README.md)。本修复直接修改这个生成入口：签名 metadata 非空的包，以及 SERVER_CACHE 包，不再向包内生成 IDE 配置；未签名 GLOBAL/WORLD 包和根工作区仍照常生成，已有配置原字节保留。任何 pack-local IDE 配置需要在签名前纳入内容集合，生成的目录指南已说明这个例外。

这是工作区自动写入范围的有意修正；不删除公开接口/配置/文件、不更改 manifest、wire、签名 payload、包哈希、路径检查、信任、公钥绑定或 reload/runtime owner 行为。签名 metadata 这里只用作保守的“不可修改”标记，不授予信任；无效签名仍由既有验签流程拒绝。Probe backend 的编辑器贡献写根目录及 Probe 输出，不写包目录，本次无需改动。common 没有 MC/loader 依赖。

源码基点 `99e48fa30755ce226581b101cd59d4e9ffc48b17`，生产补丁 SHA256 `d0afd044e05fe8cfdfd1c635e728f04dc2f64c0d97e79e867adb3850b8c07baf`。只有 `WorkspaceGenerator.java` 一个生产文件变更；回归在现有 `WorkspaceGeneratorModifyConfigHookTest` 中添加，恢复包注册表测试全局态，覆盖签名内容/服务器缓存哈希、现有签名配置、普通本地配置和根工作区，连续生成两次保持幂等。单元 fixture 的 signature 块是保护标记，不声称它本身是有效密码学签名；下面的真实制品验收另外使用真实 Ed25519 签名。

RED 真实 JUnit 2 tests/1 failure，原始 XML 与 expected/actual 签名内容哈希冻结；修复后 workspace/preserve/managed-config/signature-verifier 四组 18 tests/0 failures。完整最终矩阵 4811 tests、271 skips、0 failures/errors、810 XML，109 tasks/3m33s，common check/isolation、API processor、guardLint、三个 NBT、五节点 build、四个平台 gates 通过。首次矩阵也通过，但编译后曾调整注释和括号，因此明确作为 intermediate 保留；最终矩阵重新绑定最终字节，不把中间检查代用。只读 jcmd 诊断报 AttachNotSupportedException/拒绝访问，没有线程栈、权限升级或停止进程；可选汇总读取一次 GBK 解码失败后改为明确 UTF-8，不是游戏/验签失败。

```powershell
./gradlew.bat :common:test --tests com.tkisor.nekojs.core.plugin.WorkspaceGeneratorModifyConfigHookTest --tests com.tkisor.nekojs.script.WorkspaceGeneratorPreserveTest --tests com.tkisor.nekojs.script.WorkspaceGeneratorManagedTypesTest --tests com.tkisor.nekojs.core.pack.sync.PackSignatureVerifierTest --console=plain
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
python tools/nekojs-ci-gates.py all --out build/ticket37-signed-workspace-repair/platform-gates.json
```

五个新官方制品在全新专属服务端实际安装：注册/嵌套 fingerprint、放置、重载、NeoForge FluidType/流体以及 26.1.2 MobEffect 前后应用均 PASS，五端 RCON 正常 stop、直接退出 0。每端都在启动前制作“一个脚本、无 jsconfig”的真实签名包；启动后仍仅一个内容文件，原始哈希不变，生产验签器仍通过，签名没有重生成。1600 个实际 Python stub 全部 AST 通过，完整默认 includes 的严格 TSC 仍 FAIL：server/startup 分别632/633、789/790、684/685、1195/1196、951/952；Pyright1.1.414 仍 FAIL7/7/7/13/13。相对旧 7d 的相对文件/错误码/首行消息多重集新增0、移除0，忽略位置与安装根路径，不比较续行解释。

| 节点 | JAR SHA256 | 匹配的正式编译类 |
| --- | --- | ---: |
| 1.21.1 | `c96a196524324f835ff21ea8cc75d53a42a273ec4ae61b4da89f544481479d89` | 1512 |
| 26.1.2 | `f9e3dc322989e4a3951aadd6d4b920aa278117525af45a4bf929b825002a5dc9` | 1592 |
| 26.2.0 | `a8a5f8bcaeabfeb92872f1553ce68011600511a35cd7af4a9994a54c99f5006c` | 1592 |
| 26.1.2-fabric | `27ddf964eb18caed8e24e873f61cca00fcbcdb4088e1679fd4daa70446a4ee8f` | 1518 |
| 26.2.0-fabric | `a16b1e77090cf771446f1846b16a2cb5a93fb214b2ac897967581ec4d46df679` | 1518 |

同一新 NeoForge26.2 JAR 还安装到全新 server/C/D 环境（另有未启动 E profile），1592 类逐字节匹配，相对旧 7d 唯一改变的类为 WorkspaceGenerator。再次在启动前签名单脚本包，未放入 jsconfig；服务端及两个客户端缓存最终都保持同一原始包哈希与一个内容文件，客户端确实验签、公钥绑定并执行远端脚本，没有重签或改生成输出。D bundle 共366bytes，前64bytes送达时没有 cached manifest/执行，第3代 AWAITING_ACK/C-onlyACK/activated2/noCOMMIT；放行剩余原帧后两份真实 ACK/activation-report、COMMITTED3、D 配置完成/登录/give PASS。三进程 owner-tick stop/RCON/直接0，没有 bridge/native activation/input/强杀/hs_err，十四个测试端口归档时空闲。该窗口只覆盖脚本 bundle，不外推原版资源包 HTTP 下载或首帧像素。

实机入口与源码冻结在 ZIP helpers 中：`accept-signed-workspace-installed.py` 等待最终矩阵、冻结 XML/gates、准备五节点、启动原 runner/proof、校验启动前后真实签名及生成 IDE 报告；`prepare/run/proxy/orchestrate-signed-workspace-client.py` 绑定新 JAR 并运行真实双端窗口。早期 `nekojs registry` 返回 Incorrect argument 的原始响应保留，不把它当 registry query 成功；注册与放置判断来自实际 fixture 日志和操作响应。无独立代理审查；自审核对差异、调用链、签名与缓存内容边界、测试全局恢复、语言、公开契约和生成输出，没有黄金变更或 gate 放宽。

不可变 `signed-workspace-evidence.zip`：4376 entries，7983702 bytes，SHA256 `848ce22a6e18ca2d102e3e75f0c2b862e682abe30e9c5e4e78ad16381b6fa8fa`。清单保存逐条目哈希；原始日志、完整实际生成内容、TSC/Pyright diagnostics、810 XML、源码/补丁/JAR/class 绑定、真实签名 fixture、退出记录齐全。独立 `python docs/architecture-refactor/evidence/ticket37-signed-workspace-repair/verify.py` PASS，除重算原始包哈希，还用 Node 内置 crypto 独立验证 Ed25519；它没有重用生产验签器。密码/server.properties/world/私钥/制品二进制/启动参数/原始 JFR/hs_err 不入库，六份实际 RCON 密码扫描通过。可选诊断命令的工具输出未另存为原始文件，不能宣称 ZIP 含完整 jcmd/GBK 工具输出。

新补丁正式性能 NOT_RUN，旧 7d 正式重载341.6/315.6ms FAIL285.3保持历史绑定；不因改动小而认证新制品性能。首帧像素、其他客户端节点/剩余领域/磁盘故障、完整 IDE/Pyright、真实维护者结论及发布/回滚仍未完成。总体 NOT_COMPLETE，维护者 NOT_RECORDED，自动续跑 PAUSED，权限字段问题 UNRESOLVED；A22/A28 与无发布授权边界保持。用户要求后续完整测试集中到任务完成前的最终验收，此轮已完成的原始检查继续保留。
