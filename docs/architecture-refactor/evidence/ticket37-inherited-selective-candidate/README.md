# 选择性继承候选：完整 IDE 与嵌套 getter 顺序仍失败

本包固定基线 `e48ea68cafbde0007ac7271d99615c146199853b` 加 12 个生产文件补丁 `2d0b1886581191cb21fab4be73f8ec8690b144ad471cdf98498e0de8ee4e3a7f`。状态 **NOT ACCEPTED**。后续源码修改不得套用本包制品或测试 SHA。

相对 ec765，普通具体父类接口实现保留在父类声明，通过 extends 继承；当前类仍声明自身接口要求、真实默认方法、同名重载与祖先接口明确要求的 Object 方法。最小真实 guest 调用通过而重复声明失败的控制有 RED→GREEN。覆盖 getter 时保留真实继承 setter 的 String 参数；属性写入、显式调用和严格正负 caller 均验证。没有删除 manual、放宽 HostAccess/ClassFilter 或移除实际公开成员。

| 检查 | 结果 |
|---|---|
| 完整五节点/common 隔离/processor/guard | PASS：800 XML、4717 测试、271 跳过、零失败；106 任务，2m3s，直接退出 0 |
| 三个相关完整测试类 | PASS：43 测试；setter 缺失的真实 RED、生成输出和 XML 保留 |
| canonical regenerate、两个 npm 项目、平台门禁、严格 TS caller | PASS |
| 控制 Python 正/负 caller | 正向 0 错误、1 stub 来源警告；负向 1 预期类型错误、同一警告；不声称零诊断 |
| 五个精确新 JAR 注册/生成/放置/重载 | 支持子集 PASS；旧版 8 类反射遗漏仍 PARTIAL |
| 两个 26.x NeoForge 效果施加及重载后再施加 | PASS |
| 全部实机 Python AST / TS 解析 | PASS：1600 / 1675 文件、零语法错误 |
| 完整默认 editor includes 严格 TSC / 项目 Pyright | FAIL，退出分别 2 / 1；不缩小范围、不忽略错误 |
| 复审的嵌套 getter 选择顺序 | FAIL：真实 selector 控制已复现，后续修复另验 |
| 最新源码正式性能、客户端与视觉 | NOT RUN；历史失败保留 |
| 真实维护者结论 | NOT RECORDED；旧 20 份 event golden 批准不覆盖本轮 |

| 节点 | TSC SERVER / STARTUP | Pyright 错误 | 对 ec765 SERVER 减少 / 新增身份 |
|---|---:|---:|---:|
| 1.21.1 | 768 / 769 | 21 | 8 / 2 |
| 26.1.2 | 942 / 943 | 18 | 473 / 2 |
| 26.2.0 | 827 / 828 | 18 | 96 / 1 |
| 26.1.2-fabric | 1306 / 1307 | 23 | 1252 / 4 |
| 26.2.0-fabric | 1046 / 1047 | 23 | 260 / 1 |

Python 诊断身份相对 ec765 无增减；两个语言的生成路径无删除。其他固定基线的逐文件/代码/消息身份比较也保留。净下降不等于通过，新增身份继续诊断。11 份 canonical golden 的类型名称保留、成员、约束、alias 和 import 差异见 golden-audit.json；并非仅旧 import 清理。

Standards 复审发现未排序 getMethods 与名称扩展相互作用。真实 Parent 提供 getValue/setValue/getGetValue，Child 提供 getGetGetValue，生产 selector 选中三个 getter 却遗漏 setValue。原 Java 写入通过，独立控制退出 1；原始源码和日志保留在 ZIP。此缺陷不得由当前矩阵或局部 setter caller 通过掩盖。

两个 Fabric 初次启动等待安装器 HTTP 下载，专属 jcmd 线程快照保留；后来都到就绪并完成检查。五个 PID 11068/29760/18536/12948/15264 均 RCON 正常关闭、直接退出 0，十个端口确认空闲；没有停止用户进程。

复现：

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
node node_modules/typescript/bin/tsc -p build/ticket37-config-sync-repair/inherited-self-comparable-boundaries-tsconfig.json --pretty false
```

inherited-selective-candidate.zip：3908 条目、12277998 字节，SHA-256 `aa954e383bb1a78c1dfe16fd3435c959d56ef69017d069e64f340c9597b7d267`。完整 XML ZIP SHA `b65a5e7fd957cc4ad98565004862c3400086f34c565ef0ee3e2bcdb989032407`。压缩包逐项读回、SHA 与实际专属 RCON 密码扫描通过。源码/基线、所有失败和成功控制、日志、真实生成树、配置和制品 SHA 保留；不包含密码、world、账户、库/JAR 或敏感原始 JFR。

运行时关闭中断修复仍包含在精确补丁中，但应与 Probe 分开交付。整体票据、性能、间歇文件系统失败、native 客户端崩溃/首帧状态、其他领域及窗口、跨节点视觉、维护者和发布回滚仍未完成；自动续跑暂停，没有发布授权。
