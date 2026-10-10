# 继承与关闭中断候选：完整 IDE 仍失败

本包固定基线 `e48ea68cafbde0007ac7271d99615c146199853b` 加生产补丁 `ec765e58d7edf840f7097c51a20a8f31245873edf701ee4f78e1e168ffedc9c6`，共 12 个生产文件。旧候选的源码、制品 SHA 和通过记录不用于认证本包。状态 **NOT ACCEPTED**。

在第三候选基础上，声明补齐接口要求的 Object 方法、两个真实 get/is getter 方法，以及 self-Comparable 的已有 boxed primitive 投影；非 self 约束和其他交叉约束保持。实际 Graal Integer/Boolean/String 回调与严格正负 caller 通过。Parent/Child 的 Object 重载遗漏有真实 RED 后 GREEN；没有无条件开放全部 Object 方法。

完整矩阵首先暴露关闭测试没有观察候选已开始执行的问题：受控线程启动顺序复现 PREPARATION / REJECTED_CLOSED。测试增加真实 guest 首次 spin 的前置观察后，两项实际关闭中断又暴露运行时归因错误。ScriptManager 仅在关闭标志与真实 Graal isInterrupted 原因链同时存在时报告 close-preempted，保留原始模块包装错误、来源、候选丢弃和资源所有权。普通错误仍为 script-execution，watchdog 仍为 candidate-killed；原拒绝提交和拒绝启动后续脚本的断言保留。新增测试误以为顶层错误就是 PolyglotException 的两次失败也保留；真实异常由 NekoModuleError 包装，测试已检查原因链。运行时修复应与 Probe 修复分开交付。

| 检查 | 结果 |
|---|---|
| 最新完整五节点矩阵、common 隔离、processor、guard | PASS：800 XML，4714 测试，271 跳过，零失败；106 任务，3m14s |
| 关闭/继承相关完整测试类 | PASS：34 测试；此前所有 RED 报告保留 |
| 两个 npm 项目、严格正负 caller、平台门禁 | PASS |
| 五份精确 JAR 的注册、生成、放置、重载 | 支持子集 PASS；旧版客户端类型反射遗漏仍 PARTIAL |
| 两个 26.x NeoForge 效果施加与重载后再施加 | PASS |
| 实机 Python AST / TypeScript 解析 | PASS：1600 / 1675 文件，零语法错误 |
| 全部默认范围严格 TSC / 项目 Pyright | FAIL：分别退出 2 / 1；没有缩小 includes 或抑制错误 |
| 最新源码正式性能、客户端/视觉 | NOT RUN；旧失败仍有效 |
| 真实维护者结论 | NOT RECORDED；旧 golden 批准不覆盖本轮 |

| 节点 | TSC SERVER / STARTUP | Pyright 错误 | 对第三候选 SERVER 减少 / 新增身份 |
|---|---:|---:|---:|
| 1.21.1 | 774 / 775 | 21 | 14 / 12 |
| 26.1.2 | 1413 / 1414 | 18 | 15 / 478 |
| 26.2.0 | 922 / 923 | 18 | 10 / 101 |
| 26.1.2-fabric | 2554 / 2555 | 23 | 17 / 1264 |
| 26.2.0-fabric | 1305 / 1306 | 23 | 14 / 272 |

Python 诊断身份相对第三候选无增减，生成文件集合也未删除。接口沿父类查找的实现同时把所有祖先接口成员加入当前类，可能导致普通父类成员重复展开及越过当前泛型/有界收集语境；这仍待最小回归验证，不能由局部 caller 通过或净诊断下降推断接受。字段/方法同名控制另证实直接读取 noSave 是 boolean，成员调用可执行方法，而脱离对象后的读取不能调用；未用 callable intersection、Any 或删除真实成员掩盖差异。

canonical golden 通过既有 regenerate 流程产生，逐类型成员、alias、import 和现有名称保留审计见 golden-audit.json。它包含真实声明变化，不只是旧 20 份基线的未使用 import 变动。两个独立轴结论见 REVIEW.md；它们仅为只读源码审查，不能认证完整 IDE、维护者或发布。

复现：

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
node node_modules/typescript/bin/tsc -p build/ticket37-config-sync-repair/inherited-self-comparable-boundaries-tsconfig.json --pretty false
```

inherited-boundary-candidate.zip：3839 条目，11288991 字节，SHA-256 `595da900b756a403cbac9828469b6f9588c1de6c1e1dcb0290ec0d02bbfc1b9b`。矩阵 XML ZIP SHA-256 `f15e293f1d7eb40d8e85daf45a98d440729f352a922af40f158263e887e8d4d0`。完整原始失败/成功日志、配置、实际生成树、源码、基线、XML 和控制均保留；制品 SHA 见 manifest.json。压缩包逐项读回与实际专属 RCON 密码扫描通过，不含凭据、world、账户、库/JAR 或原始 JFR。

五个服务器 PID 26644/28604/19724/22836/8876 全部正常 RCON 关闭并直接观察退出 0，十个专属端口空闲。无用户进程被停止，自动续跑保持暂停。整体票据、性能、文件系统间歇失败、native 客户端失败、剩余领域/窗口和发布回滚仍未完成；没有发布授权。
