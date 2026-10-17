# 信任主体修复后的正式复验：重载仍失败

日期：2026-10-17。精确源码 `6e5bb8f00c3747b828056589853cb0471477b18a`。本记录补充[上一轮证据](../README.md)，不覆盖先前失败，也不表示性能目标完成。

## 正式结果

| 组 | 五个正式值（ms） | 均值（ms） | 原门槛（ms） | 判定 |
| --- | --- | ---: | ---: | --- |
| reload-1 | 623, 399, 430, 454, 426 | 466.4 | 285.3 | FAIL |
| reload-2 | 622, 378, 387, 478, 482 | 469.4 | 285.3 | FAIL |
| startup | 50731, 29529, 29210, 30115, 30968 | 34110.6 | 41173 | PASS |

预先指定两组各五次重载、一组两次预热加五次正式启动。沿用原采样器、fixtures、NeoForge 26.1.2、JDK 25.0.2、专属性能工作树、Gradle 缓存及端口；没有删首值、修改门槛、改变工作负载或为选择成绩重跑正式组。采样期间没有并行运行本任务构建、测试或其他游戏。全部会话有效、正常 RCON 停服，全部重载回执为 `phase=COMMIT - no errors.`。启动首值 50731 ms 仍保留并计入均值。

采样器 SHA-256 为 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`。采集前后检查 fixture 和编译类摘要，实际编译类与正式构建 JAR 绑定；计时仍是源码 `runServer` 路径。marker 沿用日志可见的墙钟差和 100 ms 轮询，不是引擎独占耗时。宿主系统缓存与其他进程没有完全受控，不能把不同会话的分数差全归因于代码。准备时一次错误的手写提交号被检查拒绝，尚未创建采样目录；随后解析真实完整提交号，仅采集上述一套正式组。

## 修复和合同

本地信任批准现在绑定到已经执行保护根检查的规范主体，避免第二次规范化遇到符号链接重定向后，为不同主体签发本地批准。后续 `covers`、读前及读后复验继续执行，每次授权重新观察路径；不缓存跨调用的信任结果，不承诺文件句柄级原子读取。Script API、Plugin API、wire 和持久数据格式未变。

两项回归覆盖主体切换和下一次批准重新观察路径。修复前确实有一项失败，修复后两项均通过；失败 XML 与日志保留。精确旧/新类同 JVM 十对交替组件测量，每轮六文件并执行后续 `covers`，均值 13.2783 → 7.6727 ms，约节省 5.6056 ms。组件收益不能替代上表正式失败。组件准备首次误将 JSON 配置计为第七个入口，在计时前被断言拒绝，修正只筛选 JS 后测量；失败记录保留。

## 验证

- `gradlew.bat --offline :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`：PASS。
- 冻结的测试 XML 合计 4959 tests、0 failures/errors、271 skipped。两项新信任回归均实际执行。
- `:26.1.2:runGameTestServer`：PASS，实际出现 `All 1 required tests passed :)`。
- `python tools/nekojs-ci-gates.py all`：四项 PASS；`git diff --check`：PASS。
- 本次没有声明/API 变化，未运行 TypeScript 声明检查。真实客户端、脚本包完整验收和维护者人工结论仍待完成。

## 独立诊断

归档还含同源码、相同文件内容在 C 盘性能路径和新建 D 盘诊断目录的组件对照：解析约 6.28 / 2.56 ms、批准及 covers 约 6.53 / 2.88 ms、文件系统校验约 8.68 / 3.67 ms，均为每六文件。相对差明显，但绝对收益有限；没有移动正式环境或把诊断成绩当正式通过。

## 复核

[evidence.zip](evidence.zip)：928 条目，2308125 字节，SHA-256 `b22f7876f7731cc152774b6361e7c61f16eecbeb9edafc939993afd57b51b628`。[manifest.json](manifest.json) 绑定全部长度和摘要。归档含正式样本与日志、采集绑定、回归红绿结果、冻结 XML、组件测量及工具；不含 world、server.properties、凭据、原始未筛选 JFR 或 JAR。与 RCON 凭据相同的字节已替换，替换次数和原始摘要在归档 `redactions.json` 中，数值样本不变。

```powershell
python docs/architecture-refactor/evidence/2026-10-17-reload-performance/trust-6e5bb8f0/verify.py
python docs/architecture-refactor/evidence/2026-10-17-reload-performance/trust-6e5bb8f0/verify.py --require-performance-pass
```

第一条应退出 0，只证明归档完整及判定可重算；第二条应退出 2，明确表示性能未全通过。离线验证不能重新证明当时环境或已删除的二进制制品。
