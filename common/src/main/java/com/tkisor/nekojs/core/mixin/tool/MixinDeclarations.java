package com.tkisor.nekojs.core.mixin.tool;

import com.tkisor.nekojs.core.compiler.NekoTypeScriptCompiler;
import com.tkisor.nekojs.core.mixin.MixinPlan;
import com.tkisor.nekojs.core.module.esm.*;
import graal.graalvm.polyglot.*;
import graal.graalvm.polyglot.io.IOAccess;
import graal.graalvm.polyglot.proxy.*;
import java.nio.file.*;
import java.util.*;

/** Isolated declaration evaluator with no Java lookup, filesystem, process or network access. */
final class MixinDeclarations implements AutoCloseable {
    private final Path root;
    private final Context context;
    private final Map<Path, Value> modules = new LinkedHashMap<>();
    private final Set<Path> visiting = new HashSet<>();
    final Map<String, byte[]> sources = new TreeMap<>();
    final List<MixinPlan.Hook> hooks = new ArrayList<>();
    private final Value api;

    MixinDeclarations(Path root) throws Exception {
        this.root = root.toRealPath();
        context = Context.newBuilder("js").allowHostAccess(HostAccess.NONE).allowHostClassLookup(name -> false)
                .allowIO(IOAccess.NONE).allowCreateThread(false).allowNativeAccess(false)
                .resourceLimits(ResourceLimits.newBuilder().statementLimit(100_000, source -> true).build()).build();
        api = context.asValue(ProxyObject.fromMap(Map.of("Mixin", ProxyObject.fromMap(Map.of("target", (ProxyExecutable) args -> {
            if (args.length != 2 || !args[0].isString() || !args[1].canExecute()) throw MixinPlan.invalid("Expected Mixin.target(className, callback)");
            String target = args[0].asString();
            undefined(args[1].execute(ProxyObject.fromMap(Map.of("inject", (ProxyExecutable) values -> {
                if (values.length != 3 || !values[0].isString() || !values[1].isString() || !values[2].canExecute()) throw MixinPlan.invalid("Expected inject(methodDescriptor, id, builder)");
                String selector = values[0].asString(); int start = selector.indexOf('(');
                if (start <= 0) throw MixinPlan.invalid("A full method descriptor is required");
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("at", "HEAD"); fields.put("cancellable", false); fields.put("scriptType", "SERVER");
                fields.put("thread", "SERVER_MAIN"); fields.put("require", 1); fields.put("staticMethod", false);
                boolean[] live = {true};
                ProxyObject builder = new ProxyObject() {
                    public Object getMember(String key) { return fields.get(key); }
                    public Object getMemberKeys() { return fields.keySet().toArray(String[]::new); }
                    public boolean hasMember(String key) { return fields.containsKey(key); }
                    public void putMember(String key, Value value) {
                        if (!live[0] || !fields.containsKey(key)) throw MixinPlan.invalid("Unknown or expired hook field: " + key);
                        Object current = fields.get(key);
                        if (current instanceof Boolean && value.isBoolean()) fields.put(key, value.asBoolean());
                        else if (current instanceof Integer && value.fitsInInt()) fields.put(key, value.asInt());
                        else if (current instanceof String && value.isString()) fields.put(key, value.asString());
                        else throw MixinPlan.invalid("Invalid hook field type: " + key);
                    }
                };
                try { undefined(values[2].execute(builder)); } finally { live[0] = false; }
                hooks.add(new MixinPlan.Hook(values[1].asString(), target, selector.substring(0, start), selector.substring(start),
                        (String) fields.get("at"), (boolean) fields.get("cancellable"), (String) fields.get("scriptType"),
                        (String) fields.get("thread"), (int) fields.get("require"), (boolean) fields.get("staticMethod")));
                return null;
            }))));
            return null;
        })))));
    }

    void loadAll() throws Exception {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".mixin.js") || p.toString().endsWith(".mixin.ts")).sorted().toList()) load(path);
        }
        if (hooks.isEmpty()) throw MixinPlan.invalid("No .mixin.js or .mixin.ts hooks found");
    }

    private Value load(Path input) throws Exception {
        Path path = input.toRealPath();
        if (!path.startsWith(root)) throw MixinPlan.invalid("Declaration import escapes the source root");
        if (modules.containsKey(path)) return modules.get(path);
        if (!visiting.add(path)) throw MixinPlan.invalid("Cyclic declaration imports are unsupported");
        try {
            byte[] bytes = Files.readAllBytes(path);
            sources.put(root.relativize(path).toString().replace((char) 92, (char) 47), bytes);
            String code = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (path.toString().endsWith(".ts")) code = NekoTypeScriptCompiler.eraseTypescript(path, code);
            var ast = new NekoEsmParser(path, code).parse();
            if (ast.topLevelAwait() || !ast.runtimeExpressions().isEmpty() || !ast.exports().isEmpty()) throw MixinPlan.invalid("Declarations allow static imports, not exports, dynamic imports or top-level await");
            var dependencies = new ArrayList<Value>();
            var edits = new ArrayList<Map.Entry<NekoEsmSpan, String>>();
            for (var declaration : ast.imports()) {
                String specifier = declaration.specifier(); Value dependency;
                if (specifier.equals("nekojs/mixin")) dependency = api;
                else {
                    if (!specifier.startsWith("./") && !specifier.startsWith("../")) throw MixinPlan.invalid("Only relative declaration imports and nekojs/mixin are allowed");
                    if (!specifier.endsWith(".js") && !specifier.endsWith(".ts")) throw MixinPlan.invalid("Relative declaration imports require an explicit .js or .ts extension");
                    dependency = load(path.getParent().resolve(specifier));
                }
                int index = dependencies.size(); dependencies.add(dependency);
                String base = "__deps[" + index + "]"; StringBuilder replacement = new StringBuilder();
                if (declaration.defaultName() != null) throw MixinPlan.invalid("Default declaration imports are unsupported");
                if (declaration.namespaceName() != null) replacement.append("const ").append(declaration.namespaceName()).append(" = ").append(base).append(';');
                for (var binding : declaration.namedBindings()) replacement.append("const ").append(binding.local()).append(" = ").append(base).append('[').append(MixinCarrierWriter.JSON.toJson(binding.imported())).append("]; ");
                edits.add(Map.entry(declaration.span(), replacement.toString()));
            }
            var rewritten = new StringBuilder(code);
            edits.sort((left, right) -> Integer.compare(right.getKey().start(), left.getKey().start()));
            edits.forEach(edit -> rewritten.replace(edit.getKey().start(), edit.getKey().end(), edit.getValue()));
            var script = Source.newBuilder("js", "(__deps) => { 'use strict';\n" + rewritten + "\n; return Object.freeze({}); }", path.toString()).build();
            Value exports = context.eval(script).execute(ProxyArray.fromArray(dependencies.toArray()));
            modules.put(path, exports); return exports;
        } finally { visiting.remove(path); }
    }
    private static void undefined(Value value) {
        if (!value.toString().equals("undefined")) throw MixinPlan.invalid("Declaration builders must be synchronous and return no value");
    }
    @Override public void close() {
        try { context.close(true); }
        catch (PolyglotException failure) { if (!failure.isCancelled() && !failure.isResourceExhausted()) throw failure; }
        finally { modules.clear(); }
    }
}
