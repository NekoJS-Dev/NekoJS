# F1 完整捕获合同与 SAM 输入进展

本轮长期目标已设为 active。产品/证据基点32408e4c（产品84ae51c1），完整严格 TS 1079；F2/F4清零，F1公共合同仍开放。主代理实现与执行验证，子代理仅只读审查；本地提交，不push、不发PR、不发布。

## 隔离捕获原型，不作为生产通过

只在build下复制84ae51c1的公开backend fixture，保留具体实现到接口赋值、上界读取、窄返回和错误载荷负例；没有手改生产产物。

- 普通or补回：tsc exit2/7条；通用量化回调or：exit0。
- 通用回调or+单参reduce：exit0；Collector重载恢复：exit0。
- 直接toList():List<B>：exit2/7条。完整List/Collection/SequencedCollection/ListIterator视图、递归Stream/Optional及回调式操作：exit0，未删公开成员；直接host工厂消费仍失败，因此不能投产。
- 新host协议原型初次暴露Function.apply误入host分支；加非可执行值约束后仍发现不同元素域的泛型工厂被接收。错误结果/错误域负例分别抓到了这两项漏洞，历史失败保留。
- 在每个实际参数域上加来源元组校验的隔离原型通过正负例；但工厂“保留任意子类型”的证明目前仅有手工identity/maxBy实验，尚未成为通用生产规则。含元素生产参数、F-bound/self生产能力、来源编辑/隐藏/重命名和任意方法局部变量仍需证据；不能把通用返回签名当作无条件参数性保证。

重算入口暂保留build下capture-rank2/full-view/host-protocol/domain-guard脚本与各变体tsc原文。后续归档须包含冻结基础声明及每个变体覆盖文件，避免将未来生成树误当本次输入。

## 当前可独立落地的机制：对象式 SAM 输入

既有Graal HostAccess支持对象按Java SAM方法名实现接口，默认方法仍由Java执行。真实运行已证明：仅apply对象可用于UnaryOperator/BinaryOperator，andThen默认方法可用；@Remap操作的对象代理使用original，而非宿主暴露名renamed。测试初次受nashorn默认ES5语法影响，明确设置ECMAScript2022后两项运行时检查通过；这次语法失败不作产品缺陷红证。

旧TS别名只接受函数或完整host接口，因缺andThen/compose而拒绝同一对象输入。新增消费者在旧生成器报TS2345/TS2353；实现只在既有别名追加受保护的对象分支，方法名取真实Java SAM，参数/返回复用既有callback槽。原函数和完整host分支保留，接口成员、IR、运行时转换与权限都不改；Function.apply/call不能绕过结果检查。

四项新回归及严格消费者已通过。新增 sealed 边界实测证明真实 host 可用，而函数及对象代理均被 Graal 拒绝；对应别名保留名称、泛型元数和完整 host，移除原来无法运行的函数输入，不增加对象代理。一般 Java host 的结构类型仍可能被完整形状的伪对象模拟，本次没有宣称解决该既有名义身份限制。

完整 npm 链曾抓到新增对象分支从 Function.apply 收集错误推断候选，导致既有三参数 reduce 结果从 number 变为 unknown；新分支使用 globalThis.NoInfer 阻止该额外推断路径，仍执行最终参数检查，没有把原调用改成显式泛型。修复后普通 Probe 套件和完整 test:probe-types 链通过。既有 host 保留测试改为要求完整旧 host 分支存在，不再要求它恰为别名最后一个分支。官方再生17个 golden，独立逐行核对为78个新增代理分支及2个 sealed host-only 别名，其余成员、名称、元数及顺序不变。

完整 common/check、处理器、guard 与五节点矩阵通过（3m50s，918 suites / 5191 tests / 271 skipped / 零失败或错误）；每个 Test 任务的临时目录独立，未并发运行第二个 Gradle。四项 CI gate 通过。隔离捕获原型已冻结于 ../architecture-refactor/evidence/2026-10-26-capture-protocol-prototypes/，16组实际重放的退出码与错误码计数全部吻合；保留失败实验，不作为生产 F1 完成证据。

