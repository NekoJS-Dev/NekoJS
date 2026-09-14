# English translation guide

_Pass this guide to the AI when translating_

This document describes how the English documentation under `wiki/en_us/` is
produced and maintained. It exists so that the translation stays consistent
across pages, and so that a reviewer can see exactly what was done and how
much to trust it.

| | |
|---|---|
| Status | Proposal. One sample page translated ([`wiki/en_us/Home.md`](../wiki/en_us/Home.md)). |
| Source locale | `zh_CN` (the existing pages in `wiki/`) |
| Target locale | `en_us` |
| Last updated | 2026-09-12 |

---

## 1. How this translation was produced

**The translation was drafted with AI assistance (Claude, by Anthropic), then
reviewed and edited by a human contributor.**

This is stated plainly because it affects how the work should be reviewed.

**What the human contributor can check:**

- That the English reads naturally and is easy for non-native speakers.
- That terminology is used consistently across pages.
- That code identifiers, paths, commands, and table structures match the
  source exactly.
- That links resolve.

**What the human contributor cannot check:**

- Whether the English says the same thing as the Chinese. The contributor
  does not read Chinese. Semantic accuracy depends on the AI draft and on
  review by a Chinese-speaking maintainer.

This is the main limitation of this contribution. It is the reason for the
`TRANSLATION-NOTE` mechanism in section 5.

---

## 2. Layout and naming

Following the maintainer's guidance: the original documents stay where they
are, and each translation goes into a subfolder named after its locale.

```text
wiki/
├── Home.md                 # Chinese, unchanged - wiki URLs do not change
├── 快速开始.md
├── ...
└── en_us/
    └── Home.md             # English

docs/
├── ROADMAP.md              # Chinese, not translated (changes too often)
├── DEVELOPMENT_SPEC.md     # Chinese, not translated
└── translation-guide.md    # this document (process doc, English only)
```

**Rules:**

- The Chinese pages are **never moved or renamed**. Moving them would change
  their GitHub wiki URLs and break any link that has already been shared.
- Chinese pages receive at most one added line: a link to the English version.
  No other edit is made to a Chinese page.
- English page filenames are the English page title in `Kebab-Case`, for
  example `Quick-Start.md`, `Recipe-System.md`. The full list is in section 9.
- Links between English pages are bare and relative, matching the existing
  wiki convention: `[Quick start](Quick-Start)`.
- Links from an English page back to a Chinese page use `../`, for example
  `[中文](../Home)`.

---

## 3. The metadata block

Every translated page begins with a block recording which version of the
source it was translated from. This addresses the concern that the API is
still changing and that translations can silently go out of date.

```markdown
> **English** · [中文](../Home)
>
> | | |
> |---|---|
> | Source page | [`wiki/Home.md`](../Home) |
> | Source version | commit `101fd07a`, 2026-08-20 |
> | Translation updated | 2026-09-12 |
>
> If the Chinese page has changed since that commit, the Chinese page is correct and this one may be out of date.
```

**Why a commit hash and not only a date:** a date tells the reader roughly how
old the translation is. The commit hash lets anyone check drift exactly:

```bash
# Has the Chinese source changed since this page was translated?
git log --oneline 101fd07a..HEAD -- wiki/Home.md
```

If that command prints nothing, the translation is current. If it prints
commits, the translation needs review.

`scripts/check-translation-drift` does this for every page at once and also
lists Chinese pages that have no translation yet. See
[`scripts/check-translation-drift.README.md`](../scripts/check-translation-drift.README.md).

**The Chinese page is always the authority.** When the two disagree, the
Chinese page is correct.

### Pages that cannot show a visible table

`_Sidebar.md` is rendered beside every page in the wiki, so a visible metadata
table there would appear on every page. For that file only, the same fields go
in an HTML comment at the top instead:

```markdown
<!--
  Source page: `wiki/_Sidebar.md`
  Source version: commit `abc1234`, 2026-09-12
  Translation updated: 2026-09-12
-->
```

`scripts/check-translation-drift` reads both forms.

Get the values for a new page with:

```bash
git log -1 --format='%h %ad' --date=short -- wiki/<page>.md
```

---

## 4. Writing style

Many readers of the English documentation will not be native English speakers.
The English is written to be plain and direct rather than expressive.

