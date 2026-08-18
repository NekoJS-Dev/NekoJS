package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;

/**
 * Platform implementation of {@link RecipeSchemaHost}, bridging the common recipe namespace
 * proxies to the NeoForge {@link RecipeEventJS} and its resolvers/serializers.
 *
 * <p>This is the single place that knows how to turn a JS value into recipe JSON for each
 * {@link RecipeFieldKind} (resolving ingredients/fluids and serializing Minecraft stacks).
 */
final class RecipeEventSchemaHost implements RecipeSchemaHost {
    private final RecipeEventJS event;

    RecipeEventSchemaHost(RecipeEventJS event) {
        this.event = event;
    }

    /** 创建 JSON 配方 builder（代理到 {@link RecipeJsonBuilder}）。 */
    @Override
    public RecipeBuilder builder(String type, String prefix) {
        return new RecipeJsonBuilder(event, type, prefix);
    }

    /** 添加自定义（非 schema 化）配方 JSON。 */
    @Override
    public Object custom(JsonObject json) {
        return event.custom(json);
    }

    /** 任意 JS 值 → 配方 JSON（经 {@link RecipeJsonValueConverter}）。 */
    @Override
    public JsonElement toJson(Value value) {
        return RecipeJsonValueConverter.toJson(event, value);
    }

    /** 按字段种类编码 JS 值：标量直转，ingredient/fluid 走各 resolver 后序列化。 */
    @Override
    public JsonElement encodeField(RecipeFieldKind kind, Value value) {
        return switch (kind) {
            case JSON -> RecipeJsonValueConverter.toJson(event, value);
            case STRING -> new JsonPrimitive(requireString(value));
            case INT -> new JsonPrimitive(requireInt(value));
            case NUMBER -> new JsonPrimitive(requireNumber(value));
            case BOOLEAN -> new JsonPrimitive(requireBoolean(value));
            case INGREDIENT -> event.serializeIngredient(IngredientResolver.fromValue(value));
            case ITEM_STACK -> event.serializeResult(new ItemStackAdapter().apply(value));
            case FLUID_STACK -> event.serializeFluidStack(FluidResolver.stackFromValue(value));
            case FLUID_INGREDIENT -> event.serializeFluidIngredient(FluidResolver.ingredientFromValue(value));
            case SIZED_FLUID_INGREDIENT -> event.serializeSizedFluidIngredient(FluidResolver.sizedFromValue(value));
        };
    }

    /**
     * 带字段上下文的转换（阶段 2：类型安全构造器）。数组元素也逐一带字段名报错。
     * 覆盖 {@link RecipeSchemaHost#convertField} 的 default 实现，在标量/数组编码失败时
     * 抛出带「recipe 字段名 + 期望类型 + 实际类型」的清晰错误。
     */
    @Override
    public JsonElement convertField(RecipeFieldDefinition field, Value value) {
        try {
            return RecipeSchemaHost.super.convertField(field, value);
        } catch (RecipeFieldTypeException e) {
            throw new RecipeFieldTypeException(
                    "Field '" + field.name() + "' of recipe field kind " + field.kind() + ": " + e.getMessage());
        }
    }

    private static String requireString(Value value) {
        if (value.isString()) return value.asString();
        throw new RecipeFieldTypeException("expected a string but got " + describe(value));
    }

    private static int requireInt(Value value) {
        if (value.isNumber()) {
            double number = value.asDouble();
            if (number == Math.rint(number)) return value.asInt();
        }
        throw new RecipeFieldTypeException("expected an integer but got " + describe(value));
    }

    private static double requireNumber(Value value) {
        if (value.isNumber()) return value.asDouble();
        throw new RecipeFieldTypeException("expected a number but got " + describe(value));
    }

    private static boolean requireBoolean(Value value) {
        if (value.isBoolean()) return value.asBoolean();
        throw new RecipeFieldTypeException("expected a boolean but got " + describe(value));
    }

    private static String describe(Value value) {
        if (value == null) return "null";
        if (value.isNull()) return "null";
        if (value.isString()) return "string '" + value.asString() + "'";
        if (value.isBoolean()) return "boolean " + value.asBoolean();
        if (value.isNumber()) return "number " + value.asDouble();
        if (value.hasArrayElements()) return "array";
        return String.valueOf(value);
    }

    /** 带字段上下文标记的类型错误（convertField 捕获后补字段名）。 */
    private static final class RecipeFieldTypeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RecipeFieldTypeException(String message) {
            super(message);
        }
    }
}
