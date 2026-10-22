# 已输出事件签名的声明依赖

修复提交：`1f877b0fa7435be6fea61a44ff2e9266ce2c3c2a`，本地 `stonecutter`。

## 行为与范围

事件生成器此前输出 payload/dispatch-key 类型后，仍用默认主扫描白名单决定是否导入，既可能漏掉自定义事件类型，也可能漏报当前配置排除的真实依赖。现在只把实际出现在签名中的引用交给现有 TypeScript declaration closure；不输出 key 的事件及 script-defined 事件不增加该 key 依赖。主扫描、当前 exclude/hide、运行时权限、公开成员和 wire 不变。

允许的真实类型补齐声明和 import；被排除的类型不生成声明，依赖报告保持不完整。没有用空类型、any/unknown 或删除公开成员隐藏失败。动态事件 golden 仅由生产生成器新增实际 import 和空行；[生成记录](../../baseline/2026-09-12-managed-surface/REGENERATE.md)说明原因、精确命令、原字节核对和人工验收边界。

## 验证

- 两项回归均先实测失败：默认白名单外的真实事件引用缺失；未输出 key 却成为依赖。各一项失败 XML 保留。
- 普通定向检查：闭包 10 项 + 动态事件 golden 2 项，12 项通过、零跳过。
- `gradlew.bat -g D:/mcmodDemo/NekoJS/.gradle-perf02 -Dorg.gradle.java.home="C:/Program Files/Java/jdk-25.0.2" --offline --no-parallel :common:check guardLint --console=plain`：4m51s，通过；2416 tests / 4 skipped / 零失败，含 common 隔离与 addon dependency gate；guard 561 文件、零警告。
- `npm run test:probe-types`：完整定向契约套件通过，包含新生产生成声明的严格 TS 正反例；`skipLibCheck=false`。
- `git diff --check` 通过。

首次 common/check 因旧动态事件 golden 缺少真实 import 而失败，完整日志仍保留。仅对该方法使用既有显式 regenerate 开关后，普通测试重跑通过；最终检查没有使用 regenerate 开关。

## Standards

Luna xhigh 只读审查：最终硬违例 0、判断项 0。对 script-defined 测试控制流的一项误报，经 `eventType == null` 与生成器提前 return 的实际代码核对后撤回；代理没有运行测试或修改文件。

## Spec

首次 1 项：非输出 dispatch key 不应扩张依赖。主代理补充先红后绿回归并修复，复审确认消除，最终 0 项。复审中同一 script-defined 控制流误报也明确撤回，未把错误意见当作改动依据。

## 尚未验收

这不是新的五节点安装完整 IDE 或正式性能结果。最近冻结的 `7bea6d6d` 完整严格 TSC 仍为 5867 条失败、正式重载 340.0 / 292.8 ms 失败；本修复没有据定向套件通过改判它们。后续完整捕获类型、Bean/字段与方法冲突、最终候选客户端/TCP/分离回退和维护者结论继续开放。

归档 359 项、353,187 bytes，含提交源码绑定、实际补丁、全部 common XML、先红后绿与历史失败日志、新严格消费夹具。复核：`python docs/architecture-refactor/evidence/2026-10-23-event-dependencies/verify.py`。
