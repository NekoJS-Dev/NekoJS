# 87baad4b原政策正式性能

本轮继承属性声明修复后的生产源码87baad4b，在独立性能checkout执行原source runServer采样器。1641个common/node class与实际构建JAR逐字节一致，JAR SHA256为f58385eff396e6ddcf73dccebd5199e68ddaa228f2796fe31a9042e7a13c2f2e，和五节点安装批次中26.1.2制品相同。采集期间没有其他本任务游戏或编译测试任务。

| 组 | 原始正式样本ms | 均值ms | 原门槛ms | 结论 |
|---|---|---:|---:|---|
| reload-1 | 277,300,282,297,281 | 287.4 | 285.3 | FAIL |
| reload-2 | 262,282,279,394,311 | 305.6 | 285.3 | FAIL |
| startup | 28538,27442,27658,28668,27768 | 28014.8 | 41173 | PASS |

启动另有两次预热，合计17条有效记录、9次RCON正常停止0，无超时/强制清理。重载保持发送命令到原ASCII marker的时间口径、每组五次及3秒间隔；没有改采样器、fixture、日志刷新、完成时机或门槛，没有剔除394ms样本。两组均FAIL，第一组接近门槛也不四舍五入判绿。声明表示修复未改运行时重载路径，不据这次与旧测量的差异宣称性能因果改善。

Windows原始采样器SHA256仍1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4；Git LF blob与CRLF工作文件分开记录并规范化核对，执行包装器每组前独立核对原始摘要。本次没有早期LF/CRLF误比较失败。

归档68项、18,163,375字节，包含真实JAR、完整class绑定、17条原样本、原日志和环境快照、采样器/fixture源及准备/执行/判断工具。47个文件匹配实际测试RCON密码的UTF8/UTF16字节已脱敏；原摘要和精确替换范围保留，敏感源副本可由本仓Git源码和换行规则独立重建校验。world、properties及完整参数不在归档。

复核：`python docs/architecture-refactor/evidence/2026-10-24-inherited-bean-performance/verify.py`，PASS仅确认封存完整且复算为FAIL/FAIL/PASS。完整严格TSC仍5549条FAIL，票37仍in-progress；客户端/TCP/分离回退的既有独立证据继续绑定975制品，不因本轮源码改变而自动改写其摘要或候选范围。
