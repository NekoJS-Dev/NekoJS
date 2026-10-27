# SectionPos精确Bean方法投影实施记录

范围：用户明确批准五节点15处SectionPos零参x/y/z的TS方法声明迁移。主代理实现、验证及本地提交；不push、不发PR、不发布或代签。原型973不是当前生产验收，正式基线仍绑定11c9a180的997。

## 实现

- 新增BeanMethodProjectionCatalogEntry，要求有真实声明的公开零参实例方法及可见、同结果类型getter。通过既有TypeDocs/NodeTypeDocs产品、runtime和每侧catalog收集；列表不可变、去重且不跨runtime复用。旧snapshot构造签名保留。
- 版本树MinecraftTypeDocsPlugin只登记SectionPos的x/getX、y/getY、z/getZ三条规则；common不引用MC或loader。
- TS渲染器从已编辑可见祖先链核对真实getter与Bean合同，保留数字属性、getter、静态重载及其他类。隐藏、重命名、返回编辑、合成getter/字段和手写替代不被反射重建；不改共享IR、Java/Python或运行时。
- 新严格消费加入现有npm总链；中英文wiki迁移表及NEKO-4053说明同步。

## 已验证与发现

公开backend旧逻辑先红：TS2416/TS2426、合法父赋值及泛型约束失败；原始输入/退出码保存于build/bean-method-red-*。首次修复已正确省略目标方法，但测试误用了静态参数占位arg0，实际源码保留参数value，修正该断言后通过。没有改源签名或放宽类型门禁。

定向Java/严格TS通过；bootstrap验证common/node合并、快照不可变、sealed收集及新runtime隔离。只读自查又补合成getter/字段两个反例，先红（2项），加入可见编辑优先保护后绿。隐藏/改名/返回编辑、手写、独立类型、静态重载、Python及IR保持均覆盖。

首轮完整矩阵因旧MinecraftTypeDocsPluginTest的collector只接受Entity规则而失败；已更新该公开贡献接收方，明确保留Entity唯一规则并核对三条获批坐标规则。失败XML和日志保留；不作为产品通过。节点真实Graal回归确认数字读取、直接调用、getter均保持，签名选择器仍按实际行为失败。

## 当前门禁及下一步

最终完整common/check、处理器、guard及五节点build已通过（5m48s，929 suites /5226 tests /271 skipped /零失败错误）；无golden变化。完整TS链及四项CI门禁均通过；提交确切产品后，用新JAR逐节点安装、十入口严格重采和完整多行诊断比较，再冻结证据。最终候选性能、Python默认安装、客户端/TCP/回退/包装/内存预算与维护者结论继续独立开放。
