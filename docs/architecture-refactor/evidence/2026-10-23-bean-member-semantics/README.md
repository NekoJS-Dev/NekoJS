# 同名字段、Bean 属性与方法的真实宿主语义

这项诊断用于判断能否直接用交叉类型修复完整 TSC 的成员冲突。没有修改生产声明、运行时或已批准的五处 Entity 迁移范围。

使用实际 Fabric 26.1.2 Minecraft JAR 中的 Vec3 / SectionPos 类、生产 NekoSharedHostAccess、相同 Nashorn / foreign-object / ECMAScript / strict / V8 选项。此为独立 JVM，没有 Minecraft 启动器或已应用的 Mixin；不是五节点安装运行的替代证据。归档绑定实际 Minecraft / Graal JAR、宿主实现源码与 class、编译器和夹具的摘要。

| 实际表达式 | 结果 |
|---|---|
| `typeof vector.x` | `number` |
| `vector.x` | `7` |
| `vector.x()` | `7` |
| `const member = vector.x; member()` | TypeError: member is not a function |
| `typeof section.x` | `number` |
| `section.x()` | `11` |
| `section.getX()` | `11` |
| `const member = section.x; member()` | TypeError: member is not a function |

因此同名成员的直接调用与先读取再调用存在差异。`number & (() => number)` 在严格 TSC 中会接受上述提取后调用，却与实际运行冲突；该实验类型不能直接作为正确修复。归档中的独立 TS 夹具严格检查通过，只证明这个反例被该类型接受，不代表完整声明通过。首次误加本项目未安装的 node 类型包导致 TS2688，保留失败日志；之后使用只包含此文件的严格配置、`skipLibCheck=false`，退出 0。

既有完整声明的 SectionPos/Vec3i TS2430 和 Vec3 的同名字段/方法诊断仍有效。简单删除属性、方法或继承关系会改变声明契约，也不能直接解决 Position 等接口的可赋值要求。需要继续核对真实宿主调用规则、编辑后 IR、祖先视图和消费用例，再确定完整投影；不能将交叉类型能通过 TSC 当作正确性证据。本批没有扩大删除授权，也没有通过隐藏公开成员关闭 gate。

离线复核：`python docs/architecture-refactor/evidence/2026-10-23-bean-member-semantics/verify.py`。