| Rule | Do | Do not |
|---|---|---|
| **No idioms or metaphors** | "This is not supported." | "This is a non-starter." |
| **Plain verbs, not phrasal verbs** | cancel, start, remove, continue | call off, kick off, get rid of, carry on |
| **Active voice** | "The loader scans the directory." | "The directory is scanned by the loader." |
| **One idea per sentence** | Split long Chinese sentences that chain clauses with commas. | Preserve the original sentence boundaries when the result is hard to read. |
| **Sentence case headings** | "Registering new content" | "Registering New Content" |

**Requirement strength must be preserved.** These carry meaning and are kept
consistent:

| Chinese | English |
|---|---|
| 必须 | must |
| 应该 / 应 | should |
| 可以 / 可 | can, may |
| 不要 / 禁止 | do not, must not |

**Structure is preserved** so that an English page can be compared against the
Chinese page side by side: same heading levels, same table columns and row
order, same list order, same blockquote callouts.

**Numbers, versions, tick counts, and units are left unchanged.** Write
"20 ticks", not "twenty ticks".

### Recurring table headers

The same header rows appear across many pages. Translate them exactly as below,
every time, so the pages read as one set of documents. The count is how often
each appears in the Chinese wiki.

| Chinese header | English header | Count |
|---|---|---|
| `\| 方法 \| 说明 \|` | `\| Method \| Description \|` | 37 |
| `\| 事件 \| 事件对象 \| 说明 \|` | `\| Event \| Event object \| Description \|` | 9 |
| `\| 特性 \| PEP \| 语法示例 \| 对 JS 转译 \| NekoJS 状态 \|` | `\| Feature \| PEP \| Syntax example \| JS translation \| NekoJS status \|` | 5 |
| `\| 钩子 \| 作用 \|` | `\| Hook \| Purpose \|` | 5 |
| `\| 项 \| 值 \|` | `\| Option \| Value \|` | 4 |
| `\| 注解 \| 目标 \| 作用 \|` | `\| Annotation \| Target \| Purpose \|` | 2 |
| `\| 语法 \| 状态 \|` | `\| Syntax \| Status \|` | 2 |
| `\| 特性 \| 说明 \|` | `\| Feature \| Description \|` | 2 |
| `\| 平台 \| 配方 reload \|` | `\| Platform \| Recipe reload \|` | 2 |

Note that 作用 in a header is **Purpose**, not "Effect".

---

## 5. What is not translated

**Never translated — these are identifiers:**

Code identifiers, class names, method names, file paths, configuration keys,
command names, registry IDs, and anything inside backticks or a fenced code
block. For example `startup_scripts`, `RecipeEventJS`, `nekojs:cool_gem`,
`/nekojs reload`, `engine.toml`, `probe.toml`, `jsconfig.json`,
`manifest.json`, and all Gradle module names.

Product and project names: NekoJS, NekoProbe, KubeJS, ProbeJS, CraftTweaker,
GraalJS, GraalVM, Graal, Rhino, NeoForge, Forge, Fabric, Cleanroom, Mixin,
Access Transformer, JEI, HEI, Minecraft, Mojang.

**Translated:** Chinese comments *inside documentation code samples* are
translated, because they are prose written for the reader.

**Not translated by this effort:** Chinese comments in the Java source tree.
There are roughly 9,570 such lines across 783 files. Readers who need them can
use an IDE translation plugin.

**When the source is ambiguous**, the translator translates as literally as
possible and adds a note on the line above, rather than guessing at behaviour:

```markdown
<!-- TRANSLATION-NOTE: unclear whether this applies to 1.12.2 as well -->
```

These notes are collected into the pull request description as questions for
the maintainer. They are not to be left in merged pages.

---

## 6. Minecraft terminology

Game concepts use the official English Minecraft term, not a literal
translation.

