# 合成接口实现的声明保留

源码 `459c0cd7c498d8959bd6e4d8cf808793aab3fd2f`。主代理实现、运行和提交；Luna xhigh 仅只读调查与两轴审查。没有 PR、push、正式发布或人工签署。

TypeReflector 现在保留类自身公开、非 static、非 bridge 的 synthetic 方法，前提是实际接口存在同名、同原始参数及返回类型的公开实例契约。原 Method 继续负责 Hide/remap 和签名来源；普通 synthetic、所有 bridge、接口和枚举路径保持原有过滤。没有扩大运行时权限或修改 wire、持久数据。

两次隔离 Fabric 26.1.2 观察确认：Mixin 生成的 ContextChain.getModifiers 是 public synthetic nonbridge，原始返回 List，Graal 可调用且结果 size=0。GameRule.fabric_enumCycle 则是 Enum→Enum、无方法变量，原接口保留 `<E extends Enum<E>>(E)=>E`；目标方法有 MixinMerged 注解。**本批没有恢复后者的泛型关系。**合并来源标记本身不足以证明源方法原有泛型签名，普通 raw MethodHost 仍保持 raw。

先红后绿用例验证真实 Graal 调用与 Bean 读取、正式 Index 生成、严格 TS 正负例，以及 Hide/remap、无关 synthetic 和 bridge 过滤。最初的 Bean 选项、嵌套类加载和资源名夹具失败分别保留；真正的功能红测是一项缺成员断言失败、四条严格 TS 诊断。修复后 Java 定向检查和 TS 全链通过。实际 raw ContextChain 只生成 List<any>，没有猜测与目标类 S 的关系。

| 检查 | 结果 |
|---|---|
| common/check、处理器、guard、五节点 build/platform/artifact | PASS；5141 tests / 271 skipped / 零失败错误；guard 零警告 |
| npm run test:probe-types | PASS；新消费夹具已接入 |
| 五节点新安装 JAR | 全部实际行为 PASS；五进程 RCON 正常退出 0；无强制清理 |
| 新安装默认 Python | 1609 stubs；Pyright 1.1.414 零错误警告 |
| 十入口完整严格 TSC | **FAIL，5575 条**；相对 88e18d84 移除44 / 新增0；无编译器崩溃 |
| 两轴源码审查 | Standards 0 硬性违反 / 0 剩余气味；Spec 0；严格 TS 接线疑问已复核消除 |
| wiki 严格文档检查 | 仍有其他五主题的10条旧 digest/review 诊断；本批未代盖章 |
| 正式重载性能 | 本源码未测；最近正式 7bea6d6d 为340.0 / 292.8 ms，仍超285.3 ms门槛 |

新安装逐个核对全部 common/node 编译类与实际 JAR 字节；其 SHA、来源 patch、脚本、完整新声明/stub、原始诊断及 XML 随归档保留。包含启动注册、嵌套 fingerprint、实际方块/流体、重载及适用节点的交易/效果生命周期。Fabric261 新安装再次验证 accessor 可调用。先前其他源码的客户端、TCP、回退证据保持各自绑定，不据本批服务端成功称为最终统一候选验收。

复现：`gradlew.bat -g D:/mcmodDemo/NekoJS/.gradle-perf02 -Dorg.gradle.java.home="C:/Program Files/Java/jdk-25.0.2" :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --offline --no-parallel --console=plain`；再运行 `npm run test:probe-types`。安装与 IDE 工具随归档保留。

离线复核：`python docs/architecture-refactor/evidence/2026-10-23-synthetic-contract/verify.py`。一致性 PASS 不表示完整 TSC 或性能通过。归档不包含凭据、world、JAR、运行库或启动参数文件；匹配到的 RCON 密码字节已脱敏。票37继续 in-progress，票28已工程关闭。
