# TypeScript真实字段/方法冲突的选择器迁移决策

状态：用户已明确回复“批准规则并实现（推荐）”，批准本页规则及完整清单。主代理正在实现生成器和回归；尚未冻结新生产候选。既有候选6e43b827的完整严格结果仍为5537条FAIL，原重载门槛仍FAIL；本次规则批准不代表发布批准。

## 要批准的行为

仅处理有真实Java来源的、已暴露的实例字段与同名方法冲突，并使必要的父类/接口合同一致：保留字段及其读写类型；冲突分支及会与其冲突的祖先方法使用Graal已有显式选择器。其他安全分支保留原普通方法，同时提供选择器；若普通操作原先只从被迁移的父合同继承，在无字段冲突的安全分支补回其精确声明。静态与实例表面分开处理。

例如Vec3的`vector.x`保持数字，TypeScript方法调用改为`vector["x()"]()`；JOML setter使用`matrix["m00(double)"](0.5)`。JavaScript原来的`vector.x()`运行时行为继续保留。没有同名字段的legacy配方子类仍可调用`recipe.showNotification()`。这是声明的真实表达能力修复，既有Graal方法不需要新增运行时包装器。

规则适用于五节点默认输出，以及自定义扫描中满足同样真实来源/可见性条件的类型。下方清单是当前默认输出的确切影响，不是将业务实现硬编码成Java类白名单。无源方法、手写替换和合成成员不得猜Java签名；明确的IR编辑继续优先。排除和隐藏规则、已批准的五处Entity.attackable边界、其他Bean别名、Python、Java方法签名/枚举身份、网络与持久数据均保持。

## 当前确切范围和证据

[可复核证据](../architecture-refactor/evidence/2026-10-24-selector-closure/README.md)来自五个真实安装服务端、原候选JAR和当前完整默认声明：初始96个跨节点字段/方法冲突，经必要继承关系扩展为221个类/成员位置、86个类；其中208个成员家族在本地有声明，另13个仅受继承影响。

五节点共874个有声明的类/成员位置、1004条原方法声明。拟替换979条普通拼写；另外25条保留普通拼写并增加选择器；再补回1条原来继承的普通声明。对应44个生成包文件，原字段不删、不改为函数交叉类型。选择器从真实sourceMethod的Java原名、擦除参数类型和二进制内部类名产生，不能从改写后的脚本名或TS参数拼接。安装实验已证明重命名方法的普通名与选择器原名可以不同；隐藏方法的两种调用均受原规则约束。

两个原型分别运行十个完整严格程序。最终保留普通拼写的方案与首次统一改名方案都将5537条诊断降至1621条，精确差分删除3916、新增0。两次均保留strict=true、skipLibCheck=false、相同程序输入；只是隔离副本中的原型，没有修改生成器。剩余1621条及正式重载仍未通过，不能据此关闭票37。

## 实现时加入什么

- 在现有依赖闭包完成后的批量继承投影接缝加入TS专用方法名投影，复用IndexFileGenerator.prepareClassInheritance与既有泛型/继承机制；不全局改写共享IR方法名。
- 以真实字段/方法来源和可见性判定冲突，必要时补内部字段来源元数据；保留隐藏、重命名、明确编辑、手写类型和无源成员的权威性。不会为无法投影的合同补any、空壳或假签名。
- 保留精确参数、返回、泛型与重载；安全分支的补声明使用正确的IR类型替换，生产不得硬编码原型中唯一的配方布尔签名。
- 在已有真实成员规则下核对新写法的静态校验与Graal调用，补公开backend、严格正负消费、隐藏/重命名、继承/泛型/编辑、静态隔离和来源身份回归，再按原流程重新生成受影响输出。实际平台/性能验收在最终候选冻结后按原标准复验。

本次已批准的是上述迁移规则及相应实现。不会顺带迁移被排除的core类型、其他公共名称、其他Java/Python合同或扩大运行时权限。维护者发布结论仍须单独真实记录。原封存原型中的待批准标记保留为当时快照，不追改历史证据。

## 执行记录

主代理负责全部源码、验证和提交；Luna xhigh仅只读审查。已完成公开backend的字段/选择器、泛型继承、方法变量作用域、隐藏继承副本、重命名、继承字段与新增接口、IR返回编辑、静态/合成字段及枚举边界回归；审查后又补先祖字段配子方法、父IR重命名及安全后代复用。十六项选择器回归及十四组严格消费通过，保持strict=true、skipLibCheck=false并包含负例，已接入既有npm总类型检查。枚举结构单测独立核对其固定渲染骨架，不把触发的既有JDK通配符闭包错误计作通过；全量默认严格门槛仍单独保留。

