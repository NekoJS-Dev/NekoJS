# 选择器实现的实际安装差分跟进

## 已完成

生产生成器首提交为7d17de4e；[公开回归证据](../architecture-refactor/evidence/2026-10-24-selector-backend/README.md)保持原快照，915份报告、5174项及十四组严格程序不追改。主代理随后以该提交的实际JAR建立五个新隔离安装：五节点运行验收PASS，runner正常exit0，新增Vec3及JOML选择器实际调用通过；Python共1609份stub，Pyright错误/警告均0。尚未封存新的安装归档，不冒称已完成最终候选验收。

首轮完整严格TS十组共1629条，全部exit2。每个server配置有4条来自新增验收TS脚本：两条未声明的Java全局、两条把未列入本次迁移的Matrix4d当成必有选择器类型。主代理已将后续脚本改为已有java模块类导入及清单中的Matrix2d；先前脚本、诊断、实际运行结果均保留。不能删去这20条后把新配置直接报为通过，须在新隔离安装中实际重跑。

实际默认声明对比还显示，普通方法分组使与选择器无关的重载位置变化。主代理先构造同名remap但Java原名不相邻的失败回归，再限制合并到真正需要继承手写重载索引的组；其他方法按原IR次序逐项发射。修正后17项选择器回归及十五组严格正负消费通过，完整common/处理器/隔离/guard/五节点矩阵PASS，汇总5175项、271 skipped、0失败；总npm、wiki和全部CI门禁通过。首个顺序测试因Java原名仍相邻而未能复现，改用zLast后的red-v2才是实际失败记录，未将首轮PASS计作red。

## 原清单漏列的继承字段家族

26.1.2和26.1.2-fabric的旧默认声明及原型中，MinecartFurnace有真实push字段，而三个普通push重载声明在祖先AbstractMinecart、Entity。原96个起点清单要求同类有本地字段及本地方法，因此漏掉了这个跨继承冲突。生产通用规则沿父IR查方法，发现了该家族：保留字段、必要祖先使用选择器、安全旁支保留普通方法；这属于用户已批准的真实字段与必要继承合同规则，不是另加一类无来源迁移。

两节点源码生成层的三个参数拼写分别为`push(double,double,double)`、`push(net.minecraft.world.entity.Entity)`、`push(net.minecraft.world.phys.Vec3)`；这些字符串本身不能证明原始声明类。主代理正在准备受控Java helper，在两次真实安装中采集class.getName、字段声明类/类型、JavaMemberIndex可见方法的原名/声明类/参数/返回及实际加载来源；待采集后更新完整迁移清单和数量，不把979+25+1预期当作已核对的最终范围。只读代理指出的旧版vehicle.MinecartFurnace包名也须由运行时确认，不能根据简称猜测26.1.2的实际Java名称。

## 第三轮安装（v3）结果

v2 安装暴露的 `java:`/`node:` Windows 路径缺陷已按共享根因修复（3ccea55a，[证据](../architecture-refactor/evidence/2026-10-24-java-module-windows-path/README.md)），并以修复后源码重跑完整流程，结果见[第三轮验收证据](../architecture-refactor/evidence/2026-10-24-selector-installed-v3/README.md)：五节点矩阵 5177 项 / 0 失败 / 271 skipped；新目录新端口的隔离安装五节点运行时全 PASS，`java:` 类导入实际执行，v2 的 NOT GREEN 与 trade 连带失败全部转绿。

push 真实身份已在两加载器采集一致：实际类为 `net.minecraft.world.entity.vehicle.minecart.MinecartFurnace`，字段在本类，方法在 `Entity`/`AbstractMinecart`。AST 语义差分显示生产生成器相对原型恰在该家族超出（26.1.2 双加载器 +33 选择器 / +60 普通 / −6 普通），其余三节点与原型逐成员一致；全节点字段零删改。实测迁移量为 991 移除 / 1070 选择器 / 121 普通新增，旧 979+25+1 预期作废。

十组完整严格 TS 修正计数为 **1609**（= 1629 首轮实测 − 20 条脚本自生诊断，全部 exit2），与旧原型 1621 同源存量为 TS2416/TS2344 主体；Python 五节点 1609 份 stub，Pyright 0 错误 0 警告。

## 下一步

正式重载（285.3ms 门槛）、新冻结候选（3ccea55a）的最终制品/数据分离回退、性能复验与 1.2.0 版本切换重打包复验仍未执行；维护者迁移确认与发布结论未完成。无PR、push、正式发布或代签。
