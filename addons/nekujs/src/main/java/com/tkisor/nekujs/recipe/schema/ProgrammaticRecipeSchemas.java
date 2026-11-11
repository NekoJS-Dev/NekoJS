package com.tkisor.nekujs.recipe.schema;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeBuilderSession;
import com.tkisor.nekojs.api.recipe.RecipeExecutionFactory;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.*;
import com.tkisor.nekujs.recipe.value.LegacyRecipeComponents;
import com.tkisor.nekujs.recipe.value.LegacyRecipeInputTypes;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.ListRecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.schema.*;
import graal.graalvm.polyglot.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/** Compiles original Java schema metadata to the same native profiles used by resource schemas. */
public final class ProgrammaticRecipeSchemas {
    private ProgrammaticRecipeSchemas() { }

    public static Map<String, RecipeTypeDefinition> compile(RecipeSchemaStorage storage) {
        var result = new LinkedHashMap<String, RecipeTypeDefinition>();
        storage.programmaticSchemas().forEach((id, schema) -> {
            RecipeTypeDefinition definition = compile(id, schema, storage);
            result.put(definition.key(), definition);
        });
        return Map.copyOf(result);
    }

    private static RecipeTypeDefinition compile(ResourceLocation id, RecipeSchema schema, RecipeSchemaStorage storage) {
        if (!schema.keyOverrides.isEmpty()) throw new UnsupportedOperationException(
                "[NEKO-5347] Forced programmatic constructor overrides are not supported: " + id);
        var type = new RecipeSchemaType(new RecipeNamespace(storage, id.getNamespace()), id, schema);
        var fields = new LinkedHashMap<String, RecipeFieldDefinition>();
        var aliases = new LinkedHashMap<String, String>();
        var functions = new LinkedHashMap<String, String>();
        for (RecipeKey<?> key : schema.keys) {
            var defaultSource = schema.keyOverrides.getOrDefault(key, key.optional);
            Object defaultValue = defaultSource == null ? null : defaultSource.getDefaultValue(type);
            JsonElement encodedDefault = defaultValue == null ? null : encodeDefault(key, defaultValue, storage);
            fields.put(key.name, new RecipeFieldDefinition(key.name, key.name, RecipeFieldKind.JSON, false,
                    key.optional() || schema.keyOverrides.containsKey(key), encodedDefault,
                    RecipeFieldRole.valueOf(key.role.name()), codec(key.component, storage), key.alwaysWrite || schema.keyOverrides.containsKey(key)));
            for (String name : key.names) {
                if (!name.equals(key.name) && aliases.putIfAbsent(name, key.name) != null) {
                    throw new IllegalArgumentException("[NEKO-5337] Duplicate programmatic recipe field alias: " + id + "." + name);
                }
            }
            for (String name : key.getValidFunctionNames()) {
                if (functions.putIfAbsent(name, key.name) != null) {
                    throw new IllegalArgumentException("[NEKO-5338] Duplicate programmatic recipe field function: " + id + "." + name);
                }
            }
        }
        var constructors = new ArrayList<List<String>>();
        for (int count = schema.minRequiredArguments(); count <= schema.includedKeys.size(); count++) {
            constructors.add(schema.includedKeys.subList(0, count).stream().map(key -> key.name).toList());
        }
        RecipeExecutionFactory execution = schema.recipeFactory == KubeRecipeFactory.DEFAULT ? null : new RecipeExecutionFactory() {
            @Override public Object create(RecipeBuilder builder, RecipeSchemaHost host, RecipeBuilderSession session) {
                var target = schema.recipeFactory.factory().get();
                if (!exposedType().isInstance(target)) throw new IllegalStateException("[NEKO-5339] Recipe factory returned an incompatible fluent target: " + id);
                target.attach(type, builder, host, session);
                return target;
            }
            @Override public Class<?> exposedType() { return schema.recipeFactory.recipeType().asClass(); }
        };
        return new RecipeTypeDefinition(id.getNamespace(), id.getPath(), type.serializerType, null, constructors,
                fields, List.of(), aliases, Map.of(), Map.of(), null, functions, execution);
    }

