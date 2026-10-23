# 方法泛型上界赋值与分组

票37的严格声明后续，基线 `d9519e02`。主代理实现与验证；Luna xhigh只读审查。接续[类级完整约束](2026-10-23-class-bounds.md)，补齐现有方法IR的bounds处理。

`ProbeAssignTypeEventJS.applyMethod`现在遍历已有typeParameterBounds槽；类和方法使用同一私有约束渲染规则，每个多界操作数均分组。有效签名依赖仍由现有TS闭包收集，不新增扫描通道或泛型猜测，不改运行时、wire、数据、公开成员或exclude/hide。

新测试在正式TypeScriptProbeBackend产生三套声明：plain、assigned、union。实际Graal调用保留主界/尾界成员。初次3项Java中2项失败；严格TS的assigned有2条错误，union有1条TS2578，plain通过。修复后各严格消费者通过，已接入完整test:probe-types链。

首次完整common检查2432项中1项失败，为既有继承测试预期未分组的精确文本；保留初次日志和XML，随后仅把该一行更新为分组后文本。原Comparable类型关系、实际Graal调用与严格消费断言保持，未删测试、修改声明golden文件或弱化门禁。后续62项定向检查通过。

两轴最终审查：Standards零硬违反/零气味，Spec零偏差。构造器不是新增承诺：reflectConstructor原本不收集自身类型参数，其局部变量经ConstructorInputTypes投影首界。这个预存限制作为独立后续，不能据applyMethod共用代码称为完整构造器泛型已修复。

2026-10-24复核最终完整矩阵：5149 tests / 271 skipped / 零失败错误；common/check含隔离、处理器、guard及五节点build/platform/artifact均通过，3m59s，106 tasks / 34 executed。完整test:probe-types链再次通过。本片段新JAR安装、全图IDE及正式性能尚待捕获。最近冻结的完整严格TSC仍是6e7452b7的5565条失败、正式重载仍7bea6d6d的340.0/292.8ms失败。没有PR、push、发布或人工结论代签。
