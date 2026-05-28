package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.annotation.CalledByDynamicCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Single proxy for a recipe namespace. Resolves recipe types in order:
 *
 * <ol>
 *   <li>Handler methods (reflection-based, exact type match)</li>
 *   <li>Schema definitions ({@link RecipeTypeDefinition}, auto-discovered or plugin-registered)</li>
 *   <li>Raw JSON fallback (adds type field, any structure accepted)</li>
 * </ol>
 *
 * All public methods below are called by GraalVM via {@link ProxyObject} dispatch,
 * not by direct Java callers. IDE "find usages" will show 0 results.
 */
final class RecipeNamespaceProxy implements ProxyObject {
    private final RecipeEventJS event;
    private final String namespace;
    private final Object handler;
    private final Map<String, List<Method>> handlerMethods;
    private final RecipeTypeDefinitionRegistry definitions;

    RecipeNamespaceProxy(RecipeEventJS event, String namespace, Object handler,
                         RecipeTypeDefinitionRegistry definitions) {
        this.event = event;
        this.namespace = namespace;
        this.handler = handler;
        this.definitions = definitions;
        this.handlerMethods = handler != null ? reflectMethods(handler) : Map.of();
    }

    // ==================== ProxyObject (GraalVM interop) ====================

    @Override @CalledByDynamicCode
    public Object getMember(String type) {
        List<Method> methods = handlerMethods.get(type);
        if (methods != null && !methods.isEmpty()) {
            return handlerExecutable(type, methods);
        }

        RecipeTypeDefinition def = definitions.get(namespace, type);
        if (def != null) {
            return schemaExecutable(def);
        }

        return fallbackExecutable(type);
    }

    @Override @CalledByDynamicCode
    public Object getMemberKeys() {
        Set<String> keys = new LinkedHashSet<>(handlerMethods.keySet());
        keys.addAll(definitions.types(namespace));
        return keys.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return handlerMethods.containsKey(key)
                || definitions.get(namespace, key) != null
                || true; // raw JSON fallback always accepts
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Recipe namespace is read-only");
    }

    // ==================== Handler dispatch ====================

    private ProxyExecutable handlerExecutable(String type, List<Method> methods) {
        return args -> {
            for (Method m : methods) {
                int total = m.getParameterCount();
                int required = requiredParamCount(m);
                if (args.length >= required && args.length <= total) {
                    try {
                        Object[] converted = convertArgs(m, args, total);
                        return m.invoke(handler, converted);
                    } catch (Exception e) {
                        break; // conversion failed → try next layer
                    }
                }
            }
            // handler failed → try schema
            RecipeTypeDefinition def = definitions.get(namespace, type);
            if (def != null) return schemaExecutable(def).execute(args);
            return fallbackExecutable(type).execute(args);
        };
    }

    // ==================== Schema dispatch ====================

    private ProxyExecutable schemaExecutable(RecipeTypeDefinition def) {
        return args -> {
            Map<String, Value> values = resolveArgs(def, args);
            RecipeJsonBuilder builder = new RecipeJsonBuilder(event, def.type(), def.prefix());
            for (RecipeFieldDefinition field : def.fields().values()) {
                if (values.containsKey(field.name())) {
                    builder.setPath(field.path(), new RecipeJsonValue(convertField(field, values.get(field.name()))));
                } else if (field.defaultValue() != null) {
                    builder.setPath(field.path(), new RecipeJsonValue(field.defaultValue().deepCopy()));
                } else if (field.required()) {
                    throw new IllegalArgumentException("Missing required field '" + field.name() + "' for " + def.key());
                }
            }
            return builder;
        };
    }

