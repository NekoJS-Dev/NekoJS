# 选择器生成器第三轮隔离安装验收（保序修复与 java: 路径修复后）

本页绑定提交3ccea55a（= e13cbe82 保序修复 + [java:/node: Windows 路径修复](../2026-10-24-java-module-windows-path/README.md)）的实际安装验收。v1 首轮（PASS 但验收脚本含 20 条自生诊断）与 v2（java: 缺陷五节点 NOT GREEN）记录保留在 `build/closeout-selector-backend-installed*`，不追改；三个计数 1621（旧原型）/1629（v2 首轮实测）/1609（本轮修正）不可混用。

## 矩阵与安装绑定

- 五节点矩阵：`:common:check :common-api-processor:test guardLint :五个节点:build` BUILD SUCCESSFUL；[matrix-record-summary.json](matrix-record-summary.json) 汇总 **5177 tests / 0 failures / 0 errors / 271 skipped**（较 e13cbe82 轮增加的 2 项为 java: 修复回归）。
- 新隔离安装：`build/closeout-selector-backend-installed-v3`，新端口 34421+，五份制品 SHA256 与源码绑定见 [preparation-manifest-trimmed.json](preparation-manifest-trimmed.json)（完整逐类绑定清单 1.1MB 留存于安装目录 `preparation-manifest.json`）。

## 运行时验收

[runtime-summary.json](runtime-summary.json)：**五节点全部 PASS、errors 均为空**。各节点 OWNED 标记初始+重载各一次；v2 失败的 `java:` 类导入（`import { $Vec3 } from 'java:net/minecraft/world/phys'` 等）在本轮 EXECUTION 实际执行，Vec3 字段/选择器调用与 JOML Matrix2d 链式选择器断言通过；trade 观察与注册检查恢复绿色。

## push 继承家族的运行时身份与 AST 差分

[push-identity-runtime.json](push-identity-runtime.json)：两加载器（26.1.2 NeoForge/Fabric）运行时一致确认——实际类为 `net.minecraft.world.entity.vehicle.minecart.MinecartFurnace`（非旧简称 `vehicle.MinecartFurnace`），`push` 字段（public Vec3）声明在本类，`push(double,double,double)`/`push(Vec3)` 声明在 `Entity`，`push(Entity)` 声明在 `AbstractMinecart`；两节点 helper 内嵌的 `PushIdentity.class` 摘要相同（b7a064a3…），但两个 `owned-push-identity.jar` 文件摘要不同（208f198d… / 6734e141…，身份采集自 v2 安装目录）。

[ast-diff-summary.json](ast-diff-summary.json)（TypeScript AST 语义对比，忽略包序与行号）：

- baseline（6e43b827 安装）→ 本轮实际：五节点共移除 991、新增 1070 条显式选择器与 121 条普通方法；**字段零删改**（fieldsRemoved/Added 全 0），headers/other 无语义变化。旧清单预期 979+25+1 不作为核对口径，以本实测为准。
- 超出原型的差异只出现在 26.1.2 双加载器（+33 选择器 / +60 普通 / −6 普通，明细见 [ast-beyond-prototype-26.1.2.json](ast-beyond-prototype-26.1.2.json)）：恰为 push 继承家族——`Entity`/`AbstractMinecart`/`LivingEntity` 等方法声明层补三个选择器，`Minecart`/`ItemEntity` 等安全旁支保留普通 `push`，与上列运行时身份及已批准规则一致。其余三节点 beyondPrototype 全 0，实际与原型逐成员一致。

## 完整严格 TS 与 Python 修正计数

[ide-check-summary.json](ide-check-summary.json)：十组（5 节点 × server/startup，strict、skipLibCheck=false）**共 1609 条诊断、全部 exit2**，恰为 v2 首轮 1629 减去 20 条验收脚本自生诊断；剩余主体为 TS2416/TS2344，与旧原型 1621 同源存量一致，本轮不宣称消除。Python 五节点共 1609 份 stub，Pyright 错误/警告均 0（320/378/349/296/266）。

## 边界

正式重载性能（285.3ms 门槛）、新冻结候选的最终制品/数据分离回退、1.2.0 版本切换与重打包复验、维护者迁移与发布结论均未完成；无 PR、推送、正式发布或代签。后续安排见[安装差分跟进](../../../research/2026-10-24-selector-installed-followup.md)。
