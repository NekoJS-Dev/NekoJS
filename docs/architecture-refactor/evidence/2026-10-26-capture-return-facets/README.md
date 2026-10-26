# 捕获返回交叉合同：受控原型

本目录仅封存手工编辑的声明原型，不是完整生产生成器、五节点安装或最终候选通过证据。起点为前轮冻结的公开 backend fixture，功能接口模块换用当前公开 backend 产物；没有增强 `identity<T>()`、`maxBy` 或其他工厂签名，也没有新增工厂贡献 API。

具体实现返回 `Optional<Narrow> & OptionalView<Bound>`，窄返回在首项，父接口继续返回上界视图。这样完整视图不必再依赖原始可变容器的结构协变。此处六个返回边界是手工修改；生产规则必须从可见、编辑后的真实继承 IR 得出，不能硬编码方法名。后续原型令完整视图继承原始源类型，验证它们没有以删减公开 API 换取通过。

| 精确冻结输入 | 实际 strict / skipLibCheck=false 结果 | 意义 |
|---|---|---|
| host-first | exit 0 | host 重载前置、回调/对象重载后置，隐式 `identity()` 保留上下文推断 |
| callback-first | exit 2，TS2769 ×1 | 顺序反转后同一工厂消费失败；保留对照 |
| coverage | exit 2，TS2345 ×2、TS2578 ×1 | 新增窄 Collection 正例被拒，窄 Collector 负例未生效 |
| guarded-inputs | exit 0 | 集合输入绑定 iterator 元素；Collector 以函数属性约束 accumulator 域，三条控制均恢复 |
| source-inheritance | exit 0 | 完整视图继承各自源类型；同一组正负消费仍通过 |

正例包括普通/对象/host Supplier、完整 host 操作器、隐式/显式工厂、两种 BinaryOperator reduce、三参泛型 reduce、两种 collect、显式方法泛型、map/flatMap、递归 Optional/Stream/List 导航、两种 Collection 输入及原始同元素容器赋值。负例包括错误结果、窄域 host/工厂/对象、窄域或无关 Collector、错误 Supplier 和窄成员不可达。

输入载体中的 `NoInfer` 仅阻止额外推断候选，不会使 TypeScript 的显式 `any` 安全。已知错误元素的 host/回调仍需被拒；显式 `any`、断言以及完整伪 host 的既有结构身份缺口不宣称解决。`toList()` 实际返回不可变列表，声明可见方法不代表 Java 操作总能成功；运行测试不把 `replaceAll` 成功施加于它作为要求。

`validation.zip` 保存每组完整声明、消费者、配置、原始 tsc 输出及实验脚本；`manifest.json` 逐文件绑定摘要。实验脚本保留原来的 build 目录依赖，独立复放以冻结输入为准：

```powershell
python -X utf8 docs/architecture-refactor/evidence/2026-10-26-capture-return-facets/verify.py --typecheck
```

已实际重放，五组退出码和错误码计数全部吻合。验证器要求本仓库 TypeScript 5.8.3，不下载依赖。java.time 使用该隔离 fixture 原有的不透明声明；完整 java.time 错误仍归十入口基线，不被此处的 exit 0 覆盖。

最近正式安装仍绑定产品 `3d2cc1cb`：完整严格 TS **1029，FAIL**。本轮原型不能修改该数字，F1 公开方法缺口、其他族、最终性能/Python/客户端/TCP/回退验收继续开放。
