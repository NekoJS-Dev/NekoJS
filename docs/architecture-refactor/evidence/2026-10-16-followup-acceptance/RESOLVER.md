# 模块入口重复路径检查修复与正式性能复测

生产源码 `6faed0e9`，固定增量 `45fc820f...6faed0e9`。主代理实现并验证，Luna xhigh 只读复核。票 28、37 继续开放。

## 改动与契约

入口解析原先在路径转换和文件解析入口各执行一次相同的路径信任检查。现在纯路径解析由私有方法负责，`resolveEntry` 直接进入既有文件解析检查；相对导入与 require 的父路径仍先经过信任检查再探测文件系统。候选文件和最终真实路径的检查均保留，没有缓存信任结果或扩大可访问目录。

三个新增回归覆盖相对/绝对/file URI/省略扩展名的规范身份、越界入口与越界父路径、指向根目录外的符号链接。实际符号链接回归已运行，未跳过。没有 Script API、数据或 wire 变更。

## 验证

```text
.\gradlew.bat --offline :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
python tools/nekojs-ci-gates.py all --out build/domain-resolver-ci-gates.json
python build/domain-closeout-tooling/judge-formal-performance.py build/domain-performance-resolver
python docs/architecture-refactor/evidence/2026-10-16-followup-acceptance/verify.py
```

完整构建 PASS，3m51s，106 tasks（34 executed / 72 up-to-date）；七模块完整测试 XML 合计 4916 tests、271 skipped、0 failures/errors。Probe 类型回归和四项 CI gate PASS。

五节点新官方 JAR 的 class 字节绑定确切源码，每个新独立服务器 runtime verdict PASS，正常 RCON stop 后 exitCode=0。完整默认 Pyright 五节点均为 0 error/warning；完整严格 TSC 与上一批逐条诊断多重集一致，无新增或移除，仍 FAIL。server/startup 分别为 693/694、867/868、639/640、1290/1291、923/924。全部 Java 类 Python stub 逐字节一致；入口/事件模块差异按完整导入类型身份核对，不能声称全目录字节一致。

## 定向测量与正式性能结果

同一 JVM 内交替运行旧生产实现和新公共 `resolveEntry`，每组六种 JSON 路径、每臂 30 次、十对样本：旧均值 21.014ms/组，新均值 13.985ms/组，均值节省 7.029ms。六种规范身份及五种拒绝场景相等，实际检查符号链接。旧源码和旧 class、新 class 摘要均绑定；更早两次试验及全部样本保留。

这是 JSON 组件诊断，不代表六个脚本的重载耗时，也不能作为正式性能门禁通过依据。样本存在阶段变化，未删除不利样本。

使用未修改的原采样器、fixture、两次预热、五个正式样本及原阈值，在绑定新制品全部 class 的专属工作树复测；测量期间没有并行构建或本任务游戏进程。所有进程正常 RCON 停止，未强杀。

| 组 | 五个正式样本（ms） | 均值（ms） | 原阈值（ms） | 结果 |
|---|---|---:|---:|---|
| reload-1 | 470, 297, 263, 269, 378 | 335.4 | 285.3 | FAIL |
| reload-2 | 376, 407, 407, 389, 304 | 376.6 | 285.3 | FAIL |
| startup | 27058, 29332, 24878, 24596, 24645 | 26101.8 | 41173 | PASS |

正式性能总结果 FAIL；判定脚本以非零退出报告失败。该口径为源码 runServer，不是安装 JAR 启动计时。既有旧源码对照也失败的证据仍保留，本次未重新跑旧对照，不能把当前差异全部归因于此修复。

## 独立签名诊断与剩余项

单独使用同一 26.1.2 新 JAR 观察三种宿主：MinecraftServer、Player、Level 的 `neko$data` 在运行时反射中均已返回裸 AttachedData，Probe IR 同样无实参；启动后与重载后共六个标记一致。接口源码的 `AttachedData<Host>` 事实在 Mixin 变换后丢失，后续应从唯一明确的接口契约恢复，而非统一改成 any。

这次专属诊断复用了已完成交易阶段的 fixture，综合 runtime verdict 因缺少当前交易观察而失败；正常停止且 exitCode=0。它只支持签名观察，不计入五节点 PASS，也不作为第六次实机验收。

[归档](resolver-evidence.zip)、[清单](resolver-manifest.json)、[摘要](resolver-summary.json)保存新 JAR 实机、完整声明与诊断、源码/class 绑定、测试 XML、组件原始样本、正式性能全部样本和工具、独立签名诊断的失败结果。首次归档脚本误将前一批目录指向当前目录，独立校验拒绝后已保留无效归档于 `build/domain-resolver-first-invalid-archive`；修正目录后重新生成，未改动原始实机或性能日志。

独立 verifier PASS 仅证明归档完整和结果可重算，明确断言完整 TSC 与重载性能 FAIL。固定源码增量 Standards 0 / Spec 0 均为静态复核；其他节点客户端首帧、最终候选数据回退和真实维护者发布结论尚未补齐。
