<!-- wiki-page: quick-start; locale: cn -->

> **中文** · [English](quick-start_us)

<a id="wiki-section-1"></a>
# 快速开始

本页带你从零跑起第一个 NekoJS 脚本。

<a id="wiki-section-2"></a>
## 1. 安装前置

NekoJS 依赖 [Graal](https://www.curseforge.com/minecraft/mc-mods/graal)（提供 GraalJS 运行时），需要 **25.1.3.7 或更高**。更低版本缺少正则语言，脚本使用正则时会出现 `No language for id regex found`。

1. 从 [CurseForge](https://www.curseforge.com/minecraft/search?search=NekoJS) 或 [GitHub Releases](https://github.com/NekoJS-Dev/NekoJS/releases) 下载与你所用 Minecraft / NeoForge 或 Fabric 版本匹配的 **NekoJS**，以及对应的 **Graal**。
2. 把两个 jar 都放进 `mods/` 文件夹。
3. 启动游戏。

> 当前仓库的 Fabric 26.x 节点仍是实验性 API 子集，具体差异见 [平台与兼容性](platform-compatibility_cn)。Cleanroom 1.12.2 属于独立 legacy 分支，不纳入本页主流程。

> 版本必须匹配。发布页面会标注每个 NekoJS 对应的 Minecraft、加载器和 Java 版本。

仓库构建的 **NeoForge 1.21.1** 制品按运行环境选择：客户端使用普通 `.jar`；独立专服使用同版本的 `-server.jar`，它额外携带专服缺少的 ICU 模块。每个安装目录只放其中一种 NekoJS 制品，仍需安装对应 Graal。客户端使用专服变体会与 Minecraft 自带 ICU 模块冲突；专服使用普通客户端制品会缺少 ICU。`./gradlew :1.21.1:build` 同时生成并检查两种制品。NeoForge26.x 和 Fabric 的制品选择方式保持现状。发布包以发布页实际列出的文件为准。

<a id="wiki-section-3"></a>
## 2. 目录结构

首次启动后，游戏根目录下会自动生成 `nekojs/` 文件夹：

```text
.neko_probe/                # 类型声明输出根目录（/nekojs probe 生成，与 nekojs 同级）
nekojs/
├── startup_scripts/        # 启动脚本：注册物品/方块等（改了要重启游戏）
│   └── jsconfig.json
├── server_scripts/         # 服务端脚本：配方、事件监听（支持 /nekojs reload）
│   └── jsconfig.json
├── client_scripts/         # 客户端脚本：GUI、粒子、按键（支持 client reload）
│   └── jsconfig.json
├── test_scripts/           # 测试脚本：通过 /nekojs test 显式运行
├── packs/                  # 可选：脚本包（见下节）
├── node_modules/           # 外部纯 JS npm 依赖
├── assets/                 # 资源
├── data/                   # 数据包
└── config/                 # probe.toml 与 engine.toml（引擎/沙盒配置）
                              （旧 config/nekojs-engine.toml 仅作只读回退）
```

四个脚本目录都支持 `.js`、`.mjs`、`.cjs`、`.ts`、`.jsx`、`.tsx` 和 `.py`。`.py` 是 NekoJS 的 Python 子集转译脚本，不需要外部 Python 运行时。详见 [脚本基础](script-basics_cn)。

<a id="wiki-section-4"></a>
### 脚本包（可选）

除了平铺的四个脚本目录，也可以把脚本组织成「包」：`nekojs/packs/<包id>/` 下放一个
`manifest.json` 加自己的 `startup_scripts/`、`server_scripts/` 等子目录：

```text
nekojs/packs/my_pack/
├── manifest.json           # {"id": "my_pack", "name": "我的包", "version": "1.0.0"}
├── server_scripts/
│   └── boss.js
└── client_scripts/
    └── hud.js
```

- **加载顺序**：全局包（按 id 字母序）→ 世界包 → 平铺目录。
- **启用/禁用**：`/nekojs packs` 列出全部包；`/nekojs packs disable my_pack` 写状态文件
  （优先级高于 manifest 的 `enabled`），随后 `/nekojs reload` 生效。
- **世界包**：放在存档目录 `<世界文件夹>/nekojs_packs/<包id>/`（结构与全局包一致），
  进入该世界时自动加载、退出世界时自动卸载其事件监听与定时器——不同世界可以有不同的脚本。
- manifest 字段宽松解析：`id`/`name`/`version`/`description`/`authors`/`enabled`/`clientSync`
  （`clientSync` 预留给多人脚本分发，见下）。每个包的脚本目录同样自动生成 `jsconfig.json`，
  编辑器补全与平铺目录一致。
- 包的 `data/<命名空间>/**` 目录会在服务器启动/reload 时作为强制数据包挂载（配方、战利品表、标签、村民交易 JSON 等）。这是 NeoForge 能力；Fabric 当前不提供同等 datagen/强制数据包面。

<a id="wiki-section-5"></a>
### 多人脚本包分发（服务端 → 客户端）

服务器可以把脚本包自动分发给连入的客户端，客户端在**原版注册表校验之前**验证并执行完包内脚本
（远端脚本仍走 ClassFilter/Watchdog 沙箱）。在服务端 `nekojs/config/engine.toml` 打开：

```toml
[packSync]
mode = "all"           # off（默认）| hashOnly（只对哈希不执行）| all
allowUnsigned = false   # 是否接受无签名包
```

- **签名**：manifest 里写 `signature = { algorithm = "Ed25519", keyId = "...", publicKey = "<X.509 base64>", signature = "<base64>" }`
  （签名覆盖去掉 signature 键的规范化 manifest + 全部文件内容）。
- **信任**：客户端首次连入未信任的服务器会被**断开**并提示地址；在该客户端的单人世界里执行
  `/nekojs trust <地址>` 后重连即可收包。信任会同时 pin 该服务器当前签名公钥（密钥静默轮换会被拒绝）。
- **缓存**：包按服务器地址哈希分桶缓存在 `nekojs/server_packs/`，哈希不变则复用本地缓存。
- Cleanroom legacy 分支没有配置阶段，只能退化成登录后同步，而且只同步纯脚本；当前仓库不验证该分支行为。

<a id="wiki-section-6"></a>
## 3. 第一个脚本

在 `nekojs/server_scripts/` 下新建 `hello.js`：

```javascript
// server_scripts/hello.js

// 监听「服务端启动完成」事件
ServerEvents.started(event => {
  console.info('[NekoJS] 服务端脚本已加载！')
})
```

进入世界并等待服务端启动完成后，日志里应出现 `[NekoJS] 服务端脚本已加载！`。如果在已启动的世界中才添加本例，重新进入世界以触发 `started`；普通 `/nekojs reload` 只重新注册监听器，不会再次触发这个生命周期事件。

<a id="wiki-section-7"></a>
## 4. 改一个配方

```javascript
// server_scripts/recipes.js

ServerEvents.recipes(event => {
  // 删除所有原版木棍配方
  event.remove({ output: 'minecraft:stick' })

  // 添加一项：用 1 个圆石合成 4 个木棍
  event.shaped('4x minecraft:stick', [
    'C'
  ], {
    C: 'minecraft:cobblestone'
  }).id('nekojs:cobble_to_sticks')
})
```

执行 `/nekojs reload`。NeoForge 和 Fabric 26.x 都支持服务端配方热重载；NeoForge 的配方查看器可能需要客户端刷新，Fabric 以当前节点实现和平台兼容性说明为准。

详见 [配方系统](recipe-system_cn)。

<a id="wiki-section-8"></a>
## 5. 注册一个新物品

```javascript
// startup_scripts/my_items.js

RegistryEvents.register(event => {
  event.item('mymod:cool_gem', b => {
    b.maxStackSize = 16
    b.rarity = 'rare'
  })
})
```

> 注意：启动脚本由游戏启动阶段执行。修改注册内容后必须重启游戏；当前 `/nekojs reload startup` 不支持从运行中的命令重载 STARTUP 脚本。

详见 [注册新内容](registering-new-content_cn)。

<a id="wiki-section-9"></a>
## 6. 开启 IDE 智能提示

1. 执行 `/nekojs probe`，生成 `.neko_probe/typescript/` 下的 TypeScript `.d.ts`。
2. 需要 Python 补全时，再执行 `/nekojs probe python`，并在每个使用 NekoJS API 的 `.py` 文件顶部写 `from nekojs import *`（或 `from nekojs import Item, ServerEvents` 等具名导入）。需要运行所有 backend 时执行 `/nekojs probe all`。
3. 用 VS Code 打开 `nekojs/server_scripts/`（或对应目录），里面的 `jsconfig.json` 会关联 TypeScript 类型库。
4. 需要开服自动运行默认 TS probe 时，在 `nekojs/config/probe.toml` 设置 `runAtStartup = true`；该选项默认关闭。

<a id="wiki-section-10"></a>
## 7. 下一步

- [脚本基础](script-basics_cn) —— 脚本类型、生命周期、reload 行为。
- [全局绑定](global-bindings_cn) —— `Item`、`Ingredient`、`Fluid`、`Text`、`JsonIO` 等全部 API。
- [事件参考](event-reference_cn) —— 所有可用事件。
- [模块系统](module-system_cn) —— 拆分多文件、引用 npm 依赖、`java:` 导入。
- [TypeScript 与 JSX](typescript-and-jsx_cn) —— 用 TS 写脚本。

<!-- wiki-nav -->

---

[上一篇: 平台与兼容性](platform-compatibility_cn) · [目录](Home) · [下一篇: 常见问题](faq_cn)
