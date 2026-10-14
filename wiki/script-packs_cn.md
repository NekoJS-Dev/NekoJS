<!-- wiki-page: script-packs; locale: cn -->

> **中文** · [English](script-packs_us)

<a id="wiki-section-1"></a>
# 版本化脚本包

无 `formatVersion` 的旧包及平铺脚本继续使用原来的递归扫描、同类型 Context 和 `global`。以下机制仅用于显式声明 `formatVersion: 1` 的包。

<a id="wiki-section-2"></a>
## 创建与清单

游戏内使用 `/nekojs packs create server economy`。仓库工具支持一次选择多个类型：

```powershell
./tools/nekojs-pack.ps1 -Action create -InputPath ./build/author/packs -Id economy -Types server,client
./tools/nekojs-pack.ps1 -Action validate -InputPath ./build/author/packs/economy
```

模板创建 `manifest.json`、所选类型目录的 `main.js` 和 `pack-api.d.ts`，存在的目标不会覆盖。默认示例输出一条日志。将包目录放到游戏的 `nekojs/packs/`；WORLD 包放到当前世界的 `nekojs_packs/`，仅在平台装配该来源时执行。

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

机器规范位于仓库 `docs/schema/script-pack-v1.schema.json`。未知字段、字段类型错误和非法入口在激活前拒绝。`entries` 键为 `startup/server/client/test`；路径相对对应的类型目录，不能绝对定位、穿越、指向 `node_modules`，必须是现有且受支持的脚本。只有显式入口会独立执行；未列出的库由入口 import，每个包的模块会话负责缓存。物理 import 限于本包内容根，跨包交互使用声明能力。

版本使用三段数字，可带 SemVer 预发布和 build 标识；数字段最多九位，禁止前导零。依赖范围支持 `*`、精确版本、`>= <= > < =` 比较器以及空格连接的交集，比较器可使用一至三段数字；不支持 `^`、`~`、OR、自动下载或 npm 求解。`environment.mods` 使用相同范围规则。依赖先于消费者执行；文件原有 `priority/after` 规则仍有效，但不能让消费者跨过包依赖。缺失、不兼容、禁用、重复身份和环会给出阻止原因，无关包仍可运行。

裸依赖 ID 先查同来源；WORLD 可查 GLOBAL/MOD，GLOBAL 可查 MOD，MOD 可查 GLOBAL。GLOBAL/MOD 不借用 WORLD。远端包只查当前已授权缓存，传输前声明的 `GLOBAL:id/WORLD:id` 对应当前缓存中的原来源，不借用本地信任。远端 MOD 依赖不能成立。

<a id="wiki-section-3"></a>
## 能力与实例

提供者的清单声明 `"capabilities":{"provides":["wallet"]}`，在入口初始化中发布一次：

```js
Pack.provide('wallet', request => ({ balance: 42 }));
```

消费者声明 `requires` 后获得函数：

```js
const wallet = Pack.require('economy', 'wallet');
console.info(wallet({ player: 'example' }).balance);
```

每次调用接收一个 JSON 值，返回 JSON 值；禁止函数和宿主对象跨 Context 传递。数据有深度 32、节点 10000、名称和字符串合计 1 Mi 字符的限制。能力函数为同步调用，遵循脚本沙箱及资源预算。`Pack.selector` 和 `Pack.activation` 是只读属性。

新包按类型、实际来源及世界/连接会话拥有独立 Context；`global` 属于该包来源实例，跨普通重载保留，世界/连接结束释放；`shared` 属于 runtime root。句柄绑定取得时的消费者和提供者激活，提供者替换、停用、失效或会话结束后旧句柄拒绝调用。依赖缺失时消费者等待；运行中的提供者失效后消费者停止，恢复需要显式重载；管理员禁用的消费者不会因提供者恢复而复活。

<a id="wiki-section-4"></a>
## 查询、启停和重载

- `/nekojs packs list` 显示发现快照及保存的启用意图。
- `/nekojs packs enable GLOBAL:shop` / `disable GLOBAL:shop` 保存本机意图；保存失败不会报告成功。
- `/nekojs packs plan server` 显示当前文件顺序和阻止原因，不执行脚本。
- `/nekojs packs status server` 显示真实激活版本、实例及入口结果。
- `/nekojs packs reload server GLOBAL:economy` 替换提供者及必要消费者；无关包保留原激活。
- `/nekojs reload server` 联合替换完整类型，包括旧脚本分区。

