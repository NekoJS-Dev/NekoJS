# 7d模块内部边界诊断

在[入口边界诊断](../ticket37-reload-execution-7d/README.md)结束并恢复源码后，预声明一组五重载，区分路径解析、模块准备及实际executor。基点精确7d77308b，五文件临时patch SHA256 `a112d45348822ee45d0e4696691e0dc5e572e91f679b1114201841f68bd594d8`。原sampler/fixtures/JDK/cache/端口及路径/reparse/process保护不变；所有工作和安全检查保留，主工作区生产源码未动。

| 六入口合计，五次均值 | ms | 边界 |
|---|---:|---|
| 解析 | 29.93672 | resolveEntryPath |
| 依赖刷新 | 0.52146 | markEntry及refreshPreparedExecutionTree |
| 准备cache | 37.71462 | preparationCache.prepare，包含信任/读/编译/映射 |
| 准备观察 | 0.3501 | observePrepared |
| guest executor | 13.753 | executor.execute，包含new Function及函数体 |

这些区间在每个入口内没有嵌套，六个值可求和。全部对应的原始时间点、path、索引和逐项计算见[summary](summary.json)。这组模块总区间84.02122ms；未把未覆盖差额归为某个原因。结果不支持把全部模块耗时归为Graal执行；下一步拆分key/approval/read/pipeline/publish。同步打印观察成本未量化，实际executor也不是纯guest函数体时间。

原五marker307/253/360/372/363ms，均值331.0；RCON213.2、root代理118.05378，RCON与marker差额117.8。这是diagnosticOnly，原JSONL的formal字段不构成官方认证，[正式341.6/315.6FAIL](../ticket37-node-assert-performance/README.md)仍保留。Done前初始root和后续五个RCON调用均保留，没有删首值、重采样或修改门槛。sampler/runner/前后guard直接0，正常RCON关闭、端口空闲；五文件已逐字恢复，checkout干净。编译cache的十一项诊断差异仅属于五个插桩文件类家族；1581个其余class等于官方JAR，需重编译才能再认证官方字节。

[不可变ZIP](reload-module-evidence.zip)50entries/354704bytes，SHA256 `8213171280cc1fee52245c5beaed73085652e875d2caf4eb4d1ea24b697cdaea`；[独立验证器](verify.py)从原日志重算所有边界/六入口/五原索引，校验完整hash、源码patch、直接退出及恢复。实际凭据扫描PASS，含默认测试认证的sampler只保留SHA，不归档properties/world/rawJFR/二进制/用户资料。准备helper会重写本地前一级runner以适配新诊断目标；每组实际使用的helper已分别冻结，不用后续本地helper代替历史版本。

正式重载、完整IDE/Pyright、当前剩余多人/下载/首像素/跨节点/领域/磁盘及真实维护者/发布回滚仍未完成。总体NOT COMPLETE、自动续跑暂停、工具权限字段问题未解决、A22/A28及发布边界不变。
