# Exact cb5f7fe6 formal performance plan

2026-10-09。精确提交cb5f7fe6a81d1415acff69c5e91ac430457385d5，复用已核对clean的专属perf-owned-ac810fa7 checkout，主stonecutter工作区不切换。此次预声明顺序固定：reload-confirmed-1（5次）、reload-confirmed-2（5次）、startup-confirmed（2次warmup+5次formal）。不根据结果追加替代分组、丢首样本、混revision、改阈值或解释历史PASS为新源码认证。

原sample.ps1 SHA256固定1F9C5D14A1A586DA4B3F1CA8E71B2407CE089EE8BEE185418963E1B5745DF0B4，原fixtures、节点26.1.2、startup41173ms/reload285.3ms门槛保持。synchronous PowerShell调用直接捕获每个sampler的LASTEXITCODE；outer exit另存。每组都复核exact HEAD、clean、sampler、专属进程、端口25871/25872以及run目标的绝对路径/祖先/reparse检查。所有旧证据不重写，游戏必须正常RCON关闭；失败或未观测退出不得当PASS。

当前全IDE仍FAIL，legacy Probe PARTIAL；先前文件系统间歇失败未修复、独立审查未完成。此计划只补正式性能窗口，不授权发布、清理无关数据、放宽安全检查或恢复自动调度。采样期间不并行构建/测试/游戏、不修改生产代码或sampler。
