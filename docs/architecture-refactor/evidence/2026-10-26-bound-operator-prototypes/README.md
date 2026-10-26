# 无工厂强化的操作数域原型：失败记录

2026-10-26。这是主代理在build隔离副本中的声明实验，未修改生产生成器。基础视图来自84ae51c1的domain-guard原型，SAM模块替换为3d2cc1cb公开backend的真实输出，保留完整alias/NoInfer及原始identity/maxBy/minBy工厂签名，没有手工witness。

输入沿用此前隔离fixture的java.time排除与不相关名义占位，**不代表当前默认完整声明图**。这些手写视图尚未成为IR驱动的产品规则，也不能用来关闭F1。

两组保留O泛型及每个操作数的域元组检查，改用普通完整SAM输入，结果只承诺B或OptionalView<B>，不从O推导更窄C；覆盖replaceAll、单参及带identity的reduce。supplier组还测试普通Supplier<Optional<Narrow>>和Bound fallback。新增裸Bound host正例、窄域host/factory负例；原始隐式identity()正例保留，没有改成显式泛型来绕过失败。

| 变体 | tsc退出 | 错误码计数 | 结论 |
|---|---:|---|---|
| operators |2|TS2416×6 / TS2322×1 / TS2345×1|合法继承/父接口赋值仍因完整集合视图的addAll关系失败；隐式identity()推断为unknown而被拒绝 |
| supplier |2|TS2416×6 / TS2322×1 / TS2345×2|额外暴露or/Supplier输入及同一推断问题；合法Supplier<Optional<Narrow>>也被拒绝 |

两组没有TS2578，但整体都FAIL，不能因局部host/域负例未新增诊断就投产。原始红文、37份输入/组、当前SAM模块快照和生成脚本均在prototypes.zip。首版生成脚本的文本替换曾截断成员内部的分号，产生TS1109/TS1128；harness-syntax-error保留其原文，**只算实验脚本错误，不算API反例**。修复后的上表才是语义实验。

下一步应分离变量：当前SAM别名与旧别名、工厂witness移除、操作数约束、Collection.addAll重载和工厂上下文锚定。不要把同时改动多变量后的失败归为唯一原因，不删方法、放宽结果或削弱窄域负例。新增工厂语义贡献API尚未证明必要。

```powershell
python -X utf8 docs/architecture-refactor/evidence/2026-10-26-bound-operator-prototypes/verify.py
python -X utf8 docs/architecture-refactor/evidence/2026-10-26-bound-operator-prototypes/verify.py --typecheck
```

第二条固定TypeScript5.8.3，在build独立目录重建两组并核对真实exit2和错误码计数，保留重放产物；本轮实际执行通过表示忠实复现失败，不能记作产品绿态。当前产品仍是3d2cc1cb，[默认十入口1029条](../2026-10-26-constable-values-3d2cc1cb/README.md)，完整捕获公共合同和整票门禁保持开放。
