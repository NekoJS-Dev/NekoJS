package com.tkisor.nekojs.api.recipe.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.recipe.RecipeJsonTypeCatalog;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 1.12.2 自动配方 schema 扫描器（方案 A：平台专用，直接用 MC 类型）。
 *
 * <p>类反射：遍历 {@link CraftingManager#REGISTRY}，按 registry id 命名空间 × 配方类分组，
 * 反射字段类型推断 schema（Ingredient/ItemStack/数值/String/List&lt;Ingredient&gt;...），
 * 产出 {@link RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes} 供现有 discover() 转正。
 * 跳过 minecraft/nekojs 命名空间与 net.minecraft(.forge) 前缀类。
 *
 * <p>JSON 目录：遍历各 mod 容器 {@code assets/<modid>/recipes/*.json}，收集去重 type 值，
 * 写入 {@link RecipeJsonTypeCatalog}（仅目录，无 schema）。
 */
public final class LegacyRecipeSchemaScanner {

    /** 自动发现得到的 (namespace:type) → 配方类（供 ReflectiveRecipeBuilder 构造时查找）。 */
    private static volatile Map<String, Class<?>> RECIPE_CLASSES = Map.of();

    private LegacyRecipeSchemaScanner() {}

    /** 全量扫描：注册表类反射 + mod jar JSON 目录。 */
    public static RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes scan() {
        List<IRecipe> recipes = new ArrayList<>();
        for (ResourceLocation id : CraftingManager.REGISTRY.getKeys()) {
            IRecipe recipe = CraftingManager.REGISTRY.getObject(id);
            if (recipe != null) recipes.add(recipe);
        }
        RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes types = collectTypes(recipes);
        RECIPE_CLASSES = recipeClassMap(recipes);
        try {
            RecipeJsonTypeCatalog.setCatalog(scanJsonTypes());
        } catch (Throwable e) {
            NekoJS.LOGGER.debug("LegacyRecipeSchemaScanner: JSON catalog scan failed", e);
        }
        return types;
    }

    /** (namespace:type) → 配方类；供构造器按类型名反查类。 */
    public static Class<?> recipeClass(String namespace, String type) {
        return RECIPE_CLASSES.get(namespace + ":" + type);
    }

    /** 从 IRecipe 集合收集类型（公开供测试；去重、跳过 vanilla）。 */
    public static RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes collectTypes(Iterable<IRecipe> recipes) {
        Map<String, List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey>> types = new LinkedHashMap<>();
        Set<String> seenClasses = new LinkedHashSet<>();
        for (IRecipe recipe : recipes) {
            if (recipe == null || recipe.getRegistryName() == null) continue;
            ResourceLocation id = recipe.getRegistryName();
            String ns = id.getNamespace();
            if (ns.equals("minecraft") || ns.equals("nekojs")) continue;
            Class<?> cls = recipe.getClass();
            String fqn = cls.getName();
            if (fqn.startsWith("net.minecraft") || fqn.startsWith("net.minecraftforge")) continue;
            String typeName = typeNameFor(cls);
            String key = ns + ":" + typeName;
            if (seenClasses.contains(key)) continue;
            List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> keys = inferFields(cls);
            if (keys.isEmpty()) {
                NekoJS.LOGGER.debug("LegacyRecipeSchemaScanner: no injectable fields in {}; skipping type {}", fqn, key);
                continue;
            }
            seenClasses.add(key);
            types.put(key, keys);
        }
        return RecipeSchemaAutoDiscovery.DiscoveredRecipeTypes.of(types);
    }

    private static Map<String, Class<?>> recipeClassMap(Iterable<IRecipe> recipes) {
        Map<String, Class<?>> map = new LinkedHashMap<>();
        for (IRecipe recipe : recipes) {
            if (recipe == null || recipe.getRegistryName() == null) continue;
            ResourceLocation id = recipe.getRegistryName();
            String ns = id.getNamespace();
            if (ns.equals("minecraft") || ns.equals("nekojs")) continue;
            Class<?> cls = recipe.getClass();
            String fqn = cls.getName();
            if (fqn.startsWith("net.minecraft") || fqn.startsWith("net.minecraftforge")) continue;
            map.putIfAbsent(ns + ":" + typeNameFor(cls), cls);
        }
        return Map.copyOf(map);
    }

    /**
     * 反射类字段 → DiscoveredRecipeKey 列表（跳过 static/synthetic/final；含继承链）。
     *
     * <p>只保留运行时真正能注入的字段：ReflectiveRecipeBuilder 的注入路径靠
     * {@code field.set} 赋值（final 字段被跳过）、值转换只覆盖 Ingredient/ItemStack/
     * 数值/布尔/String/ResourceLocation/JsonElement 族。其余类型（TypeToken、
     * IRegistryDelegate、Pattern、int[] 等）既设不进去也渲染成无意义的 {@code object}，
     * 一律剔除；上溯到注册表基建（{@code IForgeRegistryEntry.Impl}）即停止——
     * 它的 token/delegate/registryName 是装载器内部字段，registryName 由 builder 自动生成。
     */
    public static List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> inferFields(Class<?> cls) {
        List<RecipeSchemaAutoDiscovery.DiscoveredRecipeKey> keys = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().startsWith("net.minecraftforge.registries.")) break;
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                if (Modifier.isFinal(field.getModifiers())) continue;
                String name = field.getName();
                if (name.startsWith("$") || !seen.add(name)) continue;
                RecipeFieldKind kind = inferKind(field);
                if (kind == null) continue;
                if (kind == RecipeFieldKind.INGREDIENT && isListType(field.getGenericType())) {
                    keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.list(
                            RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required(name, RecipeFieldKind.INGREDIENT)));
                } else {
                    keys.add(RecipeSchemaAutoDiscovery.DiscoveredRecipeKey.required(name, kind));
                }
            }
        }
        return keys;
    }

    /** 推断字段 kind；返回 null 表示该类型没有运行时转换路径，字段整体剔除。 */
    private static RecipeFieldKind inferKind(Field field) {
        Type type = field.getGenericType();
        // 列表容器（List/NonNullList）：kind 由泛型实参决定（如 List<Ingredient> → INGREDIENT）
        if (type instanceof ParameterizedType pt) {
            Class<?> raw = rawClass(pt.getRawType());
            if (raw == List.class || raw == NonNullList.class) {
                Type arg = pt.getActualTypeArguments()[0];
                Class<?> argRaw = rawClass(arg);
                if (argRaw != null && Ingredient.class.isAssignableFrom(argRaw)) return RecipeFieldKind.INGREDIENT;
                if (argRaw != null && ItemStack.class.isAssignableFrom(argRaw)) return RecipeFieldKind.ITEM_STACK;
            }
            return null;
        }
        Class<?> raw = rawClass(type);
        if (raw == null) return null;
        if (Ingredient.class.isAssignableFrom(raw)) return RecipeFieldKind.INGREDIENT;
        if (ItemStack.class.isAssignableFrom(raw)) return RecipeFieldKind.ITEM_STACK;
        if (raw == int.class || raw == Integer.class) return RecipeFieldKind.INT;
        if (raw == float.class || raw == double.class || raw == Float.class || raw == Double.class) return RecipeFieldKind.NUMBER;
        if (raw == boolean.class || raw == Boolean.class) return RecipeFieldKind.BOOLEAN;
        if (raw == String.class || raw == ResourceLocation.class) return RecipeFieldKind.STRING;
        if (JsonElement.class.isAssignableFrom(raw)) return RecipeFieldKind.JSON;
        return null;
    }

    private static boolean isListType(Type type) {
        if (type instanceof ParameterizedType pt) {
            Class<?> raw = rawClass(pt.getRawType());
            return raw == List.class || raw == NonNullList.class;
        }
        return false;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> cls) return cls;
        if (type instanceof ParameterizedType pt) return rawClass(pt.getRawType());
        if (type instanceof GenericArrayType) return Object[].class;
        return null;
    }

    /** 类简单名 → snake_case（含数字边界：X2Thing → x2_thing）。 */
    public static String typeNameFor(Class<?> cls) {
        return toSnakeCase(cls.getSimpleName());
    }

    static String toSnakeCase(String simpleName) {
        StringBuilder sb = new StringBuilder();
        char[] chars = simpleName.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c)) {
                boolean prevUpper = isUpper(chars, i - 1);
                boolean nextLower = Character.isLowerCase(peek(chars, i + 1));
                if (sb.length() > 0 && (!prevUpper || nextLower)) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else if (Character.isDigit(c)) {
                if (sb.length() > 0 && Character.isLowerCase(peek(chars, i - 1))) {
                    sb.append('_');
                }
                sb.append(c);
            } else if (c == '_' || c == '$') {
                sb.append('_');
            } else if (Character.isLowerCase(c)) {
                sb.append(c);
            }
            // 其余字符（非法标识符）跳过
        }
        return sb.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    private static char peek(char[] chars, int i) {
        return i >= 0 && i < chars.length ? chars[i] : '\0';
    }

    private static boolean isUpper(char[] chars, int i) {
        char c = peek(chars, i);
        return c != '\0' && Character.isUpperCase(c);
    }

    /* ================= JSON 目录 ================= */

    private static Map<String, Set<String>> scanJsonTypes() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            String modId = mod.getModId();
            Set<String> types = new LinkedHashSet<>();
            File source = mod.getSource();
            if (source == null) continue;
            if (source.isDirectory()) {
                scanJsonDir(source.toPath(), modId, types);
            } else if (source.isFile() && source.getName().endsWith(".jar")) {
                scanJsonJar(source, modId, types);
            }
            if (!types.isEmpty()) result.put(modId, types);
        }
        return result;
    }

    private static void scanJsonJar(File jar, String modId, Set<String> types) {
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                String prefix = "assets/" + modId + "/recipes/";
                if (name.startsWith(prefix) && name.endsWith(".json")) {
                    try (InputStream in = jf.getInputStream(entry)) {
                        collectJsonType(in, name, types);
                    }
                }
            }
        } catch (IOException e) {
            NekoJS.LOGGER.debug("LegacyRecipeSchemaScanner: jar scan failed for {}", jar, e);
        }
    }

    private static void scanJsonDir(Path dir, String modId, Set<String> types) {
        Path recipesRoot = dir.resolve("assets").resolve(modId).resolve("recipes");
        if (!Files.isDirectory(recipesRoot)) return;
        try (Stream<Path> paths = Files.walk(recipesRoot)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try (InputStream in = Files.newInputStream(p)) {
                            collectJsonType(in, p.toString(), types);
                        } catch (IOException e) {
                            NekoJS.LOGGER.debug("LegacyRecipeSchemaScanner: file scan failed for {}", p, e);
                        }
                    });
        } catch (IOException e) {
            NekoJS.LOGGER.debug("LegacyRecipeSchemaScanner: dir scan failed for {}", recipesRoot, e);
        }
    }

    private static void collectJsonType(InputStream in, String name, Set<String> types) {
        try {
            JsonElement el = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (el.isJsonObject() && el.getAsJsonObject().has("type")
                    && el.getAsJsonObject().get("type").isJsonPrimitive()) {
                types.add(el.getAsJsonObject().get("type").getAsString());
            }
        } catch (Exception e) {
            NekoJS.LOGGER.debug("LegacyRecipeSchemaScanner: unparseable recipe JSON {}", name);
        }
    }
}
