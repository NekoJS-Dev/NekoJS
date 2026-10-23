# 类泛型完整上界验收

源码 `6e7452b7fd67a2fc5e5cb4cdfee987ab6835ddbc`，基线 `f860df0c`。主代理实现、验证和提交；Luna xhigh 仅只读调查与两轴审查。没有 PR、push、正式发布或人工签署。

反射 IR 保留类泛型的全部非 Object 上界。类、canonical 导出和适配器别名共用有效约束渲染；每个交叉项分组，避免 union 绕过主约束。依赖闭包及 assign_type 读取所有有效上界。旧 bound 字段、构造函数及显式替换/编辑/清空的整约束语义保留；尾界的独立修改不丢首界。Python 泛型擦除、运行时、wire、数据及 exclude/hide 约束没有改变。

| 检查 | 结果 |
|---|---|
| common/check、处理器、guard、五节点 build/platform/artifact | PASS；5146 tests / 271 skipped / 零失败错误；5m44s，106 tasks / 41 executed；guard零警告 |
| 定向 Java / 实际 Graal | PASS；21项，包含实际宿主值的首界和尾界访问 |
| npm run test:probe-types | PASS；已接入8种新的严格TS消费模式 |
| 五个新安装JAR | 实际行为全部PASS；五进程正常RCON退出0，无强制清理 |
| 默认 Python | 1609 stubs；Pyright 1.1.414 零错误警告 |
| 十入口完整严格TSC | **FAIL，5565条**；相对459c0cd7移除10/新增0，均为E缺少StringRepresentable约束；无编译器崩溃 |
| 两轴审查 | Standards 0硬违反/0剩余气味；Spec 0；未置overridden的ref编辑疑问沿投影调用与实际输出复核后撤销 |
| 正式重载性能 | 本源码未测；最近正式7bea6d6d仍为340.0 / 292.8 ms，超过285.3 ms门槛 |

完整TSC server/startup分别为491/492、560/561、549/550、595/596、585/586；skipLibCheck=false，默认编辑器入口保留。新安装逐项比对全部common/node编译类与实际JAR字节，记录源码patch和SHA；不是复用旧声明。运行覆盖启动注册、嵌套fingerprint、实际方块/流体、重载，及适用节点的交易、动态空代保留效果和实体重载前后效果；Fabric261再次验证实际合成accessor可调用。

红测分别保留：遗漏尾界、未迁移别名、assign尾界及union缺括号。对应严格TS诊断为1/3/1/2；最后两项另有Java精确断言失败。初次catalog.empty()夹具编译错误单独保留，不充当功能红测。GREEN包含8种真实输出与严格正负例，不把expect-error移除或扩大为any。

复现完整矩阵命令见[实现记录](../../../research/2026-10-23-class-bounds.md)，随后执行 `npm run test:probe-types`。安装、完整IDE、差异和封存工具随归档保存。离线复核：`python docs/architecture-refactor/evidence/2026-10-23-class-bounds/verify.py`。归档5201条目，9,454,786字节；校验重新统计XML、原始诊断、Python AST和五次退出。归档不含world、JAR、运行库、properties、完整启动参数或凭据；匹配的RCON密码字节已脱敏。

本次一致性PASS不表示完整TSC或性能通过。方法级约束编辑/分组、捕获类型、成员投影冲突及Mixin擦除泛型继续处理；最终统一候选的客户端/TCP/制品与数据分离回退和维护者结论仍独立开放。票28保持工程closed，票37保持in-progress。
