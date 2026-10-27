# 票 37 持续推进目标

起点：dbdfcfeb（产品 dad806fa）；主代理实现、验证及本地提交，不 push、不发 PR。

1. 完整捕获视图及连续 wildcard 祖先合同已实现（8f71ca31 /15c4d9a6），恢复 or、全部 reduce、Collector collect、toList 与递归导航，保留编辑后的可见 IR、工厂及全部正负合同；host 结构身份和 JS 数组桥异常继续记录，不能把它们写成已修复。
2. 当前正式安装11c9a180为997条完整严格TS错误，对15c4d9a6完整块−32/+0，F1/F2/F4/F8均零，其余族逐入口不变。F8已完成确切安装与冻结输入重放；F6 Recipe约束488条真实DFU原型未通过捕获身份/底类型控制，全部未投产。继续调查F13 Bean/方法冲突的真实运行行为，再处理F6/F7/F5等共享原因；不全局协变、不漏检或删除成员换取计数。
3. 修改遵循编辑后的 IR；保留枚举静态工厂与 Enum<Self> 实例继承分离、重命名和隐藏。用合法默认方法、接口赋值及错误载荷验证。
4. 定向回归通过后执行 common/check、严格 TS 链；有意 golden 变化通过现有生成器再生并审查。确切产物需新的五节点十入口证明，不能借用旧安装结果。
5. 每阶段记录 passed/failed/not-run、诊断增减和未解决合同。最后候选性能、客户端/TCP/分离回退与维护者接受仍独立验收。

完整视图公开方法恢复已经有实际新安装证明；其余边界与其他族不得被诊断清零掩盖。

2026-10-26阶段5280d20b仅完成返回合同基础及common/定向TS验证，[该阶段冻结证据](../architecture-refactor/evidence/2026-10-26-capture-return-contracts/README.md)不覆盖新五节点安装或性能。后续完整视图及新安装见[2026-10-27证据](../architecture-refactor/evidence/2026-10-27-transitive-capture-facets/README.md)；其他族及最终候选继续推进，早期JS数组调用触发ClassCastException的扩展失败仍保留。

## 枚举接口修复：实现及局部门禁

renderEnum 从编辑后的 IR 补回实例接口继承，沿用 Enum<Self> 与静态工厂分离的合并声明。七项枚举测试覆盖默认方法、泛型/self、重命名、移除接口和隐藏依赖报告；六组 strict/skipLibCheck=false 消费进入既有总链。隐藏来源不复活，真实引用保留且 dependencies.json 明确报告 incomplete。

初次普通 common/check 有两项预期 golden 差异；原生生成后，首次矩阵尚未退出时错误地启动了第二次 common/check，两个 Gradle 测试运行交叉污染固定临时游戏目录，并发生结果文件冲突。失败、日志占用及无限循环线程栈保留在 build/enum-interface-*；核实身份后回收本次挂住的 worker 4940。没有改断言或产品运行时，也没有将这些运行计作通过。

最终使用新的临时目录以及 --no-daemon --no-parallel --max-workers=1 串行运行 common/check、处理器、guardLint 和五节点 build，2m25s 通过（106 tasks；节点测试有缓存复用）。当前 XML 合计5187 tests、271 skipped、0 failures/errors。npm test:probe-types 全链与四项 CI gate 通过；两轴只读复审无新增确证问题。84ae51c1五节点新安装全部正常停服，十入口1409→1079，F2清零；原文差分−334/+4，同源缺失引用核对后−330/+0，详见冻结证据。最终候选性能、Python 默认安装与客户端/TCP/回退未重测。

## 独立后续调查记录

F14 的4条并非缺少Java实现：当前 Fabric Entity 声明有 getPDataRoot()/pDataRoot，而 NekoEntityPData 要求 neko$getPDataRoot()。实体从 EntityExtension 获得类级 RemapByPrefix，影响另一接口实现的方法名称；未标该注解的接口自身仍按原名发射。后续需要来源和接收者上下文一致的合同投影，并实测实际暴露方式，不能未经证明就添加接口原名或修改接口注解来消除诊断。此前对原名运行时不可访问的表述超出了源码核对范围，现明确为待实测。本阶段未改此处产品代码。

## 2026-10-26 标量合同结果与后续顺序

3d2cc1cb修正ConstantDesc值的标量/host表示，显式adapter、原始IR覆写及继承均保留；5193 tests矩阵、TS全链和四项CI gate通过。[新安装证据](../architecture-refactor/evidence/2026-10-26-constable-values-3d2cc1cb/README.md)1079→1029，原文/同源−50/+0，F1诊断清零、其他族逐入口不变，完整严格TS仍FAIL。

优先顺序继续是完整F1公共捕获合同，而不是按诊断清零关闭。移除手工factory witness、保留B结果的操作数域原型仍有合法继承、Collection.addAll重载及隐式factory上下文推断失败；先做受控变量实验，不删成员、改调用为显式泛型或削弱负例。正式新候选性能、Python/客户端/TCP/回退/包装与真实维护者结论继续按各自未完成范围推进。
