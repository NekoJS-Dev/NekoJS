# Console 与接口类值声明验收

局部技术验收 **PASS**；完整 IDE、Pyright 和整体任务仍 **FAIL / NOT_COMPLETE**。源码基点 `0881f29bfe9c4137f58adae7c2003a588dfdd12a`，最终生产补丁 SHA256 `752c26d164b52e97fb3f5a1a151ad0b7ef634d49d5fa871dcfd995d585b9fbc2`。完整数据在 [evidence.zip](evidence.zip)、[manifest.json](manifest.json)、[summary.json](summary.json)；实际严格调用输入在 [caller-inputs.zip](caller-inputs.zip)。

## 修改

- 通过既有 `registerNodeTypeDocs` 注册实际 Graal console 的16个方法，未知消息参数使用 unknown，返回值与真实方法一致。Graal 包中的 ConsoleBuiltins 枚举及真实 Context 验证了方法存在；没有编造 `table` / `trace`。Neko 既有 warn/debug 包装及运行时行为不变。
- 从实际 Binding 元数据选出需要类值的接口：直接类 Binding 的 typeof 和委托目标 `extraDocTypes` 的 typeof。后者在实际 ParticleOptions 助手中使用，不能只处理直接类 Binding。没有伪造接口静态工厂、扩大扫描或改 HostAccess/ClassFilter。
- 类值是否存在由同一渲染事实判断；重命名后的 canonical export 也使用该事实。模块局部渲染、隐藏、无 Binding、实例、抑制 emit、显式主类型 override 和 backend 复用/重设有回归。已有无绑定接口静态隐藏行为保持原样。
- 宿主输入负例新增有效 UUID、路径和文件字符串，证明拒绝的是未注册转换，而不只是非法 UUID 文本。此前宿主身份修复及适配器覆盖规则不变。

## 集中检查与保留的中间记录

源码收敛后集中运行；验收发现委托遗漏后修正，并在最终审查中统一重命名导出事实，再验证最终源码。三次源码/制品/输出分别绑定，没有将它们合并成一个 revision 或累计测试数。

| 检查 | 最终结果 |
|---|---|
| 普通 common/processor/guardLint/三NBT/五build | PASS：4822测试、271跳过、813XML、零失败；109任务，2m38s |
| Probe 类型检查及四类 CI gate | PASS |
| 严格 Console / 空接口类值正例与3/2条真实负例 | PASS；ESNext、strict、skipLibCheck=false |
| 实际 MetaObject、console 方法、有效宿主字符串拒绝、委托/rename/hide/reset 单元检查 | PASS |
| 最终五节点正式 JAR 的全部编译类绑定及实机注册/放置/重载/NF fluid/26.1 effect | PASS |
| 最终 ESM assert/default/布尔 helper/fail/UUID 首次及重载后执行 | PASS，每节点两个标记 |
| 最终1605 Python stub AST | PASS；三独立环境共4815份，未将重复样本当更多覆盖 |
| 默认 server/startup IDE 项目、只覆盖 skipLibCheck=false/noEmit | **FAIL**，下表；目标错误已退出闭包 |
| Pyright 1.1.414 | **FAIL**：7/7/7/13/13 个重声明 |
| 精确新源码性能、维护者结论 | NOT_RUN / NOT_RECORDED |

首个严格调用 harness 意外带入默认 DOM：console 的两个不存在方法被浏览器 Console 声明接受，仅剩1条错误，预期3条检查失败。原配置、输出和脚本保留；随后按实际 editor 的 ESNext lib 核验出3条预期错误。修改的是测试环境的错误假设，没有修改真实默认配置或放宽检查。

首轮实机只覆盖直接接口类 Binding，Console 错误消失而 ParticleOptions 委托错误仍在；这轮3m24s矩阵及5JAR/日志/默认项目输出是 **中间结果**。修正实际委托路径后的3m26s矩阵及五实机同样保留；最终重命名事实统一后才使用本包最终制品和2m38s普通矩阵。各轮全部五 JVM 均正常 RCON/direct0，共15个独立进程，30个专属端口最终空闲，无 hs_err、强杀、HTTP bridge 或桌面激活。

本轮没有 golden 变更，没有手改生成结果，没有删 manual 或未通过检查。归档过滤器曾拒绝 writer 自身规则里的私钥标记；没有生成不合格 ZIP，writer 正文排除、SHA 单独绑定，实际密码/标记扫描仍启用。归档不含 world、RCON密码、私钥、JAR、原始JFR或进程命令行；15份实际专属配置密码扫描通过。

## 真实默认项目仍未通过

| 节点 | 0881 server/startup | 最终 server/startup | Python stub / Pyright错误 |
|---|---:|---:|---:|
| 1.21.1 | 588 / 588 | 586 / 587 | 320 / 7 |
| 26.1.2 | 746 / 746 | 744 / 745 | 379 / 7 |
| 26.2.0 | 640 / 640 | 638 / 639 | 347 / 7 |
| 26.1.2-fabric | 1164 / 1164 | 1162 / 1163 | 296 / 13 |
| 26.2.0-fabric | 919 / 919 | 917 / 918 | 263 / 13 |

忽略位置和独立安装根目录的诊断首行多重集：每节点 server 移除2条（Console及ParticleOptions），startup移除1条（ParticleOptions），**新增0条**。其余真实错误、深度边界符号、手写声明和字段/调用双行为均保留，不宣称此次修改解决全部 IDE 错误。

下一步继续真实依赖闭包、Python 字段/调用投影、精确源码性能及剩余第一帧/客户端节点/领域/磁盘窗口和交接材料。历史7d reload性能失败仍保留，人类维护者 NOT_RECORDED，自动续跑 PAUSED，工具权限字段 UNRESOLVED；A22/A28及发布授权边界未变。通过 `python docs/architecture-refactor/evidence/ticket37-interface-console-repair/verify.py` 独立检查本包；验证数据完整并不等于整体完成。