桥接回归先通过生产HostAccess的真实Graal调用，确认具体String实现同时支持String和父接口Object擦除选择器，再补可由真实编译桥接证明的父合同。继承手写重载通过父类型的索引访问保留泛型和模块作用域，不解析或猜写TypeScript字符串。Bean getter override及排除规则变化后刷新声明快照。最终common、处理器、隔离、guard与五节点构建矩阵已通过：5174项、271 skipped、0失败；npm总消费、wiki和CI门禁通过。[实现与可复核证据](../architecture-refactor/evidence/2026-10-24-selector-backend/README.md)记录全部结果与限制。

下一步：在五个新隔离安装中按原流程生成，核对默认声明实际变化与本清单，完整检查十组严格TS配置及Python输出，冻结新候选后复验运行时和原性能标准。不能把原型的1621条错误或旧候选的验收继承成新源码的结果。

## 普通写法需要改用选择器：183个成员家族

表中列出所有当前直接声明变化；具体节点的原/新整条签名、源码位置及Java身份在证据join/records.json和policy/records.json中。一个家族可含getter和setter两个选择器。

| Java类 | 成员 | 选择器 | 节点 |
|---|---|---|---|
| `net.minecraft.core.Position` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.core.Position` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.core.Position` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.server.level.ServerLevel` | `isClientSide` | `isClientSide()` | 1.21.1 |
| `net.minecraft.server.level.ServerLevel` | `noSave` | `noSave()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.server.level.ServerPlayer` | `gameMode` | `gameMode()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.entity.animal.Chicken` | `isChickenJockey` | `isChickenJockey()` | 1.21.1 |
| `net.minecraft.world.entity.animal.chicken.Chicken` | `isChickenJockey` | `isChickenJockey()` | 26.1.2, 26.1.2-fabric |
| `net.minecraft.world.entity.player.Player` | `gameMode` | `gameMode()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.item.crafting.Recipe` | `showNotification` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.ShapedRecipe` | `showNotification` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.SingleItemRecipe` | `result` | `result()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.level.Explosion` | `radius` | `radius()` | 1.21.1 |
| `net.minecraft.world.level.Level` | `isClientSide` | `isClientSide()` | 1.21.1 |
| `net.minecraft.world.level.Level` | `noSave` | `noSave()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.level.LevelAccessor` | `isClientSide` | `isClientSide()` | 1.21.1 |
| `net.minecraft.world.level.LevelReader` | `isClientSide` | `isClientSide()` | 1.21.1 |
| `net.minecraft.world.level.biome.BiomeGenerationSettings` | `features` | `features()` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.biome.BiomeSpecialEffects` | `dryFoliageColorOverride` | `dryFoliageColorOverride()` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.biome.BiomeSpecialEffects` | `foliageColorOverride` | `foliageColorOverride()` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.biome.BiomeSpecialEffects` | `grassColorModifier` | `grassColorModifier()` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.biome.BiomeSpecialEffects` | `grassColorOverride` | `grassColorOverride()` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.biome.BiomeSpecialEffects` | `waterColor` | `waterColor()` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.saveddata.maps.MapItemSavedData` | `locked` | `locked()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.phys.Vec3` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.phys.Vec3` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.phys.Vec3` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2d` | `m00` | `m00()`, `m00(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2d` | `m01` | `m01()`, `m01(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2d` | `m10` | `m10()`, `m10(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2d` | `m11` | `m11()`, `m11(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2dc` | `m00` | `m00()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2dc` | `m01` | `m01()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2dc` | `m10` | `m10()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2dc` | `m11` | `m11()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2f` | `m00` | `m00()`, `m00(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2f` | `m01` | `m01()`, `m01(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2f` | `m10` | `m10()`, `m10(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2f` | `m11` | `m11()`, `m11(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2fc` | `m00` | `m00()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2fc` | `m01` | `m01()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2fc` | `m10` | `m10()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2fc` | `m11` | `m11()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m00` | `m00()`, `m00(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m01` | `m01()`, `m01(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m02` | `m02()`, `m02(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m10` | `m10()`, `m10(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m11` | `m11()`, `m11(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m12` | `m12()`, `m12(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m20` | `m20()`, `m20(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m21` | `m21()`, `m21(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m22` | `m22()`, `m22(double)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3dc` | `m00` | `m00()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3dc` | `m01` | `m01()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3dc` | `m02` | `m02()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3dc` | `m10` | `m10()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3dc` | `m11` | `m11()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3dc` | `m12` | `m12()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3dc` | `m20` | `m20()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3dc` | `m21` | `m21()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3dc` | `m22` | `m22()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m00` | `m00()`, `m00(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m01` | `m01()`, `m01(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m02` | `m02()`, `m02(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m10` | `m10()`, `m10(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m11` | `m11()`, `m11(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m12` | `m12()`, `m12(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m20` | `m20()`, `m20(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m21` | `m21()`, `m21(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m22` | `m22()`, `m22(float)` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3fc` | `m00` | `m00()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3fc` | `m01` | `m01()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3fc` | `m02` | `m02()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3fc` | `m10` | `m10()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3fc` | `m11` | `m11()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3fc` | `m12` | `m12()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3fc` | `m20` | `m20()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3fc` | `m21` | `m21()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3fc` | `m22` | `m22()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m00` | `m00()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m01` | `m01()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m10` | `m10()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m11` | `m11()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m20` | `m20()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m21` | `m21()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2dc` | `m00` | `m00()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2dc` | `m01` | `m01()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2dc` | `m10` | `m10()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2dc` | `m11` | `m11()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2dc` | `m20` | `m20()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2dc` | `m21` | `m21()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m00` | `m00()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m01` | `m01()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m10` | `m10()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m11` | `m11()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m20` | `m20()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m21` | `m21()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2fc` | `m00` | `m00()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2fc` | `m01` | `m01()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2fc` | `m10` | `m10()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2fc` | `m11` | `m11()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2fc` | `m20` | `m20()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2fc` | `m21` | `m21()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniond` | `w` | `w()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniond` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniond` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniond` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniondc` | `w` | `w()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniondc` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniondc` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniondc` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionf` | `w` | `w()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionf` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionf` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionf` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionfc` | `w` | `w()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionfc` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionfc` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionfc` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2L` | `x` | `x()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2L` | `y` | `y()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2Lc` | `x` | `x()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2Lc` | `y` | `y()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2d` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2d` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2dc` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2dc` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2f` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2f` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2fc` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2fc` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2i` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2i` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2ic` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2ic` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3L` | `x` | `x()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3L` | `y` | `y()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3L` | `z` | `z()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3Lc` | `x` | `x()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3Lc` | `y` | `y()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3Lc` | `z` | `z()` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3d` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3d` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3d` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3dc` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3dc` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3dc` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3f` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3f` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3f` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3fc` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3fc` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3fc` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3i` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3i` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3i` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3ic` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3ic` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3ic` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4d` | `w` | `w()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4d` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4d` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4d` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4dc` | `w` | `w()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4dc` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4dc` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4dc` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4f` | `w` | `w()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4f` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4f` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4f` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4fc` | `w` | `w()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4fc` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4fc` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4fc` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4i` | `w` | `w()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4i` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4i` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4i` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4ic` | `w` | `w()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4ic` | `x` | `x()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4ic` | `y` | `y()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4ic` | `z` | `z()` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |

## 保留普通写法并补选择器：25个成员家族

| Java类 | 保留方法 | 新增选择器 | 节点 |
|---|---|---|---|
| `net.minecraft.server.level.WorldGenRegion` | `isClientSide()` | `isClientSide()` | 1.21.1 |
| `net.minecraft.world.item.crafting.AbstractCookingRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.ArmorDyeRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.BannerDuplicateRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.BlastingRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.BookCloningRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.CampfireCookingRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.CustomRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.DecoratedPotRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.FireworkRocketRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.FireworkStarFadeRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.FireworkStarRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.MapCloningRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.RepairItemRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.ShapelessRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.ShieldDecorationRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.ShulkerBoxColoring` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.SingleItemRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.SmeltingRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.SmithingTransformRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.SmithingTrimRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.SmokingRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.StonecutterRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.SuspiciousStewRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |
| `net.minecraft.world.item.crafting.TippedArrowRecipe` | `showNotification()` | `showNotification()` | 1.21.1 |

## 仅继承合同受到影响的13个位置

这些类型没有本地原方法声明，选择器随父合同继承。1.21.1的SmithingRecipe另外补回原`showNotification(): boolean`，保留普通调用。

| Java类 | 成员 | 节点 |
|---|---|---|
| `net.minecraft.world.item.crafting.AbstractCookingRecipe` | `result` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.item.crafting.BlastingRecipe` | `result` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.item.crafting.CampfireCookingRecipe` | `result` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.item.crafting.CraftingRecipe` | `showNotification` | 1.21.1 |
| `net.minecraft.world.item.crafting.MapExtendingRecipe` | `showNotification` | 1.21.1 |
| `net.minecraft.world.item.crafting.SmeltingRecipe` | `result` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.item.crafting.SmithingRecipe` | `showNotification` | 1.21.1 |
| `net.minecraft.world.item.crafting.SmokingRecipe` | `result` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.item.crafting.StonecutterRecipe` | `result` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.level.CommonLevelAccessor` | `isClientSide` | 1.21.1 |
| `net.minecraft.world.level.LevelTimeAccess` | `isClientSide` | 1.21.1 |
| `net.minecraft.world.level.ServerLevelAccessor` | `isClientSide` | 1.21.1 |
| `net.minecraft.world.level.WorldGenLevel` | `isClientSide` | 1.21.1 |
