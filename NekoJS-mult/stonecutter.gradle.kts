// stonecutter 控制器脚本（保留文件名；build script 不得占用此名）。
// active = 直编分支共享 src 的节点；其余节点（含 fabric）走 stonecutterGenerate 预处理副本。
plugins {
    id("dev.kikugie.stonecutter")
    // fabric 节点的 loom-back-compat 从控制器脚本读取 Loom 插件版本（它按 MC 版本挑变体，
    // 自己不硬编码 Loom 版本）——apply false，只声明坐标
    id("net.fabricmc.fabric-loom") version "1.17.20" apply false
}

stonecutter active "26.1.2"

// stonecutter 默认只认 java/kt/cfg/json5/yaml/... 这些扩展名（jar 内 DefaultsKt）：
// 本项目还要处理 nekojs.mixins.json（json5 语义）与 neoforge.mods.toml（`#` 注释，同 cfg），
// 用 handlers.inherit 把已有处理器套到新扩展名上。
stonecutter handlers {
    inherit("json5", "json")
    inherit("cfg", "toml")
}

// 版本间的**纯名义改名**交给 replacements，共享 src 里只存 26.x 一种形态。
// 分两组，按风险给不同的默认开关（标识符带 `!` = 默认启用）：
//
//   !mc_ids       —— 全局默认启用。**入选门槛：该 token 在某一侧完全不出现**
//                    （实测：26.x 侧 GuiGraphicsExtractor 44 次 / 裸 GuiGraphics 0 次，
//                    1.21.1 侧反之 42 / 0；ResourceLocation↔Identifier 同理），
//                    所以全局替换不可能误伤。这批规则让文件不必为改名写守卫，
//                    是可读性的主要来源（守卫块 991 → 764）。
//   mc_legacy_api —— **默认关闭**，只在已证明"归一化后逐字节相同"的 masters 里
//                    用 `//~ mc_legacy_api` 局部启用。收纳两类不安全规则：
//                    ① 调用形状不同（`sendSystemMessage` 与 `displayClientMessage`
//                       参数个数不一样，实测 PlayerExtension 盲改编译失败）；
//                    ② **同一侧两种写法并存**——26.x 里 `.identifier()` 用 28 次、
//                       但 TagKey/ResourceKey 上仍是 `.location()`（2 处：
//                       NeoForgeCatalogPlatformProvider / RecipeEventJS）。把它放进
//                       全局组会把那 2 处改坏（实测 26.2.0 编译失败）——这正是主仓
//                       gen-1211 规则必须配 allowlist 的原因。
//
// 方向语义：条件为真时 source→target，为假时自动反向；regex 不能自动反向，正/反两条都写。
// 一律用 `\b` 词边界——纯字符串替换会误伤 NekoHostIdentifier 之类的复合标识符。
//
// 已知副作用（可接受）：字符串字面量（8 处）与注释（17 处）里的 `Identifier` 也会被改名。
// 前者只影响日志/异常文本里的类名措辞（改后恰好是该版本的真实类名，反而更准），
// 后者是文档措辞。字节码等价性由 tools/verify_bytecode.py 把关。
// `!mc_ids` 的 token 对必须写成**字面** replace 调用：实测把它们改成
// `rules.forEach { replace(...) }` 从文件循环生成后，整组规则静默失效（编译不报错、
// 替换不发生）——stonecutter 的 replacements DSL 要在配置期就地登记。
// 这份清单与 tools/merge_pairs.py 的 NORMALIZE 是同一套规则（前者构建期还原、后者合并期
// 归一化），改规则要同时改两处；guardLint 无法自动核对（见下），靠这条注释与 code review。
stonecutter parameters {
    // 加载器常量：守卫的加载器轴，如 `//? if neoforge { ... //?}`。
    // parameters 块按节点懒求值，值取自各节点 gradle.properties 的 deps.loader。
    constants.match(node.project.property("deps.loader") as String, "neoforge", "fabric", "forge")
    replacements {
        regex(current.parsed >= "26", "!mc_ids") {
            // 方向：第一条 pair 必须是「1.21.1 形态 -> 26.x 形态」（条件为真=26.x 走正向、
            // 为假=1.21.1 走反向）。写反了两侧都不报错但规则静默失效（踩过一次）。
            replace("""\bResourceLocation\b""" to "Identifier", """\bIdentifier\b""" to "ResourceLocation")
            replace("""\bGuiGraphics\b""" to "GuiGraphicsExtractor", """\bGuiGraphicsExtractor\b""" to "GuiGraphics")
            replace("""\bdrawCenteredString\b""" to "centeredText", """\bcenteredText\b""" to "drawCenteredString")
        }

        regex(current.parsed >= "26", "mc_legacy_api") {
            replace("""\.location\(\)""" to ".identifier()", """\.identifier\(\)""" to ".location()")
            replace(
                """\.displayClientMessage\(""" to ".sendSystemMessage(",
                """\.sendSystemMessage\(""" to ".displayClientMessage(",
            )
            replace(
                """\.listRegistries\(\)""" to ".listRegistryKeys()",
                """\.listRegistryKeys\(\)""" to ".listRegistries()",
            )
        }
    }
}

