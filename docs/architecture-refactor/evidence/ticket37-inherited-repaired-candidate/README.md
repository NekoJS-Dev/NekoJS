# 第二轮继承方法候选：未接受

源码基点为 `e48ea68cafbde0007ac7271d99615c146199853b`，生产差异 SHA 为 `f4699c4941d8e22d02cef50ca64f3fdbeb291ff880263ab15c9cc138d57ae875`。这是未提交源码构建的精确五 JAR 候选，不能使用旧提交或第一轮候选的制品 SHA 代替。所有制品、测试 XML、真实输出和原始诊断由 manifest 定位。

归档 `inherited-repaired-candidate.zip`：3691 条目，8668435 字节，SHA256 `0e3584978b3c4fdb91eddada627d4facafc5edb2862f8dad4e0ff9c0771b6587`。已逐条读回校验并扫描专属 RCON 凭据；不含密码、world、用户账号数据、原始 JFR 或依赖/JAR 二进制。

串行完整命令 `gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain` PASS：800 XML、4701 测试、271 跳过、零失败；106 任务、2m13s。npm 两个 Probe 项目和平台 all gate PASS。节点测试在前一次矩阵执行，串行矩阵相应任务 UP-TO-DATE；common 在串行矩阵重新执行。此前三次失败均保留：首轮 common 的物理恢复 AccessDenied 与 golden/旧断言；两次重叠 Gradle 的 EOF/缺失中间结果文件。重叠由主线程提前启动复核造成，不能记成通过，也不据此解释独立的文件操作失败。

五节点真实启动、声明生成、注册/放置、重载均通过各自支持子集；NeoForge 26.x 效果重载前后实际施加 PASS。1.21.1 的真实客户端类型不可用窗口仍为 PARTIAL。五服均 RCON 正常关闭、直接 exit 0。1600 Python stub AST 与1675 TS 声明语法解析零错误。未验证这些新 JAR 的客户端、多人、正式性能。

完整默认 editor includes、仅 `skipLibCheck=false` 的严格 TSC **全部 FAIL**；Pyright 1.1.414 完整实际 stub 检查 **全部 FAIL**：

| 节点 | TS server/startup | Python 错误 |
|---|---:|---:|
| 1.21.1 | 1209 / 1210 | 22 |
| 26.1.2 | 1438 / 1439 | 19 |
| 26.2.0 | 1136 / 1137 | 19 |
| 26.1.2-fabric | 1766 / 1767 | 23 |
| 26.2.0-fabric | 1312 / 1313 | 23 |

相对第一轮候选，TS server 新增诊断身份为176/162/72/192/113，Python 新增身份为1/1/1/0/0；完整移除/新增明细在两个 comparison 文件。错误总数下降不是通过。生成路径没有丢失，但有新依赖包；不能沿用第一轮“路径集合相同”的结论。

此候选已经冻结为 **NOT ACCEPTED**。后续源码再次修改，不由本候选矩阵或 JAR 证明。新增 Python `TagEntry.tag`、继承真实方法与 Bean 合成名、函数式别名 Host 泛型约束已有新的实际 RED；接下来修复并重新构建、安装、生成。另一个新严格 caller 控制发现 Function.apply 能结构性冒充 Java SAM，已保留失败控制；正在用真实生产 ECMAScript `latest`、原 HostAccess/ClassFilter 和明确的 host/function 类型边界修复。维护者结论 NOT RECORDED，整体目标未完成，无发布授权。
