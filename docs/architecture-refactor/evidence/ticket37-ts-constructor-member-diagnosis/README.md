# constructor 成员名诊断：尚未实施修复

实际源码仍为 `60139fffffa6e54b2248797c2a32d4fd75025a54`；本包只保存诊断及候选模型，不修改生产代码或原始生成输出。默认完整严格 TSC 的真实 `ApiSignature` 含一个实际构造函数、`constructor` Bean 属性及同名静态工厂，分别引出 TS1341、TS1089、TS1093。实际 `SavedDataType` 另有返回 Supplier 的普通实例方法 `constructor()`，在完整报告中引出另一条 TS1093；本轮隔离 caller 未覆盖该实例方法。

保留完整实际 surface 声明，只将 `java/lang.Record` 与 `java/util.List` 两个外部依赖换成明确的隔离 leaf fixture。正确配置模块映射后的 RED 编译直接退出 2，恰好保留上述三条 surface 错误，合法 caller 本身没有额外诊断。原完整声明字节未变。

| 独立候选副本 | 合法 caller | 非法 caller | 含义 |
| --- | --- | --- | --- |
| 普通 `"constructor"` 名称 | 退出 2，剩 TS1089/TS1093 | 退出 2 | getter 已消除 TS1341；静态工厂仍被视作构造函数，候选不成立 |
| computed `["constructor"]` 名称 | 退出 0，零诊断 | 退出 2，三项拒绝 | 真构造函数、boolean Bean 读取及静态工厂保留；拒绝 boolean→string、工厂结果→number、非法工厂参数 |

三个解释按顺序核查：class 中成员名的特殊解析、外部依赖干扰、Java 成员分类错误。原资源/Java API 及 renderer 调用链显示：实际构造函数走独立 `formatConstructor`；静态 `ApiSignature.constructor` 是普通 Java 方法、走 `formatMethod`；Bean 属性输出另有直接拼接。隔离外部依赖后仍 RED，名称对照支持第一项为当前语法原因。尚不能据此认证全部 Java host 属性行为或最终 generator 修复。

初版 caller 缺少 `java:` 模块映射，出现额外 TS2792；初版模型脚本又错误预期 quoted getter 仍会出现 TS1341，assert 失败。两次错误与控制台均保留。随后只修复隔离 caller 的映射及这项错误预期，不改 compiler gate、原生成输出或生产实现。结果按实际 compiler 退出值记录，收集脚本退出 0 不代表原始声明通过。

独立验证首次逐字比较候选与两处名称替换的原文时 FAIL：原始生成文件 78 个 LF、候选 78 个 CRLF，转换发生在 Windows 写入候选副本时。原始副本仍逐字相同；独立检查明确要求候选除此换行格式及两处名称替换外完全相同，没有忽略其他空白或语法。该次验证失败、原 verifier 及 hash 另外保留在 [verifier-byte-failure.txt](verifier-byte-failure.txt)、[verifier-byte-failure.py](verifier-byte-failure.py)、[post-archive-verification-failure.json](post-archive-verification-failure.json)，不覆盖已经封存的 ZIP。

[manifest.json](manifest.json) 及 [54 项原始证据](constructor-member-diagnosis.zip) 保留完整默认 TSC、两份实际生成模块、源码、首轮失败、修正后的 RED/候选/非法调用。ZIP 64,336 bytes，SHA256 `0d1f02056e90934427a10f692cac8b9a93dea517d0fe0a0ed9e9563928bbfaf1`；逐项 hash/读回及实际 RCON 密码扫描通过，无 properties/world/JAR/JFR。独立验证见 [archive-verification.json](archive-verification.json)。

接下来必须先建立真实反射→共享 backend 回归，覆盖普通静态/实例方法、Bean getter/setter、field、overload、hidden/renamed 及真构造函数；在生产 HostAccess/ClassFilter 下核对实际同名成员读取/调用，再修改 shared renderer 的上下文名称处理。需要完整矩阵和精确新 JAR 默认 IDE/运行时验证。不要删除成员、改成 any、改默认 includes 或手改生成输出。本包没有运行这些后续检查，不能称为修复完成，也不能把现有 60139fff 性能 PASS 套用于未来修改。

完整 IDE FAIL、总体票据 NOT COMPLETE、维护者 NOT RECORDED、自动续跑 PAUSED、工具权限字段问题 UNRESOLVED；A22/A28 边界及无发布授权保持。
