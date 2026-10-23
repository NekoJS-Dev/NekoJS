# raw实现与泛型接口：重载原型未采用

主代理以Java25和当前TypeScript编译器运行独立throwaway原型；没有修改生产源码、输出成员、扫描范围或门禁。Luna xhigh仅只读调查与反例复核。

| 检查 | 实际结果 |
|---|---|
| TS raw-first/generic-last，普通直接调用及提取调用保留raw返回 | exit0 |
| TS显式host.cycle<Alpha>(alpha)也不得获得新增Alpha保证 | **exit2，TS2578**：新增泛型重载仍允许该错误保证 |
| Java raw类显式类型实参调用 | 可调用，仍返回Enum；实际允许返回Beta |
| Java将该具体类调用结果赋给Alpha | javac exit1，稳定诊断compiler.err.prob.found.req |
| Java接口静态视图保留E关系 | 编译可用，但unchecked raw实现返回Beta时，调用者插入的Alpha cast实际抛ClassCastException |

因此普通调用选raw不能证明完整类视图合同保持。接口原有泛型签名也不能单独证明raw实现的具体类视图具有同样保证。该反例否决直接追加接口泛型重载的当前提案，不能据此宣称所有可能的签名恢复设计都不可行。未采用Mixin marker猜测、全局any/协变、成员删除或TS-only编辑旁路。

现有pipeline还会将E签名排到raw Enum之前；在共享IR加第二个MethodDecl会扩大同名编辑选择并改变Python输出，renderer-only补签名则绕开assign_type/modify_type。后续方案必须同时证明具体调用、排序、Hide/remap、编辑权威与跨后端保持。

最初原型校验器寻找英文javac文本，实际系统输出本地化诊断，导致校验器断言失败；原始字节保留。改用-XDrawDiagnostics的稳定诊断键后确认同一预期编译拒绝，没有修改Java负例或把失败改称通过。

初版离线verifier又把带本地化尾行的原始javac字节当UTF8解码，原工具与UnicodeDecodeError日志同目录保留。改为按ASCII稳定诊断键直接查原字节，未改变或转码编译器日志，复核通过。

23条目、21,451字节，保留Java/TS源码、已执行class、原始日志、编译器版本、精确检查工具与975496d3相关生产源码。`python docs/architecture-refactor/evidence/2026-10-24-declaration-prototypes/verify.py`核对封包和上述原始结果；它不是完整TSC通过证明。完整严格TSC仍5565，性能仍失败。