选择器为 `GLOBAL:id`、`WORLD:id`、`MOD:id`；版本化远端包为 `SERVER_CACHE:packs:id` 或 `SERVER_CACHE:worldpacks:id`。裸 ID 仅在唯一匹配时可用。同名歧义不会默认修改第一个包。MOD 和 SERVER_CACHE 是只读来源。

受管状态、监听器、timer 和领域计划在联合预检后交接；候选失败保留健康旧集合并清理候选资源。使用整类型贡献表的领域会报告扩大集合，CLIENT 渲染贡献在一次发布中合并。顶层 `global/shared` 写冲突拒绝整批；嵌套对象、任意 Java/世界/文件副作用不属于回滚承诺。领域计划必须在预检后非抛异常发布；违反契约时会报告领域失败，其部分效果由领域承担。提交后的清理失败另行报告，不冒充回滚。

世界/连接结束或提供者失效时，先停止相应激活，再从仍活动的领域订阅重新收集整表贡献；不会重跑这些包的入口。渲染器按关闭 Context 的归属直接撤回。领域重收集失败时尝试发布空计划撤回该领域，记录 `NEKO-1017`，需要显式重载恢复；若撤回本身失败，日志会单独报告未完成的资源恢复。收集回调应只声明领域内容，任意宿主副作用仍由作者负责。

STARTUP 内容变更需要重启。旧单文件重跑继续采用 active Context；版本化包文件不接受单文件重跑，应使用包重载。客户端命令仍受现有单人/所有者线程限制。

受管回调中请求关闭 root 会立即阻止新的包回调，实际释放等待最外层回调返回；关闭失败记录 `NEKO-1017`。计时器关闭后拒绝新任务并清空队列，最多等待调度线程退出 2 秒；超时或等待被中断会报告未完成的清理，其余资源仍继续释放。

<a id="wiki-section-5"></a>
## 归档、安装与模组 JAR

```powershell
./tools/nekojs-pack.ps1 -Action archive -InputPath ./build/author/packs/economy -OutputPath ./build/economy.nekopack
./tools/nekojs-pack.ps1 -Action install -InputPath ./build/economy.nekopack -OutputPath ./build/installed/packs
# Upgrade or rollback with a selected archive; retain existing local enabled intent.
./tools/nekojs-pack.ps1 -Action install -InputPath ./build/economy.nekopack -OutputPath ./build/installed/packs -Replace
./tools/nekojs-pack.ps1 -Action jar -InputPath ./build/author/packs/economy -OutputPath ./build/economy.jar -Loader neoforge -Minecraft 26.1.2
```

归档保留原清单/签名字节，携带四种脚本目录及 `assets/data`、排序内容清单和原内容 hash。本机启用状态不分发。固定 ZIP 时间戳与 STORED 格式使同一内容可重复构建。安装通过暂存与恢复替换整个包，删除的内容随升级移除；已有目标必须显式 `-Replace`。坏清单、穿越、链接/reparse、便携文件名冲突以及大小/数量超限拒绝。

JAR 是依赖 NekoJS 的内容模组。模组 ID 等于包 ID，要求 2–48 个小写字母、数字或下划线，且以字母开头，不能占用 runtime ID。目标支持 NeoForge 1.21.1/26.1.2/26.2 和 Fabric 26.1.2/26.2，`26.2` 是 Minecraft 版本字符串。需要安装含本功能的 NekoJS 及其依赖；没有 Forge 目标。

将 JAR 放入对应侧 `mods/`，loader 发现的内容进入 MOD 来源；不直接扫描未知 JAR，也不把 JS 编译为独立 Java 模组。JAR 根的 `assets/data` 由原生 loader 资源机制处理。MOD 来源不加入现有 GLOBAL/WORLD 同步协议，需要在要求运行的一侧部署。目录包在 Fabric 上的自动资源挂载桥不属于此功能。

游戏内也支持 `/nekojs packs validate <selector>`、`archive <selector>`、`jar <loader> <minecraft> <selector>`；输出写到游戏的 `nekojs/exports/`，已有制品不会覆盖。CLI 可安装归档，游戏内不提供从任意路径安装的命令。

现有签名/key pin 和同步 wire 保持。默认拒绝无签名远端内容。显式 `allowUnsigned=true` 仍要求服务器信任；无签名授权保持独立标识，不伪造 key、不降为本地信任。同桶失败候选恢复旧物理内容、凭证和健康激活；断线、切服或撤权会使旧句柄失效。

<!-- wiki-nav -->

---

[上一篇: 错误与日志参考](error-reference_cn) · [目录](Home) · [下一篇: 配方系统](recipe-system_cn)
