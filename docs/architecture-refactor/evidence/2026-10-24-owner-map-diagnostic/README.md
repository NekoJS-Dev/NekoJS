# 6e43b827的source map分段诊断

主代理在专用性能checkout临时修改一个SourceMapRegistry源文件，用原性能fixture运行十次实际无错误COMMIT和十次交替查询，一次RCON正常停止0。记录60个JFR事件，每轮恰好六次同步解析；六组generatedPath/raw map SHA256/cache revision/行偏移在十轮均重复。临时类增加到1643个；结束后源码、全部1642个生产class及整个JAR恢复为正式6e候选确切字节，JAR仍为7b0dd154…。

每轮六次parse合计均值2.80407ms，最高7.3132ms；外层generated path词法归一化合计均值0.0356ms。原始每次调用和每轮全部样本保留，没有删除首轮或最大值。CPU计数在Windows上较粗，零计数不代表零开销。计时包含JSON、map内路径归一化和VLQ解析；原map摘要在计时结束后计算，映射注册和revision写入照常执行。该测量不区分其中各子步骤或分配成本，也没有测出可直接扣除的端到端收益。

当前重复键得到证实，量级仍不足以证明新增root缓存能解决原政策失败，生产没有新增缓存。构造器对root做一次真实路径规范化；map解析中的URI/Path操作是词法处理，不承担授权或文件存在性检查。当前源读取、信任与真实路径校验仍在模块准备和后续诊断原位置。失败map告警时机、每代映射和候选失败清理保持现状。

这是有临时事件和JFR、十次/一秒间隔的诊断，不能当成原五样本/三秒间隔正式验收。[正式结果](../2026-10-24-owner-stamp-performance/README.md)仍为288.2/299.4ms FAIL、启动26174.8ms PASS，门槛保持285.3ms/41173ms。

首次启动准备在PowerShell参数解析处失败，尚未编译诊断源或启动游戏，保留build.log；修正参数引用后build-v2通过。完整控制器、服务端日志、临时源补丁、60个派生事件、分析工具与恢复绑定封存30项/325,674字节。原始JFR仅保留本机，归档记录摘要；world、properties、完整参数及诊断JAR不入档。归档可复算派生记录和恢复绑定，不能代替从本机原JFR重新导出。实际测试RCON密码字节按UTF8/UTF16脱敏。

`python docs/architecture-refactor/evidence/2026-10-24-owner-map-diagnostic/verify.py`已实际通过。维护者及发布结论未代签，没有推送或PR。
