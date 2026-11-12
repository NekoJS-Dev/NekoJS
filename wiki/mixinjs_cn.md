<!-- wiki-page: mixinjs; locale: cn -->

> **中文** · [English](mixinjs_us)

<a id="wiki-section-1"></a>
# MixinJS（实验性）

MixinJS 让脚本在 Minecraft 的某个 Java 方法开头或返回时插一段代码，读取参数、改返回值或取消调用。用法分两部分：

1. 写一个**注入声明**，说明要注入哪个方法。它在游戏启动时由加载器生效，改了必须重启游戏。
2. 在普通脚本里写**回调**，决定注入点要做什么。回调可以用 `/nekojs reload` 随时修改。

> **这是实验功能，只能在开发环境里用。** 在正式游戏（生产环境）里，NekoJS 会拒绝加载注入。目前只在 NeoForge 26.1.2 开发环境的专用服务器上验证过简单场景，其他版本、加载器和客户端组合都还没有完整测试。不要用在正式整合包或服务器上。

<a id="wiki-section-2"></a>
## 开始之前：开启 MixinJS

MixinJS 默认关闭。在 `nekojs/config/engine.toml` 里加上：

```toml
[mixinjs]
enabled = true
```

然后**完全重启游戏**。以后每次改这个开关（包括关掉）都要完全重启，`/nekojs reload` 和 `/nekojs probe` 都不会重新读取它。

- 配置文件缺失或格式错误时，MixinJS 保持关闭。
- 开启 MixinJS 不需要打开 `allowAsm` 或 `allowReflection`，也不会顺带打开它们。
- 关闭时，写声明文件的代码补全和离线工具仍然可以用，只是注入不会生效。

其他配置项见 [配置参考](configuration_cn)。

<a id="wiki-section-3"></a>
## 工作原理

| 部分 | 放在哪里 | 怎么生效 |
|---|---|---|
| 注入声明：目标类、方法、注入位置、Hook ID | `nekojs/mixins/` 下的 `*.mixin.js` 或 `*.mixin.ts` | 生成注入文件，然后完全重启游戏 |
| 回调：读参数、改返回值 | `nekojs/server_scripts/` 或 `nekojs/client_scripts/` | 游戏加载注入后，像普通脚本一样加载或 reload |

两部分靠 **Hook ID**（例如 `demo:max_health`）对应起来。声明文件用 `import { Mixin } from "nekojs/mixin"`；普通脚本用全局的 `MixinEvents`。

<a id="wiki-section-4"></a>
## 1. 编写注入声明

下面这个例子把所有生物的最大生命值改成 40。新建 `nekojs/mixins/max-health.mixin.js`：

```javascript
import { Mixin } from "nekojs/mixin";

Mixin.target("net.minecraft.world.entity.LivingEntity", target => {
    target.inject("getMaxHealth()F", "demo:max_health", build => {
        build.at = "RETURN";
        build.cancellable = true;
        build.scriptType = "SERVER";
        build.thread = "SERVER_MAIN";
    });
});
```

- 第一个参数是**方法名加完整的 JVM 描述符**：`getMaxHealth()F` 表示没有参数、返回 float；`tick()V` 表示没有参数、返回 void。
- 第二个参数是 Hook ID，格式是 `命名空间:路径`，只能用小写字母、数字和 `_`、`.`、`-`、`/`。所有已加载的声明里不能重名。
- 类名和方法名必须是**当前游戏运行时实际使用的名字**。不同 Minecraft 版本和加载器的名字可能不同，NekoJS 不会帮你转换映射。

可以设置的字段：

| 字段 | 可选值 | 默认值 |
|---|---|---|
| `at` | `HEAD`（方法开头）、`RETURN`（方法返回时） | `HEAD` |
| `cancellable` | `true` 才能取消调用或修改返回值 | `false` |
| `scriptType` | `SERVER`、`CLIENT` | `SERVER` |
| `thread` | 和 `scriptType` 对应：`SERVER_MAIN` 或 `CLIENT_MAIN` | `SERVER_MAIN` |
| `require` | 只能是 `1` | `1` |
| `staticMethod` | 目标是静态方法时设为 `true`，必须和实际方法一致 | `false` |

注意：注入只作用于你指定的那个类里的方法。如果子类重写了这个方法，又没有调用父类的版本，对子类对象的调用就不会经过你的注入。

声明文件运行在一个单独的受限环境里：

- 不能访问 Java 类、文件、网络。
- 只能导入 `nekojs/mixin`，以及带 `.js` 或 `.ts` 扩展名的相对路径文件。被导入的文件不能有导出。
- 不能用默认导入、动态 `import()`、顶层 `await`。
- `build => { ... }` 必须同步执行完，不能返回值。写了不存在的字段会报错。
- 单次运行最多执行 100,000 条语句。

<a id="wiki-section-5"></a>
## 2. 生成并加载注入

**NeoForge 26.1.2 和 26.2.0：自动生成。** 把声明放进 `nekojs/mixins/` 后启动游戏。NekoJS 会自动生成 `mods/nekojs-mixins-auto-<hash>.jar`，然后以 `NEKO-4090` 停止这次启动，提示你重启。再次启动后注入就生效了。

- 这个 jar 不会出现在模组列表里，不用手动安装。
- 以后每次改了声明，都会重新生成并要求重启一次。
- 删掉所有声明后，下次启动就不再加载注入。如果当时注入已经加载，还要再重启一次。
- 旧的 jar 不会被自动删除，但也不会被加载。可以在游戏关闭时手动删掉。
- 不要同时手动安装注入 jar，否则会报错。