    private Map<String, Value> resolveArgs(RecipeTypeDefinition def, Value[] args) {
        if (args.length == 1 && args[0].hasMembers() && hasNamedField(def, args[0])) {
            Map<String, Value> values = new LinkedHashMap<>();
            for (String field : def.fields().keySet()) {
                if (args[0].hasMember(field)) values.put(field, args[0].getMember(field));
            }
            return values;
        }
        for (List<String> ctor : def.constructors()) {
            if (ctor.size() == args.length) {
                Map<String, Value> values = new LinkedHashMap<>();
                for (int i = 0; i < ctor.size(); i++) values.put(ctor.get(i), args[i]);
                return values;
            }
        }
        StringBuilder msg = new StringBuilder("No constructor for " + def.key() + " accepts " + args.length + " args.\n");
        msg.append("Available: ");
        for (List<String> c : def.constructors()) msg.append("(").append(String.join(", ", c)).append(") ");
        msg.append("or named object: { ").append(String.join(", ", def.fields().keySet())).append(" }");
        throw new IllegalArgumentException(msg.toString());
    }

    private static boolean hasNamedField(RecipeTypeDefinition def, Value value) {
        for (String field : def.fields().keySet()) {
            if (value.hasMember(field)) return true;
        }
        return false;
    }

    // ==================== Raw JSON fallback ====================

    private ProxyExecutable fallbackExecutable(String recipeType) {
        return args -> {
            if (args.length == 1 && args[0].hasMembers()) {
                JsonElement converted = RecipeJsonValueConverter.toJson(event, args[0]);
                if (converted.isJsonObject()) {
                    JsonObject json = converted.getAsJsonObject();
                    json.addProperty("type", namespace + ":" + recipeType);
                    return event.custom(json);
                }
            }
            return null;
        };
    }

    // ==================== Field conversion ====================

    private JsonElement convertField(RecipeFieldDefinition field, Value value) {
        if (!field.array()) return convertOne(field.kind(), value);
        JsonArray array = new JsonArray();
        if (value.hasArrayElements()) {
            for (long i = 0; i < value.getArraySize(); i++) array.add(convertOne(field.kind(), value.getArrayElement(i)));
        } else {
            array.add(convertOne(field.kind(), value));
        }
        return array;
    }

    private JsonElement convertOne(RecipeFieldKind kind, Value value) {
        return switch (kind) {
            case JSON -> RecipeJsonValueConverter.toJson(event, value);
            case STRING -> new JsonPrimitive(value.asString());
            case INT -> new JsonPrimitive(value.asInt());
            case NUMBER -> new JsonPrimitive(value.asDouble());
            case BOOLEAN -> new JsonPrimitive(value.asBoolean());
            case INGREDIENT -> event.serializeIngredient(IngredientResolver.fromValue(value));
            case ITEM_STACK -> event.serializeResult(new ItemStackAdapter().apply(value));
            case FLUID_STACK -> event.serializeFluidStack(FluidResolver.stackFromValue(value));
            case FLUID_INGREDIENT -> event.serializeFluidIngredient(FluidResolver.ingredientFromValue(value));
            case SIZED_FLUID_INGREDIENT -> event.serializeSizedFluidIngredient(FluidResolver.sizedFromValue(value));
        };
    }

    // ==================== Reflection ====================

    private static Map<String, List<Method>> reflectMethods(Object handler) {
        Map<String, List<Method>> methods = new LinkedHashMap<>();
        for (Method m : handler.getClass().getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            methods.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
        }
        return methods;
    }

    private static Object[] convertArgs(Method m, Value[] args, int totalParams) {
        Class<?>[] paramTypes = m.getParameterTypes();
        Object[] converted = new Object[totalParams];
        for (int i = 0; i < totalParams; i++) {
            if (i < args.length) {
                converted[i] = args[i].as(paramTypes[i]);
            } else {
                converted[i] = Optional.empty(); // trailing Optional<T> param
            }
        }
        return converted;
    }

    private static int requiredParamCount(Method m) {
        int count = 0;
        for (var p : m.getParameterTypes()) {
            if (p == Optional.class) break;
            count++;
        }
        return count;
    }
}
