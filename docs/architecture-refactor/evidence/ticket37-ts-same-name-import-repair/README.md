# TypeScript 同名导入身份修复

基线 `7adbe62709525ef8466058b201e7cdee5197b405` 加精确生产 patch SHA256 `3a028f011071a150220a029867233da7f82f671af0e36d555400de40479f4dad`；源码提交 SHA 另随交付记录。六个生产文件的原始内容、测试 fixture、完整输出及类字节绑定见 [manifest](manifest.json) 与 [原始验收包](same-name-import.zip)。既有 [诊断 RED](../ticket37-ts-same-name-heritage/README.md) 保留。

共享模块出口按 `(FQN, exportedName)` 分配私有 import 名称，避让公开类、输入别名和既有 getter imports。冲突模块使用独立 registry 视图，从规范 IR、adapter shapes 和 SAM 签名重新渲染所有类型引用；普通模块继续原缓存。getter imports 保守预留标识符，包括 Unicode 转义和 namespace 名称，正文不改。手写声明原文及原导入约定保留，不声称原有歧义自动修复。未删除继承、成员、泛型、manual、golden 或安全限制；没有改运行时、数据或 wire 合约。

真实反射 RED、分离的 nongeneric adapter RED 和首次 Java GREEN 后仍被严格 caller 抓出的 generic input FAIL 均保留。独立审查发现 getter 既有导入遗漏后，新增 named/escaped/namespace 回归先 RED（重复标识符和错误泛型），再修复共享名称分配。最终实际生成声明合法 caller 全部通过 `strict=true/skipLibCheck=false`；十五个非法诊断头仍被拒绝。最后定向 Java 检查覆盖十四项；完整矩阵覆盖其余 authored 声明检查，不把未匹配到的定向 selector 算成已运行。

`gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain` 最终直接退出 0：4755 测试、271 跳过、803 XML、零失败，109 任务、3m47s。首轮 4754 测试 GREEN 单独冻结，不认证其后 getter 修复。`npm run test:probe-types` 与 `tools/nekojs-ci-gates.py all` 通过。历史文件系统失败仍未解决，没有删测试或放宽门禁。

五个正式新 JAR 在独立新 world 中安装，以显式五节点参数执行实机 proof：注册、放置、重载、适用 NeoForge fluid identity、26.1.2 effect 重载前后检查通过。五个服务器正常 RCON 关闭、直接退出 0，十个专属端口释放。1600 Python stub AST、1675 TS 声明语法通过；十四个修改类及嵌套类字节绑定到全部五个实际 JAR。此范围不替代客户端、多人或视觉验收；legacy 八个遗漏类型身份及次数保持，生成仍为 PARTIAL。

完整默认 editor includes 仅将 `skipLibCheck=false` 用于严格验收，全部原始报告保留。TS server/startup 仍 FAIL：legacy 755/756、NF26.1.2 929/930、NF26.2.0 814/815、Fabric26.1.2 1290/1291、Fabric26.2.0 1030/1031；每个 NeoForge 项目移除三条、每个 Fabric 项目移除九条 TS2506/TS2300，原始及私有名规范化对比均新增零诊断身份。Pyright 仍为 7/7/7/13/13，错误身份不变。生成路径与 manual 字节保留，Python 导入按限定身份比较。

顺序审查保留 FAIL：legacy 四个 managed 模块的 `ID.of` 和 `ScriptEventRegistrationEvent.register` 重载顺序变化。原始 AST 比较拒绝这些变化，没有排序重载来伪造 PASS。仅一次预声明旧 7b JAR 对照亦复现 `ID.of` 顺序变化，但未复现当前 `register` 变化；两者所有签名及非成员结构保留，旧/新 JAR 除列明修复类外的 Neko 类字节相同。不能声称全局顺序稳定或把全部变化归因已定。其余变化模块的私有名规范化结构保持；旧短名歧义引用的 FQN 不作整体追认。首次控制审查因遍历回调提前返回失败、TS parse 调用缺输出参数失败均保存，修正只作用于证据工具。

## Standards

最终只读复审：硬性违规 0，可行动启发式问题 0。原文保持、模块局部视图和共享原因修复符合编码约定；不认证未来性能或整体 IDE。

## Spec

最终只读复审：发现 0；此前 P2「私有 import alias 未避让既有 getter import」已由真实 RED→GREEN 验证修复。未发现成员丢失、共享 registry 修改或安全扩张。

不可变 ZIP：4375 entries、9684489 bytes、SHA256 `97f85611c00de21bef8d30326673c10ed7e9692f8088d9572192181506ed5a8f`。全 hash/读回和实际凭据扫描通过；不含 properties/world/JAR/raw JFR。独立复核见 [验证脚本](verify.py) 与 [结果](archive-verification.json)。

冻结此修复包时新源码正式性能 NOT RUN，既有 7b PASS 不可沿用。后续源码提交 `93f4b259ffafb6466918582fcfa950c2c2480d77` 已推送且核验远端一致；[精确新源码性能](../ticket37-same-name-performance/README.md) 启动22134.8ms PASS，两组重载383.8/330.4ms FAIL，原包状态保持历史检查点。完整 IDE、重载性能、字段/方法与名称/重载确定性、文件系统、native crash、fireResistant、剩余领域、多人独立下载窗口、首帧像素/跨节点视觉、维护者、发布及回滚仍未完成。维护者 NOT RECORDED、总体 NOT COMPLETE、自动续跑 PAUSED、工具权限字段 UNRESOLVED，无发布授权。
