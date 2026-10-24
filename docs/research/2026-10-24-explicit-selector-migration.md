# 显式方法选择器声明迁移：待定方案与当前范围

状态：本文保留初次原型与96项起点。当时继承范围尚未收敛；后续已完成当前五节点默认输出的范围、Java身份与完整严格副本检查，见[具体决策与完整清单](2026-10-24-explicit-selector-decision.md)。生产未实施，新的迁移规则仍待用户批准；不能将此前五处Entity.attackable批准扩展到本方案。

[主代理实验与复核](../architecture-refactor/evidence/2026-10-24-method-selectors/README.md)证明当前NeoForge 26.1.2.71实际安装环境支持数字字段与显式方法选择器并存：`vector.x`和`vector["x()"]()`均返回数字；JOML支持`matrix["m00(double)"](3)`，提取显式getter后调用也成功。普通`vector.x()`在运行时仍可调用，但无法与同名数字字段同时写成正确的普通TypeScript类成员；数字与函数交叉类型会接受运行时无效的提取调用，不能作为替代。

## 拟实现的行为

保留真实字段类型、可写/只读约束及运行时普通方法调用；只在得到确切迁移授权的范围内，将发生冲突的方法声明改为独立字面量选择器。参数、返回、泛型、继承、源方法身份和明确的IR编辑继续生效。没有新增JavaScript运行时包装器、绑定、反射权限或按路径/类型放宽hide/exclude。Java与Python合同不在本次拟迁移范围。

选择器使用源Java方法名、擦除后的原始参数类型和二进制内部类名称，varargs使用数组拼写。当前安装环境的`@Remap("renamed") original(int)`实测是普通`renamed(2)`成功、显式`["original(int)"](2)`成功；`["renamed(int)"](2)`失败。不能将脚本名或用户编辑后的TypeScript参数类型直接拼入选择器。隐藏成员不能生成选择器；无真实sourceMethod的新增声明和手写重载需要明确策略，不能猜签名。

## 初次原型时的继承缺口（后续已完成当前默认范围）

以下是从6e43b827五份默认server输出重新独立复算的31类96个冲突起点、436个节点/类/成员、1002个TS2300位置；完整原声明、行列和SHA在实验归档的strict/collision-inventory.json中。重复import错误已经修复，不在此范围。

仅改变这些类自己的方法名仍可能留下父接口/父类方法与子类字段的不兼容，例如ServerLevel的数字noSave与Level的noSave()。同一继承分支中其他覆盖或实现也必须一致。Luna已只读核对当前26.1.2输出：ServerLevel第469/791行的boolean字段/方法与Level第719行方法冲突；Vec3实现Position，Position第560–562行要求x/y/z方法；Vector3d/Vector3dc及Matrix2d/Matrix2dc等类/接口存在同样约束。这些具体反例支持向祖先和其他实现类追踪，但尚未计数完整闭包。后续已计算并记录当前默认范围：208个有声明成员家族、另13个仅继承位置。保留普通写法的原型已完成十个完整严格程序，5537→1621，删除3916/新增0；生产公开backend实现仍待迁移批准。下面96项只是初始起点，不能代表最终全部影响，也不是删除许可。

后续[决策](2026-10-24-explicit-selector-decision.md)列出每个节点/类/成员与新选择器，证据同时保留1004条原/新签名和Java身份。若规则获准，仍须在生产生成器实现、正确处理泛型继承及作者编辑，再按既有流程再生并复验完整默认输出；副本原型不能代替该验收。

| Java declaration class | Member | Nodes |
|---|---|---|
| `net.minecraft.server.level.ServerLevel` | `noSave` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.server.level.ServerPlayer` | `gameMode` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.entity.animal.chicken.Chicken` | `isChickenJockey` | 26.1.2, 26.1.2-fabric |
| `net.minecraft.world.entity.animal.Chicken` | `isChickenJockey` | 1.21.1 |
| `net.minecraft.world.item.crafting.ShapedRecipe` | `showNotification` | 1.21.1 |
| `net.minecraft.world.item.crafting.SingleItemRecipe` | `result` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.level.biome.BiomeGenerationSettings` | `features` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.biome.BiomeSpecialEffects` | `dryFoliageColorOverride` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.biome.BiomeSpecialEffects` | `foliageColorOverride` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.biome.BiomeSpecialEffects` | `grassColorModifier` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.biome.BiomeSpecialEffects` | `grassColorOverride` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.biome.BiomeSpecialEffects` | `waterColor` | 26.1.2-fabric, 26.2.0-fabric |
| `net.minecraft.world.level.Explosion` | `radius` | 1.21.1 |
| `net.minecraft.world.level.Level` | `isClientSide` | 1.21.1 |
| `net.minecraft.world.level.saveddata.maps.MapItemSavedData` | `locked` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.phys.Vec3` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.phys.Vec3` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `net.minecraft.world.phys.Vec3` | `z` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2d` | `m00` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2d` | `m01` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2d` | `m10` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2d` | `m11` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2f` | `m00` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2f` | `m01` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2f` | `m10` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix2f` | `m11` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m00` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m01` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m02` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m10` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m11` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m12` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m20` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m21` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3d` | `m22` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m00` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m01` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m02` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m10` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m11` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m12` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m20` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m21` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3f` | `m22` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m00` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m01` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m10` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m11` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m20` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2d` | `m21` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m00` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m01` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m10` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m11` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m20` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Matrix3x2f` | `m21` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniond` | `w` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniond` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniond` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaterniond` | `z` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionf` | `w` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionf` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionf` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Quaternionf` | `z` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2L` | `x` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2L` | `y` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2d` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2d` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2f` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2f` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2i` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector2i` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3L` | `x` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3L` | `y` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3L` | `z` | 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3d` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3d` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3d` | `z` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3f` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3f` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3f` | `z` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3i` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3i` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector3i` | `z` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4d` | `w` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4d` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4d` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4d` | `z` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4f` | `w` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4f` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4f` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4f` | `z` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4i` | `w` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4i` | `x` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4i` | `y` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |
| `org.joml.Vector4i` | `z` | 1.21.1, 26.1.2, 26.1.2-fabric, 26.2.0, 26.2.0-fabric |

当前尚未完成：五节点实际选择器调用矩阵、生产公开backend实现/回归、实际再生后的完整严格通过及人工结论。当前默认继承范围与Java签名采集已完成；其他自定义配置不被这份默认清单穷尽。原型不使完整5537条失败变为通过，也不解决其他wildcard/SAM、专用服务器不可加载类型和被排除engine类型的问题。
