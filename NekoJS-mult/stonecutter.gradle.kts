// stonecutter 控制器脚本（保留文件名；build script 不得占用此名）。
// active = 直编分支共享 src 的节点；其余节点（含 fabric）走 stonecutterGenerate 预处理副本。
plugins {
    id("dev.kikugie.stonecutter")
    // fabric 节点的 loom-back-compat 从控制器脚本读取 Loom 插件版本（它按 MC 版本挑变体，
    // 自己不硬编码 Loom 版本）——apply false，只声明坐标
    id("net.fabricmc.fabric-loom") version "1.17.20" apply false
}

// 切版本用 `gradlew switchVersion -Pnode=<节点>`；勿手改本行、勿在本文件做含本行的全局替换
stonecutter active "26.1.2"
// stonecutter 默认只认 java/kt/cfg/json5/yaml/... 这些扩展名（jar 内 DefaultsKt）：
// 本项目还要处理 nekojs.mixins.json（json5 语义）与 neoforge.mods.toml（`#` 注释，同 cfg），
// 用 handlers.inherit 把已有处理器套到新扩展名上。
stonecutter handlers {
    inherit("json5", "json")
    inherit("cfg", "toml")
}

// 版本间的**纯改名**由 replacements 处理，共享 src 里只写 26.x 一种形态。规则分两组，
// 默认开关不同（标识符带 `!` = 默认启用）：
//
//   !mc_ids       —— 全局默认启用。入选门槛是「该 token 在某一侧完全不出现」：例如
//                    26.x 只用 GuiGraphicsExtractor、1.21.1 只用 GuiGraphics，两侧不交叉，
//                    所以全局替换不可能误伤。这组规则让文件不必为改名写守卫。
//   mc_legacy_api —— 默认关闭，只在验证过的单个文件里用 `//~ mc_legacy_api` 局部启用。
//                    收纳两类不安全规则：
//                    ① 调用形状不同——`sendSystemMessage` 与 `displayClientMessage` 的
//                       参数个数不一样，盲替换会编译失败；
//                    ② 同一侧两种写法并存——26.x 上大部分地方是 `.identifier()`，但
//                       TagKey / ResourceKey 上仍是 `.location()`，全局替换会改坏后者。
//
// 方向语义：条件为真时 source→target，为假时自动反向。regex 不能自动反向，正反两条都要写。
// 一律用 `\b` 词边界，否则会误伤 NekoHostIdentifier 之类的复合标识符。
//
// 可接受的副作用：字符串字面量与注释里的 `Identifier` 也会被改名。前者只影响日志/异常
// 文本里的类名措辞（改后恰好是该版本的真实类名），后者是文档措辞。
//
// 约束：`!mc_ids` 的 token 对必须写成**字面** replace 调用。改成 `rules.forEach { replace(...) }`
// 从列表循环生成，整组规则会静默失效——编译不报错、替换也不发生，因为 stonecutter 的
// replacements DSL 要求在配置期就地登记。
stonecutter parameters {
    // 加载器常量：守卫的加载器轴，如 `//? if neoforge { ... //?}`。
    // parameters 块按节点懒求值，值取自各节点 gradle.properties 的 deps.platform。
    // 注意 deps.platform（平台 token）与 deps.loader_version（loader 版本号）是两个键——
    // 曾经共用一个键，导致 fabric 节点上三个常量全部不命中，见下面 guardLint 规则 8。
    constants.match(node.project.property("deps.platform") as String, "neoforge", "fabric")
    replacements {
        regex(current.parsed >= "26", "!mc_ids") {
            // 方向：第一条 pair 必须是「1.21.1 形态 -> 26.x 形态」（条件为真=26.x 走正向、
            // 为假=1.21.1 走反向）。写反了两侧都不报错，规则只是静默失效。
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

// ---- 守卫体检任务（长期护栏）----------------------------------------------------
// 八条规则，每条对应一种"守卫写坏了但编译器不报错"的形态：
//   1. 守卫必须配对（`//? if` 数 == 闭合数）——手工编辑最容易破坏的不变量；
//   2. 守卫不能落在 Java 文本块（"""）内——标记会被当字符串内容，stonecutter 报
//      Unmatched scope closer；
//   3. 守卫分支首行不能以 `/*` 开头——与"分支被禁用"的磁盘表示歧义，真 javadoc 会被
//      当成禁用包装剥掉；
//   4. 密度：单文件 `//? if` ≤ 20，超限须写 `// guard-exempt(20): 理由` 豁免标记
//      （纯 Java 注释，不用 `//?` 前缀——那是 stonecutter 指令语法），豁免清单每次输出；
//   5. 连续守卫段 > 8 行软告警——"方法级密度"的代理指标，避免脆弱的大括号追踪；
//   6. 模块边界（ADR-0007 2026-08-30 修订）：common 里 com.tkisor.nekojs.api.* 与其它
//      common 包一律**零 MC/Loader import**；Graal 已不再是禁止项——common（含 api.*）
//      允许直接依赖 GraalJS，不为隔离 Graal 抽 DTO / adapter 或另立 API jar。当前基线为
//      零 MC/Loader 违规，新增即硬失败；
//   7. wrapper 层零 loader import（ADR-0004）。例外一：整文件 loader 守卫
//      （`//? if neoforge/fabric {` 包住全文件）的 wrapper 文件是显式平台面，其 loader
//      import 在对侧编译单元根本不存在，不计违规、只做提示性列出；例外二：行内 loader
//      守卫（如配方面的流体分支 `//? if neoforge`）内的 loader import 同理在对侧求值时
//      整段消失——按守卫深度豁免，只有守卫外的 loader import 才是漏网的双面污染；
//   8. 恒假常量：守卫条件引用的 loader 常量必须至少在一个节点取值为真。平台事实源是
//      versions/*/gradle.properties 的 deps.platform（与 constants.match 同源）。否则
//      该分支永不激活，而这既不报编译错也不报守卫错，只能在这里拦。
val guardLint = tasks.register("guardLint") {
    group = "verification"
    description = "Lints stonecutter guards (pairing, hazard shapes, density) and module boundaries."

    val sources = fileTree("src") { include("**/*.java") }
    // 契约包按包前缀取材：api.* 与引擎实现同住 common，边界不再由模块划分承载（ADR-0007）
    val apiSources = fileTree("common/src/main/java/com/tkisor/nekojs/api") { include("**/*.java") }
    val commonSources = fileTree("common/src/main/java") { include("**/*.java") }
    val nodeProperties = fileTree("versions") { include("*/gradle.properties") }
    inputs.files(sources, apiSources, commonSources, nodeProperties)

    val densityLimit = 20
    val runLimit = 8
    val exemptMarker = Regex("""^\s*//\s*guard-exempt\((\d+)\):\s*(.+)""")
    val wrapperLoaderImportHardFail = true // wrapper 层未守卫文件零 loader import（规则 7）
    // 探测面刻意比当前支持的加载器宽：net.minecraftforge 现在不该出现，真出现了要报出来
    val loaderImport = Regex("""^\s*import\s+(net\.neoforged|net\.fabricmc|net\.minecraftforge)\b.*""")
    val mcLoaderImport = Regex("""^\s*import\s+(net\.minecraft|net\.neoforged|net\.fabricmc|net\.minecraftforge)\b.*""")
    // 历史 Graal 禁令已按 ADR-0007 撤销：common（含 api.*）允许使用 GraalJS。这里不再提供
    // graalImport 硬失败规则，避免出现"规则还在但早已空转"的假门禁；MC/loader 隔离由
    // mcLoaderImport 继续强制（L1/L2 + checkCommonIsolation + ModulePipelineIsolationTest）。
    val wrapperDir = "src/main/java/com/tkisor/nekojs/wrapper/"

    doLast {
        val problems = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val platformFacing = mutableListOf<String>()
        val exemptions = mutableListOf<String>()
        var guards = 0

        // 规则 8 的事实源：节点平台集合（deps.platform，与 constants.match 同源）。
        val platformLine = Regex("""^deps\.platform=(\S+)$""")
        val nodePlatforms = nodeProperties.files
            .flatMap { f -> f.readLines().mapNotNull { platformLine.matchEntire(it.trim())?.groupValues?.get(1) } }
            .toSet()
        val usedConstants = sortedSetOf<String>()
        val constantToken = Regex("""\b(neoforge|fabric|forge)\b""")

        fun rel(f: File) = f.relativeTo(project.projectDir).invariantSeparatorsPath

        // 最外层守卫 = 文件里第一个 `//? if` 行（嵌套守卫必在内层）；条件含 loader
        // token 即"最外层 loader 守卫"——loader import 随外层分支在对侧编译单元整体消失。
        val outerLoaderGuard = Regex("""^//\? if .*\b(neoforge|fabric)\b.*\{$""")

        sources.forEach { source ->
            val lines = source.readLines()
            var opens = 0
            var closes = 0
            var guardDepth = 0
            var inTextBlock = false
            var run = 0
            var maxRun = 0
            var loaderImports = 0
            val path = rel(source)
            val inWrapper = path.startsWith(wrapperDir)
            // 整文件 loader 守卫 = 显式平台面，不计入 wrapper 零 loader import 目标
            //（对侧编译单元无这些 import）。两个条件都要满足：首个 `//? if`（最外层）
            // 条件含 loader token，且最后一个非空行是该守卫的收尾 `//?}`——只看首行会把
            //「方法级 loader 守卫 + 其余裸奔」的文件误判成平台面（hardFail 被静默绕过）。
            val lastSignificant = lines.lastOrNull { it.isNotBlank() }?.trim()
            val platformFacingWrapper = inWrapper &&
                lines.firstOrNull { it.trim().startsWith("//? if ") }
                    ?.let { outerLoaderGuard.matches(it.trim()) } == true &&
                (lastSignificant?.removePrefix("*/")?.trim() == "//?}")
            lines.forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.startsWith("//? if ")) {
                    opens++
                    guards++
                    guardDepth++
                    // 规则 8：收集条件里的 loader 常量引用（如 `//? if neoforge && >=26 {`）
                    constantToken.findAll(line).forEach { usedConstants += it.value }
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
                if (normalized == "//?}") {
                    closes++
                    if (guardDepth > 0) guardDepth--
                }
                // 行内守卫内的 loader import 在对侧求值时整段消失，与整文件守卫同理豁免；
                // 只有守卫外（depth=0）的 loader import 才是漏网的双面污染。
                if (inWrapper && guardDepth == 0 && loaderImport.matches(line)) loaderImports++
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

        // 规则 8：恒假常量——被守卫引用、但没有任何节点平台能取真的常量 = 永不激活分支
        //（写坏不报编译错，只能在这里拦）
        usedConstants.forEach { c ->
            if (c !in nodePlatforms) {
                problems += "守卫常量 `$c` 无任何节点取值为真（节点平台集合：$nodePlatforms）——恒假分支（规则 8）"
            }
        }

        // 模块边界（ADR-0007 修订版）：api.* 与 common 其它包一律零 MC/Loader import。
        // Graal 不是禁止项（common 有意拥有 GraalJS 引擎），因此这里只拦 MC/loader。
        apiSources.forEach { f ->
            f.readLines().forEach { line ->
                val t = line.trim()
                if (mcLoaderImport.matches(t)) {
                    problems += "${rel(f)}: 违反 L1 边界（com.tkisor.nekojs.api.* 零 MC/Loader import，ADR-0007）: $t"
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
// 共享树对各加载器保持中立，靠守卫和 code review 把关——没有目录级隔离机制。
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

// ---- switchVersion：切换 active 节点 -----------------------------------------------
// 用法：gradlew switchVersion -Pnode=26.2.0。改控制器脚本的 active 行，执行后需在 IDE
// 重新 Gradle sync 才生效（本任务在配置完成后执行，改写对本次构建无影响）。
// 可用节点 = versions/ 下的目录。
tasks.register("switchVersion") {
    group = "nekojs"
    description = "Switches the stonecutter active node: gradlew switchVersion -Pnode=<node>."
    outputs.upToDateWhen { false }   // 每次执行都改写控制器行
    doLast {
        val versionsDir = rootDir.resolve("versions")
        val available = versionsDir.listFiles { f -> f.isDirectory }?.map { it.name }?.sorted().orEmpty()
        val target = (findProperty("node") as String?)
            ?: throw GradleException("用法：gradlew switchVersion -Pnode=<节点名>（可用：$available）")
        if (target !in available) {
            throw GradleException("未知节点 $target——可用：$available")
        }
        val controller = rootDir.resolve("stonecutter.gradle.kts")
        // 按行前缀定位声明；不写含 `stonecutter active "26.1.2-fabric"` 字样的正则——正则字面量与
        // 第 10 行声明同形，对本文件做全局替换时会连坐改坏
        val text = controller.readText()
        val activeLine = text.lineSequence().firstOrNull { it.startsWith("stonecutter active ") }
            ?: throw GradleException("stonecutter.gradle.kts 里找不到 `stonecutter active \"…\"` 声明")
        val at = text.indexOf(activeLine)
        controller.writeText(text.replaceRange(at, at + activeLine.length, "stonecutter active \"$target\""))
        println("switchVersion: active 节点已切换为 $target（IDE 重新 Gradle sync 后生效）")
    }
}