// ---- 自研守卫体检任务（长期护栏）--------------------------------------------------
// 迁移期踩到的坑都是"守卫写坏"的具体形态；把它们固化成 lint，避免重犯：
//   1. 守卫必须配对（`//? if` 数 == 闭合数）——手工编辑最容易破坏的不变量；
//   2. 守卫不能落在 Java 文本块（"""）内——标记会被当字符串内容，stonecutter 报
//      Unmatched scope closer；
//   3. 守卫分支首行不能以 `/*` 开头——与"分支被禁用"的磁盘表示歧义，真 javadoc 会被
//      当成禁用包装剥掉。
// 路线图 P0 扩展（ADR-0007 / ADR-0008，docs/MIGRATION-ROADMAP.md）：
//   4. 密度阈值：文件级 `//? if` ≤ 20 硬限；超限须有 `// guard-exempt(20): 理由`
//      豁免标记（纯 Java 注释，不用 `//?` 前缀——那是 stonecutter 指令语法），
//      豁免清单每次运行输出；
//   5. 连续守卫段 > 8 行软告警——"方法级 ≤ 5"的代理指标（免脆弱的大括号追踪）；
//   6. 模块边界（ADR-0007）：common-api 零 MC/Loader/Graal import、common 零
//      MC/Loader import——当前基线为零，新增即硬失败；
//   7. wrapper 层零 loader import（ADR-0004 目标，hardFail 已于票 07 翻转）。
//      例外：整文件 loader 守卫（`//? if neoforge/fabric {` 包住全文件）的 wrapper 文件
//      是显式平台面——其 loader import 在对侧编译单元不存在，不计违规、仅 informational
//      列出（配方簇 / 能力系统 / 网络脚本通道等 fabric 移植件落地时逐个去掉守卫）。
// （规则一致性不用 lint：`!mc_ids` 的 token 对是字面 replace 调用（循环生成会静默失效，
//   见 replacements 块注释），与已删除的合并工具 NORMALIZE 是同一套规则。）
val guardLint = tasks.register("guardLint") {
    group = "verification"
    description = "Lints stonecutter guards (pairing, hazard shapes, density) and module boundaries."

    val sources = fileTree("src") { include("**/*.java") }
    val commonApiSources = fileTree("common-api/src/main/java") { include("**/*.java") }
    val commonSources = fileTree("common/src/main/java") { include("**/*.java") }
    inputs.files(sources, commonApiSources, commonSources)

    val densityLimit = 20
    val runLimit = 8
    val exemptMarker = Regex("""^\s*//\s*guard-exempt\((\d+)\):\s*(.+)""")
    val wrapperLoaderImportHardFail = true // 票 07 翻转：wrapper 层未守卫文件零 loader import
    val loaderImport = Regex("""^\s*import\s+(net\.neoforged|net\.fabricmc|net\.minecraftforge)\b.*""")
    val mcLoaderImport = Regex("""^\s*import\s+(net\.minecraft|net\.neoforged|net\.fabricmc|net\.minecraftforge)\b.*""")
    val graalImport = Regex("""^\s*import\s+org\.graalvm\b.*""")
    val wrapperDir = "src/main/java/com/tkisor/nekojs/wrapper/"

    doLast {
        val problems = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val platformFacing = mutableListOf<String>()
        val exemptions = mutableListOf<String>()
        var guards = 0

        fun rel(f: File) = f.relativeTo(project.projectDir).invariantSeparatorsPath

        // 最外层守卫 = 文件里第一个 `//? if` 行（嵌套守卫必在内层）；条件含 loader
        // token 即"最外层 loader 守卫"——loader import 随外层分支在对侧编译单元整体消失。
        val outerLoaderGuard = Regex("""^//\? if .*\b(neoforge|fabric)\b.*\{$""")

        sources.forEach { source ->
            val lines = source.readLines()
            var opens = 0
            var closes = 0
            var inTextBlock = false
            var run = 0
            var maxRun = 0
            var loaderImports = 0
            val path = rel(source)
            val inWrapper = path.startsWith(wrapperDir)
            // 整文件 loader 守卫（首行守卫 + 尾行收尾）= 显式平台面，不计入 wrapper 零
            // loader import 目标（对侧编译单元无这些 import）；未守卫文件才受 hardFail 约束。
            val platformFacingWrapper = inWrapper &&
                lines.firstOrNull { it.trim().startsWith("//? if ") }
                    ?.let { outerLoaderGuard.matches(it.trim()) } == true
            lines.forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.startsWith("//? if ")) {
                    opens++
                    guards++
                    if (inTextBlock) {
                        problems += "${source.name}:${index + 1} 守卫落在文本块内（stonecutter 会报 Unmatched scope closer）"
                    }
                    val next = lines.getOrNull(index + 1)?.trim().orEmpty()
                    if (next.startsWith("/*") && !next.startsWith("/**/")) {
                        // 注释态的合法形态是 `/*` 紧贴分支首行内容；单独一行 `/*` 说明包了 javadoc
                        if (next == "/*" || next.startsWith("/**")) {
                            problems += "${source.name}:${index + 2} 守卫分支首行以块注释开头（真 javadoc 会被当禁用包装剥掉）"
                        }
                    }
                }
                // 连续守卫段：`//? if` / `//?}` / `//?} else {` / `//? else`（含 `*/` 前缀形态）
                val normalized = line.removePrefix("*/").trim()
                val isGuardLine = normalized.startsWith("//? if ") || normalized == "//?}" ||
                    normalized.startsWith("//?} else") || normalized.startsWith("//? else")
                run = if (isGuardLine) run + 1 else 0
                if (run > maxRun) maxRun = run
                // 闭合只算真正的收尾（`//?}` / `*///?}`）；`//?} else {` 这类是**续接**，
                // 记成闭合会把双分支守卫误判为"未配对"。
                if (normalized == "//?}") closes++
                if (inWrapper && loaderImport.matches(line)) loaderImports++
                // 文本块起止（同一行成对出现时不翻转）
                val quotes = Regex("\"\"\"").findAll(raw).count()
                if (quotes % 2 == 1) inTextBlock = !inTextBlock
            }
            if (opens != closes) {
                problems += "${source.name}: 守卫未配对（if=$opens, close=$closes）"
            }
            if (opens > densityLimit) {
                val reason = lines.firstNotNullOfOrNull { exemptMarker.find(it)?.groupValues?.get(2)?.trim() }
                if (reason == null) {
                    problems += "$path: 守卫 $opens 条 > $densityLimit（治理，或加豁免标记 `// guard-exempt(20): 理由`，ADR-0008）"
                } else {
                    exemptions += "$path: $opens 条 — $reason"
                }
            }
            if (maxRun > runLimit) {
                warnings += "$path: 连续守卫段 $maxRun 行 > $runLimit（方法级密度代理，考虑抽 facade 或拆分，ADR-0008）"
            }
            if (inWrapper && loaderImports > 0 && !platformFacingWrapper) {
                val msg = "$path: loader import $loaderImports 处（ADR-0004 目标为零）"
                if (wrapperLoaderImportHardFail) problems += msg else warnings += msg
            } else if (platformFacingWrapper && loaderImports > 0) {
                platformFacing += "$path: loader import $loaderImports 处（整文件平台面，informational）"
            }
        }

        // 模块边界（ADR-0007 四层判据）：当前基线均为零违规，新增即失败
        commonApiSources.forEach { f ->
            f.readLines().forEach { line ->
                val t = line.trim()
                if (mcLoaderImport.matches(t) || graalImport.matches(t)) {
                    problems += "${rel(f)}: 违反 L1 边界（common-api 零 MC/Loader/Graal import，ADR-0007）: $t"
                }
            }
        }
        commonSources.forEach { f ->
            f.readLines().forEach { line ->
                val t = line.trim()
                if (mcLoaderImport.matches(t)) {
                    problems += "${rel(f)}: 违反 L2 边界（common 零 MC/Loader import，ADR-0007）: $t"
                }
            }
        }

        logger.lifecycle("guardLint: 守卫块 $guards，扫描 ${sources.files.size} 个文件；超限豁免 ${exemptions.size} 个；警告 ${warnings.size} 条")
        exemptions.forEach { logger.lifecycle("  [exempt] $it") }
        warnings.forEach { logger.warn("guardLint: $it") }
        platformFacing.forEach { logger.lifecycle("guardLint: [platform-facing] $it") }
        if (problems.isNotEmpty()) {
            throw GradleException("guardLint 发现 ${problems.size} 个问题：\n" + problems.joinToString("\n") { "  - $it" })
        }
    }
}

// ---- 门禁聚合：guard lint + 全部节点 check -----------------------------------------
// src-common 目录已折入共享版本树（loader 轴由 `//? if <loader>` 守卫表达，原目录墙
// 机制随之退役）；共享树的跨加载器中立性由守卫与 code review 把关。
tasks.register("sandboxCheck") {
    group = "verification"
    description = "Gate: guard lint + every node's check (compile/tests/artifact verify)."
    dependsOn(guardLint)
    // 只挂**节点**项目的 check：分支容器项目本身不套 java 插件、没有 check 任务
    dependsOn(
        subprojects
            .filter { it.plugins.hasPlugin("java") }
            .map { "${it.path}:check" }
    )
}
