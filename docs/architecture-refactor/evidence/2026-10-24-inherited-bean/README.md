# 继承字段与Bean属性声明

源码87baad4b。主代理修复TypeScript的继承属性表示，运行时和Python未改；Luna xhigh只读调查与两轴审查。最近编辑后祖先确实输出同型数据属性，且中间Bean的读写可同型表示时才用property语法；getter-only保持readonly、同型setter保持可写，真实方法保留。父级accessor、不同类型与hide/rename/missing parent保留约束，不反射补齐或删除成员。泛型父实参替换仍未新增。

最小红绿：修复前3项中2项Java断言失败、公开生成物2条TS2611；修复后严格TS正负例和实际Graal读写通过。新夹具还覆盖三代继承、继承setter、field-backed Bean及其override、普通getter override、最近祖先的不同writer阻断。它是主代理本轮新增用例。

完整矩阵命令：`:common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`，结果5152项、271跳过、零失败/错误，8m18s；包括common隔离。首次命令用了不存在的节点名，选择阶段失败日志保留；未作为测试通过。`npm run test:probe-types`整链及`tools/nekojs-ci-gates.py all`通过。一次线程快照落在JSX用例，随后矩阵正常结束，未终止进程；不能将快照解读为该用例卡死。

五节点全新安装世界均完成启动/注册/真实放置/重载/声明生成和RCON正常停止0，实际制品与全部common/node class摘要绑定。默认1609个Python stub，Pyright1.1.414零错误/警告。完整严格TS使用默认server/startup入口、skipLibCheck=false，无编译器崩溃：

| 节点 | server | startup | 两入口减少 |
|---|---:|---:|---:|
| 1.21.1 | 489 | 490 | 4 |
| NeoForge26.1.2 | 558 | 559 | 4 |
| NeoForge26.2 | 548 | 549 | 2 |
| Fabric26.1.2 | 593 | 594 | 4 |
| Fabric26.2 | 584 | 585 | 2 |

总数5565→**5549**，删除16条TS2611，没有新增诊断；按文件/代码/首行消息多重集合比较，忽略位置变化，不比较多行细节。整体仍FAIL。NeoForge26.1.2实际输出的Bee.goalSelector是readonly GoalSelector，Arrow.noPhysics是可写boolean，真实get/is/set方法保留。

归档5066项、9,361,875字节，包括完整矩阵XML、原始日志、五节点生成物、十份完整TS错误、1609个Python stub、前版TS错误、最小红绿和工具。生产JAR留在本机并按摘要/class绑定，不冒称归档内已有二进制；不含world、properties或完整参数，实际RCON密码UTF8/UTF16字节已脱敏。复核：`python docs/architecture-refactor/evidence/2026-10-24-inherited-bean/verify.py`。

旧975候选的客户端/TCP/回退证据仍绑定旧制品，本次没有声称重新完成那些实机流程。87baad4b的[原政策正式性能](../2026-10-24-inherited-bean-performance/README.md)已独立完成：重载287.4/305.6ms仍FAIL、启动28014.8ms通过，17有效记录/9正常停止。整票仍待完整严格TS、性能与真实维护者发布结论，当前版本号未切换或发布。
