# 7d模块准备边界诊断

承接[模块内部诊断](../ticket37-reload-module-7d/README.md)，预声明一组五重载，区分canonical key、approval、read/stamp、pipeline和publish。精确7d基点，加六文件patch SHA256 `733848bb07053860d8e09ea3b12ba310c645f9b9d3ba9e333ba03184ccfa9cf2`。原sampler/fixtures/JDK/cache/端口及路径/reparse/process保护不变，没有删除或替换工作、安全/信任检查或cache；主工作区生产源码未动。

| 六入口合计，五次均值 | ms | 实际范围 |
|---|---:|---|
| canonical key | 4.05718 | ensureOpen及key(path) |
| approval | 10.68558 | approvedSource，包括实际批准及covers |
| read/stamp | 2.59102 | readSource，含读取/语言绑定/stamp |
| pipeline | 24.96688 | prepareSource，含covers及编译/校验 |
| publish | 11.82366 | publishSourceMap |

本组路径解析40.45028ms，prepare cache56.6165ms，guest executor17.00224ms。各均值是同一组内边界，不与前两组拼成一个样本。pipeline仍含信任covers，不是纯编译；publish内部和解析内部尚未细分，不能把全部归因于某次toRealPath。同步输出与type分类观察成本未量化，不宣称某阶段可安全删除。

五marker356/392/304/345/349全部保留，均值349.2；RCON275.2、root代理164.00024。原JSONL仍由不改动的sampler写formal，本组以sourcepatch判为diagnosticOnly；[正式341.6/315.6FAIL](../ticket37-node-assert-performance/README.md)未改变。一初始Done前root及五RCON调用均保留，sampler/runner/前后guard直接0、正常RCON停服、端口空闲。六文件逐字恢复，owned checkout干净；1573个编译class等于官方JAR，十九诊断差异仅属于插桩文件类家族；编译cache不能继承官方验收，需重编译。

[不可变ZIP](reload-preparation-evidence.zip)57entries/477870bytes，SHA256 `9684b5a11a51e88e4c110dd23d4913bffec9affe039819a6d22e8b2a1de5903a`；[summary](summary.json)与[独立验证器](verify.py)绑定全部raw事件/六入口/五索引/patch/hash/直接退出/恢复。实际凭据扫描PASS，含默认认证值的sampler只留SHA，不归档properties/world/rawJFR/二进制/用户资料。逐组实际helper冻结；后续本地helper适配目标的重写不替代此前冻结版本。

下一步在保留每次路径/信任核验的前提下判断是否有可复现的共享冗余；没有已确认修复，不创建未证实的cache或修改正式指标。完整IDE/Pyright、正式重载、当前剩余多人/下载/首像素/跨节点/领域/磁盘及真实维护者/发布回滚仍未完成。总体NOT COMPLETE、自动续跑暂停、工具权限字段问题未解决、A22/A28及发布授权边界不变。
