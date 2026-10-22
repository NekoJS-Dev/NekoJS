# 事件依赖修复：五节点安装与完整 IDE 测量

生产源码 **`1f877b0f`**，安装准备时 checkout `6612cf05`（后续文档/派生测试记录）。五份 JAR 在 holder 修复前已复制并逐项绑定编译 class；本材料只证明这些固定制品，不外推为后续 holder 修复的最终候选。

五节点构建已通过：两个现代 NeoForge build 与普通五节点 platform gate 3m14s；另三个节点 build 4m56s。首次事件 golden 失败和 canonical 再生成见[节点复验](../2026-10-23-event-node-followup/README.md)，不以生成模式替代普通门禁。

全新五个安装服务端的行为检查均 PASS、五进程 RCON 正常停止并 exit0；包括初始/重载的集合实际转换、注册、实体/方块/适用流体、交易池、物理效果及声明生成。空服暂停显式关闭。源码 patch、各 JAR SHA-256、1569/1640/1640/1583/1583 个 class 绑定、脚本摘要与原始日志封存。旧暂停/实体查询失败的原因仍未据本轮通过而宣称确诊。

全部 **1609** 个新生成 Python stub 在默认 Pyright 1.1.414 下 0 error/warning。默认编辑器包括范围、`strict`、`skipLibCheck=false` 的十入口完整 TSC 均 exit2，无 checker crash：

| 节点 | server | startup |
|---|---:|---:|
| 1.21.1 | 514 | 515 |
| 26.1.2 | 577 | 578 |
| 26.2.0 | 568 | 569 |
| 26.1.2-fabric | 645 | 646 |
| 26.2.0-fabric | 622 | 623 |

合计 **5857 条，完整 TSC 继续 FAIL**；旧 `7bea6d6d` 为 5867，不能据少十条关闭门禁。来源被 exclude 的实际事件依赖仍不生成，完整输出不得用 any/unknown、虚构声明或删成员绕过。

本次服务器行为成功不替代[真实 TCP 已发现的断线缺陷](../2026-10-23-dynamic-commit-red/README.md)，也不覆盖新源码的客户端画面、分离回退或发布。最近正式重载 340.0 / 292.8 ms 仍超 285.3 ms 门槛；维护者验收未代签。

复核：`python docs/architecture-refactor/evidence/2026-10-23-event-installed/verify.py`。
