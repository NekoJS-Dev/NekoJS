# SAM 对象输入、sealed 转换边界与完整安装复核

日期2026-10-26，产品提交1a1660b6，分支stonecutter。本次是票37的合同修复阶段，不是最终候选验收。

## 改动和可观察合同

Graal允许普通对象按真实Java SAM方法名实现非sealed接口，默认方法仍由Java提供；旧TS别名只接受回调或完整host，错误拒绝仅含apply的UnaryOperator/BinaryOperator对象。生成器复用既有callback参数/结果槽，追加带非可执行值保护的对象输入；真实@Remap的宿主名称不改变代理的Java分派名称。

额外对象分支使用globalThis.NoInfer：第一次全链检查发现Function.apply参与泛型推断，让既有三参reduce的number结果退成unknown；修复后原消费不改显式泛型即可通过。TypeScript固定5.8.3，最终参数和返回检查仍存在。

真实Graal另证sealed接口拒绝函数和对象代理，合法host可用。因此sealed别名保留名称、泛型元数、完整host，去掉不支持的旧函数输入、不增最小代理分支。完整结构对象仍可能模拟无品牌host而通过TS，这项旧名义身份限制由两轴复审共同指出，已保留为缺口；本轮不宣称类型系统能识别所有真实Java实例，不以删除合法host来规避。

四项新回归覆盖真实Graal对象输入、默认方法、重映射分派、sealed转换和公开backend生成；严格消费包含正确host/回调/对象，以及错误参数、错误结果、类值与sealed代理负例。原始红证、语法配置失误、golden变化及推断失败日志均保留，早期失败没有算作绿态。

## 正式验证

- 官方LegacyProbeTreeTest再生17个文件；独立逐行比较确认78个非sealed别名追加分支、2个sealed别名仅保留原host。成员、名称、泛型元数、其他分支及顺序保持；JDK21 sealed标志另行反射核对。生成模式不充当普通测试。
- `:common:check :common-api-processor:test guardLint`和五节点build同一Gradle调用通过，3m50s，918 suites / 5191 tests / 271 skipped / 0 failures/errors。每个Test任务使用独立临时目录；归档保留init脚本和实际路径映射，没有改测试断言或生产目录。
- `npm run test:probe-types`完整链退出0，四项CI gate通过。捕获协议另有16组冻结实验重放，含预期失败；详见[独立原型证据](../2026-10-26-capture-protocol-prototypes/README.md)，不能将它们当作生产F1通过。
- 新五节点由当前构建JAR全新安装，RCON触发默认声明生成，全部正常停服、exit0、未kill、世界锁释放。源方法生成器class在五个实际加载安装JAR中逐字节匹配编译结果；绑定范围是该class和JAR摘要，不冒称所有class都逐个比较。
- 十入口strict/skipLibCheck=false全部有效执行但exit2：总数仍1079；原文多重集和限定缺失名称同源核对均移除0/新增0。各族计数不变，零UNMAPPED。完整严格TS仍FAIL；本次恢复合法输入合同，没有宣称消除诊断。

| 节点 | server | startup | 生成文件数 |
|---|---:|---:|---:|
| 1.21.1 |69|70|394|
| 26.1.2 |121|122|448|
| 26.2.0 |115|116|430|
| 26.1.2-fabric |119|120|540|
| 26.2.0-fabric |113|114|519|

## 复算和边界

`python -X utf8 docs/architecture-refactor/evidence/2026-10-26-sam-proxy-1a1660b6/verify.py`

追加 `--typecheck` 可在build独立目录重放归档中的完整正负消费者（要求已安装固定TypeScript5.8.3）；本轮实际重放退出0，产物保留，不覆盖原输入。

验证器重算归档摘要、矩阵XML、十入口原文和同源差分、族级计数及五次运行摘要。归档包含完整诊断原文、限定同源判断所需源码、公开消费输入、检查日志、前版golden和变化核对、服务端日志及源/class绑定；不带RCON密码、世界或配置。JAR本体留在本地隔离安装，仓库只保存摘要与当时的逐字节核对结果。

复用安装命令是bench/tsbaseline/run.py的prepare（revision1a1660b6/base-port38000）、run、check，再运行classify_diagnostics.py；实际命令及原文在归档。F1仍缺or/部分reduce/Collector collect/toList的完整公共视图合同，1079条其余根因仍开放。新源码的正式性能、默认Python安装、最终客户端/TCP/分离回退与包装未重验；不外推d06cd961旧性能结果。本轮技术复审和自动检查不代替维护者人工接受，未push、PR、发布或代签。
