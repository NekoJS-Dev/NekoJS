# 票 29 迁移材料：Assets/Lang 资源生成与 plugin-only 语言声明

日期：2026-09-21　worktree：`D:/mcmodDemo/NekoJS-mult-t29`（分支 `ticket-29-assets-lang`，基线 `f9c725f0`）

## 1. 行为变更

### 1.1 plugin 语言必须显式声明（对本票影响最大的迁移项）

旧版：平台只遍历 `ClientEvents.LANG.registeredKeys()`（脚本 keyed listener 的语言）。
插件 `generateLang(LangGeneratorJS)` 因此被**脚本 listener 隐式门控**——没有任何脚本在该语言上
注册 `ClientEvents.lang` 监听器时，插件的 `generateLang` 回调**一次都不会被触发**。

新版：平台先取「插件 `generatedLangs()` 声明 ∪ 脚本 keyed listener 语言」的确定性有序并集，
再逐语言触发 plugin 与脚本。

> **迁移要求**：旧版依赖脚本 listener 解锁非 `en_us` plugin 语言的插件，
> **现在必须覆写 `generatedLangs()` 显式声明这些语言**。
> **默认 `en_us` 无需声明**——`NekoJSPlugin.generatedLangs()` 的 default 实现即
> `Set.of("en_us")`，只写 en_us 的插件保持零改动。

```java
// 旧（依赖某脚本恰好注册了 ClientEvents.lang('ja_jp')，插件才会被调到）
public class MyPlugin implements NekoJSPlugin {
    @Override
    public void generateLang(LangGeneratorJS generator) {
        generator.add("mymod.item.thing", "物");
    }
}

// 新（自己声明需要的语言；en_us 可省略）
public class MyPlugin implements NekoJSPlugin {
    @Override
    public Set<String> generatedLangs() {
        return Set.of("en_us", "ja_jp");
    }

    @Override
    public void generateLang(LangGeneratorJS generator) {
        switch (generator.getLang()) {
            case "ja_jp" -> generator.add("mymod.item.thing", "物");
            default -> generator.add("mymod.item.thing", "Thing");
        }
    }
}
```

`generatedLangs()` 是**回调面（直调型）**钩子：覆写即生效，不需要 `implements` 任何
`Contributor`、不需要注册、不构成第二注册框架或通用资源声明系统。

### 1.2 语言集合的确定性顺序

旧版顺序来自 `ClientEvents.LANG.registeredKeys()` → `DispatchEventBusBase` 的
`Set.copyOf(dispatched.keySet())`——**无序**，同一份脚本集合在不同 JVM 运行下可能以不同顺序
遍历语言。

新版用 `TreeSet` 归一后字典序返回。影响：多语言产出顺序可依赖了（日志/诊断顺序稳定，
同 key 跨语言不会互相影响，但重复运行的产物顺序一致）。

### 1.3 非法语言代码：整批拒绝（有意的严格语义）

**明确写出后果**：任一非法语言代码（插件 `generatedLangs()` 声明或脚本 listener key）会让
**本轮客户端资产生成整体失败**——`resolveGeneratedLangs` 抛 `IllegalStateException`，
`NekoJSClient.postClientGeneration` 的 `catch` 记为 `generate_assets` 回调错误并进错误面板，
**该轮不会写入任何 `lang/<lang>.json`**（包括那些本身合法的语言）。

这是设计选择，**不是静默 no-op**：
- 票据 AC4 明确要求「语言集合先完整确定并校验，未通过不进入任何语言文件写入」+
  「无效输入不产生部分文件」；
- 跳过非法项会产生「一半语言有文件、一半没有」的不可诊断中间态，且失败无人知晓；
- 校验全部发生在返回**之前**，所以调用方拿到集合时不可能部分写入。

合法判据：`[A-Za-z0-9_]{1,64}`（`LangGeneratorJS.isValidLangCode`，与 `writeTo` 同源）。
`null` 声明、集合内 `null`、空串、长度 > 64、含空格/斜杠等一律算非法。
错误消息含 owner（插件 FQN 或 `script lang listener`）与非法 code，便于定位。

### 1.4 单个插件的 `generatedLangs()` 抛异常只作废它自己

若某插件的 `generatedLangs()` 抛异常，平台记 error 日志
（`generatedLangs hook failed for <FQN>; its language declarations are skipped`）并**跳过该插件的
声明**，其余插件的声明与脚本语言照常生成。这与 §1.3 的整批拒绝**刻意不同**：代码缺陷不连坐他人，
非法输入才整批拒绝。

### 1.5 `Assets` 绑定的恢复（修复，不是 breaking）

`registry.register("Assets", new AssetGeneratorJS())` 曾被 `c8066519`/`59919f87` 误删
（只留下同名的 TypeDoc 文档条目），导致脚本侧 `Assets.blockState/blockModel/itemModel/texture`
不可用而文档/补全仍宣称存在。本票**恢复**了该注册，绑定名与实现类均保持原样。

