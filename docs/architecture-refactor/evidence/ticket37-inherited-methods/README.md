# Inherited method declaration candidate — NOT ACCEPTED

此固定包对应基线e48ea68cafbde0007ac7271d99615c146199853b加生产补丁6763dfdd049c2982b6b6402d634b38b25216543ad6299e3a3c746b05c716616f。源码当时未提交；它不是后续修正源码或整体验收的通过记录。

## 通过的局部窗口

真实default/interface方法在生产HostAccess/ClassFilter下可调用，但旧class声明缺失。继承泛型上下文、同名父类重载、remapped名称、declared setter优先以及static setter边界有真实RED→GREEN。严格caller和npm既有Probe检查通过。真实remapper规则与原Java名称调用分别通过；裸common环境remapped.call控制报Unknown identifier，该失败保留，不承诺该调用通过。Bean fixture只证明写类型与显式setter调用，不承诺天然属性分发或完整interface Bean兼容。

3golden重新生成，共29新增成员，AST审查确认所有旧member/headers/其他declarations保持。完整五节点/common隔离/processor/build/guard矩阵106任务1m34s，800XML、4688测试、271跳过、0失败。中间两个完整矩阵也单独保留，未覆盖。两个独立审查初始发现3项P2及Javadoc问题，均修正；实际产物复审随后又发现以下问题，源码复审不能取代实机IDE。

5新官方JAR完整重放注册/放置/重载，NF26.x真实MobEffect前后施加、NFFluidType身份通过；5服正常RCON关闭，直接exit0。1590PythonAST/1667TSparse通过。生成文件集合前后相同，legacy8不可用类型身份保持，其他4节点仍0。legacy三类报错的首先拒载依赖变化已保留原始消息；不能宣称全部warning消息逐字一致。

## 未通过的新增问题

默认editor includes，仅skipLibCheck=false/noEmit=true。所有完整TSC exit2，Pyright exit1。下表是server/startup，不是减项验收：

| 节点 | 旧TSC | 本candidate TSC | 移除/新增身份server | Pyright旧→新 |
|---|---|---|---|---|
| 1.21.1 | 2072 / 2073 | 1816 / 1817 | 688 / 432 | 79 → 80 |
| 26.1.2 | 2782 / 2783 | 2138 / 2139 | 1169 / 525 | 88 → 89 |
| 26.2.0 | 2303 / 2304 | 1578 / 1579 | 903 / 178 | 87 → 88 |
| 26.1.2-fabric | 2710 / 2711 | 2253 / 2254 | 1080 / 623 | 88 → 89 |
| 26.2.0-fabric | 2215 / 2216 | 1538 / 1539 | 830 / 153 | 88 → 89 |

- 类型变量身份被名字合并：class S与继承方法S、class E与方法E，实际Codec出现新的TS2416。需一致alpha-renaming。
- 普通泛型容器递归input alias，把DataResult<Map<K,V>>变为DataResult<{[key:K]:V}>，既破坏host泛型身份又产生TS1337；不能通过Any或删方法遮盖。
- 继承方法真实泛型bounds未进入声明，ServerLevel.getEntities<T>新增TS2344。
- 新成员引用未进入既有BFS闭包，出现新未生成import；需从同一事实枚举依赖，保留depth/package限制及明确未生成的原因。
- TS合成Bean名level/players/error撞真实方法或字段；父accessor被子真实字段覆盖还有TS2610。保留真实成员，不能伪别名或只隐藏继承方法。
- 每节点Python新增1条ItemStack.matches混合staticmethod/实例overload错误，需表达真实类/实例绑定，不能删除overload或统一改staticmethod。

继续修正源码并用新的独立profile重做验收；此candidate总体FAIL，不提交为已完成修复。正式最新源码性能、文件系统间歇AccessDenied、native glfw崩溃、其余视觉/领域/发布回滚与真实维护者结论仍开放。自动续跑PAUSED，没有发布动作。

## 固定证据

[证据ZIP](inherited-method-evidence.zip) 3571项、9347346字节，SHA256 `0d0bc5d2234c6928a3d6a7687524bb56e30ecb889dc9f84e64b9ba6bed80bdaf`。逐项hash/readback、嵌套ZIP/gzip凭据检查通过；包含源码/新测试/旧新golden、所有RED/GREEN/失败控制、matrixXML、实机outputs/logs/直接退出及完整IDE身份对照。排除RCON属性/密码、world、账户/binaries/libs、原始JFR。制品SHA见[manifest](manifest.json)，不能复用旧7e98制品SHA。
