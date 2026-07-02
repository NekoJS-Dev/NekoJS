package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import com.tkisor.nekojs.api.probe.ProbeGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeConverter;
import com.tkisor.nekojs.script.ScriptType;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * 探针编排器：协调所有生成器，管理完整的探针生成流程。
 */
public final class ProbeOrchestrator implements ProbeGenerator {
    private final TypeAliasRegistry aliasRegistry = new TypeAliasRegistry();
    private final TypeConverter typeConverter = new TypeConverter(aliasRegistry);
    private final ClassDeclGenerator classDeclGenerator = new ClassDeclGenerator(typeConverter);
    private final AdapterAliasGenerator adapterAliasGenerator = new AdapterAliasGenerator(aliasRegistry);
    private final IndexFileGenerator indexFileGenerator = new IndexFileGenerator(classDeclGenerator, typeConverter, adapterAliasGenerator);
    private final EventDeclarationGenerator eventGenerator = new EventDeclarationGenerator(typeConverter, adapterAliasGenerator);
    private final BindingDeclarationGenerator bindingGenerator = new BindingDeclarationGenerator(typeConverter);
    private final RecipeEventDeclarationGenerator recipeEventGenerator = new RecipeEventDeclarationGenerator(aliasRegistry);
    private final SpecialTypeGenerator specialTypeGenerator = new SpecialTypeGenerator();

    {
        // RecipeEventJS.recipes getter 由 RecipeEventDeclarationGenerator 提供，
        // 让它返回 DocumentedRecipes（来自 @side-only/server/events/recipes）
        try {
            Class<?> recipeEventClass = Class.forName("com.tkisor.nekojs.wrapper.event.server.RecipeEventJS");
            classDeclGenerator.overrideGetter(
                    recipeEventClass,
                    "recipes",
                    "DocumentedRecipes",
                    "import { DocumentedRecipes } from \"@side-only/server/events/recipes\";"
            );
        } catch (ClassNotFoundException ignored) {
        }
    }

    @Override
    public String name() {
        return "NekoJS Builtin Probe";
    }