产品1a1660b6的全新五节点安装与十入口复测完成：五次RCON正常停服、exit0、未kill且世界锁释放；1079条与84ae51c1逐入口一致，各族不变，原文及同源差分均移除0/新增0。新冻结消费者另行重放通过，完整严格TS仍FAIL。[本次冻结证据](../architecture-refactor/evidence/2026-10-26-sam-proxy-1a1660b6/README.md)不外推性能和最终候选结论。

## 下一步

1. 对象协议普通门禁、golden、分轴复审与产品commit已完成；新安装零诊断回归，完整形状伪host的名义身份限制另记开放。
2. 继续将隔离原型变为可证明的通用规则：先证工厂参数性及其反例，再设计完整IR驱动视图，保留所有公开成员、既有Bound直接输入与方法局部变量，并让真实host和callback都通过。
3. 合同证明通过前不移除生产排除器、不宣称F1完成；可以推进独立且有来源证明的族。完整新候选需五节点安装/十入口重采与原文及同源差分，未知项不能静默归类。

## 工厂见证的只读调查结论

不能仅凭 `<T> UnaryOperator<T>` 推导“返回每个输入的任意子类型”：合法的 `nuller()` 工厂可返回 `value -> null`；带生产参数或正向 F-bound 的工厂也可能返回另一个 T。需要的是精确的行为合同，例如“返回 SAM 的第0个或第1个实际操作数”，不能把该性质写到所有 `$BinaryOperator_` 或 `$Function_` 的全局别名。

现有 `MethodDecl.sourceMethod` 能精确定位工厂，但 IR 没有这种行为关系字段。候选接入点是现有 `NekoJSPlugin.registerTypeDocs` → `TypeDocsPoint` → catalog snapshot 通道，使用精确 Method 和返回 SAM 的操作数索引；不是在 renderer 按方法名列白名单。若证明确实需要扩展，须校验工厂/SAM/索引及冲突，依据编辑后的 IR 激活：隐藏仍隐藏，重命名保留身份，改写参数/返回槽则取消该见证。该方案尚未实现，完整 host 成员和泛型方法消费仍需先证；不将只读建议当作已通过的设计。

两轴复审共同指出完整结构对象仍可模拟 sealed host；这是保留的旧结构分支限制，当前不作“仅真实 host 能通过 TypeScript”的承诺。要消除此限制需要另行处理 Java host 的名义身份，不能通过删除合法 host 输入掩盖。

## F1 标量合同修复（源码门禁通过，待安装复核）

真实Graal确认ConstantDesc的Double/Integer/String输出为number/number/string，标量没有resolveConstantDesc；ClassDesc仍为具备该方法的host，数字/字符串输入和混合数组可用，boolean被拒绝。公开backend在旧源码产生五项describeConstable继承错误，拒绝合法数字/字符串输入，并错误放行标量的host方法访问；原文保存在build/constable-value-consumer-red.log。

第一版提前将ApiTypeRef展开为union虽然通过初例，但复审后新增公开消费者证实了三条adapter优先权回退：通配符集合、SAM返回与嵌套SAM返回均出现TS2578；golden逐行核对另发现varargs数组括号缺口。这些中间结果保留，不算最终候选通过。

当前实现保留原Java符号到最后的值渲染边界：先用显式输入adapter，再根据固定JDK装箱类型的sealed祖先元数据输出带括号的标量/host联合。继承顶部保留host，泛型实参仍在值位置渲染。显式编辑使用独立raw-value命名视图，保留原始host、数组及嵌套SAM槽，且不泄漏至普通输出。三个严格消费者（normal/edited/adapted）及真实运行已通过，包含构造器局部泛型的varargs数组；显式array(union)/varargs分组缺陷也有两条TS2322红证和对应绿证。

收口后的完整common/check、处理器、guard及五节点构建矩阵通过（9m05s，919 suites / 5193 tests / 271 skipped / 零失败错误），TS完整链退出0，四项CI gate通过；使用独立测试临时目录，未并发启动另一Gradle。两个golden文件八处值变化独立核对通过，其他行/成员/继承不变。源码最终复审没有新的确证项，旧host名义身份缺口保持开放；尚无新安装数量结论。

本项只修正F1的标量值合同；or/部分reduce/Collector collect/toList的公共捕获视图缺口仍需完整实现，不因诊断计数变化而关闭。正式性能、Python默认安装及最终候选门禁仍保持各自范围。
