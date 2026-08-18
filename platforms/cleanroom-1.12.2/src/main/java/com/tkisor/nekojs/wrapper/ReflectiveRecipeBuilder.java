package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1.12.2 schema 驱动配方的反射构造 builder（对齐 NeoForge RecipeJsonBuilder 的「构造即入队」语义）。
 *
 * <p>脚本经 SchemaRecipeBuilder 链式 setter 调 {@link #setTyped}（RecipeEventSchemaHost.applyField
 * 覆写路由），字段值以 typed Java 值累积；event flush 时按 schema 反射构造 IRecipe 并注册。
 * 构造方式：script schema 声明 ctor（字段名序列）→ 按字段 kinds→Java 类型匹配构造器；
 * 否则无参构造 + 字段注入。类解析：registerSchema 的 class FQN → 自动扫描 recipeClass。
 */
public final class ReflectiveRecipeBuilder implements RecipeBuilder {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    private final RecipeEventJS event;
    private final String type;      // "ns:type"
    private final String prefix;
    private final Map<String, Object> typedValues = new LinkedHashMap<>();
    private final Map<String, JsonElement> jsonValues = new LinkedHashMap<>();
    private boolean registered;

    public ReflectiveRecipeBuilder(RecipeEventJS event, String type, String prefix) {
        this.event = event;
        this.type = type;
        this.prefix = prefix;
        event.addPendingBuilder(this);
    }

    /** SchemaRecipeBuilder 经 RecipeEventSchemaHost.applyField 调用的 typed 入口（同包，JS 不可见）。 */
    void setTyped(String name, RecipeFieldKind kind, Value value) {
        typedValues.put(name, convertTyped(kind, value));
    }

    private static Object convertTyped(RecipeFieldKind kind, Value value) {
        return switch (kind) {
            case INGREDIENT -> IngredientResolver.fromValue(value);
            case ITEM_STACK -> new ItemStackAdapter().apply(value);
            case INT -> value.asInt();
            case NUMBER -> value.asDouble();
            case BOOLEAN -> value.asBoolean();
            case STRING -> value.asString();
            default -> value; // JSON：原样保留 Value，构造时再转换
        };
    }

    @Override
    public RecipeBuilder setPath(String path, RecipeJsonValue value) {
        // JSON 逃生口：仅 kind=json 的字段接受；其余给出明确指引
        String fieldName = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        RecipeTypeDefinition def = currentDef();
        if (def != null && def.fields().containsKey(fieldName)
                && def.fields().get(fieldName).kind() == RecipeFieldKind.JSON) {
            jsonValues.put(fieldName, RecipeJsonValueConverter.toJson(value.value()));
            return this;
        }
        throw new UnsupportedOperationException(
                "setPath is not supported for field '" + path + "' on 1.12.2 (no JSON pipeline). "
                        + "Call the typed setter '" + fieldName + "(value)' instead.");
    }

    /** event flush 调用（脚本也可显式调用）：反射构造 + 注册。任何失败仅记录，不抛出（不中断事件）。幂等。 */
    public void register() {
        if (registered) return;
        registered = true;
        String ns = type.substring(0, type.indexOf(':'));
        String name = type.substring(type.indexOf(':') + 1);
        RecipeTypeDefinition def = currentDef();
        if (def == null) {
            NekoJS.LOGGER.error("ReflectiveRecipeBuilder: no schema for '{}'; register it via event.registerSchema or a Java plugin", type);
            return;
        }
        Class<?> cls = event.recipeClassFor(ns, name);
        if (cls == null) {
            NekoJS.LOGGER.error("ReflectiveRecipeBuilder: no recipe class known for '{}'; declare 'class' in event.registerSchema", type);
            return;
        }
        IRecipe recipe = null;
        try {
            List<String> ctorFields = event.scriptCtorFieldsFor(ns, name);
            recipe = ctorFields != null
                    ? constructViaCtor(cls, def, ctorFields)
                    : constructViaInjection(cls, def);
        } catch (Throwable e) {
            NekoJS.LOGGER.error("ReflectiveRecipeBuilder: failed to construct '{}' (class {}): {}. "
                            + "Fall back to event.registerSchema with an explicit 'class'/'ctor' or a Java plugin handler.",
                    type, cls.getName(), e.toString());
            return;
        }
        if (recipe == null) return;
        ResourceLocation id = new ResourceLocation("nekojs",
                "nekojs_" + name + "_" + ID_COUNTER.getAndIncrement());
        recipe.setRegistryName(id);
        try {
            ForgeRegistries.RECIPES.register(recipe);
            NekoJS.LOGGER.info("ReflectiveRecipeBuilder: registered {} → {}", type, id);
        } catch (Throwable e) {
            NekoJS.LOGGER.error("ReflectiveRecipeBuilder: register failed for '{}' (class {}): {}", type, cls.getName(), e.toString());
        }
    }

    private RecipeTypeDefinition currentDef() {
        String ns = type.substring(0, type.indexOf(':'));
        String name = type.substring(type.indexOf(':') + 1);
        return RecipeTypeDefinitionStorage.current().get(ns, name);
    }

    /* ================= 构造 ================= */

    private IRecipe constructViaCtor(Class<?> cls, RecipeTypeDefinition def, List<String> ctorFields) throws Exception {
        Class<?>[] paramTypes = new Class<?>[ctorFields.size()];
        for (int i = 0; i < ctorFields.size(); i++) {
            RecipeFieldDefinition f = def.fields().get(ctorFields.get(i));
            if (f == null) throw new IllegalArgumentException("ctor field '" + ctorFields.get(i) + "' not in schema fields");
            paramTypes[i] = javaTypeFor(f.kind());
        }
        Constructor<?> ctor = findConstructor(cls, paramTypes);
        Object[] args = new Object[ctorFields.size()];
        for (int i = 0; i < ctorFields.size(); i++) {
            Object value = typedValues.get(ctorFields.get(i));
            if (value == null && jsonValues.containsKey(ctorFields.get(i))) {
                value = jsonValues.get(ctorFields.get(i));
            }
            args[i] = convertForType(value, paramTypes[i]);
            if (args[i] == null) throw new IllegalArgumentException("missing field '" + ctorFields.get(i) + "'");
        }
        return (IRecipe) ctor.newInstance(args);
    }

    private IRecipe constructViaInjection(Class<?> cls, RecipeTypeDefinition def) throws Exception {
        IRecipe instance = (IRecipe) cls.getDeclaredConstructor().newInstance();
        for (String fieldName : def.fields().keySet()) {
            Object value = typedValues.get(fieldName);
            if (value == null && jsonValues.containsKey(fieldName)) {
                value = jsonValues.get(fieldName);
            }
            if (value == null) {
                if (def.fields().get(fieldName).required()) {
                    throw new IllegalArgumentException("missing required field '" + fieldName + "'");
                }
                continue;
            }
            Field field = findField(cls, fieldName);
            if (field == null) {
                throw new IllegalArgumentException("no field '" + fieldName + "' on " + cls.getName());
            }
            field.setAccessible(true);
            field.set(instance, convertForType(value, field.getType()));
        }
        return instance;
    }

    private static Constructor<?> findConstructor(Class<?> cls, Class<?>[] paramTypes) {
        for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
            Class<?>[] actual = ctor.getParameterTypes();
            if (actual.length != paramTypes.length) continue;
            boolean match = true;
            for (int i = 0; i < actual.length; i++) {
                if (!assignable(actual[i], paramTypes[i])) { match = false; break; }
            }
            if (match) {
                ctor.setAccessible(true);
                return ctor;
            }
        }
        throw new IllegalArgumentException("no constructor with parameter types "
                + List.of(paramTypes) + " on " + cls.getName());
    }

    /** target 是否可接受 declared（declared 是 kinds→Java 类型的期望形参）。 */
    private static boolean assignable(Class<?> target, Class<?> declared) {
        if (declared == float.class && (target == double.class || target == float.class)) return true;
        if (declared == int.class && (target == int.class || target == Integer.class || target == long.class)) return true;
        return target == declared || declared.isAssignableFrom(target);
    }

    private static Object convertForType(Object value, Class<?> target) {
        if (value == null) return null;
        if (target.isInstance(value)) return value;
        if (value instanceof Number n) {
            if (target == int.class || target == Integer.class) return n.intValue();
            if (target == float.class || target == Float.class) return n.floatValue();
            if (target == double.class || target == Double.class) return n.doubleValue();
            if (target == long.class || target == Long.class) return n.longValue();
        }
        if (value instanceof Value v) { // JSON kind 的原始 Value
            if (target == JsonElement.class || target == com.google.gson.JsonObject.class) {
                return RecipeJsonValueConverter.toJson(v);
            }
        }
        return value;
    }

    private static Class<?> javaTypeFor(RecipeFieldKind kind) {
        return switch (kind) {
            case INGREDIENT -> Ingredient.class;
            case ITEM_STACK -> ItemStack.class;
            case INT -> int.class;
            case NUMBER -> float.class;
            case BOOLEAN -> boolean.class;
            case STRING -> String.class;
            default -> Object.class;
        };
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
                return f;
            } catch (NoSuchFieldException ignored) {
                // 沿继承链继续
            }
        }
        return null;
    }
}
