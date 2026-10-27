# SectionPos坐标声明迁移提案

状态：原型已完成，待用户决定。生产HEAD产品仍为11c9a180，正式五节点严格TS997条FAIL。以下973仅为隔离声明副本结果，未修改公共生成器。

## 已验证的行为

五节点均用实际Minecraft `Vec3i`、`SectionPos`，生产NekoSharedHostAccess和Graal Context执行相同观察：

| 表达式 | 实际结果 |
|---|---|
| `section.x` / `.y` / `.z` | 数字4/5/6 |
| `section.x()` / `.y()` / `.z()` | 数字4/5/6 |
| `section["x()"]()` / `"y()"` / `"z()"` | TypeError：不是函数 |
| `section.getX()` / `getY()` / `getZ()` | 数字4/5/6 |
| `section["getX()"]()` | 数字4 |

成员读取和直接方法调用在Graal中的行为不同。此前真实字段的选择器规则不能直接套在这里；不得把失败的`["x()"]()`写成迁移建议，也不使用`number & (() => number)`来假装读出的数字本身可当函数保存并执行。

Java来源核对了1.21.1和26.1.2：Vec3i坐标字段是private，公开getter形成Bean；SectionPos声明同名方法。26.2未另行核对源码，但实际二进制已参与上述五节点运行。`offset`错误是SectionPos坐标结构不兼容的下游错误，不是另一个缺失重载修复。

## 精确迁移范围

只处理以下15处TypeScript声明；源Java、JavaScript运行时、Python合同均保持。普通JS的`section.x()`仍可执行；TS脚本改用`section.x`或`section.getX()`，y/z同理。

| 节点 | 类型 | 省略的TS方法声明 | 保留的TS读法 |
|---|---|---|---|
| 1.21.1 | net.minecraft.core.SectionPos | x(), y(), z() | x/y/z数字Bean及getX()/getY()/getZ() |
| 26.1.2 | 同上 | x(), y(), z() | 同上 |
| 26.2.0 | 同上 | x(), y(), z() | 同上 |
| 26.1.2-fabric | 同上 | x(), y(), z() | 同上 |
| 26.2.0-fabric | 同上 | x(), y(), z() | 同上 |

不会移除Vec3i的Bean别名，不改SectionPos静态x(long)/y(long)/z(long)、offset及其他方法，不迁移Shelf.level或其他同名成员。隐藏、排除、手写替代、明确的IR编辑保持权威；不得从被隐藏来源恢复getter。

若批准，采用版本树通过现有TypeDocs贡献渠道登记的精确TS投影规则，按接收类型、真实实例方法身份/零参数和已保留getter合同匹配，避免在common硬编码MC类名或全局压制同名方法。规则只用于这三项有真实等价getter的坐标入口；验证无法满足时保持明确失败，不用any/never补洞。补公开backend正负消费、继承/静态/隐藏/重命名/编辑/手写隔离和真实Graal回归；最终再跑common/check、TS全链及确切新制品安装，不能把本原型当产品通过。

## 原型结果与授权边界

基于已冻结11c9a180五节点生成树，只从五份SectionPos声明省略三个零参坐标方法，其他字节内容保持。十入口strict/skipLibCheck=false重跑997→973，完整诊断块−24/+0，F13 36→12，其余族逐入口不变且零UNMAPPED。仍为FAIL；余下Shelf类冲突未解决。

此前[已批准的选择器规则](2026-10-24-explicit-selector-decision.md)明确限定真实暴露字段，并保持其他Bean别名；这次涉及真实方法在TS中的省略，不能把旧批准当作本次迁移授权。现提交这份精确方案供决定。所有运行输入/输出、实际测试来源、历史配置失败及原型驱动见[证据](../architecture-refactor/evidence/2026-10-27-section-pos-bean-prototype/README.md)。
