# 6e43b827模块准备分段诊断

本轮只在独立性能checkout临时修改NekoModulePipelineCache一个源文件。原fixture、source runServer、原COMMIT/marker完成时机不变；十次实际无错误COMMIT与十次交替list查询完成，RCON正常停止、进程exit0。它是带JFR和临时计时、一秒间隔的诊断，不是原五样本/三秒间隔正式验收。[正式结果](../2026-10-24-owner-stamp-performance/README.md)仍为288.2/299.4ms FAIL，285.3ms门槛未改。

| 成功prepare(Path)内阶段 | 每次重载合计均值ms |
|---|---:|
| key路径规范化 | 0.67305 |
| approvedSource授权/覆盖检查 | 1.27626 |
| Files.readString | 0.28346 |
| 捕获编译器绑定 | 0.08579 |
| 文件属性与mtime/size | 0.06048 |
| 内容SHA256 | 0.16237 |
| preparedCache.compute，含准备管线 | 1.62838 |
| 其中prepareSource管线 | 1.43257 |
| 映射发布与准备观察器 | 1.33772 |

60个事件均在Server thread、每轮六次，六个不同模块路径摘要各出现十次，缓存命中0。表中compute包含pipeline，不能重复相加；排除嵌套pipeline后的所列阶段合计均值5.50751ms。计时之间仍有未纳入表格的控制逻辑，事件写入也有开销，不能把该数当成整个执行阶段或可直接节省的量。

这些结果不支持以跨代源/路径/信任缓存解决当前正式差额，也不证明缓存未命中本身是缺陷。当前每次读取、内容哈希、编译器身份和revision、独立授权/覆盖检查都保留；没有删除重复校验、缓存可变Recipe/Context或改变失败顺序。事件只在成功prepare后提交，JSON路径、失败路径及其他调用者的规范化/授权开销未覆盖；本轮不能排除模块解析或沙盒文件系统的其他耗时。后续应据范围继续调查，不从这些小阶段推断全局耗时。

诊断编译通过，临时class数1643。退出后恢复原源文件字节，重建通过；整个checkout重新干净，全部1642个生产class和整个JAR与正式6e候选逐字节一致，SHA256仍为7b0dd1544837cf86ba35ccc4817da40cde54ff7c07544fc3b25fd5437027e8ae。没有向生产分支加入临时计时代码或新的性能缓存。

归档29项、332,302字节，SHA256为e3cd70ba76ce18f22f97d49e28fe6c9d61e9c980d2f1ff4d8aee51780fb43a36；包含原始源码、补丁、控制/分析/恢复工具、构建与停服日志、全部派生事件、每轮记录和class恢复绑定。原始JFR仅留本机，归档记录摘要，公开复核只能重算派生事件，不能代替从未归档JFR重提取。无world、properties或完整启动参数。六个文件中匹配实际测试RCON密码的UTF8/UTF16字节共替换97处，原摘要与字节范围保留；公开工具不含私密模式，不能独立重复完整凭据扫描。

`python docs/architecture-refactor/evidence/2026-10-24-owner-source-diagnostic/verify.py`重算全部60条事件/十轮分组与均值、核对正常停止和确切恢复绑定。Luna xhigh仅只读追查八个计划内及两个额外的小型信任路径定义，没有修改文件或运行检查；主代理实施插桩、构建、游戏运行、分析和恢复。完整严格声明、发布和维护者验收状态保持未完成，见[统一状态](../2026-10-24-owner-stamp-acceptance/README.md)。
