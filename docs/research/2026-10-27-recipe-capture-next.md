# 下一共享原因：RecipeSerializer wildcard 与双向 Codec

## 当前目标与范围

完整捕获视图和连续 wildcard 合同已实现、提交并通过五节点新安装，15c4d9a6 的完整严格 TS 仍为 1029。下一目标是 F6 Recipe 约束族 488 条；优先追踪真实 Java wildcard 返回和嵌套 Codec，不改工厂、不删成员或泛型参数，也不全局放宽 Codec 的方差。

来源调查由 Luna 子代理只读执行；主代理核对原始源码签名和摘要后记录。随后主代理已取得公开 backend 的真实 DFU 红证，以及 Java 正负捕获编译对照，[五组冻结实验](../architecture-refactor/evidence/2026-10-27-recipe-capture-prototypes/README.md)均仍失败。不透明标记遗漏独立捕获身份，never方案泄漏底类型，全部未投产；未修改本族产品或宣称全族已定位。

## 已核实来源

NeoForm 26.1.2 本地反编译缓存 `decompile_af69325062c0f778607d4b1c4b4b6e42c68bd651_output.jar`，SHA256 `1ddb1c8e289c9d7cc22b0c592092b00a872e583147e68e91a94e918d2a6dc73b`。其中：

- `net/minecraft/world/item/crafting/Recipe.java:18,36`：`Recipe<T extends RecipeInput>` 的 `getSerializer()` 返回 `RecipeSerializer<? extends Recipe<T>>`。
- `RecipeSerializer.java:7`：record 的参数为 `T extends Recipe<?>`，codec 组件为 `MapCodec<T>`。
- `SingleItemRecipe` 实现 `Recipe<SingleRecipeInput>`，返回 `RecipeSerializer<? extends SingleItemRecipe>`；`AbstractCookingRecipe.java:36` 再返回 `RecipeSerializer<? extends AbstractCookingRecipe>`。
- `StonecutterRecipe.java:28` 返回具体 `RecipeSerializer<StonecutterRecipe>`；`CraftingRecipe.java:18` 返回 `RecipeSerializer<? extends CraftingRecipe>`。

DFU `datafixerupper-10.0.21-sources.jar`，SHA256 `ecac55047605b48de872ebf8041018ea83e8fe6d06b309ff532bf812a83985e0`：`MapCodec.java:18` 同时实现 MapDecoder/MapEncoder；`MapDecoder.java:17` 解码得到 A，`MapEncoder.java:11` 编码接收 A。不能仅因 Java 允许 wildcard 返回覆盖，就把整个编码器标为协变。

缓存位于本机 Gradle `caches/neoformruntime/intermediate_results/` 及 `caches/modules-2/files-2.1/com.mojang/datafixerupper/10.0.21/`，没有复制完整 Minecraft 或 DFU 源码入库。

## 诊断与尚未证明的范围

前驱 5dd4de8e 的 26.1.2-server 三个代表点：crafting 声明第33行 AbstractCookingRecipe.getSerializer 的 TS2416、第490行 StonecutterRecipe.getSerializer 的 TS2416、第145行 CraftingRecipe 的 TS2430。生成器把以上 wildcard 压成具体 RecipeSerializer<X>，随后 MapCodec/Codec 链暴露不变性；相关 TS2344 约束错误是已观察到的下游表现。

15c4d9a6 的该族计数及完整诊断块保持不变。没有证明488条都由一个单点造成，也没有据26.1.2来源推断26.2每个签名相同；两个版本的族计数为每入口62/60，后续要保留这一区分。

## 实施顺序与验收

1. 已建立真实 public backend 红证：Recipe<I>、带 `T extends Recipe<?>` 的 Serializer、真实DFU MapCodec/Codec/DataResult、wildcard中间覆盖及具体叶子覆盖。简化Codec确实不能复现继承错误；真实DFU基线12条错误，加强独立捕获控制后13条。已保留完整输入和原文，不删除导致困难的成员。
2. 正例保留具体 recipe 到两级父接口赋值、父级 codec 解码得到父 bound、具体返回的窄读取，以及合法的同一捕获域往返；负例拒绝将任意父对象编码到可能捕获更窄类型的 codec，拒绝不相关 serializer、错误 SAM 参数域和错误结果。
3. 基于来源与公开消费选择现有 IR/返回 facet/实例面生成接缝；authored、hide、exclude、rename、局部泛型变量及 backend 重用均需保留。若候选无法同时表达完整读写合同，记录失败，不以 any、删成员或全局协变掩盖。
4. 定向红绿之后执行 common/check、严格消费链、官方 golden 再生与差异审阅、受影响平台矩阵；新制品五节点十入口重采并按完整诊断块和互斥分类比较。性能和最终候选门禁仍独立验收。

当前先推进 F8 的 Stream wildcard 返回合同（32条），复用已有完整StreamView和继承facet，不借此关闭F6。F6继续要求有作用域捕获/等价安全表示的真实正负证明，现有近似不能因其余错误消失就进入产品。
