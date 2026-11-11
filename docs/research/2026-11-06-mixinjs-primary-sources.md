# MixinJS 一手资料核对

日期：2026-11-06。性质：供维护者审阅的设计调研，不是实现交付或内嵌 AI 手册。只修改本文；未运行、构建或移植参考模组。

## 1. 参考项目的版本与许可事实

- 仓库为 [3093FengMing/mixinjs](https://github.com/3093FengMing/mixinjs)，查阅时 GitHub 显示已归档；本次源代码固定为提交 [`5d23461791d1484404bba2d3ae775174105e35e7`](https://github.com/3093FengMing/mixinjs/commit/5d23461791d1484404bba2d3ae775174105e35e7)。
- 配置实际指向 Minecraft **1.21.1**、NeoForge **21.1.42**、Rhino **2101.2.5-build.54**、KubeJS **2101.7.0-build.171**；构建使用 Java 21。声明的宽版本范围不等于已验证兼容 NekoJS 的 NeoForge 26.x 或 Fabric。[版本参数](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/gradle.properties)、[构建文件](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/build.gradle)。
- `mod_license` 写的是 `GNU GPL 3.0`，但该提交文件树没有独立 LICENSE/COPYING，GitHub API 的 license 字段为空。不能因此当作没有许可限制，亦不能仅凭元数据断言可直接移植到本项目；复用源代码前需维护者核实。本轮只借鉴结构和设计问题，不复制其实现。[完整提交树](https://github.com/3093FengMing/mixinjs/tree/5d23461791d1484404bba2d3ae775174105e35e7)、[许可声明位置](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/gradle.properties)。

## 2. 它实际如何运行

| 阶段 | 已从源代码核实的行为 |
|---|---|
| 极早启动 | CoreMod 通过服务发现加载 `ITransformationService`，在 `onLoad` 修改 Sponge 的 `MixinConfig` 与 `MixinInfo` 实现。它使用 ASM、Unsafe 和 `IMPL_LOOKUP` 调用 ClassLoader 的 defineClass，不是普通 Mixin 配置插件功能。 |
| 固定主模组发现 | `IDependencyLocator.scanMods` 将 `META-INF/jarjar/mixinjs-main.jar` 加入加载器发现流程。这个 JAR 是预先打包的主模组，不是脚本生成的 Mixin carrier。 |
| 声明脚本 | 主 JAR 的 manifest 指定 `MixinConnector`。其 `connect` 读取 `mixinjs/mixins.config.json`，运行列出的 JS 文件；脚本通过 builder 生成类。 |
| 类与配置输出 | ASM 写出带 `@Mixin`、注入注解及嵌套 `@At` 的真实 class，放在游戏目录 `.mixinjs/mixinjs/generated/`；配置写入 `mixinjs/generated.mixins.json`，再调用 `Mixins.addConfiguration`。 |
| 字节码可见性 | 被修改的 `MixinInfo` 先查常规 bytecode provider，失败后读取 `.mixinjs` 中的 class；被修改的 `MixinConfig` 支持外部配置文件流。因此，磁盘上出现 class/config 就能被加载，是其修改 Mixin 内部实现后的效果。 |
| 游戏运行时 | 生成 handler 调用固定 Java dispatcher，再根据字符串 key 找到 Rhino 回调并传入 this、CallbackInfo 与参数数组。 |

上述各行的一手实现分别见：[TransformationService](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs_coremod/core/MixinJsTransformationService.java)、[服务声明](https://github.com/3093FengMing/mixinjs/tree/5d23461791d1484404bba2d3ae775174105e35e7/src/main/templates/META-INF/services)、[DependencyLocator](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs_coremod/core/MixinJsDependencyLocator.java)、[JarInJarUtils](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs_coremod/utils/JarInJarUtils.java)、[Connector](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/mixin/MixinJsConnector.java)、[配置加载](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/config/MixinJsConfig.java)、[类生成](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/core/MixinClassGenerator.java)、[外部 class 读取](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs_coremod/core/MixinInfoInjector.java)、[dispatcher](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/core/MixinClassUtils.java)。

**结论：**“JS 描述 → 标准 Mixin 注解 class → Java dispatcher → JS handler”值得借鉴；“普通插件能直接读取任意外部或内存生成 class”不成立，参考项目为此额外修改了 Mixin 内部类。

## 3. 参考实现不能直接当作 NekoJS 契约的地方

1. **线程与所有权：**`MixinScriptManager` 持有静态 Rhino Context/scope 和 handlers HashMap；`handle` 在调用线程直接执行回调，没有看到适用于 NekoJS owner-thread 模型的路由与归属校验。不能把它当作 Graal Context 并发调用的证明。[MixinScriptManager](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/script/MixinScriptManager.java)。
2. **重载：**`/mixinjs reload` 调用 `loadScript`，后者重新建 Rhino 环境、执行声明、登记 handler；没有重新织入已加载目标类的实现。源码行为只能支持“脚本回调重新绑定”的推断，不能证明增删注入结构可热重载，也未看到 NekoJS 式候选失败保留旧代的事务流程。[命令](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/commands/MixinJsCommand.java)、[管理器](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/script/MixinScriptManager.java)。
3. **身份：**handler 名由方法标记、injector 名和 at 值拼接；不能直接作为跨目标、重载方法、多注入点的稳定唯一 slot。[MixinBuilderJS](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/script/js/MixinBuilderJS.java)。
4. **映射：**配置会转写用户提供的 refmap 名称，但未发现把任意脚本目标选择器自动生成跨运行环境 refmap 的完整流程。生成器写入 target 字符串不能替代名称映射。[MixinJsConfig](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/config/MixinJsConfig.java)、[生成器](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/core/MixinClassGenerator.java)。
5. **声明广度不等于实现完备度：**`InjectorJS` 列出多种 Mixin/MixinExtras 注解，但通用生成器只发出部分通用字段；例如 `InjectInjectorJS.locals` 存在，而本次检查的生成器未写入 locals 注解成员。不能据注册名称列表宣称全部注解语义已经支持。[InjectorJS](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/script/js/InjectorJS.java)、[InjectInjectorJS](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/script/js/injectors/InjectInjectorJS.java)、[生成器](https://github.com/3093FengMing/mixinjs/blob/5d23461791d1484404bba2d3ae775174105e35e7/src/main/java/me/fengming/mixinjs/core/MixinClassGenerator.java)。

## 4. Sponge Mixin 与加载器的硬约束

本次另核对 Sponge Mixin master 提交 [`4053421aa10aaac6127d969028a29c94fe3054f6`](https://github.com/SpongePowered/Mixin/commit/4053421aa10aaac6127d969028a29c94fe3054f6)。这是设计资料版本，不表示它等于 NekoJS 每个节点内嵌的 Mixin 版本。

- **目标必须及时准备。**Mixin 在类定义前处理字节码；MixinInfo 会检查 target 是否已经加载，并可能抛出 `MixinTargetAlreadyLoadedException`。不能在普通 STARTUP 脚本加载完成后才假定所有 Minecraft 类仍可注入；也不能把 `Mixins.addConfiguration` 理解成已加载类的通用重新织入接口。[MixinInfo](https://github.com/SpongePowered/Mixin/blob/4053421aa10aaac6127d969028a29c94fe3054f6/src/main/java/org/spongepowered/asm/mixin/transformer/MixinInfo.java)、[环境说明](https://github.com/SpongePowered/Mixin/wiki/Introduction-to-Mixins---The-Mixin-Environment)。
- **配置插件的能力有限。**`IMixinConfigPlugin.getMixins` 返回追加类名，`acceptTargets` 不支持靠添加名称扩展 target 集合；接口要求避免引用游戏类及非早期安全模组类。它不负责安装额外 classpath 或自带 class 字节供应器。[IMixinConfigPlugin](https://github.com/SpongePowered/Mixin/blob/4053421aa10aaac6127d969028a29c94fe3054f6/src/main/java/org/spongepowered/asm/mixin/extensibility/IMixinConfigPlugin.java)。
- **Inject 与返回值有明确语义。**HEAD 为方法入口，RETURN 可对应多个返回指令；void 用 CallbackInfo，非 void 用 CallbackInfoReturnable。`setReturnValue` 内部调用 cancel，因此“返回一个值”与“正常继续原方法”不能在脚本协议里含糊处理。`require` 可要求最少匹配数。[callback 官方说明](https://github.com/SpongePowered/Mixin/wiki/Advanced-Mixin-Usage---Callback-Injectors)、[CallbackInfoReturnable](https://github.com/SpongePowered/Mixin/blob/4053421aa10aaac6127d969028a29c94fe3054f6/src/main/java/org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java)。
- **构造器需要单独设计。**2016 年 callback wiki 的“constructor 仅 RETURN”不应直接当作所有现代内嵌版本的完整规则。本次核对的 CallbackInjector 明确拒绝 constructor 上的 cancellable Inject；构造过程还涉及未初始化 this。建议首版排除 `<init>`/`<clinit>`，后续以各节点实际 Mixin 源码与测试决定支持范围。[CallbackInjector](https://github.com/SpongePowered/Mixin/blob/4053421aa10aaac6127d969028a29c94fe3054f6/src/main/java/org/spongepowered/asm/mixin/injection/callback/CallbackInjector.java)。
- **标准 JAR 配置发现是可依赖的基础。**NeoForge 文档列出 `neoforge.mods.toml` 的 `[[mixins]] config`，Fabric 的 `fabric.mod.json` 亦有 mixins 声明。由标准模组发现流程加载预生成 carrier JAR 是较保守的候选路线；“当前进程里才生成 JAR 并立即被 loader 发现”仍须另外证明，不能由这两份文档推出。[NeoForge mod files](https://docs.neoforged.net/docs/gettingstarted/modfiles/#mixin-configuration-properties)、[Fabric 项目结构](https://docs.fabricmc.net/develop/getting-started/project-structure#fabricmodjson)。
- **命名环境不可省略。**Fabric 的历史映射文档说明开发命名与发布运行环境可不同；其旧版 intermediary 说明不能不加区分推广到所有 26.x 节点。NekoJS 应按实际节点记录并验证命名空间、选择器和 refmap，不把 Probe 能显示的名字直接当作运行时 Mixin 选择器。[Fabric mappings](https://wiki.fabricmc.net/tutorial:mappings)。

## 5. 设计判断与待验证项

以下为推断／建议，不是上游已经提供的 NekoJS 功能：

1. 优先将注入结构编译为标准带注解 class、Mixin 配置及加载器元数据，通过下次进程启动发现；生成物绑定 MC 节点、loader、命名环境和结构指纹。不要复制参考项目修改 Mixin 内部类的方案作为默认路线。
2. 注入结构与运行时 handler 分开：class 中只保留稳定 Java dispatcher/slot，JS handler 由现有 NekoRuntimeRoot 的生命周期拥有。结构变更必须重启；handler 是否支持候选重载，需要先证明固定槽、切换时序、旧代释放和线程模型。
3. 若首版坚持本次启动自动读取脚本并立即注入，则每个 loader 的发现入口、生成 class 的 bytecode provider 可见性、目标尚未加载、桥接类可见性与 Graal 初始化安全性都属于先行验证条件；不能写成普通 `getMixins()` 就能完成。
4. 返回值或取消必须同步决定，不能简单异步排队到 owner thread 后再修改已返回的方法。跨线程同步等待又可能死锁；第一版应明确限制调用线程或限制目标范围。
5. 生成器要覆盖静态/实例方法、所有 primitive、引用和数组、long/double 双槽参数、精确 descriptor、多个 RETURN、异常与重入。`COMPUTE_FRAMES` 也不能借加载游戏目标类来偷懒解析共同父类。[JVMS 局部变量槽规则](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.1)。
6. 后续若需要包装原调用，优先评估 MixinExtras `WrapOperation`。官方说明其可在多个包装者之间串联，适合“保留并包装原操作”；这不等于所有注入类型无冲突，也不自动解决 JS continuation 的有效期、线程或多次调用语义。[WrapOperation 官方说明](https://github.com/LlamaLad7/MixinExtras/wiki/WrapOperation)。

## 6. 本次核对范围

已读上述固定提交的关键 bootstrap、类生成、配置、运行时与命令源代码；已查 Sponge 和 loader 的一手资料。未执行参考模组，没有验证其真实安装步骤、所有 injector、线程安全或现代节点兼容性。没有运行 NekoJS 构建、游戏或字节码实验。本轮笔记只提供事实和后续实验清单，不构成 MixinJS 可用性验收。
