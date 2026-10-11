# Python 基类顺序修复：实机 MRO 错误消失，完整 IDE 仍失败

固定基线 `e343563c1d2db6df45046cf52e802238b27ac3e1`，单生产文件补丁 SHA-256 `9a6cdb1efa97235dca6888a21e7b5c84801631d7da9e5dd8e15260fa3e395f97`。构建时源码尚未提交；提交与精确官方JAR映射另见 DELIVERY.md。整体 **NOT ACCEPTED**。

Python 直接基类列表现在按已收集、编辑后的 IR 关系，把后代排在其祖先之前；保留全部原先可发射的 superclass/interfaces，不删除基类或修改 IR。可选候选仍按原声明优先级选择。遍历迭代且带 visited，遇缺失/隐藏声明停止；双向循环关系不用于推断顺序。没有扩大反射、collector、HostAccess/ClassFilter、路径权限，未改 runtime、manual、公共成员或添加 Any/排除。它修复直接基类的祖先顺序约束，不保证解决任意 C3 多继承冲突。

真实 JDK `AbstractInterruptibleChannel`/`Channel`/`InterruptibleChannel`/`Closeable`/`AutoCloseable` 完整 backend RED：1测试/1失败，完整 basic Pyright 1项“Cannot create consistent method ordering”。调整仅声明顺序后 basic 退出0；完整 strict 从14错误降为13，全部剩余 unused imports 保留。只检查正向 caller 在 RED 时已经能通过，不能作为修复验收。修改后的正向 caller0错误，错误成员调用拒绝3错误。

| 检查 | 结果 |
|---|---|
| 聚焦 backend/renderer/Bean | PASS：54测试、0失败；包含编辑后传递关系、missing/hidden/cycle边界 |
| Standards / Spec | 0 / 0 剩余发现；注释P3已纠正并冻结预审源码记录 |
| common/check、隔离、processor、guard、NBT smoke、五节点 build | PASS：800 XML、4736测试、271跳过、0失败，109任务，38执行/71 up-to-date，3m13s，直接退出0 |
| npm 严格声明 / 平台门禁 | PASS |
| 完整 JDK basic fixture | PASS：8份stub+caller，0错误 |
| 完整 JDK strict fixture | FAIL：13 unused-import错误，未排除/忽略 |
| 五节点精确 JAR 实机生成/注册/放置/重载 | 支持子集PASS，旧版8项不可用反射类型仍PARTIAL |
| 26.x NeoForge流体身份/MobEffect重载前后施加 | PASS |
| 全 Python AST / TS parse | PASS：1600 / 1675文件，0语法错误 |
| 全默认 editor includes严格TSC / 全stub Pyright | FAIL：TSC退出2/Pyright退出1；实际结果保留 |
| 本源码正式性能、客户端/视觉 | NOT RUN；历史失败保留 |
| 人类维护者结论 / 发布 | NOT RECORDED / 未授权发布 |

| 节点 | TSC SERVER / STARTUP | Pyright错误 | 对e343删除 / 新增诊断 |
|---|---:|---:|---:|
| 1.21.1 | 768 / 769 | 8 | 10 / 0 |
| 26.1.2 | 942 / 943 | 8 | 6 / 0 |
| 26.2.0 | 827 / 828 | 8 | 6 / 0 |
| 26.1.2-fabric | 1306 / 1307 | 14 | 7 / 0 |
| 26.2.0-fabric | 1046 / 1047 | 14 | 7 / 0 |

36条消失的完整 Python 诊断全是继承顺序错误，新增0；TSC身份删除0/新增0。生成路径和旧版遗漏身份保留，九组历史比较完整。剩余 Python 包括 typing Callable/同包Java类冲突、真实字段与同名方法重复声明；没有把较低错误数量当整体通过。

五个本次实际PID24944/25028/25040/25024/25356全部RCON正常关闭、直接退出0，十端口空闲。五个世界与NekoJS JAR重新准备；Fabric仅复用已停止专属环境原版缓存并记录SHA，没有停止用户进程。完整矩阵正常结束前曾计划线程诊断，但测试进程已退出，身份保护检查拒绝附加，无线程快照或其他进程操作。

失败控制保留：最早四类型夹具缺少Closeable，修正五类型仍真实RED；审查只纠正排序优先级注释，预审补丁174f5a8a及manifest单独冻结。主代理又在比较进程仍运行时提前调用归档，完整性assert在创建ZIP/manifest前阻止；随后验证失败FileNotFound。原wrapper最后读取日志的exit0不是验证退出0，另一次直接退出1的失败记录保留。最终实际观察比较进程退出0后才归档、校验成功，九个完整比较与已结束日志逐字节一致。

`python-heritage-candidate.zip`：3632条目、8086190字节，SHA-256 `d9df75c1f5b5f71c0b04c193c6bc0d0023d4dd09c2a0f95a85c0f4a46e5b03a1`。XML ZIP SHA `b147a4e8586ccd666b7eeb1d7006a9b2121371d2c2681b74404f488155e44e17`。逐项hash/readback、完整比较/日志/源码补丁一致性及真实RCON密码扫描PASS。归档程序自身运行console明确排除，完成后保留于packaging-console.txt；没有world、凭据、账户、库/JAR或原始JFR。

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
```

实际生成、whole compiler/caller、实机、默认IDE和比较脚本/输出在ZIP。没有golden变更。整体15/16/22/28/34/37未完成，38仍可选；完整IDE、当前性能、FS间歇失败、native崩溃/首帧、pack下载窗口、领域/跨节点视觉、维护者/发布回滚均开放，自动续跑保持暂停。
