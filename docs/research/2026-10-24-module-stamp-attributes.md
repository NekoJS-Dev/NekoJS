# 合并模块stamp的重复文件属性查询

Luna xhigh只读调查后，主代理追查了NekoModulePipelineCache.prepare/readSource/FileStamp.read及其调用、缓存失效与错误阶段回归。975候选的实际execution采样两次命中`Files.size → readAttributes → FileStamp.read`；总共只有43个execution样本，不能从采样次数推算耗时或断言这是正式失败的主要原因。

窄修改是将连续的`Files.getLastModifiedTime`、`Files.size`换成一次默认跟随链接的`Files.readAttributes(path, BasicFileAttributes.class)`，从同一份属性得到mtime和size。每次准备仍先读取源码、捕获编译器绑定，再查询属性；仍比较源码哈希、时间、大小、language/mode身份、registry revision，不跨重载缓存路径、内容或权限，也不省略最终真实路径检查。相邻两次属性查询改为一次一致读取，减少的是重复查询；并发修改的文件仍非原子快照，未承诺新事务保证。

使用已有公开cache准备接缝的真实文件/内容覆盖/同mtime等长度/编译器替换/错误阶段/信任回归验证合同，不为“某内部方法被调用几次”新增实现耦合测试。源码固定到6e43b827后的[完整5158项检查及五节点安装](../architecture-refactor/evidence/2026-10-24-owner-stamp/README.md)通过；[原政策正式性能](../architecture-refactor/evidence/2026-10-24-owner-stamp-performance/README.md)重载288.2/299.4ms仍FAIL，启动26174.8ms通过。285.3ms门槛、原采样器、fixture与marker时机保持；没有用局部微测量替代端到端结果。

未合并预检和模块准备之间的两次源码读取；那会影响两次读取间文件变化及失败时序。未修改运行时owner、Context生命周期、source map、转译代码或插件初始化时机。未推送、发PR、发布或代签维护者结论。

主代理补充的配对微测量已实际完成：JDK25.0.2、原性能fixture的18个真实文件、3对预热后10对正式样本，每对交替先后顺序、每种形式每样本18000次完整查询。分开读取均值4873.64ns/次，合并读取2418.68ns/次，比例0.49628；26条原始记录的checksum一致，前后fixture SHA不变。工具、完整样本和环境在`build/priority-closeout/stamp-attributes-benchmark`及同级`run-stamp-attributes-benchmark.py`、`FileStampAttributesBenchmark.java`。此测量只隔离属性查询，不包含源码读取/哈希/编译，也不能推算整个重载收益或替代正式285.3ms门禁。
