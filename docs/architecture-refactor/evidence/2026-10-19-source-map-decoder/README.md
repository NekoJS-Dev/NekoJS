# Source-map 解码优化与原标准复验

**结论：解码器分配优化通过，整体性能仍 FAIL。** 主代理实现、验证、归档及提交；Luna 仅作限定范围的只读调查。未创建 PR。

## 变更与契约

实现／实测提交为 `606d1fff6b14e4a513bf16e247b0ec37437195c6`，基于 `4550a385` 的专用性能 checkout。主线对应提交为 `280128e105380c4cdc789aa09ee0c8ca6bab0960`；两个提交的生产改动和测试相同，主线额外包含此前诊断文档。

只改 `SourceMapRegistry` 私有 mapping/VLQ 解码：单次扫描原字符串，复用五个 primitive 字段槽，避免逐行／逐段切字符串和临时 `List<Integer>`。全部字符仍被校验，额外字段不会绕过非法字符检查；非法段不应用部分增量，保留既有不完整尾字段容错、跨行 source/name 增量和空行行为。

无 Script API、wire、持久数据、信任、schema、路径规范化或 Context／代际所有权变更；不增加跨代缓存、依赖或 Windows 策略。

## 组件反馈

固定六份原 fixture 的 identity map，100 次预热，30 对轮换顺序；13 个边界输入、10,000 个固定种子随机输入和六个真实 map，共 **10,019 个结果与冻结旧实现一致**。

| 同进程控制 | 旧臂平均分配 | 对照臂平均分配 | 预设分配信号 |
| --- | ---: | ---: | --- |
| 旧／旧 `baseline-v2` | 2,051,936 B 左右 | 2,051,936.8 B | FAIL：没有下降 20% |
| 旧／新 `candidate` | 2,051,940 B | 490,385.6 B | PASS：下降约 76.1% |

“下降 20%”是组件诊断信号，**不是用户的重载门槛**。旧／新同进程墙钟均值约 2.517 / 1.269 ms；不将不同会话的旧／旧、旧／新计时相减。CPU 计数分辨率有限；这里没有完整 Minecraft，不能推断真实重载必然节省同样时间。

首次 `baseline` 控制准备因 classpath 规范化断言失败，尚未运行 Java 或产生样本；修正 runner 后另建 `baseline-v2`，没有覆盖旧输出。完整旧／旧 FAIL、旧／新 PASS 和工具均保留。前三项新增语义回归在旧源码也通过；第四项及全部十项在候选完整验证通过，不将功能单测描述成性能红绿。

## 完整验证

```text
gradlew.bat --offline :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
python tools/nekojs-ci-gates.py all --out <output>/ci-gates.json
```

第一条 PASS，10m43s，106 tasks（59 executed / 47 up-to-date），包含 common isolation 和各节点 platform gate。共 **4,989 tests、0 failures/errors、271 skipped**；各 XML 原样冻结。common 使用已配置 Java 21 测试 toolchain；正式游戏使用 JDK 25.0.2，不将两者混记。四个 CI 报告一致性／声明／节点门禁 PASS；没有据此声称重新执行所有 smoke 或实机验收。

本阶段未另跑 GameTest、NBT smoke、TypeScript 编译或包客户端验收；没有修改对应声明 golden／生成文件。跳过项与先前实机接受缺口仍保留。

## 原标准正式成绩

原采样器 SHA-256：`1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`。相同六脚本负载、NeoForge 26.1.2、JDK 25.0.2、固定 Gradle cache 和 25871/25872；无外部 QoS controller，未改电源策略。运行前 clean checkout，采样前后实际编译类／fixture 相同，并与冻结 JAR 一致。期间不并行本任务构建、测试或其他游戏。

预先指定两组各五次重载，启动两次预热加五次正式。保留全部 17 行与首值，不择优重跑。全部样本有效，十次重载均 COMMIT；九个游戏会话正常 RCON 停服、无 forced kill，Gradle 全部正常完成。

| 组别 | 全部正式值（ms） | 均值（ms） | 原门槛（ms） | 结论 |
| --- | --- | ---: | ---: | --- |
| reload-1 | 746 / 589 / 528 / 587 / 556 | **601.2** | 285.3 | FAIL |
| reload-2 | 770 / 432 / 545 / 458 / 432 | **527.4** | 285.3 | FAIL |
| startup | 61088 / 59749 / 59443 / 60453 / 61001 | **60346.8** | 41173 | FAIL |

启动预热为 60229 / 62888 ms，原样保留但不纳入正式均值。启动口径是原 `runServer` 源码运行，包含 Gradle 准备，不冒充安装 JAR 的独立启动计时。判定程序按 FAIL 退出是预期结果，不能修改门槛或忽略首值使其通过。

本次成绩比此前主线会话更慢；没有同会话旧／新完整游戏对照，因此不能确定跨会话差值归因。组件收益可信，但**不能称整体回归已修复**。下一步单独做启动请求／task／游戏创建／Done 边界与 JFR 诊断，不重判本表。

## 独立复核

```text
python docs/architecture-refactor/evidence/2026-10-19-source-map-decoder/verify.py
```

复核 PASS：归档 956 项、19,021,822 bytes，SHA-256 `c69845cf9f6aaaabc707a08c19c72dfc4b6334190f75cf6a668ea85efb56830e`。验证器检查逐项摘要、冻结 JAR／编译类、源码绑定、原 sampler／fixture、30 对轮换分配控制、XML 总数、四 CI gate、17 条正式／预热行及原门槛判定。

`evidence.zip` 包含实际 NeoForge JAR、源码／patch、组件工具和原始输出、完整测试 XML、正式日志／环境快照和采样工具。RCON 凭据替换后另记原文件摘要；不含完整 classpath、server.properties、world NBT 或私钥。归档通过只代表证据自洽，不代表正式性能通过或真人维护者已接受。
