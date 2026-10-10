# Python 模块名称修复：局部通过，完整 IDE 仍失败

固定基线 `b2895779661720f0f173c37c6a30d05d93c353a9`，五生产文件补丁 SHA-256 `2d46d7a7997d91c0994ba7d1289ef2aa71d259ce040c738f5f6a1ca89d941b2b`。补丁采用明确的9字符 index 元数据，包含新 `PythonImports.java`；构建时源码尚未提交，提交映射单独记录。整体状态 **NOT ACCEPTED**。

共享导入分配器按实际 FQN 和导出名称区分类型，为碰撞引用分配私有名称。真实公共类名称、编辑后的 IR renameTo、继承字段不同读写类型均保留。绑定、事件和适配器输入使用同一名称事实；嵌套 Self/Host 数组保留，仅删除顶层重复目标类型。枚举输入别名参与导入预留；生成输入别名与同包真实公共类碰撞时，只给生成别名分配私有名称，事件 dispatch 与模块适配器重渲染沿用它。

没有扩大 collector 深度、HostAccess/ClassFilter 或路径权限，没有删除 manual 生产器、实际类型/成员，也没有增加 Any 兜底或验收排除。保留已存在的隐藏/未收集类型降级规则。

| 检查 | 结果 |
|---|---|
| backend/renderer/Bean 聚焦 | PASS：51 测试、0 失败；真实 RED 和单变量恢复控制保留 |
| Standards / Spec 复审 | 0 / 0 剩余发现；历史发现和修复见 REVIEW.md |
| common/check、隔离、processor、guard、NBT smoke、五节点 build | PASS：800 XML、4733 测试、271 跳过、0 失败；109 任务，42执行/67 up-to-date，1m38s，直接退出0 |
| npm 严格声明 / 平台门禁 | PASS |
| 七组严格 Python 正向 caller | PASS：各退出0、0错误 |
| 七组错误 caller | 预期拒绝：各退出1，错误数3/6/3/3/3/3/4 |
| 七组完整 fixture stub 严格检查 | FAIL：错误数9/13/15/10/13/10/11，包含 unused imports 和真实 property/parent-variable override；无忽略或排除 |
| 五节点精确 JAR 实机生成/注册/放置/重载 | 支持子集 PASS；旧版八个反射遗漏仍 PARTIAL |
| 两个26.x NeoForge MobEffect/流体身份 | 重载前后施加及身份检查 PASS |
| 全部 Python AST / TS parse | PASS：1600 / 1675 文件，0语法错误 |
| 默认 editor includes 严格 TSC / 全 stub Pyright | FAIL：TSC退出2，Pyright退出1；结果和诊断身份完整保留 |
| 本源码正式性能、客户端/视觉 | NOT RUN；历史失败不被替代 |
| 维护者结论 / 发布 | NOT RECORDED / 未授权发布 |

| 节点 | TSC SERVER / STARTUP | Pyright 错误 | 对 b289 删除 / 新增 Python 诊断 |
|---|---:|---:|---:|
| 1.21.1 | 768 / 769 | 18 | 3 / 0 |
| 26.1.2 | 942 / 943 | 14 | 4 / 0 |
| 26.2.0 | 827 / 828 | 14 | 4 / 0 |
| 26.1.2-fabric | 1306 / 1307 | 21 | 2 / 0 |
| 26.2.0-fabric | 1046 / 1047 | 21 | 2 / 0 |

15条消失的诊断是同名类型错误导入/自继承或实例字段 writer 依赖引发的声明错误；不是完整 IDE 通过。TSC诊断身份0删除/0新增；生成文件路径未删除，旧版反射遗漏身份未变化。八组历史比较全部在包中。

失败和夹具纠正均保留：第一次跨 surface 修复编译失败缺少两项API import；嵌套 Self 过宽去重导致真实46测试/1失败；枚举夹具误期望后端发射额外方法，改由实际消费类后重新复现；首次两组 caller 直接引用私有生成别名触发 reportPrivateUsage，改用实际公开事件及等价返回联合后七组通过。初始失败不是通过，完整 fixture 严格失败也不是 caller 通过能替代的结果。

五个实际 PID25096/28864/27596/3060/22060 均 RCON 正常关闭、直接退出0，10专属端口空闲，没有停止用户进程。五个世界重新生成，安装精确当前官方 JAR。Fabric 仅复用已停止专属环境原版缓存并记录SHA；没有提交world、密码、账户、库/JAR或原始JFR。

归档 `python-module-candidate.zip`：4099条目、8377758字节，SHA-256 `09cafc3bd8810cf173fab89059c94d4fca92d21bd5570455dfcd55d5f346ae68`。XML ZIP SHA `a64fa13e967de4dc9e29d401728414e8d34dd401f71a1b4009012d71849a4ea0`。完整源文件/补丁、全部最终比较和已结束的验证日志逐项读回一致，真实专属RCON密码扫描通过。归档程序自身执行时的ZIP内console为空，完成后的完整结果单独保留于 packaging-console.txt；它不是构建或IDE日志。

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
```

生成夹具、严格 caller、源码恢复控制、实机 runner、完整IDE、比较和归档脚本保留在ZIP。整体15/16/22/28/34/37未完成，38仍可选。完整IDE、当前性能、文件系统间歇失败、native崩溃/首帧、pack下载窗口、其他领域与跨节点视觉、维护者/发布回滚均开放；自动续跑保持暂停。
