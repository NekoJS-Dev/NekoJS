# 非静态内类捕获的 owner 泛型

Luna xhigh仅只读调查，主代理核实并实现。87baad4b完整严格诊断的唯一TS2304来自26.2 Fabric的`Settings<T>.MutableValue<V>.update`：实际class Signature返回外类`T`，反射签名保留了owner身份，原IR仅取内类本级`V`与引用的本级实参，结果输出未声明的`T`。这与Mixin猜测无关，也不通过补方法泛型、删除成员或把该结果改成any处理。

## 实现与合同

JavaGenericTypes统一收集非静态member class的捕获参数与owner实参，按外到内顺序展平，遇static边界停止。按TypeVariable声明身份分配稳定名字；子层重名不会改变已经分配给父层的名字。原始Class用点继续遵循既有raw擦除，显式参数化引用保留实际类型。

TypeReflector把完整真实签名写入语言中性IR，已有TS类声明、适配器与重命名canonical export共用同一参数列表；JavaTypeScriptProjection、SAM输入/返回、事件原始payload/dispatch和manual raw引用按相同元数渲染。继承签名解析保留owner替换并避免方法变量遮蔽捕获参数。主扫描补齐真实owner参数和捕获边界的有界边，深度、include、exclude规则继续生效；TS独立完整声明闭包仍以实际渲染引用为准。

显式overridden slot保持优先，未用source metadata覆盖编辑。未改ApiTypeRef结构、runtime host对象或Java公开方法。Python普通host泛型仍按原有近似展平、变量仍为Any；共享IR新增真实owner/bound引用可能增加其可用类型导入，不声称stub逐字不变。这是已批准完整签名依赖方案的真实边，不放宽权限或排除规则。

## 已运行与待验

- 最小Java回归先失败，严格TSC复现TS2304与失效负例；修复后通过。
- 三层非静态捕获、重名变量、后层名字与已分配父层名字碰撞、static边界、整个raw inner引用、普通输入及SAM输入/返回通过严格正负例。
- 显式返回/字段编辑、类重命名canonical export、带捕获参数的adapter alias通过独立严格正负例。
- raw事件dispatch首轮复现TS2314，补齐共用raw投影后通过。显式any仅沿用该raw Class用点的既有擦除，不代替缺失的参数化结果T。
- 公开ProbeCoordinator回归确认owner实参与bound进入受限扫描，maxDepth=1不遍历下一层、maxDepth=2可遍历、精确正则exclude仍拒绝。首轮测试错误把包前缀规则当成精确类规则、把maxDepth=0当成零深度；已按现有合同改夹具，生产规则未改。最初扫描红例在缺少真实owner边时失败。
- Python公开backend消费确认update仍输出Any且没有未声明T。首次夹具未提供Python要求的共享IR被拒绝，补上真实反射IR后通过，未修改backend错误处理。
- 四组Java回归、三份`skipLibCheck:false`严格TS均通过，既有泛型/继承/输入消费者的定向Java回归通过。

完整common隔离、五节点build、最新安装全图诊断及正式性能将在这一批共享原因修改完成后统一运行；87baad4b的5152项、5549条诊断与287.4/305.6ms仍是旧源码结果，不改写为本次结果。未推送、发PR、发布或代签维护者结论。
