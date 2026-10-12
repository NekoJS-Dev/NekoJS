# 7d脚本执行边界诊断

承接[前一组阶段测量](../ticket37-reload-phase-7d/README.md)，预声明一次五重载诊断，基点仍为精确7d77308b，加四文件临时时间点 patch `26eac8dfc7998bce7b1c4c24392d7155ea7bbd27258be86d3b94db023c6a01f3`。原 sampler、fixtures、JDK、cache、端口、路径/reparse/process保护和所有工作保留；主工作区生产源码没有变化。同步输出观察成本未量化，诊断不能替代正式验收。

| 五次均值 | ms | 实际边界 |
|---|---:|---|
| before hook | 0.042 | fireBeforeScriptsLoaded |
| after hook | 0.0276 | fireAfterScriptsLoaded |
| preload | 1.38634 | prepareScriptsForLoad，含属性读取和排序 |
| 六个入口合计 | 112.88368 | executeEntry开始到watchdog disarm后 |
| 六个入口校验合计 | 6.37814 | 文件读取及validateGlobalBindings |
| 六个模块合计 | 105.08898 | waitForEntryModule，含解析/准备/执行/等待 |

这些观测支持继续区分模块准备与实际guest executor；不证明全部105ms是编译或eval，也不支持删校验/hooks。root-through-manager代理154.90684ms、RCON273.0ms、marker302.0ms。原五marker值361/288/259/377/225全部保留；[正式341.6/315.6ms失败](../ticket37-node-assert-performance/README.md)未改变。原JSONL仍写formal，但必须按本页patch判为diagnosticOnly。

保留一个Done之前的初始root及五个RCON调用。第三次入口在00:48:06.983、marker在00:48:07，首次读取器同秒假设失败；原helper和失败输出已冻，修正为marker时钟必须落在对应root开始/结束的秒范围，没有重采样或删值。sampler/runner/前后guard直接0、正常RCON停服、端口空闲。四文件逐字恢复、checkout干净；编译cache仍含九个诊断class差异（仅四文件类家族），1583个其余class等于官方JAR，不冒称官方字节验收。

[不可变ZIP](reload-execution-evidence.zip)45entries/269618bytes，SHA256 `e5c1011ef072af7236d75f6e403de76412fabf0df9b069c8e5b315325cd9843f`。[summary](summary.json)保留全部事件/阶段/源及编译绑定，[独立验证器](verify.py)重新读取原始时间点、六入口及五索引计算。实际凭据扫描PASS；原sampler含默认测试认证值，仅留SHA，不归档properties/world/rawJFR/二进制/用户资料。

总体NOT COMPLETE。完整IDE/Pyright、正式重载性能、当前剩余多人/下载/首像素/跨节点/领域/磁盘及真实维护者/发布回滚仍有缺口。自动续跑暂停、工具权限字段问题未解决，A22/A28及发布授权边界不变。