- 对脚本作者：这是**修复**——`Assets.*` 恢复可用，写法不变。
- 无 breaking：没有任何公开方法被删除或改名。
- 平台面：注册行仍带 `//? if >=26` 守卫，与仓库顶部注释「Assets 是 26.x 独有」一致；
  **1.21.1 节点没有 `Assets` 绑定**（既存差异，本票未扩大）。

## 2. 旧 → 新对照

| 能力 | 旧路径 | 新路径 | 兼容性 |
|---|---|---|---|
| 插件声明要生成的语言 | 无（靠脚本 listener 隐式门控） | `NekoJSPlugin#generatedLangs()`（default `Set.of("en_us")`） | **新增**；非 en_us 的旧写法行为改变，见 §1.1 |
| 语言集合来源 | 仅 `ClientEvents.LANG.registeredKeys()` | 插件声明 ∪ 脚本 keyed listener 语言 | 行为收紧，见 §1.1/§1.2 |
| 语言遍历顺序 | 无序（`Set.copyOf`） | 字典序（`TreeSet`） | 行为收紧，见 §1.2 |
| 非法语言代码 | 仅在 `writeTo` 单语言层抛 `IllegalArgumentException`（前面的语言已写盘） | `resolveGeneratedLangs` 在写入任何文件前整批拒绝 | **行为收紧**，见 §1.3 |
| `Assets` typed binding | （被 `c8066519` 误删后不可用） | `registry.register("Assets", new AssetGeneratorJS())` 恢复 | **修复**，见 §1.5 |
| lang code 校验 | `writeTo` 内联正则 | `LangGeneratorJS.isValidLangCode(String)` 静态判据，`writeTo` 与预检共用 | 行为不变（同正则、同位置、同异常类型与消息；`requireNonNull` 仍先于校验） |
| `ClientEvents.generateAssets` 事件 | 不变 | 不变（唯一 Assets 生成事件） | 保持 |
| `ClientEvents.lang` 事件 | 不变 | 不变（唯一 lang 生成事件） | 保持 |
| `DataGeneratorJS`/`LangGeneratorJS`/`AssetGeneratorJS` 公开方法 | 不变 | 不变 | 保持 |

## 3. 旧 route 消费者清单与删除条件

### 3.1 消费者

| 消费者 | 位置 | 处置 |
|---|---|---|
| 脚本侧 `ClientEvents.lang('ja_jp', ...)` 写法 | 脚本作者 | 仍有效（keyed listener 语言进并集），但**不再是**解锁插件该语言的唯一手段 |
| 插件侧 `generateLang` | `common/.../api/NekoJSPlugin.java` | 保留，调用时机/次数改变（现按语言集合，含插件自身声明） |
| 插件侧 `generateAssets` | 同上 | 不变 |
| `Assets.*` 脚本写法 | wiki「事件参考.md」/README | 恢复可用；wiki 未在本票更新（见 §4） |
| Fabric 节点 | `src/fabric` | Assets/Lang 的 client 生成路径是 NeoForge 面（`NekoJSClient` 在 `//? if neoforge` 内）；fabric 侧的对应能力本票未改、未声明 parity |

### 3.2 删除条件（本票无删除对象）

本票**未删除任何旧公开路径**：`generateAssets`/`lang` 事件、三个 generator 的公开方法面
全部保持。`Assets` 绑定是**恢复**而非删除。因此 AC11 的「旧直接文件写入 / 绕过 DataGenerator
的 asset helper / 重复生成入口」在本票范围内**没有适用对象**，无迁移期双路径需要清理，
也无需等待删除确认——但按票据 Human input note，AC11 保持**未勾选**，由维护者确认这一段
「无适用对象」的判断。

若将来要收口，候选与前提（均**不在**本票）：
- `NekoJSCorePlugin.registerBinding` 里 `Assets` 注册行与 `registerTypeDocs` 里同名文档条目
  是两处手写、靠 `Ticket29AssetBindingTest` 关联——可选合并为单一事实源；
- `//? if >=26` 守卫下 1.21.1 无 `Assets` 绑定的既存差异，需要正式 capability 记录（票 31/32）。

## 4. 遗留（owner）

- wiki「事件参考.md」/「全局绑定.md」的 `Assets` 段落未在本票更新（wiki 属维护者文档面，
  改它会与其它票重叠）；内容本身在绑定恢复后重新成立 —— owner：维护者文档面（票 37）。
- Fabric 节点 Assets/Lang 的 capability/source-trace 正式记录 —— owner：票 31/32。
- 真机客户端资源 reload smoke（`nekojs/assets` 下产物被 MC resource manager 实际读到、
  F3+T 恰好触发一次） —— owner：票 34 / CLIENT_GUI_RENDER owner。
- TS/Python declaration 面未覆盖 `Assets`/`generatedLangs()` —— owner：票 09/33。
