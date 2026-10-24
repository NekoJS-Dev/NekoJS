# 当前正式性能之后的source map调查

本项没有实施生产缓存。确切6e43b827的[原政策正式结果](../architecture-refactor/evidence/2026-10-24-owner-stamp-performance/README.md)为重载288.2/299.4ms FAIL、启动26174.8ms PASS；原采样器、fixture及285.3ms门槛保持。后续[十轮分段诊断](../architecture-refactor/evidence/2026-10-24-owner-map-diagnostic/README.md)已实际完成，不能替代该正式结果。

Luna xhigh只读追查得到：NekoModulePipelineCache.openSession每代创建新的SourceMapRegistry，prepare发布映射，register调用parse；同一root中下一代即使编译cacheKey相同，也没有显式共享解析结果。候选失败关闭本代映射，不能把失败代的诊断状态发布到活动代。当前registry是查映射的注册表，不等同于成功解析结果缓存。

## 已测量与仍未证明的范围

十次真实COMMIT与十次交替查询记录60个事件，每轮六次解析、六组路径/raw SHA256/cache revision/行偏移均在十轮重复。每轮parse合计均值2.80407ms、最大7.3132ms；外层generated path规范化均值0.0356ms，全部样本保留。CPU计数较粗，未从零值推断零开销。该量级不能证明增加root memo能解决正式门槛，当前不新增缓存。临时源码、1642生产class及整个JAR均已恢复原字节，一次RCON正常停止0。

1. 当前真实fixture的重复reload确有相同raw map、生成路径与行偏移反复解析，但可节省的端到端收益未测出，不能从总parse时间直接相减。
2. 当前六键重复已证实；不同插件和脚本集合的基数、无效map与大小仍未测量，当前不新增memo。
3. 源码确认构造器对root做真实路径规范化，map内URI/Path只做词法操作，不是授权或文件存在性边界。空值/非对象静默回退，sections和解析异常当场告警，非法VLQ字符忽略为无映射段。失败结果复用或延迟解析仍可能改变告警时机；当前读取/信任/真实路径检查必须照常执行。
4. 原始marker度量中其他开销仍可能占主要部分；不能仅凭局部微测或旧约4ms概数宣称正式门禁已解决。

975的30轮诊断只有43个execution/native样本，source map register只见一次栈命中；更旧1b406626记录也没有配对同一map/key。当前诊断已记录路径/map哈希、行偏移、调用次数、elapsed/cpu及基数，没有记录源码正文或凭据；没有专门触发无效map，不能把成功COMMIT当作所有解析格式的独立验收。JSON/path/VLQ子步骤和分配量仍未分开测量。诊断与正式样本分开，原始JFR留本机、归档派生记录和其原摘要，不能冒称归档足以独立重新解析原JFR。

若证据支持复用，设计边界才是runtime-root拥有、有界、仅保留不可变的成功解析结果；每代映射注册、当前编译器/绑定校验、文件读取与授权、安全真实路径检查照常执行。不能仅按路径或cacheKey缓存，必须保留raw map、生成路径/URI、行偏移、根路径和大小写语义；动态等长同mtime源码变化仍由内容哈希失效。无效map保持当前同步诊断，root关闭、按类型清理和路径失效不能留下不可控引用。不跨root共享、不复用guest Context或改变初始化/失败时机。具体缓存形状与上限尚未选定。
