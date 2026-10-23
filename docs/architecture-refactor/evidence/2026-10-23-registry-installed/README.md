# 88e18d84 五节点新安装与完整声明测量

生产源码 `88e18d84bed2f501416b1ef44e7b87d32fa8ef38`，包含 raw 泛型输出和注册表目录修复。五节点使用全新目录/世界与本批完整构建 JAR；全部 common/node 编译类逐字节匹配安装制品，另记录源码、JAR、fixture 与 class 摘要。不是复用旧声明结果。

## 实际运行

五节点行为全部 PASS，五个独立服务端均经 RCON 正常退出 0，无强制清理。覆盖初次/重载后的实际 Node assert/UUID、字符和宿主集合操作、注册回调、嵌套 builder 指纹、方块/适用节点流体、完整重载与声明生成。NeoForge 另验证交易池生命周期，两个现代 NeoForge 验证空动态 generation 保留实际效果；26.1.2 的实体效果在重载前后均通过。测试副本保持 pause-when-empty-seconds=-1；旧暂停相关失败仍单独保留。

| 节点 | 制品内匹配的 common/node class | server TSC | startup TSC |
|---|---:|---:|---:|
| 1.21.1 NeoForge | 1570 | 493 | 494 |
| 26.1.2 NeoForge | 1641 | 562 | 563 |
| 26.2.0 NeoForge | 1641 | 551 | 552 |
| 26.1.2 Fabric | 1584 | 615 | 616 |
| 26.2.0 Fabric | 1584 | 586 | 587 |

## 完整 IDE 仍失败

1609 个新生成 Python stub 的默认 Pyright 1.1.414 全部 exit0、零错误/警告。

十入口采用默认 editor includes，strict、skipLibCheck=false、noEmit，全部 TSC exit2、无 compiler crash，合计 **5619 条，FAIL**。对前一安装源码 1f877b0f 的文件/错误码/首行消息多重集比较，忽略位置和安装根：5857 → 5619，移除 244、**新增 6**，不能写成零新增。多行详情没有用于差异键。

移除项为 TS2694 136 条、TS2724 10 条、TS2314 98 条。新增：Fabric 26.1.2 的 ContextChainAccessor<any> 缺 getModifiers（两入口 TS2420 共两条），两个 Fabric 节点的 GameRule.fabric_enumCycle 擦除签名与泛型接口不一致（四条 TS2416）。前一输出分别使用裸泛型 implements/结果而先报 TS2314；补齐参数后这些关系继续不兼容，仍需共享原因调查，未放宽或删除成员掩盖。完整旧/新诊断及比较器均封存。

## 边界与复核

本批通过不代替 PostEffects 当前整批客户端、动态注册表全部真实 TCP 窗口、最终统一候选分离回退或维护者结论。正式性能未在本批重跑，最近正式原口径结果仍为 7bea6d6d 的 340.0 / 292.8 ms，超过 285.3 ms，继续 FAIL。没有 PR、push、远程发布或新增公开符号删除。

归档 4125 项、8,408,540 bytes，含新声明/stub、完整诊断、旧诊断、差异、正常退出、运行结果、来源摘要和执行工具；排除 JAR/依赖、世界、server.properties、完整启动参数文件和人工签字。RCON 凭据按实际测试副本值在导出内容中脱敏。

复核：`python docs/architecture-refactor/evidence/2026-10-23-registry-installed/verify.py`。验证器重新计数原始 TSC 诊断和多重集差异，PASS 表示证据一致；完整 TSC 与性能门禁仍失败。
