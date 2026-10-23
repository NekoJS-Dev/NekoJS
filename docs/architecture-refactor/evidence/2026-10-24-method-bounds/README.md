# 方法上界赋值与交叉分组验收

源码 `975496d3bddf6fc76b206921a1d09207120ca24d`，基线 `d9519e02`。主代理实现、验证与提交；Luna xhigh只读调查和两轴审查。没有PR、push、正式发布或人工签署。

方法现有typeParameterBounds槽参与assign_type。类和方法共用约束渲染，多界逐项分组，避免union从交叉约束中逸出。依赖使用现有TS闭包；没有引入泛型猜测、公共类型kind、扫描旁路或成员删除，没有改变运行时、wire、数据、Python或exclude/hide。

| 检查 | 结果 |
|---|---|
| 完整矩阵 | PASS；5149 tests / 271 skipped / 零失败错误；3m59s，106tasks/34executed；common隔离、处理器、guard、五节点build/platform/artifact通过 |
| 最终定向 Java | PASS；62项，包含实际Graal、继承及Comparable |
| npm run test:probe-types | PASS；新plain/assigned/union三模式接入并通过 |
| 五个新安装JAR | 全部实际行为PASS，五进程RCON正常退出0，无强制清理 |
| 默认Python | 1609stubs，Pyright1.1.414零错误警告 |
| 十入口完整严格TSC | **FAIL，5565条**；相对6e7452b7移除0/新增0，无编译器崩溃 |
| 两轴审查 | Standards零硬违反/零气味；Spec零偏差 |
| 正式性能 | 同一源码[原政策复验](../2026-10-24-method-bounds-performance/README.md)完成：重载313.8/308.6ms均FAIL，启动14132.6ms PASS；17有效样本/9正常停止 |

功能RED为3项Java中2失败，严格TS assigned2条、union1条TS2578、plain通过；修复后正负例通过。首次完整common2432项中1失败是继承测试的旧精确文本预期，日志/XML保留。仅把其一行改为分组后文本，原Comparable、Graal和严格消费断言保留，声明golden文件未改。该输出变化是分组修复的预期结果，未弱化门禁。

构造器不是新增承诺：原reflectConstructor不收集自身类型参数，局部变量仍沿ConstructorInputTypes首界投影。不能据共用applyMethod代码宣称完整构造器交叉约束已解决。

新安装逐项核对全部common/node类与实际JAR字节，记录来源patch及SHA。实际运行覆盖启动注册、嵌套fingerprint、方块/流体、重载及适用的交易/动态空代保留效果、实体重载前后效果；Fabric261再次验证真实合成accessor可调用。完整TSC server/startup依次为491/492、560/561、549/550、595/596、585/586；默认入口与skipLibCheck=false保留。

复现矩阵命令与前一[类上界片段](../2026-10-23-class-bounds/README.md)相同，随后执行 `npm run test:probe-types`；本片段工具、原始输出、声明/stub、源码和XML保留在归档。离线复核：`python docs/architecture-refactor/evidence/2026-10-24-method-bounds/verify.py`。归档5134条目、9,412,210字节；不含JAR、world、运行库、properties、完整启动参数或凭据，匹配的RCON密码字节已脱敏。

离线一致性PASS不表示全图TSC或性能通过。捕获类型、成员投影冲突、Mixin泛型擦除、最终候选客户端/TCP/制品与数据分离回退及维护者结论继续按各自范围开放。票28工程closed，票37仍in-progress。
