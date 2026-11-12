<!-- wiki-page: mixinjs; locale: cn -->

> **中文** · [English](mixinjs_us)

<a id="wiki-section-1"></a>
# MixinJS（实验性）

MixinJS 将原生方法注入分成两部分：**随加载器启动的声明产物**，以及**可随脚本 reload 替换的回调**。明确启用后，NeoForge 26.x 自动准备内部游戏库；旧版 NeoForge 和 Fabric 使用带强制依赖的载体模组。它不是在运行中的游戏里动态添加 Mixin。

> **当前仅供开发环境实验。** 非空载体在生产环境中会被拒绝。构建/JVM 检查不等于游戏内验收。NeoForge 26.1.2 开发独立服务端的有限冒烟已覆盖加载器发现、Sponge 变换和一个不可取消的 SERVER 回调；这不代表完整原生 ABI、客户端、跨加载器或生产映射已验收。具体证据边界见下文，不构成正式服部署保证。

**运行时开关（默认关闭）**

MixinJS 原生执行默认关闭。若要明确选择开发环境实验，请在保留其他设置的前提下创建或编辑 `<game>/nekojs/config/engine.toml`：

```toml
[mixinjs]
enabled = true
```

运行时配置缺失或格式损坏时，MixinJS 保持关闭。旧路径 `<game>/config/nekojs-engine.toml` 只在规范文件不存在时作为只读兜底；新增修改请使用规范路径。这是同一个引擎设置，不是单独的 MixinJS 配置文件，也没有系统属性旁路。

请在启动前设置，并在每次修改后**完整重启**，关闭时也一样。脚本 reload 和 Probe 都不会切换此值。关闭时，自动启动准备、加载器接纳、已安装原生回调槽及已安装 Hook 的 Probe 重载均不启用；声明编辑、Probe 编辑器声明与离线校验/构建工具仍可使用。已有载体或缓存文件本身不会启用执行。

`enabled = true` 仅表示明确选择实验功能，**不是生产环境解锁**。仅开发环境接纳，以及既有环境配置、本地信任、线程和 ABI 检查仍然有效。

<a id="wiki-section-2"></a>
## 1. 区分声明与回调

| 内容 | 放置位置 | 生效方式 |
|---|---|---|
| 注入声明：目标类、方法描述符、注入点、Hook ID | `nekojs/mixins/*.mixin.js` 或 `*.mixin.ts` | 引擎开关启用后，NeoForge 26.x 自动准备后重启；旧版/Fabric 离线构建、安装并重启 |
| 运行时回调：读取参数、修改返回值 | `nekojs/server_scripts/` 或 `nekojs/client_scripts/` | 启用后启动并经加载器接纳，再按对应 Script Type 加载或 reload |

声明文件不属于普通脚本入口。只有离线声明导入 `nekojs/mixin`；普通脚本使用事件组 `MixinEvents`。

<a id="wiki-section-3"></a>
## 2. 编写离线声明

例如，在 `nekojs/mixins/max-health.mixin.js` 中写：

```js
import { Mixin } from "nekojs/mixin";

Mixin.target("net.minecraft.world.entity.LivingEntity", target => {
    target.inject("getMaxHealth()F", "demo:max_health", build => {
        build.at = "RETURN";
        build.cancellable = true;
        build.scriptType = "SERVER";
        build.thread = "SERVER_MAIN";
        build.require = 1;
    });
});
```

此例的类名和方法名必须与传给工具的**精确运行时命名空间**一致。不能将示例名称或开发映射中的名称直接视为适用于所有版本和加载器。

| 字段 | 当前支持值 | 默认值 |
|---|---|---|
| `at` | `HEAD`、`RETURN` | `HEAD` |
| `cancellable` | 布尔值；允许取消或替换返回值 | `false` |
| `scriptType` | `SERVER`、`CLIENT` | `SERVER` |
| `thread` | 与类型匹配的 `SERVER_MAIN`、`CLIENT_MAIN` | `SERVER_MAIN` |
| `require` | 仅 `1` | `1` |
| `staticMethod` | 是否为静态目标方法；必须与字节码一致 | `false` |

方法选择器必须含完整 JVM 描述符：`getMaxHealth()F` 表示无参数、返回 float；`tick()V` 表示无参数、返回 void。Hook ID 使用 `namespace:path`，已加载的多个载体之间不能重名。

离线求值器没有 Java 类查找、文件、网络或进程 API。除 `nekojs/mixin` 外，仅允许受源目录约束、带明确扩展名的相对静态导入；当前相对模块仅适用于副作用导入，不支持导出。动态导入、顶层 await、异步 builder、未知字段和超出语句预算的执行均会失败。声明回调必须同步返回 `undefined`。