    @SuppressWarnings("unchecked")
    private static <T> JsonElement encodeDefault(RecipeKey<T> key, Object value, RecipeSchemaStorage storage) {
        return key.codec.encodeStart(storage.registries().json(), (T) value).getOrThrow(message ->
                new IllegalArgumentException("[NEKO-5340] Programmatic default encode failed: " + key.name + ": " + message));
    }

    private static RecipeFieldValueCodec codec(RecipeComponent<?> component, RecipeSchemaStorage storage) {
        RecipeFieldValueCodec nativeCodec = nativeType(primaryClass(component)) == null ? null
                : new LegacyRecipeComponents(storage, storage.registries()).resolve(new JsonPrimitive(nativeType(primaryClass(component))));
        RecipeFieldValueCodec elementCodec = component instanceof ListRecipeComponent<?> list ? codec(list.component(), storage) : null;
        AdapterInputShape input = nativeCodec == null ? shape(component) : nativeCodec.inputShape();
        if (component instanceof ListRecipeComponent<?> list) {
            input = list.canWriteSelf() ? union(elementCodec.inputShape(), arrayOf(elementCodec.inputShape()))
                    : arrayOf(elementCodec.inputShape());
        }
        return new RecipeFieldValueCodec(input, LegacyRecipeInputTypes.of(component.typeInfo()),
                (value, host) -> encode(component, value, host, nativeCodec, elementCodec));
    }

    private static Class<?> primaryClass(RecipeComponent<?> component) {
        var info = component.typeInfo();
        return info instanceof dev.latvian.mods.rhino.type.TypeInfo.UnionTypeInfo union
                ? union.alternatives().getFirst().asClass() : info.asClass();
    }

    private static boolean acceptsItemStack(RecipeComponent<?> component) {
        return component.typeInfo() instanceof dev.latvian.mods.rhino.type.TypeInfo.UnionTypeInfo union
                && union.alternatives().stream().anyMatch(type -> type.asClass() == ItemStack.class);
    }

    private static AdapterInputShape shape(RecipeComponent<?> component) {
        if (component instanceof ListRecipeComponent<?> list) {
            AdapterInputShape element = shape(list.component());
            return list.canWriteSelf() ? union(element, arrayOf(element)) : arrayOf(element);
        }
        Class<?> raw = primaryClass(component);
        String nativeType = nativeType(raw);
        if (nativeType != null) return union(host(raw), nativeShape(nativeType));
        if (acceptsItemStack(component)) return union(host(raw), host(ItemStack.class), nativeShape("item_stack"));
        if (raw == Boolean.class || raw == boolean.class) return bool();
        if (Number.class.isAssignableFrom(raw) || raw.isPrimitive() && raw != char.class) return number();
        if (raw == String.class) return string();
        if (raw.isEnum()) {
            AdapterInputShape[] names = java.util.Arrays.stream(raw.getEnumConstants())
                    .map(value -> literal(((net.minecraft.util.StringRepresentable) value).getSerializedName()))
                    .toArray(AdapterInputShape[]::new);
            return union(host(raw), new AdapterInputShape.UnionValue(List.of(names)));
        }
        if (raw == Block.class) return union(host(raw), string());
        if (raw == net.minecraft.tags.TagKey.class || raw == net.minecraft.resources.ResourceKey.class
                || raw == net.minecraft.core.Holder.class) return union(host(raw), string());
        // Unknown components accept their exact original host value, not fabricated structural coercions.
        return host(raw);
    }

    private static String nativeType(Class<?> raw) {
        if (raw == ItemStack.class) return "item_stack";
        if (raw == Ingredient.class) return "ingredient";
        if (raw == SizedIngredient.class) return "flat_sized_ingredient";
        if (raw == FluidStack.class) return "fluid_stack";
        if (raw == SizedFluidIngredient.class) return "flat_sized_fluid_ingredient";
        return null;
    }

