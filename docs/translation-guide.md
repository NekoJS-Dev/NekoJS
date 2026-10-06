# Wiki translation guide

Read this guide when translating or updating a bilingual Wiki page. Follow the steps in [translation-workflow.md](translation-workflow.md); this file defines naming, structure and terminology.

## Canonical pages

[wiki-pages.json](wiki-pages.json) is the single ordered list of topics. Each topic has two files in the Wiki root:

```text
wiki/python-scripts_cn.md
wiki/python-scripts_us.md
```

The maintainer selected `_cn` for Simplified Chinese and `_us` for English. These are repository filename conventions, not locale identifiers for a runtime. Topic IDs are lowercase English kebab-case. Keep the same topic ID for both languages.

[Home](../wiki/Home.md) is the shared entry page; [_Sidebar](../wiki/_Sidebar.md) is the shared bilingual sidebar. Both are generated from the manifest. Old Chinese names and `wiki/en_us/` pages are migration entries, not translation sources. Read and edit the canonical page.

## Structure and links

- Preserve heading levels and order, table columns and row order, lists, callouts and example order. Translate all sections, rather than summarizing them.
- Translate explanatory prose and comments in documentation examples. Preserve commands, identifiers, configuration keys, versions, values and code behavior.
- Keep diagnostic messages and protocol fixtures literal when the reference quotes actual values.
- Link within the current language: `[Python scripts](python-scripts_us)` in English and `[Python 脚本](python-scripts_cn)` in Chinese.
- The language link at the top goes to the other version of the same topic. For example, English Python scripts links to `python-scripts_cn`, not a home page.
- Preserve `#wiki-section-N` link targets. The sync command inserts matching explicit anchors into both pages from their heading positions; generated navigation and anchors are not hand-maintained.
- Read the complete source before translation. Investigate uncertain behavior against its implementation and tests. Record unresolved questions separately rather than silently changing the product contract.

A canonical page starts with:

```markdown
<!-- wiki-page: python-scripts; locale: us -->

> **English** · [中文](python-scripts_cn)

# Python scripts
```

## Synchronization and review

The manifest stores `sourceDigest` and `translationDigest`, computed from canonical bodies without generated headers, anchors and navigation. The checker reads current working files, so uncommitted edits also cause drift. A digest match establishes the reviewed file version, not semantic correctness.

After checking translation accuracy and running structural checks, record the reviewed pair explicitly:

```bash
node scripts/wiki-docs.mjs stamp python-scripts
```

Use `stamp --all` only after reviewing the complete bilingual set. A successful automated check is not human acceptance. Current `translationStatus` values describe technical draft review; maintainer acceptance is reported separately.

## Writing style

Use direct English, active voice and one idea per sentence. Preserve requirement strength: 必须 = must, 应 = should, 可以 = can, 禁止 = must not. Use sentence-case headings. Natural sentence boundaries can differ between languages while section structure stays aligned.

The implementation is the source of behavior. A Chinese source can also contain a mistake; resolve it explicitly and update both languages together rather than declaring either language automatically correct.

## Terminology

| Chinese | English | Usage |
|---|---|---|
| 方块 / 方块实体 / 方块状态 | block / block entity / block state | Modern `BlockEntity`, not tile entity |
| 物品 / 物品栈 / 物品栏 | item / item stack / inventory | |
| 配方 / 合成 / 战利品表 | recipe / crafting / loot table | |
| 数据包 / 资源包 / 整合包 | data pack / resource pack / modpack | |
| 原版 / 原生 | vanilla / native | These are different concepts |
| 标签 | tag | Clarify registry tag versus NBT |
| 创造标签页 / 生物蛋 | creative tab / spawn egg | |
| 状态效果 / 附魔 / 药水 | status effect / enchantment / potion | |
| 服务端 / 客户端 / 主线程 | server / client / main thread | |
| 脚本类型 | Script Type | `STARTUP`, `SERVER`, `CLIENT`, `TEST` |
| 事件组 / 事件对象 / 绑定 | Event Group / Event Object / Binding | Follow [CONTEXT](../CONTEXT.md) |
| 插件钩子 / 扩展点 / 贡献者 | Plugin Hook / Extension Point / Contributor | These are not synonyms |
| 注册表 / 冻结 | registry / freeze | |
| 重载 / 热重载 | reload / hot reload | |
| 适配器 / 包装器 | adapter / wrapper | |
| 沙盒 / 工作区 | sandbox / workspace | |
| 补全 / 声明文件 | code completion / declaration file | `.d.ts` or `.pyi` |
| 契约 / 不变量 / 能力 | contract / invariant / capability | |
| 冒烟测试 / 回归测试 / 门禁 | smoke test / regression test / gate | |
| 转译器 / 源码映射 / 擦除 | transpiler / source map / erasure | |
| 实时绑定 / 循环依赖 | live binding / circular dependency | |
| 幂等 / 默认 / 内置 | idempotent / default / built-in | |
| 本体内置 | built into NekoJS | Not a literal translation of 本体 |

## Context-dependent terms

| Term | Context | English |
|---|---|---|
| 降级 | Compiler transforms syntax | lower / lowering |
| 降级 | Reduced platform behavior | degrade / degradation |
| 解析 | Source to AST | parse |
| 解析 | ID or module lookup | resolve |
| 生成 | Files and declarations | generate |
| 生成 | Entities | spawn |
| 覆盖 | Inherited behavior | override |
| 覆盖 | Stored content | overwrite |
| 覆盖 | Tests or feature surface | coverage |
| 收敛 | Restricting a surface | narrow |
| 收敛 | Combining implementations | consolidate |
| 产物 | Probe declarations | output |
| 产物 | Compiled jars | build artifact |
| 分发 | Event callbacks | dispatch |
| 分发 | Script packs | distribution |

## Python examples

Examples using NekoJS Bindings include `from nekojs import *` or unaliased named imports. Explain that `/nekojs probe python` generates the editor stubs and the transpiler strips this special import. Plain `/nekojs probe` generates TypeScript only. Both languages must carry these same conditions.
