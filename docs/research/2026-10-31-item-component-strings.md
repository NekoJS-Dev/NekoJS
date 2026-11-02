# 物品组件字符串往返

## 问题与范围

旧 `hand` 的 ID/数量字符串丢弃了组件，完整 JSON 虽保留数据，却不能直接跨版本交给 `Item.of`。本次同时修复字符串输出与 ItemStack 适配器，使 `Item.of('minecraft:iron_sword[damage=2]')`、对象内的 `id`/`item` 字符串及自动 ItemStack 参数转换使用同一条解析路径。`hand`、`hotbar`、`inventory` 均输出带组件字符串，仍保留完整 JSON 行。

## KubeJS 只读调研

按用户要求由 Luna xhigh 只读核查 `D:/kubejs-2601`：

- `core/ItemStackKJS.java:202-235`：`toStringJS` → `kjs$toItemString0`，数量前缀、物品 ID、组件补丁，外包 JS 单引号。
- `component/DataComponentWrapper.java:491-525`：codec + registry-aware NBT ops 编码组件值，再以 SNBT 输出；minecraft 命名空间缩写，删除项用 `!component`。
- `plugin/builtin/wrapper/ItemWrapper.java`：字符串通过 type wrapper 进入 `ItemWrapper.read`，自写 StringReader 读 ID/patch；`Item.of` 的参数转换会走同一逻辑。KubeJS 不是直接使用原版 ItemParser。

NekoJS 复用原版 ItemParser 和组件 codec，不引入 KubeJS 依赖。复制内容用 JSON 字符串转义，可直接粘贴到 JS/TS；不要求外层必须单引号。保留现有正整数数量语义及超过自然堆叠上限的工厂行为，不复制 KubeJS 的小数数量和旧式尾随 `{custom_data}` 语法。

## 实现

`ItemStackStrings` 统一解析/格式化。SNBT codec 保留嵌套值、名称引号/反斜杠、损伤、附魔注册表引用和组件删除；格式化按组件名排序，使输出稳定。解析拒绝重复/未知组件、无效值、未闭合括号、多余尾部，不会悄悄退化为纯 ID。原来的纯 ID、空值和数量入口保持原路径。

注册表按调用时获取，不缓存世界实例：NeoForge/Fabric 使用各自已有服务端入口；客户端使用当前连接；无世界时回退内建注册表，缺少的动态引用会明确报错。客户端访问独立在 client 类中，仅通过物理客户端检查后调用。1.21.1 与 26.x 的 ItemParser 返回对象虽不同，`item()`/`components()` 可共享。组件字符串声明加入现有 AdapterInputShape，包含对象 `id`/`item` 字段；不放宽成裸 string，保留纯 ID 的补全。

`Item.id` 仍查询物品类型；完整 ItemStack JSON 对象仍不承诺跨版本作为 `Item.of(object)` 输入，1.21.1 的 `components` 对象支持没有在此扩展。

## 验证

- 五节点 `build`、`:common:check`、`guardLint`：首轮通过（1 分 20 秒）；最终描述符和文案修改后再次全部通过（3 分 3 秒）。
- `npm run test:probe-types`：通过。
- `python tools/nekojs-ci-gates.py all --out build/item-strings-platform-gates.json`：通过。
- `python bench/commands/run_smoke.py`：26.1.2 独立服务端 44 条通过。
- `python bench/commands/run_smoke.py --node 1.21.1`：1.21.1 独立服务端 44 条通过。

实机断言包括真正的 JS `Item.of`、自动适配、对象数量覆盖、无空格数量前缀、精确 `minecraft:iron_sword[damage=2]` 输出、含动态附魔引用的组件往返，以及 hotbar/inventory 的字符串重建。两次服务端均正常停止。普通 JVM 的 ItemStack 数据测试依旧可能明确跳过，不将其作为实机证据。

客户端实际复制/悬停/文件管理器操作及 Fabric 游戏进程未在此轮实测；Fabric 由构建和公共实现测试覆盖。不代表所有 KubeJS 物品语法已兼容。本次未推送、未创建 PR。


## 用户仓库同步

本轮补丁已应用到 `D:/mcmodDemo/NekoJS-mult`。通过正式 Stonecutter 切换流程应用，结束恢复活动 1.21.1；同步前后 4,342 个已跟踪文件的摘要比较显示功能范围外零内容变化。未暂存或提交 D 盘原有改动，未处理 lite。六份生产 JAR 均包含共享组件解析器，未包含服务端冒烟辅助类。日志、两节点结构化实机报告与制品摘要位于本工作树 `build/item-strings-*` 及各节点专用 smoke 目录，不入库。

D 盘实际执行 `gradlew.bat :1.21.1:build --offline --console=plain` 通过（1 分 7 秒）；新生成普通与服务端 JAR 均检查到适配器、命令对共享组件编解码器的实际调用，且没有测试辅助类。
