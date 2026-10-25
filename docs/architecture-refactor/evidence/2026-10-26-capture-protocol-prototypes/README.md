# F1 捕获协议隔离实验

日期2026-10-26。基础为84ae51c1公开backend产生的通配Optional fixture，冻结37份输入。16组实验以基础输入加覆盖文件重建；所有变更仅在build中的独立副本，**不是生产生成结果、完整五节点结果或F1完成证明**。

此fixture保留了原有java.time排除与两个名义类型占位，以隔离与本题无关的闭包；不能冒称完整默认声明图通过。手工工厂见证是实验变量，不是经过来源分析的生产合同。

## 结果

| 实验 | tsc退出 | 结论范围 |
|---|---:|---|
| baseline | 0 | 具体实现到接口赋值、窄返回和上界读取控制 |
| plain-or | 2 | 原样补回or仍有7条错误 |
| rank2-or / or-reduce / or-collect | 0 | 通用回调可在隔离树中保留这些调用 |
| rank2-or-list | 2 | 原样List<B>返回仍有7条错误 |
| full-callback-only | 0 | 完整容器方法与递归返回可通过回调消费，尚不能保真实host输入 |
| full-host-consumer / host-union | 2 | 真实UnaryOperator/BinaryOperator工厂暴露剩余缺口 |
| protocol-v1 input / witness | 2 | 最小SAM协议使Function.apply可能绕过错误结果检查 |
| protocol-v2 input / witness | 2 | 加非可执行保护后，不同元素域工厂仍可误接收 |
| protocol-v3 input / witness | 2 | 额外交叉签名破坏回调上下文推导，且仍未消除错误域 |
| domain-guard | 0 | 参数域元组校验保住当前正负例；工厂参数性尚未证明 |

失败实验包括TS2578：预期错误没有发生，代表原型接受了不应接受的调用，不能把它当成无关噪声清掉。最后一组也只证明已列入的反例，不证明任意工厂、bounds、隐藏/编辑及所有host变体安全。

关键负例包括错误元素/回调结果、不可凭显式泛型凭空获取窄返回、不相容的工厂元素域、以及二元操作第二参数的窄域。最终元组检查验证所有参数，不只第一个。具体输入和原始诊断在prototypes.zip；manifest记录每组原始退出码与错误码计数。

## 复算

```powershell
python -X utf8 docs/architecture-refactor/evidence/2026-10-26-capture-protocol-prototypes/verify.py
python -X utf8 docs/architecture-refactor/evidence/2026-10-26-capture-protocol-prototypes/verify.py --typecheck
```

第一条仅校验归档与记录一致性。第二条要求仓库已安装TypeScript5.8.3，独立重建16组输入并逐组核对实际tsc退出码/错误码计数；产物留在build/capture-prototype-replay-*，不覆盖原输入或用户数据。实验中的exit2是预期失败，整体replay通过仅表示成功复现，没有把它们改记为生产绿态。

## 后续门槛

1. 对象式SAM输入有独立真实Graal证明，后续产品1a1660b6在非sealed别名追加协议分支，保留callback与完整host分支；sealed转换另以真实运行约束，不从这些手工原型推断。普通构建及定向消费通过，新的完整安装诊断另采。完整结构对象模拟host的旧名义身份限制仍存在。
2. 通用工厂的子类型保持不能只从方法名推断。输入若能生产元素、F-bound/self合同若能生产另一实例，都必须拒绝不成立的见证；反例先行，参数域校验不能替代来源证明。
3. 完整视图须由编辑后IR驱动，保留全部成员/重载/Bean/native输入以及方法局部变量；未证明前保留F1开放，生产中不再扩大删减视图。

当前完整安装基线仍是[84ae51c1的1079条](../2026-10-25-enum-interface-84ae51c1/README.md)。长期工作见[进展计划](../../../research/2026-10-26-capture-protocol-progress.md)。未push、未发PR、未发布或代签维护者接受。
