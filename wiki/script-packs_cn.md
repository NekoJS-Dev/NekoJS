<!-- wiki-page: script-packs; locale: cn -->

> **中文** · [English](script-packs_us)

<a id="wiki-section-1"></a>
# 版本化脚本包

脚本包把一组脚本、数据和资源放在一个文件夹里，方便整体启用、停用和分发。NekoJS 有两种脚本包：

- **简单目录包**：只有一个简单的 `manifest.json`，适合把自己的脚本分组。见下文“简单目录包”一节。
- **版本化包**：`manifest.json` 里写了 `"formatVersion": 1`。它有版本号，可以声明依赖、给其他包提供功能，可以打包成 `.nekopack` 归档或模组 JAR 发布。

本页前半部分讲版本化包。只是想给脚本分组的话，直接看简单目录包就够了。

<a id="wiki-section-2"></a>
## 创建一个版本化包

在游戏里执行：

```text
/nekojs packs create server economy
```

它会创建 `nekojs/packs/economy/`：

```text
nekojs/packs/economy/
├── manifest.json
└── server_scripts/
    ├── main.js          # 示例入口，输出一条日志
    └── pack-api.d.ts    # Pack 对象的代码补全声明
```

`server` 可以换成 `startup`、`client` 或 `test`。目标文件夹已经存在时，命令会失败，不会覆盖。

然后执行 `/nekojs reload`，再用 `/nekojs packs status server` 查看这个包是否运行成功。`logs/nekojs/server.log` 里应该有示例脚本的那条日志。

<a id="wiki-section-3"></a>
## manifest.json

```json
{
  "formatVersion": 1,
  "id": "shop",
  "version": "1.0.0",
  "entries": { "server": ["main.js"] },
  "dependencies": { "economy": ">=1.0.0 <2.0.0" },
  "environment": { "loaders": ["neoforge"], "minecraft": ">=26.1 <27" },
  "capabilities": { "requires": { "economy": ["wallet"] } }
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `formatVersion` | 是 | 固定写 `1` |
| `id` | 是 | 包 ID：小写字母、数字、`_`、`-`，以字母或数字开头 |
| `version` | 是 | 三段式版本号，例如 `1.0.0`，可以带 `-beta.1` 这样的预发布后缀 |
| `entries` | 是 | 每种脚本的入口文件，键为 `startup`、`server`、`client`、`test` |
| `dependencies` | 否 | 依赖的包和版本范围 |
| `environment` | 否 | 运行条件：`loaders`（加载器）、`minecraft`（版本范围）、`mods`（需要的模组及版本范围） |
| `capabilities` | 否 | `provides`：本包提供的功能；`requires`：需要其他包的哪些功能 |
| `name`、`description`、`authors` | 否 | 显示用的名称、简介、作者 |
| `enabled` | 否 | 默认是否启用 |
| `clientSync` | 否 | 写 `false` 时不发给多人游戏的玩家 |
| `signature` | 否 | 签名，见下文“把脚本包发给多人游戏的玩家” |
| `config` | 否 | 自定义配置对象 |

版本化包的清单检查很严格：多写字段、字段类型不对或入口无效，这个包都不会运行。完整格式见仓库中的 `docs/schema/script-pack-v1.schema.json`。

<a id="wiki-section-4"></a>
### 入口文件

`entries` 里的路径相对于对应的脚本目录。例如 `"server": ["main.js"]` 指的是 `server_scripts/main.js`。

- 只有列在 `entries` 里的文件会被直接运行。包里的其他脚本是库文件，由入口 `import` 使用。
- `import` 只能引用本包内的文件。想调用别的包，用下文的“能力”。
- 路径不能是绝对路径，不能用 `..` 跳出目录，也不能指向 `node_modules`。

<a id="wiki-section-5"></a>
### 版本范围

`dependencies`、`environment.minecraft` 和 `environment.mods` 里的版本范围这样写：

- `*`：任何版本
- `1.2.0` 或 `=1.2.0`：精确版本
- `>=1.0.0`、`<2`、`>1.1`、`<=1.5.3`：比较
- 用空格连接表示“同时满足”，例如 `>=1.0.0 <2.0.0`

不支持 `^`、`~` 和“或”。依赖不会自动下载。

<a id="wiki-section-6"></a>
### 依赖

被依赖的包总是先于依赖它的包运行。文件开头的 `// priority:` 和 `// after:` 不能改变这个顺序。

