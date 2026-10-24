# 6e43b827的配方重载分段诊断

原性能fixture、专用性能checkout和同一生产源码6e43b827。主代理临时修改RecipeManagerMixin、增加一个common诊断事件类，十次无错误COMMIT与十次交替查询后一次RCON正常停止0。每次reload COMMIT恰好观测到一次配方apply、每遍1515条输入/条件过滤后JSON/最终JSON/成功解码；没有在本夹具中观测到数据包重载导致的第二遍apply。十条list查询没有被当成reload或apply。

| 阶段 | 十轮墙钟均值ms |
|---|---:|
| 工作集重建及条件过滤 | 1.66348 |
| RecipeEventJS创建及JSON深拷贝 | 1.93726 |
| 配方前置hook与事件 | 0.02335 |
| 全部codec解码 | 37.52728 |
| RecipeMap构造及一次引用提交 | 1.04497 |
| afterRecipes事件与hook | 0.02443 |

保留每轮全部值及10条原始阶段事件，未删除首轮或最大值。另保存26个落在配方pass时间窗内的Server thread执行/原生采样栈；栈可能截断，1ms请求周期不代表每毫秒一个样本，次数不是毫秒或精确占比。没有把这些数字当成正式性能或直接可扣除收益。

Luna xhigh只读核对本地Minecraft源码jar的RegistryOps、HolderLookup、Recipe和Ingredient：createSerializationContext走Provider overload，HolderLookupAdapter以ConcurrentHashMap.computeIfAbsent缓存同一registry key的RegistryInfo/缺项。当前mixin每遍最终decode延迟创建一次ops，Recipe/Ingredient codec本身也为静态对象。这条路径已经复用元信息；没有证据支持再加查找缓存。Ingredient/custom ingredient实例保有可变值及懒状态，不能由此跨代复用解析后的Recipe或跳过事件/解码。

源码已恢复，临时helper已按原路径/摘要核对后移除。原1642生产class和整个JAR逐字节恢复至正式候选，SHA256仍7b0dd154…。生产分支没有加入诊断类、缓存、流程跳过或时机变化。条件性的目录数据包双apply仍需单独夹具验证，本轮单遍结论不推广到其他pack路径。

封存32项、746,645字节：完整控制器/服务端日志、两个源文件的原字节/补丁或新增源、class绑定、阶段和CPU派生事件、全部分析工具及恢复结果。原始JFR只留本机并记录摘要，归档可复算派生值，不能据此重新解析未归档JFR。world、properties、启动参数全文和诊断JAR不入档；按实际测试凭据的UTF8/UTF16字节脱敏，实际替换范围和原摘要见redactions.json。工具派生最初遇到参数引用和缺少旧build-v2日志的错误，尚未生成ZIP；修正后封存，未重采样或删除原样本。

`python docs/architecture-refactor/evidence/2026-10-24-owner-recipe-diagnostic/verify.py`实际通过。CPU JSON也有与短测试密码字节相同的内容，按统一策略替换；其原摘要与analysis中的原始CPU摘要绑定，不把脱敏后字节误说成原字节。本机验证额外读取七个已有原文件，按记录的偏移逐个重建脱敏结果核对；缺少原文件的环境只验证其原摘要声明与封存绑定，不能假称重验未归档原文。首次验证在这个原始/脱敏后摘要区别上失败，修正为核对原摘要及精确转换后通过，未重封归档或改动测量数值。原政策结果仍为重载288.2/299.4ms FAIL、启动26174.8ms PASS，完整严格TSC5537条FAIL；本轮不关闭票35/37，不代签、不push或发PR。
