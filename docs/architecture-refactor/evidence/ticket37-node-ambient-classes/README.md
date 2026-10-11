# Node ambient class：源码修复及精确 JAR 验收

源码提交 `f84e2adbc36cac31ec8b2da8a448fbc0b8bbebae`，基线 `fbb3ea4a`。`NodeModuleTypeDocs` 将类放在 `declare module` 内，却再次生成 `export declare class`，导致 TS1038。两个共享分支现生成 `export class`；类头、成员签名、模块别名及 manual 生产器保留。未改变运行时、安全设置、默认 editor includes 或验收门槛；没有 golden 更新。

## 原始 RED 与回归

真实默认 includes、`skipLibCheck=false` 的主节点检查保留 942 错误，其中 4 条 TS1038。生成器回归实际 1 测试失败，双模块严格合法 caller 2 条相同错误；非法 caller 5 错误，另含 3 条真实类型拒绝。提出重复修饰符、模块消费不匹配、泛型/继承提取错误三个解释后，只修改修饰符。

修复后 18 个 focused 测试通过，合法 caller 2→0，非法 caller 5→3，原三条拒绝保持。真实旧 JAR 的 `registerBuiltin` 输出与新生成器输出逐字比较，除 4 处冗余修饰符外完全相同；完整 builtin 严格编译 18→14 错误，**仍 FAIL**。未手改生成输出。

## 构建及实机

`gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain` 实际 exit0：4742 测试、271 跳过、801 份 XML、0 失败，109 任务，2m59s。common 包含此前两个 field/method 事实测试；并非将旧节点 XML 拼到新测试结果。`npm run test:probe-types` 和构建后的平台检查实际 exit0；较早在构建期间执行的门槛日志单独保留，不代替构建后的记录。

五个精确新 JAR 在专属全新环境完成 Probe 生成、支持的注册/放置、重载；NeoForge 验证 fluid identity，26.x NeoForge 验证 MobEffect 重载前后实际施加。辅助证明脚本默认只选四节点的调用失误已保留四节点日志和 summary，随后单独补 26.2 并保留独立结果后合并五节点；没有重做四个成功节点。五进程正常 RCON 关闭、直接 exit0、十个端口释放。1600 份 Python stub AST 通过，全部 TS 声明 parse 通过；旧版八类遗漏仍 PARTIAL，未认证客户端或新增多人窗口。

| 节点 | 完整 Pyright 错误 | 完整严格 TSC server / startup |
| --- | ---: | ---: |
| 1.21.1 | 7 | 764 / 765 |
| 26.1.2 | 7 | 938 / 939 |
| 26.2.0 | 7 | 823 / 824 |
| 26.1.2-fabric | 13 | 1302 / 1303 |
| 26.2.0-fabric | 13 | 1042 / 1043 |

以上完整 IDE **仍 FAIL**。十个 TS 项目各移除四条目标 TS1038，新增诊断身份 0；其余 TS/Python 诊断身份及文件路径集合保持。采集器 exit0 不是编译器通过。

实机输出有既有排序及内部 Python 导入别名分配变化，**不能宣称所有文件逐字相同**。完整 manual 仅删除四个修饰符，其他改动经所属作用域 AST 核对：不同声明/配方名组可重新排序，同名 overload 顺序、class body 及每个载荷引用的限定 Java 身份保持。负控验证重排同名 overload、改变返回类型、错误导入目标会被拒绝。内部模块导入的本地名字稳定性不作认证，仍是确定性缺口。旧版遗漏身份与次数保持；IClientFluidTypeExtensions 的首次失败依赖由 Camera 变为 Minecraft，原始警告差异完整保留，不声称原因文本相同。最初的逐字/未解析别名/警告比较失败及审计修订均归档。

## 交付及剩余缺口

[两轴审查](REVIEW.md) Standards 0 硬问题/0 启发式问题、Spec 0 问题。精确五个制品 SHA、测试 XML、完整输出、日志、源码和失败控制见 [manifest.json](manifest.json) 与 [原始证据包](node-ambient-candidate.zip)：3558 条目、7697324 字节，SHA256 `847999ce70d4fe624695aab6ee4c9c32a540049aa8fe42b7075cf66a1d69aec5`。[独立校验](archive-verification.json)确认全部条目、提交源码、五个 JAR 的实际 class 字节、最终完整比较及完成日志匹配；实际 RCON 密码的嵌套扫描通过。未提交密码、world 或敏感原始 JFR。

[精确 f84 性能](../ticket37-f84-performance/README.md)通过：重载 237.4/224.8ms，启动 21833.2ms；历史 FAIL 保留，无因果提速声明。总票据仍 NOT COMPLETE：完整 TS/Python、字段/同名方法、内部名字确定性、FS/native/fireResistant、剩余领域、多人与资源下载窗口、第一帧/视觉、发布/回滚和真实维护者结论尚有缺口。维护者 NOT RECORDED，自动续跑 PAUSED，工具权限字段入口未解决；无发布或扩大 A22/A28 删除范围的授权。