| Chinese | English | Note |
|---|---|---|
| 方块 | block | |
| 方块实体 | block entity | Class is `BlockEntity`. Never "tile entity" on modern versions. |
| 方块状态 | block state | |
| 物品 | item | |
| 物品栈 | item stack | |
| 物品栏 | inventory | |
| 实体 | entity | |
| 流体 | fluid | |
| 液体方块 | liquid block | |
| 桶 | bucket | |
| 配方 | recipe | |
| 合成 | crafting | |
| 熔炉 | furnace | |
| 战利品表 | loot table | |
| 标签 | tag | Registry tag or NBT tag. Keep surrounding words clear about which. |
| 数据包 | data pack | Two words, matching Mojang's spelling. |
| 资源包 | resource pack | |
| 整合包 | modpack | One word. |
| 原版 | vanilla | Means unmodified Minecraft. Never "original version". |
| 创造模式 | Creative mode | |
| 创造标签页 | creative tab | Class is `CreativeModeTab`. |
| 附魔 | enchantment | |
| 铁砧 | anvil | |
| 药水 | potion | |
| 状态效果 | status effect | Class is `MobEffect`. Use "status effect" in prose. |
| 粒子 | particle | |
| 模型 | model | The block or item model JSON. Not a data model. |
| 纹理 | texture | The `.png` asset. |
| 画作 | painting | |
| 村民 | villager | |
| 生物蛋 | spawn egg | |
| 硬度 | hardness | |
| 抗爆 | blast resistance | |
| 亮度 | light level | |
| 耐久 | durability | |
| 堆叠 | stack | 最大堆叠数 is "max stack size". |
| 稀有度 | rarity | |
| 燃烧时间 | burn time | |
| 掉落 | drop | |
| 世界 | world | Type is `Level`. Use "world" in prose, "level" only when naming the type. |
| 维度 | dimension | |
| 区块 | chunk | |
| 刻 | tick | |
| 主线程 | main thread | |
| 服务端 | server | 服务端脚本 is "server script". |
| 客户端 | client | |
| 单机 | singleplayer | |
| 多人 | multiplayer | |
| 前置 mod | required dependency mod | Clearer than "pre-mod" or "prerequisite mod". |

---

## 7. NekoJS terminology

### Framework concepts

| Chinese | English | Note |
|---|---|---|
| 脚本 | script | |
| 脚本包 | script pack | |
| 脚本属性 | script property | The first-line `// priority=...` declarations. |
| 脚本类型 | script type | STARTUP, SERVER, CLIENT, TEST. |
| 绑定 | binding | |
| 全局绑定 | global binding | |
| 事件 | event | |
| 事件组 | event group | |
| 事件总线 | event bus | |
| 监听 | listen | 监听器 is "listener". |
| 取消 | cancel | 可取消事件 is "cancellable event". Double `l` throughout. |
| 注册 | register | As a noun, "registration". |
| 注册表 | registry | |
| 冻结 | freeze | 解冻 is "unfreeze". The vanilla lifecycle state. |
| 重载 | reload | |
| 热重载 | hot reload | |
| 适配器 | adapter | `JSTypeAdapter` is a "type adapter". |
| 包装器 | wrapper | |
| 插件 | plugin | |
| 扩展点 | extension point | |
| 沙箱 / 沙盒 | sandbox | The source uses both forms for the same concept. |
| 工作区 | workspace | |
| 类型生成 | type generation | |
| 声明文件 | declaration file | The generated `.d.ts` and `.pyi` files. |
| 补全 | completion | Editor autocomplete. "Code completion" on first use. |
| 契约 | contract | A deliberate project term. Not a synonym for "interface". |
| 不变量 | invariant | |
| 门禁 | gate | "Build gate" or "CI gate" on first use. |
| 弃用 | deprecate | 弃用跑道 is "deprecation runway". |
| 冒烟测试 | smoke test | |
| 回归测试 | regression test | |
| 快照 | snapshot | |
| 信任存储 | trust store | |
| 签名 | signature | |
| 优先级 | priority | |
| 看板娘 | mascot | Appears in the README credits. |
| 本体 | NekoJS itself | **Do not translate literally.** 本体 means NekoJS proper, as opposed to a plugin or an external mod. `NekoJS 本体内置` is "built into NekoJS itself". Never "ontology" or "body". |
| 条目 | entry | A registry or catalog entry. |
| 第三方 | third-party | Hyphenated as an adjective. |
| 轻量 | lightweight | One word. |
| 能力 | capability | `PlatformCapability`. Use "capability", not "ability". |
| 回调 | callback | |
| 集成 | integration | |
| 环境 | environment | 脚本环境 is "script environment", meaning one GraalJS context. |
| 服务器 | server | The server instance or machine. Distinct from 服务端, which is the server *side*. Both render as "server"; keep the surrounding words clear. |
| 黑名单 | blocklist | Used for the sandbox class filter. "Blacklist" also appears in the source; prefer "blocklist" in new English text. |
| 高危 | dangerous | 高危 Java 类 is "dangerous Java classes". Not "high-risk", which reads as risk-management jargon. |
| 可信代码 | trusted code | 半受信 is "semi-trusted". |
| 受限 | restricted | |
| 暴露 | expose | |
| 拦截 | intercept | |
| 过滤 | filter | |
| 定向 | targeted | 定向失效 is "targeted invalidation". |

