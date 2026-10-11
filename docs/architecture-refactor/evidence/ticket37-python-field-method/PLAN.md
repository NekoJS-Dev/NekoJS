# 实际同名字段/方法下一反馈环

基线 0a212fbb：Callable 修复及实机验收已完成，精确生产源码 2a421c18 的主节点启动/重载性能 PASS；完整 IDE 仍 FAIL。新反馈环仅为剩余实际 field/method 同名重复声明，不能删实际成员、添加 Any、改安全设置或混用性能 revision。主 agent 独占源码和执行；不并行改动。

先通过现有生产 HostAccess/ClassFilter 实测同名 boolean 字段/方法与 double 字段/方法，保留真实 Java 读值、方法返回值与赋值，避免凭 Python 无法表达某种形状而推断运行时。已有完整新 JAR 输出的 Pyright7/7/7/13/13 重复声明是原症状。下一步用实际反射/backend 的完整输出及严格调用构建 RED，随后才排序可证伪解释和修改共享声明生成器。

本计划不认证尚未完成的真实访问、完整 IDE、其他领域或维护者结论。当前没有产品源码修复；两个无关 EOL 文件、配置和八张 native 图片保留，自动续跑 PAUSED。

实际 HostAccess 事实已通过：locked 字段读 false、同名方法返回 true；x 字段读3、同名方法返回4；写 locked=true 后两种访问分别为 true/false；保存 x 到普通变量后调用会抛 TypeError。backend 留下全部8份 stub，反射字段/方法各2个且实际方法为实例方法；生成前后共享事实不变。

完整 basic RED2错误、strict RED6（含未使用导入），合法严格调用 RED7（字段读/赋值错误和同名方法 self/Unknown）；错误参数严格调用3错误。采集脚本 exit0 只表示完整收集并确认 RED，不表示编译通过。尚未修改生产声明。