**旧版 NeoForge 和 Fabric：手动构建。** 需要本仓库的开发环境，在仓库根目录用 Gradle 运行，不是游戏内命令。先新建 `mixin-args.json`（单独放参数文件，可以避开 PowerShell 的引号问题）：

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

- `--classes` 换成包含目标类字节码的 jar 或目录。
- 版本号要和运行环境完全一致。26.2.0 环境里 Minecraft 版本写 `26.2`。Fabric 写 `--loader fabric`，`--loader-version` 填 Fabric Loader 的版本，不是 Fabric API 的版本。
- `--side client` 用于客户端（单人游戏的内置服务器也算），可以包含 SERVER 和 CLIENT 两种 Hook。`--side server` 用于专用服务器，不能包含 CLIENT Hook。

校验通过后，把 `validate` 改成 `build`，再加上 `"--output", "build/demo-mixins.jar"`，运行同一条命令。输出文件已存在时不会覆盖，要换一个路径。

把生成的 jar 放进开发环境的 `mods/`，删掉旧版本的 jar，然后完全重启游戏。这个 jar 依赖 NekoJS，没装 NekoJS 时加载器会拒绝它。

不管哪种方式，**改了声明都要重新生成并完全重启**。`/nekojs reload` 不能增加、删除或修改注入。

<a id="wiki-section-6"></a>
## 3. 在脚本里写回调

注入加载后，新建 `nekojs/server_scripts/max-health.js`：

```javascript
MixinEvents.inject("demo:max_health", event => {
    event.returnValue = 40;
});
```

执行 `/nekojs reload`，生物的最大生命值就变成 40 了。

**回调不能有返回值。** 不要写成 `event => event.returnValue = 40`，这样会返回 40，这次的修改会被丢弃。回调也不能是 `async` 函数，不能返回 Promise。

- 回调要写在和声明的 `scriptType` 对应的目录里：`SERVER` 写在 `server_scripts`，`CLIENT` 写在 `client_scripts`。启动脚本和测试脚本不能用。
- 每个 Hook 只能有一个回调。Hook ID 不存在、脚本类型不对或者重复注册，都会让这次加载失败，游戏继续用上一次成功加载的脚本。
- 回调必须写在本地的脚本文件里。从服务器同步下来的脚本包不能注册回调。
- reload 成功后，新回调替换旧回调。
- 退出世界时回调会被清除，下次加载脚本时重新注册。

<a id="wiki-section-7"></a>
## 4. 事件对象

| 成员 | 说明 |
|---|---|
| `self` | 被调用方法所属的对象。静态方法时是 `null` |
| `args` | 方法的参数，只读 |
| `returnValue` | `RETURN` 时是原本的返回值，`HEAD` 时还没有值。只有 `cancellable = true` 且方法有返回值时才能修改 |
| `cancel()` | 取消这次调用。只能用于 `cancellable = true`、`at = "HEAD"`、返回 void 的方法 |

- 不改 `returnValue` 时，方法照常返回原来的结果。
- 回调抛出异常、写了不允许的值、或者有返回值时，**这次调用的所有修改都作废**，方法按原样执行。但回调里已经做的其他事（比如改了世界里的方块）不会被撤销。
- `event` 只在回调执行期间有效。不要把它存起来在定时器或异步代码里用。

以下情况回调会被跳过，方法按原样执行：

- 脚本正在 reload 的过程中；
- 方法在错误的线程上被调用（例如 SERVER Hook 不在服务器主线程上）；
- 回调执行时又触发了同一个 Hook。

线程不对或回调出错时，日志里会出现一次 `NEKO-4092` 警告。

<a id="wiki-section-8"></a>
## 5. 支持的类型与限制

参数和返回值支持：boolean、char（一个字符）、byte / short / int（会检查范围）、float / double（必须是有限数）、Java 对象和数组（类型必须匹配，可以是 `null`）。返回 String 的方法可以直接赋 JavaScript 字符串。普通的 JavaScript 对象和数组不能当作 Java 对象返回。

目前不支持：

- 参数或返回值里有 `long`（包括 `long[]`）的方法；
- 构造器和静态初始化块；
- abstract、native、synthetic、bridge 方法；
- `HEAD` 和 `RETURN` 以外的注入位置；
- 在游戏运行时添加或修改注入。

**代码补全：** 写声明文件时，`nekojs/mixin` 总是有补全。MixinJS 开启并且注入已加载时，执行 [`/nekojs probe`](probe-type-generation_cn) 后，`MixinEvents.inject` 会按 Hook ID 提示 `self`、参数和返回值的类型。没有提示时，先确认开关已打开，再确认注入确实被加载了。

<a id="wiki-mixinjs-troubleshooting"></a>
<a id="wiki-section-9"></a>
## 故障排查

| 错误码 | 先检查什么 |
|---|---|
| `NEKO-4090` | 是否需要按提示重启；`[mixinjs] enabled` 是否打开；是否在开发环境；声明里的类名、描述符和字段是否正确；Hook ID 是否重复；构建时的版本、加载器和 `--side` 是否和运行环境一致；有没有混用自动生成和手动安装的 jar |
| `NEKO-4091` | 回调是否写在本地脚本文件里，而不是同步下来的脚本包 |
| `NEKO-4092` | 回调是否有返回值或是 async 函数；返回值类型和范围是否正确；是否在回调结束后还在用 `event`；Hook 是否在错误的线程上被调用 |

更多错误码见 [错误与日志参考](error-reference_cn)。加载器自己的 Mixin 报错不会带上面这些错误码，要看游戏日志。

<!-- wiki-nav -->

---

[上一篇: 客户端绘制与后处理](client-rendering_cn) · [目录](Home) · [下一篇: Node.js 兼容](nodejs-compatibility_cn)