依赖缺失、版本不符、被停用或者形成循环时，只有依赖它的包不运行，其他包照常运行。原因可以用 `/nekojs packs plan <类型>` 查看。

依赖 ID 可以加来源前缀，例如 `GLOBAL:economy`。不加前缀时优先找同一来源的包。世界包可以依赖全局包和模组包，全局包和模组包不能依赖世界包。

<a id="wiki-section-7"></a>
## 包之间互相调用：能力

一个包可以把函数提供给其他包使用，这叫“能力”。

提供方在 `manifest.json` 写 `"capabilities": { "provides": ["wallet"] }`，然后在入口脚本里发布：

```javascript
// economy 包的 server_scripts/main.js
Pack.provide('wallet', request => ({ balance: 42 }))
```

使用方在 `manifest.json` 写 `"capabilities": { "requires": { "economy": ["wallet"] } }`，然后获取并调用：

```javascript
// shop 包的 server_scripts/main.js
const wallet = Pack.require('economy', 'wallet')
console.info(wallet({ player: 'example' }).balance)
```

- 调用是同步的，参数和返回值只能是 JSON 数据（对象、数组、字符串、数字、布尔、`null`）。不能传函数或游戏对象。
- 数据最多嵌套 32 层、10000 个节点，所有字符串加起来不超过约 100 万个字符。
- `Pack.selector` 是本包的选择器（如 `GLOBAL:economy`），`Pack.activation` 是本次运行的编号。两者只读。
- 提供方被重载或停止后，之前拿到的函数就失效了。下次运行时重新 `Pack.require` 即可。

<a id="wiki-section-8"></a>
## 每个包独立运行

- 每个版本化包有自己独立的运行环境。一个包的顶层变量，其他包看不到。
- 每个包有自己的 `global`，重载后保留。世界包的 `global` 在退出世界后清空。
- `shared` 在所有包和普通脚本之间共享。
- 没有 `formatVersion` 的简单目录包和 `nekojs/` 下的普通脚本仍然共用同类型的 `global`。

<a id="wiki-section-9"></a>
## 管理命令

| 命令 | 作用 |
|---|---|
| `/nekojs packs` | 列出所有脚本包及启用状态 |
| `/nekojs packs enable <包>` / `disable <包>` | 启用或停用，之后需要 reload |
| `/nekojs packs plan <类型>` | 预览加载顺序，以及哪些包为什么不能运行。不执行脚本 |
| `/nekojs packs status <类型>` | 查看每个包实际的运行结果 |
| `/nekojs packs reload <类型> <包>` | 只重载这个包和依赖它的包，其他包不受影响 |
| `/nekojs packs create <类型> <id>` | 创建新的版本化包 |
| `/nekojs packs validate <包>` | 检查包的清单和文件 |
| `/nekojs packs archive <包>` | 打包成 `.nekopack` 归档 |
| `/nekojs packs jar <加载器> <版本> <包>` | 打包成模组 JAR |

`<包>` 写成“来源:ID”的形式：

| 选择器 | 包的位置 |
|---|---|
| `GLOBAL:id` | `nekojs/packs/<id>/` |
| `WORLD:id` | `<存档文件夹>/nekojs_packs/<id>/` |
| `MOD:id` | 以模组 JAR 安装的包 |
| `SERVER_CACHE:packs:id` / `SERVER_CACHE:worldpacks:id` | 从多人服务器收到的包 |