    private static AdapterInputShape nativeShape(String type) {
        return switch (type) {
            case "item_stack" -> union(string(), object(Slot.req("id", string()), Slot.opt("count", number())));
            case "ingredient" -> union(string(), object(Slot.req("item", string())), object(Slot.req("tag", string())));
            case "flat_sized_ingredient" -> union(string(), object(Slot.req("item", string()), Slot.opt("count", number())),
                    object(Slot.req("tag", string()), Slot.opt("count", number())));
            case "fluid_stack" -> union(string(), object(Slot.req("id", string()), Slot.req("amount", number())));
            case "flat_sized_fluid_ingredient" -> union(object(Slot.req("type", literal("neoforge:single")), Slot.req("fluid", string()), Slot.req("amount", number())),
                    object(Slot.req("type", literal("neoforge:tag")), Slot.req("tag", string()), Slot.req("amount", number())));
            default -> throw new IllegalArgumentException("[NEKO-5341] Unknown programmatic native component: " + type);
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> JsonElement encode(RecipeComponent<T> component, Value value, RecipeSchemaHost host,
                                      RecipeFieldValueCodec nativeCodec, RecipeFieldValueCodec elementCodec) {
        HolderLookup.Provider registries = (HolderLookup.Provider) host.registryAccess();
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Object object = value.isHostObject() ? value.asHostObject() : null;
        JsonElement json;
        if (object != null && primaryClass(component).isInstance(object)) {
            json = component.codec().encodeStart(ops, (T) object).getOrThrow(message ->
                    new IllegalArgumentException("[NEKO-5342] Programmatic component encode failed: " + message));
        } else if (component instanceof ListRecipeComponent<?> list) {
            var array = new com.google.gson.JsonArray();
            if (value.hasArrayElements()) {
                for (long index = 0; index < value.getArraySize(); index++) array.add(elementCodec.encoder().encode(value.getArrayElement(index), host));
            } else if (list.canWriteSelf()) array.add(elementCodec.encoder().encode(value, host));
            else throw new IllegalArgumentException("[NEKO-5343] Recipe component requires an array: " + component);
            json = array;
        } else {
            if (nativeCodec != null) {
                json = nativeCodec.encoder().encode(value, host);
            } else if (acceptsItemStack(component)) {
                JsonElement itemJson = host.encodeField(RecipeFieldKind.ITEM_STACK, value);
                ItemStack stack = ItemStack.CODEC.parse(ops, itemJson).getOrThrow(message ->
                        new IllegalArgumentException("[NEKO-5346] Item component decode failed: " + message));
                T wrapped = component.wrap(() -> new dev.latvian.mods.rhino.Context(), stack);
                json = component.codec().encodeStart(ops, wrapped).getOrThrow(message ->
                        new IllegalArgumentException("[NEKO-5342] Programmatic component encode failed: " + message));
            } else if (primaryClass(component) == Block.class && value.isString()) {
                json = new JsonPrimitive(value.asString());
            } else if (primaryClass(component) == net.minecraft.tags.TagKey.class && value.isString()) {
                json = new JsonPrimitive(value.asString().replaceFirst("^#", ""));
            } else {
                Class<?> raw = primaryClass(component);
                if (raw == Integer.class || raw == int.class || raw == Long.class || raw == long.class) {
                    if (!value.isNumber() || !value.fitsInLong() || Math.abs(value.asDouble()) > 9_007_199_254_740_991D) {
                        throw new IllegalArgumentException("[NEKO-5344] Recipe number must be an exact safe integer");
                    }
                    if ((raw == Integer.class || raw == int.class) && !value.fitsInInt()) {
                        throw new IllegalArgumentException("[NEKO-5344] Recipe number is outside integer bounds");
                    }
                }
                if (!(shape(component) instanceof AdapterInputShape.HostValue)) json = host.toJson(value);
                else throw new IllegalArgumentException("[NEKO-5345] Component requires its original typed host value: " + component);
            }
        }
        T decoded = component.codec().parse(ops, json).getOrThrow(message ->
                new IllegalArgumentException("[NEKO-5346] Programmatic component decode failed: " + message));
        return component.codec().encodeStart(ops, decoded).getOrThrow(message ->
                new IllegalArgumentException("[NEKO-5342] Programmatic component encode failed: " + message));
    }
}