<a id="wiki-section-4"></a>
## 3. 准备声明产物

**NeoForge 26.1.2 / 26.2.0：** 将声明放在开发实例的 `nekojs/mixins/`。NekoJS 读取目标 class 资源但不初始化目标类，并自动准备 `mods/nekojs-mixins-auto-<hash>.jar`。它属于加载器的游戏库，不作为独立条目显示在模组列表中，无需手动构建或安装载体。首次准备和结构变更会以 `NEKO-4090` 停止本次启动，完整重启后使用新库。删除全部声明时清空启用选择；若本次已加载该库，仍需重启。编译失败会保留原选择并停止启动。`nekojs/.cache/mixins/` 下原子更新的选择文件只启用一个哈希；为避免覆盖或删除 Windows 上被加载器锁住的归档，旧的不可变库保留但不注入，可在游戏关闭时删除。

**旧版 NeoForge / Fabric，或离线校验：** 使用下面的工具。高版本离线构建也会生成游戏库；不要将手动安装的产物与工作区自动准备混用。

工具从目标 JAR 或 class 目录直接读取字节码，不初始化目标类。运行工具需要本仓库的构建环境；这不是游戏内命令。

为了避免 PowerShell 的 JSON 引号问题，在仓库根目录创建 `mixin-args.json`：

```json
[
  "mixins", "validate",
  "--source", "nekojs/mixins",
  "--classes", "runtime-targets.jar",
  "--minecraft", "26.1.2",
  "--loader", "neoforge",
  "--loader-version", "26.1.2.71",
  "--side", "client"
]
```

```powershell
./gradlew.bat :common:packTool -PpackArgsFile=mixin-args.json
```

将 `runtime-targets.jar` 替换为实际的目标字节码路径；版本号和物理侧别也必须与开发运行环境完全匹配。仓库的 `26.2.0` 节点运行时将 Minecraft 标识为 `26.2`，该配置应使用 `--minecraft 26.2` 和 NeoForge `26.2.0.57`；自动准备会读取原生标识。Fabric 使用 `--loader fabric` 和实际 Fabric Loader 版本，而不是 Fabric API 版本。

校验成功后，把参数中的 `validate` 改为 `build`，追加 `"--output", "build/demo-mixins.jar"`，再运行同一命令。已有输出文件不会被覆盖，需选择新路径或自行处理旧文件。

当前工具使用 `runtime` 命名空间，生成的 Mixin 显式关闭重映射；**没有通用的开发映射到生产映射转换功能**。缺失类、错误描述符、静态形式不匹配或不支持的方法会在离线校验阶段失败。

<a id="wiki-section-5"></a>
## 4. 激活并完整重启

先在启动前设置引擎开关；修改开关必须完整重启。NeoForge 26.x 自动准备完成后，按提示重启。下一次启动必须由加载器真正接纳受管游戏库，不能只凭磁盘上有文件就加载回调。不要编辑自有缓存或额外安装手动副本。

旧版 NeoForge / Fabric：

1. 将生成的载体 JAR 放入该开发实例的 `mods/`，保留 NekoJS 及其依赖。
2. 替换载体时移除旧副本，避免重复 Hook ID。
3. 完整停止并重启实例，让原生加载器在正常启动阶段发现载体。
4. 确认载体的 Minecraft、加载器及版本、物理侧别和 ABI 与当前环境匹配。

旧版 NeoForge 和 Fabric 载体都声明 **NekoJS 为加载器强制依赖**；缺少 NekoJS 时加载器会拒绝该载体。所有新生成产物另带早期插件保护：NekoJS 缺失、引擎开关关闭或运行时配置无法读取、环境配置不匹配或处于生产环境时，在链接运行时桥之前禁用注入。因此高版本残留的游戏库不会在缺少 NekoJS 时注入。这道保护尚不等于真实加载器验收。

修改声明后，仍需完成准备/构建并完整重启才能生效。普通 reload 也不能添加、删除或重织原生注入结构。

`--side client` 是物理客户端配置，可以含集成服务端的 SERVER 回调；`--side server` 是独立服务端配置，不能含 CLIENT 回调。这些配置能力不代表真实独立服务端验收已经完成。

<a id="wiki-section-6"></a>
## 5. 在普通脚本中绑定 Hook

载体已被加载器接纳后，在 `nekojs/server_scripts/max-health.js` 中写：