### Runtime, compiler, and module system

| Chinese | English | Note |
|---|---|---|
| 运行时 | runtime | |
| 编译 | compile | |
| 前端 | frontend | A *language* frontend, not web frontend. |
| 擦除 | erasure | 可擦除 TypeScript is "erasable TypeScript". |
| 加载 | load | |
| 入口 | entry point | |
| 依赖 | dependency | |
| 模块 | module | |
| 互操作 | interoperability | Shorten to "interop" only after the full word appears once. |
| 实时绑定 | live binding | |
| 循环依赖 | circular dependency | |
| 顶层 await | top-level await | |
| 遮蔽 | shadow | The noun is "shadowing". |
| 失效 | invalidate | Cache or module invalidation. Not "expire". |
| 缓存 | cache | |
| 反射 | reflection | |
| 注入 | inject | Mixin or interface injection. |
| 字面量 | literal | |
| 语义 | semantics | |
| 语法 | syntax | |
| 子集 | subset | |
| 转译 | transpile | 转译器 is "transpiler". |
| 源码映射 | source map | |
| 落盘 | write to disk | |
| 兜底 | fallback | Verb form: "fall back to". Not "bottom line". |
| 透传 | pass through | Adjective: "pass-through". |
| 边界 | boundary | |
| 阶段 | phase | |
| 实现 | implementation | As a verb, "implement". |
| 内置 | built-in | Hyphenated as an adjective. |
| 自定义 | custom | |
| 默认 | default | |
| 静态 | static | |
| 诊断 | diagnostic | |
| 错误追踪 | error tracking | |
| 行列号 | line and column number | |
| 方法 | method | |
| 函数 | function | |
| 参数 | parameter | |
| 返回 | return | |
| 字段 | field | |
| 对象 | object | |
| 字符串 | string | |
| 目录 | directory | |
| 路径 | path | |
| 调用 | call | |
| 版本 | version | |
| 平台 | platform | |
| 差异 | difference | |
| 渲染 | render | |
| 命名 | naming | 命名空间 is "namespace". |
| 匹配 | match | |
| 执行 | execute | |
| 可用 | available | |
| 一致 | consistent | |
| 启动 | startup | 启动脚本 is "startup script" and matches `startup_scripts`. But 启动游戏 is "start the game". |
| 配置 | configuration | As a verb, "configure". |
| 说明 | description | As a table column header, always "Description". |
| 作用 | purpose | As a table column header, "Purpose", not "Effect". |
| 后端 | backend | A probe backend. Pairs with 前端 (language frontend). |
| 成员 | member | A class member: field or method. |
| 泛型 | generic | TypeScript generics. |
| 幂等 | idempotent | Safe to run more than once with the same result. |
| 原生 | native | Native ESM, a native Minecraft class. **Do not confuse with 原版, which is "vanilla".** |
| 驱动 | driven | 数据驱动 is "data-driven". For 强力驱动 in marketing text, use "powered by". |
| 包级模块 | package-level module | The `java:package/path` import form. |
| 类级模块 | class-level module | The `java:package/Class` import form. |
| 拼写错误 | spelling mistake | Used by the script member validation feature. |

### Process vocabulary

| Chinese | English | Note |
|---|---|---|
| 路线图 | roadmap | |
| 开发规范 | development specification | |
| 台账 | decision log | Clearer than the literal accounting term "ledger". |
| 裁决 | decision | "Ruling" is too legal. |
| 归档 | archive | |
| 待办 | to-do | |
| 破坏性变更 | breaking change | |
| 参与贡献 | contributing | |
| 详见 | See | "See [Page]" or "For details, see [Page]". Not "see in detail". |
| 已知限制 | known limitations | |
| 常见问题 | frequently asked questions | "FAQ" in navigation links. |

