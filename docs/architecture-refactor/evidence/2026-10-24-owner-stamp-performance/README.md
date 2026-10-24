# 6e43b827原政策正式性能

本轮生产源码6e43b82789a7dbf7b9659dc1c21fefecac662649，在独立性能checkout执行原source runServer采样器。1642个common/node class与五节点安装候选逐字节一致；整个JAR SHA256为7b0dd1544837cf86ba35ccc4817da40cde54ff7c07544fc3b25fd5437027e8ae。采集期间没有其他本任务游戏、构建或测试任务。

| 组 | 全部正式样本ms | 均值ms | 原门槛ms | 结论 |
|---|---|---:|---:|---|
| reload-1 | 306,332,299,208,296 | 288.2 | 285.3 | FAIL |
| reload-2 | 292,311,312,296,286 | 299.4 | 285.3 | FAIL |
| startup | 25871,26480,25815,26686,26022 | 26174.8 | 41173 | PASS |

启动另有两次预热，合计17条有效记录、9次RCON正常停止0，无超时或强制清理。重载保持命令到原ASCII marker的口径、每组五次和3秒间隔；没有改采样器、fixture、日志刷新、完成时机或门槛，没有剔除208ms低样本或其他样本。两组均失败；不能把接近门槛、与上次不同或局部属性查询微测量约减半当作正式通过或端到端因果证据。

Windows原采样器SHA256保持1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4，Git LF blob与CRLF工作文件分别记录并规范化核对，执行包装器每组前独立核对摘要。收集器exit0表示三组收集完成；判断工具exit1表示有效样本下NOT_PASS，不能混淆。

归档68项、18,168,257字节，包含确切JAR、完整class绑定、全部样本、环境和原日志、原采样器/fixture及准备/执行/判断工具。47个文件中实际测试RCON密码的UTF8/UTF16字节已脱敏，原摘要和精确替换范围保留；敏感源副本可从Git和换行规则独立重建核对。world、properties和完整参数不在归档。

复核：`python docs/architecture-refactor/evidence/2026-10-24-owner-stamp-performance/verify.py`，实际PASS，复算仍为FAIL/FAIL/PASS。完整严格TSC仍5537条FAIL；票35/37保持in-progress。客户端渲染、跨进程TCP及分离回退的既有验收仍绑定975，不冒称本轮6e二进制已执行那些流程；维护者结论未代签。