```js
MixinEvents.inject("demo:max_health", event => {
    event.returnValue = 40;
});
```

不要写成 `event => event.returnValue = 40`：它会返回数字，导致本次控制决定被丢弃。回调必须同步返回 `undefined`，不能是 async 函数，也不能返回 Promise。

- 仅对应类型的 SERVER、CLIENT 脚本可绑定；STARTUP、TEST 不支持。
- 一个已安装的 Hook 只允许一个回调；未知 ID、类型不符或重复绑定会使候选加载失败。
- 入口、回调来源及整个模块会话必须是本地可信来源；远程依赖、来源不明或虚拟生成的回调不能取得绑定权限。
- 有已安装 Hook 的脚本类型，其首次加载也走候选事务。成功 reload 替换回调；失败或重复绑定保留旧代回调。
- 世界退出清理 SERVER、CLIENT 回调；后续需要加载或 reload 对应脚本类型重新绑定。进程级注入结构仍然存在。

脚本目录及 reload 的基本用法见 [脚本基础](script-basics_cn) 和 [命令](commands_cn)。

<a id="wiki-section-7"></a>
## 6. 事件对象与控制规则

| 成员 | 契约 |
|---|---|
| `self` | 原生接收者；静态方法为 null。属性只读，不表示原生对象不可变。 |
| `args` | 只读的原生参数元组；不能替换元组元素。 |
| `returnValue` | RETURN 时为原始结果；HEAD 时尚无结果。仅 cancellable 的非 void Hook 可写。 |
| `cancel()` | 仅 cancellable、void、HEAD Hook 可用；非 void HEAD 必须设置返回值。 |

未修改的 RETURN 保留原结果。异常、非法写入或非 `undefined` 返回会丢弃本次所有控制写入；即使脚本捕获非法写入的异常，之前的写入也不会恢复。**这不能回滚对原生对象、世界或其他外部资源已经产生的副作用。**

事件控制对象仅在同步回调期间有效；回调后访问会失效，不能保存它供定时器或异步任务使用。候选执行期间、原生线程不匹配或同一 Hook 重入时会跳过回调，保留原生行为；不会排队等待脚本线程。不同 Hook 可以嵌套。

<a id="wiki-section-8"></a>
## 7. 类型与当前不支持的能力

支持 boolean、单个 UTF-16 code unit 的 char、范围检查后的 byte/short/int、有限 float/double，以及类型可赋值的原生引用和原生数组。引用允许 null，String 返回值接受 JavaScript 字符串；普通 JS 对象和数组不能冒充任意原生引用。

当前不支持 long/BigInt、构造器、类初始化器、abstract/native/synthetic/bridge 方法、HEAD/RETURN 以外的注入点，以及运行时注入类路径或动态重织。

MixinJS 关闭时仍可使用声明编辑器补全及离线工具；`MixinEvents` 辅助对象也可能存在，但没有已安装的回调槽。运行 [Probe](probe-type-generation_cn) 后，仅在执行已启用时，TypeScript 才为**加载器已接纳的计划**生成 Hook ID 字面量重载及 self、参数、返回值类型。待构建声明文件不会提前获得“已安装”类型。没有提示时，先检查引擎开关，再检查载体是否实际加载及环境配置是否匹配。

<a id="wiki-section-9"></a>
## 8. 故障排查与验收边界

| 诊断码 | 优先检查 |
|---|---|
| `NEKO-4090` | 自动准备与重启提示、缓存归属、加载器接纳、声明、描述符、重复 ID、精确配置、生产环境拒绝。 |
| `NEKO-4091` | 本地可信来源、候选注册权限、Runtime Root 桥接所有权。 |
| `NEKO-4092` | 同步返回要求、返回值 ABI、过期对象、线程或回调旁路。 |

详细说明见 [错误与日志参考](error-reference_cn)。普通加载器/Mixin 变换失败也可能由加载器自身报告，而不是上述诊断码。

公共运行时、载体字节码、TypeScript 和五节点构建检查已通过。NeoForge 26.1.2 开发环境的无界面服务端也已验证自动准备、重启、真实游戏库发现、Sponge 变换和一个不可取消的 SERVER 回调；26.2.0 节点通过了原生启动。这仍不等于完整的客户端/服务端、ABI、旧版/Fabric 或映射验收；正式发布前仍需补齐，目前生产接纳保持关闭。

<!-- wiki-nav -->

---

[上一篇: 客户端绘制与后处理](client-rendering_cn) · [目录](Home) · [下一篇: Node.js 兼容](nodejs-compatibility_cn)
