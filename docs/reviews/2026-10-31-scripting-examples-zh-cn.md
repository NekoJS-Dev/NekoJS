# NekoJS 脚本编写实践篇（中文审阅版）

> 对应 1.1.0-preview3；2026-10-31。补足“只有规则、缺少实际写法”的问题：**12 个场景、14 个完整脚本文件**。此文供人工阅读，不进入正式 AI 文档包；正式英文包使用 [scripting-examples.md](<../../common/src/main/resources/nekojs/authoring/handbook/scripting-examples.md>)。

## 如何使用

1. 先看当前实例声明，确认下列事件、Binding、重载和平台能力真实存在；不要按别的模组接口猜名字。
2. 只选需要的场景，不要把所有例子一起安装。资源包里的 examples 目录是参考资料，不会自动安装到脚本目录。
3. 以下安装位置均相对**游戏目录**；文件夹不存在时自行创建。每个例子给出原文件链接，代码保持与正式英文资源完全一致。
4. 优先使用一次性测试实例和世界。配方会改变玩法；注册内容删除前先丢弃测试世界；UI 和标签有明确平台门槛。
5. “源码核对”“隔离回归”“真实 common 引擎测试”与“实际进游戏成功”是不同证据。本次没有把任何场景标为已完成实机验收。

英文 [元数据清单](<../../common/src/main/resources/nekojs/authoring/examples/metadata.json>) 记录安装、触发、结果、清理与证据等级。基础术语见 [中文基础手册](<2026-10-31-ai-handbook-zh-cn.md>)。

## 场景目录

