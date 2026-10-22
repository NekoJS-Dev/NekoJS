# 重载预检的纯源码事实复用

改动基线 `911f3b5d`；主代理实现、执行和提交，Luna xhigh 仅只读复审。

在现有 root-owned 转换缓存的生命周期中，复用相同源码的 ValParser 树、局部声明名和别名关系。最多 128 个路径、512 Ki 个源码字符，内容改变即替换，显式失效、清理和关闭释放；超大文本仍校验但不保留。只保存源码事实，不缓存 schema、Context/Value、宿主类型流或诊断接收方。

入口每次读源码并按当前 schema 校验，模块仍逐次读源码、授权并执行原有预检路径；同 stamp 的 prepared-cache 命中沿用原有跳过模块预检的频率。自定义语言前端仍每次执行。不会通过取消校验或复用旧诊断减少工作。

首个新增用例先因缓存能力不存在编译失败，随后通过；compiler/module/script 集成检查通过。最终命令：

`gradlew.bat -g D:/mcmodDemo/NekoJS/.gradle-perf02 -Dorg.gradle.java.home="C:/Program Files/Java/jdk-25.0.2" --offline --no-parallel :common:check :common-api-processor:test guardLint :26.1.2:build --console=plain`

PASS：6m50s，40 tasks / 15 executed；3113 tests / 85 skipped / 0 failures / 0 errors，包含 common 隔离检查与 26.1.2 platform gate。新增回归覆盖相同源码的 schema/sink 更新、不同文件归因、内容改变、淘汰/超限、失效/清理/关闭和 generation 隔离。1640 个生产类与本地 JAR 逐字节绑定；归档保留哈希及实际绑定工具，不包含 JAR。

Standards：0 硬性违反，1 非阻塞命名建议（私有查找方法 `source`）；Spec：0 明确实现错误、0 范围蔓延，审查提出的 prepared-hit 字面边界已澄清为保留旧行为。解析失败驱逐有实现，但没有直接失败后重解析的执行夹具；不为此加入测试专用解析器注入或不稳定的栈溢出测试。

本归档**不证明性能通过**；之前候选重载 348.2/332.2 ms 的正式失败继续有效，当前优化仍须原政策复验。其他四节点构建、新安装运行和完整五节点候选验收尚未由这批检查覆盖。完整严格 TSC gate 也仍未关闭。

离线复核：`python docs/architecture-refactor/evidence/2026-10-22-source-facts/verify.py`。测试 XML、源码补丁与快照、review 绑定和原始执行日志封存在归档中。
