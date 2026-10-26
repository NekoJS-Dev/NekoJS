# 继承捕获返回合同：产品5280d20b

本阶段实现完整 F1 视图恢复所需的返回合同基础，**没有宣称完整捕获视图已经交付**。产品提交为 `5280d20bd0936e5dca034982d3e2e7bda2c1ec4c`，主代理实施和验证，子代理只读复查；没有 push、PR、发布或维护者代签。

## 改动与合同

`TypeScriptCaptureReturns` 只遍历当前可见 IR 的继承链，以真实 Java 方法签名定位匹配声明，替换类和方法泛型变量。在原窄返回后追加继承的 `OptionalView<Bound>`；直接 `.get()` 仍返回窄类型，较宽的继承 fallback 返回上界类型，不会错误承诺子类字段。IndexFileGenerator 通过既有继承重渲染机制更新类缓存及导入；Java/Python 的共享 IR、Java 工厂及运行时行为保持。

定向检查覆盖两层泛型接口、参数与泛型数组专化、无关 int 重载、中间类重复覆盖、wildcard 窄返回，以及由超类继承方法、在真正子类上满足泛型接口的情形。隐藏、重命名和覆盖父返回类型后不会恢复旧反射合同。`JavaGenericTypes.erased` 复用从 StaticInheritance 原样移出的擦除规则。

实际 NekoSharedHostAccess 执行了普通/host/对象 or、两种 BinaryOperator reduce、三参 collect、host Collector、toList、较宽 orElse 和真实 Java 数组参数。另一个扩展尝试直接传 JS 数组时触发 ClassCastException；失败日志 `capture-return-parameter-array-green.log` 保留，文件名是当时的尝试名，**不是 PASS 标记**。本阶段未修复或宣称覆盖该 bridge 选择问题；最终正例使用真实 Java 数组，严格消费中的 hostInputs 对应这个范围。

## 验证

| 检查 | 实际结果 |
|---|---|
| `:common:check guardLint`，使用每 Test 独立临时目录 init | PASS，2m49s；338 suites、2478 tests、4 skipped、零失败/错误；隔离检查通过 |
| 完整 `npm run test:probe-types` | exit 0，含新增 capture-return-contract 组；在最终 common 检查之后单独运行 |
| `python -X utf8 tools/nekojs-ci-gates.py all ...` | 四项 state=pass、零失败；node-report 读取既有节点制品和结果，不能当作本产品五节点重新构建 |
| 官方 `:common:regenerateGoldens` 后独立字节/逐行核对 | 两文件，10 个窄返回原文保留并附 facet，3 行必要导入；其他文件/行字节不变 |
| 确切生成消费者及全部负例 | strict、skipLibCheck=false，exit 0 |
| 仅从同一生成树移除返回 facet 的变异对照 | exit 2，TS2345 ×5；五个合法 fallback 变为不可用，不把预期失败算成产品失败 |

泛型参数、继承接收者和中间覆盖的回归曾先失败，修复后通过。早期开发日志保留，但中间源码没有完整冻结，不冒充最终回放输入。最终消费者、配置、源码、相关 class、JUnit XML、命令原文和 golden 备份按摘要冻结于 validation.zip；源码逐字绑定产品提交，class 仅来自 common 构建目录，**不是安装 JAR 来源证明**。

独立回放：

```powershell
python -X utf8 docs/architecture-refactor/evidence/2026-10-26-capture-return-contracts/verify.py --typecheck
python -X utf8 docs/architecture-refactor/evidence/2026-10-26-capture-return-facets/verify.py --typecheck
```

第一项已实际重放 generated exit0 / without-facets exit2、TS2345×5；第二项独立封存完整视图的手工原型，不是本产品生成结果。fixture 保留原有 java.time 不透明声明隔离，完整 java.time 错误继续留在十入口基线。

## 分轴复查与剩余工作

Standards：只读复查发现继承方法应按真正接收者解析泛型接口；已补独立红证并修复，复查无新增确定 P1/P2。Spec：只读复查发现泛型覆盖父子参数擦除不同；已按真实继承专化后比较，并补普通参数、数组、非覆盖重载控制，复查无新增确定问题。两项复查都没有替主代理运行测试。

完整生产视图仍缺 or / 部分 reduce / Collector collect / toList，需将已通过手工原型变为遵守可见编辑 IR 的完整生成器。还要覆盖 helper 名冲突、成员隐藏/编辑、方法局部泛型、同上界覆盖，以及显式较宽 flatMap 的消费，不能通过删成员或放宽负例收敛。

本阶段没有新五节点安装、十入口诊断采样或正式性能测试。最近正式安装仍是产品3d2cc1cb的 **1029 条，完整严格 TS FAIL**；该记录不能冒称5280d20b的安装结果。其他族、最终性能、Python默认安装、客户端/TCP/回退和维护者结论继续开放。