    @Override
    public GenerateResult generate(NekoScriptCatalogSnapshot snapshot, Path outputDir) {
        long start = System.currentTimeMillis();
        int filesGenerated = 0;

        try {
            // 清理旧文件
            if (Files.exists(outputDir)) {
                Files.walk(outputDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
            Files.createDirectories(outputDir);

            // 1. 收集需要生成声明的类
            Set<String> classesToGenerate = collectClasses(snapshot);

            // 强制纳入相关前缀内的适配器目标类，确保其包模块与输入别名（$Foo_）一定生成。
            // 跳过不在相关性前缀内的目标（如 gson 的 JsonObject）：它们的依赖图无法被干净探测，
            // 强行生成会引入悬空类型引用；这类目标若被引用，按既有行为保持原样。
            for (AdapterCatalogEntry adapter : snapshot.adapters()) {
                String name = adapter.targetType().getName();
                if (isRelevantClass(name)) {
                    classesToGenerate.add(name);
                }
            }

            // 准备适配器输入别名：仅处理会被实际生成的目标，填充 TypeAliasRegistry（放宽引用该类型的
            // 方法参数）+ 别名表（就近发声明）。必须在 pregenerateDeclarations 之前，因为参数渲染依赖已注册的别名
            adapterAliasGenerator.prepare(snapshot.adapters(), classesToGenerate);

            // 别名引用的跨包 host 类型（如 NekoId、Item）也需生成声明，否则别名里的 $NekoId 等会悬空
            for (String host : adapterAliasGenerator.hostImports()) {
                if (isRelevantClass(host)) {
                    classesToGenerate.add(host);
                }
            }

            NekoJS.LOGGER.info("Probe: {} classes to generate", classesToGenerate.size());

            // 2. 构建包树
            PackageTree tree = new PackageTree();
            for (String fqn : classesToGenerate) {
                tree.addClass(fqn);
            }

            // 3. 并行预生成所有类声明
            pregenerateDeclarations(classesToGenerate);

            // 4. 生成 @package Java 类型声明
            filesGenerated += generatePackageDeclarations(tree, outputDir);

            // 4. 生成事件声明
            filesGenerated += generateEventDeclarations(snapshot, outputDir);

            // 5. 生成 recipe 事件声明（event.recipes.<namespace>.<type>(...)）
            filesGenerated += generateRecipeEventDeclarations(snapshot, outputDir);

            // 6. 生成绑定声明
            filesGenerated += generateBindingDeclarations(snapshot, outputDir);

            // 6. 生成 @side-only/{side}/index.d.ts（重新导出 events 和 bindings）
            filesGenerated += generateSideRootIndexes(outputDir);

            // 7. 生成 @special 注册表字面量类型
            filesGenerated += generateSpecialTypes(snapshot, outputDir);

            // 8. 生成 @manual 手动声明（node:xxx 模块、helper 类型、插件模块）
            filesGenerated += generateManualDeclarations(snapshot, outputDir);

            // 9. 生成 .github/agents/ 模板文件
            Path agentsDir = outputDir.getParent().resolve(".github").resolve("agents");
            AgentTemplateGenerator.generate(agentsDir);

            long duration = System.currentTimeMillis() - start;
            NekoJS.LOGGER.info("Probe generated: {} files in {}ms", filesGenerated, duration);
            return GenerateResult.success(filesGenerated, duration);

        } catch (Exception e) {
            NekoJS.LOGGER.error("Probe generation failed", e);
            return GenerateResult.failure(e.getMessage());
        } finally {
            // 清理生成过程中积累的缓存，释放内存
            indexFileGenerator.clearCaches();
            typeConverter.clearCaches();
        }
    }

    /**
     * 收集需要生成声明的 Java 类。
     * 从多个来源发现类：
     * 1. 常用 Java 标准库类
     * 2. 事件类和绑定类型
     * 3. BFS 发现引用的类（每展开一个类 = 1 深度单位）
     */
    private Set<String> collectClasses(NekoScriptCatalogSnapshot snapshot) {
        Set<String> visited = new LinkedHashSet<>();
        java.util.Queue<Object[]> queue = new java.util.LinkedList<>();

        // 种子类：事件类型和绑定类型（depth 0）
        for (EventCatalogEntry event : snapshot.events()) {
            if (event.eventType() != null) queue.add(new Object[]{event.eventType(), 0});
            if (event.dispatchKeyType() != null) queue.add(new Object[]{event.dispatchKeyType(), 0});
        }
        for (BindingCatalogEntry binding : snapshot.bindings()) {
            if (binding.javaType() != null) queue.add(new Object[]{binding.javaType(), 0});
        }

        int maxDepth = 5;

        // 轻量 BFS：只收集类名，不做声明生成
        while (!queue.isEmpty()) {
            Object[] entry = queue.poll();
            Class<?> cls = (Class<?>) entry[0];
            int depth = (int) entry[1];

            if (depth > maxDepth) continue;
            if (cls == null || cls.isPrimitive() || cls == Object.class) continue;

            String name = cls.getName();
            if (visited.contains(name)) continue;
            if (!isRelevantClass(name)) continue;

            visited.add(name);

            int nextDepth = depth + 1;
            if (nextDepth > maxDepth) continue;

            if (cls.getSuperclass() != null) queue.add(new Object[]{cls.getSuperclass(), nextDepth});
            for (Class<?> iface : cls.getInterfaces()) queue.add(new Object[]{iface, nextDepth});

            for (java.lang.reflect.Constructor<?> ctor : cls.getDeclaredConstructors()) {
                if (java.lang.reflect.Modifier.isPublic(ctor.getModifiers())) {
                    for (java.lang.reflect.Type p : ctor.getGenericParameterTypes()) collectTypeToQueue(p, queue, nextDepth);
                }
            }
            for (java.lang.reflect.Method method : cls.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                    collectTypeToQueue(method.getGenericReturnType(), queue, nextDepth);
                    for (java.lang.reflect.Type p : method.getGenericParameterTypes()) collectTypeToQueue(p, queue, nextDepth);
                }
            }
            for (java.lang.reflect.Field field : cls.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isPublic(field.getModifiers())) collectTypeToQueue(field.getGenericType(), queue, nextDepth);
            }
        }

        return visited;
    }

