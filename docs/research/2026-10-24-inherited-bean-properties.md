# 继承字段与Bean声明冲突

当前正式候选975496d3的完整严格TSC中，NeoForge26.1.2有Bee.goalSelector与AbstractArrow.noPhysics两条TS2611：祖先声明为数据属性，子类的真实Java Bean却输出为TypeScript accessor。其他节点同类错误仍须由新的实际生成物逐项复核。

主代理实现保守的TypeScript渲染投影，Luna xhigh仅只读核实IR和边界。测试通过既有公开TypeScriptProbeBackend生成路径和真实Graal HostAccess执行；TypeScriptInheritedBeanTest是本轮主代理新增用例，非历史已存在的证据。

投影只改变声明写法：经过编辑后的父链从近到远确实存在同名、同型数据属性，且每个中间Bean均可表达为同型属性时，才把子类Bean写成property。getter-only保持readonly；存在同型setter时保持可写；同类字段与Bean并存时以beanFieldWriteType保留字段或setter的实际写类型。真实访问器方法保留，普通getter override仍保留原只读约定。继承型声明缓存会在完整编辑后IR收集完毕后重绘。

最近祖先的成员若是不可投影accessor、真实同名方法或类型不匹配，不越过它寻找更远的数据属性；父类缺失或隐藏时停止，不从反射补回。不同read/write类型保留get/set。只比较现有渲染后的类型，不新增泛型父参数替换，因此Base<T>实例化后的某些等价类型仍可能漏投影；这不是全图TSC已修复的声明。未删除任何Bean别名或真实字段/方法，没有扩大既往五处Entity.attackable迁移范围，Python和运行时未改。

红绿证据：新增用例修复前3项中2项失败，公开生成物严格TSC出现2条TS2611；修复后3项通过、严格TS正负例通过。扩展的renderer、继承接口和静态继承回归通过；新增夹具包括只读/可写三代继承、继承setter、field-backed Bean、父级已经是accessor、类型差异、hide/rename/missing parent与getter override。完整矩阵与新候选全图声明的结果在后续封存后补充，旧975候选的5565条完整严格TSC失败和313.8/308.6ms正式性能失败仍保留。

复核入口：`:common:check`、`:common-api-processor:test`、`guardLint`及CI五节点build；`npm run test:probe-types`包含新增`test:probe-inherited-bean`。原性能仍须按原采样器和285.3ms门槛另行复验，不以[30轮分段诊断](../architecture-refactor/evidence/2026-10-24-reload-stage-profile/README.md)代替。
