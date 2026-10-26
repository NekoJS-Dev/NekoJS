# ConstantDesc 标量合同与新安装严格声明复核

2026-10-26，产品提交3d2cc1cb，stonecutter。Java/Python共享IR、脚本执行行为、权限、数据及wire合同未改；这是票37的声明工程阶段，完整严格TS和最终候选验收仍开放。

## 改动与来源

真实Graal消费证明ConstantDesc的Double/Integer/String结果表现为number/number/string，标量没有resolveConstantDesc；ClassDesc仍有该host方法。数字/字符串输入、混合数组及构造器局部泛型varargs可用，boolean被拒绝。旧声明既拒绝合法输入，也将标量当作Java接口；公开backend消费者先产生五项describeConstable继承错误、两项合法输入拒绝和一项错误成员访问被放行的红证。

生成器保留原Java符号到最终渲染边界，依据固定JDK装箱类的sealed接口祖先元数据补标量/host联合。输入先选择显式adapter；继承顶部保持host，泛型实参为值位置；显式覆写使用独立raw-value视图，保留host、数组和嵌套SAM合同并避免泄漏。联合元素的数组/varargs保持括号。没有新增依赖、配置、运行时线程或插件资源。

初版提前展开ApiTypeRef union，三个公开消费负例抓到了wildcard集合、SAM结果和嵌套SAM结果的adapter优先权回退；golden核对又抓到varargs分组错误，显式array(union)另有两条TS2322红证。原文、中间矩阵和最后收口结果都在归档；中间通过不能覆盖这些失败。当前normal/edited/adapted三变体均strict、skipLibCheck=false，正负消费包括真实host、原始覆写、错误元素/返回和不泄漏检查。

## 收口验证

- 完整common/check（含隔离）、处理器、guard及五节点build同一次Gradle调用通过，9m05s；919 suites / 5193 tests / 271 skipped / 0 failures/errors。每个Test任务使用独立临时目录，未并发启动第二个Gradle。
- npm run test:probe-types完整链退出0，四项CI gate通过。只读规范/规格复审没有新的确证项；旧Java host结构类型的名义身份限制保持开放。
- 官方LegacyProbeTreeTest再生两个golden文件八处值类型；独立逐行比较确认其余行、成员、别名名称/元数、顺序和host继承保持。原字节、核对脚本和报告保留，未手改golden。
- 新五节点均由当前JAR全新安装，RCON生成默认声明，exit0、stopSent=true、未kill、世界锁释放。26.2 Fabric启动器出现Maven读取超时后原进程继续完成；原日志保留，未另起实例，不把这轮启动耗时当性能结果。
- 四个改动源码族共12个class在五个实际安装JAR中逐字节匹配编译结果，记录源码Git字节与class/JAR摘要；范围是这些族，不冒称所有class都逐一核对。JAR本体保留于本地安装目录。

| 节点 | server | startup | 生成文件数 |
|---|---:|---:|---:|
| 1.21.1 |64|65|394|
| 26.1.2 |116|117|448|
| 26.2.0 |110|111|430|
| 26.1.2-fabric |114|115|540|
| 26.2.0-fabric |108|109|519|

十入口全部有效执行、exit2：总数1079→1029。逐入口各移除5条，原文及限定缺失名称同源差分均为−50/+0；F1诊断50→0，其他族逐入口计数完全一致，零UNMAPPED。完整严格TS仍FAIL，F1还缺or/部分reduce/Collector collect/toList的完整公共捕获视图合同，不能因诊断清零而关闭。

## 复算和剩余范围

```powershell
python -X utf8 docs/architecture-refactor/evidence/2026-10-26-constable-values-3d2cc1cb/verify.py
python -X utf8 docs/architecture-refactor/evidence/2026-10-26-constable-values-3d2cc1cb/verify.py --typecheck
```

第一条重算归档/hash、919份矩阵XML、十入口原文与同源差分、族计数及五次运行摘要。第二条要求固定TypeScript5.8.3，重建normal/edited/adapted冻结输入并实际检查三次退出0；本轮已执行通过，产物保留于build/constable-value-replay-*。归档保留全部诊断原文、必要声明源码、消费者、检查和服务端日志、原始golden及源码/class绑定，不带RCON密码、完整进程命令、配置或世界。

安装重算依次使用bench/tsbaseline/run.py的prepare（revision3d2cc1cb/base-port39000）、run、check和classify_diagnostics.py，实际参数/输出已记录。对照基线为1a1660b6的1079条，而不是历史1609；分类器没有静默兜底。

正式性能仍仅有旧候选各自范围的证据，不外推本次；新候选的Python默认安装、客户端/TCP、分离回退、包装和真实维护者结论未完成。本轮没有push、PR、发布或代签。持续计划见[捕获合同进展](../../../research/2026-10-26-capture-protocol-progress.md)。