1. [玩家登录后发送欢迎消息](#player-login)
2. [删除一个配方，并增加有序、无序和熔炼配方](#recipes)
3. [编辑物品标签，并理解“移除优先”](#item-tags)
4. [编辑方块标签：同名不等于同一个注册表](#block-tags)
5. [在启动期注册物品和方块](#registry)
6. [使用物品组件字符串和显式数量构造物品栈](#item-components)
7. [启动配置、SERVER 重载计数和一次性定时器](#state-and-timer)
8. [拆分 CommonJS 入口和可复用纯函数模块](#commonjs)
9. [用 TEST 脚本编写可执行断言](#assertions)
10. [可点击的 TSX 计数器界面](#client-counter)
11. [最小检查：确认脚本确实执行](#greeting)
12. [最小检查：区分建立订阅与事件实际触发](#event-subscription)

<a id="player-login"></a>
## 1. 玩家登录后发送欢迎消息

**适用与前提**：SERVER；需要 PlayerEvents.loggedIn、event.player 和 player.sendMessage。先查当前实例声明，不要照搬其他脚本模组的事件名。

**安装位置**：`nekojs/server_scripts/authoring-examples/player-login.js`

[完整源文件：server/player-login.js](<../../common/src/main/resources/nekojs/authoring/examples/server/player-login.js>)

```js
// Place in server_scripts. The callback runs for future logins, not existing players.
PlayerEvents.loggedIn(event => {
    event.player.sendMessage("Welcome to NekoJS!");
});
```

**如何运行**：安装后执行 `/nekojs reload server`，然后让玩家退出并重新登录。已经在线的玩家不会因为订阅刚建立就自动收到消息。

**预期结果与检查**：每次后续登录收到一次 `Welcome to NekoJS!`。脚本顶层只建立订阅，读取玩家和发送消息都在回调内进行。

**如何清理**：删除文件，重新加载 SERVER；旧代订阅应随代际退休，不要把一次登录重复收到消息当成正常结果。

**验证边界**：隔离接口回归检查回调延迟、player 属性读取与精确消息；尚未完成真实玩家登录验收。

<a id="recipes"></a>
## 2. 删除一个配方，并增加有序、无序和熔炼配方

**适用与前提**：SERVER；需要配方事件、Item.of、Ingredient.of、result-first 配方辅助方法与 .id。使用一次性世界，先确认没有其他脚本使用本例的三个 ID。

**安装位置**：`nekojs/server_scripts/authoring-examples/recipes.js`

[完整源文件：server/recipes.js](<../../common/src/main/resources/nekojs/authoring/examples/server/recipes.js>)

```js
// Use a disposable world: this replaces the vanilla stick recipe.
ServerEvents.recipes(event => {
    event.remove({ id: "minecraft:stick" });
    event.shaped(Item.of("minecraft:stick", 4), ["P", "P"], {
        P: Ingredient.of("minecraft:oak_planks")
    }).id("nekojs_example:sticks");
    event.shapeless(Item.of("minecraft:flint"), [
        Ingredient.of("minecraft:gravel"),
        Ingredient.of("minecraft:gravel")
    ]).id("nekojs_example:flint");
    event.smelting(Item.of("minecraft:glass"), Ingredient.of("minecraft:sand"), 0.1, 200)
        .id("nekojs_example:glass");
});
```

**如何运行**：安装后执行完整的 `/nekojs reload server`，等待脚本和配方处理完成。不要只单独 reload 此文件来判断配方是否重放。

**预期结果与检查**：精确删除 `minecraft:stick`；竖排两块橡木木板合成四根木棍；两块砂砾无序合成一个燧石；沙子熔炼为玻璃，经验 0.1、耗时 200 tick。新增 ID 分别是 `nekojs_example:sticks`、`nekojs_example:flint` 和 `nekojs_example:glass`。检查配方显示或实际制作结果；不要把本机控制台出现脚本日志当成配方已生效。

**如何清理**：删除脚本并完整 reload SERVER，确认三个示例 ID 消失且原版木棍配方恢复。已制作出的物品不会被撤销。不要使用 `remove({})`；空过滤器会匹配所有配方。`.id` 可能覆盖已有同 ID 配方，因此始终选用独立命名空间。

**验证边界**：隔离回归检查精确 removal、三种 builder 的参数、输入数量、形状、经验、时间和 ID；真实配方加载及各节点游戏内制作仍未验收。

<a id="item-tags"></a>
## 3. 编辑物品标签，并理解“移除优先”

**适用与前提**：SERVER；当前标签重建实现按 NeoForge 路径说明，不据此承诺 Fabric 可运行。需要 minecraft:item 注册表的标签事件。

**安装位置**：`nekojs/server_scripts/authoring-examples/item-tags.js`

[完整源文件：server/item-tags.js](<../../common/src/main/resources/nekojs/authoring/examples/server/item-tags.js>)

```js
// Requires the NeoForge tag-reload path; follow server script reload with /reload.
ServerEvents.tags("minecraft:item", event => {
    event.add("nekojs_example:stones", "minecraft:stone");
    event.add("nekojs_example:stones", "minecraft:granite");
    event.remove("nekojs_example:stones", "minecraft:granite");
});
```

**如何运行**：先 `/nekojs reload server` 建立新订阅，再执行原版 `/reload` 触发数据包标签重建，等待两者完成。

**预期结果与检查**：物品标签 `nekojs_example:stones` 最终包含石头，不包含本例先添加又移除的花岗岩。参数是普通条目 ID，不写 `#minecraft:stone`。增删是排队操作，当前 getEntries 不会提前合并待提交编辑；不要在回调里用它验证最终结果。

**如何清理**：移除文件、reload SERVER、再 `/reload`；检查示例标签不再获得本例的条目。其他数据包也能贡献同名标签，因此先在干净测试实例验证。

**验证边界**：隔离回归检查目标注册表、两个 add 与一个 remove；真实数据包重建没有执行。

<a id="block-tags"></a>
## 4. 编辑方块标签：同名不等于同一个注册表

**适用与前提**：SERVER、NeoForge 标签重建路径；需要 minecraft:block 注册表的标签事件。

**安装位置**：`nekojs/server_scripts/authoring-examples/block-tags.js`

[完整源文件：server/block-tags.js](<../../common/src/main/resources/nekojs/authoring/examples/server/block-tags.js>)

```js
// This block tag is separate from the item tag with the same name. NeoForge tag-reload path required.
ServerEvents.tags("minecraft:block", event => {
    event.add("nekojs_example:stones", "minecraft:stone");
});
```

**如何运行**：先 `/nekojs reload server`，再原版 `/reload`，等待完成。

**预期结果与检查**：方块标签 `nekojs_example:stones` 包含石头。它和上一例同名的物品标签相互独立；不要混用两种注册表的判断。

**如何清理**：移除文件、reload SERVER、再 `/reload`，分别检查方块标签与物品标签。

**验证边界**：隔离回归检查 minecraft:block 与普通石头 ID；真实方块标签重建没有执行。

<a id="registry"></a>
## 5. 在启动期注册物品和方块

**适用与前提**：STARTUP；需要 RegistryEvents.register、event.item、event.block 和对应 Builder。不是 ItemEvents.registry 或 BlockEvents.registry。客户端和服务端安装一致定义；先用一次性世界。源码存在 NeoForge 与 Fabric 26 路径，但不等于已完成各节点实机验收。

**安装位置**：`nekojs/startup_scripts/authoring-examples/registry.js`

[完整源文件：startup/registry.js](<../../common/src/main/resources/nekojs/authoring/examples/startup/registry.js>)

```js
// STARTUP only: restart the whole process. Use a disposable world and matching client/server definitions.
RegistryEvents.register(event => {
    event.item("nekojs_example:ruby", build => {
        build.maxStackSize = 16;
        build.rarity = "rare";
    });
    event.block("nekojs_example:ruby_block", build => {
        build.hardness = 2;
        build.resistance = 6;
        build.lightLevel = 3;
    });
});
```

**如何运行**：安装后彻底重启游戏／服务器进程。注册只在启动阶段执行；不要用普通 startup reload 期待完成新的注册。

**预期结果与检查**：注册 `nekojs_example:ruby`：最大堆叠 16、稀有度 rare；注册 `nekojs_example:ruby_block`：硬度 2、抗性 6、发光等级 3。方块会自动带同 ID 的 BlockItem，不要再手动 event.item 注册重复项。配置回调放最后，Builder 使用已暴露的可写属性。示例不提供模型、纹理、语言条目或掉落表，缺失资源表现并不代表 builder 失败。

**如何清理**：先丢弃一次性世界，再删除注册脚本并重启。不要在已经使用这些内容的长期存档中直接移除定义；这不是安全的数据迁移方案。

**验证边界**：隔离回归验证两个 builder、ID、全部属性以及没有重复注册方块物品；实际注册实现、资产和客户端服务端一致性仍需游戏内验收。

<a id="item-components"></a>
## 6. 使用物品组件字符串和显式数量构造物品栈

**适用与前提**：SERVER；需要 Item.of(String,int) 与当前实例支持的组件解析器。组件文本通过当前版本的注册表和 codec 解析，不是随意的 JavaScript 对象。

**安装位置**：`nekojs/server_scripts/authoring-examples/item-components.js`

[完整源文件：server/item-components.js](<../../common/src/main/resources/nekojs/authoring/examples/server/item-components.js>)

```js
// Detached stacks only: component decoding still needs the running instance registry and codec.
const exampleSword = Item.of("minecraft:iron_sword[damage=2]", 1);
const exampleStones = Item.of("minecraft:stone", 4);
console.info("Detached sword with damage 2:");
console.info(exampleSword);
console.info("Detached stone stack with count 4:");
console.info(exampleStones);
```

**如何运行**：安装后 `/nekojs reload server`，查看控制台日志。

**预期结果与检查**：创建一把 damage=2、数量为 1 的铁剑，以及数量为 4 的石头物品栈，并打印标签与对象。两者都只是内存对象：本例不会发给玩家，也不会修改背包或世界。具体对象日志格式不作跨版本承诺。

**如何清理**：删除文件并 reload SERVER，不需要清理背包；本例没有向其中写入内容。

**验证边界**：隔离回归核对完整组件字符串、两个显式数量与四次日志调用；并未用真实游戏注册表／codec 解码，也不以此验证普通 ItemStack Bean 写入。

<a id="state-and-timer"></a>
## 7. 启动配置、SERVER 重载计数和一次性定时器

**适用与前提**：两个文件配合：STARTUP 写 shared 顶层原始值，SERVER 读取并使用 global 计数及 setTimeout。shared/global 不表示联网同步或磁盘持久化。

**安装位置**：`nekojs/startup_scripts/authoring-examples/shared-config.js`

[完整源文件：startup/shared-config.js](<../../common/src/main/resources/nekojs/authoring/examples/startup/shared-config.js>)

```js
// Restart the process after installing this STARTUP script. Store primitives, not player handles.
shared.authoringMessage = "Welcome to NekoJS";
```

**安装位置**：`nekojs/server_scripts/authoring-examples/state-and-timer.js`

[完整源文件：server/state-and-timer.js](<../../common/src/main/resources/nekojs/authoring/examples/server/state-and-timer.js>)

```js
// Copy startup/shared-config.js first, then restart once. This counter is not saved to disk.
global.authoringLoads = (global.authoringLoads || 0) + 1;
console.info(`Authoring SERVER load #${global.authoringLoads}: ${shared.authoringMessage}`);
setTimeout(() => {
    console.info("Authoring timeout fired");
}, 1000);
```

**如何运行**：两份文件都安装后先完整重启一次；随后每次执行 `/nekojs reload server` 观察计数。

**预期结果与检查**：同一个根运行时内 SERVER 成功加载时输出 load #1、#2 等，同时读到 Welcome to NekoJS。1000 毫秒表示回调最早具备运行条件，实际要等待所属线程的 flush，并不是严格墙钟时刻，也不是 1000 tick。旧代退休时定时器会取消。不要把 STARTUP 正延迟计时器或 interval 当作通用支持能力。

**如何清理**：删除两份脚本并重启以获得干净根状态。不要假定退出进程后这些值会保存，也不要依赖对嵌套对象的修改被追踪。

**验证边界**：隔离回归检查原始值键、重复执行计数和 1000 ms 单次回调；没有把模拟回调调用当成真实调度／重载取消机制验收。

<a id="commonjs"></a>
## 8. 拆分 CommonJS 入口和可复用纯函数模块

**适用与前提**：SERVER、.cjs、require 与 module.exports；两份文件的相对目录必须保持不变。不是 npm 项目安装示例。

**安装位置**：`nekojs/server_scripts/authoring-examples/modules/hello.cjs`

[完整源文件：modules/hello.cjs](<../../common/src/main/resources/nekojs/authoring/examples/modules/hello.cjs>)

```js
// Copy this entry and lib/message.cjs together, preserving their relative paths.
const { formatGreeting } = require("./lib/message.cjs");
const message = formatGreeting("NekoJS");
console.info(message);
module.exports = { message };
```

**安装位置**：`nekojs/server_scripts/authoring-examples/modules/lib/message.cjs`

[完整源文件：modules/lib/message.cjs](<../../common/src/main/resources/nekojs/authoring/examples/modules/lib/message.cjs>)

```js
// This helper has no top-level side effects, even if discovered as an entry.
exports.formatGreeting = name => `Hello, ${name}!`;
```

**如何运行**：复制入口和 lib 辅助文件后 `/nekojs reload server`。

**预期结果与检查**：入口日志和导出的 message 都为 Hello, NekoJS!。入口通过 ./lib/message.cjs 使用纯函数；辅助文件只定义导出，即使脚本发现机制把它单独执行，也不会订阅事件或修改世界。

**如何清理**：移除整个示例 modules 目录，再 reload SERVER。

**验证边界**：通过真实模块加载管线运行原文件，检查输出、helper 功能以及重复 require 的导出对象缓存身份；不涉及真实游戏副作用。

<a id="assertions"></a>
## 9. 用 TEST 脚本编写可执行断言

**适用与前提**：TEST；需要 Test.section、assertEquals、assertTrue、assertThrows 和 summary。不要放进 server_scripts 后期待它自动以 TEST 方式执行。

**安装位置**：`nekojs/test_scripts/authoring-examples/assertions.js`

[完整源文件：test/assertions.js](<../../common/src/main/resources/nekojs/authoring/examples/test/assertions.js>)

```js
// Place in test_scripts and run /nekojs test. This does not run on server reload.
Test.section("Authoring example assertions");
Test.assertEquals("minecraft:stick", ["minecraft", "stick"].join(":"), "identifier formatting");
Test.assertTrue(["oak", "birch"].includes("oak"), "membership check");
Test.assertThrows(() => { throw new Error("Expected example failure"); }, "failure is observed");
Test.summary();
```

**如何运行**：复制到 test_scripts 对应位置后执行 `/nekojs test`。

**预期结果与检查**：本文件的三个断言通过、零失败。第三个断言检查显式抛出异常的函数，不是省略错误处理。

**如何清理**：移除文件即可；普通 SERVER reload 不会执行这份 TEST 脚本。

**验证边界**：原文件通过实际 TestJS 绑定执行并核对 passed=3、failed=0，不是另造一个同名假断言 API。

<a id="client-counter"></a>
## 10. 可点击的 TSX 计数器界面

**适用与前提**：仅限提供 ClientUI 的 NeoForge 26.x CLIENT profile；不要安装到缺少该能力的 Fabric 或 1.21.1。需要 nekojs/jsx-runtime、信号、createRoot 和 ClientUI.screen。它是 NekoJS UI，不是 React／浏览器 DOM。

**安装位置**：`nekojs/client_scripts/authoring-examples/counter.tsx`

[完整源文件：client/counter.tsx](<../../common/src/main/resources/nekojs/authoring/examples/client/counter.tsx>)

```tsx
// CLIENT only: requires the ClientUI binding published on NeoForge 26.x.
import { UI } from "nekojs/jsx-runtime";

const count = UI.createSignal(0);
const host = ClientUI.screen("Authoring counter", false);
const root = UI.createRoot(() => (
    <screen>
        <column>
            <label id="count">{String(count.get())}</label>
            <button id="increment" onClick={() => count.update(value => value + 1)}>
                Increment
            </button>
        </column>
    </screen>
), host);
host.bindRoot(root);
host.open();
```

**如何运行**：在支持该能力的客户端安装后执行 `/nekojs reload client`。

**预期结果与检查**：打开一个计数从 0 开始的屏幕，按 Increment 后变为 1、2 等。信号使用 get/update；root 与 host 绑定后打开。CLIENT 代际更换时会重建信号，不持久保存计数。

**如何清理**：关闭屏幕；移除脚本并 reload CLIENT，释放旧代资源。

**验证边界**：真实 TSX 编译器、模块管线和 UI runtime 执行源文件，仅平台屏幕 host 用测试替身；自动检查初值、click 事件后的 1 和 root.close 清理。真实绘制、输入路由、GPU 与完整游戏重载仍未验收。

<a id="greeting"></a>
## 11. 最小检查：确认脚本确实执行

**适用与前提**：SERVER 与 console.info；用于排查目录／脚本类型问题，不是完整业务示例。

**安装位置**：`nekojs/server_scripts/authoring-examples/greeting.js`

[完整源文件：greeting.js](<../../common/src/main/resources/nekojs/authoring/examples/greeting.js>)

```js
// Place in server_scripts. This example only writes a startup message.
console.info("NekoJS authoring example loaded");
```

**如何运行**：放入目标位置后 `/nekojs reload server`。

**预期结果与检查**：每次执行产生一次 NekoJS authoring example loaded 日志。

**如何清理**：删除后 reload SERVER。

**验证边界**：沿用隔离执行测试；只证明日志脚本执行，不证明世界事件或 API。

<a id="event-subscription"></a>
## 12. 最小检查：区分建立订阅与事件实际触发

**适用与前提**：SERVER 与 ServerEvents.recipes；不编辑任何配方。

**安装位置**：`nekojs/server_scripts/authoring-examples/event-subscription.js`

[完整源文件：event-subscription.js](<../../common/src/main/resources/nekojs/authoring/examples/event-subscription.js>)

```js
// Place in server_scripts. Observe the recipe event without changing recipes.
ServerEvents.recipes(event => {
    console.info("NekoJS recipe authoring example loaded");
});
```

**如何运行**：安装后完整 `/nekojs reload server`，等待配方处理。

**预期结果与检查**：在配方回调实际触发时记录观察消息，而不是订阅建立时就打印。

**如何清理**：删除后 reload SERVER。

**验证边界**：沿用隔离事件回调执行测试；不把它当成配方制作效果验收。

## 遇到问题先检查

- 没有任何日志：先检查文件是否在正确的 Script Type 目录、扩展名是否受支持、是否执行了相应命令。
- 订阅后没有动作：需要真正触发登录、配方处理或标签重建，单纯“文件加载成功”不是事件已经触发。
- 方法或属性不存在：读取同实例的生成声明，按重载和参数逐一检查；不要把 UNKNOWN 当作不存在，也不要用两个重载拼成一个签名。
- 注册内容缺失：检查是否完整重启，以及当前节点是否提供能力；注册期与运行期重载不是一回事。
- 标签未变化：区分 item/block 注册表，先换订阅代，再执行数据包重载；不要用待提交队列的旧视图判断结果。
- UI 失败：确认 NeoForge 26.x 的 ClientUI 能力，再看脚本、编译、主机三个层次；编译通过不等于 GPU 或输入验收完成。

真实实例最终验收应另外记录节点／加载器版本、操作命令、观察结果和维护者结论，不能由这份示例自动代替。