    private void collectTypeToQueue(java.lang.reflect.Type type, java.util.Queue<Object[]> queue, int depth) {
        if (type instanceof Class<?> cls) {
            queue.add(new Object[]{cls, depth});
        } else if (type instanceof java.lang.reflect.ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?> rawCls) queue.add(new Object[]{rawCls, depth});
            for (java.lang.reflect.Type arg : pt.getActualTypeArguments()) collectTypeToQueue(arg, queue, depth);
        } else if (type instanceof java.lang.reflect.GenericArrayType gat) {
            collectTypeToQueue(gat.getGenericComponentType(), queue, depth);
        }
    }

    /**
     * 并行预生成所有类声明（重反射工作放到线程池）。
     */
    private void pregenerateDeclarations(Set<String> classNames) {
        int parallelism = Math.min(Runtime.getRuntime().availableProcessors(), 8);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(parallelism);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (String fqn : classNames) {
            futures.add(executor.submit(() -> {
                try { indexFileGenerator.pregenerateClass(Class.forName(fqn)); } catch (ClassNotFoundException ignored) {}
            }));
        }
        for (var f : futures) { try { f.get(); } catch (Exception ignored) {} }
        executor.shutdown();
    }

    /**
     * 添加常用 Java 标准库类。
     */
    private void addCommonJavaClasses(Set<String> classes) {
        String[] commonClasses = {
            // java.lang
            "java.lang.String",
            "java.lang.StringBuilder",
            "java.lang.StringBuffer",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Character",
            "java.lang.Number",
            "java.lang.Comparable",
            "java.lang.Iterable",
            "java.lang.Cloneable",
            "java.lang.AutoCloseable",
            "java.lang.Runnable",
            "java.lang.Thread",
            "java.lang.ThreadGroup",
            "java.lang.Class",
            "java.lang.ClassLoader",
            "java.lang.Enum",
            "java.lang.Exception",
            "java.lang.RuntimeException",
            "java.lang.Error",
            "java.lang.Throwable",
            "java.lang.Math",
            "java.lang.System",
            "java.lang.Process",
            "java.lang.ProcessBuilder",
            "java.lang.Module",
            "java.lang.Package",
            "java.lang.StackWalker",
            "java.lang.StackTraceElement",
            "java.lang.StringJoiner",
            "java.lang.System.Logger",
            "java.lang.System.Logger.Level",

            // java.lang.annotation
            "java.lang.annotation.Annotation",
            "java.lang.annotation.Retention",
            "java.lang.annotation.RetentionPolicy",
            "java.lang.annotation.Target",
            "java.lang.annotation.ElementType",

            // java.lang.invoke
            "java.lang.invoke.MethodHandle",
            "java.lang.invoke.MethodHandles",
            "java.lang.invoke.MethodType",
            "java.lang.invoke.VarHandle",

            // java.lang.reflect
            "java.lang.reflect.Field",
            "java.lang.reflect.Method",
            "java.lang.reflect.Constructor",
            "java.lang.reflect.Parameter",
            "java.lang.reflect.Modifier",
            "java.lang.reflect.Proxy",

            // java.util
            "java.util.Collection",
            "java.util.List",
            "java.util.ArrayList",
            "java.util.LinkedList",
            "java.util.Set",
            "java.util.HashSet",
            "java.util.TreeSet",
            "java.util.LinkedHashSet",
            "java.util.Map",
            "java.util.HashMap",
            "java.util.TreeMap",
            "java.util.LinkedHashMap",
            "java.util.EnumMap",
            "java.util.WeakHashMap",
            "java.util.IdentityHashMap",
            "java.util.Queue",
            "java.util.Deque",
            "java.util.ArrayDeque",
            "java.util.PriorityQueue",
            "java.util.Iterator",
            "java.util.ListIterator",
            "java.util.Spliterator",
            "java.util.Optional",
            "java.util.OptionalInt",
            "java.util.OptionalLong",
            "java.util.OptionalDouble",
            "java.util.Random",
            "java.util.RandomAccess",
            "java.util.UUID",
            "java.util.Date",
            "java.util.Calendar",
            "java.util.Locale",
            "java.util.TimeZone",
            "java.util.Currency",
            "java.util.Formatter",
            "java.util.Scanner",
            "java.util.StringTokenizer",
            "java.util.Properties",
            "java.util.BitSet",
            "java.util.Objects",
            "java.util.Arrays",
            "java.util.Collections",
            "java.util.Comparator",
            "java.util.Enumeration",
            "java.util.EventListener",
            "java.util.EventObject",
            "java.util.Observable",
            "java.util.Observer",
            "java.util.Timer",
            "java.util.TimerTask",
            "java.util.concurrent.Callable",
            "java.util.concurrent.CompletableFuture",
            "java.util.concurrent.CompletionStage",
            "java.util.concurrent.ConcurrentHashMap",
            "java.util.concurrent.ConcurrentMap",
            "java.util.concurrent.CountDownLatch",
            "java.util.concurrent.CyclicBarrier",
            "java.util.concurrent.Executor",
            "java.util.concurrent.ExecutorService",
            "java.util.concurrent.Executors",
            "java.util.concurrent.Future",
            "java.util.concurrent.ScheduledExecutorService",
            "java.util.concurrent.ScheduledFuture",
            "java.util.concurrent.Semaphore",
            "java.util.concurrent.ThreadFactory",
            "java.util.concurrent.TimeUnit",
            "java.util.concurrent.atomic.AtomicBoolean",
            "java.util.concurrent.atomic.AtomicInteger",
            "java.util.concurrent.atomic.AtomicLong",
            "java.util.concurrent.atomic.AtomicReference",
            "java.util.concurrent.locks.Lock",
            "java.util.concurrent.locks.ReentrantLock",
            "java.util.concurrent.locks.ReadWriteLock",
            "java.util.concurrent.locks.ReentrantReadWriteLock",
            "java.util.concurrent.locks.Condition",

            // java.util.function
            "java.util.function.BiConsumer",
            "java.util.function.BiFunction",
            "java.util.function.BinaryOperator",
            "java.util.function.Consumer",
            "java.util.function.Function",
            "java.util.function.Predicate",
            "java.util.function.Supplier",
            "java.util.function.UnaryOperator",
            "java.util.function.IntConsumer",
            "java.util.function.IntFunction",
            "java.util.function.IntPredicate",
            "java.util.function.IntSupplier",
            "java.util.function.LongConsumer",
            "java.util.function.LongFunction",
            "java.util.function.LongPredicate",
            "java.util.function.LongSupplier",
            "java.util.function.DoubleConsumer",
            "java.util.function.DoubleFunction",
            "java.util.function.DoublePredicate",
            "java.util.function.DoubleSupplier",
            "java.util.function.BiPredicate",
            "java.util.function.ToDoubleFunction",
            "java.util.function.ToIntFunction",
            "java.util.function.ToLongFunction",

            // java.util.stream
            "java.util.stream.Stream",
            "java.util.stream.IntStream",
            "java.util.stream.LongStream",
            "java.util.stream.DoubleStream",
            "java.util.stream.Collector",
            "java.util.stream.Collectors",

            // java.io
            "java.io.File",
            "java.io.InputStream",
            "java.io.OutputStream",
            "java.io.Reader",
            "java.io.Writer",
            "java.io.BufferedReader",
            "java.io.BufferedWriter",
            "java.io.ByteArrayInputStream",
            "java.io.ByteArrayOutputStream",
            "java.io.DataInput",
            "java.io.DataOutput",
            "java.io.DataInputStream",
            "java.io.DataOutputStream",
            "java.io.FileFilter",
            "java.io.FilenameFilter",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.io.FileReader",
            "java.io.FileWriter",
            "java.io.FilterInputStream",
            "java.io.FilterOutputStream",
            "java.io.Flushable",
            "java.io.IOException",
            "java.io.InputStreamReader",
            "java.io.ObjectInput",
            "java.io.ObjectOutput",
            "java.io.ObjectInputStream",
            "java.io.ObjectOutputStream",
            "java.io.OutputStreamWriter",
            "java.io.PrintStream",
            "java.io.PrintWriter",
            "java.io.RandomAccessFile",
            "java.io.Serializable",
            "java.io.Closeable",

            // java.nio
            "java.nio.ByteBuffer",
            "java.nio.CharBuffer",
            "java.nio.DoubleBuffer",
            "java.nio.FloatBuffer",
            "java.nio.IntBuffer",
            "java.nio.LongBuffer",
            "java.nio.ShortBuffer",
            "java.nio.ByteOrder",
            "java.nio.Buffer",
            "java.nio.MappedByteBuffer",
            "java.nio.channels.Channel",
            "java.nio.channels.ReadableByteChannel",
            "java.nio.channels.WritableByteChannel",
            "java.nio.channels.SeekableByteChannel",
            "java.nio.channels.FileChannel",
            "java.nio.charset.Charset",
            "java.nio.charset.CharsetDecoder",
            "java.nio.charset.CharsetEncoder",
            "java.nio.charset.StandardCharsets",

            // java.nio.file
            "java.nio.file.Path",
            "java.nio.file.Paths",
            "java.nio.file.Files",
            "java.nio.file.FileSystem",
            "java.nio.file.FileSystems",
            "java.nio.file.FileVisitor",
            "java.nio.file.FileVisitResult",
            "java.nio.file.WatchEvent",
            "java.nio.file.WatchKey",
            "java.nio.file.WatchService",
            "java.nio.file.attribute.BasicFileAttributes",
            "java.nio.file.attribute.FileAttribute",
            "java.nio.file.attribute.FileTime",
            "java.nio.file.AccessMode",
            "java.nio.file.CopyOption",
            "java.nio.file.DirectoryStream",
            "java.nio.file.FileStore",
            "java.nio.file.LinkOption",
            "java.nio.file.OpenOption",
            "java.nio.file.StandardCopyOption",
            "java.nio.file.StandardOpenOption",

            // java.math
            "java.math.BigDecimal",
            "java.math.BigInteger",
            "java.math.MathContext",
            "java.math.RoundingMode",

            // java.net
            "java.net.URI",
            "java.net.URL",
            "java.net.URLConnection",
            "java.net.HttpURLConnection",
            "java.net.InetAddress",
            "java.net.InetSocketAddress",
            "java.net.Proxy",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.net.CookieManager",
            "java.net.CookieHandler",
            "java.net.CookiePolicy",
            "java.net.URLClassLoader",
            "java.net.URLDecoder",
            "java.net.URLEncoder",

            // java.time
            "java.time.Duration",
            "java.time.Instant",
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.LocalTime",
            "java.time.Month",
            "java.time.Year",
            "java.time.YearMonth",
            "java.time.ZoneId",
            "java.time.ZoneOffset",
            "java.time.ZonedDateTime",
            "java.time.OffsetDateTime",
            "java.time.OffsetTime",
            "java.time.Period",
            "java.time.Clock",
            "java.time.DayOfWeek",
            "java.time.format.DateTimeFormatter",
            "java.time.format.DateTimeFormatterBuilder",
            "java.time.temporal.ChronoField",
            "java.time.temporal.ChronoUnit",
            "java.time.temporal.Temporal",
            "java.time.temporal.TemporalAccessor",
            "java.time.temporal.TemporalAmount",
            "java.time.temporal.TemporalField",
            "java.time.temporal.TemporalUnit",
            "java.time.temporal.ValueRange",

            // java.text
            "java.text.DateFormat",
            "java.text.SimpleDateFormat",
            "java.text.DecimalFormat",
            "java.text.MessageFormat",
            "java.text.NumberFormat",
            "java.text.ChoiceFormat",
            "java.text.CollationKey",
            "java.text.Collator",
            "java.text.BreakIterator",
            "java.text.CharacterIterator",
            "java.text.AttributedCharacterIterator",
            "java.text.AttributedString",
            "java.text.Format",
            "java.text.FieldPosition",
            "java.text.ParsePosition",

            // java.security
            "java.security.CodeSource",
            "java.security.Permission",
            "java.security.PermissionCollection",
            "java.security.Permissions",
            "java.security.Policy",
            "java.security.Principal",
            "java.security.PrivilegedAction",
            "java.security.PrivilegedExceptionAction",
            "java.security.ProtectionDomain",
            "java.security.SecureRandom",
            "java.security.cert.Certificate",
            "java.security.cert.X509Certificate",

            // java.sql
            "java.sql.Connection",
            "java.sql.DriverManager",
            "java.sql.PreparedStatement",
            "java.sql.ResultSet",
            "java.sql.SQLException",
            "java.sql.Statement",
            "java.sql.Types",

            // javax.script
            "javax.script.Bindings",
            "javax.script.Compilable",
            "javax.script.CompiledScript",
            "javax.script.Invocable",
            "javax.script.ScriptContext",
            "javax.script.ScriptEngine",
            "javax.script.ScriptEngineFactory",
            "javax.script.ScriptException",
            "javax.script.SimpleBindings",
            "javax.script.SimpleScriptContext"
        };

        for (String className : commonClasses) {
            try {
                Class<?> cls = Class.forName(className);
                classes.add(cls.getName());
            } catch (ClassNotFoundException e) {
                // 类不存在，跳过
            }
        }
    }

    private boolean isRelevantClass(String name) {
        return name.startsWith("java.") ||
               name.startsWith("net.minecraft.") ||
               name.startsWith("net.neoforged.") ||
               name.startsWith("com.tkisor.nekojs.");
    }

    private int generatePackageDeclarations(PackageTree tree, Path outputDir) throws IOException {
        Path packageDir = outputDir.resolve("@package");
        Files.createDirectories(packageDir);

        List<PackageTree.Node> nodes = tree.traversePackages();

        // 收集所有类的简单名（用于同名冲突检测）
        Set<String> allClassNames = new LinkedHashSet<>();
        for (PackageTree.Node node : nodes) {
            allClassNames.addAll(node.classes);
        }

        // 1. 批量创建所有目录
        for (PackageTree.Node node : nodes) {
            Files.createDirectories(packageDir.resolve(node.getPackagePath()));
        }

        // 2. 并行生成内容 + 写入文件
        List<PackageTree.Node> nodeList = new ArrayList<>(nodes);
        int parallelism = Math.min(Runtime.getRuntime().availableProcessors(), 8);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(parallelism);
        java.util.concurrent.CompletionService<Void> completion =
                new java.util.concurrent.ExecutorCompletionService<>(executor);

        int taskCount = 0;
        for (PackageTree.Node node : nodeList) {
            completion.submit(() -> {
                String packageName = node.getPackageName();
                List<String> subpackages = node.getSubPackageNames();
                String content = indexFileGenerator.generate(packageName, node.classes, subpackages, allClassNames);
                Files.writeString(packageDir.resolve(node.getPackagePath()).resolve("index.d.ts"), content);
                return null;
            });
            taskCount++;
        }

        // 等待所有任务完成
        for (int i = 0; i < taskCount; i++) {
            try {
                completion.take().get();
            } catch (java.util.concurrent.ExecutionException e) {
                NekoJS.LOGGER.error("Package generation task failed", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        executor.shutdown();

        // 3. 生成根 index.d.ts
        List<String> topPackages = new ArrayList<>();
        for (PackageTree.Node child : tree.getRoot().children.values()) {
            topPackages.add(child.name);
        }
        Files.writeString(packageDir.resolve("index.d.ts"), indexFileGenerator.generateRoot(topPackages));

        return taskCount + 1;
    }

    private int generateEventDeclarations(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        int count = 0;
        Path sideOnlyDir = outputDir.resolve("@side-only");

        for (ScriptType type : ScriptType.all()) {
            List<EventCatalogEntry> events = snapshot.events().stream()
                    .filter(e -> e.scriptType().test(type))
                    .toList();
            if (events.isEmpty()) continue;

            Path dir = sideOnlyDir.resolve(type.name).resolve("events");
            Files.createDirectories(dir);

            String content = eventGenerator.generate(events, type);
            Files.writeString(dir.resolve("index.d.ts"), content);
            count++;
        }

        return count;
    }

    private int generateRecipeEventDeclarations(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        List<RecipeNamespaceCatalogEntry> namespaces = snapshot.recipeNamespaces();
        if (namespaces.isEmpty()) return 0;

        // 只为 server 脚本生成 recipe 声明
        Path dir = outputDir.resolve("@side-only").resolve("server").resolve("events").resolve("recipes");
        Files.createDirectories(dir);

        String content = recipeEventGenerator.generate(namespaces, ScriptType.SERVER);
        Files.writeString(dir.resolve("index.d.ts"), content);
        return 1;
    }

    private int generateBindingDeclarations(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        int count = 0;
        Path sideOnlyDir = outputDir.resolve("@side-only");

        for (ScriptType type : ScriptType.all()) {
            List<BindingCatalogEntry> bindings = snapshot.bindings().stream()
                    .filter(b -> b.scriptType() == type && b.emit())
                    .toList();
            if (bindings.isEmpty()) continue;

            // 参考 ProbeJS: @side-only/server/bindings/index.d.ts（无 GlobalBindings 子目录）
            Path dir = sideOnlyDir.resolve(type.name).resolve("bindings");
            Files.createDirectories(dir);

            String content = bindingGenerator.generate(bindings, type);
            Files.writeString(dir.resolve("index.d.ts"), content);
            count++;
        }

        return count;
    }

    /**
     * 为每个 side 生成根 index.d.ts，重新导出 events 和 bindings。
     * 参考 ProbeJS: @side-only/server/index.d.ts
     */
    private int generateSideRootIndexes(Path outputDir) throws IOException {
        int count = 0;
        Path sideOnlyDir = outputDir.resolve("@side-only");

        for (ScriptType type : ScriptType.all()) {
            Path sideDir = sideOnlyDir.resolve(type.name);
            if (!Files.exists(sideDir)) continue;

            StringBuilder sb = new StringBuilder();
            Path eventsDir = sideDir.resolve("events");
            Path bindingsDir = sideDir.resolve("bindings");

            if (Files.exists(eventsDir)) {
                sb.append("export * as events from \"@side-only/").append(type.name).append("/events\";\n");
            }
            if (Files.exists(bindingsDir)) {
                sb.append("export * as bindings from \"@side-only/").append(type.name).append("/bindings\";\n");
            }

            if (sb.length() > 0) {
                Files.writeString(sideDir.resolve("index.d.ts"), sb.toString());
                count++;
            }
        }

        return count;
    }

    private int generateSpecialTypes(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        List<RegistryTypeCatalogEntry> registries = snapshot.registryTypes();
        if (registries.isEmpty()) return 0;

        Path specialDir = outputDir.resolve("@special");
        Files.createDirectories(specialDir);
        specialTypeGenerator.generate(registries, specialDir);
        return 2; // index.d.ts + types/index.d.ts
    }

    private int generateManualDeclarations(NekoScriptCatalogSnapshot snapshot, Path outputDir) throws IOException {
        return ManualDeclarationGenerator.generate(snapshot.manualDeclarations(), outputDir.resolve("@manual"));
    }
}