只有一个包叫这个 ID 时，可以只写 ID。`MOD` 和 `SERVER_CACHE` 来源的包不能用这些命令启用、停用或修改。不要手动编辑服务器缓存来绕过签名检查。

<a id="wiki-section-10"></a>
## 改完怎么生效

| 改了什么 | 怎么做 |
|---|---|
| 某个包的服务端或客户端脚本 | `/nekojs packs reload server GLOBAL:economy`，或者 `/nekojs reload` 重载整个类型 |
| 启动脚本 | 重启游戏 |
| 包里的 `data/` | 原版 `/reload` |
| 包里的 `assets/` | F3+T |

- 保存文件不会自动生效。
- 版本化包里的文件不能用 `/nekojs reload server <文件>` 单独重载，请用 `packs reload`。
- 重载失败时，这次改动全部不生效，继续使用上一次成功的版本。
- 提供方运行出错后，依赖它的包也会停止。修好后需要手动 reload，它们不会自动恢复。被你停用的包也不会因为依赖恢复而自己启用。

<a id="wiki-section-11"></a>
## 打包和发布

<a id="wiki-section-12"></a>
### 打包成 .nekopack 归档

```text
/nekojs packs validate GLOBAL:economy
/nekojs packs archive GLOBAL:economy
```

归档写到 `nekojs/exports/economy-1.0.0.nekopack`。同名文件已存在时不会覆盖。

归档包含清单、签名、四种脚本目录以及 `assets/`、`data/`。本机的启用/停用状态不会打包进去。同样的内容每次打出的文件完全相同。

<a id="wiki-section-13"></a>
### 打包成模组 JAR

```text
/nekojs packs jar neoforge 26.1.2 GLOBAL:economy
```

输出到 `nekojs/exports/`。生成的 JAR 是一个依赖 NekoJS 的普通内容模组，放进 `mods/` 就能用，它的包会以 `MOD:id` 出现。

- 支持的目标：NeoForge `1.21.1`、`26.1.2`、`26.2`；Fabric `26.1.2`、`26.2`。
- 模组 ID 就是包 ID，要求更严：2–48 个字符，只能用小写字母、数字和下划线，以字母开头。
- 玩家仍然需要安装 NekoJS。
- 模组 JAR 里的包不会通过多人同步发给玩家。客户端也需要的话，玩家要自己安装这个 JAR。

<a id="wiki-section-14"></a>
### 用仓库工具批量处理

有 NekoJS 源码仓库时，也可以在命令行用 `tools/nekojs-pack.ps1`。它支持一次创建多种脚本类型，以及安装归档：

```powershell
./tools/nekojs-pack.ps1 -Action create -InputPath ./build/author/packs -Id economy -Types server,client
./tools/nekojs-pack.ps1 -Action validate -InputPath ./build/author/packs/economy
./tools/nekojs-pack.ps1 -Action archive -InputPath ./build/author/packs/economy -OutputPath ./build/economy.nekopack
./tools/nekojs-pack.ps1 -Action install -InputPath ./build/economy.nekopack -OutputPath ./build/installed/packs
# Upgrade or roll back to the selected archive; the local enabled state is kept.
./tools/nekojs-pack.ps1 -Action install -InputPath ./build/economy.nekopack -OutputPath ./build/installed/packs -Replace
./tools/nekojs-pack.ps1 -Action jar -InputPath ./build/author/packs/economy -OutputPath ./build/economy.jar -Loader neoforge -Minecraft 26.1.2
```

安装会整体替换包的内容，新版本里删掉的文件也会被删除。目标已存在时必须加 `-Replace`。游戏内没有安装归档的命令。

<a id="wiki-section-15"></a>
## 注意事项

