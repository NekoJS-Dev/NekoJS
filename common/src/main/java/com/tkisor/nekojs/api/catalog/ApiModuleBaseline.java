package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.annotation.ScriptNativeBridge;
import com.tkisor.nekojs.api.data.BindingExposure.Category;

import java.lang.reflect.Modifier;
import java.util.*;

/** Generated module observations for release comparison, not verified managed contracts. */
public record ApiModuleBaseline(String profile, Map<String, Entry> exports) {
    public record Entry(Category category, boolean nativeBridge, Set<String> facets) {
        public Entry { facets = Collections.unmodifiableSet(new TreeSet<>(facets)); }
    }
    public record Change(String export, Set<String> removed, Set<String> added) {
        public Change {
            removed = Collections.unmodifiableSet(new TreeSet<>(removed));
            added = Collections.unmodifiableSet(new TreeSet<>(added));
        }
    }
    public record Report(List<Change> stableBreaks, List<Change> versionChanges, List<Change> additions) {
        public Report {
            stableBreaks = List.copyOf(stableBreaks);
            versionChanges = List.copyOf(versionChanges);
            additions = List.copyOf(additions);
        }
        public boolean compatible() { return stableBreaks.isEmpty(); }
    }
    public ApiModuleBaseline {
        Objects.requireNonNull(profile, "profile");
        exports = Collections.unmodifiableMap(new TreeMap<>(exports));
    }

    public static ApiModuleBaseline capture(String profile, NekoScriptCatalogSnapshot catalog) {
        Map<String, Entry> entries = new TreeMap<>();
        for (var module : catalog.moduleBindings()) {
            var binding = module.binding();
            Set<String> facets = new TreeSet<>();
            facets.add("binding:" + binding.staticClass());
            collect(binding.javaType(), binding.staticClass(), "", facets, new HashSet<>());
            entries.put(binding.scriptType() + ":" + module.moduleId() + ":" + binding.name(),
                    entry(module.category(), binding.javaType().isAnnotationPresent(ScriptNativeBridge.class), facets));
        }
        for (var module : catalog.moduleEvents()) {
            var event = module.event();
            Set<String> facets = new TreeSet<>();
            facets.add("event:cancel=" + event.cancellable() + ":dispatch=" + event.dispatchable());
            facets.add("key:" + (event.dispatchKeyType() == null ? "none" : event.dispatchKeyType().getName()));
            facets.add("callback:" + event.callSignatures());
            if (event.eventType() != null) collect(event.eventType(), false, "payload.", facets, new HashSet<>());
            entries.put(module.scriptType() + ":" + module.moduleId() + ":" + event.group() + "." + event.name(),
                    new Entry(module.category(), false, facets));
        }
        return new ApiModuleBaseline(profile, entries);
    }

    private static Entry entry(Category category, boolean bridge, Set<String> facets) {
        if (!bridge) return new Entry(category, false, facets);
        Set<String> classified = new TreeSet<>();
        for (String facet : facets) {
            String shape = facet.replaceAll("net\\.(?:minecraft|neoforged|fabricmc)\\.[a-zA-Z0-9_.$]+", "<native>");
            classified.add(shape);
            if (!shape.equals(facet)) classified.add("native:" + facet);
        }
        return new Entry(category, true, classified);
    }

    /** Deterministic JSON suitable for checked-in, per-profile release observations. */
    public String toJson() {
        return new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(this) + "\n";
    }

    public static ApiModuleBaseline fromJson(String json) {
        return new com.google.gson.Gson().fromJson(json, ApiModuleBaseline.class);
    }

    private static void collect(Class<?> type, boolean statics, String path, Set<String> facets, Set<Class<?>> seen) {
        if (type.isPrimitive() || type.isArray() || !seen.add(type)) return;
        // Native/JDK internals are not portable API members. Their precise names remain in signatures.
        if (type.getPackageName().startsWith("java.") || nativeType(type)) return;
        var members = JavaMemberIndex.exposedMembersOf(type);
        members.methods().forEach((name, methods) -> methods.stream()
                .filter(method -> Modifier.isStatic(method.getModifiers()) == statics).forEach(method -> {
                    String signature = Arrays.stream(method.getGenericParameterTypes())
                            .map(java.lang.reflect.Type::getTypeName).collect(java.util.stream.Collectors.joining(","));
                    facets.add(path + "call:" + name + "(" + signature + "):" + method.getGenericReturnType().getTypeName()
                            + ":varargs=" + method.isVarArgs() + ":annotations=" + Arrays.stream(method.getAnnotations()).map(Object::toString).sorted().toList());
                    collect(method.getReturnType(), false, "type:" + method.getReturnType().getName() + ".", facets, seen);
                    for (Class<?> parameter : method.getParameterTypes())
                        collect(parameter, false, "type:" + parameter.getName() + ".", facets, seen);
                }));
        members.propertyGetters().forEach((name, methods) -> methods.stream()
                .filter(method -> Modifier.isStatic(method.getModifiers()) == statics)
                .forEach(method -> facets.add(path + "read:" + name + ":" + method.getGenericReturnType().getTypeName())));
        members.methods().forEach((name, methods) -> {
            if (name.startsWith("set") && name.length() > 3) methods.stream()
                    .filter(method -> method.getParameterCount() == 1 && Modifier.isStatic(method.getModifiers()) == statics)
                    .forEach(method -> facets.add(path + "write:" + Character.toLowerCase(name.charAt(3)) + name.substring(4)
                            + ":" + method.getGenericParameterTypes()[0].getTypeName()));
        });
        if (!statics) members.fields().forEach((name, fields) -> fields.forEach(field -> {
            facets.add(path + "read:" + name + ":" + field.getGenericType().getTypeName());
            if (!Modifier.isFinal(field.getModifiers())) facets.add(path + "write:" + name + ":" + field.getGenericType().getTypeName());
        }));
    }

    private static boolean nativeType(Class<?> type) {
        String name = type.getName();
        return name.startsWith("net.minecraft.") || name.startsWith("net.neoforged.") || name.startsWith("net.fabricmc.");
    }

    /** Compares old to new: stable removals fail; version and native-bridge changes remain visible. */
    public Report compareTo(ApiModuleBaseline next) {
        List<Change> stable = new ArrayList<>(), version = new ArrayList<>(), additions = new ArrayList<>();
        exports.forEach((key, old) -> {
            Entry current = next.exports.get(key);
            Set<String> removed = new TreeSet<>(old.facets()), added = new TreeSet<>();
            if (current != null) {
                removed.removeAll(current.facets());
                added.addAll(current.facets());
                added.removeAll(old.facets());
                if (current.category() != old.category()) removed.add("category:" + old.category());
                if (current.nativeBridge() != old.nativeBridge()) removed.add("nativeBridge:" + old.nativeBridge());
            } else removed.add("export");
            if (removed.isEmpty() && added.isEmpty()) return;
            Change change = new Change(key, removed, added);
            // The stable name and scope of a bridge are still required; native signatures are reported separately.
            if (!removed.isEmpty() && old.category() == Category.STABLE
                    && removed.stream().anyMatch(facet -> !old.nativeBridge() || !facet.startsWith("native:"))) stable.add(change);
            else if (!removed.isEmpty()) version.add(change);
            else additions.add(change);
        });
        next.exports.forEach((key, entry) -> {
            if (!exports.containsKey(key)) additions.add(new Change(key, Set.of(), entry.facets()));
        });
        return new Report(stable, version, additions);
    }
}