---

## 8. Terms with more than one meaning

**This is the most important section of this document.** These Chinese terms
appear in the documentation with different meanings in different places. Using
the wrong English word changes what the sentence says.

### 降级

| English | When |
|---|---|
| **lower** (noun: lowering) | Compiler context: converting a syntax form into a simpler one. `enum 降级为 IIFE` becomes "`enum` is lowered to an IIFE". |
| **degrade** | Platform capability context: providing reduced behaviour where the full feature is impossible. `诚实降级` becomes "honest degradation". |

Do not use "downgrade" or "demote" for either sense. **Both senses appear on
the Python documentation page**, so read each sentence before choosing.
Getting this wrong inverts the meaning.

### 解析

| English | When |
|---|---|
| **parse** | Turning source text into tokens or an AST. |
| **resolve** | Turning a module specifier or an ID into a concrete target. 模块解析 is "module resolution". |

### 生成

| English | When |
|---|---|
| **generate** | Producing files, declarations, or output. The common sense. |
| **spawn** | Creating an entity in the world. 自然生成 is "natural spawning". |

### 覆盖

| English | When |
|---|---|
| **override** | Replacing an inherited or default behaviour. |
| **overwrite** | Replacing file contents or a stored value. |
| **coverage** | Test or platform coverage. 测试覆盖 is "test coverage". |

### 收敛

| English | When |
|---|---|
| **narrow** | Restricting an API surface to a smaller set of types. `形参收敛为 Object` is "the parameter type is narrowed to `Object`". |
| **consolidate** | Moving several scattered implementations into one place. `已收敛到 common` is "consolidated into `common`". |

Do not use "converge".

### 产物

| English | When |
|---|---|
| **output** | Generated files. probe 产物 is "probe output". |
| **build artifact** | Compiled jars and Gradle outputs. 编译产物. |

Do not use "product".

### 生效

| English | When |
|---|---|
| **take effect** | A setting or change becoming active. |
| **apply** | A platform actually honouring an option. "`renderType` 在 1.21.1 上会实际生效" is "`renderType` is actually applied on 1.21.1". |

### 分发

| English | When |
|---|---|
| **dispatch** | Routing an event to listeners. `DispatchEventBus`. |
| **distribution** | Shipping script packs to clients. 多人脚本包分发. |

---

## 9. Progress

| Page | English file | Status |
|---|---|---|
| `Home.md` | `Home.md` | Translated, awaiting review |
| `快速开始.md` | `Quick-Start.md` | Not started |
| `脚本基础.md` | `Script-Basics.md` | Not started |
| `脚本属性.md` | `Script-Properties.md` | Not started |
| `全局绑定.md` | `Global-Bindings.md` | Not started |
| `Python-脚本.md` | `Python-Scripts.md` | Not started |
| `Python-特性清单.md` | `Python-Feature-Support.md` | Not started |
| `事件参考.md` | `Event-Reference.md` | Not started |
| `命令.md` | `Commands.md` | Not started |
| `常见问题.md` | `FAQ.md` | Not started |
| `配方系统.md` | `Recipe-System.md` | Not started |
| `注册新内容.md` | `Registering-New-Content.md` | Not started |
| `模块系统.md` | `Module-System.md` | Not started |
| `TypeScript-与-JSX.md` | `TypeScript-and-JSX.md` | Not started |
| `Node-js-兼容.md` | `Node-js-Compatibility.md` | Not started |
| `插件开发.md` | `Plugin-Development.md` | Not started |
| `类型适配器.md` | `Type-Adapters.md` | Not started |
| `事件扩展.md` | `Event-Extensions.md` | Not started |
| `注解体系.md` | `Annotations.md` | Not started |
| `项目架构.md` | `Project-Architecture.md` | Not started |
| `Probe-类型生成.md` | `Probe-Type-Generation.md` | Not started |
| `构建系统.md` | `Build-System.md` | Not started |
| `_Sidebar.md` | `_Sidebar.md` | Translated. Entries link to the Chinese page until that page is translated. |
| `README.md` | `README.en_us.md` | Not started |