- **不会自动中断脚本。** 能力回调里的死循环会卡住游戏。
- **重载失败不会撤销副作用。** 失败前已经写入的文件、改动的世界不会恢复。
- **签名不等于安全。** 签名只能证明包来自谁、没被改过，不能保证代码无害。只安装你信任的包。
- **Fabric 的资源顺序**：后面的覆盖前面的——`nekojs/assets`、`nekojs/data` → 全局包（按 ID 排序）→ 世界包（按 ID 排序）→ 从服务器收到的包（只有 assets）。被停用、或因依赖/环境条件不能运行的包不提供资源。
- 世界包的脚本在世界启动时运行、关闭时停止。只是在存档列表里预览不会运行它们。

<a id="wiki-section-16"></a>
## 简单目录包（不写 formatVersion）

不需要依赖、能力这些功能时，可以用更简单的目录包：在 `nekojs/packs/<包id>/` 下放一个 `manifest.json`，再放和平铺目录一样的脚本子目录。

```text
nekojs/packs/my_pack/
├── manifest.json           # {"id": "my_pack", "name": "我的包", "version": "1.0.0"}
├── server_scripts/
│   └── boss.js
└── client_scripts/
    └── hud.js
```

`manifest.json` 里没有 `formatVersion` 的包都按简单目录包处理，上文版本化包的规则对它不适用。

- **可用字段**：`id`、`name`、`version`、`description`、`authors`、`enabled`、`clientSync`。写错或多写的字段不会报错。
- **加载顺序**：全局包（按 id 字母顺序）→ 世界包 → `nekojs/` 下的平铺脚本目录。
- **启用和停用**：`/nekojs packs disable GLOBAL:my_pack`，它的优先级高于 `manifest.json` 里的 `enabled`。之后 reload 对应类型生效；启动脚本需要重启。
- **世界包**：放在 `<存档文件夹>/nekojs_packs/<包id>/`，只在进入这个世界时加载。
- **数据和资源**：包里的 `data/` 和 `assets/` 也会被加载。之后修改 data 用原版 `/reload`，修改 assets 按 F3+T。
- **代码补全**：包里的每个脚本目录同样会生成 `jsconfig.json`。

<a id="wiki-section-17"></a>
## 把脚本包发给多人游戏的玩家

服务器可以把脚本包自动发给加入的玩家，玩家不用手动安装。这个功能默认关闭。

<a id="wiki-section-18"></a>
### 服务器设置

在服务器的 `nekojs/config/engine.toml` 里开启：

```toml
[packSync]
mode = "all"            # off（默认，不发送）| hashOnly（只比对，不执行）| all（发送并执行）
allowUnsigned = false   # 是否允许发送没有签名的包，建议保持 false
```

不想发给玩家的包，在它的 `manifest.json` 里写 `"clientSync": false`。

包需要签名才能被玩家接受（除非开启 `allowUnsigned`）。在 `manifest.json` 里写入签名信息：

```json
"signature": { "algorithm": "Ed25519", "keyId": "...", "publicKey": "<X.509 base64>", "signature": "<base64>" }
```

签名覆盖的是去掉 `signature` 字段后的清单，以及包里所有文件的内容。

<a id="wiki-section-19"></a>
### 玩家第一次加入

1. 玩家第一次连接这个服务器时会被断开，并看到服务器的地址。
2. 玩家进入任意单人世界，执行 `/nekojs trust <服务器地址>`。
3. 重新连接，就会收到并运行服务器的脚本包。

信任服务器时会同时记住它当前的签名公钥。如果服务器以后悄悄换了密钥，玩家会被拒绝连接，需要重新信任。

收到的包缓存在玩家的 `nekojs/server_packs/` 里。包没有变化时，下次连接直接使用缓存。

> 只信任你了解的服务器。脚本包里的脚本会在你的客户端上运行。

<!-- wiki-nav -->

---

[上一篇: 错误与日志参考](error-reference_cn) · [目录](Home) · [下一篇: 配方系统](recipe-system_cn)
