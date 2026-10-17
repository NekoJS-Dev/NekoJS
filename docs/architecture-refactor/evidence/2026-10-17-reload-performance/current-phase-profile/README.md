# 当前完整游戏阶段诊断：不是正式通过

日期：2026-10-17。精确生产源码 `6e5bb8f00c3747b828056589853cb0471477b18a`，专属性能工作树临时插桩；NeoForge 26.1.2、原六个 server fixture、同一 JDK/Gradle 缓存/端口，二十条独立诊断命令、间隔三秒。没有新增或替换正式性能组。正式结论仍为[两组重载 466.4 / 469.4 ms，FAIL](../trust-6e5bb8f0/README.md)。

## 观察

| 项目 | 二十次均值（ms） | 每次调用数 |
| --- | ---: | ---: |
| root.reload | 104.386565 | 1 |
| 配方重应用 | 29.739470 | 1 |
| RCON 客户端至完整回执 | 209.150945 | 1 |
| Context 创建及 Node 安装 | 17.408770 | 1 |
| 全部内建资源清单处理 | 7.017670 | 1 |
| 其中：清单读取 | 0.489550 | 1 |
| 其中：JSX runtime eval | 0.881540 | 1 |
| 全部 Binding 安装 | 5.928950 | 1 |
| 其中：managed globals | 0.698815 | 1 |
| 六入口执行 | 71.804990 | 6 |
| 其中：入口解析 | 15.900390 | 6 |
| 其中：模块准备 | 31.921955 | 6 |
| 其中：guest 模块执行 | 18.252155 | 6 |
| 模块规范化 | 19.457035 | 24 |

这是包含关系，不得将父子项相加。17 个内建资源分别记录，均每 Context 执行一次。JSX 和 managed globals 的绝对成本不足以支持高风险的懒加载或新跨代缓存。模块规范化确为 24 次，符合信任批准修复去除六次重复规范化、仍保留读前后 covers 的调用链；没有删除其余校验。

前五次 root / recipe / RCON 均值为 121.28 / 40.46 / 242.84 ms，后十五次为 98.76 / 26.16 / 197.92 ms。二十条均 COMMIT、错误数为 0，正常 RCON 停服，没有强杀。JFR 命令窗口主线程 CPU/native 样本 113 个，reload 路径 park 记录为 0；采样次数不是耗时，也不能证明不存在短暂等待或调度延迟。

这些分段属于有插桩的另一会话，不是原 marker 采样：本轮没有测正式 marker 的日志可见时间，不能用 209.15 ms 宣布通过 285.3 ms 门槛。最新正式两组的 RCON 均值是 413.6 / 405.0 ms，其 marker 还分别多出约 52.8 / 64.4 ms。不同会话的宿主负载、JIT 和系统缓存未完全受控，不将这种差异归功于一个未经证明的修复。

下一步是独立观测命令到达、回执和 stdout marker 可见时间，区分游戏处理、客户端/调度及输出观察的成本。保留原正式工作负载和门槛，不为挑选成绩重跑无改动的正式组。

## 恢复与检查

11 个原文件已逐一按插桩前摘要还原，临时 helper 源文件删除，专属性能工作树干净且仍在精确 `6e5bb8f0`。重新执行 `gradlew.bat --offline :26.1.2:build --console=plain`：PASS。重新构建的 common 与该节点 main 共 1625 个类未发现 `ReloadPerfProbe` 或两种 DEBUG-CLOSEOUT 标记。

本次没有新的生产实现，因此未重新运行完整五节点回归；该源码已有[4959 项 XML、五节点和 GameTest 验证](../trust-6e5bb8f0/README.md)。资源关闭实机扩展、双世界切换、签名同步和 Fabric 目录资源桥尚未完成。

## 归档与复算

[evidence.zip](evidence.zip)：45 个条目、241874 字节，SHA-256 `7bd231561e4afa74a5e344ea27516594128eddb34eac3e7fd75f6afae25b8afe`。[manifest.json](manifest.json) 绑定原始命令回执、stdout/stderr、阶段数据、临时 patch、还原摘要、构建日志及工具。与 RCON 凭据相同的字节以占位符替换，原始摘要及次数保存在 `redactions.json`。没有提交凭据、world、server.properties、JAR、class 或任何 JFR 二进制；JFR 仅保留过滤后的文本统计。

```powershell
python docs/architecture-refactor/evidence/2026-10-17-reload-performance/current-phase-profile/verify.py
```

退出 0 只验证归档和诊断数据可复算，不证明正式门禁通过。

## 旧 JFR 归档限制更正

前一份主归档里的 f41/a09 两份 `safe-profile.jfr` 经过直接字节脱敏，**不可回读，不作为可复用 JFR 制品**；此前仅凭文件摘要一致判为完整，未覆盖格式可读性。这是归档处理错误。原 ZIP 与其摘要保持不变以保留记录。

[prior-jfr-audit.json](prior-jfr-audit.json) 保存了实际验证：原文件 `jfr summary` 退出 0、归档脱敏文件退出 1；再用摘要吻合的原文件重新执行 WindowedReloadProfile，两份文本与已归档的 `windowed-profile.txt` 逐字一致。旧文本统计仍可核对，正式 JSONL 和判定不受这项二进制错误影响。新归档排除 JFR 二进制，避免再次把可脱敏文本和二进制文件混同处理。
