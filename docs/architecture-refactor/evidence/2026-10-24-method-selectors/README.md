# GraalMC 显式方法选择器原型

本项只验证一种声明表达方案，没有修改生产源码或默认生成声明。当前生产候选仍为6e43b827，完整严格声明5537条FAIL、正式重载288.2/299.4ms FAIL，发布与维护者结论保持未完成。最近实际功能验收见[统一状态](../2026-10-24-owner-stamp-acceptance/README.md)。

## 实际观察

主代理先用未应用Mixin的Fabric 26.1.2类、GraalMC Fabric包8762963、生产HostAccess运行独立JVM，然后在新的NeoForge 26.1.2.71专用服务器中，以原候选JAR和GraalMC包8762962运行隔离辅助模组及普通server脚本。整个NekoJS JAR SHA256为7b0dd1544837cf86ba35ccc4817da40cde54ff7c07544fc3b25fd5437027e8ae；加载时的HostAccess/成员映射类CodeSource与class摘要同时记录，与[原候选归档](../2026-10-24-owner-stamp-performance/README.md)绑定。辅助模组仅提供测试值和来源观察，没有修改生产JAR或成员映射规则。

| 观察 | 实际安装的服务端结果 |
|---|---|
| 同名数字字段、普通方法、显式`["x()"]()`、提取显式方法再调用 | 分别3、4、4、4；真实Vec3字段与显式方法均为7 |
| JOML Matrix2d | 字段/显式getter为1，显式double setter返回自身，字段和提取getter随后均为3 |
| int/double重载 | 分别返回`int:2`、`double:2.0`；当前Number映射使int选择器输入2.5变为2，没有声称整数范围校验 |
| 数组、嵌套类、泛型擦除、varargs | `int[]`/`java.lang.String[]`、二进制`Outer$Inner`、`java.util.List`、数组拼写的varargs成功；点分内部类、泛型实参、`...`选择器失败 |
| `@HideFromJS` | 普通与显式调用均失败 |
| `@Remap("renamed") original(int)` | 普通`renamed(2)`成功，普通`original(2)`失败；选择器恰好相反：`["original(int)"](2)`成功，`["renamed(int)"](2)`失败 |

修正后的第二次安装服务器记录28项观察（21成功、7个预期拒绝），明确记录stop请求/返回UTC时间、RCON响应“Stopping the server”、关闭标记和exit0。首次控制器会无条件把normalRconStop记为true，因此首次停止主张标为UNCONFIRMED，不计入已确认正常停止数；原ZIP及原复核收据作为initial-stop-record嵌套保留，不删除观察。原始stdout有非UTF8字节，观察行均为ASCII，复核仅在文本读取时容错解码，归档字节不变。原始stderr仍保留JDK警告，以及Windows上Netty kqueue/epoll初始化异常触发的DebugFile appender错误；本项不宣称日志无错误或整体模组验收全部通过。两条生产类加载绑定证明本次调用用的是候选JAR，但没有代替完整五节点类来源验收。

独立JVM另记录36项观察，engineVersion为25.1.3。即使注册了生产MemberRemapper，未应用Mixin的独立JVM仍允许隐藏成员、没有应用重命名。因此不能用独立JVM证明安装环境的hide/remap约束；两组不同观察都保留。拒绝Java.type的独立JVM中，已绑定对象的方法选择仍成功；这只涉及已有对象的签名选择，没有扩大类查找权限。

## TypeScript与精确范围

手写最小消费原型在strict=true、skipLibCheck=false、types=[]下运行：原字段与同名方法声明产生2条TS2300、exit2；改为数字字段和独立字面量方法名后exit0，提取调用/重载返回值通过，字段调用、字符串setter、隐藏成员和错误重命名选择器由`@ts-expect-error`验证。第一次准备误指定不存在的Node类型包，产生TS2688；保留失败日志，后续使用与既有独立消费检查相同的types=[]配置。它没有Node导入，没有改项目门禁；这不是公开backend或完整默认输出通过。

重新读取6e的五份server诊断和原声明，得到1002个TS2300位置、436个节点/类/成员、31类96个跨节点成员（JOML78、Minecraft18），无残余重复import。结构化清单同时保存每条当前声明、行列和原文件摘要；方案与完整96项起点见[待定迁移方案](../../../research/2026-10-24-explicit-selector-migration.md)。继承闭包尚未收敛，未请求新的迁移批准或实施，其他成员、运行时普通调用、Bean、Python和Java合同均没有变更。

## 复核与限制

`python docs/architecture-refactor/evidence/2026-10-24-method-selectors/verify.py`检查当前evidence-v2.zip、原候选JAR及1642个生产class计数、两条加载class绑定、全部28项安装观察、明确停止响应、独立JVM反例、严格原型和96项清单；还将36个清单来源文件与既有完整归档的真实字节/摘要逐一核对。本机还核对三个实际安装JAR；缺少本地目录时该项计数为0，不伪称重新启动游戏。维护者验收为false。

当前归档35项、82,167字节，SHA256为57737df28bbcaf6643fa63c414d4656de4b54a43cf03ec25740c6727ab9a94d6。实际本地隐私扫描72个顶层/嵌套载荷，UTF8/UTF16凭据匹配0、替换0；公开工具仅绑定收据，不能在没有私密模式的环境重复这一扫描。不归档properties、world、完整启动参数或真实凭据。原型控制器和辅助源码随归档保留；原候选大JAR复用已有归档，没有重新打包。

最初封存准备中的候选归档文件名写错，在本地保留初次ZIP后，生成当前使用实际evidence.zip路径的新ZIP；没有重跑或删除任何观察。随后因停止收据审阅发现生成evidence-v2.zip，包含第一次正确绑定但停止未证实的完整ZIP。本页与verify.py只以新运行确认正常停止。

Luna xhigh只读调查了现有边界及[官方语法](https://www.graalvm.org/jdk22/reference-manual/js/JavaInteroperability/)，未改文件或运行测试；上述编译、实际服务端、消费检查与封存由主代理完成。未实测其他四个安装节点、继承中字段/方法冲突或所有96个成员，也未实施删除、迁移、推送、PR或正式发布。
